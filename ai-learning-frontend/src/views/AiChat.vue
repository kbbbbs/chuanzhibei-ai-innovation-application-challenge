<template>
  <div class="ai-chat-page">
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <el-button type="primary" class="new-chat-btn" :loading="creatingSession" @click="handleNewSession">
          <el-icon><Plus /></el-icon>
          <span>新对话</span>
        </el-button>
      </div>
      <div class="session-list" v-loading="sessionsLoading">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: isCurrentSession(session.id), disabled: loading }"
          @click="switchSession(session)"
        >
          <div class="session-info">
            <div class="session-title">{{ session.title || '新对话' }}</div>
            <div class="session-time">{{ formatTime(session.updatedAt || session.createdAt) }}</div>
          </div>
          <div class="session-actions">
            <el-button
              class="session-action"
              text
              size="small"
              @click.stop="renameSession(session)"
            >
              <el-icon><EditPen /></el-icon>
            </el-button>
            <el-button
              class="session-action"
              text
              size="small"
              type="danger"
              @click.stop="deleteSession(session.id)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        <el-empty v-if="!sessionsLoading && sessions.length === 0" description="暂无对话" :image-size="60" />
      </div>
    </div>

    <div class="chat-main">
      <div class="chat-header">
        <el-select
          v-model="selectedAgentId"
          class="agent-selector"
          size="large"
          filterable
          :loading="agentsLoading"
          :disabled="loading || creatingSession"
        >
          <el-option
            v-for="agent in agents"
            :key="agent.id"
            :label="agent.name"
            :value="agent.id"
          >
            <div class="agent-option">
              <span>{{ agent.name }}</span>
              <small v-if="agent.description">{{ agent.description }}</small>
            </div>
          </el-option>
        </el-select>

        <div class="chat-actions">
          <el-tooltip content="刷新会话" placement="bottom">
            <el-button circle @click="refreshChatData">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="单轮调试" placement="bottom">
            <el-button circle @click="openChatOnceDialog">
              <el-icon><ChatLineRound /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="待确认动作" placement="bottom">
            <el-button circle @click="openPendingActionDialog">
              <el-icon><CircleCheck /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="Agent 管理" placement="bottom">
            <el-button circle @click="openAgentDrawer">
              <el-icon><Setting /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>

      <div class="chat-messages" ref="messagesContainer" v-loading="messagesLoading">
        <div v-if="messages.length === 0 && !loading && !messagesLoading" class="chat-placeholder">
          <div class="placeholder-icon">
            <el-icon :size="64"><ChatDotRound /></el-icon>
          </div>
          <h2>AI 学习助手</h2>
          <p>{{ currentAgentDescription }}</p>
        </div>

        <div
          v-for="msg in messages"
          :key="msg.id || msg.localId"
          class="message-wrapper"
          :class="{ 'message-user': msg.role === 'user', 'message-ai': msg.role === 'assistant' }"
        >
          <div v-if="msg.role === 'assistant'" class="message-avatar">
            <el-icon :size="28"><Service /></el-icon>
          </div>
          <div class="message-bubble" :class="msg.role">
            <div v-if="msg.role === 'user'">{{ msg.content }}</div>
            <template v-else>
              <div
                v-if="displayMessageContent(msg)"
                class="markdown-body"
                v-html="renderMarkdown(displayMessageContent(msg))"
              ></div>
              <div v-else-if="msg.streaming" class="message-hint">
                <el-icon><Loading /></el-icon>
                <span>正在生成回复...</span>
              </div>
              <div v-else-if="hasActionHint(msg.content) && !hasConfirmableAction(msg)" class="message-hint">
                <el-icon><Loading /></el-icon>
                <span>确认信息生成中，请稍后再试。</span>
              </div>
              <div class="message-tools">
                <el-button v-if="displayMessageContent(msg)" size="small" text @click="copyMessage(displayMessageContent(msg))">
                  <el-icon><CopyDocument /></el-icon>
                  <span>复制</span>
                </el-button>
                <el-button v-if="msg.failed && msg.retryContent" size="small" text type="warning" @click="retryMessage(msg.retryContent)">
                  <el-icon><Refresh /></el-icon>
                  <span>重试</span>
                </el-button>
                <el-button v-if="hasConfirmableAction(msg)" size="small" text type="primary" @click="openActionDialog(msg)">
                  <el-icon><CircleCheck /></el-icon>
                  <span>确认动作</span>
                </el-button>
              </div>
              <div v-if="hasConfirmableAction(msg)" class="message-hint">
                  <el-icon><CircleCheck /></el-icon>
                  <span>检测到待确认操作，请核对后再执行。</span>
              </div>
            </template>
          </div>
          <el-avatar
            v-if="msg.role === 'user'"
            :size="36"
            class="message-avatar user-avatar"
            :src="currentUserAvatar"
          >
            {{ currentUserInitial }}
          </el-avatar>
        </div>

        <div v-if="loading" class="message-wrapper message-ai">
          <div class="message-avatar">
            <el-icon :size="28"><Service /></el-icon>
          </div>
          <div class="message-bubble assistant typing-indicator">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </div>
        </div>
      </div>

      <div class="chat-input-area">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="3"
          placeholder="输入你的问题，按 Enter 发送，Shift+Enter 换行"
          :disabled="loading || messagesLoading"
          @keydown.enter.exact="handleKeyDown"
          resize="none"
        />
        <el-button
          class="send-btn"
          type="primary"
          :disabled="!inputMessage.trim() || loading || messagesLoading"
          :loading="loading"
          @click="sendMessage"
        >
          发送
        </el-button>
      </div>
    </div>

    <el-drawer v-model="agentDrawerVisible" title="Agent 管理" size="760px" class="agent-drawer">
      <div class="agent-admin">
        <div class="admin-toolbar">
          <el-input
            v-model="agentKeyword"
            placeholder="搜索编码或名称"
            clearable
            class="agent-search"
            @keyup.enter="loadAgentDefinitions"
            @clear="loadAgentDefinitions"
          />
          <el-select v-model="agentStatusFilter" placeholder="状态" class="status-filter" @change="loadAgentDefinitions">
            <el-option label="全部" value="" />
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button @click="loadAgentDefinitions">
            <el-icon><Refresh /></el-icon>
          </el-button>
          <el-button type="primary" :disabled="!isAdmin" @click="openCreateAgent">
            新增 Agent
          </el-button>
        </div>

        <el-table v-loading="agentDefinitionsLoading" :data="agentDefinitions" height="430">
          <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="code" label="编码" min-width="150" show-overflow-tooltip />
          <el-table-column prop="model" label="模型" min-width="120" show-overflow-tooltip />
          <el-table-column prop="temperature" label="温度" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="openEditAgent(row)">编辑</el-button>
              <el-button size="small" text :disabled="!isAdmin" @click="toggleAgentStatus(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button size="small" text :disabled="!isAdmin" @click="openKnowledgeDialog(row)">上传知识</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-alert
          v-if="!isAdmin"
          class="admin-alert"
          type="info"
          show-icon
          :closable="false"
          title="当前账号不是管理员，只能查看 Agent 定义。"
        />
      </div>
    </el-drawer>

    <el-dialog
      v-model="agentFormVisible"
      :title="agentFormMode === 'create' ? '新增 Agent' : '编辑 Agent'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="agentFormRef" :model="agentForm" :rules="agentFormRules" label-width="120px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="agentForm.code" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="agentForm.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="agentForm.description" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input v-model="agentForm.systemPrompt" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="模型">
          <el-input v-model="agentForm.model" placeholder="不填则使用后端默认模型" />
        </el-form-item>
        <el-form-item label="温度">
          <el-input-number v-model="agentForm.temperature" :min="0" :max="2" :step="0.1" :precision="2" />
        </el-form-item>
        <el-form-item label="知识库 IDs">
          <el-input v-model="agentForm.knowledgeBaseIds" placeholder='例如：[1,2] 或 ["kb-java"]' />
        </el-form-item>
        <el-form-item label="启用工具">
          <el-input v-model="agentForm.enabledTools" type="textarea" :rows="3" placeholder='例如：["course_search"]' />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="agentForm.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="agentFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingAgent" :disabled="!isAdmin" @click="submitAgentForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="knowledgeDialogVisible" title="上传 Agent 知识文档" width="560px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="Agent">
          <el-input v-model="knowledgeForm.agentName" disabled />
        </el-form-item>
        <el-form-item label="知识库 ID">
          <el-input v-model="knowledgeForm.knowledgeBaseId" placeholder="例如：kb-java" />
        </el-form-item>
        <el-form-item label="Markdown">
          <el-upload
            drag
            accept=".md,text/markdown,text/plain"
            :auto-upload="false"
            :limit="1"
            :file-list="knowledgeFileList"
            :on-change="handleKnowledgeFileChange"
            :on-remove="handleKnowledgeFileRemove"
            :on-exceed="handleKnowledgeFileExceed"
          >
            <el-icon class="upload-icon"><Upload /></el-icon>
            <div>拖入或点击选择 Markdown 文件</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <pre v-if="knowledgeUploadResult" class="upload-result">{{ knowledgeUploadResult }}</pre>
      <template #footer>
        <el-button @click="knowledgeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploadingKnowledge" :disabled="!isAdmin" @click="submitKnowledgeUpload">
          上传
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="chatOnceVisible" title="单轮 Agent 调试" width="620px" destroy-on-close>
      <el-input
        v-model="chatOnceContent"
        type="textarea"
        :rows="5"
        maxlength="4000"
        show-word-limit
        placeholder="输入一条不写入会话历史的问题"
      />
      <div v-if="chatOnceReply" class="single-reply markdown-body" v-html="renderMarkdown(chatOnceReply)"></div>
      <template #footer>
        <el-button @click="chatOnceVisible = false">关闭</el-button>
        <el-button type="primary" :loading="chatOnceLoading" @click="runChatOnce">发送</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pendingActionDialogVisible" title="待确认 Agent 动作" width="760px" destroy-on-close>
      <el-table
        v-loading="pendingActionsLoading"
        :data="pendingActions"
        row-key="actionId"
        class="pending-action-table"
        empty-text="暂无待确认动作"
      >
        <el-table-column prop="actionName" label="动作" min-width="120" />
        <el-table-column prop="actionCode" label="编码" min-width="160" />
        <el-table-column label="请求" min-width="180">
          <template #default="{ row }">
            <span class="pending-action-summary">{{ summarizePendingParams(row.parameters) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" min-width="170">
          <template #default="{ row }">{{ formatPendingTime(row.expiresAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openPendingActionConfirm(row)">
              <el-icon><CircleCheck /></el-icon>
              <span>确认</span>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="pendingActionDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="pendingActionsLoading" @click="loadPendingActions">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="actionDialogVisible" title="确认指令" width="520px" destroy-on-close>
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
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  ChatLineRound,
  CircleCheck,
  CopyDocument,
  Delete,
  EditPen,
  Loading,
  Plus,
  Refresh,
  Service,
  Setting,
  Upload
} from '@element-plus/icons-vue'
import {
  addAgentDefinition,
  agentChatOnce,
  confirmAgentAction,
  createAgentSession,
  deleteAgentSession,
  getAgentDefinition,
  getAgentDefinitions,
  getAgentMessages,
  getAgentPendingActions,
  getAgentPendingAction,
  getAgentSessions,
  sendAgentMessage,
  updateAgentDefinition,
  updateAgentDefinitionStatus,
  updateAgentSession,
  uploadAgentKnowledgeDocument
} from '../api/agent'
import { renderMarkdown } from '../utils/markdown'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'

const DEFAULT_AGENT = {
  id: 1,
  code: 'learning_assistant',
  name: '通用学习助教',
  description: '面向学生的通用学习答疑 Agent',
  status: 1
}
const MAX_KNOWLEDGE_FILE_SIZE = 5 * 1024 * 1024

const agents = ref([DEFAULT_AGENT])
const route = useRoute()
const userStore = useUserStore()
const selectedAgentId = ref(DEFAULT_AGENT.id)
const currentSessionId = ref(null)
const inputMessage = ref('')
const loading = ref(false)
const agentsLoading = ref(false)
const sessionsLoading = ref(false)
const messagesLoading = ref(false)
const creatingSession = ref(false)
const messages = ref([])
const sessions = ref([])
const messagesContainer = ref(null)

const agentDrawerVisible = ref(false)
const agentDefinitions = ref([])
const agentDefinitionsLoading = ref(false)
const agentKeyword = ref('')
const agentStatusFilter = ref('')

const agentFormVisible = ref(false)
const agentFormMode = ref('create')
const agentFormRef = ref(null)
const savingAgent = ref(false)
const agentForm = reactive(emptyAgentForm())
const agentFormRules = {
  code: [{ required: true, message: '请输入 Agent 编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入 Agent 名称', trigger: 'blur' }]
}

const knowledgeDialogVisible = ref(false)
const uploadingKnowledge = ref(false)
const knowledgeFileList = ref([])
const selectedKnowledgeFile = ref(null)
const knowledgeUploadResult = ref('')
const knowledgeForm = reactive({
  agentId: null,
  agentName: '',
  knowledgeBaseId: ''
})

const chatOnceVisible = ref(false)
const chatOnceContent = ref('')
const chatOnceReply = ref('')
const chatOnceLoading = ref(false)

const actionDialogVisible = ref(false)
const pendingActionDialogVisible = ref(false)
const pendingActionsLoading = ref(false)
const pendingActions = ref([])
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

const isAdmin = computed(() => {
  const role = localStorage.getItem('roleCode') || localStorage.getItem('role') || ''
  return ['admin', 'ROLE_ADMIN'].includes(role)
})

const currentAgentDescription = computed(() => {
  const agent = agents.value.find(item => sameId(item.id, selectedAgentId.value))
  return agent?.description || '开始一次新的学习对话'
})

const currentUserAvatar = computed(() => userStore.avatar || localStorage.getItem('avatar') || '')
const currentUserInitial = computed(() => {
  const name = userStore.nickname || localStorage.getItem('nickname') || localStorage.getItem('username') || 'U'
  return name.charAt(0).toUpperCase()
})

function unwrapData(response) {
  const data = response?.data ?? response
  return data?.data ?? data
}

function pendingActionFromPayload(payload) {
  const data = unwrapData(payload)
  return data?.pendingAction || data?.pending_action || null
}

function toArray(value) {
  const data = unwrapData(value)
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.records)) return data.records
  if (Array.isArray(data?.list)) return data.list
  if (Array.isArray(data?.messages)) return data.messages
  return []
}

