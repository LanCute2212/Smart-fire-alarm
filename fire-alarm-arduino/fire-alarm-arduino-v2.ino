#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <DHT.h>

// ===== CẤU HÌNH MẠNG & MQTT =====
const char* WIFI_SSID   = "PTIT_B5";        // <-- Đổi lại nếu cần
const char* WIFI_PASS   = "";               // <-- Đổi lại nếu cần

const char* MQTT_SERVER = "172.17.171.250"; // <-- IP Backend hiện tại
const int   MQTT_PORT   = 1883;
const char* DEVICE_ID   = "ESP32_009";      // <-- ID thiết bị bạn vừa tạo trên Admin App

String telemetryTopic = String("fire-alarm/telemetry/") + DEVICE_ID;
String commandTopic   = String("fire-alarm/command/") + DEVICE_ID;

// ===== CHÂN KẾT NỐI CẢM BIẾN =====
#define DHT_PIN       27   
#define DHT_TYPE      DHT11

#define MQ135_A_PIN   34   
#define MQ135_D_PIN   25   

#define IR_A_PIN      32   
#define IR_D_PIN      33   

#define BUZZER_PIN    4    

// ===== ĐỐI TƯỢNG TOÀN CỤC =====
DHT dht(DHT_PIN, DHT_TYPE);
WiFiClient espClient;
PubSubClient client(espClient);

hw_timer_t * timer = NULL;

// ===== CỜ (FLAGS) CHO NGẮT VÀ TIMER =====
// Từ khoá volatile báo cho Compiler biết biến này có thể bị thay đổi bất ngờ bởi Ngắt
volatile bool isFireDetected = false;
volatile bool shouldReadSensors = false;

// Biến cho Non-blocking MQTT Reconnect
unsigned long lastReconnectAttempt = 0;

// ==========================================
// 1. HÀM PHỤC VỤ NGẮT (INTERRUPT)
// ==========================================
// Kích hoạt ngay lập tức khi Cảm biến lửa IR rớt tín hiệu (FALLING)
void IRAM_ATTR handleFireInterrupt() {
  isFireDetected = true; // Chỉ bật cờ, KHÔNG thực thi logic phức tạp (tránh lỗi Watchdog)
}

// Kích hoạt định kỳ mỗi 2 giây bởi Hardware Timer
void IRAM_ATTR onTimerTriggered() {
  shouldReadSensors = true;
}

// ==========================================
// 2. SETUP
// ==========================================
void setup() {
  Serial.begin(115200);
  dht.begin();
  
  pinMode(MQ135_A_PIN, INPUT);
  pinMode(MQ135_D_PIN, INPUT);
  pinMode(IR_A_PIN, INPUT);
  pinMode(IR_D_PIN, INPUT_PULLUP); // Cảm biến thường là Open-Collector
  pinMode(BUZZER_PIN, OUTPUT);
  
  digitalWrite(BUZZER_PIN, LOW); // Tắt còi ban đầu

  // --- CÀI ĐẶT NGẮT CHO CẢM BIẾN LỬA ---
  attachInterrupt(digitalPinToInterrupt(IR_D_PIN), handleFireInterrupt, FALLING);

  // --- CÀI ĐẶT HARDWARE TIMER ---
  timer = timerBegin(0, 80, true);                  // Timer 0, prescaler 80 -> 1 tick = 1us
  timerAttachInterrupt(timer, &onTimerTriggered, true);
  timerAlarmWrite(timer, 2000000, true);            // Ngắt mỗi 2,000,000 us (2 giây)
  timerAlarmEnable(timer);

  // --- KẾT NỐI WIFI ---
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.print("Đang kết nối WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi Connected!");

  client.setServer(MQTT_SERVER, MQTT_PORT);
  client.setCallback(mqttCallback);
}

// ==========================================
// 3. MQTT CALLBACK & RECONNECT (NON-BLOCKING)
// ==========================================
void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (int i = 0; i < length; i++) {
    message += (char)payload[i];
  }
  Serial.println("Nhận lệnh MQTT: " + message);

  if (message == "SILENCE_ALARM") {
    digitalWrite(BUZZER_PIN, LOW); // Tắt còi
    Serial.println("Đã tắt còi báo động từ xa!");
  }
}

boolean reconnect() {
  if (client.connect(DEVICE_ID)) {
    Serial.println("Đã kết nối lại MQTT Broker");
    client.subscribe(commandTopic.c_str());
  }
  return client.connected();
}

// ==========================================
// 4. VÒNG LẶP CHÍNH (LOOP)
// ==========================================
void loop() {
  // --- 1. NON-BLOCKING MQTT KEEPALIVE ---
  if (!client.connected()) {
    long now = millis();
    if (now - lastReconnectAttempt > 5000) { // Thử lại mỗi 5s thay vì dùng delay()
      lastReconnectAttempt = now;
      if (reconnect()) {
        lastReconnectAttempt = 0;
      }
    }
  } else {
    client.loop(); // Lắng nghe lệnh từ Backend
  }

  // --- 2. XỬ LÝ KHẨN CẤP (NGẮT LỬA) ---
  if (isFireDetected) {
    digitalWrite(BUZZER_PIN, HIGH); // Rú còi LẬP TỨC
    Serial.println("🔥🔥🔥 CẢNH BÁO: ĐÃ PHÁT HIỆN TIA LỬA!!!");
    
    // Gửi cảnh báo khẩn cấp lên Backend
    if (client.connected()) {
      StaticJsonDocument<200> doc;
      doc["temperature"] = dht.readTemperature();
      doc["humidity"] = dht.readHumidity();
      doc["gasLevel"] = analogRead(MQ135_A_PIN);
      doc["irFlame"] = 1; // 1 = Có lửa
      
      char jsonBuffer[512];
      serializeJson(doc, jsonBuffer);
      client.publish(telemetryTopic.c_str(), jsonBuffer);
    }
    
    // Reset cờ ngắt
    isFireDetected = false; 
  }

  // --- 3. GỬI DỮ LIỆU ĐỊNH KỲ (TIMER) ---
  if (shouldReadSensors) {
    float t = dht.readTemperature();
    float h = dht.readHumidity();
    int gasA = analogRead(MQ135_A_PIN);
    
    // Đọc trạng thái lửa hiện tại (dành cho telemetry định kỳ)
    int irD = digitalRead(IR_D_PIN);
    int irFlame = (irD == LOW) ? 1 : 0; 

    if (!isnan(t) && !isnan(h)) {
      StaticJsonDocument<200> doc;
      doc["temperature"] = t;
      doc["humidity"] = h;
      doc["gasLevel"] = gasA;
      doc["irFlame"] = irFlame;
      
      char jsonBuffer[512];
      serializeJson(doc, jsonBuffer);
      
      if (client.connected()) {
        client.publish(telemetryTopic.c_str(), jsonBuffer);
        Serial.print("📤 Gửi MQTT định kỳ | ");
        Serial.println(jsonBuffer);
      }
    }
    
    // Reset cờ Timer
    shouldReadSensors = false;
  }
}
