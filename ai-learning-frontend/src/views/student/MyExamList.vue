<template>
  <div class="my-exams">
    <el-card v-loading="loading">
      <template #header>
        <span>我的考试</span>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="待参加" name="upcoming">
          <el-table :data="upcomingExams" border stripe>
            <template #empty><AppEmpty /></template>
            <el-table-column prop="title" label="考试名称" />
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
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="handleStartExam(row)" :disabled="!canStartExam(row)">
                  {{ canStartExam(row) ? '开始考试' : '未到时间' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="已完成" name="completed">
          <el-table :data="completedExams" border stripe>
            <template #empty><AppEmpty /></template>
            <el-table-column prop="title" label="考试名称" />
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
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import { getRecords, normalizeStatus } from '@/utils/apiHelpers'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('upcoming')
const upcomingExams = ref([])
const completedExams = ref([])
const loading = ref(false)

const canStartExam = (exam) => {
  const now = new Date()
  const start = new Date(exam.startTime)
  const end = new Date(exam.endTime)
  return exam.status === 1 && now >= start && now <= end
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

const mapSubmission = (submission, exam) => {
  const normalized = normalizeStatus(submission.status, {
    IN_PROGRESS: 0,
    SUBMITTED: 1,
    GRADED: 2,
    PENDING_MANUAL_REVIEW: 1,
  }, 0)
  return {
    ...submission,
    title: exam?.title || submission.examName || `考试${submission.examId}`,
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
      const examRes = await request.get('/api/exams', { params: { pageSize: 100 } })
      if (examRes.code === '200') {
        const allExams = getRecords(examRes.data).map(mapExam).filter(e => classIds.includes(e.classId))

        // Get my submissions
        const subRes = await request.get('/api/submissions', {
          params: { studentId: userStore.userId, pageSize: 100 }
        })
        const mySubmissions = subRes.code === '200' ? getRecords(subRes.data) : []
        const mappedSubmissions = mySubmissions.map(sub => {
          const exam = allExams.find(e => e.id === sub.examId)
          return mapSubmission(sub, exam)
        })

        const finishedExamIds = new Set(
          mappedSubmissions
            .filter(sub => sub.status >= 1)
            .map(sub => sub.examId)
        )

        upcomingExams.value = allExams.filter(e => !finishedExamIds.has(e.id) && e.status === 1)
        completedExams.value = mappedSubmissions.filter(sub => sub.status >= 1)
      }
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    loading.value = false
  }
}

const handleStartExam = (exam) => {
  if (!canStartExam(exam)) {
    ElMessage.warning('考试未到开始时间')
    return
  }

  router.push({ name: 'ExamTaking', params: { examId: exam.id } })
}

const handleViewResult = (submission) => {
  router.push({ name: 'ExamResult', params: { submissionId: submission.id } })
}

onMounted(() => {
  loadExams()
})
</script>
