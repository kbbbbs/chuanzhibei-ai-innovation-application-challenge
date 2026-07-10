<template>
  <div class="exam-grading">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>考试批改：{{ exam?.title }}</span>
          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>

      <el-table :data="submissions" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="学生" width="150">
          <template #default="{ row }">
            学生{{ row.studentId }}
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="180">
          <template #default="{ row }">
            {{ row.submitTime ? new Date(row.submitTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="客观题得分" width="120">
          <template #default="{ row }">
            {{ row.objectiveScore || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="主观题得分" width="120">
          <template #default="{ row }">
            {{ row.subjectiveScore !== null ? row.subjectiveScore : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="总分" width="100">
          <template #default="{ row }">
            {{ row.totalScore !== null ? row.totalScore : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 2" type="success">已批改</el-tag>
            <el-tag v-else-if="row.status === 1" type="warning">待批改</el-tag>
            <el-tag v-else type="info">未提交</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status >= 1" size="small" type="primary" @click="handleGrade(row)">批改</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty />
        </template>
      </el-table>

      <el-pagination
        class="pagination"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="allSubmissions.length"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- Grading Dialog -->
    <el-dialog v-model="gradingDialogVisible" title="批改主观题" width="900px" top="5vh">
      <div v-if="currentSubmission.id">
        <el-descriptions :column="2" border class="desc-block">
          <el-descriptions-item label="学生">学生{{ currentSubmission.studentId }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ currentSubmission.submitTime ? new Date(currentSubmission.submitTime).toLocaleString() : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="客观题得分">{{ currentSubmission.objectiveScore || 0 }}</el-descriptions-item>
        </el-descriptions>

        <div v-for="(answer, idx) in subjectiveAnswers" :key="idx" class="answer-card">
          <div class="answer-title">{{ idx + 1 }}. {{ answer.questionText }}</div>
          <div class="answer-student">
            <template v-if="answer.questionType === 'FILE_UPLOAD' && answer.studentAnswer">
              学生答案：<el-link :href="resolveResourceUrl(answer.studentAnswer)" target="_blank" type="primary">查看上传文件</el-link>
            </template>
            <template v-else>学生答案：{{ answer.studentAnswer || '未作答' }}</template>
          </div>
          <div class="answer-reference">参考答案：{{ answer.referenceAnswer || '无' }}</div>

          <el-form :inline="true" :model="answer" :rules="answerRules" ref="answerFormRef">
            <el-form-item label="得分" prop="score">
              <el-input-number v-model="answer.score" :min="0" :max="answer.maxScore" size="small" />
              <span class="score-split">/ {{ answer.maxScore }}</span>
            </el-form-item>
            <el-form-item label="批注">
              <el-input v-model="answer.comment" placeholder="选填" class="comment-input" size="small" />
            </el-form-item>
          </el-form>
        </div>

        <div class="score-summary">
          <strong>主观题总分：</strong>{{ subjectiveTotal }} / {{ subjectiveMax }}
        </div>
      </div>

      <template #footer>
        <el-button @click="gradingDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmitGrade">提交批改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppEmpty from '@/components/AppEmpty.vue'
import { currentUserId, enumName, getRecords, normalizeStatus, paginateRecords } from '@/utils/apiHelpers'
import { useUserStore } from '@/store/user'
import request from '@/utils/request'

const route = useRoute()
const userStore = useUserStore()
const examId = computed(() => Number(route.params.id))

const loading = ref(false)
const submitLoading = ref(false)
const exam = ref({})
const allSubmissions = ref([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})
const gradingDialogVisible = ref(false)
const currentSubmission = ref({})
const subjectiveAnswers = ref([])
const answerFormRef = ref(null)
const answerRules = {
  score: [{ required: true, message: '请输入得分', trigger: 'blur' }]
}

const subjectiveTotal = computed(() => {
  return subjectiveAnswers.value.reduce((sum, a) => sum + (a.score || 0), 0)
})

const subjectiveMax = computed(() => {
  return subjectiveAnswers.value.reduce((sum, a) => sum + (a.maxScore || 0), 0)
})

const submissions = computed(() => paginateRecords(allSubmissions.value, pagination.pageNum, pagination.pageSize))

const mapSubmission = (submission) => ({
  ...submission,
  submitTime: submission.submittedAt,
  status: normalizeStatus(submission.status, {
    IN_PROGRESS: 0,
    SUBMITTED: 1,
    PENDING_MANUAL_REVIEW: 1,
    GRADED: 2,
  }, 0),
})

const isManualGradable = (answer) => ['SHORT_ANSWER', 'FILE_UPLOAD'].includes(enumName(answer.questionType))

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

const resolveResourceUrl = (url) => {
  if (!url || /^(https?:)?\/\//i.test(url)) return url || ''
  return url.startsWith('/') ? `${apiBaseUrl}${url}` : url
}

const loadExam = async () => {
  try {
    const res = await request.get(`/api/exams/${examId.value}`)
    if (res.code === '200') {
      exam.value = {
        ...res.data,
        title: res.data.name || res.data.title || '',
      }
    }
  } catch (e) {
    // 全局已提示
  }
}

const loadSubmissions = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/submissions', { params: { examId: examId.value } })
    if (res.code === '200') {
      allSubmissions.value = getRecords(res.data).map(mapSubmission)
      normalizePage()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const normalizePage = () => {
  const maxPage = Math.max(1, Math.ceil(allSubmissions.value.length / pagination.pageSize))
  if (pagination.pageNum > maxPage) {
    pagination.pageNum = maxPage
  }
}

const handleSizeChange = () => {
  pagination.pageNum = 1
}

const handleGrade = async (row) => {
  currentSubmission.value = row

  // Load submission details
  try {
    const res = await request.get(`/api/submissions/${row.id}/grading`)
    if (res.code === '200') {
      const detail = res.data

      // Extract subjective questions
      subjectiveAnswers.value = (detail.answers || [])
        .filter(isManualGradable)
        .map(a => ({
          submissionAnswerId: a.id,
          questionId: a.questionId,
          questionText: a.questionContent,
          questionType: a.questionType,
          studentAnswer: a.answerContent,
          referenceAnswer: a.referenceAnswer || '',
          maxScore: Number(a.maxScore || 0),
          score: Number(a.obtainedScore || 0),
          comment: a.comment || ''
        }))

      gradingDialogVisible.value = true
    }
  } catch (e) {
    // 全局已提示
  }
}

const handleSubmitGrade = async () => {
  if (subjectiveAnswers.value.some(a => a.score == null || a.score < 0 || a.score > a.maxScore)) {
    ElMessage.warning('主观题得分不能小于0或超过题目分值')
    return
  }

  const data = {
    graderId: currentUserId(userStore),
    grades: subjectiveAnswers.value.map(a => ({
      submissionAnswerId: a.submissionAnswerId,
      score: Number(a.score || 0),
      comment: a.comment
    }))
  }

  submitLoading.value = true
  try {
    const res = await request.post(`/api/submissions/${currentSubmission.value.id}/grade-subjective`, data)
    if (res.code === '200') {
      ElMessage.success('批改成功')
      gradingDialogVisible.value = false
      loadSubmissions()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    submitLoading.value = false
  }
}

const loadPage = () => {
  exam.value = {}
  allSubmissions.value = []
  pagination.pageNum = 1
  gradingDialogVisible.value = false
  currentSubmission.value = {}
  subjectiveAnswers.value = []
  loadExam()
  loadSubmissions()
}

watch(
  () => route.params.id,
  loadPage,
  { immediate: true }
)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.desc-block {
  margin-bottom: 20px;
}

.answer-card {
  margin-bottom: 20px;
  border: 1px solid #ddd;
  padding: 15px;
  border-radius: 4px;
}

.answer-title {
  font-weight: bold;
  margin-bottom: 8px;
}

.answer-student {
  color: #666;
  margin-bottom: 8px;
}

.answer-reference {
  color: #999;
  font-size: 12px;
  margin-bottom: 8px;
}

.score-split {
  margin-left: 5px;
}

.comment-input {
  width: 300px;
}

.score-summary {
  margin-top: 20px;
  padding: 10px;
  background: #f5f5f5;
  border-radius: 4px;
}

.pagination {
  margin-top: 20px;
  justify-content: center;
}
</style>
