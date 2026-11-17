import { ref, readonly, reactive, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import * as rag from '@/api/rag'
import * as agentApi from '@/api/agent'

const messageOf = (error, fallback) => error?.response?.data?.msg || error?.response?.data?.message || error?.message || fallback

export function useNativeRag() {
  const messages = ref([])
  const sessions = ref([])
  const knowledgeBases = ref([])
  const loading = ref(false)
  const generating = ref(false)
  const currentSessionId = ref(null)
  const currentAgentSessionId = ref(null)
  const currentKbId = ref(null)
  const courseContext = ref(null)
  const error = ref(null)
  const progress = ref('')
  let activeRequest = null
  let sessionVersion = 0

  function cancel() { activeRequest?.abort() }
  function resetCourseContext(courseId, lessonId) {
    resetKnowledgeContext(null)
    courseContext.value = { courseId: Number(courseId), lessonId: Number(lessonId) }
  }
  function resetKnowledgeContext(knowledgeBaseId) {
    cancel()
    sessionVersion++
    currentSessionId.value = null
    currentKbId.value = knowledgeBaseId || null
    courseContext.value = null
    loading.value = generating.value = false
    progress.value = ''
    messages.value = []
    error.value = null
  }
  onBeforeUnmount(cancel)

  async function loadKnowledgeBases() {
    try {
      knowledgeBases.value = rag.ragList(await rag.listKnowledgeBases())
      return knowledgeBases.value
    } catch (e) {
      error.value = messageOf(e, '加载知识库失败')
      ElMessage.error(error.value)
      return []
    }
  }
  async function createNewSession(knowledgeBaseId, title = '新对话') {
    resetKnowledgeContext(knowledgeBaseId)
    const version = sessionVersion
    if (!knowledgeBaseId) return null
    try {
      const data = rag.unwrapRag(await rag.createSession({ knowledgeBaseId, title }))
      if (version !== sessionVersion) return null
      currentSessionId.value = data.id
      return data.id
    } catch (e) {
      error.value = messageOf(e, '创建对话失败')
      ElMessage.error(error.value)
      return null
    }
  }
  async function loadSessions() {
    try {
      sessions.value = rag.ragList(await rag.listSessions())
      return sessions.value
    } catch (e) {
      error.value = messageOf(e, '加载对话失败')
      return []
    }
  }
  async function restoreSession(session) {
    cancel()
    loading.value = generating.value = false
    const version = ++sessionVersion
    const list = rag.ragList(await rag.getSessionMessages(session.id))
    if (version !== sessionVersion) return
    currentSessionId.value = session.id
    courseContext.value = session.courseId ? { courseId: Number(session.courseId), lessonId: Number(session.lessonId) } : null
    currentKbId.value = session.knowledgeBaseId || session.kb_id || currentKbId.value
    messages.value = list
  }
  async function removeSession(id) {
    await rag.deleteSession(id)
    sessions.value = sessions.value.filter(session => session.id !== id)
    if (currentSessionId.value === id) {
      cancel()
      currentSessionId.value = null
      messages.value = []
    }
  }
  async function createNewAgentSession(title = '智能助手对话') {
    cancel()
    const data = rag.unwrapRag(await agentApi.createAgentSession({ agentId: 1, title }))
    currentAgentSessionId.value = data?.id || null
    messages.value = []
    return currentAgentSessionId.value
  }
  async function sendAgentMessage(query, displayContent) {
    if (!currentAgentSessionId.value) await createNewAgentSession()
    const controller = new AbortController()
    activeRequest = controller
    const message = reactive({ role: 'assistant', content: '', streaming: true, citations: [] })
    messages.value.push({ role: 'user', content: displayContent || query }, message)
    loading.value = generating.value = true
    error.value = null
    progress.value = '正在处理任务…'
    try {
      const data = rag.unwrapRag(await agentApi.sendAgentMessage(currentAgentSessionId.value, { content: query }, { signal: controller.signal, timeout: 120000, silent:true }))
      message.content = data?.content || data?.reply || data?.answer || ''
      message.pendingAction = data?.pendingAction || data?.pending_action || null
      message.citations = data?.citations || []
      message.status = data?.knowledgeStatus || data?.status || 'complete'
      message.warning = data?.knowledgeWarning || data?.warning || ''
      return message.content
    } catch (e) {
      if (controller.signal.aborted) message.status = 'cancelled'
      else {
        message.error = true
        message.errorMessage = error.value = messageOf(e, '任务处理失败，请重试')
        throw e
      }
      return ''
    } finally {
      message.streaming = false
      if (activeRequest === controller) {
        activeRequest = null
        loading.value = generating.value = false
        progress.value = ''
      }
    }
  }
  function sendMessage(mode, query, displayContent = query, selectedText = '') {
    if (generating.value) return Promise.reject(new Error('请等待当前回答完成或先停止生成'))
    if (mode === 'agent') return sendAgentMessage(query, displayContent)
    if (!currentKbId.value && !courseContext.value) return Promise.reject(new Error('请先选择知识库'))
    const message = reactive({ role: 'assistant', content: '', streaming: true, citations: [], status: 'generating' })
    messages.value.push({ role: 'user', content: displayContent, selectedText }, message)
    loading.value = generating.value = true
    progress.value = '正在检索资料…'
    error.value = null
    const version = sessionVersion
    return new Promise((resolve, reject) => {
      let settled = false
      let requestHandle
      const finish = () => {
        settled = true
        message.streaming = false
        if (activeRequest === requestHandle) {
          loading.value = generating.value = false
          progress.value = ''
          activeRequest = null
        }
      }
      const payload = {
        query,
        sessionId: currentSessionId.value,
        knowledgeBaseIds: currentKbId.value ? [currentKbId.value] : [],
        ...(courseContext.value ? { ...courseContext.value, selectedText } : {})
      }
      requestHandle = rag.streamChat(payload, {
        onProgress: data => { if (!settled && version === sessionVersion) progress.value = data.message || '正在处理…' },
        onDelta: data => { if (!settled && version === sessionVersion) message.content += data.content || '' },
        onComplete: data => {
          if (settled || version !== sessionVersion) return
          message.content = data.answer || ''
          message.citations = Array.isArray(data.citations) ? data.citations : []
          message.mode = data.mode
          message.actualMode = data.actualMode
          message.degradedReason = data.degradedReason
          message.indexVersions = data.indexVersions
          message.status = data.status || 'complete'
          message.warning = data.warning || ''
          if (version === sessionVersion) currentSessionId.value = data.sessionId || currentSessionId.value
        },
        onDone: () => { if (!settled) { finish(); resolve(message.content) } },
        onCancel: () => {
          if (settled) return
          message.status = 'cancelled'
          message.citations = []
          finish()
          resolve('')
        },
        onError: e => {
          if (settled) return
          message.error = true
          message.status = 'error'
          message.errorMessage = messageOf(e, '回答生成失败，请重试')
          if (version === sessionVersion) error.value = message.errorMessage
          finish()
          reject(e)
        }
      })
      activeRequest = requestHandle
    })
  }
  async function searchInKB(knowledgeBaseIds, query) { return rag.unwrapRag(await rag.search({ knowledgeBaseIds, query })) }
  async function uploadToKB(id, file) { return rag.unwrapRag(await rag.uploadDocument(id, file)) }

  return {
    messages, sessions, knowledgeBases, loading, generating, currentSessionId, currentAgentSessionId, currentKbId,
    error: readonly(error), progress: readonly(progress), cancel, resetKnowledgeContext, resetCourseContext, loadKnowledgeBases, createNewSession,
    createNewAgentSession, loadSessions, restoreSession, removeSession, searchInKB, sendMessage, uploadToKB
  }
}
