import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 30000,
})

request.interceptors.request.use(config => {
  config.headers['content-Type'] = 'application/json;charset=utf-8'
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
}, error => {
  return Promise.reject(error)
})

request.interceptors.response.use(response => {
  let res = response.data
  if (typeof res === 'string') {
    res = res ? JSON.parse(res) : res
  }
  if (res.code && res.code !== '200') {
    ElMessage.error(res.msg || '请求失败')
  }
  return res
}, error => {
  if (error.response && error.response.status === 401) {
    ElMessage.error('登录已过期')
  } else {
    ElMessage.error('接口请求失败')
  }
  return Promise.reject(error)
})

export default request
