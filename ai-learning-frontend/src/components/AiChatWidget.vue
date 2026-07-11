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
        <el-select v-if="!hideKbSelector" v-model="selectedKbId" size="small" class="kb-select" placeholder="选择知识库" :disabled="!canUseAssistant" @change="handleKbChange" style="width: 130px">
          <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-select v-model="currentMode" size="small" class="mode-select" @change="handleModeChange" style="width: 130px">
          <el-option label="知识库 RAG" value="knowledge" />
          <el-option label="Agent 智能体" value="agent" />
        </el-select>
        <el-button v-if="collapsible" circle size="small" class="collapse-btn" @click="emit('toggleCollapse')">
          <el-icon><ArrowDown v-if="collapsed" /><ArrowUp v-else /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- Mode switch for floating dialog -->
    <div class="dialog-top-bar" v-else>
      <div class="dialog-left">
        <el-radio-group v-model="currentMode" size="small" @change="handleModeChange">
          <el-radio-button label="knowledge">知识库问答 (RAG)</el-radio-button>
          <el-radio-button label="agent">Agent 智能助手</el-radio-button>
        </el-radio-group>
        <el-select v-if="!hideKbSelector" v-model="selectedKbId" size="small" placeholder="选择知识库" :disabled="!canUseAssistant" @change="handleKbChange" style="width: 140px; margin-left: 12px">
          <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
      </div>
      <el-button type="primary" link size="small" @click="handleNewSession">
        <el-icon><RefreshRight /></el-icon> 新对话
      </el-button>
    </div>

    <!-- Chat Messages Box -->
    <div class="chat-messages" ref="messagesContainer" v-show="!collapsed">
      <div class="welcome-msg" v-if="messages.length === 0">
        <div class="welcome-icon"><el-icon><ChatDotRound /></el-icon></div>
        <h4 v-if="!canUseAssistant">登录后使用 AI 知识助手</h4>
        <h4 v-else-if="isAgentMode">您好，我是系统 Agent 助手！</h4>
        <h4 v-else-if="selectedKbId">您好，我是您的专属 AI 助手！</h4>
        <h4 v-else>请先选择一个知识库</h4>
        <p v-if="!canUseAssistant">登录后即可选择课程知识库，进行答疑、总结和智能检索。</p>
        <p v-else-if="isAgentMode">可以帮你规划课程、作业、考试、通知等系统操作，并在确认后执行。</p>
        <p v-else-if="selectedKbId">您可以问我任何关于课程内容、习题解答，或让我帮您搜索文献、编写大纲。</p>
        <p v-else>在顶部下拉菜单中选择要使用的知识库</p>
        <div class="quick-prompts" v-if="canUseAssistant && isAgentMode">
          <span class="prompt-chip" @click="sendQuick('帮我创建一门课程，先给出待确认操作计划。')">创建课程计划</span>
          <span class="prompt-chip" @click="sendQuick('帮我发布一条课程通知，先生成待确认操作。')">发布通知计划</span>
        </div>
        <div class="quick-prompts" v-else-if="canUseAssistant && selectedKbId">
          <span class="prompt-chip" @click="sendQuick('请帮我总结一下本课程的核心知识点。')">总结核心知识点</span>
          <span class="prompt-chip" @click="sendQuick('本课程的重点和难点是什么？')">重点和难点是什么</span>
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
          <div class="bubble-content" v-if="displayMessageContent(msg)">
            <MdPreview :modelValue="proxyLocalUrls(displayMessageContent(msg))" previewTheme="default" />
          </div>
          <div class="typing-indicator" v-else-if="msg.streaming">
            <span></span><span></span><span></span>
          </div>
          <div
            v-else-if="msg.role === 'assistant' && hasActionHint(msg.content) && !hasConfirmableAction(msg)"
            class="action-confirm-panel pending"
          >
            <div>
              <div class="action-confirm-title">待确认操作</div>
              <div class="action-confirm-desc">确认信息生成中，请稍后再试。</div>
            </div>
          </div>
          <div class="error-text" v-if="msg.error">{{ msg.errorMessage || '请求失败，请稍后重试或检查后台服务。' }}</div>
          <div v-if="msg.role === 'assistant' && hasConfirmableAction(msg)" class="action-confirm-panel">
            <div>
              <div class="action-confirm-title">待确认操作</div>
              <div class="action-confirm-desc">执行前请核对 AI 生成的操作计划。</div>
            </div>
            <el-button size="small" type="danger" @click="openActionDialog(msg)">确认执行</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- Chat Input Box -->
    <div class="chat-input-area" v-show="!collapsed">
      <el-input
        v-model="inputQuery"
        type="textarea"
        :rows="inline ? 2 : 3"
        :placeholder="inputPlaceholder"
        resize="none"
        :disabled="!canSendMessage"
        @keydown.ctrl.enter="handleSend"
      />
      <div class="input-actions">
        <span class="tip-text">{{ inputTip }}</span>
        <el-button
          type="primary"
          class="send-btn"
          :loading="loading"
          :disabled="!canSendMessage || !inputQuery.trim()"
          @click="handleSend"
        >
          发送
          <el-icon class="el-icon--right"><Position /></el-icon>
        </el-button>
      </div>
    </div>

    <el-dialog v-model="actionDialogVisible" title="确认指令" width="520px" append-to-body destroy-on-close>
      <div class="action-summary-card">
        <div class="action-summary-row">
          <span>指令模式</span>
          <strong>待确认</strong>
        </div>
        <div class="action-summary-row">
          <span>指令类型</span>
          <strong>{{ actionCrudType(actionForm.actionCode) }}</strong>
        </div>
        <div class="action-summary-row action-summary-content">
          <span>指令内容</span>
          <strong>{{ actionForm.summaryContent || '已识别系统指令' }}</strong>
        </div>
      </div>
      <el-alert
        v-if="actionForm.confirmationText"
        class="action-warning"
        type="warning"
        show-icon
        :closable="false"
        :title="`该操作会自动提交确认文本：${actionForm.confirmationText}`"
      />
      <div v-if="hasActionParameterSchema" class="action-parameter-card">
        <div class="action-parameter-header">
          <span>指令参数</span>
          <span>{{ actionParameterCount }} 项</span>
        </div>
        <div v-if="actionForm.missingRequiredFields.length" class="action-missing-fields">
          <span>待补齐</span>
          <el-tag
            v-for="field in actionForm.missingRequiredFields"
            :key="field"
            type="warning"
            size="small"
          >
            {{ field }}
          </el-tag>
        </div>
        <el-form
          v-if="actionForm.parameterSchema.length"
          class="action-parameter-form"
          label-position="top"
        >
          <el-form-item
            v-for="field in actionForm.parameterSchema"
            :key="field.name"
            :label="parameterLabel(field)"
            :class="{ 'is-missing': isMissingParameter(field.name) }"
          >
            <el-select
              v-if="field.enumValues?.length"
              :model-value="actionForm.parameters[field.name]"
              clearable
              @update:model-value="value => updateActionParameter(field, value)"
            >
              <el-option
                v-for="option in field.enumValues"
                :key="option"
                :label="option"
                :value="option"
              />
            </el-select>
            <el-input-number
              v-else-if="field.type === 'integer' || field.type === 'number'"
              :model-value="numberParameterValue(field)"
              :precision="field.type === 'integer' ? 0 : undefined"
              :min="numericConstraint(field.minimum)"
              :max="numericConstraint(field.maximum)"
              controls-position="right"
              @update:model-value="value => updateActionParameter(field, value)"
            />
            <el-switch
              v-else-if="field.type === 'boolean'"
              :model-value="Boolean(actionForm.parameters[field.name])"
              @update:model-value="value => updateActionParameter(field, value)"
            />
            <el-input
              v-else
              :model-value="actionForm.parameters[field.name] || ''"
              :type="longTextParameter(field) ? 'textarea' : 'text'"
              :maxlength="field.maxLength || undefined"
              show-word-limit
              clearable
              @update:model-value="value => updateActionParameter(field, value)"
            />
            <div v-if="field.description" class="action-parameter-help">{{ field.description }}</div>
          </el-form-item>
        </el-form>
        <el-input
          v-model="actionForm.parametersText"
          class="action-parameter-json"
          type="textarea"
          :rows="4"
          @blur="syncParameterTextToForm"
        />
      </div>
      <div v-if="actionResult.visible" class="action-result-card" :class="{ success: actionResult.success, error: !actionResult.success }">
        <div class="action-result-title">{{ actionResult.success ? '完成' : '未完成' }}</div>
        <div class="action-result-message">{{ actionResult.message }}</div>
        <router-link class="action-result-link" :to="actionResult.link">
          打开{{ actionResult.linkText }}
        </router-link>
      </div>
      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirmingAction" @click="submitActionConfirm">确认执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, watch, computed } from 'vue'
