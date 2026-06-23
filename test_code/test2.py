import requests
import time

# ========== 设备信息 ==========
DEVICE_ID  = "dev_8291adf5"
API_KEY    = "7b08d20b48bc487b8a8316bc4f050ced"
SENSOR_ID  = "s_1781665808480"
BASE_URL   = "http://127.0.0.1:8080"

HEADERS = { "X-Api-Key": API_KEY }

def fetch_latest(limit: int = 10):
    """获取超声波传感器最新数据"""
    url = f"{BASE_URL}/api/data/{DEVICE_ID}/latest"
    params = {"sensorId": SENSOR_ID, "limit": limit}
    try:
        resp = requests.get(url, params=params, headers=HEADERS, timeout=5)
        if resp.status_code == 200:
            return resp.json().get("data", [])
        else:
            print(f"请求失败 [{resp.status_code}]: {resp.text[:100]}")
    except requests.exceptions.ConnectionError:
        print(f"连接失败: 后端未启动? ({BASE_URL})")
    except Exception as e:
        print(f"异常: {e}")
    return []

def fetch_device_info():
    """获取设备基本信息"""  
    url = f"{BASE_URL}/api/devices/{DEVICE_ID}"
    try:
        resp = requests.get(url, headers=HEADERS, timeout=5)
        if resp.status_code == 200:
            return resp.json().get("data", {})
    except Exception:
        pass
    return {}

def print_device_header():
    """打印设备信息头"""
    info = fetch_device_info()
    status = info.get("status", "?").upper()
    status_icon = "●" if status == "ONLINE" else "○"
    print(f"\n{'=' * 50}")
    print(f"  {status_icon} {info.get('name', '?')} ({DEVICE_ID})")
    print(f"  类型: {info.get('type', '?')}  状态: {status}")
    print(f"{'=' * 50}")

def monitor_ultrasonic(interval: int = 3):
    """持续监控超声波数据变化"""
    print_device_header()
    print(f"  拉取间隔: {interval}s")
    print(f"{'─' * 50}")

    prev_value = None
    count = 0
    try:
        while True:
            data = fetch_latest(limit=1)
            ts = time.strftime("%H:%M:%S")

            if data:
                latest = data[0]
                cur = latest["value"]
                count += 1

                # 数值变化指示
                if prev_value is None:
                    delta = ""
                else:
                    diff = cur - prev_value
                    if abs(diff) < 0.01:
                        delta = "  →"
                    elif diff > 0:
                        delta = f"  ↑ +{diff:.2f}"
                    else:
                        delta = f"  ↓ {diff:.2f}"

                bar = "█" * min(40, int(cur / 100 * 40))
                print(f"  [{ts}] #{count:<4} {cur:>8.2f}  {bar}{delta}")

                prev_value = cur
            else:
                print(f"  [{ts}] 无数据")

            time.sleep(interval)

    except KeyboardInterrupt:
        print(f"\n{'─' * 50}")
        print(f"  停止。共拉取 {count} 条")

if __name__ == "__main__":
    print_device_header()

    print("  📡 获取最新 10 条...")
    data = fetch_latest(limit=10)
    if data:
        values = [d["value"] for d in data]
        print(f"  最新值: {values[0]:.2f}  平均值: {sum(values)/len(values):.2f}")
        print(f"  最小值: {min(values):.2f}  最大值: {max(values):.2f}")
    else:
        print("  暂无数据")

    print()
    monitor_ultrasonic(interval=3)
