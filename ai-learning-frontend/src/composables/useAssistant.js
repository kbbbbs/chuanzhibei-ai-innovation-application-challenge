import { computed, reactive } from 'vue'
import { useUserStore } from '@/store/user'
import * as api from '@/api/assistant'
import { appendKernelEvent } from '@/utils/kernelStream'

const workspaces = new Map()
const ownerPollers = new Map()
const ownerStreams = new Map()
export const assistantPageContext = reactive({
  courseId: null,
  lessonId: null,
  courseTitle: '',
  lessonTitle: '',
})
export const assistantError = (error) =>
  error?.response?.data?.msg || error?.message || '请求失败，请重试'
export const scopeKey = (scope) =>
  `${Number(scope.courseId) || 0}:${Number(scope.lessonId) || 0}`
export const assistantSectionLabel = (type) =>
  ({
    COURSE_MATERIAL: '课程资料依据',
    LESSON_CONTEXT: '课时内容／本人笔记',
    GENERAL: '通用知识补充',
    PLATFORM: '平台查询结果',
  })[type] || '回答'
const listOf = (value) =>
  Array.isArray(value) ? value : value?.records || value?.messages || []
const isRunning = (status) =>
  ['RUNNING', 'PENDING', 'GENERATING'].includes(
    String(status || '').toUpperCase(),
  )
const isForbidden = (error) =>
  String(error?.response?.status || error?.code) === '403'

