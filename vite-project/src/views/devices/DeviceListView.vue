<script setup lang="ts">
defineOptions({ name: 'DeviceList' })
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useDeviceStore } from '../../stores/device'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Edit, Delete, View, Upload, Cpu, DataLine, VideoPlay, VideoPause,
  Odometer, Switch, Refresh, Document, SwitchButton
} from '@element-plus/icons-vue'
import { api } from '../../api'

const router = useRouter()
const deviceStore = useDeviceStore()

const searchQuery = ref('')
const statusFilter = ref('')
const dialogVisible = ref(false)
const sensorDialogVisible = ref(false)
const actuatorDialogVisible = ref(false)
const isEdit = ref(false)
const currentDeviceId = ref('')
const simRunning = ref(false)
const simInterval = ref(5)
const refreshing = ref(false)
const commandLogs = ref<any[]>([])
const commandLogVisible = ref(false)
const commandLogLoading = ref(false)
const lastActuatorCmd = ref<Record<string, { cmd: string; time: string; ok: boolean }>>({})

const deviceForm = ref({
  name: '',
  type: '',
  location: '',
  status: 'offline' as 'online' | 'offline' | 'warning',
  description: '',
  sensors: [] as any[],
  actuators: [] as any[]
})

const sensorForm = ref({
  id: '',
  name: '',
  type: '',
  dataType: 'float',
  unit: '',
  min: 0,
  max: 100
})

const actuatorForm = ref({
  id: '',
  name: '',
  type: '',
  commandType: 'toggle',
  defaultValue: 'off',
  parameters: ''
})


const sensorTypes = [
  { label: '温度 (Temperature)', value: 'temperature' },
  { label: '湿度 (Humidity)', value: 'humidity' },
  { label: '光照 (Light)', value: 'light' },
  { label: 'PM2.5', value: 'pm25' },
  { label: 'CO₂', value: 'co2' },
  { label: 'TVOC', value: 'tvoc' },
  { label: '电压 (Voltage)', value: 'voltage' },
  { label: '电流 (Current)', value: 'current' },
  { label: '功率 (Power)', value: 'power' },
  { label: '烟雾 (Smoke)', value: 'smoke' },
  { label: '水浸 (Water)', value: 'water' },
  { label: '气压 (Pressure)', value: 'pressure' },
  { label: '风速 (Wind Speed)', value: 'windspeed' },
  { label: '噪声 (Noise)', value: 'noise' }
]

const dataTypes = [
  { label: '浮点型 (Float)', value: 'float' },
  { label: '整型 (Integer)', value: 'integer' },
  { label: '布尔型 (Boolean)', value: 'boolean' },
  { label: '字符串 (String)', value: 'string' }
]

const actuatorTypes = [
  { label: '开关 (Switch)', value: 'switch' },
  { label: '电机 (Motor)', value: 'motor' },
  { label: '阀门 (Valve)', value: 'valve' },
  { label: '继电器 (Relay)', value: 'relay' },
  { label: '蜂鸣器 (Buzzer)', value: 'buzzer' },
  { label: 'LED灯 (LED)', value: 'led' },
  { label: '风扇 (Fan)', value: 'fan' },
  { label: '泵 (Pump)', value: 'pump' }
]

const commandTypes = [
  { label: '开关切换 (Toggle)', value: 'toggle' },
  { label: '开启 (Turn On)', value: 'on' },
  { label: '关闭 (Turn Off)', value: 'off' },
  { label: '设置数值 (Set Value)', value: 'setValue' },
  { label: '脉冲触发 (Pulse)', value: 'pulse' }
]

const filteredDevices = computed(() => {
  let result = deviceStore.devices
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(d => d.name.toLowerCase().includes(q) || d.type.toLowerCase().includes(q))
  }
  if (statusFilter.value) {
    result = result.filter(d => d.status === statusFilter.value)
  }
  return result
})

const allSensorRows = computed(() => {
  const rows: any[] = []
  for (const device of filteredDevices.value) {
    for (const sensor of (device.sensors || []).filter((s: any) => !s.isActuator)) {
      rows.push({ device, sensor })
    }
  }
  return rows
})

const allActuatorRows = computed(() => {
  const rows: any[] = []
  for (const device of filteredDevices.value) {
    for (const actuator of (device.actuators || [])) {
      rows.push({ device, actuator })
    }
  }
  return rows
})

function getActuatorStatusTag(value: string) {
  if (value === 'on') return 'success'
  if (value === 'auto') return 'warning'
  return 'info'
}

