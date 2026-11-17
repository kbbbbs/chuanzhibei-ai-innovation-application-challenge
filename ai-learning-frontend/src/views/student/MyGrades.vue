<template>
  <div class="my-grades workspace-page">
    <PageHeader eyebrow="学习" title="我的成绩" description="汇总考试与作业成绩。统计只计入已批改且有效的记录。">
      <template #actions>
        <el-button :loading="loading" @click="loadGrades">刷新</el-button>
      </template>
    </PageHeader>

    <ErrorNotice
      v-if="failedTypes.length"
      :title="loadProblem.title"
      :message="loadProblem.message"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadGrades"
    />

    <!-- 成绩统计面板：只有全部成绩读取成功才统计，避免用不完整数据给出总览 -->
    <section v-if="statsReady" class="metric-grid" aria-label="成绩概览">
      <div class="metric-tile">
        <span>平均得分率</span>
        <strong :class="percentTone(overallPercent)">{{ formatPercent(overallPercent) }}</strong>
      </div>
      <div class="metric-tile"><span>已出成绩</span><strong>{{ gradedAll.length }}</strong></div>
      <div class="metric-tile">
        <span>最近趋势（百分点）</span>
        <strong v-if="latestTrend === null">—</strong>
        <strong v-else :class="latestTrend >= 0 ? 'tone-positive' : 'tone-critical'">{{ latestTrend >= 0 ? '↑' : '↓' }}{{ Math.abs(latestTrend) }}</strong>
      </div>
      <div class="metric-tile"><span>及格率</span><strong>{{ formatPercent(overallPassRate) }}</strong></div>
    </section>
    <p v-if="statsReady" class="note-strip">得分率与及格率按每次考试或作业的实际满分换算，及格线为满分的 60%，每次记录权重相同。<template v-if="unknownFullCount"> {{ unknownFullCount }} 条成绩满分未知，未计入比例统计。</template></p>

    <!-- 图表区域 -->
    <div v-if="statsReady && gradedKnown.length" class="charts-row">
      <section class="content-panel" aria-labelledby="trend-heading">
        <div class="section-head"><h2 id="trend-heading">得分率趋势</h2></div>
        <ScoreTrendChart :data="trendData" height="280px" unit="%" />
      </section>
      <section class="content-panel" aria-labelledby="distribution-heading">
        <div class="section-head">
          <h2 id="distribution-heading">得分率分布</h2>
          <el-radio-group v-model="distType" size="small" aria-label="图表类型">
            <el-radio-button label="bar">柱状图</el-radio-button>
            <el-radio-button label="pie">饼图</el-radio-button>
          </el-radio-group>
        </div>
        <GradeDistributionChart :data="distData" :type="distType" height="280px" unit="%" />
      </section>
      <section class="content-panel" aria-labelledby="subject-heading">
        <div class="section-head"><h2 id="subject-heading">考试与作业对比</h2></div>
        <SubjectBreakdownChart :data="subjectData" height="280px" unit="%" />
      </section>
      <section v-if="compareData.length" class="content-panel" aria-labelledby="compare-heading">
        <div class="section-head"><h2 id="compare-heading">班级平均对比</h2></div>
        <ClassAverageChart :data="compareData" height="280px" unit="%" />
      </section>
    </div>

    <section v-if="failedTypes.length < ASSESSMENT_SOURCES.length" class="table-panel" v-loading="loading" aria-label="成绩记录">
      <el-table :data="grades" stripe>
        <template #empty><AppEmpty description="暂无成绩记录" /></template>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.assessmentType === 'HOMEWORK' ? '作业' : '考试' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="examTitle" label="名称" min-width="200" />
        <el-table-column prop="courseTitle" label="课程" min-width="120" />
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
        <el-table-column label="总分 / 满分" width="120">
          <template #default="{ row }">
            <span v-if="row.cheated" class="score-cheated">作弊（-1）</span>
            <span v-else-if="row.totalScore !== null" class="score-total">
              {{ row.totalScore }} / {{ row.fullScore ?? '未知' }}
            </span>
            <span v-else class="score-pending">批改中</span>
          </template>
        </el-table-column>
        <el-table-column label="得分率" width="90">
          <template #default="{ row }">{{ row.status === 2 && !row.cheated ? formatPercent(row.percent) : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.cheated" type="danger">作弊</el-tag>
            <el-tag v-else-if="row.status === 2" type="success">已批改</el-tag>
            <el-tag v-else type="warning">批改中</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleView(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="allGrades.length"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import ScoreTrendChart from '@/components/charts/ScoreTrendChart.vue'
import GradeDistributionChart from '@/components/charts/GradeDistributionChart.vue'
import SubjectBreakdownChart from '@/components/charts/SubjectBreakdownChart.vue'
import ClassAverageChart from '@/components/charts/ClassAverageChart.vue'
import { getRecords, normalizeStatus, paginateRecords } from '@/utils/apiHelpers'
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

const ASSESSMENT_SOURCES = [
  { type: 'EXAM', label: '考试', url: '/api/submissions' },
  { type: 'HOMEWORK', label: '作业', url: '/api/homework-submissions' },
]

const router = useRouter()
const userStore = useUserStore()

const allGrades = ref([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})
const grades = computed(() => paginateRecords(allGrades.value, pagination.pageNum, pagination.pageSize))
const loading = ref(false)
const failedTypes = ref([])
const distType = ref('bar')
let loadRun = 0

const loadProblem = computed(() => {
  const failed = ASSESSMENT_SOURCES.filter(source => failedTypes.value.includes(source.type))
  if (failed.length === ASSESSMENT_SOURCES.length) {
    return { title: '成绩未加载', message: '考试和作业成绩都未读取成功，请确认网络后重试。' }
  }
  const loaded = ASSESSMENT_SOURCES.find(source => !failedTypes.value.includes(source.type))
  return {
    title: `${failed[0]?.label}成绩未加载`,
    message: `下方只列出${loaded?.label}记录。成绩概览和图表已隐藏，避免用不完整的数据统计。`,
  }
})

const gradedAll = computed(() =>
  allGrades.value.filter(g => g.status === 2 && g.totalScore != null && !g.cheated)
)
const statsReady = computed(() => !failedTypes.value.length && gradedAll.value.length > 0)

// Scores only compare as a share of their own full score; unknown ones stay out of the ratios.
const gradedKnown = computed(() => gradedAll.value.filter(g => g.percent !== null))
const gradedPercents = computed(() => gradedKnown.value.map(g => g.percent))
const unknownFullCount = computed(() => gradedAll.value.length - gradedKnown.value.length)
const overallPercent = computed(() => meanPercent(gradedPercents.value))
const overallPassRate = computed(() => passRate(gradedPercents.value))

const latestTrend = computed(() => {
  const items = gradedKnown.value
  if (items.length < 2) return null
  const sorted = [...items].sort((a, b) => new Date(b.submitTime) - new Date(a.submitTime))
  return roundPercent(sorted[0].percent - sorted[1].percent)
})

const percentTone = (percent) => {
  if (percent === null) return ''
  if (percent >= 80) return 'tone-positive'
  return isPassing(percent) ? 'tone-accent' : 'tone-critical'
}

// Submissions carry no course, so the honest grouping available here is the assessment type.
const subjectData = computed(() =>
  ASSESSMENT_SOURCES.flatMap(({ type, label }) => {
    const percents = gradedKnown.value.filter(g => g.assessmentType === type).map(g => g.percent)
    return percents.length ? [{ name: label, score: roundPercent(meanPercent(percents)) }] : []
  })
)

const trendData = computed(() =>
  gradedKnown.value.map(g => ({
    date: g.submitTime ? new Date(g.submitTime).toLocaleDateString('zh-CN') : '-',
    score: roundPercent(g.percent),
    name: g.examTitle
  }))
)

const distData = computed(() => percentDistribution(gradedPercents.value))

const compareData = computed(() =>
  gradedKnown.value.filter(g => g.classAvgScore != null).map(g => ({
    name: g.examTitle || g.courseTitle || '-',
    myScore: roundPercent(g.percent),
    avgScore: roundPercent(scorePercent(g.classAvgScore, g.fullScore))
  }))
)

const mapSubmission = (sub, { type, label }) => {
  const fullScore = submissionFullScore(sub) ?? declaredFullScore(sub.fullScore)
  return {
    ...sub,
    assessmentType: type,
    examTitle: sub.examName || `${label}${sub.examId}`,
    courseTitle: '-',
    submitTime: sub.submittedAt,
    fullScore,
    percent: sub.cheated ? null : scorePercent(sub.totalScore, fullScore),
    status: normalizeStatus(sub.status, {
      IN_PROGRESS: 0,
      SUBMITTED: 1,
      PENDING_MANUAL_REVIEW: 1,
      GRADED: 2,
    }, 0),
  }
}

const loadGrades = async () => {
  const run = ++loadRun
  const params = { studentId: userStore.userId, pageSize: 100 }
  loading.value = true
  // Both sources are required: one alone must never read as the student's complete record.
  const results = await Promise.allSettled(ASSESSMENT_SOURCES.map(source => request.get(source.url, { params })))
  if (run !== loadRun) return

  const failed = []
  const submissions = []
  results.forEach((result, index) => {
    const source = ASSESSMENT_SOURCES[index]
    if (result.status === 'fulfilled' && result.value?.code === '200') {
      submissions.push(...getRecords(result.value.data).map(sub => mapSubmission(sub, source)))
    } else {
      failed.push(source.type)
    }
  })

  failedTypes.value = failed
  allGrades.value = submissions.filter(sub => sub.status >= 1)
  normalizePage()
  loading.value = false
}

const normalizePage = () => {
  const maxPage = Math.max(1, Math.ceil(allGrades.value.length / pagination.pageSize))
  if (pagination.pageNum > maxPage) {
    pagination.pageNum = maxPage
  }
}

const handleSizeChange = () => {
  pagination.pageNum = 1
}

const handleView = (row) => {
  router.push({
    name: 'ExamResult',
    params: { submissionId: row.id },
    query: row.assessmentType === 'HOMEWORK' ? { assessmentType: 'HOMEWORK' } : undefined
  })
}

onMounted(() => {
  loadGrades()
})

onBeforeUnmount(() => {
  loadRun += 1
})
</script>

<style scoped>
.tone-positive { color: var(--positive); }
.tone-accent { color: var(--accent); }
.tone-critical { color: var(--critical); }

.charts-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5);
}

.score-total {
  color: var(--accent);
  font-weight: 650;
}

.score-cheated {
  color: var(--critical);
  font-weight: 600;
}

.score-pending {
  color: var(--text-subtle);
}

@media (max-width: 900px) {
  .charts-row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