import { useWeKnora } from '@/composables/useWeKnora'
import { confirmAgentAction, getAgentPendingAction } from '@/api/agent'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { Cpu, User, ChatDotRound, Position, RefreshRight, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

function proxyLocalUrls(text) {
  if (!text) return ''
  return text.replace(/local:\/\/(\d+)\//g, '/api/weknora/local-file/$1/')
}

const props = defineProps({
  inline: {
    type: Boolean,
    default: false
  },
  knowledgeBaseId: {
    type: String,
    default: ''
  },
  hideKbSelector: {
    type: Boolean,
    default: false
  },
  collapsible: {
    type: Boolean,
    default: false
  },
  collapsed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['toggleCollapse'])

const {
  messages,
  loading,
  knowledgeBases,
  currentSessionId,
  currentAgentSessionId,
  loadKnowledgeBases,
  createNewSession,
  createNewAgentSession,
  sendMessage
} = useWeKnora()

const currentMode = ref('knowledge')
const selectedKbId = ref('')
const inputQuery = ref('')
const messagesContainer = ref(null)
const userStore = useUserStore()
const canUseAssistant = computed(() => userStore.isLoggedIn)
const isAgentMode = computed(() => currentMode.value === 'agent')
const canSendMessage = computed(() => canUseAssistant.value && (isAgentMode.value || !!selectedKbId.value))
const inputPlaceholder = computed(() => {
  if (!canUseAssistant.value) return '请先登录后使用 AI 知识助手'
  return isAgentMode.value ? '输入要交给 Agent 处理的任务，按 Ctrl + Enter 快速发送...' : '输入您的问题，按 Ctrl + Enter 快速发送...'
})
const inputTip = computed(() => {
  if (!canUseAssistant.value) return '登录后可用'
  if (isAgentMode.value) return currentAgentSessionId.value ? '本地 Agent 编排服务' : '将自动创建 Agent 会话'
  return selectedKbId.value ? 'WeKnora 引擎支持' : '请先选择知识库'
})
const actionDialogVisible = ref(false)
const confirmingAction = ref(false)
const actionResult = reactive({
  visible: false,
  success: false,
  message: '',
  link: '/console',
  linkText: '控制台'
})
const actionForm = reactive({
  actionId: '',
  actionCode: '',
  confirmationText: '',
  parametersText: '{}',
  parameters: {},
  parameterSchema: [],
  missingRequiredFields: [],
  requiredAnyOf: [],
  confirmed: true,
  summaryContent: ''
})
const hasActionParameterSchema = computed(() => actionForm.parameterSchema.length > 0 || actionForm.missingRequiredFields.length > 0)
const actionParameterCount = computed(() => Object.keys(actionForm.parameters || {}).length)

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
  if (!canUseAssistant.value) return
  await loadKnowledgeBases()
  if (props.knowledgeBaseId && knowledgeBases.value.some(kb => kb.id === props.knowledgeBaseId)) {
    selectedKbId.value = props.knowledgeBaseId
    await createNewSession(selectedKbId.value, '智能学习助手会话')
    return
  }
  if (knowledgeBases.value.length > 0) {
    selectedKbId.value = knowledgeBases.value[0].id
    await createNewSession(selectedKbId.value, '智能学习助手会话')
  }
})

watch(() => props.knowledgeBaseId, async (kbId) => {
  if (!canUseAssistant.value || !kbId || kbId === selectedKbId.value) return
  if (!knowledgeBases.value.length) {
    await loadKnowledgeBases()
  }
  if (!knowledgeBases.value.some(kb => kb.id === kbId)) return
  selectedKbId.value = kbId
  messages.value = []
  await createNewSession(kbId, '智能学习助手会话')
})

const handleKbChange = async (kbId) => {
  messages.value = []
  if (isAgentMode.value) return
  await createNewSession(kbId, '智能学习助手会话')
}

const handleNewSession = async () => {
  messages.value = []
  if (isAgentMode.value) {
    await createNewAgentSession('Agent 智能助手会话')
    return
  }
  if (!selectedKbId.value) return
  await createNewSession(selectedKbId.value, '智能学习助手会话')
}

const handleModeChange = () => {
  ElMessage.info(`已切换至 ${currentMode.value === 'agent' ? 'Agent 智能体' : '知识库 RAG'} 模式`)
}

const sendQuick = (text) => {
  inputQuery.value = text
  handleSend()
}

function hasActionHint(content) {
  const text = content || ''
  return /(?:actionId|确认ID)\s*[=：:]/i.test(text) || /待确认操作|确认执行|操作计划|指令模式|确认按钮/.test(text)
}

function pendingActionOf(message) {
  return message?.pendingAction || message?.pending_action || null
}

function hasPendingAction(message) {
  return hasConfirmableAction(message)
}

function hasConfirmableAction(message) {
  const pendingAction = pendingActionOf(message)
  if (pendingAction?.actionId || pendingAction?.actionCode) {
    return true
  }
  return false
}

function extractActionId(content) {
  return (content || '').match(/(?:actionId|确认ID)\s*[=：:]\s*([A-Za-z0-9_-]+)/i)?.[1] || ''
}

function extractActionCode(content) {
  return (content || '').match(/actionCode\s*[=：:]\s*([A-Za-z0-9_.-]+)/i)?.[1] || ''
}

function displayMessageContent(message) {
  if (message?.role === 'assistant' && hasActionHint(message.content)) {
    return ''
  }
  return message?.content || ''
}

function executableParameters(parameters) {
  if (!parameters || typeof parameters !== 'object') {
    return {}
  }
  const ignoredKeys = new Set([
    'rawRequest',
    'requiredFields',
    'requiredConstraints',
    'requiredAnyOf',
    'optionalFields',
    'missingRequiredFields',
    'parameterSchema',
    'jsonSchema',
    'parameterExtraction'
  ])
  const values = {}
  Object.entries(parameters).forEach(([key, value]) => {
    if (!ignoredKeys.has(key) && value !== null && value !== undefined && value !== '') {
      values[key] = value
    }
  })
  if (Object.keys(values).length === 0) {
    Object.assign(values, inferParametersFromRawRequest(parameters.rawRequest))
  }
  return values
}

function metadataArray(value) {
  return Array.isArray(value) ? value : []
}

function parameterLabel(field) {
  const label = field?.label || field?.name || ''
  return field?.required ? `${label} *` : label
}

function isMissingParameter(name) {
  return actionForm.missingRequiredFields.some(field => {
    if (field === name) return true
    return String(field || '').split(/\s*或\s*/).includes(name)
  })
}

function numericConstraint(value) {
  if (value === null || value === undefined || value === '') return undefined
  const number = Number(value)
  return Number.isFinite(number) ? number : undefined
}

function numberParameterValue(field) {
  const value = actionForm.parameters[field.name]
  if (value === null || value === undefined || value === '') return undefined
  const number = Number(value)
  return Number.isFinite(number) ? number : undefined
}

function longTextParameter(field) {
  return Number(field?.maxLength || 0) > 120 || String(field?.description || '').length > 24
}

function cleanActionParameters(source) {
  const values = {}
  Object.entries(source || {}).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') {
      values[key] = value
    }
  })
  return values
}

