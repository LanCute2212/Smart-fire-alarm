"""
FireAlarm ESP32 Simulator
Giả lập thiết bị ESP32 gửi dữ liệu cảm biến qua MQTT
Chạy: pip install paho-mqtt && python esp32_simulator.py
"""

import paho.mqtt.client as mqtt
import json
import time
import random
import math

# ===== CẤU HÌNH =====
MQTT_BROKER  = "localhost"   # Đổi thành IP máy tính nếu chạy từ máy khác
MQTT_PORT    = 1883
DEVICE_ID    = "DEV_001"
INTERVAL_SEC = 3             # Gửi mỗi 3 giây

# ===== KẾT NỐI MQTT =====
client = mqtt.Client(client_id=f"simulator_{DEVICE_ID}")

def on_connect(c, userdata, flags, rc):
    if rc == 0:
        print(f"✅ Connected to MQTT Broker!")
        # Lắng nghe lệnh từ Backend (Quick Action)
        topic = f"fire-alarm/command/{DEVICE_ID}"
        c.subscribe(topic)
        print(f"📡 Subscribed to: {topic}")
    else:
        print(f"❌ Connection failed, code: {rc}")

def on_message(c, userdata, msg):
    print(f"\n📩 Command received: {msg.payload.decode()}")
    data = json.loads(msg.payload.decode())
    if data.get("action") == "SILENCE_ALARM":
        print(f"🔇 Alarm silenced for {data.get('duration', 30)} seconds")

client.on_connect = on_connect
client.on_message = on_message
client.connect(MQTT_BROKER, MQTT_PORT, 60)
client.loop_start()

time.sleep(1)  # Chờ connect xong

TOPIC = f"fire-alarm/telemetry/{DEVICE_ID}"

# ===== CÁC CHẾ ĐỘ GIẢ LẬP =====
def mode_normal(t):
    """Trạng thái bình thường — SAFE"""
    return {
        "t":   round(28 + 3 * math.sin(t / 10), 1),  # 25-31°C dao động
        "h":   round(65 + 5 * math.sin(t / 15), 1),  # 60-70%
        "g":   int(200 + 50 * random.random()),       # 200-250 ppm
        "ir":  0,
        "bat": max(20, 100 - int(t / 60))             # Pin giảm dần
    }

def mode_warning(t):
    """Nhiệt độ tăng cao — WARNING"""
    return {
        "t":   round(45 + random.uniform(-2, 2), 1),  # ~45°C
        "h":   round(50 + random.uniform(-3, 3), 1),
        "g":   int(350 + random.uniform(-50, 100)),   # 300-450 ppm (vượt ngưỡng)
        "ir":  0,
        "bat": 75
    }

def mode_fire(t):
    """Cháy thực sự — DANGER (riskScore >= 80)"""
    return {
        "t":   round(70 + random.uniform(-5, 10), 1), # 65-80°C
        "h":   round(35 + random.uniform(-5, 5), 1),
        "g":   int(450 + random.uniform(0, 150)),     # 450-600 ppm
        "ir":  1,                                     # Phát hiện tia lửa!
        "bat": 70
    }

def mode_recovery(t):
    """Sau khi tắt còi — đang hạ nhiệt"""
    return {
        "t":   round(40 - (t % 30) * 0.5, 1),        # Hạ dần từ 40°C
        "h":   round(55 + random.uniform(-2, 2), 1),
        "g":   int(250 + random.uniform(-30, 30)),
        "ir":  0,
        "bat": 70
    }

print("\n" + "="*50)
print("🔥 FireAlarm ESP32 Simulator")
print("="*50)
print("Chọn chế độ giả lập:")
print("  1. Normal (SAFE) — Bình thường")
print("  2. Warning — Nhiệt độ tăng cao")
print("  3. Fire!  — Báo cháy khẩn cấp")
print("  4. Auto   — Tự động chạy qua các kịch bản")
print("="*50)

choice = input("Chọn (1-4): ").strip()

t = 0
try:
    print(f"\n📤 Bắt đầu gửi dữ liệu lên topic: {TOPIC}")
    print("Nhấn Ctrl+C để dừng\n")

    while True:
        if choice == "1":
            payload = mode_normal(t)
        elif choice == "2":
            payload = mode_warning(t)
        elif choice == "3":
            payload = mode_fire(t)
        elif choice == "4":
            # Auto: 30s normal → 15s warning → 10s fire → 20s recovery → lặp
            cycle = t % 75
            if cycle < 30:
                payload = mode_normal(t)
                scenario = "🟢 NORMAL"
            elif cycle < 45:
                payload = mode_warning(t)
                scenario = "🟡 WARNING"
            elif cycle < 55:
                payload = mode_fire(t)
                scenario = "🔴 FIRE!"
            else:
                payload = mode_recovery(t)
                scenario = "🔵 RECOVERY"
            print(f"[Auto mode] {scenario}")
        else:
            payload = mode_normal(t)

        msg = json.dumps(payload)
        client.publish(TOPIC, msg)
        print(f"[t={t:3d}s] 📤 {msg}")

        t += INTERVAL_SEC
        time.sleep(INTERVAL_SEC)

except KeyboardInterrupt:
    print("\n\n✋ Simulator stopped.")
    client.loop_stop()
    client.disconnect()
