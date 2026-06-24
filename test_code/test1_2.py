import requests
import time
import random
import math

# 发送数据（以光照传感器为例）
device_id = "dev_b087404c"
api_key = "b1c432fb86d8477a96170ce13a220c5e"
sensor_id = "s_1781530543162"
base_url = "http://127.0.0.1:8080"

def upload_data(value):
    "上传一条光照数据"
    url = f"{base_url}/api/data/{device_id}"
    headers = {
        "X-Api-Key": api_key,
        "Content-Type": "application/json"
    }
    payload = {
        "sensorId": sensor_id,
        "value": round(value, 2)
    }
    try:
        resp = requests.post(url, headers=headers, json=payload, timeout=5)
        if resp.status_code == 200:
            print(f"光照数据上传成功！")
        else:
            print(f"光照数据上传失败")
    except Exception as e:
        print(f"异常{e}")

if __name__ == '__main__':
    upload_data(43)





