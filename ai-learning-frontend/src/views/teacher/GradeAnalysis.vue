<template>
  <div class="grade-analysis">
    <div class="page-header">
      <div>
        <h2>成绩分析</h2>
        <p>按教学班查看整体成绩矩阵、考试分布与个体学生表现。</p>
      </div>
    </div>

    <!-- 班级选择 -->
    <el-card>
      <el-form :inline="true">
        <el-form-item label="教学班">
          <el-select v-model="selectedClassId" placeholder="选择教学班" style="width: 300px" @change="handleClassChange" :loading="classesLoading">
            <el-option v-for="c in classes" :key="c.id" :label="`${c.className}（${c.courseTitle}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学生">
          <el-select v-model="selectedStudentId" placeholder="全览（可选筛选）" clearable style="width: 200px" @change="selectStudent(selectedStudentId)">
            <el-option v-for="s in students" :key="s.studentId" :label="s.studentName" :value="s.studentId" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadData">刷新数据</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="dataError" :title="dataError" type="warning" show-icon closable @close="dataError=''" style="margin-bottom:8px" />

    <!-- 班级整体成绩矩阵 -->
    <template v-if="classExams.length">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>班级成绩矩阵</span>
            <div class="header-tags">
              <el-tag size="small">学生 {{ overviewStudents.length }} 人</el-tag>
              <el-tag size="small">评测 {{ classExams.length }} 次</el-tag>
              <el-button size="small" @click="exportOverview">导出 CSV</el-button>
            </div>
          </div>
        </template>
        <div class="overview-table-wrap">
          <table class="overview-table">
            <thead>
              <tr>
                <th class="col-fix col-rank">#</th>
                <th class="col-fix col-name">学生</th>
                <th v-for="exam in classExams" :key="exam.examKey" class="col-exam"
                  :class="{ 'th-active': selectedExamId === exam.examKey }"
                  @click="selectedExamId = exam.examKey; nextTick(() => renderExamCharts())">
                  <div class="exam-th">
                    <span class="exam-th-type">{{ assessmentTypeLabel(exam.type) }}</span>
                    <span class="exam-th-title">{{ exam.examTitle.length > 7 ? exam.examTitle.slice(0,7)+'…' : exam.examTitle }}</span>
                    <span class="exam-th-avg">均{{ exam.averageScore ?? '—' }}</span>
                  </div>
                </th>
                <th class="col-fix col-avg">均分</th>
                <th class="col-fix col-trend">趋势</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(s, i) in overviewStudents" :key="s.studentId"
                :class="{ 'row-sel': selectedStudentId === s.studentId }"
                @click="selectStudent(s.studentId)">
                <td class="col-rank">{{ i + 1 }}</td>
                <td class="col-name">{{ s.studentName }}</td>
                <td v-for="exam in classExams" :key="exam.examKey" class="col-exam">
                  <template v-if="s.examStates[exam.examKey]">
                    <span v-if="s.examStates[exam.examKey].status === 'GRADED' && s.examStates[exam.examKey].score !== null"
                      class="score-cell" :style="scoreBg(s.examStates[exam.examKey].score, exam.fullScore)">
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
                  <b v-if="s.avgScore !== null" :style="{ color: s.avgScore >= 80 ? '#67C23A' : s.avgScore >= 60 ? '#409EFF' : '#F56C6C' }">{{ s.avgScore }}</b>
                  <span v-else class="dash">—</span>
                </td>
                <td class="col-trend">
                  <span v-if="s.trend > 0" style="color:#67C23A">↑{{ s.trend }}</span>
                  <span v-else-if="s.trend < 0" style="color:#F56C6C">↓{{ Math.abs(s.trend) }}</span>
                  <span v-else class="dash">—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </el-card>

      <!-- 班级统计 -->
      <div class="stats-row four-cols">
        <div class="stat-card"><span class="stat-value">{{ overviewClassAvg }}</span><span class="stat-label">已评分均分</span></div>
        <div class="stat-card"><span class="stat-value">{{ overviewClassPassRate === null ? '—' : `${overviewClassPassRate}%` }}</span><span class="stat-label">已评分合格率</span></div>
        <div class="stat-card"><span class="stat-value">{{ overviewAboveAvg }}</span><span class="stat-label">达到已评分均分</span></div>
        <div class="stat-card"><span class="stat-value" style="color:#F56C6C">{{ overviewNeedHelp }}</span><span class="stat-label">已评分需关注(&lt;60)</span></div>
      </div>

      <!-- 考试卡片 -->
      <div class="exam-cards">
        <el-card v-for="exam in classExams" :key="exam.examKey" class="exam-card"
          :class="{ 'exam-card-sel': selectedExamId === exam.examKey }"
          shadow="hover" @click="selectedExamId = exam.examKey; nextTick(() => renderExamCharts())">
          <div class="exam-card-head">
            <strong>{{ exam.examTitle }}</strong>
            <div class="exam-card-tags">
              <el-tag type="info" effect="plain" size="small">{{ assessmentTypeLabel(exam.type) }}</el-tag>
              <el-tag :type="assessmentStatusMeta(exam).type" size="small">{{ assessmentStatusMeta(exam).label }}</el-tag>
            </div>
          </div>
          <div class="exam-card-stats" v-if="exam.hasResults">
            <div><span class="mini-label">均分</span><b>{{ exam.averageScore }}</b></div>
            <div><span class="mini-label">合格率</span><b>{{ exam.passRate }}%</b></div>
            <div><span class="mini-label">最高</span><b>{{ exam.highestScore }}</b></div>
            <div><span class="mini-label">最低</span><b>{{ exam.lowestScore }}</b></div>
          </div>
          <div v-if="assessmentProgressText(exam)" class="exam-card-progress">{{ assessmentProgressText(exam) }}</div>
          <div v-if="!exam.hasResults" class="exam-card-empty">{{ assessmentSummary(exam) }}</div>
        </el-card>
      </div>

      <!-- 选中考试的分布图 -->
      <el-card v-if="selectedExam && selectedExam.hasResults">
        <template #header><span>{{ selectedExam.examTitle }} — 成绩分析</span></template>
        <div class="charts-grid">
          <div class="chart-half"><GradeDistributionChart :data="selectedExamDist" type="bar" height="280px" /></div>
          <div class="chart-half"><div ref="examPassRef" class="pass-donut" /></div>
        </div>
      </el-card>
    </template>

    <el-empty v-if="!classExams.length && !loading && selectedClassId" description="该班级暂无考试或作业数据" />

    <!-- 学生详情 -->
    <template v-if="selectedStudentId && studentRecords.length">
      <el-card style="margin-top:16px">
        <template #header>
          <span>{{ selectedStudentName }} — 历次成绩</span>
        </template>
        <div class="stats-row three-cols">
          <div class="stat-card"><span class="stat-value">{{ studentAvgScore }}</span><span class="stat-label">均分</span></div>
          <div class="stat-card"><span class="stat-value">{{ studentPassRate }}%</span><span class="stat-label">及格率</span></div>
          <div class="stat-card"><span class="stat-value">{{ studentRecords.length }}</span><span class="stat-label">参评次数</span></div>
        </div>
        <div class="charts-grid">
          <div class="chart-half"><div ref="studentTrendRef" class="student-trend" /></div>
          <div class="chart-half"><div ref="studentCompareRef" class="student-compare" /></div>
        </div>
        <el-table :data="studentRecords" border stripe style="margin-top:16px">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="类型" width="76" align="center">
            <template #default="{ row }"><el-tag size="small" effect="plain">{{ assessmentTypeLabel(row.type) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="title" label="考试/作业" min-width="200" />
          <el-table-column label="得分" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.score >= 80 ? 'success' : row.score >= 60 ? '' : 'danger'" effect="dark" size="small">{{ row.score }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="班级均分" width="100" align="center">
            <template #default="{ row }">{{ row.avgScore ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="差值" width="80" align="center">
            <template #default="{ row }">
              <span :style="{ color: row.avgScore != null && (row.score - row.avgScore) >= 0 ? '#67C23A' : '#F56C6C' }">
                {{ row.avgScore != null ? (row.score - row.avgScore >= 0 ? '+' : '') + (row.score - row.avgScore) : '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ row.submitTime ? new Date(row.submitTime).toLocaleString() : '—' }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import GradeDistributionChart from '@/components/charts/GradeDistributionChart.vue'
import { useUserStore } from '@/store/user'
import { getTeachingClasses, getClassStudents } from '@/api/teachingClass'
import { getRecords, scopedTeacherParams } from '@/utils/apiHelpers'
import request from '@/utils/request'

const userStore = useUserStore()
const loading = ref(false)
const classesLoading = ref(false)
const dataError = ref('')
const classes = ref([])
const students = ref([])
const selectedClassId = ref('')
const selectedStudentId = ref('')
const classExams = ref([])
const selectedExamId = ref('')
const studentRecords = ref([])
const examPassRef = ref(null)
const studentTrendRef = ref(null)
const studentCompareRef = ref(null)
let examPassChart = null, studentTrendChart = null, studentCompareChart = null

// === computed ===
const selectedExam = computed(() => classExams.value.find(e => e.examKey === selectedExamId.value) || classExams.value[0] || null)
const selectedExamDist = computed(() => {
  if (!selectedExam.value?.scores?.length) return []
  const b = { '0-59': 0, '60-69': 0, '70-79': 0, '80-89': 0, '90-100': 0 }
  selectedExam.value.scores.forEach(s => { const v = Number(s) || 0; const k = v < 60 ? '0-59' : v < 70 ? '60-69' : v < 80 ? '70-79' : v < 90 ? '80-89' : '90-100'; b[k]++ })
  return Object.entries(b).map(([range, count]) => ({ range, count }))
})
const selectedStudentName = computed(() => students.value.find(s => String(s.studentId) === String(selectedStudentId.value))?.studentName || '')
const studentAvgScore = computed(() => { const r = studentRecords.value; return r.length ? Math.round(r.reduce((a, b) => a + b.score, 0) / r.length) : 0 })
const studentPassRate = computed(() => { const r = studentRecords.value; return r.length ? Math.round(r.filter(x => x.score >= 60).length / r.length * 100) : 0 })

const overviewStudents = computed(() => {
  const map = new Map()
  // 以完整班级名单初始化，确保未参加考试的学生也会显示。
  students.value.forEach(s => {
    const sid = String(s.studentId)
    if (!sid) return
    map.set(sid, { studentId: sid, studentName: s.studentName, examScores: {}, examStates: {}, scoresList: [] })
  })
  // 合并考试/作业状态；只有真正已评分的记录进入成绩统计。
  classExams.value.forEach(exam => {
    ;(exam.studentStates || []).forEach(s => {
      const id = String(s.studentId)
      if (!id) return
      if (!map.has(id)) map.set(id, { studentId: id, studentName: s.studentName || ('学生'+id), examScores: {}, examStates: {}, scoresList: [] })
      const row = map.get(id)
      row.examStates[exam.examKey] = { status: s.status, score: s.score }
      if (s.status === 'GRADED' && s.score !== null) row.examScores[exam.examKey] = s.score
    })
  })
  // Build scoresList from examScores values
  return Array.from(map.values()).map(s => {
    const arr = Object.values(s.examScores).filter(v => v >= 0)
    const avg = arr.length ? Math.round(arr.reduce((a,b)=>a+b,0)/arr.length) : null
    return { ...s, scoresList: arr, avgScore: avg, trend: arr.length >= 2 ? Math.round(arr[arr.length-1]-arr[arr.length-2]) : null }
  }).sort((a,b) => (b.avgScore ?? -1) - (a.avgScore ?? -1))
})
const allScores = computed(() => overviewStudents.value.flatMap(s => s.scoresList))
const overviewClassAvg = computed(() => { const a = allScores.value; return a.length ? Math.round(a.reduce((x,y)=>x+y,0)/a.length) : '—' })
const overviewClassPassRate = computed(() => { const a = allScores.value; return a.length ? Math.round(a.filter(s=>s>=60).length/a.length*100) : null })
const overviewAboveAvg = computed(() => {
  if (!allScores.value.length) return 0
  const avg = Number(overviewClassAvg.value)
  return overviewStudents.value.filter(s => s.avgScore !== null && s.avgScore >= avg).length
})
const overviewNeedHelp = computed(() => overviewStudents.value.filter(s => s.avgScore !== null && s.avgScore < 60).length)

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
    const status = normalizeSubmissionState(submission.status)
    const rawScore = submissionScore(submission)
    const score = status === 'GRADED' ? rawScore : null
    statesByStudent.set(studentId, {
      studentId,
      studentName: submission.studentName || submission.candidateName || `学生${studentId}`,
      status,
      score,
      totalScore: score,
    })
  })

  const studentStates = Array.from(statesByStudent.values())
  const scores = studentStates.filter(state => state.status === 'GRADED' && state.score !== null).map(state => state.score)
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
    studentStates,
    studentScores: studentStates.filter(state => state.status === 'GRADED' && state.score !== null),
    fullScore: Number(item.fullScore) || 100,
    hasResults: scores.length > 0,
    gradedCount,
    pendingCount,
    inProgressCount,
    notStartedCount,
    workflowStatus,
    averageScore: scores.length ? Math.round(scores.reduce((a,b)=>a+b,0)/scores.length) : null,
    passRate: scores.length ? Math.round(scores.filter(score=>score>=60).length/scores.length*100) : null,
    highestScore: scores.length ? Math.max(...scores) : null,
    lowestScore: scores.length ? Math.min(...scores) : null,
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
  if (['SUBMITTED', 'PENDING_MANUAL_REVIEW'].includes(status)) return '待批改'
  if (status === 'IN_PROGRESS') return type === 'HOMEWORK' ? '进行中' : '答题中'
  return status === 'GRADED' ? '已评分' : '状态未知'
}

