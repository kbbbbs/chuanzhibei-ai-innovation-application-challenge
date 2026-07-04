<template>
  <div class="ai-widget-container" :class="{ 'inline-mode': inline, 'floating-mode': !inline }">
    <!-- Header -->
    <div class="chat-header" v-if="inline">
      <div class="header-title">
        <span class="icon-pulse"></span>
        <el-icon class="ai-logo"><Cpu /></el-icon>
        <span>AI 智能伴学与教学助手</span>
      </div>
      <div class="header-controls">
        <el-select v-model="currentMode" size="small" class="mode-select" @change="handleModeChange" style="width: 140px">
          <el-option label="知识库 RAG" value="knowledge" />
          <el-option label="Agent 智能体" value="agent" />
        </el-select>
      </div>
    </div>

    <!-- Mode switch for floating dialog -->
    <div class="dialog-top-bar" v-else>
      <el-radio-group v-model="currentMode" size="small">
        <el-radio-button label="knowledge">知识库问答 (RAG)</el-radio-button>
        <el-radio-button label="agent">Agent 智能助手</el-radio-button>
      </el-radio-group>
      <el-button type="primary" link size="small" @click="handleNewSession">
        <el-icon><RefreshRight /></el-icon> 新对话
      </el-button>
    </div>

    <!-- Chat Messages Box -->
    <div class="chat-messages" ref="messagesContainer">
      <div class="welcome-msg" v-if="messages.length === 0">
        <div class="welcome-icon"><el-icon><ChatDotRound /></el-icon></div>
        <h4>您好，我是您的专属 AI 助手！</h4>
        <p>您可以问我任何关于课程内容、习题解答，或让我帮您搜索文献、编写大纲。</p>
        <div class="quick-prompts">
          <span class="prompt-chip" @click="sendQuick('请帮我总结一下关系型数据库的范式理论。')">💡 总结数据库范式理论</span>
          <span class="prompt-chip" @click="sendQuick('我想备课关于Python数据分析的公开课，请给出提纲。')">💡 生成Python备课大纲</span>
        </div>
      </div>

      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="message-row"
        :class="msg.role === 'user' ? 'user-row' : 'assistant-row'"
      >
        <div class="msg-avatar">
          <el-avatar :size="32" v-if="msg.role === 'user'" class="user-avt">
            <el-icon><User /></el-icon>
          </el-avatar>
          <div class="ai-avt" v-else>
            <el-icon><Cpu /></el-icon>
          </div>
        </div>

        <div class="msg-bubble" :class="{ 'streaming': msg.streaming, 'error': msg.error }">
          <div class="bubble-content" v-if="msg.content">{{ msg.content }}</div>
          <div class="typing-indicator" v-else-if="msg.streaming">
            <span></span><span></span><span></span>
          </div>
          <div class="error-text" v-if="msg.error">请求失败，请稍后重试或检查后台服务。</div>
        </div>
      </div>
    </div>

    <!-- Chat Input Box -->
    <div class="chat-input-area">
      <el-input
        v-model="inputQuery"
        type="textarea"
        :rows="inline ? 2 : 3"
        placeholder="输入您的问题，按 Ctrl + Enter 快速发送..."
        resize="none"
        @keydown.ctrl.enter="handleSend"
      />
      <div class="input-actions">
        <span class="tip-text">WeKnora 引擎支持</span>
        <el-button
          type="primary"
          class="send-btn"
          :loading="loading"
          :disabled="!inputQuery.trim()"
          @click="handleSend"
        >
          发送
          <el-icon class="el-icon--right"><Position /></el-icon>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { useWeKnora } from '@/composables/useWeKnora'
import { Cpu, User, ChatDotRound, Position, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  inline: {
    type: Boolean,
    default: false
  }
})

const {
  messages,
  loading,
  currentSessionId,
  createNewSession,
  sendMessage
} = useWeKnora()

const currentMode = ref('knowledge')
const inputQuery = ref('')
const messagesContainer = ref(null)

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(messages, () => {
  scrollToBottom()
}, { deep: true })

