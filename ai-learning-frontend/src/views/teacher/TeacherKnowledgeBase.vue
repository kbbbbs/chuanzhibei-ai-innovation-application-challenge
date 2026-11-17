<template>
  <div class="teacher-kb-page">
    <PageHeader v-if="!embedded"
      eyebrow="AI 备课"
      title="知识库管理"
      description="整理教学资料，明确共享范围，查看原文出处并进行问答与出题。"
    />
    <NativeKnowledgeWorkspace manage :course-id="courseId" :workspace-tab="workspaceTab" :embedded="embedded" @select="selectKb" @generate="openGenerateDialog" />
    <!-- Generate Questions from Knowledge Base Dialog -->
    <el-dialog v-model="genVisible" title="根据知识库出题" width="900px" :close-on-click-modal="false" destroy-on-close>
      <el-steps :active="genStep - 1" finish-status="success" align-center class="gen-steps">
        <el-step title="配置参数" />
        <el-step title="生成中" />
        <el-step title="预览编辑" />
        <el-step title="保存结果" />
      </el-steps>

      <!-- Step 1: Configure -->
      <div v-if="genStep === 1">
        <div v-if="genTasks.length" class="gen-tasks" data-testid="gen-task-list">
          <strong>最近的出题任务</strong>
          <div v-for="task in genTasks" :key="task.id" class="gen-task">
            <span>{{ formatSessionTime(String(task.createdAt || '')) }} · {{ taskStatusLabel(task) }}</span>
            <el-button size="small" link type="primary" @click="openTask(task)">{{ task.status === 'RUNNING' ? '查看进度' : '打开' }}</el-button>
          </div>
        </div>
        <el-form label-width="100px">
          <el-form-item label="知识库">
            <el-tag type="info">{{ selectedKb?.name }}</el-tag>
          </el-form-item>
          <el-form-item v-if="!courseId" label="选择课程" required>
            <el-select v-model="genForm.courseId" placeholder="请选择课程">
              <el-option v-for="c in courses" :key="c.id" :label="c.title" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="题目数量">
            <el-input-number v-model="genForm.count" :min="1" :max="20" />
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="genForm.difficulty">
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
          <el-form-item label="题型">
            <el-checkbox-group v-model="genForm.types">
              <el-checkbox label="SINGLE_CHOICE">单选题</el-checkbox>
              <el-checkbox label="MULTIPLE_CHOICE">多选题</el-checkbox>
              <el-checkbox label="TRUE_FALSE">判断题</el-checkbox>
              <el-checkbox label="FILL_BLANK">填空题</el-checkbox>
              <el-checkbox label="SHORT_ANSWER">简答题</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="补充要求">
            <el-input v-model="genForm.instruction" type="textarea" :rows="2" placeholder="如：更偏概念理解，不要太偏记忆" />
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 2: Generating -->
      <div v-else-if="genStep === 2" class="gen-progress" role="status">
        <el-icon class="is-loading" :size="40" aria-hidden="true"><Loading /></el-icon>
        <p>正在基于知识库生成题目{{ currentTask ? `（已生成 ${currentTask.generated}/${currentTask.requested}）` : '' }}</p>
        <p class="gen-hint">可以关闭窗口，生成会在后台继续；之后在「最近的出题任务」中打开即可。</p>
      </div>

      <!-- Step 3: Preview & Edit -->
      <div v-else-if="genStep === 3">
        <el-alert v-if="currentTask?.status === 'INCOMPLETE'" type="warning" :closable="false" show-icon class="gen-alert"
          :title="`已生成 ${currentTask.generated}/${currentTask.requested} 道题：${currentTask.errorMessage || '生成未完成'}`">
          <el-button size="small" type="primary" data-testid="resume-gen-task" @click="resumeTask">继续生成剩余 {{ currentTask.requested - currentTask.generated }} 题</el-button>
        </el-alert>
        <el-alert v-else-if="generatedQuestions.length > 0"
          :title="`已生成 ${generatedQuestions.length} 道题，修改会自动保存为草稿；确认后保存到题库`"
          type="success" :closable="false" class="gen-alert" />
        <p v-if="genDraftSave.label.value" class="gen-hint" data-testid="gen-draft-status">{{ genDraftSave.label.value }}</p>
        <el-table :data="generatedQuestions" border stripe max-height="400" v-if="generatedQuestions.length > 0">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="题型" width="90">
            <template #default="{ row }">
              <el-tag :type="TYPE_TAG[row.type] || ''" size="small">{{ TYPE_LABEL[row.type] || row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="题干" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag v-if="row.saved" size="small" type="success">已入库</el-tag>
              {{ row.content }}
              <div v-if="row.saveError" class="gen-error">未保存：{{ row.saveError }}</div>
            </template>
          </el-table-column>
          <el-table-column label="难度" width="80">
            <template #default="{ row }">{{ row.difficulty }}</template>
          </el-table-column>
          <el-table-column label="分值" width="70">
            <template #default="{ row }">{{ row.score }}</template>
          </el-table-column>
          <el-table-column label="操作" :width="narrow ? ROW_SHEET_COLUMN_WIDTH : 160" fixed="right">
            <template #default="{ row, $index }">
              <RowActions :label="row.content || `第 ${$index + 1} 题`">
                <el-button size="small" :disabled="row.saved" @click="openEditQuestion($index)">编辑</el-button>
                <el-button size="small" type="danger" :disabled="row.saved" @click="removeGenerated($index)">删除</el-button>
              </RowActions>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="未生成任何题目" />
      </div>

      <!-- Step 4: Save Result -->
      <div v-else-if="genStep === 4" style="text-align:center;padding:40px">
        <el-result :icon="genResult.fail > 0 ? 'warning' : 'success'"
          :title="genResult.fail > 0 ? '部分题目未保存' : '保存完成'"
          :sub-title="`本次保存 ${genResult.success} 条，未保存 ${genResult.fail} 条；本任务共 ${genResult.total} 条已入库`" />
        <el-button v-if="genResult.fail > 0" @click="genStep = 3">返回修改未保存的题目</el-button>
      </div>

      <template #footer>
        <el-button @click="closeGenDialog">{{ genStep === 1 ? '取消' : '关闭' }}</el-button>
        <el-button v-if="genStep === 1" type="primary" :disabled="!canGenerate" @click="startGenerate">开始生成</el-button>
        <el-button v-if="genStep === 3 && unsavedCount > 0" type="primary" :loading="genSaving" @click="saveGenQuestions">保存 {{ unsavedCount }} 道题到题库</el-button>
        <el-button v-if="genStep === 3 && currentTask" @click="skipGenSave">放弃本次题目</el-button>
      </template>
    </el-dialog>

    <!-- Edit Generated Question Dialog -->
    <el-dialog v-model="editGenVisible" title="编辑题目" width="700px">
      <el-form v-if="editGenForm" label-width="100px">
        <el-form-item label="题型">
          <el-select v-model="editGenForm.type">
            <el-option label="单选题" value="SINGLE_CHOICE" />
            <el-option label="多选题" value="MULTIPLE_CHOICE" />
            <el-option label="判断题" value="TRUE_FALSE" />
            <el-option label="填空题" value="FILL_BLANK" />
            <el-option label="简答题" value="SHORT_ANSWER" />
          </el-select>
        </el-form-item>
        <el-form-item label="题干">
          <el-input v-model="editGenForm.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="editGenForm.difficulty">
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
          </el-select>
        </el-form-item>
        <el-form-item label="分值">
          <el-input-number v-model="editGenForm.score" :min="1" />
        </el-form-item>
        <el-form-item v-if="['SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE'].includes(editGenForm.type)" label="选项">
          <div v-for="(opt, idx) in (editGenForm.options || [])" :key="idx" style="display:flex;gap:8px;margin-bottom:8px">
            <el-tag>{{ String.fromCharCode(65 + idx) }}</el-tag>
            <el-input v-model="opt.optionContent" :placeholder="'选项' + String.fromCharCode(65 + idx)" style="flex:1" />
            <el-button size="small" type="danger" @click="editGenForm.options.splice(idx,1)">删除</el-button>
          </div>
          <el-button size="small" @click="editGenForm.options.push({optionKey:String.fromCharCode(65+(editGenForm.options||[]).length), optionContent:'', sortOrder:(editGenForm.options||[]).length+1})">添加选项</el-button>
        </el-form-item>
        <el-form-item label="正确答案">
          <template v-if="['SINGLE_CHOICE','TRUE_FALSE'].includes(editGenForm.type)">
            <el-select v-model="editGenForm.answerIdx" placeholder="选择正确答案">
              <el-option v-for="(opt, idx) in (editGenForm.options || [])" :key="idx" :label="opt.optionContent || String.fromCharCode(65+idx)" :value="idx" />
            </el-select>
          </template>
          <template v-else-if="editGenForm.type === 'MULTIPLE_CHOICE'">
            <el-checkbox-group v-model="editGenForm.answerIdxList">
              <el-checkbox v-for="(opt, idx) in (editGenForm.options || [])" :key="idx" :label="idx">{{ opt.optionContent || String.fromCharCode(65+idx) }}</el-checkbox>
            </el-checkbox-group>
          </template>
          <template v-else>
            <el-input v-model="editGenForm.answerText" type="textarea" :rows="2" placeholder="参考答案" />
          </template>
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="editGenForm.analysis" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="知识点">
          <el-input v-model="editGenForm.knowledgePoint" placeholder="知识点" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editGenVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEditGen">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, ArrowDown, ArrowUp, Loading } from '@element-plus/icons-vue'
import NativeKnowledgeWorkspace from '@/components/knowledge/NativeKnowledgeWorkspace.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import RowActions from '@/components/ui/RowActions.vue'
import { ROW_SHEET_COLUMN_WIDTH, useNarrowScreen } from '@/composables/useNarrowScreen'
import KnowledgeGraphPreview from '@/components/knowledge/KnowledgeGraphPreview.vue'
import AlgorithmBilingualGraph from '@/components/knowledge/AlgorithmBilingualGraph.vue'
import * as kbApi from '@/api/knowledgeBase'
import {
  startQuestionGenerationTask, listQuestionGenerationTasks, getQuestionGenerationTask, updateQuestionGenerationDrafts,
  resumeQuestionGenerationTask, saveQuestionGenerationTask, discardQuestionGenerationTask
} from '@/api/exam'
import { useAutosave } from '@/composables/useAutosave'
import { getCourses } from '@/utils/course'
import { currentUserId, scopedTeacherParams, getRecords } from '@/utils/apiHelpers'
import { useUserStore } from '@/store/user'
import { useAssistant } from '@/composables/useAssistant'
import { deleteAssistantSession } from '@/api/assistant'

const props = defineProps({ courseId: Number, workspaceTab: String, embedded: Boolean })
function unwrapData(data) {
  while (data && typeof data === 'object' && !Array.isArray(data) && data.data !== undefined)
    data = data.data
  return data
}

function unwrapList(data) {
  const unwrapped = unwrapData(data)
  if (Array.isArray(unwrapped)) return unwrapped
  return unwrapped?.items || unwrapped?.list || unwrapped?.documents || unwrapped?.results || unwrapped?.records || []
}

const kbs = ref([]); const kbLoading = ref(false)
const kbSearch = ref('')
const selectedKb = ref(null)
const kbListCollapsed = ref(false)
const historyCollapsed = ref(false)
const chatCollapsed = ref(false)
const filesCollapsed = ref(false)
const router = useRouter()
const narrow = useNarrowScreen()
const files = ref([]); const filesLoading = ref(false)
const uploadFile = ref(null); const uploading = ref(false)
const graphRefreshKey = ref(0)
const statusPollTimer = ref(null)

// 历史对话
const assistant = useAssistant()
const sessions = computed(() => assistant.workspace.sessions)
const loadSessions = assistant.loadSessions
async function removeSession(id) { await deleteAssistantSession(id); await loadSessions() }
const sessionsLoading = ref(false)
const activeSessionId = ref(null)

const filteredKbs = computed(() => {
  const keyword = kbSearch.value.trim().toLowerCase()
  if (!keyword) return kbs.value
  return kbs.value.filter(kb => [kb.name, kb.description]
    .some(value => String(value || '').toLowerCase().includes(keyword)))
})

const createVisible = ref(false); const creating = ref(false)
const createForm = reactive({ name: '', description: '' })
const createRef = ref(null)
const createRules = { name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }] }

