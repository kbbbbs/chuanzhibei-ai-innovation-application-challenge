<template>
  <div class="exam-taking" v-loading="loading">
    <div class="exam-layout">
      <div class="exam-paper">
        <header class="exam-heading">
          <p class="exam-kicker">{{ isHomework ? '作业作答' : '考试答题' }}</p>
          <h1>{{ exam.title || (isHomework ? '作业' : '考试') }}</h1>
        </header>

        <ErrorNotice
          v-if="loadFailed"
          :title="`${isHomework ? '作业' : '试卷'}未加载`"
          message="试题没有读取成功，这不代表没有题目。请返回列表后重新进入。"
        />

        <el-alert v-if="exam.description" :title="exam.description" type="info" :closable="false" />
        <el-alert
          v-if="!isHomework"
          :title="antiCheatNotice"
          type="warning"
          :closable="false"
          show-icon
        />

        <section
          v-for="(question, idx) in questions"
          :id="`question-${question.id}`"
          :key="question.id"
          class="question-block"
          :aria-label="`第 ${idx + 1} 题`"
        >
          <div class="question-title">
            <span class="question-number">{{ idx + 1 }}</span>
            <div class="question-text" v-html="renderMarkdown(question.questionText)"></div>
            <span class="question-score">{{ question.score }} 分</span>
          </div>

          <!-- Single Choice -->
          <el-radio-group v-if="question.questionType === 'single_choice'" v-model="answers[question.id]" class="option-group">
            <el-radio v-for="(opt, optIdx) in question.options" :key="optIdx" :label="optIdx">
              <span class="option-text">{{ String.fromCharCode(65 + optIdx) }}. <span v-html="renderMarkdown(opt.text)"></span></span>
            </el-radio>
          </el-radio-group>

          <!-- Multiple Choice -->
          <el-checkbox-group v-if="question.questionType === 'multiple_choice'" v-model="answers[question.id]" class="option-group">
            <el-checkbox v-for="(opt, optIdx) in question.options" :key="optIdx" :label="optIdx">
              <span class="option-text">{{ String.fromCharCode(65 + optIdx) }}. <span v-html="renderMarkdown(opt.text)"></span></span>
            </el-checkbox>
          </el-checkbox-group>

          <!-- True/False -->
          <el-radio-group v-if="question.questionType === 'true_false'" v-model="answers[question.id]">
            <el-radio :label="0">正确</el-radio>
            <el-radio :label="1">错误</el-radio>
          </el-radio-group>

          <!-- Fill Blank / Short Answer -->
          <el-input v-if="['fill_blank', 'short_answer'].includes(question.questionType)"
                    v-model="answers[question.id]"
                    type="textarea"
                    :rows="question.questionType === 'short_answer' ? 6 : 2"
                    :aria-label="`第 ${idx + 1} 题答案`"
                    placeholder="请输入答案" />

          <div v-if="question.questionType === 'file_upload'" class="file-answer">
            <el-upload
              action="#"
              :auto-upload="false"
              :limit="1"
              :on-change="file => handleExamFileChange(question, file)"
              :on-remove="() => answers[question.id] = ''"
              :on-exceed="() => ElMessage.warning('每题只能上传一个文件')"
            >
              <el-button :loading="uploadingQuestionId === question.id">上传答题文件</el-button>
              <template #tip><div class="el-upload__tip">支持 PDF、文档、图片、压缩包等，上传后再交卷</div></template>
            </el-upload>
            <el-link v-if="answers[question.id]" :href="resolveResourceUrl(answers[question.id])" target="_blank" type="success">查看已上传文件</el-link>
          </div>
        </section>

        <AppEmpty v-if="!questions.length && !loading && !loadFailed" :description="isHomework ? '暂无作业题目' : '暂无试题'" />
      </div>

      <aside class="exam-side" aria-label="答题状态">
        <div class="exam-status-panel" :class="{ urgent: remainingTime <= 300 }">
          <el-icon :size="20" aria-hidden="true"><Clock /></el-icon>
          <div>
            <span class="timer-label">剩余时间</span>
            <strong class="timer-text" role="timer">{{ formatTime(remainingTime) }}</strong>
          </div>
          <div v-if="!isHomework" class="screen-switch-status">
            切屏 {{ screenSwitchCount }} / {{ maxScreenSwitches }}
          </div>
        </div>

        <div class="answer-sheet">
          <div class="sheet-head">
            <strong>答题卡</strong>
            <span>已答 {{ answeredCount }} / {{ questions.length }}</span>
          </div>
          <div class="sheet-grid">
            <button
              v-for="(question, idx) in questions"
              :key="question.id"
              type="button"
              class="sheet-cell"
              :class="{ 'is-answered': isAnswered(question) }"
              :aria-label="`第 ${idx + 1} 题，${isAnswered(question) ? '已答' : '未答'}`"
              @click="scrollToQuestion(question)"
            >
              {{ idx + 1 }}
            </button>
          </div>
          <div class="sheet-legend" aria-hidden="true">
            <span><i class="legend-dot is-answered"></i>已答</span>
            <span><i class="legend-dot"></i>未答</span>
          </div>
        </div>

        <p class="autosave-status" :class="`is-${autosave.state.value}`" data-testid="answer-save-status" aria-live="polite">
          {{ autosave.label.value || '作答会自动暂存，交卷前不会提交' }}
        </p>
        <div class="submit-action">
          <el-button :loading="saveLoading" :disabled="loading" @click="handleSaveDraft">暂存答案</el-button>
          <el-button type="primary" :loading="loading" :disabled="saveLoading" @click="handleSubmit()">{{ isHomework ? '提交作业' : '提交试卷' }}</el-button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, reactive, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import { enumName, requireSuccess } from '@/utils/apiHelpers'
