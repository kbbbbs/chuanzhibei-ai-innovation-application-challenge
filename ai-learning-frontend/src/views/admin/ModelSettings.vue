<template>
  <div class="model-settings">
    <PageHeader
      eyebrow="系统配置"
      title="模型设置"
      description="内容生成、Agent 规划、语义向量和 OCR 分别配置。基础教学功能不依赖模型，未配置时对应 AI 功能会明确提示。"
    >
      <template #actions>
        <el-button :loading="loading" :disabled="busy || saving" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div v-if="loading && !settings" class="state-block" role="status" v-loading="true">
      <span class="visually-hidden">正在读取模型设置</span>
    </div>

    <ErrorNotice
      v-if="lifecycleBlocked"
      title="实例正在应用配置或等待恢复"
      message="业务服务暂时不可用，模型设置待服务恢复后才能读取。"
    >
      <template #actions>
        <el-button size="small" type="primary" @click="openSetup">查看安装与恢复状态</el-button>
      </template>
    </ErrorNotice>
    <ErrorNotice
      v-else-if="loadError"
      :title="loadError.status === 401 ? '登录已过期' : '无法读取模型设置'"
      :message="loadError.message"
      :code="loadError.errorCode"
      :retry-label="loadError.status === 401 ? '' : '重新读取'"
      :busy="loading"
      @retry="load"
    >
      <template v-if="loadError.status === 401" #actions>
        <el-button size="small" type="primary" @click="router.push({ path: '/login', query: { redirect: '/settings/models' } })">重新登录</el-button>
      </template>
    </ErrorNotice>

    <OperationProgress
      v-if="tracker.phase.value !== 'idle'"
      class="settings-operation"
      :phase="tracker.phase.value"
      :report="tracker.report.value"
      :target-revision="tracker.targetRevision.value"
      :mismatch="tracker.mismatch.value"
      @resume="tracker.resume"
    >
      <template #applied>
        <p class="applied-note">配置已由服务确认生效。外部模型是否可用仍需逐项探测，真实课程质量需单独评估。</p>
        <el-button size="small" @click="tracker.dismiss">知道了</el-button>
      </template>
      <template #recovery-actions>
        <el-button size="small" type="primary" @click="openSetup">进入恢复流程</el-button>
      </template>
      <template #unconfirmed-actions>
        <el-button size="small" @click="openSetup">查看安装与恢复状态</el-button>
      </template>
      <template #unauthorized-actions>
        <el-button size="small" type="primary" @click="openSetup">使用恢复码继续</el-button>
      </template>
    </OperationProgress>

    <template v-if="settings">
      <section class="revision-panel surface-card" aria-labelledby="revision-heading">
        <div class="revision-summary">
          <h2 id="revision-heading">配置版本</h2>
          <dl>
            <div>
              <dt>当前生效</dt>
              <dd><code>{{ settings.activeRevision || '尚无' }}</code></dd>
            </div>
            <div>
              <dt>待应用</dt>
              <dd><code>{{ settings.pendingRevision || '无' }}</code></dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd><StatusBadge :label="revisionState.label" :tone="revisionState.tone" /></dd>
            </div>
          </dl>
        </div>
        <div class="revision-actions">
          <el-button type="primary" :loading="saving" :disabled="busy || !dirty" @click="save">保存为待应用版本</el-button>
          <el-button :loading="applying" :disabled="!canApply" @click="confirmApply">应用并重启服务</el-button>
          <p class="field-hint">{{ actionHint }}</p>
        </div>
      </section>

      <ErrorNotice
        v-if="validationSummary.length"
        class="settings-notice"
        title="还有内容需要修正"
        :details="validationSummary"
      />
      <ErrorNotice
        v-if="saveError"
        class="settings-notice"
        :title="isExternallyManagedConflict(saveError) ? '有字段由环境变量管理，修改未保存' : saveError.status === 409 ? '配置已发生变化' : '保存未完成'"
        :message="saveError.message"
        :code="saveError.errorCode"
        :details="generalFieldErrors"
        :retry-label="saveError.status === 409 ? '' : saveError.retryable ? '重试保存' : ''"
        :busy="saving"
        @retry="save"
      >
        <template v-if="saveError.status === 409" #actions>
          <el-button size="small" @click="load">读取最新版本</el-button>
        </template>
      </ErrorNotice>
      <ErrorNotice
        v-if="applyError"
        class="settings-notice"
        :title="applyError.transient ? '无法确认应用请求是否送达' : '应用未开始'"
        :message="applyError.message"
        :code="applyError.errorCode"
        :retry-label="applyError.transient ? '用同一请求重试' : ''"
        :busy="applying"
        @retry="apply"
      />
      <p v-if="savedNotice" class="saved-note" role="status">{{ savedNotice }}</p>

      <el-alert
        v-if="settings.lifecycleSupported === false"
        class="settings-notice"
        type="warning"
        :closable="false"
        show-icon
        title="当前部署不支持一键应用，保存的配置不会自动生效。"
      />
      <el-alert
        v-if="embeddingChanged"
        class="settings-notice"
        type="warning"
        :closable="false"
        show-icon
        title="更换语义向量模型不会自动重建索引。旧索引保留，需要在知识库中对资料显式重建。"
      />

      <section v-if="activeStatus.length" class="active-status surface-card" aria-labelledby="active-status-heading">
        <h2 id="active-status-heading">当前生效状态</h2>
        <p class="field-hint">读取运行中服务的配置状态，不发起模型请求，也不代表模型可用。</p>
        <div class="table-scroll">
          <table>
            <thead>
              <tr><th scope="col">能力</th><th scope="col">模型</th><th scope="col">配置</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in activeStatus" :key="item.role">
                <th scope="row">{{ ROLE_META[item.role]?.title || item.role }}</th>
                <td><code>{{ item.model || '—' }}</code></td>
                <td><StatusBadge v-bind="configurationState(item)" /></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <p v-else-if="statusError" class="field-hint status-unavailable">{{ statusError }}</p>

      <div class="role-grid">
        <ModelRoleEditor
          v-for="role in MODEL_ROLES"
          :key="`${role}-${formVersion}`"
          :role="role"
          :draft="drafts[role]"
          :view="settings.roles?.[role] || null"
          :errors="clientErrors[role] || {}"
          :server-errors="fieldErrorsForRole(saveError?.fieldErrors || [], role)"
          :dirty="dirtyRoles[role]"
          :disabled="busy"
          :probing="Boolean(probeControllers[role])"
          :probe-report="probes[role]?.report || null"
          :probe-scope="probes[role]?.scope"
          :probe-error="probes[role]?.error || null"
          @probe="probeDraft"
          @cancel-probe="cancelProbe"
        >
          <template #probe-actions>
            <el-button :disabled="busy || Boolean(probeControllers[role])" @click="probeActive(role)">探测当前生效配置</el-button>
          </template>
        </ModelRoleEditor>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import ModelRoleEditor from '@/components/settings/ModelRoleEditor.vue'