const editVisible = ref(false); const saving = ref(false)
const editForm = reactive({ id: '', name: '', description: '' })
const editRef = ref(null)
const editRules = { name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }] }

function formatTime(t) {
  if (!t) return '-'
  return t.slice(0, 19).replace('T', ' ')
}
function formatSize(bytes) {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}
function normalizeParseStatus(value) {
  const status = String(value || 'pending').toLowerCase()
  if (status === 'success') return 'success'
  if (status === 'failed') return 'failed'
  if (status === 'parsing') return 'parsing'
  return 'pending'
}
function parseStatusLabel(value) {
  return {
    pending: '待处理',
    parsing: '处理中',
    success: '已完成',
    failed: '失败'
  }[normalizeParseStatus(value)] || '待处理'
}
function parseStatusTag(value) {
  return {
    pending: 'info',
    parsing: 'warning',
    success: 'success',
    failed: 'danger'
  }[normalizeParseStatus(value)] || 'info'
}
function fileHasRunningStatus(file) {
  return ['pending', 'parsing'].includes(normalizeParseStatus(file?.parseStatus))
}
function stopStatusPolling() {
  if (statusPollTimer.value) {
    clearTimeout(statusPollTimer.value)
    statusPollTimer.value = null
  }
}
function scheduleStatusPolling() {
  stopStatusPolling()
  if (!files.value.some(fileHasRunningStatus) || !selectedKb.value?.id) return
  statusPollTimer.value = setTimeout(async () => {
    await loadFiles()
  }, 3000)
}

