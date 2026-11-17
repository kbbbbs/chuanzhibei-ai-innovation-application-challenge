<template>
  <div class="setup-page">
    <header class="setup-bar">
      <BrandMark />
      <ThemeToggle />
    </header>

    <main class="setup-main">
      <div v-if="stage === 'loading'" class="setup-state" role="status" v-loading="true">
        <span class="visually-hidden">正在读取实例状态</span>
      </div>

      <section v-else-if="stage === 'unreachable'" class="setup-card surface-card" aria-labelledby="unreachable-heading">
        <h1 id="unreachable-heading">暂时无法连接到服务</h1>
        <p class="lead">服务可能仍在启动。此页面不会自行改变任何配置。</p>
        <ErrorNotice :message="initError?.message" :code="initError?.errorCode" title="无法读取实例状态" retry-label="重新连接" @retry="init" />
      </section>

      <section v-else-if="stage === 'ready'" class="setup-card surface-card" aria-labelledby="ready-heading">
        <h1 id="ready-heading">{{ route.name === 'SetupRecovery' ? '当前无需恢复' : 'EduLoop 已完成安装' }}</h1>
        <p class="lead">实例运行正常，安装与恢复入口已关闭。如需调整模型，请用管理员账号登录后进入模型设置。</p>
        <el-button type="primary" size="large" @click="router.push('/login')">前往登录</el-button>
      </section>

      <section v-else-if="stage === 'blocked'" class="setup-card surface-card" aria-labelledby="blocked-heading">
        <h1 id="blocked-heading">当前不能进行安装</h1>
        <p class="lead">实例没有开放安装入口。请在服务器上确认部署状态后再试。</p>
        <el-button @click="init">重新检查</el-button>
      </section>

      <section v-else-if="stage === 'code' || stage === 'recovery-code'" class="setup-card setup-card--narrow surface-card" aria-labelledby="code-heading">
        <p class="eyebrow">{{ stage === 'code' ? '首次安装' : '实例恢复' }}</p>
        <h1 id="code-heading">{{ stage === 'code' ? '输入安装码' : '输入恢复码' }}</h1>
        <p class="lead">{{ stage === 'code'
          ? '安装码由服务器管理员在宿主机上生成，只能使用一次，有效期 30 分钟。'
          : '恢复码由服务器管理员在宿主机上生成。安装码不能用于恢复。' }}</p>
        <ErrorNotice v-if="sessionNotice" class="stack-gap" title="需要重新验证" :message="sessionNotice" />
        <ErrorNotice
          v-if="codeError"
          class="stack-gap"
          title="验证未通过"
          :message="codeError.message"
          :code="codeError.errorCode"
        />
        <form @submit.prevent="submitCode">
          <label class="field-label" for="access-code">{{ stage === 'code' ? '安装码' : '恢复码' }}</label>
          <el-input
            id="access-code"
            v-model="accessCode"
            type="password"
            show-password
            size="large"
            autocomplete="off"
            spellcheck="false"
          />
          <el-button class="code-submit" type="primary" size="large" native-type="submit" :loading="codeBusy" :disabled="!accessCode.trim()">继续</el-button>
        </form>
      </section>

      <section v-else-if="stage === 'waiting'" class="setup-card surface-card" aria-labelledby="waiting-heading">
        <h1 id="waiting-heading">{{ waitingExpired ? '尚无法确认安装结果' : '实例正在应用配置' }}</h1>
        <p class="lead" role="status">
          {{ waitingExpired
            ? '等待时间内实例状态没有变化，也没有收到就绪确认。安装没有被标记为成功，也没有重复提交。'
            : '这个浏览器没有本次操作的进度授权，页面会持续读取实例状态。' }}
        </p>
        <el-button v-if="waitingExpired" @click="init">继续检查</el-button>
      </section>

      <div v-else-if="stage === 'operation'" class="setup-column">
        <OperationProgress
          :phase="tracker.phase.value"
          :report="tracker.report.value"
          :target-revision="tracker.targetRevision.value"
          :mismatch="tracker.mismatch.value"
          @resume="tracker.resume"
        >
          <template #applied>
            <p class="applied-note">{{ appliedNote }}</p>
            <el-button type="primary" size="small" @click="router.push('/login')">前往登录</el-button>
          </template>
          <template #recovery-actions>
            <el-button size="small" type="primary" @click="enterRecovery">查看恢复选项</el-button>
          </template>
          <template #unconfirmed-actions>
            <el-button size="small" @click="init">重新读取实例状态</el-button>
          </template>
          <template #unauthorized-actions>
            <el-button size="small" type="primary" @click="stage = 'recovery-code'">输入恢复码</el-button>
          </template>
        </OperationProgress>
      </div>

      <div v-else-if="stage === 'recovery'" class="setup-column setup-column--wide">
        <RecoveryWorkspace
          :key="recoveryOperationId"
          :operation-id="recoveryOperationId"
          @started="startOperation"
          @unauthorized="stage = 'recovery-code'"
        />
      </div>

      <div v-else-if="stage === 'wizard'" class="setup-column" :class="{ 'setup-column--wide': step === 2 }">
        <ol class="wizard-steps" aria-label="安装步骤">
          <li
            v-for="(item, index) in STEPS"
            :key="item"
            :class="{ 'is-current': index === step, 'is-done': index < step }"
            :aria-current="index === step ? 'step' : undefined"
          >
            <span class="step-index" aria-hidden="true">{{ index + 1 }}</span>
            <span class="step-name">{{ item }}</span>
            <span v-if="index < step" class="visually-hidden">（已完成）</span>
          </li>
        </ol>

        <section v-if="step === 0" class="setup-card surface-card" aria-labelledby="wizard-step-heading">
          <h1 id="wizard-step-heading" ref="stepHeading" tabindex="-1">连接数据库</h1>
          <p class="lead">先检查数据库现状。检查不会建表，也不会写入数据。</p>
          <DatabaseForm :draft="database" :errors="databaseErrors" :disabled="checking" />
          <ErrorNotice
            v-if="checkError"
            class="stack-top"
            :title="isExternallyManagedConflict(checkError) ? '有字段由环境变量管理，请按部署配置填写' : '数据库检查未完成'"
            :message="checkError.message"
            :code="checkError.errorCode"
            :details="checkError.fieldErrors.map(item => item.message)"
            :retry-label="checkError.retryable ? '重新检查' : ''"
            :busy="checking"
            @retry="runDatabaseCheck"
          />
          <div v-if="check" class="check-result" :class="`check-result--${classification.tone}`" role="status">
            <StatusBadge :label="classification.title" :tone="classification.tone" />
            <p>{{ classification.detail }}</p>
          </div>
          <footer class="wizard-footer">
            <el-button :loading="checking" @click="runDatabaseCheck">{{ check ? '重新检查' : '检查数据库' }}</el-button>
            <el-button type="primary" :disabled="!installMode" @click="goTo(1)">下一步</el-button>
          </footer>
        </section>

        <section v-else-if="step === 1" class="setup-card surface-card" aria-labelledby="wizard-step-heading">
          <h1 id="wizard-step-heading" ref="stepHeading" tabindex="-1">{{ installMode === 'NEW' ? '创建首个管理员' : '验证现有管理员' }}</h1>
          <p class="lead">{{ installMode === 'NEW'
            ? '这是实例唯一一次创建首个管理员的机会，请使用不与其他服务共用的密码。'
            : '已有数据库需要现有管理员认领，不会创建新账号。' }}</p>
          <el-form ref="adminFormRef" :model="adminForm" :rules="adminRules" label-position="top" @submit.prevent="submitAdminStep">
            <div class="field-pair">
              <el-form-item label="用户名" prop="username">
                <el-input v-model="adminForm.username" autocomplete="username" spellcheck="false" />
              </el-form-item>
              <el-form-item v-if="installMode === 'NEW'" label="昵称" prop="nickname">
                <el-input v-model="adminForm.nickname" autocomplete="nickname" />
              </el-form-item>
            </div>
            <el-form-item label="密码" prop="password">
              <el-input v-model="adminForm.password" type="password" show-password :autocomplete="installMode === 'NEW' ? 'new-password' : 'current-password'" />
            </el-form-item>
            <el-form-item v-if="installMode === 'NEW'" label="确认密码" prop="confirmPassword">
              <el-input v-model="adminForm.confirmPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <footer class="wizard-footer">
              <el-button @click="goTo(0)">上一步</el-button>
              <el-button type="primary" native-type="submit">下一步</el-button>
            </footer>
          </el-form>
        </section>

        <section v-else-if="step === 2" aria-labelledby="wizard-step-heading">
          <div class="step-intro">
            <h1 id="wizard-step-heading" ref="stepHeading" tabindex="-1">配置 AI 模型（可跳过）</h1>
            <p class="lead">
              四项能力各自独立，凭证不会跨服务复用。不配置也能使用课程、作业和考试等基础功能，之后可在模型设置中补充。
            </p>
          </div>
          <ErrorNotice
            v-if="modelSummary.length"
            class="stack-gap"
            title="还有内容需要修正"
            :details="modelSummary"
          />
          <div class="role-grid">
            <ModelRoleEditor
              v-for="role in MODEL_ROLES"
              :key="role"
              :role="role"
              :draft="drafts[role]"
              :errors="modelErrors[role] || {}"
              :server-errors="fieldErrorsForRole(completeError?.fieldErrors || [], role)"
              :probing="Boolean(probeControllers[role])"
              :probe-report="probes[role]?.report || null"
              :probe-scope="probes[role]?.scope"
              :probe-error="probes[role]?.error || null"
              @probe="probeDraft"
              @cancel-probe="cancelProbe"
            />
          </div>
          <footer class="wizard-footer wizard-footer--sticky">
            <el-button @click="goTo(1)">上一步</el-button>
            <el-button @click="skipModels">跳过 AI 配置</el-button>
            <el-button type="primary" @click="submitModelStep">下一步</el-button>
          </footer>
        </section>

        <section v-else class="setup-card surface-card" aria-labelledby="wizard-step-heading">
          <h1 id="wizard-step-heading" ref="stepHeading" tabindex="-1">确认并完成安装</h1>
          <p class="lead">完成后服务会自动重启到正式模式，期间页面会短暂断开并自动重连。</p>
          <dl class="review-list">
            <div>
              <dt>数据库</dt>
              <dd>{{ database.mode === 'MANAGED' ? '随部署提供的 MySQL' : `外部 MySQL · ${database.host}:${database.port}/${database.database}` }}</dd>
            </div>
            <div>
              <dt>安装方式</dt>
              <dd>{{ installMode === 'NEW' ? '初始化新实例' : '认领已有数据库' }}</dd>
            </div>
            <div>
              <dt>管理员</dt>
              <dd>{{ adminForm.username }}{{ installMode === 'NEW' ? '（新建）' : '（现有账号）' }}</dd>
            </div>
            <div>
              <dt>AI 能力</dt>
              <dd>
                <ul class="review-roles">
                  <li v-for="role in MODEL_ROLES" :key="role">
                    <span>{{ ROLE_META[role].title }}</span>
                    <StatusBadge :label="reviewRoleLabel(role)" :tone="drafts[role].enabled ? 'accent' : 'neutral'" />
                  </li>
                </ul>
              </dd>
            </div>
          </dl>
          <ErrorNotice
            v-if="completeError"
            class="stack-top"
            :title="completeErrorTitle"
            :message="completeError.message"
            :code="completeError.errorCode"
            :details="completeError.fieldErrors.map(item => item.message)"
            :retry-label="completeError.transient ? '用同一请求重试' : ''"
            :busy="completing"
            @retry="submitComplete"
          >
            <template v-if="completeError.status === 409 || completeError.status === 403 || completeError.status === 422" #actions>
              <el-button v-if="isExternallyManagedConflict(completeError) || completeError.status === 422" size="small" @click="goTo(2)">返回修改模型</el-button>
              <el-button v-if="completeError.status === 403 || completeError.status === 422" size="small" @click="goTo(1)">{{ installMode === 'ADOPT' ? '重新验证管理员' : '返回修改管理员' }}</el-button>
              <el-button v-if="completeError.status === 409" size="small" @click="restartFromDatabase">重新检查数据库</el-button>
            </template>
          </ErrorNotice>
          <footer class="wizard-footer">
            <el-button :disabled="completing" @click="goTo(2)">上一步</el-button>
            <el-button type="primary" :loading="completing" @click="submitComplete">完成安装</el-button>
          </footer>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ModelRoleEditor from '@/components/settings/ModelRoleEditor.vue'