import { resolveResourceUrl } from '@/utils/backendUrl'
import { renderMarkdown } from '@/utils/markdown'
import request from '@/utils/request'
import { uploadExamAnswerAttachment } from '@/api/exam'
import { useAutosave } from '@/composables/useAutosave'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { confirmSubmit } = useConfirm()

const examId = computed(() => Number(route.params.examId))
const isHomework = computed(() => route.query.assessmentType === 'HOMEWORK')
const assessmentApi = computed(() => isHomework.value ? '/api/homeworks' : '/api/exams')
const submissionApi = computed(() => isHomework.value ? '/api/homework-submissions' : '/api/submissions')

const exam = ref({ title: '' })
const submission = ref(null)
const questions = ref([])
const answers = reactive({})
const remainingTime = ref(0)
const screenSwitchCount = ref(0)
const loading = ref(false)
const loadFailed = ref(false)
const saveLoading = ref(false)
const uploadingQuestionId = ref(null)
let timer = null
let examDeadline = 0
let screenSwitchReporting = false
let autoSubmitTriggered = false
let submitInFlight = false
let answersReady = false

// Autosave uses the same draft endpoint as 「暂存答案」; only the submit buttons hand in the paper.
const autosave = useAutosave({
  key: () => submission.value?.id ? `eduloop:answers:${submissionApi.value}:${submission.value.id}` : '',
  snapshot: () => JSON.parse(JSON.stringify(answers)),
  save: async () => {
    if (!submission.value?.id) return false
    const res = await request.post(`${submissionApi.value}/${submission.value.id}/answers`, buildAnswerPayload(), { silent: true })
    return res?.code === '200'
  },
  delay: 2000
})
watch(answers, () => { if (answersReady) autosave.touch() }, { deep: true })

const isAnswered = (question) => {
  const value = answers[question.id]
  if (Array.isArray(value)) return value.length > 0
  return value !== null && value !== undefined && value !== ''
}
const answeredCount = computed(() => questions.value.filter(isAnswered).length)

const scrollToQuestion = (question) => {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  document.getElementById(`question-${question.id}`)?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' })
}

const maxScreenSwitches =computed(() => Number(exam.value.maxScreenSwitches ?? 3))
const antiCheatNotice = computed(() => {
  const clipboardText = exam.value.disableClipboard ? '本场考试已禁用复制、剪切和粘贴。' : ''
  return `请勿切换标签页或离开考试窗口，超过 ${maxScreenSwitches.value} 次将判定作弊，成绩记为 -1。${clipboardText}`
})

