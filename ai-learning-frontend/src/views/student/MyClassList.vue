<template>
  <div class="my-classes">
    <el-card v-loading="loading">
      <template #header>
        <div class="class-header">
          <div>
            <span class="header-title">我的课程与学习路线</span>
            <p>从这里进入可视化路线图、互动课本、实验和在线 IDE。</p>
          </div>
        </div>
      </template>

      <div class="course-card-grid">
        <article v-for="row in classList" :key="`card-${row.classStudentId}`" class="course-card">
          <div class="course-card-main">
            <el-tag type="success">{{ row.semester || '当前学期' }}</el-tag>
            <h2>{{ row.courseName }}</h2>
            <p>{{ row.courseDescription || row.description || '这门课还没有课程简介。' }}</p>
            <div class="course-meta">
              <span>班级：{{ row.className }}</span>
              <span v-if="row.difficulty">难度：{{ row.difficulty }}</span>
              <span v-if="row.rating">评分：{{ row.rating }}</span>
            </div>
          </div>
          <div class="course-card-actions">
            <el-button type="primary" size="large" :disabled="!canOpenCourse(row)" @click="handleLearn(row)">继续学习</el-button>
            <el-button size="large" :disabled="!canOpenCourse(row)" @click="handleMap(row)">查看学习路线</el-button>
          </div>
        </article>
      </div>

      <el-pagination
        class="pagination"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="allClassList.length"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
      />

<!--      <el-table :data="classList" border stripe>-->
<!--        <template #empty><AppEmpty /></template>-->
<!--        <el-table-column prop="className" label="班级名称" min-width="140" />-->
<!--        <el-table-column prop="courseName" label="课程名称" min-width="140" />-->
<!--        <el-table-column prop="semester" label="学期" width="100" />-->
<!--        <el-table-column label="论文方向" min-width="280">-->
<!--          <template #default="{ row }">-->
<!--            <div v-if="row.paperLoading" class="paper-loading">-->
<!--              <el-icon class="is-loading"><i class="el-icon-loading" /></el-icon>-->
<!--              <span>加载中...</span>-->
<!--            </div>-->
<!--            <div v-else-if="row.paperCount > 0" class="paper-info">-->
<!--              <div class="paper-count">-->
<!--                <el-tag size="small" type="success">{{ row.paperCount }} 篇相关论文</el-tag>-->
<!--                <span v-if="row.yearRange" class="year-range">{{ row.yearRange }}</span>-->
<!--              </div>-->
<!--              <div v-if="row.directions && row.directions.length" class="paper-directions">-->
<!--                <el-tag-->
<!--                  v-for="d in row.directions"-->
<!--                  :key="d"-->
<!--                  size="small"-->
<!--                  type="info"-->
<!--                  class="direction-tag"-->
<!--                  effect="plain"-->
<!--                >{{ d.length > 30 ? d.slice(0, 30) + '...' : d }}</el-tag>-->
<!--              </div>-->
<!--              <div v-if="row.sampleTitle" class="paper-sample">-->
<!--                <el-icon><i class="el-icon-document" /></el-icon>-->
<!--                <span class="sample-text">{{ row.sampleTitle }}</span>-->
<!--              </div>-->
<!--            </div>-->
<!--            <span v-else class="no-paper">暂无相关论文</span>-->
<!--          </template>-->
<!--        </el-table-column>-->
<!--        <el-table-column label="加入时间" width="170">-->
<!--          <template #default="{ row }">-->
<!--            {{ row.joinTime ? new Date(row.joinTime).toLocaleString() : '-' }}-->
<!--          </template>-->
<!--        </el-table-column>-->
<!--        <el-table-column label="操作" width="210">-->
<!--          <template #default="{ row }">-->
<!--            <el-button size="small" type="success" :disabled="!canOpenCourse(row)" @click="handleMap(row)">学习地图</el-button>-->
<!--            <el-button size="small" type="primary" :disabled="!canOpenCourse(row)" @click="handleLearn(row)">进入学习</el-button>-->
<!--          </template>-->
<!--        </el-table-column>-->
<!--      </el-table>-->
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import { getRecords, paginateRecords } from '@/utils/apiHelpers'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()

