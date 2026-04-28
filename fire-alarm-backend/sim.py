import paho.mqtt.client as mqtt
import json, time, random, math

DEVICE_ID = "DEV_001"
MQTT_HOST = "172.19.0.2"   # IP của firealarm_mqtt container
TOPIC = f"fire-alarm/telemetry/{DEVICE_ID}"

client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, f"simulator_{DEVICE_ID}")

def on_connect(c, u, f, rc):
    print("Connected!" if rc == 0 else f"Failed: {rc}")
    c.subscribe(f"fire-alarm/command/{DEVICE_ID}")

def on_message(c, u, msg):
    print(f"Command: {msg.payload.decode()}")

client.on_connect = on_connect
client.on_message = on_message
client.connect(MQTT_HOST, 1883)
client.loop_start()
time.sleep(1)

print(f"Sending to {TOPIC} every 3s. Ctrl+C to stop.")
t = 0
while True:
    payload = json.dumps({
        "t":  round(28 + 3*math.sin(t/10) + random.uniform(-0.5, 0.5), 1),
        "h":  round(65 + 5*math.sin(t/15), 1),
        "g":  int(200 + 50*random.random()),
        "ir": 0,
        "bat": max(20, 100 - int(t/60))
    })
    client.publish(TOPIC, payload)
    print(f"[t={t}s] {payload}")
    t += 3
    time.sleep(3)
