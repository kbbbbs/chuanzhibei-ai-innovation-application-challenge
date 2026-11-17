<template>
  <section class="recovery" aria-labelledby="recovery-heading">
    <header class="section-heading">
      <h1 id="recovery-heading">恢复配置</h1>
      <p>上一次配置没有生效。上一版可用配置仍保留，系统不会自动切换或重试，下一步由你决定。</p>
    </header>

    <div v-if="loading" class="recovery-loading" role="status" v-loading="true">
      <span class="visually-hidden">正在读取恢复信息</span>
    </div>

    <ErrorNotice
      v-else-if="loadError"
      title="无法读取恢复信息"
      :message="loadError.message"
      :code="loadError.errorCode"
      retry-label="重新读取"
      @retry="load"
    />

    <template v-else>
      <ErrorNotice
        title="失败原因"
        :message="report?.error?.message || '服务没有提供具体原因。'"
        :code="report?.error?.code || ''"
      />

      <dl class="revision-facts surface-card">
        <div>
          <dt>失败的目标版本</dt>
          <dd><code>{{ report?.targetRevision || '—' }}</code></dd>
        </div>
        <div>
          <dt>当前生效版本</dt>
          <dd><code>{{ report?.activeRevision || '尚无' }}</code></dd>
        </div>
        <div>
          <dt>上一成功版本</dt>
          <dd><code>{{ report?.previousRevision || config?.previousRevision || '无' }}</code></dd>
        </div>
      </dl>

      <section class="recovery-actions surface-card" aria-labelledby="recovery-actions-heading">
        <h2 id="recovery-actions-heading">选择恢复方式</h2>
        <div class="action-row">
          <div>
            <strong>回滚到上一成功版本</strong>
            <p>{{ report?.canRollback ? '用上一成功配置生成新版本并重启，不会改动失败的版本。' : '没有可回滚的成功版本，只能修正配置。' }}</p>
          </div>
          <el-button :disabled="!report?.canRollback || submitting" :loading="activeAction === 'rollback'" @click="confirmRollback">回滚</el-button>
        </div>
        <div class="action-row">
          <div>
            <strong>{{ saved ? '应用修正后的配置' : '重新应用失败的版本' }}</strong>
            <p>{{ applyHint }}</p>
          </div>
          <el-button type="primary" :disabled="!canApply || submitting" :loading="activeAction === 'apply'" @click="confirmApply">
            {{ saved ? '应用修正' : '重新应用' }}
          </el-button>
        </div>
      </section>

      <ErrorNotice
        v-if="actionError"
        :title="actionError.problem.transient ? '无法确认请求是否送达' : '操作未开始'"
        :message="actionError.problem.message"
        :code="actionError.problem.errorCode"
        :retry-label="actionError.problem.transient ? '用同一请求重试' : ''"
        :busy="submitting"
        @retry="submit(actionError.action)"
      />

      <section v-if="config" class="correction" aria-labelledby="correction-heading">
        <h2 id="correction-heading">修正配置</h2>
        <p class="field-hint">恢复模式不能创建管理员，也不能连接到另一套实例的数据库。此处不提供模型探测，服务恢复后可在模型设置中探测。</p>

        <div class="role-grid">
          <ModelRoleEditor
            v-for="role in MODEL_ROLES"
            :key="`${role}-${formVersion}`"
            :role="role"
            :draft="drafts[role]"
            :view="config.roles?.[role] || null"
            :errors="clientErrors[role] || {}"
            :server-errors="fieldErrorsForRole(saveError?.fieldErrors || [], role)"
            :disabled="submitting || saving"
            :probe-enabled="false"
          />
        </div>

        <div class="database-toggle surface-card">
          <el-checkbox v-model="editDatabase" :disabled="submitting || saving">同时修正数据库连接</el-checkbox>
          <DatabaseForm
            v-if="editDatabase"
            class="database-fields"
            :draft="database"
            :errors="databaseErrors"
            :disabled="submitting || saving"
            :view="config.database"
            allow-keep
          />
        </div>

        <ErrorNotice
          v-if="saveError"
          :title="saveErrorTitle"
          :message="saveError.message"
          :code="saveError.errorCode"
          :details="generalFieldErrors"
          :retry-label="saveError.status === 409 ? '' : saveError.retryable ? '重试保存' : ''"
          :busy="saving"
          @retry="save"
        >
          <template v-if="saveError.status === 409" #actions>
            <el-button size="small" @click="load">读取最新配置</el-button>
          </template>
        </ErrorNotice>
        <p v-if="saved" class="saved-note" role="status">修正已保存，尚未生效。请在上方选择“应用修正”。</p>
        <div class="correction-footer">
          <el-button type="primary" :loading="saving" :disabled="submitting" @click="save">保存修正</el-button>
        </div>
      </section>
    </template>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import ModelRoleEditor from '@/components/settings/ModelRoleEditor.vue'
