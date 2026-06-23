"""
MQTT 设备端示例 — 通过 MQTT 协议上传传感器数据

安装依赖: pip install paho-mqtt requests
MQTT Broker: 需启动 Mosquitto (默认端口 1883)
  Docker: docker run -d --name mosquitto -p 1883:1883 eclipse-mosquitto
"""

import json
import time
import random
import math

# ========== 设备信息 ==========
DEVICE_ID   = "dev_b087404c"
API_KEY     = "b1c432fb86d8477a96170ce13a220c5e"
SENSOR_ID   = "s_1781530543162"   # 光照传感器
MQTT_BROKER = "localhost"
MQTT_PORT   = 1883

# ========== MQTT 客户端 ==========
USE_MQTT = True  # 改为 False 则降级使用 HTTP

if USE_MQTT:
    try:
        import paho.mqtt.client as mqtt
        mqtt_client = mqtt.Client(client_id=f"device_{DEVICE_ID}")
        mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
        mqtt_client.loop_start()
        print(f"MQTT 已连接 → {MQTT_BROKER}:{MQTT_PORT}")
    except Exception as e:
        print(f"MQTT 连接失败: {e}，降级使用 HTTP")
        USE_MQTT = False

def upload_data(value: float):
    """上传光照数据：优先 MQTT，降级 HTTP"""
    if USE_MQTT:
        # MQTT 发布
        topic = f"iot/{DEVICE_ID}/telemetry"
        payload = json.dumps({
            "sensorId": SENSOR_ID,
            "value": round(value, 2),
            "timestamp": int(time.time() * 1000)
        })
        mqtt_client.publish(topic, payload, qos=1)
        print(f"  ✓ [MQTT] {topic} → {value:.2f} lux")
    else:
        # HTTP 降级
        import requests
        url = f"http://127.0.0.1:8080/api/data/{DEVICE_ID}"
        headers = {"X-Api-Key": API_KEY, "Content-Type": "application/json"}
        payload = {"sensorId": SENSOR_ID, "value": round(value, 2)}
        try:
            resp = requests.post(url, json=payload, headers=headers, timeout=5)
            if resp.status_code == 200:
                print(f"  ✓ [HTTP] → {value:.2f} lux")
            else:
                print(f"  ✗ [HTTP] {resp.status_code}")
        except Exception as e:
            print(f"  ✗ [HTTP] {e}")

def simulate_daylight():
    """模拟一天光照曲线 (正弦波形)"""
    MAX_LUX = 80.0
    MIN_LUX = 0.5
    INTERVAL = 3
    DAY_SECONDS = 120

    print(f"☀  光照数据模拟 (MQTT)")
    print(f"   Broker: {MQTT_BROKER}:{MQTT_PORT}")
    print(f"   设备: {DEVICE_ID}  Topic: iot/{DEVICE_ID}/telemetry")
    print(f"   光照范围: {MIN_LUX} ~ {MAX_LUX} lux, 每 {INTERVAL}s 上报")
    print(f"{'─' * 50}")

    start = time.time()
    count = 0
    try:
        while True:
            elapsed = time.time() - start
            time_of_day = (elapsed % DAY_SECONDS) / DAY_SECONDS

            # 正弦模拟日照
            base = math.sin((time_of_day - 0.25) * math.pi)
            if base < 0:
                base *= 0.1
            light = MIN_LUX + (MAX_LUX - MIN_LUX) * max(0, base)

            # 噪声 + 云层
            light += random.uniform(-0.08, 0.08) * MAX_LUX
            light += random.uniform(-0.05, 0.05) * MAX_LUX * random.random()
            light = max(MIN_LUX, min(MAX_LUX, light))

            hours = int(time_of_day * 24)
            minutes = int((time_of_day * 24 - hours) * 60)
            period = "☀" if 6 <= hours < 18 else "🌙"
            print(f"[{period} {hours:02d}:{minutes:02d}]", end=" ")

            upload_data(light)
            count += 1
            time.sleep(INTERVAL)

    except KeyboardInterrupt:
        print(f"\n{'─' * 50}")
        print(f"⏹  共上报 {count} 条数据")
        if USE_MQTT:
            mqtt_client.loop_stop()
            mqtt_client.disconnect()

if __name__ == "__main__":
    print(">>> 连接测试...")
    upload_data(random.uniform(30, 50))
    time.sleep(1)
    print()
    simulate_daylight()