export function useAssistant() {
  const user = useUserStore()
  const ownerKey = `${user.userId}:${user.roleCode}`
  if (!workspaces.has(ownerKey))
    workspaces.set(
      ownerKey,
      reactive({ sessions: [], scopes: {}, loaded: false }),
    )
  const workspace = workspaces.get(ownerKey)
  if (!ownerPollers.has(ownerKey)) ownerPollers.set(ownerKey, new Map())
  const pollers = ownerPollers.get(ownerKey)
  if (!ownerStreams.has(ownerKey)) ownerStreams.set(ownerKey, new Map())
  const streams = ownerStreams.get(ownerKey)
  const storageKey = (key) => `assistant:${ownerKey}:${key}`

  function stateFor(scope = {}) {
    const key = scopeKey(scope)
    if (!workspace.scopes[key]) {
      let saved = {}
      try {
        saved = JSON.parse(localStorage.getItem(storageKey(key)) || '{}')
      } catch {
        /* empty local preference */
      }
      workspace.scopes[key] = {
        scope: {
          courseId: Number(scope.courseId) || null,
          lessonId: Number(scope.lessonId) || null,
        },
        sessionId: saved.sessionId || null,
        requestId: saved.requestId || null,
        messages: [],
        events: [],
        loading: false,
        generating: false,
        error: '',
        readOnly: false,
        draft: '',
        ready: false,
        retryPayload: null,
        terminalPayload: null,
        stoppedRequestId: null,
        canRetry: true,
      }
    }
    return workspace.scopes[key]
  }

  function save(state) {
    localStorage.setItem(
      storageKey(scopeKey(state.scope)),
      JSON.stringify({
        sessionId: state.sessionId,
        requestId: state.requestId,
      }),
    )
  }

  async function loadSessions() {
    workspace.sessions = listOf(await api.listAssistantSessions())
    workspace.loaded = true
    return workspace.sessions
  }

  function matchingSessions(scope) {
    return workspace.sessions.filter(
      (session) => scopeKey(session) === scopeKey(scope),
    )
  }

  async function refreshMessages(state) {
    if (!state.sessionId) {
      state.messages = []
      return
    }
    const preview = state.messages.find((message) => message.streaming)
    state.messages = listOf(await api.getAssistantMessages(state.sessionId))
    if (preview && state.requestId) state.messages.push(preview)
  }

  function clearPreview(state) {
    state.messages = state.messages.filter((message) => !message.streaming)
  }

  function updateEvents(state, events) {
    if (!Array.isArray(events)) return
    const validated = []
    for (const event of events) appendKernelEvent(validated, event)
    if (validated.length >= state.events.length) state.events = validated
  }

  function appendEvent(state, event) {
    // Reconnecting replays the same sequence from 1. Never duplicate a run step.
    if (event?.sequence <= state.events.length) return
    appendKernelEvent(state.events, event)
  }

  function restrictPreview(state, result) {
    if (!result.eventsRestricted && !result.message?.eventsRestricted && result.status !== 'RESTRICTED' && result.message?.status !== 'RESTRICTED') return false
    state.events = []
    clearPreview(state)
    return true
  }

  function updatePreview(state, result) {
    if (state.stoppedRequestId === state.requestId) return
    if (!restrictPreview(state, result)) updateEvents(state, result.events)
    if (!result.message || !isRunning(result.status)) return
    const id = `stream-${state.requestId}`
    const preview = {
      ...result.message,
      id,
      role: 'assistant',
      streaming: true,
    }
    const index = state.messages.findIndex((message) => message.id === id)
    if (index < 0) state.messages.push(preview)
    else state.messages[index] = preview
  }

  async function open(scope = {}, requestedId) {
    const state = stateFor(scope)
    state.loading = true
    state.error = ''
    state.canRetry = true
    try {
      await loadSessions()
      const session = requestedId
        ? workspace.sessions.find(
            (item) => String(item.id) === String(requestedId),
          )
        : matchingSessions(scope).find(
            (item) => String(item.id) === String(state.sessionId),
          ) || matchingSessions(scope)[0]
      if (requestedId && !session) throw new Error('对话不存在或已无权访问')
      if (session && scopeKey(session) !== scopeKey(scope))
        throw new Error('这段对话属于其他课程，请切换课程后打开')
      if (
        state.requestId &&
        session &&
        String(session.id) !== String(state.sessionId)
      )
        throw new Error('请先停止当前回答，再切换历史对话')
      if (String(state.sessionId) !== String(session?.id)) state.events = []
      state.sessionId = session?.id || null
      state.readOnly = Boolean(session?.readOnly)
      if (!session) {
        state.messages = []
        state.events = []
        state.requestId = null
      } else {
        await refreshMessages(state)
        state.requestId = session.activeRequestId || state.requestId
      }
      state.ready = true
      save(state)
      if (state.requestId) resume(state)
      return state
    } catch (error) {
      state.messages = []
      state.events = []
      state.ready = false
      state.error = assistantError(error)
      state.canRetry = !isForbidden(error)
      throw error
    } finally {
      state.loading = false
    }
  }

  async function newSession(scope = {}) {
    const state = stateFor(scope)
    if (state.requestId) throw new Error('请等待回答完成或停止生成后新建对话')
    const session = await api.createAssistantSession({
      ...state.scope,
      title: '新对话',
    })
    state.sessionId = session.id
    state.messages = []
    state.events = []
    state.requestId = null
    state.readOnly = false
    state.error = ''
    state.ready = true
    workspace.sessions.unshift(session)
    save(state)
    return state
  }

  async function poll(state) {
    const key = scopeKey(state.scope)
    if (!state.requestId || !state.sessionId || pollers.get(key) === 'busy')
      return
    pollers.set(key, 'busy')
    const requestId = state.requestId
    try {
      const result = await api.getAssistantRequest(state.sessionId, requestId)
      if (state.requestId !== requestId) return
      state.error = ''
      if (!restrictPreview(state, result)) updateEvents(state, result.events)
      if (isRunning(result.status)) {
        state.generating = state.stoppedRequestId !== requestId
        updatePreview(state, result)
        pollers.set(
          key,
          setTimeout(() => {
            pollers.delete(key)
            poll(state)
          }, 1000),
        )
        return
      }
      const previousPayload = state.retryPayload
      clearPreview(state)
      state.generating = false
      state.requestId = null
      state.retryPayload = null
      save(state)
      await refreshMessages(state)
      if (
        !state.messages.some(
          (item) => item.id && item.id === result.message?.id,
        ) &&
        result.message
      )
        state.messages.push(result.message)
      if (result.message || state.messages.some((message) => message.requestId === requestId && message.events?.length)) state.events = []
      const status = String(result.status || '').toUpperCase()
      if (status === 'FAILED' || status === 'INTERRUPTED') {
        state.error = result.error || '回答已中断，请重新提问'
        state.terminalPayload = previousPayload || {
          content: [...state.messages]
            .reverse()
            .find((item) => item.role === 'user')?.content,
          taskType: 'chat',
          context: {},
        }
      }
    } catch (error) {
      state.error = assistantError(error)
      state.canRetry = !isForbidden(error)
      if (!state.canRetry) {
        state.messages = []
        state.events = []
        state.ready = false
        state.requestId = null
        state.retryPayload = null
        save(state)
      }
      clearPreview(state)
      // A failed status query keeps the durable request identifier for explicit retry.
      state.generating = false
    } finally {
      if (pollers.get(key) === 'busy') pollers.delete(key)
    }
  }

  function resume(state) {
    const key = scopeKey(state.scope)
    if (pollers.has(key) || streams.has(key)) return
    state.generating = true
    const requestId = state.requestId
    const current = () => state.requestId === requestId
    const handle = api.streamAssistantRequest(state.sessionId, requestId, {
      onState: (event) => {
        if (current() && state.stoppedRequestId !== requestId) appendEvent(state, event)
      },
      onDelta: (result) => {
        if (current()) updatePreview(state, result)
      },
      onProgress: (result) => {
        if (current()) updatePreview(state, result)
      },
      onComplete: (result) => {
        streams.delete(key)
        if (current()) {
          if (!restrictPreview(state, result)) updateEvents(state, result.events)
          poll(state)
        }
      },
      onError: () => {
        streams.delete(key)
        if (current()) poll(state)
      },
    })
    streams.set(key, handle)
  }

  async function send(scope, content, taskType, context = {}) {
    const state = stateFor(scope)
    if (state.readOnly) throw new Error('历史对话仅供查看，请开始新对话')
    if (state.requestId) throw new Error('请先恢复查询或停止当前回答')
    if (!state.sessionId) await newSession(scope)
    const payload = {
      requestId:
        globalThis.crypto?.randomUUID?.() ||
        `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      content,
      taskType: (taskType || 'chat').toLowerCase(),
      context,
    }
    state.terminalPayload = null
    state.stoppedRequestId = null
    state.events = []
    state.canRetry = true
    state.retryPayload = payload
    state.requestId = payload.requestId
    state.generating = true
    state.error = ''
    save(state)
    state.messages.push({
      id: `local-${payload.requestId}`,
      role: 'user',
      content,
    })
    try {
      const result = await api.sendAssistantMessage(state.sessionId, payload)
      updateEvents(state, result.events)
      state.draft = ''
      resume(state)
    } catch (error) {
      state.generating = false
      state.error = assistantError(error)
      state.canRetry = !isForbidden(error)
      if (!state.canRetry) {
        state.messages = []
        state.events = []
        state.ready = false
        state.requestId = null
        state.retryPayload = null
        save(state)
      }
      throw error
    }
  }

  async function retry(state) {
    state.error = ''
    if (state.terminalPayload?.content) {
      const payload = state.terminalPayload
      await send(
        state.scope,
        payload.content,
        payload.taskType,
        payload.context,
      )
      return
    }
    if (state.retryPayload) {
      state.generating = true
      try {
        await api.sendAssistantMessage(state.sessionId, state.retryPayload)
      } catch (error) {
        state.generating = false
        state.error = assistantError(error)
        return
      }
    }
    if (state.requestId) resume(state)
    else await open(state.scope)
  }

  async function cancel(state) {
    if (!state.requestId) return
    const key = scopeKey(state.scope)
    try {
      await api.cancelAssistantRequest(state.sessionId, state.requestId)
      state.stoppedRequestId = state.requestId
      state.generating = false
      streams.get(key)?.abort()
      streams.delete(key)
      clearPreview(state)
      const scheduled = pollers.get(key)
      if (scheduled !== 'busy') {
        clearTimeout(scheduled)
        pollers.delete(key)
        await poll(state)
      }
    } catch (error) {
      state.error = assistantError(error)
    }
  }

  async function remove(state) {
    await api.deleteAssistantSession(state.sessionId)
    state.sessionId = null
    state.requestId = null
    state.messages = []
    state.events = []
    save(state)
    await loadSessions()
  }

  return {
    workspace,
    stateFor,
    matchingSessions,
    loadSessions,
    open,
    newSession,
    send,
    retry,
    cancel,
    remove,
    refreshMessages,
    activePageScope: computed(() => ({ ...assistantPageContext })),
  }
}
