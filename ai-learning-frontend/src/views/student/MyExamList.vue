<template>
  <div class="my-exams">
    <el-card v-loading="loading">
      <template #header>
        <span>我的{{ assessmentLabel }}</span>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="待参加" name="upcoming">
          <el-table :data="upcomingExams" border stripe>
            <template #empty><AppEmpty /></template>
            <el-table-column prop="title" :label="`${assessmentLabel}名称`" />
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
            <el-table-column label="时长" width="100">
              <template #default="{ row }">
                {{ row.duration }}分钟
              </template>
            </el-table-column>
            <el-table-column label="开始状态" width="100">
              <template #default="{ row }">
                <el-tag :type="hasExamStarted(row) ? 'success' : 'warning'">
                  {{ hasExamStarted(row) ? '已开始' : '未开始' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="handleStartExam(row)" :disabled="!canStartExam(row)">
                  {{ canStartExam(row) ? `开始${assessmentLabel}` : '未到时间' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pagination"
            v-model:current-page="upcomingPagination.pageNum"
            v-model:page-size="upcomingPagination.pageSize"
            :total="allUpcomingExams.length"
            :page-sizes="[5, 10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleUpcomingSizeChange"
          />
        </el-tab-pane>

        <el-tab-pane label="已完成" name="completed">
          <el-table :data="completedExams" border stripe>
            <template #empty><AppEmpty /></template>
            <el-table-column prop="title" :label="`${assessmentLabel}名称`" />
            <el-table-column label="提交时间" width="180">
              <template #default="{ row }">
                {{ row.submitTime ? new Date(row.submitTime).toLocaleString() : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="得分" width="100">
              <template #default="{ row }">
                {{ row.totalScore !== null ? row.totalScore : '批改中' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.status === 2" type="success">已批改</el-tag>
                <el-tag v-else type="warning">批改中</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" @click="handleViewResult(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pagination"
            v-model:current-page="completedPagination.pageNum"
            v-model:page-size="completedPagination.pageSize"
            :total="allCompletedExams.length"
            :page-sizes="[5, 10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleCompletedSizeChange"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import { getRecords, normalizeStatus, paginateRecords } from '@/utils/apiHelpers'
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
const assessmentType = computed(() => route.meta.assessmentType || 'EXAM')
const assessmentLabel = computed(() => assessmentType.value === 'HOMEWORK' ? '作业' : '考试')
const isHomework = computed(() => assessmentType.value === 'HOMEWORK')
const assessmentApi = computed(() => isHomework.value ? '/api/homeworks' : '/api/exams')
const submissionApi = computed(() => isHomework.value ? '/api/homework-submissions' : '/api/submissions')

const canStartExam = (exam) => {
  const now = new Date()
  const start = new Date(exam.startTime)
  const end = new Date(exam.endTime)
  return exam.status === 1 && now >= start && now <= end
}

const hasExamStarted = (exam) => exam.status === 1 && new Date() >= new Date(exam.startTime)

const mapExam = (exam) => {
  const normalized = normalizeStatus(exam.status, { DRAFT: 0, PUBLISHED: 1, CLOSED: 2 }, 0)
  return {
    ...exam,
    title: exam.name || exam.title || '',
    duration: exam.durationMinutes || exam.duration || 0,
    status: normalized === 3 ? 2 : normalized,
  }
}

const mapSubmission = (submission, exam) => {
  const normalized = normalizeStatus(submission.status, {
    IN_PROGRESS: 0,
    SUBMITTED: 1,
    GRADED: 2,
    PENDING_MANUAL_REVIEW: 1,
  }, 0)
  return {
    ...submission,
    assessmentId: submission.homeworkId ?? submission.examId,
    title: exam?.title || submission.homeworkName || submission.examName || `${assessmentLabel.value}${submission.homeworkId ?? submission.examId}`,
    duration: exam?.duration || 0,
    submitTime: submission.submittedAt,
    status: normalized,
  }
}

const loadExams = async () => {
  loading.value = true
  try {
    // Get my classes
    const classRes = await request.get(`/class-students/student/${userStore.userId}`)
    if (classRes.code === '200') {
      const classIds = getRecords(classRes.data).map(c => c.classId)

      // Get all exams for these classes
      const examRes = await request.get(assessmentApi.value, {
        params: { pageSize: 100 }
      })
      if (examRes.code === '200') {
        const allExams = getRecords(examRes.data).map(mapExam).filter(e => classIds.includes(e.classId))

        // Get my submissions
        const subRes = await request.get(submissionApi.value, {
          params: { studentId: userStore.userId, pageSize: 100 }
        })
        const mySubmissions = subRes.code === '200' ? getRecords(subRes.data) : []
        const mappedSubmissions = mySubmissions.map(sub => {
          const exam = allExams.find(e => e.id === (sub.homeworkId ?? sub.examId))
          return mapSubmission(sub, exam)
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
      }
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    loading.value = false
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
    ElMessage.warning('考试未到开始时间')
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

watch(
  () => route.name,
  () => loadExams()
)
</script>

<style scoped>
.pagination {
  margin-top: 16px;
  justify-content: center;
}
</style>
