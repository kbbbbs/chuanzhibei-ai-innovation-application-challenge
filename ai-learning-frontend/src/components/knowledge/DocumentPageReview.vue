<template>
  <section class="document-page-review" data-testid="document-page-review">
    <div class="review-heading"><h3>原页与提取结果</h3><el-button size="small" :loading="loading" @click="loadPages">刷新处理结果</el-button></div>
    <p class="review-note">核验版本：第 {{ sourceVersion }} 版<span v-if="sourceVersion !== activeVersion">；当前可用：第 {{ activeVersion }} 版</span></p>
    <p class="review-note">逐项对照原文。处理完成不代表文字、表格或公式已经核验。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-empty v-if="!loading && !error && !pages.length" description="尚无处理结果，请等待当前任务或重新解析" />
    <div class="review-layout" v-loading="loading">
      <nav v-if="pages.length" class="review-pages" aria-label="选择核验位置">
        <button v-for="page in pages" :key="page.pageNo" type="button" :data-testid="`review-page-${page.pageNo}`" :class="{ active: selectedNo === page.pageNo }" @click="selectPage(page.pageNo)">
          <strong>{{ pageLabel(page) }}</strong><span>{{ statusLabel(page.pageStatus) }}</span>
        </button>
      </nav>
      <div v-if="selected" class="review-content">
        <div class="review-position"><strong>{{ locationLabel(selected) }}</strong><span>{{ methodLabel(selected.method) }}</span></div>
        <el-alert v-if="selected.pageStatus === 'FAILED'" :title="processingError(selected.errorCode) || '该位置处理失败，请核对原文后重试。'" type="warning" :closable="false" show-icon />
        <p v-if="selected.needsReview" class="review-warning">此处需要人工核验。</p>
        <div class="review-columns" :class="{ 'text-only': !canPreview }">
          <div v-if="canPreview" class="original-panel" v-loading="previewLoading">
            <strong>原始页面</strong>
            <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" />
            <img v-if="previewUrl" :src="previewUrl" alt="原始页面预览" />
          </div>
          <div class="extracted-panel">
            <strong>用于检索的文本</strong>
            <pre v-if="selected.chosenText">{{ selected.chosenText }}</pre><p v-else class="review-note">该{{ paged ? '页' : '位置' }}尚无可用文本</p>
            <section class="formula-review" data-testid="formula-review">
              <strong>公式核验</strong>
              <p v-if="!selected.formulaVersion" class="review-warning">旧解析结果尚未检查公式，请重新解析本页。</p>
              <p v-else-if="!selected.formulas.length" class="review-note">未提取到结构化公式；这不代表原页没有公式。</p>
              <p v-if="selected.formulas.length" class="review-note">以下公式需逐项对照原页。结构检查通过或能够排版，不代表数学内容正确。字符位置对应识别文本，不是图像坐标。</p>
              <article v-for="formula in selected.formulas" :key="formula.ordinal" class="formula-card" :data-testid="`formula-${formula.ordinal}`">
                <div>公式 {{ formula.ordinal }} · {{ formula.display ? '独立公式' : '行内公式' }} · 字符 {{ formula.start }}–{{ formula.end }}</div>
                <p v-if="formula.status !== 'STRUCTURE_OK'" class="review-warning">公式结构不完整，本页未纳入有效检索。{{ processingError(formula.errorCode) }}</p>
                <div v-else class="formula-preview" v-html="renderFormula(formula.latex, formula.display)"></div>
                <details open><summary>公式源码（{{ selected.formulaSource === 'OCR' ? '图像识别' : '原始文本' }}）</summary><pre>{{ formula.latex }}</pre></details>
              </article>
            </section>
            <VisualEvidence :key="`${sourceVersion}-${selected.pageNo}`" :visuals="selected.visuals" :version="selected.visualVersion" :issues="selected.visualIssues" />
            <details v-if="selected.questions.length" :key="`questions-${selected.pageNo}`" class="question-review" data-testid="question-review">
              <summary>按题号整理（待核验）</summary>
              <p class="review-note">根据行首题号整理，可能出现错分或跨页缺失，请对照原页核验题干、选项、公式和配图关系。</p>
              <article v-for="(question, index) in selected.questions" :key="`${index}-${question.number}`" class="question-card">
                <strong>题号 {{ question.number || '待确认' }} · 关联插图 {{ Array.isArray(question.visualIds) ? question.visualIds.length : 0 }} 张</strong>
                <div class="question-text" v-html="renderMarkdown(String(question.text || ''), { allowRemoteImages: false })"></div>
              </article>
            </details>
            <p class="review-note">{{ confidenceLabel(selected.confidence) }}</p>
            <details><summary>文字层原文</summary><pre>{{ selected.rawText || '未提取到文字层文本' }}</pre></details>
            <details v-if="selected.ocrText"><summary>识别整理文本</summary><pre>{{ selected.ocrText }}</pre></details>
            <details v-if="selected.ocrRawText"><summary>模型原始识别结果</summary><pre>{{ selected.ocrRawText }}</pre></details>
            <details v-if="selected.location?.initialOcrRawText"><summary>切换公式识别前的原始结果</summary><pre>{{ selected.location.initialOcrRawText }}</pre></details>
            <details v-if="selected.location?.resolutionRetryRawText"><summary>调整图像尺寸前的截断结果</summary><pre>{{ selected.location.resolutionRetryRawText }}</pre></details>
          </div>
        </div>
        <div v-if="canRetry" class="review-actions">
          <el-button :disabled="busy" @click="$emit('retry', { pages: [selected.pageNo], forceOcr: false })">重试此{{ paged ? '页' : '位置' }}</el-button>
          <el-button v-if="canPreview" type="primary" :disabled="busy" @click="$emit('retry', { pages: [selected.pageNo], forceOcr: true })">强制识别本页</el-button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import * as rag from '@/api/rag'
