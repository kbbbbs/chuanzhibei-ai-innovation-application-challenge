<template>
  <div class="teacher-applications workspace-page">
    <PageHeader eyebrow="系统配置" title="教师入驻审核" description="审核通过后账号获得教师权限；审核意见会随结果保存。">
      <template #actions>
        <el-radio-group v-model="status" aria-label="审核状态" @change="handleStatusChange">
          <el-radio-button :label="0">待审核</el-radio-button>
          <el-radio-button :label="1">已通过</el-radio-button>
          <el-radio-button :label="2">已拒绝</el-radio-button>
        </el-radio-group>
      </template>
    </PageHeader>

    <section class="filter-panel" aria-label="筛选申请">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="请输入用户名、姓名、邮箱或电话" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <ErrorNotice
      v-if="loadFailed"
      title="申请列表未加载"
      message="请确认网络后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadApplications"
    />

    <section v-else class="table-panel" aria-label="申请列表">
      <el-table :data="applications" stripe v-loading="loading">
        <template #empty><AppEmpty /></template>
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="教师姓名" width="140" />
        <el-table-column prop="email" label="邮箱" width="180" />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column prop="reason" label="申请说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="narrow ? ROW_SHEET_COLUMN_WIDTH : 180" fixed="right">
          <template #default="{ row }">
            <RowActions v-if="row.status === 0" :label="row.nickname || row.username || `申请 ${row.id}`">
              <el-button size="small" type="success" @click="review(row, 1)">通过</el-button>
              <el-button size="small" type="danger" @click="review(row, 2)">拒绝</el-button>
            </RowActions>
            <span v-else class="row-note">已处理</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadApplications"
        @size-change="handleSizeChange"
      />
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import RowActions from '@/components/ui/RowActions.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { ROW_SHEET_COLUMN_WIDTH, useNarrowScreen } from '@/composables/useNarrowScreen'
import { getRecords, getTotal, requireSuccess } from '@/utils/apiHelpers'
import request from '@/utils/request'

const narrow = useNarrowScreen()
const status = ref(0)
const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  applications.value = []
  pagination.total = 0
})
const applications = ref([])
const searchForm = reactive({
  keyword: ''
})
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const statusText = value => ({ 0: '待审核', 1: '已通过', 2: '已拒绝' }[value] || '未知')
const statusTag = value => ({ 0: 'warning', 1: 'success', 2: 'danger' }[value] || 'info')

const loadApplications = () => guarded(async (isCurrent) => {
  const res = requireSuccess(await request.get('/teacher-applications', {
    params: {
      status: status.value,
      keyword: searchForm.keyword.trim(),
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
  }))
  if (!isCurrent()) return
  applications.value = getRecords(res.data)
  pagination.total = getTotal(res.data)
})

const handleStatusChange = () => {
  pagination.pageNum = 1
  loadApplications()
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadApplications()
}

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadApplications()
}

const handleReset = () => {
  searchForm.keyword = ''
  pagination.pageNum = 1
  loadApplications()
}

const review = async (row, nextStatus) => {
  const action = nextStatus === 1 ? '通过' : '拒绝'
  try {
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
  } catch {
    // 取消审核，或请求错误已全局提示
  }
}

onMounted(loadApplications)
</script>
