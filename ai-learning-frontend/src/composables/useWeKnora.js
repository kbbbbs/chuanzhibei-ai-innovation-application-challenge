import { ref, readonly } from 'vue'
import { ElMessage } from 'element-plus'
import * as weknoraApi from '../api/weknora.js'

export function useWeKnora() {
  const messages = ref([])
  const sessions = ref([])
  const knowledgeBases = ref([])
  const loading = ref(false)
  const currentSessionId = ref(null)
  const error = ref(null)

  async function loadKnowledgeBases() {
    try {
      loading.value = true
      const res = await weknoraApi.listKnowledgeBases()
      knowledgeBases.value = res.data || []
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
      const data = res.data
      currentSessionId.value = data?.id || data?.session_id
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

  async function loadSessions() {
    try {
      loading.value = true
      const res = await weknoraApi.listSessions()
      sessions.value = res.data || []
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

  function sendMessage(mode, query) {
    if (!currentSessionId.value) {
      error.value = new Error('请先创建会话')
      ElMessage.warning('请先创建会话')
      return Promise.reject(error.value)
    }

    return new Promise((resolve, reject) => {
      messages.value.push({ role: 'user', content: query })

      const aiIndex = messages.value.length
      messages.value.push({ role: 'assistant', content: '', streaming: true })
      loading.value = true
      error.value = null

      weknoraApi.weKnoraStreamChat(mode, currentSessionId.value, query, {
        onMessage(data) {
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
          ElMessage.error(err?.error || '流式回答出错')
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
    error: readonly(error),

    loadKnowledgeBases,
    createNewSession,
    loadSessions,
    removeSession,
    searchInKB,
    sendMessage,
    uploadToKB
  }
}