function studentStateTagType(status) {
  if (status === 'GRADED') return 'success'
  if (['SUBMITTED', 'PENDING_MANUAL_REVIEW'].includes(status)) return 'warning'
  if (status === 'IN_PROGRESS') return 'primary'
  return 'info'
}

function missingStateLabel(type) {
  return type === 'HOMEWORK' ? '未提交' : '未参加'
}

function scoreBg(score, full = 100) {
  const r = score / (full || 100)
  if (r >= 0.8) return { background: '#f0f9eb', color: '#67C23A', fontWeight: 700 }
  if (r >= 0.6) return { background: '#ecf5ff', color: '#409EFF', fontWeight: 600 }
  if (r >= 0.4) return { background: '#fdf6ec', color: '#E6A23C', fontWeight: 600 }
  return { background: '#fef0f0', color: '#F56C6C', fontWeight: 700 }
}

function selectStudent(id) {
  const sid = String(id || '')
  selectedStudentId.value = sid
  loadStudentDetail()
}

// === data loading ===
async function loadClasses() {
  classesLoading.value = true
  try {
    const res = await getTeachingClasses(scopedTeacherParams(userStore, { pageSize: 100 }))
    if (res.code === '200') {
      const list = getRecords(res.data) || []
      const result = []
      for (const c of list) {
        let courseTitle = '—'
        try {
          if (c.courseId) {
            const r = await request.get(`/courses/${c.courseId}`)
            if (r.code === '200' && r.data) courseTitle = r.data.title || '—'
          }
        } catch { /* skip */ }
        result.push({ ...c, courseTitle })
      }
      classes.value = result
    }
  } catch { classes.value = [] } finally { classesLoading.value = false }
}