function resetKbSearch() {
  kbSearch.value = ''
}

function openGraph() {
  if (!selectedKb.value?.id) return
  router.push({ name: 'KnowledgeGraph', query: { kbId: selectedKb.value.id } })
}

async function loadKBs() {
  kbLoading.value = true
  try {
    const res = await kbApi.listKnowledgeBases()
    if (res.code === '200') kbs.value = unwrapList(res.data)
  } finally { kbLoading.value = false }
}

function selectKb(kb) {
  stopStatusPolling()
  selectedKb.value = kb
  uploadFile.value = null
  if (kb) loadFiles()
}

async function loadFiles() {
  if (!selectedKb.value) return
  filesLoading.value = true
  try {
    const res = await kbApi.listKnowledge(selectedKb.value.id)
    if (res.code === '200') files.value = unwrapList(res.data)
  } finally {
    filesLoading.value = false
    scheduleStatusPolling()
  }
}

async function doUpload() {
  if (!uploadFile.value || !selectedKb.value) return
  uploading.value = true
  try {
    const res = await kbApi.uploadToKB(selectedKb.value.id, uploadFile.value)
    if (res.code === '200') {
      ElMessage.success(res.msg || '上传成功')
      uploadFile.value = null
      await loadFiles()
      await loadKBs()
      graphRefreshKey.value++
      scheduleStatusPolling()
    } else ElMessage.error(res.msg || '上传失败')
  } finally { uploading.value = false }
}

