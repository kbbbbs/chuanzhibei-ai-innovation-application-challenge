import request from '@/utils/request'
import { ElMessage } from 'element-plus'

// ==================== 知识库 ====================

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