import { renderFormula } from '@/utils/math'
import { renderMarkdown } from '@/utils/markdown'
import VisualEvidence from './VisualEvidence.vue'
import { documentIdOf, isPagedDocument, processingError, processingVersionOf } from '@/utils/documentProcessing'
const props = defineProps({ document: { type: Object, required: true }, job: { type: Object, default: null }, canRetry: Boolean, busy: Boolean })
defineEmits(['retry'])
const pages = ref([]), selectedNo = ref(null), loading = ref(false), error = ref('')
const previewUrl = ref(''), previewLoading = ref(false), previewError = ref('')
const sourceVersion = computed(() => processingVersionOf(props.document, props.job))
const activeVersion = computed(() => Number(props.document.document_version || props.document.documentVersion || 1))
const paged = computed(() => pages.value.length ? pages.value.some(page => page.pageNumber != null) : isPagedDocument(props.document))
const selected = computed(() => pages.value.find(page => page.pageNo === selectedNo.value))
const canPreview = computed(() => selected.value?.pageNumber != null)
let loadVersion = 0, imageVersion = 0, pagesController = null, previewController = null
const failure = (problem, fallback) => problem?.response?.data?.msg || problem?.message || fallback
function clearImage() {
  previewController?.abort(); previewController = null; imageVersion++
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''; previewError.value = ''; previewLoading.value = false
}
function normalizedPage(page) {
  return { pageNo: Number(page.pageNo ?? page.page_no), pageNumber: page.pageNumber ?? page.page_number ?? null,
    pageStatus: String(page.pageStatus || page.page_status || 'QUEUED').toUpperCase(),
    rawText: page.rawText ?? page.raw_text ?? '', ocrRawText: page.ocrRawText ?? page.ocr_raw_text ?? '',
    ocrText: page.ocrText ?? page.ocr_text ?? '', chosenText: page.chosenText ?? page.chosen_text ?? '',
    method: page.method || '', confidence: page.confidence ?? null, errorCode: page.errorCode || page.error_code || '',
    needsReview: !!(page.needsReview ?? page.needs_review), location: page.location || '',
    formulaVersion: Number(page.formulaVersion || 0), formulas: Array.isArray(page.formulas) ? page.formulas : [], formulaSource: page.formulaSource,
    visualVersion: Number(page.visualVersion || 0), visuals: Array.isArray(page.visuals) ? page.visuals : [], visualIssues: Array.isArray(page.visualIssues) ? page.visualIssues : [],
    questions: Array.isArray(page.questions) ? page.questions.filter(question => question && typeof question === 'object') : [] }
}
async function loadPages() {
  pagesController?.abort(); clearImage()
  const version = ++loadVersion, controller = new AbortController(); pagesController = controller
  loading.value = true; error.value = ''; pages.value = []; selectedNo.value = null
  try {
    const data = rag.ragList(await rag.getDocumentPages(documentIdOf(props.document), sourceVersion.value, { signal: controller.signal }))
    if (version !== loadVersion || controller.signal.aborted) return
    pages.value = data.map(normalizedPage).filter(page => Number.isInteger(page.pageNo) && page.pageNo > 0)
    if (pages.value.length) await selectPage(pages.value[0].pageNo)
  } catch (problem) {
    if (version === loadVersion && !controller.signal.aborted) { pages.value = []; error.value = failure(problem, '无法读取逐页处理结果') }
  } finally { if (version === loadVersion) loading.value = false }
}
async function selectPage(number) {
  selectedNo.value = number; clearImage()
  if (!canPreview.value) return
  const version = ++imageVersion, controller = new AbortController(); previewController = controller
  previewLoading.value = true
  try {
    const blob = await rag.getDocumentPagePreview(documentIdOf(props.document), number,
      sourceVersion.value, { signal: controller.signal })
    if (version !== imageVersion || controller.signal.aborted) return
    previewUrl.value = URL.createObjectURL(blob)
  } catch (problem) {
    if (version === imageVersion && !controller.signal.aborted) previewError.value = failure(problem, '无法读取原页预览')
  } finally { if (version === imageVersion) previewLoading.value = false }
}
function pageLabel(page) {
  if (page.pageNumber == null) return `位置 ${page.pageNo}`
  return /\.pptx$/i.test(props.document.file_name || '') ? `幻灯片 ${page.pageNumber}` : `第 ${page.pageNumber} 页`
}
function locationLabel(page) {
  if (typeof page.location === 'string' && page.location) return page.location
  const start = page.location?.charStart ?? page.location?.start, end = page.location?.charEnd ?? page.location?.end
  if (Number.isFinite(start) && Number.isFinite(end)) return `字符 ${start}–${end}`
  return pageLabel(page)
}
function statusLabel(status) { return { READY: '已提取', TEXT_READY: '已提取', COMPLETED: '已提取', FAILED: '处理失败', BLANK: '空白', QUEUED: '等待处理', OCR: '识别中', PARTIAL: '需核验' }[status] || '待核验' }
function methodLabel(method) { return { TEXT_LAYER: '文字层提取', TEXT: '文字层提取', OCR: '图中文字识别', MERGED: '文字层与识别文本合并', BLANK: '空白页', VISUAL_REFERENCE: '原页图像参考' }[method] || '处理方式待确认' }
function confidenceLabel(value) {
  return typeof value === 'number' && Number.isFinite(value) ? `模型返回置信度：${Math.round(value <= 1 ? value * 100 : value)}%（不代表文字正确率）` : '服务未提供置信度'
}
watch([() => documentIdOf(props.document), () => sourceVersion.value,
  () => props.job?.parseRevision || props.document.parse_revision || props.document.parseRevision], loadPages, { immediate: true })
