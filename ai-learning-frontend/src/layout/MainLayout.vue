<template>
  <el-container class="layout-container">
    <el-aside v-if="userStore.isLoggedIn" width="200px" class="layout-aside">
      <div class="logo">EduLoop 智教云环</div>
      <el-menu
        :default-active="activeMenu"
        :router="true"
        class="el-menu-vertical"
      >
        <template v-for="route in menuRoutes" :key="route.path">
          <el-menu-item :index="resolveMenuPath(route)" v-if="!route.meta.hidden">
            <el-icon><component :is="route.meta.icon" /></el-icon>
            <span>{{ route.meta.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-brand">
          <span class="header-title">AI智学平台</span>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentRoute.meta.title">{{ currentRoute.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <el-dropdown @command="handleCommand">
          <span class="user-dropdown">
            <el-icon><User /></el-icon>
            {{ userStore.nickname }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { layoutChildren } from '@/router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const currentRoute = computed(() => route)
const resolveMenuPath = (routeRecord) => `/${routeRecord.path}`.replace(/\/+/g, '/')

const activeMenu = computed(() => {
  const firstSegment = route.path.split('/').filter(Boolean)[0]
  return firstSegment ? `/${firstSegment}` : '/home'
})

// Filter menu routes based on user role
const menuRoutes = computed(() => {
  return layoutChildren.filter(route => {
    if (route.meta?.hidden) return false
    if (!route.meta?.roles) return true
    if (userStore.roleCode === 'admin') return true
    return route.meta.roles.includes(userStore.roleCode)
  })
})

const handleCommand = (command) => {
  if (command === 'logout') {
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-aside {
  background: #F7F4EF;
  border-right: 1px solid #E7E1D7;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: #1F2421;
  border-bottom: 1px solid #E7E1D7;
}

.layout-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #FFFFFF;
  border-bottom: 1px solid #E7E1D7;
  padding: 0 20px;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 24px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #1F2421;
}

.user-dropdown {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #1F2421;
}

.layout-main {
  background: #FBF9F5;
  padding: 20px;
}
</style>
