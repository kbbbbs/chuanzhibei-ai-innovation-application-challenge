<template>
  <section class="original-page" v-loading="loading">
    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
    <el-button v-if="error" size="small" @click="load">重新加载原页</el-button>
    <el-empty v-else-if="!page || !documentId" description="该资料没有可预览的原页图片，请下载原始文件查看" />
    <el-image v-else-if="url" :src="url" :preview-src-list="[url]" preview-teleported fit="contain" :alt="`第 ${page} 页原图`" data-testid="source-page-image" />
    <p v-if="url">第 {{ page }} 页 · 点击图片放大查看</p>
  </section>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { getDocumentPagePreview } from '@/api/rag'
const props = defineProps({ documentId: String, page: Number, version: Number, courseId: Number })
const url = ref(''), loading = ref(false), error = ref('')
let controller, generation = 0
function clear() {
  controller?.abort(); generation++
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''; loading.value = false; error.value = ''
}
async function load() {
  clear()
  if (!props.documentId || !props.page) return
  const current = generation
  controller = new AbortController()
  loading.value = true
  try {
    const blob = await getDocumentPagePreview(props.documentId, props.page, props.version, { signal: controller.signal, courseId: props.courseId })
    if (current === generation) url.value = URL.createObjectURL(blob)
  } catch (problem) {
    if (current === generation) error.value = problem?.message || '原页图片暂时无法加载，请重试或下载原始文件'
  } finally { if (current === generation) loading.value = false }
}
watch(() => [props.documentId, props.page, props.version, props.courseId], load, { immediate: true })
onBeforeUnmount(clear)
</script>

<style scoped>
.original-page { min-height: 160px; margin: 12px 0; }
.original-page :deep(.el-image) { display: block; width: 100%; background: white; }
.original-page :deep(img) { display: block; width: 100%; height: auto; }
.original-page p { font-size: var(--text-xs); color: var(--text-subtle); text-align: center; }
.original-page .el-button { margin-top: 12px; }
</style>
