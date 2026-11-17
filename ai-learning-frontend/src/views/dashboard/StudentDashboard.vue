<template>
  <div class="role-dashboard student-dashboard" v-loading="loading">
    <section class="dashboard-hero">
      <div class="hero-copy">
        <p class="eyebrow">{{ today }}</p>
        <h1>学生学习控制台</h1>
        <p>{{ greeting }}，{{ displayName }}。从班级课程继续学习，并在这里掌握考试、作业与实践进度。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" @click="go('/my-classes')">
          <el-icon><Reading /></el-icon>
          继续学习
        </el-button>
        <el-button @click="openAgentAssistant()">
          <el-icon><Cpu /></el-icon>
          Agent 助手
        </el-button>
        <el-button :loading="loading" @click="loadDashboard">
          <el-icon><Refresh /></el-icon>
          刷新数据
        </el-button>
      </div>
    </section>

    <section class="stat-grid" aria-label="学习概览">
      <button v-for="item in stats" :key="item.label" class="stat-card" type="button" @click="go(item.path)">
        <span class="stat-icon" :class="item.tone"><el-icon><component :is="item.icon" /></el-icon></span>
        <span class="stat-content"><strong>{{ item.value }}</strong><small>{{ item.label }}</small></span>
      </button>
    </section>

    <section class="dashboard-layout">
      <div class="dashboard-main">
        <section class="panel">
          <div class="panel-header">
            <div>
              <p class="section-kicker">课程学习</p>
              <h2>继续学习</h2>
              <p>从已加入的教学班进入课程内容与互动实验。</p>
            </div>
            <el-button link type="primary" @click="go('/my-classes')">全部班级</el-button>
          </div>

          <div v-if="classPreview?.length" class="learning-list">
            <article v-for="item in classPreview" :key="item.classStudentId || item.classId" class="learning-item">
              <span class="course-mark"><el-icon><Reading /></el-icon></span>
              <div class="item-copy">
                <h3>{{ item.courseName }}</h3>
                <p>{{ item.className }} · {{ item.semester || '当前学期' }}</p>
              </div>
              <el-button size="small" type="primary" :disabled="!canOpenCourse(item)" @click="learnCourse(item)">
                进入学习
              </el-button>
            </article>
          </div>
          <div v-else-if="classPreview === null" class="data-unavailable">班级数据暂时不可用，请稍后刷新。</div>
          <el-empty v-else description="暂未加入教学班" :image-size="76" />
        </section>

        <section class="panel">
          <div class="panel-header">
            <div>
              <p class="section-kicker">近期任务</p>
              <h2>学习待办</h2>
              <p>作业统一读取新版考核数据，和“我的作业”页面保持一致。</p>
            </div>
          </div>

          <div class="todo-columns">
            <div class="todo-block">
              <div class="todo-title"><el-icon><Tickets /></el-icon><span>待完成作业</span></div>
              <template v-if="pendingHomework !== null">
                <article v-for="item in pendingHomework.slice(0, 3)" :key="item.id" class="todo-item">
                  <div><h3>{{ item.title }}</h3><p>截止：{{ formatDateTime(item.endTime) }}</p></div>
                  <el-button size="small" @click="go('/my-homeworks')">查看</el-button>
                </article>
                <div v-if="!pendingHomework.length" class="mini-empty">暂无待完成作业</div>
              </template>
              <div v-else class="data-unavailable compact">作业数据暂时不可用</div>
            </div>

            <div class="todo-block">
              <div class="todo-title"><el-icon><Checked /></el-icon><span>待参加考试</span></div>
              <template v-if="upcomingExams !== null">
                <article v-for="item in upcomingExams.slice(0, 3)" :key="item.id" class="todo-item">
                  <div><h3>{{ item.title }}</h3><p>{{ formatDateTime(item.startTime) }} 开始</p></div>
                  <el-button size="small" @click="go('/my-exams')">查看</el-button>
                </article>
                <div v-if="!upcomingExams.length" class="mini-empty">暂无待参加考试</div>
              </template>
              <div v-else class="data-unavailable compact">考试数据暂时不可用</div>
            </div>
          </div>
        </section>
      </div>

      <aside class="dashboard-side">
        <section class="panel feature-panel">
          <span class="feature-icon"><el-icon><Cpu /></el-icon></span>
          <p class="section-kicker">AI 学习</p>
          <h2>智能学伴与推荐</h2>
          <p>使用知识库问答梳理知识点，再根据学习情况查看个性化内容。</p>
          <div class="stack-actions">
            <el-button type="primary" @click="go('/knowledge-base')">打开智能学伴</el-button>
            <el-button @click="go('/recommendation')">查看智能推荐</el-button>
          </div>
        </section>

        <section class="panel compact-panel">
          <div class="panel-header compact">
            <div><p class="section-kicker">学习成果</p><h2>成绩概览</h2></div>
            <el-button link type="primary" @click="go('/my-grades')">详情</el-button>
          </div>
          <div class="primary-metric"><strong>{{ averagePercentText }}</strong><span>已评分考试平均得分率</span></div>
          <p class="muted">已提交考试 {{ countText(completedExamCount) }} 场，待教师批改 {{ countText(gradingCount) }} 场。</p>
        </section>

        <section class="panel compact-panel">
          <div class="panel-header compact">
            <div><p class="section-kicker">校园实践</p><h2>勤工俭学</h2></div>
            <el-button link type="primary" @click="go('/work-study-jobs')">岗位大厅</el-button>
          </div>
          <div class="metric-pair">
            <span><b>{{ countText(availableJobTotal) }}</b><small>招聘中岗位</small></span>
            <span><b>{{ countText(pendingApplicationTotal) }}</b><small>待审核申请</small></span>
          </div>
          <p class="muted">我的全部申请：{{ countText(applicationTotal) }} 条</p>
          <el-button class="wide-button" @click="go('/my-work-study')">查看我的申请</el-button>
        </section>

        <button type="button" class="panel compact-panel research-panel" @click="go('/paper-search')">
          <span class="inline-icon"><el-icon><Search /></el-icon></span>
          <span><strong>论文检索</strong><small>检索研究资料与原文链接</small></span>
          <el-icon><ArrowRight /></el-icon>
        </button>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  Checked,
  Cpu,
  DataLine,
  Reading,
  Refresh,
  School,
  Search,
  Tickets,
  Wallet,
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getRecords, normalizeStatus } from '@/utils/apiHelpers'
import { formatPercent, meanPercent, scorePercent, submissionFullScore } from '@/utils/scoreMetrics'
import { useUserStore } from '@/store/user'
import { openAgentAssistant } from '@/utils/agentAssistant'
import { countText, exactTotal, formatDateTime, safeResponse, todayText } from './dashboardUtils'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const classPreview = ref(null)
const classTotal = ref(null)
const pendingHomework = ref(null)
const upcomingExams = ref(null)
const completedExams = ref(null)
const availableJobTotal = ref(null)
const applicationTotal = ref(null)
const pendingApplicationTotal = ref(null)

