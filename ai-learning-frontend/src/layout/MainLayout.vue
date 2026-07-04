<template>
  <el-container class="layout-container">
    <!-- Persistent Top Navigation Header (Glassmorphism & High-end) -->
    <el-header class="layout-header">
      <div class="header-left" @click="router.push('/')">
        <div class="logo-icon">E</div>
        <span class="logo-text">EduLoop <span class="sub-title">智教云环</span></span>
      </div>

      <!-- Top Nav Links / Recommendations Carousel -->
      <div class="header-center">
        <el-menu
          mode="horizontal"
          :default-active="activeTopMenu"
          :router="true"
          class="top-menu"
          :ellipsis="false"
        >
          <el-menu-item index="/">
            <el-icon><House /></el-icon>首页推荐
          </el-menu-item>
<!--          <el-menu-item v-if="userStore.isLoggedIn" index="/home">-->
<!--            <el-icon><DataBoard /></el-icon>控制台-->
<!--          </el-menu-item>-->
<!--          <el-menu-item v-if="userStore.isLoggedIn && (userStore.roleCode === 'student' || userStore.roleCode === 'admin')" index="/recommendation">-->
<!--            <el-icon><Aim /></el-icon>智能推荐-->
<!--          </el-menu-item>-->
<!--          <el-menu-item v-if="userStore.isLoggedIn && (userStore.roleCode === 'teacher' || userStore.roleCode === 'admin')" index="/resource-gen">-->
<!--            <el-icon><Lightning /></el-icon>智能备课-->
<!--          </el-menu-item>-->
        </el-menu>
      </div>

      <div class="header-right">
        <!-- Floating Chat Trigger for Internal Pages -->
        <div v-if="!isPublicHome" class="header-ai-btn" @click="toggleAiModal">
          <el-button type="primary" link class="ai-trigger-btn">
            <el-icon class="ai-icon"><Cpu /></el-icon>
            <span>AI知识助手</span>
          </el-button>
        </div>

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
                <el-dropdown-item divided command="home">
                  <el-icon><DataBoard /></el-icon> 个人控制台
                </el-dropdown-item>

                <!-- Student Menu Items -->
                <template v-if="userStore.roleCode === 'student' || userStore.roleCode === 'admin'">
                  <el-dropdown-item command="my-classes">
                    <el-icon><School /></el-icon> 我的班级
                  </el-dropdown-item>
                  <el-dropdown-item command="my-homeworks">
                    <el-icon><Tickets /></el-icon> 我的作业
                  </el-dropdown-item>
                  <el-dropdown-item command="my-exams">
                    <el-icon><Checked /></el-icon> 我的试卷/考试
                  </el-dropdown-item>
                  <el-dropdown-item command="my-grades">
                    <el-icon><DataLine /></el-icon> 学习成绩与分析
                  </el-dropdown-item>
                </template>

                <!-- Teacher Menu Items -->
                <template v-if="userStore.roleCode === 'teacher' || userStore.roleCode === 'admin'">
                  <el-dropdown-item command="courses">
                    <el-icon><Reading /></el-icon> 课程管理
                  </el-dropdown-item>
                  <el-dropdown-item command="teaching-classes">
                    <el-icon><School /></el-icon> 教学班级管理
                  </el-dropdown-item>
                  <el-dropdown-item command="questions">
                    <el-icon><Edit /></el-icon> 题库管理
                  </el-dropdown-item>
                  <el-dropdown-item command="papers">
                    <el-icon><Document /></el-icon> 试卷配置
                  </el-dropdown-item>
                  <el-dropdown-item command="exams">
                    <el-icon><Checked /></el-icon> 考试与成绩发布
                  </el-dropdown-item>
                </template>

                <el-dropdown-item divided command="logout" class="logout-item">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <div class="auth-buttons">
            <el-button @click="router.push('/login')" class="btn-login">登录</el-button>
            <el-button type="primary" @click="router.push('/register')" class="btn-reg">免费加入</el-button>
          </div>
        </template>
      </div>
    </el-header>

    <!-- Main Content Body (With Conditionally Displayed Sidebar for Internal Workspaces) -->
    <el-container class="body-container">
      <el-aside v-if="shouldShowSidebar" width="220px" class="layout-aside">
        <div class="aside-title">
          <span>工作台菜单</span>
        </div>
        <el-menu
          :default-active="activeMenu"
          :router="true"
          class="el-menu-vertical"
        >
          <template v-for="route in menuRoutes" :key="route.path">
            <el-menu-item :index="resolveMenuPath(route)" v-if="!route.meta.hidden && !route.meta.noAuth">
              <el-icon><component :is="route.meta.icon || 'Menu'" /></el-icon>
              <span>{{ route.meta.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>

      <el-main class="layout-main" :class="{ 'public-main': isPublicHome }">
        <router-view />
      </el-main>
    </el-container>

    <!-- Global Floating AI Assistant Modal/Widget (For non-homepage views) -->
    <el-dialog
      v-model="aiModalVisible"
      title="AI 知识库与智能教学助理"
      width="680px"
      class="ai-dialog"
      :append-to-body="true"
      destroy-on-close
    >
      <AiChatWidget :inline="false" />
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { layoutChildren } from '@/router'
import { ElMessage } from 'element-plus'
import {
  House, DataBoard, Aim, Lightning, Cpu, School, Tickets, Checked,
  DataLine, Reading, Edit, Document, SwitchButton, User, Menu
} from '@element-plus/icons-vue'
import AiChatWidget from '@/components/AiChatWidget.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const aiModalVisible = ref(false)

const isPublicHome = computed(() => route.path === '/' || route.path === '')
const shouldShowSidebar = computed(() => userStore.isLoggedIn && !isPublicHome.value && route.name !== 'CourseLearn')

const activeTopMenu = computed(() => {
  if (route.path === '/') return '/'
  if (route.path.startsWith('/recommendation')) return '/recommendation'
  if (route.path.startsWith('/resource-gen')) return '/resource-gen'
  return '/home'
})

const activeMenu = computed(() => {
  const firstSegment = route.path.split('/').filter(Boolean)[0]
  return firstSegment ? `/${firstSegment}` : '/home'
})

const resolveMenuPath = (routeRecord) => `/${routeRecord.path}`.replace(/\/+/g, '/')

const menuRoutes = computed(() => {
  return layoutChildren.filter(r => {
    if (r.meta?.hidden) return false
    if (!r.meta?.roles) return true
    if (userStore.roleCode === 'admin') return true
    return r.meta.roles.includes(userStore.roleCode)
  })
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

const toggleAiModal = () => {
  aiModalVisible.value = !aiModalVisible.value
}

const handleCommand = (command) => {
  if (command === 'logout') {
    userStore.logout()
    ElMessage.success('已安全退出登录')
    router.push('/')
  } else {
    router.push(`/${command}`)
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
  padding: 0 32px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-color);
  position: sticky;
  top: 0;
  z-index: 1000;
  box-shadow: 0 1px 3px rgba(0,0,0,0.02);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-color-hover));
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
  letter-spacing: -0.02em;
}

.sub-title {
  font-weight: 400;
  color: var(--text-secondary);
  font-size: 0.95rem;
  margin-left: 4px;
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.top-menu {
  background: transparent !important;
  border-bottom: none !important;
}

.top-menu .el-menu-item {
  height: 64px;
  line-height: 64px;
  font-size: 0.95rem;
  font-weight: 500;
  color: var(--text-secondary);
  transition: all 0.2s ease;
}

.top-menu .el-menu-item.is-active {
  color: var(--primary-color) !important;
  font-weight: 600;
  border-bottom: 2px solid var(--primary-color) !important;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-ai-btn {
  display: flex;
  align-items: center;
}

.ai-trigger-btn {
  font-weight: 600;
  padding: 6px 14px;
  border-radius: 20px;
  background: #EEF2FF;
  color: var(--primary-color);
  transition: all 0.3s ease;
}

.ai-trigger-btn:hover {
  background: #E0E7FF;
  transform: scale(1.02);
}

.ai-icon {
  margin-right: 4px;
  font-size: 16px;
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

.auth-buttons {
  display: flex;
  gap: 12px;
}

.btn-login {
  border: none;
  background: transparent;
  font-weight: 600;
  color: var(--text-primary);
}

.btn-reg {
  border-radius: 20px;
  padding: 8px 20px;
  font-weight: 600;
  background-color: var(--primary-color);
  border: none;
}

/* Body and Sidebar */
.body-container {
  flex: 1;
  overflow: hidden;
}

.layout-aside {
  background: white;
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}

.aside-title {
  padding: 20px 24px 10px;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.el-menu-vertical {
  border-right: none !important;
  padding: 0 12px;
}

.el-menu-vertical .el-menu-item {
  border-radius: 10px;
  margin-bottom: 4px;
  height: 44px;
  line-height: 44px;
  color: var(--text-secondary);
  font-weight: 500;
}

.el-menu-vertical .el-menu-item.is-active {
  background-color: #EEF2FF;
  color: var(--primary-color);
  font-weight: 600;
}

.el-menu-vertical .el-menu-item:hover {
  background-color: var(--bg-hover);
}

.layout-main {
  flex: 1;
  overflow-y: auto;
  padding: 32px;
  background-color: #F8FAFC;
}

.public-main {
  padding: 0;
  background-color: white;
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

.logout-item {
  color: #EF4444 !important;
}
</style>