function openCreateDialog() {
  createForm.name = ''; createForm.description = ''
  createVisible.value = true
}

async function doCreate() {
  const valid = await createRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    const res = await kbApi.createKnowledgeBase({ name: createForm.name, description: createForm.description })
    if (res.code === '200') {
      ElMessage.success('创建成功')
      createVisible.value = false
      await loadKBs()
    } else ElMessage.error(res.msg || '创建失败')
  } finally { creating.value = false }
}

function openEditDialog(kb) {
  editForm.id = kb.id; editForm.name = kb.name; editForm.description = kb.description || ''
  editVisible.value = true
}

async function doEdit() {
  const valid = await editRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const res = await kbApi.updateKnowledgeBase(editForm.id, { name: editForm.name, description: editForm.description })
    if (res.code === '200') {
      ElMessage.success('已保存')
      editVisible.value = false
      await loadKBs()
      if (selectedKb.value?.id === editForm.id) {
        selectedKb.value.name = editForm.name
        selectedKb.value.description = editForm.description
      }
    } else ElMessage.error(res.msg || '保存失败')
  } finally { saving.value = false }
}

async function handleDeleteKb(row) {
  try { await ElMessageBox.confirm(`确定删除知识库「${row.name}」？操作不可恢复。`, '确认', { type: 'warning' }) } catch { return }
  try {
    const res = await kbApi.deleteKnowledgeBase(row.id)
    if (res.code === '200') {
      ElMessage.success('已删除')
      if (selectedKb.value?.id === row.id) { selectedKb.value = null; files.value = [] }
      await loadKBs()
    } else ElMessage.error(res.msg || '删除失败')
  } catch { ElMessage.error('删除失败') }
}

