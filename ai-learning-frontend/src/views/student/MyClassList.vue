<template>
  <div class="my-classes">
    <el-card v-loading="loading">
      <template #header>
        <span>我的教学班</span>
      </template>

      <el-table :data="classList" border stripe>
        <template #empty><AppEmpty /></template>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="className" label="班级名称" />
        <el-table-column prop="courseName" label="课程名称" />
        <el-table-column prop="semester" label="学期" width="120" />
        <el-table-column label="加入时间" width="180">
          <template #default="{ row }">
            {{ row.joinTime ? new Date(row.joinTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleLearn(row)">进入学习</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import { getRecords } from '@/utils/apiHelpers'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()

const classList = ref([])
const loading = ref(false)

const loadClasses = async () => {
  loading.value = true
  try {
    const res = await request.get(`/class-students/student/${userStore.userId}`)
    if (res.code === '200') {
      const memberships = getRecords(res.data)
      const rows = []
      for (const item of memberships) {
        const classRes = await request.get(`/teaching-classes/${item.classId}`)
        const teachingClass = classRes.code === '200' ? classRes.data : {}
        let course = {}
        if (teachingClass.courseId) {
          const courseRes = await request.get(`/courses/${teachingClass.courseId}`)
          course = courseRes.code === '200' ? courseRes.data : {}
        }
        rows.push({
          ...item,
          ...teachingClass,
          classStudentId: item.id,
          classId: item.classId,
          courseId: teachingClass.courseId,
          className: teachingClass.className || `教学班${item.classId}`,
          courseName: course.title || '-',
        })
      }
      classList.value = rows
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    loading.value = false
  }
}

const handleLearn = (row) => {
  router.push({ name: 'CourseLearn', params: { courseId: row.courseId }, query: { classId: row.classId } })
}

onMounted(() => {
  loadClasses()
})
</script>