import OperationProgress from '@/components/settings/OperationProgress.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { DRAFT_PROBE_SCOPE, useModelProbes } from '@/composables/useModelProbes'
import { useOperationTracker } from '@/composables/useOperationTracker'
import {
  applyModelSettings,
  createIdempotencyKey,
  getModelSettings,
  getModelStatus,
  getOperationAt,
  getRecoverySession,
  getSettingsOperation,
  getSetupStatus,
  probeActiveModel,
  probeModelDraft,
  saveModelSettings,
} from '@/api/configuration'
import {
  MODEL_ROLES,
  ROLE_META,
  clearDraftSecrets,
  configurationState,
  createEmptyDrafts,
  draftsFromViews,
  fieldErrorsForRole,
  toModelDrafts,
  toRolePayload,
  validateDrafts,
} from '@/utils/modelConfig'
import { isExternallyManagedConflict } from '@/utils/databaseConfig'

const LIFECYCLE_BUSY_STATES = ['PENDING_RESTART', 'RECOVERY_REQUIRED']

const router = useRouter()
const { probes, probeControllers, runProbe, cancelProbe, invalidateDraftProbe, cancelAllProbes } = useModelProbes()
const tracker = useOperationTracker((id, statusUrl) => (
  statusUrl ? getOperationAt(statusUrl, { admin: true }) : getSettingsOperation(id)
))