function syncFormParametersToText() {
  actionForm.parametersText = JSON.stringify(cleanActionParameters(actionForm.parameters), null, 2)
}

function syncParameterTextToForm() {
  try {
    actionForm.parameters = cleanActionParameters(parseParametersJson(actionForm.parametersText))
  } catch (e) {
    ElMessage.error(e.message || 'parameters JSON 格式不正确')
    syncFormParametersToText()
  }
}

function updateActionParameter(field, value) {
  if (!field?.name) return
  if (value === null || value === undefined || value === '') {
    delete actionForm.parameters[field.name]
  } else if (field.type === 'integer') {
    const number = Number(value)
    actionForm.parameters[field.name] = Number.isFinite(number) ? Math.trunc(number) : value
  } else if (field.type === 'number') {
    const number = Number(value)
    actionForm.parameters[field.name] = Number.isFinite(number) ? number : value
  } else {
    actionForm.parameters[field.name] = value
  }
  syncFormParametersToText()
}

function inferParametersFromRawRequest(rawRequest) {
  const text = String(rawRequest || '')
  const parameters = {}
  const titleMatch = text.match(/(?:课程名称|课程标题|课程名|课名|标题|名称|名字)\s*(?:是|为|叫|设置为|命名为|=|:|：)?\s*([^，,。；;\n]+)/)
  if (titleMatch?.[1]) {
    parameters.title = titleMatch[1].replace(/\s*(?:分类\s*ID|分类id|分类编号|课程分类|category\s*Id|category_id|categoryId).*$/i, '').trim()
  }
  const categoryMatch = text.match(/(?:分类\s*ID|分类id|分类编号|课程分类ID|课程分类id|课程分类|category\s*Id|category_id|categoryId)\s*(?:是|为|=|:|：)?\s*(\d+)/i)
  if (categoryMatch?.[1]) {
    parameters.categoryId = Number(categoryMatch[1])
  }
  return parameters
}

