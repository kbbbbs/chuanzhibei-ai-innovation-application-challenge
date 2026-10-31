<template>
  <main class="role-dashboard teacher-dashboard" v-loading="loading">
    <section class="dashboard-hero teacher-hero">
      <div class="hero-copy">
        <p class="eyebrow">{{ today }}</p>
        <h1>教师教学控制台</h1>
        <p>{{ displayName }}，这里聚焦课程建设、班级教学、考核评价与教学智能工具。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" @click="go('/courses')"><el-icon><Reading /></el-icon>课程管理</el-button>
        <el-button @click="go('/grade-analysis')"><el-icon><DataLine /></el-icon>成绩分析</el-button>
        <el-button :loading="loading" @click="loadDashboard"><el-icon><Refresh /></el-icon>刷新数据</el-button>
      </div>
    </section>

    <section class="stat-grid" aria-label="教学概览">
      <button v-for="item in stats" :key="item.label" class="stat-card" type="button" @click="go(item.path)">
        <span class="stat-icon" :class="item.tone"><el-icon><component :is="item.icon" /></el-icon></span>
        <span class="stat-content"><strong>{{ item.value }}</strong><small>{{ item.label }}</small></span>
      </button>
    </section>

    <section class="dashboard-layout">
      <div class="dashboard-main">
        <section class="panel">
          <div class="panel-header">
            <div><p class="section-kicker">教师业务域</p><h2>课程与课堂</h2><p>围绕课程内容、教学班和课堂通知组织日常教学。</p></div>
          </div>
          <div class="domain-actions">
            <button v-for="action in teachingActions" :key="action.path" type="button" class="domain-action" @click="go(action.path)">
              <span class="action-icon"><el-icon><component :is="action.icon" /></el-icon></span>
              <span><strong>{{ action.label }}</strong><small>{{ action.description }}</small></span>
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </section>

        <section class="panel">
          <div class="panel-header">
            <div><p class="section-kicker">考核评价域</p><h2>题库、试卷与考核</h2><p>作业入口统一使用新版考核模型，首页与管理页保持同一数据来源。</p></div>
          </div>
          <div class="domain-actions">
            <button v-for="action in assessmentActions" :key="action.path" type="button" class="domain-action" @click="go(action.path)">
              <span class="action-icon"><el-icon><component :is="action.icon" /></el-icon></span>
              <span><strong>{{ action.label }}</strong><small>{{ action.description }}</small></span>
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </section>

        <section class="panel">
          <div class="panel-header">
            <div><p class="section-kicker">近期安排</p><h2>已发布考核</h2><p>按开始时间展示最近的考试和作业。</p></div>
          </div>
          <div v-if="recentAssessments?.length" class="timeline-list">
            <article v-for="item in recentAssessments" :key="`${item.type}-${item.id}`" class="timeline-item">
              <span class="timeline-dot" :class="item.type === '考试' ? 'exam' : 'homework'"></span>
              <div><h3>{{ item.title }}</h3><p>{{ item.type }} · {{ formatDateTime(item.startTime || item.endTime) }}</p></div>
              <el-tag effect="plain">已发布</el-tag>
            </article>
          </div>
          <div v-else-if="recentAssessments === null" class="data-unavailable">考核数据暂时不可用，请稍后刷新。</div>
          <el-empty v-else description="暂无已发布考核" :image-size="72" />
        </section>
      </div>

      <aside class="dashboard-side">
        <section class="panel feature-panel teacher-feature">
          <span class="feature-icon"><el-icon><Cpu /></el-icon></span>
          <p class="section-kicker">教学智能域</p>
          <h2>知识库与 Agent</h2>
          <p>维护课程知识资产，辅助生成教学任务、检索资料和组织备课内容。</p>
          <div class="stack-actions">
            <el-button type="primary" @click="go('/teacher-knowledge-base')">知识库管理</el-button>
            <el-button @click="openAgentAssistant()">打开 Agent 助手</el-button>
          </div>
        </section>

        <section class="panel compact-panel">
          <div class="panel-header compact"><div><p class="section-kicker">数据说明</p><h2>可信统计</h2></div></div>
          <ul class="truth-list">
            <li><span>课程与班级</span><b>分页总数</b></li>
            <li><span>题库题目</span><b>totalElements</b></li>
            <li><span>考试与作业</span><b>新版考核接口</b></li>
          </ul>
          <p class="muted">“—”表示接口暂时不可用，不会用当前页条数冒充总数。</p>
        </section>

        <section class="panel compact-panel">
          <div class="panel-header compact"><div><p class="section-kicker">学生支持域</p><h2>教学与实践支持</h2></div></div>
          <div class="quick-links">
            <button type="button" @click="go('/notices')"><el-icon><Bell /></el-icon><span>通知管理</span></button>
            <button type="button" @click="go('/work-studies')"><el-icon><Wallet /></el-icon><span>勤工岗位</span></button>
            <button type="button" @click="go('/operation-logs')"><el-icon><Document /></el-icon><span>操作日志</span></button>
          </div>
        </section>
      </aside>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Bell,
  Checked,
  Cpu,
  DataLine,
  Document,
  Edit,
  Lightning,
  Reading,
  Refresh,
  School,
  Tickets,
  Wallet,
} from '@element-plus/icons-vue'
import { normalizeStatus } from '@/utils/apiHelpers'
import { useUserStore } from '@/store/user'
import { openAgentAssistant } from '@/utils/agentAssistant'
import { countText, formatDateTime, loadExactPage, loadRecords, todayText } from './dashboardUtils'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const courseTotal = ref(null)
const classTotal = ref(null)
const questionTotal = ref(null)
const papers = ref(null)
const exams = ref(null)
const homeworks = ref(null)

