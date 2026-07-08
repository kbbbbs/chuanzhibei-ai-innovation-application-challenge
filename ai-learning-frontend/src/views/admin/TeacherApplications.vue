<template>
  <div class="teacher-applications">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>教师入驻审核</span>
          <el-radio-group v-model="status" size="small" @change="loadApplications">
            <el-radio-button :label="0">待审核</el-radio-button>
            <el-radio-button :label="1">已通过</el-radio-button>
            <el-radio-button :label="2">已拒绝</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-table :data="applications" border stripe v-loading="loading">
        <template #empty><AppEmpty /></template>
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="教师姓名" width="140" />
        <el-table-column prop="email" label="邮箱" width="180" />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column prop="reason" label="申请说明" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button size="small" type="success" @click="review(row, 1)">通过</el-button>
              <el-button size="small" type="danger" @click="review(row, 2)">拒绝</el-button>
            </template>
            <span v-else class="muted">已处理</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppEmpty from '@/components/AppEmpty.vue'
import { getRecords } from '@/utils/apiHelpers'
import request from '@/utils/request'

const status = ref(0)
const loading = ref(false)
const applications = ref([])

const statusText = value => ({ 0: '待审核', 1: '已通过', 2: '已拒绝' }[value] || '未知')
const statusTag = value => ({ 0: 'warning', 1: 'success', 2: 'danger' }[value] || 'info')

const loadApplications = async () => {
  loading.value = true
  try {
    const res = await request.get('/teacher-applications', { params: { status: status.value, pageSize: 100 } })
    if (res.code === '200') applications.value = getRecords(res.data)
  } finally {
    loading.value = false
  }
}

const review = async (row, nextStatus) => {
  const action = nextStatus === 1 ? '通过' : '拒绝'
  const { value } = await ElMessageBox.prompt(`确认${action} ${row.nickname || row.username} 的教师申请？`, '教师审核', {
    confirmButtonText: action,
    cancelButtonText: '取消',
    inputPlaceholder: '审核意见（可选）'
  })
  const res = await request.post(`/teacher-applications/${row.id}/review`, {
    status: nextStatus,
    reviewComment: value || ''
  })
  if (res.code === '200') {
    ElMessage.success(`已${action}`)
    loadApplications()
  }
}

onMounted(loadApplications)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.muted {
  color: var(--text-muted);
}
</style>
