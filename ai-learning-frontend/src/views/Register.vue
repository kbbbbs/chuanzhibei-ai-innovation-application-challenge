<template>
  <div class="auth-page">
    <section class="auth-box">
      <h2>用户注册</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="70px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="确认" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
        </el-form-item>
        <el-form-item label="身份" prop="roleCode">
          <el-radio-group v-model="form.roleCode">
            <el-radio value="student">学生注册</el-radio>
            <el-radio value="teacher">教师入驻申请</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.roleCode === 'teacher'" label="说明" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请填写授课方向、学校/机构、申请原因" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="register">注册</el-button>
          <el-button @click="router.push('/login')">返回登录</el-button>
        </el-form-item>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request.js'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const form = reactive({
  username: '',
  nickname: '',
  password: "[REDACTED]"
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
  password: "[REDACTED]"
  confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择身份', trigger: 'change' }],
  reason: [{ required: true, message: '请填写教师入驻说明', trigger: 'blur' }],
}

const register = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const endpoint = form.roleCode === 'teacher' ? '/teacher-applications' : '/auth/register'
    const res = await request.post(endpoint, {
      username: form.username,
      nickname: form.nickname,
      password: "[REDACTED]"
      roleCode: 'student',
      reason: form.reason
    })
    if (res.code === '200') {
      ElMessage.success(form.roleCode === 'teacher' ? '申请已提交，请等待管理员审核' : '注册成功，请登录')
      router.push('/login')
    }
  } catch {} finally {
    loading.value = false
  }
}
</script>
