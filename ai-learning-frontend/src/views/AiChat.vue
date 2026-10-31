<template>
  <div class="ai-chat-page" :class="{ 'embedded-mode': props.embedded }">
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <el-button
          type="primary"
          class="new-chat-btn"
          :loading="creatingSession"
          :disabled="!hasAvailableAgent || loading"
          @click="handleNewSession"
        >
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
            <div class="session-meta">
              <div class="session-time">{{ formatTime(session.updatedAt || session.createdAt) }}</div>
              <div class="session-actions">
                <el-button
                  class="session-action"
                  text
                  size="small"
                  aria-label="编辑会话标题"
                  @click.stop="renameSession(session)"
                >
                  <el-icon><EditPen /></el-icon>
                </el-button>
                <el-button
                  class="session-action"
                  text
                  size="small"
                  type="danger"
                  aria-label="删除会话"
                  @click.stop="deleteSession(session.id)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
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
          :disabled="!hasAvailableAgent || loading || creatingSession"
          placeholder="暂无可用 Agent"
        >
          <el-option
            v-for="agent in agents"
            :key="agent.id"
            :label="agent.displayName"
            :value="agent.id"
          >
            <div class="agent-option">
              <span class="agent-option-title">
                {{ agent.name }}
                <el-tag v-if="agent.ragEnabled" size="small" type="success" effect="plain">RAG</el-tag>
              </span>
              <small v-if="agent.description">{{ agent.description }}</small>
            </div>
          </el-option>
        </el-select>

        <div class="chat-actions">
          <el-tooltip content="刷新会话" placement="bottom">
            <el-button circle aria-label="刷新会话" @click="refreshChatData">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip v-if="canUseOperations" content="单轮调试" placement="bottom">
            <el-button circle aria-label="单轮调试" @click="openChatOnceDialog">
              <el-icon><ChatLineRound /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip v-if="canUseOperations" content="待确认动作" placement="bottom">
            <el-button circle aria-label="待确认动作" @click="openPendingActionDialog">
              <el-icon><CircleCheck /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip v-if="canUseOperations" content="Agent 管理" placement="bottom">
            <el-button circle aria-label="Agent 管理" @click="openAgentDrawer">
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
          <h2>{{ hasAvailableAgent ? 'AI 学习助手' : '暂无可用 Agent' }}</h2>
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
            <div v-if="msg.role === 'user'">
              <div v-if="msg.contextText" class="user-context-quote">
                <span>选中的课时原文</span>
                <p>{{ msg.contextText }}</p>
              </div>
              <div>{{ msg.displayContent || msg.content }}</div>
            </div>
            <template v-else>
              <div
                v-if="hasConfirmableAction(msg)"
                class="message-hint action-pending-hint"
              >
                <el-icon><CircleCheck /></el-icon>
                <span>动作待确认</span>
              </div>
              <div
                v-else-if="displayMessageContent(msg)"
                class="markdown-body"
                v-html="renderMarkdown(displayMessageContent(msg))"
              ></div>
              <div v-else-if="msg.streaming" class="message-hint">
                <el-icon><Loading /></el-icon>
                <span>正在生成…</span>
              </div>
              <RagCitations :citations="msg.citations || []" />
              <RagAnswerStatus :status="msg.knowledgeStatus" :warning="msg.knowledgeWarning" />
              <div v-if="displayMessageContent(msg) || (msg.failed && msg.retryContent)" class="message-tools">
                <el-button v-if="displayMessageContent(msg)" size="small" text @click="copyMessage(displayMessageContent(msg))">
                  <el-icon><CopyDocument /></el-icon>
                  <span>复制</span>
                </el-button>
                <el-button v-if="msg.failed && msg.retryContent" size="small" text type="warning" @click="retryMessage(msg.retryContent)">
                  <el-icon><Refresh /></el-icon>
                  <span>重试</span>
                </el-button>
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
          <el-button v-if="loading && activeChatRequest" @click="activeChatRequest.abort()">停止接收</el-button>
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            :maxlength="inputMaxLength"
            show-word-limit
            :placeholder="hasAvailableAgent ? '输入你的问题，按 Enter 发送，Shift+Enter 换行' : '请联系管理员创建或启用 Agent'"
            :disabled="!hasAvailableAgent || loading || messagesLoading"
            @keydown.enter.exact="handleKeyDown"
            resize="none"
          />
          <el-button
            class="send-btn"
            type="primary"
            :disabled="!hasAvailableAgent || !inputMessage.trim() || loading || messagesLoading"
            :loading="loading"
            @click="sendMessage"
          >
            发送
          </el-button>
        </div>
      </div>
    </div>

    <el-drawer v-if="canUseOperations" v-model="agentDrawerVisible" title="Agent 管理" size="760px" class="agent-drawer">
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
          <el-table-column label="RAG" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.knowledgeBaseIds.length" type="success" effect="plain">
                {{ row.knowledgeBaseIds.length }} 个库
              </el-tag>
              <span v-else class="muted-text">未绑定</span>
            </template>
          </el-table-column>
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
      v-if="canUseOperations"
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
        <el-form-item label="RAG 知识库">
          <el-select
            v-model="agentForm.knowledgeBaseIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :loading="knowledgeBasesLoading"
            placeholder="选择一个或多个课程知识库"
            class="full-width-select"
          >
            <el-option
              v-for="kb in knowledgeBases"
              :key="kb.id"
              :label="kb.name"
              :value="kb.id"
            />
          </el-select>
          <div class="form-help">选中后，Agent 在普通问答时会先检索这些知识库。</div>
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

    <el-dialog v-if="canUseOperations" v-model="knowledgeDialogVisible" title="上传 Agent 知识文档" width="560px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="Agent">
          <el-input v-model="knowledgeForm.agentName" disabled />
        </el-form-item>
        <el-form-item label="课程知识库">
          <el-select
            v-model="knowledgeForm.knowledgeBaseId"
            filterable
            :loading="knowledgeBasesLoading"
            placeholder="选择知识库"
            class="full-width-select"
          >
            <el-option
              v-for="kb in writableKnowledgeBases"
              :key="kb.id"
              :label="kb.name"
              :value="kb.id"
            />
          </el-select>
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

    <el-dialog v-if="canUseOperations" v-model="chatOnceVisible" title="单轮 Agent 调试" width="620px" destroy-on-close>
      <el-alert
        class="chat-once-alert"
        type="info"
        show-icon
        :closable="false"
        :title="`使用“${currentAgentDisplayName}”的提示词、模型与 RAG 配置；本轮不写入会话历史。`"
      />
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

    <el-dialog v-if="canUseOperations" v-model="pendingActionDialogVisible" title="待确认 Agent 动作" width="760px" destroy-on-close>
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

    <el-dialog
      v-if="canUseOperations"
      v-model="actionDialogVisible"
      title="确认动作"
      width="min(480px, calc(100vw - 32px))"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="action-summary-card">
        <div class="action-summary-row">
          <span>操作类型</span>
          <strong>{{ actionCrudType(actionForm.actionCode) }}</strong>
        </div>
        <div class="action-summary-row action-summary-content">
          <span>操作内容</span>
          <strong>{{ actionForm.summaryContent || '已识别系统指令' }}</strong>
        </div>
      </div>
      <el-alert
        v-if="actionForm.confirmationText"
        class="action-warning"
        type="warning"
        show-icon
        :closable="false"
        :title="`高风险操作确认词：${actionForm.confirmationText}`"
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
      </div>
      <div v-if="actionResult.visible" class="action-result-card error">
        <div class="action-result-title">执行失败</div>
        <div class="action-result-message">{{ actionResult.message }}</div>
      </div>
      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirmingAction" @click="submitActionConfirm">确认动作</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  ChatLineRound,
  CircleCheck,
  CopyDocument,
  Delete,
  Document,
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
import { listKnowledgeBases } from '@/api/knowledgeBase'
import RagCitations from '@/components/knowledge/RagCitations.vue'
import RagAnswerStatus from '@/components/knowledge/RagAnswerStatus.vue'
import { renderMarkdown } from '../utils/markdown'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { buildAgentContextPrompt, parseAgentContextPrompt } from '@/utils/agentContext'

