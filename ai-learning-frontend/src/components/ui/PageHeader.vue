<template>
  <header class="workspace-page-header">
    <div class="header-copy">
      <p v-if="eyebrow" class="header-eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description" class="header-description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="header-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  eyebrow: { type: String, default: '' },
})
</script>

<style scoped>
.workspace-page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-4);
  flex-wrap: wrap;
  margin-bottom: var(--space-6);
}

.header-copy {
  flex: 1 1 320px;
  min-width: 0;
}

.header-eyebrow {
  margin: 0 0 var(--space-1);
  color: var(--accent);
  font-size: var(--text-xs);
  font-weight: 650;
}

h1 {
  margin: 0;
  font-size: var(--text-2xl);
  overflow-wrap: anywhere;
}

.header-description {
  max-width: 68ch;
  margin: var(--space-2) 0 0;
  color: var(--text-subtle);
  line-height: 1.7;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.header-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
