<template>
  <article class="role-editor surface-card" :aria-labelledby="headingId">
    <header class="role-header">
      <div class="role-title">
        <h3 :id="headingId">{{ meta.title }}</h3>
        <p>{{ meta.summary }}</p>
      </div>
      <el-switch
        v-model="draft.enabled"
        :disabled="disabled || isLocked('enabled')"
        :aria-label="`启用${meta.title}`"
        active-text="启用"
      />
    </header>

    <dl class="status-row">
      <div>
        <dt>配置</dt>
        <dd><StatusBadge :label="configuredState.label" :tone="configuredState.tone" /></dd>
      </div>
      <div>
        <dt>连通探针</dt>
        <dd><StatusBadge :label="probedState.label" :tone="probedState.tone" /></dd>
      </div>
      <div>
        <dt>真实课程质量</dt>
        <dd><StatusBadge label="未在此评估" tone="neutral" /></dd>
      </div>
    </dl>

    <div v-if="draft.enabled" class="role-form">
      <div v-if="role === 'planning'" class="inherit-row">
        <el-checkbox v-model="inheritsGeneration" :disabled="disabled || isLocked('baseUrl') || isLocked('model')">
          整组继承内容生成的地址、模型和凭证
        </el-checkbox>
        <p v-if="errors.inheritFrom" class="field-error" role="alert">{{ errors.inheritFrom }}</p>
      </div>

      <template v-if="!draft.inheritFrom">
        <div class="field">
          <label :for="`${role}-preset`">服务预设</label>
          <el-select
            :id="`${role}-preset`"
            v-model="presetId"
            :disabled="disabled"
            :aria-label="`${meta.title}服务预设`"
            @change="applyPreset"
          >
            <el-option v-for="preset in presets" :key="preset.id" :label="preset.label" :value="preset.id" />
          </el-select>
          <p class="field-hint">预设只填入可修改的地址和模型，不代表服务已通过探测。{{ ADAPTER_PROTOCOL_NOTE }}</p>
        </div>

        <div class="field">
          <label :for="`${role}-base-url`">接口地址</label>
          <el-input
            :id="`${role}-base-url`"
            v-model="draft.baseUrl"
            :disabled="disabled || isLocked('baseUrl')"
            placeholder="https://example.com/v1"
            autocomplete="off"
            spellcheck="false"
            :aria-invalid="Boolean(errors.baseUrl)"
          />
          <p v-if="errors.baseUrl" class="field-error" role="alert">{{ errors.baseUrl }}</p>
          <p v-else-if="plainHttpRemote" class="field-hint">非本机明文 HTTP 地址需要部署者加入可信目标后才能使用。</p>
          <FieldSource :view="view" field="baseUrl" />
        </div>

        <div class="field">
          <label :for="`${role}-model`">模型名称</label>
          <el-input
            :id="`${role}-model`"
            v-model="draft.model"
            :disabled="disabled || isLocked('model')"
            autocomplete="off"
            spellcheck="false"
            :aria-invalid="Boolean(errors.model)"
          />
          <p v-if="errors.model" class="field-error" role="alert">{{ errors.model }}</p>
          <FieldSource :view="view" field="model" />
        </div>

        <div class="field">
          <span :id="`${role}-secret-label`" class="field-label">凭证</span>
          <el-radio-group
            v-if="view?.secretConfigured"
            v-model="draft.secretAction"
            class="secret-actions"
            :disabled="disabled || isLocked('secret')"
            :aria-labelledby="`${role}-secret-label`"
          >
            <el-radio-button value="KEEP">保留现有</el-radio-button>
            <el-radio-button value="REPLACE">替换</el-radio-button>
            <el-radio-button value="CLEAR">清除</el-radio-button>
          </el-radio-group>
          <el-input
            v-if="draft.secretAction === 'REPLACE'"
            v-model="draft.secretValue"
            class="secret-input"
            type="password"
            show-password
            :disabled="disabled"
            autocomplete="new-password"
            :aria-labelledby="`${role}-secret-label`"
            :aria-invalid="Boolean(errors.secret)"
            placeholder="填写该服务自己的 API Key"
          />
          <p v-if="errors.secret" class="field-error" role="alert">{{ errors.secret }}</p>
          <p class="field-hint">
            {{ secretHint }}
          </p>
          <FieldSource :view="view" field="secret" />
        </div>
      </template>

      <div class="field-pair">
        <div class="field">
          <label :for="`${role}-timeout`">超时（秒）</label>
          <el-input-number
            :id="`${role}-timeout`"
            v-model="draft.timeoutSeconds"
            :min="1"
            :max="300"
            :precision="0"
            controls-position="right"
            :disabled="disabled || isLocked('timeoutSeconds')"
          />
          <p v-if="errors.timeoutSeconds" class="field-error" role="alert">{{ errors.timeoutSeconds }}</p>
          <FieldSource :view="view" field="timeoutSeconds" />
        </div>
        <div v-if="meta.protocol === 'chat' && !draft.inheritFrom" class="field">
          <label :for="`${role}-context`">声明上下文窗口（可选）</label>
          <el-input-number
            :id="`${role}-context`"
            v-model="draft.contextWindowTokens"
            :min="0"
            :step="1000"
            :precision="0"
            controls-position="right"
            :disabled="disabled"
          />
          <p class="field-hint">由部署者声明，0 表示未声明；探测不会推断该值。</p>
        </div>
      </div>
    </div>
    <p v-else class="disabled-note">{{ disabledNote }}</p>

    <ErrorNotice
      v-if="serverErrors.length"
      title="服务未接受此项配置"
      :details="serverErrors.map(item => item.message)"
    />

    <footer v-if="probeEnabled" class="role-footer">
      <div class="probe-actions">
        <el-button v-if="!probing" :disabled="disabled || !draft.enabled" @click="$emit('probe', role)">探测草稿</el-button>
        <el-button v-else @click="$emit('cancel-probe', role)">取消探测</el-button>
        <slot name="probe-actions" />
      </div>
      <p class="field-hint">探测只在点击后运行，会向该服务发送少量真实请求，可能产生费用。结果不会保存配置。</p>
      <p v-if="probing" class="probe-running" role="status">正在探测{{ meta.title }}，请稍候…</p>
    </footer>

    <ErrorNotice
      v-if="probeError"
      title="探测未完成"
      :message="probeError.message"
      :code="probeError.errorCode"
      :retry-label="probeError.retryable ? '重新探测' : ''"
      @retry="$emit('probe', role)"
    />

    <div v-if="probeReport" class="probe-report">
      <p class="probe-scope">探测对象：{{ probeScope }}</p>
      <div class="table-scroll">
        <table>
          <caption class="visually-hidden">{{ meta.title }}探测结果</caption>
          <thead>
            <tr><th scope="col">检查项</th><th scope="col">结果</th><th scope="col">说明</th><th scope="col">耗时</th></tr>
          </thead>
          <tbody>
            <tr v-for="check in probeReport.checks" :key="check.name">
              <td>{{ checkLabel(check.name) }}</td>
              <td>{{ checkStatusLabel(check.status) }}</td>
              <td><code>{{ check.detail }}</code></td>
              <td>{{ check.latencyMs }} ms</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="field-hint">探测只说明样本请求在本次结果如此，不代表真实课程内容的质量。</p>
    </div>
  </article>
