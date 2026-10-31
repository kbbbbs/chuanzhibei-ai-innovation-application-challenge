import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 30000,
})

api.interceptors.request.use(config => {
  config.headers['Content-Type'] = 'application/json;charset=utf-8'
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
}, error => Promise.reject(error))

api.interceptors.response.use(
  response => {
    const res = response.data
    // 适配 Result { code, msg, data }
    if (res && res.code && res.code !== '200') {
      ElMessage.error(res.msg || '请求失败')
    }
    return res
  },
  error => {
    const status = error.response?.status
    if (status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.clear()
      window.location.href = '/login'
    } else if (status === 403) {
      ElMessage.error('无权限访问该资源')
    } else if (status >= 500) {
      ElMessage.error('服务器异常，请稍后重试')
    } else {
      ElMessage.error(error.message || '网络请求失败')
    }
    return Promise.reject(error)
  }
)

export default api
