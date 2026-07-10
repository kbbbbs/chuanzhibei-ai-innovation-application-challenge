<template>
  <el-container class="layout-container">
    <!-- Persistent Top Navigation Header (Glassmorphism & High-end) -->
    <el-header v-if="!hideChrome" class="layout-header">
      <div class="header-left">
        <button class="brand-link" type="button" @click="router.push('/home')">
          <div class="logo-icon">E</div>
          <span class="logo-text">EduLoop <span class="sub-title">智教云环</span></span>
        </button>
        <nav v-if="isPublicHome" class="home-nav" aria-label="首页模块导航">
          <button
            v-for="item in homeNavItems"
            :key="item.id"
            type="button"
            class="home-nav-item"
            :class="{ active: activeHomeSection === item.id }"
            @click="scrollToHomeSection(item.id)"
          >
            {{ item.label }}
          </button>
        </nav>
      </div>

      <div class="header-right">
        <el-button class="console-nav-btn" type="primary" link @click="router.push('/console')">
          <el-icon><DataBoard /></el-icon>
          <span>控制台</span>
        </el-button>
        <el-button class="agent-nav-btn" type="primary" link @click="router.push('/agent-chat')">
          <el-icon><Cpu /></el-icon>
          <span>Agent 助手</span>
        </el-button>

        <!-- User Profile Dropdown or Login Button -->
        <template v-if="userStore.isLoggedIn">
          <el-dropdown @command="handleCommand" trigger="click">
            <div class="user-profile-trigger">
              <el-avatar :size="36" class="user-avatar" :src="userStore.avatar || ''">
                {{ (userStore.nickname || 'U').charAt(0).toUpperCase() }}
              </el-avatar>
              <span class="username">{{ userStore.nickname }}</span>
              <el-tag size="small" :type="getRoleTagType(userStore.roleCode)" class="role-badge">
                {{ getRoleName(userStore.roleCode) }}
              </el-tag>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="custom-dropdown-menu">
                <div class="dropdown-header">
                  <p class="name">{{ userStore.nickname }}</p>
                  <p class="role">{{ getRoleName(userStore.roleCode) }}专属主页</p>
                </div>
                <template v-for="entry in accountMenuEntries" :key="entry.index">
                  <template v-if="entry.children">
                    <div class="dropdown-section-label">
                      <el-icon><component :is="entry.icon" /></el-icon>
                      <span>{{ entry.title }}</span>
                    </div>
                    <el-dropdown-item
                      v-for="item in entry.children"
                      :key="item.path"
                      :command="resolveMenuPath(item)"
                      class="dropdown-child-item"
                    >
                      <el-icon><component :is="item.meta.icon || 'Menu'" /></el-icon>
                      {{ item.meta.title }}
                    </el-dropdown-item>
                  </template>
                  <el-dropdown-item
                    v-else
                    :command="entry.path"
                    :disabled="entry.disabled"
                  >
                    <el-icon><component :is="entry.icon" /></el-icon>
                    {{ entry.title }}
                  </el-dropdown-item>
                </template>

                <el-dropdown-item divided command="/profile" class="profile-item">
                  <el-icon><Setting /></el-icon> 个人设置
                </el-dropdown-item>
                <el-dropdown-item command="logout" class="logout-item">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button class="user-entry-btn" @click="router.push('/login')">
            <el-icon><User /></el-icon>
            <span>用户</span>
          </el-button>
        </template>
      </div>
    </el-header>

    <!-- Main Content Body (With Conditionally Displayed Sidebar for Internal Workspaces) -->
    <el-container class="body-container">
      <el-aside v-if="shouldShowSidebar" :width="sidebarWidth" class="layout-aside">
        <div class="aside-title" :class="{ collapsed: sidebarCollapsed }">
          <span v-show="!sidebarCollapsed">{{ isManagementUser ? '管理导航' : '学习导航' }}</span>
          <el-tooltip :content="sidebarCollapsed ? '展开导航' : '收起导航'" placement="right">
            <el-button
              class="sidebar-collapse-btn"
              text
              circle
              :aria-label="sidebarCollapsed ? '展开导航' : '收起导航'"
              @click="toggleSidebar"
            >
              <el-icon><component :is="sidebarCollapsed ? Expand : Fold" /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
        <el-menu
          :default-active="activeMenu"
          :default-openeds="defaultOpenMenus"
          :collapse="sidebarCollapsed"
          :collapse-transition="false"
          :router="true"
          :unique-opened="true"
          class="el-menu-vertical"
        >
          <template v-if="isManagementUser">
            <template v-for="entry in managementMenuEntries" :key="entry.index">
              <el-sub-menu v-if="entry.children" :index="entry.index">
                <template #title>
                  <el-icon><component :is="entry.icon" /></el-icon>
                  <span>{{ entry.title }}</span>
                </template>
                <el-menu-item
                  v-for="item in entry.children"
                  :key="item.path"
                  :index="resolveMenuPath(item)"
                >
                  <el-icon><component :is="item.meta.icon || 'Menu'" /></el-icon>
                  <span>{{ item.meta.title }}</span>
                </el-menu-item>
              </el-sub-menu>

              <el-menu-item
                v-else
                :index="entry.path"
                :disabled="entry.disabled"
              >
                <el-icon><component :is="entry.icon" /></el-icon>
                <span>{{ entry.title }}</span>
              </el-menu-item>
            </template>
          </template>

          <template v-else>
            <el-menu-item
              v-for="route in menuRoutes"
              :key="route.path"
              :index="resolveMenuPath(route)"
            >
              <el-icon><component :is="route.meta.icon || 'Menu'" /></el-icon>
              <span>{{ route.meta.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>

      <el-main
        class="layout-main"
        :class="{ 'public-main': hideChrome || isPublicHome, 'full-width-main': route.meta?.fullWidth }"
        @scroll.passive="handleMainScroll"
      >
        <router-view />
      </el-main>
    </el-container>

  </el-container>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { layoutChildren } from '@/router'
import { ElMessage } from 'element-plus'
import {
  DataBoard, Cpu, School, Tickets, Checked,
  DataLine, Reading, Edit, Document, SwitchButton, User, UserFilled, Menu, Collection, Bell, MapLocation,
  Suitcase, Setting, Fold, Expand
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const hideChrome = computed(() => Boolean(route.meta?.hideChrome))
const isPublicHome = computed(() => Boolean(route.meta?.publicHome))
const sidebarCollapsed = ref(
  window.innerWidth <= 760 || localStorage.getItem('layout-sidebar-collapsed') === 'true'
)
const sidebarWidth = computed(() => sidebarCollapsed.value ? '76px' : '220px')
const shouldShowSidebar = computed(() => userStore.isLoggedIn
  && !hideChrome.value
  && !isPublicHome.value
  && route.name !== 'CourseLearn'
  && !route.meta?.fullWidth)
const activeHomeSection = ref('')
const homeNavItems = [
  { id: 'home-recommendation', label: '智能推荐' },
  { id: 'home-resource-gen', label: '资源生成' },
  { id: 'home-paper-search', label: '论文查询' },
  { id: 'home-teaching-loop', label: '教学闭环' },
]
let scrollFrame = null

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem('layout-sidebar-collapsed', String(sidebarCollapsed.value))
}

async function scrollToHomeSection(id) {
  activeHomeSection.value = id
  const hash = `#${id}`
  if (!isPublicHome.value) {
    await router.push({ path: '/home', hash })
  } else if (route.hash !== hash) {
    await router.replace({ path: '/home', hash })
  }
  await nextTick()
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function updateActiveHomeSection() {
  scrollFrame = null
  if (!isPublicHome.value) return

  let current = ''
  for (const item of homeNavItems) {
    const section = document.getElementById(item.id)
    if (section && section.getBoundingClientRect().top <= 112) {
      current = item.id
    }
  }
  activeHomeSection.value = current
}

function handleMainScroll() {
  if (!isPublicHome.value || scrollFrame !== null) return
  scrollFrame = window.requestAnimationFrame(updateActiveHomeSection)
}

watch(
  () => route.fullPath,
  async () => {
    if (!isPublicHome.value) {
      activeHomeSection.value = ''
      return
    }

    await nextTick()
    const targetId = route.hash.replace(/^#/, '')
    if (targetId) {
      activeHomeSection.value = targetId
      document.getElementById(targetId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    } else {
      updateActiveHomeSection()
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (scrollFrame !== null) window.cancelAnimationFrame(scrollFrame)
})

const activeMenu = computed(() => {
  const firstSegment = route.path.split('/').filter(Boolean)[0]
  return firstSegment ? `/${firstSegment}` : '/console'
})

const resolveMenuPath = (routeRecord) => `/${routeRecord.path}`.replace(/\/+/g, '/')

const isManagementUser = computed(() => ['admin', 'teacher'].includes(userStore.roleCode))

const getMenuRoute = (path) => layoutChildren.find(routeRecord => routeRecord.path === path)

const managementMenuEntries = computed(() => {
  const canAccess = (routeRecord) => routeRecord?.meta?.roles?.includes(userStore.roleCode)
  const teachingPaths = [
    'courses',
    'teaching-classes',
    'questions',
    'papers',
    'exams',
    'homeworks',
    'resource-gen',
    'categories',
  ]

  const teachingChildren = teachingPaths
    .map(getMenuRoute)
    .filter(canAccess)
  const workStudyChildren = ['work-studies', 'work-study-students']
    .map(getMenuRoute)
    .filter(canAccess)

  const entries = [
    {
      index: 'teaching-management',
      title: '教学管理',
      icon: Reading,
      children: teachingChildren,
    },
    {
      index: 'student-management',
      title: '学生管理',
      icon: School,
      path: '/administrative-classes',
      disabled: userStore.roleCode !== 'admin',
    },
    {
      index: 'work-study-management',
      title: '勤工俭学管理',
      icon: Suitcase,
      children: workStudyChildren,
    },
    {
      index: 'knowledge-management',
      title: '知识库管理',
      icon: Collection,
      path: '/teacher-knowledge-base',
    },
    {
      index: 'notice-management',
      title: '通知管理',
      icon: Bell,
      path: '/notices',
    },
    {
      index: 'log-management',
      title: '日志管理',
      icon: Document,
      path: '/operation-logs',
    },
  ]

  if (userStore.roleCode === 'admin') {
    entries.push(
      {
        index: 'user-management',
        title: '用户管理',
        icon: User,
        path: '/users',
      },
      {
        index: 'teacher-review',
        title: '教师审核',
        icon: UserFilled,
        path: '/teacher-applications',
      },
    )
  }

  return entries
})

const defaultOpenMenus = computed(() => {
  if (!isManagementUser.value) return []
  if (['work-studies', 'work-study-students'].includes(activeMenu.value.slice(1))) {
    return ['work-study-management']
  }
  return ['teaching-management']
})

const menuRoutes = computed(() => {
  const adminHiddenPersonalPaths = new Set([
    'my-classes',
    'learning-routes',
    'my-homeworks',
    'my-exams',
    'my-grades',
    'recommendation',
    'work-study-jobs',
    'my-work-study',
    'knowledge-base',
  ])
  return layoutChildren.filter(r => {
    if (r.meta?.hidden || r.meta?.noAuth || ['console', 'agent-chat'].includes(r.path)) return false
    if (!r.meta?.roles) return true
    if (userStore.roleCode === 'admin') return !adminHiddenPersonalPaths.has(r.path)
    return r.meta.roles.includes(userStore.roleCode)
  })
})

const accountMenuEntries = computed(() => {
  if (isManagementUser.value) return managementMenuEntries.value
  return menuRoutes.value.map(routeRecord => ({
    index: `account-${routeRecord.path}`,
    title: routeRecord.meta.title,
    icon: routeRecord.meta.icon || Menu,
    path: resolveMenuPath(routeRecord),
  }))
})

const getRoleName = (role) => {
  const map = {
    admin: '系统管理员',
    teacher: '教师导师',
    student: '学习者'
  }
  return map[role] || '用户'
}

const getRoleTagType = (role) => {
  const map = {
    admin: 'danger',
    teacher: 'warning',
    student: 'success'
  }
  return map[role] || 'info'
}

const handleCommand = (command) => {
  if (command === 'logout') {
    userStore.logout()
    ElMessage.success('已安全退出登录')
    router.push('/')
  } else {
    router.push(command.startsWith('/') ? command : `/${command}`)
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-color);
}

.layout-header {
  height: 64px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-color);
  position: sticky;
  top: 0;
  z-index: 1000;
  box-shadow: var(--shadow-xs);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 22px;
  flex: 1;
  min-width: 0;
}

.brand-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 8px 6px 0;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  cursor: pointer;
  transition: background 0.2s ease;
}

.brand-link:hover {
  background: var(--bg-hover);
}

.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  color: white;
  font-weight: 800;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.25);
}

.logo-text {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0;
}

.home-nav {
  align-self: stretch;
  display: flex;
  align-items: stretch;
  justify-content: space-evenly;
  flex: 1;
  min-width: 0;
  margin: 0 12px;
  overflow-x: auto;
  scrollbar-width: none;
}

.home-nav::-webkit-scrollbar {
  display: none;
}

.home-nav-item {
  position: relative;
  flex: 0 0 auto;
  padding: 0 22px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: 1.05rem;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease;
}

.home-nav-item::after {
  content: "";
  position: absolute;
  right: 22px;
  bottom: 0;
  left: 22px;
  height: 3px;
  background: transparent;
}

.home-nav-item:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.home-nav-item.active {
  color: var(--primary-color);
}

.home-nav-item.active::after {
  background: var(--primary-color);
}

.sub-title {
  font-weight: 400;
  color: var(--text-secondary);
  font-size: 0.95rem;
  margin-left: 4px;
}

.console-nav-btn,
.agent-nav-btn {
  height: 36px;
  padding: 0 14px;
  border-radius: 18px;
  background: var(--primary-color-soft);
  color: var(--primary-color);
  font-weight: 700;
  border: 1px solid var(--primary-color-light);
}

.console-nav-btn:hover,
.agent-nav-btn:hover {
  background: var(--primary-color-light);
  color: var(--primary-color-hover);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 0 0 auto;
}

.user-profile-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border-radius: 30px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}

.user-profile-trigger:hover {
  background-color: var(--bg-hover);
  border-color: var(--border-color);
}

.user-avatar {
  background: var(--primary-color);
  color: white;
  font-weight: 600;
}

.username {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
}

.role-badge {
  font-weight: 600;
}

.user-entry-btn {
  height: 36px;
  padding: 0 14px;
  font-weight: 600;
  color: var(--text-primary);
  background: transparent;
  border-color: var(--border-color);
}

/* Body and Sidebar */
.body-container {
  flex: 1;
  overflow: hidden;
}

.layout-aside {
  background: var(--bg-elevated);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
  transition: width 0.2s ease;
}

.aside-title {
  min-height: 54px;
  padding: 12px 12px 8px 24px;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  white-space: nowrap;
}

.aside-title.collapsed {
  justify-content: center;
  padding: 12px 0 8px;
}

.sidebar-collapse-btn {
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  color: var(--text-secondary);
}

.sidebar-collapse-btn:hover {
  color: var(--primary-color);
  background: var(--primary-color-soft);
}

.el-menu-vertical {
  flex: 1;
  min-height: 0;
  border-right: none !important;
  padding: 0 12px;
  overflow-y: auto;
}

.el-menu-vertical.el-menu--collapse {
  width: 100%;
  padding: 0 10px;
}

.el-menu-vertical.el-menu--collapse :deep(.el-menu-item),
.el-menu-vertical.el-menu--collapse :deep(.el-sub-menu__title) {
  justify-content: center;
  padding: 0 !important;
}

.el-menu-vertical :deep(.el-menu-item),
.el-menu-vertical :deep(.el-sub-menu__title) {
  border-radius: var(--radius-md);
  margin-bottom: 4px;
  height: 44px;
  line-height: 44px;
  color: var(--text-secondary);
  font-weight: 500;
}

.el-menu-vertical :deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
  padding-left: 48px !important;
}

