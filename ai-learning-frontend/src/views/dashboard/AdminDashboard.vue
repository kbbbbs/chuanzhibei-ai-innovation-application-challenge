<template>
  <div class="role-dashboard admin-dashboard" v-loading="loading">
    <section class="dashboard-hero">
      <div class="hero-copy">
        <p class="eyebrow">{{ today }}</p>
        <h1>平台管理控制台</h1>
        <p>{{ displayName }}，这里集中处理平台账号、教师入驻、组织数据、教学资源与运行审计。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" @click="go('/users')"><el-icon><User /></el-icon>用户管理</el-button>
        <el-button @click="go('/teacher-applications')"><el-icon><UserFilled /></el-icon>教师审核</el-button>
        <el-button :loading="loading" @click="loadDashboard"><el-icon><Refresh /></el-icon>刷新数据</el-button>
      </div>
    </section>

    <section class="stat-grid" aria-label="平台概览">
      <button v-for="item in stats" :key="item.label" class="stat-card" type="button" @click="go(item.path)">
        <span class="stat-icon" :class="item.tone"><el-icon><component :is="item.icon" /></el-icon></span>
        <span class="stat-content"><strong>{{ item.value }}</strong><small>{{ item.label }}</small></span>
      </button>
    </section>

    <section class="dashboard-layout">
      <div class="dashboard-main">
        <section class="panel">
          <div class="panel-header">
            <div><p class="section-kicker">待办事项</p><h2>教师入驻审核</h2><p>只展示待审核申请，统计来自分页响应总数。</p></div>
            <el-button link type="primary" @click="go('/teacher-applications')">查看全部</el-button>
          </div>
          <div v-if="pendingTeachers?.length" class="timeline-list">
            <article v-for="item in pendingTeachers" :key="item.id" class="timeline-item">
              <span class="avatar-mark">{{ String(item.nickname || item.username || '师').slice(0, 1) }}</span>
              <div><h3>{{ item.nickname || item.username || `申请 ${item.id}` }}</h3><p>{{ item.reason || '等待教师身份审核' }}</p></div>
              <el-tag type="warning" effect="plain">待审核</el-tag>
            </article>
          </div>
          <div v-else-if="pendingTeachers === null" class="data-unavailable">教师申请数据暂时不可用，请稍后刷新。</div>
          <el-empty v-else description="暂无待审核教师申请" :image-size="72" />
        </section>

        <section class="panel">
          <div class="panel-header">
            <div><p class="section-kicker">平台运营域</p><h2>账号、权限与平台内容</h2><p>管理账号生命周期、教师准入、课程分类、首页推荐与运行审计。</p></div>
          </div>
          <div class="domain-actions">
            <button v-for="action in platformActions" :key="action.path" type="button" class="domain-action" @click="go(action.path)">
              <span class="action-icon"><el-icon><component :is="action.icon" /></el-icon></span>
              <span><strong>{{ action.label }}</strong><small>{{ action.description }}</small></span>
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </section>

        <section class="panel">
          <div class="panel-header">
            <div><p class="section-kicker">教学治理域</p><h2>课程、班级与考核资源</h2><p>从平台视角巡检课程目录、两类班级和教学考核资源。</p></div>
          </div>
          <div class="domain-actions">
            <button v-for="action in teachingActions" :key="action.path" type="button" class="domain-action" @click="go(action.path)">
              <span class="action-icon"><el-icon><component :is="action.icon" /></el-icon></span>
              <span><strong>{{ action.label }}</strong><small>{{ action.description }}</small></span>
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </section>
      </div>

      <aside class="dashboard-side">
        <section class="panel feature-panel">
          <span class="feature-icon"><el-icon><Monitor /></el-icon></span>
          <p class="section-kicker">运行治理域</p>
          <h2>平台运行与审计</h2>
          <p>通过操作日志追踪关键行为，维护知识资产和勤工俭学业务。</p>
          <div class="stack-actions">
            <el-button type="primary" @click="go('/operation-logs')">查看操作日志</el-button>
            <el-button @click="openAgentAssistant()">打开智能助手</el-button>
          </div>
        </section>

        <section class="panel compact-panel">
          <div class="panel-header compact"><div><p class="section-kicker">组织数据</p><h2>平台结构</h2></div></div>
          <div class="metric-pair">
            <span><b>{{ countText(categoryCount) }}</b><small>课程分类</small></span>
            <span><b>{{ countText(adminClassTotal) }}</b><small>行政班</small></span>
          </div>
          <p class="muted">用户、课程、教学班、行政班与日志均读取服务端总数，不使用首屏条数代替。</p>
        </section>

        <section class="panel compact-panel">
          <div class="panel-header compact"><div><p class="section-kicker">业务支持域</p><h2>校园服务与知识资产</h2></div></div>
          <div class="quick-links">
            <button type="button" @click="go('/work-studies')"><el-icon><Wallet /></el-icon><span>岗位管理</span></button>
            <button type="button" @click="go('/work-study-students')"><el-icon><Money /></el-icon><span>劳务名单</span></button>
            <button type="button" @click="go('/courses')"><el-icon><Collection /></el-icon><span>知识库</span></button>
          </div>
        </section>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Checked,
  Collection,
  Cpu,
  Document,
  Edit,
  Menu,
  Money,
  Monitor,
  Reading,
  Refresh,
  School,
  Star,
  User,
  UserFilled,
  Wallet,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { openAgentAssistant } from '@/utils/agentAssistant'
