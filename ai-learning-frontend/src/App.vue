<template>
  <div class="app-layout">
    <header class="topbar">
      <router-link class="brand" to="/">AI智学平台</router-link>
      <nav>
        <router-link to="/">首页</router-link>
        <router-link v-if="!isLogin" to="/login">登录</router-link>
        <router-link v-if="!isLogin" to="/register">注册</router-link>
        <span v-if="isLogin" class="nickname">{{ nickname }}</span>
        <el-button v-if="isLogin" size="small" @click="logout">退出</el-button>
      </nav>
    </header>
    <router-view />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
const isLogin = computed(() => {
  route.fullPath
  return !!localStorage.getItem('token')
})
const nickname = computed(() => {
  route.fullPath
  return localStorage.getItem('nickname') || localStorage.getItem('username')
})

const logout = () => {
  localStorage.clear()
  router.push('/login')
}
</script>