function applyPendingActionToForm(pendingAction) {
  if (!pendingAction) return
  actionForm.actionId = pendingAction.actionId || actionForm.actionId
  actionForm.actionCode = pendingAction.actionCode || actionForm.actionCode
  actionForm.confirmationText = pendingAction.confirmationText || actionForm.confirmationText
  actionForm.summaryContent = pendingAction.parameters?.rawRequest || actionForm.summaryContent
  actionForm.parameterSchema = metadataArray(pendingAction.parameters?.parameterSchema || pendingAction.parameterSchema)
  actionForm.missingRequiredFields = metadataArray(pendingAction.parameters?.missingRequiredFields || pendingAction.missingRequiredFields)
  actionForm.requiredAnyOf = metadataArray(pendingAction.parameters?.requiredAnyOf || pendingAction.requiredAnyOf)
  const parameters = executableParameters(pendingAction.parameters)
  actionForm.parameters = cleanActionParameters(parameters)
  if (Object.keys(parameters).length > 0) {
    syncFormParametersToText()
  }
}

function resetActionResult() {
  actionResult.visible = false
  actionResult.success = false
  actionResult.message = ''
  actionResult.link = '/console'
  actionResult.linkText = '控制台'
}

async function openActionDialog(message) {
  const pendingAction = pendingActionOf(message)
  actionForm.actionId = pendingAction?.actionId || ''
  actionForm.actionCode = pendingAction?.actionCode || ''
  actionForm.confirmationText = ''
  actionForm.parametersText = '{}'
  actionForm.parameters = {}
  actionForm.parameterSchema = []
  actionForm.missingRequiredFields = []
  actionForm.requiredAnyOf = []
  actionForm.confirmed = true
  actionForm.summaryContent = ''
  resetActionResult()
  applyPendingActionToForm(pendingAction)
  if (!actionForm.actionId.trim() && !actionForm.actionCode.trim()) {
    ElMessage.warning('这条消息没有结构化待确认操作，请重新发送任务生成新的待确认操作。')
    return
  }
  if (actionForm.actionId) {
    try {
      applyPendingActionToForm(unwrapData(await getAgentPendingAction(actionForm.actionId)))
    } catch {
      actionForm.actionId = ''
      actionForm.actionCode = ''
      ElMessage.warning('该待确认操作不存在或已过期，请重新发送任务生成新的待确认操作。')
      return
    }
  }
  actionDialogVisible.value = true
}