import OperationProgress from '@/components/settings/OperationProgress.vue'
import DatabaseForm from '@/components/setup/DatabaseForm.vue'
import RecoveryWorkspace from '@/components/setup/RecoveryWorkspace.vue'
import BrandMark from '@/components/ui/BrandMark.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import ThemeToggle from '@/components/ui/ThemeToggle.vue'
import { DRAFT_PROBE_SCOPE, useModelProbes } from '@/composables/useModelProbes'
import { useOperationTracker } from '@/composables/useOperationTracker'
import {
  checkSetupDatabase,
  completeSetup,
  createIdempotencyKey,
  createRecoverySession,
  createSetupSession,
  getOperationAt,
  getRecoverySession,
  getSetupOperation,
  getSetupSession,
  getSetupStatus,
  probeSetupModel,
} from '@/api/configuration'
import {
  DATABASE_CLASSIFICATIONS,
  UNRECOGNIZED_DATABASE,
  clearDatabaseSecrets,
  createDatabaseDraft,
  isExternallyManagedConflict,
  toDatabasePayload,
  validateDatabaseDraft,
} from '@/utils/databaseConfig'
import {
  MODEL_ROLES,
  ROLE_META,
  clearDraftSecrets,
  createEmptyDrafts,
  fieldErrorsForRole,
  toModelDrafts,
  validateDrafts,
} from '@/utils/modelConfig'

