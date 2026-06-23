import { ref } from 'vue'

export interface WsDeviceData {
  type: string; deviceId: string; sensorId: string
  value: number; unit: string; timestamp: string
}
export interface WsAlert {
  deviceId: string; level: string; title: string; timestamp: string
}

let Client: any = null
let SockJS: any = null

async function loadSockJS() {
  try {
    // @ts-ignore sockjs-client 没有可用的类型声明
    const mod = await import('sockjs-client')
    SockJS = mod.default || mod
  } catch {
    /* ignore */
  }
}
async function loadStomp() {
  try { 
    const mod = await import('@stomp/stompjs'); Client = mod.Client 
  } catch {
     /* ignore */ 
    }
}

// 预加载
loadSockJS()
loadStomp()

const connected = ref(false)
let stompClient: any = null
const deviceCallbacks = new Map<string, Set<(data: WsDeviceData) => void>>()
const alertCallbacks = new Set<(data: WsAlert) => void>()

export function useWebSocket() {
  function connect() {
    if (!SockJS || !Client) return  // 库未加载，静默降级
    if (stompClient?.active) return

    const baseUrl = window.location.protocol + '//' + window.location.host
    try {
      stompClient = new Client({
        webSocketFactory: () => new SockJS(baseUrl + '/ws'),
        reconnectDelay: 3000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
          connected.value = true
          console.log('[WS] Connected')

          stompClient.subscribe('/topic/device/*', (msg: any) => {
            try {
              const data: WsDeviceData = JSON.parse(msg.body)
              deviceCallbacks.get(data.deviceId)?.forEach(fn => fn(data))
              deviceCallbacks.get('*')?.forEach(fn => fn(data))
            } catch { /* ignore */ }
          })

          stompClient.subscribe('/topic/alert', (msg: any) => {
            try {
              const alert: WsAlert = JSON.parse(msg.body)
              alertCallbacks.forEach(fn => fn(alert))
            } catch { /* ignore */ }
          })
        },
        onDisconnect: () => { connected.value = false },
        onStompError: () => { /* ignore */ }
      })
      stompClient.activate()
    } catch { /* WebSocket 不可用时静默降级到轮询 */ }
  }

  function disconnect() {
    try { stompClient?.deactivate() } catch { /* ignore */ }
    connected.value = false
  }

  function onDeviceData(deviceId: string, fn: (data: WsDeviceData) => void) {
    if (!deviceCallbacks.has(deviceId)) deviceCallbacks.set(deviceId, new Set())
    deviceCallbacks.get(deviceId)!.add(fn)
    return () => deviceCallbacks.get(deviceId)?.delete(fn)
  }

  function onAllDeviceData(fn: (data: WsDeviceData) => void) {
    return onDeviceData('*', fn)
  }

  function onAlert(fn: (data: WsAlert) => void) {
    alertCallbacks.add(fn)
    return () => { alertCallbacks.delete(fn) }
  }

  return { connected, connect, disconnect, onDeviceData, onAllDeviceData, onAlert }
}
