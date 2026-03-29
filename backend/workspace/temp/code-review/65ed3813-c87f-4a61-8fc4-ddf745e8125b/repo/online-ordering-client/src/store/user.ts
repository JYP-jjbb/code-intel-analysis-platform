import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo } from '@/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return {
    token,
    userInfo,
    setToken,
    setUserInfo,
    logout
  }
})












