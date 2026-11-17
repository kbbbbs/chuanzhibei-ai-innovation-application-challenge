<template>
  <div class="error-notice" role="alert">
    <el-icon class="notice-icon" :size="18" aria-hidden="true"><CircleCloseFilled /></el-icon>
    <div class="notice-body">
      <strong>{{ title }}</strong>
      <p v-if="message">{{ message }}</p>
      <ul v-if="details.length">
        <li v-for="detail in details" :key="detail">{{ detail }}</li>
      </ul>
      <p v-if="code" class="notice-code">错误代码：<code>{{ code }}</code></p>
      <div v-if="$slots.actions || retryLabel" class="notice-actions">
        <el-button v-if="retryLabel" size="small" :loading="busy" @click="$emit('retry')">{{ retryLabel }}</el-button>
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { CircleCloseFilled } from '@element-plus/icons-vue'

defineProps({
  title: { type: String, default: '操作未完成' },
  message: { type: String, default: '' },
  details: { type: Array, default: () => [] },
  code: { type: String, default: '' },
  retryLabel: { type: String, default: '' },
  busy: { type: Boolean, default: false },
})

defineEmits(['retry'])
</script>

<style scoped>
.error-notice {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  color: var(--text-body);
  background: var(--critical-surface);
  border: 1px solid var(--critical-line);
  border-radius: var(--radius-md);
}

.notice-icon {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--critical);
}

.notice-body {
  min-width: 0;
  overflow-wrap: anywhere;
}

strong {
  color: var(--critical);
  font-weight: 650;
}

p {
  margin: var(--space-1) 0 0;
  line-height: 1.6;
}

ul {
  margin: var(--space-1) 0 0;
  padding-left: var(--space-4);
}

.notice-code {
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.notice-actions {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  margin-top: var(--space-2);
}

.notice-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