const loading = ref(false)
const saving = ref(false)
const applying = ref(false)
const settings = ref(null)
const activeStatus = ref([])
const statusError = ref('')
const loadError = ref(null)
const saveError = ref(null)
const applyError = ref(null)
const savedNotice = ref('')
const lifecycleState = ref('')
const clientErrors = ref({})
const formVersion = ref(0)
const drafts = reactive(createEmptyDrafts())
const baseline = ref({})
let applyIntent = null

const serialize = role => JSON.stringify(toRolePayload(role, drafts[role]))

const busy = computed(() => ['polling', 'reconnecting'].includes(tracker.phase.value))
const dirtyRoles = computed(() => Object.fromEntries(MODEL_ROLES.map(role => [role, serialize(role) !== baseline.value[role]])))
const dirty = computed(() => MODEL_ROLES.some(role => dirtyRoles.value[role]))
const canApply = computed(() => Boolean(settings.value?.pendingRevision)
  && settings.value?.lifecycleSupported !== false
  && !dirty.value
  && !busy.value
  && !saving.value)
const lifecycleBlocked = computed(() => Boolean(loadError.value) && LIFECYCLE_BUSY_STATES.includes(lifecycleState.value))
const embeddingChanged = computed(() => dirtyRoles.value.embedding)

const revisionState = computed(() => {
  if (dirty.value) return { label: '有未保存修改', tone: 'caution' }
  if (settings.value?.pendingRevision) return { label: '等待应用', tone: 'accent' }
  return { label: '无待应用修改', tone: 'neutral' }
})

const actionHint = computed(() => {
  if (dirty.value) return '保存后才能应用。保存不会改变正在运行的配置。'
  if (settings.value?.pendingRevision) return '应用时服务会先完成进行中的业务（最多 30 秒）再重启，期间页面短暂不可用。'
  return '修改配置后，先保存再应用。'
})

const validationSummary = computed(() => Object.entries(clientErrors.value).flatMap(([role, errors]) => (
  Object.values(errors).map(message => `${ROLE_META[role].title}：${message}`)
)))

const generalFieldErrors = computed(() => (saveError.value?.fieldErrors || [])
  .filter(item => !MODEL_ROLES.some(role => fieldErrorsForRole([item], role).length))
  .map(item => item.message))

function openSetup() {
  router.push('/setup/recovery')
}

function hydrate(data) {
  settings.value = data
  Object.assign(drafts, draftsFromViews(data.roles))
  baseline.value = Object.fromEntries(MODEL_ROLES.map(role => [role, serialize(role)]))
  clientErrors.value = {}
  formVersion.value += 1
}

async function load() {
  loading.value = true
  loadError.value = null
  saveError.value = null
  try {
    hydrate(await getModelSettings())
  } catch (problem) {
    loadError.value = problem
  } finally {
    loading.value = false
  }
  try {
    activeStatus.value = await getModelStatus()
    statusError.value = ''
  } catch (problem) {
    activeStatus.value = []
    statusError.value = `当前生效状态暂不可读：${problem.message}`
  }
}

// A refresh during apply reattaches through the recovery cookie; the operation id is never kept client-side.
async function resumeRunningOperation() {
  try {
    const status = await getSetupStatus()
    lifecycleState.value = status?.state || ''
    if (!LIFECYCLE_BUSY_STATES.includes(lifecycleState.value)) return
    const session = await getRecoverySession()
    if (session?.operationId) tracker.start(session.operationId)
  } catch {
    // No session in this browser: the page still shows settings or the lifecycle notice.
  }
}

async function save() {
  savedNotice.value = ''
  saveError.value = null
  clientErrors.value = validateDrafts(drafts, settings.value?.roles)
  if (Object.keys(clientErrors.value).length) return

  saving.value = true
  try {
    const result = await saveModelSettings({
      expectedRevision: settings.value.editableRevision,
      roles: toModelDrafts(drafts),
    })
    clearDraftSecrets(drafts)
    settings.value = { ...settings.value, ...result }
    await load()
    savedNotice.value = '已保存为待应用版本，运行中的配置尚未改变。'
  } catch (problem) {
    saveError.value = problem
  } finally {
    saving.value = false
  }
}

