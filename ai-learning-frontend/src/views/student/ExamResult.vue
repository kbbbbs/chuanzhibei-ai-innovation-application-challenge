<template>
  <div class="exam-result workspace-page workspace-page--narrow" v-loading="loading">
    <PageHeader class="card-header" eyebrow="学习" :title="`${assessmentLabel}结果`">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
      </template>
    </PageHeader>

    <ErrorNotice
      v-if="loadFailed"
      :title="`${assessmentLabel}结果未加载`"
      message="请确认网络后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadSubmission"
    />

    <template v-if="submission.id">
      <section class="result-summary content-panel" :class="`result-summary--${resultState.tone}`" :aria-label="`${assessmentLabel}成绩`">
        <div class="summary-score">
          <StatusBadge :label="resultState.label" :tone="resultState.tone" />
          <p class="score-headline">{{ resultState.headline }}</p>
          <p class="score-detail">{{ resultState.detail }}</p>
        </div>
        <el-descriptions :column="descriptionColumns" border>
          <el-descriptions-item :label="`${assessmentLabel}名称`">{{ exam.title }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ submission.submitTime ? new Date(submission.submitTime).toLocaleString() : '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="!submission.cheated && submission.status !== 2" label="客观题得分">{{ submission.objectiveScore || 0 }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section v-if="submission.answers && submission.answers.length > 0" class="answer-section" aria-labelledby="answer-detail-heading">
        <h2 id="answer-detail-heading">答题详情</h2>
        <article v-for="(answer, idx) in submission.answers" :key="idx" class="answer-block">
          <div class="answer-title">
            <span class="question-number">{{ idx + 1 }}</span>
            <div class="question-text" v-html="renderMarkdown(answer.questionText)"></div>
            <span class="question-score">{{ answer.score }} 分</span>
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
        </article>
      </section>
    </template>
    <div v-else-if="!loading && !loadFailed" class="empty-panel">
      <AppEmpty :description="`暂无${assessmentLabel}结果`" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'
import AppEmpty from '@/components/AppEmpty.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { useNarrowScreen } from '@/composables/useNarrowScreen'
import { enumName, normalizeStatus, requireSuccess } from '@/utils/apiHelpers'
import { renderMarkdown } from '@/utils/markdown'

const route = useRoute()
const submissionId = computed(() => Number(route.params.submissionId))
const isHomework = computed(() => route.query.assessmentType === 'HOMEWORK')
const assessmentLabel = computed(() => isHomework.value ? '作业' : '考试')
const submissionApi = computed(() => isHomework.value ? '/api/homework-submissions' : '/api/submissions')

const { loading, loadFailed, guarded } = useGuardedLoad()
const submission = ref({
  id: null,
  status: 0,
  objectiveScore: 0,
  subjectiveScore: 0,
  totalScore: 0,
  answers: [],
})
const exam = ref({ title: '' })

const narrow = useNarrowScreen()
const descriptionColumns = computed(() => (narrow.value ? 1 : 2))
const resultState = computed(() => {
  const value = submission.value
  if (value.cheated) {
    return {
      label: '作弊', tone: 'critical',
      headline: `本次${assessmentLabel.value}被判定为作弊`,
      detail: `切屏 ${value.screenSwitchCount || 0} 次，成绩记为 -1`,
    }
  }
  if (value.status === 2) {
    return {
      label: '已批改', tone: 'positive',
      headline: `总分：${value.totalScore}`,
      detail: `客观题：${value.objectiveScore} | 主观题：${value.subjectiveScore}`,
    }
  }
  return { label: '批改中', tone: 'caution', headline: '批改中', detail: '老师正在批改主观题，请稍后查看' }
})

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

const loadSubmission = () => guarded(async (isCurrent) => {
  const res = requireSuccess(await request.get(`${submissionApi.value}/${submissionId.value}/student`))
  if (!isCurrent()) return
  submission.value = mapSubmission(res.data)

  exam.value = {
    title: submission.value.homeworkName || submission.value.examName ||
      `${isHomework.value ? '作业' : '考试'}${submission.value.homeworkId ?? submission.value.examId}`
  }
})

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
  // The instance also sees navigation away from this route; that must load nothing.
  if (route.params.submissionId) loadSubmission()
}

watch(
  () => [route.params.submissionId, route.query.assessmentType],
  loadPage,
  { immediate: true }
)
</script>

<style scoped>
.result-summary {
  display: grid;
  gap: var(--space-4);
}

.summary-score {
  display: grid;
  justify-items: start;
  gap: var(--space-1);
}

.score-headline {
  margin: var(--space-1) 0 0;
  color: var(--text-strong);
  font-size: var(--text-3xl);
  font-weight: 650;
  line-height: 1.25;
}

.result-summary--critical .score-headline {
  color: var(--critical);
}

.score-detail {
  margin: 0;
  color: var(--text-subtle);
}

.answer-section {
  display: grid;
  gap: var(--space-3);
}

.answer-section h2 {
  margin: 0;
  font-size: var(--text-lg);
}

.answer-block {
  padding: var(--space-4);
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
}

.answer-title {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
  color: var(--text-strong);
}

.question-number {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  color: var(--accent);
  background: var(--surface-accent);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-weight: 650;
}

.question-text {
  flex: 1 1 auto;
  min-width: 0;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.question-text :deep(p) {
  margin: 0;
}

.question-score {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-size: var(--text-sm);
}

.answer-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  color: var(--text-body);
  font-size: var(--text-md);
  overflow-wrap: anywhere;
}

.answer-content strong {
  color: var(--text-strong);
  font-weight: 600;
}

.score-success {
  color: var(--positive);
}

.score-error {
  color: var(--critical);
}
</style>
