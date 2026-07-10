import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: "[REDACTED]"
    userId: localStorage.getItem('userId') || '',
    username: localStorage.getItem('username') || '',
    nickname: localStorage.getItem('nickname') || '',
    email: localStorage.getItem('email') || '',
    phone: localStorage.getItem('phone') || '',
    avatar: localStorage.getItem('avatar') || '',
    roleCode: localStorage.getItem('roleCode') || '',
  }),

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

      localStorage.setItem('token', data.token)
      localStorage.setItem('userId', this.userId)
      localStorage.setItem('username', data.username)
      localStorage.setItem('nickname', data.nickname || data.username)
      localStorage.setItem('email', this.email)
      localStorage.setItem('phone', this.phone)
      localStorage.setItem('avatar', this.avatar)
      localStorage.setItem('roleCode', this.roleCode)
    },

    updateProfile(data) {
      this.nickname = data.nickname || this.username
      this.email = data.email || ''
      this.phone = data.phone || ''
      this.avatar = data.avatar || ''
      localStorage.setItem('nickname', this.nickname)
      localStorage.setItem('email', this.email)
      localStorage.setItem('phone', this.phone)
      localStorage.setItem('avatar', this.avatar)
    },

    logout() {
      this.token = ''
      this.userId = ''
      this.username = ''
      this.nickname = ''
      this.email = ''
      this.phone = ''
      this.avatar = ''
      this.roleCode = ''
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('username')
      localStorage.removeItem('nickname')
      localStorage.removeItem('email')
      localStorage.removeItem('phone')
      localStorage.removeItem('avatar')
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
