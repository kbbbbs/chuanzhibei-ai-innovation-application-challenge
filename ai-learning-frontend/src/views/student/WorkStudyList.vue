<template>
  <div class="work-study-list workspace-page">
    <PageHeader eyebrow="校园事务" title="勤工俭学" description="浏览招聘中的校内岗位。申请提交后，可在“我的岗位”查看审核进度。">
      <template #actions>
        <el-button @click="$router.push('/my-work-study')">我的岗位</el-button>
      </template>
    </PageHeader>

    <section class="filter-panel" aria-label="筛选岗位">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="岗位名称">
          <el-input v-model="searchForm.jobName" placeholder="请输入岗位名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <ErrorNotice
      v-if="appliedFailed"
      title="申请状态未加载"
      message="无法确认哪些岗位已申请，申请入口已暂停，请重试。"
      retry-label="重新加载"
      @retry="loadAppliedIds"
    />

    <ErrorNotice
      v-if="loadFailed"
      title="岗位列表未加载"
      message="请确认网络后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadData"
    />

    <section v-else class="table-panel" aria-label="岗位列表">
      <el-table :data="list" stripe v-loading="loading">
        <template #empty><AppEmpty description="暂无招聘中的岗位" /></template>
        <el-table-column prop="jobName" label="岗位名称" min-width="140">
          <template #default="{ row }"><span class="row-title">{{ row.jobName }}</span></template>
        </el-table-column>
        <el-table-column prop="jobDesc" label="岗位描述" show-overflow-tooltip min-width="200" />
        <el-table-column prop="workLocation" label="工作地点" width="130" />
        <el-table-column prop="workHours" label="工作时间" width="170" />
        <el-table-column prop="hourlyRate" label="时薪" width="120">
          <template #default="{ row }">{{ row.hourlyRate }} 元/小时</template>
        </el-table-column>
        <el-table-column prop="maxRecruit" label="招聘人数" width="100" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              :loading="applyingId === row.id"
              :disabled="appliedFailed || appliedIds.includes(row.id)"
              @click="handleApply(row)"
            >
              {{ appliedIds.includes(row.id) ? '已申请' : '申请岗位' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { getRecords, getTotal, requireSuccess } from '@/utils/apiHelpers'
import { getWorkStudies, applyForPosition, getMyApplications } from '@/api/workStudy'

const userStore = useUserStore()

const list = ref([])
const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  list.value = []
  pagination.total = 0
})
const applyingId = ref(null)
const appliedIds = ref([])
const appliedFailed = ref(false)

const searchForm = reactive({
  jobName: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const loadAppliedIds = async () => {
  appliedFailed.value = false
  try {
    const res = requireSuccess(await getMyApplications({
      pageNum: 1,
      pageSize: 999,
      studentId: Number(userStore.userId)
    }))
    appliedIds.value = getRecords(res.data).map(r => r.workStudyId)
  } catch (err) {
    appliedFailed.value = true
  }
}

const loadData = () => guarded(async (isCurrent) => {
  const params = {
    jobName: searchForm.jobName || undefined,
    status: 1, // 只显示招聘中的岗位
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }
  const res = requireSuccess(await getWorkStudies(params))
  if (!isCurrent()) return
  list.value = getRecords(res.data)
  pagination.total = getTotal(res.data)
})

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadData()
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
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
