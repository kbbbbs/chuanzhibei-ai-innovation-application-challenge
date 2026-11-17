<template>
  <div class="app-error">
    <ErrorNotice :title="title" :message="subTitle" retry-label="重试" @retry="emit('retry')" />
  </div>
</template>

<script setup>
import ErrorNotice from '@/components/ui/ErrorNotice.vue'

defineProps({
  title: {
    type: String,
    default: '加载失败',
  },
  subTitle: {
    type: String,
    default: '请求出错，请稍后重试',
  },
})

const emit = defineEmits(['retry'])
</script>

<style scoped>
.app-error {
  padding: var(--space-6) 0;
}
</style>
