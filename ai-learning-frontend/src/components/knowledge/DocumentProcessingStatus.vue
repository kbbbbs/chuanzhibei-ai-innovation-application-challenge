<template>
  <div class="document-processing-status" :data-testid="`processing-status-${documentIdOf(document)}`" aria-live="polite">
    <el-tag :type="tagType">{{ label }}</el-tag>
    <el-progress v-if="job || active" :percentage="percentage" :status="phase === 'FAILED' ? 'exception' : undefined" :stroke-width="6" />
    <p v-if="total > 0" class="processing-detail">可用{{ unit }}：{{ completed }}/{{ total }}</p>
    <p v-if="missing.length" class="processing-warning">缺失{{ unit }}：{{ missing.join('、') }}</p>
    <p v-if="needsReview.length" class="processing-warning">需核验{{ unit }}：{{ needsReview.join('、') }}</p>
    <p v-if="error" class="processing-error">{{ error }}</p>
    <p v-else-if="failureMessage" class="processing-error">{{ failureMessage }}</p>
    <p v-if="phase === 'COMPLETED' && state === 'READY'" class="processing-detail">处理结果仍需核对原文。</p>
    <div v-if="canWrite" class="processing-actions">
      <el-button v-if="active && jobId" size="small" :disabled="busy || cancelRequested" :loading="busy" @click="$emit('cancel')">{{ cancelRequested ? '等待取消' : '取消处理' }}</el-button>
      <el-button v-if="!active && missing.length" size="small" :disabled="busy" @click="$emit('retry', { pages: missing, forceOcr: false })">重试失败{{ unit }}</el-button>
      <el-button v-if="!active" size="small" :disabled="busy" @click="$emit('retry', { pages: [], forceOcr: false })">重新解析全部</el-button>
      <el-button v-if="!active && hasIndex" size="small" :disabled="busy" @click="$emit('reindex')">重建向量索引</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { documentIdOf, jobIdOf, isPagedDocument, processingStage, processingDocumentState, isProcessingActive, pageNumbers, processingError, processingVersionOf } from '@/utils/documentProcessing'
const props = defineProps({ document: { type: Object, required: true }, job: { type: Object, default: null }, canWrite: Boolean, busy: Boolean, error: { type: String, default: '' } })
defineEmits(['cancel', 'retry', 'reindex'])
const phase = computed(() => processingStage(props.document, props.job))
const state = computed(() => processingDocumentState(props.document, props.job))
const active = computed(() => isProcessingActive(props.document, props.job))
const jobId = computed(() => props.job?.jobId || props.job?.id || jobIdOf(props.document))
const cancelRequested = computed(() => !!(props.job?.cancelRequested || props.job?.cancel_requested))
const unit = computed(() => {
  const activeVersion = Number(props.document.document_version || props.document.documentVersion || 1)
  if (processingVersionOf(props.document, props.job) !== activeVersion) return '位置'
  return isPagedDocument(props.document) ? '页' : '段'
})
const total = computed(() => Number(props.job?.totalPages ?? props.job?.total_pages ?? props.document.page_count ?? 0))
const completed = computed(() => Number(props.job?.completedPages ?? props.job?.completed_pages ?? 0))
const missing = computed(() => pageNumbers(props.job?.failedPages ?? props.document.unindexed_pages))
const needsReview = computed(() => pageNumbers(props.job?.needsReviewPages ?? props.document.needs_review_pages))
const percentage = computed(() => Math.max(0, Math.min(100, Number(props.job?.progress ?? props.document.parse_progress ?? 0) || 0)))
const hasIndex = computed(() => !!props.document.active_index_version || Number(props.document.chunk_count) > 0)
const failureMessage = computed(() => processingError(props.job?.errorCode || props.job?.error_code || props.document.error_code))
const label = computed(() => {
  if (active.value && cancelRequested.value) return '正在取消，等待当前处理结束'
  if (!active.value && state.value === 'PARTIAL') return '部分可用'
  return { QUEUED: '排队等待处理', PARSING: '正在提取原文', OCR: '正在识别图中文字', CHUNKING: '正在整理出处片段', EMBEDDING: '正在建立检索索引', COMPLETED: '处理完成', FAILED: '处理失败', CANCELLED: '已取消', PARTIAL: '部分可用' }[phase.value] || '状态待确认'
})
const tagType = computed(() => phase.value === 'FAILED' ? 'danger' : phase.value === 'CANCELLED' ? 'info' : state.value === 'PARTIAL' || active.value ? 'warning' : state.value === 'READY' ? 'success' : 'info')
</script>

<style scoped>
.document-processing-status { min-width: 190px; padding: 4px 0; }
.document-processing-status .el-progress { margin-top: 10px; max-width: 230px; }
.processing-detail,.processing-warning,.processing-error { margin: 6px 0; font-size: 12px; line-height: 1.6; white-space: normal; }
.processing-detail { color: #64748b; }.processing-warning { color: #a16207; }.processing-error { color: #b91c1c; }
.processing-actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }.processing-actions .el-button + .el-button { margin-left: 0; }
</style>