function sameId(left, right) {
  return String(left) === String(right)
}

function trimTitle(content) {
  const title = (content || '新会话').trim()
  return title.length > 24 ? title.slice(0, 24) + '...' : title
}

function normalizeAgent(agent) {
  const normalized = normalizeAgentDefinition(agent)
  return {
    id: normalized.id,
    code: normalized.code,
    name: normalized.name,
    description: normalized.description,
    status: normalized.status
  }
}

function normalizeAgentDefinition(agent = {}) {
  const id = agent.id ?? agent.agentId
  return {
    id,
    code: agent.code || '',
    name: agent.name || agent.title || `Agent ${id}`,
    description: agent.description || '',
    systemPrompt: agent.systemPrompt || agent.system_prompt || '',
    model: agent.model || '',
    temperature: agent.temperature ?? 0.7,
    knowledgeBaseIds: agent.knowledgeBaseIds || agent.knowledge_base_ids || '',
    enabledTools: agent.enabledTools || agent.enabled_tools || '',
    status: Number(agent.status ?? 1)
  }
}

function normalizeSession(session) {
  const id = session.id ?? session.sessionId
  const createdAt = session.createTime || session.createdAt || session.latestTime
  const updatedAt = session.updateTime || session.updatedAt || session.latestTime || createdAt
  return {
    id,
    agentId: session.agentId ?? DEFAULT_AGENT.id,
    title: session.title || session.preview || '新会话',
    createdAt,
    updatedAt
  }
}

