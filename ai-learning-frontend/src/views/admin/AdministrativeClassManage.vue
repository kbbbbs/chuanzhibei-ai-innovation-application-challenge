<template>
  <div class="administrative-class-manage">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>行政班管理</span>
          <el-button type="primary" @click="handleCreate">新增行政班</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm" class="search-form" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="编号、名称、年级或专业" clearable @keyup.enter="handleSearch" />
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

      <el-table :data="classList" border stripe v-loading="loading">
        <template #empty><AppEmpty /></template>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="classCode" label="班级编号" width="130" />
        <el-table-column prop="className" label="行政班名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="关联课程" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ courseTitle(row.courseId) }}
          </template>
        </el-table-column>
        <el-table-column prop="grade" label="年级" width="110" />
        <el-table-column prop="major" label="专业" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">正常</el-tag>
            <el-tag v-else type="info">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleStudents(row)">学生管理</el-button>
            <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" :loading="deleteLoading" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadClasses"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑行政班' : '新增行政班'" width="560px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="关联课程" prop="courseId">
          <el-select v-model="form.courseId" placeholder="请选择课程">
            <el-option v-for="course in courses" :key="course.id" :label="course.title" :value="course.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级编号" prop="classCode">
          <el-input v-model="form.classCode" />
        </el-form-item>
        <el-form-item label="行政班名称" prop="className">
          <el-input v-model="form.className" />
        </el-form-item>
        <el-form-item label="年级">
          <el-input v-model="form.grade" placeholder="例如：2026级" />
        </el-form-item>
        <el-form-item label="专业">
          <el-input v-model="form.major" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">正常</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="studentDialogVisible" :title="currentClass ? `${currentClass.className} - 学生管理` : '学生管理'" width="760px">
      <div class="student-toolbar">
        <el-input v-model="studentKeyword" placeholder="输入学生用户名、昵称或ID" class="student-input" @keyup.enter="handleAddStudent" />
        <el-button type="primary" :loading="studentLoading" @click="handleAddStudent">添加学生</el-button>
      </div>
      <el-table :data="classStudents" border stripe v-loading="studentLoading">
        <template #empty><AppEmpty /></template>
        <el-table-column prop="studentId" label="学生ID" width="100" />
        <el-table-column label="学生姓名" min-width="160">
          <template #default="{ row }">
            {{ row.studentName || `学生${row.studentId}` }}
          </template>
        </el-table-column>
        <el-table-column label="加入时间" min-width="170">
          <template #default="{ row }">
            {{ row.joinTime ? new Date(row.joinTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">正常</el-tag>
            <el-tag v-else type="info">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" :loading="studentLoading" @click="handleRemoveStudent(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import AppEmpty from '@/components/AppEmpty.vue'
import { useConfirm } from '@/composables/useConfirm'
import { getCourses } from '@/utils/course'
import { getRecords, getTotal } from '@/utils/apiHelpers'
import request from '@/utils/request'
import {
  getAdministrativeClasses,
  createAdministrativeClass,
  updateAdministrativeClass,
  deleteAdministrativeClass,
  getAdministrativeClassStudents,
  addAdministrativeClassStudent,
  removeAdministrativeClassStudent
} from '@/api/administrativeClass'

const { confirmDelete } = useConfirm()
const formRef = ref(null)

const loading = ref(false)
const submitLoading = ref(false)
const deleteLoading = ref(false)
const studentLoading = ref(false)
const classList = ref([])
const courses = ref([])
const classStudents = ref([])
const currentClass = ref(null)
const studentKeyword = ref('')

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
const studentDialogVisible = ref(false)

const form = reactive({
  id: null,
  courseId: null,
  classCode: '',
  className: '',
  grade: '',
  major: '',
  description: '',
  status: 1
})

const rules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  classCode: [{ required: true, message: '请输入班级编号', trigger: 'blur' }],
  className: [{ required: true, message: '请输入行政班名称', trigger: 'blur' }]
}

const courseTitle = (courseId) => {
  return courses.value.find(course => course.id === courseId)?.title || `课程${courseId || '-'}`
}

const loadCourses = async () => {
  try {
    const res = await getCourses({ pageNum: 1, pageSize: 200 })
    if (res.code === '200') {
      courses.value = getRecords(res.data)
    }
  } catch (e) {
    // 全局已提示
  }
}

const loadClasses = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.keyword && searchForm.keyword.trim()) {
      params.keyword = searchForm.keyword.trim()
    }
    if (searchForm.courseId) {
      params.courseId = searchForm.courseId
    }
    const res = await getAdministrativeClasses(params)
    if (res.code === '200') {
      classList.value = getRecords(res.data)
      pagination.total = getTotal(res.data)
    }
  } catch (e) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadClasses()
}

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadClasses()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.courseId = null
  pagination.pageNum = 1
  loadClasses()
}

