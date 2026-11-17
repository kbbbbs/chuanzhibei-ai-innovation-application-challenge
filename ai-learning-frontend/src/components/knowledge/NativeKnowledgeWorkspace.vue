<template>
  <AssistantConversation v-if="workspaceTab === 'chat'" :course-id="courseId" />
  <div v-else class="native-kb-workspace" :class="{ 'course-workspace': courseId, embedded }">
    <aside v-if="!courseId" class="library-panel" v-loading="kbLoading">
      <div class="panel-heading"><strong>课程知识库</strong><el-button v-if="manage" size="small" type="primary" @click="openCreate">新建</el-button></div>
      <el-input v-model="keyword" clearable placeholder="搜索名称或描述" aria-label="搜索知识库" />
      <el-radio-group v-model="scope" size="small" class="scope-tabs" @change="loadLibraries">
        <el-radio-button value="all" label="all">全部可用</el-radio-button>
        <el-radio-button value="mine" label="mine">我的</el-radio-button>
        
      </el-radio-group>
      <ErrorNotice v-if="kbError" title="知识库未加载" :message="kbError" retry-label="重新加载" :busy="kbLoading" @retry="loadLibraries()" />
      <el-empty v-else-if="!filteredLibraries.length && !kbLoading" description="暂无可用知识库" />
      <button v-for="kb in filteredLibraries" :key="kb.id" type="button" class="library-item" :class="{ active:selected?.id === kb.id }" :aria-current="selected?.id === kb.id ? 'true' : undefined" @click="chooseLibrary(kb)">
        <span class="library-title">{{ kb.name }} <el-tag size="small" type="info">仅所有者管理</el-tag></span>
        <span class="library-description">{{ kb.description || '暂无描述' }}</span>
        <span class="library-count">{{ kb.knowledge_count || 0 }} 份资料{{ kb.read_only ? ' · 只读' : '' }}</span>
      </button>
    </aside>

    <section class="workspace-main" aria-label="知识库内容">
      <ErrorNotice v-if="courseId && kbError" title="课程资料未加载" :message="kbError" retry-label="重新加载" @retry="loadLibraries" />
      <el-empty v-if="!selected" description="选择知识库，查看资料、检索来源或提问" />
      <template v-else>
        <div v-if="!embedded" class="workspace-heading">
          <div><h3>{{ selected.name }}</h3><p>{{ selected.description || '基于课程资料学习，让每个回答有据可查。' }}</p></div>
          <div class="heading-actions" v-if="manage">
            <el-button v-if="canWrite && !courseId" @click="openEdit">编辑资料库</el-button>
            <el-button v-if="manage" type="success" @click="$emit('generate', selected)">根据资料出题</el-button>
            <el-button v-if="canWrite && !courseId" type="danger" plain @click="deleteLibrary">删除</el-button>
          </div>
        </div>
        <el-alert v-if="!courseId" title="只有资料所有者可以管理。学生学习资料需另行授权给课程。" type="info" :closable="false" show-icon />
        <el-tabs v-model="activeTab" class="workspace-tabs" :class="{ 'fixed-tab': workspaceTab }">
          <el-tab-pane label="资料与出处" name="documents">
            <div class="document-toolbar" data-testid="document-toolbar">
              <div class="document-primary-actions">
                <el-upload v-if="canWrite" :auto-upload="false" :show-file-list="false" multiple accept=".pdf,.txt,.md,.docx,.pptx" :on-change="chooseUpload"><el-button type="primary">上传资料</el-button></el-upload>
                <el-button v-if="courseId && canWrite && embedded" @click="emit('generate', selected)">根据资料出题</el-button>
              </div>
              <el-button class="document-refresh" :loading="documentLoading" @click="loadDocuments">刷新</el-button>
            </div>
            <p class="document-upload-hint">支持PDF、DOCX、PPTX、MD和UTF-8文本；单文件20 MB，PDF/PPTX最多200页</p>
            <div v-if="uploadQueue.length" class="upload-queue" aria-live="polite">
              <div v-for="item in uploadQueue" :key="item.key"><span>{{ item.file.name }} · {{ {queued:'等待上传',uploading:'上传中',done:'已上传',failed:'上传失败'}[item.status] }}</span><span v-if="item.error">{{ item.error }}</span><el-button v-if="item.status === 'failed'" size="small" @click="retryUpload(item)">重试</el-button></div>
            </div>
            <el-alert v-if="accessError" :title="accessError" type="error" :closable="false" />
            <ErrorNotice v-if="documentError" class="document-error" title="资料未加载" :message="documentError" retry-label="重新加载" :busy="documentLoading" @retry="loadDocuments" />
            <el-table :data="documents" v-loading="documentLoading" stripe empty-text="暂无资料">
              <el-table-column label="资料" min-width="230"><template #default="{row}"><strong>{{ row.title || row.file_name }}</strong><div class="muted">{{ row.file_name }}</div><div v-if="row.errorMessage || row.error_message" class="error-text">{{ row.errorMessage || row.error_message }}</div><div v-if="row.parse_warning" class="parse-warning">{{ row.parse_warning }}</div></template></el-table-column>
              <el-table-column label="处理状态" min-width="230"><template #default="{row}"><DocumentProcessingStatus :document="row" :job="documentJobs[row.id]" :can-write="canWrite" :busy="!!processingBusy[row.id]" :error="jobErrors[row.id] || ''" @cancel="cancelDocumentProcessing(row)" @retry="retryParsing(row,$event)" @reindex="rebuildIndex(row)" @apply-edits="applyEdits(row)" /></template></el-table-column>
              <el-table-column v-if="courseId" label="学生访问" min-width="190">
                <template #default="{ row }">
                  <el-select
                    v-if="canWrite"
                    :model-value="accessReady ? accessMode(row) : ''"
                    :aria-label="`${row.title || row.file_name}的学生访问权限`"
                    :placeholder="accessLoading ? '读取中' : '未读取'"
                    :disabled="!accessReady || accessSaving"
                    size="small"
                    @change="setDocumentAccess(row, $event)"
                  >
                    <el-option v-for="option in accessOptions" :key="option.value" :label="option.label" :value="option.value" />
                  </el-select>
                  <el-tag v-else-if="!accessReady" type="info">{{ accessLoading ? '读取中' : '未读取' }}</el-tag>
                  <el-tag v-else :type="accessMode(row) === 'teacher' ? 'info' : 'success'">
                    {{ accessMode(row) === 'teacher' ? '仅教师使用' : accessMode(row) === 'download' ? '可问答、查看和下载' : '可问答和查看' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="出处片段" width="100"><template #default="{row}">{{ row.chunk_count ?? '—' }}</template></el-table-column>
              <el-table-column label="操作" min-width="250"><template #default="{row}"><div class="document-actions"><el-button size="small" @click="openPageReview(row)">查看与修改</el-button><el-button size="small" @click="previewDocument(row)">查看出处</el-button><el-button size="small" @click="download(row)">下载</el-button><el-button v-if="canWrite" size="small" type="danger" plain @click="deleteFile(row)">删除</el-button></div></template></el-table-column>
            </el-table>
            <el-pagination v-if="selected.knowledge_count > 100" v-model:current-page="documentPage" :page-size="100" :total="selected.knowledge_count" layout="prev, pager, next" @current-change="loadDocuments" />
          </el-tab-pane>
          <el-tab-pane label="知识问答" name="chat">
            <AssistantConversation :course-id="courseId" :course-title="selected?.name" />
          </el-tab-pane>
          <el-tab-pane label="检索与验证" name="search">
            <div class="search-toolbar"><el-input v-model="searchQuery" placeholder="输入关键词或问题，查看实际命中的原文" @keydown.enter="runSearch" /><el-button v-if="searching" @click="cancelSearch">停止检索</el-button><el-button type="primary" :loading="searching" :disabled="!searchQuery.trim() || searching" @click="runSearch">检索</el-button></div>
            <p v-if="searchModeLabel" class="muted" data-testid="retrieval-mode">{{ searchModeLabel }}<span v-if="searchEffectiveMode !== 'NONE'"> · 分数用于本次结果排序，不能作为事实可信度。</span></p>
            <el-alert v-if="searchWarningText" :title="searchWarningText" type="warning" :closable="false" show-icon />
            <GraphEvidencePaths :course-id="courseId" :paths="searchGraphPaths" />
            <details v-if="searchIndexVersions.length" class="retrieval-snapshots"><summary>本次检索来源版本</summary><ul><li v-for="snapshot in searchIndexVersions" :key="`${snapshot.documentId}:${snapshot.indexVersion}`">{{ snapshotTitle(snapshot) }} · {{ sourceVersionLabel(snapshot) || '来源版本未提供' }}<code v-if="snapshot.indexVersion">{{ snapshot.indexVersion }}</code></li></ul></details>
            <el-alert v-if="searchError" :title="searchError" type="error" :closable="false" show-icon />
            <p v-if="searchCancelled" class="muted">已停止检索，本次未完成。</p>
            <el-empty v-if="searched && !hits.length && !searching && !searchError" description="未检索到相关资料，请调整关键词或上传资料" />
            <div v-for="(hit,index) in hits" :key="hit.chunkId" class="search-hit"><div class="hit-title"><strong>{{ hit.title }}</strong><el-tag v-if="hit.visuals?.length" size="small" type="info">含图 {{ hit.visuals.length }}</el-tag><el-tag size="small">排序分数 {{ Number(hit.score || 0).toFixed(3) }}</el-tag></div><p class="source-position">{{ hit.page ? `第 ${hit.page} 页 · ` : '' }}片段 {{ hit.ordinal ?? index + 1 }}<span v-if="sourceVersionLabel(hit)"> · {{ sourceVersionLabel(hit) }}</span></p><RagCitations :course-id="courseId" :citations="[hit]" inline /></div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </section>

    <el-dialog v-model="editVisible" :title="editing ? '编辑资料库' : '新建知识库'" width="min(520px, 95vw)">
      <el-form label-position="top"><el-form-item label="名称" required><el-input v-model="form.name" maxlength="80" /></el-form-item><el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" /></el-form-item></el-form>
      <el-alert title="资料库保持私有，课程学习资料需由所有者另行授权。" type="info" :closable="false" show-icon />
      <template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!form.name.trim()" @click="saveLibrary">保存</el-button></template>
    </el-dialog>
    <el-drawer v-model="pageReviewVisible" :title="reviewFile?.title || reviewFile?.file_name || '资料内容'" size="min(1080px, 96vw)" destroy-on-close>
      <DocumentPageReview v-if="pageReviewVisible && reviewFile" :document="reviewFile" :job="documentJobs[reviewFile.id]" :can-retry="canWrite" :busy="!!processingBusy[reviewFile.id] || isProcessingActive(reviewFile, documentJobs[reviewFile.id])" @retry="retryParsing(reviewFile,$event)" @apply="applyEdits(reviewFile)" @saved="loadDocuments" />
    </el-drawer>
    <el-drawer v-model="previewVisible" :title="previewFile?.title || previewFile?.file_name || '原文出处'" size="min(720px, 96vw)">
      <div v-loading="previewLoading">
        <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" />
        <template v-else-if="!previewLoading">
          <div v-if="sourcePages.length" class="source-page-navigation">
            <el-button :disabled="sourcePageIndex === 0" @click="sourcePageIndex--">上一页</el-button>
            <span>第 {{ sourcePages[sourcePageIndex]?.pageNumber }} 页 / 共 {{ sourcePages.length }} 页</span>
            <el-button :disabled="sourcePageIndex >= sourcePages.length - 1" @click="sourcePageIndex++">下一页</el-button>
          </div>
          <OriginalPagePreview v-if="previewVisible && previewFile" :document-id="previewFile.id" :page="sourcePages[sourcePageIndex]?.pageNo" :version="Number(previewFile.document_version || previewFile.documentVersion || 1)" />
        </template>
        <el-button v-if="previewFile" @click="download(previewFile)">下载原始文件</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import * as rag from '@/api/rag'
import AssistantConversation from '@/components/assistant/AssistantConversation.vue'
import { renderMarkdown } from '@/utils/markdown'
import { actualRetrievalMode, retrievalModeLabel, retrievalWarning, sourceVersionLabel } from '@/utils/retrievalStatus'
import RagCitations from './RagCitations.vue'
import GraphEvidencePaths from './GraphEvidencePaths.vue'
import RagAnswerStatus from './RagAnswerStatus.vue'
import DocumentProcessingStatus from './DocumentProcessingStatus.vue'
import DocumentPageReview from './DocumentPageReview.vue'
import OriginalPagePreview from './OriginalPagePreview.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import { documentIdOf, jobIdOf, isProcessingActive } from '@/utils/documentProcessing'
const props = defineProps({ manage: { type: Boolean, default: false }, courseId: Number, workspaceTab: String, embedded: Boolean })
const emit = defineEmits(['select', 'generate'])
const route = useRoute()
const user = useUserStore()
const libraries = ref([]), selected = ref(null), scope = ref('all'), keyword = ref('')
const kbLoading = ref(false), kbError = ref(''), documents = ref([]), documentLoading = ref(false), documentError = ref(''), uploading = ref(false)
const documentPage = ref(1)
const documentAccess = ref([]), accessError = ref(''), accessReady = ref(false)
const accessLoading = ref(false), accessSaving = ref(false)
const accessOptions = [
  { value: 'teacher', label: '仅教师使用', readExcerpt: false, downloadAllowed: false },
  { value: 'view', label: '开放给学生', readExcerpt: true, downloadAllowed: false },
  { value: 'download', label: '开放并允许下载', readExcerpt: true, downloadAllowed: true },
]
const uploadQueue = ref([])
let uploadSequence = 0
const activeTab = ref(props.workspaceTab || 'documents'), editVisible = ref(false), editing = ref(false), saving = ref(false)
const form = reactive({ name: '', description: '' })
const documentJobs = ref({}), jobErrors = ref({}), processingBusy = ref({})
const pageReviewVisible = ref(false), reviewFile = ref(null)
const previewVisible = ref(false), previewLoading = ref(false), previewError = ref(''), previewFile = ref(null), sourcePages = ref([]), sourcePageIndex = ref(0)
const searchQuery = ref(''), searchMode = ref(''), searching = ref(false), searched = ref(false), searchWarning = ref(''), searchError = ref(''), hits = ref([])
const searchCancelled = ref(false)
const searchActualMode = ref(''), searchDegradedReason = ref(null), searchIndexVersions = ref([])
const searchGraphPaths = ref([])
const searchEffectiveMode = computed(() => actualRetrievalMode(searchActualMode.value, searchMode.value))
const searchModeLabel = computed(() => retrievalModeLabel(searchActualMode.value, searchMode.value))
const searchWarningText = computed(() => retrievalWarning(searchDegradedReason.value, searchWarning.value))
const snapshotTitle = snapshot => hits.value.find(hit => hit.documentId === snapshot.documentId)?.title || '资料'
const canWrite = computed(() => props.manage && selected.value && !selected.value.read_only && (String(selected.value.owner_id) === String(user.userId) || user.roleCode === 'admin'))
const filteredLibraries = computed(() => libraries.value.filter(kb => `${kb.name} ${kb.description || ''}`.toLowerCase().includes(keyword.value.trim().toLowerCase())))
let documentVersion = 0, libraryVersion = 0, previewVersion = 0, accessVersion = 0, pollTimer = null, searchController = null
const failure = (e, fallback) => e?.response?.data?.msg || e?.message || fallback
function cancelSearch() {
  if (!searchController) return
  searchController.abort()
  searched.value = false; searchCancelled.value = true; searchGraphPaths.value = []
}
onBeforeUnmount(() => { clearTimeout(pollTimer); cancelSearch(); documentVersion++; libraryVersion++; previewVersion++; accessVersion++ })
onMounted(loadLibraries)
watch(() => props.courseId, () => {
  clearTimeout(pollTimer)
  documentVersion++
  resetDocumentAccess()
  loadLibraries()
})
watch(() => props.workspaceTab, value => { if (value) activeTab.value = value })
async function loadLibraries(preferredId) {
  if (props.workspaceTab === 'chat') return
  const version = ++libraryVersion
  kbLoading.value = true; kbError.value = ''
  try {
    const list = props.courseId ? [rag.unwrapRag(await rag.getCourseKnowledgeBase(props.courseId))] : rag.ragList(await rag.listKnowledgeBases({ scope:scope.value }))
    if (version !== libraryVersion) return
    libraries.value = list
    const desired = typeof preferredId === 'string' ? preferredId : selected.value?.id || route.query.kbId
    const next = list.find(kb => kb.id === desired) || list[0] || null
    if (next?.id !== selected.value?.id) await chooseLibrary(next)
    else selected.value = next
  } catch (e) { if (version === libraryVersion) kbError.value = failure(e, '加载知识库失败') }
  finally { if (version === libraryVersion) kbLoading.value = false }
}
async function chooseLibrary(kb) {
  clearTimeout(pollTimer); cancelSearch(); documentVersion++; previewVersion++; previewVisible.value = false; pageReviewVisible.value = false; reviewFile.value = null; documentJobs.value = {}; jobErrors.value = {}; processingBusy.value = {}
  resetDocumentAccess()
  selected.value = kb; documents.value = []; hits.value = []; searchGraphPaths.value = []; searched.value = false; searchCancelled.value = false; searchMode.value = ''; searchError.value = ''; searchWarning.value = ''; searchActualMode.value = ''; searchDegradedReason.value = null; searchIndexVersions.value = []
  documentPage.value = 1
  emit('select', kb)
  if (kb) await loadDocuments()
}
async function loadDocuments() {
  if (!selected.value) return
  clearTimeout(pollTimer)
  const kbId = selected.value.id, version = ++documentVersion
  documentLoading.value = true; documentError.value = ''
  try {
    const list = rag.ragList(await rag.listDocuments(kbId,100,(documentPage.value-1)*100))
    if (version !== documentVersion || selected.value?.id !== kbId) return
    const previous = documentJobs.value
    const states = await Promise.all(list.map(async doc => {
      const id = documentIdOf(doc), jobId = jobIdOf(doc), cached = previous[id]
      if (!jobId) return [id,null,'']
      if (cached && (cached.jobId || cached.id) === jobId && !isProcessingActive(doc,cached)) return [id,cached,'']
      try { return [id,rag.unwrapRag(await rag.getProcessingJob(jobId)),''] }
      catch (problem) { return [id,null,failure(problem,'无法获取处理进度，请刷新重试')] }
    }))
    if (version !== documentVersion || selected.value?.id !== kbId) return
    documentJobs.value = Object.fromEntries(states.map(([id,job])=>[id,job]))
    jobErrors.value = Object.fromEntries(states.map(([id,,problem])=>[id,problem]))
    documents.value = list
    if (props.courseId) await loadDocumentAccess()
    if (version !== documentVersion) return
    if (reviewFile.value) reviewFile.value = list.find(doc=>documentIdOf(doc)===documentIdOf(reviewFile.value)) || reviewFile.value
    if (list.some(doc => isProcessingActive(doc,documentJobs.value[documentIdOf(doc)]))) pollTimer = setTimeout(loadDocuments,3000)
  } catch (problem) {
    if (version === documentVersion) {
      documents.value = []; documentJobs.value = {}; jobErrors.value = {}; pageReviewVisible.value = false; previewVisible.value = false
      documentError.value = failure(problem,'加载资料失败')
    }
  } finally { if (version === documentVersion) documentLoading.value = false }
}
function openCreate() { editing.value = false; Object.assign(form,{name:'',description:''}); editVisible.value = true }
function openEdit() { editing.value = true; Object.assign(form,{name:selected.value.name,description:selected.value.description || ''}); editVisible.value = true }
async function saveLibrary() {
  saving.value = true
  try {
    const result = editing.value ? await rag.updateKnowledgeBase(selected.value.id,{name:form.name.trim(),description:form.description}) : await rag.createKnowledgeBase({name:form.name.trim(),description:form.description})
    editVisible.value = false
    if (!editing.value) scope.value = 'mine'
    await loadLibraries(rag.unwrapRag(result)?.id || selected.value?.id)
    ElMessage.success('知识库已保存')
  } catch (e) { ElMessage.error(failure(e,'保存失败')) }
  finally { saving.value = false }
}
async function deleteLibrary() {
  try { await ElMessageBox.confirm(`删除“${selected.value.name}”及其中所有资料？`, '删除知识库', {type:'warning'}) } catch { return }
  try { await rag.deleteKnowledgeBase(selected.value.id); await loadLibraries(); ElMessage.success('知识库已删除') }
  catch (e) { ElMessage.error(failure(e,'删除失败')) }
}
function chooseUpload(file) {
  if (!file.raw || !canWrite.value) return
  if (file.raw.size > 20 * 1024 * 1024) { ElMessage.error('单文件不能超过20 MB'); return }
  if (!/\.(pdf|txt|md|docx|pptx)$/i.test(file.raw.name)) { ElMessage.error('请选择PDF、DOCX、PPTX、MD或UTF-8文本'); return }
  uploadQueue.value.push({ key: ++uploadSequence, file: file.raw, kbId: selected.value.id, status: 'queued', error: '' })
  processUploads()
}
function retryUpload(item) { item.status = 'queued'; item.error = ''; processUploads() }
async function processUploads() {
  if (uploading.value) return
  uploading.value = true
  try {
    for (const item of uploadQueue.value.filter(item => item.status === 'queued')) {
      item.status = 'uploading'
      try {
        await rag.uploadDocument(item.kbId, item.file)
        item.status = 'done'
        if (selected.value?.id === item.kbId) { await loadDocuments(); await loadLibraries(item.kbId) }
      } catch (problem) { item.status = 'failed'; item.error = failure(problem, '上传失败') }
    }
  } finally {
    uploading.value = false
    if (uploadQueue.value.some(item => item.status === 'queued')) processUploads()
  }
}
function resetDocumentAccess() {
  accessVersion++
  documentAccess.value = []
  accessReady.value = false
  accessLoading.value = false
  accessSaving.value = false
  accessError.value = ''
}

async function loadDocumentAccess() {
  if (accessSaving.value) return
  const version = ++accessVersion
  accessLoading.value = true
  accessError.value = ''
  try {
    const access = rag.ragList(await rag.getCourseDocumentAccess(props.courseId))
    if (version !== accessVersion) return
    documentAccess.value = access
    accessReady.value = true
  } catch (problem) {
    if (version !== accessVersion) return
    accessReady.value = false
    accessError.value = failure(problem, '学生访问设置未读取，请刷新重试')
  } finally {
    if (version === accessVersion) accessLoading.value = false
  }
}

function accessMode(doc) {
  const version = Number(doc.document_version || doc.documentVersion || 1)
  const access = documentAccess.value.find(entry =>
    (entry.document_id || entry.documentId) === doc.id &&
    Number(entry.document_version ?? entry.version ?? entry.documentVersion) === version
  )
  if (!access?.read_excerpt) return 'teacher'
  return access.download_allowed ? 'download' : 'view'
}

async function setDocumentAccess(doc, value) {
  if (!props.courseId || !canWrite.value || !accessReady.value || accessSaving.value) return
  const option = accessOptions.find(item => item.value === value)
  if (!doc.id || !option || value === accessMode(doc)) return
  const version = ++accessVersion
  accessLoading.value = false
  accessSaving.value = true
  accessError.value = ''
  try {
    const access = rag.ragList(await rag.updateCourseDocumentAccess(props.courseId, {
      documentIds: [doc.id], readExcerpt: option.readExcerpt, downloadAllowed: option.downloadAllowed,
    }))
    if (version !== accessVersion) return
    documentAccess.value = access
    ElMessage.success('学生访问范围已更新')
  } catch (problem) {
    if (version === accessVersion) accessError.value = failure(problem, '学生访问范围保存失败')
  } finally {
    if (version === accessVersion) accessSaving.value = false
  }
}

function openPageReview(doc) { reviewFile.value = doc; pageReviewVisible.value = true }
async function processingAction(doc,action,message) {
  const id = documentIdOf(doc), kbId = selected.value?.id
  if (!canWrite.value || !id || processingBusy.value[id]) return
  processingBusy.value = {...processingBusy.value,[id]:true}
  try {
    const result = rag.unwrapRag(await action())
    if (selected.value?.id !== kbId) return
    if (result?.jobId || result?.id && result?.stage) documentJobs.value = {...documentJobs.value,[id]:result}
    await loadDocuments(); ElMessage.success(typeof message==='function' ? message(result) : message)
  } catch (problem) { if (selected.value?.id===kbId) ElMessage.error(failure(problem,'处理请求失败，请重试')) }
  finally { if (selected.value?.id===kbId) processingBusy.value = {...processingBusy.value,[id]:false} }
}
async function cancelDocumentProcessing(doc) {
  const jobId = documentJobs.value[documentIdOf(doc)]?.jobId || documentJobs.value[documentIdOf(doc)]?.id || jobIdOf(doc)
  if (jobId) await processingAction(doc,()=>rag.cancelProcessingJob(jobId),'已请求取消处理')
}
async function retryParsing(doc,options) {
  await processingAction(doc,()=>rag.retryDocumentParsing(documentIdOf(doc),options),'已提交重新解析任务')
}
async function applyEdits(doc) {
  await processingAction(doc,()=>rag.applyPageEdits(documentIdOf(doc)),'已开始更新检索内容，完成前检索仍使用原内容')
}
async function rebuildIndex(doc) {
  await processingAction(doc,()=>rag.reindexDocument(documentIdOf(doc)),result=>result?.jobId ? '已提交向量重建任务' : result?.embedding_status==='indexed' ? '向量索引已重建' : '请核对当前向量索引状态')
}
async function deleteFile(doc) {
  try { await ElMessageBox.confirm(`删除“${doc.title || doc.file_name}”及其检索片段？`, '删除资料', {type:'warning'}) } catch { return }
  try { await rag.deleteDocument(doc.id); await loadDocuments(); await loadLibraries(selected.value.id); ElMessage.success('资料已删除') }
  catch (e) { ElMessage.error(failure(e,'删除失败')) }
}
async function previewDocument(doc) {
  const version = ++previewVersion
  previewFile.value = doc; previewVisible.value = true; previewLoading.value = true; previewError.value = ''; sourcePages.value = []; sourcePageIndex.value = 0
  try { const list = rag.ragList(await rag.getDocumentPages(doc.id, doc.document_version || doc.documentVersion || 1)); if (version === previewVersion) sourcePages.value = list.filter(page => (page.pageNumber ?? page.page_number) != null).map(page => ({ pageNo: Number(page.pageNo ?? page.page_no), pageNumber: Number(page.pageNumber ?? page.page_number) })) }
  catch (e) { if (version === previewVersion) previewError.value = failure(e,'无法读取原页列表') }
  finally { if (version === previewVersion) previewLoading.value = false }
}
async function download(doc) {
  try {
    const blob = await rag.downloadDocument(doc.id)
    if (!(blob instanceof Blob)) throw new Error('未能读取原始文件')
    if (blob.type.includes('application/json')) { const data = JSON.parse(await blob.text()); if (data.code && String(data.code) !== '200') throw new Error(data.msg || '文件不可访问') }
    const url = URL.createObjectURL(blob), link = document.createElement('a')
    link.href = url; link.download = doc.file_name || doc.title || '资料'; link.click()
    setTimeout(() => URL.revokeObjectURL(url),1000)
  } catch (e) { ElMessage.error(failure(e,'下载失败')) }
}
async function runSearch() {
  if (!selected.value || !searchQuery.value.trim() || searching.value) return
  const kbId = selected.value.id, controller = new AbortController(); searchController = controller
  searching.value = true; searched.value = false; searchCancelled.value = false; searchError.value = ''; searchWarning.value = ''; hits.value = []; searchGraphPaths.value = []; searchMode.value = ''; searchActualMode.value = ''; searchDegradedReason.value = null; searchIndexVersions.value = []
  try {
    const data = rag.unwrapRag(await rag.search({courseId:props.courseId,knowledgeBaseIds:props.courseId ? [] : [kbId],query:searchQuery.value.trim(),topK:8},{signal:controller.signal}))
    if (selected.value?.id === kbId && !controller.signal.aborted) {
      searched.value = true
      hits.value = Array.isArray(data.hits) ? data.hits : []; searchMode.value = data.mode; searchWarning.value = data.warning || ''
      searchActualMode.value = data.actualMode || ''; searchDegradedReason.value = data.degradedReason || null
      searchIndexVersions.value = Array.isArray(data.indexVersions) ? data.indexVersions : []
      searchGraphPaths.value = Array.isArray(data.graphPaths) ? data.graphPaths : []
    }
  } catch (e) { if (!controller.signal.aborted) searchError.value = failure(e,'检索失败，请重试') }
  finally { if (searchController === controller) { searching.value = false; searchController = null } }
}
defineExpose({openCreate,loadLibraries})
</script>

<style scoped>
.course-workspace { display: block !important; }.embedded .workspace-main { padding: 0; border: 0; box-shadow: none; }.fixed-tab :deep(.el-tabs__header) { display: none; }.upload-queue { margin: 12px 0; }.upload-queue>div { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 6px 0; }

.source-page-navigation { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.native-kb-workspace { display:grid; grid-template-columns:272px minmax(0,1fr); gap:var(--space-5); align-items:start; }
.library-panel,.workspace-main { background:var(--surface-base); border:1px solid var(--line-subtle); border-radius:var(--radius-lg); padding:var(--space-5); min-width:0; box-shadow:var(--shadow-1); }
.panel-heading,.workspace-heading,.heading-actions,.document-toolbar,.search-toolbar,.chat-toolbar,.input-footer,.hit-title { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
.panel-heading,.workspace-heading,.hit-title { justify-content:space-between; }
.panel-heading { margin-bottom:var(--space-3); color:var(--text-strong); }.scope-tabs { display:flex; margin:var(--space-3) 0 var(--space-2); }
.library-item { display:flex; flex-direction:column; gap:6px; width:100%; text-align:left; padding:var(--space-3); border:1px solid var(--line-subtle); border-radius:var(--radius-md); background:transparent; cursor:pointer; margin:var(--space-2) 0; color:var(--text-body); transition:background-color .12s var(--ease-standard), border-color .12s var(--ease-standard); }
.library-item:hover { background:var(--surface-hover); }.library-item.active { background:var(--surface-accent); border-color:var(--line-accent); }
.library-title { display:flex; align-items:center; justify-content:space-between; gap:8px; color:var(--text-strong); font-weight:600; overflow-wrap:anywhere; }
.library-description,.library-count,.muted,.source-position,.document-toolbar>span { font-size:var(--text-xs); color:var(--text-subtle); line-height:1.6; }.library-description { overflow-wrap:anywhere; }
.workspace-heading { margin-bottom:var(--space-4); }.workspace-heading h3 { margin:0 0 6px; font-size:var(--text-xl); overflow-wrap:anywhere; }.workspace-heading p { margin:0; font-size:var(--text-sm); color:var(--text-subtle); }
.heading-actions .el-button + .el-button { margin-left:0; }
.workspace-tabs { margin-top:var(--space-4); }.document-toolbar,.chat-toolbar,.search-toolbar { margin-bottom:var(--space-4); }.document-toolbar>span { flex:1 1 220px; }.search-toolbar .el-input { flex:1; min-width:180px; }.chat-toolbar .el-select { flex:1 1 200px; max-width:320px; }
.document-error { margin-bottom:var(--space-3); }
.conversation { min-height:220px; max-height:520px; overflow:auto; padding:var(--space-2) 0 var(--space-5); }.chat-message { padding:var(--space-4); margin:var(--space-3) 0; color:var(--text-body); background:var(--surface-canvas); border:1px solid var(--line-subtle); border-radius:var(--radius-lg); line-height:1.75; overflow-wrap:anywhere; }.chat-message.user { background:var(--surface-accent); border-color:var(--line-accent); margin-left:var(--space-8); }.message-role { display:block; font-size:var(--text-xs); color:var(--text-subtle); margin-bottom:6px; }
.input-footer { margin:var(--space-3) 0; }.input-footer .muted { margin-right:auto; }.search-hit,.document-chunk { padding:var(--space-4); margin:var(--space-4) 0; color:var(--text-body); border:1px solid var(--line-subtle); border-radius:var(--radius-lg); }
.retrieval-snapshots { color:var(--text-subtle); font-size:var(--text-xs); line-height:1.7; margin:var(--space-3) 0; }.retrieval-snapshots summary { cursor:pointer; }.retrieval-snapshots code { display:block; overflow-wrap:anywhere; }
pre { white-space:pre-wrap; overflow-wrap:anywhere; font-family:inherit; line-height:1.8; font-size:var(--text-md); }.error-text { color:var(--critical); font-size:var(--text-xs); }
.document-actions { display:grid; grid-template-columns:repeat(2, max-content); gap:8px; align-items:center; }
.course-workspace.embedded .workspace-main {
  padding:20px;
  border:1px solid var(--line-subtle);
  border-radius:var(--radius-lg);
  background:var(--surface-base);
}
.course-workspace.embedded .workspace-tabs.fixed-tab { margin-top:0; }
.document-toolbar { justify-content:space-between; margin-bottom:8px; }
.document-primary-actions { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
.document-primary-actions .el-button + .el-button { margin-left:0; }
.document-refresh { margin-left:auto; }
.document-upload-hint { color:var(--text-subtle); font-size:12px; line-height:1.8; margin:0 0 20px; }
.document-actions .el-button { width:100%; margin:0; }
.document-processing-status :deep(.processing-actions) { gap:8px; }
@media (max-width:600px) { .document-primary-actions { gap:8px; } .document-toolbar { gap:12px; } }
.document-actions .el-button + .el-button { margin-left:0; }
.parse-warning { color:var(--caution); font-size:var(--text-xs); line-height:1.6; margin-top:4px; }.el-pagination { margin-top:var(--space-4); }
:deep(.chat-message pre) { overflow:auto; padding:var(--space-3); background:var(--surface-sunken); border-radius:var(--radius-md); }:deep(.chat-message table) { border-collapse:collapse; max-width:100%; display:block; overflow:auto; }:deep(.chat-message td),:deep(.chat-message th) { border:1px solid var(--line-default); padding:var(--space-2); }:deep(.chat-message img) { max-width:100%; }
@media(max-width:1000px) { .native-kb-workspace { grid-template-columns:220px minmax(0,1fr); gap:var(--space-3); }.library-panel,.workspace-main { padding:var(--space-4); } }
@media(max-width:760px) { .native-kb-workspace { grid-template-columns:minmax(0,1fr); }.library-panel { max-height:340px; overflow:auto; }.heading-actions { width:100%; }.chat-message.user { margin-left:var(--space-3); } }
</style>