function normalizeMessage(message) {
  return {
    id: message.id,
    localId: message.localId || `local_${Date.now()}_${Math.random()}`,
    role: message.role === 'assistant' ? 'assistant' : 'user',
    content: message.content || message.message || message.reply || '',
    pendingAction: message.pendingAction || message.pending_action || null,
    createdAt: message.createTime || message.createdAt
  }
}

function normalizeReply(payload) {
  const data = unwrapData(payload)
  if (typeof data === 'string') return data
  return data?.content || data?.reply || data?.message || '抱歉，未能获取回复。'
}

function timeValue(dateStr) {
  const value = Date.parse(dateStr || '')
  return Number.isNaN(value) ? 0 : value
}

function sortSessions() {
  sessions.value.sort((a, b) => {
    return timeValue(b.updatedAt || b.createdAt) - timeValue(a.updatedAt || a.createdAt)
  })
}

function upsertSession(session) {
  if (!session?.id) return
  const index = sessions.value.findIndex(item => sameId(item.id, session.id))
  if (index >= 0) {
    sessions.value[index] = { ...sessions.value[index], ...session }
  } else {
    sessions.value.unshift(session)
  }
  sortSessions()
}

function isCurrentSession(sessionId) {
  return currentSessionId.value != null && sameId(currentSessionId.value, sessionId)
}