import { countText, loadExactPage, loadRecords, todayText } from './dashboardUtils'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const userTotal = ref(null)
const courseTotal = ref(null)
const classTotal = ref(null)
const adminClassTotal = ref(null)
const pendingTeacherTotal = ref(null)
const operationLogTotal = ref(null)
const categoryCount = ref(null)
const pendingTeachers = ref(null)

const today = todayText('管理员端')
const displayName = computed(() => userStore.nickname || userStore.username || '管理员')

const stats = computed(() => [
  { label: '平台用户', value: countText(userTotal.value), icon: User, path: '/users', tone: 'tone-blue' },
  { label: '待审教师', value: countText(pendingTeacherTotal.value), icon: UserFilled, path: '/teacher-applications', tone: 'tone-amber' },
  { label: '平台课程', value: countText(courseTotal.value), icon: Reading, path: '/courses', tone: 'tone-green' },
  { label: '教学班', value: countText(classTotal.value), icon: School, path: '/teaching-classes', tone: 'tone-cyan' },
  { label: '行政班', value: countText(adminClassTotal.value), icon: School, path: '/administrative-classes', tone: 'tone-slate' },
  { label: '操作日志', value: countText(operationLogTotal.value), icon: Document, path: '/operation-logs', tone: 'tone-red' },
])

const platformActions = [
  { label: '用户管理', description: '维护账号、角色与启用状态', path: '/users', icon: User },
  { label: '教师入驻审核', description: '审核教师身份与平台准入', path: '/teacher-applications', icon: UserFilled },
  { label: '课程分类', description: '维护平台课程分类结构', path: '/categories', icon: Menu },
  { label: '首页推荐', description: '配置公共首页推荐课程', path: '/home-course-recommendations', icon: Star },
  { label: '操作日志', description: '追踪平台关键操作记录', path: '/operation-logs', icon: Document },
  { label: '模型设置', description: '配置、探测并应用四项 AI 能力', path: '/settings/models', icon: Cpu },
]

const teachingActions = [
  { label: '课程管理', description: '巡检课程目录与内容状态', path: '/courses', icon: Reading },
  { label: '教学班管理', description: '维护授课班级及选课关系', path: '/teaching-classes', icon: School },
  { label: '行政班管理', description: '维护组织班级与学生归属', path: '/administrative-classes', icon: School },
  { label: '题库管理', description: '巡检平台题目与知识点', path: '/questions', icon: Edit },
  { label: '试卷管理', description: '查看组卷和题目配置', path: '/papers', icon: Document },
  { label: '考试管理', description: '查看发布、批改与结果状态', path: '/exams', icon: Checked },
]

function go(path) {
  router.push(path)
}

async function loadDashboard() {
  if (loading.value) return
  loading.value = true
  try {
    const [users, courses, classes, adminClasses, teacherApplications, logs, categories] = await Promise.all([
      loadExactPage('/users', { pageNum: 1, pageSize: 1 }),
      loadExactPage('/courses', { pageNum: 1, pageSize: 1 }),
      loadExactPage('/teaching-classes', { pageNum: 1, pageSize: 1 }),
      loadExactPage('/administrative-classes', { pageNum: 1, pageSize: 1 }),
      loadExactPage('/teacher-applications', { status: 0, pageNum: 1, pageSize: 3 }),
      loadExactPage('/operation-logs', { pageNum: 1, pageSize: 1 }),
      loadRecords('/categories'),
    ])
    userTotal.value = users.total
    courseTotal.value = courses.total
    classTotal.value = classes.total
    adminClassTotal.value = adminClasses.total
    pendingTeacherTotal.value = teacherApplications.total
    pendingTeachers.value = teacherApplications.records
    operationLogTotal.value = logs.total
    categoryCount.value = Array.isArray(categories) ? categories.length : null
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped src="./dashboard.css"></style>
