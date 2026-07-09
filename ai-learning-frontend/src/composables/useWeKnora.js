import { ref, readonly } from 'vue'
import { ElMessage } from 'element-plus'
import * as weknoraApi from '../api/weknora.js'
import * as agentApi from '../api/agent.js'

const DEFAULT_AGENT_ID = 1

function unwrapData(data) {
  while (data && typeof data === 'object' && !Array.isArray(data) && data.data !== undefined) {
    data = data.data
  }
  return data
}

function payloadContent(payload) {
  const data = unwrapData(payload)
  if (typeof data === 'string') return data
  return data?.content || data?.reply || data?.message || ''
}

function pendingActionFromPayload(payload) {
  const data = unwrapData(payload)
  return data?.pendingAction || data?.pending_action || null
}

function extractMessage(payload, fallback) {
  if (!payload) return fallback
  if (payload instanceof Error) return payload.message || fallback
  if (typeof payload === 'string') return payload || fallback
  return payload.msg || payload.message || payload.error || fallback
}

export function useWeKnora() {
  const messages = ref([])
  const sessions = ref([])
  const knowledgeBases = ref([])
  const loading = ref(false)
  const currentSessionId = ref(null)
  const currentAgentSessionId = ref(null)
  const currentKbId = ref(null)
  const error = ref(null)

  async function loadKnowledgeBases() {
    try {
      loading.value = true
      const res = await weknoraApi.listKnowledgeBases()
      knowledgeBases.value = unwrapData(res.data) || []
      return knowledgeBases.value
    } catch (e) {
      error.value = e
      ElMessage.error('加载知识库失败')
      return []
    } finally {
      loading.value = false
    }
  }

  async function createNewSession(knowledgeBaseId, title = '新对话') {
    try {
      loading.value = true
      const res = await weknoraApi.createSession({ knowledgeBaseId, title })
      const data = unwrapData(res.data)
      currentSessionId.value = data?.id || data?.session_id
      currentKbId.value = knowledgeBaseId
      messages.value = []
      return currentSessionId.value
    } catch (e) {
      error.value = e
      ElMessage.error('创建会话失败')
      return null
    } finally {
      loading.value = false
    }
  }

  async function createNewAgentSession(title = 'Agent 智能助手会话') {
    try {
      loading.value = true
      const res = await agentApi.createAgentSession({ agentId: DEFAULT_AGENT_ID, title })
      const data = unwrapData(res?.data ?? res)
      currentAgentSessionId.value = data?.id || null
      messages.value = []
      return currentAgentSessionId.value
    } catch (e) {
      error.value = e
      ElMessage.error(extractMessage(e, '创建 Agent 会话失败'))
      return null
    } finally {
      loading.value = false
    }
  }

  async function loadSessions() {
    try {
      loading.value = true
      const res = await weknoraApi.listSessions()
      sessions.value = unwrapData(res.data) || []
      return sessions.value
    } catch (e) {
      error.value = e
      ElMessage.error('加载会话列表失败')
      return []
    } finally {
      loading.value = false
    }
  }

  async function removeSession(sessionId) {
    try {
      await weknoraApi.deleteSession(sessionId)
      sessions.value = sessions.value.filter(s => s.id !== sessionId)
      if (currentSessionId.value === sessionId) {
        currentSessionId.value = null
        messages.value = []
      }
    } catch (e) {
      error.value = e
      ElMessage.error('删除会话失败')
    }
  }

  async function searchInKB(knowledgeBaseIds, query) {
    try {
      const res = await weknoraApi.search({ knowledgeBaseIds, query })
      return res.data || null
    } catch (e) {
      error.value = e
      ElMessage.error('知识检索失败')
      return null
    }
  }

  async function sendLocalAgentMessage(query, displayContent = query) {
    if (!currentAgentSessionId.value) {
      const sessionId = await createNewAgentSession()
      if (!sessionId) {
        const err = new Error('请先创建 Agent 会话')
        error.value = err
        return Promise.reject(err)
      }
    }

    messages.value.push({ role: 'user', content: displayContent || query })

    const aiIndex = messages.value.length
    messages.value.push({ role: 'assistant', content: '', streaming: true })
    loading.value = true
    error.value = null
    let streamError = null

    try {
      const completePayload = await agentApi.streamAgentMessage(currentAgentSessionId.value, { content: query }, {
        onMessage(chunk) {
          messages.value[aiIndex].content += typeof chunk === 'string'
            ? chunk
            : (chunk?.content || chunk?.answer || chunk?.text || '')
        },
        onComplete(payload) {
          const content = payloadContent(payload)
          if (!messages.value[aiIndex].content && content) {
            messages.value[aiIndex].content = content
          }
          const pendingAction = pendingActionFromPayload(payload)
          if (pendingAction) {
            messages.value[aiIndex].pendingAction = pendingAction
          }
        },
        onError(payload) {
          streamError = payload
        }
      })
      if (streamError) {
        throw new Error(extractMessage(streamError, 'Agent 回复失败，请稍后重试'))
      }
      const pendingAction = pendingActionFromPayload(completePayload)
      if (pendingAction) {
        messages.value[aiIndex].pendingAction = pendingAction
      }
      messages.value[aiIndex].streaming = false
      loading.value = false
      return messages.value[aiIndex].content || payloadContent(completePayload)
    } catch (e) {
      messages.value[aiIndex].streaming = false
      messages.value[aiIndex].error = true
      messages.value[aiIndex].errorMessage = extractMessage(e, 'Agent 回复失败，请稍后重试')
      loading.value = false
      error.value = e
      ElMessage.error(extractMessage(e, 'Agent 回复失败，请稍后重试'))
      return Promise.reject(e)
    }
  }

  function sendMessage(mode, query, displayContent = query) {
    if (mode === 'agent') {
      return sendLocalAgentMessage(query, displayContent)
    }

    if (!currentSessionId.value) {
      error.value = new Error('请先创建会话')
      ElMessage.warning('请先创建会话')
      return Promise.reject(error.value)
    }

    return new Promise((resolve, reject) => {
      messages.value.push({ role: 'user', content: displayContent || query })

      const aiIndex = messages.value.length
      messages.value.push({ role: 'assistant', content: '', streaming: true })
      loading.value = true
      error.value = null

      weknoraApi.weKnoraStreamChat(mode, currentSessionId.value, query, currentKbId.value ? [currentKbId.value] : null, {
        onMessage(data) {
          // 过滤掉 thinking/tool_call/tool_result/agent_query，只显示 answer
          const type = data.response_type
          if (type && type !== 'answer' && type !== 'complete') return
          const chunk = data.content || data.answer || data.text || ''
          messages.value[aiIndex].content += chunk
        },
        onDone() {
          messages.value[aiIndex].streaming = false
          loading.value = false
          resolve(messages.value[aiIndex].content)
        },
        onError(err) {
          messages.value[aiIndex].streaming = false
          messages.value[aiIndex].error = true
          loading.value = false
          error.value = err
          ElMessage.error(err?.error || '获取知识库对话失败')
          reject(err)
        }
      })
    })
  }

  async function uploadToKB(kbId, file) {
    try {
      const res = await weknoraApi.uploadFile(kbId, file)
      return res.data || null
    } catch (e) {
      error.value = e
      ElMessage.error('文件上传失败')
      return null
    }
  }

  return {
    messages,
    sessions,
    knowledgeBases,
    loading,
    currentSessionId,
    currentAgentSessionId,
    error: readonly(error),

    loadKnowledgeBases,
    createNewSession,
    createNewAgentSession,
    loadSessions,
    removeSession,
    searchInKB,
    sendMessage,
    uploadToKB
  }
}
