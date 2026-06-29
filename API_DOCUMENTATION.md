# IoT Platform API 接口文档

**Base URL:** `http://localhost:8080`  
**Version:** 1.0.0  
**认证方式:** Bearer Token (JWT)，除登录/注册外均需在 Header 中携带 `Authorization: Bearer <token>`

---

## 通用说明

### 响应格式

所有接口统一返回：

```json
{
  "success": true,
  "message": "操作成功",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 请求是否成功 |
| message | string | 提示信息 |
| data | object/array/null | 响应数据 |

### 错误处理

- **400** — 请求参数错误（含 `@Valid` 校验失败的字段详情）
- **401** — 未登录或 Token 过期

---

## 1. 认证模块 `/api/auth`

### 1.1 账号密码登录

```
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "123456"
}
```

**响应示例：**

```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOi...",
    "id": 1,
    "username": "admin",
    "email": "admin@example.com",
    "phone": null,
    "role": "ADMIN",
    "createdAt": "2026-06-01T10:00:00"
  }
}
```

### 1.2 用户注册

```
POST /api/auth/register
Content-Type: application/json

{
  "username": "newuser",       // 必填，3~20字符
  "email": "user@example.com", // 必填，合法邮箱
  "password": "123456"         // 必填，6~20字符
}
```

### 1.3 发送短信验证码

```
POST /api/auth/send-code
Content-Type: application/json

{
  "phone": "13800138000"       // 必填，1 开头 11 位手机号
}
```

### 1.4 手机号 + 验证码登录

```
POST /api/auth/login-by-phone
Content-Type: application/json

{
  "phone": "13800138000",
  "code": "123456"             // 6位验证码
}
```

### 1.5 获取当前用户信息

```
GET /api/auth/me
Authorization: Bearer <token>
```

---

## 2. 设备管理 `/api/devices`

### 2.1 获取设备列表

```
GET /api/devices
```

返回所有设备的列表，每个设备包含 `sensors` 和 `actuators` 数组。

### 2.2 获取单个设备

```
GET /api/devices/{deviceId}
```

### 2.3 创建设备

```
POST /api/devices
Content-Type: application/json

{
  "name": "温控设备-01",        // 必填
  "type": "temperature_controller", // 必填
  "status": "ONLINE",           // 默认 OFFLINE
  "location": "A栋-3楼-实验室",
  "description": "三楼实验室温度监测",
  "sensors": [
    {
      "id": "sensor_temp_01",
      "name": "温度传感器",
      "type": "temperature",
      "unit": "°C",
      "minVal": -20,
      "maxVal": 80
    }
  ]
}
```

### 2.4 更新设备（全量）

```
PUT /api/devices/{deviceId}
Content-Type: application/json

{
  "name": "温控设备-01-改",
  "type": "temperature_controller",
  "status": "ONLINE",
  "location": "A栋-3楼-实验室-改",
  "description": "更新后的描述",
  "sensors": [ ... ]
}
```

### 2.5 删除设备

```
DELETE /api/devices/{deviceId}
```

### 2.6 更新设备状态（部分）

```
PATCH /api/devices/{deviceId}/status
Content-Type: application/json

{
  "status": "OFFLINE"
}
```

### 2.7 设备统计

```
GET /api/devices/stats
```

**响应示例：**

```json
{
  "data": {
    "total": 10,
    "online": 7,
    "offline": 2,
    "warning": 1
  }
}
```

---

## 3. 数据查询与写入 `/api/data`

### 3.1 查询设备数据（时间范围）

```
GET /api/data/{deviceId}?sensorId=sensor_temp_01&from=2026-06-22T00:00:00&to=2026-06-29T23:59:59&limit=200
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sensorId | string | 否 | 传感器 ID 过滤 |
| from | ISO DateTime | 否 | 开始时间，不传则查最新数据 |
| to | ISO DateTime | 否 | 结束时间 |
| limit | int | 否 | 最大返回条数，默认 200 |

**响应示例：**

```json
{
  "data": [
    {
      "id": null,
      "deviceId": "dev_8291adf5",
      "sensorId": "sensor_temp_01",
      "value": 25.5,
      "ownerId": 1,
      "timestamp": "2026-06-29T14:30:00"
    }
  ]
}
```