const formatTime = (seconds) => {
  const safeSeconds = Math.max(Number(seconds) || 0, 0)
  const h = Math.floor(safeSeconds / 3600)
  const m = Math.floor((safeSeconds % 3600) / 60)
  const s = safeSeconds % 60
  return h > 0
    ? `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
    : `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

const TYPE_TO_FRONTEND = {
  SINGLE_CHOICE: 'single_choice',
  MULTIPLE_CHOICE: 'multiple_choice',
  TRUE_FALSE: 'true_false',
  FILL_BLANK: 'fill_blank',
  SHORT_ANSWER: 'short_answer',
  FILE_UPLOAD: 'file_upload'
}

const trueFalseOptions = [
  { key: 'true', text: '正确' },
  { key: 'false', text: '错误' }
]

const normalizeQuestion = (answer) => {
  const questionType = TYPE_TO_FRONTEND[enumName(answer.questionType)] || 'short_answer'
  const options = (answer.options || []).map(option => ({
    key: option.optionKey,
    text: option.optionContent,
  }))
  return {
    id: answer.id,
    paperQuestionId: answer.paperQuestionId,
    questionId: answer.questionId,
    questionText: answer.questionContent || '',
    questionType,
    options: questionType === 'true_false' && options.length === 0 ? trueFalseOptions : options,
    score: answer.score || 0,
  }
}

const initializeAnswers = () => {
  questions.value.forEach(q => {
    const savedAnswer = submission.value?.answers?.find(item => item.id === q.id)?.answerContent
    if (q.questionType === 'file_upload') {
      answers[q.id] = savedAnswer || ''
      return
    }
    if (q.questionType === 'multiple_choice') {
      const keys = savedAnswer ? savedAnswer.split(',').map(item => item.trim()).filter(Boolean) : []
      answers[q.id] = keys.map(key => q.options.findIndex(option => option.key === key)).filter(idx => idx >= 0)
    } else if (q.questionType === 'single_choice' || q.questionType === 'true_false') {
      answers[q.id] = q.options.findIndex(option => option.key === savedAnswer)
      if (answers[q.id] < 0) answers[q.id] = null
    } else {
      answers[q.id] = savedAnswer || ''
    }
  })
}

const startTimer = () => {
  if (timer) clearInterval(timer)
  const durationMinutes = exam.value.durationMinutes || exam.value.duration || 0
  const startedAt = submission.value?.startedAt ? new Date(submission.value.startedAt) : new Date()
  const durationCutoff = startedAt.getTime() + durationMinutes * 60 * 1000
  const examCutoff = exam.value.endTime ? new Date(exam.value.endTime).getTime() : durationCutoff
  examDeadline = Math.min(durationCutoff, examCutoff)
  const updateRemainingTime = () => {
    remainingTime.value = Math.max(Math.ceil((examDeadline - Date.now()) / 1000), 0)
    if (remainingTime.value === 0 && !autoSubmitTriggered) {
      autoSubmitTriggered = true
      clearInterval(timer)
      ElMessage.warning(`${isHomework.value ? '作业' : '考试'}时间到，自动提交`)
      handleSubmit(true)
    }
  }
  updateRemainingTime()
  timer = setInterval(() => {
    updateRemainingTime()
  }, 1000)
}

const handleCheated = () => {
  if (timer) clearInterval(timer)
  ElMessage.error('切屏次数已超过上限，本次考试被判定为作弊，成绩为 -1')
  router.replace({ name: 'MyExamList' })
}

const reportVisibilityViolation = async () => {
  if (isHomework.value || !submission.value?.id || screenSwitchReporting) return
  screenSwitchReporting = true
  try {
    const res = await request.post(`${submissionApi.value}/${submission.value.id}/screen-switch`)
    if (res.code === '200' && res.data) {
      screenSwitchCount.value = Math.max(screenSwitchCount.value, Number(res.data.screenSwitchCount || 0))
      if (res.data.cheated) {
        handleCheated()
      } else {
        ElMessage.warning(`检测到切屏，当前 ${screenSwitchCount.value}/${maxScreenSwitches.value} 次`)
      }
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    screenSwitchReporting = false
  }
}

const handleVisibilityChange = () => {
  if (document.hidden) {
    reportVisibilityViolation()
  }
}

const preventClipboard = (event) => {
  if (isHomework.value || !exam.value.disableClipboard || !submission.value?.id) return
  event.preventDefault()
  ElMessage.warning('本场考试已禁用剪贴板操作')
}

const bindAntiCheatEvents = () => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  document.removeEventListener('copy', preventClipboard)
  document.removeEventListener('cut', preventClipboard)
  document.removeEventListener('paste', preventClipboard)
  document.addEventListener('copy', preventClipboard)
  document.addEventListener('cut', preventClipboard)
  document.addEventListener('paste', preventClipboard)
}

const unbindAntiCheatEvents = () => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  document.removeEventListener('copy', preventClipboard)
  document.removeEventListener('cut', preventClipboard)
  document.removeEventListener('paste', preventClipboard)
}

