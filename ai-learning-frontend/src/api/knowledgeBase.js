import request from '@/utils/request'

export const listKnowledgeBases = () => request.get('/api/weknora/knowledge-bases')
export const createKnowledgeBase = (data) => request.post('/api/weknora/knowledge-bases', data)
export const updateKnowledgeBase = (kbId, data) => request.put(`/api/weknora/knowledge-bases/${kbId}`, data)
export const deleteKnowledgeBase = (kbId) => request.delete(`/api/weknora/knowledge-bases/${kbId}`)
export const listKnowledge = (kbId, limit = 50, offset = 0) => request.get(`/api/weknora/knowledge-bases/${kbId}/knowledge`, { params: { limit, offset } })
export const getKnowledgeContent = (kbId, knowledgeId) => request.get(`/api/weknora/knowledge-bases/${kbId}/knowledge/${knowledgeId}/content`)
export const deleteKnowledge = (knowledgeId) => request.delete(`/api/weknora/knowledge/${knowledgeId}`)
export const uploadToKB = (kbId, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/api/weknora/knowledge-bases/${kbId}/upload`, formData)
}
export const createSession = (data) => request.post('/api/weknora/sessions', data)
export const listSessions = () => request.get('/api/weknora/sessions')
export const deleteSession = (id) => request.delete(`/api/weknora/sessions/${id}`)
export const searchKB = (params) => request.post('/api/weknora/search', params)
