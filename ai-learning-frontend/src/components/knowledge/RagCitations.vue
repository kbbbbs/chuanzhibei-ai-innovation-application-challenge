<template>
  <div v-if="citations.length" class="rag-citations">
    <span class="source-label">参考资料</span>
    <el-button v-for="(source, index) in citations" :key="source.chunkId || index" size="small" plain @click="openSource(source)">
      {{ citationLabel(source, index) }} {{ source.title || '资料' }}<span v-if="source.page"> · 第 {{ source.page }} 页</span>
    </el-button>
    <el-drawer v-model="visible" title="原文出处" size="min(620px, 95vw)" append-to-body>
      <div v-loading="loading">
        <h3>{{ selected?.title || '资料片段' }}</h3>
        <p class="source-location">
          <span v-if="selected?.page">第 {{ selected.page }} 页 · </span>
          <span v-if="selected?.ordinal != null">片段 {{ selected.ordinal }}</span>
        </p>
        <p v-if="sourceVersionLabel(selected)" class="source-location" data-testid="source-version">{{ sourceVersionLabel(selected) }}</p>
        <details v-if="selected?.indexVersion" class="source-snapshot"><summary>索引快照</summary><code data-testid="source-index-version">{{ selected.indexVersion }}</code></details>
        <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon />
        <template v-else-if="hasFormula">
          <div class="source-formula" v-html="renderMarkdown(selected?.content)"></div>
          <details><summary>查看原文源码</summary><pre class="source-text">{{ selected?.content }}</pre></details>
        </template>
        <pre v-else class="source-text">{{ selected?.content }}</pre>
        <VisualEvidence v-if="visible && !loading && !loadError && selected?.visuals?.length" :visuals="selected.visuals" />
        <el-button v-if="selected?.documentId && !courseId" :loading="downloading" @click="download">下载原始文件</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import { sourceVersionLabel } from '@/utils/retrievalStatus'
import { ElMessage } from 'element-plus'
import { getChunk, getCourseDocument, downloadDocument, unwrapRag } from '@/api/rag'
import VisualEvidence from './VisualEvidence.vue'
const props = defineProps({ citations: { type: Array, default: () => [] }, courseId: Number })
const visible = ref(false)
const loading = ref(false)
const downloading = ref(false)
const selected = ref(null)
const loadError = ref('')
const hasFormula = computed(() => /\$|\\[([]|\\begin\{/.test(selected.value?.content || ''))
let sourceVersion = 0, sourceController = null
const citationLabel = (source, index) => /^\[\d+\]$/.test(source.citation || '') ? source.citation : `[${index + 1}]`
async function openSource(source) {
  sourceController?.abort()
  const controller = new AbortController(); sourceController = controller
  const version = ++sourceVersion
  visible.value = true
  selected.value = source
  loadError.value = ''
  loading.value = false
  if (!source.chunkId) return
  loading.value = true
  try {
    let chunk
    if (props.courseId) {
      const document = unwrapRag(await getCourseDocument(props.courseId, source.documentId, source.documentVersion, { signal: controller.signal, silentError: true }))
      chunk = (document.chunks || []).find(item => (item.chunkId || item.id) === source.chunkId)
      if (!chunk) throw new Error('引用片段已不可访问，请重新提问。')
    } else chunk = unwrapRag(await getChunk(source.chunkId, { signal: controller.signal, silentError: true }))
    if (source.indexVersion && chunk.indexVersion && source.indexVersion !== chunk.indexVersion) throw new Error('引用对应的索引版本已改变，请重新检索。')
    if (source.documentVersion != null && chunk.documentVersion != null && source.documentVersion !== chunk.documentVersion) throw new Error('引用对应的资料版本已改变，请重新检索。')
    if (version === sourceVersion) selected.value = { ...source, ...chunk,
      indexVersion: chunk.indexVersion || null, documentVersion: chunk.documentVersion ?? null, parseRevision: chunk.parseRevision ?? null,
      visuals: !props.courseId && Array.isArray(chunk.visuals) ? chunk.visuals : [],
      documentId: chunk.documentId || chunk.document_id || source.documentId }
  } catch (e) {
    if (version === sourceVersion && !controller.signal.aborted) {
      selected.value = null
      loadError.value = e?.response?.data?.msg || e.message || '原文片段不可读取，资料可能已被删除或权限已变更'
    }
  } finally { if (version === sourceVersion) loading.value = false }
}
watch(visible, value => { if (!value) { sourceVersion++; sourceController?.abort(); selected.value = null; loading.value = false } })
onBeforeUnmount(() => { sourceVersion++; sourceController?.abort() })
async function download() {
  downloading.value = true
  try {
    const blob = await downloadDocument(selected.value.documentId)
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
</script>

<style scoped>
.rag-citations { display:flex; align-items:center; flex-wrap:wrap; gap:6px; margin-top:10px; border-top:1px solid #e8edf5; padding-top:10px; }
.rag-citations .el-button { margin-left:0; max-width:100%; height:auto; white-space:normal; text-align:left; padding:7px 9px; line-height:1.4; }
.source-label, .source-location { color:#64748b; font-size:12px; }
.source-snapshot { margin:10px 0; color:#64748b; font-size:12px; }.source-snapshot code { display:block; overflow-wrap:anywhere; padding:8px 0; }
.source-formula { max-width:100%; overflow-x:auto; line-height:1.8; padding:12px 0; } summary { cursor:pointer; color:#475569; }
.source-text { white-space:pre-wrap; overflow-wrap:anywhere; line-height:1.8; padding:16px; background:#f8fafc; border-radius:10px; font-family:inherit; font-size:14px; }
</style>