async function handleDeleteFile(row) {
  try { await ElMessageBox.confirm(`确定删除「${row.title || row.file_name || row.id}」？`, '确认', { type: 'warning' }) } catch { return }
  try {
    const res = await kbApi.deleteKnowledge(row.id)
    if (res.code === '200') {
      files.value = files.value.filter(f => f.id !== row.id)
      if (selectedKb.value && typeof selectedKb.value.knowledge_count === 'number') {
        selectedKb.value.knowledge_count = Math.max(0, selectedKb.value.knowledge_count - 1)
      }
      const kb = kbs.value.find(item => item.id === selectedKb.value?.id)
      if (kb && typeof kb.knowledge_count === 'number') {
        kb.knowledge_count = Math.max(0, kb.knowledge_count - 1)
      }
      ElMessage.success('已删除')
      graphRefreshKey.value++
      void loadFiles()
      void loadKBs()
    } else ElMessage.error(res.msg || '删除失败')
  } catch { ElMessage.error('删除失败') }
}

// ========== 根据知识库出题 ==========
const userStore = useUserStore()
const genVisible = ref(false)
const genStep = ref(1)
const genSaving = ref(false)
const genForm = reactive({
  courseId: null,
  agentId: null,
  count: 5,
  difficulty: 'MEDIUM',
  types: ['SINGLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER'],
  instruction: ''
})
const generatedQuestions = ref([])
const genTasks = ref([])
const currentTask = ref(null)
let genPollTimer = null, genPollRun = 0
const unsavedCount = computed(() => generatedQuestions.value.filter(question => !question.saved).length)
// Edits to a generated draft go to the server task, so reopening the task later shows them.
const genDraftSave = useAutosave({
  key: () => currentTask.value ? `eduloop:question-task:${currentTask.value.id}` : '',
  snapshot: () => JSON.parse(JSON.stringify(generatedQuestions.value)),
  save: async () => {
    if (!currentTask.value) return true
    const res = await updateQuestionGenerationDrafts(currentTask.value.id, generatedQuestions.value, { silent: true })
    if (res?.code !== '200') return false
    currentTask.value = { ...currentTask.value, ...res.data, questions: undefined }
    return true
  }
})
function taskStatusLabel(task) {
  const counts = `${task.generated}/${task.requested} 题`
  return {
    RUNNING: `生成中 ${counts}`, READY: `已生成 ${counts}，${task.saved ? `已入库 ${task.saved} 题` : '未入库'}`,
    INCOMPLETE: `未完成 ${counts}`, SAVED: `已全部入库（${task.saved} 题）`
  }[task.status] || counts
}
function showTask(task) {
  currentTask.value = { ...task, questions: undefined }
  if (task.status === 'RUNNING') { genStep.value = 2; pollTask(task.id); return }
  generatedQuestions.value = task.questions || []
  genStep.value = 3
}
async function openTask(task) {
  try {
    const res = await getQuestionGenerationTask(task.id)
    if (res.code === '200') showTask(res.data)
  } catch { ElMessage.error('任务读取失败，请重试') }
}
function stopTaskPolling() { genPollRun += 1; clearTimeout(genPollTimer); genPollTimer = null }
function pollTask(id) {
  stopTaskPolling()
  const run = genPollRun
  const tick = async () => {
    try {
      const res = await getQuestionGenerationTask(id, { silent: true })
      if (run !== genPollRun) return
      if (res.code === '200') {
        currentTask.value = { ...res.data, questions: undefined }
        if (res.data.status !== 'RUNNING') {
          generatedQuestions.value = res.data.questions || []
          genStep.value = 3
          if (res.data.status === 'INCOMPLETE') ElMessage.warning(res.data.errorMessage || '生成未完成，可以继续生成剩余题目')
          return
        }
      }
    } catch { /* transient: keep following */ }
    if (run === genPollRun) genPollTimer = setTimeout(tick, 2000)
  }
  genPollTimer = setTimeout(tick, 1000)
}
async function resumeTask() {
  try {
    await genDraftSave.flush()
    const res = await resumeQuestionGenerationTask(currentTask.value.id)
    if (res.code === '200') showTask(res.data)
  } catch { ElMessage.error('继续生成失败，请重试') }
}
function removeGenerated(index) { generatedQuestions.value.splice(index, 1); genDraftSave.touch() }
const genResult = reactive({ total: 0, success: 0, fail: 0 })
const courses = ref([])
const editGenIndex = ref(-1)
const editGenVisible = ref(false)
const editGenForm = ref(null)

