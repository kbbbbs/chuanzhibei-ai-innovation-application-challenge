import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: "[REDACTED]"
    userId: localStorage.getItem('userId') || '',
    username: localStorage.getItem('username') || '',
    nickname: localStorage.getItem('nickname') || '',
    roleCode: localStorage.getItem('roleCode') || '',
  }),

  actions: {
    setUser(data) {
      this.token = data.token
      this.userId = data.userId == null ? '' : String(data.userId)
      this.username = data.username
      this.nickname = data.nickname || data.username
      this.roleCode = (data.roleCode || '').toLowerCase()

      localStorage.setItem('token', data.token)
      localStorage.setItem('userId', this.userId)
      localStorage.setItem('username', data.username)
      localStorage.setItem('nickname', data.nickname || data.username)
      localStorage.setItem('roleCode', this.roleCode)
    },

    logout() {
      this.token = ''
      this.userId = ''
      this.username = ''
      this.nickname = ''
      this.roleCode = ''
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('username')
      localStorage.removeItem('nickname')
      localStorage.removeItem('roleCode')
    }
  },

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.roleCode === 'admin',
    isTeacher: (state) => state.roleCode === 'teacher',
    isStudent: (state) => state.roleCode === 'student',
  }
})
