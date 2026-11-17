import { defineStore } from 'pinia'
import { clearUserSession, loadUserSession, saveUserSession } from '@/utils/userSession'

export const useUserStore = defineStore('user', {
  state: loadUserSession,

  actions: {
    setUser(data) {
      this.token = data.token
      this.userId = data.userId == null ? '' : String(data.userId)
      this.username = data.username
      this.nickname = data.nickname || data.username
      this.email = data.email || ''
      this.phone = data.phone || ''
      this.avatar = data.avatar || ''
      this.roleCode = (data.roleCode || '').toLowerCase()

      saveUserSession(this, true)
    },

    updateProfile(data) {
      this.nickname = data.nickname || this.username
      this.email = data.email || ''
      this.phone = data.phone || ''
      this.avatar = data.avatar || ''
      saveUserSession(this)
    },

    logout() {
      clearUserSession(this.token)
      this.$reset()
    }
  },

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.roleCode === 'admin',
    isTeacher: (state) => state.roleCode === 'teacher',
    isStudent: (state) => state.roleCode === 'student',
  }
})
