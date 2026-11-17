<template>
  <div class="database-form">
    <p v-if="lockedLabels.length" class="lock-summary" role="status">
      {{ lockedLabels.join('、') }}由环境变量管理，此处不能修改。
    </p>

    <fieldset class="mode-group" :disabled="disabled || isLocked('mode')">
      <legend>数据库位置</legend>
      <el-radio-group v-model="draft.mode" class="mode-options" :disabled="disabled || isLocked('mode')">
        <el-radio value="MANAGED" border>
          <span class="mode-title">随部署提供的 MySQL</span>
          <span class="mode-detail">使用部署挂载的连接凭证，无需在此填写</span>
        </el-radio>
        <el-radio value="EXTERNAL" border>
          <span class="mode-title">外部 MySQL</span>
          <span class="mode-detail">连接你已有的数据库服务</span>
        </el-radio>
      </el-radio-group>
      <p class="field-hint">切换数据库位置不会搬迁数据。</p>
    </fieldset>

    <div v-if="draft.mode === 'EXTERNAL'" class="external-fields">
      <div class="field field-wide">
        <label for="db-host">主机</label>
        <el-input id="db-host" v-model="draft.host" :disabled="disabled || isLocked('host')" autocomplete="off" spellcheck="false" :aria-invalid="Boolean(errors.host)" />
        <p v-if="errors.host" class="field-error" role="alert">{{ errors.host }}</p>
        <p v-if="sourceNote('host')" class="field-hint">{{ sourceNote('host') }}</p>
      </div>
      <div class="field">
        <label for="db-port">端口</label>
        <el-input-number id="db-port" v-model="draft.port" :min="1" :max="65535" :precision="0" controls-position="right" :disabled="disabled || isLocked('port')" />
        <p v-if="errors.port" class="field-error" role="alert">{{ errors.port }}</p>
        <p v-if="sourceNote('port')" class="field-hint">{{ sourceNote('port') }}</p>
      </div>
      <div class="field">
        <label for="db-name">数据库名称</label>
        <el-input id="db-name" v-model="draft.database" :disabled="disabled || isLocked('database')" autocomplete="off" spellcheck="false" :aria-invalid="Boolean(errors.database)" />
        <p v-if="errors.database" class="field-error" role="alert">{{ errors.database }}</p>
        <p v-if="sourceNote('database')" class="field-hint">{{ sourceNote('database') }}</p>
      </div>
      <div class="field">
        <label for="db-user">用户名</label>
        <el-input id="db-user" v-model="draft.username" :disabled="disabled || isLocked('username')" autocomplete="off" spellcheck="false" :aria-invalid="Boolean(errors.username)" />
        <p v-if="errors.username" class="field-error" role="alert">{{ errors.username }}</p>
        <p v-if="sourceNote('username')" class="field-hint">{{ sourceNote('username') }}</p>
      </div>
      <div class="field field-wide">
        <span id="db-password-label" class="field-label">密码</span>
        <el-radio-group v-model="draft.passwordAction" :disabled="disabled || isLocked('password')" aria-labelledby="db-password-label">
          <el-radio-button v-if="allowKeep" value="KEEP">保留现有</el-radio-button>
          <el-radio-button value="REPLACE">{{ allowKeep ? '替换' : '填写密码' }}</el-radio-button>
          <el-radio-button value="CLEAR">无密码</el-radio-button>
        </el-radio-group>
        <el-input
          v-if="draft.passwordAction === 'REPLACE'"
          v-model="draft.passwordValue"
          class="password-input"
          type="password"
          show-password
          :disabled="disabled"
          autocomplete="new-password"
          aria-labelledby="db-password-label"
          :aria-invalid="Boolean(errors.passwordValue)"
        />
        <p v-if="errors.passwordValue" class="field-error" role="alert">{{ errors.passwordValue }}</p>
        <p v-if="draft.passwordAction === 'CLEAR'" class="field-hint">仅在实例策略允许空密码时可用。</p>
        <p v-if="view && draft.passwordAction === 'KEEP'" class="field-hint">
          {{ view.passwordConfigured ? '已有保存的密码，页面不显示其内容。' : '当前没有保存的密码。' }}
        </p>
        <p v-if="sourceNote('password')" class="field-hint">{{ sourceNote('password') }}</p>
      </div>
      <div class="field field-wide">
        <span id="db-tls-label" class="field-label">传输加密</span>
        <el-radio-group v-model="draft.tls" :disabled="disabled || isLocked('tls')" aria-labelledby="db-tls-label">
          <el-radio-button value="REQUIRED">要求 TLS</el-radio-button>
          <el-radio-button value="DISABLED">不加密</el-radio-button>
        </el-radio-group>
        <p v-if="draft.tls === 'DISABLED'" class="field-hint">不加密时连接内容可被网络路径上的设备读取，只适合可信内网。</p>
        <p v-if="sourceNote('tls')" class="field-hint">{{ sourceNote('tls') }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { DATABASE_FIELD_LABELS } from '@/utils/databaseConfig'
import { sourceLabel } from '@/utils/modelConfig'

const props = defineProps({
  draft: { type: Object, required: true },
  errors: { type: Object, default: () => ({}) },
  disabled: { type: Boolean, default: false },
  allowKeep: { type: Boolean, default: false },
  // The redacted recovery view; absent during first installation.
  view: { type: Object, default: null },
})

const isLocked = field => Boolean(props.view?.lockedFields?.includes(field))

const lockedLabels = computed(() => (props.view?.lockedFields || []).map(field => DATABASE_FIELD_LABELS[field] || field))

function sourceNote(field) {
  const label = sourceLabel(props.view?.source?.[field])
  if (!label) return ''
  return isLocked(field) ? `由${label}管理，此处不能修改` : `当前来源：${label}`
}
</script>

<style scoped>
.database-form {
  display: grid;
  gap: var(--space-4);
}

.lock-summary {
  margin: 0;
  padding: var(--space-3);
  color: var(--text-body);
  background: var(--caution-surface);
  border: 1px solid var(--caution-line);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
}

.mode-group {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

legend,
.field label,
.field-label {
  padding: 0;
  color: var(--text-strong);
  font-size: var(--text-sm);
  font-weight: 600;
}

.mode-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-2);
}

.mode-options :deep(.el-radio) {
  height: auto;
  margin: 0;
  padding: var(--space-3);
  white-space: normal;
  border-radius: var(--radius-md);
}

.mode-options :deep(.el-radio__label) {
  display: grid;
  gap: 2px;
  padding-left: var(--space-2);
}

.mode-title {
  color: var(--text-strong);
  font-weight: 600;
}

.mode-detail {
  color: var(--text-subtle);
  font-size: var(--text-xs);
  line-height: 1.5;
}

.external-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4) var(--space-3);
}

.field {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.field-wide {
  grid-column: 1 / -1;
}

.field :deep(.el-input-number) {
  width: 100%;
}

.password-input {
  margin-top: var(--space-1);
}

.field-error {
  margin: 0;
  color: var(--critical);
  font-size: var(--text-xs);
  font-weight: 600;
}

@media (max-width: 560px) {
  .mode-options,
  .external-fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
