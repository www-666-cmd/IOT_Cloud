<script setup lang="ts">
import { ref, computed } from 'vue'
import { Upload, Download, Key, Promotion, Connection, SwitchButton, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

interface ParamDef { name: string; type: string; required?: boolean; desc: string }
interface ApiDef {
  method: string; path: string; title: string; desc: string; auth: string; id: string
  example: string; response: string
  pathParams?: { name: string; type: string; desc: string }[]
  queryParams?: ParamDef[]
  bodyParams?: ParamDef[]
  headers?: { name: string; desc: string; required: boolean }[]
}

const activeTab = ref('quick')
const copiedId = ref('')
const BASE_URL = 'http://127.0.0.1:8080'
const activeSection = computed(() => apiSections.find(s => s.id === activeTab.value))
const apis = computed<ApiDef[]>(() => activeSection.value?.apis || [])

function copyCode(text: string, id?: string) {
  navigator.clipboard.writeText(text)
  if (id) {
    copiedId.value = id
    setTimeout(() => copiedId.value = '', 1500)
  }
  ElMessage.success({ message: '已复制', duration: 1200 })
}

const quickTests = [
  {
    title: '上传光照数据',
    icon: Upload,
    lang: 'curl',
    code: `curl -X POST "${BASE_URL}/api/data/dev_b087404c" \\
  -H "X-Api-Key: b1c432fb86d8477a96170ce13a220c5e" \\
  -H "Content-Type: application/json" \\
  -d '{"sensorId": "s_1781530543162", "value": 55.5}'`,
    id: 'quick-upload'
  },
  {
    title: '查询最新数据',
    icon: Download,
    lang: 'curl',
    code: `curl -H "X-Api-Key: b1c432fb86d8477a96170ce13a220c5e" \\
  "${BASE_URL}/api/data/dev_b087404c/latest?sensorId=s_1781530543162&limit=5"`,
    id: 'quick-query'
  },
  {
    title: '查询设备详情',
    icon: Document,
    lang: 'curl',
    code: `curl -H "X-Api-Key: b1c432fb86d8477a96170ce13a220c5e" \\
  "${BASE_URL}/api/devices/dev_b087404c"`,
    id: 'quick-device'
  },
  {
    title: '发送执行器指令',
    icon: SwitchButton,
    lang: 'curl',
    code: `curl -X POST "${BASE_URL}/api/data/dev_414305e8/command" \\
  -H "X-Api-Key: f37ff332325d47b7b261e3fb22a949c6" \\
  -H "Content-Type: application/json" \\
  -d '{"command": "1", "params": {"actuator": "电动开关"}}'`,
    id: 'quick-command'
  },
  {
    title: 'Python 上传示例',
    icon: Promotion,
    lang: 'python',
    code: `import requests

resp = requests.post(
    "${BASE_URL}/api/data/dev_b087404c",
    json={"sensorId": "s_1781530543162", "value": 55.5},
    headers={"X-Api-Key": "b1c432fb86d8477a96170ce13a220c5e"},
    timeout=5
)
print(resp.json())`,
    id: 'quick-python'
  }
]

const apiSections = [
  {
    id: 'upload',
    title: '数据上报',
    icon: Upload,
    desc: '设备向平台发送传感器数据',
    apis: [
      {
        method: 'POST', path: '/api/data/{deviceId}',
        title: '上报传感器数据',
        desc: '向指定设备上报传感器读数。后端自动写入时序数据库、更新实时缓存、触发告警评估。',
        auth: 'X-Api-Key',
        pathParams: [{ name: 'deviceId', type: 'string', desc: '设备 ID（如 dev_b087404c）' }],
        bodyParams: [
          { name: 'sensorId', type: 'string', required: true, desc: '传感器 ID（如 s_1781530543162）' },
          { name: 'value', type: 'number', required: true, desc: '传感器读数值' }
        ] as ParamDef[],
        headers: [
          { name: 'X-Api-Key', desc: '设备 API Key（从设备详情页获取）', required: true },
          { name: 'Content-Type', desc: 'application/json', required: true }
        ],
        example: `curl -X POST "${BASE_URL}/api/data/dev_b087404c" \\
  -H "X-Api-Key: b1c432fb86d8477a96170ce13a220c5e" \\
  -H "Content-Type: application/json" \\
  -d '{"sensorId": "s_1781530543162", "value": 55.5}'`,
        response: `{
  "success": true,
  "data": {
    "deviceId": "dev_b087404c",
    "sensorId": "s_1781530543162",
    "value": 55.5,
    "timestamp": "2026-06-16T10:30:00"
  }
}`,
        id: 'api-upload'
      },
      {
        method: 'POST', path: '/api/data/{deviceId}/command',
        title: '下发控制指令',
        desc: '向执行器发送控制命令（1=ON, 0=OFF, toggle=翻转）。后端自动更新执行器状态。',
        auth: 'X-Api-Key',
        pathParams: [{ name: 'deviceId', type: 'string', desc: '设备 ID' }],
        bodyParams: [
          { name: 'command', type: 'string', required: true, desc: '指令: "1"/"0"/"on"/"off"/"toggle"' },
          { name: 'params.actuator', type: 'string', required: false, desc: '执行器名称（与设备页一致）' }
        ] as ParamDef[],
        example: `curl -X POST "${BASE_URL}/api/data/dev_414305e8/command" \\
  -H "X-Api-Key: f37ff332325d47b7b261e3fb22a949c6" \\
  -H "Content-Type: application/json" \\
  -d '{"command": "1", "params": {"actuator": "电动开关"}}'`,
        response: `{
  "success": true,
  "data": {
    "message": "命令 [1] → 电动开关 已ON",
    "deviceId": "dev_414305e8",
    "command": "1"
  }
}`,
        id: 'api-command'
      }
    ]
  },
  {
    id: 'query',
    title: '数据查询',
    icon: Download,
    desc: '从平台查询已上报的传感器数据',
    apis: [
      {
        method: 'GET', path: '/api/data/{deviceId}/latest',
        title: '获取最新数据',
        desc: '返回指定传感器最新 N 条读数，按时间倒序。适合轮询获取实时值。',
        auth: 'X-Api-Key',
        pathParams: [{ name: 'deviceId', type: 'string', desc: '设备 ID' }],
        queryParams: [
          { name: 'sensorId', type: 'string', required: false, desc: '传感器 ID（不填返回全部传感器）' },
          { name: 'limit', type: 'int', required: false, desc: '返回条数（默认 10）' }
        ] as ParamDef[],
        example: `curl -H "X-Api-Key: b1c432fb86d8477a96170ce13a220c5e" \\
  "${BASE_URL}/api/data/dev_b087404c/latest?sensorId=s_1781530543162&limit=5"`,
        response: `{
  "success": true,
  "data": [
    { "value": 55.5, "sensorId": "s_1781530543162", "timestamp": "2026-06-16T10:30:00" },
    { "value": 54.2, "sensorId": "s_1781530543162", "timestamp": "2026-06-16T10:29:57" }
  ]
}`,
        id: 'api-latest'
      },
      {
        method: 'GET', path: '/api/data/{deviceId}',
        title: '时间范围查询',
        desc: '按起止时间查询历史数据，返回完整 DataPoint 对象（含 id、ownerId 等字段）。',
        auth: 'X-Api-Key',
        pathParams: [{ name: 'deviceId', type: 'string', desc: '设备 ID' }],
        queryParams: [
          { name: 'sensorId', type: 'string', required: false, desc: '传感器 ID' },
          { name: 'from', type: 'ISO datetime', required: false, desc: '起始时间（如 2026-06-16T00:00:00）' },
          { name: 'to', type: 'ISO datetime', required: false, desc: '结束时间' },
          { name: 'limit', type: 'int', required: false, desc: '返回条数（默认 200）' }
        ] as ParamDef[],
        example: `curl -H "X-Api-Key: b1c432fb86d8477a96170ce13a220c5e" \\
  "${BASE_URL}/api/data/dev_b087404c?sensorId=s_1781530543162&from=2026-06-16T00:00:00&to=2026-06-16T12:00:00&limit=100"`,
        response: `{
  "success": true,
  "data": [
    { "id": 123, "deviceId": "dev_b087404c", "sensorId": "s_1781530543162",
      "value": 55.5, "timestamp": "2026-06-16T10:30:00" }
  ]
}`,
        id: 'api-range'
      },
      {
        method: 'GET', path: '/api/data/{deviceId}/history',
        title: '聚合查询（降采样）',
        desc: '按时间间隔聚合，返回 avg/max/min。用于生成趋势图。',
        auth: 'X-Api-Key',
        pathParams: [{ name: 'deviceId', type: 'string', desc: '设备 ID' }],
        queryParams: [
          { name: 'sensorId', type: 'string', required: false, desc: '传感器 ID' },
          { name: 'from', type: 'ISO datetime', required: true, desc: '起始时间' },
          { name: 'to', type: 'ISO datetime', required: true, desc: '结束时间' },
          { name: 'interval', type: 'string', required: false, desc: '聚合间隔: 5m / 1h / 1d' }
        ] as ParamDef[],
        example: `curl -H "X-Api-Key: b1c432fb86d8477a96170ce13a220c5e" \\
  "${BASE_URL}/api/data/dev_b087404c/history?from=2026-06-16T00:00:00&to=2026-06-16T12:00:00&interval=5m"`,
        response: `{
  "success": true,
  "data": [
    { "ts": "2026-06-16T10:00:00", "avg_val": 24.5, "max_val": 26.1, "min_val": 23.8 },
    { "ts": "2026-06-16T10:05:00", "avg_val": 25.1, "max_val": 26.3, "min_val": 24.2 }
  ]
}`,
        id: 'api-history'
      }
    ]
  },
  {
    id: 'device',
    title: '设备信息',
    icon: Document,
    desc: '设备管理相关的查询接口',
    apis: [
      {
        method: 'GET', path: '/api/devices',
        title: '获取所有设备',
        desc: '返回当前用户的所有设备列表（含传感器实时值）。',
        auth: 'Bearer Token',
        example: `curl -H "Authorization: Bearer {token}" \\
  "${BASE_URL}/api/devices"`,
        response: `{
  "success": true,
  "data": [{
    "deviceId": "dev_b087404c",
    "name": "光照传感器",
    "status": "ONLINE",
    "sensors": [{ "id": "s_1781530543162", "name": "光照传感器", "value": 55.5 }],
    "apiKey": "b1c432fb..."
  }]
}`,
        id: 'api-devices'
      },
      {
        method: 'GET', path: '/api/devices/{deviceId}',
        title: '获取设备详情',
        desc: '返回单个设备的完整信息，含 API Key、传感器列表和实时值。',
        auth: 'X-Api-Key 或 Bearer Token',
        pathParams: [{ name: 'deviceId', type: 'string', desc: '设备 ID' }],
        example: `curl -H "X-Api-Key: b1c432fb86d8477a96170ce13a220c5e" \\
  "${BASE_URL}/api/devices/dev_b087404c"`,
        response: `{
  "success": true,
  "data": {
    "deviceId": "dev_b087404c",
    "name": "光照传感器", "type": "光照 (Light)",
    "status": "ONLINE", "apiKey": "b1c432fb86d...",
    "sensors": [{
      "id": "s_1781530543162", "name": "光照传感器",
      "type": "light", "value": 55.5, "minVal": 0, "maxVal": 100
    }],
    "lastActive": "2026-06-16T10:30:00"
  }
}`,
        id: 'api-device-detail'
      }
    ]
  }
]
</script>

<template>
  <div class="api-docs">
    <!-- Header -->
    <div class="page-header">
      <div>
        <h2>数据接口文档</h2>
        <span class="header-desc">设备数据上报、查询与命令下发 REST API 参考</span>
      </div>
    </div>

    <!-- 快速测试 -->
    <el-card class="quick-card" shadow="never">
      <template #header>
        <div class="card-title">
          <el-icon :size="18" color="var(--accent)"><Promotion /></el-icon>
          <span>快速测试</span>
          <el-tag size="small" type="success" effect="dark" class="quick-badge">直接复制运行</el-tag>
        </div>
      </template>
      <div class="quick-list">
        <div v-for="test in quickTests" :key="test.id" class="quick-item">
          <div class="quick-item-header">
            <el-icon :size="14" color="var(--accent)"><component :is="test.icon" /></el-icon>
            <span class="quick-item-title">{{ test.title }}</span>
            <el-tag size="small" effect="plain" class="quick-lang">{{ test.lang }}</el-tag>
          </div>
          <div class="quick-code-wrapper">
            <pre class="quick-code"><code>{{ test.code }}</code></pre>
            <el-button
              size="small"
              :type="copiedId === test.id ? 'success' : 'default'"
              class="quick-copy-btn"
              @click="copyCode(test.code, test.id)"
            >
              {{ copiedId === test.id ? '已复制 ✓' : '复制' }}
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 认证说明 -->
    <el-card class="auth-card" shadow="never">
      <template #header>
        <div class="card-title">
          <el-icon :size="18" color="var(--accent)"><Key /></el-icon>
          <span>认证方式</span>
        </div>
      </template>
      <el-row :gutter="24">
        <el-col :span="12">
          <div class="auth-box">
            <div class="auth-box-header">
              <el-tag type="success" effect="dark" size="small">推荐</el-tag>
              <span>X-Api-Key（设备 Key）</span>
            </div>
            <code class="code-inline">-H "X-Api-Key: {apiKey}"</code>
            <p class="auth-box-desc">在设备详情页可查看复制。适用于设备/脚本端直接调用。</p>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="auth-box">
            <div class="auth-box-header">
              <span>Bearer Token（用户登录）</span>
            </div>
            <code class="code-inline">-H "Authorization: Bearer {token}"</code>
            <p class="auth-box-desc">通过 POST /api/auth/login 获取。适用于 Web 前端调用。</p>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 接口详情 -->
    <div class="api-section">
      <div class="section-tabs">
        <el-radio-group v-model="activeTab" size="small">
          <el-radio-button value="quick"><el-icon :size="13"><Promotion /></el-icon> 快速测试</el-radio-button>
          <el-radio-button value="upload"><el-icon :size="13"><Upload /></el-icon> 数据上报</el-radio-button>
          <el-radio-button value="query"><el-icon :size="13"><Download /></el-icon> 数据查询</el-radio-button>
          <el-radio-button value="device"><el-icon :size="13"><Document /></el-icon> 设备信息</el-radio-button>
        </el-radio-group>
      </div>

      <div v-if="activeSection" :key="activeSection.id" class="section-body">
        <div class="section-header">
          <el-icon :size="20" color="var(--accent)"><component :is="activeSection.icon" /></el-icon>
          <div>
            <h3 class="section-title">{{ activeSection.title }}</h3>
            <p class="section-sub">{{ activeSection.desc }}</p>
          </div>
        </div>

        <el-card
          v-for="api in apis"
          :key="api.id"
          class="api-card"
          shadow="never"
        >
            <!-- 接口概要 -->
            <div class="api-card-top">
              <div class="api-method-row">
                <el-tag :type="api.method === 'POST' ? 'success' : 'primary'" size="small" effect="dark">
                  {{ api.method }}
                </el-tag>
                <code class="api-path-display">{{ api.path }}</code>
                <el-tag size="small" effect="plain" type="info">{{ api.auth }}</el-tag>
              </div>
              <h4 class="api-card-title">{{ api.title }}</h4>
              <p class="api-card-desc">{{ api.desc }}</p>
            </div>

            <!-- 路径参数 -->
            <div v-if="api.pathParams" class="api-table-block">
              <span class="api-section-label">路径参数</span>
              <table class="param-table">
                <thead>
                  <tr><th>参数</th><th>类型</th><th>说明</th></tr>
                </thead>
                <tbody>
                  <tr v-for="p in api.pathParams" :key="p.name">
                    <td><code>{{ p.name }}</code></td>
                    <td><el-tag size="small" effect="plain">{{ p.type }}</el-tag></td>
                    <td>{{ p.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Query 参数 -->
            <div v-if="api.queryParams" class="api-table-block">
              <span class="api-section-label">Query 参数</span>
              <table class="param-table">
                <thead>
                  <tr><th>参数</th><th>类型</th><th>必填</th><th>说明</th></tr>
                </thead>
                <tbody>
                  <tr v-for="p in api.queryParams" :key="p.name">
                    <td><code>{{ p.name }}</code></td>
                    <td><el-tag size="small" effect="plain">{{ p.type }}</el-tag></td>
                    <td><el-tag :type="p.required ? 'danger' : 'info'" size="small">{{ p.required ? '是' : '否' }}</el-tag></td>
                    <td>{{ p.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Body 参数 -->
            <div v-if="api.bodyParams" class="api-table-block">
              <span class="api-section-label">Body 参数 (JSON)</span>
              <table class="param-table">
                <thead>
                  <tr><th>参数</th><th>类型</th><th>必填</th><th>说明</th></tr>
                </thead>
                <tbody>
                  <tr v-for="p in api.bodyParams" :key="p.name">
                    <td><code>{{ p.name }}</code></td>
                    <td><el-tag size="small" effect="plain">{{ p.type }}</el-tag></td>
                    <td><el-tag :type="p.required ? 'danger' : 'info'" size="small">{{ p.required ? '是' : '否' }}</el-tag></td>
                    <td>{{ p.desc }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- 示例请求 -->
            <div class="code-section">
              <div class="code-section-header">
                <el-icon :size="14"><Connection /></el-icon>
                <span>示例请求</span>
                <el-button size="small" text type="primary" @click="copyCode(api.example, api.id)">复制</el-button>
              </div>
              <pre class="code-block"><code>{{ api.example }}</code></pre>
            </div>

            <!-- 响应示例 -->
            <div class="code-section">
              <div class="code-section-header">
                <span>响应示例</span>
                <el-button size="small" text type="primary" @click="copyCode(api.response, api.id + '-resp')">复制</el-button>
              </div>
              <pre class="code-block"><code>{{ api.response }}</code></pre>
            </div>
          </el-card>
        </div>
      </div>

    <!-- 错误码 -->
    <el-card class="error-card" shadow="never">
      <template #header>
        <div class="card-title">
          <el-icon :size="18" color="var(--warning)"><Key /></el-icon>
          <span>通用错误码</span>
        </div>
      </template>
      <table class="param-table">
        <thead>
          <tr><th>状态码</th><th>含义</th></tr>
        </thead>
        <tbody>
          <tr><td><el-tag type="success" size="small">200</el-tag></td><td>成功</td></tr>
          <tr><td><el-tag type="danger" size="small">401</el-tag></td><td>认证失败 — API Key 无效或 Token 过期</td></tr>
          <tr><td><el-tag type="danger" size="small">404</el-tag></td><td>设备/传感器不存在</td></tr>
          <tr><td><el-tag type="danger" size="small">500</el-tag></td><td>服务器内部错误</td></tr>
        </tbody>
      </table>
    </el-card>
  </div>
</template>

<style scoped>
.api-docs { max-width: 960px; margin: 0 auto; }

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0 0 4px; font-size: 22px; color: var(--text-primary); }
.header-desc { font-size: 13px; color: var(--text-muted); }

/* Card title */
.card-title { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 15px; color: var(--text-primary); }
.quick-badge { margin-left: 8px; }

/* Quick test */
.quick-card, .auth-card, .error-card { margin-bottom: 20px; }

.quick-list { display: flex; flex-direction: column; gap: 14px; }

.quick-item { border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; }
.quick-item-header { display: flex; align-items: center; gap: 8px; padding: 10px 14px; background: var(--bg-hover); border-bottom: 1px solid var(--border-color); }
.quick-item-title { font-size: 14px; font-weight: 500; color: var(--text-primary); }
.quick-lang { margin-left: auto; }

.quick-code-wrapper { position: relative; }
.quick-code { padding: 14px 16px; margin: 0; font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 12.5px; line-height: 1.6; color: var(--text-primary); background: var(--bg-card); overflow-x: auto; white-space: pre; }
.quick-copy-btn { position: absolute; top: 8px; right: 8px; }

/* Auth */
.auth-box { border: 1px solid var(--border-color); border-radius: 8px; padding: 14px; }
.auth-box-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; font-size: 14px; font-weight: 500; color: var(--text-primary); }
.auth-box-desc { margin: 8px 0 0; font-size: 12px; color: var(--text-muted); }

.code-inline { font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 13px; color: var(--accent); background: var(--bg-hover); padding: 3px 10px; border-radius: 4px; word-break: break-all; }

/* API sections */
.api-section { margin-bottom: 20px; }

.section-tabs { margin-bottom: 16px; }

.section-header { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 16px; padding-bottom: 14px; border-bottom: 1px solid var(--border-color); }
.section-title { margin: 0; font-size: 18px; color: var(--text-primary); }
.section-sub { margin: 4px 0 0; font-size: 13px; color: var(--text-muted); }

.api-cards, .section-body { display: flex; flex-direction: column; gap: 14px; }

.api-card { border: 1px solid var(--border-color); transition: border-color 0.2s; }
.api-card:hover { border-color: var(--accent); }

.api-card-top { margin-bottom: 14px; padding-bottom: 12px; border-bottom: 1px solid var(--border-light); }
.api-method-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.api-path-display { font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 14px; font-weight: 600; color: var(--text-primary); }
.api-card-title { margin: 0 0 4px; font-size: 15px; color: var(--text-primary); }
.api-card-desc { margin: 0; font-size: 13px; color: var(--text-muted); line-height: 1.5; }

/* Param tables */
.api-table-block { margin-bottom: 14px; }
.api-section-label { display: block; margin-bottom: 6px; font-size: 12px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.5px; }

.param-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.param-table th { text-align: left; padding: 7px 10px; background: var(--bg-hover); color: var(--text-secondary); font-weight: 600; font-size: 12px; border-bottom: 1px solid var(--border-color); }
.param-table td { padding: 7px 10px; color: var(--text-primary); border-bottom: 1px solid var(--border-light); }
.param-table code { font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 12.5px; color: var(--accent); }

/* Code blocks */
.code-section { margin-bottom: 12px; }
.code-section-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; font-size: 12px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.5px; }
.code-section-header .el-button { margin-left: auto; }

.code-block { background: var(--bg-hover); border: 1px solid var(--border-color); border-radius: 6px; padding: 14px 16px; margin: 0; font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 12.5px; line-height: 1.6; color: var(--text-primary); overflow-x: auto; white-space: pre; }
</style>
