<template>
  <div class="question-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>题库管理</span>
          <div class="header-actions">
            <el-button type="success" @click="openPdfDialog">PDF上传</el-button>
            <el-button type="primary" @click="handleCreate">新建题目</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm">
        <el-form-item label="题目类型">
          <el-select v-model="searchForm.questionType" placeholder="请选择" clearable>
            <el-option label="单选题" value="single_choice" />
            <el-option label="多选题" value="multiple_choice" />
            <el-option label="判断题" value="true_false" />
            <el-option label="填空题" value="fill_blank" />
            <el-option label="简答题" value="short_answer" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable>
            <el-option v-for="course in courses" :key="course.id" :label="course.title" :value="course.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="questions" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="questionText" label="题目内容" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            {{ getTypeLabel(row.questionType) }}
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="100" />
        <el-table-column prop="score" label="分值" width="80" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="loadQuestions"
        class="pagination-bar"
      />
    </el-card>

    <!-- Form Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑题目' : '新建题目'" width="700px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="form.courseId" placeholder="请选择课程">
            <el-option v-for="course in courses" :key="course.id" :label="course.title" :value="course.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目类型" prop="questionType">
          <el-select v-model="form.questionType" @change="handleTypeChange">
            <el-option label="单选题" value="single_choice" />
            <el-option label="多选题" value="multiple_choice" />
            <el-option label="判断题" value="true_false" />
            <el-option label="填空题" value="fill_blank" />
            <el-option label="简答题" value="short_answer" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目内容" prop="questionText">
          <el-input v-model="form.questionText" type="textarea" :rows="3" />
        </el-form-item>

        <!-- Options for choice questions -->
        <el-form-item v-if="['single_choice', 'multiple_choice', 'true_false'].includes(form.questionType)" label="选项">
          <div v-for="(opt, idx) in form.options" :key="idx" class="option-row">
            <el-input v-model="opt.text" :placeholder="`选项${String.fromCharCode(65 + idx)}`" class="option-input" />
            <el-button type="danger" size="small" @click="removeOption(idx)">删除</el-button>
          </div>
          <el-button size="small" @click="addOption">添加选项</el-button>
        </el-form-item>

        <el-form-item v-if="['single_choice', 'true_false'].includes(form.questionType)" label="正确答案" prop="correctAnswer">
          <el-select v-model="form.correctAnswer" placeholder="请选择正确答案">
            <el-option v-for="(opt, idx) in form.options" :key="idx" :label="opt.text" :value="idx" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="form.questionType === 'multiple_choice'" label="正确答案" prop="correctAnswers">
          <el-checkbox-group v-model="form.correctAnswers">
            <el-checkbox v-for="(opt, idx) in form.options" :key="idx" :label="idx">{{ opt.text }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item v-if="['fill_blank', 'short_answer'].includes(form.questionType)" label="参考答案">
          <el-input v-model="form.referenceAnswer" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item label="解析">
          <el-input v-model="form.explanation" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="form.difficulty">
            <el-option label="简单" value="easy" />
            <el-option label="中等" value="medium" />
            <el-option label="困难" value="hard" />
          </el-select>
        </el-form-item>

        <el-form-item label="分值" prop="score">
          <el-input-number v-model="form.score" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- PDF Upload Dialog -->
    <el-dialog v-model="pdfDialogVisible" title="PDF 上传提取题目" width="800px" :close-on-click-modal="false">
      <!-- Step 1: Upload -->
      <div v-if="pdfStep === 1">
        <el-form label-width="100px">
          <el-form-item label="选择课程" required>
            <el-select v-model="pdfCourseId" placeholder="请选择课程">
              <el-option v-for="course in courses" :key="course.id" :label="course.title" :value="course.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="上传PDF">
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :limit="1"
              accept=".pdf"
              drag
              :on-change="handlePdfChange"
              :on-remove="handlePdfRemove"
            >
              <el-icon class="el-icon--upload"><i class="el-icon-upload" /></el-icon>
              <div class="el-upload__text">拖拽PDF文件到此处或<em>点击上传</em></div>
              <template #tip>
                <div class="el-upload__tip">仅支持PDF文件，大小不超过50MB</div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 2: Extracting -->
      <div v-else-if="pdfStep === 2" class="extracting-wrap">
        <el-icon class="is-loading" :size="48"><i class="el-icon-loading" /></el-icon>
        <p class="extracting-text">正在使用 AI 解析文档，请稍候...</p>
      </div>

      <!-- Step 3: Preview -->
      <div v-else-if="pdfStep === 3">
        <div v-if="extractedQuestions.length > 0">
          <el-alert :title="`共提取 ${extractedQuestions.length} 道题目，请确认后保存`" type="success" :closable="false" style="margin-bottom:16px" />
          <el-table :data="extractedQuestions" border stripe max-height="400">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column label="题型" width="90">
              <template #default="{ row }">
                <el-tag :type="tagByType(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="题干" show-overflow-tooltip />
            <el-table-column label="难度" width="80">
              <template #default="{ row }">{{ row.difficulty || 'MEDIUM' }}</template>
            </el-table-column>
            <el-table-column label="分值" width="70">
              <template #default="{ row }">{{ row.score }}</template>
            </el-table-column>
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ $index }">
                <el-button size="small" type="danger" @click="extractedQuestions.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-empty v-else description="未提取到任何题目" />
      </div>

      <!-- Step 4: Save Result -->
      <div v-else-if="pdfStep === 4">
        <el-result
          :icon="batchResult.fail > 0 ? 'warning' : 'success'"
          :title="`保存完成`"
          :sub-title="`成功 ${batchResult.success} 条，失败 ${batchResult.fail} 条，共 ${batchResult.total} 条`"
        />
      </div>

      <template #footer>
        <el-button @click="closePdfDialog">{{ pdfStep >= 4 ? '关闭' : '取消' }}</el-button>
        <el-button v-if="pdfStep === 1" type="primary" :disabled="!canExtract" @click="startExtract">
          开始提取
        </el-button>
        <el-button v-if="pdfStep === 3 && extractedQuestions.length > 0" type="primary" :loading="batchSaving" @click="saveExtractedQuestions">
          确认保存到题库
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import { getCourses } from '@/utils/course'
import { currentUserId, enumName, getRecords, getTotal, scopedTeacherParams } from '@/utils/apiHelpers'
import request from '@/utils/request'
import { extractQuestions, batchCreateQuestions } from '@/api/exam'

