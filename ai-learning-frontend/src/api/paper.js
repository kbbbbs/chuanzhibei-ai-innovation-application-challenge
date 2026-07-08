import request from '@/utils/request'

export const searchPapers = (params) => request.get('/api/paper/search', { params })
export const getPaperDetail = (paperId, source, options = {}) => request.get(`/api/paper/detail/${paperId}`, {
  params: { source },
  ...options
})
export const importPapers = (keyword, maxResults = 20) => request.post('/api/paper/import', null, { params: { keyword, maxResults } })
export const getPaperRecommendations = (params) => request.get('/api/paper/recommend', { params })

export const listPaperMarks = (params) => request.get('/api/paper/marks', { params })
export const getPaperMark = (paperId) => request.get(`/api/paper/marks/${paperId}`)
export const getPaperMarks = (paperIds) => request.get('/api/paper/marks/batch', {
  params: { paperIds: Array.isArray(paperIds) ? paperIds.join(',') : paperIds }
})
export const savePaperMark = (paperId, data) => request.put(`/api/paper/marks/${paperId}`, data)
export const deletePaperMark = (paperId) => request.delete(`/api/paper/marks/${paperId}`)
