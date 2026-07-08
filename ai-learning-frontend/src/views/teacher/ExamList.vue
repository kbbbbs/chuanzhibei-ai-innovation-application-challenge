<template>
  <div class="exam-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>考试管理</span>
          <div class="header-actions">
            <el-button type="warning" @click="importDialogVisible = true">Excel导入</el-button>
            <el-button type="primary" @click="handleCreate">发布考试</el-button>
          </div>
        </div>
      </template>

      <el-table :data="exams" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="考试名称" />
        <el-table-column label="开始时间" width="180">
          <template #default="{ row }">
            {{ row.startTime ? new Date(row.startTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="结束时间" width="180">
          <template #default="{ row }">
            {{ row.endTime ? new Date(row.endTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">进行中</el-tag>
            <el-tag v-else-if="row.status === 2" type="info">已结束</el-tag>
            <el-tag v-else type="warning">未开始</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleGrading(row)">批改</el-button>
            <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 1" size="small" type="danger" @click="handleEnd(row)">结束</el-button>
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
        @current-change="loadExams"
        class="pagination-bar"
      />
    </el-card>

    <!-- Form Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑考试' : '发布考试'" width="760px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <div class="wizard-dialog">
          <div class="wizard-steps">
            <el-steps direction="vertical" :active="examStep" finish-status="success">
              <el-step title="发布对象" description="试卷与教学班" />
              <el-step title="考试设置" description="时间与说明" />
              <el-step title="确认发布" description="检查后提交" />
            </el-steps>
          </div>

          <div class="wizard-content">
            <template v-if="examStep === 0">
              <el-form-item label="考试名称" prop="title">
                <el-input v-model="form.title" />
              </el-form-item>
              <el-form-item label="试卷" prop="paperId">
                <el-select v-model="form.paperId" placeholder="请选择试卷">
                  <el-option v-for="paper in papers" :key="paper.id" :label="paper.title" :value="paper.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="教学班" prop="classId">
                <el-select v-model="form.classId" placeholder="请选择教学班">
                  <el-option v-for="cls in classList" :key="cls.id" :label="cls.className" :value="cls.id" />
                </el-select>
              </el-form-item>
            </template>

            <template v-else-if="examStep === 1">
              <el-form-item label="考试时长(分)" prop="durationMinutes">
                <el-input-number v-model="form.durationMinutes" :min="1" />
              </el-form-item>
              <el-form-item label="开始时间" prop="startTime">
                <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" />
              </el-form-item>
              <el-form-item label="结束时间" prop="endTime">
                <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" />
              </el-form-item>
              <el-form-item label="考试说明">
                <el-input v-model="form.description" type="textarea" :rows="3" />
              </el-form-item>
              <el-form-item label="状态">
                <el-radio-group v-model="form.status">
                  <el-radio :label="0">未开始</el-radio>
                  <el-radio :label="1">进行中</el-radio>
                </el-radio-group>
              </el-form-item>
            </template>

            <template v-else>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="考试名称">{{ form.title || '-' }}</el-descriptions-item>
                <el-descriptions-item label="试卷">{{ selectedPaperTitle }}</el-descriptions-item>
                <el-descriptions-item label="教学班">{{ selectedClassName }}</el-descriptions-item>
                <el-descriptions-item label="时长">{{ form.durationMinutes }} 分钟</el-descriptions-item>
                <el-descriptions-item label="开始时间">{{ form.startTime ? new Date(form.startTime).toLocaleString() : '-' }}</el-descriptions-item>
                <el-descriptions-item label="结束时间">{{ form.endTime ? new Date(form.endTime).toLocaleString() : '-' }}</el-descriptions-item>
                <el-descriptions-item label="状态">{{ form.status === 1 ? '进行中' : '未开始' }}</el-descriptions-item>
                <el-descriptions-item label="考试说明">{{ form.description || '-' }}</el-descriptions-item>
              </el-descriptions>
            </template>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="examStep > 0" @click="examStep -= 1">上一步</el-button>
        <el-button v-if="examStep < 2" type="primary" @click="nextExamStep">下一步</el-button>
        <el-button v-else type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <ImportDialog
      v-model="importDialogVisible"
      type="exams"
      title="考试 Excel 导入"
      @imported="loadExams"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import ImportDialog from '@/components/ImportDialog.vue'
import { getTeachingClasses } from '@/api/teachingClass'
import { currentUserId, formatLocalDateTime, getRecords, getTotal, normalizeStatus, paginateRecords, scopedTeacherParams } from '@/utils/apiHelpers'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()
const { confirmDelete } = useConfirm()
const formRef = ref(null)

const loading = ref(false)
const submitLoading = ref(false)
const deleteLoading = ref(false)
const exams = ref([])
const papers = ref([])
const classList = ref([])

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)
const importDialogVisible = ref(false)
const examStep = ref(0)

const form = reactive({
  id: null,
  paperId: null,
  classId: null,
  teacherId: currentUserId(userStore),
  title: '',
  description: '',
  startTime: null,
  endTime: null,
  durationMinutes: 60,
  status: 0
})

const rules = {
  title: [{ required: true, message: '请输入考试名称', trigger: 'blur' }],
  paperId: [{ required: true, message: '请选择试卷', trigger: 'change' }],
  classId: [{ required: true, message: '请选择教学班', trigger: 'change' }],
  durationMinutes: [{ required: true, message: '请输入考试时长', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

// ---- 状态映射 ----
// 前端: 0=未开始, 1=进行中, 2=已结束
// 后端: DRAFT(0), PUBLISHED(1), CLOSED(3)
const STATUS_TO_ENUM = { 0: 'DRAFT', 1: 'PUBLISHED', 2: 'CLOSED' }
const ENUM_TO_STATUS = { DRAFT: 0, PUBLISHED: 1, CLOSED: 2 }

// ---- 响应适配：将后端 ExamResponseDTO 映射为前端表格行 ----
const mapExamForDisplay = (e) => ({
  id: e.id,
  title: e.name || e.title || '',
  classId: e.classId,
  teacherId: e.teacherId,
  paperId: e.paperId,
  durationMinutes: e.durationMinutes,
  startTime: e.startTime,
  endTime: e.endTime,
  status: normalizeStatus(e.status, ENUM_TO_STATUS, 0) === 3 ? 2 : normalizeStatus(e.status, ENUM_TO_STATUS, 0)
})

// ---- 响应适配：将后端 PaperResponseDTO 映射为前端下拉选项 ----
const mapPaperForOption = (p) => ({
  id: p.id,
  title: p.name || p.title || '',
  teacherId: p.teacherId,
  status: normalizeStatus(p.status, { DRAFT: 0, PUBLISHED: 1, ARCHIVED: 2 }, 0)
})

const selectedPaperTitle = computed(() => {
  return papers.value.find(paper => paper.id === form.paperId)?.title || '-'
})

const selectedClassName = computed(() => {
  return classList.value.find(cls => cls.id === form.classId)?.className || '-'
})

const loadExams = async () => {
  loading.value = true
  try {
    const params = scopedTeacherParams(userStore)
    const res = await request.get('/api/exams', { params })
    if (res.code === '200' && res.data) {
      const records = getRecords(res.data).map(mapExamForDisplay)
      exams.value = paginateRecords(records, pagination.pageNum, pagination.pageSize)
      pagination.total = getTotal(records)
    } else {
      exams.value = []
      pagination.total = 0
    }
  } catch (e) {
    exams.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const loadPapers = async () => {
  try {
    const params = scopedTeacherParams(userStore)
    const res = await request.get('/api/papers', { params })
    if (res.code === '200' && res.data) {
      const data = res.data
      papers.value = getRecords(data).map(mapPaperForOption).filter(paper => paper.status === 1)
    } else {
      papers.value = []
    }
  } catch (e) {
    papers.value = []
  }
}

const loadClasses = async () => {
  try {
    const res = await getTeachingClasses(scopedTeacherParams(userStore, { pageSize: 100 }))
    if (res.code === '200' && res.data) {
      classList.value = getRecords(res.data)
    } else {
      classList.value = []
    }
  } catch (e) {
    classList.value = []
  }
}

const handleCreate = () => {
  if (!papers.value || papers.value.length === 0) {
    ElMessage.warning('暂无已发布的试卷，请先创建并发布试卷后再来发布考试')
    return
  }
  if (!classList.value || classList.value.length === 0) {
    ElMessage.warning('暂无教学班，请先创建教学班后再来发布考试')
    return
  }
  Object.assign(form, {
    id: null,
    paperId: null,
    classId: null,
    teacherId: currentUserId(userStore),
    title: '',
    description: '',
    startTime: null,
    endTime: null,
    durationMinutes: 60,
    status: 0
  })
  examStep.value = 0
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  if (row.status !== 0) {
    ElMessage.warning('已发布或已结束的考试不能直接修改')
    return
  }
  try {
    const res = await request.get(`/api/exams/${row.id}`)
    if (res.code === '200' && res.data) {
      const data = res.data
      Object.assign(form, {
        id: data.id,
        paperId: data.paperId,
        classId: data.classId,
        teacherId: data.teacherId || currentUserId(userStore),
        title: data.name || '',
        description: data.description || '',
        startTime: data.startTime ? new Date(data.startTime) : null,
        endTime: data.endTime ? new Date(data.endTime) : null,
        durationMinutes: data.durationMinutes ?? 60,
        status: normalizeStatus(data.status, ENUM_TO_STATUS, 0)
      })
      examStep.value = 0
      dialogVisible.value = true
    }
  } catch (e) {
    // 全局已提示
  }
}

const validateExamTarget = () => {
  if (!form.title) {
    ElMessage.warning('请输入考试名称')
    return false
  }
  if (!form.paperId) {
    ElMessage.warning('请选择试卷')
    return false
  }
  if (!form.classId) {
    ElMessage.warning('请选择教学班')
    return false
  }
  return true
}

const validateExamSchedule = () => {
  if (!form.durationMinutes) {
    ElMessage.warning('请输入考试时长')
    return false
  }
  if (!form.startTime) {
    ElMessage.warning('请选择开始时间')
    return false
  }
  if (!form.endTime) {
    ElMessage.warning('请选择结束时间')
    return false
  }
  if (form.endTime <= form.startTime) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return false
  }
  if (!form.id && form.startTime < new Date()) {
    ElMessage.warning('考试开始时间不能早于当前时间')
    return false
  }
  return true
}

const nextExamStep = async () => {
  if (examStep.value === 0) {
    try {
      await formRef.value.validateField(['title', 'paperId', 'classId'])
    } catch (e) {
      return
    }
    examStep.value = 1
    return
  }

  try {
    await formRef.value.validateField(['durationMinutes', 'startTime', 'endTime'])
  } catch (e) {
    return
  }
  if (validateExamSchedule()) {
    examStep.value = 2
  }
}

// ---- 请求适配：将前端表单数据转换为 ExamRequestDTO ----
const buildRequestDTO = () => {
  const selectedPaper = papers.value.find(paper => paper.id === form.paperId)
  return {
    name: form.title,
    classId: form.classId ? Number(form.classId) : null,
    teacherId: Number(selectedPaper?.teacherId || form.teacherId || currentUserId(userStore)),
    paperId: form.paperId ? Number(form.paperId) : null,
    description: form.description || '',
    startTime: formatLocalDateTime(form.startTime),
    endTime: formatLocalDateTime(form.endTime),
    durationMinutes: form.durationMinutes || 60,
    status: STATUS_TO_ENUM[form.status] || 'DRAFT'
  }
}

const handleSubmit = async () => {
  if (!validateExamTarget() || !validateExamSchedule()) {
    return
  }

  const data = buildRequestDTO()

  submitLoading.value = true
  try {
    let res
    if (form.id) {
      res = await request.put(`/api/exams/${form.id}`, data)
    } else {
      res = await request.post('/api/exams', data)
    }

    if (res.code === '200') {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadExams()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    const errorMsg = e.response?.data?.message ||
                     e.response?.data?.msg ||
                     e.message ||
                     '保存失败'
    ElMessage.error(errorMsg)
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  if (row.status === 1) {
    ElMessage.warning('进行中的考试不能删除，请先关闭考试')
    return
  }
  try {
    const confirmed = await confirmDelete('确认删除此考试？')
    if (!confirmed) return
    deleteLoading.value = true
    const res = await request.delete(`/api/exams/${row.id}`)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadExams()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    deleteLoading.value = false
  }
}

const handleGrading = (row) => {
  router.push(`/exams/${row.id}/grading`)
}

const handleEnd = async (row) => {
  try {
    const confirmed = await confirmDelete('确认结束此考试？结束后将无法恢复')
    if (!confirmed) return
    
    // 调用专用的 close 接口
    const res = await request.post(`/api/exams/${row.id}/close`)
    if (res.code === '200') {
      ElMessage.success('考试已结束')
      loadExams()
    }
  } catch (e) {
    // 全局已提示
  }
}

onMounted(() => {
  loadPapers()
  loadClasses()
  loadExams()
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

.wizard-dialog {
  display: flex;
  gap: 24px;
  min-height: 300px;
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
</style>
