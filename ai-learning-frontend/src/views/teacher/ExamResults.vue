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

      <el-descriptions :column="4" border class="summary">
        <el-descriptions-item label="平均分">{{ results.averageScore ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="合格率">{{ results.passRate ?? 0 }}%</el-descriptions-item>
        <el-descriptions-item label="最高分">{{ results.highestScore ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="最低分">{{ results.lowestScore ?? 0 }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="results.studentScores || []" border stripe>
        <template #empty><AppEmpty /></template>
        <el-table-column prop="studentId" label="学生ID" width="100" />
        <el-table-column prop="studentName" label="学生" min-width="140" />
        <el-table-column label="成绩" width="120">
          <template #default="{ row }">{{ row.totalScore ?? 0 }} / {{ results.fullScore ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
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
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppEmpty from '@/components/AppEmpty.vue'
import { exportExamResults, getExamResults } from '@/api/exam'
import request from '@/utils/request'

const route = useRoute()
const examId = Number(route.params.id)
const loading = ref(false)
const exporting = ref(false)
const results = ref({})
const paperVisible = ref(false)
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
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.summary,
.paper-summary {
  margin-bottom: 20px;
}

.answer-item {
  border-bottom: 1px solid #ebeef5;
  padding: 14px 0;
  display: grid;
  gap: 8px;
}

.question {
  font-weight: 600;
}
</style>
