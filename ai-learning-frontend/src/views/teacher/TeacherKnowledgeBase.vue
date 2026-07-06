<template>
  <div class="teacher-kb-page">
    <!-- Left: KB Management Panel -->
    <div class="kb-left">
      <div class="panel">
        <div class="panel-title">
          📚 知识库管理
          <el-button type="primary" size="small" @click="openCreateDialog">新建</el-button>
        </div>
        <div class="panel-body scroll-y" v-loading="kbLoading">
          <div v-if="kbs.length === 0 && !kbLoading" class="empty-hint">暂无知识库，点击"新建"创建</div>
          <div v-for="kb in kbs" :key="kb.id"
            :class="['kb-item', { active: selectedKb?.id === kb.id }]"
            @click="selectKb(kb)">
            <div class="kb-item-left">
              <span class="kb-name">{{ kb.name }}</span>
              <span class="kb-desc" v-if="kb.description">{{ kb.description }}</span>
            </div>
            <span class="kb-count">{{ kb.knowledge_count || 0 }} 文档</span>
          </div>
        </div>
      </div>

      <!-- Files Panel -->
      <div class="panel" v-if="selectedKb">
        <div class="panel-title">
          📄 {{ selectedKb.name }} - 文件列表
          <div class="panel-title-actions">
            <el-button type="primary" size="small" @click="openEditDialog(selectedKb)">编辑</el-button>
            <el-upload :auto-upload="false" :limit="5" accept=".pdf,.txt,.md,.doc,.docx"
              :on-change="e => (uploadFile = e.raw)" :show-file-list="false">
              <el-button size="small">上传</el-button>
            </el-upload>
            <el-button size="small" type="success" @click="openGenerateDialog">根据知识库出题</el-button>
            <el-button size="small" type="danger" @click="handleDeleteKb(selectedKb)">删除KB</el-button>
          </div>
        </div>
        <div class="upload-bar" v-if="uploadFile">
          <el-tag closable @close="uploadFile=null">{{ uploadFile.name }}</el-tag>
          <el-button type="success" size="small" :loading="uploading" @click="doUpload">确认上传</el-button>
        </div>
        <div class="panel-body scroll-y" v-loading="filesLoading">
          <div v-if="files.length === 0 && !filesLoading" class="empty-hint">暂无文件</div>
          <div v-for="f in files" :key="f.id" class="file-item">
            <span class="file-name">{{ f.title || f.file_name || f.id }}</span>
            <span class="file-meta">{{ f.file_type || f.type || '-' }} · {{ formatSize(f.file_size) }}</span>
            <el-button size="small" type="danger" :icon="Delete" circle @click.stop="handleDeleteFile(f)" />
          </div>
        </div>
      </div>
    </div>

    <!-- Right: AI Chat -->
    <div class="kb-right">
      <AiChatWidget :inline="true" :key="kbs.length + (selectedKb?.id || '')" />
    </div>

    <!-- Edit KB Dialog -->
    <el-dialog v-model="editVisible" title="编辑知识库" width="500px">
      <el-form :model="editForm" ref="editRef" label-width="80px" :rules="editRules">
        <el-form-item label="名称" prop="name">
          <el-input v-model="editForm.name" placeholder="知识库名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="简要描述" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- Create KB Dialog -->
    <el-dialog v-model="createVisible" title="新建知识库" width="500px">
      <el-form :model="createForm" :rules="createRules" ref="createRef" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="createForm.name" placeholder="知识库名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="简要描述（可选）" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- Generate Questions from Knowledge Base Dialog -->
    <el-dialog v-model="genVisible" title="根据知识库出题" width="900px" :close-on-click-modal="false" destroy-on-close>
      <!-- Step 1: Configure -->
      <div v-if="genStep === 1">
        <el-form label-width="100px">
          <el-form-item label="知识库">
            <el-tag type="info">{{ selectedKb?.name }}</el-tag>
          </el-form-item>
          <el-form-item label="选择课程" required>
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
      <div v-else-if="genStep === 2" style="text-align:center;padding:60px 20px">
        <el-icon class="is-loading" :size="48"><i class="el-icon-loading" /></el-icon>
        <p style="margin-top:16px;color:#606266">正在基于知识库生成题目，请稍候...</p>
      </div>

      <!-- Step 3: Preview & Edit -->
      <div v-else-if="genStep === 3">
        <el-alert v-if="generatedQuestions.length > 0"
          :title="`已生成 ${generatedQuestions.length} 道题，请在下方编辑后选择保存或跳过`"
          type="success" :closable="false" style="margin-bottom:16px" />
        <el-table :data="generatedQuestions" border stripe max-height="400" v-if="generatedQuestions.length > 0">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="题型" width="90">
            <template #default="{ row }">
              <el-tag :type="TYPE_TAG[row.type] || ''" size="small">{{ TYPE_LABEL[row.type] || row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="题干" show-overflow-tooltip />
          <el-table-column label="难度" width="80">
            <template #default="{ row }">{{ row.difficulty }}</template>
          </el-table-column>
          <el-table-column label="分值" width="70">
            <template #default="{ row }">{{ row.score }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ $index }">
              <el-button size="small" @click="openEditQuestion($index)">编辑</el-button>
              <el-button size="small" type="danger" @click="generatedQuestions.splice($index,1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="未生成任何题目" />
      </div>

      <!-- Step 4: Save Result -->
      <div v-else-if="genStep === 4" style="text-align:center;padding:40px">
        <el-result :icon="genResult.fail > 0 ? 'warning' : 'success'"
          :title="'保存完成'"
          :sub-title="`成功 ${genResult.success} 条，失败 ${genResult.fail} 条，共 ${genResult.total} 条`" />
      </div>

      <template #footer>
        <el-button @click="closeGenDialog">{{ genStep >= 4 ? '关闭' : '取消' }}</el-button>
        <el-button v-if="genStep === 1" type="primary" :disabled="!canGenerate" @click="startGenerate">开始生成</el-button>
        <el-button v-if="genStep === 3 && generatedQuestions.length > 0" type="primary" :loading="genSaving" @click="saveGenQuestions">保存到题库</el-button>
        <el-button v-if="genStep === 3 && generatedQuestions.length > 0" @click="skipGenSave">仅本次，不入题库</el-button>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import AiChatWidget from '@/components/AiChatWidget.vue'
import * as kbApi from '@/api/knowledgeBase'
import { generateQuestionsFromKnowledgeBase, batchCreateQuestions } from '@/api/exam'
import { getCourses } from '@/utils/course'
import { currentUserId, scopedTeacherParams, getRecords } from '@/utils/apiHelpers'
import { useUserStore } from '@/store/user'

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
const selectedKb = ref(null)
const files = ref([]); const filesLoading = ref(false)
const uploadFile = ref(null); const uploading = ref(false)

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

async function loadKBs() {
  kbLoading.value = true
  try {
    const res = await kbApi.listKnowledgeBases()
    if (res.code === '200') kbs.value = unwrapList(res.data)
  } finally { kbLoading.value = false }
}

function selectKb(kb) {
  selectedKb.value = kb
  uploadFile.value = null
  loadFiles()
}

async function loadFiles() {
  if (!selectedKb.value) return
  filesLoading.value = true
  try {
    const res = await kbApi.listKnowledge(selectedKb.value.id)
    if (res.code === '200') files.value = unwrapList(res.data)
  } finally { filesLoading.value = false }
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

const canGenerate = computed(() => genForm.courseId && genForm.count > 0 && genForm.types.length > 0)

async function openGenerateDialog() {
  genStep.value = 1
  genForm.courseId = null
  genForm.agentId = null
  genForm.count = 5
  genForm.difficulty = 'MEDIUM'
  genForm.types = ['SINGLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER']
  genForm.instruction = ''
  generatedQuestions.value = []
  genVisible.value = true

  try {
    const courseRes = await getCourses(scopedTeacherParams(userStore, { pageSize: 100 }))
    courses.value = getRecords(courseRes?.data) || []
  } catch {
    courses.value = []
  }
}

function closeGenDialog() {
  genVisible.value = false
}

async function startGenerate() {
  genStep.value = 2
  try {
    const res = await generateQuestionsFromKnowledgeBase({
      knowledgeBaseId: selectedKb.value?.id,
      courseId: genForm.courseId,
      agentId: genForm.agentId,
      count: genForm.count,
      types: genForm.types,
      difficulty: genForm.difficulty,
      instruction: genForm.instruction
    })
    if (res.code === '200' && res.data) {
      generatedQuestions.value = Array.isArray(res.data) ? res.data : (res.data?.items || res.data?.list || [])
      genStep.value = 3
    } else {
      ElMessage.error(res.msg || '生成题目失败')
      genStep.value = 1
    }
  } catch (e) {
    ElMessage.error('生成题目失败，请重试')
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
  editGenVisible.value = false
}

async function saveGenQuestions() {
  if (generatedQuestions.value.length === 0) return
  genSaving.value = true
  try {
    const res = await batchCreateQuestions(generatedQuestions.value)
    if (res.code === '200' && res.data) {
      Object.assign(genResult, {
        total: res.data.total,
        success: res.data.success,
        fail: res.data.fail
      })
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
  try { await ElMessageBox.confirm('当前题目不会保存到题库，关闭后将丢失，是否继续？', '确认', { type: 'warning' }) } catch { return }
  genVisible.value = false
}

onMounted(loadKBs)
</script>

<style scoped>
.teacher-kb-page { display: flex; gap: 16px; height: calc(100vh - 120px); }
.kb-left { width: 520px; display: flex; flex-direction: column; gap: 8px; overflow: hidden; flex-shrink: 0; }
.kb-right { flex: 1; min-width: 0; max-width: calc(100% - 536px); }
.panel { background: #fff; border-radius: 8px; border: 1px solid #ebeef5; overflow: hidden; display: flex; flex-direction: column; max-height: 50%; }
.panel:last-child { flex: 1; min-height: 0; }
.panel-title { padding: 10px 14px; font-weight: 600; font-size: 14px; color: #303133; border-bottom: 1px solid #ebeef5; display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-shrink: 0; }
.panel-title-actions { display: flex; gap: 6px; align-items: center; }
.panel-body.scroll-y { flex: 1; overflow-y: auto; padding: 8px; min-height: 0; }
.empty-hint { color: #c0c4cc; text-align: center; padding: 24px 0; font-size: 13px; }
.upload-bar { padding: 8px; display: flex; align-items: center; gap: 8px; border-bottom: 1px solid #ebeef5; }
.kb-item { padding: 10px 12px; border-radius: 6px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; transition: background .15s; }
.kb-item:hover { background: #f5f7fa; }
.kb-item.active { background: #ecf5ff; }
.kb-item-left { flex: 1; min-width: 0; }
.kb-name { font-size: 13px; font-weight: 500; color: #303133; display: block; }
.kb-desc { font-size: 11px; color: #909399; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 2px; }
.kb-count { font-size: 11px; color: #909399; background: #f0f0f0; padding: 2px 8px; border-radius: 10px; flex-shrink: 0; margin-left: 8px; }
.file-item { padding: 8px 0; display: flex; align-items: center; gap: 8px; border-bottom: 1px solid #f5f5f5; }
.file-item:last-child { border-bottom: none; }
.file-name { flex: 1; font-size: 13px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-meta { font-size: 11px; color: #b0b0b0; flex-shrink: 0; }
</style>