.el-menu-vertical :deep(.el-menu-item.is-active) {
  background-color: var(--primary-color-soft);
  color: var(--primary-color);
  font-weight: 600;
}

.el-menu-vertical :deep(.el-menu-item:hover),
.el-menu-vertical :deep(.el-sub-menu__title:hover) {
  background-color: var(--bg-hover);
}

.el-menu-vertical :deep(.el-menu-item.is-disabled) {
  opacity: 0.48;
}

.layout-main {
  flex: 1;
  overflow-y: auto;
  padding: 32px;
  background-color: var(--bg-color);
}

.public-main {
  padding: 0;
  background-color: white;
}

.full-width-main {
  padding: 24px;
}

/* Dropdown styling */
.dropdown-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 4px;
}

.dropdown-header .name {
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.dropdown-header .role {
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin: 4px 0 0;
}

.custom-dropdown-menu {
  width: 260px;
  max-height: min(72vh, 620px);
  overflow-y: auto;
}

.dropdown-section-label {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px 6px;
  color: var(--text-muted);
  font-size: 0.75rem;
  font-weight: 700;
}

.dropdown-child-item {
  padding-left: 32px !important;
}

.profile-item {
  color: var(--primary-color) !important;
  font-weight: 600;
}

.logout-item {
  color: #EF4444 !important;
}

@media (max-width: 760px) {
  .layout-header {
    padding: 0 14px;
  }

  .logo-text,
  .username,
  .role-badge {
    display: none;
  }

  .console-nav-btn span,
  .agent-nav-btn span {
    display: none;
  }

  .header-left {
    gap: 8px;
  }

  .home-nav-item {
    padding: 0 10px;
    font-size: 0.9rem;
  }

  .home-nav {
    justify-content: flex-start;
    margin: 0;
  }

  .home-nav-item::after {
    right: 10px;
    left: 10px;
  }

  .console-nav-btn,
  .agent-nav-btn,
  .user-entry-btn {
    width: 36px;
    padding: 0;
  }

  .user-entry-btn span {
    display: none;
  }

  .layout-main,
  .full-width-main {
    padding: 16px;
  }

  .layout-main.public-main {
    padding: 0;
  }
}
</style>
