<template>
  <div class="header-actions">
    <button type="button" class="action-button" :aria-label="publicHome ? '进入控制台' : '返回首页'" @click="openPrimary">
      <el-icon :size="18" aria-hidden="true"><component :is="publicHome ? DataBoard : House" /></el-icon>
      <span class="action-label">{{ publicHome ? '控制台' : '首页' }}</span>
    </button>
    <button type="button" class="action-button" aria-label="查看通知" @click="openNotices">
      <el-icon :size="18" aria-hidden="true"><Bell /></el-icon>
      <span class="action-label">通知</span>
    </button>
    <ThemeToggle />

    <el-dropdown v-if="userStore.isLoggedIn" trigger="click" @command="handleCommand">
      <button type="button" class="account-trigger" :aria-label="`账户菜单：${userStore.nickname}，${roleName}`">
        <el-avatar :size="30" class="account-avatar" :src="avatarUrl">{{ avatarInitial }}</el-avatar>
        <span class="account-name">{{ userStore.nickname }}</span>
      </button>
      <template #dropdown>
        <el-dropdown-menu class="account-menu">
          <div class="account-summary">
            <p class="summary-name">{{ userStore.nickname }}</p>
            <p class="summary-role">{{ roleName }}</p>
          </div>
          <el-dropdown-item command="/profile" :icon="Setting">个人设置</el-dropdown-item>
          <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    <el-button v-else class="login-button" @click="router.push('/login')">登录</el-button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, DataBoard, House, Setting, SwitchButton } from '@element-plus/icons-vue'
import ThemeToggle from '@/components/ui/ThemeToggle.vue'
import { useUserStore } from '@/store/user'
import { resolveResourceUrl } from '@/utils/backendUrl'

const props = defineProps({
  publicHome: { type: Boolean, default: false },
})

const router = useRouter()
const userStore = useUserStore()

const ROLE_NAMES = { admin: '系统管理员', teacher: '教师导师', student: '学习者' }

const roleName = computed(() => ROLE_NAMES[userStore.roleCode] || '用户')
const avatarUrl = computed(() => resolveResourceUrl(userStore.avatar))
const avatarInitial = computed(() => (userStore.nickname || 'U').charAt(0).toUpperCase())

function openPrimary() {
  router.push(props.publicHome ? '/console' : '/home')
}

function openNotices() {
  if (userStore.isLoggedIn) {
    router.push('/notices')
    return
  }
  router.push({ path: '/login', query: { redirect: '/notices' } })
}

async function handleCommand(command) {
  if (command !== 'logout') {
    router.push(command)
    return
  }
  userStore.logout()
  await router.replace({ name: 'LoginLanding' })
  ElMessage.success('已安全退出登录')
}
</script>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex: 0 0 auto;
}

.action-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 36px;
  height: 36px;
  padding: 0 10px;
  color: var(--text-subtle);
  background: transparent;
  border: 0;
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background-color .15s var(--ease-standard), color .15s var(--ease-standard);
}

.action-button:hover {
  color: var(--text-strong);
  background: var(--surface-hover);
}

.account-trigger {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  max-width: 180px;
  height: 40px;
  margin-left: var(--space-1);
  padding: 0 var(--space-2) 0 var(--space-1);
  color: var(--text-strong);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  cursor: pointer;
}

.account-trigger:hover {
  background: var(--surface-hover);
}

.account-avatar {
  flex: 0 0 auto;
  color: var(--accent);
  background: var(--surface-accent-strong);
  font-weight: 650;
}

.account-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm);
  font-weight: 600;
}

.login-button {
  margin-left: var(--space-1);
}

.account-summary {
  min-width: 200px;
  padding: var(--space-2) var(--space-4) var(--space-3);
  border-bottom: 1px solid var(--line-subtle);
  margin-bottom: var(--space-1);
}

.account-summary p {
  margin: 0;
}

.summary-name {
  color: var(--text-strong);
  font-weight: 650;
}

.summary-role {
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

@media (max-width: 1180px) {
  .action-label {
    display: none;
  }

  .action-button {
    padding: 0;
    justify-content: center;
  }
}

@media (max-width: 640px) {
  .account-name {
    display: none;
  }

  .account-trigger {
    padding-right: var(--space-1);
  }
}
</style>
