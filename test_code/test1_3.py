import requests
# 下发命令控制设备
base_url = "http://127.0.0.1:8080"
device_id = "dev_fc158e26"     # 电动开关
api_key = "ad828ca853d64368a6768aa6c21637bb"

def send_command(command: str, actuator: str = "风扇"):
    """发送控制指令，命令 1=ON, 0=OFF"""
    url = f"{base_url}/api/data/{device_id}/command"
    headers = {
        "X-Api-Key": api_key,
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
            print(f"发送失败")
    except Exception as e:
        print(f"异常: {e}")

if __name__ == "__main__":
    # 打开开关
    # send_command("1")
    # 关闭开关
    send_command("0")