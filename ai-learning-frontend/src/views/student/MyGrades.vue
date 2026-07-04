<template>
  <div class="my-grades">
    <el-card v-loading="loading">
      <template #header>
        <span>我的成绩</span>
      </template>

      <el-table :data="grades" border stripe>
        <template #empty><AppEmpty /></template>
        <el-table-column prop="examTitle" label="考试名称" />
        <el-table-column prop="courseTitle" label="课程" />
        <el-table-column label="提交时间" width="180">
          <template #default="{ row }">
            {{ row.submitTime ? new Date(row.submitTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="客观题" width="100">
          <template #default="{ row }">
            {{ row.objectiveScore || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="主观题" width="100">
          <template #default="{ row }">
            {{ row.subjectiveScore !== null ? row.subjectiveScore : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="总分" width="100">
          <template #default="{ row }">
            <span v-if="row.totalScore !== null" class="score-total">
              {{ row.totalScore }}
            </span>
            <span v-else class="score-pending">批改中</span>
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
            <el-button size="small" @click="handleView(row)">查看详情</el-button>
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
import { getRecords, normalizeStatus } from '@/utils/apiHelpers'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()

const grades = ref([])
const loading = ref(false)

const loadGrades = async () => {
  loading.value = true
  try {
    // Get all my submissions
    const subRes = await request.get('/api/submissions', {
      params: { studentId: userStore.userId, pageSize: 100 }
    })

    if (subRes.code === '200') {
      const submissions = getRecords(subRes.data).map(sub => ({
        ...sub,
        examTitle: sub.examName || `考试${sub.examId}`,
        courseTitle: '-',
        submitTime: sub.submittedAt,
        status: normalizeStatus(sub.status, {
          IN_PROGRESS: 0,
          SUBMITTED: 1,
          PENDING_MANUAL_REVIEW: 1,
          GRADED: 2,
        }, 0),
      }))

      // Enrich with exam and course info
      for (const sub of submissions) {
        try {
          const examRes = await request.get(`/api/exams/${sub.examId}`)
          if (examRes.code === '200') {
            sub.examTitle = examRes.data.name || examRes.data.title || `考试${sub.examId}`

            if (examRes.data.paperId) {
              const paperRes = await request.get(`/api/papers/${examRes.data.paperId}`)
              if (paperRes.code === '200' && paperRes.data?.courseId) {
                const courseRes = await request.get(`/courses/${paperRes.data.courseId}`)
                if (courseRes.code === '200') {
                  sub.courseTitle = courseRes.data.title
                }
              }
            }
          }
        } catch (e) {
          // 全局异常已由 request.js 提示
        }
      }

      grades.value = submissions.filter(sub => sub.status >= 1)
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    loading.value = false
  }
}

const handleView = (row) => {
  router.push({ name: 'ExamResult', params: { submissionId: row.id } })
}

onMounted(() => {
  loadGrades()
})
</script>

<style scoped>
.score-total {
  font-weight: bold;
  color: #409eff;
}

.score-pending {
  color: #999;
}
</style>
