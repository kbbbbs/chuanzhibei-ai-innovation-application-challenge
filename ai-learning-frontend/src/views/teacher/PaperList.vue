<template>
  <div class="paper-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>试卷管理</span>
          <el-button type="primary" @click="handleCreate">创建试卷</el-button>
        </div>
      </template>

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
        layout="total, prev, pager, next"
        @current-change="loadPapers"
        class="pagination-bar"
      />
    </el-card>

    <!-- Form Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑试卷' : '创建试卷'" width="900px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
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

        <el-divider>组卷</el-divider>

        <div class="question-pool">
          <!-- Question Bank -->
          <div class="pool-side">
            <div class="pool-title">题库</div>
            <el-select v-model="questionTypeFilter" placeholder="筛选题型" class="question-filter">
              <el-option label="全部" value="" />
              <el-option label="单选题" value="single_choice" />
              <el-option label="多选题" value="multiple_choice" />
              <el-option label="判断题" value="true_false" />
              <el-option label="填空题" value="fill_blank" />
              <el-option label="简答题" value="short_answer" />
            </el-select>
            <el-table :data="filteredQuestions" border size="small" height="300" @row-click="addQuestionToPaper">
              <el-table-column prop="questionText" label="题目" show-overflow-tooltip />
              <el-table-column label="类型" width="80">
                <template #default="{ row }">
                  {{ getTypeShort(row.questionType) }}
                </template>
              </el-table-column>
              <el-table-column prop="score" label="分值" width="60" />
              <template #empty>
                <AppEmpty description="请先选择课程或题库为空" />
              </template>
            </el-table>
          </div>

          <!-- Selected Questions -->
          <div class="pool-side">
            <div class="pool-title">
              已选题目 ({{ form.selectedQuestions.length }}) 总分: {{ totalScore }}
            </div>
            <el-table :data="form.selectedQuestions" border size="small" height="350">
              <el-table-column prop="questionText" label="题目" show-overflow-tooltip />
              <el-table-column label="类型" width="80">
                <template #default="{ row }">
                  {{ getTypeShort(row.questionType) }}
                </template>
              </el-table-column>
              <el-table-column prop="score" label="分值" width="60" />
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
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
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
import { currentUserId, enumName, getRecords, getTotal, normalizeStatus, paginateRecords, scopedTeacherParams } from '@/utils/apiHelpers'
import request from '@/utils/request'

const userStore = useUserStore()
const { confirmDelete } = useConfirm()
const formRef = ref(null)

const loading = ref(false)
const submitLoading = ref(false)
const deleteLoading = ref(false)
const papers = ref([])
const courses = ref([])
const questionBank = ref([])
const questionTypeFilter = ref('')

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
  title: '',
  description: '',
  duration: 60,
  totalScore: 0,
  status: 0,
  selectedQuestions: []
})

const rules = {
  title: [{ required: true, message: '请输入试卷名称', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }]
}

// ---- 类型映射 ----
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

// ---- 状态映射 ----
const STATUS_TO_ENUM = { 0: 'DRAFT', 1: 'PUBLISHED' }
const ENUM_TO_STATUS = { DRAFT: 0, PUBLISHED: 1, ARCHIVED: 2 }

const filteredQuestions = computed(() => {
  if (!questionTypeFilter.value) return questionBank.value
  return questionBank.value.filter(q => q.questionType === questionTypeFilter.value)
})

const totalScore = computed(() => {
  return form.selectedQuestions.reduce((sum, q) => sum + (Number(q.score) || 0), 0)
})

const getTypeShort = (type) => {
  const map = {
    single_choice: '单选',
    multiple_choice: '多选',
    true_false: '判断',
    fill_blank: '填空',
    short_answer: '简答'
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
  questionType: ENUM_TO_TYPE[enumName(q.type)] || 'short_answer',
  score: q.score
})

// ---- 数据加载 ----
const loadPapers = async () => {
  loading.value = true
  try {
    const params = scopedTeacherParams(userStore)
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
    } else {
      questionBank.value = []
    }
  } catch (e) {
    questionBank.value = []
  }
}

const addQuestionToPaper = (row) => {
  if (!form.selectedQuestions.find(q => q.id === row.id)) {
    form.selectedQuestions.push({ ...row })
  }
}

const removeQuestion = (row) => {
  const idx = form.selectedQuestions.findIndex(q => q.id === row.id)
  if (idx > -1) {
    form.selectedQuestions.splice(idx, 1)
  }
}

const handleCreate = () => {
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
          paperQuestionId: pq.id,
          questionText: pq.questionContent || '',
          questionType: '',
          score: pq.score
        }))
      })
      await handleCourseChange(data.courseId, false)
      dialogVisible.value = true
    }
  } catch (e) {
    // 全局已提示
  }
}

// ---- 请求适配：将前端表单数据转换为 PaperRequestDTO ----
const buildRequestDTO = () => {
  const selectedCourse = courses.value.find(course => course.id === form.courseId)
  return {
    name: form.title,
    courseId: form.courseId ? Number(form.courseId) : null,
    teacherId: Number(selectedCourse?.teacherId || form.teacherId || currentUserId(userStore)),
    description: form.description || '',
    durationMinutes: form.duration,
    status: STATUS_TO_ENUM[form.status] || 'DRAFT',
    questions: form.selectedQuestions.map((q, idx) => ({
      questionId: q.id,
      sortOrder: idx + 1,
      score: Number(q.score || 5),
      requiredAnswer: true
    }))
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  if (form.selectedQuestions.length === 0) {
    ElMessage.warning('请至少选择一道题目')
    return
  }

  const data = buildRequestDTO()

  submitLoading.value = true
  try {
    let res
    if (form.id) {
      res = await request.put(`/api/papers/${form.id}`, data)
    } else {
      res = await request.post('/api/papers', data)
    }

    if (res.code === '200') {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadPapers()
    }
  } catch (e) {
    // 全局已提示
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
          questionText: pq.questionContent || '',
          score: pq.score
        }))
      })
      // 复用 buildRequestDTO 创建试卷
      const copyData = buildRequestDTO()
      const createRes = await request.post('/api/papers', copyData)
      if (createRes.code === '200') {
        ElMessage.success('试卷拷贝成功')
        loadPapers()
      }
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

.pagination-bar {
  margin-top: 20px;
  justify-content: center;
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
}

.question-filter {
  width: 100%;
  margin-bottom: 10px;
}
</style>
