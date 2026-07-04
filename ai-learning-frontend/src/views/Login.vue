<template>
  <div class="auth-page">
    <section class="auth-box">
      <h2>用户登录</h2>
      <el-form :model="data.form" label-width="70px">
        <el-form-item label="用户名">
          <el-input v-model="data.form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="data.form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="login">登录</el-button>
          <el-button @click="router.push('/register')">注册账号</el-button>
        </el-form-item>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import request from '@/utils/request.js'

const router = useRouter()
const userStore = useUserStore()
const data = reactive({
  form: {
    username: '',
    password: "[REDACTED]"
  },
})

const login = () => {
  if (!data.form.username || !data.form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  request.post('/auth/login', data.form).then(res => {
    if (res.code === '200') {
      userStore.setUser(res.data)
      ElMessage.success('登录成功')
      router.push('/')
    }
  })
}
</script>
