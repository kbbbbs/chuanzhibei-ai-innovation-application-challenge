<template>
  <div class="ai-note-assistant">
    <div class="assistant-toolbar">
      <div class="assistant-title">
        <el-icon><Cpu /></el-icon>
        <span>AI 笔记助手</span>
      </div>
      <div class="assistant-controls">
        <el-select
          v-model="selectedKbId"
          size="small"
          class="kb-select"
          placeholder="知识库"
          :disabled="!canUseAssistant"
          @change="handleKbChange"
        >
          <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-button size="small" :icon="RefreshRight" title="新对话" :disabled="!selectedKbId" @click="handleNewSession" />
      </div>
    </div>

    <div class="quick-actions">
      <el-button v-if="loading" size="small" @click="cancel">停止生成</el-button>
      <el-button size="small" :icon="Document" :disabled="!canSend" :loading="loading && activeTask === 'summarize_note'" @click="runTask('summarize_note')">
        总结
      </el-button>
      <el-button size="small" :icon="Search" :disabled="!canSend" :loading="loading && activeTask === 'explain_selection'" @click="runTask('explain_selection')">
        解释
      </el-button>
      <el-button v-if="hasVideo" size="small" :icon="Tickets" :disabled="!canSend" :loading="loading && activeTask === 'outline_from_video'" @click="runTask('outline_from_video')">
        提纲
      </el-button>
      <el-button size="small" :icon="EditPen" :disabled="!canSend" :loading="loading && activeTask === 'quiz_from_note'" @click="runTask('quiz_from_note')">
        练习题
      </el-button>
    </div>

    <p v-if="loading">{{ progress || '正在处理资料…' }}</p>
    <el-alert v-if="error && !loading" :title="String(error)" type="error" :closable="false" />
    <p v-if="lastMessage?.status === 'cancelled'">已停止生成，回答未完成。</p>
    <div v-if="lastAnswer" class="assistant-result">
      <div class="result-content" v-html="renderMarkdown(lastAnswer)"></div>
      <RagCitations :citations="lastMessage?.citations || []" />
      <RagAnswerStatus :status="lastMessage?.status" :warning="lastMessage?.warning" />
      <div class="result-actions">
        <el-button size="small" :icon="Plus" @click="appendResult">追加到笔记</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Cpu, Document, EditPen, Plus, RefreshRight, Search, Tickets } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useNativeRag } from '@/composables/useNativeRag'
import RagCitations from '@/components/knowledge/RagCitations.vue'
import RagAnswerStatus from '@/components/knowledge/RagAnswerStatus.vue'
import { renderMarkdown } from '@/utils/markdown'
import { buildLessonAiPrompt } from '@/utils/lessonAiPrompt'

const props = defineProps({
  lessonTitle: {
    type: String,
    default: ''
  },
  noteContent: {
    type: String,
    default: ''
  },
  videoSummaryMd: {
    type: String,
    default: ''
  },
  currentVideoTime: {
    type: Number,
    default: 0
  },
  hasVideo: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['append-result'])

const userStore = useUserStore()
const {
  messages,
  loading,
  knowledgeBases,
  currentSessionId,
  loadKnowledgeBases,
  createNewSession,
  progress,
  error,
  cancel,
  sendMessage
} = useNativeRag()

const selectedKbId = ref('')
const lastAnswer = ref('')
const activeTask = ref('')
const lastMessage = computed(() => [...messages.value].reverse().find(message => message.role === 'assistant'))

const canUseAssistant = computed(() => userStore.isLoggedIn)
const canSend = computed(() => canUseAssistant.value && Boolean(selectedKbId.value) && !loading.value)

onMounted(async () => {
  if (!canUseAssistant.value) return
  const list = await loadKnowledgeBases()
  if (list.length > 0) {
    selectedKbId.value = list[0].id
    await createNewSession(selectedKbId.value, '课时笔记 AI')
  }
})

const handleKbChange = async (kbId) => {
  lastAnswer.value = ''
  await createNewSession(kbId, '课时笔记 AI')
}

const handleNewSession = async () => {
  if (!selectedKbId.value) return
  lastAnswer.value = ''
  await createNewSession(selectedKbId.value, '课时笔记 AI')
}

const runTask = async (task) => {
  if (!canUseAssistant.value) {
    ElMessage.warning('请先登录后使用 AI')
    return
  }
  if (!selectedKbId.value) {
    ElMessage.warning('请选择知识库')
    return
  }

  activeTask.value = task
  lastAnswer.value = ''
  const selectedText = readSelectedText()
  const prompt = buildLessonAiPrompt({
    task,
    lessonTitle: props.lessonTitle,
    noteContent: props.noteContent,
    videoSummaryMd: props.videoSummaryMd,
    selectedText,
    currentVideoTime: props.currentVideoTime
  })

  try {
    if (!currentSessionId.value) {
      await createNewSession(selectedKbId.value, '课时笔记 AI')
    }
    lastAnswer.value = await sendMessage('knowledge', prompt, taskLabel(task))
  } catch (e) {
    // useNativeRag 已提示
  } finally {
    activeTask.value = ''
  }
}

const appendResult = () => {
  if (!lastAnswer.value) return
  emit('append-result', lastAnswer.value)
  ElMessage.success('已追加到笔记')
}

const readSelectedText = () => {
  const text = window.getSelection?.().toString()?.trim()
  return text || ''
}

const taskLabel = (task) => {
  const labels = {
    summarize_note: '总结当前笔记',
    explain_selection: '解释选中内容',
    outline_from_video: '生成学习提纲',
    quiz_from_note: '生成练习题'
  }
  return labels[task] || 'AI 笔记任务'
}
</script>

<style scoped>
.ai-note-assistant {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 10px;
  background: #fafcff;
}

.assistant-toolbar,
.assistant-title,
.assistant-controls,
.quick-actions,
.result-actions {
  display: flex;
  align-items: center;
}

.assistant-toolbar {
  justify-content: space-between;
  gap: 10px;
}

.assistant-title {
  gap: 6px;
  color: #303133;
  font-weight: 600;
}

.assistant-controls {
  gap: 6px;
}

.kb-select {
  width: 130px;
}

.quick-actions {
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.quick-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.assistant-result {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
}

.result-content {
  max-height: 220px;
  overflow: auto;
  color: #303133;
  line-height: 1.6;
  font-size: 13px;
}

.result-actions {
  justify-content: flex-end;
  margin-top: 8px;
}

:deep(.result-content p) {
  margin: 0 0 6px;
}

:deep(.result-content p:last-child) {
  margin-bottom: 0;
}

:deep(.result-content ul),
:deep(.result-content ol) {
  padding-left: 18px;
  margin: 4px 0;
}
</style>
