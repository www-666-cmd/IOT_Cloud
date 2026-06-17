import { realApi } from './realApi'
import { mockApi } from './mockApi'

const USE_REAL_API = true

export const api = USE_REAL_API ? realApi : mockApi
export type { User, Device, Sensor, DataPoint } from './mockApi'