function parseParametersJson(text) {
  const value = text.trim()
  if (!value) return {}
  const parsed = JSON.parse(value)
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('parameters 必须是 JSON 对象')
  }
  return parsed
}

function unwrapData(data) {
  while (data && typeof data === 'object' && !Array.isArray(data) && data.data !== undefined) {
    data = data.data
  }
  return data
}

function extractRequestMessage(error, fallback) {
  const data = error?.response?.data
  return data?.msg || data?.message || error?.message || fallback
}

function actionCrudType(actionCode) {
  const operation = String(actionCode || '').split('.').pop()
  const labels = {
    create: 'CREATE（新增）',
    add: 'CREATE（新增）',
    attach: 'CREATE（新增）',
    update: 'UPDATE（修改）',
    status: 'UPDATE（状态变更）',
    sort: 'UPDATE（修改）',
    grade: 'UPDATE（修改）',
    close: 'UPDATE（修改）',
    delete: 'DELETE（删除）',
    remove: 'DELETE（删除）'
  }
  return labels[operation] || 'WRITE（写操作）'
}

function actionLinkFor(actionCode, resultData) {
  const code = String(actionCode || '')
  if (code.startsWith('course.') || code.startsWith('chapter.') || code.startsWith('lesson.') || code.startsWith('course.resource.')) {
    const courseId = resultData?.courseId
    if (courseId) {
      return { link: `/courses/${courseId}`, linkText: '课程详情' }
    }
    return { link: '/courses', linkText: '课程管理' }
  }
  if (code.startsWith('teaching.class.') || code.startsWith('class.student.')) {
    return { link: '/teaching-classes', linkText: '教学班管理' }
  }
  if (code.startsWith('notice.')) {
    return { link: '/notices', linkText: '通知管理' }
  }
  if (code.startsWith('homework.')) {
    return { link: '/homeworks', linkText: '作业管理' }
  }
  if (code.startsWith('exam.session.') || code.startsWith('exam.answer.')) {
    return { link: '/exams', linkText: '考试管理' }
  }
  if (code.startsWith('question.')) {
    return { link: '/questions', linkText: '题库管理' }
  }
  if (code.startsWith('exam.paper.')) {
    return { link: '/papers', linkText: '试卷管理' }
  }
  if (code.startsWith('grade.record.')) {
    return { link: '/my-grades', linkText: '成绩页面' }
  }
  return { link: '/console', linkText: '控制台' }
}

