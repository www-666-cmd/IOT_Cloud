import requests
import time

# ========== 设备信息 ==========
DEVICE_ID = "dev_414305e8"     # 电动开关
API_KEY = "f37ff332325d47b7b261e3fb22a949c6"
SENSOR_ID = "a_1781577362865"  # 开关执行器
BASE_URL = "http://127.0.0.1:8080"

def send_command(command: str, actuator: str = "电动开关"):
    """发送控制指令，命令 1=ON, 0=OFF"""
    url = f"{BASE_URL}/api/data/{DEVICE_ID}/command"
    headers = {
        "X-Api-Key": API_KEY,
        "Content-Type": "application/json"
    }
    payload = {
        "command": command,
        "params": {
            "actuator": actuator
        }
    }
    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=5)
        if resp.status_code == 200:
            data = resp.json()
            print(f"发送指令 [{command}] → {data.get('data', {}).get('message', 'ok')}")
        else:
            print(f"发送失败 [{resp.status_code}]: {resp.text[:100]}")
    except requests.exceptions.ConnectionError:
        print(f"连接失败: 后端未启动 ({BASE_URL})")
    except Exception as e:
        print(f"异常: {e}")

def fetch_status():
    """查询执行器当前状态"""
    url = f"{BASE_URL}/api/devices/{DEVICE_ID}"
    headers = {"X-Api-Key": API_KEY}
    try:
        resp = requests.get(url, headers=headers, timeout=5)
        if resp.status_code == 200:
            device = resp.json().get("data", {})
            sensors = device.get("sensors", [])
            for s in sensors:
                if s.get("id") == SENSOR_ID:
                    state = "ON" if s.get("value", 0) > 0.5 else "OFF"
                    print(f"电动开关状态: {state} (value={s['value']})")
                    return s.get("value", 0)
        return 0
    except Exception as e:
        print(f"查询异常: {e}")
        return 0

def simulate_switch_cycle():
    """周期性地开关"""
    print(f"电动开关控制模拟")
    print(f"设备: {DEVICE_ID}")
    print(f"指令: 1 → ON, 0 → OFF")
    print(f"{'─' * 40}")

    count = 0
    try:
        while True:
            cmd = "1" if count % 2 == 0 else "0"
            send_command(cmd)
            time.sleep(1)
            fetch_status()
            count += 1
            time.sleep(3)
    except KeyboardInterrupt:
        print(f"\n{'─' * 40}")
        print(f"共发送 {count} 条指令")

if __name__ == "__main__":
    print("测试")
    print("打开开关")
    send_command("1")
    time.sleep(1)
    fetch_status()

    print("\n关闭开关")
    send_command("0")
    time.sleep(1)
    fetch_status()

    print()
    simulate_switch_cycle()
