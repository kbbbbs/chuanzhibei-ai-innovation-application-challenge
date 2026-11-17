<template>
  <section
    class="assistant-conversation"
    :class="{ compact }"
    data-testid="assistant-conversation"
  >
    <header class="conversation-toolbar">
      <span class="scope-label" :title="scopeLabel">{{ scopeLabel }}</span>
      <div class="toolbar-actions">
        <el-select
          v-if="historyEnabled"
          :model-value="state.sessionId"
          aria-label="历史对话"
          placeholder="历史对话"
          :disabled="Boolean(state.requestId)"
          @change="switchSession"
        >
          <el-option
            v-for="session in assistant.matchingSessions(scope)"
            :key="session.id"
            :label="session.title || '新对话'"
            :value="session.id"
          />
        </el-select>
        <el-button
          v-if="!sidebar"
          :disabled="Boolean(state.requestId)"
          :text="compact"
          aria-label="新对话"
          title="新对话"
          @click="startNew"
          ><el-icon><Plus /></el-icon
          ><span v-if="!compact">新对话</span></el-button
        >
        <el-button
          v-if="state.sessionId && !compact"
          text
          :disabled="Boolean(state.requestId)"
          aria-label="修改标题"
          title="修改标题"
          @click="rename"
          ><el-icon><EditPen /></el-icon
        ></el-button>
        <el-button
          v-if="state.sessionId && !compact"
          text
          :disabled="Boolean(state.requestId)"
          aria-label="删除对话"
          title="删除对话"
          @click="remove"
          ><el-icon><Delete /></el-icon
        ></el-button>
        <el-button
          v-if="expandable"
          text
          aria-label="展开对话"
          title="展开对话"
          @click="expand"
          ><el-icon><FullScreen /></el-icon
        ></el-button>
      </div>
    </header>
    <div
      ref="messageList"
      v-loading="state.loading"
      class="conversation-messages"
      aria-live="polite"
      @scroll="onMessageScroll"
    >
      <div
        v-if="state.ready && !state.messages.length && !state.generating"
        class="chat-placeholder"
      >
        <span class="placeholder-icon"
          ><el-icon><ChatDotRound /></el-icon
        ></span>
        <h2>{{ assistantName }}</h2>
        <p>
          {{
            scope.courseId
              ? '围绕课程提问，结合资料与出处一起学习'
              : '可以提问，也可以告诉我你要完成的任务'
          }}
        </p>
      </div>
      <article
        v-for="(message, index) in state.messages"
        :key="message.id || index"
        class="conversation-message"
        :class="message.role"
      >
        <span class="message-avatar" :class="message.role" aria-hidden="true">
          <el-icon v-if="message.role === 'user'"><UserFilled /></el-icon>
          <span v-else class="ai-avatar">AI</span>
        </span>
        <div class="message-bubble" :class="message.role">
          <div v-if="message.role === 'user'" class="user-message-text">
            {{ message.content }}
          </div>
          <div v-else-if="!message.pendingAction" class="markdown-body">
            <template v-if="message.sections?.length">
              <section
                v-for="(section, sectionIndex) in message.sections"
                :key="sectionIndex"
                class="answer-section"
              >
                <span class="answer-source">{{
                  sectionLabel(section.type)
                }}</span>
                <div v-html="renderMarkdown(section.content || '')" />
              </section>
            </template>
            <div v-else v-html="renderMarkdown(message.content || '')" />
          </div>
          <p v-if="message.streaming" class="streaming-status" role="status">
            <span class="streaming-dot" />{{ runtimeLabel }}
          </p>
          <RagCitations
            v-if="message.citations?.length"
            :course-id="scope.courseId"
            :citations="message.citations"
          />
          <AnswerFigures v-if="message.citations?.length" :citations="message.citations" :course-id="scope.courseId" />
          <GraphEvidencePaths
            :course-id="scope.courseId"
            :paths="message.graphPaths || []"
          />
          <RagAnswerStatus
            v-if="message.retrieval"
            :status="message.status"
            :mode="message.retrieval.mode"
            :actual-mode="message.retrieval.actualMode"
            :degraded-reason="message.retrieval.degradedReason"
            :show-warning="false"
          />
          <p
            v-if="
              ['CANCELLED', 'INTERRUPTED', 'cancelled', 'interrupted'].includes(
                message.status,
              )
            "
            class="message-warning"
          >
            {{
              String(message.status).toUpperCase() === 'CANCELLED'
                ? '已停止生成'
                : '回答已中断，可以重新提问'
            }}
          </p>
          <div
            v-if="message.pendingAction && !user.isStudent"
            class="pending-action"
          >
            <p>
              {{
                message.pendingAction.summary ||
                message.pendingAction.description ||
                message.content
              }}
            </p>
            <el-button
              type="primary"
              :disabled="Boolean(message.actionResult)"
              @click="previewAction(message)"
              >{{
                message.actionResult ? '已执行' : '查看并确认操作'
              }}</el-button
            >
          </div>
          <p v-if="message.actionResult" class="action-result">
            {{ message.actionResult }}
          </p>
          <div
            v-if="
              message.role === 'assistant' &&
              message.content &&
              !message.streaming &&
              !message.pendingAction
            "
            class="message-tools"
          >
            <el-button
              text
              size="small"
              aria-label="复制回答"
              @click="copyMessage(message)"
              ><el-icon><CopyDocument /></el-icon><span>复制</span></el-button
            >
          </div>
        </div>
      </article>
      <article
        v-if="
          state.generating &&
          !state.messages.some((message) => message.streaming)
        "
        class="conversation-message assistant"
      >
        <span class="message-avatar assistant ai-avatar" aria-hidden="true">AI</span>
        <div class="message-bubble assistant">
          <div v-if="state.generating" class="generation-status" role="status">
            <span class="typing-dots"><i /><i /><i /></span><span>{{ runtimeLabel }}</span>
          </div>
        </div>
      </article>
    </div>
    <el-alert
      v-if="state.error"
      class="conversation-error"
      :title="state.error"
      type="error"
      :closable="false"
      show-icon
      ><el-button
        v-if="state.canRetry"
        size="small"
        @click="assistant.retry(state, props.pageHandoff ? handlePageAction : undefined)"
        >重试</el-button
      ></el-alert
    >
    <el-alert
      v-if="state.readOnly"
      title="历史对话仅供查看，可开始新对话继续提问"
      type="info"
      :closable="false"
    />
    <footer v-else class="conversation-input">
      <div v-if="selectedContextText" class="selected-context-card">
          <div class="selected-context-heading">
            <div>
              <el-icon><Document /></el-icon>
              <strong>已加入课时选文</strong>
              <span v-if="selectedContextLabel">{{ selectedContextLabel }}</span>
            </div>
            <el-button text size="small" @click="emit('context-consumed')">移除</el-button>
          </div>
          <p>{{ selectedContextText }}</p>
          <small v-if="props.selectedContext?.truncated">选文较长，已截取前 1500 字作为上下文。</small>
        </div>
        <div class="chat-input-row">
        <el-input
          v-model="state.draft"
          type="textarea"
          :autosize="{ minRows: compact ? 2 : 2, maxRows: compact ? 4 : 6 }"
          maxlength="12000"
          placeholder="输入问题"
          :disabled="!state.ready || state.generating"
          @keydown="onInputKeydown"
        />
        <el-button
          v-if="state.requestId"
          class="send-button"
          @click="assistant.cancel(state)"
          >停止生成</el-button
        >
        <el-button
          v-else
          type="primary"
          class="send-button"
          :disabled="!state.ready || !state.draft.trim()"
          @click="send"
          >发送</el-button
        >
      </div>
    </footer>
    <el-dialog
      v-model="actionVisible"
      title="确认操作"
      width="min(580px, 95vw)"
      :close-on-click-modal="false"
      append-to-body
    >
      <p>
        {{
          action?.actionName || '请核对以下信息'
        }}
      </p>
      <dl class="action-parameters">
        <template v-for="field in actionParameters" :key="field.key"
          ><dt>{{ field.label }}</dt>
          <dd>{{ field.value }}</dd></template
        >
      </dl>
      <el-alert
        v-if="action?.confirmationText"
        :title="`请输入确认文字 ${action.confirmationText}`"
        type="warning"
        :closable="false"
      />
      <el-input
        v-if="action?.confirmationText"
        v-model="confirmation"
        aria-label="高风险确认文字"
      />
      <el-alert
        v-if="actionError"
        :title="actionError"
        type="error"
        :closable="false"
      />
      <template #footer
        ><el-button @click="actionVisible = false">取消</el-button
        ><el-button
          type="primary"
          :loading="confirming"
          :disabled="
            Boolean(
              action?.confirmationText &&
              confirmation !== action.confirmationText,
            )
          "
          @click="confirm"
          >确认执行</el-button
        ></template
      >
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { confirmationFields } from '@/utils/agentConfirmation'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  CopyDocument,
  Delete,
  Document,
  EditPen,
  FullScreen,
  Plus,
  UserFilled,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  useAssistant,
  assistantError,
  assistantSectionLabel as sectionLabel,
} from '@/composables/useAssistant'
import { confirmAgentAction } from '@/api/agent'
import { renameAssistantSession } from '@/api/assistant'
import RagCitations from '@/components/knowledge/RagCitations.vue'
import AnswerFigures from '@/components/knowledge/AnswerFigures.vue'
import RagAnswerStatus from '@/components/knowledge/RagAnswerStatus.vue'
import GraphEvidencePaths from '@/components/knowledge/GraphEvidencePaths.vue'
import { kernelStateLabels } from '@/utils/kernelStream'
import { renderMarkdown } from '@/utils/markdown'
import { assistantPageDestination } from '@/utils/assistantPageAction'

