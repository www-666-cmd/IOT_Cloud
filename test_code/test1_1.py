import requests


# 获取传感器数据（以光照传感器为例）
device_id = "dev_b087404c"
api_key = "b1c432fb86d8477a96170ce13a220c5e"
sensor_id = "s_1781530543162"
base_url = "http://127.0.0.1:8080"

def get_light_value(limit=3):
    '''
    从平台获取光照数据
    '''
    url = f"{base_url}/api/data/{device_id}/latest"
    params = {
        "sensorId": sensor_id,
        "limit": limit  # 每3秒上传一次数据
    }
    headers = {
        "X-Api-Key": api_key
    }
    try:
        resp = requests.get(url, params=params, headers=headers, timeout=5)
        if resp.status_code == 200:
            data = resp.json().get("data", [])
            if data:
                latest = data[0]
                print(f"平台最新: {latest['value']} lux (共 {len(data)} 条)")
            else:
                print("无数据")
            return data
        else:
            print(f"查询失败")
    except Exception as e:
        print(f"查询异常{e}")
    return []



if __name__ == '__main__':
    get_light_value(limit=3)