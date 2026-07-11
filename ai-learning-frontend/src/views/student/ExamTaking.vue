<template>
  <div class="exam-taking">
    <el-card v-loading="loading">
      <template #header>
        <div class="exam-header">
          <span>{{ exam.title }}</span>
          <div class="exam-status-panel" :class="{ urgent: remainingTime <= 300 }">
            <div class="timer">
              <el-icon><Clock /></el-icon>
              <div>
                <span class="timer-label">剩余时间</span>
                <strong class="timer-text">{{ formatTime(remainingTime) }}</strong>
              </div>
            </div>
            <div v-if="!isHomework" class="screen-switch-status">
              切屏 {{ screenSwitchCount }} / {{ maxScreenSwitches }}
            </div>
          </div>
        </div>
      </template>

      <el-alert v-if="exam.description" :title="exam.description" type="info" :closable="false" class="exam-description" />
      <el-alert
        v-if="!isHomework"
        :title="antiCheatNotice"
        type="warning"
        :closable="false"
        show-icon
        class="exam-description"
      />

      <div v-for="(question, idx) in questions" :key="question.id" class="question-block">
        <div class="question-title">
          <span class="question-number">{{ idx + 1 }}.</span>
          <span>{{ question.questionText }}</span>
          <span class="question-score">({{ question.score }}分)</span>
        </div>

        <!-- Single Choice -->
        <el-radio-group v-if="question.questionType === 'single_choice'" v-model="answers[question.id]" class="option-group">
          <el-radio v-for="(opt, optIdx) in question.options" :key="optIdx" :label="optIdx">
            {{ String.fromCharCode(65 + optIdx) }}. {{ opt.text }}
          </el-radio>
        </el-radio-group>

        <!-- Multiple Choice -->
        <el-checkbox-group v-if="question.questionType === 'multiple_choice'" v-model="answers[question.id]" class="option-group">
          <el-checkbox v-for="(opt, optIdx) in question.options" :key="optIdx" :label="optIdx">
            {{ String.fromCharCode(65 + optIdx) }}. {{ opt.text }}
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
      </div>

      <div class="submit-action">
        <el-button size="large" :loading="saveLoading" :disabled="loading" @click="handleSaveDraft">暂存答案</el-button>
        <el-button type="primary" size="large" :loading="loading" :disabled="saveLoading" @click="handleSubmit()">提交试卷</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref, reactive, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import { enumName } from '@/utils/apiHelpers'
import request from '@/utils/request'
import { uploadExamAnswerAttachment } from '@/api/exam'

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
const saveLoading = ref(false)
const uploadingQuestionId = ref(null)
let timer = null
let examDeadline = 0
let screenSwitchReporting = false
let autoSubmitTriggered = false

const maxScreenSwitches = computed(() => Number(exam.value.maxScreenSwitches ?? 3))
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
      ElMessage.warning('考试时间到，自动提交')
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
  try {
    const res = await request.get(`${assessmentApi.value}/${examId.value}`)
    if (res.code === '200') {
      exam.value = {
        ...res.data,
        title: res.data.name || res.data.title || '',
      }

      const startRes = await request.post(`${assessmentApi.value}/${examId.value}/submissions/start`, null, {
        params: { candidateId: String(userStore.userId) }
      })
      if (startRes.code !== '200' || !startRes.data?.id) return

      const detailRes = await request.get(`${submissionApi.value}/${startRes.data.id}/student`)
      if (detailRes.code !== '200') return

      submission.value = detailRes.data
      screenSwitchCount.value = Number(detailRes.data.screenSwitchCount || 0)
      if (detailRes.data.cheated) {
        handleCheated()
        return
      }
      questions.value = (detailRes.data.answers || []).map(normalizeQuestion)
      initializeAnswers()
      startTimer()
      bindAntiCheatEvents()
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    loading.value = false
  }
}

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

const resolveResourceUrl = (url) => {
  if (!url) return ''
  if (/^(https?:)?\/\//i.test(url) || /^(data|blob):/i.test(url)) return url
  return url.startsWith('/') ? `${apiBaseUrl}${url}` : url
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
  if (skipConfirm !== true) {
    const confirmed = await confirmSubmit('确认提交试卷？提交后不可修改')
    if (!confirmed) return
  }

  loading.value = true
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
      ElMessage.success('提交成功')
      clearInterval(timer)
      router.push({ name: isHomework.value ? 'MyHomeworkList' : 'MyExamList' })
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    loading.value = false
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
  unbindAntiCheatEvents()
  loadExam()
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
.exam-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.timer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.exam-status-panel {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 250px;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #f5f7fa;
}

.exam-status-panel.urgent {
  border-color: #f56c6c;
  background: #fef0f0;
}

.timer-label {
  display: block;
  color: #606266;
  font-size: 12px;
  line-height: 1;
}

.screen-switch-status {
  margin-left: auto;
  color: #606266;
  font-size: 13px;
  white-space: nowrap;
}

.question-block {
  padding: 20px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  margin-bottom: 20px;
  background: #fafafa;
}

.question-title {
  font-size: 16px;
  margin-bottom: 15px;
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

.exam-description {
  margin-bottom: 20px;
}

.option-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.timer-text {
  display: block;
  margin-top: 4px;
  font-weight: bold;
  color: #f56c6c;
  font-size: 22px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 700px) {
  .exam-header {
    align-items: flex-start;
    gap: 12px;
    flex-direction: column;
  }

  .exam-status-panel {
    width: 100%;
    min-width: 0;
    box-sizing: border-box;
  }
}

.submit-action {
  margin-top: 30px;
  text-align: center;
}

.file-answer {
  display: grid;
  gap: 10px;
}
</style>
