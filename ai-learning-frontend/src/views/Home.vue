<template>
  <main class="student-dashboard" v-loading="loading">
    <section class="dashboard-hero">
      <div>
        <p class="eyebrow">{{ todayText }}</p>
        <h1>{{ greeting }}，{{ displayName }}</h1>
        <p class="summary">这里汇总你的班级、课程、作业考试、AI 学习与勤工俭学进展。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" @click="go('/my-classes')">
          <el-icon><Reading /></el-icon>
          继续学习
        </el-button>
        <el-button @click="go('/agent-chat')">
          <el-icon><Cpu /></el-icon>
          Agent 助手
        </el-button>
      </div>
    </section>

    <section class="stat-grid">
      <button v-for="item in stats" :key="item.label" class="stat-card" type="button" @click="go(item.path)">
        <span class="stat-icon" :class="item.tone">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <span class="stat-content">
          <strong>{{ item.value }}</strong>
          <small>{{ item.label }}</small>
        </span>
      </button>
    </section>

    <section class="dashboard-grid">
      <div class="dashboard-main">
        <section class="panel">
          <div class="panel-header">
            <div>
              <h2>继续学习</h2>
              <p>从已加入的教学班进入课程学习。</p>
            </div>
            <el-button link type="primary" @click="go('/my-classes')">查看全部</el-button>
          </div>

          <div v-if="classes.length" class="learning-list">
            <article v-for="item in classes.slice(0, 4)" :key="item.classStudentId || item.classId" class="learning-item">
              <div class="course-mark">
                <el-icon><Reading /></el-icon>
              </div>
              <div class="learning-copy">
                <h3>{{ item.courseName || '未命名课程' }}</h3>
                <p>{{ item.className }} · {{ item.semester || '当前学期' }}</p>
              </div>
              <el-button size="small" type="primary" @click="learnCourse(item)">进入学习</el-button>
            </article>
          </div>
          <div v-else class="empty-panel">
            <el-empty description="暂无已加入班级" :image-size="80" />
          </div>
        </section>

        <section class="panel">
          <div class="panel-header">
            <div>
              <h2>待办学习</h2>
              <p>近期作业和考试提醒。</p>
            </div>
          </div>

          <div class="todo-columns">
            <div class="todo-block">
              <div class="todo-title">
                <el-icon><Tickets /></el-icon>
                <span>待完成作业</span>
              </div>
              <article v-for="item in pendingHomework.slice(0, 3)" :key="item.id" class="todo-item">
                <div>
                  <h3>{{ item.title }}</h3>
                  <p>截止：{{ formatDateTime(item.deadline) }}</p>
                </div>
                <el-button size="small" @click="go('/my-homeworks')">提交</el-button>
              </article>
              <div v-if="!pendingHomework.length" class="mini-empty">暂无待完成作业</div>
            </div>

            <div class="todo-block">
              <div class="todo-title">
                <el-icon><Checked /></el-icon>
                <span>待参加考试</span>
              </div>
              <article v-for="item in upcomingExams.slice(0, 3)" :key="item.id" class="todo-item">
                <div>
                  <h3>{{ item.title }}</h3>
                  <p>{{ formatDateTime(item.startTime) }} 开始</p>
                </div>
                <el-button size="small" @click="go('/my-exams')">查看</el-button>
              </article>
              <div v-if="!upcomingExams.length" class="mini-empty">暂无待参加考试</div>
            </div>
          </div>
        </section>
      </div>

      <aside class="dashboard-side">
        <section class="panel ai-panel">
          <div class="ai-icon">
            <el-icon><Cpu /></el-icon>
          </div>
          <h2>AI 学习</h2>
          <p>使用智能学伴进行知识库问答，或让 Agent 辅助梳理学习任务。</p>
          <div class="side-actions">
            <el-button type="primary" @click="go('/knowledge-base')">智能学伴</el-button>
            <el-button @click="go('/recommendation')">智能推荐</el-button>
          </div>
        </section>

        <section class="panel compact-panel">
          <div class="panel-header compact">
            <h2>成绩概览</h2>
            <el-button link type="primary" @click="go('/my-grades')">详情</el-button>
          </div>
          <div class="grade-score">
            <strong>{{ averageScoreText }}</strong>
            <span>平均成绩</span>
          </div>
          <p class="muted">已完成考试 {{ completedExams.length }} 场，批改中 {{ gradingCount }} 场。</p>
        </section>

        <section class="panel compact-panel">
          <div class="panel-header compact">
            <h2>勤工俭学</h2>
            <el-button link type="primary" @click="go('/work-study-jobs')">岗位</el-button>
          </div>
          <div class="work-status">
            <span><b>{{ availableJobs.length }}</b> 个岗位可申请</span>
            <span><b>{{ pendingApplications }}</b> 个申请待审核</span>
          </div>
          <el-button class="wide-btn" @click="go('/my-work-study')">我的申请</el-button>
        </section>

        <section class="panel compact-panel">
          <div class="panel-header compact">
            <h2>资料研究</h2>
            <el-button link type="primary" @click="go('/paper-search')">进入</el-button>
          </div>
          <p class="muted">进行论文关键词检索、推荐、导入和原文链接打开。</p>
        </section>
      </aside>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import {
  Aim,
  Checked,
  Cpu,
  DataLine,
  Reading,
  School,
  Tickets,
  Wallet
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getRecords, normalizeStatus } from '@/utils/apiHelpers'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const classes = ref([])
const pendingHomework = ref([])
const upcomingExams = ref([])
const completedExams = ref([])
const availableJobs = ref([])
const workApplications = ref([])