const props = defineProps({
  embedded: {
    type: Boolean,
    default: false,
  },
  selectedContext: {
    type: Object,
    default: null,
  },
  draftPrompt: {
    type: String,
    default: '',
  },
  draftVersion: {
    type: Number,
    default: 0,
  },
})
const emit = defineEmits(['context-consumed'])

const DEFAULT_AGENT_ID = 1
const MAX_KNOWLEDGE_FILE_SIZE = 5 * 1024 * 1024

const agents = ref([])
const route = useRoute()
const userStore = useUserStore()
const selectedAgentId = ref(null)
const currentSessionId = ref(null)
const inputMessage = ref('')
const loading = ref(false)
const activeChatRequest = ref(null)
onBeforeUnmount(() => activeChatRequest.value?.abort())
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
const knowledgeBases = ref([])
const knowledgeBasesLoading = ref(false)
const writableKnowledgeBases = computed(() => knowledgeBases.value.filter(kb => !kb.read_only && String(kb.owner_id) === String(userStore.userId)))

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
const openingActionDialog = ref(false)
const pendingActionDialogVisible = ref(false)
const pendingActionsLoading = ref(false)
const pendingActions = ref([])
const confirmingAction = ref(false)
const actionResult = reactive({
  visible: false,
  message: ''
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

const isAdmin = computed(() => userStore.isAdmin)
const canUseOperations = computed(() => userStore.isAdmin || userStore.isTeacher)
const selectedContextText = computed(() => String(props.selectedContext?.text || '').trim())
const selectedContextLabel = computed(() => {
  const labels = [props.selectedContext?.courseTitle, props.selectedContext?.lessonTitle].filter(Boolean)
  return labels.join(' · ')
})
const inputMaxLength = computed(() => selectedContextText.value ? 2000 : 4000)
const hasAvailableAgent = computed(() => {
  return selectedAgentId.value != null
    && agents.value.some(agent => sameId(agent.id, selectedAgentId.value))
})

const currentAgentDescription = computed(() => {
  const agent = agents.value.find(item => sameId(item.id, selectedAgentId.value))
  if (!agent) return '请联系管理员创建或启用 Agent 后再开始对话'
  return agent.description || '开始一次新的学习对话'
})
const currentAgentDisplayName = computed(() => {
  return agents.value.find(item => sameId(item.id, selectedAgentId.value))?.displayName || '当前 Agent'
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
  if (Array.isArray(data?.items)) return data.items
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
  const ragEnabled = normalized.knowledgeBaseIds.length > 0
  return {
    id: normalized.id,
    code: normalized.code,
    name: normalized.name,
    description: normalized.description,
    status: normalized.status,
    knowledgeBaseIds: normalized.knowledgeBaseIds,
    ragEnabled,
    displayName: ragEnabled ? `${normalized.name} · RAG` : normalized.name
  }
}

function parseKnowledgeBaseIds(value) {
  let values = value
  if (typeof values === 'string') {
    if (!values.trim()) return []
    try {
      values = JSON.parse(values)
    } catch {
      return []
    }
  }
  if (!Array.isArray(values)) return []
  const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
  return [...new Set(values
    .filter(value => typeof value === 'string')
    .map(value => value.trim().toLowerCase())
    .filter(value => uuidPattern.test(value)))]
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
    knowledgeBaseIds: parseKnowledgeBaseIds(agent.knowledgeBaseIds ?? agent.knowledge_base_ids),
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
    agentId: session.agentId ?? DEFAULT_AGENT_ID,
    title: session.title || session.preview || '新会话',
    createdAt,
    updatedAt
  }
}

function composeContextualPrompt(question) {
  return buildAgentContextPrompt(question, props.selectedContext)
}

function normalizeAssistantCopy(content) {
  if (content === 'AI 回复失败，请稍后重试。') {
    return '暂时无法回复，请重试。'
  }
  return content
}

function normalizeMessage(message) {
  const rawContent = message.content || message.message || message.reply || ''
  const content = message.role === 'assistant' ? normalizeAssistantCopy(rawContent) : rawContent
  const contextual = message.role === 'assistant' ? null : parseAgentContextPrompt(content)
  return {
    id: message.id,
    localId: message.localId || `local_${Date.now()}_${Math.random()}`,
    role: message.role === 'assistant' ? 'assistant' : 'user',
    content,
    contextText: contextual?.contextText || message.contextText || '',
    displayContent: contextual?.displayContent || message.displayContent || '',
    pendingAction: message.pendingAction || message.pending_action || null,
    citations: Array.isArray(message.citations) ? message.citations : [],
    retrievalMode: message.retrievalMode,
    knowledgeStatus: message.knowledgeStatus,
    knowledgeWarning: message.knowledgeWarning,
    createdAt: message.createTime || message.createdAt
  }
}

function normalizeReply(payload) {
  const data = unwrapData(payload)
  if (typeof data === 'string') return normalizeAssistantCopy(data)
  return normalizeAssistantCopy(data?.content || data?.reply || data?.message || '暂时没有回复，请重试。')
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
    agents.value = list
    if (list.length === 0) {
      selectedAgentId.value = null
    } else if (!agents.value.some(agent => sameId(agent.id, selectedAgentId.value))) {
      const preferredAgent = agents.value.find(agent => agent.code === 'learning_assistant')
        || agents.value.find(agent => sameId(agent.id, DEFAULT_AGENT_ID))
        || agents.value[0]
      selectedAgentId.value = preferredAgent.id
    }
  } catch (e) {
    agents.value = []
    selectedAgentId.value = null
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
  } catch (e) {
    sessions.value = []
    currentSessionId.value = null
    messages.value = []
  } finally {
    sessionsLoading.value = false
  }
}

async function createSession(title = '新会话') {
  if (!hasAvailableAgent.value) {
    throw new Error('暂无可用 Agent，请联系管理员创建或启用 Agent')
  }
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
  if (!hasAvailableAgent.value) {
    ElMessage.warning('暂无可用 Agent，请联系管理员创建或启用 Agent')
    return
  }
  creatingSession.value = true
  try {
    await createSession()
  } catch (e) {
    ElMessage.error(e?.message || '新建对话失败')
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
  let pendingMessage = null
  try {
    const response = await getAgentMessages(sessionId)
    messages.value = toArray(response).map(normalizeMessage)
    pendingMessage = latestPendingActionMessage()
  } finally {
    messagesLoading.value = false
    await nextTick()
    scrollToBottom()
  }
  if (pendingMessage) {
    await openActionDialog(pendingMessage)
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
  if (!hasAvailableAgent.value) {
    ElMessage.warning('暂无可用 Agent，请联系管理员创建或启用 Agent')
    return
  }
  const question = inputMessage.value.trim()
  if (!question || loading.value) return
  const content = composeContextualPrompt(question)
  const contextText = selectedContextText.value

  loading.value = true
  let userMessageAdded = false
  let shouldRefreshHistory = false
  try {
    if (!currentSessionId.value) {
      await createSession(question)
    }

    const activeSession = sessions.value.find(session => sameId(session.id, currentSessionId.value))
    if (activeSession && messages.value.length === 0) {
      activeSession.title = trimTitle(question)
      activeSession.updatedAt = new Date().toISOString()
      sortSessions()
    }

    messages.value.push({
      localId: `user_${Date.now()}`,
      role: 'user',
      content,
      displayContent: question,
      contextText,
      createdAt: new Date().toISOString()
    })
    userMessageAdded = true
    inputMessage.value = ''
    await nextTick()
    scrollToBottom()

    shouldRefreshHistory = await sendNormalMessage(content)
    if (contextText) emit('context-consumed')

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
        content: e?.code === 'ERR_CANCELED' ? '已停止接收本次回复。' : normalizeAssistantCopy(extractRequestMessage(e, 'Agent 处理失败，请稍后重试。')),
        failed: true,
        retryContent: question,
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
  const controller = new AbortController()
  activeChatRequest.value = controller
  let response
  try {
    response = await sendAgentMessage(currentSessionId.value, { content }, { silent: true, signal: controller.signal })
  } finally {
    if (activeChatRequest.value === controller) activeChatRequest.value = null
  }
  const payload = unwrapData(response)
  const pendingAction = pendingActionFromPayload(response)
  const assistantMessage = {
    id: payload?.assistantMessageId,
    localId: `assistant_${Date.now()}`,
    role: 'assistant',
    content: normalizeReply(payload),
    pendingAction,
    citations: Array.isArray(payload?.citations) ? payload.citations : [],
    retrievalMode: payload?.retrievalMode,
    knowledgeStatus: payload?.knowledgeStatus,
    knowledgeWarning: payload?.knowledgeWarning,
    createdAt: payload?.createTime || new Date().toISOString()
  }
  messages.value.push(assistantMessage)
  if (pendingAction && canUseOperations.value) {
    await nextTick()
    await openActionDialog(assistantMessage)
  }
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

function fillPromptFromDraft() {
  const prompt = String(props.draftPrompt || '').trim()
  if (prompt) {
    inputMessage.value = prompt.slice(0, inputMaxLength.value)
  }
}

async function openAgentDrawer() {
  if (!canUseOperations.value) return
  agentDrawerVisible.value = true
  await Promise.all([loadAgentDefinitions(), loadKnowledgeBases()])
}

async function loadKnowledgeBases() {
  knowledgeBasesLoading.value = true
  try {
    const response = await listKnowledgeBases()
    knowledgeBases.value = toArray(response)
      .map(kb => ({
        ...kb,
        id: String(kb.id || kb.knowledgeBaseId || '').trim().toLowerCase(),
        name: kb.name || kb.title || kb.id || '未命名知识库'
      }))
      .filter(kb => parseKnowledgeBaseIds([kb.id]).length > 0)
  } catch {
    knowledgeBases.value = []
  } finally {
    knowledgeBasesLoading.value = false
  }
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
    knowledgeBaseIds: [],
    enabledTools: '',
    status: 1
  }
}

function resetAgentForm(agent = {}) {
  Object.assign(agentForm, emptyAgentForm(), agent, {
    knowledgeBaseIds: parseKnowledgeBaseIds(agent.knowledgeBaseIds)
  })
}

async function openCreateAgent() {
  if (!knowledgeBases.value.length) await loadKnowledgeBases()
  resetAgentForm()
  agentFormMode.value = 'create'
  agentFormVisible.value = true
}

async function openEditAgent(row) {
  const [response] = await Promise.all([
    getAgentDefinition(row.id),
    knowledgeBases.value.length ? Promise.resolve() : loadKnowledgeBases()
  ])
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
    knowledgeBaseIds: agentForm.knowledgeBaseIds.length
      ? JSON.stringify(agentForm.knowledgeBaseIds)
      : '',
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

async function openKnowledgeDialog(row) {
  if (!knowledgeBases.value.length) await loadKnowledgeBases()
  knowledgeForm.agentId = row.id
  knowledgeForm.agentName = row.name
  const boundIds = parseKnowledgeBaseIds(row.knowledgeBaseIds)
  knowledgeForm.knowledgeBaseId = writableKnowledgeBases.value.find(kb => boundIds.includes(kb.id))?.id
    || writableKnowledgeBases.value[0]?.id
    || ''
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
  const knowledgeBaseId = String(knowledgeForm.knowledgeBaseId || '').trim()
  if (!knowledgeBaseId) {
    ElMessage.warning('请选择课程知识库')
    return
  }
  if (!writableKnowledgeBases.value.some(kb => kb.id === knowledgeBaseId)) {
    ElMessage.warning('只能向自己拥有的知识库上传资料')
    return
  }
  if (!selectedKnowledgeFile.value) {
    ElMessage.warning('请选择 Markdown 文件')
    return
  }
  uploadingKnowledge.value = true
  try {
    const formData = new FormData()
    formData.append('knowledgeBaseId', knowledgeBaseId)
    formData.append('file', selectedKnowledgeFile.value)
    const response = await uploadAgentKnowledgeDocument(knowledgeForm.agentId, formData)
    knowledgeUploadResult.value = JSON.stringify(unwrapData(response), null, 2)
    ElMessage.success('知识文档已上传')
  } finally {
    uploadingKnowledge.value = false
  }
}

function openChatOnceDialog() {
  if (!canUseOperations.value) return
  chatOnceContent.value = ''
  chatOnceReply.value = ''
  chatOnceVisible.value = true
}

async function runChatOnce() {
  if (!canUseOperations.value) return
  const content = chatOnceContent.value.trim()
  if (!content) {
    ElMessage.warning('请输入调试问题')
    return
  }
  chatOnceLoading.value = true
  try {
    const response = await agentChatOnce({ agentId: selectedAgentId.value, content })
    chatOnceReply.value = normalizeReply(response)
  } finally {
    chatOnceLoading.value = false
  }
}

function pendingActionOf(message) {
  return message?.pendingAction || message?.pending_action || null
}

function hasConfirmableAction(message) {
  if (!canUseOperations.value) return false
  const pendingAction = pendingActionOf(message)
  if (pendingAction?.actionId || pendingAction?.actionCode) {
    return true
  }
  return false
}

function displayMessageContent(message) {
  if (message?.role === 'assistant' && hasConfirmableAction(message)) {
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
  if (!canUseOperations.value) return
  pendingActionDialogVisible.value = true
  await loadPendingActions()
}

async function loadPendingActions() {
  if (!canUseOperations.value) return
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
  actionResult.message = ''
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
  if (!canUseOperations.value || openingActionDialog.value || actionDialogVisible.value) return
  openingActionDialog.value = true
  try {
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
      ElMessage.warning('未生成可确认动作，请补充信息后重试。')
      return
    }
    if (actionForm.actionId) {
      try {
        applyPendingActionToForm(unwrapData(await getAgentPendingAction(actionForm.actionId)))
      } catch {
        actionForm.actionId = ''
        actionForm.actionCode = ''
        ElMessage.warning('动作已处理或已过期，请重新发起。')
        return
      }
    }
    actionDialogVisible.value = true
  } finally {
    openingActionDialog.value = false
  }
}

function latestPendingActionMessage() {
  if (!canUseOperations.value) return null
  for (let index = messages.value.length - 1; index >= 0; index -= 1) {
    if (hasConfirmableAction(messages.value[index])) {
      return messages.value[index]
    }
  }
  return null
}

function clearConfirmedPendingAction(actionId, actionCode) {
  messages.value.forEach(message => {
    const pendingAction = pendingActionOf(message)
    const matchesId = actionId && sameId(pendingAction?.actionId, actionId)
    const matchesCode = !actionId && actionCode && pendingAction?.actionCode === actionCode
    if (matchesId || matchesCode) {
      message.pendingAction = null
      message.content = '动作已完成'
    }
  })
  pendingActions.value = pendingActions.value.filter(action => {
    if (actionId) return !sameId(action.actionId, actionId)
    return action.actionCode !== actionCode
  })
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
  const serverMessage = data?.msg || data?.message
  if (serverMessage) return serverMessage
  const localMessage = error?.message || ''
  return /^Request failed with status code/i.test(localMessage) ? fallback : (localMessage || fallback)
}

function actionCrudType(actionCode) {
  const operation = String(actionCode || '').split('.').pop()
  const labels = {
    create: '新增',
    add: '新增',
    attach: '新增',
    update: '修改',
    status: '状态变更',
    sort: '排序调整',
    grade: '批改',
    close: '结束',
    delete: '删除',
    remove: '移除'
  }
  return labels[operation] || '系统操作'
}

function showActionError(message) {
  actionResult.visible = true
  actionResult.message = message || '动作执行失败'
}

async function submitActionConfirm() {
  if (!canUseOperations.value) return
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
  const confirmedActionId = actionForm.actionId.trim()
  const confirmedActionCode = actionForm.actionCode.trim()
  try {
    const response = await confirmAgentAction({
      actionId: actionForm.actionId.trim() || null,
      actionCode: actionForm.actionCode.trim() || null,
      confirmed: true,
      confirmationText: actionForm.confirmationText.trim() || null,
      parameters
    })
    const result = unwrapData(response)
    clearConfirmedPendingAction(confirmedActionId, confirmedActionCode)
    actionDialogVisible.value = false
    resetActionResult()
    ElMessage.success(result?.message || '动作已完成')
  } catch (e) {
    const message = extractRequestMessage(e, '动作执行失败')
    showActionError(message)
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
watch(() => props.draftVersion, fillPromptFromDraft)

onMounted(async () => {
  await loadAgents()
  await loadSessions(true)
  fillPromptFromRoute()
  fillPromptFromDraft()
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

.ai-chat-page.embedded-mode {
  height: 100%;
  min-height: 0;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.embedded-mode .chat-sidebar {
  width: 132px;
  min-width: 132px;
}

.embedded-mode .sidebar-header {
  padding: 10px;
}

.embedded-mode .session-list {
  padding: 6px;
}

.embedded-mode .session-item {
  margin-bottom: 6px;
  padding: 8px;
}

.embedded-mode .session-title {
  display: -webkit-box;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.45;
  white-space: normal;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.embedded-mode .chat-header {
  padding: 8px 10px;
  flex-wrap: wrap;
  gap: 6px;
}

.embedded-mode .agent-selector {
  width: auto;
  min-width: 150px;
  flex: 1 1 180px;
}

.agent-option-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.full-width-select {
  width: 100%;
}

.form-help {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.muted-text {
  color: var(--text-secondary);
  font-size: 12px;
}

.chat-once-alert {
  margin-bottom: 14px;
}

.embedded-mode .chat-actions {
  gap: 4px;
}

.embedded-mode .chat-actions :deep(.el-button.is-circle) {
  width: 30px;
  height: 30px;
}

.embedded-mode .chat-messages {
  padding: 12px;
}

.embedded-mode .message-wrapper {
  max-width: 96%;
}

.embedded-mode .chat-input-area {
  padding: 10px;
  gap: 8px;
}

.embedded-mode .chat-input-row {
  gap: 8px;
}

.embedded-mode .chat-input-area :deep(.el-textarea__inner) {
  min-height: 60px !important;
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
  display: block;
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
  min-width: 0;
  overflow: hidden;
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
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  color: var(--text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  min-height: 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}

.session-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
}

.session-actions :deep(.el-button + .el-button) {
  margin-left: 0;
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

.user-context-quote {
  margin-bottom: 10px;
  padding: 9px 11px;
  border: 1px solid rgba(255, 255, 255, .28);
  border-radius: 10px;
  background: rgba(255, 255, 255, .12);
}

.user-context-quote span {
  display: block;
  margin-bottom: 4px;
  color: rgba(255, 255, 255, .72);
  font-size: 11px;
  font-weight: 700;
}

.user-context-quote p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 5;
  white-space: pre-wrap;
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
  flex-direction: column;
  gap: 12px;
}

.action-pending-hint {
  margin-top: 0;
  color: #D97706;
  font-weight: 600;
}

.chat-input-row {
  width: 100%;
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.chat-input-row .el-textarea {
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

.selected-context-card {
  padding: 10px 12px;
  border: 1px solid #bfdbfe;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, #eff6ff, #f8fafc);
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
  color: #1d4ed8;
}

.selected-context-heading span {
  min-width: 0;
  overflow: hidden;
  color: #64748b;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-context-card p {
  display: -webkit-box;
  margin: 7px 0 0;
  overflow: hidden;
  color: #334155;
  font-size: 12px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  white-space: pre-wrap;
}

.selected-context-card small {
  display: block;
  margin-top: 5px;
  color: #b45309;
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

.action-result-card {
  margin-top: 14px;
  padding: 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
  background: #F8FAFC;
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

  .ai-chat-page.embedded-mode {
    flex-direction: row;
    height: 100%;
    min-height: 0;
  }

  .chat-sidebar {
    width: 100%;
    min-width: 0;
    height: 220px;
    border-right: 0;
    border-bottom: 1px solid var(--border-color);
  }

  .embedded-mode .chat-sidebar {
    width: 120px;
    min-width: 120px;
    height: auto;
    border-right: 1px solid var(--border-color);
    border-bottom: 0;
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

@media (max-width: 560px) {
  .embedded-mode .chat-sidebar {
    display: none;
  }
}
</style>