</template>

<script setup>
import { computed, h, ref } from 'vue'
import { Lock } from '@element-plus/icons-vue'
import { ElIcon } from 'element-plus'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import {
  ADAPTER_PROTOCOL_NOTE,
  ENDPOINT_PRESETS,
  ROLE_META,
  checkLabel,
  checkStatusLabel,
  configurationState,
  draftConfigurationState,
  probeState,
  sourceLabel,
} from '@/utils/modelConfig'

const props = defineProps({
  role: { type: String, required: true },
  draft: { type: Object, required: true },
  view: { type: Object, default: null },
  errors: { type: Object, default: () => ({}) },
  serverErrors: { type: Array, default: () => [] },
  dirty: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  probeEnabled: { type: Boolean, default: true },
  probing: { type: Boolean, default: false },
  probeReport: { type: Object, default: null },
  probeScope: { type: String, default: '当前草稿' },
  probeError: { type: Object, default: null },
})

defineEmits(['probe', 'cancel-probe'])

// Shows where a field's current value comes from and whether the environment locks it.
const FieldSource = (sourceProps) => {
  const label = sourceLabel(sourceProps.view?.source?.[sourceProps.field])
  const locked = sourceProps.view?.lockedFields?.includes(sourceProps.field)
  if (!label) return null
  return h('p', { class: 'field-source' }, [
    locked ? h(ElIcon, { size: 12, 'aria-hidden': 'true' }, () => h(Lock)) : null,
    locked ? `由${label}管理，此处不能修改` : `当前来源：${label}`,
  ])
}
FieldSource.props = ['view', 'field']

