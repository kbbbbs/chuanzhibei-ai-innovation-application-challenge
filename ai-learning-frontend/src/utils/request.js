import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 30000,
})

request.interceptors.request.use(config => {
  if (!(config.data instanceof FormData)) {
    config.headers['Content-Type'] = 'application/json;charset=utf-8'
  }
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
}, error => {
  return Promise.reject(error)
})

function normalizeResponse(data) {
  // 兼容考试分支 ApiResponse { success, data, message }
  if (data && typeof data.success === 'boolean') {
    return {
      code: data.success ? '200' : '500',
      msg: data.message || (data.success ? '请求成功' : '请求失败'),
      data: data.data,
    }
  }
  if (data && data.code !== undefined) {
    return {
      ...data,
      code: String(data.code),
      msg: data.msg || data.message || (String(data.code) === '200' ? '请求成功' : '请求失败'),
    }
  }
  return data
}

request.interceptors.response.use(response => {
  let res = response.data
  if (typeof res === 'string') {
    try {
      res = res ? JSON.parse(res) : res
    } catch {
      return res
    }
  }

  res = normalizeResponse(res)

  if (res && res.code && res.code !== '200' && !response.config?.silentError) {
    ElMessage.error(res.msg || '请求失败')
  if (res && res.code && res.code !== '200') {
    // 静默降级：Agent 定义接口不可用时只记录不弹框
    const silent = response.config?.silent
    if (!silent) {
      ElMessage.error(res.msg || '请求失败')
    }
  }
  return res
}, error => {
  const status = error.response?.status
  if (error.config?.silentError) {
    return Promise.reject(error)
  }

  const silent = error.config?.silent
  if (status === 401) {
    if (!silent) ElMessage.error('登录已过期，请重新登录')
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('nickname')
    localStorage.removeItem('roleCode')
    window.location.href = '/login'
  } else if (status === 403) {
    if (!silent) ElMessage.error('无权限访问该资源')
  } else if (status >= 500) {
    if (!silent) ElMessage.error('服务器异常，请稍后重试')
  } else {
    if (!silent) ElMessage.error(error.message || '网络请求失败')
  }
  return Promise.reject(error)
})

export default request
