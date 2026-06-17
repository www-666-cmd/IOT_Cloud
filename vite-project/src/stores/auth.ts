import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '../api'
import type { User } from '../api/mockApi'
import { useDeviceStore } from './device'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(null)
  const loading = ref(false)

  const isLoggedIn = computed(() => !!token.value && !!user.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'admin')
  const username = computed(() => user.value?.username || '')

  async function initAuth() {
    const storedToken = localStorage.getItem('iot_token')
    if (storedToken) {
      token.value = storedToken
      try {
        const currentUser = await api.getCurrentUser()
        user.value = currentUser
      } catch {
        token.value = null
        localStorage.removeItem('iot_token')
      }
    }
  }

  async function login(loginUsername: string, password: string) {
    loading.value = true
    try {
      const result = await api.login(loginUsername, password)
      user.value = result.user as User
      token.value = result.token
      return true
    } finally {
      loading.value = false
    }
  }

  async function sendCode(phone: string) {
    return await api.sendVerificationCode(phone)
  }

  async function loginByPhone(phone: string, code: string) {
    loading.value = true
    try {
      const result = await api.loginByPhone(phone, code)
      user.value = result.user as User
      token.value = result.token
      return true
    } finally {
      loading.value = false
    }
  }

  async function register(regUsername: string, email: string, password: string) {
    loading.value = true
    try {
      await api.register(regUsername, email, password)
      return true
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    localStorage.removeItem('iot_token')
    localStorage.removeItem('iot_current_user')
    user.value = null
    token.value = null
    // Clear device store so next user sees a clean slate
    const deviceStore = useDeviceStore()
    deviceStore.reset()
  }

  return {
    user, token, loading,
    isLoggedIn, isAdmin, username,
    initAuth, login, loginByPhone, register, logout, sendCode
  }
})
