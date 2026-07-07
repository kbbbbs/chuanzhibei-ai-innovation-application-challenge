import request from '@/utils/request'

function rejectBusinessError(response) {
  if (response && response.code && String(response.code) !== '200') {
    return Promise.reject(new Error(response.msg || response.message || '请求失败'))
  }
  return response
}

const unwrap = (promise) => promise.then(rejectBusinessError)

export const getAgentDefinitions = (params) => unwrap(request.get('/api/agent/definitions', { params }))
export const getAgentDefinition = (agentId) => unwrap(request.get(`/api/agent/definitions/${agentId}`))
export const addAgentDefinition = (data) => unwrap(request.post('/api/agent/definitions', data))
export const updateAgentDefinition = (agentId, data) => unwrap(request.put(`/api/agent/definitions/${agentId}`, data))
export const updateAgentDefinitionStatus = (agentId, status) => unwrap(request.put(`/api/agent/definitions/${agentId}/status`, null, { params: { status } }))
export const uploadAgentKnowledgeDocument = (agentId, data) => unwrap(request.post(`/api/agent/definitions/${agentId}/knowledge-documents`, data, {
  headers: { 'Content-Type': 'multipart/form-data' }
}))

export const createAgentSession = (data) => unwrap(request.post('/api/agent/sessions', data))
export const getAgentSessions = (params) => unwrap(request.get('/api/agent/sessions', { params }))
export const updateAgentSession = (sessionId, data) => unwrap(request.put(`/api/agent/sessions/${sessionId}`, data))
export const deleteAgentSession = (sessionId) => unwrap(request.delete(`/api/agent/sessions/${sessionId}`))
export const getAgentMessages = (sessionId) => unwrap(request.get(`/api/agent/sessions/${sessionId}/messages`))
export const sendAgentMessage = (sessionId, data) => unwrap(request.post(`/api/agent/sessions/${sessionId}/chat`, data))
export const agentChatOnce = (data) => unwrap(request.post('/api/agent/chat-once', data))
export const confirmAgentAction = (data) => unwrap(request.post('/api/agent/actions/confirm', data))

function buildApiUrl(path) {
  const baseURL = request.defaults.baseURL || ''
  return `${baseURL.replace(/\/$/, '')}${path}`
}

export async function streamAgentMessage(sessionId, data, callbacks = {}) {
  const token = localStorage.getItem('token')
  const response = await fetch(buildApiUrl(`/api/agent/sessions/${sessionId}/stream`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=utf-8',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data),
    signal: callbacks.signal
  })

  if (!response.ok) {
    const message = await response.text()
    callbacks.onError?.(message)
    throw new Error(message || '流式请求失败')
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let completePayload = null

  function emitSseEvent(rawEvent) {
    const lines = rawEvent.split(/\r?\n/)
    let eventName = 'message'
    const dataLines = []

    for (const line of lines) {
      if (!line || line.startsWith(':')) continue
      const separatorIndex = line.indexOf(':')
      const field = separatorIndex === -1 ? line : line.slice(0, separatorIndex)
      let value = separatorIndex === -1 ? '' : line.slice(separatorIndex + 1)
      if (value.startsWith(' ')) value = value.slice(1)
      if (field === 'event') eventName = value || 'message'
      if (field === 'data') dataLines.push(value)
    }

    const eventData = dataLines.join('\n')
    if (!eventData) return
    if (eventName === 'message') {
      callbacks.onMessage?.(eventData)
      return
    }
    if (eventName === 'complete') {
      try {
        completePayload = JSON.parse(eventData)
      } catch {
        completePayload = eventData
      }
      callbacks.onComplete?.(completePayload)
      return
    }
    if (eventName === 'error') {
      let errorPayload = eventData
      try {
        errorPayload = JSON.parse(eventData)
      } catch {
        // Keep server-provided text when it is not JSON.
      }
      callbacks.onError?.(errorPayload)
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''
    events.forEach(emitSseEvent)
  }

  buffer += decoder.decode()
  if (buffer.trim()) {
    emitSseEvent(buffer)
  }
  return completePayload
}