const props = defineProps({
  courseId: Number,
  lessonId: Number,
  courseTitle: String,
  lessonTitle: String,
  sessionId: [Number, String],
  compact: Boolean,
  sidebar: Boolean,
  pageHandoff: Boolean,
  expandable: Boolean,
  selectedContext: Object,
  draftPrompt: String,
  draftVersion: Number,
  taskContext: Object,
  historyEnabled: { type: Boolean, default: true },
})
const emit = defineEmits(['context-consumed', 'session-change', 'expand', 'page-handoff'])
let active = true
onBeforeUnmount(() => { active = false })
const user = useUserStore(),
  router = useRouter(),
  assistant = useAssistant()
const ownerId = String(user.userId)
const scope = computed(() => ({
  courseId: props.courseId || null,
  lessonId: props.lessonId || null,
}))
const state = computed(() => assistant.stateFor(scope.value))
const assistantName = computed(() => (user.isStudent ? '智能学伴' : '智能助手'))
const scopeLabel = computed(() =>
  props.lessonId
    ? `${props.courseTitle || '当前课程'} · ${props.lessonTitle || `课时 ${props.lessonId}`}`
    : props.courseId
      ? props.courseTitle || `课程 ${props.courseId}`
      : '通用对话',
)
const selectedContextText = computed(() => String(props.selectedContext?.text || '').trim())
const selectedContextLabel = computed(() => [props.selectedContext?.courseTitle, props.selectedContext?.lessonTitle].filter(Boolean).join(' · '))
const runtimeLabel = computed(() => kernelStateLabels[state.value.events.at(-1)?.state] || '正在处理…')
const messageList = ref(null),
  actionVisible = ref(false),
  actionMessage = ref(null),
  confirmation = ref(''),
  confirming = ref(false),
  actionError = ref('')
