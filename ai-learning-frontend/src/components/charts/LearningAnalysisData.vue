<template>
  <div class="analysis-data">
    <p>数据截止时间 {{ snapshot.capturedAt?.replace('T', ' ') }}，覆盖 {{ snapshot.classes.length }} 个教学班、{{ snapshot.studentCount }} 名学生。</p>
    <el-table :data="snapshot.classes" border>
      <el-table-column prop="name" label="教学班" min-width="160" />
      <el-table-column prop="studentCount" label="学生人数" width="100" />
      <el-table-column label="考试均分"><template #default="{ row }">{{ row.exam.averageScore ?? '—' }}</template></el-table-column>
      <el-table-column label="考试及格率"><template #default="{ row }">{{ percent(row.exam.passRate) }}</template></el-table-column>
      <el-table-column label="作业提交率"><template #default="{ row }">{{ percent(row.homework.submissionRate) }}</template></el-table-column>
      <el-table-column label="待批改"><template #default="{ row }">{{ row.homework.pendingCount }}</template></el-table-column>
    </el-table>
    <div class="chart-controls">
      <el-select v-model="selectedKey" aria-label="评测" placeholder="选择评测" style="width: 380px">
        <el-option v-for="assessment in snapshot.assessments" :key="key(assessment)" :value="key(assessment)"
          :label="`${assessment.className} · ${assessment.title}`" />
      </el-select>
    </div>
    <template v-if="selected">
      <p>已评分 {{ selected.stats.gradedCount }} 份 · 待批改 {{ selected.stats.pendingCount }} 份 · 进行中 {{ selected.stats.inProgressCount }} 人 · 未提交/未参加 {{ selected.stats.notStartedCount }} 人</p>
      <div class="charts">
        <GradeDistributionChart :data="distribution" type="bar" height="270px" />
        <div ref="trendRef" class="trend-chart" aria-label="各班历次百分制均分趋势" />
      </div>
      <el-table :data="selected.students" max-height="360" stripe>
        <el-table-column prop="studentName" label="学生" min-width="160" />
        <el-table-column label="状态"><template #default="{ row }">{{ labels[row.status] || row.status }}</template></el-table-column>
        <el-table-column label="原始得分"><template #default="{ row }">{{ row.score ?? '—' }}</template></el-table-column>
        <el-table-column label="百分制得分"><template #default="{ row }">{{ row.percentage ?? '—' }}</template></el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" min-width="170" />
      </el-table>
    </template>
    <el-empty v-else description="该报告暂无作业或考试记录" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import GradeDistributionChart from './GradeDistributionChart.vue'

const props = defineProps({ snapshot: { type: Object, required: true } })
const selectedKey = ref('')
const trendRef = ref(null)
let trendChart
const labels = { GRADED: '已评分', SUBMITTED: '待批改', PENDING_MANUAL_REVIEW: '待人工批改', IN_PROGRESS: '进行中', NOT_STARTED: '未提交/未参加' }
const key = assessment => `${assessment.type}_${assessment.id}`
const percent = value => value == null ? '—' : `${value}%`
const selected = computed(() => props.snapshot.assessments.find(a => key(a) === selectedKey.value) || props.snapshot.assessments[0])
const distribution = computed(() => ['0-59', '60-69', '70-79', '80-89', '90-100'].map((range, i) => ({ range, count: selected.value?.stats.distribution?.[i] || 0 })))

async function renderTrend() {
  await nextTick()
  trendChart?.dispose()
  trendChart = null
  if (!trendRef.value || !selected.value) return
  trendChart = echarts.init(trendRef.value)
  const type = selected.value.type
  const series = props.snapshot.classes.map(teachingClass => {
    const assessments = props.snapshot.assessments.filter(a => a.classId === teachingClass.id && a.type === type)
    return { name: teachingClass.name, type: 'line', connectNulls: false, data: assessments.map(a => a.stats.averageScore) }
  })
  const length = Math.max(0, ...series.map(s => s.data.length))
  trendChart.setOption({
    tooltip: { trigger: 'axis' }, legend: { bottom: 0, type: 'scroll' },
    grid: { top: 25, left: 45, right: 20, bottom: 65 },
    xAxis: { type: 'category', name: '评测次序', data: Array.from({ length }, (_, i) => i + 1) },
    yAxis: { type: 'value', min: 0, max: 100, name: '百分制均分' }, series,
  })
}
function resize() { trendChart?.resize() }
watch(() => props.snapshot, () => { selectedKey.value = ''; renderTrend() })
watch(selectedKey, renderTrend)
onMounted(() => { renderTrend(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { trendChart?.dispose(); window.removeEventListener('resize', resize) })
</script>

<style scoped>
.analysis-data { display: grid; gap: 16px; }
.analysis-data p { margin: 0; color: #606266; line-height: 1.7; }
.chart-controls { margin-top: 4px; }
.charts { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.trend-chart { height: 270px; min-width: 0; }
@media (max-width: 900px) { .charts { grid-template-columns: 1fr; } }
</style>
