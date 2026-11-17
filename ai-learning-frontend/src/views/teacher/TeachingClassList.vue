<template>
  <div class="teaching-class-list workspace-page">
    <PageHeader eyebrow="课程与班级" title="教学班管理" description="为课程开设教学班并维护学生名单，作业、考试和通知都以教学班为范围。">
      <template #actions>
        <el-button v-if="isAdmin" type="primary" @click="handleCreate">创建教学班</el-button>
      </template>
    </PageHeader>

    <section class="filter-panel" aria-label="筛选教学班">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
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
    </section>

    <ErrorNotice
      v-if="loadFailed"
      title="教学班列表未加载"
      message="请确认网络后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadClasses"
    />

    <section v-else class="table-panel" aria-label="教学班列表">
      <el-table :data="classList" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="className" label="班级名称" min-width="200" />
        <el-table-column prop="semester" label="学期" width="120" />
        <el-table-column label="课程开放" width="110"><template #default="{ row }"><el-tag :type="row.courseOpen ? 'success' : 'info'">{{ row.courseOpen ? '已开放' : '未开放' }}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">进行中</el-tag>
            <el-tag v-else type="info">已结束</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="narrow ? ROW_SHEET_COLUMN_WIDTH : isAdmin ? 390 : 150" fixed="right">
          <template #default="{ row }">
            <RowActions :label="row.className || `教学班 ${row.id}`">
              <el-button size="small" @click="handleStudents(row)">{{ isAdmin ? '学生管理' : '查看学生' }}</el-button>
              <el-button v-if="isAdmin" size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button v-if="isAdmin" size="small" @click="openPublication(row)">课程开放</el-button>
              <el-button v-if="isAdmin" size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            </RowActions>
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
      />
    </section>

    <!-- Form Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑教学班' : '创建教学班'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="form.courseId" placeholder="请选择课程">
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
    <el-dialog v-model="studentDialogVisible" :title="isAdmin ? '学生管理' : '学生名单'" width="700px">
      <div v-if="isAdmin" class="student-toolbar">
        <el-input v-model="studentUsername" aria-label="学生用户名" placeholder="输入学生用户名" class="student-input" />
        <el-button type="primary" :loading="studentLoading" @click="handleAddStudent">添加学生</el-button>
      </div>
      <el-table :data="classStudents" v-loading="studentLoading">
        <el-table-column prop="studentId" label="学生ID" width="100" />
        <el-table-column label="学生姓名" width="150">
          <template #default="{ row }">
            {{ row.studentName || '学生' + row.studentId }}
          </template>
        </el-table-column>
        <el-table-column prop="joinTime" label="加入时间" min-width="170" />
        <el-table-column v-if="isAdmin" label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="danger" :loading="deleteLoading" @click="handleRemoveStudent(row)">移除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty />
        </template>
      </el-table>
    </el-dialog>
    <CoursePublicationDialog v-model="publicationVisible" :course-id="publicationCourseId" :course-title="publicationCourseTitle" administrative @saved="loadClasses" />
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import RowActions from '@/components/ui/RowActions.vue'
import CoursePublicationDialog from '@/components/course/CoursePublicationDialog.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { ROW_SHEET_COLUMN_WIDTH, useNarrowScreen } from '@/composables/useNarrowScreen'
import { getTeachingClasses, createTeachingClass, updateTeachingClass, deleteTeachingClass, getClassStudents } from '@/api/teachingClass'
import { addClassStudent, removeClassStudent } from '@/api/teachingClass'
import { getCourses } from '@/utils/course'
import { getRecords, getTotal, requireSuccess, scopedTeacherParams } from '@/utils/apiHelpers'
import request from '@/utils/request'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)
const publicationVisible = ref(false), publicationCourseId = ref(null), publicationCourseTitle = ref('')
function openPublication(row) {
  publicationCourseId.value = row.courseId
  publicationCourseTitle.value = courses.value.find(course => course.id === row.courseId)?.title || ''
  publicationVisible.value = true
}
const { confirmDelete } = useConfirm()
const formRef = ref(null)
const narrow = useNarrowScreen()

const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  classList.value = []
  pagination.total = 0
})
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
  teacherId: null,
  className: '',
  semester: '',
  description: '',
  status: 1
})

const rules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  className: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  semester: [{ required: true, message: '请输入学期', trigger: 'blur' }],
}

const loadClasses = () => guarded(async (isCurrent) => {
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
  const res = requireSuccess(await getTeachingClasses(params))
  if (!isCurrent()) return
  classList.value = getRecords(res.data)
  pagination.total = getTotal(res.data)
})

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
    const available = []
    let pageNum = 1
    let total = 0
    do {
      const res = requireSuccess(await getCourses(scopedTeacherParams(userStore, { box: 'all', pageNum, pageSize: 100 })))
      const records = getRecords(res.data)
      available.push(...records)
      total = getTotal(res.data)
      pageNum += 1
      if (!records.length) break
    } while (available.length < total)
    courses.value = available
  } catch (e) {
    // 全局已提示
  }
}

const handleCreate = async () => {
  if (!isAdmin.value) return
  if (!courses.value || courses.value.length === 0) {
    await loadCourses()
  }
  if (!courses.value || courses.value.length === 0) {
    ElMessage.warning('暂无课程数据，请先创建课程后再创建教学班')
    return
  }
  Object.assign(form, {
    id: null,
    courseId: null,
    teacherId: null,
    className: '',
    semester: '',
    description: '',
    status: 1,
    courseOpen: false
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  if (!isAdmin.value) return
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!isAdmin.value || submitLoading.value) return
  await formRef.value.validate()

  submitLoading.value = true
  try {
    const selectedCourse = courses.value.find(course => Number(course.id) === Number(form.courseId))
    const teacherId = Number(selectedCourse?.teacherId)
    if (!Number.isInteger(teacherId) || teacherId <= 0) {
      ElMessage.error('课程授课教师未加载，请重新选择课程后重试')
      await loadCourses()
      return
    }
    const payload = {
      ...form,
      teacherId
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
  let loaded = false
  try {
    const res = requireSuccess(await getClassStudents(row.id))
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
    loaded = true
  } catch (e) {
    // 全局已提示；不打开名单，避免把读取失败或上一个班级的名单当作当前名单
    classStudents.value = []
  } finally {
    studentLoading.value = false
  }
  studentDialogVisible.value = loaded
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
.student-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.student-input {
  flex: 1 1 200px;
  max-width: 280px;
}
</style>
