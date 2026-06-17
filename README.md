# IoT Cloud Platform 物联网云平台

一个完整的前后端分离物联网云平台，支持设备数据采集、实时监控、告警管理和远程控制。

## 技术栈

### 前端
| 技术 | 版本 | 说明 |
|---|---|---|
| Vue | 3.5+ | Composition API + `<script setup>` |
| TypeScript | 6.0 | 类型安全 |
| Vite | 8.0 | 构建工具 |
| Element Plus | 2.14+ | UI 组件库 |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 4.6 | 路由 |   
| ECharts | 6.0 | 图表 |
| Axios | 1.16 | HTTP 客户端 |

### 后端
| 技术 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 3.4 | Java 框架 |
| JPA / Hibernate | — | ORM |
| PostgreSQL | — | 关系型数据库 |
| Redis | — | 实时缓存、设备状态 |
| Kafka | 3.9 | 高并发消息缓冲 |
| TDengine | 3.3 | 时序数据库 |
| JWT | 0.12 | 身份认证 |
| HikariCP | — | 数据库连接池 |

## 项目结构

```
test/
├── vite-project/          # 前端 (Vue 3 + Vite + Element Plus)
│   └── src/
│       ├── api/           # API 接口层 (realApi.ts + mockApi.ts)
│       ├── layouts/       # 布局组件 (MainLayout.vue)
│       ├── router/        # 路由配置
│       ├── stores/        # Pinia 状态管理 (device, auth, theme)
│       └── views/         # 页面组件
│           ├── auth/      # 登录/注册
│           ├── dashboard/ # 数据大屏
│           ├── devices/   # 设备管理、设备详情
│           ├── docs/      # API 接口文档
│           ├── monitor/   # 实时监控、数据中心、实时数据
│           └── user/      # 个人中心、系统设置
│
├── iot-backend/           # 后端 (Spring Boot)
│   └── src/main/java/com/iot/
│       ├── config/        # 安全、JWT、Kafka、TDengine、Redis 配置
│       ├── controller/    # REST 控制器
│       ├── dto/           # 请求/响应 DTO
│       ├── model/         # JPA 实体
│       ├── repository/    # JPA Repository
│       └── service/       # 业务逻辑层
│
├── test1.py               # 光照传感器模拟脚本
├── test2.py               # 温度数据查询脚本
└── test3.py               # 执行器控制脚本
```

## 功能模块

### 1. 用户认证
- JWT Token 认证（登录 / 注册）
- 密码加密存储（BCrypt）
- 设备 API Key 认证（设备端免登）
- 请求拦截器自动续 Token
- 多用户数据隔离

### 2. 数据大屏 (Dashboard)
- 设备总数 / 在线 / 离线 / 告警 统计卡片
- 最近告警实时列表（15秒轮询）
- 设备状态图表（ECharts）

### 3. 设备管理
- 设备 CRUD（创建 / 编辑 / 删除）
- 传感器和执行器配置
- 设备状态实时展示（5秒自动刷新）
- 一键上下线切换
- 传感器数据面板 + 执行器控制面板
- API Key 查看与复制

### 4. 实时数据采集
- REST API 数据上报（`POST /api/data/{deviceId}`）
- 传感器值实时更新
- 数据自动写入三层存储（TDengine → Redis → PostgreSQL）
- Kafka 消息缓冲（异步、高并发）

### 5. 命令下发
- 执行器远程控制（ON / OFF / Toggle）
- 指令发送 → 状态回写 → 前端实时更新
- 指令日志记录

### 6. 告警引擎
- 自定义告警规则（阈值、条件表达式）
- 实时数据评估告警
- Redis 防抖（避免重复告警）
- 告警记录确认 / 解决
- 铃铛通知弹窗 + Dashboard 告警列表
- 按用户隔离告警数据

### 7. 数据查询
- 最新 N 条数据查询
- 时间范围查询（from / to）
- 聚合查询（降采样：5m / 1h / 1d）
- 传感器数据缓存加速

### 8. 实时监控
- 设备实时状态面板
- 传感器数据图表
- 执行器控制面板

### 9. 接口文档
- 在线 API 文档页面（`/docs`）
- 快速测试示例（curl + Python）
- 完整参数表格（路径 / Query / Body）
- 请求响应示例 + 一键复制
- 通用错误码说明

