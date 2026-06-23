# IoT Cloud Platform 物联网云平台

基于 **Spring Boot + EMQX + Kafka + Redis + PostgreSQL + TDengine** 技术栈的前后端分离物联网云平台，支持设备数据采集、实时监控、告警管理和远程控制。

## 技术栈

### 前端
| 技术 | 版本 | 说明 |
|---|---|---|
| Vue | 3.5+ | Composition API + `<script setup>` |
| TypeScript | 6.0 | 类型安全 |
| Vite | 8.0 | 构建工具 + 开发服务器 |
| Element Plus | 2.14+ | UI 组件库 |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 4.6 | 路由 |
| ECharts | 6.0 | 图表 |
| Axios | 1.16 | HTTP 客户端 |
| STOMP.js | — | WebSocket 实时推送客户端 |

### 后端
| 技术 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 3.4 | Java 框架 |
| JPA / Hibernate | — | ORM |
| PostgreSQL | — | 关系型数据库（业务数据 + 兜底存储） |
| Redis | — | 实时缓存、设备状态、告警防抖 |
| Kafka | 3.9 | 高并发异步消息缓冲 |
| TDengine | 3.3 | 时序数据库 |
| EMQX | 5.x | MQTT Broker（企业级，替代 Mosquitto） |
| Eclipse Paho | 1.2 | MQTT 客户端 |
| JWT | 0.12 | 身份认证 |
| STOMP WebSocket | — | 服务端实时数据推送 |
| HikariCP | — | 数据库连接池 |

## 项目结构

```
test/
├── vite-project/          # 前端 (Vue 3 + Vite + Element Plus)
│   └── src/
│       ├── api/           # API 接口层 (realApi.ts)
│       ├── layouts/       # 布局组件 (MainLayout.vue)
│       ├── router/        # 路由配置
│       ├── stores/        # Pinia 状态管理 (device, auth, theme, websocket)
│       └── views/         # 页面组件
│           ├── auth/      # 登录 / 注册
│           ├── dashboard/ # 数据大屏
│           ├── devices/   # 设备管理、设备详情
│           ├── docs/      # API 接口文档
│           ├── monitor/   # 实时监控、数据中心、实时数据
│           └── user/      # 个人中心、系统设置
│
├── iot-backend/           # 后端 (Spring Boot)
│   └── src/main/java/com/iot/
│       ├── config/        # Security, JWT, Kafka, TDengine, Redis, MQTT, WebSocket
│       ├── controller/    # REST 控制器
│       ├── dto/           # 请求 / 响应 DTO
│       ├── model/         # JPA 实体
│       ├── repository/    # JPA Repository
│       └── service/       # 业务逻辑层
│
├── test_code/             # Python 设备模拟脚本
│   ├── test1.py           # 光照传感器 HTTP 上传
│   ├── test2.py           # 超声波数据查询
│   ├── test3.py           # 执行器控制
│   └── test4.py           # MQTT 设备端示例
└── README.md
```

## 功能模块

### 1. 用户认证
- JWT Token 认证（登录 / 注册 / 密码修改）
- 设备 API Key 认证（设备端免登，X-Api-Key Header）
- 请求拦截器自动续 Token / 401 跳转登录
- 多用户数据隔离（设备、告警按 ownerId 分离）

### 2. 数据大屏 (Dashboard)
- 设备统计卡片（总数 / 在线 / 离线 / 告警）
- 最近告警实时列表（从后端拉取，15s 轮询）
- 设备状态图表（ECharts）

### 3. 设备管理
- 设备 CRUD（创建 / 编辑 / 删除），传感器 + 执行器配置
- 传感器数据面板 + 执行器控制面板（同屏双栏）
- 自定义传感器 / 执行器 ID（留空自动生成）
- 设备状态实时更新（WebSocket 推送 + 5s 轮询兜底）
- 一键上下线切换（列表内快速切换 + 底部确认切换）
- API Key 查看与一键复制
- 传感器 ID 卡片展示 + 复制
- 设备详情自动加载（切换设备时 watch 路由参数）