const STEPS = ['数据库', '管理员', 'AI 模型', '确认']
const STATUS_POLL_MS = 3000
const STATUS_WAIT_LIMIT_MS = 180000

const route = useRoute()
const router = useRouter()
const { probes, probeControllers, runProbe, cancelProbe, invalidateDraftProbe, cancelAllProbes } = useModelProbes()
const tracker = useOperationTracker((id, statusUrl) => (statusUrl ? getOperationAt(statusUrl) : getSetupOperation(id)))

// loading | unreachable | code | wizard | operation | waiting | recovery-code | recovery | ready | blocked
const stage = ref('loading')
const step = ref(0)
const stepHeading = ref(null)
const initError = ref(null)

const accessCode = ref('')
const codeBusy = ref(false)
const codeError = ref(null)
const sessionNotice = ref('')

const database = reactive(createDatabaseDraft())
const databaseErrors = ref({})
const checking = ref(false)
const checkError = ref(null)
const check = ref(null)

const adminFormRef = ref(null)
const adminForm = reactive({ username: '', nickname: '', password: '', confirmPassword: '' })

const drafts = reactive(createEmptyDrafts())
const modelErrors = ref({})
const modelsSkipped = ref(false)

const completing = ref(false)
const completeError = ref(null)
const installSubmitted = ref(false)
const recoveryOperationId = ref('')
const waitingExpired = ref(false)
let completeIntent = null
let statusTimer = null