async function handleClassChange() {
  selectedStudentId.value = ''; studentRecords.value = []; classExams.value = []; selectedExamId.value = ''
  students.value = []
  if (!selectedClassId.value) return

  // 1. Get class enrollment list
  try {
    const res = await getClassStudents(selectedClassId.value)
    if (res.code === '200') {
      const records = getRecords(res.data) || []
      if (!records.length) { students.value = []; loadData(); return }

      // 2. Batch fetch user names for ALL students at once
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

      students.value = records.map(r => ({
        studentId: String(r.studentId),
        studentName: nameMap[r.studentId] || `学生${r.studentId}`
      }))
    }
  } catch { students.value = [] }
  loadData()
}

function isOk(res) {
  if (!res) return false
  if (res.code === '200' || res.code === 200) return true
  if (res.success === true) return true
  return false
}

function toNum(v) { const n = Number(v); return Number.isFinite(n) && n > 0 ? n : 0 }

async function loadData() {
  if (!selectedClassId.value) return
  loading.value = true; dataError.value = ''
  try {
    // Step 1: get exam and homework lists for this class
    const [examListRes, hwListRes] = await Promise.all([
      request.get('/api/exams', { params: { classId: selectedClassId.value, pageSize: 200 } }),
      request.get('/api/homeworks', { params: { classId: selectedClassId.value, pageSize: 200 } })
    ])

    const examItems = isOk(examListRes) ? (getRecords(examListRes.data) || []) : []
    const hwItems = isOk(hwListRes) ? (getRecords(hwListRes.data) || []) : []

    const allItems = [
      ...examItems.map(e => ({
        id: e.id,
        examKey: assessmentKey('EXAM', e.id),
        title: e.title || e.name || '未命名考试',
        type: 'EXAM',
        status: e.status,
        startTime: e.startTime,
        endTime: e.endTime,
        fullScore: e.totalScore,
      })),
      ...hwItems.map(h => ({
        id: h.id,
        examKey: assessmentKey('HOMEWORK', h.id),
        title: h.title || h.name || '未命名作业',
        type: 'HOMEWORK',
        status: h.status,
        startTime: h.startTime,
        endTime: h.endTime,
        fullScore: h.totalScore,
      }))
    ]

    if (!allItems.length) {
      dataError.value = `通过考试/作业列表未找到记录。正在通过学生提交记录聚合...`
      await loadViaStudentSubmissions()
      return
    }

    // Step 2: for each exam/homework, fetch ALL submissions (not paginated to 500)
    const submissionsResults = await Promise.all(allItems.map(async item => {
      try {
        const url = item.type === 'HOMEWORK' ? '/api/homework-submissions' : '/api/submissions'
        const paramName = item.type === 'HOMEWORK' ? 'homeworkId' : 'examId'
        const res = await request.get(url, { params: { [paramName]: item.id, pageSize: 5000 } })
        const subs = isOk(res) ? (getRecords(res.data) || []) : []
        return buildAssessmentResult(item, subs)
      } catch { return null }
    }))

    classExams.value = submissionsResults.filter(Boolean)
    if (!classExams.value.length) {
      if (allItems.length) {
        dataError.value = `找到 ${allItems.length} 场考试/作业，但状态加载失败。`
      }
      await loadViaStudentSubmissions()
    } else {
      selectedExamId.value = (classExams.value.find(item => item.hasResults) || classExams.value[0]).examKey
      await nextTick(); renderExamCharts()
    }
  } catch (e) { dataError.value = '加载数据失败: ' + (e.message || '未知错误') } finally { loading.value = false }
}

