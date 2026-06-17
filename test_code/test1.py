import requests
import time
import random
import math

# ========== 设备信息 ==========
DEVICE_ID  = "dev_b087404c"
API_KEY    = "b1c432fb86d8477a96170ce13a220c5e"
SENSOR_ID  = "s_1780667505763"   # 温度传感器A1
BASE_URL   = "http://127.0.0.1:8080"

def fetch_latest(limit: int = 3):
    """从平台获取设备最新光照数据"""
    url = f"{BASE_URL}/api/data/{DEVICE_ID}/latest"
    params = {"sensorId": SENSOR_ID, "limit": limit}
    headers = {"X-Api-Key": API_KEY}
    try:   
        resp = requests.get(url, params=params, headers=headers, timeout=5)
        if resp.status_code == 200:
            data = resp.json().get("data", [])
            if data:
                latest = data[0]
                print(f"  📡 平台最新: {latest['value']} lux (共 {len(data)} 条)")
            else:
                print(f"  📡 平台暂无数据")
            return data
        else:
            print(f"  ✗ 查询失败 [{resp.status_code}]: {resp.text[:80]}")
    except Exception as e:
        print(f"  ✗ 查询异常: {e}")
    return []

def upload_light(value: float):
    """上传一条光照数据"""
    url = f"{BASE_URL}/api/data/{DEVICE_ID}"
    headers = {
        "X-Api-Key": API_KEY,
        "Content-Type": "application/json"
    }
    payload = {
        "sensorId": SENSOR_ID,
        "value": round(value, 2)
    }
    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=5)
        if resp.status_code == 200:
            print(f"  ✓ 上传成功 → {value:.2f} lux")
        else:
            print(f"  ✗ 上传失败 [{resp.status_code}]: {resp.text[:80]}")
    except requests.exceptions.ConnectionError:
        print(f"  ✗ 连接失败: 后端未启动? ({BASE_URL})")
    except Exception as e:
        print(f"  ✗ 异常: {e}")

def simulate_daylight(seconds_per_day: int = 120):
    """
    模拟一天的光照曲线 (正弦波形)
    日出 06:00 → 正午峰值 12:00 → 日落 18:00 → 夜间最低
    """
    # 光照参数
    MAX_LUX = 80.0    # 正午峰值
    MIN_LUX = 0.5     # 夜间最低
    interval = 3      # 每隔 3 秒上传一次

    print(f"☀  开始模拟光照数据")
    print(f"   设备: {DEVICE_ID}  传感器: {SENSOR_ID}")
    print(f"   光照范围: {MIN_LUX} ~ {MAX_LUX} lux")
    print(f"   一天周期: {seconds_per_day}s, 每 {interval}s 上传一次")
    print(f"{'─' * 50}")

    start = time.time()
    upload_count = 0
    try:
        while True:
            elapsed = time.time() - start

            # 计算当前"时间" (0.0 ~ 1.0 代表 00:00 ~ 24:00)
            time_of_day = (elapsed % seconds_per_day) / seconds_per_day

            # 正弦模拟: 6:00=0.25 开始亮, 12:00=0.5 最亮, 18:00=0.75 开始暗
            # 日照时长 12h (06:00-18:00)，基于正弦曲线
            base = math.sin((time_of_day - 0.25) * math.pi)

            # 夜间映射为负值 → 裁为零
            if base < 0:
                base = base * 0.1  # 微弱的夜间光

            # 映射到 [MIN_LUX, MAX_LUX] 范围
            light = MIN_LUX + (MAX_LUX - MIN_LUX) * max(0, base)

            # 叠加随机噪声 (±10%) + 云层波动
            noise = random.uniform(-0.08, 0.08) * MAX_LUX
            cloud_effect = random.uniform(-0.05, 0.05) * MAX_LUX * random.random()
            light = max(MIN_LUX, min(MAX_LUX, light + noise + cloud_effect))

            # 显示当前虚拟时间
            hours = int(time_of_day * 24)
            minutes = int((time_of_day * 24 - hours) * 60)
            period = "☀" if 6 <= hours < 18 else "🌙"
            print(f"[{period} {hours:02d}:{minutes:02d}]", end=" ")

            upload_light(light)
            upload_count += 1
            time.sleep(interval)

    except KeyboardInterrupt:
        print(f"\n{'─' * 50}")
        print(f"⏹  模拟结束，共上传 {upload_count} 条数据")

if __name__ == "__main__":
    # 先做一次快速测试
    print(">>> 连接测试...")
    upload_light(random.uniform(20, 40))
    time.sleep(1)

    # 验证平台数据回读
    print(">>> 回读验证...")
    fetch_latest(limit=3)
    print()

    # 开始循环模拟
    simulate_daylight(seconds_per_day=120)
