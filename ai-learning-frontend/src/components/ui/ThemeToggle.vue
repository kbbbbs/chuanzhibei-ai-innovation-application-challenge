<template>
  <el-dropdown trigger="click" @command="setThemePreference">
    <button type="button" class="theme-trigger" :aria-label="`切换主题，当前${currentLabel}`">
      <el-icon :size="18"><component :is="resolvedTheme === 'dark' ? Moon : Sunny" /></el-icon>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="option in options"
          :key="option.value"
          :command="option.value"
          :icon="option.icon"
          :class="{ 'is-current-theme': preference === option.value }"
        >
          {{ option.label }}<span v-if="preference === option.value" class="theme-current">（当前）</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { computed } from 'vue'
import { Monitor, Moon, Sunny } from '@element-plus/icons-vue'
import { useTheme } from '@/composables/useTheme'

const { preference, resolvedTheme, setThemePreference } = useTheme()

const options = [
  { value: 'light', label: '浅色', icon: Sunny },
  { value: 'dark', label: '深色', icon: Moon },
  { value: 'system', label: '跟随系统', icon: Monitor },
]

const currentLabel = computed(() => options.find(option => option.value === preference.value)?.label || '浅色')
</script>

<style scoped>
.theme-trigger {
  display: inline-grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color .15s var(--ease-standard), color .15s var(--ease-standard);
}

.theme-trigger:hover {
  color: var(--text-strong);
  background: var(--surface-hover);
}

.theme-current {
  margin-left: var(--space-1);
  color: var(--text-subtle);
}
</style>
