<template>
  <div class="my-work-study workspace-page">
    <PageHeader eyebrow="校园事务" title="我的岗位" description="查看勤工俭学申请的审核结果，以及已通过岗位的劳务信息。">
      <template #actions>
        <el-button @click="$router.push('/work-study-jobs')">浏览岗位</el-button>
      </template>
    </PageHeader>

    <section class="table-panel" aria-label="我的申请">
      <div class="tab-strip">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane label="全部" name="all" />
          <el-tab-pane label="待审核" name="0" />
          <el-tab-pane label="已通过" name="1" />
          <el-tab-pane label="已拒绝" name="2" />
        </el-tabs>
      </div>

      <ErrorNotice
        v-if="loadFailed"
        title="申请记录未加载"
        message="请确认网络后重试。"
        retry-label="重新加载"
        :busy="loading"
        @retry="loadData"
      />

      <el-table v-else :data="list" stripe v-loading="loading">
        <template #empty><AppEmpty description="暂无申请记录" /></template>
        <el-table-column prop="jobName" label="岗位名称" min-width="140">
          <template #default="{ row }"><span class="row-title">{{ row.jobName }}</span></template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" width="170">
          <template #default="{ row }">
            {{ row.applyTime ? new Date(row.applyTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.auditStatus === 0" type="warning">待审核</el-tag>
            <el-tag v-else-if="row.auditStatus === 1" type="success">已通过</el-tag>
            <el-tag v-else-if="row.auditStatus === 2" type="danger">已拒绝</el-tag>
            <el-tag v-else type="info">未知</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="auditTime" label="审核时间" width="170">
          <template #default="{ row }">
            {{ row.auditTime ? new Date(row.auditTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="rejectReason" label="拒绝原因" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.rejectReason || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="劳务信息" min-width="200" v-if="showWorkInfo">
          <template #default="{ row }">
            <template v-if="row.auditStatus === 1">
              <div class="work-info">
                <span>月份：{{ row.workMonth || '待填写' }}</span>
                <span>时长：{{ row.workHours || 0 }} 小时</span>
                <span>时薪：{{ row.hourlyRate || 0 }} 元</span>
                <span>劳务费：<el-tag type="success" size="small">{{ row.totalAmount || 0 }} 元</el-tag></span>
              </div>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="!loadFailed"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadData"
        @size-change="handleSizeChange"
      />
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { getRecords, getTotal, requireSuccess } from '@/utils/apiHelpers'
import { getMyApplications } from '@/api/workStudy'

const userStore = useUserStore()

const list = ref([])
const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  list.value = []
  pagination.total = 0
})
const activeTab = ref('all')

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const showWorkInfo = computed(() => {
  return activeTab.value === '1' || activeTab.value === 'all'
})

const loadData = () => guarded(async (isCurrent) => {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    studentId: Number(userStore.userId)
  }
  if (activeTab.value !== 'all') {
    params.auditStatus = Number(activeTab.value)
  }
  const res = requireSuccess(await getMyApplications(params))
  if (!isCurrent()) return
  list.value = getRecords(res.data)
  pagination.total = getTotal(res.data)
})

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadData()
}

const handleTabChange = () => {
  pagination.pageNum = 1
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.tab-strip {
  padding: var(--space-1) var(--space-5) 0;
}

.tab-strip :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.work-info {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  color: var(--text-body);
  font-size: var(--text-sm);
}
</style>
