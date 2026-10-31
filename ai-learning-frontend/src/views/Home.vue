<template>
  <component :is="dashboardComponent" v-if="dashboardComponent" />
  <el-result
    v-else
    icon="warning"
    title="暂时无法打开控制台"
    sub-title="当前账号缺少有效角色，请重新登录或联系管理员。"
  />
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'
import StudentDashboard from './dashboard/StudentDashboard.vue'
import TeacherDashboard from './dashboard/TeacherDashboard.vue'
import AdminDashboard from './dashboard/AdminDashboard.vue'

const userStore = useUserStore()

const dashboards = Object.freeze({
  student: StudentDashboard,
  teacher: TeacherDashboard,
  admin: AdminDashboard,
})

const dashboardComponent = computed(() => dashboards[String(userStore.roleCode || '').toLowerCase()] || null)
</script>