const classification = computed(() => DATABASE_CLASSIFICATIONS[check.value?.classification] || UNRECOGNIZED_DATABASE)
const installMode = computed(() => {
  if (!check.value?.compatible) return null
  if (check.value.classification === 'EMPTY') return check.value.requiresExistingAdmin ? null : 'NEW'
  return check.value.classification === 'KNOWN_EXISTING' ? 'ADOPT' : null
})
const completeErrorTitle = computed(() => {
  if (completeError.value?.transient) return '无法确认安装请求是否送达'
  if (isExternallyManagedConflict(completeError.value)) return '有字段由环境变量管理，安装未开始'
  return '安装未开始'
})
const modelSummary = computed(() => Object.entries(modelErrors.value).flatMap(([role, errors]) => (
  Object.values(errors).map(message => `${ROLE_META[role].title}：${message}`)
)))
const appliedNote = computed(() => {
  if (!installSubmitted.value) return '配置已由服务确认生效。模型是否可用需在模型设置中逐项探测。'
  return MODEL_ROLES.some(role => drafts[role].enabled)
    ? '安装已由服务确认完成。模型已配置但尚未验证，可登录后在模型设置中探测。'
    : '安装已由服务确认完成。AI 能力未配置，基础功能可以使用，之后可在模型设置中补充。'
})