async function refreshChatData() {
  await Promise.all([loadAgents(), loadSessions(false)])
  if (currentSessionId.value) {
    await loadSessionMessages(currentSessionId.value)
  }
}

async function loadAgents() {
  agentsLoading.value = true
  try {
    const response = await getAgentDefinitions({ status: 1 })
    const list = toArray(response).map(normalizeAgent).filter(agent => agent.id)
    if (list.length > 0) {
      agents.value = list
      if (!agents.value.some(agent => sameId(agent.id, selectedAgentId.value))) {
        selectedAgentId.value = agents.value[0].id
      }
    }
  } catch (e) {
    agents.value = [DEFAULT_AGENT]
    selectedAgentId.value = DEFAULT_AGENT.id
  } finally {
    agentsLoading.value = false
  }
}

async function loadSessions(activateFirst = false) {
  sessionsLoading.value = true
  try {
    const response = await getAgentSessions()
    sessions.value = toArray(response).map(normalizeSession).filter(session => session.id)
    sortSessions()

    const currentStillExists = sessions.value.some(session => sameId(session.id, currentSessionId.value))
    if (!currentStillExists) {
      currentSessionId.value = null
      messages.value = []
    }

    if (activateFirst && sessions.value.length > 0) {
      const defaultAgentSession = sessions.value.find(session => sameId(session.agentId, selectedAgentId.value))
      if (defaultAgentSession) {
        await switchSession(defaultAgentSession)
      }
    }
  } finally {
    sessionsLoading.value = false
  }
}

