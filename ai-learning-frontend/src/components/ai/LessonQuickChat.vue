<template>
  <el-card class="quick-chat-card">
    <template #header>
      <div class="chat-header">
        <div class="chat-title">
          <el-icon><ChatDotRound /></el-icon>
          <span>课时快问</span>
        </div>
        <div class="chat-controls">
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
    </template>

    <div ref="messagesContainer" class="chat-messages">
      <el-empty v-if="messages.length === 0" description="暂无对话" />
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="message-row"
        :class="msg.role === 'user' ? 'user-row' : 'assistant-row'"
      >
        <div class="message-bubble" :class="{ streaming: msg.streaming, error: msg.error }">
          <div v-if="msg.content" v-html="renderMarkdown(msg.content)"></div>
          <span v-else-if="msg.streaming" class="typing-text">...</span>
        </div>
      </div>
    </div>

    <div class="chat-input">
      <el-input
        v-model="question"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="输入问题"
        :disabled="!canUseAssistant || !selectedKbId"
        @keydown.ctrl.enter="handleSend"
      />
      <div class="input-actions">
        <el-button
          type="primary"
          size="small"
          :icon="Position"
          :loading="loading"
          :disabled="!canSend"
          @click="handleSend"
        >
          发送
        </el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Position, RefreshRight } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useWeKnora } from '@/composables/useWeKnora'
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
  }
})

const userStore = useUserStore()
const {
  messages,
  loading,
  knowledgeBases,
  currentSessionId,
  loadKnowledgeBases,
  createNewSession,
  sendMessage
} = useWeKnora()

const selectedKbId = ref('')
const question = ref('')
const messagesContainer = ref(null)

const canUseAssistant = computed(() => userStore.isLoggedIn)
const canSend = computed(() => canUseAssistant.value && Boolean(selectedKbId.value) && Boolean(question.value.trim()) && !loading.value)

onMounted(async () => {
  if (!canUseAssistant.value) return
  const list = await loadKnowledgeBases()
  if (list.length > 0) {
    selectedKbId.value = list[0].id
    await createNewSession(selectedKbId.value, '课时快问')
  }
})

watch(messages, async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}, { deep: true })

watch(
  () => props.lessonTitle,
  () => {
    messages.value = []
    if (selectedKbId.value) {
      createNewSession(selectedKbId.value, '课时快问')
    }
  }
)

const handleKbChange = async (kbId) => {
  messages.value = []
  await createNewSession(kbId, '课时快问')
}

const handleNewSession = async () => {
  if (!selectedKbId.value) return
  messages.value = []
  await createNewSession(selectedKbId.value, '课时快问')
}

const handleSend = async () => {
  const value = question.value.trim()
  if (!canUseAssistant.value) {
    ElMessage.warning('请先登录后使用 AI')
    return
  }
  if (!selectedKbId.value) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!value || loading.value) return

  const prompt = buildLessonAiPrompt({
    task: 'quick_chat',
    question: value,
    lessonTitle: props.lessonTitle,
    noteContent: props.noteContent,
    videoSummaryMd: props.videoSummaryMd,
    selectedText: readSelectedText(),
    currentVideoTime: props.currentVideoTime
  })

  question.value = ''
  try {
    if (!currentSessionId.value) {
      await createNewSession(selectedKbId.value, '课时快问')
    }
    await sendMessage('knowledge', prompt, value)
  } catch (e) {
    // useWeKnora 已提示
  }
}

const readSelectedText = () => {
  const text = window.getSelection?.().toString()?.trim()
  return text || ''
}
</script>

<style scoped>
.quick-chat-card {
  margin-top: 16px;
}

.chat-header,
.chat-title,
.chat-controls,
.input-actions {
  display: flex;
  align-items: center;
}

.chat-header {
  justify-content: space-between;
  gap: 10px;
}

.chat-title {
  gap: 6px;
  font-weight: 600;
}

.chat-controls {
  gap: 6px;
}

.kb-select {
  width: 128px;
}

.chat-messages {
  height: 260px;
  overflow-y: auto;
  padding-right: 4px;
}

.message-row {
  display: flex;
  margin-bottom: 10px;
}

.user-row {
  justify-content: flex-end;
}

.assistant-row {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 88%;
  padding: 8px 10px;
  border-radius: 6px;
  color: #303133;
  line-height: 1.6;
  font-size: 13px;
  word-break: break-word;
}

.user-row .message-bubble {
  background: #ecf5ff;
  border: 1px solid #d9ecff;
}

.assistant-row .message-bubble {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
}

.message-bubble.error {
  border-color: #f56c6c;
}

.typing-text {
  color: #909399;
}

.chat-input {
  margin-top: 10px;
}

.input-actions {
  justify-content: flex-end;
  margin-top: 8px;
}

:deep(.message-bubble p) {
  margin: 0 0 6px;
}

:deep(.message-bubble p:last-child) {
  margin-bottom: 0;
}

:deep(.message-bubble ul),
:deep(.message-bubble ol) {
  padding-left: 18px;
  margin: 4px 0;
}
</style>