const validateConfirm = (rule, value, callback) => {
  if (installMode.value !== 'NEW') callback()
  else if (!value) callback(new Error('请再次输入密码'))
  else if (value !== adminForm.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const adminRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }],
}

function clearAllSecrets() {
  accessCode.value = ''
  clearDatabaseSecrets(database)
  adminForm.password = ''
  adminForm.confirmPassword = ''
  clearDraftSecrets(drafts)
}

function stopStatusWait() {
  if (statusTimer !== null) clearTimeout(statusTimer)
  statusTimer = null
}

// Used when this browser holds no operation session: the public status still shows the outcome.
function waitForLifecycle() {
  stage.value = 'waiting'
  waitingExpired.value = false
  const deadline = Date.now() + STATUS_WAIT_LIMIT_MS
  const poll = async () => {
    try {
      const status = await getSetupStatus()
      if (status?.state !== 'PENDING_RESTART') {
        await enterState(status)
        return
      }
    } catch {
      // Restart gaps are expected; keep waiting.
    }
    if (Date.now() >= deadline) {
      waitingExpired.value = true
      return
    }
    statusTimer = setTimeout(poll, STATUS_POLL_MS)
  }
  statusTimer = setTimeout(poll, STATUS_POLL_MS)
}

async function attachRunningOperation() {
  try {
    const session = await getRecoverySession()
    if (session?.operationId) {
      startOperation({ operationId: session.operationId })
      return
    }
  } catch {
    // No operation cookie in this browser.
  }
  waitForLifecycle()
}

function startOperation(summary) {
  stage.value = 'operation'
  tracker.start(summary.operationId, summary.statusUrl, summary.targetRevision)
}

// Keeps the address in step with the flow, so a refresh lands in the same place.
function syncRoute(name) {
  if (route.name !== name) router.replace({ name })
}

async function enterRecovery() {
  syncRoute('SetupRecovery')
  try {
    const session = await getRecoverySession()
    recoveryOperationId.value = session?.operationId || ''
    stage.value = recoveryOperationId.value ? 'recovery' : 'recovery-code'
  } catch (problem) {
    if (problem.status === 401 || problem.status === 403) {
      stage.value = 'recovery-code'
      return
    }
    initError.value = problem
    stage.value = 'unreachable'
  }
}

async function enterInstall() {
  syncRoute('Setup')
  try {
    await getSetupSession()
    stage.value = 'wizard'
  } catch (problem) {
    if (problem.status === 401) {
      stage.value = 'code'
      return
    }
    initError.value = problem
    stage.value = 'unreachable'
  }
}

async function enterState(status) {
  if (status?.recoveryRequired || status?.state === 'RECOVERY_REQUIRED') return enterRecovery()
  if (status?.state === 'PENDING_RESTART') return attachRunningOperation()
  if (status?.state === 'READY') {
    stage.value = 'ready'
    return undefined
  }
  if (status?.setupAllowed) return enterInstall()
  stage.value = 'blocked'
  return undefined
}

async function init() {
  stopStatusWait()
  tracker.stop()
  stage.value = 'loading'
  initError.value = null
  try {
    await enterState(await getSetupStatus())
  } catch (problem) {
    initError.value = problem
    stage.value = 'unreachable'
  }
}

async function submitCode() {
  const code = accessCode.value.trim()
  if (!code || codeBusy.value) return
  const recovering = stage.value === 'recovery-code'
  codeBusy.value = true
  codeError.value = null
  // The code is single-use, so it leaves memory with the request.
  accessCode.value = ''
  try {
    if (recovering) {
      const session = await createRecoverySession(code)
      recoveryOperationId.value = session.operationId
      syncRoute('SetupRecovery')
      stage.value = 'recovery'
    } else {
      await createSetupSession(code)
      stage.value = 'wizard'
    }
    sessionNotice.value = ''
  } catch (problem) {
    codeError.value = problem
  } finally {
    codeBusy.value = false
  }
}