const loadExam = async () => {
  loading.value = true
  loadFailed.value = false
  try {
    const res = requireSuccess(await request.get(`${assessmentApi.value}/${examId.value}`))
    exam.value = {
      ...res.data,
      title: res.data.name || res.data.title || '',
    }

    const startRes = requireSuccess(await request.post(`${assessmentApi.value}/${examId.value}/submissions/start`, null, {
      params: { candidateId: String(userStore.userId) }
    }))
    if (!startRes.data?.id) throw new Error('答卷未创建')

    const detailRes = requireSuccess(await request.get(`${submissionApi.value}/${startRes.data.id}/student`))

    submission.value = detailRes.data
    screenSwitchCount.value = Number(detailRes.data.screenSwitchCount || 0)
    if (detailRes.data.cheated) {
      handleCheated()
      return
    }
    questions.value = (detailRes.data.answers || []).map(normalizeQuestion)
    initializeAnswers()
    // Answers typed before a refresh or while offline outrank the last server draft.
    const local = autosave.readLocal()
    const restored = local && questions.value.some(q => q.id in local)
    if (restored) questions.value.forEach(q => { if (q.id in local) answers[q.id] = local[q.id] })
    await nextTick()
    answersReady = true
    if (restored) { autosave.touch(); ElMessage.info('已恢复上次未保存的作答') }
    startTimer()
    bindAntiCheatEvents()
  } catch (e) {
    // 全局异常已由 request.js 提示；这里只保留页面状态，不自动重试 start
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

const handleExamFileChange = async (question, file) => {
  if (!file?.raw) return
  uploadingQuestionId.value = question.id
  try {
    const res = await uploadExamAnswerAttachment(file.raw)
    if (res.code === '200' && res.data?.url) {
      answers[question.id] = res.data.url
      ElMessage.success('文件上传成功')
    }
  } finally {
    uploadingQuestionId.value = null
  }
}

const buildAnswerPayload = () => ({
  answers: questions.value.map(q => {
    const value = answers[q.id]
    if (q.questionType === 'file_upload') {
      return { paperQuestionId: q.paperQuestionId, attachmentUrl: value || '' }
    }
    if (q.questionType === 'multiple_choice') {
      return {
        paperQuestionId: q.paperQuestionId,
        selectedOptionKeys: (Array.isArray(value) ? value : [])
          .map(idx => q.options[idx]?.key)
          .filter(Boolean)
      }
    }
    if (q.questionType === 'single_choice' || q.questionType === 'true_false') {
      return {
        paperQuestionId: q.paperQuestionId,
        selectedOptionKeys: value === null || value === undefined ? [] : [q.options[value]?.key].filter(Boolean)
      }
    }
    return {
      paperQuestionId: q.paperQuestionId,
      answerContent: value || ''
    }
  })
})

const handleSaveDraft = async ({ notify = true } = {}) => {
  if (!submission.value?.id) {
    ElMessage.error('答卷尚未初始化，请刷新后重试')
    return false
  }

  saveLoading.value = true
  try {
    const res = await request.post(`${submissionApi.value}/${submission.value.id}/answers`, buildAnswerPayload())
    if (res.code === '200') autosave.clearLocal()
    if (res.code === '200' && notify) {
      ElMessage.success('答案已暂存')
    }
    return res.code === '200'
  } catch (e) {
    // 全局异常已由 request.js 提示
    return false
  } finally {
    saveLoading.value = false
  }
}

const handleSubmit = async (skipConfirm = false) => {
  if (submitInFlight) return
  if (skipConfirm !== true) {
    const confirmed = await confirmSubmit(`确认提交${isHomework.value ? '作业' : '试卷'}？提交后不可修改`)
    if (!confirmed || submitInFlight) return
  }

  submitInFlight = true
  loading.value = true
  answersReady = false
  try {
    let res
    if (isHomework.value) {
      if (!await handleSaveDraft({ notify: false })) return
      res = await request.post(`${submissionApi.value}/${submission.value.id}/submit`)
    } else {
      const action = skipConfirm === true ? 'auto-submit' : 'submit'
      res = await request.post(`${submissionApi.value}/${submission.value.id}/${action}`, buildAnswerPayload())
    }
    if (res.code === '200') {
      autosave.finish()
      ElMessage.success('提交成功')
      clearInterval(timer)
      router.push({ name: isHomework.value ? 'MyHomeworkList' : 'MyExamList' })
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    submitInFlight = false
    loading.value = false
    answersReady = true
  }
}

const resetAnswers = () => {
  Object.keys(answers).forEach(key => {
    delete answers[key]
  })
}

const loadPage = () => {
  if (timer) clearInterval(timer)
  exam.value = { title: '' }
  submission.value = null
  questions.value = []
  resetAnswers()
  remainingTime.value = 0
  screenSwitchCount.value = 0
  examDeadline = 0
  screenSwitchReporting = false
  autoSubmitTriggered = false
  answersReady = false
  autosave.reset()
  unbindAntiCheatEvents()
  // Leaving the page also clears the param; that must not start a submission.
  if (route.params.examId) loadExam()
}

watch(
  () => route.params.examId,
  loadPage,
  { immediate: true }
)

onUnmounted(() => {
  if (timer) clearInterval(timer)
  unbindAntiCheatEvents()
})
</script>

<style scoped>
.exam-taking {
  width: min(100%, 1200px);
  margin-inline: auto;
}

.exam-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  align-items: start;
  gap: var(--space-5);
}

.exam-paper {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
}

.exam-kicker {
  margin: 0 0 var(--space-1);
  color: var(--accent);
  font-size: var(--text-xs);
  font-weight: 650;
}

.exam-heading h1 {
  margin: 0;
  font-size: var(--text-2xl);
  overflow-wrap: anywhere;
}

.exam-side {
  position: sticky;
  top: 0;
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.exam-status-panel {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  color: var(--accent);
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
}

.exam-status-panel.urgent {
  color: var(--critical);
  background: var(--critical-surface);
  border-color: var(--critical-line);
}

.timer-label {
  display: block;
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.timer-text {
  display: block;
  color: inherit;
  font-size: var(--text-2xl);
  font-weight: 650;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.screen-switch-status {
  margin-left: auto;
  color: var(--text-body);
  font-size: var(--text-sm);
  white-space: nowrap;
}

.answer-sheet {
  padding: var(--space-4);
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
}

.sheet-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: var(--space-3);
}

.sheet-head strong {
  color: var(--text-strong);
  font-weight: 600;
}

.sheet-head span {
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.sheet-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(36px, 1fr));
  gap: var(--space-2);
  max-height: 260px;
  overflow-y: auto;
}

.sheet-cell {
  height: 36px;
  color: var(--text-body);
  background: var(--surface-canvas);
  border: 1px solid var(--line-default);
  border-radius: var(--radius-md);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}

.sheet-cell:hover {
  border-color: var(--line-strong);
}

.sheet-cell.is-answered {
  color: var(--accent);
  background: var(--surface-accent);
  border-color: var(--line-accent);
  font-weight: 600;
}

.sheet-legend {
  display: flex;
  gap: var(--space-4);
  margin-top: var(--space-3);
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.sheet-legend span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.legend-dot {
  width: 10px;
  height: 10px;
  background: var(--surface-canvas);
  border: 1px solid var(--line-default);
  border-radius: 3px;
}

.legend-dot.is-answered {
  background: var(--surface-accent);
  border-color: var(--line-accent);
}

.autosave-status {
  margin: var(--space-3) 0 0;
  color: var(--text-subtle);
  font-size: var(--text-xs);
  line-height: 1.6;
}

.autosave-status.is-offline,
.autosave-status.is-error {
  color: var(--caution);
}

.submit-action {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
}

.submit-action .el-button + .el-button {
  margin-left: 0;
}

.question-block {
  min-width: 0;
  padding: var(--space-5);
  scroll-margin-top: var(--space-4);
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
}

.question-title {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  color: var(--text-strong);
  font-size: var(--text-lg);
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

.question-text :deep(p),
.option-text :deep(p) {
  display: inline;
  margin: 0;
}

.question-text :deep(pre) {
  padding: var(--space-3);
  overflow-x: auto;
  color: var(--text-on-code);
  background: var(--surface-code);
  border-radius: var(--radius-md);
}

.question-score {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-size: var(--text-sm);
}

.option-group {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--space-2);
}

.option-group :deep(.el-radio),
.option-group :deep(.el-checkbox) {
  height: auto;
  min-height: 32px;
  margin-right: 0;
  white-space: normal;
}

.option-group :deep(.el-radio__label),
.option-group :deep(.el-checkbox__label) {
  white-space: normal;
  overflow-wrap: anywhere;
  line-height: 1.6;
}

.file-answer {
  display: grid;
  gap: var(--space-2);
}

@media (max-width: 960px) {
  .exam-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .exam-side {
    order: -1;
    position: static;
  }

  .sheet-grid {
    max-height: 132px;
  }
}
</style>
