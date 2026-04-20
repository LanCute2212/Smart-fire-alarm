#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <DHT.h>

// ---------------- CẤU HÌNH WIFI & MQTT ----------------
const char* ssid = "Tên_WiFi_Của_Bạn";
const char* password = "Mật_Khẩu_WiFi";

// IP của máy tính đang chạy Backend (chạy docker-compose)
const char* mqtt_server = "192.168.1.X"; 
const int mqtt_port = 1883;

// Topic để gửi lên (chữ cuối là Device ID, phải khớp với ID trong CSDL)
const char* mqtt_topic = "telemetry/sensor/ESP32_KITCHEN_01"; 

// ---------------- CẤU HÌNH CHÂN CẢM BIẾN ----------------
#define DHTPIN 4          // Chân nối DHT22
#define DHTTYPE DHT22     // Loại cảm biến DHT
#define MQ2_PIN 34        // Chân Analog nối MQ-2 (Đo khí gas)
#define IR_FLAME_PIN 5    // Chân Digital nối Cảm biến lửa IR

DHT dht(DHTPIN, DHTTYPE);
WiFiClient espClient;
PubSubClient client(espClient);

void setup_wifi() {
  delay(10);
  Serial.println();
  Serial.print("Đang kết nối WiFi: ");
  Serial.println(ssid);
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi đã kết nối. IP: ");
  Serial.println(WiFi.localIP());
}

void reconnect() {
  // Lặp cho đến khi kết nối lại MQTT thành công
  while (!client.connected()) {
    Serial.print("Đang kết nối MQTT Broker...");
    // Tạo Client ID ngẫu nhiên
    String clientId = "ESP32Client-";
    clientId += String(random(0xffff), HEX);
    
    if (client.connect(clientId.c_str())) {
      Serial.println("Đã kết nối MQTT!");
    } else {
      Serial.print("Thất bại, lỗi = ");
      Serial.print(client.state());
      Serial.println(" Thử lại sau 5 giây");
      delay(5000);
    }
  }
}

void setup() {
  Serial.begin(115200);
  dht.begin();
  pinMode(MQ2_PIN, INPUT);
  pinMode(IR_FLAME_PIN, INPUT);

  setup_wifi();
  client.setServer(mqtt_server, mqtt_port);
}

void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop(); // Giữ kết nối MQTT sống

  // 1. Đọc dữ liệu từ cảm biến
  float temperature = dht.readTemperature();
  float humidity = dht.readHumidity();
  // Đọc Analog MQ2 (giá trị từ 0-4095 trên ESP32)
  int gasRaw = analogRead(MQ2_PIN); 
  // Chuyển đổi thô ra ppm (tuỳ chuẩn hóa, ở đây mình giả lập tỷ lệ)
  float gasLevel = gasRaw / 4.0; 
  // Cảm biến lửa IR: Tích cực mức THẤP (có lửa thì = 0, không lửa = 1)
  int irFlameRaw = digitalRead(IR_FLAME_PIN);
  int irFlameDetected = (irFlameRaw == LOW) ? 1 : 0; 

  // Kiểm tra lỗi DHT
  if (isnan(temperature) || isnan(humidity)) {
    Serial.println("Lỗi: Không thể đọc dữ liệu từ DHT22!");
    delay(2000);
    return;
  }

  // 2. Đóng gói thành chuỗi JSON
  StaticJsonDocument<200> doc;
  doc["temperature"] = temperature;
  doc["humidity"] = humidity;
  doc["gasLevel"] = gasLevel;
  doc["irFlame"] = irFlameDetected;

  char jsonBuffer[512];
  serializeJson(doc, jsonBuffer);

  // 3. Publish lên MQTT Broker
  Serial.print("Đang gửi dữ liệu: ");
  Serial.println(jsonBuffer);
  
  client.publish(mqtt_topic, jsonBuffer);

  // Gửi mỗi 2 giây một lần
  delay(2000); 
}