const action = computed(() => actionMessage.value?.pendingAction)
const actionLabels = {
  courseId: '课程编号',
  courseTitle: '课程名称',
  title: '名称',
  name: '名称',
  classId: '教学班编号',
  classIds: '开放教学班编号',
  chapterId: '章节编号',
  lessonId: '课时编号',
  studentId: '学生编号',
  teacherId: '教师编号',
  className: '教学班名称',
  content: '内容',
  description: '说明',
  status: '目标状态',
  sortOrder: '排序',
  difficulty: '难度',
  categoryId: '分类编号',
  noticeId: '通知编号',
  homeworkId: '作业编号',
  resourceUrl: '资源地址',
  semester: '学期',
  parameters: '操作内容',
}
const actionParameters = computed(() => {
  const parameters = action.value?.parameters || {}
  const schema = Array.isArray(parameters.parameterSchema)
    ? parameters.parameterSchema
    : []
  const fields = schema.length ? schema : Object.keys(actionLabels).map(name => ({ name, label: actionLabels[name] }))
  return confirmationFields(fields, parameters, parameters.rawRequest || '', parameters.requiredAnyOf || [])
    .map(field => ({
      key: field.name,
      label: field.label || actionLabels[field.name] || '确认信息',
      value: parameters[field.name] ?? '待定',
    }))
})

