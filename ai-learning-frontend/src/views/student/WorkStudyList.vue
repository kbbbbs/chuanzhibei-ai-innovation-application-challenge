<template>
  <div class="work-study-list">
    <el-card>
      <template #header>
        <span>勤工俭学岗位招聘</span>
      </template>

      <el-form :inline="true" :model="searchForm">
        <el-form-item label="岗位名称">
          <el-input v-model="searchForm.jobName" placeholder="请输入岗位名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" border stripe v-loading="loading">
        <template #empty><AppEmpty /></template>
        <el-table-column prop="jobName" label="岗位名称" min-width="120" />
        <el-table-column prop="jobDesc" label="岗位描述" show-overflow-tooltip min-width="180" />
        <el-table-column prop="workLocation" label="工作地点" width="120" />
        <el-table-column prop="workHours" label="工作时间" width="160" />
        <el-table-column prop="hourlyRate" label="时薪(元)" width="100">
          <template #default="{ row }">
            <el-tag type="warning">{{ row.hourlyRate }} 元/小时</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="maxRecruit" label="招聘人数" width="100" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              :loading="applyingId === row.id"
              :disabled="appliedIds.includes(row.id)"
              @click="handleApply(row)"
            >
              {{ appliedIds.includes(row.id) ? '已申请' : '申请岗位' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import { getRecords, getTotal } from '@/utils/apiHelpers'
import { getWorkStudies, applyForPosition, getMyApplications } from '@/api/workStudy'

const userStore = useUserStore()

const list = ref([])
const loading = ref(false)
const applyingId = ref(null)
const appliedIds = ref([])

const searchForm = reactive({
  jobName: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const loadAppliedIds = async () => {
  try {
    const res = await getMyApplications({
      pageNum: 1,
      pageSize: 999,
      studentId: Number(userStore.userId)
    })
    if (res.code === '200') {
      const records = getRecords(res.data)
      appliedIds.value = records.map(r => r.workStudyId)
    }
  } catch (err) {
    // ignore
  }
}

const loadData = async () => {
  const params = {
    jobName: searchForm.jobName || undefined,
    status: 1, // 只显示招聘中的岗位
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }
  loading.value = true
  try {
    const res = await getWorkStudies(params)
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

const handleApply = async (row) => {
  applyingId.value = row.id
  try {
    const res = await applyForPosition({
      workStudyId: row.id,
      studentId: Number(userStore.userId)
    })
    if (res.code === '200') {
      ElMessage.success('申请成功，请等待审核')
      appliedIds.value.push(row.id)
    }
  } catch (err) {
    // 全局已提示
  } finally {
    applyingId.value = null
  }
}

const handleReset = () => {
  searchForm.jobName = ''
  pagination.pageNum = 1
  loadData()
}

onMounted(() => {
  loadAppliedIds()
  loadData()
})
</script>

<style scoped>
.pagination {
  margin-top: 20px;
  justify-content: center;
}
</style>
