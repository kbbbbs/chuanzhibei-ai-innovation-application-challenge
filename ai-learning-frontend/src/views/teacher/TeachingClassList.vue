<template>
  <div class="teaching-class-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>教学班管理</span>
          <el-button type="primary" @click="handleCreate">创建教学班</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm" class="search-form" @submit.prevent>
        <el-form-item label="班级名称">
          <el-input v-model="searchForm.className" placeholder="请输入班级名称" clearable @keyup.enter="handleSearch" />
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
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="className" label="班级名称" />
        <el-table-column label="课程" min-width="160">
          <template #default="{ row }">
            {{ getCourseName(row.courseId) }}
          </template>
        </el-table-column>
        <el-table-column prop="semester" label="学期" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">进行中</el-tag>
            <el-tag v-else type="info">已结束</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleStudents(row)">学生管理</el-button>
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
        @current-change="loadClasses"
        @size-change="handleSizeChange"
        class="pagination-bar"
      />
    </el-card>

    <!-- Form Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑教学班' : '创建教学班'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="form.courseId" placeholder="可稍后关联课程" clearable>
            <el-option v-for="course in courses" :key="course.id" :label="course.title" :value="course.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级名称" prop="className">
          <el-input v-model="form.className" />
        </el-form-item>
        <el-form-item label="学期" prop="semester">
          <el-input v-model="form.semester" placeholder="如：2026春季" />
        </el-form-item>
        <el-form-item label="班级简介">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">进行中</el-radio>
            <el-radio :label="0">已结束</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- Student Management Dialog -->
    <el-dialog v-model="studentDialogVisible" title="学生管理" width="700px">
      <div class="student-toolbar">
        <el-input v-model="studentUsername" placeholder="输入学生用户名" class="student-input" />
        <el-button type="primary" :loading="studentLoading" @click="handleAddStudent">添加学生</el-button>
      </div>
      <el-table :data="classStudents" border v-loading="studentLoading">
        <el-table-column prop="studentId" label="学生ID" width="100" />
        <el-table-column label="学生姓名" width="150">
          <template #default="{ row }">
            {{ row.studentName || '学生' + row.studentId }}
          </template>
        </el-table-column>
        <el-table-column prop="joinTime" label="加入时间" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="danger" :loading="deleteLoading" @click="handleRemoveStudent(row)">移除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty />
        </template>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import { getTeachingClasses, createTeachingClass, updateTeachingClass, deleteTeachingClass, getClassStudents } from '@/api/teachingClass'
import { addClassStudent, removeClassStudent } from '@/api/teachingClass'
import { getCourses } from '@/utils/course'
import { currentUserId, getRecords, getTotal, scopedTeacherParams } from '@/utils/apiHelpers'
import request from '@/utils/request'

const userStore = useUserStore()
const { confirmDelete } = useConfirm()
const formRef = ref(null)

const loading = ref(false)
const submitLoading = ref(false)
const deleteLoading = ref(false)
const studentLoading = ref(false)
const classList = ref([])
const courses = ref([])
const classStudents = ref([])

