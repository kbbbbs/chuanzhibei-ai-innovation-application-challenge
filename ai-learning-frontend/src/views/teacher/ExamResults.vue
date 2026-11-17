<template>
  <div class="exam-results workspace-page" v-loading="loading">
    <PageHeader
      eyebrow="作业与考试"
      :title="`考试详情：${results.examName || '-'}`"
      description="成绩已提交后的结果汇总，可查看每位学生的答卷。"
    >
      <template #actions>
        <el-button :loading="exporting" @click="handleExport">导出成绩表</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </template>
    </PageHeader>

    <ErrorNotice
      v-if="loadFailed"
      title="考试结果未加载"
      message="成绩可能尚未提交，或读取失败，请稍后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadResults"
    />

    <template v-else>
    <!-- 统计卡片 -->
    <section class="metric-grid" aria-label="考试统计">
      <div class="metric-tile"><span>平均分</span><strong>{{ results.averageScore ?? 0 }}</strong></div>
      <div class="metric-tile"><span>合格率</span><strong>{{ results.passRate ?? 0 }}%</strong></div>
      <div class="metric-tile"><span>最高分</span><strong>{{ results.highestScore ?? 0 }}</strong></div>
      <div class="metric-tile"><span>最低分</span><strong>{{ results.lowestScore ?? 0 }}</strong></div>
    </section>

    <!-- 成绩分布图表 -->
    <div v-if="studentScores.length" class="charts-row">
      <section class="content-panel" aria-labelledby="distribution-heading">
        <div class="section-head"><h2 id="distribution-heading">成绩分布</h2></div>
        <GradeDistributionChart :data="chartDistData" type="bar" height="260px" unit="%" />
      </section>
      <section class="content-panel" aria-labelledby="pass-heading">
        <div class="section-head"><h2 id="pass-heading">合格率</h2></div>
        <div ref="passChartRef" class="pass-donut" role="img" aria-label="合格与不及格人数占比图" />
      </section>
    </div>

    <!-- 学生列表 - 带颜色标记 -->
    <section class="table-panel" aria-label="学生成绩">
      <el-table :data="results.studentScores || []" stripe>
        <template #empty><AppEmpty /></template>
        <el-table-column label="排名" width="70">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column prop="studentName" label="学生" min-width="120" />
        <el-table-column label="成绩" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.cheated ? 'danger' : scoreTag(row.totalScore)" size="small">
              {{ row.cheated ? '作弊（-1）' : `${row.totalScore ?? 0} / ${fullScore ?? '未知'}` }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.absent" type="info">未参加</el-tag>
            <el-tag v-else-if="row.cheated" type="danger">作弊</el-tag>
            <el-tag v-else-if="row.submissionStatus === 'GRADED'" type="success">已批改</el-tag>
            <el-tag v-else type="warning">待批改</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="切屏" width="80" align="center">
          <template #default="{ row }">{{ row.absent ? '-' : (row.screenSwitchCount || 0) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :disabled="!row.submissionId" @click="handleViewPaper(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
    </template>

    <el-dialog v-model="paperVisible" :title="`${currentStudent.studentName || ''}的试卷`" width="900px" top="5vh">
      <div v-if="paper.answers?.length">
        <el-descriptions :column="3" border class="paper-summary">
          <el-descriptions-item label="总分">{{ paper.totalScore ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="客观题得分">{{ paper.objectiveScore ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="主观题得分">{{ paper.subjectiveScore ?? 0 }}</el-descriptions-item>
        </el-descriptions>
        <section v-for="(answer, index) in paper.answers" :key="answer.id" class="answer-item">
          <div class="question">{{ index + 1 }}. {{ answer.questionContent }}（{{ answer.maxScore }}分）</div>
          <div v-if="answer.questionType === 'FILE_UPLOAD' && answer.answerContent">
            学生答案：<el-link :href="resolveResourceUrl(answer.answerContent)" target="_blank" type="primary">查看上传文件</el-link>
          </div>
          <div v-else>学生答案：{{ answer.answerContent || '未作答' }}</div>
          <div>得分：{{ answer.score ?? 0 }} / {{ answer.maxScore }}</div>
        </section>
      </div>
      <AppEmpty v-else />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppEmpty from '@/components/AppEmpty.vue'
import GradeDistributionChart from '@/components/charts/GradeDistributionChart.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { useEchart } from '@/composables/useEchart'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { exportExamResults, getExamResults } from '@/api/exam'
import { requireSuccess } from '@/utils/apiHelpers'
import { resolveResourceUrl } from '@/utils/backendUrl'
import { declaredFullScore, isPassing, percentDistribution, scorePercent } from '@/utils/scoreMetrics'
import request from '@/utils/request'

const route = useRoute()
const examId = Number(route.params.id)
const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  results.value = {}
})
const exporting = ref(false)
const results = ref({})
const paperVisible = ref(false)
const passChartRef = ref(null)

const studentScores = computed(() => results.value.studentScores || [])
const fullScore = computed(() => declaredFullScore(results.value.fullScore))
const scoredPercents = computed(() => studentScores.value
  .filter(s => !s.cheated)
  .map(s => scorePercent(Number(s.totalScore) || 0, fullScore.value))
  .filter(percent => percent !== null))

const chartDistData = computed(() => percentDistribution(scoredPercents.value))

function scoreTag(score) {
  const percent = scorePercent(Number(score) || 0, fullScore.value)
  if (percent === null) return 'info'
  if (percent >= 80) return 'success'
  return isPassing(percent) ? 'primary' : 'danger'
}

useEchart(passChartRef, palette => {
  const percents = scoredPercents.value
  if (!percents.length) return null
  const passed = percents.filter(isPassing).length
  const failed = percents.length - passed
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['50%', '72%'], center: ['50%', '45%'],
      data: [
        { name: '合格', value: passed, itemStyle: { color: palette.positive } },
        { name: '不及格', value: failed, itemStyle: { color: palette.critical } }
      ],
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold', color: palette.strong } }
    }]
  }
}, () => results.value)

const currentStudent = ref({})
const paper = ref({})

const downloadBlob = (blob, fileName) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const loadResults = () => guarded(async (isCurrent) => {
  const res = requireSuccess(await getExamResults(examId))
  if (isCurrent()) results.value = res.data || {}
})

const handleExport = async () => {
  exporting.value = true
  try {
    const blob = await exportExamResults(examId)
    downloadBlob(blob, `考试成绩_${examId}.xlsx`)
    ElMessage.success('导出成功')
  } catch (e) {
    // 全局已提示
  } finally {
    exporting.value = false
  }
}

const handleViewPaper = async (student) => {
  if (!student.submissionId) return
  try {
    const res = await request.get(`/api/submissions/${student.submissionId}/grading`)
    if (res.code === '200') {
      currentStudent.value = student
      paper.value = res.data || {}
      paperVisible.value = true
    }
  } catch (e) {
    // 全局已提示
  }
}

watch(() => route.params.id, loadResults, { immediate: true })
</script>

<style scoped>
.charts-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-5); }
.pass-donut { width: 100%; height: 260px; }

.paper-summary { margin-bottom: var(--space-5); }
.answer-item { display: grid; gap: var(--space-2); padding: var(--space-3) 0; color: var(--text-body); border-bottom: 1px solid var(--line-subtle); overflow-wrap: anywhere; }
.question { color: var(--text-strong); font-weight: 600; }

@media (max-width: 860px) {
  .charts-row { grid-template-columns: minmax(0, 1fr); }
}
</style>
