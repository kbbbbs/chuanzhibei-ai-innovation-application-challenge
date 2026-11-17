import request from '@/utils/request'
import { backendUrl } from '@/utils/backendUrl'
import { streamEventResponse } from '@/utils/eventStream'

export function unwrapAssistant(response) {
  if (response?.code && String(response.code) !== '200') {
    const error = new Error(response.msg || '请求失败')
    error.code = String(response.code)
    throw error
  }
  return response?.data ?? response
}

const get = (url, params) =>
  request.get(url, { params, silent: true }).then(unwrapAssistant)
const post = (url, data) =>
  request.post(url, data, { silent: true }).then(unwrapAssistant)
export const listAssistantSessions = () => get('/api/assistant/sessions')
export const createAssistantSession = (data) =>
  post('/api/assistant/sessions', data)
export const getAssistantMessages = (id) =>
  get(`/api/assistant/sessions/${id}/messages`)
export const deleteAssistantSession = (id) =>
  request
    .delete(`/api/assistant/sessions/${id}`, { silent: true })
    .then(unwrapAssistant)
export const renameAssistantSession = (id, title) =>
  request
    .put(`/api/assistant/sessions/${id}`, { title }, { silent: true })
    .then(unwrapAssistant)
export const sendAssistantMessage = (id, data) =>
  post(`/api/assistant/sessions/${id}/messages`, data)
export const getAssistantRequest = (id, requestId) =>
  get(`/api/assistant/sessions/${id}/requests/${requestId}`)
export const cancelAssistantRequest = (id, requestId) =>
  post(`/api/assistant/sessions/${id}/requests/${requestId}/cancel`)

export const streamAssistantRequest = (id, requestId, callbacks) =>
  streamEventResponse(
    backendUrl(`/api/assistant/sessions/${id}/stream`),
    { requestId },
    callbacks,
  )