async function confirmApply() {
  try {
    await ElMessageBox.confirm(
      '服务会完成进行中的业务后重启，登录用户会短暂无法访问。目标版本启动失败时不会自动切换配置，需要你决定修正或回滚。',
      '应用配置并重启服务',
      { type: 'warning', confirmButtonText: '应用', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await apply()
}

async function apply() {
  const revision = settings.value.pendingRevision
  // The same intent keeps its key, so a lost response can be retried without a second operation.
  if (applyIntent?.revision !== revision) applyIntent = { revision, key: createIdempotencyKey() }
  applying.value = true
  applyError.value = null
  savedNotice.value = ''
  try {
    const summary = await applyModelSettings({
      revision,
      expectedActiveRevision: settings.value.activeRevision,
    }, applyIntent.key)
    applyIntent = null
    tracker.start(summary.operationId, summary.statusUrl, summary.targetRevision || revision)
  } catch (problem) {
    if (!problem.transient) applyIntent = null
    applyError.value = problem
  } finally {
    applying.value = false
  }
}

function probeDraft(role) {
  clientErrors.value = validateDrafts(drafts, settings.value?.roles)
  if (clientErrors.value[role]) return
  runProbe(role, DRAFT_PROBE_SCOPE, signal => probeModelDraft(role, toModelDrafts(drafts), { signal }))
}

function probeActive(role) {
  runProbe(role, '当前生效配置', signal => probeActiveModel(role, { signal }))
}

watch(() => MODEL_ROLES.map(serialize), (next, previous) => {
  MODEL_ROLES.forEach((role, index) => {
    if (next[index] !== previous[index]) invalidateDraftProbe(role)
  })
  if (next[0] !== previous[0] && drafts.planning.inheritFrom) invalidateDraftProbe('planning')
  if (dirty.value) savedNotice.value = ''
})

watch(tracker.phase, phase => {
  if (phase === 'applied') load()
})

onMounted(async () => {
  await resumeRunningOperation()
  await load()
})

onBeforeUnmount(() => {
  cancelAllProbes()
  clearDraftSecrets(drafts)
})
</script>

<style scoped>
.model-settings {
  width: min(100%, 1240px);
  margin: 0 auto;
}

.state-block {
  min-height: 200px;
}

.settings-operation,
.settings-notice {
  margin-bottom: var(--space-4);
}

.applied-note {
  margin: 0 0 var(--space-2);
  color: var(--text-body);
}

.revision-panel {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-5);
  flex-wrap: wrap;
  margin-bottom: var(--space-4);
  padding: var(--space-5);
}

.revision-summary {
  flex: 1 1 340px;
  min-width: 0;
}

h2 {
  margin: 0;
  font-size: var(--text-lg);
}

dl {
  display: flex;
  gap: var(--space-3) var(--space-6);
  flex-wrap: wrap;
  margin: var(--space-3) 0 0;
}

dt {
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

dd {
  margin: 2px 0 0;
  color: var(--text-strong);
  overflow-wrap: anywhere;
}

.revision-actions {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  flex: 0 1 420px;
}

.revision-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.revision-actions .field-hint {
  flex: 1 1 100%;
}

.saved-note {
  margin: 0 0 var(--space-4);
  padding: var(--space-3);
  color: var(--text-body);
  background: var(--positive-surface);
  border: 1px solid var(--positive-line);
  border-radius: var(--radius-md);
}

.active-status {
  margin-bottom: var(--space-4);
  padding: var(--space-5);
}

.active-status table {
  width: 100%;
  min-width: 460px;
  margin-top: var(--space-3);
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.active-status th,
.active-status td {
  padding: var(--space-2);
  text-align: left;
  border-bottom: 1px solid var(--line-subtle);
}

.active-status thead th {
  color: var(--text-subtle);
  font-size: var(--text-xs);
  font-weight: 600;
}

.active-status tbody th {
  color: var(--text-strong);
  font-weight: 600;
}

.active-status code {
  overflow-wrap: anywhere;
}

.status-unavailable {
  margin-bottom: var(--space-4);
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 440px), 1fr));
  gap: var(--space-4);
  align-items: start;
}
</style>
