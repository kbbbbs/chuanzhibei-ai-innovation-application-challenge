<template>
  <div class="my-exams workspace-page">
    <PageHeader
      eyebrow="学习"
      :title="`我的${assessmentLabel}`"
      :description="isHomework ? '在开放时间内完成作业，提交后可查看批改结果。' : '在考试时间内进入答题，交卷后可查看批改结果和成绩。'"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadExams">刷新</el-button>
      </template>
    </PageHeader>

    <ErrorNotice
      v-if="loadFailed"
      :title="`${assessmentLabel}列表未加载`"
      message="请确认网络后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadExams"
    />

    <section class="metric-grid" :aria-label="`${assessmentLabel}概览`">
      <div class="metric-tile"><span>{{ pendingLabel }}</span><strong>{{ allUpcomingExams.length }}</strong></div>
      <div class="metric-tile"><span>可以开始</span><strong>{{ startableCount }}</strong></div>
      <div class="metric-tile"><span>批改中</span><strong>{{ gradingCount }}</strong></div>
      <div class="metric-tile"><span>已批改</span><strong>{{ gradedCount }}</strong></div>
    </section>

    <section class="content-panel" v-loading="loading" :aria-label="`${assessmentLabel}列表`">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane :label="pendingLabel" name="upcoming">
          <el-table :data="upcomingExams" stripe>
            <template #empty><AppEmpty :description="`暂无${pendingLabel}的${assessmentLabel}`" /></template>
            <el-table-column prop="title" :label="`${assessmentLabel}名称`" min-width="200">
              <template #default="{ row }"><span class="row-title">{{ row.title }}</span></template>
            </el-table-column>
            <el-table-column :label="isHomework ? '开放时间' : '开始时间'" width="180">
              <template #default="{ row }">
                {{ row.startTime ? new Date(row.startTime).toLocaleString() : '-' }}
              </template>
            </el-table-column>
            <el-table-column :label="isHomework ? '截止时间' : '结束时间'" width="180">
              <template #default="{ row }">
                {{ row.endTime ? new Date(row.endTime).toLocaleString() : '-' }}
              </template>
            </el-table-column>
            <el-table-column :label="isHomework ? '作答限时' : '时长'" width="100">
              <template #default="{ row }">
                {{ row.duration }}分钟
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <StatusBadge v-bind="availabilityState(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="handleStartExam(row)" :disabled="!canStartExam(row)">
                  {{ canStartExam(row) ? `开始${assessmentLabel}` : hasExamEnded(row) ? '已截止' : '未到时间' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="list-pagination"
            v-model:current-page="upcomingPagination.pageNum"
            v-model:page-size="upcomingPagination.pageSize"
            :total="allUpcomingExams.length"
            :page-sizes="[5, 10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleUpcomingSizeChange"
          />
        </el-tab-pane>

        <el-tab-pane label="已完成" name="completed">
          <el-table :data="completedExams" stripe>
            <template #empty><AppEmpty :description="`暂无已完成的${assessmentLabel}`" /></template>
            <el-table-column prop="title" :label="`${assessmentLabel}名称`" min-width="200">
              <template #default="{ row }"><span class="row-title">{{ row.title }}</span></template>
            </el-table-column>
            <el-table-column label="提交时间" width="180">
              <template #default="{ row }">
                {{ row.submitTime ? new Date(row.submitTime).toLocaleString() : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="得分" width="100">
              <template #default="{ row }">
                <span v-if="row.cheated" class="cheated-score">作弊（-1）</span>
                <template v-else>{{ row.totalScore !== null ? row.totalScore : '批改中' }}</template>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.cheated" type="danger">作弊</el-tag>
                <el-tag v-else-if="row.status === 2" type="success">已批改</el-tag>
                <el-tag v-else type="warning">批改中</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="handleViewResult(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="list-pagination"
            v-model:current-page="completedPagination.pageNum"
            v-model:page-size="completedPagination.pageSize"
            :total="allCompletedExams.length"
            :page-sizes="[5, 10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleCompletedSizeChange"
          />
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { getRecords, normalizeStatus, paginateRecords, requireSuccess } from '@/utils/apiHelpers'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeTab = ref('upcoming')
const allUpcomingExams = ref([])
const allCompletedExams = ref([])
const upcomingPagination = reactive({
  pageNum: 1,
  pageSize: 10
})
const completedPagination = reactive({
  pageNum: 1,
  pageSize: 10
})
const upcomingExams = computed(() => paginateRecords(allUpcomingExams.value, upcomingPagination.pageNum, upcomingPagination.pageSize))
const completedExams = computed(() => paginateRecords(allCompletedExams.value, completedPagination.pageNum, completedPagination.pageSize))
const loading = ref(false)
const loadFailed = ref(false)
let loadRun = 0
const assessmentType = computed(() => route.meta.assessmentType || 'EXAM')
const assessmentLabel = computed(() => assessmentType.value === 'HOMEWORK' ? '作业' : '考试')
const isHomework = computed(() => assessmentType.value === 'HOMEWORK')
const pendingLabel = computed(() => isHomework.value ? '待完成' : '待参加')
const startableCount = computed(() => allUpcomingExams.value.filter(canStartExam).length)
const gradingCount = computed(() => allCompletedExams.value.filter(item => !item.cheated && item.status !== 2).length)
const gradedCount = computed(() => allCompletedExams.value.filter(item => !item.cheated && item.status === 2).length)
const assessmentSources = {
  EXAM: { assessmentUrl: '/api/exams', submissionUrl: '/api/submissions', label: '考试' },
  HOMEWORK: { assessmentUrl: '/api/homeworks', submissionUrl: '/api/homework-submissions', label: '作业' },
}

const canStartExam = (exam) => {
  const now = new Date()
  const start = new Date(exam.startTime)
  const end = new Date(exam.endTime)
  return exam.status === 1 && now >= start && now <= end
}

const hasExamStarted = (exam) => exam.status === 1 && new Date() >= new Date(exam.startTime)

const hasExamEnded = (exam) => Boolean(exam.endTime) && new Date() > new Date(exam.endTime)

const availabilityState = (exam) => {
  if (hasExamEnded(exam)) return { label: '已截止', tone: 'neutral' }
  if (hasExamStarted(exam)) return { label: isHomework.value ? '已开放' : '已开始', tone: 'positive' }
  return { label: isHomework.value ? '未开放' : '未开始', tone: 'caution' }
}

const mapExam = (exam) => {
  const normalized = normalizeStatus(exam.status, { DRAFT: 0, PUBLISHED: 1, CLOSED: 2 }, 0)
  return {
    ...exam,
    title: exam.name || exam.title || '',
    duration: exam.durationMinutes || exam.duration || 0,
    status: normalized === 3 ? 2 : normalized,
  }
}

const mapSubmission = (submission, exam, label) => {
  const normalized = normalizeStatus(submission.status, {
    IN_PROGRESS: 0,
    SUBMITTED: 1,
    GRADED: 2,
    PENDING_MANUAL_REVIEW: 1,
  }, 0)
  return {
    ...submission,
    assessmentId: submission.homeworkId ?? submission.examId,
    title: exam?.title || submission.homeworkName || submission.examName || `${label}${submission.homeworkId ?? submission.examId}`,
    duration: exam?.duration || 0,
    submitTime: submission.submittedAt,
    status: normalized,
  }
}

const loadExams = async () => {
  // /my-exams and /my-homeworks share this instance, so one run must never use the other's values.
  const run = ++loadRun
  const { assessmentUrl, submissionUrl, label } = assessmentSources[assessmentType.value] || assessmentSources.EXAM
  const studentId = userStore.userId
  loading.value = true
  loadFailed.value = false
  try {
    // An unknown class or submission state must not offer a second attempt, so all three are required.
    // Get my classes
    const classRes = requireSuccess(await request.get(`/class-students/student/${studentId}`))
    if (run !== loadRun) return
    const classIds = getRecords(classRes.data).map(c => c.classId)

    // Get all exams for these classes
    const examRes = requireSuccess(await request.get(assessmentUrl, {
      params: { pageSize: 100 }
    }))
    if (run !== loadRun) return
    const allExams = getRecords(examRes.data).map(mapExam).filter(e => classIds.includes(e.classId))

    // Get my submissions
    const subRes = requireSuccess(await request.get(submissionUrl, {
      params: { studentId, pageSize: 100 }
    }))
    if (run !== loadRun) return

    const mappedSubmissions = getRecords(subRes.data).map(sub => {
      const exam = allExams.find(e => e.id === (sub.homeworkId ?? sub.examId))
      return mapSubmission(sub, exam, label)
    })

    const finishedExamIds = new Set(
      mappedSubmissions
        .filter(sub => sub.status >= 1)
        .map(sub => sub.assessmentId)
    )

    allUpcomingExams.value = allExams.filter(e => !finishedExamIds.has(e.id) && e.status === 1)
    allCompletedExams.value = mappedSubmissions.filter(sub => sub.status >= 1)
    normalizePage(upcomingPagination, allUpcomingExams.value.length)
    normalizePage(completedPagination, allCompletedExams.value.length)
  } catch (e) {
    if (run !== loadRun) return
    allUpcomingExams.value = []
    allCompletedExams.value = []
    loadFailed.value = true
  } finally {
    if (run === loadRun) loading.value = false
  }
}

const normalizePage = (pagination, total) => {
  const maxPage = Math.max(1, Math.ceil(total / pagination.pageSize))
  if (pagination.pageNum > maxPage) {
    pagination.pageNum = maxPage
  }
}

const handleUpcomingSizeChange = () => {
  upcomingPagination.pageNum = 1
}

const handleCompletedSizeChange = () => {
  completedPagination.pageNum = 1
}

const handleTabChange = () => {
  if (activeTab.value === 'upcoming') {
    normalizePage(upcomingPagination, allUpcomingExams.value.length)
  } else {
    normalizePage(completedPagination, allCompletedExams.value.length)
  }
}

const handleStartExam = (exam) => {
  if (!canStartExam(exam)) {
    ElMessage.warning(hasExamEnded(exam) ? `${assessmentLabel.value}已截止` : `${assessmentLabel.value}未到开始时间`)
    return
  }

  router.push({
    name: 'ExamTaking',
    params: { examId: exam.id },
    query: assessmentType.value === 'HOMEWORK' ? { assessmentType: 'HOMEWORK' } : undefined
  })
}

const handleViewResult = (submission) => {
  router.push({
    name: 'ExamResult',
    params: { submissionId: submission.id },
    query: isHomework.value ? { assessmentType: 'HOMEWORK' } : undefined
  })
}

onMounted(() => {
  loadExams()
})

// /my-homeworks and /my-exams share this component, so a switch must not show the other list.
watch(
  () => route.name,
  (name) => {
    // The shared instance also sees the navigation away from both lists; that must load nothing.
    if (!['MyExamList', 'MyHomeworkList'].includes(name)) {
      loadRun += 1
      return
    }
    allUpcomingExams.value = []
    allCompletedExams.value = []
    activeTab.value = 'upcoming'
    upcomingPagination.pageNum = 1
    completedPagination.pageNum = 1
    loadExams()
  }
)
</script>

<style scoped>
.list-pagination {
  justify-content: center;
  margin-top: var(--space-4);
}

.cheated-score {
  color: var(--critical);
  font-weight: 600;
}
</style>