import DatabaseForm from '@/components/setup/DatabaseForm.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import {
  applyRecovery,
  createIdempotencyKey,
  getRecoveryConfig,
  getSetupOperation,
  rollbackRecovery,
  saveRecoveryConfig,
} from '@/api/configuration'
import {
  clearDatabaseSecrets,
  createDatabaseDraft,
  databaseDraftFromView,
  isExternallyManagedConflict,
  toDatabasePayload,
  validateDatabaseDraft,
} from '@/utils/databaseConfig'
import {
  MODEL_ROLES,
  clearDraftSecrets,
  createEmptyDrafts,
  draftsFromViews,
  fieldErrorsForRole,
  toModelDrafts,
  validateDrafts,
} from '@/utils/modelConfig'

const props = defineProps({
  operationId: { type: String, required: true },
})

const emit = defineEmits(['started', 'unauthorized'])

const loading = ref(true)
const loadError = ref(null)
const report = ref(null)
const config = ref(null)
const saving = ref(false)
const saveError = ref(null)
const saved = ref(null)
const activeAction = ref('')
const actionError = ref(null)
const clientErrors = ref({})
const databaseErrors = ref({})
const editDatabase = ref(false)
const formVersion = ref(0)
const drafts = reactive(createEmptyDrafts())
const database = reactive(createDatabaseDraft())
let intent = null

const submitting = computed(() => Boolean(activeAction.value))
const canApply = computed(() => Boolean(saved.value) || Boolean(report.value?.canRetry))
const saveErrorTitle = computed(() => {
  if (isExternallyManagedConflict(saveError.value)) return '有字段由环境变量管理，修改未保存'
  return saveError.value?.status === 409 ? '配置已发生变化' : '保存未完成'
})
const generalFieldErrors = computed(() => (saveError.value?.fieldErrors || [])
  .filter(item => !MODEL_ROLES.some(role => fieldErrorsForRole([item], role).length))
  .map(item => item.message))
const applyHint = computed(() => {
  if (saved.value) return '用保存的修正生成的新版本重启。每个目标版本只启动一次。'
  if (report.value?.canRetry) return '不改动配置，再启动一次失败的版本。可能再次失败。'
  return '当前版本不能直接重试，请先在下方保存修正。'
})

function handleProblem(problem) {
  if (problem.status === 401 || problem.status === 403) emit('unauthorized')
}

async function load() {
  loading.value = true
  loadError.value = null
  saveError.value = null
  try {
    const [operation, recoveryConfig] = await Promise.all([getSetupOperation(props.operationId), getRecoveryConfig()])
    if (operation?.operationId && operation.operationId !== props.operationId) {
      throw new Error('服务返回的操作与当前恢复任务不一致，请重新进入恢复流程。')
    }
    report.value = operation
    config.value = recoveryConfig
    Object.assign(drafts, draftsFromViews(recoveryConfig.roles))
    Object.assign(database, databaseDraftFromView(recoveryConfig.database))
    formVersion.value += 1
  } catch (problem) {
    handleProblem(problem)
    loadError.value = problem
  } finally {
    loading.value = false
  }
}

