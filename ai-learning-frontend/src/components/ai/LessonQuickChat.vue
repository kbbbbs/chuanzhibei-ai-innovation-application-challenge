<template>
  <el-card class="quick-chat-card">
    <template #header>
      <div class="chat-header">
        <strong>课时知识问答</strong>
        <el-button size="small" @click="newConversation">新对话</el-button>
      </div>
    </template>
    <p class="course-scope">{{ courseTitle || '当前课程' }} · {{ documentSummary }}</p>
    <el-alert v-if="documentError" :title="documentError" type="error" :closable="false" />
    <div class="history-toolbar">
      <el-select v-model="historyId" clearable placeholder="历史对话" :disabled="loading" @change="switchHistory">
        <el-option v-for="session in currentSessions" :key="session.id" :label="session.title || '课时问答'" :value="session.id" />
      </el-select>
      <el-button v-if="historyId" :disabled="loading" @click="deleteConversation">删除</el-button>
    </div>
    <RagConversation
      :messages="messages"
      :progress="progress"
      :course-id="courseId"
      empty-text="输入课程问题，或选中正文、笔记中的文字提问"
    />
    <div v-if="selection" class="selection-card">
      <div>
        <strong>选中内容</strong>
        <el-button text size="small" aria-label="移除选中文本" @click="clearSelection">移除</el-button>
      </div>
      <p>{{ selection }}</p>
    </div>
    <el-input
      v-model="question"
      type="textarea"
      :rows="2"
      :maxlength="2000"
      resize="none"
      placeholder="输入问题，或直接解释选中文本"
      :disabled="!canUseAssistant"
      @keydown.ctrl.enter="handleSend"
    />
    <div class="input-actions">
      <el-button v-if="loading" size="small" @click="cancel">停止生成</el-button>
      <el-button type="primary" size="small" :loading="loading" :disabled="!canSend" @click="handleSend">发送</el-button>
    </div>
    <el-alert v-if="error && !loading" :title="String(error)" type="error" :closable="false" />
  </el-card>
</template>
<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useUserStore } from '@/store/user'
import { useNativeRag } from '@/composables/useNativeRag'
import { getCourseDocuments, ragList } from '@/api/rag'
import RagConversation from '@/components/knowledge/RagConversation.vue'

const props = defineProps({
  courseId: { type: Number, required: true },
  lessonId: { type: Number, required: true },
  courseTitle: String,
  selectedText: { type: String, default: '' }
})
const emit = defineEmits(['update:selectedText'])
const userStore = useUserStore()
const {
  messages, sessions, loading, currentSessionId, progress, error, cancel,
  resetCourseContext, loadSessions, restoreSession, removeSession, sendMessage
} = useNativeRag()
const question = ref('')
const historyId = ref('')
const documents = ref([])
const documentError = ref('')
const documentsLoading = ref(false)
const selection = ref(props.selectedText)
const documentSummary = computed(() => {
  if (documentsLoading.value) return '正在读取课程资料'
  return documents.value.length ? `${documents.value.length} 份授权资料` : '暂无授权资料'
})
const canUseAssistant = computed(() => userStore.isLoggedIn && props.courseId > 0 && props.lessonId > 0)
const canSend = computed(() => canUseAssistant.value && Boolean(question.value.trim() || selection.value) && !loading.value)
const currentSessions = computed(() => sessions.value.filter(session =>
  Number(session.courseId) === props.courseId && Number(session.lessonId) === props.lessonId
))
let scopeVersion = 0

watch(() => props.selectedText, value => { selection.value = value })
watch(() => [props.courseId, props.lessonId], loadScope)
onMounted(loadScope)
onBeforeUnmount(() => {
  scopeVersion++
  cancel()
})

function clearSelection() {
  selection.value = ''
  emit('update:selectedText', '')
}

function newConversation() {
  resetCourseContext(props.courseId, props.lessonId)
  historyId.value = ''
  question.value = ''
  clearSelection()
}

async function loadScope() {
  const version = ++scopeVersion
  newConversation()
  documents.value = []
  documentError.value = ''
  documentsLoading.value = true
  try {
    const list = ragList(await getCourseDocuments(props.courseId))
    if (version === scopeVersion) documents.value = list
  } catch (failure) {
    if (version === scopeVersion) documentError.value = failure?.message || '课程资料暂不可用，请重试'
  } finally {
    if (version === scopeVersion) documentsLoading.value = false
  }
  await loadSessions()
}

async function switchHistory(id) {
  if (!id) {
    newConversation()
    return
  }
  const session = currentSessions.value.find(item => item.id === id)
  if (!session) return
  clearSelection()
  try {
    await restoreSession(session)
  } catch {
    newConversation()
  }
}

async function deleteConversation() {
  await removeSession(historyId.value)
  newConversation()
}

async function handleSend() {
  if (!canSend.value) return
  const query = question.value.trim() || '请解释选中文本。'
  const selectedText = selection.value
  const version = scopeVersion
  question.value = ''
  try {
    await sendMessage('knowledge', query, query, selectedText)
    if (version !== scopeVersion) return
    historyId.value = currentSessionId.value || ''
    await loadSessions()
  } catch {
    if (version === scopeVersion) question.value = query
  }
}
</script>
<style scoped>
.quick-chat-card { margin-top:16px; }
.chat-header,.input-actions,.selection-card>div,.history-toolbar { display:flex; align-items:center; gap:8px; }
.chat-header,.selection-card>div { justify-content:space-between; }
.course-scope { font-size:12px; color:#64748b; margin:0 0 10px; }
.history-toolbar .el-select { flex:1; min-width:0; }
.selection-card { border-left:3px solid #60a5fa; padding:8px 12px; margin:10px 0; background:#eff6ff; border-radius:6px; }
.selection-card p { max-height:120px; overflow:auto; margin:6px 0; white-space:pre-wrap; font-size:13px; }
.input-actions { justify-content:flex-end; margin-top:8px; }
:deep(.conversation) { max-height:360px; min-height:220px; }
:deep(.chat-message.user) { margin-left:12px; }
</style>
