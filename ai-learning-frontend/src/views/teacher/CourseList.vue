<template>
  <div class="course-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>课程管理</span>
          <el-button type="primary" @click="handleCreate">新建课程</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm">
        <el-form-item label="课程名称">
          <el-input v-model="searchForm.title" placeholder="请输入课程名称" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.categoryId" placeholder="请选择分类" clearable>
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadCourses">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="courses" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="课程名称" />
        <el-table-column prop="difficulty" label="难度" width="100" />
        <el-table-column prop="studentCount" label="学生数" width="100" />
        <el-table-column prop="viewCount" label="浏览量" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">正常</el-tag>
            <el-tag v-else-if="row.status === 2" type="info">草稿</el-tag>
            <el-tag v-else type="danger">下架</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleDetail(row)">详情</el-button>
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
        @current-change="loadCourses"
        @size-change="loadCourses"
        class="pagination-bar"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import { getCourses, deleteCourse } from '@/utils/course'
import { getCategories } from '@/utils/course'
import { getRecords, getTotal } from '@/utils/apiHelpers'
import { useUserStore } from '@/store/user'

const router = useRouter()
const { confirmDelete } = useConfirm()
const userStore = useUserStore()

const loading = ref(false)
const deleteLoading = ref(false)
const searchForm = reactive({
  title: '',
  categoryId: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const courses = ref([])
const categories = ref([])

const loadCourses = async () => {
  loading.value = true
  try {
    const params = {
      ...searchForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    const res = await getCourses(params)
    if (res.code === '200') {
      const allCourses = getRecords(res.data)
      // 过滤：只显示 teacherId 等于当前用户 ID 的课程
      if (userStore.userId) {
        courses.value = allCourses.filter(course => course.teacherId == userStore.userId)
      } else {
        courses.value = allCourses
      }
      pagination.total = getTotal(res.data)
    }
  } catch (e) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    const res = await getCategories()
    if (res.code === '200') {
      categories.value = res.data
    }
  } catch (e) {
    // 全局已提示
  }
}

const handleCreate = () => {
  router.push('/courses/create')
}

const handleDetail = (row) => {
  router.push(`/courses/${row.id}`)
}

const handleEdit = (row) => {
  router.push({ name: 'CourseCreate', query: { id: row.id } })
}

const handleDelete = async (row) => {
  try {
    const confirmed = await confirmDelete('确认删除此课程？')
    if (!confirmed) return
    deleteLoading.value = true
    const res = await deleteCourse(row.id)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadCourses()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    deleteLoading.value = false
  }
}

const handleReset = () => {
  searchForm.title = ''
  searchForm.categoryId = null
  pagination.pageNum = 1
  loadCourses()
}

onMounted(() => {
  loadCategories()
  loadCourses()
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
</style>
