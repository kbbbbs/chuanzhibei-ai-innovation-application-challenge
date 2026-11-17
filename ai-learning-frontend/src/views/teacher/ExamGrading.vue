<template>
  <div class="exam-grading workspace-page">
    <PageHeader
      class="card-header"
      eyebrow="作业与考试"
      :title="pageTitle"
      description="客观题已自动判分，这里批改简答和文件题。"
    >
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
      </template>
    </PageHeader>

    <ErrorNotice
      v-if="assessmentFailed"
      :title="`${assessmentLabel}信息未加载`"
      message="无法确认当前批改对象，批改已暂停。下方提交记录按本页地址读取，仅供查看。"
      retry-label="重新加载"
      :busy="assessmentLoading"
      @retry="loadExam"
    />

    <ErrorNotice
      v-if="loadFailed"
      title="提交列表未加载"
      message="请确认网络后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadSubmissions"
    />

    <section v-else class="table-panel" aria-label="提交列表">
      <el-table :data="submissions" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="学生" width="150">
          <template #default="{ row }">
            {{ studentLabel(row.studentId) }}
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
            <el-tag v-if="row.cheated" type="danger">作弊（-1）</el-tag>
            <template v-else>{{ row.totalScore !== null ? row.totalScore : '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.cheated" type="danger">作弊</el-tag>
            <el-tag v-else-if="row.status === 2" type="success">已批改</el-tag>
            <el-tag v-else-if="row.status === 1" type="warning">待批改</el-tag>
            <el-tag v-else type="info">未提交</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!isHomework" label="切屏" width="90" align="center">
          <template #default="{ row }">{{ row.screenSwitchCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status >= 1" size="small" type="primary" :disabled="row.cheated || !assessmentReady" @click="handleGrade(row)">
              {{ row.cheated ? '已锁定' : '批改' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="allSubmissions.length"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
      />
    </section>

    <!-- Grading Dialog -->
    <el-dialog v-model="gradingDialogVisible" title="批改主观题" width="900px" top="5vh">
      <div v-if="currentSubmission.id">
        <el-descriptions :column="2" border class="desc-block">
          <el-descriptions-item label="学生">{{ studentLabel(currentSubmission.studentId) }}</el-descriptions-item>
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
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { currentUserId, enumName, getRecords, normalizeStatus, paginateRecords, requireSuccess } from '@/utils/apiHelpers'
import { resolveResourceUrl } from '@/utils/backendUrl'
import { useUserStore } from '@/store/user'
import request from '@/utils/request'

const route = useRoute()
const userStore = useUserStore()
const examId = computed(() => Number(route.params.id))
const isHomework = computed(() => route.meta.assessmentType === 'HOMEWORK')
const assessmentLabel = computed(() => isHomework.value ? '作业' : '考试')
const assessmentApi = computed(() => isHomework.value ? '/api/homeworks' : '/api/exams')
const submissionApi = computed(() => isHomework.value ? '/api/homework-submissions' : '/api/submissions')

const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  allSubmissions.value = []
})
const {
  loading: assessmentLoading,
  loadFailed: assessmentFailed,
  guarded: guardedAssessment,
  reset: resetAssessmentLoad,
} = useGuardedLoad()
const submitLoading = ref(false)
const exam = ref({})
// The assessment this page has confirmed; grading stays closed until it matches the route.
const loadedExamId = ref(null)
const assessmentReady = computed(() => loadedExamId.value === examId.value)
const pageTitle = computed(() => {
  const title = assessmentReady.value ? exam.value.title : ''
  return title ? `${assessmentLabel.value}批改：${title}` : `${assessmentLabel.value}批改`
})
const allSubmissions = ref([])
// Submissions carry only ids; names are looked up once per student and fall back to the id.
const studentNames = reactive({})
const studentLabel = (studentId) => studentNames[studentId] || `学生${studentId}`

const loadStudentNames = async (records) => {
  const ids = [...new Set(records.map(r => r.studentId).filter(id => id && !(id in studentNames)))]
  const results = await Promise.all(ids.map(id => request.get(`/users/${id}`, { silentError: true }).catch(() => null)))
  results.forEach((res, index) => {
    const user = res?.code === '200' ? res.data : null
    if (user) studentNames[ids[index]] = user.nickname || user.username
  })
}
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})
const gradingDialogVisible = ref(false)
const currentSubmission = ref({})
// Fixed when the dialog opens, so a grade always goes to the API it was read from.
const gradingApi = ref('')
let gradeRun = 0
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

const loadExam = () => {
  const id = examId.value
  const api = assessmentApi.value
  return guardedAssessment(async (isCurrent) => {
    const res = requireSuccess(await request.get(`${api}/${id}`))
    if (!isCurrent()) return
    const data = res.data
    if (!data || typeof data !== 'object' || Array.isArray(data) || String(data.id ?? id) !== String(id)) {
      throw new Error('评测信息无效')
    }
    exam.value = {
      ...data,
      title: data.name || data.title || '',
    }
    loadedExamId.value = id
  })
}

const loadSubmissions = () => guarded(async (isCurrent) => {
  const submissionKey = isHomework.value ? 'homeworkId' : 'examId'
  const res = requireSuccess(await request.get(submissionApi.value, { params: { [submissionKey]: examId.value } }))
  if (!isCurrent()) return
  allSubmissions.value = getRecords(res.data).map(mapSubmission)
  normalizePage()
  await loadStudentNames(allSubmissions.value)
})

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
  if (!assessmentReady.value) return
  const api = submissionApi.value
  const assessmentId = examId.value
  const run = ++gradeRun

  // Load submission details
  try {
    const res = await request.get(`${api}/${row.id}/grading`)
    if (run !== gradeRun) return
    if (res.code === '200') {
      const detail = res.data
      const ownerId = detail.homeworkId ?? detail.examId
      if (ownerId != null && String(ownerId) !== String(assessmentId)) {
        ElMessage.error(`该提交不属于当前${assessmentLabel.value}，已取消批改`)
        return
      }
      currentSubmission.value = row
      gradingApi.value = api

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
  if (!gradingApi.value || !currentSubmission.value.id) return
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
    const res = await request.post(`${gradingApi.value}/${currentSubmission.value.id}/grade-subjective`, data)
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
  // The instance also sees navigation away from both grading routes; that must load nothing.
  if (!['ExamGrading', 'HomeworkSubmissions'].includes(route.name)) return
  resetAssessmentLoad()
  exam.value = {}
  loadedExamId.value = null
  allSubmissions.value = []
  pagination.pageNum = 1
  gradingDialogVisible.value = false
  gradeRun += 1
  gradingApi.value = ''
  currentSubmission.value = {}
  subjectiveAnswers.value = []
  loadExam()
  loadSubmissions()
}

watch(
  () => route.fullPath,
  loadPage,
  { immediate: true }
)
</script>

<style scoped>
.desc-block {
  margin-bottom: var(--space-5);
}

.answer-card {
  margin-bottom: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-canvas);
}

.answer-title {
  margin-bottom: var(--space-2);
  color: var(--text-strong);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.answer-student {
  margin-bottom: var(--space-2);
  color: var(--text-body);
  overflow-wrap: anywhere;
}

.answer-reference {
  margin-bottom: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--text-xs);
  overflow-wrap: anywhere;
}

.score-split {
  margin-left: var(--space-1);
}

.comment-input {
  width: min(300px, 100%);
}

.score-summary {
  margin-top: var(--space-4);
  padding: var(--space-3);
  color: var(--text-strong);
  background: var(--surface-accent);
  border: 1px solid var(--line-accent);
  border-radius: var(--radius-md);
}
</style>
