<template>
  <div class="auth-page">
    <section class="auth-box">
      <h2>用户登录</h2>
      <el-form ref="formRef" :model="data.form" :rules="rules" label-width="70px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="data.form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="data.form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="login">登录</el-button>
          <el-button @click="router.push('/register')">注册账号</el-button>
        </el-form-item>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import request from '@/utils/request.js'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const data = reactive({
  form: {
    username: '',
    password: '',
  },
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const login = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const res = await request.post('/auth/login', data.form)
    if (res.code === '200') {
      userStore.setUser(res.data)
      ElMessage.success('登录成功')
      const redirect = typeof route.query.redirect === 'string' && /^\/(?!\/)/.test(route.query.redirect)
        ? route.query.redirect
        : '/console'
      router.push(redirect)
    }
  } catch {} finally {
    loading.value = false
  }
}
</script>
