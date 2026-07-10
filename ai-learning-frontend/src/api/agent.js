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
export const getAgentPendingActions = (params) => unwrap(request.get('/api/agent/actions/pending', { params }))
export const getAgentPendingAction = (actionId) => unwrap(request.get(`/api/agent/actions/pending/${actionId}`, { silent: true }))

export const confirmAgentAction = (data) => unwrap(request.post('/api/agent/actions/confirm', data, { silent: true }))