const userStore = useUserStore()
const { confirmDelete } = useConfirm()
const formRef = ref(null)

const loading = ref(false)
const submitLoading = ref(false)
const deleteLoading = ref(false)
const questions = ref([])
const courses = ref([])

const searchForm = reactive({
  questionType: '',
  courseId: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)

const form = reactive({
  id: null,
  courseId: null,
  teacherId: currentUserId(userStore),
  questionType: 'single_choice',
  questionText: '',
  options: [{ text: '' }, { text: '' }],
  correctAnswer: null,
  correctAnswers: [],
  referenceAnswer: '',
  explanation: '',
  difficulty: 'medium',
  score: 5
})

// ---- PDF 上传相关 ----
const uploadRef = ref(null)
const pdfDialogVisible = ref(false)
const pdfStep = ref(1)
const pdfCourseId = ref(null)
const pdfFile = ref(null)
const extractedQuestions = ref([])
const batchSaving = ref(false)
const batchResult = reactive({ total: 0, success: 0, fail: 0 })

const canExtract = computed(() => pdfCourseId.value && pdfFile.value)
const TYPE_LABEL_MAP = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
  FILL_BLANK: '填空题',
  SHORT_ANSWER: '简答题'
}
const TYPE_TAG_MAP = {
  SINGLE_CHOICE: 'primary',
  MULTIPLE_CHOICE: 'success',
  TRUE_FALSE: 'warning',
  FILL_BLANK: 'info',
  SHORT_ANSWER: ''
}

const tagByType = (type) => TYPE_TAG_MAP[type] || ''
const typeLabel = (type) => TYPE_LABEL_MAP[type] || type

function openPdfDialog() {
  pdfStep.value = 1
  pdfCourseId.value = null
  pdfFile.value = null
  extractedQuestions.value = []
  pdfDialogVisible.value = true
  uploadRef.value?.clearFiles()
}

function closePdfDialog() {
  pdfDialogVisible.value = false
  if (pdfStep.value >= 4) {
    loadQuestions()
  }
}

function handlePdfChange(file) {
  pdfFile.value = file.raw
}

function handlePdfRemove() {
  pdfFile.value = null
}

async function startExtract() {
  if (!canExtract.value) return
  pdfStep.value = 2
  try {
    const formData = new FormData()
    formData.append('file', pdfFile.value)
    formData.append('courseId', pdfCourseId.value)
    const res = await extractQuestions(formData)
    if (res.code === '200' && res.data) {
      extractedQuestions.value = res.data
      pdfStep.value = 3
    } else {
      ElMessage.error(res.msg || '提取失败')
      pdfStep.value = 1
    }
  } catch (e) {
    ElMessage.error('提取失败，请重试')
    pdfStep.value = 1
  }
}

async function saveExtractedQuestions() {
  if (extractedQuestions.value.length === 0) return
  batchSaving.value = true
  try {
    const res = await batchCreateQuestions(extractedQuestions.value)
    if (res.code === '200' && res.data) {
      Object.assign(batchResult, {
        total: res.data.total,
        success: res.data.success,
        fail: res.data.fail
      })
      pdfStep.value = 4
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败，请重试')
  } finally {
    batchSaving.value = false
  }
}

const rules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  questionType: [{ required: true, message: '请选择题目类型', trigger: 'change' }],
  questionText: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  score: [{ required: true, message: '请输入分值', trigger: 'blur' }]
}