async function createSession(title = '新会话') {
  const response = await createAgentSession({
    agentId: selectedAgentId.value,
    title: trimTitle(title)
  })
  const session = normalizeSession(unwrapData(response))
  if (!session.id) {
    throw new Error('Invalid agent session response')
  }
  upsertSession(session)
  currentSessionId.value = session.id
  selectedAgentId.value = session.agentId || selectedAgentId.value
  messages.value = []
  return session
}

async function handleNewSession() {
  creatingSession.value = true
  try {
    await createSession()
  } catch (e) {
    ElMessage.error('新建对话失败')
  } finally {
    creatingSession.value = false
  }
}

async function switchSession(session) {
  if (!session || isCurrentSession(session.id)) return
  if (loading.value) {
    ElMessage.warning('回复生成中，请等待完成')
    return
  }
  currentSessionId.value = session.id
  selectedAgentId.value = session.agentId || selectedAgentId.value
  await loadSessionMessages(session.id)
}

async function loadSessionMessages(sessionId) {
  messagesLoading.value = true
  messages.value = []
  try {
    const response = await getAgentMessages(sessionId)
    messages.value = toArray(response).map(normalizeMessage)
  } finally {
    messagesLoading.value = false
    await nextTick()
    scrollToBottom()
  }
}

async function renameSession(session) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新的会话标题', '重命名对话', {
      inputValue: session.title || '新会话',
      inputValidator: value => !!value?.trim() && value.trim().length <= 100,
      inputErrorMessage: '标题不能为空，且不能超过 100 字',
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    const title = value.trim()
    const response = await updateAgentSession(session.id, { title })
    const updated = normalizeSession(unwrapData(response))
    upsertSession({ ...session, ...updated, title })
    ElMessage.success('已重命名')
  } catch (e) {
    // User cancelled or request failed; Element Plus/request interceptor already handles visible errors.
  }
}

function deleteSession(sessionId) {
  ElMessageBox.confirm('确定要删除这个对话吗？', '确认删除', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteAgentSession(sessionId)
    const wasActive = isCurrentSession(sessionId)
    sessions.value = sessions.value.filter(session => !sameId(session.id, sessionId))
    if (wasActive) {
      const nextSession = sessions.value[0]
      if (nextSession) {
        await switchSession(nextSession)
      } else {
        currentSessionId.value = null
        messages.value = []
      }
    }
    ElMessage.success('已删除')
  }).catch(() => {})
}

