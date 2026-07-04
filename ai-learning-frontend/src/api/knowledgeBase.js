import request from '@/utils/request'

export const listKnowledgeBases = () => request.get('/api/weknora/knowledge-bases')
export const createKnowledgeBase = (data) => request.post('/api/weknora/knowledge-bases', data)
export const uploadToKB = (kbId, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/api/weknora/knowledge-bases/${kbId}/upload`, formData)
}
export const createSession = (data) => request.post('/api/weknora/sessions', data)
export const listSessions = () => request.get('/api/weknora/sessions')
export const deleteSession = (id) => request.delete(`/api/weknora/sessions/${id}`)
export const searchKB = (params) => request.post('/api/weknora/search', params)
