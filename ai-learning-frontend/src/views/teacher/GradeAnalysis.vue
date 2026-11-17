<template>
  <div class="learning-analysis workspace-page">
    <PageHeader title="智能学情分析" description="汇总课程下全部教学班的作业和考试，按所选模板生成学情报告。" />
    <el-card>
      <el-form label-position="top" class="report-form" @submit.prevent="generate">
        <el-form-item label="课程">
          <el-select v-model="form.courseId" aria-label="课程" placeholder="选择课程" filterable @change="courseChanged">
            <el-option v-for="course in courses" :key="course.id" :value="course.id" :label="course.title" />
          </el-select>
        </el-form-item>
        <el-form-item label="报告模板">
          <el-select v-model="form.templateId" aria-label="报告模板">
            <el-option v-for="template in templates" :key="template.id" :value="template.id" :label="template.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标字数">
          <el-input-number v-model="form.targetWords" :min="300" :max="5000" :step="100" aria-label="目标字数" />
        </el-form-item>
        <el-form-item class="generate-action">
          <el-button type="primary" :loading="submitting" :disabled="!form.courseId || generating" @click="generate">一键生成学情报告</el-button>
        </el-form-item>
      </el-form>
      <div class="template-actions">
        <el-button text @click="downloadTemplate">下载所选模板</el-button>
        <el-button text :loading="uploading" @click="fileInput.click()">上传 Word 模板</el-button>
        <span>支持 .docx，最大 5MB。可下载默认模板，保留占位符并修改版式后上传。</span>
        <input ref="fileInput" type="file" accept=".docx" hidden @change="uploadTemplate" />
      </div>
      <p v-if="templateFields" class="template-fields">模板章节 {{ templateFields }}</p>
    </el-card>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-card v-if="current" v-loading="loadingReport">
      <template #header>
        <div class="report-heading">
          <div><strong>{{ current.courseTitle }}学情分析报告</strong><p>{{ current.createdAt }} · {{ statusLabel(current.status) }}<template v-if="current.status === 'SUCCESS'"> · {{ current.wordCount }} 字</template></p></div>
          <div class="report-actions">
            <el-button :disabled="current.status !== 'SUCCESS'" @click="copyReport">复制正文</el-button>
            <el-button :disabled="current.status !== 'SUCCESS'" @click="downloadReport('word')">导出 Word</el-button>
            <el-button :disabled="!current.snapshot" @click="downloadReport('csv')">导出 CSV</el-button>
          </div>
        </div>
      </template>
      <el-alert v-if="generating" title="报告正在生成，完成后会自动显示。可以稍后从历史报告查看。" type="info" :closable="false" show-icon />
      <div v-else-if="current.status === 'FAILED'" class="failure">
        <el-alert :title="current.errorMessage || '报告生成失败，请重试'" type="error" :closable="false" show-icon />
        <el-button @click="retryReport">重新生成</el-button>
      </div>
      <article v-else class="report-body">{{ current.content }}</article>
      <el-collapse v-if="current.snapshot" v-model="expanded" class="data-panel">
        <el-collapse-item title="查看分析数据" name="data">
          <LearningAnalysisData v-if="expanded.includes('data')" :key="current.id" :snapshot="current.snapshot" />
        </el-collapse-item>
      </el-collapse>
    </el-card>
    <el-empty v-else-if="!loadingReport" description="选择课程后生成第一份学情分析报告" />

    <el-card v-if="form.courseId">
      <template #header><div class="history-heading"><span>历史报告</span><el-button text @click="loadHistory">刷新</el-button></div></template>
      <el-table :data="history" empty-text="该课程暂无历史报告">
        <el-table-column prop="createdAt" label="生成时间" min-width="180" />
        <el-table-column label="状态"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
        <el-table-column prop="targetWords" label="目标字数" />
        <el-table-column label="实际字数"><template #default="{ row }">{{ row.status === 'SUCCESS' ? row.wordCount : '—' }}</template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" @click="openReport(row.id)">查看</el-button></template></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import request from '@/utils/request'
import { getRecords, scopedTeacherParams } from '@/utils/apiHelpers'
import LearningAnalysisData from '@/components/charts/LearningAnalysisData.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { listReportTemplates, uploadReportTemplate, listLearningReports, getLearningReport, createLearningReport, downloadAnalysisFile } from '@/api/learningAnalysis'

const route = useRoute()
const user = useUserStore()
const form = reactive({ courseId: null, templateId: 'default', targetWords: 1000 })
const courses = ref([])
const templates = ref([])
const coursesReady = ref(false)
const history = ref([])
const current = ref(null)
const error = ref('')
const submitting = ref(false)
const uploading = ref(false)
const loadingReport = ref(false)
const fileInput = ref(null)
const expanded = ref([])
let timer
let selection = 0
let stopped = false
const generating = computed(() => ['QUEUED', 'GENERATING'].includes(current.value?.status))
const sectionLabels = { course_overview: '课程与教学班概况', homework_analysis: '作业情况', exam_analysis: '考试情况', class_comparison: '教学班差异', main_findings: '主要问题', teaching_suggestions: '教学改进建议' }
const templateFields = computed(() => (templates.value.find(t => t.id === form.templateId)?.fields || []).map(f => sectionLabels[f]).filter(Boolean).join('、'))
const statusLabel = status => ({ QUEUED: '排队中', GENERATING: '生成中', SUCCESS: '已完成', FAILED: '失败' })[status] || status