const today = todayText('学生端')
const displayName = computed(() => userStore.nickname || userStore.username || '同学')
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const completedExamCount = computed(() => completedExams.value === null ? null : completedExams.value.length)
const gradingCount = computed(() => completedExams.value === null
  ? null
  : completedExams.value.filter(item => item.status === 1 || item.status === 3).length)
// Exams can have different full scores, so only their share of the real full score is averaged.
const averagePercentText = computed(() => {
  if (completedExams.value === null) return '—'
  const percents = completedExams.value
    .filter(item => item.status === 2 && !item.cheated)
    .map(item => scorePercent(item.totalScore, submissionFullScore(item)))
    .filter(percent => percent !== null)
  return formatPercent(meanPercent(percents))
})

const stats = computed(() => [
  { label: '已加入班级', value: countText(classTotal.value), icon: School, path: '/my-classes', tone: 'tone-blue' },
  { label: '待完成作业', value: countText(pendingHomework.value?.length), icon: Tickets, path: '/my-homeworks', tone: 'tone-amber' },
  { label: '待参加考试', value: countText(upcomingExams.value?.length), icon: Checked, path: '/my-exams', tone: 'tone-red' },
  { label: '已提交考试', value: countText(completedExamCount.value), icon: DataLine, path: '/my-grades', tone: 'tone-green' },
  { label: '招聘中岗位', value: countText(availableJobTotal.value), icon: Wallet, path: '/work-study-jobs', tone: 'tone-cyan' },
  { label: '我的岗位申请', value: countText(applicationTotal.value), icon: Wallet, path: '/my-work-study', tone: 'tone-slate' },
])

function go(path) {
  router.push(path)
}

function isActiveMembership(item) {
  return Number(item?.status ?? 1) === 1
}

function canOpenCourse(item) {
  return Number(item?.courseId) > 0 && Number(item?.classId) > 0
}

function learnCourse(item) {
  if (!canOpenCourse(item)) {
    ElMessage.warning('课程信息不完整，暂不能进入学习')
    return
  }
  router.push({ name: 'CourseLearn', params: { courseId: item.courseId }, query: { classId: item.classId } })
}

