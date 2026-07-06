<template>
  <div class="my-classes">
    <el-card v-loading="loading">
      <template #header>
        <span>我的教学班</span>
      </template>

      <el-table :data="classList" border stripe>
        <template #empty><AppEmpty /></template>
        <el-table-column prop="className" label="班级名称" min-width="140" />
        <el-table-column prop="courseName" label="课程名称" min-width="140" />
        <el-table-column prop="semester" label="学期" width="100" />
        <el-table-column label="论文方向" min-width="280">
          <template #default="{ row }">
            <div v-if="row.paperLoading" class="paper-loading">
              <el-icon class="is-loading"><i class="el-icon-loading" /></el-icon>
              <span>加载中...</span>
            </div>
            <div v-else-if="row.paperCount > 0" class="paper-info">
              <div class="paper-count">
                <el-tag size="small" type="success">{{ row.paperCount }} 篇相关论文</el-tag>
                <span v-if="row.yearRange" class="year-range">{{ row.yearRange }}</span>
              </div>
              <div v-if="row.directions && row.directions.length" class="paper-directions">
                <el-tag
                  v-for="d in row.directions"
                  :key="d"
                  size="small"
                  type="info"
                  class="direction-tag"
                  effect="plain"
                >{{ d.length > 30 ? d.slice(0, 30) + '...' : d }}</el-tag>
              </div>
              <div v-if="row.sampleTitle" class="paper-sample">
                <el-icon><i class="el-icon-document" /></el-icon>
                <span class="sample-text">{{ row.sampleTitle }}</span>
              </div>
            </div>
            <span v-else class="no-paper">暂无相关论文</span>
          </template>
        </el-table-column>
        <el-table-column label="加入时间" width="170">
          <template #default="{ row }">
            {{ row.joinTime ? new Date(row.joinTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
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
        const classRes = await request.get(`/teaching-classes/${item.classId}`, { silentError: true })
        if (classRes.code !== '200' || !classRes.data) continue
        const teachingClass = classRes.data
        let course = {}
        if (teachingClass.courseId) {
          const courseRes = await request.get(`/courses/${teachingClass.courseId}`, { silentError: true })
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
          paperLoading: true,
          paperCount: 0,
          directions: [],
          sampleTitle: '',
          yearRange: ''
        })
      }
      classList.value = rows
      // 加载每门课程的论文方向
      rows.forEach(row => { loadPaperDirections(row) })
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    loading.value = false
  }
}

const loadPaperDirections = async (row) => {
  if (!row.courseName || row.courseName === '-') {
    row.paperLoading = false
    return
  }
  try {
    const res = await request.get('/api/paper/course-directions', {
      params: { keyword: row.courseName },
      silentError: true
    })
    if (res.code === '200' && res.data) {
      row.paperCount = res.data.paperCount || 0
      row.directions = res.data.directions || []
      row.sampleTitle = (res.data.sampleTitles && res.data.sampleTitles[0]) || ''
      row.yearRange = res.data.yearRange || ''
    }
  } catch (e) {
    // ignore
  } finally {
    row.paperLoading = false
  }
}

const handleLearn = (row) => {
  router.push({ name: 'CourseLearn', params: { courseId: row.courseId }, query: { classId: row.classId } })
}

onMounted(() => {
  loadClasses()
})
</script>

<style scoped>
.paper-loading {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #909399;
  font-size: 13px;
}

.paper-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.paper-count {
  display: flex;
  align-items: center;
  gap: 8px;
}

.year-range {
  font-size: 12px;
  color: #909399;
}

.paper-directions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.direction-tag {
  max-width: 200px;
}

.paper-sample {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
}

.sample-text {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.no-paper {
  color: #c0c4cc;
  font-size: 13px;
}
</style>