// Fallback: aggregate from per-student submissions
async function loadViaStudentSubmissions() {
  if (!students.value.length) {
    dataError.value = '该班级暂无学生，无法聚合成绩数据。'
    classExams.value = []
    return
  }
  const examMap = new Map()
  const hwMap = new Map()

  // Batch students in groups of 5 for parallel requests
  const BATCH = 5
  for (let i = 0; i < students.value.length; i += BATCH) {
    const batch = students.value.slice(i, i + BATCH)
    const results = await Promise.all(batch.map(s =>
      Promise.all([
        request.get('/api/submissions', { params: { studentId: Number(s.studentId) || 0, pageSize: 200 } }),
        request.get('/api/homework-submissions', { params: { studentId: Number(s.studentId) || 0, pageSize: 200 } })
      ])
    ))
    results.forEach(([examRes, hwRes], idx) => {
      const s = batch[idx]
      if (isOk(examRes)) {
        (getRecords(examRes.data) || []).forEach(sub => {
          if (!sub.examSessionId && !sub.examId) return
          const eid = sub.examSessionId || sub.examId; const key = 'exam_' + eid
          if (!examMap.has(key)) examMap.set(key, {
            id: eid,
            examKey: assessmentKey('EXAM', eid),
            title: sub.examName || sub.examTitle || (`考试${eid}`),
            type: 'EXAM',
            submissions: [],
          })
          examMap.get(key).submissions.push({ ...sub, studentId: String(s.studentId), studentName: s.studentName })
        })
      }
      if (isOk(hwRes)) {
        (getRecords(hwRes.data) || []).forEach(sub => {
          const hid = sub.homeworkId || sub.assessmentId || sub.examId
          if (!hid) return
          const key = 'hw_' + hid
          if (!hwMap.has(key)) hwMap.set(key, {
            id: hid,
            examKey: assessmentKey('HOMEWORK', hid),
            title: sub.homeworkName || sub.examName || sub.title || (`作业${hid}`),
            type: 'HOMEWORK',
            submissions: [],
          })
          hwMap.get(key).submissions.push({ ...sub, studentId: String(s.studentId), studentName: s.studentName })
        })
      }
    })
  }

  const allExams = [...examMap.values(), ...hwMap.values()].map(item => buildAssessmentResult(item, item.submissions))

  classExams.value = allExams
  if (classExams.value.length) {
    selectedExamId.value = (classExams.value.find(item => item.hasResults) || classExams.value[0]).examKey
    dataError.value = ''
    await nextTick(); renderExamCharts()
  } else {
    dataError.value = '遍历了 ' + students.value.length + ' 名学生，未找到任何有效成绩记录。'
  }
}