const meta = computed(() => ROLE_META[props.role])
const headingId = computed(() => `model-role-${props.role}`)
const presets = computed(() => ENDPOINT_PRESETS[meta.value.protocol])
const presetId = ref('custom')

const isLocked = field => Boolean(props.view?.lockedFields?.includes(field))

const inheritsGeneration = computed({
  get: () => props.draft.inheritFrom === 'generation',
  set: value => {
    props.draft.inheritFrom = value ? 'generation' : null
    props.draft.secretValue = ''
    props.draft.secretAction = value || props.view?.secretConfigured ? 'KEEP' : 'REPLACE'
  },
})

const configuredState = computed(() => {
  if (props.dirty || !props.view) return draftConfigurationState(props.draft)
  return configurationState(props.view)
})
const probedState = computed(() => probeState(props.probeReport))

const plainHttpRemote = computed(() => {
  try {
    const url = new URL(props.draft.baseUrl.trim())
    return url.protocol === 'http:' && !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
  } catch {
    return false
  }
})

const secretHint = computed(() => {
  if (props.draft.secretAction === 'REPLACE') return '凭证只随本次请求发送，页面不会回显，也不会保存在浏览器。'
  if (props.view?.secretConfigured) return '已有保存的凭证，页面不显示其内容。凭证需与该服务匹配，不会跨服务复用。'
  return '凭证需与该服务匹配，不会跨服务复用。'
})

const disabledNote = computed(() => {
  if (props.role === 'embedding') return '未启用时，资料检索使用关键词方式，基础功能不受影响。'
  if (props.role === 'ocr') return '未启用时，扫描件和图片内容不会被识别。'
  return '未启用时，依赖该模型的 AI 功能会提示未配置，其余功能照常使用。'
})

if (props.draft.secretAction === 'KEEP' && !props.view?.secretConfigured && !props.draft.inheritFrom) {
  props.draft.secretAction = 'REPLACE'
}

function applyPreset(id) {
  const preset = presets.value.find(item => item.id === id)
  if (!preset || id === 'custom') return
  if (!isLocked('baseUrl')) props.draft.baseUrl = preset.baseUrl
  if (!isLocked('model')) props.draft.model = preset.model
}
</script>

<style scoped>
.role-editor {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
  padding: var(--space-5);
}

.role-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-3);
}

.role-title {
  min-width: 0;
}

h3 {
  margin: 0;
  font-size: var(--text-lg);
}

.role-title p {
  margin: var(--space-1) 0 0;
  color: var(--text-subtle);
  font-size: var(--text-sm);
}

.status-row {
  display: flex;
  gap: var(--space-3) var(--space-5);
  flex-wrap: wrap;
  margin: 0;
  padding: var(--space-3);
  background: var(--surface-canvas);
  border-radius: var(--radius-md);
}

dt {
  margin-bottom: 2px;
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

dd {
  margin: 0;
}

.role-form {
  display: grid;
  gap: var(--space-4);
}

.field {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.field label,
.field-label {
  color: var(--text-strong);
  font-size: var(--text-sm);
  font-weight: 600;
}

.field :deep(.el-input-number) {
  width: 100%;
}

.field-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.field-error {
  margin: 0;
  color: var(--critical);
  font-size: var(--text-xs);
  font-weight: 600;
}

.field-hint {
  margin: 0;
}

.field :deep(.field-source) {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.secret-actions {
  margin-bottom: var(--space-1);
}

.inherit-row :deep(.el-checkbox) {
  height: auto;
  white-space: normal;
}

.disabled-note {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--text-sm);
}

.role-footer {
  display: grid;
  gap: var(--space-2);
  padding-top: var(--space-3);
  border-top: 1px solid var(--line-subtle);
}

.probe-actions {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.probe-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.probe-running {
  margin: 0;
  color: var(--text-body);
  font-size: var(--text-sm);
}

.probe-scope {
  margin: 0 0 var(--space-1);
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.probe-report table {
  width: 100%;
  min-width: 420px;
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.probe-report th {
  color: var(--text-subtle);
  font-size: var(--text-xs);
  font-weight: 600;
  text-align: left;
}

.probe-report th,
.probe-report td {
  padding: 6px var(--space-2);
  border-bottom: 1px solid var(--line-subtle);
}

.probe-report code {
  font-size: var(--text-xs);
  overflow-wrap: anywhere;
}

.probe-report .field-hint {
  margin-top: var(--space-2);
}

@media (max-width: 560px) {
  .field-pair {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