watch(
  () => [props.courseId, props.lessonId, props.sessionId],
  async () => {
    try {
      await assistant.open(scope.value, props.sessionId)
    } catch {
      /* visible state error */
    }
  },
  { immediate: true },
)
watch(
  () => props.draftVersion,
  () => {
    if (props.draftPrompt) state.value.draft = props.draftPrompt
  },
  { immediate: true },
)
watch(
  () => state.value.sessionId,
  (id) => emit('session-change', id),
)
const followMessages = ref(true)
function onMessageScroll() {
  const element = messageList.value
  if (element)
    followMessages.value =
      element.scrollHeight - element.scrollTop - element.clientHeight < 80
}
watch(
  () =>
    state.value.messages
      .map(
        (message) =>
          `${message.id}:${message.content}:${JSON.stringify(message.sections || [])}`,
      )
      .join('|') + `:${state.value.events.length}`,
  async () => {
    if (!followMessages.value) return
    await nextTick()
    messageList.value?.scrollTo({ top: messageList.value.scrollHeight })
  },
)
async function copyMessage(message) {
  const text = message.sections?.length
    ? message.sections.map((section) => section.content).join('\n\n')
    : message.content
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const previousFocus = document.activeElement
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.readOnly = true
      textarea.setAttribute('data-assistant-copy', '')
      textarea.style.position = 'fixed'
      textarea.style.left = '-9999px'
      document.body.appendChild(textarea)
      try {
        textarea.focus({ preventScroll: true })
        textarea.select()
        textarea.setSelectionRange(0, text.length)
        if (!document.execCommand('copy')) throw new Error('复制失败')
      } finally {
        textarea.remove()
        previousFocus?.focus({ preventScroll: true })
      }
    }
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请选择回答文字后复制')
  }
}

async function startNew() {
  try {
    await assistant.newSession(scope.value)
  } catch (error) {
    state.value.error = assistantError(error)
  }
}
async function switchSession(id) {
  if (id) {
    try {
      await assistant.open(scope.value, id)
    } catch {
      /* visible error */
    }
  }
}
async function remove() {
  try {
    await ElMessageBox.confirm('删除这段对话？', '删除对话', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await assistant.remove(state.value)
  } catch (error) {
    state.value.error = assistantError(error)
  }
}
async function rename() {
  try {
    const current = assistant.workspace.sessions.find(
      (session) => String(session.id) === String(state.value.sessionId),
    )
    const { value } = await ElMessageBox.prompt('输入对话标题', '修改标题', {
      inputValue: current?.title || '',
      inputValidator: (value) => Boolean(value?.trim()) || '请输入标题',
    })
    await renameAssistantSession(state.value.sessionId, value.trim())
    await assistant.loadSessions()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close')
      state.value.error = assistantError(error)
  }
}
function onInputKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  if (!event.repeat) send()
}

async function send() {
  if (!state.value.draft.trim() || state.value.requestId || !state.value.ready)
    return
  followMessages.value = true
  const context = {
    ...props.taskContext,
    pageHandoff: props.pageHandoff,
    selectedText:
      props.selectedContext?.text || props.taskContext?.selectedText || '',
  }
  try {
    await assistant.send(scope.value, state.value.draft.trim(), 'CHAT', context, props.pageHandoff ? handlePageAction : undefined)
    emit('context-consumed')
  } catch {
    /* visible error */
  }
}

async function handlePageAction(action, requestId) {
  if (!active || !props.pageHandoff || String(user.userId) !== ownerId) return
  const destination = assistantPageDestination(action, user, requestId)
  if (!destination) return
  await router.push(destination)
  emit('page-handoff')
}
async function expand() {
  if (!state.value.sessionId) await startNew()
  router.push({
    path: '/knowledge-base',
    query: {
      sessionId: state.value.sessionId,
      courseId: props.courseId || undefined,
      lessonId: props.lessonId || undefined,
    },
  })
  emit('expand')
}
function previewAction(message) {
  actionMessage.value = message
  confirmation.value = ''
  actionError.value = ''
  actionVisible.value = true
}
async function confirm() {
  confirming.value = true
  actionError.value = ''
  try {
    const response = await confirmAgentAction({
      actionId: action.value.actionId,
      confirmed: true,
      confirmationText: confirmation.value || null,
    })
    if (response?.code && String(response.code) !== '200')
      throw new Error(response.msg || '操作失败')
    const result = response?.data || response
    actionMessage.value.actionResult =
      result?.message || result?.summary || '操作已完成'
    actionVisible.value = false
  } catch (error) {
    actionError.value = assistantError(error)
  } finally {
    confirming.value = false
  }
}
defineExpose({ sessionId: computed(() => state.value.sessionId) })
</script>

