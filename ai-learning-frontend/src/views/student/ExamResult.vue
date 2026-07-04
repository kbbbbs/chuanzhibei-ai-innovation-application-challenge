<template>
  <div class="exam-result">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>考试结果</span>
          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>

      <div v-if="submission.id">
        <el-result v-if="submission.status === 2" icon="success" :title="`总分：${submission.totalScore}`" :sub-title="`客观题：${submission.objectiveScore} | 主观题：${submission.subjectiveScore}`">
        <template #extra>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="考试名称">{{ exam.title }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">
              {{ submission.submitTime ? new Date(submission.submitTime).toLocaleString() : '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </el-result>

      <el-result v-else icon="info" title="批改中" sub-title="老师正在批改主观题，请稍后查看">
        <template #extra>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="考试名称">{{ exam.title }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">
              {{ submission.submitTime ? new Date(submission.submitTime).toLocaleString() : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="客观题得分">{{ submission.objectiveScore || 0 }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </el-result>

      <el-divider />

      <div v-if="submission.answers && submission.answers.length > 0">
        <h3>答题详情</h3>
        <div v-for="(answer, idx) in submission.answers" :key="idx" class="answer-block">
          <div class="answer-title">
            <span class="question-number">{{ idx + 1 }}.</span>
            <span>{{ answer.questionText }}</span>
            <span class="question-score">({{ answer.score }}分)</span>
          </div>

          <div class="answer-content">
            <div><strong>我的答案：</strong>{{ formatAnswer(answer) }}</div>
            <div v-if="answer.correctAnswer !== undefined"><strong>正确答案：</strong>{{ formatCorrectAnswer(answer) }}</div>
            <div v-if="answer.earnedScore !== undefined">
              <strong>得分：</strong>
              <span :class="answer.earnedScore === answer.score ? 'score-success' : 'score-error'">
                {{ answer.earnedScore }} / {{ answer.score }}
              </span>
            </div>
            <div v-if="answer.comment"><strong>批注：</strong>{{ answer.comment }}</div>
          </div>
        </div>
      </div>
      </div>
      <AppEmpty v-else description="暂无考试结果" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'
import AppEmpty from '@/components/AppEmpty.vue'
import { enumName, normalizeStatus } from '@/utils/apiHelpers'

const route = useRoute()
const submissionId = computed(() => Number(route.params.submissionId))

const loading = ref(false)
const submission = ref({
  id: null,
  status: 0,
  objectiveScore: 0,
  subjectiveScore: 0,
  totalScore: 0,
  answers: [],
})
const exam = ref({ title: '' })

const TYPE_TO_FRONTEND = {
  SINGLE_CHOICE: 'single_choice',
  MULTIPLE_CHOICE: 'multiple_choice',
  TRUE_FALSE: 'true_false',
  FILL_BLANK: 'fill_blank',
  SHORT_ANSWER: 'short_answer'
}

const mapSubmission = (data) => ({
  ...data,
  submitTime: data.submittedAt,
  status: normalizeStatus(data.status, {
    IN_PROGRESS: 0,
    SUBMITTED: 1,
    PENDING_MANUAL_REVIEW: 1,
    GRADED: 2,
  }, 0),
  answers: (data.answers || []).map(answer => ({
    ...answer,
    questionText: answer.questionContent,
    questionType: TYPE_TO_FRONTEND[enumName(answer.questionType)] || 'short_answer',
    options: (answer.options || []).map(option => ({
      key: option.optionKey,
      text: option.optionContent,
    })),
    studentAnswer: answer.answerContent,
    earnedScore: answer.obtainedScore,
  }))
})

const formatAnswer = (answer) => {
  if (answer.questionType === 'multiple_choice') {
    return (answer.studentAnswer || '')
      .split(',')
      .map(key => answer.options?.find(option => option.key === key)?.text || key)
      .filter(Boolean)
      .join(', ') || '未作答'
  } else if (answer.questionType === 'single_choice' || answer.questionType === 'true_false') {
    return answer.options?.find(option => option.key === answer.studentAnswer)?.text || answer.studentAnswer
  }
  return answer.studentAnswer || '未作答'
}

const formatCorrectAnswer = (answer) => {
  if (answer.questionType === 'multiple_choice') {
    if (Array.isArray(answer.correctAnswer)) {
      return answer.correctAnswer.map(idx => String.fromCharCode(65 + idx)).join(', ')
    }
  } else if (answer.questionType === 'single_choice' || answer.questionType === 'true_false') {
    return answer.options && answer.options[answer.correctAnswer] ? answer.options[answer.correctAnswer].text : answer.correctAnswer
  }
  return answer.referenceAnswer || '-'
}

const loadSubmission = async () => {
  loading.value = true
  try {
    const res = await request.get(`/api/submissions/${submissionId.value}/student`)
    if (res.code === '200') {
      submission.value = mapSubmission(res.data)

      // Load exam
      try {
        const examRes = await request.get(`/api/exams/${submission.value.examId}`)
        if (examRes.code === '200') {
          exam.value = {
            ...examRes.data,
            title: examRes.data.name || examRes.data.title || '',
          }
        }
      } catch (e) {
        // 全局异常已由 request.js 提示
      }
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    loading.value = false
  }
}

const loadPage = () => {
  submission.value = {
    id: null,
    status: 0,
    objectiveScore: 0,
    subjectiveScore: 0,
    totalScore: 0,
    answers: [],
  }
  exam.value = { title: '' }
  loadSubmission()
}

watch(
  () => route.params.submissionId,
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

.answer-block {
  padding: 15px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  margin-bottom: 15px;
  background: #fafafa;
}

.answer-title {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 10px;
  display: flex;
  align-items: baseline;
  gap: 5px;
}

.question-number {
  font-weight: bold;
  color: #409eff;
}

.question-score {
  margin-left: auto;
  color: #999;
  font-size: 14px;
}

.answer-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
}

.score-success {
  color: #67c23a;
}

.score-error {
  color: #f56c6c;
}
</style>
