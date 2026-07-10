<template>
  <main v-if="isManagementWorkspace" class="management-console" v-loading="loading">
    <section class="management-heading">
      <div>
        <p class="eyebrow">{{ todayText }}</p>
        <h1>管理控制台</h1>
        <p>按业务域进入管理页面，减少重复入口，让教学、学生、勤工俭学与知识资产各自保持清晰。</p>
      </div>
      <el-tag effect="plain" size="large">{{ isAdmin ? '管理员' : '教师' }}</el-tag>
    </section>

    <section class="stat-grid">
      <button v-for="item in workspaceStats" :key="item.label" class="stat-card" type="button" @click="go(item.path)">
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
              <h2>{{ isAdmin ? '平台运营入口' : '教学工作台' }}</h2>
              <p>{{ isAdmin ? '管理平台账号、教师入驻、课程分类与全局资源。' : '管理课程建设、题库试卷、作业发布与批改进度。' }}</p>
            </div>
            <p>{{ domain.description }}</p>
          </div>
        </header>

        <div class="domain-actions" :class="{ 'two-columns': domain.actions.length > 3 }">
          <button
            v-for="action in domain.actions"
            :key="action.path"
            type="button"
            class="domain-action"
            :disabled="action.disabled"
            @click="!action.disabled && go(action.path)"
          >
            <span class="action-icon"><el-icon><component :is="action.icon" /></el-icon></span>
            <span class="action-copy">
              <strong>{{ action.label }}</strong>
              <small>{{ action.description }}</small>
            </span>
            <span class="action-state">{{ action.disabled ? '无权限' : '进入' }}</span>
          </button>
        </div>
      </article>
    </section>
  </main>

  <main v-else class="student-dashboard" v-loading="loading">
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
              <el-button
                size="small"
                type="primary"
                :disabled="!canOpenCourse(item)"
                @click="learnCourse(item)"
              >
                进入学习
              </el-button>
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
          <div v-if="studentTrendData.length > 1" style="margin-top:12px">
            <ScoreTrendChart :data="studentTrendData" height="200px" />
          </div>
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
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import {
  Aim,
  Bell,
  Checked,
  Collection,
  Cpu,
  DataLine,
  Document,
  Edit,
  Lightning,
  Menu,
  Reading,
  School,
  Tickets,
  User,
  UserFilled,
  Wallet
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import ScoreTrendChart from '@/components/charts/ScoreTrendChart.vue'
import GradeDistributionChart from '@/components/charts/GradeDistributionChart.vue'
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

const management = reactive({
  courses: [],
  classes: [],
  homeworks: [],
  exams: [],
  papers: [],
  questions: [],
  users: [],
  applications: [],
  categories: []
})

const analytics = reactive({
  scoreDistribution: [],
  recentSubmissions: [],
  recentExams: []
})

const displayName = computed(() => userStore.nickname || userStore.username || '同学')
const isAdmin = computed(() => userStore.roleCode === 'admin')
const isTeacher = computed(() => userStore.roleCode === 'teacher')
const isManagementWorkspace = computed(() => isAdmin.value || isTeacher.value)
const managementDomains = computed(() => [
  {
    key: 'teaching',
    title: '教学管理',
    description: '集中管理课程建设、教学班、通知、题库、试卷、考试与作业交付。',
    icon: Reading,
    tone: 'tone-blue',
    className: 'domain-teaching',
    actions: [
      { label: '课程管理', description: '课程、章节与课时内容', path: '/courses', icon: Reading },
      { label: '教学班管理', description: '教学班与选课成员', path: '/teaching-classes', icon: School },
      { label: '通知管理', description: '发布教学通知与提醒', path: '/notices', icon: Bell },
      { label: '题库管理', description: '维护题目与知识点', path: '/questions', icon: Edit },
      { label: '试卷管理', description: '组卷与试卷配置', path: '/papers', icon: Document },
      { label: '考试管理', description: '考试发布、答题与批改', path: '/exams', icon: Checked },
      { label: '作业管理', description: '作业发布、提交与反馈', path: '/homeworks', icon: Tickets },
      { label: '资源生成', description: '生成大纲、讲义与练习', path: '/resource-gen', icon: Lightning },
      ...(isAdmin.value
        ? [{ label: '课程分类', description: '维护平台课程分类', path: '/categories', icon: Menu }]
        : []),
    ]
  },
  {
    key: 'students',
    title: '学生管理',
    description: '以行政班为主体维护学生名单、班级归属与成员调整。',
    icon: School,
    tone: 'tone-cyan',
    className: 'domain-students',
    restricted: !isAdmin.value,
    actions: [
      {
        label: '行政班管理',
        description: '创建行政班并维护学生成员',
        path: '/administrative-classes',
        icon: School,
        disabled: !isAdmin.value
      }
    ]
  },
  {
    key: 'work-study',
    title: '勤工俭学管理',
    description: '管理岗位发布、学生申请与劳务费名单。',
    icon: Wallet,
    tone: 'tone-amber',
    className: 'domain-work-study',
    actions: [
      { label: '岗位管理', description: '维护岗位与招聘状态', path: '/work-studies', icon: Wallet },
      { label: '劳务费名单', description: '维护学生劳务费记录', path: '/work-study-students', icon: User },
    ]
  },
  {
    key: 'knowledge',
    title: '知识库管理',
    description: '维护课程知识资产，并通过 Agent 将知识库用于检索、答疑和教学任务。',
    icon: Collection,
    tone: 'tone-green',
    className: 'domain-knowledge',
    actions: [
      { label: '知识库管理', description: '维护知识库与上传文件', path: '/teacher-knowledge-base', icon: Collection },
      { label: 'Agent 助手', description: '基于知识库处理教学任务', path: '/agent-chat', icon: Cpu },
    ]
  }
])
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
const studentTrendData = computed(() =>
  completedExams.value
    .filter(e => e.totalScore != null && e.submitTime)
    .map(e => ({
      date: new Date(e.submitTime).toLocaleDateString('zh-CN'),
      score: Number(e.totalScore),
      name: e.title || e.examName || ''
    }))
)
const teacherTrendData = computed(() =>
  (analytics.recentExams || analytics.recentSubmissions || []).map(s => ({
    date: s.submitTime ? new Date(s.submitTime).toLocaleDateString('zh-CN') : '-',
    score: Number(s.score || 0),
    name: s.examTitle || s.studentName || s.homeworkTitle || ''
  }))
)
const teacherDistData = computed(() => {
  const raw = analytics.scoreDistribution || []
  if (raw.length && raw[0].scoreRange) {
    return raw.map(d => ({ range: d.scoreRange, count: d.count || 0 }))
  }
  const buckets = { '0-59': 0, '60-69': 0, '70-79': 0, '80-89': 0, '90-100': 0 }
  raw.forEach(d => {
    const s = Number(d.avgScore || d.score || 0)
    if (s < 60) buckets['0-59'] += (d.count || 1)
    else if (s < 70) buckets['60-69'] += (d.count || 1)
    else if (s < 80) buckets['70-79'] += (d.count || 1)
    else if (s < 90) buckets['80-89'] += (d.count || 1)
    else buckets['90-100'] += (d.count || 1)
  })
  return Object.entries(buckets).map(([range, count]) => ({ range, count }))
})
const pendingTeacherApplications = computed(() => management.applications.filter(item => Number(item.status) === 0 || item.status === 'PENDING').length)
const publishedExams = computed(() => management.exams.filter(item => normalizeStatus(item.status, { DRAFT: 0, PUBLISHED: 1, CLOSED: 2 }, item.status) === 1).length)
const publishedHomeworks = computed(() => management.homeworks.filter(item => normalizeStatus(item.status, { DRAFT: 0, PUBLISHED: 1, CLOSED: 2 }, item.status) === 1).length)

const stats = computed(() => [
  { label: '已加入班级', value: classes.value.length, icon: School, path: '/my-classes', tone: 'tone-blue' },
  { label: '待完成作业', value: pendingHomework.value.length, icon: Tickets, path: '/my-homeworks', tone: 'tone-amber' },
  { label: '待参加考试', value: upcomingExams.value.length, icon: Checked, path: '/my-exams', tone: 'tone-red' },
  { label: '已完成考试', value: completedExams.value.length, icon: DataLine, path: '/my-grades', tone: 'tone-green' },
  { label: 'AI 推荐', value: '去看看', icon: Aim, path: '/recommendation', tone: 'tone-cyan' },
  { label: '岗位申请', value: workApplications.value.length, icon: Wallet, path: '/my-work-study', tone: 'tone-slate' }
])

const workspaceTitle = computed(() => isAdmin.value ? '平台管理员工作台' : '教师工作台')
const workspaceSummary = computed(() => isAdmin.value
  ? '这里汇总账号、教师入驻审核、课程分类和平台级资源。'
  : '这里汇总你负责的课程、教学班、题库、试卷、考试、作业与批改。')
const workspaceHealthText = computed(() => isAdmin.value
  ? `当前平台有 ${management.users.length} 个用户、${pendingTeacherApplications.value} 个教师申请待审核、${management.courses.length} 门课程可运营。`
  : `你正在维护 ${management.courses.length} 门课程、${management.questions.length} 道题目、${publishedExams.value} 场已发布考试。`)

const workspaceHeroActions = computed(() => isAdmin.value
  ? [
      { label: '用户管理', path: '/users', icon: User, primary: true },
      { label: '教师审核', path: '/teacher-applications', icon: UserFilled },
    ]
  : [
      { label: '课程管理', path: '/courses', icon: Reading, primary: true },
      { label: '作业批改', path: '/homeworks', icon: Tickets },
    ])

const workspaceStats = computed(() => isAdmin.value
  ? [
      { label: '用户总数', value: management.users.length, icon: User, path: '/users', tone: 'tone-blue' },
      { label: '待审教师', value: pendingTeacherApplications.value, icon: UserFilled, path: '/teacher-applications', tone: 'tone-amber' },
      { label: '课程总数', value: management.courses.length, icon: Reading, path: '/courses', tone: 'tone-green' },
      { label: '教学班', value: management.classes.length, icon: School, path: '/teaching-classes', tone: 'tone-cyan' },
      { label: '分类', value: management.categories.length, icon: Menu, path: '/categories', tone: 'tone-slate' },
      { label: '试卷', value: management.papers.length, icon: Document, path: '/papers', tone: 'tone-red' },
    ]
  : [
      { label: '我的课程', value: management.courses.length, icon: Reading, path: '/courses', tone: 'tone-blue' },
      { label: '教学班', value: management.classes.length, icon: School, path: '/teaching-classes', tone: 'tone-cyan' },
      { label: '题库题目', value: management.questions.length, icon: Edit, path: '/questions', tone: 'tone-green' },
      { label: '试卷', value: management.papers.length, icon: Document, path: '/papers', tone: 'tone-slate' },
      { label: '已发布考试', value: publishedExams.value, icon: Checked, path: '/exams', tone: 'tone-red' },
      { label: '已发布作业', value: publishedHomeworks.value, icon: Tickets, path: '/homeworks', tone: 'tone-amber' },
    ])

const workspaceActions = computed(() => isAdmin.value
  ? [
      { label: '用户管理', path: '/users', icon: User, description: '创建学生、教师和管理员账号，维护角色权限。' },
      { label: '教师入驻审核', path: '/teacher-applications', icon: UserFilled, description: '审核教师申请，避免开放式教师注册带来的权限风险。' },
      { label: '课程与分类治理', path: '/categories', icon: Menu, description: '维护课程分类、平台课程目录和内容结构。' },
      { label: '全局考试资源巡检', path: '/papers', icon: Document, description: '查看平台试卷与考试资源，处理异常数据。' },
    ]
  : [
      { label: '建设课程课本', path: '/courses', icon: Reading, description: '创建章节、图文课本、视频课时和互动学习内容。' },
      { label: '维护题库', path: '/questions', icon: Edit, description: '创建单选、多选、判断、简答、文件上传题，并导入题目。' },
      { label: '发布试卷考试', path: '/papers', icon: Document, description: '组卷、发布考试、查看答题结果与主观题批改。' },
      { label: '布置和批改作业', path: '/homeworks', icon: Tickets, description: '发布作业、查看提交、批改分数与反馈。' },
    ])

const workspaceTodoBlocks = computed(() => {
  if (isAdmin.value) {
    return [
      {
        title: '教师申请',
        icon: UserFilled,
        path: '/teacher-applications',
        emptyText: '暂无待审核教师申请',
        items: management.applications
          .filter(item => Number(item.status) === 0 || item.status === 'PENDING')
          .map(item => ({
            id: item.id,
            title: item.username || item.realName || `申请 ${item.id}`,
            description: item.reason || item.applyReason || '等待管理员审核教师权限'
          }))
      },
      {
        title: '平台课程',
        icon: Reading,
        path: '/courses',
        emptyText: '暂无课程需要处理',
        items: management.courses.slice(0, 3).map(item => ({
          id: item.id,
          title: item.title || `课程 ${item.id}`,
          description: item.description || '请检查课程分类、教师和发布状态'
        }))
      }
    ]
  }
  return [
    {
      title: '待交付作业',
      icon: Tickets,
      path: '/homeworks',
      emptyText: '暂无作业交付提醒',
      items: management.homeworks.slice(0, 3).map(item => ({
        id: item.id,
        title: item.title || `作业 ${item.id}`,
        description: item.deadline ? `截止：${formatDateTime(item.deadline)}` : '请确认发布状态和批改入口'
      }))
    },
    {
      title: '考试安排',
      icon: Checked,
      path: '/exams',
      emptyText: '暂无考试安排提醒',
      items: management.exams.slice(0, 3).map(item => ({
        id: item.id,
        title: item.name || item.title || `考试 ${item.id}`,
        description: item.startTime ? `${formatDateTime(item.startTime)} 开始` : '请确认试卷、时间和发布状态'
      }))
    }
  ]
})

const workspaceSideMetrics = computed(() => isAdmin.value
  ? [
      { label: '个用户', value: management.users.length },
      { label: '个教师申请', value: management.applications.length },
      { label: '个分类', value: management.categories.length },
    ]
  : [
      { label: '门课程', value: management.courses.length },
      { label: '份试卷', value: management.papers.length },
      { label: '道题目', value: management.questions.length },
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

  const memberships = getRecords(res.data).filter(isActiveMembership)
  const rows = (await Promise.all(memberships.slice(0, 8).map(async item => {
    try {
      const classRes = await request.get(`/teaching-classes/${item.classId}`, { silentError: true })
      const teachingClass = classRes.code === '200' ? classRes.data : null
      if (!teachingClass?.courseId) return null
      let course = {}
      const courseRes = await request.get(`/courses/${teachingClass.courseId}`, { silentError: true })
      course = courseRes.code === '200' ? courseRes.data : {}
      if (!course?.id || !course?.title) return null
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
      return null
    }
  }))).filter(Boolean)

  classes.value = rows
  return rows.map(item => item.classId)
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

function isActiveMembership(item) {
  return Number(item.status ?? 1) === 1
}

function canOpenCourse(row) {
  return Number.isFinite(Number(row?.courseId)) && Number(row.courseId) > 0 && Number.isFinite(Number(row?.classId)) && Number(row.classId) > 0
}

function learnCourse(row) {
  if (!canOpenCourse(row)) {
    ElMessage.warning('课程信息不完整，暂不能进入学习')
    return
  }
  router.push({ name: 'CourseLearn', params: { courseId: row.courseId }, query: { classId: row.classId } })
}

async function safeRecords(path, params = {}) {
  try {
    const res = await request.get(path, {
      params: { pageSize: 100, size: 100, ...params },
      silentError: true,
    })
    return res?.code === '200' ? getRecords(res.data) : []
  } catch {
    return []
  }
}

function scopedTeacherRows(rows) {
  if (isAdmin.value) return rows
  const teacherId = Number(userStore.userId)
  return rows.filter(item => {
    const owner = Number(item.teacherId || item.createdBy || item.ownerId)
    return !Number.isFinite(owner) || owner <= 0 || owner === teacherId
  })
}

async function loadManagementDashboard() {
  const teacherParam = isTeacher.value ? { teacherId: userStore.userId } : {}
  const [
    courses,
    classes,
    homeworks,
    exams,
    papers,
    questions,
    users,
    applications,
    categories,
    dashboardSummary
  ] = await Promise.all([
    safeRecords('/courses', teacherParam),
    safeRecords('/teaching-classes', teacherParam),
    safeRecords('/homeworks', teacherParam),
    safeRecords('/api/exams', teacherParam),
    safeRecords('/api/papers', teacherParam),
    safeRecords('/api/questions', teacherParam),
    isAdmin.value ? safeRecords('/users') : Promise.resolve([]),
    isAdmin.value ? safeRecords('/teacher-applications') : Promise.resolve([]),
    isAdmin.value ? safeRecords('/categories') : Promise.resolve([]),
    safeSummary()
  ])

  management.courses = scopedTeacherRows(courses)
  management.classes = scopedTeacherRows(classes)
  management.homeworks = scopedTeacherRows(homeworks)
  management.exams = scopedTeacherRows(exams).map(mapExam)
  management.papers = scopedTeacherRows(papers)
  management.questions = scopedTeacherRows(questions)
  management.users = users
  management.applications = applications
  management.categories = categories

  if (dashboardSummary) {
    analytics.scoreDistribution = dashboardSummary.scoreDistribution || []
    analytics.recentSubmissions = dashboardSummary.recentSubmissions || []
    analytics.recentExams = dashboardSummary.recentExams || []
  }
}

async function safeSummary() {
  try {
    const res = await request.get('/dashboard/summary', { silentError: true })
    return res?.code === '200' ? res.data : null
  } catch {
    return null
  }
}

async function loadStudentDashboard() {
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

async function loadDashboard() {
  if (!userStore.userId) return
  loading.value = true
  try {
    if (isManagementWorkspace.value) {
      await loadManagementDashboard()
    } else {
      await loadStudentDashboard()
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.management-console {
  width: min(1400px, 100%);
  margin: 0 auto;
}

.management-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 26px;
  padding: 4px 2px 22px;
  border-bottom: 1px solid var(--border-color);
}

.management-heading h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 2rem;
  line-height: 1.25;
}

.management-heading > div > p:last-child {
  max-width: 760px;
  margin: 10px 0 0;
  color: var(--text-secondary);
  line-height: 1.65;
}

.management-domain-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(340px, 0.85fr);
  gap: 20px;
  align-items: stretch;
}

.management-domain {
  min-width: 0;
  padding: 24px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: white;
  box-shadow: var(--card-shadow);
}

.domain-teaching {
  grid-row: span 2;
}

.domain-knowledge {
  grid-column: 1 / -1;
}

.domain-header {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  align-items: start;
  gap: 16px;
  padding-bottom: 20px;
}

.domain-icon {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  font-size: 25px;
}

.domain-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.domain-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.28rem;
}

.domain-header p {
  margin: 7px 0 0;
  color: var(--text-secondary);
  font-size: 0.9rem;
  line-height: 1.6;
}

.domain-actions {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.domain-actions.two-columns,
.domain-knowledge .domain-actions {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.domain-action {
  min-width: 0;
  min-height: 76px;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-color);
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

.domain-action:hover:not(:disabled) {
  transform: translateY(-2px);
  border-color: var(--primary-color-light);
  background: var(--primary-color-soft);
}

.domain-action:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.action-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: white;
  color: var(--primary-color);
  font-size: 19px;
}

.action-copy {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.action-copy strong {
  font-size: 0.96rem;
}

.action-copy small {
  color: var(--text-secondary);
  font-size: 0.8rem;
  line-height: 1.4;
}

.action-state {
  color: var(--primary-color);
  font-size: 0.78rem;
  font-weight: 700;
}

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

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
  max-width: 1240px;
}

.chart-card :deep(.el-card__header) {
  display: flex;
  align-items: center;
}

@media (max-width: 1100px) {
  .management-domain-grid {
    grid-template-columns: 1fr;
  }

  .domain-teaching,
  .domain-knowledge {
    grid-column: auto;
    grid-row: auto;
  }

  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .charts-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .management-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .management-domain {
    padding: 18px;
  }

  .domain-actions.two-columns,
  .domain-knowledge .domain-actions {
    grid-template-columns: 1fr;
  }

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