const dashboard = reactive({
  averageScore: null
})

const displayName = computed(() => userStore.nickname || userStore.username || '同学')
const todayText = computed(() => new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}).format(new Date()))
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const gradingCount = computed(() => completedExams.value.filter(item => item.status === 1).length)
const pendingApplications = computed(() => workApplications.value.filter(item => Number(item.auditStatus) === 0).length)
const averageScoreText = computed(() => dashboard.averageScore == null ? '-' : dashboard.averageScore)

const stats = computed(() => [
  { label: '已加入班级', value: classes.value.length, icon: School, path: '/my-classes', tone: 'tone-blue' },
  { label: '待完成作业', value: pendingHomework.value.length, icon: Tickets, path: '/my-homeworks', tone: 'tone-amber' },
  { label: '待参加考试', value: upcomingExams.value.length, icon: Checked, path: '/my-exams', tone: 'tone-red' },
  { label: '已完成考试', value: completedExams.value.length, icon: DataLine, path: '/my-grades', tone: 'tone-green' },
  { label: 'AI 推荐', value: '去看看', icon: Aim, path: '/recommendation', tone: 'tone-cyan' },
  { label: '岗位申请', value: workApplications.value.length, icon: Wallet, path: '/my-work-study', tone: 'tone-slate' }
])

function go(path) {
  router.push(path)
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

async function loadClasses() {
  const res = await request.get(`/class-students/student/${userStore.userId}`)
  if (res.code !== '200') return []

  const memberships = getRecords(res.data)
  const rows = await Promise.all(memberships.slice(0, 8).map(async item => {
    try {
      const classRes = await request.get(`/teaching-classes/${item.classId}`)
      const teachingClass = classRes.code === '200' ? classRes.data : {}
      let course = {}
      if (teachingClass.courseId) {
        const courseRes = await request.get(`/courses/${teachingClass.courseId}`)
        course = courseRes.code === '200' ? courseRes.data : {}
      }
      return {
        ...item,
        ...teachingClass,
        classStudentId: item.id,
        classId: item.classId,
        courseId: teachingClass.courseId,
        className: teachingClass.className || `教学班${item.classId}`,
        courseName: course.title || '-'
      }
    } catch {
      return {
        ...item,
        classStudentId: item.id,
        classId: item.classId,
        className: `教学班${item.classId}`,
        courseName: '-'
      }
    }
  }))

  classes.value = rows
  return memberships.map(item => item.classId)
}

async function loadHomework(classIds) {
  if (!classIds.length) return
  const hwRes = await request.get('/homeworks', { params: { pageSize: 100 } })
  const allHomework = hwRes.code === '200'
    ? getRecords(hwRes.data).filter(item => classIds.includes(item.classId))
    : []

  const subRes = await request.get('/homework-submits', {
    params: { studentId: userStore.userId, pageSize: 100 }
  })
  const submissions = subRes.code === '200' ? getRecords(subRes.data) : []
  const submittedIds = new Set(submissions.map(item => item.homeworkId))

  pendingHomework.value = allHomework
    .filter(item => !submittedIds.has(item.id) && normalizeStatus(item.status, { PUBLISHED: 1 }, item.status) === 1)
    .sort((a, b) => new Date(a.deadline || 0) - new Date(b.deadline || 0))
}

function mapExam(exam) {
  const normalized = normalizeStatus(exam.status, { DRAFT: 0, PUBLISHED: 1, CLOSED: 2 }, 0)
  return {
    ...exam,
    title: exam.name || exam.title || `考试${exam.id}`,
    duration: exam.durationMinutes || exam.duration || 0,
    status: normalized === 3 ? 2 : normalized
  }
}

function mapSubmission(submission, exam) {
  const normalized = normalizeStatus(submission.status, {
    IN_PROGRESS: 0,
    SUBMITTED: 1,
    PENDING_MANUAL_REVIEW: 1,
    GRADED: 2
  }, 0)
  return {
    ...submission,
    title: exam?.title || submission.examName || `考试${submission.examId}`,
    submitTime: submission.submittedAt,
    status: normalized
  }
}

async function loadExams(classIds) {
  if (!classIds.length) return
  const examRes = await request.get('/api/exams', { params: { pageSize: 100 } })
  const allExams = examRes.code === '200'
    ? getRecords(examRes.data).map(mapExam).filter(item => classIds.includes(item.classId))
    : []

  const subRes = await request.get('/api/submissions', {
    params: { studentId: userStore.userId, pageSize: 100 }
  })
  const submissions = subRes.code === '200' ? getRecords(subRes.data) : []
  const mappedSubmissions = submissions.map(sub => {
    const exam = allExams.find(item => item.id === sub.examId)
    return mapSubmission(sub, exam)
  })
  const finishedExamIds = new Set(mappedSubmissions.filter(item => item.status >= 1).map(item => item.examId))

  upcomingExams.value = allExams
    .filter(item => !finishedExamIds.has(item.id) && item.status === 1)
    .sort((a, b) => new Date(a.startTime || 0) - new Date(b.startTime || 0))
  completedExams.value = mappedSubmissions.filter(item => item.status >= 1)

  const scored = completedExams.value
    .map(item => Number(item.totalScore))
    .filter(score => Number.isFinite(score))
  dashboard.averageScore = scored.length
    ? Math.round(scored.reduce((sum, score) => sum + score, 0) / scored.length)
    : null
}

function learnCourse(row) {
  if (!row.courseId) return
  router.push({ name: 'CourseLearn', params: { courseId: row.courseId }, query: { classId: row.classId } })
}

async function loadDashboard() {
  if (!userStore.userId) return
  loading.value = true
  try {
    const classIds = await loadClasses()
    await Promise.allSettled([
      loadHomework(classIds),
      loadExams(classIds)
    ])
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.student-dashboard {
  max-width: 1240px;
  margin: 0 auto;
}

.dashboard-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  padding: 28px;
  margin-bottom: 20px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, #FFFFFF 0%, #EFF6FF 58%, #F0FDFA 100%);
  box-shadow: var(--card-shadow);
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--primary-color);
  font-weight: 700;
  font-size: 13px;
}

.dashboard-hero h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.25;
  color: var(--text-primary);
}