### 10. 暗色模式
- 一键切换亮色 / 暗色主题
- 0.3s 平滑过渡动画
- Element Plus 组件全面适配
- 滚动条暗色适配

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- PostgreSQL 15+
- Redis 7+（可选，关闭不影响基本使用）
- Kafka（可选，关闭不影响基本使用）
- TDengine（可选，关闭后自动降级到 PostgreSQL）

### 1. 启动数据库

```bash
# PostgreSQL
# 确保 PostgreSQL 运行在 localhost:5432
# 数据库: iotdb  用户: postgres  密码: 123456

# Redis (可选)
redis-server
```

### 2. 启动后端

```bash
cd iot-backend
mvn spring-boot:run

# 使用开发模式（H2 内存数据库 + 关闭外部依赖）
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

后端启动后访问 `http://localhost:8080`，健康检查：`http://localhost:8080/actuator/health`

### 3. 启动前端

```bash
cd vite-project
npm install
npm run dev
```

前端启动后访问 `http://localhost:5173`

### 4. 运行设备模拟脚本

```bash
# Python 环境：pip install requests

# 光照传感器数据模拟
python test1.py

# 温度数据查询
python test2.py

# 执行器控制模拟
python test3.py
```

## API 接口一览

平台提供在线接口文档（登录后访问 `/docs`），以下为概览：

### 数据上报

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/data/{deviceId}` | X-Api-Key | 传感器数据上报 |
| POST | `/api/data/{deviceId}/command` | X-Api-Key | 执行器命令下发 |

### 数据查询

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/api/data/{deviceId}/latest` | X-Api-Key | 最新 N 条数据 |
| GET | `/api/data/{deviceId}` | X-Api-Key | 时间范围查询 |
| GET | `/api/data/{deviceId}/history` | X-Api-Key | 聚合降采样查询 |

### 设备管理

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/api/devices` | Bearer Token | 获取设备列表 |
| GET | `/api/devices/{deviceId}` | X-Api-Key | 获取设备详情 |
| POST | `/api/devices` | Bearer Token | 创建设备 |
| PUT | `/api/devices/{deviceId}` | Bearer Token | 更新设备 |
| DELETE | `/api/devices/{deviceId}` | Bearer Token | 删除设备 |
| PATCH | `/api/devices/{deviceId}/status` | Bearer Token | 切换设备状态 |

### 告警

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/api/alerts/records` | Bearer Token | 告警记录查询 |
| GET | `/api/alerts/rules` | Bearer Token | 告警规则列表 |
| POST | `/api/alerts/rules` | Bearer Token | 创建告警规则 |
| PATCH | `/api/alerts/records/{id}/acknowledge` | Bearer Token | 确认告警 |
| PATCH | `/api/alerts/records/{id}/resolve` | Bearer Token | 解决告警 |

### 认证方式

- **X-Api-Key**：设备端 / 脚本端推荐。在设备详情页获取。
- **Bearer Token**：Web 前端使用。通过 `POST /api/auth/login` 获取。

## 数据流架构

```
设备端 (Python / MCU / 任意 HTTP 客户端)
  │  POST /api/data/{deviceId}
  │  Header: X-Api-Key: {API Key}
  ▼
DataController → DataService
                   ├── TDengine (时序存储，不可用时降级)
                   ├── Kafka (异步消息缓冲)
                   ├── Redis (实时缓存 + 设备影子 + 告警防抖)
                   ├── PostgreSQL (兜底存储)
                   └── AlertService (告警评估引擎)
                        │
                        ▼
                   铃铛通知 / Dashboard / 邮件 / 短信
```

## 配置说明

### application.yml 核心开关

```yaml
app:
  kafka:
    enabled: false     # Kafka 开关
  redis:
    enabled: true      # Redis 开关
  tdengine:
    enabled: false     # TDengine 开关
```

开发环境下建议关闭 Kafka 和 TDengine（数据自动降级写入 PostgreSQL），只开 Redis 用于设备状态缓存。

### CORS / 公网部署

前端 `vite.config.ts` 中配置代理，生产环境可将前端构建产物放入 Spring Boot 的 `static` 目录，实现单端口部署。

## 许可证

MIT License
