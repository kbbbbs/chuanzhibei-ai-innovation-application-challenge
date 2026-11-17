import request from '@/utils/request'
import { useUserStore } from '@/store/user'

const BASE = '/api/rag'
const checked = async (promise) => {
  const result = await promise
  if (result?.code && String(result.code) !== '200') throw new Error(result.msg || result.message || '知识库请求失败')
  return result
}
export const listKnowledgeBases = (params = {}) => checked(request.get(`${BASE}/knowledge-bases`, { params }))
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
export const reviewDocumentPage = (id, page, data) => checked(request.post(`${BASE}/documents/${id}/pages/${page}/review`, data))
export const getDocumentPagePreview = async (id, page, version, config = {}) => {
  const blob = await request.get(`${BASE}/documents/${id}/pages/${page}/preview`, {
    params: { version }, responseType: 'blob', silentError: true, ...config
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
  try {
    blob = await request.get(`${BASE}/visuals/${id}/image`, { ...config, responseType: 'blob', silentError: true })
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
export const getChunk = (id, config = {}) => checked(request.get(`${BASE}/chunks/${id}`, config))
export const downloadDocument = (id) => request.get(`${BASE}/documents/${id}/download`, { responseType: 'blob' })
export const uploadDocument = (id, file, config = {}) => {
  const body = new FormData()
  body.append('file', file)
  return checked(request.post(`${BASE}/knowledge-bases/${id}/documents`, body, { timeout: 180000, ...config }))
}
export const search = (data, config = {}) => checked(request.post(`${BASE}/search`, {
  knowledgeBaseIds: data.knowledgeBaseIds || data.kbIds || [],
  query: data.query,
  topK: data.topK || data.limit || 8
}, { silentError:true, ...config }))
export const chat = (data, config = {}) => checked(request.post(`${BASE}/chat`, data, { timeout: 120000, ...config }))
export const createSession = (data) => checked(request.post(`${BASE}/sessions`, data))
export const listSessions = () => checked(request.get(`${BASE}/sessions`))
export const getSessionMessages = (id) => checked(request.get(`${BASE}/sessions/${id}/messages`))
export const deleteSession = (id) => checked(request.delete(`${BASE}/sessions/${id}`))

// POST SSE: handle frame boundaries, CRLF, split UTF-8, and multiple data lines.
export function streamChat(data, callbacks = {}) {
  const controller = new AbortController()
  const token = useUserStore().token
  const endpoint = `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}${BASE}/chat/stream`
  const finished = (async () => {
    let complete = false
    try {
      const response = await fetch(endpoint, {
        method: 'POST', signal: controller.signal,
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: JSON.stringify(data)
      })
      if (!response.ok) {
        const payload = await response.json().catch(() => ({}))
        throw new Error(payload.msg || payload.message || (response.status === 401 ? '登录已过期，请重新登录' : `问答请求失败（${response.status}）`))
      }
      if (!response.headers.get('content-type')?.includes('text/event-stream')) {
        const result = await response.json().catch(() => null)
        throw new Error(result?.msg || result?.message || '问答服务未返回流式响应')
      }
      const reader = response.body?.getReader()
      if (!reader) throw new Error('无法读取问答响应')
      const decoder = new TextDecoder()
      let buffer = ''
      const processFrame = (frame) => {
        const lines = frame.split('\n')
        const event = lines.find(line => line.startsWith('event:'))?.slice(6).trim() || 'message'
        const raw = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n')
        if (!raw || raw === '[DONE]') return
        let payload
        try { payload = JSON.parse(raw) } catch { throw new Error('问答响应格式异常，请重试') }
        if (event === 'error') throw new Error(payload.message || payload.error || '问答生成失败')
        if (event === 'progress') callbacks.onProgress?.(payload)
        else if (event === 'delta') callbacks.onDelta?.(payload)
        else if (event === 'complete') {
          complete = true
          callbacks.onComplete?.(payload)
        }
      }
      while (true) {
        const { done, value } = await reader.read()
        buffer += done ? decoder.decode() : decoder.decode(value, { stream: true })
        // Concatenate first: CR and LF may be split between network reads.
        buffer = buffer.replace(/\r\n/g, '\n')
        let end
        while ((end = buffer.indexOf('\n\n')) >= 0) {
          processFrame(buffer.slice(0, end))
          buffer = buffer.slice(end + 2)
          if (complete) break
        }
        if (complete) {
          await reader.cancel().catch(() => {})
          callbacks.onDone?.()
          return
        }
        if (done) {
          if (buffer.trim()) processFrame(buffer)
          break
        }
      }
      if (!complete) throw new Error('连接中断，回答尚未完成，请重试')
      callbacks.onDone?.()
    } catch (error) {
      if (controller.signal.aborted) callbacks.onCancel?.()
      else callbacks.onError?.(error)
    }
  })()
  return { abort: () => controller.abort(), finished }
}

export function unwrapRag(result) { return result?.data ?? result }
export function ragList(result) {
  const data = unwrapRag(result)
  return Array.isArray(data) ? data : data?.data || data?.items || data?.records || []
}

export const getCourseDocuments = (courseId, config = {}) => checked(request.get(`${BASE}/courses/${courseId}/documents`, config))
export const getCourseDocument = (courseId, documentId, version, config = {}) => checked(request.get(`${BASE}/courses/${courseId}/documents/${documentId}/versions/${version}`, config))
export const grantCourseDocument = (documentId, data) => checked(request.post(`${BASE}/documents/${documentId}/grants`, data))
export const revokeCourseDocument = (documentId, courseId, version) => checked(request.delete(`${BASE}/documents/${documentId}/grants/${courseId}/versions/${version}`))