<style scoped>
.assistant-conversation {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 360px;
  height: min(72vh, 760px);
  overflow: hidden;
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
}
.conversation-toolbar {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 58px;
  padding: 10px 20px;
  border-bottom: 1px solid var(--line-subtle);
}
.scope-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--text-strong);
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
}
.toolbar-actions .el-button + .el-button {
  margin-left: 0;
}
.toolbar-actions .el-select {
  width: 150px;
}
.conversation-messages {
  flex: 1;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
  padding: 24px;
  background: var(--surface-canvas);
}
.conversation-message {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin: 0 0 22px;
  min-width: 0;
}
.conversation-message.user {
  flex-direction: row-reverse;
}
.message-avatar {
  flex: none;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  font-size: 22px;
  color: var(--accent);
  background: var(--surface-accent);
  border: 1px solid var(--line-accent);
}
.message-avatar.user {
  color: var(--text-subtle);
  background: var(--surface-sunken);
  border-color: var(--line-default);
}
.message-bubble {
  max-width: min(820px, 84%);
  min-width: 0;
  padding: 14px 18px;
  border: 1px solid var(--line-subtle);
  border-radius: 4px 16px 16px 16px;
  background: var(--surface-base);
  color: var(--text-body);
  box-shadow: var(--shadow-1);
  overflow-wrap: anywhere;
  line-height: 1.75;
}
.message-bubble.user {
  color: var(--text-on-accent);
  background: var(--accent-solid);
  border-color: var(--accent-solid);
  border-radius: 16px 4px 16px 16px;
}
.ai-avatar { font-size: 14px; font-weight: 750; letter-spacing: .4px; }
.user-message-text {
  white-space: pre-wrap;
}
.answer-section + .answer-section {
  margin-top: 14px;
}
.answer-source {
  display: inline-block;
  margin-bottom: 6px;
  font-size: 11px;
  color: var(--text-subtle);
  line-height: 1.5;
}
.message-tools {
  display: flex;
  margin-top: 8px;
  border-top: 1px solid var(--line-subtle);
  padding-top: 4px;
}
.message-tools .el-button {
  color: var(--text-subtle);
  padding: 4px 0;
}
.message-tools .el-icon {
  margin-right: 4px;
}
.message-warning,
.streaming-status {
  margin: 8px 0 0;
  color: var(--text-subtle);
  font-size: 12px;
}
.streaming-status {
  display: flex;
  align-items: center;
  gap: 6px;
}
.streaming-dot {
  width: 6px;
  height: 6px;
  background: var(--accent);
  border-radius: 50%;
  animation: pulse 1.2s infinite;
}
.generation-status {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--text-subtle);
}
.typing-dots {
  display: flex;
  gap: 4px;
}
.typing-dots i {
  width: 5px;
  height: 5px;
  background: var(--accent);
  border-radius: 50%;
  animation: pulse 1.2s infinite;
}
.typing-dots i:nth-child(2) {
  animation-delay: 0.2s;
}
.typing-dots i:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes pulse {
  50% {
    opacity: 0.3;
  }
}
.chat-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--text-subtle);
}
.placeholder-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: var(--surface-accent);
  color: var(--accent);
  font-size: 32px;
}
.chat-placeholder h2 {
  margin: 18px 0 6px;
  color: var(--text-strong);
  font-size: 20px;
}
.chat-placeholder p {
  margin: 0;
  font-size: 13px;
}
.conversation-input {
  flex: none;
  padding: 16px 20px 12px;
  border-top: 1px solid var(--line-subtle);
  background: var(--surface-base);
}
.chat-input-row {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}
.chat-input-row .el-textarea {
  flex: 1;
  min-width: 0;
}
.chat-input-row :deep(.el-textarea__inner) {
  resize: none;
  border-radius: 10px;
  padding: 12px 14px;
  line-height: 1.6;
  min-height: 64px;
}
.send-button {
  flex: none;
  height: 38px;
  min-width: 72px;
  border-radius: 9px;
}
.selected-context-card {
  padding: 10px 12px;
  border: 1px solid var(--line-accent);
  border-radius: var(--radius-md);
  background: var(--surface-accent);
}

.selected-context-heading,
.selected-context-heading > div {
  display: flex;
  align-items: center;
}

.selected-context-heading {
  justify-content: space-between;
  gap: 10px;
}

.selected-context-heading > div {
  min-width: 0;
  gap: 6px;
  color: var(--accent);
}

