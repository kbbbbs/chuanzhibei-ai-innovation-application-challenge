<template>
  <main class="page-shell">
    <section class="intro">
      <p class="eyebrow">AI Academy</p>
      <h1>AI智学平台</h1>
      <p class="summary">用于课程学习、练习测评和学习辅助的教学项目。</p>
<!--      <div class="home-actions">-->
<!--        <el-button type="primary" @click="goLogin">登录体验</el-button>-->
<!--        <el-button @click="loadInfo">读取登录信息</el-button>-->
<!--      </div>-->
      <p v-if="message" class="home-message">{{ message }}</p>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request.js'

const router = useRouter()
const message = ref('')

// const goLogin = () => {
//   router.push('/login')
// }
//
// const loadInfo = () => {
//   request.get('/auth/info').then(res => {
//     if (res.code === '200') {
//       message.value = '当前登录用户：' + res.data.nickname
//     } else {
//       message.value = '请先登录后再查看用户信息'
//     }
//   })
// }
</script>