### 4. 实时数据采集
- **HTTP REST API** — `POST /api/data/{deviceId}` + X-Api-Key
- **MQTT** — `iot/{deviceId}/telemetry` 主题发布
- **WebSocket 实时推送** — 服务端写入数据后主动推送到所有前端客户端
- 数据自动写入三层存储（TDengine → Redis → PostgreSQL）
- TDengine 不可用时自动降级到 PostgreSQL
- Kafka 高并发异步缓冲（Producer → Consumer → 完整数据管线）

### 5. 命令下发
- 执行器远程控制（ON / OFF / Toggle）
- 指令发送 → 后端更新执行器状态 → 前端实时反馈
- 发送内容列显示 + 指令时间记录
- 指令日志记录与查询

### 6. 告警引擎
- 自定义告警规则（阈值、条件表达式，如 `value > 80`）
- 实时数据评估告警（传感器类型自动补全）
- Redis 防抖（SETNX，30s 窗口内不重复触发）
- 告警记录确认 / 解决
- 铃铛通知弹窗（Header 右上角，15s 轮询）
- Dashboard 告警列表（按用户隔离）
- WebSocket 推送告警通知

### 7. 数据查询
- 最新 N 条数据查询：`/api/data/{deviceId}/latest`
- 时间范围查询（from / to）：`/api/data/{deviceId}`
- 聚合降采样查询（5m / 1h / 1d）：`/api/data/{deviceId}/history`
- 三级缓存查询（Redis → TDengine → PostgreSQL）

### 8. 实时监控
- 设备实时状态面板 + ECharts 动态图表
- 传感器数据实时曲线（最多 40 点滚动窗口）
- 执行器控制面板

### 9. 接口文档
- 在线 API 文档页面（`/docs`）
- 快速测试示例（curl + Python，含真实设备 ID）
- 完整参数表格（路径 / Query / Body）
- 请求 / 响应示例 + 一键复制
- 通用错误码说明（200 / 401 / 404 / 500）

### 10. 暗色模式
- 一键切换亮色 / 暗色主题（localStorage 持久化）
- 自定义 CSS 变量适配（传感器 / 执行器 / 状态色）
- Element Plus 组件全面适配（表格 / 卡片 / 弹窗 / 输入框）
- 滚动条暗色适配

### 11. 性能优化
- KeepAlive 页面缓存（最多 8 个组件）
- WebSocket 毫秒级推送替代轮询
- GPU 加速侧边栏 + 页面过渡（will-change / transform / contain）
- 设备列表静默刷新（仅更新变化字段，避免整表重渲染）
- 暗色主题过渡仅命中关键容器（非全局 wildcard）

### 12. 用户中心
- 个人信息编辑（用户名 / 邮箱 / 手机号）
- 密码修改
- 头像上传（点击头像 → 选本地图片 → base64 预览）

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- Python 3.9+（模拟脚本）
- PostgreSQL 15+
- Docker（EMQX / Redis / Kafka / TDengine）

### 1. 启动基础服务

```bash
# PostgreSQL（必需）
# 数据库: iotdb  用户: postgres  密码: 123456

# Redis（推荐）
docker run -d --name redis -p 6379:6379 redis:7-alpine

# EMQX MQTT Broker（可选）
docker run -d --name emqx \
  -p 1883:1883 -p 8083:8083 -p 18083:18083 \
  emqx/emqx:latest

# Kafka（可选）
# 启动 Zookeeper + Kafka Server
cd d:/kafka_2.12-3.9.2
bin/windows/zookeeper-server-start.bat config/zookeeper.properties
bin/windows/kafka-server-start.bat config/server.properties

# TDengine（可选）
docker run -d --name tdengine -p 6030:6030 -p 6041:6041 tdengine/tdengine:3.3.3.0
```

### 2. 启动后端

```bash
cd iot-backend
mvn spring-boot:run

# 开发模式（H2 内存数据库 + 关闭外部依赖）
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

健康检查：`http://localhost:8080/actuator/health`

### 3. 启动前端

```bash
cd vite-project
npm install
npm run dev
```

访问：`http://localhost:5173`

### 4. 运行设备模拟脚本