async function loadStudentDetail() {
  studentRecords.value = []
  if (!selectedStudentId.value) return
  const sid = String(selectedStudentId.value)

  // Build from already-loaded classExams data (no extra API call)
  const records = []
  classExams.value.forEach(exam => {
    const found = (exam.studentScores || []).find(s => String(s.studentId) === sid)
    if (found) {
      records.push({
        title: exam.examTitle,
        type: exam.type,
        score: Number(found.totalScore) || 0,
        avgScore: exam.averageScore || 0,
        submitTime: null
      })
    }
  })

  // If not found in classExams, fallback to API
  if (!records.length) {
    const numId = toNum(sid)
    if (!numId) return
    try {
      const [examRes, hwRes] = await Promise.all([
        request.get('/api/submissions', { params: { studentId: numId, pageSize: 200 } }),
        request.get('/api/homework-submissions', { params: { studentId: numId, pageSize: 200 } })
      ])
      ;[{ response: examRes, type: 'EXAM' }, { response: hwRes, type: 'HOMEWORK' }].forEach(({ response: res, type }) => {
        if (!res || res.code !== '200') return
        (getRecords(res.data) || []).forEach(r => {
          if (normalizeSubmissionState(r.status) !== 'GRADED') return
          const score = submissionScore(r)
          if (score === null) return
          records.push({
            title: r.examName || r.homeworkName || r.title || '未命名',
            type,
            score,
            avgScore: null,
            submitTime: r.submittedAt || r.submitTime || r.createdAt
          })
        })
      })
      // Fill avg scores from classExams
      records.forEach(r => {
        const match = classExams.value.find(e => e.examTitle === r.title)
        if (match?.averageScore !== null && match?.averageScore !== undefined) r.avgScore = match.averageScore
      })
    } catch { /* ignore */ }
  }

  studentRecords.value = records.sort((a, b) => {
    if (!a.submitTime && !b.submitTime) return 0
    if (!a.submitTime) return 1
    if (!b.submitTime) return -1
    return new Date(a.submitTime) - new Date(b.submitTime)
  })
  await nextTick(); renderStudentCharts()
}

