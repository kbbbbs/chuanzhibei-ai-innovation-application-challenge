<template>
  <div class="exam-results">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>考试详情：{{ results.examName || '-' }}</span>
          <div>
            <el-button :loading="exporting" @click="handleExport">导出成绩表</el-button>
            <el-button @click="$router.back()">返回</el-button>
          </div>
        </div>
      </template>

      <!-- 统计卡片 -->
      <div class="stats-row">
        <div class="stat-card">
          <span class="stat-value">{{ results.averageScore ?? 0 }}</span>
          <span class="stat-label">平均分</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ results.passRate ?? 0 }}%</span>
          <span class="stat-label">合格率</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ results.highestScore ?? 0 }}</span>
          <span class="stat-label">最高分</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ results.lowestScore ?? 0 }}</span>
          <span class="stat-label">最低分</span>
        </div>
      </div>

      <!-- 成绩分布图表 -->
      <div v-if="studentScores.length" class="charts-row">
        <el-card class="chart-card">
          <template #header><span>成绩分布</span></template>
          <GradeDistributionChart :data="chartDistData" type="bar" height="260px" />
        </el-card>
        <el-card class="chart-card">
          <template #header><span>合格率</span></template>
          <div ref="passChartRef" class="pass-donut" />
        </el-card>
      </div>

      <!-- 学生列表 - 带颜色标记 -->
      <el-table :data="results.studentScores || []" border stripe>
        <template #empty><AppEmpty /></template>
        <el-table-column label="排名" width="70">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column prop="studentName" label="学生" min-width="120" />
        <el-table-column label="成绩" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="scoreTag(row.totalScore)" effect="dark" size="small">
              {{ row.totalScore ?? 0 }} / {{ results.fullScore ?? 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.absent" type="info">未参加</el-tag>
            <el-tag v-else-if="row.submissionStatus === 'GRADED'" type="success">已批改</el-tag>
            <el-tag v-else type="warning">待批改</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :disabled="!row.submissionId" @click="handleViewPaper(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

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
import { computed, nextTick, ref, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import AppEmpty from '@/components/AppEmpty.vue'
import GradeDistributionChart from '@/components/charts/GradeDistributionChart.vue'
import { exportExamResults, getExamResults } from '@/api/exam'
import request from '@/utils/request'

const route = useRoute()
const examId = Number(route.params.id)
const loading = ref(false)
const exporting = ref(false)
const results = ref({})
const paperVisible = ref(false)
const passChartRef = ref(null)
let passChartInstance = null

const studentScores = computed(() => results.value.studentScores || [])

const chartDistData = computed(() => {
  const buckets = { '0-59': 0, '60-69': 0, '70-79': 0, '80-89': 0, '90-100': 0 }
  studentScores.value.forEach(s => {
    const v = Number(s.totalScore) || 0
    if (v < 60) buckets['0-59']++
    else if (v < 70) buckets['60-69']++
    else if (v < 80) buckets['70-79']++
    else if (v < 90) buckets['80-89']++
    else buckets['90-100']++
  })
  return Object.entries(buckets).map(([range, count]) => ({ range, count }))
})

function scoreTag(score) {
  const v = Number(score) || 0
  if (v >= 80) return 'success'
  if (v >= 60) return ''
  return 'danger'
}

function renderPassDonut() {
  if (!passChartRef.value) return
  if (passChartInstance) passChartInstance.dispose()
  passChartInstance = echarts.init(passChartRef.value)
  const total = studentScores.value.length || 1
  const passed = studentScores.value.filter(s => (Number(s.totalScore) || 0) >= (Number(results.value.fullScore) || 100) * 0.6).length
  const failed = total - passed
  passChartInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['50%', '72%'], center: ['50%', '45%'],
      data: [
        { name: '合格', value: passed, itemStyle: { color: '#67C23A' } },
        { name: '不及格', value: failed, itemStyle: { color: '#F56C6C' } }
      ],
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } }
    }]
  })
}
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

const loadResults = async () => {
  loading.value = true
  try {
    const res = await getExamResults(examId)
    if (res.code === '200') results.value = res.data || {}
    await nextTick()
    renderPassDonut()
  } catch (e) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

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

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const resolveResourceUrl = (url) => {
  if (!url || /^(https?:)?\/\//i.test(url)) return url || ''
  return url.startsWith('/') ? `${apiBaseUrl}${url}` : url
}

watch(() => route.params.id, loadResults, { immediate: true })
onBeforeUnmount(() => { passChartInstance?.dispose() })
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.stat-card {
  background: var(--bg-elevated); border-radius: 14px; padding: 20px;
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  border: 1px solid var(--border-color); box-shadow: var(--card-shadow);
}
.stat-value { font-size: 28px; font-weight: 800; color: #303133; }
.stat-label { font-size: 12px; color: #909399; }

.charts-row { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px; }
.chart-card :deep(.el-card__header) { display: flex; align-items: center; }
.pass-donut { width: 100%; height: 260px; }

.summary, .paper-summary { margin-bottom: 20px; }
.answer-item { border-bottom: 1px solid #ebeef5; padding: 14px 0; display: grid; gap: 8px; }
.question { font-weight: 600; }

@media (max-width: 768px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .charts-row { grid-template-columns: 1fr; }
}</style>