function handleSessionExpired(problem) {
  if (problem.status !== 401) return false
  sessionNotice.value = '安装会话已过期。已填写的内容保留在此页面，请在服务器上生成新的安装码后继续。'
  stage.value = 'code'
  return true
}

async function goTo(index) {
  step.value = index
  await nextTick()
  stepHeading.value?.focus()
}

async function runDatabaseCheck() {
  databaseErrors.value = validateDatabaseDraft(database)
  if (Object.keys(databaseErrors.value).length) return
  checking.value = true
  checkError.value = null
  check.value = null
  try {
    check.value = await checkSetupDatabase(toDatabasePayload(database))
  } catch (problem) {
    if (!handleSessionExpired(problem)) checkError.value = problem
  } finally {
    checking.value = false
  }
}

async function submitAdminStep() {
  const valid = await adminFormRef.value.validate().catch(() => false)
  if (valid) await goTo(2)
}

function probeDraft(role) {
  modelErrors.value = validateDrafts(drafts)
  if (modelErrors.value[role]) return
  runProbe(role, DRAFT_PROBE_SCOPE, async signal => {
    try {
      return await probeSetupModel(role, toModelDrafts(drafts), { signal })
    } catch (problem) {
      handleSessionExpired(problem)
      throw problem
    }
  })
}

function submitModelStep() {
  modelErrors.value = validateDrafts(drafts)
  if (Object.keys(modelErrors.value).length) return
  modelsSkipped.value = !MODEL_ROLES.some(role => drafts[role].enabled)
  goTo(3)
}

function skipModels() {
  cancelAllProbes()
  Object.assign(drafts, createEmptyDrafts())
  modelErrors.value = {}
  modelsSkipped.value = true
  goTo(3)
}

function reviewRoleLabel(role) {
  const draft = drafts[role]
  if (!draft.enabled) return modelsSkipped.value ? '已跳过' : '未启用'
  return draft.inheritFrom ? '继承内容生成' : `${draft.model}（未验证）`
}

function buildCompletePayload() {
  const payload = {
    checkId: check.value.checkId,
    expectedRevision: null,
    mode: installMode.value,
    database: toDatabasePayload(database),
    roles: toModelDrafts(drafts),
  }
  if (installMode.value === 'NEW') {
    payload.admin = { username: adminForm.username.trim(), password: adminForm.password, nickname: adminForm.nickname.trim() }
  } else {
    payload.existingAdmin = { username: adminForm.username.trim(), password: adminForm.password }
  }
  return payload
}

async function submitComplete() {
  if (completing.value) return
  completeError.value = null
  if (!check.value) {
    await goTo(0)
    return
  }
  const payload = buildCompletePayload()
  const fingerprint = JSON.stringify(payload)
  // Same content keeps its key: a lost response then returns the same operation instead of a new one.
  if (completeIntent?.fingerprint !== fingerprint) completeIntent = { fingerprint, key: createIdempotencyKey() }
  completing.value = true
  try {
    const summary = await completeSetup(payload, completeIntent.key)
    completeIntent = null
    installSubmitted.value = true
    clearAllSecrets()
    startOperation(summary)
  } catch (problem) {
    if (!problem.transient) completeIntent = null
    if (!handleSessionExpired(problem)) completeError.value = problem
  } finally {
    completing.value = false
  }
}

function restartFromDatabase() {
  check.value = null
  completeError.value = null
  goTo(0)
}

// A check is bound to the exact connection it inspected.
watch(database, () => {
  check.value = null
  checkError.value = null
})

watch(() => MODEL_ROLES.map(role => JSON.stringify(drafts[role])), (next, previous) => {
  MODEL_ROLES.forEach((role, index) => {
    if (next[index] !== previous[index]) invalidateDraftProbe(role)
  })
  if (next[0] !== previous[0] && drafts.planning.inheritFrom) invalidateDraftProbe('planning')
})