// ---- 类型/难度 映射 ----
const TYPE_TO_ENUM = {
  single_choice: 'SINGLE_CHOICE',
  multiple_choice: 'MULTIPLE_CHOICE',
  true_false: 'TRUE_FALSE',
  fill_blank: 'FILL_BLANK',
  short_answer: 'SHORT_ANSWER'
}
const ENUM_TO_TYPE = Object.fromEntries(
  Object.entries(TYPE_TO_ENUM).map(([k, v]) => [v, k])
)

const DIFFICULTY_TO_ENUM = {
  easy: 'EASY',
  medium: 'MEDIUM',
  hard: 'HARD'
}
const ENUM_TO_DIFFICULTY = Object.fromEntries(
  Object.entries(DIFFICULTY_TO_ENUM).map(([k, v]) => [v, k])
)

const getTypeLabel = (type) => {
  const map = {
    single_choice: '单选题',
    multiple_choice: '多选题',
    true_false: '判断题',
    fill_blank: '填空题',
    short_answer: '简答题'
  }
  return map[type] || map[ENUM_TO_TYPE[enumName(type)]] || type
}

// ---- 响应适配：将后端 QuestionResponseDTO 映射为前端表格行 ----
const mapQuestionForDisplay = (q) => ({
  id: q.id,
  questionText: q.content || q.title || '',
  questionType: ENUM_TO_TYPE[enumName(q.type)] || 'short_answer',
  difficulty: ENUM_TO_DIFFICULTY[enumName(q.difficulty)] || 'medium',
  score: q.score
})

// ---- 响应适配：将后端 QuestionResponseDTO 映射为前端表单数据 ----
const parseResponseToForm = (data) => {
  const type = ENUM_TO_TYPE[enumName(data.type)] || 'short_answer'
  const difficulty = ENUM_TO_DIFFICULTY[enumName(data.difficulty)] || 'medium'

  const formData = {
    id: data.id,
    courseId: data.courseId,
    teacherId: data.teacherId || currentUserId(userStore),
    questionType: type,
    questionText: data.content || '',
    options: [],
    correctAnswer: null,
    correctAnswers: [],
    referenceAnswer: '',
    explanation: data.analysis || '',
    difficulty: difficulty,
    score: data.score
  }

  // 解析选项
  if (data.options && data.options.length > 0) {
    formData.options = data.options.map(opt => ({ text: opt.optionContent || '' }))
  }

  // 解析答案
  if (data.answers && data.answers.length > 0) {
    if (type === 'single_choice' || type === 'true_false') {
      const key = data.answers[0].answerContent
      if (key && key.length === 1) {
        formData.correctAnswer = key.charCodeAt(0) - 65
      }
    } else if (type === 'multiple_choice') {
      formData.correctAnswers = data.answers.map(ans => {
        const key = ans.answerContent
        return key && key.length === 1 ? key.charCodeAt(0) - 65 : 0
      })
    } else {
      formData.referenceAnswer = data.answers[0].answerContent || ''
    }
  }

  return formData
}

// ---- 请求适配：将前端表单数据转换为 QuestionRequestDTO ----
const buildRequestDTO = () => {
  const type = form.questionType
  const selectedCourse = courses.value.find(course => course.id === form.courseId)
  const dto = {
    courseId: form.courseId ? Number(form.courseId) : null,
    teacherId: Number(selectedCourse?.teacherId || form.teacherId || currentUserId(userStore)),
    type: TYPE_TO_ENUM[type],
    content: form.questionText,
    difficulty: DIFFICULTY_TO_ENUM[form.difficulty],
    score: form.score,
    analysis: form.explanation || '',
    knowledgePoint: '',
    status: 'ACTIVE'
  }

  // 构建选项
  if (['single_choice', 'multiple_choice', 'true_false'].includes(type)) {
    dto.options = form.options
      .filter(opt => opt.text && opt.text.trim())
      .map((opt, idx) => ({
      optionKey: String.fromCharCode(65 + idx),
      optionContent: opt.text.trim(),
      sortOrder: idx + 1
    }))
  }

  // 构建答案
  if (type === 'single_choice' || type === 'true_false') {
    const idx = form.correctAnswer
    if (idx !== null && idx !== undefined) {
      dto.answers = [{
        answerContent: String.fromCharCode(65 + idx),
        sortOrder: 1
      }]
    } else {
      dto.answers = [{ answerContent: 'A', sortOrder: 1 }]
    }
  } else if (type === 'multiple_choice') {
    dto.answers = (form.correctAnswers || []).map((idx, i) => ({
      answerContent: String.fromCharCode(65 + idx),
      sortOrder: i + 1
    }))
    if (dto.answers.length === 0) {
      dto.answers = [{ answerContent: 'A', sortOrder: 1 }]
    }
  } else {
    dto.answers = [{
      answerContent: (form.referenceAnswer || '').trim() || '暂无参考答案',
      sortOrder: 1
    }]
  }

  return dto
}

