<template>
  <div class="my-grades">
    <div v-if="gradedList.length" class="charts-row">
      <el-card class="chart-card">
        <template #header><span>成绩趋势</span></template>
        <ScoreTrendChart :data="trendData" height="280px" />
      </el-card>
      <el-card class="chart-card">
        <template #header>
          <span>成绩分布</span>
          <el-radio-group v-model="distType" size="small" style="margin-left:12px">
            <el-radio-button label="bar">柱状图</el-radio-button>
            <el-radio-button label="pie">饼图</el-radio-button>
          </el-radio-group>
        </template>
        <GradeDistributionChart :data="distData" :type="distType" height="280px" />
      </el-card>
    </div>

    <el-card v-loading="loading">
      <template #header>
        <span>我的成绩</span>
      </template>

      <el-table :data="grades" border stripe>
        <template #empty><AppEmpty /></template>
        <el-table-column prop="examTitle" label="考试名称" />
        <el-table-column prop="courseTitle" label="课程" />
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
        <el-table-column label="总分" width="100">
          <template #default="{ row }">
            <span v-if="row.totalScore !== null" class="score-total">
              {{ row.totalScore }}
            </span>
            <span v-else class="score-pending">批改中</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 2" type="success">已批改</el-tag>
            <el-tag v-else type="warning">批改中</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="handleView(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="allGrades.length"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import ScoreTrendChart from '@/components/charts/ScoreTrendChart.vue'
import GradeDistributionChart from '@/components/charts/GradeDistributionChart.vue'
import { getRecords, normalizeStatus, paginateRecords } from '@/utils/apiHelpers'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()

const allGrades = ref([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})
const grades = computed(() => paginateRecords(allGrades.value, pagination.pageNum, pagination.pageSize))
const loading = ref(false)
const distType = ref('bar')

const gradedList = computed(() =>
  grades.value.filter(g => g.status === 2 && g.totalScore != null)
)

const trendData = computed(() =>
  gradedList.value.map(g => ({
    date: g.submitTime ? new Date(g.submitTime).toLocaleDateString('zh-CN') : '-',
    score: Number(g.totalScore),
    name: g.examTitle
  }))
)

const distData = computed(() => {
  const buckets = { '0-59': 0, '60-69': 0, '70-79': 0, '80-89': 0, '90-100': 0 }
  gradedList.value.forEach(g => {
    const s = Number(g.totalScore)
    if (s < 60) buckets['0-59']++
    else if (s < 70) buckets['60-69']++
    else if (s < 80) buckets['70-79']++
    else if (s < 90) buckets['80-89']++
    else buckets['90-100']++
  })
  return Object.entries(buckets).map(([range, count]) => ({ range, count }))
})

const loadGrades = async () => {
  loading.value = true
  try {
    const params = { studentId: userStore.userId, pageSize: 100 }
    const [examRes, homeworkRes] = await Promise.all([
      request.get('/api/submissions', { params }),
      request.get('/api/homework-submissions', { params })
    ])
    const responses = [
      { response: examRes, assessmentType: 'EXAM' },
      { response: homeworkRes, assessmentType: 'HOMEWORK' }
    ].filter(({ response }) => response.code === '200')
    const submissions = responses.flatMap(({ response, assessmentType }) =>
      getRecords(response.data).map(sub => ({
        ...sub,
        assessmentType,
        examTitle: sub.examName || `${assessmentType === 'HOMEWORK' ? '作业' : '考试'}${sub.examId}`,
        courseTitle: '-',
        submitTime: sub.submittedAt,
        status: normalizeStatus(sub.status, {
          IN_PROGRESS: 0,
          SUBMITTED: 1,
          PENDING_MANUAL_REVIEW: 1,
          GRADED: 2,
        }, 0),
      }))
    )

    allGrades.value = submissions.filter(sub => sub.status >= 1)
    normalizePage()
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    loading.value = false
  }
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
</script>

<style scoped>
.my-grades {
  max-width: 1200px;
  margin: 0 auto;
}

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.chart-card :deep(.el-card__header) {
  display: flex;
  align-items: center;
}

.score-total {
  font-weight: bold;
  color: #409eff;
}

.score-pending {
  color: #999;
}
</style>
