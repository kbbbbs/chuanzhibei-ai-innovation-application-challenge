import request from '@/utils/request'
import { streamEventResponse } from '@/utils/eventStream'
import { backendUrl } from '@/utils/backendUrl'

const BASE = '/api/rag'
const checked = async (promise) => {
  const result = await promise
  if (result?.code && String(result.code) !== '200') throw new Error(result.msg || result.message || '知识库请求失败')
  return result
}
export const listKnowledgeBases = (params = {}) => checked(request.get(`${BASE}/knowledge-bases`, { params }))
export const getKnowledgeBase = id => checked(request.get(`${BASE}/knowledge-bases/${id}`))
export const getCourseKnowledgeBase = courseId => checked(request.get(`${BASE}/courses/${courseId}/knowledge-base`))
export const getCourseDocumentAccess = courseId => checked(request.get(`${BASE}/courses/${courseId}/document-access`))
export const updateCourseDocumentAccess = (courseId, data) => checked(request.put(`${BASE}/courses/${courseId}/document-access`, data))
export const createKnowledgeBase = (data) => checked(request.post(`${BASE}/knowledge-bases`, data))
export const updateKnowledgeBase = (id, data) => checked(request.put(`${BASE}/knowledge-bases/${id}`, data))
export const deleteKnowledgeBase = (id) => checked(request.delete(`${BASE}/knowledge-bases/${id}`))
export const listDocuments = (id, limit = 100, offset = 0) => checked(request.get(`${BASE}/knowledge-bases/${id}/documents`, { params: { limit, offset } }))
export const getDocument = (id) => checked(request.get(`${BASE}/documents/${id}`))
export const getProcessingJob = (id, config = {}) => checked(request.get(`${BASE}/jobs/${id}`, { silentError: true, ...config }))
export const cancelProcessingJob = (id) => checked(request.post(`${BASE}/jobs/${id}/cancel`))
export const retryDocumentParsing = (id, data = {}) => checked(request.post(`${BASE}/documents/${id}/parse-retry`, {
  pages: Array.isArray(data.pages) ? data.pages : [], forceOcr: data.forceOcr === true
}))
export const reindexDocument = (id) => checked(request.post(`${BASE}/documents/${id}/reindex`, null, { timeout: 180000 }))
export const getDocumentPages = (id, version, config = {}) => checked(request.get(`${BASE}/documents/${id}/pages`, {
  params: { version }, silentError: true, ...config
}))
export const getPageEdits = (id, version, config = {}) => checked(request.get(`${BASE}/documents/${id}/page-edits`, {
  params: { version }, silentError: true, ...config
}))
// Saving stores the text only; applyPageEdits is the separate, optional step that refreshes retrieval.
export const savePageEdits = (id, version, edits) => checked(request.post(`${BASE}/documents/${id}/page-edits`, { version, edits }, { silentError: true }))
export const applyPageEdits = (id) => checked(request.post(`${BASE}/documents/${id}/page-edits/apply`))
export const getDocumentPagePreview = async (id, page, version, config = {}) => {
  const { courseId, ...requestConfig } = config
  const path = courseId
    ? `${BASE}/courses/${courseId}/documents/${id}/versions/${version}/pages/${page}/preview`
    : `${BASE}/documents/${id}/pages/${page}/preview`
  const blob = await request.get(path, {
    params: courseId ? undefined : { version }, responseType: 'blob', silentError: true, ...requestConfig
  })
  if (!(blob instanceof Blob)) throw new Error('无法读取原页预览')
  if (blob.type.includes('json')) {
    const data = JSON.parse(await blob.text())
    throw new Error(data.msg || data.message || '原页预览不可访问')
  }
  if (!blob.type.startsWith('image/')) throw new Error('服务未返回有效的页面图片')
  return blob
}
// Use only the authenticated application endpoint, never an imageUrl supplied in document text.
export const getVisualImage = async (id, config = {}) => {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(String(id || ''))) throw new Error('图片标识无效')
  let blob
  const { courseId, ...requestConfig } = config
  try {
    blob = await request.get(`${BASE}/visuals/${id}/image`, { ...requestConfig,
      params: { ...requestConfig.params, ...(courseId ? { courseId } : {}) }, responseType: 'blob', silentError: true })
  } catch (problem) {
    let message = problem?.response?.data?.msg || problem?.message || '无法读取资料图片'
    const body = problem?.response?.data
    if (body instanceof Blob && body.type.includes('json')) {
      try { const data = JSON.parse(await body.text()); message = data.msg || data.message || message } catch { /* Keep safe request error. */ }
    }
    const failure = new Error(message); failure.status = problem?.response?.status
    throw failure
  }
  if (!(blob instanceof Blob)) throw new Error('服务未返回有效的资料图片')
  if (blob.type.includes('json')) {
    const data = JSON.parse(await blob.text())
    const failure = new Error(data.msg || data.message || '资料图片不可访问'); failure.status = Number(data.code)
    throw failure
  }
  if (blob.type !== 'image/png' || blob.size > 2 * 1024 * 1024) throw new Error('资料图片格式或大小不符合要求')
  return blob
}
export const deleteDocument = (id) => checked(request.delete(`${BASE}/documents/${id}`))
export const getDocumentChunks = (id) => checked(request.get(`${BASE}/documents/${id}/chunks`))
export const getCourseChunk = (courseId, id, config = {}) => checked(request.get(`${BASE}/courses/${courseId}/chunks/${id}`, config))
export const downloadCourseDocument = (courseId, id, version) => request.get(`${BASE}/courses/${courseId}/documents/${id}/versions/${version}/download`, { responseType: 'blob' })
export const getChunk = (id, config = {}) => checked(request.get(`${BASE}/chunks/${id}`, config))
export const downloadDocument = (id) => request.get(`${BASE}/documents/${id}/download`, { responseType: 'blob' })
export const uploadDocument = (id, file, config = {}) => {
  const body = new FormData()
  body.append('file', file)
  return checked(request.post(`${BASE}/knowledge-bases/${id}/documents`, body, { timeout: 180000, ...config }))
}
export const search = (data, config = {}) => checked(request.post(`${BASE}/search`, {
  courseId: data.courseId,
  knowledgeBaseIds: data.knowledgeBaseIds || data.kbIds || [],
  query: data.query,
  topK: data.topK || data.limit || 8
}, { silentError:true, ...config }))
export const chat = (data, config = {}) => checked(request.post(`${BASE}/chat`, data, { timeout: 120000, ...config }))
export const createSession = (data) => checked(request.post(`${BASE}/sessions`, data))
// Materials the course owner granted to this course; course members may ask about them.
export const listCourseMaterials = (courseId) => checked(request.get(`${BASE}/courses/${courseId}/materials`, { silentError: true }))
export const listSessions = () => checked(request.get(`${BASE}/sessions`))
export const getSessionMessages = (id) => checked(request.get(`${BASE}/sessions/${id}/messages`))
export const deleteSession = (id) => checked(request.delete(`${BASE}/sessions/${id}`))

export function streamChat(data, callbacks = {}) {
  return streamEventResponse(backendUrl(`${BASE}/chat/stream`), data, callbacks)
}

export function unwrapRag(result) { return result?.data ?? result }
export function ragList(result) {
  const data = unwrapRag(result)
  return Array.isArray(data) ? data : data?.data || data?.items || data?.records || []
}