const handleCreate = async () => {
  if (!courses.value.length) {
    await loadCourses()
  }
  if (!courses.value.length) {
    ElMessage.warning('暂无课程数据，请先创建课程')
    return
  }
  Object.assign(form, {
    id: null,
    courseId: null,
    classCode: '',
    className: '',
    grade: '',
    major: '',
    description: '',
    status: 1
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  Object.assign(form, {
    id: row.id,
    courseId: row.courseId,
    classCode: row.classCode,
    className: row.className,
    grade: row.grade || '',
    major: row.major || '',
    description: row.description || '',
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const payload = { ...form }
    let res
    if (form.id) {
      res = await updateAdministrativeClass(form.id, payload)
    } else {
      res = await createAdministrativeClass(payload)
    }
    if (res.code === '200') {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadClasses()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  const confirmed = await confirmDelete('确认删除此行政班？学生关系也会同步删除')
  if (!confirmed) return
  deleteLoading.value = true
  try {
    const res = await deleteAdministrativeClass(row.id)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadClasses()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    deleteLoading.value = false
  }
}

const handleStudents = async (row) => {
  currentClass.value = row
  studentKeyword.value = ''
  studentDialogVisible.value = true
  await loadStudents()
}

const loadStudents = async () => {
  if (!currentClass.value?.id) return
  studentLoading.value = true
  try {
    const res = await getAdministrativeClassStudents(currentClass.value.id)
    if (res.code === '200') {
      const records = getRecords(res.data)
      classStudents.value = await Promise.all(records.map(async (item) => {
        try {
          const userRes = await request.get(`/users/${item.studentId}`, { silentError: true })
          const user = userRes.code === '200' ? userRes.data : null
          return {
            ...item,
            studentName: user?.nickname || user?.username || `学生${item.studentId}`
          }
        } catch (e) {
          return item
        }
      }))
    }
  } catch (e) {
    // 全局已提示
  } finally {
    studentLoading.value = false
  }
}

const findStudent = async (keyword) => {
  const value = keyword.trim()
  if (!value) return null
  const res = await request.get('/users', {
    params: {
      keyword: value,
      roleCode: 'student',
      pageNum: 1,
      pageSize: 50
    }
  })
  const records = res.code === '200' ? getRecords(res.data) : []
  const numericId = Number(value)
  return records.find(user => user.username === value)
    || records.find(user => Number.isFinite(numericId) && user.id === numericId)
    || records.find(user => user.nickname === value)
    || records[0]
}

const handleAddStudent = async () => {
  if (!currentClass.value?.id) return
  const student = await findStudent(studentKeyword.value)
  if (!student) {
    ElMessage.error('未找到学生')
    return
  }
  studentLoading.value = true
  try {
    const res = await addAdministrativeClassStudent(currentClass.value.id, {
      studentId: student.id,
      status: 1
    })
    if (res.code === '200') {
      ElMessage.success('添加成功')
      studentKeyword.value = ''
      await loadStudents()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    studentLoading.value = false
  }
}

const handleRemoveStudent = async (row) => {
  const confirmed = await confirmDelete('确认移除此学生？')
  if (!confirmed) return
  studentLoading.value = true
  try {
    const res = await removeAdministrativeClassStudent(currentClass.value.id, row.id)
    if (res.code === '200') {
      ElMessage.success('移除成功')
      await loadStudents()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    studentLoading.value = false
  }
}

onMounted(() => {
  loadCourses()
  loadClasses()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 16px;
}

.pagination {
  margin-top: 20px;
  justify-content: center;
}

.student-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.student-input {
  width: 260px;
}
</style>