function getActuatorStatusText(value: string) {
  if (value === 'on') return '已开启'
  if (value === 'auto') return '自动'
  return '已关闭'
}

async function sendActuatorCommand(deviceId: string, actuator: any, command: string) {
  const key = `${deviceId}:${actuator.name}`
  try {
    const result = await deviceStore.sendCommand(deviceId, command, { actuator: actuator.name })
    lastActuatorCmd.value[key] = {
      cmd: command,
      time: new Date().toLocaleTimeString(),
      ok: true
    }
    ElMessage.success({
      message: `[${actuator.name}] ${command} → ${result.message || 'ok'}`,
      duration: 2000
    })
    await deviceStore.fetchDevices()
  } catch (e: any) {
    lastActuatorCmd.value[key] = {
      cmd: command,
      time: new Date().toLocaleTimeString(),
      ok: false
    }
    ElMessage.error(e.message || '指令发送失败')
  }
}

function getActuatorLastCmd(deviceId: string, name: string) {
  return lastActuatorCmd.value[`${deviceId}:${name}`]
}

function getActuatorLiveStatus(actuator: any) {
  // Use the actual sensor value if available, otherwise fall back to defaultValue
  if (actuator.value !== undefined && actuator.value !== null) {
    return actuator.value === 1 || actuator.value === 'on' ? 'on' : 'off'
  }
  return actuator.defaultValue
}

function showAddDialog() {
  isEdit.value = false
  currentDeviceId.value = ''
  deviceForm.value = {
    name: '',
    type: '',
    location: '',
    status: 'offline',
    description: '',
    sensors: [],
    actuators: []
  }
  dialogVisible.value = true
}

function showEditDialog(device: any) {
  isEdit.value = true
  currentDeviceId.value = device.id
  deviceForm.value = {
    name: device.name,
    type: device.type,
    location: device.location,
    status: device.status,
    description: device.description || '',
    sensors: [...(device.sensors || [])].filter((s: any) => !s.isActuator),
    actuators: [...(device.actuators || [])]
  }
  dialogVisible.value = true
}

function openSensorDialog() {
  sensorForm.value = { id: '', name: '', type: '', dataType: 'float', unit: '', min: 0, max: 100 }
  sensorDialogVisible.value = true
}

function addSensor() {
  if (!sensorForm.value.name || !sensorForm.value.type) {
    ElMessage.warning('请填写传感器名称和类型')
    return
  }
  deviceForm.value.sensors.push({
    id: sensorForm.value.id || 's_' + Date.now(),
    name: sensorForm.value.name,
    type: sensorForm.value.type,
    dataType: sensorForm.value.dataType,
    unit: sensorForm.value.unit,
    value: 0,
    min: sensorForm.value.min,
    max: sensorForm.value.max,
    isActuator: false
  })
  sensorDialogVisible.value = false
}

function removeSensor(index: number) {
  deviceForm.value.sensors.splice(index, 1)
}

function openActuatorDialog() {
  actuatorForm.value = { id: '', name: '', type: '', commandType: 'toggle', defaultValue: 'off', parameters: '' }
  actuatorDialogVisible.value = true
}

function addActuator() {
  if (!actuatorForm.value.name || !actuatorForm.value.type) {
    ElMessage.warning('请填写执行器名称和类型')
    return
  }
  deviceForm.value.actuators.push({
    id: actuatorForm.value.id || 'a_' + Date.now(),
    name: actuatorForm.value.name,
    type: actuatorForm.value.type,
    commandType: actuatorForm.value.commandType,
    defaultValue: actuatorForm.value.defaultValue,
    parameters: actuatorForm.value.parameters,
    isActuator: true
  })
  actuatorDialogVisible.value = false
}

function removeActuator(index: number) {
  deviceForm.value.actuators.splice(index, 1)
}

function getSensorTypeLabel(type: string) {
  return sensorTypes.find(t => t.value === type)?.label || type
}

const sensorTypeColorMap: Record<string, string> = {
  temperature: 'danger',
  humidity: 'primary',
  light: 'warning',
  pm25: '',
  co2: 'success',
  tvoc: 'info',
  voltage: 'danger',
  current: 'warning',
  power: 'success',
  smoke: 'danger',
  water: 'primary',
  pressure: 'info',
  windspeed: 'success',
  noise: 'warning'
}

function getSensorTagType(type: string) {
  return sensorTypeColorMap[type] || 'info'
}

