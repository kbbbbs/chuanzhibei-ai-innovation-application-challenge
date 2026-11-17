<template>
  <section class="document-page-review" data-testid="document-page-review">
    <div class="review-heading"><h3>原页与提取结果</h3><el-button size="small" :loading="loading" @click="loadPages">刷新处理结果</el-button></div>
    <p v-if="sourceVersion !== activeVersion" class="review-note">正在处理第 {{ sourceVersion }} 版原件；检索仍使用第 {{ activeVersion }} 版。新版处理完成后才能修改文字。</p>
    <p class="review-note">识别出的文字已自动用于检索。发现识别错误时可以直接修改并保存，需要时再更新检索内容。</p>
    <div v-if="editable && (dirtyPages.length || savedPages.length || updatingPages.length)" class="edit-bar" data-testid="page-edit-bar" aria-live="polite">
      <template v-if="dirtyPages.length">
        <span>{{ dirtyPages.length }} {{ paged ? '页' : '处' }}有未保存的修改（已在本机暂存）</span>
        <el-button size="small" @click="discardDrafts">放弃修改</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="saveDrafts">保存修改</el-button>
      </template>
      <template v-else-if="savedPages.length">
        <span>已保存 {{ savedPages.length }} {{ paged ? '页' : '处' }}修改，检索仍使用原内容。<template v-if="busy">当前处理结束后可更新。</template></span>
        <el-button size="small" type="primary" :disabled="busy" @click="$emit('apply')">更新检索内容</el-button>
      </template>
      <span v-else>正在把 {{ updatingPages.length }} {{ paged ? '页' : '处' }}修改更新到检索，可以继续修改其他内容。</span>
    </div>
    <el-alert v-if="saveError" :title="saveError" type="error" show-icon @close="saveError = ''" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-empty v-if="!loading && !error && !pages.length" description="尚无处理结果，请等待当前任务或重新解析" />
    <div class="review-layout" v-loading="loading">
      <nav v-if="pages.length" class="review-pages" aria-label="选择页面">
        <button v-for="page in pages" :key="page.pageNo" type="button" :data-testid="`review-page-${page.pageNo}`" :class="{ active: selectedNo === page.pageNo }" @click="selectPage(page.pageNo)">
          <strong>{{ pageLabel(page) }}</strong><span>{{ pageMark(page) }}</span>
        </button>
      </nav>
      <div v-if="selected" class="review-content">
        <div class="review-position"><strong>{{ locationLabel(selected) }}</strong><span>{{ methodLabel(selected.method) }}</span></div>
        <el-alert v-if="selected.pageStatus === 'FAILED'" :title="processingError(selected.errorCode) || '该位置没有取得文字，可以重试。'" type="warning" :closable="false" show-icon />
        <p v-for="issue in pageNotices(selected)" :key="issue" class="review-warning">{{ issue }}</p>
        <div class="review-columns" :class="{ 'text-only': !canPreview }">
          <div v-if="canPreview" class="original-panel" v-loading="previewLoading">
            <strong>原始页面</strong>
            <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" />
            <img v-if="previewUrl" :src="previewUrl" alt="原始页面预览" />
          </div>
          <div class="extracted-panel">
            <div class="panel-title">
              <strong>用于检索的文本</strong>
              <el-tag v-if="editOf(selected.pageNo)" size="small" :type="editTag(editOf(selected.pageNo)).type">{{ editTag(editOf(selected.pageNo)).label }}</el-tag>
              <el-button v-if="editable && !(selected.pageNo in drafts)" size="small" data-testid="edit-page-text" @click="startEdit(selected)">修改文字</el-button>
            </div>
            <template v-if="selected.pageNo in drafts">
              <el-input v-model="drafts[selected.pageNo]" type="textarea" :autosize="{ minRows: 8, maxRows: 24 }" maxlength="100000" aria-label="修改本页文字" data-testid="page-text-editor" @input="persistDrafts" />
              <div class="draft-actions"><el-button size="small" @click="dropDraft(selected.pageNo)">撤销本页修改</el-button></div>
            </template>
            <template v-else>
              <div v-if="currentText(selected)" class="page-text-preview" data-testid="page-text-preview" v-html="renderMarkdown(currentText(selected), { allowRemoteImages: false })"></div><p v-else class="review-note">该{{ paged ? '页' : '位置' }}尚无可用文本</p>
            </template>
            <section class="formula-review" data-testid="formula-review">
              <strong>公式</strong>
              <p v-if="!selected.formulaVersion" class="review-warning">旧解析结果尚未检查公式，请重新解析本页。</p>
              <p v-else-if="!selected.formulas.length" class="review-note">未提取到结构化公式；这不代表原页没有公式。</p>
              <p v-if="selected.formulas.length" class="review-note">公式可对照原页查看。结构检查通过或能够排版，不代表数学内容正确。字符位置对应识别文本，不是图像坐标。</p>
              <article v-for="formula in selected.formulas" :key="formula.ordinal" class="formula-card" :data-testid="`formula-${formula.ordinal}`">
                <div>公式 {{ formula.ordinal }} · {{ formula.display ? '独立公式' : '行内公式' }} · 字符 {{ formula.start }}–{{ formula.end }}</div>
                <p v-if="formula.status !== 'STRUCTURE_OK'" class="review-warning">{{ processingError(formula.errorCode || 'FORMULA_INVALID') }}</p>
                <div v-else class="formula-preview" v-html="renderFormula(formula.latex, formula.display)"></div>
                <details open><summary>公式源码（{{ selected.formulaSource === 'OCR' ? '图像识别' : '原始文本' }}）</summary><pre>{{ formula.latex }}</pre></details>
              </article>
            </section>
            <VisualEvidence :key="`${sourceVersion}-${selected.pageNo}`" :visuals="selected.visuals" :version="selected.visualVersion" :issues="selected.visualIssues" />
            <details v-if="selected.questions.length" :key="`questions-${selected.pageNo}`" class="question-review" data-testid="question-review">
              <summary>按题号整理</summary>
              <p class="review-note">根据行首题号整理，可能出现错分或跨页缺失，使用时请对照原页。</p>
              <article v-for="(question, index) in selected.questions" :key="`${index}-${question.number}`" class="question-card">
                <strong>题号 {{ question.number || '待确认' }} · 关联插图 {{ Array.isArray(question.visualIds) ? question.visualIds.length : 0 }} 张</strong>
                <div class="question-text" v-html="renderMarkdown(String(question.text || ''), { allowRemoteImages: false })"></div>
              </article>
            </details>
            <details class="technical-details"><summary>识别详情</summary>
              <p class="review-note">{{ confidenceLabel(selected.confidence) }}</p>
              <details><summary>文字层原文</summary><pre>{{ selected.rawText || '未提取到文字层文本' }}</pre></details>
              <details v-if="selected.ocrText"><summary>识别整理文本</summary><pre>{{ selected.ocrText }}</pre></details>
              <details v-if="selected.ocrRawText"><summary>模型原始识别结果</summary><pre>{{ selected.ocrRawText }}</pre></details>
              <details v-if="selected.location?.initialOcrRawText"><summary>切换公式识别前的原始结果</summary><pre>{{ selected.location.initialOcrRawText }}</pre></details>
              <details v-if="selected.location?.resolutionRetryRawText"><summary>调整图像尺寸前的截断结果</summary><pre>{{ selected.location.resolutionRetryRawText }}</pre></details>
              <details v-if="historyOf(selected.pageNo).length > 1"><summary>修改记录（{{ historyOf(selected.pageNo).length }} 次）</summary>
                <article v-for="entry in historyOf(selected.pageNo)" :key="entry.id" class="history-entry"><span class="review-note">{{ formatTime(entry.savedAt) }}</span><pre>{{ entry.text }}</pre></article>
              </details>
            </details>
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
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import * as rag from '@/api/rag'
import { renderFormula } from '@/utils/math'
import { renderMarkdown } from '@/utils/markdown'
import VisualEvidence from './VisualEvidence.vue'
import { documentIdOf, isPagedDocument, processingError, processingVersionOf } from '@/utils/documentProcessing'
const props = defineProps({ document: { type: Object, required: true }, job: { type: Object, default: null }, canRetry: Boolean, busy: Boolean })
const emit = defineEmits(['retry', 'apply', 'saved'])
const pages = ref([]), selectedNo = ref(null), loading = ref(false), error = ref('')
const previewUrl = ref(''), previewLoading = ref(false), previewError = ref('')
const edits = ref([]), drafts = reactive({}), saving = ref(false), saveError = ref('')
const sourceVersion = computed(() => processingVersionOf(props.document, props.job))
const activeVersion = computed(() => Number(props.document.document_version || props.document.documentVersion || 1))
const paged = computed(() => pages.value.length ? pages.value.some(page => page.pageNumber != null) : isPagedDocument(props.document))
const selected = computed(() => pages.value.find(page => page.pageNo === selectedNo.value))
const canPreview = computed(() => selected.value?.pageNumber != null)
// Edits target the version retrieval uses; a pending replacement is read-only until it is published.
const editable = computed(() => props.canRetry && sourceVersion.value === activeVersion.value && pages.value.length > 0)
const latestEdits = computed(() => edits.value.filter(edit => edit.latest))
const dirtyPages = computed(() => Object.keys(drafts).map(Number).filter(no => drafts[no] !== baseText(no)))
const savedPages = computed(() => latestEdits.value.filter(edit => edit.state === 'SAVED').map(edit => edit.pageNo))
const updatingPages = computed(() => latestEdits.value.filter(edit => edit.state === 'UPDATING').map(edit => edit.pageNo))
const draftKey = computed(() => `eduloop:page-drafts:${documentIdOf(props.document)}:${activeVersion.value}`)
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
    formulaIssues: Array.isArray(page.formulaIssues) ? page.formulaIssues : [],
    visualVersion: Number(page.visualVersion || 0), visuals: Array.isArray(page.visuals) ? page.visuals : [], visualIssues: Array.isArray(page.visualIssues) ? page.visualIssues : [],
    questions: Array.isArray(page.questions) ? page.questions.filter(question => question && typeof question === 'object') : [] }
}
async function loadPages() {
  pagesController?.abort(); clearImage()
  const version = ++loadVersion, controller = new AbortController(); pagesController = controller
  const keep = selectedNo.value
  loading.value = true; error.value = ''; pages.value = []; selectedNo.value = null
  try {
    const id = documentIdOf(props.document)
    const [data, history] = await Promise.all([
      rag.getDocumentPages(id, sourceVersion.value, { signal: controller.signal }),
      props.canRetry ? rag.getPageEdits(id, activeVersion.value, { signal: controller.signal }).catch(() => null) : null,
    ])
    if (version !== loadVersion || controller.signal.aborted) return
    pages.value = rag.ragList(data).map(normalizedPage).filter(page => Number.isInteger(page.pageNo) && page.pageNo > 0)
    edits.value = history ? rag.ragList(history) : []
    restoreDrafts()
    if (pages.value.length) await selectPage(pages.value.some(page => page.pageNo === keep) ? keep : pages.value[0].pageNo)
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
const editOf = number => latestEdits.value.find(edit => edit.pageNo === number)
const historyOf = number => edits.value.filter(edit => edit.pageNo === number)
const baseText = number => editOf(number)?.text ?? pages.value.find(page => page.pageNo === number)?.chosenText ?? ''
const currentText = page => editOf(page.pageNo)?.text ?? page.chosenText
function editTag(edit) {
  return { ACTIVE: { label: '已修改 · 检索已更新', type: 'success' }, UPDATING: { label: '已修改 · 正在更新检索', type: 'warning' } }[edit.state]
    || { label: '已修改 · 检索未更新', type: 'warning' }
}
function startEdit(page) { drafts[page.pageNo] = currentText(page) || ''; persistDrafts() }
function dropDraft(number) { delete drafts[number]; persistDrafts() }
function discardDrafts() { Object.keys(drafts).forEach(key => delete drafts[key]); persistDrafts() }
// Unsaved text survives a closed drawer, refresh or failed request in this browser until it is saved or discarded.
function persistDrafts() {
  try {
    if (Object.keys(drafts).length) localStorage.setItem(draftKey.value, JSON.stringify(drafts))
    else localStorage.removeItem(draftKey.value)
  } catch { /* storage unavailable: drafts stay in memory */ }
}
function restoreDrafts() {
  Object.keys(drafts).forEach(key => delete drafts[key])
  if (!editable.value) return
  try {
    const stored = JSON.parse(localStorage.getItem(draftKey.value) || '{}')
    for (const [key, text] of Object.entries(stored)) if (typeof text === 'string' && pages.value.some(page => page.pageNo === Number(key))) drafts[Number(key)] = text
  } catch { /* ignore unreadable drafts */ }
}
async function saveDrafts() {
  const changes = dirtyPages.value.map(pageNo => ({ pageNo, text: drafts[pageNo] }))
  if (changes.some(change => !change.text.trim())) { saveError.value = '页面文字不能为空。'; return }
  saving.value = true; saveError.value = ''
  try {
    edits.value = rag.ragList(await rag.savePageEdits(documentIdOf(props.document), activeVersion.value, changes))
    changes.forEach(change => delete drafts[change.pageNo]); persistDrafts()
    emit('saved')
  } catch (problem) { saveError.value = `${failure(problem, '保存失败')}。修改仍保留在本机，可以稍后重试。` }
  finally { saving.value = false }
}
function pageLabel(page) {
  if (page.pageNumber == null) return `位置 ${page.pageNo}`
  return /\.pptx$/i.test(props.document.file_name || '') ? `幻灯片 ${page.pageNumber}` : `第 ${page.pageNumber} 页`
}
function pageMark(page) {
  if (dirtyPages.value.includes(page.pageNo)) return '未保存'
  if (editOf(page.pageNo)) return '已修改'
  if (page.pageStatus === 'FAILED') return '未取得文字'
  if (page.needsReview) return '有提示'
  return { BLANK: '空白', QUEUED: '等待处理', OCR: '识别中' }[page.pageStatus] || '可检索'
}
function pageNotices(page) {
  const notices = []
  if (page.formulaIssues.includes('FORMULA_STRUCTURE_MISSING')) notices.push(processingError('FORMULA_STRUCTURE_MISSING'))
  if (page.method === 'VISUAL_REFERENCE') notices.push('本页主要是图片，回答时会引用原图。')
  return notices
}
function locationLabel(page) {
  if (typeof page.location === 'string' && page.location) return page.location
  const start = page.location?.charStart ?? page.location?.start, end = page.location?.charEnd ?? page.location?.end
  if (Number.isFinite(start) && Number.isFinite(end)) return `字符 ${start}–${end}`
  return pageLabel(page)
}
function methodLabel(method) { return { TEXT_LAYER: '文字层提取', TEXT: '文字层提取', OCR: '图中文字识别', MERGED: '文字层与识别文本合并', BLANK: '空白页', VISUAL_REFERENCE: '原页图像参考', MANUAL: '人工修改' }[method] || '处理方式待确认' }
function confidenceLabel(value) {
  return typeof value === 'number' && Number.isFinite(value) ? `模型返回置信度：${Math.round(value <= 1 ? value * 100 : value)}%（不代表文字正确率）` : '服务未提供置信度'
}
const formatTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : ''
watch([() => documentIdOf(props.document), () => sourceVersion.value,
  () => props.job?.parseRevision || props.document.parse_revision || props.document.parseRevision,
  () => props.job?.stage || props.job?.status], loadPages, { immediate: true })
onBeforeUnmount(() => { loadVersion++; pagesController?.abort(); clearImage() })
</script>

<style scoped>
.page-text-preview { line-height: 1.8; overflow-wrap: anywhere; overflow-x: auto; }.page-text-preview :deep(table) { border-collapse: collapse; width: 100%; }.page-text-preview :deep(th),.page-text-preview :deep(td) { border: 1px solid var(--line-subtle); padding: 6px 10px; }
.review-heading,.review-position,.review-actions,.edit-bar,.panel-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }.review-heading h3 { margin: 0; }
.review-note { color: var(--text-subtle); font-size: var(--text-xs); line-height: 1.7; }.review-warning { color: var(--caution); font-size: var(--text-sm); }
.edit-bar { position: sticky; top: 0; z-index: 2; justify-content: flex-start; margin: 8px 0 12px; padding: 10px 12px; background: var(--surface-accent); border: 1px solid var(--line-accent); border-radius: var(--radius-md); font-size: var(--text-sm); }.edit-bar span { flex: 1 1 260px; }.edit-bar .el-button + .el-button { margin-left: 0; }
.panel-title { justify-content: flex-start; margin-bottom: 8px; }.panel-title .el-button { margin-left: auto; }.draft-actions { margin-top: 8px; }
.formula-review { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--line-subtle); }.formula-card { padding: 12px; margin-top: 12px; background: var(--surface-canvas); border: 1px solid var(--line-subtle); border-radius: var(--radius-md); }.formula-preview { overflow-x: auto; max-width: 100%; padding: 8px 0; }
.question-review { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--line-subtle); }.question-card { margin-top: 12px; padding: 12px; border: 1px solid var(--line-subtle); border-radius: var(--radius-md); font-size: var(--text-sm); }.question-text { line-height: 1.8; overflow-x: auto; overflow-wrap: anywhere; }
.technical-details { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--line-subtle); }.history-entry { margin-top: 8px; padding-left: 10px; border-left: 2px solid var(--line-subtle); }
.review-layout { display: grid; grid-template-columns: 130px minmax(0, 1fr); gap: 16px; min-height: 180px; margin-top: 16px; }
.review-pages { display: flex; flex-direction: column; gap: 8px; max-height: 65vh; overflow: auto; }.review-pages button { display: flex; flex-direction: column; gap: 5px; padding: 10px; background: var(--surface-base); border: 1px solid var(--line-subtle); border-radius: var(--radius-md); cursor: pointer; text-align: left; color: var(--text-body); }.review-pages button.active { border-color: var(--line-accent); background: var(--surface-accent); }.review-pages span { font-size: var(--text-xs); color: var(--text-subtle); }
.review-content { min-width: 0; }.review-position { margin-bottom: 12px; font-size: var(--text-sm); }.review-position span { color: var(--text-subtle); }
.review-columns { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; }.review-columns.text-only { grid-template-columns: 1fr; }.original-panel,.extracted-panel { min-width: 0; border: 1px solid var(--line-subtle); border-radius: var(--radius-md); padding: 12px; }.original-panel img { display: block; width: 100%; height: auto; margin-top: 12px; background: white; }.original-panel .el-alert { margin-top: 10px; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; font-family: inherit; line-height: 1.8; font-size: var(--text-md); }.review-actions { justify-content: flex-start; margin-top: 16px; }details { margin-top: 12px; }summary { cursor: pointer; color: var(--text-body); font-size: var(--text-sm); }
@media(max-width:800px) { .review-layout { grid-template-columns: 1fr; }.review-pages { flex-direction: row; flex-wrap: wrap; max-height: 140px; }.review-columns { grid-template-columns: 1fr; } }
</style>
