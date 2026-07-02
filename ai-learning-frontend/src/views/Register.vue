<template>
  <div class="auth-page">
    <section class="auth-box">
      <h2>用户注册</h2>
      <el-form :model="data.form" label-width="70px">
        <el-form-item label="用户名">
          <el-input v-model="data.form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="data.form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="data.form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="确认">
          <el-input v-model="data.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="register">注册</el-button>
          <el-button @click="router.push('/login')">返回登录</el-button>
        </el-form-item>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request.js'

const router = useRouter()
const data = reactive({
  confirmPassword: '',
  form: {
    username: '',
    nickname: '',
    password: "[REDACTED]"
  },
})

const register = () => {
  // 前后端双重校验
  if (!data.form.username || !data.form.password) {
    ElMessage.warning('用户名和密码不能为空')
    return
  }
  if (data.form.password !== data.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  request.post('/auth/register', data.form).then(res => {
    if (res.code === '200') {
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    }
  })
}
</script>
