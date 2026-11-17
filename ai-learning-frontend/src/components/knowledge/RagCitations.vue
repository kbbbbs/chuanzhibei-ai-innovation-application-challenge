<template>
  <div v-if="citations.length" class="rag-citations" :class="{ inline }">
    <span v-if="!inline" class="source-label">参考资料</span>
    <el-button v-for="(source, index) in (inline ? [] : citations)" :key="source.chunkId || index" size="small" plain @click="openSource(source)">
      {{ citationLabel(source, index) }} {{ source.title || '资料' }}<span v-if="source.page"> · 第 {{ source.page }} 页</span>
    </el-button>
    <component :is="inline ? 'section' : ElDrawer" v-model="visible" class="source-body" title="原文出处" size="min(900px, 96vw)" append-to-body>
      <div v-loading="loading">
        <h3 v-if="!inline">{{ selected?.title || '资料片段' }}</h3>
        <p v-if="!inline" class="source-location">
          <span v-if="selected?.page">第 {{ selected.page }} 页 · </span>
          <span v-if="selected?.ordinal != null">片段 {{ selected.ordinal }}</span>
        </p>
        <p v-if="sourceVersionLabel(selected)" class="source-location" data-testid="source-version">{{ sourceVersionLabel(selected) }}</p>
        <details v-if="selected?.indexVersion" class="source-snapshot"><summary>索引快照</summary><code data-testid="source-index-version">{{ selected.indexVersion }}</code></details>
        <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon />
        <OriginalPagePreview v-if="visible && !loading && !loadError && selected" :course-id="studentCourseId" :document-id="selected.documentId" :page="selected.page == null ? undefined : Number(selected.page)" :version="selected.documentVersion == null ? undefined : Number(selected.documentVersion)" />
        <template v-if="inline && !loading && !loadError && selected">
          <el-button :aria-expanded="textVisible" @click="textVisible = !textVisible">{{ textVisible ? '收起原文' : '查看原文' }}</el-button>
          <pre v-if="textVisible" class="source-text">{{ selected.content || '暂无文字内容' }}</pre>
        </template>
        <el-button v-if="selected?.documentId" :loading="downloading" @click="download">下载原始文件</el-button>
      </div>
    </component>
  </div>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { sourceVersionLabel } from '@/utils/retrievalStatus'
import { ElMessage, ElDrawer } from 'element-plus'
import { getChunk, getCourseChunk, downloadCourseDocument, downloadDocument, unwrapRag } from '@/api/rag'
import OriginalPagePreview from './OriginalPagePreview.vue'
import { useUserStore } from '@/store/user'
const props = defineProps({ citations: { type: Array, default: () => [] }, inline: Boolean, courseId: Number })
const user = useUserStore()
const studentCourseId = computed(() => user.roleCode === 'student' ? props.courseId : undefined)
const textVisible = ref(false)
const visible = ref(false)
const loading = ref(false)
const downloading = ref(false)
const selected = ref(null)
const loadError = ref('')
let sourceVersion = 0, sourceController = null
const citationLabel = (source, index) => /^\[\d+\]$/.test(source.citation || '') ? source.citation : `[${index + 1}]`
async function openSource(source) {
  sourceController?.abort()
  textVisible.value = false
  const controller = new AbortController(); sourceController = controller
  const version = ++sourceVersion
  visible.value = true
  selected.value = source
  loadError.value = ''
  loading.value = false
  if (!source.chunkId) return
  loading.value = true
  try {
    const config = { signal: controller.signal, silentError: true }
    const chunk = unwrapRag(await (studentCourseId.value
      ? getCourseChunk(studentCourseId.value, source.chunkId, config)
      : getChunk(source.chunkId, config)))
    if (source.indexVersion && chunk.indexVersion && source.indexVersion !== chunk.indexVersion) throw new Error('引用对应的索引版本已改变，请重新检索。')
    if (source.documentVersion != null && chunk.documentVersion != null && source.documentVersion !== chunk.documentVersion) throw new Error('引用对应的资料版本已改变，请重新检索。')
    if (version === sourceVersion) selected.value = { ...source, ...chunk,
      indexVersion: chunk.indexVersion || null, documentVersion: chunk.documentVersion ?? null, parseRevision: chunk.parseRevision ?? null,
      visuals: Array.isArray(chunk.visuals) ? chunk.visuals : [],
      documentId: chunk.documentId || chunk.document_id || source.documentId }
  } catch (e) {
    if (version === sourceVersion && !controller.signal.aborted) {
      selected.value = null
      loadError.value = e?.response?.data?.msg || e.message || '原文片段不可读取，资料可能已被删除或权限已变更'
    }
  } finally { if (version === sourceVersion) loading.value = false }
}
watch(() => props.inline ? props.citations[0] : null, source => { if (source) openSource(source) }, { immediate: true })
watch(visible, value => { if (!value) { sourceVersion++; sourceController?.abort(); selected.value = null; loading.value = false } })
onBeforeUnmount(() => { sourceVersion++; sourceController?.abort() })
async function download() {
  downloading.value = true
  try {
    const blob = await (studentCourseId.value
      ? downloadCourseDocument(studentCourseId.value, selected.value.documentId, selected.value.documentVersion)
      : downloadDocument(selected.value.documentId))
    if (!(blob instanceof Blob)) throw new Error('原始文件下载失败')
    if (blob.type.includes('application/json')) {
      const payload = JSON.parse(await blob.text())
      if (payload.code && String(payload.code) !== '200') throw new Error(payload.msg || '文件不可访问')
    }
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = selected.value.file_name || selected.value.title || '资料'
    anchor.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (e) { ElMessage.error(e.message || '文件下载失败') }
  finally { downloading.value = false }
}
defineExpose({ openSource })
</script>

<style scoped>
.rag-citations { display:flex; align-items:center; flex-wrap:wrap; gap:6px; margin-top:10px; border-top:1px solid var(--line-subtle); padding-top:10px; }
.rag-citations .el-button { margin-left:0; max-width:100%; height:auto; white-space:normal; text-align:left; padding:7px 9px; line-height:1.4; }
.rag-citations.inline { display: block; border-top: 0; padding-top: 0; }.inline .source-body { width: 100%; }.inline :deep(.original-page) { max-width: 720px; margin: 12px auto; }
.source-label, .source-location { color:var(--text-subtle); font-size:var(--text-xs); }
.source-snapshot { margin:10px 0; color:var(--text-subtle); font-size:var(--text-xs); }.source-snapshot code { display:block; overflow-wrap:anywhere; padding:8px 0; }
.source-formula { max-width:100%; overflow-x:auto; line-height:1.8; padding:12px 0; } summary { cursor:pointer; color:var(--text-body); }
.source-text { white-space:pre-wrap; overflow-wrap:anywhere; line-height:1.8; padding:var(--space-4); color:var(--text-body); background:var(--surface-canvas); border-radius:var(--radius-md); font-family:inherit; font-size:var(--text-md); }
</style>
