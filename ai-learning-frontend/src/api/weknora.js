import request from '@/utils/request'

// ==================== 知识库 ====================

function extractErrorMessage(payload, fallback) {
  if (!payload) return fallback
  if (typeof payload === 'string') return payload || fallback
  return payload.msg || payload.message || payload.error || fallback
}

async function readErrorPayload(response) {
  try {
    const text = await response.text()
    if (!text) return null
    try {
      return JSON.parse(text)
    } catch {
      return text
    }
  } catch {
    return null
  }
}

export function listKnowledgeBases() {
  return request.get('/api/weknora/knowledge-bases')
}

// ==================== 知识检索 ====================

export function search(params) {
  return request.post('/api/weknora/search', params)
}

// ==================== 会话管理 ====================

export function createSession(params) {
  return request.post('/api/weknora/sessions', params)
}

export function listSessions() {
  return request.get('/api/weknora/sessions')
}

export function deleteSession(sessionId) {
  return request.delete(`/api/weknora/sessions/${sessionId}`)
}

export function stopSession(sessionId) {
  return request.post(`/api/weknora/sessions/${sessionId}/stop`)
}

// ==================== 文件上传 ====================

export function uploadFile(kbId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/api/weknora/knowledge-bases/${kbId}/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ==================== SSE 流式问答 ====================

export function weKnoraStreamChat(mode, sessionId, query, knowledgeBaseIds, callbacks = {}) {
  const token = localStorage.getItem('token')
  const endpoint = mode === 'agent'
    ? `/api/weknora/agent-chat/${sessionId}`
    : `/api/weknora/knowledge-chat/${sessionId}`

  let url = `${endpoint}?query=${encodeURIComponent(query)}`
  if (knowledgeBaseIds && knowledgeBaseIds.length > 0) {
    url += `&knowledgeBaseIds=${encodeURIComponent(knowledgeBaseIds.join(','))}`
  }

  fetch(url, {
    method: 'POST',
    headers: {
      'Authorization': token ? `Bearer ${token}` : '',
      'Content-Type': 'application/json'
    }
  }).then(async response => {
    if (!response.ok) {
      const payload = await readErrorPayload(response)
      if (response.status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('userId')
        localStorage.removeItem('username')
        localStorage.removeItem('nickname')
        localStorage.removeItem('roleCode')
        callbacks.onError?.({ error: '登录已过期，请重新登录', status: 401 })
        window.location.href = '/login'
        return
      }
      callbacks.onError?.({ error: extractErrorMessage(payload, response.status ? `获取知识库对话失败（HTTP ${response.status}）` : '获取知识库对话失败'), status: response.status })
      return
    }

    const reader = response.body?.getReader()
    if (!reader) {
      callbacks.onError?.({ error: '获取知识库对话失败' })
      return
    }
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
              const parsed = JSON.parse(data)
              if (parsed?.error) {
                callbacks.onError?.({ error: parsed.error })
                return
              }
              callbacks.onMessage?.(parsed)
            } catch {
              callbacks.onMessage?.(data)
            }
          }
        }

        read()
      }).catch(err => {
        callbacks.onError?.({ error: err.message || '获取知识库对话失败' })
      })
    }

    read()
  }).catch(err => {
    callbacks.onError?.({ error: err.message || '获取知识库对话失败' })
  })
}
