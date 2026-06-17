import requests
import time

# ========== 设备信息 ==========
DEVICE_ID = "dev_9eaa7c27"
API_KEY = "8b655638acd541c1beac63fe9723e511"
SENSOR_ID = "s_1780667505763"   # 温度传感器A1
BASE_URL = "http://127.0.0.1:8080"

def fetch_temperature(limit: int = 10):
    """从平台获取温度传感器最新数据"""
    url = f"{BASE_URL}/api/data/{DEVICE_ID}/latest"
    params = {
        "sensorId": SENSOR_ID,
        "limit": limit
    }
    headers = {"X-Api-Key": API_KEY}
    try:
        resp = requests.get(url, params=params, headers=headers, timeout=5)
        if resp.status_code == 200:
            data = resp.json().get("data", [])
            return data
        else:
            print(f"请求失败 [{resp.status_code}]: {resp.text[:100]}")
    except requests.exceptions.ConnectionError:
        print(f"连接失败: 后端未启动? ({BASE_URL})")
    except Exception as e:
        print(f"异常: {e}")
    return []

def print_temperature(data):
    """打印温度数据"""
    if not data:
        print("  暂无温度数据")
        return
    for i, item in enumerate(data):
        marker = " ← 最新" if i == 0 else ""
        ts = item.get("timestamp", "")[:19].replace("T", " ")
        print(f"  [{ts}]  {item['value']} ℃{marker}")

def poll_temperature(interval: int = 5):
    """持续拉取温度数据"""
    print(f"🌡  开始获取温度数据")
    print(f"   设备: {DEVICE_ID}")
    print(f"   拉取间隔: {interval}s")
    print(f"{'─' * 45}")
    try:
        while True:
            data = fetch_temperature(limit=1)
            if data:
                latest = data[0]
                ts = latest.get("timestamp", "")[:19].replace("T", " ")
                print(f"[{ts}]  温度: {latest['value']} ℃")
            else:
                print("  [无数据]")
            time.sleep(interval)
    except KeyboardInterrupt:
        print(f"\n{'─' * 45}")
        print("⏹  停止拉取")

if __name__ == "__main__":
    # 先拉取最新 10 条
    # print(">>> 获取最新温度数据...")
    # data = fetch_temperature(limit=10)
    # print_temperature(data)

    # 持续监控（每 5s 拉一次）
    print()
    poll_temperature(interval=5)
