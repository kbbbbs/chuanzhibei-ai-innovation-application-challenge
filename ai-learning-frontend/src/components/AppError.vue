<template>
  <div class="app-error">
    <el-result icon="error" :title="title" :sub-title="subTitle">
      <template #extra>
        <el-button type="primary" @click="emit('retry')">重试</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
const props = defineProps({
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
  padding: 40px 0;
}
</style>