// === charts ===
function renderExamCharts() {
  if (!selectedExam.value?.scores?.length) return
  const scores = selectedExam.value.scores; const passScore = Math.round((selectedExam.value.fullScore || 100) * 0.6)
  const passed = scores.filter(s => s >= passScore).length; const failed = scores.length - passed
  if (examPassRef.value) {
    if (examPassChart) examPassChart.dispose(); examPassChart = echarts.init(examPassRef.value)
    examPassChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' }, legend: { bottom: 0 },
      series: [{ type: 'pie', radius: ['48%','70%'], center: ['50%','45%'],
        data: [{ name: '合格', value: passed, itemStyle: { color: '#67C23A' } }, { name: '不及格', value: failed, itemStyle: { color: '#F56C6C' } }],
        label: { show: false }, emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } } }]
    })
  }
}

function renderStudentCharts() {
  const items = studentRecords.value
  if (!items.length) return
  // trend
  if (studentTrendRef.value) {
    if (studentTrendChart) studentTrendChart.dispose(); studentTrendChart = echarts.init(studentTrendRef.value)
    studentTrendChart.setOption({
      tooltip: { trigger: 'axis' }, grid: { left: 44, right: 20, top: 20, bottom: 36 },
      xAxis: { type: 'category', data: items.map(r => r.title).map(t => t.length > 8 ? t.slice(0,8)+'…' : t), axisLabel: { rotate: 30, fontSize: 10 } },
      yAxis: { type: 'value', max: 100 },
      series: [{ type: 'line', data: items.map(r => r.score), smooth: true, symbol: 'circle', symbolSize: 8,
        lineStyle: { color: '#409EFF', width: 3 },
        areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1,[{ offset:0, color:'rgba(64,158,255,0.25)' },{ offset:1, color:'rgba(64,158,255,0.02)' }]) },
        markLine: { silent: true, data: [{ type: 'average', name: '均分' }], lineStyle: { color: '#E6A23C', type: 'dashed' } } }]
    })
  }
  // pass rate donut
  if (studentCompareRef.value) {
    if (studentCompareChart) studentCompareChart.dispose(); studentCompareChart = echarts.init(studentCompareRef.value)
    const passed = items.filter(r => r.score >= 60).length
    studentCompareChart.setOption({
      tooltip: { trigger: 'item' }, legend: { bottom: 0 },
      series: [{ type: 'pie', radius: ['48%','70%'], center: ['50%','45%'],
        data: [{ name: '及格', value: passed, itemStyle: { color: '#67C23A' } }, { name: '不及格', value: items.length - passed, itemStyle: { color: '#F56C6C' } }],
        label: { show: false }, emphasis: { label: { show: true } } }]
    })
  }
}