const today = todayText('教师端')
const displayName = computed(() => userStore.nickname || userStore.username || '教师')
const publishedExamCount = computed(() => countPublished(exams.value))
const publishedHomeworkCount = computed(() => countPublished(homeworks.value))

const stats = computed(() => [
  { label: '我的课程', value: countText(courseTotal.value), icon: Reading, path: '/courses', tone: 'tone-blue' },
  { label: '教学班', value: countText(classTotal.value), icon: School, path: '/teaching-classes', tone: 'tone-cyan' },
  { label: '题库题目', value: countText(questionTotal.value), icon: Edit, path: '/questions', tone: 'tone-green' },
  { label: '试卷', value: countText(papers.value?.length), icon: Document, path: '/papers', tone: 'tone-slate' },
  { label: '已发布考试', value: countText(publishedExamCount.value), icon: Checked, path: '/exams', tone: 'tone-red' },
  { label: '已发布作业', value: countText(publishedHomeworkCount.value), icon: Tickets, path: '/homeworks', tone: 'tone-amber' },
])

const teachingActions = [
  { label: '课程管理', description: '建设章节、课时与互动内容', path: '/courses', icon: Reading },
  { label: '教学班管理', description: '维护授课班级与学生名单', path: '/teaching-classes', icon: School },
  { label: '通知管理', description: '发布课堂通知和学习提醒', path: '/notices', icon: Bell },
  { label: '资源生成', description: '生成大纲、讲义与练习', path: '/resource-gen', icon: Lightning },
]

const assessmentActions = [
  { label: '题库管理', description: '维护题目、难度与知识点', path: '/questions', icon: Edit },
  { label: '试卷管理', description: '组卷并检查题目分值', path: '/papers', icon: Document },
  { label: '考试管理', description: '发布考试、批改与汇总结果', path: '/exams', icon: Checked },
  { label: '作业管理', description: '发布新版作业并查看提交', path: '/homeworks', icon: Tickets },
  { label: '成绩分析', description: '按教学班分析考核结果', path: '/grade-analysis', icon: DataLine },
]

const recentAssessments = computed(() => {
  if (exams.value === null || homeworks.value === null) return null
  return [
    ...publishedRows(exams.value).map(item => ({ ...item, type: '考试' })),
    ...publishedRows(homeworks.value).map(item => ({ ...item, type: '作业' })),
  ]
    .sort((a, b) => new Date(b.startTime || b.endTime || 0) - new Date(a.startTime || a.endTime || 0))
    .slice(0, 5)
})

function go(path) {
  router.push(path)
}

function isPublished(item) {
  return normalizeStatus(item?.status, { DRAFT: 0, PUBLISHED: 1, CLOSED: 2 }, 0) === 1
}

function publishedRows(rows) {
  return Array.isArray(rows)
    ? rows.filter(isPublished).map(item => ({ ...item, title: item.name || item.title || `考核 ${item.id}` }))
    : []
}

function countPublished(rows) {
  return Array.isArray(rows) ? rows.filter(isPublished).length : null
}

async function loadDashboard() {
  if (!userStore.userId || loading.value) return
  loading.value = true
  const teacherId = Number(userStore.userId)
  try {
    const [coursePage, classPage, questionPage, paperRows, examRows, homeworkRows] = await Promise.all([
      loadExactPage('/courses', { pageNum: 1, pageSize: 1 }),
      loadExactPage('/teaching-classes', { pageNum: 1, pageSize: 1 }),
      loadExactPage('/api/questions', { teacherId, page: 0, size: 1 }),
      loadRecords('/api/papers', { teacherId }),
      loadRecords('/api/exams', { teacherId }),
      loadRecords('/api/homeworks', { teacherId }),
    ])
    courseTotal.value = coursePage.total
    classTotal.value = classPage.total
    questionTotal.value = questionPage.total
    papers.value = paperRows
    exams.value = examRows
    homeworks.value = homeworkRows
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped src="./dashboard.css"></style>
