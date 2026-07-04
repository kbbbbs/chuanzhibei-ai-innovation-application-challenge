<template>
  <main class="page-shell" v-loading="loading">
    <section class="intro">
      <p class="eyebrow">AI Academy</p>
      <h1>AI智学平台</h1>
      <p class="summary">用于课程学习、练习测评和学习辅助的教学项目。</p>
<!--      <div class="home-actions">-->
<!--        <el-button type="primary" @click="goLogin">登录体验</el-button>-->
<!--        <el-button @click="loadInfo">读取登录信息</el-button>-->
<!--        <el-button @click="loadCourses">查看课程接口</el-button>-->
<!--      </div>-->
      <p v-if="message" class="home-message">{{ message }}</p>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request.js'
import { getCourses } from '@/utils/course.js'
import { getTotal } from '@/utils/apiHelpers.js'

const router = useRouter()
const message = ref('')
const loading = ref(false)

const goLogin = () => {
  router.push('/login')
}

// const loadInfo = async () => {
//   loading.value = true
//   try {
//     const res = await request.get('/auth/info')
//     if (res.code === '200') {
//       message.value = '当前登录用户：' + res.data.nickname
//     } else {
//       message.value = '请先登录后再查看用户信息'
//     }
//   } catch (e) {
//     message.value = '获取用户信息失败'
//   } finally {
//     loading.value = false
//   }
// }
//
// const loadCourses = async () => {
//   loading.value = true
//   try {
//     const res = await getCourses({ pageNum: 1, pageSize: 5 })
//     if (res.code === '200') {
//       message.value = '课程接口已接入，共 ' + getTotal(res.data) + ' 门课程'
//     }
//   } catch (e) {
//     message.value = '获取课程信息失败'
//   } finally {
//     loading.value = false
//   }
// }
</script>
