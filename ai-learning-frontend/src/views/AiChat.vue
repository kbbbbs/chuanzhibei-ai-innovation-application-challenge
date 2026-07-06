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
          <el-tooltip content="动作确认" placement="bottom">
            <el-button circle @click="openActionDialog()">
              <el-icon><CircleCheck /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="Agent 管理" placement="bottom">
            <el-button circle @click="openAgentDrawer">
              <el-icon><Setting /></el-icon>
            </el-button>
          </el-tooltip>
          <el-switch
            v-model="streamEnabled"
            class="stream-switch"
            active-text="流式"
            inactive-text="普通"
            :disabled="loading"
          />
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
              <div class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
              <div class="message-tools">
                <el-button size="small" text @click="copyMessage(msg.content)">
                  <el-icon><CopyDocument /></el-icon>
                  <span>复制</span>
                </el-button>
                <el-button v-if="msg.failed && msg.retryContent" size="small" text type="warning" @click="retryMessage(msg.retryContent)">
                  <el-icon><Refresh /></el-icon>
                  <span>重试</span>
                </el-button>
                <el-button size="small" text type="primary" @click="openActionDialog(msg)">
                  <template v-if="hasActionHint(msg.content)">
                    <el-icon><CircleCheck /></el-icon>
                    <span>确认动作</span>
                  </template>
                  <template v-else>
                    <el-icon><CircleCheck /></el-icon>
                    <span>动作确认</span>
                  </template>
                </el-button>
              </div>
              <div v-if="hasActionHint(msg.content)" class="message-hint">
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

        <div v-if="loading && !streamEnabled" class="message-wrapper message-ai">
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
          v-if="loading && streamEnabled"
          class="send-btn"
          type="danger"
          @click="stopStreaming"
        >
          <el-icon><Close /></el-icon>
          停止
        </el-button>
        <el-button
          v-else
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

    <el-dialog v-model="actionDialogVisible" title="确认 Agent 动作" width="640px" destroy-on-close>
      <el-alert
        class="action-warning"
        type="warning"
        show-icon
        :closable="false"
        title="确认前请核对 actionId、actionCode 和参数。高风险动作需要填写后端要求的确认文本。"
      />
      <el-form label-width="120px">
        <el-form-item label="actionId">
          <el-input v-model="actionForm.actionId" placeholder="AI 回复中提示的 actionId" />
        </el-form-item>
        <el-form-item label="actionCode">
          <el-input v-model="actionForm.actionCode" placeholder="没有 actionId 时必须填写" />
        </el-form-item>
        <el-form-item label="确认文本">
          <el-input v-model="actionForm.confirmationText" placeholder="例如：确认执行" />
        </el-form-item>
        <el-form-item label="parameters">
          <el-input v-model="actionForm.parametersText" type="textarea" :rows="6" placeholder='JSON，例如：{"courseId":12}' />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="actionForm.confirmed">我已核对并确认执行</el-checkbox>
        </el-form-item>
      </el-form>
      <pre v-if="actionResultText" class="action-result">{{ actionResultText }}</pre>
      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="confirmingAction" @click="submitActionConfirm">确认执行</el-button>
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
  Close,
  CircleCheck,
  CopyDocument,
  Delete,
  EditPen,
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
  getAgentSessions,
  sendAgentMessage,
  streamAgentMessage,
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
const streamEnabled = ref(false)
const streamAbortController = ref(null)
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
const confirmingAction = ref(false)
const actionResultText = ref('')
const actionForm = reactive({
  actionId: '',
  actionCode: '',
  confirmationText: '',
  parametersText: '{}',
  confirmed: true
})

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
      await switchSession(sessions.value[0])
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
    ElMessage.warning('回复生成中，请先停止或等待完成')
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

    if (streamEnabled.value) {
      shouldRefreshHistory = await sendStreamMessage(content)
    } else {
      await sendNormalMessage(content)
      shouldRefreshHistory = true
    }

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
  messages.value.push({
    id: payload?.assistantMessageId,
    localId: `assistant_${Date.now()}`,
    role: 'assistant',
    content: normalizeReply(payload),
    createdAt: payload?.createTime || new Date().toISOString()
  })
}

async function sendStreamMessage(content) {
  const controller = new AbortController()
  streamAbortController.value = controller
  const assistantMessage = {
    localId: `assistant_stream_${Date.now()}`,
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString()
  }
  messages.value.push(assistantMessage)
  try {
    await streamAgentMessage(currentSessionId.value, { content }, {
      signal: controller.signal,
      onMessage(chunk) {
        assistantMessage.content += chunk
        scrollToBottom()
      },
      onComplete(payload) {
        const data = unwrapData(payload)
        assistantMessage.id = data?.assistantMessageId
        assistantMessage.content = normalizeReply(data) || assistantMessage.content
        assistantMessage.createdAt = data?.createTime || assistantMessage.createdAt
      },
      onError(error) {
        const message = typeof error === 'string' ? error : (error?.msg || error?.message)
        assistantMessage.content = message || '流式回复失败，请稍后重试。'
      }
    })
    return true
  } catch (e) {
    if (controller.signal.aborted) {
      assistantMessage.content = assistantMessage.content
        ? assistantMessage.content + '\n\n已停止生成。'
        : '已停止生成。'
      return false
    }
    throw e
  } finally {
    if (streamAbortController.value === controller) {
      streamAbortController.value = null
    }
  }
}

function stopStreaming() {
  streamAbortController.value?.abort()
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
  return /actionId\s*[=：:]/i.test(text) || /待确认操作|确认执行|操作计划/.test(text)
}

function extractActionId(content) {
  return (content || '').match(/actionId\s*[=：:]\s*([A-Za-z0-9_-]+)/i)?.[1] || ''
}

function extractActionCode(content) {
  return (content || '').match(/actionCode\s*[=：:]\s*([A-Za-z0-9_.-]+)/i)?.[1] || ''
}

function openActionDialog(message) {
  const content = message?.content || ''
  actionForm.actionId = extractActionId(content)
  actionForm.actionCode = extractActionCode(content)
  actionForm.confirmationText = ''
  actionForm.parametersText = '{}'
  actionForm.confirmed = true
  actionResultText.value = ''
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

async function submitActionConfirm() {
  if (!actionForm.confirmed) {
    ElMessage.warning('请先勾选确认执行')
    return
  }
  let parameters
  try {
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
    actionResultText.value = JSON.stringify(result, null, 2)
    ElMessage.success(result?.message || '动作已执行')
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

.stream-switch {
  margin-left: 4px;
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
.upload-result,
.action-result {
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

.action-result {
  max-height: 220px;
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

