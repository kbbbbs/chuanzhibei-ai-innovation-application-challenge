<template>
  <div class="paper-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>试卷管理</span>
          <div class="header-actions">
            <el-button type="warning" @click="importDialogVisible = true">导入</el-button>
            <el-button :loading="exporting" @click="handleExport('xlsx')">导出Excel</el-button>
            <el-button :loading="exporting" @click="handleExport('csv')">导出CSV</el-button>
            <el-button type="primary" @click="handleCreate">创建试卷</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm" class="search-form" @submit.prevent>
        <el-form-item label="试卷名称">
          <el-input v-model="searchForm.keyword" placeholder="请输入试卷名称" clearable @keyup.enter="handleSearch" />
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

      <el-table :data="papers" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="试卷名称" />
        <el-table-column prop="totalScore" label="总分" width="100" />
        <el-table-column prop="duration" label="时长(分钟)" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">启用</el-tag>
            <el-tag v-else type="info">草稿</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            <el-button size="small" type="success" @click="handleCopy(row)">拷贝</el-button>
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
        @current-change="loadPapers"
        @size-change="handleSizeChange"
        class="pagination-bar"
      />
    </el-card>

    <!-- Form Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑试卷' : '创建试卷'" width="980px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <div class="wizard-dialog">
          <div class="wizard-steps">
            <el-steps direction="vertical" :active="paperStep" finish-status="success">
              <el-step title="基本信息" description="课程与试卷设置" />
              <el-step title="选择题目" description="从题库组卷" />
              <el-step title="确认保存" description="检查后提交" />
            </el-steps>
          </div>

          <div class="wizard-content">
            <template v-if="paperStep === 0">
              <el-form-item label="试卷名称" prop="title">
                <el-input v-model="form.title" />
              </el-form-item>
              <el-form-item label="课程" prop="courseId">
                <el-select v-model="form.courseId" placeholder="请选择课程" @change="handleCourseChange">
                  <el-option v-for="course in courses" :key="course.id" :label="course.title" :value="course.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="试卷说明">
                <el-input v-model="form.description" type="textarea" :rows="2" />
              </el-form-item>
              <el-form-item label="考试时长(分)">
                <el-input-number v-model="form.duration" :min="10" />
              </el-form-item>
              <el-form-item label="状态">
                <el-radio-group v-model="form.status">
                  <el-radio :label="0">草稿</el-radio>
                  <el-radio :label="1">启用</el-radio>
                </el-radio-group>
              </el-form-item>
            </template>

            <template v-else-if="paperStep === 1">
              <div class="question-pool">
                <div class="pool-side">
                  <div class="pool-title">题库</div>
                  <el-select v-model="questionTypeFilter" placeholder="筛选题型" class="question-filter">
                    <el-option label="全部" value="" />
                    <el-option label="单选题" value="single_choice" />
                    <el-option label="多选题" value="multiple_choice" />
                    <el-option label="判断题" value="true_false" />
                    <el-option label="填空题" value="fill_blank" />
                    <el-option label="简答题" value="short_answer" />
                    <el-option label="文件上传题" value="file_upload" />
                  </el-select>
                  <el-table :data="filteredQuestions" border size="small" height="300" @row-click="addQuestionToPaper">
                    <el-table-column prop="questionText" label="题目" show-overflow-tooltip />
                    <el-table-column label="类型" width="80">
                      <template #default="{ row }">
                        {{ getTypeShort(row.questionType) }}
                      </template>
                    </el-table-column>
                    <template #empty>
                      <AppEmpty description="请先选择课程或题库为空" />
                    </template>
                  </el-table>
                </div>

                <div class="pool-side">
                  <div class="pool-title">
                    已选题目 ({{ form.selectedQuestions.length }}) 总分: {{ totalScore }}
                    <el-button size="small" type="primary" :disabled="!form.courseId" @click="openCustomQuestionDialog">
                      添加自定义题目
                    </el-button>
                  </div>
                  <el-table :data="form.selectedQuestions" border size="small" height="350">
                    <el-table-column prop="questionText" label="题目" show-overflow-tooltip />
                    <el-table-column label="类型" width="80">
                      <template #default="{ row }">
                        {{ getTypeShort(row.questionType) }}
                      </template>
                    </el-table-column>
                    <el-table-column label="分值" width="132">
                      <template #default="{ row }">
                        <el-input-number v-model="row.score" :min="0.01" :precision="2" :step="1" controls-position="right" />
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="80">
                      <template #default="{ row }">
                        <el-button size="small" type="danger" @click="removeQuestion(row)">移除</el-button>
                      </template>
                    </el-table-column>
                    <template #empty>
                      <AppEmpty description="点击左侧题目添加到试卷" />
                    </template>
                  </el-table>
                </div>
              </div>
            </template>

            <template v-else>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="试卷名称">{{ form.title || '-' }}</el-descriptions-item>
                <el-descriptions-item label="课程">{{ selectedCourseTitle }}</el-descriptions-item>
                <el-descriptions-item label="时长">{{ form.duration }} 分钟</el-descriptions-item>
                <el-descriptions-item label="状态">{{ form.status === 1 ? '启用' : '草稿' }}</el-descriptions-item>
                <el-descriptions-item label="题目数量">{{ form.selectedQuestions.length }}</el-descriptions-item>
                <el-descriptions-item label="总分">{{ totalScore }}</el-descriptions-item>
                <el-descriptions-item label="试卷说明" :span="2">{{ form.description || '-' }}</el-descriptions-item>
              </el-descriptions>
            </template>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="paperStep > 0" @click="paperStep -= 1">上一步</el-button>
        <el-button v-if="paperStep < 2" type="primary" @click="nextPaperStep">下一步</el-button>
        <el-button v-else type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="customQuestionDialogVisible" title="添加自定义题目" width="760px" append-to-body>
      <el-form ref="customQuestionFormRef" :model="customQuestion" :rules="customQuestionRules" label-width="100px">
        <el-form-item label="题目类型" prop="questionType">
          <el-select v-model="customQuestion.questionType" @change="handleCustomQuestionTypeChange">
            <el-option label="单选题" value="single_choice" />
            <el-option label="多选题" value="multiple_choice" />
            <el-option label="判断题" value="true_false" />
            <el-option label="填空题" value="fill_blank" />
            <el-option label="简答题" value="short_answer" />
            <el-option label="文件上传题" value="file_upload" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目内容" prop="questionText">
          <el-input v-model="customQuestion.questionText" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item v-if="hasCustomQuestionOptions" label="选项">
          <div v-for="(option, index) in customQuestion.options" :key="index" class="option-row">
            <el-input v-model="option.text" :placeholder="`选项${String.fromCharCode(65 + index)}`" />
            <el-button size="small" type="danger" @click="removeCustomQuestionOption(index)">删除</el-button>
          </div>
          <el-button size="small" @click="addCustomQuestionOption">添加选项</el-button>
        </el-form-item>
        <el-form-item v-if="['single_choice', 'true_false'].includes(customQuestion.questionType)" label="正确答案" prop="correctAnswer">
          <el-select v-model="customQuestion.correctAnswer" placeholder="请选择正确答案">
            <el-option v-for="(option, index) in customQuestion.options" :key="index" :label="option.text" :value="index" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="customQuestion.questionType === 'multiple_choice'" label="正确答案" prop="correctAnswers">
          <el-checkbox-group v-model="customQuestion.correctAnswers">
            <el-checkbox v-for="(option, index) in customQuestion.options" :key="index" :label="index">{{ option.text }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item v-if="['fill_blank', 'short_answer'].includes(customQuestion.questionType)" label="参考答案">
          <el-input v-model="customQuestion.referenceAnswer" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="知识点">
          <el-input v-model="customQuestion.knowledgePoint" />
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="customQuestion.explanation" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="customQuestion.difficulty">
            <el-option label="简单" value="easy" />
            <el-option label="中等" value="medium" />
            <el-option label="困难" value="hard" />
          </el-select>
        </el-form-item>
        <el-form-item label="本卷分值" prop="score">
          <el-input-number v-model="customQuestion.score" :min="0.01" :precision="2" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="customQuestionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="addCustomQuestionToPaper">添加到试卷</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="warningDialogVisible" title="组卷检查" width="520px" append-to-body :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon title="以下问题不会阻止出卷，但需要明确确认。" />
      <ul class="preflight-warning-list">
        <li v-for="warning in preflightWarnings" :key="warning.code || warning">{{ formatPreflightWarning(warning) }}</li>
      </ul>
      <template #footer>
        <el-button @click="warningDialogVisible = false">返回修改</el-button>
        <el-button type="warning" :loading="submitLoading" @click="savePaper(true)">忽略问题并保存</el-button>
      </template>
    </el-dialog>

    <ImportDialog
      v-model="importDialogVisible"
      type="papers"
      title="试卷导入"
      @imported="loadPapers"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import ImportDialog from '@/components/ImportDialog.vue'
import { exportImportData } from '@/api/imports'
import { getCourses } from '@/utils/course'
import { currentUserId, enumName, getRecords, getTotal, normalizeStatus, paginateRecords, scopedTeacherParams } from '@/utils/apiHelpers'
import request from '@/utils/request'

const userStore = useUserStore()
const { confirmDelete } = useConfirm()
const formRef = ref(null)
const customQuestionFormRef = ref(null)

const loading = ref(false)
const submitLoading = ref(false)
const deleteLoading = ref(false)
const exporting = ref(false)
const papers = ref([])
const courses = ref([])
const questionBank = ref([])
const questionTypeFilter = ref('')

const searchForm = reactive({
  keyword: '',
  courseId: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)
const importDialogVisible = ref(false)
const paperStep = ref(0)
const customQuestionDialogVisible = ref(false)
const warningDialogVisible = ref(false)
const preflightWarnings = ref([])
const preflightSummary = ref(null)
let customQuestionSequence = 0

const form = reactive({
  id: null,
  courseId: null,
  teacherId: currentUserId(userStore),
  title: '',
  description: '',
  duration: 60,
  totalScore: 0,
  status: 0,
  selectedQuestions: []
})

const customQuestion = reactive({
  questionType: 'single_choice',
  questionText: '',
  options: [{ text: '' }, { text: '' }],
  correctAnswer: null,
  correctAnswers: [],
  referenceAnswer: '',
  knowledgePoint: '',
  explanation: '',
  difficulty: 'medium',
  score: 5
})

const rules = {
  title: [{ required: true, message: '请输入试卷名称', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }]
}

const customQuestionRules = {
  questionType: [{ required: true, message: '请选择题目类型', trigger: 'change' }],
  questionText: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  score: [{ required: true, message: '请输入本卷分值', trigger: 'blur' }]
}

// ---- 类型映射 ----
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

// ---- 状态映射 ----
const STATUS_TO_ENUM = { 0: 'DRAFT', 1: 'PUBLISHED' }
const ENUM_TO_STATUS = { DRAFT: 0, PUBLISHED: 1, ARCHIVED: 2 }

const filteredQuestions = computed(() => {
  if (!questionTypeFilter.value) return questionBank.value
  return questionBank.value.filter(q => q.questionType === questionTypeFilter.value)
})

const hasCustomQuestionOptions = computed(() => ['single_choice', 'multiple_choice', 'true_false'].includes(customQuestion.questionType))

const totalScore = computed(() => {
  return form.selectedQuestions.reduce((sum, q) => sum + (Number(q.score) || 0), 0)
})

const selectedCourseTitle = computed(() => {
  return courses.value.find(course => course.id === form.courseId)?.title || '-'
})

const getTypeShort = (type) => {
  const map = {
    single_choice: '单选',
    multiple_choice: '多选',
    true_false: '判断',
    fill_blank: '填空',
    short_answer: '简答',
    file_upload: '文件'
  }
  return map[type] || type || '-'
}

// ---- 响应适配：将后端 PaperResponseDTO 映射为前端表格行 ----
const mapPaperForDisplay = (p) => ({
  id: p.id,
  title: p.name || p.title || '',
  totalScore: p.totalScore,
  duration: p.durationMinutes ?? p.duration ?? 0,
  status: normalizeStatus(p.status, ENUM_TO_STATUS, 0),
  teacherId: p.teacherId,
  courseId: p.courseId
})

// ---- 响应适配：将后端 QuestionResponseDTO 映射为题库行 ----
const mapQuestionForBank = (q) => ({
  id: q.id,
  questionText: q.content || q.title || '',
  questionType: ENUM_TO_TYPE[enumName(q.type)] || 'short_answer'
})

// ---- 数据加载 ----
const loadPapers = async () => {
  loading.value = true
  try {
    const params = scopedTeacherParams(userStore)
    if (searchForm.keyword && searchForm.keyword.trim()) {
      params.keyword = searchForm.keyword.trim()
    }
    if (searchForm.courseId) {
      params.courseId = searchForm.courseId
    }
    const res = await request.get('/api/papers', { params })
    if (res.code === '200' && res.data) {
      const records = getRecords(res.data).map(mapPaperForDisplay)
      papers.value = paginateRecords(records, pagination.pageNum, pagination.pageSize)
      pagination.total = getTotal(records)
    } else {
      papers.value = []
      pagination.total = 0
    }
  } catch (e) {
    papers.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadPapers()
}

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadPapers()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.courseId = null
  pagination.pageNum = 1
  loadPapers()
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

const handleExport = async (format) => {
  exporting.value = true
  try {
    const params = scopedTeacherParams(userStore, { format })
    if (searchForm.keyword && searchForm.keyword.trim()) {
      params.keyword = searchForm.keyword.trim()
    }
    if (searchForm.courseId) {
      params.courseId = searchForm.courseId
    }
    const blob = await exportImportData('papers', params)
    downloadBlob(blob, `试卷_${new Date().toISOString().slice(0, 10)}.${format}`)
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

const handleCourseChange = async (courseId, resetSelected = true) => {
  if (resetSelected) {
    form.selectedQuestions = []
    questionTypeFilter.value = ''
  }
  if (!courseId) {
    questionBank.value = []
    return
  }
  try {
    const res = await request.get('/api/questions', {
      params: { courseId, page: 0, size: 200 }
    })
    if (res.code === '200' && res.data) {
      const pageData = res.data
      questionBank.value = getRecords(pageData).map(mapQuestionForBank)
      const questionsById = new Map(questionBank.value.map(question => [question.id, question]))
      form.selectedQuestions.forEach(question => {
        const bankQuestion = questionsById.get(question.id)
        if (bankQuestion) {
          question.questionText = bankQuestion.questionText
          question.questionType = bankQuestion.questionType
        }
      })
    } else {
      questionBank.value = []
    }
  } catch (e) {
    questionBank.value = []
  }
}

const addQuestionToPaper = (row) => {
  if (!form.selectedQuestions.find(q => q.id === row.id)) {
    form.selectedQuestions.push({
      ...row,
      selectionKey: `question-${row.id}`,
      score: null
    })
  }
}

const removeQuestion = (row) => {
  const idx = form.selectedQuestions.findIndex(q => q.selectionKey === row.selectionKey)
  if (idx > -1) {
    form.selectedQuestions.splice(idx, 1)
  }
}

const resetCustomQuestion = () => {
  Object.assign(customQuestion, {
    questionType: 'single_choice',
    questionText: '',
    options: [{ text: '' }, { text: '' }],
    correctAnswer: null,
    correctAnswers: [],
    referenceAnswer: '',
    knowledgePoint: '',
    explanation: '',
    difficulty: 'medium',
    score: 5
  })
  customQuestionFormRef.value?.clearValidate()
}

const openCustomQuestionDialog = () => {
  resetCustomQuestion()
  customQuestionDialogVisible.value = true
}

const handleCustomQuestionTypeChange = () => {
  if (customQuestion.questionType === 'true_false') {
    customQuestion.options = [{ text: '正确' }, { text: '错误' }]
  } else if (['single_choice', 'multiple_choice'].includes(customQuestion.questionType)) {
    customQuestion.options = [{ text: '' }, { text: '' }]
  } else {
    customQuestion.options = []
  }
  customQuestion.correctAnswer = null
  customQuestion.correctAnswers = []
}

const addCustomQuestionOption = () => {
  customQuestion.options.push({ text: '' })
}

const removeCustomQuestionOption = (index) => {
  customQuestion.options.splice(index, 1)
}

const buildInlineQuestion = () => {
  const type = customQuestion.questionType
  const options = hasCustomQuestionOptions.value
    ? customQuestion.options
      .filter(option => option.text?.trim())
      .map((option, index) => ({
        optionKey: String.fromCharCode(65 + index),
        optionContent: option.text.trim(),
        sortOrder: index + 1
      }))
    : []

  let answers
  if (type === 'single_choice' || type === 'true_false') {
    if (customQuestion.correctAnswer === null || customQuestion.correctAnswer === undefined) {
      return null
    }
    answers = [{
      answerContent: String.fromCharCode(65 + customQuestion.correctAnswer),
      sortOrder: 1
    }]
  } else if (type === 'multiple_choice') {
    if (customQuestion.correctAnswers.length < 2) {
      return null
    }
    answers = customQuestion.correctAnswers.map((index, answerIndex) => ({
      answerContent: String.fromCharCode(65 + index),
      sortOrder: answerIndex + 1
    }))
  } else {
    answers = [{
      answerContent: customQuestion.referenceAnswer.trim() || '暂无参考答案',
      sortOrder: 1
    }]
  }

  if (hasCustomQuestionOptions.value && options.length < 2) {
    return null
  }

  return {
    type: TYPE_TO_ENUM[type],
    content: customQuestion.questionText.trim(),
    difficulty: DIFFICULTY_TO_ENUM[customQuestion.difficulty],
    knowledgePoint: customQuestion.knowledgePoint.trim(),
    analysis: customQuestion.explanation.trim(),
    status: 'ACTIVE',
    options,
    answers
  }
}

const addCustomQuestionToPaper = async () => {
  try {
    await customQuestionFormRef.value.validate()
  } catch (e) {
    return
  }

  const inlineQuestion = buildInlineQuestion()
  if (!inlineQuestion) {
    ElMessage.warning('请补全有效选项和正确答案')
    return
  }

  customQuestionSequence += 1
  form.selectedQuestions.push({
    selectionKey: `custom-${customQuestionSequence}`,
    questionText: customQuestion.questionText.trim(),
    questionType: customQuestion.questionType,
    score: Number(customQuestion.score),
    inlineQuestion
  })
  customQuestionDialogVisible.value = false
}

const handleCreate = async () => {
  if (!courses.value || courses.value.length === 0) {
    await loadCourses()
  }
  if (!courses.value || courses.value.length === 0) {
    ElMessage.warning('暂无课程数据，请先创建课程后再来创建试卷')
    return
  }
  Object.assign(form, {
    id: null,
    courseId: null,
    teacherId: currentUserId(userStore),
    title: '',
    description: '',
    duration: 60,
    totalScore: 0,
    status: 0,
    selectedQuestions: []
  })
  questionBank.value = []
  paperStep.value = 0
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  if (row.status === 1) {
    ElMessage.warning('已发布试卷不能直接修改，请拷贝试卷副本')
    return
  }
  try {
    const res = await request.get(`/api/papers/${row.id}`)
    if (res.code === '200' && res.data) {
      const data = res.data
      Object.assign(form, {
        id: data.id,
        courseId: data.courseId,
        teacherId: data.teacherId || currentUserId(userStore),
        title: data.name || '',
        description: data.description || '',
        duration: data.durationMinutes ?? 60,
        totalScore: data.totalScore ?? 0,
        status: normalizeStatus(data.status, ENUM_TO_STATUS, 0),
        selectedQuestions: (data.paperQuestions || []).map(pq => ({
          id: pq.questionId,
          selectionKey: `question-${pq.questionId}`,
          paperQuestionId: pq.id,
          questionText: pq.questionContent || '',
          questionType: '',
          score: pq.score
        }))
      })
      await handleCourseChange(data.courseId, false)
      paperStep.value = 0
      dialogVisible.value = true
    }
  } catch (e) {
    // 全局已提示
  }
}

const validatePaperBasics = () => {
  if (!form.title) {
    ElMessage.warning('请输入试卷名称')
    return false
  }
  if (!form.courseId) {
    ElMessage.warning('请选择课程')
    return false
  }
  return true
}

const nextPaperStep = async () => {
  if (paperStep.value === 0) {
    try {
      await formRef.value.validateField(['title', 'courseId'])
    } catch (e) {
      return
    }
    paperStep.value = 1
    return
  }

  if (form.selectedQuestions.length === 0) {
    ElMessage.warning('请至少选择一道题目')
    return
  }
  paperStep.value = 2
}

const validateComposition = () => {
  if (form.selectedQuestions.length === 0) {
    ElMessage.warning('请至少选择一道题目')
    return false
  }
  if (form.selectedQuestions.some(question => !Number.isFinite(Number(question.score)) || Number(question.score) <= 0)) {
    ElMessage.warning('请为每道题设置大于 0 的分值')
    return false
  }
  return true
}

// ---- 请求适配：将前端表单数据转换为 PaperRequestDTO ----
const buildRequestDTO = (ignoreWarnings = false) => {
  const selectedCourse = courses.value.find(course => course.id === form.courseId)
  return {
    name: form.title,
    courseId: form.courseId ? Number(form.courseId) : null,
    teacherId: Number(selectedCourse?.teacherId || form.teacherId || currentUserId(userStore)),
    description: form.description || '',
    durationMinutes: form.duration,
    status: STATUS_TO_ENUM[form.status] || 'DRAFT',
    ignoreWarnings,
    questions: form.selectedQuestions.map((question, index) => ({
      ...(question.inlineQuestion ? { inlineQuestion: question.inlineQuestion } : { questionId: question.id }),
      sortOrder: index + 1,
      score: Number(question.score),
      requiredAnswer: true
    }))
  }
}

const extractPreflightWarnings = (payload) => {
  const diagnostics = payload?.data || payload || {}
  const warnings = diagnostics.warnings || diagnostics.warningCodes || []
  if (warnings.length > 0) {
    return { warnings, diagnostics }
  }

  const derivedWarnings = []
  if (diagnostics.totalScore !== undefined && Number(diagnostics.totalScore) !== 100) {
    derivedWarnings.push({ code: 'TOTAL_NOT_100', totalScore: diagnostics.totalScore })
  }
  if (Array.isArray(diagnostics.missingTypes) && diagnostics.missingTypes.length > 0) {
    derivedWarnings.push({ code: 'MISSING_TYPES', missingTypes: diagnostics.missingTypes })
  }
  return { warnings: derivedWarnings, diagnostics }
}

const formatPreflightWarning = (warning) => {
  if (typeof warning === 'object' && warning.message) return warning.message
  const code = typeof warning === 'string' ? warning : warning.code
  if (code === 'TOTAL_NOT_100') {
    const score = warning.totalScore ?? preflightSummary.value?.totalScore ?? totalScore.value
    return `试卷总分为 ${score} 分，不是 100 分`
  }
  if (code === 'MISSING_TYPES') {
    const missingTypes = warning.missingTypes || preflightSummary.value?.missingTypes || []
    const labels = missingTypes.map(type => getTypeShort(ENUM_TO_TYPE[enumName(type)] || type))
    return `缺少题型：${labels.join('、') || '未指定'}`
  }
  return String(code || '存在需要确认的组卷问题')
}

const showPreflightWarnings = (payload) => {
  const { warnings, diagnostics } = extractPreflightWarnings(payload)
  preflightWarnings.value = warnings
  preflightSummary.value = diagnostics
  warningDialogVisible.value = warnings.length > 0
  return warnings.length > 0
}

const errorMessage = (error, fallback) => {
  const responseData = error?.response?.data
  const data = responseData?.data || responseData
  return data?.msg || data?.message || responseData?.msg || responseData?.message || error?.message || fallback
}

const runPreflight = async () => {
  const response = await request.post('/api/papers/preflight', buildRequestDTO(), { silent: true })
  if (response?.code !== '200') {
    throw new Error(response?.msg || '组卷检查失败')
  }
  return response
}

const handleSubmit = async () => {
  if (!validatePaperBasics()) {
    return
  }

  if (!validateComposition()) {
    return
  }

  submitLoading.value = true
  try {
    const preflight = await runPreflight()
    if (showPreflightWarnings(preflight)) {
      return
    }
    await savePaper(false)
  } catch (error) {
    const warningPayload = error?.response?.data
    if (showPreflightWarnings(warningPayload)) {
      return
    }
    ElMessage.error(errorMessage(error, '组卷检查失败，请稍后重试'))
  } finally {
    submitLoading.value = false
  }
}

const savePaper = async (ignoreWarnings) => {
  const data = buildRequestDTO(ignoreWarnings)
  warningDialogVisible.value = false
  submitLoading.value = true
  try {
    let res
    if (form.id) {
      res = await request.put(`/api/papers/${form.id}`, data, { silent: true })
    } else {
      res = await request.post('/api/papers', data, { silent: true })
    }

    if (res.code === '200') {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadPapers()
      return
    }
    if (!ignoreWarnings && showPreflightWarnings(res)) return
    ElMessage.error(res?.msg || '保存失败')
  } catch (e) {
    if (!ignoreWarnings && showPreflightWarnings(e?.response?.data)) return
    ElMessage.error(errorMessage(e, '保存失败，请稍后重试'))
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  if (row.status === 1) {
    ElMessage.warning('已发布试卷不能删除，请先停止相关考试或改为草稿后再删除')
    return
  }
  try {
    const confirmed = await confirmDelete('确认删除此试卷？')
    if (!confirmed) return
    deleteLoading.value = true
    const res = await request.delete(`/api/papers/${row.id}`)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadPapers()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    deleteLoading.value = false
  }
}

const handleCopy = async (row) => {
  try {
    const res = await request.get(`/api/papers/${row.id}`)
    if (res.code === '200' && res.data) {
      const data = res.data
      // 填充表单（标题加副本后缀，状态设为草稿）
      Object.assign(form, {
        id: null,
        courseId: data.courseId,
        teacherId: data.teacherId || currentUserId(userStore),
        title: (data.name || '') + ' - 副本',
        description: data.description || '',
        duration: data.durationMinutes ?? 60,
        totalScore: data.totalScore ?? 0,
        status: 0,
        selectedQuestions: (data.paperQuestions || []).map(pq => ({
          id: pq.questionId,
          selectionKey: `question-${pq.questionId}`,
          questionText: pq.questionContent || '',
          score: pq.score
        }))
      })
      if (!validateComposition()) {
        return
      }
      const preflight = await runPreflight()
      if (showPreflightWarnings(preflight)) {
        return
      }
      await savePaper(false)
    }
  } catch (e) {
    // 全局已提示
  }
}

onMounted(() => {
  loadCourses()
  loadPapers()
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

.search-form {
  margin-bottom: 16px;
}

.question-pool {
  display: flex;
  gap: 20px;
}

.pool-side {
  flex: 1;
}

.pool-title {
  font-weight: bold;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.question-filter {
  width: 100%;
  margin-bottom: 10px;
}

.wizard-dialog {
  display: flex;
  gap: 24px;
  min-height: 420px;
}

.wizard-steps {
  width: 170px;
  padding: 8px 8px 8px 0;
  border-right: 1px solid #ebeef5;
}

.wizard-content {
  flex: 1;
  min-width: 0;
}

.option-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.preflight-warning-list {
  margin: 16px 0 0;
  padding-left: 24px;
  line-height: 1.8;
}
</style>