const TYPE_LABEL = {
  SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题',
  FILL_BLANK: '填空题', SHORT_ANSWER: '简答题'
}
const TYPE_TAG = {
  SINGLE_CHOICE: 'primary', MULTIPLE_CHOICE: 'success', TRUE_FALSE: 'warning',
  FILL_BLANK: 'info', SHORT_ANSWER: ''
}

const canGenerate = computed(() => selectedKb.value?.id && genForm.courseId && genForm.count > 0 && genForm.types.length > 0)

async function openGenerateDialog(kb) {
  if (kb?.id) selectedKb.value = kb
  genStep.value = 1
  genForm.courseId = props.courseId || null
  genForm.agentId = null
  genForm.count = 5
  genForm.difficulty = 'MEDIUM'
  genForm.types = ['SINGLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER']
  genForm.instruction = ''
  generatedQuestions.value = []
  currentTask.value = null
  genTasks.value = []
  genVisible.value = true
  listQuestionGenerationTasks(selectedKb.value?.id)
    .then(res => { if (res?.code === '200') genTasks.value = (res.data || []).filter(task => task.status !== 'SAVED').slice(0, 5) })
    .catch(() => { genTasks.value = [] })

  try {
    const courseRes = await getCourses(scopedTeacherParams(userStore, { pageSize: 100 }))
    courses.value = getRecords(courseRes?.data) || []
  } catch {
    courses.value = []
  }
}

function closeGenDialog() {
  stopTaskPolling()
  genDraftSave.flush()
  genVisible.value = false
}

async function startGenerate() {
  genStep.value = 2
  try {
    const res = await startQuestionGenerationTask({
      knowledgeBaseId: selectedKb.value?.id,
      courseId: genForm.courseId,
      agentId: genForm.agentId,
      count: genForm.count,
      types: genForm.types,
      difficulty: genForm.difficulty,
      instruction: genForm.instruction
    })
    if (res.code === '200' && res.data) showTask(res.data)
    else genStep.value = 1
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.response?.data?.msg || '出题任务未能开始，请重试')
    genStep.value = 1
  }
}

function openEditQuestion(index) {
  const q = generatedQuestions.value[index]
  const options = (q.options || []).map((o, i) => ({
    optionKey: o.optionKey || String.fromCharCode(65 + i),
    optionContent: o.optionContent || '',
    sortOrder: o.sortOrder || i + 1
  }))

  let answerIdx = null
  let answerIdxList = []
  let answerText = ''
  const answers = q.answers || []
  if (answers.length > 0) {
    const first = answers[0].answerContent || ''
    if (['SINGLE_CHOICE', 'TRUE_FALSE'].includes(q.type)) {
      answerIdx = first.charCodeAt(0) - 65
    } else if (q.type === 'MULTIPLE_CHOICE') {
      answerIdxList = answers.map(a => a.answerContent.charCodeAt(0) - 65).filter(i => i >= 0)
    } else {
      answerText = first
    }
  }

  editGenIndex.value = index
  editGenForm.value = reactive({
    type: q.type,
    content: q.content || '',
    difficulty: q.difficulty || 'MEDIUM',
    score: q.score || 5,
    options: options,
    answerIdx,
    answerIdxList,
    answerText,
    analysis: q.analysis || '',
    knowledgePoint: q.knowledgePoint || ''
  })
  editGenVisible.value = true
}

