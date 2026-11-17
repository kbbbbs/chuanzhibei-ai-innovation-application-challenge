<template>
  <div class="grade-analysis workspace-page">
    <PageHeader
      eyebrow="作业与考试"
      title="成绩分析"
      description="按教学班查看整体成绩矩阵、考试与作业分布，以及个体学生表现。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">刷新数据</el-button>
        <el-button :disabled="!classExams.length" @click="exportOverview">导出 CSV</el-button>
      </template>
    </PageHeader>

    <!-- 班级选择 -->
    <section class="filter-panel" aria-label="选择分析范围">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="教学班">
          <el-select v-model="selectedClassId" class="class-select" placeholder="选择教学班" @change="handleClassChange" :loading="classesLoading">
            <el-option v-for="c in classes" :key="c.id" :label="`${c.className}（${c.courseTitle}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学生">
          <el-select v-model="selectedStudentId" placeholder="全览（可选筛选）" clearable @change="selectStudent(selectedStudentId)">
            <el-option v-for="s in students" :key="s.studentId" :label="s.studentName" :value="s.studentId" />
          </el-select>
        </el-form-item>
      </el-form>
    </section>

    <ErrorNotice
      v-if="classesFailed"
      title="教学班未加载"
      message="教学班列表读取失败，暂时无法选择分析范围。"
      retry-label="重新加载"
      :busy="classesLoading"
      @retry="loadClasses"
    />
    <ErrorNotice
      v-if="dataError"
      title="成绩数据未加载"
      :message="dataError"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadData"
    />

    <div v-if="!selectedClassId && !classesLoading && !classesFailed" class="empty-panel">
      <AppEmpty description="选择教学班后查看成绩分析" />
    </div>

    <template v-if="classExams.length">
      <!-- 班级统计 -->
      <section class="metric-grid" aria-label="班级统计">
        <div class="metric-tile"><span>已评分平均得分率</span><strong>{{ formatPercent(overviewClassPercent) }}</strong></div>
        <div class="metric-tile"><span>已评分合格率</span><strong>{{ formatPercent(overviewClassPassRate) }}</strong></div>
        <div class="metric-tile"><span>达到平均得分率</span><strong>{{ overviewAboveAvg }}</strong></div>
        <div class="metric-tile metric-tile--attention"><span>已评分需关注 (&lt;60%)</span><strong>{{ overviewNeedHelp }}</strong></div>
      </section>
      <p class="note-strip">得分率与合格率按每次评测的实际满分换算，合格线为满分的 60%。<template v-if="unknownFullCount"> {{ unknownFullCount }} 份成绩满分未知，未计入比例统计。</template></p>

      <!-- 班级整体成绩矩阵 -->
      <section class="content-panel" aria-labelledby="matrix-heading">
        <div class="section-head">
          <div>
            <h2 id="matrix-heading">班级成绩矩阵</h2>
            <p>点击列头切换下方分布，点击行查看该学生的历次成绩。</p>
          </div>
          <div class="toolbar">
            <StatusBadge :label="`学生 ${overviewStudents.length} 人`" />
            <StatusBadge :label="`评测 ${classExams.length} 次`" />
          </div>
        </div>
        <div class="overview-table-wrap" tabindex="0" role="region" aria-label="班级成绩矩阵，可横向滚动">
          <table class="overview-table">
            <thead>
              <tr>
                <th scope="col" class="col-fix col-rank">#</th>
                <th scope="col" class="col-fix col-name">学生</th>
                <th v-for="exam in classExams" :key="exam.examKey" scope="col" class="col-exam"
                  :class="{ 'th-active': selectedExamId === exam.examKey }">
                  <button type="button" class="exam-th" :aria-pressed="selectedExamId === exam.examKey" @click="chooseExam(exam.examKey)">
                    <span class="exam-th-type">{{ assessmentTypeLabel(exam.type) }}</span>
                    <span class="exam-th-title">{{ exam.examTitle.length > 7 ? exam.examTitle.slice(0,7)+'…' : exam.examTitle }}</span>
                    <span class="exam-th-avg">均{{ exam.averageScore ?? '—' }} / {{ exam.fullScore ?? '满分未知' }}</span>
                  </button>
                </th>
                <th scope="col" class="col-fix col-avg">得分率</th>
                <th scope="col" class="col-fix col-trend" title="最近两次已评分记录的得分率变化（百分点）">趋势</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(s, i) in overviewStudents" :key="s.studentId"
                :class="{ 'row-sel': selectedStudentId === s.studentId }"
                @click="selectStudent(s.studentId)">
                <td class="col-rank">{{ i + 1 }}</td>
                <td class="col-name">
                  <button type="button" class="student-link" @click.stop="selectStudent(s.studentId)">{{ s.studentName }}</button>
                </td>
                <td v-for="exam in classExams" :key="exam.examKey" class="col-exam">
                  <template v-if="s.examStates[exam.examKey]">
                    <span v-if="s.examStates[exam.examKey].status === 'GRADED' && s.examStates[exam.examKey].score !== null"
                      class="score-cell" :class="scoreBand(s.examStates[exam.examKey].percent)"
                      :title="s.examStates[exam.examKey].fullScore ? `满分 ${s.examStates[exam.examKey].fullScore}` : '满分未知'">
                      {{ s.examStates[exam.examKey].score }}
                    </span>
                    <el-tag v-else size="small" effect="plain" class="state-cell"
                      :type="studentStateTagType(s.examStates[exam.examKey].status)">
                      {{ studentStateLabel(s.examStates[exam.examKey].status, exam.type) }}
                    </el-tag>
                  </template>
                  <el-tag v-else size="small" effect="plain" type="info" class="state-cell">
                    {{ missingStateLabel(exam.type) }}
                  </el-tag>
                </td>
                <td class="col-avg">
                  <b v-if="s.avgPercent !== null" :class="averageTone(s.avgPercent)">{{ formatPercent(s.avgPercent) }}</b>
                  <span v-else class="dash">—</span>
                </td>
                <td class="col-trend">
                  <span v-if="s.trend > 0" class="tone-positive">↑{{ s.trend }}</span>
                  <span v-else-if="s.trend < 0" class="tone-critical">↓{{ Math.abs(s.trend) }}</span>
                  <span v-else class="dash">—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 考试卡片 -->
      <div class="exam-cards">
        <button v-for="exam in classExams" :key="exam.examKey" type="button" class="exam-card"
          :class="{ 'exam-card-sel': selectedExamId === exam.examKey }"
          :aria-pressed="selectedExamId === exam.examKey"
          @click="chooseExam(exam.examKey)">
          <div class="exam-card-head">
            <strong>{{ exam.examTitle }}</strong>
            <div class="exam-card-tags">
              <el-tag type="info" effect="plain" size="small">{{ assessmentTypeLabel(exam.type) }}</el-tag>
              <el-tag :type="assessmentStatusMeta(exam).type" size="small">{{ assessmentStatusMeta(exam).label }}</el-tag>
            </div>
          </div>
          <div class="exam-card-stats" v-if="exam.hasResults">
            <div><span class="mini-label">均分</span><b>{{ exam.averageScore ?? '—' }}</b></div>
            <div><span class="mini-label">满分</span><b>{{ exam.fullScore ?? '未知' }}</b></div>
            <div><span class="mini-label">得分率</span><b>{{ formatPercent(exam.averagePercent) }}</b></div>
            <div><span class="mini-label">合格率</span><b>{{ formatPercent(exam.passRate) }}</b></div>
            <div><span class="mini-label">最高</span><b>{{ exam.highestScore ?? '—' }}</b></div>
            <div><span class="mini-label">最低</span><b>{{ exam.lowestScore ?? '—' }}</b></div>
          </div>
          <div v-if="assessmentProgressText(exam)" class="exam-card-progress">{{ assessmentProgressText(exam) }}</div>
          <div v-if="!exam.hasResults" class="exam-card-empty">{{ assessmentSummary(exam) }}</div>
        </button>
      </div>

      <!-- 选中考试的分布图 -->
      <section v-if="selectedExam && selectedExam.hasResults" class="content-panel" aria-labelledby="exam-chart-heading">
        <div class="section-head"><h2 id="exam-chart-heading">{{ selectedExam.examTitle }} — 成绩分析</h2></div>
        <div v-if="selectedExam.percents.length" class="charts-grid">
          <GradeDistributionChart :data="selectedExamDist" type="bar" height="280px" unit="%" />
          <div ref="examPassRef" class="pass-donut" role="img" aria-label="合格与不及格人数占比图" />
        </div>
        <p v-else class="note-strip">该评测满分未知，无法按比例统计分布与合格情况。</p>
      </section>
    </template>

    <div v-if="!classExams.length && !loading && selectedClassId && !dataError" class="empty-panel">
      <AppEmpty description="该班级暂无考试或作业数据" />
    </div>

    <!-- 学生详情 -->
    <div v-if="selectedStudentId && !studentRecords.length && classExams.length" class="empty-panel">
      <AppEmpty :description="`${selectedStudentName || '该学生'}在本班暂无已评分记录`" />
    </div>
    <section v-if="selectedStudentId && studentRecords.length" class="content-panel student-panel" aria-labelledby="student-heading">
      <div class="section-head"><h2 id="student-heading">{{ selectedStudentName }} — 历次成绩</h2></div>
      <div class="metric-grid student-metrics">
        <div class="metric-tile"><span>平均得分率</span><strong>{{ formatPercent(studentAvgPercent) }}</strong></div>
        <div class="metric-tile"><span>及格率</span><strong>{{ formatPercent(studentPassRate) }}</strong></div>
        <div class="metric-tile"><span>参评次数</span><strong>{{ studentRecords.length }}</strong></div>
      </div>
      <div v-if="studentPercents.length" class="charts-grid">
        <div ref="studentTrendRef" class="student-trend" role="img" aria-label="学生历次成绩趋势图" />
        <div ref="studentCompareRef" class="student-compare" role="img" aria-label="学生及格次数占比图" />
      </div>
      <el-table :data="studentRecords" stripe class="student-table">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="类型" width="76" align="center">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ assessmentTypeLabel(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="title" label="考试/作业" min-width="200" />
        <el-table-column label="得分 / 满分" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="percentTagType(row.percent)" size="small">{{ row.score }} / {{ row.fullScore ?? '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="得分率" width="90" align="center">
          <template #default="{ row }">{{ formatPercent(row.percent) }}</template>
        </el-table-column>
        <el-table-column label="班级得分率" width="110" align="center">
          <template #default="{ row }">{{ formatPercent(row.classPercent) }}</template>
        </el-table-column>
        <el-table-column label="差值" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.percentGap === null" class="dash">—</span>
            <span v-else :class="row.percentGap >= 0 ? 'tone-positive' : 'tone-critical'">
              {{ row.percentGap >= 0 ? '+' : '' }}{{ row.percentGap }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.submitTime ? new Date(row.submitTime).toLocaleString() : '—' }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppEmpty from '@/components/AppEmpty.vue'
import GradeDistributionChart from '@/components/charts/GradeDistributionChart.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useEchart } from '@/composables/useEchart'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { useUserStore } from '@/store/user'
import { getTeachingClasses, getClassStudents } from '@/api/teachingClass'
import { getRecords, requireSuccess, scopedTeacherParams } from '@/utils/apiHelpers'
import {
  declaredFullScore,
  formatPercent,
  isPassing,
  meanPercent,
  passRate,
  percentDistribution,
  roundPercent,
  scorePercent,
  submissionFullScore,
} from '@/utils/scoreMetrics'
import request from '@/utils/request'

const userStore = useUserStore()
const classesLoading = ref(false)
const classesFailed = ref(false)
const dataError = ref('')
const classes = ref([])
const students = ref([])
const selectedClassId = ref('')
const selectedStudentId = ref('')
const classExams = ref([])
const selectedExamId = ref('')
const examPassRef = ref(null)
const studentTrendRef = ref(null)
const studentCompareRef = ref(null)

const { loading, guarded } = useGuardedLoad()

// === computed ===
const selectedExam = computed(() => classExams.value.find(e => e.examKey === selectedExamId.value) || classExams.value[0] || null)
const selectedExamDist = computed(() => percentDistribution(selectedExam.value?.percents || []))
const selectedStudentName = computed(() => students.value.find(s => String(s.studentId) === String(selectedStudentId.value))?.studentName || '')

// Only this class's assessments: another class's results must never stand in for the selected one.
const studentRecords = computed(() => {
  const sid = String(selectedStudentId.value || '')
  if (!sid) return []
  const records = classExams.value.flatMap(exam => {
    const found = exam.studentScores.find(s => String(s.studentId) === sid)
    if (!found) return []
    return [{
      title: exam.examTitle,
      type: exam.type,
      score: found.score,
      fullScore: found.fullScore,
      percent: found.percent,
      classPercent: exam.averagePercent,
      percentGap: found.percent === null || exam.averagePercent === null
        ? null
        : roundPercent(found.percent - exam.averagePercent),
      submitTime: found.submitTime,
    }]
  })
  return records.sort((a, b) => {
    if (!a.submitTime && !b.submitTime) return 0
    if (!a.submitTime) return 1
    if (!b.submitTime) return -1
    return new Date(a.submitTime) - new Date(b.submitTime)
  })
})
const studentPercents = computed(() => studentRecords.value.map(r => r.percent).filter(p => p !== null))
const studentAvgPercent = computed(() => meanPercent(studentPercents.value))
const studentPassRate = computed(() => passRate(studentPercents.value))

const overviewStudents = computed(() => {
  const map = new Map()
  const blankRow = (studentId, studentName) => ({ studentId, studentName, examStates: {}, graded: [] })
  // 以完整班级名单初始化，确保未参加考试的学生也会显示。
  students.value.forEach(s => {
    const sid = String(s.studentId)
    if (!sid) return
    map.set(sid, blankRow(sid, s.studentName))
  })
  // 合并考试/作业状态；只有已评分且满分已知的记录进入比例统计。
  classExams.value.forEach(exam => {
    ;(exam.studentStates || []).forEach(s => {
      const id = String(s.studentId)
      if (!id) return
      if (!map.has(id)) map.set(id, blankRow(id, s.studentName || ('学生'+id)))
      const row = map.get(id)
      row.examStates[exam.examKey] = s
      if (s.status === 'GRADED' && s.percent !== null) {
        row.graded.push({ percent: s.percent, time: new Date(s.submitTime || exam.startTime || 0).getTime() })
      }
    })
  })
  return Array.from(map.values()).map(s => {
    const ordered = [...s.graded].sort((a, b) => a.time - b.time).map(item => item.percent)
    return {
      ...s,
      percents: ordered,
      avgPercent: meanPercent(ordered),
      trend: ordered.length >= 2 ? roundPercent(ordered[ordered.length - 1] - ordered[ordered.length - 2]) : null,
    }
  }).sort((a,b) => (b.avgPercent ?? -1) - (a.avgPercent ?? -1))
})
const allPercents = computed(() => overviewStudents.value.flatMap(s => s.percents))
const unknownFullCount = computed(() => classExams.value.reduce((sum, exam) => sum + exam.unknownFullCount, 0))
const overviewClassPercent = computed(() => meanPercent(allPercents.value))
const overviewClassPassRate = computed(() => passRate(allPercents.value))
const overviewAboveAvg = computed(() => {
  const average = overviewClassPercent.value
  if (average === null) return 0
  return overviewStudents.value.filter(s => s.avgPercent !== null && s.avgPercent >= average).length
})
const overviewNeedHelp = computed(() => overviewStudents.value.filter(s => s.avgPercent !== null && !isPassing(s.avgPercent)).length)

function averageTone(percent) {
  if (percent >= 80) return 'tone-positive'
  return isPassing(percent) ? 'tone-accent' : 'tone-critical'
}

function percentTagType(percent) {
  if (percent === null) return 'info'
  if (percent >= 80) return 'success'
  return isPassing(percent) ? 'primary' : 'danger'
}

function assessmentTypeLabel(type) {
  return type === 'HOMEWORK' ? '作业' : '考试'
}

function normalizeSubmissionState(value) {
  if (value === null || value === undefined || value === '') return 'IN_PROGRESS'
  const text = String(value).trim().toUpperCase()
  return ({ '0': 'IN_PROGRESS', '1': 'SUBMITTED', '2': 'GRADED', '3': 'PENDING_MANUAL_REVIEW' })[text] || text
}

function submissionScore(submission) {
  const raw = submission?.totalScore ?? submission?.score
  if (raw === null || raw === undefined || raw === '') return null
  const value = Number(raw)
  return Number.isFinite(value) ? value : null
}

function assessmentKey(type, id) {
  return `${type}_${id}`
}

function assessmentEnded(item) {
  const status = String(item?.status ?? '').toUpperCase()
  if (['CLOSED', 'RESULTS_SUBMITTED', 'ARCHIVED', '2', '3', '4'].includes(status)) return true
  const endTime = item?.endTime ? new Date(item.endTime).getTime() : NaN
  return Number.isFinite(endTime) && endTime < Date.now()
}

function buildAssessmentResult(item, submissions) {
  const statesByStudent = new Map()
  ;(submissions || []).forEach(submission => {
    const studentId = String(submission.studentId || submission.candidateId || '')
    if (!studentId) return
    // Cheating is recorded as a score of -1 and, as in the backend results, leaves the statistics.
    const status = submission.cheated ? 'CHEATED' : normalizeSubmissionState(submission.status)
    const rawScore = submissionScore(submission)
    const score = status === 'GRADED' ? rawScore : null
    const fullScore = submissionFullScore(submission) ?? item.fullScore
    statesByStudent.set(studentId, {
      studentId,
      studentName: submission.studentName || submission.candidateName || `学生${studentId}`,
      status,
      score,
      fullScore,
      percent: scorePercent(score, fullScore),
      submitTime: submission.submittedAt || null,
    })
  })

  const studentStates = Array.from(statesByStudent.values())
  const gradedStates = studentStates.filter(state => state.status === 'GRADED' && state.score !== null)
  const scores = gradedStates.map(state => state.score)
  const percents = gradedStates.map(state => state.percent).filter(percent => percent !== null)
  const knownFullScores = new Set(studentStates.map(state => state.fullScore).filter(full => full !== null))
  // Raw figures are only meaningful when every graded attempt shares one full score.
  const comparableRaw = new Set(gradedStates.map(state => state.fullScore)).size === 1
  const gradedCount = scores.length
  const pendingCount = studentStates.filter(state => ['SUBMITTED', 'PENDING_MANUAL_REVIEW'].includes(state.status)).length
  const inProgressCount = studentStates.filter(state => state.status === 'IN_PROGRESS').length
  const studentCount = students.value.length
  const notStartedCount = Math.max(studentCount - statesByStudent.size, 0)
  let workflowStatus = 'NOT_STARTED'
  if (pendingCount > 0) workflowStatus = 'PENDING_GRADING'
  else if (gradedCount > 0 && (inProgressCount > 0 || notStartedCount > 0)) workflowStatus = 'PARTIAL_RESULTS'
  else if (gradedCount > 0) workflowStatus = 'RESULTS'
  else if (inProgressCount > 0) workflowStatus = 'IN_PROGRESS'
  else if (assessmentEnded(item)) workflowStatus = 'ENDED'

  return {
    examId: item.id,
    examKey: item.examKey || assessmentKey(item.type, item.id),
    examTitle: item.title,
    type: item.type,
    status: item.status,
    startTime: item.startTime,
    endTime: item.endTime,
    scores,
    percents,
    studentStates,
    studentScores: gradedStates,
    fullScore: knownFullScores.size === 1 ? [...knownFullScores][0] : null,
    unknownFullCount: scores.length - percents.length,
    hasResults: scores.length > 0,
    gradedCount,
    pendingCount,
    inProgressCount,
    notStartedCount,
    workflowStatus,
    averageScore: comparableRaw ? roundPercent(scores.reduce((a,b)=>a+b,0)/scores.length) : null,
    averagePercent: meanPercent(percents),
    passRate: passRate(percents),
    highestScore: comparableRaw ? Math.max(...scores) : null,
    lowestScore: comparableRaw ? Math.min(...scores) : null,
  }
}

function assessmentStatusMeta(exam) {
  if (exam.workflowStatus === 'PENDING_GRADING') {
    return { label: exam.gradedCount ? '部分待批改' : '待批改', type: 'warning' }
  }
  if (exam.workflowStatus === 'PARTIAL_RESULTS') return { label: '部分出成绩', type: 'success' }
  if (exam.workflowStatus === 'RESULTS') return { label: '已出成绩', type: 'success' }
  if (exam.workflowStatus === 'IN_PROGRESS') return { label: exam.type === 'HOMEWORK' ? '进行中' : '答题中', type: 'primary' }
  if (exam.workflowStatus === 'ENDED') return { label: '已结束', type: 'info' }
  return { label: exam.type === 'HOMEWORK' ? '待完成' : '待参加', type: 'info' }
}

function assessmentSummary(exam) {
  const parts = []
  if (exam.pendingCount) parts.push(`${exam.pendingCount} 份待批改`)
  if (exam.inProgressCount) parts.push(`${exam.inProgressCount} 人${exam.type === 'HOMEWORK' ? '正在完成' : '正在答题'}`)
  if (exam.notStartedCount) parts.push(`${exam.notStartedCount} 人${exam.type === 'HOMEWORK' ? '待完成' : '待参加'}`)
  if (parts.length) return `${parts.join(' · ')}，暂不纳入成绩统计`
  if (exam.workflowStatus === 'ENDED') return '已结束，暂无可统计成绩'
  return `${exam.notStartedCount || students.value.length} 人${exam.type === 'HOMEWORK' ? '待完成' : '待参加'}`
}

function assessmentProgressText(exam) {
  if (!exam.hasResults) return ''
  const parts = []
  if (exam.pendingCount) parts.push(`待批改 ${exam.pendingCount}`)
  if (exam.inProgressCount) parts.push(`进行中 ${exam.inProgressCount}`)
  if (exam.notStartedCount) parts.push(`${exam.type === 'HOMEWORK' ? '未提交' : '未参加'} ${exam.notStartedCount}`)
  return parts.join(' · ')
}

function studentStateLabel(status, type) {
  if (status === 'CHEATED') return '作弊'
  if (['SUBMITTED', 'PENDING_MANUAL_REVIEW'].includes(status)) return '待批改'
  if (status === 'IN_PROGRESS') return type === 'HOMEWORK' ? '进行中' : '答题中'
  return status === 'GRADED' ? '已评分' : '状态未知'
}

function studentStateTagType(status) {
  if (status === 'CHEATED') return 'danger'
  if (status === 'GRADED') return 'success'
  if (['SUBMITTED', 'PENDING_MANUAL_REVIEW'].includes(status)) return 'warning'
  if (status === 'IN_PROGRESS') return 'primary'
  return 'info'
}

function missingStateLabel(type) {
  return type === 'HOMEWORK' ? '未提交' : '未参加'
}

function scoreBand(percent) {
  if (percent === null) return 'score-unknown'
  if (percent >= 80) return 'score-high'
  if (isPassing(percent)) return 'score-pass'
  if (percent >= 40) return 'score-low'
  return 'score-fail'
}

function selectStudent(id) {
  selectedStudentId.value = String(id || '')
}

// === data loading ===
async function loadClasses() {
  classesLoading.value = true
  classesFailed.value = false
  try {
    const res = requireSuccess(await getTeachingClasses(scopedTeacherParams(userStore, { pageSize: 100 })))
    const list = getRecords(res.data) || []
    const result = []
    for (const c of list) {
      let courseTitle = '—'
      try {
        if (c.courseId) {
          const r = await request.get(`/courses/${c.courseId}`)
          if (r.code === '200' && r.data) courseTitle = r.data.title || '—'
        }
      } catch { /* the class stays selectable without its course title */ }
      result.push({ ...c, courseTitle })
    }
    classes.value = result
  } catch {
    classes.value = []
    classesFailed.value = true
  } finally { classesLoading.value = false }
}

function handleClassChange() {
  selectedStudentId.value = ''; classExams.value = []; selectedExamId.value = ''
  students.value = []
  return loadData()
}

async function loadRoster(classId) {
  const res = await requiredRead('班级名单', getClassStudents(classId))
  const records = getRecords(res.data) || []

  // Batch fetch user names for ALL students at once; a missing name falls back to the student id
  const ids = [...new Set(records.map(r => r.studentId).filter(Boolean))]
  const nameMap = {}
  // Fetch in parallel batches of 10
  const BATCH = 10
  for (let i = 0; i < ids.length; i += BATCH) {
    const batch = ids.slice(i, i + BATCH)
    const results = await Promise.all(
      batch.map(id => request.get(`/users/${id}`, { silentError: true }).catch(() => null))
    )
    results.forEach((ur, idx) => {
      const sid = batch[idx]
      if (ur?.code === '200' && ur.data) {
        nameMap[sid] = ur.data.nickname || ur.data.username || `学生${sid}`
      } else {
        nameMap[sid] = `学生${sid}`
      }
    })
  }

  return records.map(r => ({
    studentId: String(r.studentId),
    studentName: nameMap[r.studentId] || `学生${r.studentId}`
  }))
}

// Every read here feeds a class-wide statistic, so one failure hides the statistics altogether.
async function requiredRead(label, pending) {
  try {
    return requireSuccess(await pending)
  } catch {
    throw new Error(`${label}读取失败，成绩统计已隐藏，请重试。`)
  }
}

function loadData() {
  const classId = selectedClassId.value
  dataError.value = ''
  if (!classId) {
    return guarded(async () => {})
  }
  return guarded(async isCurrent => {
    try {
      await loadClassData(classId, isCurrent)
    } catch (problem) {
      if (!isCurrent()) return
      classExams.value = []
      dataError.value = problem.message
    }
  })
}

async function loadClassData(classId, isCurrent) {
  const roster = await loadRoster(classId)
  if (!isCurrent()) return

  // Step 1: get exam and homework lists for this class
  const [examListRes, hwListRes] = await Promise.all([
    requiredRead('考试列表', request.get('/api/exams', { params: { classId, pageSize: 200 } })),
    requiredRead('作业列表', request.get('/api/homeworks', { params: { classId, pageSize: 200 } }))
  ])
  if (!isCurrent()) return

  const examItems = getRecords(examListRes.data) || []
  const hwItems = getRecords(hwListRes.data) || []

  students.value = roster
  const allItems = [
    ...examItems.map(e => ({
      id: e.id,
      examKey: assessmentKey('EXAM', e.id),
      title: e.title || e.name || '未命名考试',
      type: 'EXAM',
      status: e.status,
      startTime: e.startTime,
      endTime: e.endTime,
      fullScore: declaredFullScore(e.fullScore ?? e.totalScore),
    })),
    ...hwItems.map(h => ({
      id: h.id,
      examKey: assessmentKey('HOMEWORK', h.id),
      title: h.title || h.name || '未命名作业',
      type: 'HOMEWORK',
      status: h.status,
      startTime: h.startTime,
      endTime: h.endTime,
      fullScore: declaredFullScore(h.fullScore ?? h.totalScore),
    }))
  ]

  // Step 2: for each exam/homework, fetch ALL submissions (not paginated to 500)
  const results = await Promise.all(allItems.map(async item => {
    const url = item.type === 'HOMEWORK' ? '/api/homework-submissions' : '/api/submissions'
    const paramName = item.type === 'HOMEWORK' ? 'homeworkId' : 'examId'
    const res = await requiredRead(`“${item.title}”的提交记录`, request.get(url, { params: { [paramName]: item.id, pageSize: 5000 } }))
    return buildAssessmentResult(item, getRecords(res.data) || [])
  }))
  if (!isCurrent()) return

  classExams.value = results
  if (!results.some(item => item.examKey === selectedExamId.value)) {
    selectedExamId.value = (results.find(item => item.hasResults) || results[0])?.examKey || ''
  }
  if (!roster.some(s => s.studentId === String(selectedStudentId.value))) selectedStudentId.value = ''
}

function chooseExam(examKey) {
  selectedExamId.value = examKey
}

// === charts ===
useEchart(examPassRef, palette => {
  const percents = selectedExam.value?.percents || []
  if (!percents.length) return null
  const passed = percents.filter(isPassing).length; const failed = percents.length - passed
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' }, legend: { bottom: 0 },
    series: [{ type: 'pie', radius: ['48%','70%'], center: ['50%','45%'],
      data: [{ name: '合格', value: passed, itemStyle: { color: palette.positive } }, { name: '不及格', value: failed, itemStyle: { color: palette.critical } }],
      label: { show: false }, emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold', color: palette.strong } } }]
  }
}, () => selectedExam.value)

useEchart(studentTrendRef, palette => {
  const items = studentRecords.value.filter(r => r.percent !== null)
  if (!items.length) return null
  return {
    tooltip: { trigger: 'axis', valueFormatter: value => `${value}%` }, grid: { left: 44, right: 20, top: 20, bottom: 48 },
    xAxis: { type: 'category', data: items.map(r => r.title).map(t => t.length > 8 ? t.slice(0,8)+'…' : t), axisLabel: { rotate: 30, fontSize: 10, color: palette.subtle }, axisLine: { lineStyle: { color: palette.line } } },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%', color: palette.subtle }, splitLine: { lineStyle: { color: palette.split } } },
    series: [{ type: 'line', name: '得分率', data: items.map(r => roundPercent(r.percent)), smooth: true, symbol: 'circle', symbolSize: 8,
      lineStyle: { color: palette.accent, width: 3 }, itemStyle: { color: palette.accent },
      areaStyle: { color: palette.accent, opacity: 0.12 },
      markLine: { silent: true, data: [{ type: 'average', name: '平均得分率' }], label: { color: palette.subtle }, lineStyle: { color: palette.caution, type: 'dashed' } } }]
  }
}, studentRecords)

useEchart(studentCompareRef, palette => {
  const percents = studentPercents.value
  if (!percents.length) return null
  const passed = percents.filter(isPassing).length
  return {
    tooltip: { trigger: 'item' }, legend: { bottom: 0 },
    series: [{ type: 'pie', radius: ['48%','70%'], center: ['50%','45%'],
      data: [{ name: '及格', value: passed, itemStyle: { color: palette.positive } }, { name: '不及格', value: percents.length - passed, itemStyle: { color: palette.critical } }],
      label: { show: false }, emphasis: { label: { show: true, color: palette.strong } } }]
  }
}, studentRecords)

function csvCell(value) {
  const text = String(value)
  // Names and titles are user-entered; a spreadsheet must not run them as formulas.
  const safe = /^[=+\-@\t\r]/.test(text) ? `'${text}` : text
  return `"${safe.replace(/"/g, '""')}"`
}

function exportOverview() {
  const header = [
    '排名',
    '学生',
    ...classExams.value.map(e => `【${assessmentTypeLabel(e.type)}】${e.examTitle}（满分${e.fullScore ?? '未知'}）`),
    '已评分平均得分率',
    '得分率趋势（百分点）',
  ]
  const rows = overviewStudents.value.map((s,i) => [
    i+1,
    s.studentName,
    ...classExams.value.map(e => {
      const state = s.examStates[e.examKey]
      if (!state) return missingStateLabel(e.type)
      return state.status === 'GRADED' && state.score !== null ? state.score : studentStateLabel(state.status, e.type)
    }),
    formatPercent(s.avgPercent),
    s.trend>0?`↑${s.trend}`:s.trend<0?`↓${Math.abs(s.trend)}`:'—'
  ])
  const csv = [header, ...rows].map(r => r.map(csvCell).join(',')).join('\n')
  const blob = new Blob(['﻿'+csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = '班级成绩总览.csv'; a.click(); URL.revokeObjectURL(a.href)
}

onMounted(loadClasses)
</script>

<style scoped>
/* Inline form items size to their content, so a percentage width collapses the select;
   cap it by the viewport instead. */
.class-select { width: min(320px, calc(100vw - 96px)); }
.filter-panel :deep(.el-form--inline .class-select) { width: min(320px, calc(100vw - 96px)); }

.metric-tile--attention > strong { color: var(--critical); }
.tone-positive { color: var(--positive); }
.tone-accent { color: var(--accent); }
.tone-critical { color: var(--critical); }

/* overview table: sticky row and name columns inside its own scroll container */
.overview-table-wrap { overflow: auto; max-height: 520px; border: 1px solid var(--line-subtle); border-radius: var(--radius-md); }
.overview-table { width: 100%; border-collapse: separate; border-spacing: 0; font-size: var(--text-sm); min-width: 700px; color: var(--text-body); }
.overview-table th, .overview-table td { padding: 8px 6px; text-align: center; background: var(--surface-base); border-bottom: 1px solid var(--line-subtle); white-space: nowrap; }
.overview-table thead th { position: sticky; top: 0; z-index: 3; color: var(--text-subtle); background: var(--surface-sunken); font-weight: 600; }
.overview-table .col-fix, .overview-table tbody .col-rank, .overview-table tbody .col-name, .overview-table tbody .col-avg, .overview-table tbody .col-trend { position: sticky; z-index: 2; }
.overview-table thead .col-fix { z-index: 4; }
.col-rank { left: 0; width: 38px; color: var(--text-subtle); }
.col-name { left: 38px; min-width: 84px; max-width: 120px; text-align: left !important; }
.col-avg { right: 56px; width: 62px; }
.col-trend { right: 0; width: 56px; }
.col-exam { min-width: 68px; }
.overview-table thead .col-exam.th-active { background: var(--surface-accent-strong); }
.overview-table tbody tr { cursor: pointer; }
.overview-table tbody tr:hover td { background: var(--surface-hover); }
.overview-table tbody tr.row-sel td { background: var(--surface-accent); }
.student-link { max-width: 110px; padding: 0; overflow: hidden; color: var(--text-strong); background: none; border: 0; font-weight: 600; text-overflow: ellipsis; cursor: pointer; }
.student-link:hover { color: var(--accent); }
.exam-th { display: flex; flex-direction: column; align-items: center; gap: 2px; width: 100%; padding: 2px; background: none; border: 0; border-radius: var(--radius-xs); cursor: pointer; }
.exam-th-type { font-size: var(--text-xs); color: var(--accent); font-weight: 650; }
.exam-th-title { font-size: var(--text-xs); color: var(--text-strong); font-weight: 600; }
.exam-th-avg { font-size: var(--text-xs); color: var(--text-subtle); font-weight: 400; }
.score-cell { display: inline-block; min-width: 34px; padding: 2px 8px; border-radius: var(--radius-xs); font-size: var(--text-xs); font-weight: 650; }
.score-high { color: var(--positive); background: var(--positive-surface); }
.score-pass { color: var(--accent); background: var(--surface-accent); }
.score-low { color: var(--caution); background: var(--caution-surface); }
.score-fail { color: var(--critical); background: var(--critical-surface); }
.score-unknown { color: var(--text-body); background: var(--neutral-surface); }
.dash { color: var(--text-subtle); }
.state-cell { white-space: nowrap; }

.exam-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 240px), 1fr)); gap: var(--space-3); }
.exam-card { display: block; width: 100%; min-width: 0; padding: var(--space-4); color: inherit; text-align: left; background: var(--surface-base); border: 1px solid var(--line-subtle); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); cursor: pointer; transition: border-color .15s var(--ease-standard), background-color .15s var(--ease-standard); }
.exam-card:hover { background: var(--surface-hover); border-color: var(--line-default); }
.exam-card-sel, .exam-card-sel:hover { background: var(--surface-accent); border-color: var(--line-accent); }
.exam-card-head { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-2); margin-bottom: var(--space-3); font-size: var(--text-md); }
.exam-card-head strong { min-width: 0; color: var(--text-strong); font-weight: 600; overflow-wrap: anywhere; }
.exam-card-tags { display: flex; flex-shrink: 0; flex-wrap: wrap; justify-content: flex-end; gap: var(--space-1); }
.exam-card-stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-2); }
.exam-card-empty { padding: var(--space-3) 0; color: var(--text-subtle); font-size: var(--text-xs); text-align: center; }
.exam-card-progress { margin-top: var(--space-2); color: var(--caution); font-size: var(--text-xs); }
.mini-label { display: block; font-size: var(--text-xs); color: var(--text-subtle); }
.exam-card-stats b { font-size: var(--text-lg); color: var(--text-strong); font-weight: 650; }

.charts-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-4); }
.pass-donut { width: 100%; height: 280px; }
.student-trend, .student-compare { width: 100%; height: 300px; min-width: 0; }
.student-metrics { margin-bottom: var(--space-4); }
.student-table { margin-top: var(--space-4); }

@media (max-width: 900px) {
  .charts-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