// ---- 数据加载 ----
const loadQuestions = async () => {
  loading.value = true
  try {
    const params = scopedTeacherParams(userStore, {
      page: pagination.pageNum - 1,
      size: pagination.pageSize
    })
    if (searchForm.questionType) {
      params.type = TYPE_TO_ENUM[searchForm.questionType]
    }
    if (searchForm.courseId) {
      params.courseId = searchForm.courseId
    }
    const res = await request.get('/api/questions', { params })
    if (res.code === '200' && res.data) {
      const pageData = res.data
      questions.value = getRecords(pageData).map(mapQuestionForDisplay)
      pagination.total = getTotal(pageData)
    } else {
      questions.value = []
      pagination.total = 0
    }
  } catch (e) {
    questions.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const loadCourses = async () => {
  try {
    const res = await getCourses(scopedTeacherParams(userStore, { pageSize: 100 }))
    if (res.code === '200' && res.data) {
      courses.value = getRecords(res.data)
    } else {
      courses.value = []
    }
  } catch (e) {
    courses.value = []
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadQuestions()
}

const handleCreate = async () => {
  if (!courses.value || courses.value.length === 0) {
    await loadCourses()
  }
  if (!courses.value || courses.value.length === 0) {
    ElMessage.warning('暂无课程数据，请先创建课程后再来添加题目')
    return
  }
  Object.assign(form, {
    id: null,
    courseId: null,
    teacherId: currentUserId(userStore),
    questionType: 'single_choice',
    questionText: '',
    options: [{ text: '' }, { text: '' }],
    correctAnswer: null,
    correctAnswers: [],
    referenceAnswer: '',
    explanation: '',
    difficulty: 'medium',
    score: 5
  })
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  try {
    const res = await request.get(`/api/questions/${row.id}`)
    if (res.code === '200' && res.data) {
      Object.assign(form, parseResponseToForm(res.data))
      dialogVisible.value = true
    }
  } catch (e) {
    // 全局已提示
  }
}

const handleTypeChange = () => {
  if (form.questionType === 'true_false') {
    form.options = [{ text: '正确' }, { text: '错误' }]
  } else if (['single_choice', 'multiple_choice'].includes(form.questionType)) {
    form.options = [{ text: '' }, { text: '' }]
  }
  form.correctAnswer = null
  form.correctAnswers = []
}

const addOption = () => {
  form.options.push({ text: '' })
}

const removeOption = (idx) => {
  form.options.splice(idx, 1)
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  // 对选择题校验答案
  if (['single_choice', 'multiple_choice'].includes(form.questionType) &&
      form.options.filter(opt => opt.text && opt.text.trim()).length < 2) {
    ElMessage.warning('选择题至少需要两个有效选项')
    return
  }
  if (['single_choice', 'true_false'].includes(form.questionType) &&
      (form.correctAnswer === null || form.correctAnswer === undefined)) {
    ElMessage.warning('请选择正确答案')
    return
  }
  if (form.questionType === 'multiple_choice' &&
      (!form.correctAnswers || form.correctAnswers.length < 2)) {
    ElMessage.warning('多选题请至少选择两个正确答案')
    return
  }

  const data = buildRequestDTO()

  submitLoading.value = true
  try {
    let res
    if (form.id) {
      res = await request.put(`/api/questions/${form.id}`, data)
    } else {
      res = await request.post('/api/questions', data)
    }

    if (res.code === '200') {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadQuestions()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    const confirmed = await confirmDelete('确认删除此题目？')
    if (!confirmed) return
    deleteLoading.value = true
    const res = await request.delete(`/api/questions/${row.id}`)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadQuestions()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    deleteLoading.value = false
  }
}

const handleReset = () => {
  searchForm.questionType = ''
  searchForm.courseId = null
  pagination.pageNum = 1
  loadQuestions()
}

onMounted(() => {
  loadCourses()
  loadQuestions()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.pagination-bar {
  margin-top: 20px;
  justify-content: center;
}

.option-row {
  margin-bottom: 8px;
  display: flex;
  gap: 8px;
}

.option-input {
  flex: 1;
}

.extracting-wrap {
  text-align: center;
  padding: 60px 0;
}

.extracting-text {
  margin-top: 20px;
  font-size: 16px;
  color: #606266;
}
</style>
