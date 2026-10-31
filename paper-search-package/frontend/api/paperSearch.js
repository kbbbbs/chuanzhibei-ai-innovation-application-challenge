// ============================================
// 论文检索功能 - 前端 API 模块
// 放置在 src/api/paperSearch.js
// ============================================
import api from './index'

export const searchPapers = (params) => api.get('/paper/search', { params })
export const getPaperDetail = (paperId) => api.get(`/paper/detail/${paperId}`)
export const importPapers = (keyword, maxResults = 20) =>
  api.post('/paper/import', null, { params: { keyword, maxResults } })