onMounted(init)

onBeforeUnmount(() => {
  stopStatusWait()
  cancelAllProbes()
  clearAllSecrets()
})
</script>

<style scoped>
.setup-page {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  background: var(--surface-canvas);
}

.setup-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) clamp(var(--space-4), 4vw, var(--space-10));
  background: var(--surface-base);
  border-bottom: 1px solid var(--line-subtle);
}

.setup-main {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-5);
  padding: clamp(var(--space-5), 4vw, var(--space-10)) clamp(var(--space-4), 4vw, var(--space-10));
}

.setup-state {
  width: 100%;
  min-height: 240px;
}

.setup-column {
  display: grid;
  gap: var(--space-5);
  width: min(100%, 720px);
}

.setup-column--wide {
  width: min(100%, 1160px);
}

.setup-card {
  width: min(100%, 720px);
  padding: clamp(var(--space-5), 3vw, var(--space-8));
}

.setup-card--narrow {
  width: min(100%, 460px);
}

.eyebrow {
  margin: 0 0 var(--space-1);
  color: var(--accent);
  font-size: var(--text-sm);
  font-weight: 650;
}

h1 {
  margin: 0;
  font-size: var(--text-2xl);
}

h1:focus {
  outline: none;
}

.lead {
  margin: var(--space-2) 0 var(--space-5);
  color: var(--text-subtle);
  line-height: 1.7;
}

.stack-gap {
  margin-bottom: var(--space-4);
}

.stack-top {
  margin-top: var(--space-4);
}

.field-label {
  display: block;
  margin-bottom: var(--space-1);
  color: var(--text-strong);
  font-size: var(--text-sm);
  font-weight: 600;
}

.code-submit {
  width: 100%;
  margin-top: var(--space-4);
}

.applied-note {
  margin: 0 0 var(--space-2);
}

.wizard-steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
  width: 100%;
  max-width: 720px;
  margin: 0 auto;
  padding: 0;
  list-style: none;
}

.wizard-steps li {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  color: var(--text-subtle);
  font-size: var(--text-sm);
}

.step-index {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 1px solid var(--line-default);
  border-radius: 50%;
  font-size: var(--text-xs);
  font-weight: 650;
}

.step-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wizard-steps .is-done .step-index {
  color: var(--accent);
  background: var(--surface-accent);
  border-color: var(--line-accent);
}

.wizard-steps .is-current {
  color: var(--text-strong);
  font-weight: 650;
}

.wizard-steps .is-current .step-index {
  color: var(--text-on-accent);
  background: var(--accent-solid);
  border-color: var(--accent-solid);
}

.check-result {
  display: grid;
  justify-items: start;
  gap: var(--space-2);
  margin-top: var(--space-4);
  padding: var(--space-3);
  background: var(--surface-canvas);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-md);
}

.check-result p {
  margin: 0;
}

.wizard-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  flex-wrap: wrap;
  margin-top: var(--space-6);
}

.wizard-footer :deep(.el-button + .el-button) {
  margin-left: 0;
}

.wizard-footer--sticky {
  position: sticky;
  bottom: 0;
  padding: var(--space-3) 0;
  background: var(--surface-canvas);
  border-top: 1px solid var(--line-subtle);
}

.field-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.step-intro {
  margin-bottom: var(--space-4);
}

.step-intro .lead {
  margin-bottom: 0;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr));
  gap: var(--space-4);
  align-items: start;
}

.review-list {
  display: grid;
  gap: var(--space-3);
  margin: 0;
}

.review-list > div {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--line-subtle);
}

dt {
  color: var(--text-subtle);
}

dd {
  margin: 0;
  color: var(--text-strong);
  overflow-wrap: anywhere;
}

.review-roles {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.review-roles li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

@media (max-width: 560px) {
  .wizard-steps li:not(.is-current) .step-name {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip-path: inset(50%);
  }

  .wizard-steps {
    grid-template-columns: auto auto auto auto;
    justify-content: space-between;
  }

  .field-pair {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }

  .review-list > div {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-1);
  }
}
</style>
