<template>
  <AuthShell title="登录 EduLoop" description="使用你的账号继续学习或进入教学工作台。">
    <ErrorNotice v-if="errorMessage" class="auth-error" title="登录未成功" :message="errorMessage" />
    <el-form ref="formRef" :model="data.form" :rules="rules" label-position="top" @submit.prevent="login">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="data.form.username" size="large" autocomplete="username" placeholder="请输入用户名" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="data.form.password"
          size="large"
          type="password"
          show-password
          autocomplete="current-password"
          placeholder="请输入密码"
        />
      </el-form-item>
      <el-button type="primary" size="large" class="auth-submit" native-type="submit" :loading="loading">登录</el-button>
    </el-form>
    <p class="auth-switch">还没有账号？<router-link to="/register">注册或申请教师入驻</router-link></p>
  </AuthShell>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import request from '@/utils/request.js'
import AuthShell from '@/components/auth/AuthShell.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const errorMessage = ref('')
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
  if (loading.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const res = await request.post('/auth/login', data.form, { silentError: true })
    if (res.code === '200') {
      userStore.setUser(res.data)
      ElMessage.success('登录成功')
      const redirect = typeof route.query.redirect === 'string' && /^\/(?!\/)/.test(route.query.redirect)
        ? route.query.redirect
        : '/console'
      router.push(redirect)
    } else {
      errorMessage.value = res.msg || '用户名或密码不正确'
    }
  } catch (error) {
    const body = error?.response?.data
    errorMessage.value = body?.msg || body?.message || (error?.response ? '登录失败，请稍后重试' : '无法连接到服务，请确认网络后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-error {
  margin-bottom: var(--space-4);
}

.auth-submit {
  width: 100%;
  margin-top: var(--space-2);
}

.auth-switch {
  margin: var(--space-5) 0 0;
  color: var(--text-subtle);
  text-align: center;
}
</style>
