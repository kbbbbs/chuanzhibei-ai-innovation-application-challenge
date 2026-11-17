import axios from 'axios'
import { ElMessage } from 'element-plus'
import { API_BASE_URL } from '@/utils/backendUrl'

const request = axios.create({
  baseURL: API_BASE_URL,
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
      msg: data.success ? (data.message || '请求成功') : friendlyErrorMessage(data.message),
      data: data.data,
    }
  }
  if (data && data.code !== undefined) {
    return {
      ...data,
      code: String(data.code),
      msg: String(data.code) === '200'
        ? (data.msg || data.message || '请求成功')
        : friendlyErrorMessage(data.msg || data.message),
    }
  }
  return data
}

function friendlyErrorMessage(message, fallback = '请求失败，请稍后重试') {
  if (typeof message !== 'string' || !message.trim()) return fallback
  if (/No static resource|Request failed with status code|(?:[\w$]+\.)+[\w$]*(?:Exception|Error)\b|SQL(?:SyntaxError|IntegrityConstraint)|bad SQL grammar|JDBC exception/i.test(message)) {
    return '服务暂时不可用，请稍后重试'
  }
  return message
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

  if (res && res.code && res.code !== '200') {
    const silent = response.config?.silent || response.config?.silentError
    if (!silent) ElMessage.error({ message: res.msg || '请求失败', grouping: true })
  }
  return res
}, error => {
  const status = error.response?.status
  const responseData = normalizeResponse(error.response?.data)
  const serverMessage = !error.response
    ? (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '无法连接到服务，请确认网络后重试')
    : status >= 500
      ? '服务暂时不可用，请稍后重试'
      : friendlyErrorMessage(responseData?.msg || responseData?.message, '请求失败，请稍后重试')
  error.message = serverMessage
  if (error.response?.data && typeof error.response.data === 'object') {
    for (const field of ['msg', 'message']) {
      if (field in error.response.data) error.response.data[field] = serverMessage
    }
  }
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
    window.location.href = `${import.meta.env.BASE_URL}login`
  } else if (status === 403) {
    if (!silent) ElMessage.error('无权限访问该资源')
  } else if (status >= 500) {
    if (!silent) ElMessage.error({ message: serverMessage, grouping: true })
  } else {
    if (!silent) ElMessage.error({ message: serverMessage, grouping: true })
  }
  return Promise.reject(error)
})

export default request