async function sendMessage() {
  const content = inputMessage.value.trim()
  if (!content || loading.value) return

  loading.value = true
  let userMessageAdded = false
  let shouldRefreshHistory = false
  try {
    if (!currentSessionId.value) {
      await createSession(content)
    }

    const activeSession = sessions.value.find(session => sameId(session.id, currentSessionId.value))
    if (activeSession && messages.value.length === 0) {
      activeSession.title = trimTitle(content)
      activeSession.updatedAt = new Date().toISOString()
      sortSessions()
    }

    messages.value.push({
      localId: `user_${Date.now()}`,
      role: 'user',
      content,
      createdAt: new Date().toISOString()
    })
    userMessageAdded = true
    inputMessage.value = ''
    await nextTick()
    scrollToBottom()

    shouldRefreshHistory = await sendNormalMessage(content)

    if (activeSession) {
      activeSession.updatedAt = new Date().toISOString()
      sortSessions()
    }
    if (shouldRefreshHistory) {
      await refreshActiveMessages()
    }
  } catch (e) {
    if (userMessageAdded) {
      messages.value.push({
        localId: `assistant_error_${Date.now()}`,
        role: 'assistant',
        content: '请求失败，请稍后重试。',
        failed: true,
        retryContent: content,
        createdAt: new Date().toISOString()
      })
    }
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

async function sendNormalMessage(content) {
  const response = await sendAgentMessage(currentSessionId.value, { content })
  const payload = unwrapData(response)
  const pendingAction = pendingActionFromPayload(response)
  messages.value.push({
    id: payload?.assistantMessageId,
    localId: `assistant_${Date.now()}`,
    role: 'assistant',
    content: normalizeReply(payload),
    pendingAction,
    createdAt: payload?.createTime || new Date().toISOString()
  })
  return !pendingAction
}

async function refreshActiveMessages() {
  if (!currentSessionId.value) return
  try {
    const response = await getAgentMessages(currentSessionId.value)
    const serverMessages = toArray(response).map(normalizeMessage)
    if (serverMessages.length > 0) {
      messages.value = serverMessages
    }
  } catch {
    // Keep optimistic messages when history refresh fails.
  }
}

async function copyMessage(content) {
  if (!content) return
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

async function retryMessage(content) {
  if (loading.value) return
  inputMessage.value = content
  await nextTick()
  await sendMessage()
}

function handleKeyDown(event) {
  if (!event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

function formatTime(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return (date.getMonth() + 1) + '/' + date.getDate()
}

function scrollToBottom() {
  nextTick(() => {
    const el = messagesContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function fillPromptFromRoute() {
  const promptValue = Array.isArray(route.query.prompt) ? route.query.prompt[0] : route.query.prompt
  const prompt = typeof promptValue === 'string' ? promptValue.trim() : ''
  if (prompt) {
    inputMessage.value = prompt.slice(0, 4000)
  }
}

async function openAgentDrawer() {
  agentDrawerVisible.value = true
  await loadAgentDefinitions()
}

async function loadAgentDefinitions() {
  agentDefinitionsLoading.value = true
  try {
    const params = {}
    if (agentKeyword.value.trim()) params.keyword = agentKeyword.value.trim()
    if (agentStatusFilter.value !== '') params.status = agentStatusFilter.value
    const response = await getAgentDefinitions(params)
    agentDefinitions.value = toArray(response).map(normalizeAgentDefinition).filter(agent => agent.id)
  } finally {
    agentDefinitionsLoading.value = false
  }
}

function emptyAgentForm() {
  return {
    id: null,
    code: '',
    name: '',
    description: '',
    systemPrompt: '',
    model: '',
    temperature: 0.7,
    knowledgeBaseIds: '',
    enabledTools: '',
    status: 1
  }
}

function resetAgentForm(agent = {}) {
  Object.assign(agentForm, emptyAgentForm(), agent)
}

function openCreateAgent() {
  resetAgentForm()
  agentFormMode.value = 'create'
  agentFormVisible.value = true
}

async function openEditAgent(row) {
  const response = await getAgentDefinition(row.id)
  resetAgentForm(normalizeAgentDefinition(unwrapData(response)))
  agentFormMode.value = 'edit'
  agentFormVisible.value = true
}

function buildAgentPayload() {
  return {
    code: agentForm.code.trim(),
    name: agentForm.name.trim(),
    description: agentForm.description?.trim() || '',
    systemPrompt: agentForm.systemPrompt || '',
    model: agentForm.model?.trim() || '',
    temperature: agentForm.temperature,
    knowledgeBaseIds: agentForm.knowledgeBaseIds || '',
    enabledTools: agentForm.enabledTools || '',
    status: Number(agentForm.status)
  }
}

async function submitAgentForm() {
  if (!isAdmin.value) {
    ElMessage.warning('只有管理员可以维护 Agent 定义')
    return
  }
  await agentFormRef.value?.validate()
  savingAgent.value = true
  try {
    if (agentFormMode.value === 'create') {
      await addAgentDefinition(buildAgentPayload())
      ElMessage.success('Agent 已创建')
    } else {
      await updateAgentDefinition(agentForm.id, buildAgentPayload())
      ElMessage.success('Agent 已保存')
    }
    agentFormVisible.value = false
    await Promise.all([loadAgents(), loadAgentDefinitions()])
  } finally {
    savingAgent.value = false
  }
}

async function toggleAgentStatus(row) {
  if (!isAdmin.value) {
    ElMessage.warning('只有管理员可以维护 Agent 定义')
    return
  }
  const nextStatus = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确定要${nextStatus === 1 ? '启用' : '停用'}这个 Agent 吗？`, '状态变更', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
  await updateAgentDefinitionStatus(row.id, nextStatus)
  ElMessage.success('状态已更新')
  await Promise.all([loadAgents(), loadAgentDefinitions()])
}

function openKnowledgeDialog(row) {
  knowledgeForm.agentId = row.id
  knowledgeForm.agentName = row.name
  knowledgeForm.knowledgeBaseId = ''
  knowledgeFileList.value = []
  selectedKnowledgeFile.value = null
  knowledgeUploadResult.value = ''
  knowledgeDialogVisible.value = true
}

function handleKnowledgeFileChange(file, fileList) {
  const rawFile = file.raw
  knowledgeUploadResult.value = ''
  if (!rawFile) {
    knowledgeFileList.value = []
    selectedKnowledgeFile.value = null
    return
  }
  if (!rawFile.name.toLowerCase().endsWith('.md')) {
    ElMessage.warning('请上传 .md Markdown 文件')
    knowledgeFileList.value = []
    selectedKnowledgeFile.value = null
    return
  }
  if (rawFile.size > MAX_KNOWLEDGE_FILE_SIZE) {
    ElMessage.warning('Markdown 文件不能超过 5MB')
    knowledgeFileList.value = []
    selectedKnowledgeFile.value = null
    return
  }
  knowledgeFileList.value = fileList.slice(-1)
  selectedKnowledgeFile.value = knowledgeFileList.value[0]?.raw || null
}

function handleKnowledgeFileRemove() {
  knowledgeFileList.value = []
  selectedKnowledgeFile.value = null
  knowledgeUploadResult.value = ''
}

function handleKnowledgeFileExceed() {
  ElMessage.warning('每次只能上传一个 Markdown 文件')
}

async function submitKnowledgeUpload() {
  if (!isAdmin.value) {
    ElMessage.warning('只有管理员可以上传知识文档')
    return
  }
  if (!knowledgeForm.knowledgeBaseId.trim()) {
    ElMessage.warning('请输入知识库 ID')
    return
  }
  if (!selectedKnowledgeFile.value) {
    ElMessage.warning('请选择 Markdown 文件')
    return
  }
  uploadingKnowledge.value = true
  try {
    const formData = new FormData()
    formData.append('knowledgeBaseId', knowledgeForm.knowledgeBaseId.trim())
    formData.append('file', selectedKnowledgeFile.value)
    const response = await uploadAgentKnowledgeDocument(knowledgeForm.agentId, formData)
    knowledgeUploadResult.value = JSON.stringify(unwrapData(response), null, 2)
    ElMessage.success('知识文档已上传')
  } finally {
    uploadingKnowledge.value = false
  }
}

function openChatOnceDialog() {
  chatOnceContent.value = ''
  chatOnceReply.value = ''
  chatOnceVisible.value = true
}

async function runChatOnce() {
  const content = chatOnceContent.value.trim()
  if (!content) {
    ElMessage.warning('请输入调试问题')
    return
  }
  chatOnceLoading.value = true
  try {
    const response = await agentChatOnce({ content })
    chatOnceReply.value = normalizeReply(response)
  } finally {
    chatOnceLoading.value = false
  }
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

async function openPendingActionDialog() {
  pendingActionDialogVisible.value = true
  await loadPendingActions()
}

async function loadPendingActions() {
  pendingActionsLoading.value = true
  try {
    pendingActions.value = toArray(await getAgentPendingActions({ limit: 20 }))
  } finally {
    pendingActionsLoading.value = false
  }
}

function openPendingActionConfirm(pendingAction) {
  pendingActionDialogVisible.value = false
  openActionDialog({ pendingAction })
}

function openManualActionDialog() {
  pendingActionDialogVisible.value = false
  openActionDialog()
}

function resetActionResult() {
  actionResult.visible = false
  actionResult.success = false
  actionResult.message = ''
  actionResult.link = '/console'
  actionResult.linkText = '控制台'
}

function summarizePendingParams(parameters) {
  if (!parameters || typeof parameters !== 'object') {
    return '-'
  }
  if (parameters.rawRequest) {
    return String(parameters.rawRequest)
  }
  const keys = Object.keys(parameters)
  return keys.length ? keys.slice(0, 4).join(', ') : '-'
}

function formatPendingTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
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

watch(selectedAgentId, (agentId) => {
  const activeSession = sessions.value.find(session => sameId(session.id, currentSessionId.value))
  if (activeSession?.agentId && !sameId(activeSession.agentId, agentId)) {
    currentSessionId.value = null
    messages.value = []
  }
})

watch(() => route.query.prompt, fillPromptFromRoute)

onMounted(async () => {
  await loadAgents()
  await loadSessions(true)
  fillPromptFromRoute()
})
</script>

<style scoped>
.ai-chat-page {
  display: flex;
  height: calc(100vh - 112px);
  min-height: 640px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.chat-sidebar {
  width: 292px;
  min-width: 292px;
  background: #F8FAFC;
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-elevated);
}

.new-chat-btn {
  width: 100%;
  height: 40px;
  border-radius: var(--radius-md);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  margin-bottom: 8px;
}

.session-item:hover {
  background: var(--bg-elevated);
  border-color: var(--border-color);
}

.session-item.active {
  background: var(--primary-color-soft);
  border-color: var(--primary-color-light);
  box-shadow: var(--shadow-xs);
}

.session-item.disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.session-info {
  flex: 1;
  overflow: hidden;
  margin-right: 8px;
}

.session-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.session-time {
  font-size: 12px;
  color: var(--text-muted);
}

.session-actions {
  display: flex;
  align-items: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.session-item:hover .session-actions,
.session-item.active .session-actions {
  opacity: 1;
}

.session-action {
  width: 26px;
  height: 26px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: linear-gradient(180deg, #FFFFFF 0%, #F8FAFC 100%);
}

.chat-header {
  padding: 14px 18px;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.agent-selector {
  width: min(460px, 100%);
}

.chat-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.chat-actions :deep(.el-button.is-circle) {
  background: var(--bg-subtle);
  border-color: transparent;
}

.chat-actions :deep(.el-button.is-circle:hover) {
  background: var(--primary-color-soft);
  color: var(--primary-color);
}

.agent-option {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 100%;
}

.agent-option span {
  flex-shrink: 0;
  font-weight: 600;
}

.agent-option small {
  min-width: 0;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 28px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.chat-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  text-align: center;
}

.placeholder-icon {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
  background: var(--primary-color-soft);
  margin-bottom: 18px;
}

.chat-placeholder h2 {
  margin: 0 0 8px;
  font-size: 24px;
  color: var(--text-primary);
  font-weight: 700;
}

.chat-placeholder p {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.message-wrapper {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  max-width: min(820px, 84%);
}

.message-wrapper.message-user {
  align-self: flex-end;
  flex-direction: row;
}

.message-wrapper.message-ai {
  align-self: flex-start;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--secondary-color-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--secondary-color);
  flex-shrink: 0;
  border: 1px solid rgba(15, 118, 110, 0.12);
}

.message-avatar.user-avatar {
  background: var(--primary-color-soft);
  color: var(--primary-color);
  border-color: var(--primary-color-light);
  font-weight: 700;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: var(--radius-md);
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
}

.message-bubble.user {
  background: var(--primary-color);
  color: #fff;
  box-shadow: 0 10px 20px rgba(37, 99, 235, 0.16);
}

.message-bubble.assistant {
  background: #fff;
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-xs);
}

.message-tools {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
}

.message-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: var(--accent-color);
  font-size: 12px;
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 14px 20px;
}

.typing-indicator .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: typing-bounce 1.4s infinite ease-in-out both;
}

.typing-indicator .dot:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator .dot:nth-child(2) { animation-delay: -0.16s; }
.typing-indicator .dot:nth-child(3) { animation-delay: 0s; }

@keyframes typing-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.markdown-body {
  font-size: 14px;
  line-height: 1.8;
}

.markdown-body :deep(pre) {
  background: #0F172A;
  color: #E2E8F0;
  border-radius: var(--radius-md);
  padding: 12px 16px;
  overflow-x: auto;
  font-size: 13px;
}

.markdown-body :deep(code) {
  background: var(--bg-subtle);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.markdown-body :deep(pre code) {
  background: none;
  color: inherit;
  padding: 0;
}

.markdown-body :deep(p) {
  margin: 0 0 8px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 4px 0;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--secondary-color);
  margin: 8px 0;
  padding: 4px 12px;
  color: var(--text-secondary);
  background: var(--secondary-color-soft);
}

.chat-input-area {
  padding: 16px 18px;
  background: #fff;
  border-top: 1px solid var(--border-color);
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-input-area .el-textarea {
  flex: 1;
}

.chat-input-area :deep(.el-textarea__inner) {
  min-height: 72px !important;
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 1px var(--border-color) inset;
}

.send-btn {
  height: 40px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
}

.agent-admin {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.admin-toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
}

.agent-search {
  flex: 1;
}

.status-filter {
  width: 120px;
}

.admin-alert {
  margin-top: 4px;
}

.upload-icon {
  font-size: 28px;
  color: var(--primary-color);
}

.single-reply,
.upload-result {
  overflow: auto;
  padding: 12px;
  border-radius: var(--radius-md);
  background: var(--bg-color);
  border: 1px solid var(--border-color);
  font-size: 12px;
}

.single-reply {
  margin-top: 14px;
  max-height: 260px;
}

.upload-result {
  max-height: 180px;
}

.action-warning {
  margin-bottom: 14px;
}

.action-summary-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: #F8FAFC;
}

.action-summary-row {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 12px;
  align-items: start;
  font-size: 14px;
}

.action-summary-row span {
  color: var(--text-secondary);
}

.action-summary-row strong {
  color: var(--text-primary);
  font-weight: 600;
  word-break: break-word;
}

.action-parameter-card {
  margin-bottom: 14px;
  padding: 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
  background: #FFFFFF;
}

.action-parameter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.action-parameter-header span:last-child {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.action-missing-fields {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #92400E;
}

.action-parameter-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px 14px;
}

.action-parameter-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.action-parameter-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  line-height: 1.3;
  font-size: 12px;
  color: var(--text-secondary);
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
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.action-parameter-json {
  margin-top: 12px;
}

.action-result-card {
  margin-top: 14px;
  padding: 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
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
  color: var(--text-primary);
}

.action-result-message {
  margin-top: 6px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.action-result-link {
  display: inline-flex;
  margin-top: 10px;
  font-weight: 600;
  color: var(--primary-color);
}

.pending-action-table {
  width: 100%;
}

.pending-action-summary {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  color: var(--text-secondary);
  text-overflow: ellipsis;
  vertical-align: bottom;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .ai-chat-page {
    flex-direction: column;
    height: auto;
    min-height: calc(100vh - 96px);
  }

  .chat-sidebar {
    width: 100%;
    min-width: 0;
    height: 220px;
    border-right: 0;
    border-bottom: 1px solid var(--border-color);
  }

  .chat-header {
    align-items: stretch;
    flex-direction: column;
  }

  .chat-actions {
    flex-wrap: wrap;
  }

  .chat-messages {
    padding: 18px;
  }

  .message-wrapper {
    max-width: 94%;
  }
}
</style>

