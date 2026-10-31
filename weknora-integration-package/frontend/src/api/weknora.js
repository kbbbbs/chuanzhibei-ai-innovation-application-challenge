import axios from 'axios'
import { ElMessage } from 'element-plus'

const API_BASE = 'http://localhost:8080/api'

const api = axios.create({
  baseURL: API_BASE,
  timeout: 30000
})

// ---- 请求拦截器: 自动从 localStorage 读取 token ----
api.interceptors.request.use(config => {
  if (!(config.data instanceof FormData)) {
    config.headers['Content-Type'] = 'application/json;charset=utf-8'
  }
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}, error => Promise.reject(error))

// ---- 响应拦截器: 统一处理错误 ----
api.interceptors.response.use(
  response => {
    const res = response.data
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

// ==================== 知识库 ====================

export function listKnowledgeBases() {
  return api.get('/weknora/knowledge-bases')
}

// ==================== 知识检索 ====================

export function search(params) {
  return api.post('/weknora/search', params)
}

// ==================== 会话管理 ====================

export function createSession(params) {
  return api.post('/weknora/sessions', params)
}

export function listSessions() {
  return api.get('/weknora/sessions')
}

export function deleteSession(sessionId) {
  return api.delete(`/weknora/sessions/${sessionId}`)
}

export function stopSession(sessionId) {
  return api.post(`/weknora/sessions/${sessionId}/stop`)
}

// ==================== 文件上传 ====================

export function uploadFile(kbId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post(`/weknora/knowledge-bases/${kbId}/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ==================== SSE 流式问答 ====================

export function weKnoraStreamChat(mode, sessionId, query, callbacks = {}) {
  const token = localStorage.getItem('token')
  const endpoint = mode === 'agent'
    ? `/weknora/agent-chat/${sessionId}`
    : `/weknora/knowledge-chat/${sessionId}`

  const url = `${API_BASE}${endpoint}?query=${encodeURIComponent(query)}`

  fetch(url, {
    method: 'POST',
    headers: {
      'Authorization': token ? `Bearer ${token}` : '',
      'Content-Type': 'application/json'
    }
  }).then(response => {
    if (!response.ok) {
      callbacks.onError?.({ error: `HTTP ${response.status}` })
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    function read() {
      reader.read().then(({ done, value }) => {
        if (done) {
          callbacks.onDone?.()
          return
        }

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop()

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.substring(5).trim()
            if (data === '[DONE]') {
              callbacks.onDone?.()
              return
            }
            try {
              callbacks.onMessage?.(JSON.parse(data))
            } catch {
              callbacks.onMessage?.(data)
            }
          }
        }

        read()
      }).catch(err => {
        callbacks.onError?.({ error: err.message })
      })
    }

    read()
  }).catch(err => {
    callbacks.onError?.({ error: err.message })
  })
}