### 3.2 查询历史聚合数据（降采样）

```
GET /api/data/{deviceId}/history?from=2026-06-22T00:00:00&to=2026-06-29T23:59:59&sensorId=sensor_temp_01&interval=1h
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| from | ISO DateTime | **是** | 开始时间 |
| to | ISO DateTime | **是** | 结束时间 |
| sensorId | string | 否 | 传感器 ID 过滤 |
| interval | string | 否 | 聚合窗口，如 `5m`、`1h`、`1d`（不传则返回原始数据） |

**返回 Map 数组，包含 ts / avg_val / max_val / min_val / sensor_id**

### 3.3 写入数据点

```
POST /api/data/{deviceId}
Content-Type: application/json

{
  "sensorId": "sensor_temp_01",
  "value": 26.8
}
```

### 3.4 查询最新数据

```
GET /api/data/{deviceId}/latest?sensorId=sensor_temp_01&limit=10
```

轻量接口，同时支持 `X-Api-Key` Header 认证（设备直连上报场景）。

### 3.5 下发执行器指令

```
POST /api/data/{deviceId}/command
Content-Type: application/json

{
  "command": "on",
  "params": {
    "actuator": "风扇"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| command | string | `on` / `off` / `toggle` / `1` / `0`（大小写不敏感） |
| params.actuator | string | 执行器名称（需和数据库 `sensor.name` 一致） |

---

## 4. 告警管理 `/api/alerts`

### 4.1 告警规则 CRUD

```
GET    /api/alerts/rules                  # 获取告警规则列表
GET    /api/alerts/rules/{id}             # 获取单条规则
POST   /api/alerts/rules                  # 创建规则
PUT    /api/alerts/rules/{id}             # 更新规则（全量）
DELETE /api/alerts/rules/{id}             # 删除规则
PATCH  /api/alerts/rules/{id}/toggle?enabled=true   # 启用/禁用规则
```

**AlertRule 结构示例：**

```json
{
  "id": 1,
  "name": "温度过高告警",
  "deviceId": "dev_8291adf5",
  "sensorType": "temperature",
  "condition": ">",
  "threshold": 40.0,
  "level": "WARNING",
  "enabled": true,
  "ownerId": 1
}
```

`level` 可选值: `INFO` / `WARNING` / `CRITICAL`  
`condition` 可选值: `>` / `<` / `>=` / `<=` / `==`

### 4.2 告警记录

```
GET    /api/alerts/records?deviceId=dev_8291adf5&status=ACTIVE&level=WARNING&page=0&size=20
GET    /api/alerts/records/{id}
DELETE /api/alerts/records/{id}
PATCH  /api/alerts/records/{id}/acknowledge    # 确认告警
PATCH  /api/alerts/records/{id}/resolve         # 解决告警
```

### 4.3 告警统计

```
GET /api/alerts/stats
```

---

## 5. 系统设置 `/api/settings`

```
GET /api/settings                              # 获取当前用户设置
PUT /api/settings                              # 更新当前用户设置（部分更新）

Content-Type: application/json
{
  "dataRetentionDays": 90,
  "alertEmailEnabled": true
}
```

---

## 6. 模拟控制 `/api/simulation`

### 6.1 数据上报模拟

```
POST /api/simulation/upload/start?interval=5    # 启动（间隔秒）
POST /api/simulation/upload/stop                # 停止
POST /api/simulation/upload/once                # 手动触发一轮
```

### 6.2 指令下发模拟

```
POST /api/simulation/delivery/start?interval=10  # 启动（间隔秒）
POST /api/simulation/delivery/stop               # 停止
POST /api/simulation/delivery/once               # 手动触发一轮
```

### 6.3 全部控制

```
POST /api/simulation/stop       # 停止所有模拟
GET  /api/simulation/status     # 查询模拟状态
```

### 6.4 指令日志

```
GET /api/simulation/commands?deviceId=dev_8291adf5   # 查询指令下发历史
```

---

## 7. 健康检查 `/api/health`

```
GET /api/health              # { "status": "UP", "service": "iot-platform", "version": "1.0.0" }
GET /api/health/ready        # { "status": "READY" }
```

---

## 8. 执行器控制说明

### REST API 方式

```json
POST /api/data/{deviceId}/command
{
  "command": "on",
  "params": { "actuator": "风扇" }
}
```

### MQTT 方式

通过 EMQX 向设备 topic 下发指令：

```
Topic: iot/{deviceId}/command
QoS:   1
Payload:
{
  "command": "off",
  "actuator": "风扇"
}
```

平台接收到 MQTT 指令后，会自动：
1. 更新 PostgreSQL 中传感器的当前值
2. 写入 TDengine 时序记录（支持历史查询）
3. 通过 WebSocket 推送实时状态更新到前端

---

## 9. MQTT 主题一览

| 主题 | 方向 | 说明 |
|------|------|------|
| `iot/+/telemetry` | 设备 → 平台 | 传感器数据上报 |
| `iot/+/status` | 设备 → 平台 | 设备状态上报 |
| `iot/+/command` | 设备/外部 → 平台 | 执行器指令下发 |

**telemetry 上报格式：**

```json
{
  "sensorId": "sensor_temp_01",
  "value": 25.5
}
```

**status 上报格式：**

```json
{
  "status": "online"
}
```

---

## 10. 接口汇总

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 账号密码登录 |
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/send-code` | 发送短信验证码 |
| POST | `/api/auth/login-by-phone` | 手机验证码登录 |
| GET | `/api/auth/me` | 获取当前用户信息 |
| GET | `/api/devices` | 获取设备列表 |
| GET | `/api/devices/stats` | 设备统计 |
| POST | `/api/devices` | 创建设备 |
| GET | `/api/devices/{deviceId}` | 获取单个设备 |
| PUT | `/api/devices/{deviceId}` | 更新设备 |
| DELETE | `/api/devices/{deviceId}` | 删除设备 |
| PATCH | `/api/devices/{deviceId}/status` | 更新设备状态 |
| GET | `/api/data/{deviceId}` | 查询设备数据 |
| GET | `/api/data/{deviceId}/history` | 查询历史聚合数据 |
| POST | `/api/data/{deviceId}` | 写入数据点 |
| GET | `/api/data/{deviceId}/latest` | 查询最新数据 |
| POST | `/api/data/{deviceId}/command` | 下发执行器指令 |
| GET | `/api/alerts/rules` | 告警规则列表 |
| POST | `/api/alerts/rules` | 创建告警规则 |
| GET | `/api/alerts/rules/{id}` | 获取单条规则 |
| PUT | `/api/alerts/rules/{id}` | 更新告警规则 |
| DELETE | `/api/alerts/rules/{id}` | 删除告警规则 |
| PATCH | `/api/alerts/rules/{id}/toggle` | 启用/禁用规则 |
| GET | `/api/alerts/records` | 告警记录列表（分页） |
| GET | `/api/alerts/records/{id}` | 获取单条告警记录 |
| DELETE | `/api/alerts/records/{id}` | 删除告警记录 |
| PATCH | `/api/alerts/records/{id}/acknowledge` | 确认告警 |
| PATCH | `/api/alerts/records/{id}/resolve` | 解决告警 |
| GET | `/api/alerts/stats` | 告警统计 |
| GET | `/api/settings` | 获取系统设置 |
| PUT | `/api/settings` | 更新系统设置 |
| POST | `/api/simulation/upload/start` | 启动数据上报模拟 |
| POST | `/api/simulation/upload/stop` | 停止数据上报模拟 |
| POST | `/api/simulation/upload/once` | 手动触发一轮上报 |
| POST | `/api/simulation/delivery/start` | 启动指令下发模拟 |
| POST | `/api/simulation/delivery/stop` | 停止指令下发模拟 |
| POST | `/api/simulation/delivery/once` | 手动触发一轮下发 |
| POST | `/api/simulation/stop` | 停止所有模拟 |
| GET | `/api/simulation/status` | 查询模拟状态 |
| GET | `/api/simulation/commands` | 查询指令日志 |
| GET | `/api/health` | 健康检查 |
| GET | `/api/health/ready` | 就绪检查 |