onMounted(async () => {
  if (!currentSessionId.value) {
    await handleNewSession()
  }
})

const handleNewSession = async () => {
  // Use a default KB ID or null if none loaded
  await createNewSession(null, '智能学习助手会话')
}

const handleModeChange = () => {
  ElMessage.info(`已切换至 ${currentMode.value === 'agent' ? 'Agent 智能体' : '知识库 RAG'} 模式`)
}

const sendQuick = (text) => {
  inputQuery.value = text
  handleSend()
}

const handleSend = async () => {
  const query = inputQuery.value.trim()
  if (!query || loading.value) return

  inputQuery.value = ''
  try {
    if (!currentSessionId.value) {
      await handleNewSession()
    }
    await sendMessage(currentMode.value, query)
  } catch (e) {
    console.error('Chat error:', e)
  }
}
</script>

<style scoped>
.ai-widget-container {
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.08), 0 8px 10px -6px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.inline-mode {
  width: 100%;
  height: 100%;
  max-height: 580px;
}

.floating-mode {
  width: 100%;
  height: 500px;
  box-shadow: none;
  border: none;
}

/* Header */
.chat-header {
  height: 56px;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #4F46E5, #3B82F6);
  color: white;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 1rem;
}

.ai-logo {
  font-size: 20px;
}

.icon-pulse {
  width: 8px;
  height: 8px;
  background: #10B981;
  border-radius: 50%;
  box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
}

.dialog-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: #F8FAFC;
  border-bottom: 1px solid var(--border-color);
}

/* Messages */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #F9FAFB;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-msg {
  text-align: center;
  padding: 24px 16px;
  color: var(--text-secondary);
}

.welcome-icon {
  font-size: 36px;
  color: var(--primary-color);
  margin-bottom: 12px;
}

.welcome-msg h4 {
  color: var(--text-primary);
  font-size: 1.1rem;
  margin-bottom: 8px;
}

.welcome-msg p {
  font-size: 0.9rem;
  line-height: 1.5;
  margin-bottom: 20px;
}

.quick-prompts {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}

.prompt-chip {
  background: white;
  border: 1px solid var(--border-color);
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 0.85rem;
  color: var(--text-primary);
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px rgba(0,0,0,0.02);
}

.prompt-chip:hover {
  background: #EEF2FF;
  border-color: #C7D2FE;
  color: var(--primary-color);
}

/* Rows */
.message-row {
  display: flex;
  gap: 12px;
  max-width: 85%;
}

.user-row {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.assistant-row {
  align-self: flex-start;
}

.msg-avatar {
  flex-shrink: 0;
}

.ai-avt {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4F46E5, #8B5CF6);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.user-avt {
  background: #4B5563;
}

.msg-bubble {
  padding: 12px 16px;
  border-radius: 14px;
  font-size: 0.95rem;
  line-height: 1.5;
  word-break: break-word;
}

.user-row .msg-bubble {
  background: var(--primary-color);
  color: white;
  border-top-right-radius: 4px;
}

.assistant-row .msg-bubble {
  background: white;
  color: var(--text-primary);
  border: 1px solid rgba(0,0,0,0.05);
  box-shadow: 0 1px 2px rgba(0,0,0,0.02);
  border-top-left-radius: 4px;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  background: #9CA3AF;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.error-text {
  color: #EF4444;
  font-size: 0.85rem;
}

/* Input Area */
.chat-input-area {
  padding: 12px 16px;
  background: white;
  border-top: 1px solid var(--border-color);
}

:deep(.chat-input-area .el-textarea__inner) {
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 0.95rem;
  transition: all 0.2s ease;
}

:deep(.chat-input-area .el-textarea__inner:focus) {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px rgba(79, 70, 229, 0.1);
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.tip-text {
  font-size: 0.75rem;
  color: #9CA3AF;
}

.send-btn {
  border-radius: 8px;
  padding: 6px 16px;
}
</style>