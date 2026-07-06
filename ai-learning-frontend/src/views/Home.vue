<template>
  <main class="page-shell" v-loading="loading">
    <!-- 访客及未登录首页 -->
    <section v-if="!userStore.isLoggedIn" class="intro">
      <p class="eyebrow">AI Academy</p>
      <h1>AI智学平台</h1>
      <p class="summary">用于课程学习、练习测评和学习辅助的智能教学项目。</p>
      <div class="home-actions">
        <el-button type="primary" size="large" @click="router.push('/login')" class="action-btn">登录体验</el-button>
        <el-button size="large" @click="router.push('/register')" class="action-btn">免费注册</el-button>
      </div>
    </section>

      <!-- 登录用户工作台 -->
    <div v-else class="dashboard-container">
      <div class="welcome-header">
        <div class="user-greeting">
          <h2>你好，{{ userStore.nickname }}</h2>
          <p class="sub-greeting">{{ getRoleTitle(userStore.roleCode) }}专属智能教学工作台</p>
        </div>
        <div class="quick-action" v-if="userStore.roleCode === 'teacher' || userStore.roleCode === 'admin'">
          <el-button type="primary" @click="router.push('/resource-gen')">
            <el-icon class="btn-icon"><Lightning /></el-icon> AI 智能备课
          </el-button>
        </div>
      </div>

      <!-- 学生端：AI 学习待办中心 -->
      <template v-if="userStore.roleCode === 'student'">
        <div class="stat-grid">
          <el-card shadow="never" class="stat-card stat-primary">
            <div class="stat-label">待交作业</div>
            <div class="stat-value">{{ dashboardData.pendingHomework?.length || 0 }} <span class="stat-unit">项</span></div>
          </el-card>
          <el-card shadow="never" class="stat-card stat-warning">
            <div class="stat-label">待参加考试</div>
            <div class="stat-value">{{ dashboardData.upcomingExams?.length || 0 }} <span class="stat-unit">场</span></div>
          </el-card>
          <el-card shadow="never" class="stat-card stat-success">
            <div class="stat-label">学习状态</div>
            <div class="stat-value">{{ studentStatusText }} <span class="stat-unit">状态</span></div>
          </el-card>
        </div>

        <div class="content-grid">
          <!-- 待办作业列表 -->
          <el-card shadow="never" class="section-card">
            <template #header>
              <div class="card-header-flex">
                <span class="card-title"><el-icon><Tickets /></el-icon> 待办作业任务</span>
                <el-button link type="primary" @click="router.push('/my-homeworks')">查看全部</el-button>
              </div>
            </template>
            <el-table :data="dashboardData.pendingHomework || []" stripe style="width: 100%" v-if="(dashboardData.pendingHomework?.length || 0) > 0">
              <el-table-column prop="title" label="作业名称" min-width="180" />
              <el-table-column prop="deadline" label="截止倒计时" width="170">
                <template #default="{ row }">
                  <el-tag size="small" :type="countdownTagType(row.deadline)">{{ formatCountdown(row.deadline) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" align="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" @click="goHomework(row)">去完成</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="太棒了，暂无待办作业！" :image-size="80" />
          </el-card>

          <!-- 近期测评考试 -->
          <el-card shadow="never" class="section-card">
            <template #header>
              <div class="card-header-flex">
                <span class="card-title"><el-icon><Checked /></el-icon> 近期考试安排</span>
                <el-button link type="primary" @click="router.push('/my-exams')">查看全部</el-button>
              </div>
            </template>
            <el-table :data="dashboardData.upcomingExams || []" stripe style="width: 100%" v-if="(dashboardData.upcomingExams?.length || 0) > 0">
              <el-table-column prop="title" label="考试名称" min-width="180" />
              <el-table-column prop="endTime" label="考试倒计时" width="170">
                <template #default="{ row }">
                  <el-tag size="small" :type="examTagType(row)">{{ formatExamCountdown(row) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" align="right">
                <template #default="{ row }">
                  <el-button size="small" type="success" @click="goExam(row)">进入测评</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无待参加的考试" :image-size="80" />
          </el-card>
        </div>
      </template>

      <!-- 教师端与管理员：班级智能看板 -->
      <template v-else>
        <div class="stat-grid">
          <el-card shadow="never" class="stat-card stat-primary">
            <div class="stat-label">管理的教学班级</div>
            <div class="stat-value">{{ dashboardData.classCount || 0 }} <span class="stat-unit">个</span></div>
          </el-card>
          <el-card shadow="never" class="stat-card stat-warning">
            <div class="stat-label">待批改作业/答卷</div>
            <div class="stat-value">{{ dashboardData.pendingGradingCount || 0 }} <span class="stat-unit">份</span></div>
          </el-card>
          <el-card shadow="never" class="stat-card stat-success">
            <div class="stat-label">班级实时状态</div>
            <div class="stat-value">{{ dashboardData.activeClassCount || 0 }} <span class="stat-unit">进行中</span></div>
          </el-card>
        </div>

        <div class="content-grid single-col">
          <el-card shadow="never" class="section-card">
            <template #header>
              <div class="card-header-flex">
                <span class="card-title"><el-icon><DataLine /></el-icon> 最新作业提交动态 (智能预警)</span>
                <el-button link type="primary" @click="router.push('/teaching-classes')">进入教学管理</el-button>
              </div>
            </template>
            <el-table :data="dashboardData.recentSubmissions || []" stripe style="width: 100%" v-if="(dashboardData.recentSubmissions?.length || 0) > 0">
              <el-table-column prop="studentName" label="学生姓名" width="140">
                <template #default="{ row }">
                  <span :class="{ 'score-low': row.gradeStatus === 1 && row.score < 60 }">{{ row.studentName }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="homeworkTitle" label="作业题目" min-width="200" />
              <el-table-column prop="submitTime" label="提交时间" width="180">
                <template #default="{ row }">
                  {{ formatTime(row.submitTime) }}
                </template>
              </el-table-column>
              <el-table-column prop="gradeStatus" label="批改状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.gradeStatus === 1 ? 'success' : 'warning'" size="small">
                    {{ row.gradeStatus === 1 ? '已批改' : '待批改' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="score" label="成绩" width="120" align="right">
                <template #default="{ row }">
                  <span v-if="row.gradeStatus === 1" :class="{ 'score-low': row.score < 60, 'score-high': row.score >= 85 }">
                    {{ row.score }} 分
                  </span>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无最新学生提交记录" :image-size="80" />
          </el-card>
        </div>
      </template>
    </div>
  </main>
</template>

<script setup>
import { computed, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getDashboardSummary } from '@/api/dashboard'
import { Lightning, Tickets, Checked, DataLine } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const dashboardData = ref({})

const studentStatusText = computed(() => {
  const pendingHomeworkCount = dashboardData.value.pendingHomework?.length || 0
  const upcomingExamCount = dashboardData.value.upcomingExams?.length || 0
  if (pendingHomeworkCount === 0 && upcomingExamCount === 0) return '良好'
  if (pendingHomeworkCount + upcomingExamCount >= 5) return '紧张'
  return '待办'
})

const loadDashboard = async () => {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    const res = await getDashboardSummary()
    if (res.code === '200') {
      dashboardData.value = res.data || {}
    }
  } catch (e) {
    console.error('加载看板数据失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboard()
})

watch(() => userStore.isLoggedIn, (newVal) => {
  if (newVal) loadDashboard()
})

const getRoleTitle = (roleCode) => {
  if (roleCode === 'student') return '学习者'
  if (roleCode === 'teacher') return '教师导师'
  return '系统管理'
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  return timeStr.replace('T', ' ').substring(0, 16)
}

const parseDate = (value) => {
  const date = value ? new Date(value) : null
  return date && !Number.isNaN(date.getTime()) ? date : null
}

const formatDuration = (diffMs) => {
  if (diffMs <= 0) return '0分钟'
  const minutes = Math.ceil(diffMs / 60000)
  const days = Math.floor(minutes / 1440)
  const hours = Math.floor((minutes % 1440) / 60)
  const restMinutes = minutes % 60
  if (days > 0) return `${days}天${hours}小时`
  if (hours > 0) return `${hours}小时${restMinutes}分钟`
  return `${restMinutes}分钟`
}

const formatCountdown = (timeStr) => {
  const date = parseDate(timeStr)
  if (!date) return '-'
  const diff = date.getTime() - Date.now()
  return diff > 0 ? `剩余 ${formatDuration(diff)}` : '已截止'
}

const countdownTagType = (timeStr) => {
  const date = parseDate(timeStr)
  if (!date) return 'info'
  const diff = date.getTime() - Date.now()
  if (diff <= 0) return 'danger'
  if (diff <= 24 * 60 * 60 * 1000) return 'danger'
  if (diff <= 3 * 24 * 60 * 60 * 1000) return 'warning'
  return 'success'
}

const isExamOpen = (exam) => {
  const start = parseDate(exam.startTime)
  const end = parseDate(exam.endTime)
  const now = Date.now()
  return (!start || now >= start.getTime()) && (!end || now <= end.getTime())
}

const formatExamCountdown = (exam) => {
  const start = parseDate(exam.startTime)
  const end = parseDate(exam.endTime)
  const now = Date.now()
  if (start && now < start.getTime()) return `距开始 ${formatDuration(start.getTime() - now)}`
  if (end && now <= end.getTime()) return `剩余 ${formatDuration(end.getTime() - now)}`
  return '已结束'
}

const examTagType = (exam) => {
  const start = parseDate(exam.startTime)
  const now = Date.now()
  if (start && now < start.getTime()) return 'warning'
  return isExamOpen(exam) ? 'success' : 'info'
}

const goHomework = (homework) => {
  router.push({ path: '/my-homeworks', query: { homeworkId: homework.id } })
}

const goExam = (exam) => {
  if (isExamOpen(exam)) {
    router.push({ name: 'ExamTaking', params: { examId: exam.id } })
    return
  }
  router.push({ path: '/my-exams', query: { examId: exam.id } })
}
</script>

<style scoped>
.page-shell {
  min-height: calc(100vh - 64px);
}

/* 未登录介绍 */
.intro {
  text-align: center;
  padding: 80px 20px;
  max-width: 680px;
  margin: 0 auto;
}
.eyebrow {
  color: var(--primary-color);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  font-size: 0.9rem;
  margin-bottom: 12px;
}
.intro h1 {
  font-size: 3rem;
  font-weight: 800;
  color: var(--text-primary);
  margin-bottom: 20px;
}
.summary {
  font-size: 1.25rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 32px;
}
.home-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}
.action-btn {
  border-radius: 24px;
  padding: 12px 28px;
  font-weight: 600;
}

.dashboard-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 8px 0;
}

.welcome-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.user-greeting h2 {
  font-size: 1.75rem;
  font-weight: 800;
  color: var(--text-primary);
  margin: 0 0 6px 0;
}
.sub-greeting {
  color: var(--text-secondary);
  font-size: 0.95rem;
  margin: 0;
}
.btn-icon {
  margin-right: 6px;
}

/* 指标栅格 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}
.stat-card {
  border-radius: 8px;
  border: 1px solid var(--border-color);
  transition: all 0.2s ease;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.05) !important;
}
.stat-label {
  font-size: 0.9rem;
  color: var(--text-secondary);
  font-weight: 600;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 2.25rem;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1;
}
.stat-unit {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--text-secondary);
  margin-left: 2px;
}
.stat-primary { border-left: 4px solid var(--primary-color); }
.stat-warning { border-left: 4px solid #F59E0B; }
.stat-success { border-left: 4px solid #10B981; }

/* 内容布局栅格 */
.content-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(460px, 1fr));
  gap: 20px;
}
.content-grid.single-col {
  grid-template-columns: 1fr;
}
.section-card {
  border-radius: 8px;
  border: 1px solid var(--border-color);
}
.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-weight: 700;
  font-size: 1.05rem;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}
.score-low { color: #EF4444; font-weight: 700; }
.score-high { color: #10B981; font-weight: 700; }
.text-muted { color: #94A3B8; }
</style>