```bash
pip install requests paho-mqtt

# HTTP 上传 + 查询
python test_code/test1.py    # 光照传感器
python test_code/test2.py    # 超声波数据查询

# 执行器控制（HTTP）
python test_code/test3.py

# MQTT 设备端示例
python test_code/test4.py
```

## 数据流架构

```
设备端 (Python / MCU / ESP32 / 任意 HTTP 客户端)
  │
  ├── HTTP POST /api/data/{deviceId}    ──┐
  └── MQTT iot/{deviceId}/telemetry    ──┤
                                          ▼
                                   DataController / MqttMessageHandler
                                          │
                                          ▼
                                     DataService
                                    writeDataPoint()
                    ┌─────────────────────┼─────────────────────┐
                    ▼                     ▼                     ▼
                TDengine               Kafka                PostgreSQL
            (时序存储主库)        (异步消息缓冲)           (兜底存储)
                    │                     │
                    │              KafkaConsumerService
                    │               → DataService.fromKafka()
                    │                     │
                    └─────────────────────┼─────────────────────┘
                                          │
                              ┌───────────┼───────────┐
                              ▼           ▼           ▼
                            Redis    WebSocket    AlertService
                         (实时缓存)  (前端推送)   (告警评估引擎)
                              │           │           │
                              ▼           ▼           ▼
                       设备状态缓存   浏览器实时  铃铛通知 / Dashboard
                       告警防抖       显示数据   邮件 / 推送
```

## 数据传输协议

| 协议 | 用途 | 端口 |
|---|---|---|
| HTTP/HTTPS | 通用数据上报、查询、设备管理 | 8080 |
| MQTT | 低功耗设备数据上报（QoS 1） | 1883 |
| MQTT WebSocket | 浏览器端 MQTT 直连 | 8083 |
| WebSocket (STOMP) | 服务端 → 前端实时推送 | 8080 (/ws) |
| Kafka | 高并发异步缓冲 | 9092 |

## API 接口一览

在线接口文档：登录后访问 `/docs`

### 认证方式

| 方式 | Header | 来源 |
|---|---|---|
| API Key | `X-Api-Key: {32位Key}` | 设备详情页复制 |
| Bearer Token | `Authorization: Bearer {token}` | `POST /api/auth/login` |

### 数据上报

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/data/{deviceId}` | 传感器数据上报 |
| POST | `/api/data/{deviceId}/command` | 执行器命令下发 |

### 数据查询

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/data/{deviceId}/latest` | 最新 N 条 |
| GET | `/api/data/{deviceId}` | 时间范围查询 |
| GET | `/api/data/{deviceId}/history` | 聚合降采样 |

### 设备管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/api/devices` | 列表 / 创建 |
| GET/PUT/DELETE | `/api/devices/{deviceId}` | 详情 / 更新 / 删除 |
| PATCH | `/api/devices/{deviceId}/status` | 切换上下线 |

### 告警

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/api/alerts/rules` | 规则列表 / 创建 |
| GET | `/api/alerts/records` | 告警记录查询 |
| PATCH | `/api/alerts/records/{id}/acknowledge` | 确认 |
| PATCH | `/api/alerts/records/{id}/resolve` | 解决 |

## 配置说明

### application.yml 核心开关

```yaml
app:
  kafka:
    enabled: false     # Kafka 开关
  mqtt:
    enabled: false     # MQTT 开关（EMQX）
  redis:
    enabled: true      # Redis 开关
  tdengine:
    enabled: false     # TDengine 开关
```

- 开发环境：只开 Redis，其余关闭。数据走 HTTP → PostgreSQL → Redis 路径。
- 生产环境：全部开启，数据走 HTTP/MQTT → Kafka → TDengine → Redis 全链路。

### 公网部署

```bash
# cpolar 隧道（开发测试）
cpolar http 8080    # 后端
cpolar http 5173    # 前端（Vite dev）
```

生产部署：前端 `npm run build` 构建产物放入 Spring Boot `static/` 目录，单端口（8080）同时服务前后端。

### EMQX Dashboard

```
地址: http://localhost:18083
账号: admin  密码: public
```

可查看实时设备连接数、消息吞吐量、Topic 列表。

## 许可证

MIT License
