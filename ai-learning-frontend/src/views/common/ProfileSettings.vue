<template>
  <div class="profile-page workspace-page" v-loading="loading">
    <PageHeader eyebrow="账户" title="个人设置" description="维护个人资料和登录密码。密码只在保存时发送，不会显示或保留在页面上。">
      <template #actions>
        <el-button v-if="!loadFailed" :icon="Refresh" :loading="loading" :disabled="saving" @click="handleReload">重新加载</el-button>
      </template>
    </PageHeader>

    <ErrorNotice
      v-if="loadFailed"
      :title="profileReady ? '个人资料未刷新' : '个人资料未加载'"
      :message="profileReady ? '下方是上次读取的资料和你未保存的修改，没有被清除。' : '为避免用空白内容覆盖原资料，保存和修改密码已暂停。'"
      retry-label="重新加载"
      :busy="loading"
      @retry="handleReload"
    />

    <div class="profile-layout">
      <aside class="profile-summary">
        <el-avatar :size="112" :src="avatarPreview" class="profile-avatar">
          {{ avatarText }}
        </el-avatar>
        <h2>{{ form.nickname || form.username || userStore.username }}</h2>
        <p>@{{ form.username || userStore.username }}</p>
        <StatusBadge :label="roleName" tone="accent" />

        <dl v-if="profileReady" class="profile-meta">
          <div>
            <dt>邮箱</dt>
            <dd>{{ form.email || '未填写' }}</dd>
          </div>
          <div>
            <dt>手机号</dt>
            <dd>{{ form.phone || '未填写' }}</dd>
          </div>
        </dl>
      </aside>

      <section class="profile-form-panel">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          :disabled="formLocked"
          label-position="top"
          status-icon
          @submit.prevent
        >
          <section class="form-section">
            <div class="section-heading">
              <h2>基本信息</h2>
            </div>
            <div class="form-grid">
              <el-form-item label="用户名">
                <el-input v-model="form.username" disabled />
              </el-form-item>
              <el-form-item label="昵称" prop="nickname">
                <el-input v-model="form.nickname" maxlength="50" show-word-limit />
              </el-form-item>
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="form.email" maxlength="100" clearable />
              </el-form-item>
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="form.phone" maxlength="20" clearable />
              </el-form-item>
              <el-form-item class="full-field" label="头像地址" prop="avatar">
                <el-input v-model="form.avatar" maxlength="255" clearable />
              </el-form-item>
            </div>
          </section>

          <section class="form-section password-section">
            <div class="section-heading">
              <h2>修改密码</h2>
              <span>不修改时留空</span>
            </div>
            <div class="form-grid password-grid">
              <el-form-item label="当前密码" prop="currentPassword">
                <el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password" />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
              </el-form-item>
              <el-form-item label="确认新密码" prop="confirmPassword">
                <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
              </el-form-item>
            </div>
          </section>

          <div class="form-actions">
            <el-button @click="resetProfile">撤销修改</el-button>
            <el-button type="primary" :icon="Check" :loading="saving" @click="saveProfile">保存设置</el-button>
          </div>
        </el-form>
      </section>
    </div>

    <section class="danger-zone">
      <div>
        <h2>注销账号</h2>
        <p>账号将停止登录，课程、作业和学习记录会继续保留。</p>
      </div>
      <el-button type="danger" plain :icon="Delete" :disabled="!profileReady" @click="deactivateAccount">注销账号</el-button>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Check, Delete, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { requireSuccess } from '@/utils/apiHelpers'
import { resolveResourceUrl } from '@/utils/backendUrl'
import request from '@/utils/request'

const EDITABLE_FIELDS = ['nickname', 'email', 'phone', 'avatar']

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const { loading, loadFailed, guarded } = useGuardedLoad()
const saving = ref(false)
const originalProfile = ref(null)
// A save sends every field, so nothing may be written until the real profile has been read.
const profileReady = ref(false)
const formLocked = computed(() => !profileReady.value || loading.value)
const savedFields = ref({})

const form = reactive({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  avatar: '',
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const roleName = computed(() => ({
  admin: '系统管理员',
  teacher: '教师导师',
  student: '学习者',
})[userStore.roleCode] || '用户')

const avatarText = computed(() => (form.nickname || form.username || 'U').charAt(0).toUpperCase())
const avatarPreview = computed(() => resolveResourceUrl(form.avatar))
const isDirty = computed(() => profileReady.value && (
  Boolean(form.currentPassword || form.newPassword || form.confirmPassword)
  || EDITABLE_FIELDS.some(field => form[field] !== savedFields.value[field])
))

const validateCurrentPassword = (_rule, value, callback) => {
  if (form.newPassword && !value) {
    callback(new Error('请输入当前密码'))
    return
  }
  callback()
}

const validateNewPassword = (_rule, value, callback) => {
  if (value && value.length < 6) {
    callback(new Error('新密码至少 6 位'))
    return
  }
  callback()
}

const validateConfirmPassword = (_rule, value, callback) => {
  if (form.newPassword && value !== form.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

const rules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 50, message: '昵称不能超过 50 个字符', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }],
  phone: [{ pattern: /^[0-9+()\-\s]*$/, message: '请输入有效的手机号', trigger: 'blur' }],
  currentPassword: [{ validator: validateCurrentPassword, trigger: ['blur', 'change'] }],
  newPassword: [{ validator: validateNewPassword, trigger: ['blur', 'change'] }],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: ['blur', 'change'] }],
}