function exportOverview() {
  const header = ['排名', '学生', ...classExams.value.map(e => `【${assessmentTypeLabel(e.type)}】${e.examTitle}`), '已评分均分', '趋势']
  const rows = overviewStudents.value.map((s,i) => [
    i+1,
    s.studentName,
    ...classExams.value.map(e => {
      const state = s.examStates[e.examKey]
      if (!state) return missingStateLabel(e.type)
      return state.status === 'GRADED' && state.score !== null ? state.score : studentStateLabel(state.status, e.type)
    }),
    s.avgScore ?? '—',
    s.trend>0?`↑${s.trend}`:s.trend<0?`↓${Math.abs(s.trend)}`:'—'
  ])
  const csv = [header.join(','), ...rows.map(r => r.join(','))].join('\n')
  const blob = new Blob(['﻿'+csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = '班级成绩总览.csv'; a.click(); URL.revokeObjectURL(a.href)
}

onMounted(loadClasses)
onBeforeUnmount(() => { examPassChart?.dispose(); studentTrendChart?.dispose(); studentCompareChart?.dispose() })
</script>

<style scoped>
.grade-analysis { display: flex; flex-direction: column; gap: 16px; max-width: 1500px; }
.page-header p { margin: 6px 0 0; color: var(--text-secondary); font-size: 13px; }

.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-tags { display: flex; align-items: center; gap: 8px; }

/* overview table */
.overview-table-wrap { overflow-x: auto; max-height: 520px; overflow-y: auto; border: 1px solid #ebeef5; border-radius: 8px; }
.overview-table { width: 100%; border-collapse: collapse; font-size: 13px; min-width: 700px; }
.overview-table th, .overview-table td { padding: 7px 5px; text-align: center; border-bottom: 1px solid #ebeef5; white-space: nowrap; }
.overview-table thead th { background: #f5f7fa; position: sticky; top: 0; z-index: 3; font-weight: 600; color: #606266; }
.col-fix { position: sticky; z-index: 2; }
.col-rank { left: 0; width: 38px; color: #909399; }
.col-name { left: 38px; min-width: 72px; max-width: 100px; text-align: left !important; font-weight: 500; overflow: hidden; text-overflow: ellipsis; }
.col-avg { right: 56px; width: 50px; }
.col-trend { right: 0; width: 50px; }
.col-exam { min-width: 60px; cursor: pointer; }
.col-exam.th-active { background: #d9ecff; }
.overview-table tbody .col-rank, .overview-table tbody .col-name, .overview-table tbody .col-avg, .overview-table tbody .col-trend { background: #fafafa; }
.overview-table tbody tr:hover td { background: #f5f7fa; }
.overview-table tbody tr.row-sel td { background: #ecf5ff; }
.overview-table tbody tr.row-sel .col-rank, .overview-table tbody tr.row-sel .col-name, .overview-table tbody tr.row-sel .col-avg, .overview-table tbody tr.row-sel .col-trend { background: #d9ecff; }
.exam-th { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.exam-th-type { font-size: 9px; color: #409eff; font-weight: 700; }
.exam-th-title { font-size: 11px; color: #303133; }
.exam-th-avg { font-size: 10px; color: #909399; }
.score-cell { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; min-width: 32px; }
.score-cell.dash { color: #c0c4cc; background: transparent; }
.state-cell { white-space: nowrap; }

.stats-row { display: grid; gap: 16px; margin-bottom: 16px; }
.stats-row.four-cols { grid-template-columns: repeat(4, 1fr); }
.stats-row.three-cols { grid-template-columns: repeat(3, 1fr); }
.stat-card { background: var(--bg-elevated); border-radius: 14px; padding: 20px; display: flex; flex-direction: column; align-items: center; gap: 6px; border: 1px solid var(--border-color); box-shadow: var(--card-shadow); }
.stat-value { font-size: 28px; font-weight: 800; color: #303133; }
.stat-label { font-size: 12px; color: #909399; }

.exam-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; margin-bottom: 4px; }
.exam-card { cursor: pointer; transition: all .18s; }
.exam-card:hover { border-color: var(--primary-color-light); }
.exam-card-sel { border-color: var(--primary-color); box-shadow: 0 0 0 2px var(--primary-color-soft); }
.exam-card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-size: 14px; }
.exam-card-head strong { min-width: 0; }
.exam-card-tags { display: flex; flex-shrink: 0; gap: 6px; margin-left: 8px; }
.exam-card-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.exam-card-empty { color: #c0c4cc; font-size: 12px; text-align: center; padding: 12px 0; }
.exam-card-progress { margin-top: 10px; color: #e6a23c; font-size: 12px; }
.mini-label { display: block; font-size: 11px; color: #909399; }
.exam-card-stats b { font-size: 16px; color: #303133; }

.charts-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.chart-half { min-height: 280px; }
.pass-donut { width: 100%; height: 280px; }
.student-trend, .student-compare { width: 100%; height: 300px; }

@media (max-width: 900px) {
  .charts-grid, .stats-row.four-cols { grid-template-columns: 1fr 1fr; }
  .stats-row.three-cols { grid-template-columns: repeat(2, 1fr); }
  .exam-cards { grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); }
}
</style>
