<template>
  <div class="app-empty">
    <el-empty :description="description">
      <template #default>
        <slot />
      </template>
    </el-empty>
  </div>
</template>

<script setup>
defineProps({
  description: {
    type: String,
    default: '暂无数据',
  },
})
</script>

<style scoped>
.app-empty {
  padding: 40px 0;
}
</style>
