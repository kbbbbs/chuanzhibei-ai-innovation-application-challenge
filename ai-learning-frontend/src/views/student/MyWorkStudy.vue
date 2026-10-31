<template>
  <div class="my-work-study">
    <el-card>
      <template #header>
        <span>我的勤工俭学</span>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="待审核" name="0" />
        <el-tab-pane label="已通过" name="1" />
        <el-tab-pane label="已拒绝" name="2" />
      </el-tabs>

      <el-table :data="list" border stripe v-loading="loading">
        <template #empty><AppEmpty /></template>
        <el-table-column prop="jobName" label="岗位名称" min-width="120" />
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
        class="pagination"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadData"
        @size-change="handleSizeChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import { getRecords, getTotal } from '@/utils/apiHelpers'
import { getMyApplications } from '@/api/workStudy'

const userStore = useUserStore()

const list = ref([])
const loading = ref(false)
const activeTab = ref('all')

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const showWorkInfo = computed(() => {
  return activeTab.value === '1' || activeTab.value === 'all'
})

const loadData = async () => {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    studentId: Number(userStore.userId)
  }
  if (activeTab.value !== 'all') {
    params.auditStatus = Number(activeTab.value)
  }
  loading.value = true
  try {
    const res = await getMyApplications(params)
    if (res.code === '200') {
      list.value = getRecords(res.data)
      pagination.total = getTotal(res.data)
    }
  } catch (err) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

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
.pagination {
  margin-top: 20px;
  justify-content: center;
}

.work-info {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}
</style>
