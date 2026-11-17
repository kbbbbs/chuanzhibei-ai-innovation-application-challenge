<template>
  <AuthShell title="创建账号" description="学生账号可直接注册；教师入驻需要管理员审核。" heading-id="register-heading">
    <ErrorNotice v-if="errorMessage" class="auth-error" title="提交未成功" :message="errorMessage" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="register">
      <el-form-item label="身份" prop="roleCode">
        <el-radio-group v-model="form.roleCode" class="role-switch">
          <el-radio-button value="student">学生注册</el-radio-button>
          <el-radio-button value="teacher">教师入驻申请</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <div class="field-pair">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" autocomplete="nickname" placeholder="请输入昵称" />
        </el-form-item>
      </div>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" type="password" show-password autocomplete="new-password" placeholder="请输入密码" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" placeholder="请再次输入密码" />
      </el-form-item>
      <el-form-item v-if="form.roleCode === 'teacher'" label="申请说明" prop="reason">
        <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请填写授课方向、学校/机构、申请原因" />
      </el-form-item>
      <el-button type="primary" size="large" class="auth-submit" native-type="submit" :loading="loading">
        {{ form.roleCode === 'teacher' ? '提交申请' : '注册' }}
      </el-button>
    </el-form>
    <p class="auth-switch">已有账号？<router-link to="/login">返回登录</router-link></p>
  </AuthShell>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request.js'
import AuthShell from '@/components/auth/AuthShell.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  roleCode: 'student',
  reason: '',
})

const validateConfirmPassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择身份', trigger: 'change' }],
  reason: [{ required: true, message: '请填写教师入驻说明', trigger: 'blur' }],
}

const register = async () => {
  if (loading.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const endpoint = form.roleCode === 'teacher' ? '/teacher-applications' : '/auth/register'
    const res = await request.post(endpoint, {
      username: form.username,
      nickname: form.nickname,
      password: form.password,
      roleCode: 'student',
      reason: form.reason
    }, { silentError: true })
    if (res.code === '200') {
      ElMessage.success(form.roleCode === 'teacher' ? '申请已提交，请等待管理员审核' : '注册成功，请登录')
      router.push('/login')
    } else {
      errorMessage.value = res.msg || '提交失败，请检查填写内容'
    }
  } catch (error) {
    const body = error?.response?.data
    errorMessage.value = body?.msg || body?.message || (error?.response ? '提交失败，请稍后重试' : '无法连接到服务，请确认网络后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-error {
  margin-bottom: var(--space-4);
}

.role-switch {
  display: flex;
  width: 100%;
}

.role-switch :deep(.el-radio-button) {
  flex: 1 1 0;
}

.role-switch :deep(.el-radio-button__inner) {
  width: 100%;
}

.field-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
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

@media (max-width: 480px) {
  .field-pair {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }
}
</style>