function saveEditGen() {
  const ef = editGenForm.value
  if (!ef) return

  let answers = []
  if (['SINGLE_CHOICE', 'TRUE_FALSE'].includes(ef.type)) {
    if (ef.answerIdx != null && ef.answerIdx >= 0) {
      answers = [{ answerContent: String.fromCharCode(65 + ef.answerIdx), sortOrder: 1 }]
    }
  } else if (ef.type === 'MULTIPLE_CHOICE') {
    answers = (ef.answerIdxList || []).map((i, idx) => ({
      answerContent: String.fromCharCode(65 + i), sortOrder: idx + 1
    }))
  } else {
    answers = [{ answerContent: ef.answerText || '', sortOrder: 1 }]
  }

  generatedQuestions.value[editGenIndex.value] = {
    ...generatedQuestions.value[editGenIndex.value],
    type: ef.type,
    content: ef.content,
    difficulty: ef.difficulty,
    score: ef.score,
    options: ef.options?.filter(o => o.optionContent),
    answers: answers,
    analysis: ef.analysis,
    knowledgePoint: ef.knowledgePoint
  }
  delete generatedQuestions.value[editGenIndex.value].saveError
  genDraftSave.touch()
  editGenVisible.value = false
}

async function saveGenQuestions() {
  if (generatedQuestions.value.length === 0) return
  genSaving.value = true
  try {
    if (!await genDraftSave.flush()) { ElMessage.error('题目修改尚未保存，请稍后重试'); return }
    const res = await saveQuestionGenerationTask(currentTask.value.id)
    if (res.code === '200' && res.data) {
      currentTask.value = { ...res.data, questions: undefined }
      generatedQuestions.value = res.data.questions || []
      Object.assign(genResult, { total: res.data.saved, success: res.data.savedNow, fail: res.data.failedNow })
      genStep.value = 4
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch {
    ElMessage.error('保存失败，请重试')
  } finally {
    genSaving.value = false
  }
}

async function skipGenSave() {
  try { await ElMessageBox.confirm('未入库的题目将被放弃，这个出题任务也会从列表中移除。已入库的题目不受影响。', '放弃本次题目', { type: 'warning' }) } catch { return }
  try { await discardQuestionGenerationTask(currentTask.value.id) } catch { ElMessage.error('放弃失败，请重试'); return }
  genDraftSave.finish()
  stopTaskPolling()
  genVisible.value = false
}

function formatSessionTime(t) {
  if (!t) return '-'
  return t.slice(0, 19).replace('T', ' ')
}

async function loadHistorySessions() {
  sessionsLoading.value = true
  try { await loadSessions() } finally { sessionsLoading.value = false }
}

function switchSession(s) {
  activeSessionId.value = s.id
  ElMessage.success(`已切换到「${s.title || '未命名会话'}」`)
}

async function handleDeleteSession(s) {
  try { await ElMessageBox.confirm(`确定删除「${s.title || '未命名会话'}」？`, '确认', { type: 'warning' }) } catch { return }
  try {
    await removeSession(s.id)
    if (activeSessionId.value === s.id) activeSessionId.value = null
    ElMessage.success('已删除')
  } catch { ElMessage.error('删除失败') }
}

function handleNewChat() {
  selectedKb.value = null
  files.value = []
  activeSessionId.value = null
}

// Knowledge workspace owns document and conversation loading.
onBeforeUnmount(() => { stopStatusPolling(); stopTaskPolling() })
</script>

<style scoped>
.gen-tasks { margin-bottom: var(--space-4); padding: var(--space-3); border: 1px solid var(--line-subtle); border-radius: var(--radius-md); font-size: var(--text-sm); }
.gen-task { display: flex; justify-content: space-between; align-items: center; gap: var(--space-2); margin-top: var(--space-2); }
.gen-hint { color: var(--text-subtle); font-size: var(--text-xs); }
.gen-alert { margin-bottom: var(--space-3); }
.gen-error { color: var(--critical); font-size: var(--text-xs); white-space: normal; }
.teacher-kb-page {
  display: flex;
  flex-direction: column;
  width: min(100%, 1320px);
  margin: 0 auto;
}

.gen-progress {
  display: grid;
  justify-items: center;
  gap: var(--space-4);
  padding: var(--space-12) var(--space-5);
  color: var(--text-subtle);
}

.gen-progress p {
  margin: 0;
}

.teacher-kb-layout {
  display: grid;
  grid-template-columns: minmax(360px, 460px) minmax(0, 1fr);
  gap: 16px;
  min-height: 0;
  flex: 1;
}

.kb-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.kb-right {
  min-width: 0;
  min-height: 640px;
}

.panel {
  background: var(--bg-elevated);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: var(--card-shadow);
}

.kb-list-panel {
  min-height: 260px;
  max-height: 360px;
}

.kb-list-panel.collapsed {
  min-height: 0;
  max-height: none;
}

.panel-title {
  padding: 16px 18px;
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.panel-title-stack {
  align-items: flex-start;
}

.panel-title-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapse-toggle {
  border: none;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  transition: all 0.2s ease;
}

.collapse-toggle:hover {
  background: var(--border-color);
  color: var(--text-primary);
}

.panel-sub {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-muted);
}

.kb-search {
  display: flex;
  gap: 8px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--border-color);
}

.panel-body.scroll-y {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  min-height: 0;
}

.upload-bar {
  padding: 12px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-subtle);
}