function showError(reason) { error.value = reason?.message || '请求失败，请重试' }
function stopPolling() { clearTimeout(timer) }
async function loadHistory() {
  const courseId = form.courseId
  if (!courseId) return
  try {
    const records = await listLearningReports(courseId)
    if (courseId === form.courseId && !stopped) history.value = records
  } catch (reason) { showError(reason) }
}
async function openReport(id, polling = false) {
  stopPolling()
  const token = ++selection
  loadingReport.value = true
  try {
    const report = await getLearningReport(id)
    if (token !== selection || stopped) return
    current.value = report
    if (!polling) {
      form.courseId = report.courseId
      form.templateId = report.templateId
      form.targetWords = report.targetWords
    }
    await loadHistory()
    if (['QUEUED', 'GENERATING'].includes(report.status)) timer = setTimeout(() => openReport(id, true), 2000)
  } catch (reason) {
    if (token === selection) showError(reason)
  } finally { if (token === selection) loadingReport.value = false }
}
async function courseChanged() {
  stopPolling()
  selection++
  loadingReport.value = false
  current.value = null
  error.value = ''
  expanded.value = []
  history.value = []
  await loadHistory()
  if (history.value.length) await openReport(history.value[0].id)
}
async function generate() {
  if (submitting.value || !form.courseId) return
  submitting.value = true
  error.value = ''
  const courseId = form.courseId
  const token = ++selection
  stopPolling()
  try {
    const report = await createLearningReport({ ...form, requestId: crypto.randomUUID() })
    if (stopped || token !== selection || courseId !== form.courseId) return
    current.value = report
    await openReport(report.id)
  } catch (reason) { showError(reason) }
  finally { submitting.value = false }
}
async function retryReport() {
  form.courseId = current.value.courseId
  form.templateId = current.value.templateId
  form.targetWords = current.value.targetWords
  await generate()
}
async function uploadTemplate(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploading.value = true
  error.value = ''
  try {
    const template = await uploadReportTemplate(file)
    templates.value = await listReportTemplates()
    form.templateId = template.id
    ElMessage.success('模板上传成功，已识别正文填写位置')
  } catch (reason) { showError(reason) }
  finally { uploading.value = false; event.target.value = '' }
}
async function downloadTemplate() {
  try { await downloadAnalysisFile(`templates/${form.templateId}/word`, '课程学情分析模板.docx') }
  catch (reason) { showError(reason) }
}
async function downloadReport(kind) {
  const report = current.value
  try { await downloadAnalysisFile(`reports/${report.id}/${kind}`, `${report.courseTitle}学情分析.${kind === 'word' ? 'docx' : 'csv'}`) }
  catch (reason) { showError(reason) }
}
async function copyReport() {
  try { await navigator.clipboard.writeText(current.value.content); ElMessage.success('已复制报告正文') }
  catch { error.value = '复制失败，请选中报告正文复制' }
}
async function loadCourses() {
  const result = []
  for (let pageNum = 1; ; pageNum++) {
    const response = await request.get('/courses', { params: scopedTeacherParams(user, { pageNum, pageSize: 100 }) })
    if (response.code !== '200') throw new Error(response.msg || '课程加载失败')
    const records = getRecords(response.data)
    result.push(...records)
    if (records.length < 100) break
  }
  courses.value = result
  coursesReady.value = true
}
async function selectRouteCourse() {
  if (!coursesReady.value || route.query.reportId) return
  const courseId = Number(route.query.courseId)
  if (!Number.isSafeInteger(courseId) || courseId <= 0) return
  if (!courses.value.some(course => Number(course.id) === courseId)) {
    form.courseId = null
    await courseChanged()
    error.value = '课程不存在或无权分析，请选择自己的课程。'
    return
  }
  if (form.courseId === courseId) return
  form.courseId = courseId
  await courseChanged()
}
onMounted(async () => {
  try {
    await Promise.all([loadCourses(), listReportTemplates().then(result => { templates.value = result })])
    if (route.query.reportId) await openReport(String(route.query.reportId))
    else await selectRouteCourse()
  } catch (reason) { showError(reason) }
})
watch(() => route.query.reportId, id => { if (id) openReport(String(id)) })
watch(() => route.query.courseId, selectRouteCourse)
onBeforeUnmount(() => { stopped = true; selection++; stopPolling() })
</script>

<style scoped>
.learning-analysis { display: grid; gap: var(--space-5); max-width: 1480px; }
 .learning-analysis :deep(.workspace-page-header) { margin-bottom: 0; }
.report-heading p { margin: 0; color: var(--text-subtle); line-height: 1.7; }
.report-form { display: grid; grid-template-columns: minmax(220px, 1.4fr) minmax(200px, 1fr) 165px auto; gap: 16px; align-items: end; }
.report-form .el-select { width: 100%; }
.template-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.template-actions span, .template-fields { font-size: 13px; color: var(--text-subtle); line-height: 1.7; }
.template-fields { margin: 8px 0 0; }
.report-heading, .history-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.report-heading p { font-size: 13px; margin-top: 6px; }
.report-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.report-actions .el-button { margin-left: 0; }
.report-body { white-space: pre-wrap; line-height: 1.95; font-size: 16px; color: var(--text-body); max-width: 900px; margin: 0 auto; }
.data-panel { margin-top: 24px; }
.failure { display: grid; gap: 14px; justify-items: start; }
@media (max-width: 1000px) { .report-form { grid-template-columns: 1fr 1fr; } .report-heading { align-items: flex-start; flex-direction: column; } }
@media (max-width: 600px) { .report-form { grid-template-columns: 1fr; } }
</style>