function showActionResult(success, message, resultData) {
  const target = actionLinkFor(actionForm.actionCode, resultData)
  actionResult.visible = true
  actionResult.success = success
  actionResult.message = message || (success ? '操作已完成' : '操作未完成')
  actionResult.link = target.link
  actionResult.linkText = target.linkText
}

async function submitActionConfirm() {
  let parameters
  try {
    if (hasActionParameterSchema.value) {
      syncFormParametersToText()
    }
    parameters = parseParametersJson(actionForm.parametersText)
  } catch (e) {
    ElMessage.error(e.message || 'parameters JSON 格式不正确')
    return
  }
  if (!actionForm.actionId.trim() && !actionForm.actionCode.trim()) {
    ElMessage.warning('actionId 和 actionCode 至少填写一个')
    return
  }
  confirmingAction.value = true
  try {
    const response = await confirmAgentAction({
      actionId: actionForm.actionId.trim() || null,
      actionCode: actionForm.actionCode.trim() || null,
      confirmed: true,
      confirmationText: actionForm.confirmationText.trim() || null,
      parameters
    })
    const result = unwrapData(response)
    showActionResult(true, result?.message || '动作已执行', result?.data)
    ElMessage.success(actionResult.message)
  } catch (e) {
    const message = extractRequestMessage(e, '动作执行失败')
    showActionResult(false, message, null)
    if (!/待确认操作不存在|待确认操作已过期/.test(message)) {
      ElMessage.error(message)
    }
  } finally {
    confirmingAction.value = false
  }
}