function getActuatorTypeLabel(type: string) {
  return actuatorTypes.find(t => t.value === type)?.label || type
}

function getDataTypeLabel(type: string) {
  return dataTypes.find(t => t.value === type)?.label || type
}

function getCommandTypeLabel(type: string) {
  return commandTypes.find(t => t.value === type)?.label || type
}

async function saveDevice() {
  try {
    // 自动生成设备名称：首个传感器名称 或 "新设备"
    const firstName = deviceForm.value.sensors[0]?.name || deviceForm.value.actuators[0]?.name
    const deviceName = deviceForm.value.name || firstName || '新设备'
    // 从传感器类型自动推导设备类型
    const sensorType = deviceForm.value.sensors[0]?.type
    const deviceType = sensorType ? getSensorTypeLabel(sensorType) : '通用设备'
    const payload = {
      ...deviceForm.value,
      name: deviceName,
      type: deviceType,
      sensors: [
        ...deviceForm.value.sensors,
        ...deviceForm.value.actuators.map((a: any) => ({ ...a, value: 0, min: 0, max: 1, unit: '', dataType: 'boolean' }))
      ]
    }
    if (isEdit.value) {
      await deviceStore.updateDevice(currentDeviceId.value, payload)
      ElMessage.success('设备更新成功')
    } else {
      await deviceStore.createDevice(payload)
      ElMessage.success('设备添加成功')
    }
    dialogVisible.value = false
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

async function handleDelete(device: any) {
  try {
    await ElMessageBox.confirm(
      `确定要删除设备 "${device.name}" 吗？此操作不可恢复。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deviceStore.deleteDevice(device.id)
    ElMessage.success('设备已删除')
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

function getSelectedDevice() {
  return deviceStore.devices.find(d => d.id === currentDeviceId.value)
}

function viewDetail(device: any) {
  router.push(`/devices/${device.id}`)
}

async function handleTriggerUpload() {
  try {
    await api.triggerUploadOnce()
    ElMessage.success('已触发一次模拟数据上发，请刷新查看')
    await deviceStore.fetchDevices()
  } catch (e: any) {
    ElMessage.error(e.message || '触发失败')
  }
}

async function handleTriggerDelivery() {
  try {
    await api.triggerDeliveryOnce()
    ElMessage.success('已触发一次模拟命令下报')
  } catch (e: any) {
    ElMessage.error(e.message || '触发失败')
  }
}

async function toggleSimulation() {
  try {
    if (simRunning.value) {
      await api.stopDataUpload()
      await api.stopCommandDelivery()
      simRunning.value = false
      ElMessage.success('模拟已停止')
    } else {
      await api.startDataUpload(simInterval.value)
      await api.startCommandDelivery(simInterval.value)
      simRunning.value = true
      ElMessage.success(`模拟运行中，数据上报间隔: ${simInterval.value}s`)
    }
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function checkSimStatus() {
  try {
    const status = await api.getSimulationStatus()
    simRunning.value = status.dataUploadRunning || status.commandDeliveryRunning
  } catch { /* ignore */ }
}

// ===== 快捷操作 =====

async function refreshStatus() {
  refreshing.value = true
  try {
    await deviceStore.fetchDevices()
    ElMessage.success('设备状态已刷新')
  } catch (e: any) {
    ElMessage.error(e.message || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

async function quickToggleStatus(device: any) {
  if (!device) return
  const newStatus = device.status === 'online' ? 'offline' : 'online'
  const label = newStatus === 'online' ? '上线' : '离线'
  try {
    await api.updateDeviceStatus(device.id, newStatus)
    await deviceStore.fetchDevices()
    ElMessage.success(`${device.name} 已${label}`)
  } catch (e: any) {
    ElMessage.error(e.message || '切换失败')
  }
}

async function toggleDeviceStatus(device: any) {
  if (!device) return
  const newStatus = device.status === 'online' ? 'offline' : 'online'
  const label = newStatus === 'online' ? '上线' : '离线'
  try {
    await ElMessageBox.confirm(`确定将 "${device.name}" 切换为${label}状态吗？`, '切换状态', {
      confirmButtonText: `切换为${label}`,
      cancelButtonText: '取消',
      type: 'warning'
    })
    await api.updateDeviceStatus(device.id, newStatus)
    await deviceStore.fetchDevices()
    ElMessage.success(`${device.name} 已${label}`)
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '切换失败')
  }
}

async function loadCommandLogs(deviceId?: string) {
  commandLogLoading.value = true
  commandLogVisible.value = true
  try {
    commandLogs.value = await api.getCommandLogs(deviceId)
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
    commandLogs.value = []
  } finally {
    commandLogLoading.value = false
  }
}

let listPollTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  deviceStore.fetchDevices()
  checkSimStatus()
  listPollTimer = setInterval(() => deviceStore.silentRefreshDevices(), 3000)
})

onUnmounted(() => {
  if (listPollTimer) clearInterval(listPollTimer)
})
</script>

<template>
  <div class="device-list">
    <div class="page-header">
      <h2>设备管理</h2>
      <el-button type="primary" :icon="Plus" @click="showAddDialog">添加设备</el-button>
    </div>

    <div class="simulation-bar">
      <div class="sim-bar-left">
        <el-icon :size="18"><Cpu /></el-icon>
        <span class="sim-label">模拟测试</span>
        <el-input-number
          v-model="simInterval"
          :min="1"
          :max="60"
          size="small"
          style="width: 100px"
          :disabled="simRunning"
        />
        <span class="sim-unit">秒间隔</span>
      </div>
      <div class="sim-bar-right">
        <el-button
          :type="simRunning ? 'danger' : 'success'"
          size="small"
          @click="toggleSimulation"
        >
          <el-icon><component :is="simRunning ? VideoPause : VideoPlay" /></el-icon>
          {{ simRunning ? '停止模拟' : '开始自动模拟' }}
        </el-button>
        <el-button size="small" type="primary" plain @click="handleTriggerUpload">
          <el-icon><Upload /></el-icon> 数据上发
        </el-button>
        <el-button size="small" type="warning" plain @click="handleTriggerDelivery">
          <el-icon><DataLine /></el-icon> 命令下报
        </el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索设备名称或类型"
        :prefix-icon="Search"
        clearable
        style="width: 250px"
      />
      <el-select v-model="statusFilter" placeholder="设备状态" clearable style="width: 150px">
        <el-option label="在线" value="online" />
        <el-option label="离线" value="offline" />
        <el-option label="告警" value="warning" />
      </el-select>
      <div class="filter-right">
        <span class="device-count">共 {{ filteredDevices.length }} 台设备</span>
      </div>
    </div>

    <el-row :gutter="16" class="two-panel-row">
      <!-- Left: Sensor Data Panel -->
      <el-col :span="12">
        <div class="data-panel sensor-data-panel">
          <div class="panel-top-bar">
            <div class="panel-top-left">
              <div class="panel-icon-box sensor-icon-box">
                <el-icon :size="18"><Odometer /></el-icon>
              </div>
              <div>
                <div class="panel-top-title">传感器数据</div>
                <div class="panel-top-sub">Sensor Data</div>
              </div>
              <el-tag size="small" type="info" round effect="plain">{{ allSensorRows.length }}</el-tag>
            </div>
            <div class="panel-top-right">
              <el-icon :size="16" class="panel-dot-icon" color="var(--sensor-color)"><Odometer /></el-icon>
            </div>
          </div>

          <div class="panel-table-wrap" v-loading="deviceStore.loading">
            <el-table :data="allSensorRows" style="width: 100%" size="small" class="data-table" v-if="allSensorRows.length > 0">
              <el-table-column label="设备名称" min-width="130">
                <template #default="{ row }">
                  <div class="cell-device">
                    <span class="cell-device-status" :class="row.device.status" />
                    <span class="cell-device-name">{{ row.device.name }}</span>
                    <el-tooltip :content="row.device.status === 'online' ? '点击下线' : '点击上线'" placement="top">
                      <el-button
                        size="small"
                        :type="row.device.status === 'online' ? 'success' : 'info'"
                        link
                        class="quick-toggle-btn"
                        @click.stop="quickToggleStatus(row.device)"
                      >
                        <el-icon :size="14"><SwitchButton /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="传感器" min-width="90">
                <template #default="{ row }">
                  <span class="cell-mono">{{ row.sensor.name }}</span>
                </template>
              </el-table-column>
              <el-table-column label="类型" min-width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="getSensorTagType(row.sensor.type)" round effect="plain">
                    {{ getSensorTypeLabel(row.sensor.type) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="当前值" min-width="110" align="right">
                <template #default="{ row }">
                  <span :class="['cell-value', { 'cell-value-warn': row.sensor.value > row.sensor.max * 0.9 }]">
                    {{ row.sensor.value.toFixed(2) }}
                  </span>
                  <span class="cell-unit">{{ row.sensor.unit }}</span>
                </template>
              </el-table-column>
              <el-table-column label="范围" min-width="100" align="right">
                <template #default="{ row }">
                  <span class="cell-range">{{ row.sensor.min }} ~ {{ row.sensor.max }} {{ row.sensor.unit }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.sensor.value > row.sensor.max * 0.9 ? 'danger' : 'success'" size="small" round effect="dark">
                    {{ row.sensor.value > row.sensor.max * 0.9 ? '超限' : '正常' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="panel-empty-table">
              <el-icon :size="32" color="var(--text-muted)"><Odometer /></el-icon>
              <p>暂无传感器数据</p>
            </div>
          </div>
        </div>
      </el-col>

      <!-- Right: Actuator Control Panel -->
      <el-col :span="12">
        <div class="data-panel actuator-data-panel">
          <div class="panel-top-bar">
            <div class="panel-top-left">
              <div class="panel-icon-box actuator-icon-box">
                <el-icon :size="18"><Switch /></el-icon>
              </div>
              <div>
                <div class="panel-top-title">执行器控制</div>
                <div class="panel-top-sub">Actuator Control</div>
              </div>
              <el-tag size="small" type="warning" round effect="plain">{{ allActuatorRows.length }}</el-tag>
            </div>
            <div class="panel-top-right">
              <el-icon :size="16" class="panel-dot-icon" color="var(--actuator-color)"><Switch /></el-icon>
            </div>
          </div>

          <div class="panel-table-wrap" v-loading="deviceStore.loading">
            <el-table :data="allActuatorRows" style="width: 100%" size="small" class="data-table" v-if="allActuatorRows.length > 0">
              <el-table-column label="设备名称" min-width="130">
                <template #default="{ row }">
                  <div class="cell-device">
                    <span class="cell-device-status" :class="row.device.status" />
                    <span class="cell-device-name">{{ row.device.name }}</span>
                    <el-tooltip :content="row.device.status === 'online' ? '点击下线' : '点击上线'" placement="top">
                      <el-button
                        size="small"
                        :type="row.device.status === 'online' ? 'success' : 'info'"
                        link
                        class="quick-toggle-btn"
                        @click.stop="quickToggleStatus(row.device)"
                      >
                        <el-icon :size="14"><SwitchButton /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="执行器" min-width="80">
                <template #default="{ row }">
                  <span class="cell-mono">{{ row.actuator.name }}</span>
                </template>
              </el-table-column>
              <el-table-column label="类型" min-width="80">
                <template #default="{ row }">
                  <el-tag size="small" type="warning" round effect="plain">{{ getActuatorTypeLabel(row.actuator.type) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="发送内容" min-width="150" align="center">
                <template #default="{ row }">
                  <div class="cmd-display">
                    <!-- 从后端读取的真实状态 -->
                    <el-tag
                      :type="getActuatorLiveStatus(row.actuator) === 'on' ? 'success' : 'info'"
                      size="small" effect="dark"
                    >
                      {{ getActuatorLiveStatus(row.actuator) === 'on' ? 'ON' : 'OFF' }}
                    </el-tag>
                    <!-- 上次指令（来自页面点击或后端刷新） -->
                    <span v-if="getActuatorLastCmd(row.device.id, row.actuator.name)" class="cmd-time">
                      ↑ {{ getActuatorLastCmd(row.device.id, row.actuator.name)?.cmd }} {{ getActuatorLastCmd(row.device.id, row.actuator.name)?.time }}
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="操作" min-width="120" align="center">
                <template #default="{ row }">
                  <div class="actuator-btn-group">
                    <el-button
                      size="small"
                      :type="getActuatorLiveStatus(row.actuator) === 'on' ? 'success' : 'default'"
                      :disabled="row.device.status !== 'online'"
                      @click="sendActuatorCommand(row.device.id, row.actuator, 'on')"
                    >
                      ON
                    </el-button>
                    <el-button
                      size="small"
                      :type="getActuatorLiveStatus(row.actuator) === 'off' ? 'danger' : 'default'"
                      :disabled="row.device.status !== 'online'"
                      @click="sendActuatorCommand(row.device.id, row.actuator, 'off')"
                    >
                      OFF
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="panel-empty-table">
              <el-icon :size="32" color="var(--text-muted)"><Switch /></el-icon>
              <p>暂无执行器配置</p>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 快捷操作 -->
    <div class="device-actions-bar">
      <div class="actions-left">
        <el-select v-model="currentDeviceId" placeholder="选择设备" clearable style="width: 200px" size="small">
          <el-option v-for="d in deviceStore.devices" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <el-button size="small" :icon="Refresh" :loading="refreshing" @click="refreshStatus">刷新状态</el-button>
        <el-button size="small" type="warning" :icon="Switch" :disabled="!currentDeviceId" @click="toggleDeviceStatus(getSelectedDevice())">切换状态</el-button>
        <el-button size="small" :icon="Document" @click="loadCommandLogs(currentDeviceId || undefined)">命令日志</el-button>
      </div>
      <div class="actions-center">
        <el-button size="small" type="primary" :icon="Edit" :disabled="!currentDeviceId" @click="showEditDialog(getSelectedDevice())">编辑</el-button>
        <el-button size="small" :icon="View" :disabled="!currentDeviceId" @click="viewDetail(getSelectedDevice())">详情</el-button>
        <el-button size="small" type="danger" :icon="Delete" :disabled="!currentDeviceId" @click="handleDelete(getSelectedDevice())">删除</el-button>
      </div>
    </div>

    <!-- 命令日志 Dialog -->
    <el-dialog v-model="commandLogVisible" title="命令日志" width="700px" destroy-on-close>
      <el-table :data="commandLogs" v-loading="commandLogLoading" max-height="400" size="small">
        <el-table-column prop="deviceId" label="设备ID" width="120" />
        <el-table-column prop="command" label="指令" width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'EXECUTED' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="response" label="响应" min-width="180">
          <template #default="{ row }">
            <span style="font-size:12px;color:var(--text-muted)">{{ row.response || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发送时间" width="160">
          <template #default="{ row }">
            {{ row.sentAt ? new Date(row.sentAt).toLocaleString() : '-' }}
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!commandLogLoading && commandLogs.length === 0" style="text-align:center;padding:40px;color:var(--text-muted)">
        暂无命令日志
      </div>
    </el-dialog>

    <!-- Device Add/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑设备' : '添加设备'" width="900px" destroy-on-close>

      <el-row :gutter="20">
        <!-- Sensors Section -->
        <el-col :span="12">
          <div class="config-panel sensor-panel">
            <div class="panel-header">
              <div class="panel-title">
                <el-icon :size="20" color="var(--sensor-color)"><Odometer /></el-icon>
                <span>数据采集 — 传感器</span>
                <el-tag size="small" type="info" round>{{ deviceForm.sensors.length }}</el-tag>
              </div>
              <el-button size="small" type="primary" plain @click="openSensorDialog">
                <el-icon><Plus /></el-icon> 添加
              </el-button>
            </div>
            <div class="panel-body">
              <div v-if="deviceForm.sensors.length === 0" class="panel-empty">
                <el-icon :size="32" color="var(--text-muted)"><Odometer /></el-icon>
                <p>暂无传感器，点击添加</p>
              </div>
              <div v-for="(s, i) in deviceForm.sensors" :key="s.id" class="config-item">
                <div class="config-item-left">
                  <div class="config-item-icon sensor-icon">
                    <el-icon :size="16"><Odometer /></el-icon>
                  </div>
                  <div class="config-item-info">
                    <div class="config-item-name">{{ s.name }}</div>
                    <div class="config-item-meta">
                      {{ getSensorTypeLabel(s.type) }} · {{ getDataTypeLabel(s.dataType) }} · {{ s.unit || '无单位' }}
                    </div>
                    <div class="config-item-range">范围: {{ s.min }} ~ {{ s.max }} {{ s.unit }}</div>
                  </div>
                </div>
                <el-button link type="danger" :icon="Delete" @click="removeSensor(i)" />
              </div>
            </div>
          </div>
        </el-col>

        <!-- Actuators Section -->
        <el-col :span="12">
          <div class="config-panel actuator-panel">
            <div class="panel-header">
              <div class="panel-title">
                <el-icon :size="20" color="var(--actuator-color)"><Switch /></el-icon>
                <span>执行控制 — 执行器</span>
                <el-tag size="small" type="warning" round>{{ deviceForm.actuators.length }}</el-tag>
              </div>
              <el-button size="small" type="warning" plain @click="openActuatorDialog">
                <el-icon><Plus /></el-icon> 添加
              </el-button>
            </div>
            <div class="panel-body">
              <div v-if="deviceForm.actuators.length === 0" class="panel-empty">
                <el-icon :size="32" color="var(--text-muted)"><Switch /></el-icon>
                <p>暂无执行器，点击添加</p>
              </div>
              <div v-for="(a, i) in deviceForm.actuators" :key="a.id" class="config-item">
                <div class="config-item-left">
                  <div class="config-item-icon actuator-icon">
                    <el-icon :size="16"><Switch /></el-icon>
                  </div>
                  <div class="config-item-info">
                    <div class="config-item-name">{{ a.name }}</div>
                    <div class="config-item-meta">
                      {{ getActuatorTypeLabel(a.type) }} · {{ getCommandTypeLabel(a.commandType) }}
                    </div>
                    <div class="config-item-range">默认: {{ a.defaultValue }}</div>
                  </div>
                </div>
                <el-button link type="danger" :icon="Delete" @click="removeActuator(i)" />
              </div>
            </div>
          </div>
        </el-col>
      </el-row>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDevice">保存</el-button>
      </template>
    </el-dialog>

    <!-- Sensor Add Dialog -->
    <el-dialog v-model="sensorDialogVisible" title="添加传感器" width="480px" destroy-on-close>
      <div class="sub-dialog-header">
        <div class="sub-dialog-icon sensor-dialog-icon">
          <el-icon :size="28"><Odometer /></el-icon>
        </div>
        <span class="sub-dialog-desc">配置数据采集传感器参数</span>
      </div>
      <el-form :model="sensorForm" label-width="80px" class="sensor-form-dialog">
        <el-form-item label="传感器 ID">
          <el-input v-model="sensorForm.id" placeholder="留空自动生成 (如 my-temp-sensor)" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="sensorForm.name" placeholder="温度传感器" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" required>
              <el-select v-model="sensorForm.type" placeholder="选择类型">
                <el-option v-for="t in sensorTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="数据类型">
              <el-select v-model="sensorForm.dataType" placeholder="选择">
                <el-option v-for="t in dataTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="sensorForm.unit" placeholder="°C、%、lux" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="最小值">
              <el-input-number v-model="sensorForm.min" :precision="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大值">
              <el-input-number v-model="sensorForm.max" :precision="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="sensorDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="addSensor">确认添加</el-button>
      </template>
    </el-dialog>

    <!-- Actuator Add Dialog -->
    <el-dialog v-model="actuatorDialogVisible" title="添加执行器" width="480px" destroy-on-close>
      <div class="sub-dialog-header">
        <div class="sub-dialog-icon actuator-dialog-icon">
          <el-icon :size="28"><Switch /></el-icon>
        </div>
        <span class="sub-dialog-desc">配置执行控制参数与指令类型</span>
      </div>
      <el-form :model="actuatorForm" label-width="80px" class="actuator-form-dialog">
        <el-form-item label="执行器 ID">
          <el-input v-model="actuatorForm.id" placeholder="留空自动生成 (如 my-switch-01)" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="actuatorForm.name" placeholder="主开关" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" required>
              <el-select v-model="actuatorForm.type" placeholder="选择类型">
                <el-option v-for="t in actuatorTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="指令类型">
              <el-select v-model="actuatorForm.commandType" placeholder="选择">
                <el-option v-for="t in commandTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认状态">
              <el-radio-group v-model="actuatorForm.defaultValue" size="small">
                <el-radio-button value="on">开</el-radio-button>
                <el-radio-button value="off">关</el-radio-button>
                <el-radio-button value="auto">自动</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="参数">
          <el-input v-model="actuatorForm.parameters" type="textarea" rows="2" placeholder='可选，{"timeout":5000}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actuatorDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="addActuator">确认添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 22px;
  color: var(--text-primary);
}

.simulation-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 16px;
}

.sim-bar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
}

.sim-label {
  font-weight: 600;
  font-size: 14px;
}

.sim-unit {
  font-size: 12px;
  color: var(--text-muted);
}

.sim-bar-right {
  display: flex;
  gap: 8px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 16px;
}

.filter-right {
  margin-left: auto;
}

.device-count {
  font-size: 13px;
  color: var(--text-muted);
}

/* Two Panel Layout */
.two-panel-row {
  margin-bottom: 16px;
}

.data-panel {
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  height: 100%;
}

.sensor-data-panel {
  border-top: 3px solid var(--sensor-color);
}

.actuator-data-panel {
  border-top: 3px solid var(--actuator-color);
}

.panel-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-light);
}

.panel-top-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-icon-box {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sensor-icon-box {
  background: var(--sensor-bg);
  color: var(--sensor-color);
}

.actuator-icon-box {
  background: var(--actuator-bg);
  color: var(--actuator-color);
}

.panel-top-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.panel-top-sub {
  font-size: 10px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.panel-top-right {
  opacity: 0.5;
}

.panel-table-wrap {
  background: var(--bg-card);
  min-height: 320px;
}

.data-table :deep(.el-table__header th) {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.panel-empty-table {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 50px 0;
  color: var(--text-muted);
  gap: 8px;
}

.panel-empty-table p {
  margin: 0;
  font-size: 13px;
}

/* Cell styles */
.cell-device {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cell-device-status {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.cell-device-status.online {
  background: var(--success);
  box-shadow: 0 0 6px rgba(52,211,153,0.4);
}

.cell-device-status.offline {
  background: var(--text-muted);
}

.cell-device-status.warning {
  background: var(--actuator-color);
  box-shadow: 0 0 6px rgba(251,191,36,0.4);
}

.cell-device-name {
  font-weight: 500;
  color: var(--text-primary);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.quick-toggle-btn {
  flex-shrink: 0;
  padding: 2px;
  margin-left: 2px;
  opacity: 0.5;
  transition: opacity 0.15s;
}

.cell-device:hover .quick-toggle-btn {
  opacity: 1;
}

.cell-mono {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
}

.cell-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.cell-value-warn {
  color: var(--danger);
}

.cell-unit {
  font-size: 11px;
  color: var(--text-muted);
  margin-left: 2px;
}

.cell-range {
  font-size: 12px;
  color: var(--text-muted);
}

.actuator-btn-group {
  display: flex;
  gap: 4px;
  justify-content: center;
}

.actuator-btn-group .el-button {
  padding: 3px 10px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  min-width: 40px;
}

.actuator-btn-group .el-button[type="success"] {
  box-shadow: 0 0 6px rgba(52,211,153,0.3);
}

.actuator-btn-group .el-button[type="danger"] {
  box-shadow: 0 0 6px rgba(248,113,113,0.3);
}

.actuator-last-cmd {
  margin-top: 4px;
  font-size: 10px;
  line-height: 1;
}

.cmd-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.cmd-time {
  font-size: 10px;
  color: var(--text-muted);
}

.cmd-none {
  font-size: 13px;
  color: var(--text-muted);
}

/* Bottom actions bar */
.device-actions-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.actions-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.actions-center {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Config Panels (dialog) */
.config-panel {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  overflow: hidden;
  min-height: 280px;
}

.sensor-panel {
  border-top: 3px solid var(--sensor-color);
}

.actuator-panel {
  border-top: 3px solid var(--actuator-color);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-light);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.panel-body {
  padding: 8px;
  max-height: 340px;
  overflow-y: auto;
}

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: var(--text-muted);
  gap: 8px;
}

.panel-empty p {
  margin: 0;
  font-size: 13px;
}

.config-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--bg-card);
  border-radius: 8px;
  margin-bottom: 6px;
  border: 1px solid var(--border-light);
  transition: all 0.2s;
}

.config-item:hover {
  border-color: var(--accent);
  box-shadow: 0 2px 8px var(--accent-glow);
}

.config-item-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.config-item-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sensor-icon {
  background: var(--sensor-bg);
  color: var(--sensor-color);
}

.actuator-icon {
  background: var(--actuator-bg);
  color: var(--actuator-color);
}

.config-item-info {
  flex: 1;
  min-width: 0;
}

.config-item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.config-item-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.config-item-range {
  font-size: 11px;
  color: var(--text-muted);
  opacity: 0.7;
  margin-top: 1px;
}

/* Sub-dialog styles */
.sub-dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-light);
}

.sub-dialog-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sensor-dialog-icon {
  background: var(--sensor-gradient);
  color: var(--sensor-color);
}

.actuator-dialog-icon {
  background: var(--actuator-gradient);
  color: var(--actuator-color);
}

.sub-dialog-desc {
  font-size: 14px;
  color: var(--text-secondary);
}

/* Responsive: narrow screen stacks panels vertically */
@media (max-width: 1200px) {
  .two-panel-row .el-col {
    max-width: 100%;
    flex: 0 0 100%;
    margin-bottom: 16px;
  }

  .data-panel {
    height: auto;
  }

  .panel-table-wrap {
    min-height: auto;
  }
}

@media (max-width: 768px) {
  .simulation-bar {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }

  .filter-bar {
    flex-wrap: wrap;
  }

  .device-actions-bar {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }

  .actions-left,
  .actions-center {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