const allClassList = ref([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})
const classList = computed(() => paginateRecords(allClassList.value, pagination.pageNum, pagination.pageSize))
const loading = ref(false)

const loadClasses = async () => {
  loading.value = true
  try {
    const res = await request.get(`/class-students/student/${userStore.userId}`)
    if (res.code === '200') {
      const memberships = getRecords(res.data).filter(isActiveMembership)
      const rows = []
      for (const item of memberships) {
        const classRes = await request.get(`/teaching-classes/${item.classId}`, { silentError: true })
        if (classRes.code !== '200' || !classRes.data) continue
        const teachingClass = classRes.data
        if (!teachingClass.courseId) continue
        const courseRes = await request.get(`/courses/${teachingClass.courseId}`, { silentError: true })
        const course = courseRes.code === '200' ? courseRes.data : {}
        if (!course?.id || !course?.title) continue
        rows.push({
          ...item,
          ...teachingClass,
          classStudentId: item.id,
          classId: item.classId,
          courseId: teachingClass.courseId,
          className: teachingClass.className || `教学班${item.classId}`,
          courseName: course.title || '-',
          courseDescription: course.description || '',
          difficulty: course.difficulty || '',
          rating: course.rating || '',
          paperLoading: true,
          paperCount: 0,
          directions: [],
          sampleTitle: '',
          yearRange: ''
        })
      }
      allClassList.value = rows
      normalizePage()
      // 加载每门课程的论文方向
      rows.forEach(row => { loadPaperDirections(row) })
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    loading.value = false
  }
}

const normalizePage = () => {
  const maxPage = Math.max(1, Math.ceil(allClassList.value.length / pagination.pageSize))
  if (pagination.pageNum > maxPage) {
    pagination.pageNum = maxPage
  }
}

const handleSizeChange = () => {
  pagination.pageNum = 1
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

const isActiveMembership = (item) => Number(item.status ?? 1) === 1

const canOpenCourse = (row) => {
  return Number.isFinite(Number(row?.courseId)) && Number(row.courseId) > 0 && Number.isFinite(Number(row?.classId)) && Number(row.classId) > 0
}

const handleMap = (row) => {
  if (!canOpenCourse(row)) {
    ElMessage.warning('课程信息不完整，暂不能查看学习路线')
    return
  }
  router.push({ name: 'LearningMap', params: { courseId: row.courseId }, query: { classId: row.classId } })
}

const handleLearn = (row) => {
  if (!canOpenCourse(row)) {
    ElMessage.warning('课程信息不完整，暂不能进入学习')
    return
  }
  router.push({ name: 'CourseLearn', params: { courseId: row.courseId }, query: { classId: row.classId } })
}

onMounted(() => {
  loadClasses()
})
</script>

<style scoped>
.course-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.class-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.header-title {
  font-size: 18px;
  font-weight: 800;
  color: #172033;
}

.class-header p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.course-card {
  min-height: 210px;
  padding: 22px;
  border-radius: 24px;
  border: 1px solid #dbeafe;
  background:
    radial-gradient(circle at 12% 18%, rgba(59, 130, 246, .22), transparent 28%),
    linear-gradient(135deg, #ffffff, #eff6ff);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 18px;
  box-shadow: 0 16px 34px rgba(15, 23, 42, .08);
}

.course-card h2 {
  margin: 12px 0 8px;
  font-size: 24px;
  color: #172033;
}

.course-card p {
  margin: 0;
  color: #475569;
  line-height: 1.8;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.course-meta,
.course-card-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.course-meta {
  margin-top: 12px;
  color: #64748b;
  font-size: 13px;
}

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

.pagination {
  margin-top: 16px;
  justify-content: center;
}
</style>