function assignProfile(profile) {
  form.username = profile.username || userStore.username
  form.nickname = profile.nickname || form.username
  form.email = profile.email || ''
  form.phone = profile.phone || ''
  form.avatar = profile.avatar || ''
  form.currentPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  savedFields.value = Object.fromEntries(EDITABLE_FIELDS.map(field => [field, form[field]]))
}

function loadProfile() {
  return guarded(async (isCurrent) => {
    const res = requireSuccess(await request.get('/users/me'))
    if (!isCurrent()) return
    if (!res.data || typeof res.data !== 'object' || Array.isArray(res.data)) {
      throw new Error('个人资料无效')
    }
    originalProfile.value = { ...res.data }
    assignProfile(res.data)
    userStore.updateProfile(res.data)
    profileReady.value = true
    await nextTick()
    formRef.value?.clearValidate()
  })
}

async function handleReload() {
  if (isDirty.value) {
    try {
      await ElMessageBox.confirm('重新加载会放弃未保存的修改，确定继续吗？', '重新加载', {
        confirmButtonText: '重新加载',
        cancelButtonText: '继续编辑',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  await loadProfile()
}

async function saveProfile() {
  if (formLocked.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    const res = await request.put('/users/me', {
      nickname: form.nickname,
      email: form.email,
      phone: form.phone,
      avatar: form.avatar,
      currentPassword: form.currentPassword || null,
      newPassword: form.newPassword || null,
    })
    if (res.code === '200') {
      originalProfile.value = { ...res.data }
      assignProfile(res.data)
      userStore.updateProfile(res.data)
      ElMessage.success(res.msg || '个人信息已更新')
      await nextTick()
      formRef.value?.clearValidate()
    }
  } catch {
  } finally {
    saving.value = false
  }
}

async function resetProfile() {
  if (!originalProfile.value) return
  assignProfile(originalProfile.value)
  await nextTick()
  formRef.value?.clearValidate()
}

async function deactivateAccount() {
  try {
    const { value } = await ElMessageBox.prompt('请输入当前密码确认注销账号', '注销账号', {
      confirmButtonText: '确认注销',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
      inputType: 'password',
      inputPlaceholder: '当前密码',
      inputValidator: value => Boolean(value?.trim()) || '请输入当前密码',
    })
    const res = await request.delete('/users/me', {
      data: { currentPassword: value },
    })
    if (res.code === '200') {
      userStore.logout()
      ElMessage.success(res.msg || '账号已注销')
      router.replace('/')
    }
  } catch (action) {
    if (action === 'cancel' || action === 'close') return
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-page {
  width: min(1120px, 100%);
}

.profile-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: var(--space-5);
  align-items: start;
}

.profile-summary,
.profile-form-panel,
.danger-zone {
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
}

.profile-summary {
  padding: var(--space-8) var(--space-6);
  text-align: center;
}

.profile-avatar {
  color: #fff;
  background: var(--accent-solid);
  font-size: 2rem;
  font-weight: 650;
}

.profile-summary h2 {
  margin: 20px 0 4px;
  font-size: 1.35rem;
  overflow-wrap: anywhere;
}

.profile-summary > p {
  margin: 0 0 16px;
  color: var(--text-secondary);
  overflow-wrap: anywhere;
}

.profile-meta {
  margin: 28px 0 0;
  text-align: left;
}

.profile-meta div {
  padding: 14px 0;
  border-top: 1px solid var(--border-color);
}

.profile-meta dt {
  margin-bottom: 5px;
  color: var(--text-muted);
  font-size: 0.75rem;
  font-weight: 700;
}

.profile-meta dd {
  margin: 0;
  color: var(--text-primary);
  font-size: 0.9rem;
  overflow-wrap: anywhere;
}

.profile-form-panel {
  overflow: hidden;
}

.form-section {
  padding: 26px 28px 8px;
}

.password-section {
  border-top: 1px solid var(--border-color);
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-heading h2,
.danger-zone h2 {
  margin: 0;
  font-size: 1.15rem;
  letter-spacing: 0;
}

.section-heading span {
  color: var(--text-muted);
  font-size: 0.75rem;
  font-weight: 700;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.password-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.full-field {
  grid-column: 1 / -1;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 18px 28px 26px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-subtle);
}

.danger-zone {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: var(--space-5) var(--space-6);
  border-color: var(--critical-line);
  box-shadow: none;
}

.danger-zone h2 {
  color: var(--critical);
}

.danger-zone p {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 0.9rem;
}

@media (max-width: 900px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }

  .profile-summary {
    display: grid;
    grid-template-columns: auto 1fr auto;
    align-items: center;
    gap: 8px 18px;
    text-align: left;
  }

  .profile-summary h2,
  .profile-summary > p {
    margin: 0;
  }

  .profile-summary :deep(.status-badge) {
    justify-self: start;
  }

  .profile-avatar {
    grid-row: 1 / 3;
  }

  .profile-meta {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
    margin-top: 14px;
  }
}

@media (max-width: 680px) {
  .danger-zone {
    align-items: stretch;
    flex-direction: column;
  }

  .form-grid,
  .password-grid,
  .profile-meta {
    grid-template-columns: 1fr;
  }

  .profile-summary {
    grid-template-columns: auto 1fr;
  }

  .profile-summary :deep(.status-badge) {
    grid-column: 2;
  }

  .form-section,
  .form-actions {
    padding-right: 20px;
    padding-left: 20px;
  }

  .form-actions {
    flex-direction: column-reverse;
  }

  .form-actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>