.summary {
  margin: 10px 0 0;
  color: var(--text-secondary);
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}

.stat-card {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: white;
  box-shadow: var(--shadow-xs);
  cursor: pointer;
  text-align: left;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--card-shadow);
}

.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.tone-blue { background: var(--primary-color-soft); color: var(--primary-color); }
.tone-amber { background: #FFFBEB; color: #D97706; }
.tone-red { background: #FEF2F2; color: #DC2626; }
.tone-green { background: var(--secondary-color-soft); color: var(--secondary-color); }
.tone-cyan { background: #ECFEFF; color: #0891B2; }
.tone-slate { background: var(--bg-subtle); color: var(--text-secondary); }

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.stat-content strong {
  font-size: 22px;
  color: var(--text-primary);
  line-height: 1;
}

.stat-content small {
  color: var(--text-secondary);
  white-space: nowrap;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
}

.dashboard-main,
.dashboard-side {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.panel {
  background: white;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--card-shadow);
  padding: 20px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-header.compact {
  align-items: center;
}

.panel h2 {
  margin: 0;
  font-size: 18px;
  color: var(--text-primary);
}

.panel-header p,
.muted {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.learning-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.learning-item,
.todo-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-color);
}

.course-mark {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary-color-soft);
  color: var(--primary-color);
  flex-shrink: 0;
}

.learning-copy {
  flex: 1;
  min-width: 0;
}

.learning-copy h3,
.todo-item h3 {
  margin: 0 0 4px;
  font-size: 15px;
  color: var(--text-primary);
}

.learning-copy p,
.todo-item p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.todo-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.todo-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.todo-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
  font-weight: 700;
  margin-bottom: 2px;
}

.todo-item {
  justify-content: space-between;
}

.mini-empty {
  padding: 22px;
  text-align: center;
  color: var(--text-muted);
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-color);
}

.empty-panel {
  padding: 10px 0;
}

.ai-panel {
  background: linear-gradient(135deg, var(--primary-color) 0%, var(--secondary-color) 100%);
  color: white;
}

.ai-panel h2,
.ai-panel p {
  color: white;
}

.ai-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.16);
  font-size: 24px;
  margin-bottom: 14px;
}

.side-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.side-actions .el-button:not(.el-button--primary) {
  background: rgba(255, 255, 255, 0.94);
  border: none;
}

.compact-panel {
  padding: 18px;
}

.grade-score {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 8px;
}

.grade-score strong {
  font-size: 34px;
  color: var(--primary-color);
}

.grade-score span {
  color: var(--text-secondary);
}

.work-status {
  display: grid;
  gap: 8px;
  margin: 12px 0 14px;
  color: var(--text-secondary);
}

.work-status b {
  color: var(--text-primary);
}

.wide-btn {
  width: 100%;
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .dashboard-hero {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-actions,
  .side-actions {
    flex-direction: column;
  }

  .stat-grid,
  .todo-columns {
    grid-template-columns: 1fr;
  }
}
</style>
