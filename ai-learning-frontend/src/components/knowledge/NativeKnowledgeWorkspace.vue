<template>
  <div class="native-kb-workspace">
    <aside class="library-panel" v-loading="kbLoading">
      <div class="panel-heading"><strong>课程知识库</strong><el-button v-if="manage" size="small" type="primary" @click="openCreate">新建</el-button></div>
      <el-input v-model="keyword" clearable placeholder="搜索名称或描述" aria-label="搜索知识库" />
      <el-radio-group v-model="scope" size="small" class="scope-tabs" @change="loadLibraries">
        <el-radio-button value="all" label="all">全部可用</el-radio-button>
        <el-radio-button value="mine" label="mine">我的</el-radio-button>
        
      </el-radio-group>
      <el-alert v-if="kbError" :title="kbError" type="error" :closable="false" show-icon />
      <el-empty v-else-if="!filteredLibraries.length && !kbLoading" description="暂无可用知识库" />
      <button v-for="kb in filteredLibraries" :key="kb.id" class="library-item" :class="{ active:selected?.id === kb.id }" @click="chooseLibrary(kb)">
        <span class="library-title">{{ kb.name }} <el-tag size="small" type="info">仅所有者管理</el-tag></span>
        <span class="library-description">{{ kb.description || '暂无描述' }}</span>
        <span class="library-count">{{ kb.knowledge_count || 0 }} 份资料{{ kb.read_only ? ' · 只读' : '' }}</span>
      </button>
    </aside>

    <main class="workspace-main">
      <el-empty v-if="!selected" description="选择知识库，查看资料、检索来源或提问" />
      <template v-else>
        <div class="workspace-heading">
          <div><h3>{{ selected.name }}</h3><p>{{ selected.description || '基于课程资料学习，让每个回答有据可查。' }}</p></div>
          <div class="heading-actions" v-if="manage">
            <el-button v-if="canWrite" @click="openEdit">编辑资料库</el-button>
            <el-button v-if="manage" type="success" @click="$emit('generate', selected)">根据资料出题</el-button>
            <el-button v-if="canWrite" type="danger" plain @click="deleteLibrary">删除</el-button>
          </div>
        </div>
        <el-alert title="只有资料所有者可以管理。学生学习资料需另行授权给课程。" type="info" :closable="false" show-icon />
        <el-tabs v-model="activeTab" class="workspace-tabs">
          <el-tab-pane label="资料与出处" name="documents">
            <div class="document-toolbar">
              <el-upload v-if="canWrite" :auto-upload="false" :show-file-list="false" accept=".pdf,.txt,.md,.docx,.pptx" :disabled="uploading" :on-change="chooseUpload">
                <el-button type="primary" :loading="uploading">{{ uploading ? '正在上传' : '上传资料' }}</el-button>
              </el-upload>
              <span>支持PDF、DOCX、PPTX、MD和UTF-8文本；单文件20 MB，PDF/PPTX最多200页</span><el-button :loading="documentLoading" @click="loadDocuments">刷新</el-button>
            </div>
            <el-alert v-if="documentError" :title="documentError" type="error" :closable="false" show-icon />
            <el-table :data="documents" v-loading="documentLoading" stripe empty-text="暂无资料">
              <el-table-column label="资料" min-width="180"><template #default="{row}"><strong>{{ row.title || row.file_name }}</strong><div class="muted">{{ row.file_name }}</div><div v-if="row.errorMessage || row.error_message" class="error-text">{{ row.errorMessage || row.error_message }}</div><div v-if="row.parse_warning" class="parse-warning">{{ row.parse_warning }}</div></template></el-table-column>
              <el-table-column label="处理状态" min-width="250"><template #default="{row}"><DocumentProcessingStatus :document="row" :job="documentJobs[row.id]" :can-write="canWrite" :busy="!!processingBusy[row.id]" :error="jobErrors[row.id] || ''" @cancel="cancelDocumentProcessing(row)" @retry="retryParsing(row,$event)" @reindex="rebuildIndex(row)" /></template></el-table-column>
              <el-table-column label="出处片段" width="100"><template #default="{row}">{{ row.chunk_count ?? '—' }}</template></el-table-column>
              <el-table-column label="操作" min-width="240"><template #default="{row}"><div class="document-actions"><el-button size="small" @click="openPageReview(row)">逐页核验</el-button><el-button size="small" @click="previewDocument(row)">查看出处</el-button><el-button size="small" @click="download(row)">下载</el-button><el-button v-if="canWrite" size="small" @click="openGrant(row)">课程授权</el-button><el-button v-if="canWrite" size="small" type="danger" plain @click="deleteFile(row)">删除</el-button></div></template></el-table-column>
            </el-table>
            <el-pagination v-if="selected.knowledge_count > 100" v-model:current-page="documentPage" :page-size="100" :total="selected.knowledge_count" layout="prev, pager, next" @current-change="loadDocuments" />
          </el-tab-pane>
          <el-tab-pane label="知识问答" name="chat">
            <div class="chat-toolbar"><el-select v-model="historyId" clearable placeholder="历史对话" @change="switchHistory"><el-option v-for="session in currentSessions" :key="session.id" :label="session.title || '学习对话'" :value="session.id" /></el-select><el-button :disabled="generating" @click="newConversation">新对话</el-button><el-button v-if="historyId" :disabled="generating" @click="deleteConversation">删除对话</el-button></div>
            <RagConversation :messages="messages" :progress="progress" />
            <el-input v-model="question" type="textarea" :rows="3" placeholder="例如：这节课的核心概念是什么？" :disabled="generating" @keydown.ctrl.enter="ask" />
            <div class="input-footer"><span class="muted">Ctrl + Enter 发送 · 以资料来源核实回答</span><el-button v-if="generating" @click="cancel">停止生成</el-button><el-button type="primary" :loading="generating" :disabled="!question.trim() || generating" @click="ask">发送</el-button></div>
            <el-alert v-if="error && !generating" :title="String(error)" type="error" :closable="false" show-icon />
          </el-tab-pane>
          <el-tab-pane label="检索与验证" name="search">
            <div class="search-toolbar"><el-input v-model="searchQuery" placeholder="输入关键词或问题，查看实际命中的原文" @keydown.enter="runSearch" /><el-button v-if="searching" @click="cancelSearch">停止检索</el-button><el-button type="primary" :loading="searching" :disabled="!searchQuery.trim() || searching" @click="runSearch">检索</el-button></div>
            <p v-if="searchModeLabel" class="muted" data-testid="retrieval-mode">{{ searchModeLabel }}<span v-if="searchEffectiveMode !== 'NONE'"> · 分数用于本次结果排序，不能作为事实可信度。</span></p>
            <el-alert v-if="searchWarningText" :title="searchWarningText" type="warning" :closable="false" show-icon />
            <details v-if="searchIndexVersions.length" class="retrieval-snapshots"><summary>本次检索来源版本</summary><ul><li v-for="snapshot in searchIndexVersions" :key="`${snapshot.documentId}:${snapshot.indexVersion}`">{{ snapshotTitle(snapshot) }} · {{ sourceVersionLabel(snapshot) || '来源版本未提供' }}<code v-if="snapshot.indexVersion">{{ snapshot.indexVersion }}</code></li></ul></details>
            <el-alert v-if="searchError" :title="searchError" type="error" :closable="false" show-icon />
            <p v-if="searchCancelled" class="muted">已停止检索，本次未完成。</p>
            <el-empty v-if="searched && !hits.length && !searching && !searchError" description="未检索到相关资料，请调整关键词或上传资料" />
            <div v-for="(hit,index) in hits" :key="hit.chunkId" class="search-hit"><div class="hit-title"><strong>{{ hit.title }}</strong><el-tag v-if="hit.visuals?.length" size="small" type="info">含图 {{ hit.visuals.length }}</el-tag><el-tag size="small">排序分数 {{ Number(hit.score || 0).toFixed(3) }}</el-tag></div><p class="source-position">{{ hit.page ? `第 ${hit.page} 页 · ` : '' }}片段 {{ hit.ordinal ?? index + 1 }}<span v-if="sourceVersionLabel(hit)"> · {{ sourceVersionLabel(hit) }}</span></p><pre>{{ hit.content }}</pre><RagCitations :citations="[hit]" /></div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </main>

    <el-dialog v-model="grantVisible" title="课程资料授权" width="min(520px, 95vw)">
      <p>{{ grantFile?.title || grantFile?.file_name }}</p>
      <el-select v-model="grantCourseId" placeholder="选择课程" aria-label="选择课程" :disabled="grantSaving" :loading="grantLoading" @change="loadGrantState"><el-option v-for="course in grantCourses" :key="course.id" :label="course.title" :value="Number(course.id)" /></el-select>
      <p class="muted">{{ grantActive ? '当前版本已授权给此课程。' : '授权后，该课程有学习资格的学生可检索这份资料。' }}</p>
      <el-alert v-if="grantError" :title="grantError" type="error" :closable="false" />
      <template #footer><el-button @click="grantVisible=false">关闭</el-button><el-button v-if="grantActive" type="danger" plain :loading="grantSaving" @click="saveGrant(false)">撤销授权</el-button><el-button v-else type="primary" :disabled="!grantCourseId || grantLoading || !!grantError" :loading="grantSaving" @click="saveGrant(true)">授权阅读</el-button></template>
    </el-dialog>
    <el-dialog v-model="editVisible" :title="editing ? '编辑资料库' : '新建知识库'" width="min(520px, 95vw)">
      <el-form label-position="top"><el-form-item label="名称" required><el-input v-model="form.name" maxlength="80" /></el-form-item><el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" /></el-form-item></el-form>
      <el-alert title="资料库保持私有，课程学习资料需由所有者另行授权。" type="info" :closable="false" show-icon />
      <template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!form.name.trim()" @click="saveLibrary">保存</el-button></template>
    </el-dialog>
    <el-drawer v-model="pageReviewVisible" :title="reviewFile?.title || reviewFile?.file_name || '资料核验'" size="min(1080px, 96vw)" destroy-on-close>
      <DocumentPageReview v-if="pageReviewVisible && reviewFile" :document="reviewFile" :job="documentJobs[reviewFile.id]" :can-retry="canWrite" :busy="!!processingBusy[reviewFile.id] || isProcessingActive(reviewFile, documentJobs[reviewFile.id])" @retry="retryParsing(reviewFile,$event)" @saved="reviewSaved" />
    </el-drawer>
    <el-drawer v-model="previewVisible" :title="previewFile?.title || previewFile?.file_name || '原文出处'" size="min(720px, 96vw)">
      <div v-loading="previewLoading"><el-alert v-if="previewError" :title="previewError" type="error" :closable="false" /><el-alert v-if="previewFile?.parse_warning" :title="previewFile.parse_warning" type="warning" :closable="false" /><el-empty v-if="!previewError && !chunks.length && !previewLoading" description="暂无可读片段，请确认解析状态" /><div v-for="chunk in chunks" :key="chunk.id || chunk.chunkId" class="document-chunk"><p class="source-position">{{ chunk.page ? `第 ${chunk.page} 页 · ` : '' }}片段 {{ chunk.ordinal }}</p><pre>{{ chunk.content }}</pre></div><el-button v-if="previewFile" @click="download(previewFile)">下载原始文件</el-button></div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import * as rag from '@/api/rag'