onBeforeUnmount(() => { loadVersion++; pagesController?.abort(); clearImage() })
</script>

<style scoped>
.review-heading,.review-position,.review-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }.review-heading h3 { margin: 0; }
.review-note { color: #64748b; font-size: 12px; line-height: 1.7; }.review-warning { color: #a16207; font-size: 13px; }
.formula-review { margin-top: 18px; padding-top: 12px; border-top: 1px solid #e2e8f0; }.formula-card { padding: 12px; margin-top: 12px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; }.formula-preview { overflow-x: auto; max-width: 100%; padding: 8px 0; }
.question-review { margin-top: 18px; padding-top: 12px; border-top: 1px solid #e2e8f0; }.question-card { margin-top: 12px; padding: 12px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 13px; }.question-text { line-height: 1.8; overflow-x: auto; overflow-wrap: anywhere; }
.review-layout { display: grid; grid-template-columns: 130px minmax(0, 1fr); gap: 16px; min-height: 180px; margin-top: 16px; }
.review-pages { display: flex; flex-direction: column; gap: 8px; max-height: 65vh; overflow: auto; }.review-pages button { display: flex; flex-direction: column; gap: 5px; padding: 10px; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; cursor: pointer; text-align: left; color: #334155; }.review-pages button.active { border-color: #93b4ed; background: #eff6ff; }.review-pages span { font-size: 12px; color: #64748b; }
.review-content { min-width: 0; }.review-position { margin-bottom: 12px; font-size: 13px; }.review-position span { color: #64748b; }
.review-columns { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; }.review-columns.text-only { grid-template-columns: 1fr; }.original-panel,.extracted-panel { min-width: 0; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; }.original-panel img { display: block; width: 100%; height: auto; margin-top: 12px; }.original-panel .el-alert { margin-top: 10px; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; font-family: inherit; line-height: 1.8; font-size: 14px; }.review-actions { justify-content: flex-start; margin-top: 16px; }details { margin-top: 12px; }summary { cursor: pointer; color: #475569; font-size: 13px; }
@media(max-width:800px) { .review-layout { grid-template-columns: 1fr; }.review-pages { flex-direction: row; flex-wrap: wrap; max-height: 140px; }.review-columns { grid-template-columns: 1fr; } }
</style>