const searchForm = reactive({
  className: '',
  courseId: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)
const studentDialogVisible = ref(false)
const currentClassId = ref(null)
const studentUsername = ref('')

const form = reactive({
  id: null,
  courseId: null,
  teacherId: currentUserId(userStore),
  className: '',
  semester: '',
  description: '',
  status: 1
})

const rules = {
  className: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  semester: [{ required: true, message: '请输入学期', trigger: 'blur' }],
}

const loadClasses = async () => {
  loading.value = true
  try {
    const params = scopedTeacherParams(userStore, {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    if (searchForm.className && searchForm.className.trim()) {
      params.className = searchForm.className.trim()
    }
    if (searchForm.courseId) {
      params.courseId = searchForm.courseId
    }
    const res = await getTeachingClasses(params)
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
  searchForm.className = ''
  searchForm.courseId = null
  pagination.pageNum = 1
  loadClasses()
}

const loadCourses = async () => {
  try {
    const res = await getCourses(scopedTeacherParams(userStore, { pageSize: 100 }))
    if (res.code === '200') {
      courses.value = getRecords(res.data)
    }
  } catch (e) {
    // 全局已提示
  }
}

const getCourseName = (courseId) => {
  if (!courseId) return '待关联课程'
  const course = courses.value.find(item => Number(item.id) === Number(courseId))
  return course?.title || `课程${courseId}`
}

const handleCreate = async () => {
  if (!courses.value || courses.value.length === 0) {
    await loadCourses()
  }
  Object.assign(form, {
    id: null,
    courseId: null,
    teacherId: currentUserId(userStore),
    className: '',
    semester: '',
    description: '',
    status: 1
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value.validate()

  submitLoading.value = true
  try {
    const selectedCourse = courses.value.find(course => course.id === form.courseId)
    const payload = {
      ...form,
      courseId: form.courseId || null,
      teacherId: Number(selectedCourse?.teacherId || form.teacherId || currentUserId(userStore))
    }
    let res
    if (form.id) {
      res = await updateTeachingClass(form.id, payload)
    } else {
      res = await createTeachingClass(payload)
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
  try {
    const confirmed = await confirmDelete('确认删除此教学班？')
    if (!confirmed) return
    deleteLoading.value = true
    const res = await deleteTeachingClass(row.id)
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
  currentClassId.value = row.id
  studentLoading.value = true
  try {
    const res = await getClassStudents(row.id)
    if (res.code === '200') {
      const memberships = getRecords(res.data)
      classStudents.value = await Promise.all(memberships.map(async (membership) => {
        if (membership.studentName || !membership.studentId) return membership
        try {
          const userRes = await request.get(`/users/${membership.studentId}`)
          const user = userRes.code === '200' ? userRes.data : null
          return {
            ...membership,
            studentName: user?.nickname || user?.username || `学生${membership.studentId}`
          }
        } catch (e) {
          return membership
        }
      }))
    }
  } catch (e) {
    // 全局已提示
  } finally {
    studentLoading.value = false
  }
  studentDialogVisible.value = true
}

const handleAddStudent = async () => {
  const username = studentUsername.value.trim()
  if (!username) {
    ElMessage.warning('请输入学生用户名')
    return
  }

  studentLoading.value = true
  try {
    const userRes = await request.get('/users', { params: { username, roleCode: 'student', pageSize: 50 } })
    const userRecords = userRes.code === '200' ? getRecords(userRes.data) : []
    const student = userRecords.find(user => user.username === username)
    if (student) {
      if (student.roleCode && student.roleCode !== 'student') {
        ElMessage.error('只能添加学生角色用户')
        return
      }
      const res = await addClassStudent({
        classId: currentClassId.value,
        studentId: student.id,
        status: 1
      })
      if (res.code === '200') {
        ElMessage.success('添加成功')
        studentUsername.value = ''
        await handleStudents({ id: currentClassId.value })
      }
    } else {
      ElMessage.error('未找到该学生')
    }
  } catch (e) {
    // 全局已提示
  } finally {
    studentLoading.value = false
  }
}

const handleRemoveStudent = async (row) => {
  try {
    const confirmed = await confirmDelete('确认移除此学生？')
    if (!confirmed) return
    deleteLoading.value = true
    const res = await removeClassStudent(row.id)
    if (res.code === '200') {
      ElMessage.success('移除成功')
      await handleStudents({ id: currentClassId.value })
    }
  } catch (e) {
    // 全局已提示
  } finally {
    deleteLoading.value = false
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

.pagination-bar {
  margin-top: 20px;
  justify-content: center;
}

.search-form {
  margin-bottom: 16px;
}

.student-toolbar {
  margin-bottom: 10px;
}

.student-input {
  width: 200px;
  margin-right: 10px;
}
</style>