async function enrichMembership(item) {
  const fallback = {
    ...item,
    classStudentId: item.id,
    classId: item.classId,
    className: `教学班 ${item.classId}`,
    courseName: '课程信息加载中',
  }
  try {
    const classResponse = await request.get(`/teaching-classes/${item.classId}`, { silentError: true })
    if (classResponse?.code !== '200' || !classResponse.data) return fallback
    const teachingClass = classResponse.data
    const courseResponse = teachingClass.courseId
      ? await request.get(`/courses/${teachingClass.courseId}`, { silentError: true })
      : null
    const course = courseResponse?.code === '200' ? courseResponse.data : null
    return {
      ...fallback,
      ...teachingClass,
      classStudentId: item.id,
      classId: item.classId,
      courseId: teachingClass.courseId,
      className: teachingClass.className || fallback.className,
      courseName: course?.title || `课程 ${teachingClass.courseId || ''}`.trim(),
    }
  } catch {
    return fallback
  }
}

async function loadMemberships() {
  const response = await safeResponse(`/class-students/student/${userStore.userId}`)
  if (!response) {
    classPreview.value = null
    classTotal.value = null
    return null
  }
  const memberships = getRecords(response.data).filter(isActiveMembership)
  classTotal.value = memberships.length
  classPreview.value = await Promise.all(memberships.slice(0, 4).map(enrichMembership))
  return memberships.map(item => Number(item.classId)).filter(id => Number.isFinite(id) && id > 0)
}

function mapAssessment(item) {
  return {
    ...item,
    title: item.name || item.title || `考核 ${item.id}`,
    status: normalizeStatus(item.status, { DRAFT: 0, PUBLISHED: 1, CLOSED: 2 }, 0),
  }
}

function mapSubmission(item, assessmentType) {
  return {
    ...item,
    assessmentId: Number(assessmentType === 'homework' ? (item.homeworkId ?? item.examId) : item.examId),
    status: normalizeStatus(item.status, {
      IN_PROGRESS: 0,
      SUBMITTED: 1,
      PENDING_MANUAL_REVIEW: 1,
      GRADED: 2,
    }, 0),
  }
}

async function loadAssessments(classIds, type) {
  const target = type === 'homework' ? pendingHomework : upcomingExams
  const completedTarget = type === 'exam' ? completedExams : null
  if (!Array.isArray(classIds)) {
    target.value = null
    if (completedTarget) completedTarget.value = null
    return
  }
  if (!classIds.length) {
    target.value = []
    if (completedTarget) completedTarget.value = []
    return
  }

  const assessmentPath = type === 'homework' ? '/api/homeworks' : '/api/exams'
  const submissionPath = type === 'homework' ? '/api/homework-submissions' : '/api/submissions'
  const [assessmentResponse, submissionResponse] = await Promise.all([
    safeResponse(assessmentPath),
    safeResponse(submissionPath, { studentId: userStore.userId }),
  ])
  if (!assessmentResponse || !submissionResponse) {
    target.value = null
    if (completedTarget) completedTarget.value = null
    return
  }

  const classSet = new Set(classIds)
  const assessments = getRecords(assessmentResponse.data)
    .map(mapAssessment)
    .filter(item => classSet.has(Number(item.classId)))
  const assessmentIds = new Set(assessments.map(item => Number(item.id)))
  const submissions = getRecords(submissionResponse.data)
    .map(item => mapSubmission(item, type))
    .filter(item => assessmentIds.has(item.assessmentId))
  const finishedIds = new Set(submissions.filter(item => item.status >= 1).map(item => item.assessmentId))
  const now = Date.now()
  target.value = assessments
    .filter(item => item.status === 1 && !finishedIds.has(Number(item.id)))
    .filter(item => !item.endTime || new Date(item.endTime).getTime() >= now)
    .sort((a, b) => new Date(a.startTime || a.endTime || 0) - new Date(b.startTime || b.endTime || 0))
  if (completedTarget) completedTarget.value = submissions.filter(item => item.status >= 1)
}

async function loadWorkStudy() {
  const [jobsResponse, applicationsResponse, pendingResponse] = await Promise.all([
    safeResponse('/work-studies', { status: 1, pageNum: 1, pageSize: 1 }),
    safeResponse('/work-studies/my-applications', { studentId: userStore.userId, pageNum: 1, pageSize: 1 }),
    safeResponse('/work-studies/my-applications', { studentId: userStore.userId, auditStatus: 0, pageNum: 1, pageSize: 1 }),
  ])
  availableJobTotal.value = jobsResponse ? exactTotal(jobsResponse.data) : null
  applicationTotal.value = applicationsResponse ? exactTotal(applicationsResponse.data) : null
  pendingApplicationTotal.value = pendingResponse ? exactTotal(pendingResponse.data) : null
}

async function loadDashboard() {
  if (!userStore.userId || loading.value) return
  loading.value = true
  try {
    const workStudyPromise = loadWorkStudy()
    const classIds = await loadMemberships()
    await Promise.allSettled([
      loadAssessments(classIds, 'homework'),
      loadAssessments(classIds, 'exam'),
      workStudyPromise,
    ])
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped src="./dashboard.css"></style>