import { useNativeRag } from '@/composables/useNativeRag'
import { getCourses } from '@/utils/course'
import { actualRetrievalMode, retrievalModeLabel, retrievalWarning, sourceVersionLabel } from '@/utils/retrievalStatus'
import RagCitations from './RagCitations.vue'
import RagConversation from './RagConversation.vue'
import DocumentProcessingStatus from './DocumentProcessingStatus.vue'
import DocumentPageReview from './DocumentPageReview.vue'
import { documentIdOf, jobIdOf, isProcessingActive } from '@/utils/documentProcessing'
const props = defineProps({ manage: { type: Boolean, default: false } })
const emit = defineEmits(['select', 'generate'])
const route = useRoute()
const user = useUserStore()
const libraries = ref([]), selected = ref(null), scope = ref('all'), keyword = ref('')
const kbLoading = ref(false), kbError = ref(''), documents = ref([]), documentLoading = ref(false), documentError = ref(''), uploading = ref(false)
const documentPage = ref(1)
const activeTab = ref('documents'), editVisible = ref(false), editing = ref(false), saving = ref(false)
const form = reactive({ name: '', description: '' })
const grantVisible = ref(false), grantFile = ref(null), grantCourseId = ref(null), grantCourses = ref([])
const grantLoading = ref(false), grantSaving = ref(false), grantError = ref(''), grantActive = ref(false)
let grantVersion = 0
async function openGrant(file) {
  grantFile.value = file; grantCourseId.value = null; grantVisible.value = true; grantError.value = ''; grantActive.value = false
  grantLoading.value = true
  try { grantCourses.value = rag.ragList(await getCourses({ pageNum:1, pageSize:100 })) }
  catch (e) { grantError.value = failure(e, '无法读取课程') }
  finally { grantLoading.value = false }
}
async function loadGrantState() {
  const version = ++grantVersion
  grantLoading.value = true; grantError.value = ''; grantActive.value = false
  try {
    const list = rag.ragList(await rag.getCourseDocuments(grantCourseId.value))
    if (version !== grantVersion) return
    const documentVersion = Number(grantFile.value.document_version || grantFile.value.documentVersion || 1)
    grantActive.value = list.some(doc => (doc.documentId || doc.document_id || doc.id) === grantFile.value.id && Number(doc.documentVersion || doc.document_version) === documentVersion)
  } catch (e) { if (version === grantVersion) grantError.value = failure(e, '无法读取授权状态') }
  finally { if (version === grantVersion) grantLoading.value = false }
}
async function saveGrant(enabled) {
  grantSaving.value = true; grantError.value = ''
  try {
    const version = Number(grantFile.value.document_version || grantFile.value.documentVersion || 1)
    if (enabled) await rag.grantCourseDocument(grantFile.value.id, { courseId:grantCourseId.value, documentVersion:version, readExcerpt:true, downloadAllowed:false })
    else await rag.revokeCourseDocument(grantFile.value.id, grantCourseId.value, version)
    ElMessage.success(enabled ? '课程资料已授权' : '课程授权已撤销')
    await loadGrantState()
  } catch (e) { grantError.value = failure(e, '授权操作失败') }
  finally { grantSaving.value = false }
}
const documentJobs = ref({}), jobErrors = ref({}), processingBusy = ref({})
const pageReviewVisible = ref(false), reviewFile = ref(null)
const previewVisible = ref(false), previewLoading = ref(false), previewError = ref(''), previewFile = ref(null), chunks = ref([])
const searchQuery = ref(''), searchMode = ref(''), searching = ref(false), searched = ref(false), searchWarning = ref(''), searchError = ref(''), hits = ref([])
const searchCancelled = ref(false)
const searchActualMode = ref(''), searchDegradedReason = ref(null), searchIndexVersions = ref([])
const searchEffectiveMode = computed(() => actualRetrievalMode(searchActualMode.value, searchMode.value))
const searchModeLabel = computed(() => retrievalModeLabel(searchActualMode.value, searchMode.value))
const searchWarningText = computed(() => retrievalWarning(searchDegradedReason.value, searchWarning.value))
const snapshotTitle = snapshot => hits.value.find(hit => hit.documentId === snapshot.documentId)?.title || '资料'
const question = ref(''), historyId = ref('')
const { messages, sessions, generating, currentSessionId, progress, error, cancel, resetKnowledgeContext, createNewSession, loadSessions, restoreSession, removeSession, sendMessage } = useNativeRag()
const canWrite = computed(() => props.manage && selected.value && !selected.value.read_only && String(selected.value.owner_id) === String(user.userId))
const filteredLibraries = computed(() => libraries.value.filter(kb => `${kb.name} ${kb.description || ''}`.toLowerCase().includes(keyword.value.trim().toLowerCase())))
const currentSessions = computed(() => sessions.value.filter(session => (session.knowledgeBaseId || session.kb_id) === selected.value?.id))
let documentVersion = 0, libraryVersion = 0, previewVersion = 0, pollTimer = null, searchController = null
const failure = (e, fallback) => e?.response?.data?.msg || e?.message || fallback
function cancelSearch() {
  if (!searchController) return
  searchController.abort()
  searched.value = false; searchCancelled.value = true
}
onBeforeUnmount(() => { clearTimeout(pollTimer); cancelSearch(); documentVersion++; libraryVersion++; previewVersion++ })
onMounted(() => { loadLibraries(); loadSessions() })
async function loadLibraries(preferredId) {
  const version = ++libraryVersion
  kbLoading.value = true; kbError.value = ''
  try {
    const list = rag.ragList(await rag.listKnowledgeBases({ scope:scope.value }))
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
  selected.value = kb; documents.value = []; hits.value = []; searched.value = false; searchCancelled.value = false; searchMode.value = ''; searchError.value = ''; searchWarning.value = ''; searchActualMode.value = ''; searchDegradedReason.value = null; searchIndexVersions.value = []
  documentPage.value = 1
  historyId.value = ''; resetKnowledgeContext(kb?.id)
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
async function reviewSaved(result) {
  const updated = rag.unwrapRag(result)
  if (reviewFile.value && updated) Object.assign(reviewFile.value, updated)
  await loadDocuments()
}
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
async function chooseUpload(file) {
  if (!file.raw || !canWrite.value || uploading.value) return
  if (file.raw.size>20*1024*1024) { ElMessage.error('单文件不能超过20 MB'); return }
  if (!/\.(pdf|txt|md|docx|pptx)$/i.test(file.raw.name)) { ElMessage.error('请选择PDF、DOCX、PPTX、MD或UTF-8文本；不支持旧DOC/PPT格式'); return }
  const kbId = selected.value.id
  uploading.value = true; documentError.value = ''
  try {
    const doc = rag.unwrapRag(await rag.uploadDocument(kbId,file.raw))
    if (selected.value?.id === kbId) { await loadDocuments(); await loadLibraries(kbId) }
    ElMessage.success(doc?.duplicate ? '相同资料已存在，保留现有处理结果' : '资料已上传，等待后台处理')
  } catch (problem) { if (selected.value?.id === kbId) documentError.value = failure(problem,'上传失败，请重试') }
  finally { uploading.value = false }
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
  previewFile.value = doc; previewVisible.value = true; previewLoading.value = true; previewError.value = ''; chunks.value = []
  try { const list = rag.ragList(await rag.getDocumentChunks(doc.id)); if (version === previewVersion) chunks.value = list }
  catch (e) { if (version === previewVersion) previewError.value = failure(e,'无法读取资料片段') }
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
  searching.value = true; searched.value = false; searchCancelled.value = false; searchError.value = ''; searchWarning.value = ''; hits.value = []; searchMode.value = ''; searchActualMode.value = ''; searchDegradedReason.value = null; searchIndexVersions.value = []
  try {
    const data = rag.unwrapRag(await rag.search({knowledgeBaseIds:[kbId],query:searchQuery.value.trim(),topK:8},{signal:controller.signal}))
    if (selected.value?.id === kbId && !controller.signal.aborted) {
      searched.value = true
      hits.value = Array.isArray(data.hits) ? data.hits : []; searchMode.value = data.mode; searchWarning.value = data.warning || ''
      searchActualMode.value = data.actualMode || ''; searchDegradedReason.value = data.degradedReason || null
      searchIndexVersions.value = Array.isArray(data.indexVersions) ? data.indexVersions : []
    }
  } catch (e) { if (!controller.signal.aborted) searchError.value = failure(e,'检索失败，请重试') }
  finally { if (searchController === controller) { searching.value = false; searchController = null } }
}
async function ask() {
  if (!question.value.trim() || generating.value) return
  const query = question.value.trim(); question.value = ''
  try { await sendMessage('knowledge',query); historyId.value = currentSessionId.value; await loadSessions() }
  catch (e) { ElMessage.error(failure(e,'回答生成失败')) }
}
async function newConversation() { historyId.value = ''; await createNewSession(selected.value.id,'学习对话'); historyId.value = currentSessionId.value; await loadSessions() }
async function switchHistory(id) { if (!id) return; const session = currentSessions.value.find(s => s.id === id); if (session) { try { await restoreSession(session) } catch(e) { ElMessage.error(failure(e,'加载对话失败')) } } }
async function deleteConversation() {
  try { await ElMessageBox.confirm('删除这段对话记录？','删除对话',{type:'warning'}) } catch { return }
  try { await removeSession(historyId.value); historyId.value = '' } catch(e) { ElMessage.error(failure(e,'删除失败')) }
}
defineExpose({openCreate,loadLibraries})
</script>

<style scoped>
.retrieval-snapshots { color:#64748b; font-size:12px; line-height:1.7; margin:12px 0; }.retrieval-snapshots summary { cursor:pointer; }.retrieval-snapshots code { display:block; overflow-wrap:anywhere; }
.hit-title .el-tag { color:#475569; background:#f1f5f9; border-color:#cbd5e1; }
.native-kb-workspace { display:grid; grid-template-columns:280px minmax(0,1fr); gap:20px; align-items:start; }
.library-panel,.workspace-main { background:#fff; border:1px solid #e4e9f2; border-radius:14px; padding:20px; min-width:0; }
.panel-heading,.workspace-heading,.heading-actions,.document-toolbar,.search-toolbar,.chat-toolbar,.input-footer,.hit-title { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
.panel-heading,.workspace-heading,.hit-title { justify-content:space-between; }
.panel-heading { margin-bottom:16px; }.scope-tabs { display:flex; margin:12px 0 16px; }
.library-item { display:flex; flex-direction:column; gap:8px; width:100%; text-align:left; padding:15px 12px; border:1px solid #edf1f7; border-radius:10px; background:#fff; cursor:pointer; margin:10px 0; color:#334155; }
.library-item.active { background:#eef4ff; border-color:#a9c6fb; }.library-title { display:flex; align-items:center; justify-content:space-between; gap:8px; font-weight:600; }
.library-description,.library-count,.muted,.source-position,.document-toolbar>span { font-size:12px; color:#718096; line-height:1.6; }.library-description { overflow-wrap:anywhere; }
.workspace-heading { margin-bottom:16px; }.workspace-heading h3 { margin:0 0 8px; font-size:20px; }.workspace-heading p { margin:0; font-size:13px; color:#64748b; }
.workspace-tabs { margin-top:18px; }.document-toolbar,.chat-toolbar,.search-toolbar { margin-bottom:18px; }.search-toolbar .el-input { flex:1; min-width:180px; }
.conversation { min-height:220px; max-height:520px; overflow:auto; padding:8px 0 20px; }.chat-message { padding:16px; margin:12px 0; background:#f5f8fd; border-radius:12px; line-height:1.75; overflow-wrap:anywhere; }.chat-message.user { background:#edf3ff; margin-left:32px; }.message-role { display:block; font-size:12px; color:#64748b; margin-bottom:6px; }
.input-footer { margin:12px 0; }.input-footer .muted { margin-right:auto; }.search-hit,.document-chunk { padding:16px; margin:16px 0; border:1px solid #e6edf5; border-radius:12px; }
pre { white-space:pre-wrap; overflow-wrap:anywhere; font-family:inherit; line-height:1.8; font-size:14px; }.error-text { color:#dc2626; font-size:12px; }
.document-actions { display:flex; flex-wrap:wrap; gap:6px; }.document-actions .el-button + .el-button { margin-left:0; }
.parse-warning { color:#a16207; font-size:12px; line-height:1.6; margin-top:4px; }.el-pagination { margin-top:16px; }
:deep(.chat-message pre) { overflow:auto; padding:12px; background:#fff; border-radius:8px; }:deep(.chat-message table) { border-collapse:collapse; max-width:100%; display:block; overflow:auto; }:deep(.chat-message td),:deep(.chat-message th) { border:1px solid #dfe6ef; padding:8px; }:deep(.chat-message img) { max-width:100%; }
@media(max-width:1000px) { .native-kb-workspace { grid-template-columns:220px minmax(0,1fr); gap:12px; }.library-panel,.workspace-main { padding:14px; } }
@media(max-width:760px) { .native-kb-workspace { grid-template-columns:1fr; }.library-panel { max-height:340px; overflow:auto; }.heading-actions { width:100%; }.chat-message.user { margin-left:12px; } }
</style>