async function save() {
  saveError.value = null
  clientErrors.value = validateDrafts(drafts, config.value.roles)
  databaseErrors.value = editDatabase.value ? validateDatabaseDraft(database, config.value.database) : {}
  if (Object.keys(clientErrors.value).length || Object.keys(databaseErrors.value).length) return

  saving.value = true
  try {
    const payload = {
      operationId: props.operationId,
      expectedRevision: config.value.expectedRevision,
      roles: toModelDrafts(drafts),
    }
    if (editDatabase.value) payload.database = toDatabasePayload(database)
    saved.value = await saveRecoveryConfig(payload)
    clearDraftSecrets(drafts)
    clearDatabaseSecrets(database)
    intent = null
    actionError.value = null
    await load()
  } catch (problem) {
    handleProblem(problem)
    saveError.value = problem
  } finally {
    saving.value = false
  }
}

function buildAction(action) {
  const expectedActiveRevision = saved.value?.activeRevision ?? report.value?.activeRevision ?? null
  if (action === 'rollback') {
    return { request: rollbackRecovery, payload: { operationId: props.operationId, expectedActiveRevision } }
  }
  const revision = saved.value?.pendingRevision ?? report.value?.targetRevision
  return { request: applyRecovery, payload: { operationId: props.operationId, revision, expectedActiveRevision } }
}

async function submit(action) {
  const { request, payload } = buildAction(action)
  const signature = JSON.stringify([action, payload])
  // The same intent keeps its key, so a lost response cannot start a second operation.
  if (intent?.signature !== signature) intent = { signature, key: createIdempotencyKey() }
  activeAction.value = action
  actionError.value = null
  try {
    const summary = await request(payload, intent.key)
    intent = null
    emit('started', summary)
  } catch (problem) {
    if (!problem.transient) intent = null
    handleProblem(problem)
    actionError.value = { action, problem }
  } finally {
    activeAction.value = ''
  }
}

async function confirmAction(message, title, action) {
  try {
    await ElMessageBox.confirm(message, title, { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
  } catch {
    return
  }
  await submit(action)
}

function confirmRollback() {
  return confirmAction('用上一成功配置生成新版本并重启服务。失败版本的修改不会生效。', '回滚配置', 'rollback')
}

function confirmApply() {
  return confirmAction('服务将尝试启动目标版本。若再次失败会回到恢复状态，不会自动切换配置。', '应用配置', 'apply')
}

onMounted(load)

onBeforeUnmount(() => {
  clearDraftSecrets(drafts)
  clearDatabaseSecrets(database)
})
</script>

<style scoped>
.recovery {
  display: grid;
  gap: var(--space-4);
}

.section-heading h1 {
  margin: 0;
  font-size: var(--text-2xl);
}

.section-heading p {
  margin: var(--space-2) 0 0;
  color: var(--text-subtle);
}

h2 {
  margin: 0;
  font-size: var(--text-lg);
}

.recovery-loading {
  min-height: 180px;
}

.revision-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: var(--space-4);
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

.recovery-actions {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-5);
}

.action-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  padding-top: var(--space-3);
  border-top: 1px solid var(--line-subtle);
}

.action-row strong {
  color: var(--text-strong);
}

.action-row p {
  margin: 2px 0 0;
  color: var(--text-subtle);
  font-size: var(--text-sm);
}

.correction {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-2);
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 380px), 1fr));
  gap: var(--space-4);
  align-items: start;
}

.database-toggle {
  padding: var(--space-4);
}

.database-fields {
  margin-top: var(--space-4);
}

.saved-note {
  margin: 0;
  padding: var(--space-3);
  background: var(--positive-surface);
  border: 1px solid var(--positive-line);
  border-radius: var(--radius-md);
}

.correction-footer {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .revision-facts {
    grid-template-columns: minmax(0, 1fr);
  }

  .action-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