.upload-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.kb-item {
  padding: 14px 16px;
  border-radius: 14px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  transition: all .18s ease;
  border: 1px solid transparent;
}

.kb-item + .kb-item {
  margin-top: 8px;
}

.kb-item:hover {
  background: var(--bg-subtle);
  border-color: var(--border-color);
}

.kb-item.active {
  background: var(--primary-color-soft);
  border-color: var(--primary-color-light);
  box-shadow: var(--shadow-xs);
}

/* 历史对话 */
.history-panel {
  max-height: 320px;
}

.session-item {
  padding: 12px 14px;
  border-radius: 12px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  transition: all .18s ease;
  border: 1px solid transparent;
}

.session-item + .session-item {
  margin-top: 6px;
}

.session-item:hover {
  background: var(--bg-subtle);
  border-color: var(--border-color);
}

.session-item.active {
  background: var(--primary-color-soft);
  border-color: var(--primary-color-light);
}

.session-item-left {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  font-size: 11px;
  color: var(--text-muted);
  display: block;
  margin-top: 3px;
}

/* Chat panel */
.chat-panel {
  height: 100%;
}

.chat-panel .ai-widget-container {
  max-height: none;
  height: 100%;
}

.kb-item-left {
  flex: 1;
  min-width: 0;
}

.kb-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  display: block;
}

.kb-desc {
  font-size: 12px;
  color: var(--text-secondary);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 4px;
}

.kb-summary-actions {
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-bottom: 1px solid var(--border-color);
}

.action-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.section-caption {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding: 12px 12px 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.section-caption-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-item {
  padding: 12px 2px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--border-color);
}

.file-item:last-child {
  border-bottom: none;
}

.file-main {
  flex: 1;
  min-width: 0;
}

.file-name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.graph-panel :deep(.preview-shell) {
  min-height: 260px;
  margin-bottom: 20px;
}

@media (max-width: 1100px) {
  .teacher-kb-layout {
    grid-template-columns: 1fr;
  }

  .kb-right {
    min-height: 520px;
  }
}

@media (max-width: 768px) {
  .panel-title,
  .upload-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .upload-bar {
    width: 100%;
  }
}
</style>
