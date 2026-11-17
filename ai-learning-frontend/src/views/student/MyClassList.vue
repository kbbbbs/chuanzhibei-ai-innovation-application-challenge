<template>
  <div class="my-classes workspace-page" v-loading="loading">
    <PageHeader
      eyebrow="学习"
      title="我的班级"
      description="从这里进入可视化路线图、互动课本、实验和在线 IDE。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadClasses">刷新</el-button>
      </template>
    </PageHeader>

    <ErrorNotice
      v-if="loadFailed"
      title="班级列表未加载"
      message="请确认网络后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadClasses"
    />

    <ErrorNotice
      v-if="skippedCount"
      title="部分班级未加载"
      :message="`有 ${skippedCount} 个班级读取失败，下方列表不完整。`"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadClasses"
    />

    <div v-if="!loading && !allClassList.length && !loadFailed && !skippedCount" class="empty-panel">
      <AppEmpty description="暂无已开放的课程，请联系任课教师或管理员。" />
    </div>

    <section v-if="classList.length" class="card-collection" aria-label="我的课程">
      <article v-for="row in classList" :key="`card-${row.classId}`" class="item-card course-card">
        <div class="course-card-main">
          <StatusBadge :label="row.semester || '当前学期'" tone="accent" />
          <h2>{{ row.courseName }}</h2>
          <p class="course-summary">{{ row.courseDescription || row.description || '这门课还没有课程简介。' }}</p>
          <div class="course-meta">
            <span>班级：{{ row.className }}</span>
            <span v-if="row.difficulty">难度：{{ row.difficulty }}</span>
            <span v-if="row.rating">评分：{{ row.rating }}</span>
          </div>
        </div>
        <div v-if="row.paperCount > 0" class="paper-info">
          <div class="paper-count">
            <span>{{ row.paperCount }} 篇相关论文</span>
            <span v-if="row.yearRange" class="year-range">{{ row.yearRange }}</span>
          </div>
          <div v-if="row.directions?.length" class="paper-directions">
            <el-tag
              v-for="direction in row.directions"
              :key="direction"
              size="small"
              type="info"
              effect="plain"
              class="direction-tag"
            >{{ direction.length > 30 ? `${direction.slice(0, 30)}...` : direction }}</el-tag>
          </div>
        </div>
        <div class="item-card-foot">
          <el-button type="primary" :disabled="!canOpenCourse(row)" @click="handleLearn(row)">继续学习</el-button>
          <el-button :disabled="!canOpenCourse(row)" @click="handleMap(row)">查看学习路线</el-button>
        </div>
      </article>
    </section>

    <el-pagination
      v-if="allClassList.length"
      class="list-pagination"
      v-model:current-page="pagination.pageNum"
      v-model:page-size="pagination.pageSize"
      :total="allClassList.length"
      :page-sizes="[5, 10, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { getRecords, getTotal, paginateRecords, requireSuccess } from '@/utils/apiHelpers'
import request from '@/utils/request'

const router = useRouter()

const allClassList = ref([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})
const classList = computed(() => paginateRecords(allClassList.value, pagination.pageNum, pagination.pageSize))
const skippedCount = ref(0)
const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  allClassList.value = []
  skippedCount.value = 0
})

const loadClassRow = async (teachingClass) => {
  if (!teachingClass?.courseId) return null
  const courseRes = requireSuccess(await request.get(`/courses/${teachingClass.courseId}`, { silentError: true }))
  const course = courseRes.data
  if (!course?.id || !course?.title) return null
  return {
    ...teachingClass,
    classId: teachingClass.id,
    courseId: teachingClass.courseId,
    className: teachingClass.className || `教学班${teachingClass.id}`,
    courseName: course.title || '-',
    courseDescription: course.description || '',
    difficulty: course.difficulty || '',
    rating: course.rating || '',
    paperLoading: true,
    paperCount: 0,
    directions: [],
    sampleTitle: '',
    yearRange: ''
  }
}

const loadClasses = () => guarded(async (isCurrent) => {
  // The server returns only classes with current learning access.
  const teachingClasses = []
  let pageNum = 1
  let total = 0
  do {
    const res = requireSuccess(await request.get('/teaching-classes/my', { params: { pageNum, pageSize: 100 } }))
    if (!isCurrent()) return
    const records = getRecords(res.data)
    teachingClasses.push(...records)
    total = getTotal(res.data)
    pageNum += 1
    if (!records.length) break
  } while (teachingClasses.length < total)
  const rows = []
  let skipped = 0
  for (const item of teachingClasses) {
    try {
      const row = await loadClassRow(item)
      if (row) rows.push(row)
    } catch (e) {
      // One deleted course must not hide the student's other classes.
      skipped += 1
    }
    if (!isCurrent()) return
  }
  allClassList.value = rows
  skippedCount.value = skipped
  normalizePage()
  // 加载每门课程的论文方向；经响应式数组访问，结果才会更新到卡片
  allClassList.value.forEach(row => { loadPaperDirections(row) })
})

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
.course-card-main {
  display: grid;
  justify-items: start;
  gap: var(--space-2);
}

.course-card h2 {
  margin: 0;
  font-size: var(--text-xl);
  overflow-wrap: anywhere;
}

.course-summary {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.course-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-3);
  color: var(--text-subtle);
  font-size: var(--text-sm);
}

.paper-info {
  display: grid;
  gap: var(--space-2);
  padding-top: var(--space-3);
  border-top: 1px solid var(--line-subtle);
}

.paper-count {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  color: var(--text-body);
  font-size: var(--text-sm);
}

.year-range {
  color: var(--text-subtle);
}

.paper-directions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}

.direction-tag {
  max-width: 100%;
}

.list-pagination {
  justify-content: center;
}
</style>
