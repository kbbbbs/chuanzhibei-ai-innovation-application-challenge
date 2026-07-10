<template>
  <div class="question-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>题库管理</span>
          <div class="header-actions">
            <el-button type="warning" @click="importDialogVisible = true">导入</el-button>
            <el-button :loading="exporting" @click="handleExport('xlsx')">导出Excel</el-button>
            <el-button :loading="exporting" @click="handleExport('csv')">导出CSV</el-button>
            <el-button type="primary" @click="handleCreate">新建题目</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="请输入题目内容" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="题目类型">
          <el-select v-model="searchForm.questionType" placeholder="请选择" clearable>
            <el-option label="单选题" value="single_choice" />
            <el-option label="多选题" value="multiple_choice" />
            <el-option label="判断题" value="true_false" />
            <el-option label="填空题" value="fill_blank" />
            <el-option label="简答题" value="short_answer" />
            <el-option label="文件上传题" value="file_upload" />
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
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadQuestions"
        @size-change="handleSizeChange"
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
            <el-option label="文件上传题" value="file_upload" />
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

        <el-form-item v-if="['single_choice', 'true_false'].includes(form.questionType)" label="正确答案" prop="correctAnswer" required>
          <el-select v-model="form.correctAnswer" placeholder="请选择正确答案">
            <el-option v-for="(opt, idx) in form.options" :key="idx" :label="opt.text" :value="idx" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="form.questionType === 'multiple_choice'" label="正确答案" prop="correctAnswers" required>
          <el-checkbox-group v-model="form.correctAnswers">
            <el-checkbox v-for="(opt, idx) in form.options" :key="idx" :label="idx">{{ opt.text }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item v-if="form.questionType === 'fill_blank'" label="正确答案" prop="referenceAnswer" required>
          <el-input v-model="form.referenceAnswer" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item v-if="form.questionType === 'short_answer'" label="参考答案">
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

      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <ImportDialog
      v-model="importDialogVisible"
      type="questions"
      title="题库导入"
      @imported="loadQuestions"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import ImportDialog from '@/components/ImportDialog.vue'
import { exportImportData } from '@/api/imports'
import { getCourses } from '@/utils/course'
import { currentUserId, enumName, getRecords, getTotal, scopedTeacherParams } from '@/utils/apiHelpers'
import request from '@/utils/request'

const userStore = useUserStore()
const { confirmDelete } = useConfirm()
const formRef = ref(null)

const loading = ref(false)
const submitLoading = ref(false)
const deleteLoading = ref(false)
const exporting = ref(false)
const questions = ref([])
const courses = ref([])

const searchForm = reactive({
  keyword: '',
  questionType: '',
  courseId: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)
const importDialogVisible = ref(false)

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
  difficulty: 'medium'
})

const rules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  questionType: [{ required: true, message: '请选择题目类型', trigger: 'change' }],
  questionText: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }]
}

// ---- 类型/难度 映射 ----
const TYPE_TO_ENUM = {
  single_choice: 'SINGLE_CHOICE',
  multiple_choice: 'MULTIPLE_CHOICE',
  true_false: 'TRUE_FALSE',
  fill_blank: 'FILL_BLANK',
  short_answer: 'SHORT_ANSWER',
  file_upload: 'FILE_UPLOAD'
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
    short_answer: '简答题',
    file_upload: '文件上传题'
  }
  return map[type] || map[ENUM_TO_TYPE[enumName(type)]] || type
}

// ---- 响应适配：将后端 QuestionResponseDTO 映射为前端表格行 ----
const mapQuestionForDisplay = (q) => ({
  id: q.id,
  questionText: q.content || q.title || '',
  questionType: ENUM_TO_TYPE[enumName(q.type)] || 'short_answer',
  difficulty: ENUM_TO_DIFFICULTY[enumName(q.difficulty)] || 'medium'
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
    difficulty: difficulty
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
    }
  } else if (type === 'multiple_choice') {
    dto.answers = (form.correctAnswers || []).map((idx, i) => ({
      answerContent: String.fromCharCode(65 + idx),
      sortOrder: i + 1
    }))
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
    if (searchForm.keyword && searchForm.keyword.trim()) {
      params.keyword = searchForm.keyword.trim()
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

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadQuestions()
}

const handleExport = async (format) => {
  exporting.value = true
  try {
    const params = scopedTeacherParams(userStore, { format })
    if (searchForm.questionType) {
      params.type = TYPE_TO_ENUM[searchForm.questionType]
    }
    if (searchForm.courseId) {
      params.courseId = searchForm.courseId
    }
    if (searchForm.keyword && searchForm.keyword.trim()) {
      params.keyword = searchForm.keyword.trim()
    }
    const blob = await exportImportData('questions', params)
    downloadBlob(blob, `题库_${new Date().toISOString().slice(0, 10)}.${format}`)
    ElMessage.success('导出成功')
  } catch (e) {
    // 全局已提示
  } finally {
    exporting.value = false
  }
}

const downloadBlob = (blob, fileName) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
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
    difficulty: 'medium'
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
  if (form.questionType === 'fill_blank' && !form.referenceAnswer?.trim()) {
    ElMessage.warning('请输入正确答案')
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
  searchForm.keyword = ''
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

</style>