const handleSend = async () => {
  const query = inputQuery.value.trim()
  if (!canUseAssistant.value) {
    ElMessage.warning('请先登录后使用 AI 知识助手')
    return
  }
  if (!query || loading.value) return
  if (!isAgentMode.value && !selectedKbId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }

  inputQuery.value = ''
  try {
    if (!isAgentMode.value && !currentSessionId.value) {
      await createNewSession(selectedKbId.value, '智能学习助手会话')
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

.header-controls {
  display: flex;
  gap: 8px;
}

.kb-select :deep(.el-input__wrapper),
.mode-select :deep(.el-input__wrapper) {
  background: rgba(255,255,255,0.2);
  border: 1px solid rgba(255,255,255,0.3);
  box-shadow: none;
}

.kb-select :deep(.el-input__inner),
.mode-select :deep(.el-input__inner) {
  color: #fff;
}

.kb-select :deep(.el-select__caret),
.mode-select :deep(.el-select__caret) {
  color: rgba(255,255,255,0.7);
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

.dialog-left {
  display: flex;
  align-items: center;
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

.action-confirm-panel {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #E5E7EB;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.action-confirm-title {
  font-size: 0.86rem;
  font-weight: 600;
  color: #111827;
}

.action-confirm-desc {
  margin-top: 2px;
  font-size: 0.76rem;
  line-height: 1.4;
  color: #6B7280;
}

.action-warning {
  margin-bottom: 16px;
}

.action-summary-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  background: #F8FAFC;
}

.action-summary-row {
  display: grid;
  grid-template-columns: 80px 1fr;
  gap: 10px;
  align-items: start;
  font-size: 0.9rem;
}

.action-summary-row span {
  color: #6B7280;
}

.action-summary-row strong {
  color: #111827;
  font-weight: 600;
  word-break: break-word;
}

.action-parameter-card {
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  background: #FFFFFF;
}

.action-parameter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 0.86rem;
  font-weight: 600;
  color: #111827;
}

.action-parameter-header span:last-child {
  color: #6B7280;
  font-size: 0.76rem;
  font-weight: 500;
}

.action-missing-fields {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
  font-size: 0.78rem;
  color: #92400E;
}

.action-parameter-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px 12px;
}

.action-parameter-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.action-parameter-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  line-height: 1.3;
  font-size: 0.78rem;
  color: #374151;
}

.action-parameter-form :deep(.el-form-item.is-missing .el-form-item__label) {
  color: #B45309;
  font-weight: 700;
}

.action-parameter-form :deep(.el-input-number),
.action-parameter-form :deep(.el-select) {
  width: 100%;
}

.action-parameter-help {
  margin-top: 4px;
  color: #6B7280;
  font-size: 0.74rem;
  line-height: 1.35;
}

.action-parameter-json {
  margin-top: 12px;
}

.action-result-card {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  background: #F8FAFC;
}

.action-result-card.success {
  border-color: #A7F3D0;
  background: #ECFDF5;
}

.action-result-card.error {
  border-color: #FECACA;
  background: #FEF2F2;
}

.action-result-title {
  font-weight: 700;
  color: #111827;
}

.action-result-message {
  margin-top: 6px;
  color: #6B7280;
  line-height: 1.5;
}

.action-result-link {
  display: inline-flex;
  margin-top: 10px;
  font-weight: 600;
  color: #4F46E5;
}

/* Markdown rendered content */
:deep(.bubble-content .md-editor-preview) { background: transparent; padding: 0; }
:deep(.bubble-content .md-editor-preview a) { color: #4F46E5; }

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