.selected-context-heading span {
  min-width: 0;
  overflow: hidden;
  color: var(--text-subtle);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-context-card p {
  display: -webkit-box;
  margin: 7px 0 0;
  overflow: hidden;
  color: var(--text-body);
  font-size: 12px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  white-space: pre-wrap;
}

.selected-context-card small {
  display: block;
  margin-top: 5px;
  color: var(--caution);
}

.selected-context-card { margin-bottom: 10px; }
.conversation-error {
  flex: none;
}
.compact {
  height: 100%;
  min-height: 0;
  border: 0;
  border-radius: 0;
}
.compact .conversation-toolbar {
  min-height: 46px;
  padding: 6px 12px;
  gap: 6px;
}
.compact .scope-label {
  font-size: 12px;
}
.compact .toolbar-actions {
  gap: 2px;
}
.compact .toolbar-actions .el-select {
  width: 115px;
}
.compact .toolbar-actions .el-button {
  padding: 7px;
}
.compact .conversation-messages {
  padding: 20px;
}
.compact .conversation-message {
  gap: 12px;
  margin-bottom: 22px;
}
.compact .message-avatar {
  width: 32px;
  height: 32px;
  font-size: 17px;
  border-radius: 9px;
}
.compact .message-bubble {
  max-width: calc(100% - 44px);
  padding: 14px 18px;
  font-size: 15px;
  line-height: 1.85;
}
.compact .conversation-input {
  padding: 10px 12px 8px;
}
.compact .chat-input-row {
  gap: 8px;
}
.compact .send-button {
  min-width: 58px;
  padding: 8px;
}
.compact .chat-input-row :deep(.el-textarea__inner) {
  padding: 9px 10px;
  font-size: 15px;
  min-height: 64px;
}
.action-parameters {
  display: grid;
  grid-template-columns: minmax(90px, 1fr) 2fr;
  gap: 10px;
  overflow-wrap: anywhere;
}
.action-parameters dd {
  margin: 0;
}
.action-parameters dt {
  color: var(--text-subtle);
}
.action-result {
  color: var(--positive);
}
.markdown-body {
  line-height: 1.85;
  min-width: 0;
}
.markdown-body :deep(p) {
  margin: 0 0 12px;
}
.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 16px 0 10px;
  line-height: 1.5;
  color: var(--text-strong);
}
.markdown-body :deep(h2:first-child) {
  margin-top: 0;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 8px 0 12px;
  padding-left: 24px;
}
.markdown-body :deep(li) {
  margin: 4px 0;
}
.markdown-body :deep(pre) {
  max-width: 100%;
  overflow: auto;
  margin: 12px 0;
  padding: 14px 16px;
  border-radius: 9px;
  background: var(--surface-code);
  color: var(--text-on-code);
  font-size: 12px;
  line-height: 1.65;
}
.markdown-body :deep(code) {
  font-family: var(--font-mono);
  font-size: 0.9em;
}
.markdown-body :deep(:not(pre) > code) {
  padding: 2px 5px;
  border-radius: 4px;
  background: var(--surface-sunken);
  color: var(--accent);
}
.markdown-body :deep(table) {
  display: block;
  max-width: 100%;
  overflow: auto;
  border-collapse: collapse;
  margin: 12px 0;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--line-default);
  padding: 8px 12px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: var(--surface-sunken);
}
.markdown-body :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 14px;
  border-left: 3px solid var(--line-accent);
  background: var(--surface-accent);
  color: var(--text-subtle);
}
.markdown-body :deep(img) {
  max-width: 100%;
  height: auto;
}
.markdown-body :deep(.katex-display) {
  overflow-x: auto;
  overflow-y: hidden;
  max-width: 100%;
}
.markdown-body :deep(a) {
  color: var(--accent);
}
@media (max-width: 600px) {
  .conversation-toolbar {
    padding: 8px 12px;
    min-height: 48px;
  }
  .conversation-messages {
    padding: 16px 12px;
  }
  .conversation-message {
    gap: 8px;
  }
  .message-avatar {
    width: 28px;
    height: 28px;
    font-size: 18px;
  }
  .message-bubble {
    max-width: calc(100% - 36px);
    padding: 12px;
    font-size: 13px;
  }
  .conversation-input {
    padding: 12px;
  }
  .chat-input-row {
    gap: 8px;
  }
  .send-button {
    min-width: 58px;
    padding: 8px;
  }
  .toolbar-actions .el-select {
    width: 112px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .typing-dots i,
  .streaming-dot {
    animation: none;
  }
}
</style>
