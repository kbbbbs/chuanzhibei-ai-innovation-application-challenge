<template>
  <div class="operation-log-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <h2>{{ pageTitle }}</h2>
            <p>{{ pageSubtitle }}</p>
          </div>
          <el-button :icon="Refresh" :loading="loading" @click="loadLogs">刷新</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm" class="filter-form">
        <el-form-item :label="userStore.isAdmin ? '用户ID' : '学生ID'">
          <el-input
            v-model="searchForm.userId"
            class="short-input"
            placeholder="按ID筛选"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="操作">
          <el-input
            v-model="searchForm.operation"
            class="medium-input"
            placeholder="usage.request"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="方法">
          <el-select v-model="searchForm.method" class="short-input" placeholder="全部" clearable>
            <el-option v-for="method in methodOptions" :key="method" :label="method" :value="method" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-input
            v-model="searchForm.status"
            class="short-input"
            placeholder="200"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="地址">
          <el-input
            v-model="searchForm.url"
            class="url-input"
            placeholder="/courses"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            class="time-range"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="logs" border stripe v-loading="loading">
        <template #empty><AppEmpty /></template>
        <el-table-column prop="createTime" label="时间" min-width="170">
          <template #default="{ row }">{{ formatDisplayTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="用户" min-width="150">
          <template #default="{ row }">
            <div class="user-cell">
              <span>{{ row.nickname || row.username || '未登录用户' }}</span>
              <small v-if="row.userId">ID {{ row.userId }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="operation" label="操作" min-width="150" show-overflow-tooltip />
        <el-table-column prop="method" label="方法" width="92">
          <template #default="{ row }">
            <el-tag size="small" :type="methodTag(row.method)">{{ row.method || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="url" label="接口" min-width="190" show-overflow-tooltip />
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.summary.status)">
              {{ row.summary.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">
            <span :class="durationClass(row.summary.durationMs)">{{ formatDuration(row.summary.durationMs) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" min-width="130" show-overflow-tooltip />
        <el-table-column label="摘要" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.summary.summary || `${row.method || ''} ${row.url || ''}` }}</template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[5, 10, 20, 50]"
        @current-change="loadLogs"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-drawer v-model="detailVisible" title="日志详情" size="520px">
      <template v-if="currentLog">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="时间">{{ formatDisplayTime(currentLog.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ currentLog.nickname || currentLog.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ currentLog.userId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="操作">{{ currentLog.operation || '-' }}</el-descriptions-item>
          <el-descriptions-item label="方法">{{ currentLog.method || '-' }}</el-descriptions-item>
          <el-descriptions-item label="接口">{{ currentLog.url || '-' }}</el-descriptions-item>
          <el-descriptions-item label="IP">{{ currentLog.ip || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <h3>请求摘要</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="Query">{{ currentLog.summary.query || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ currentLog.summary.status || '-' }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ formatDuration(currentLog.summary.durationMs) }}</el-descriptions-item>
            <el-descriptions-item label="Content-Type">{{ currentLog.summary.contentType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Content-Length">{{ currentLog.summary.contentLength ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="User-Agent">{{ currentLog.summary.userAgent || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h3>Params JSON</h3>
          <pre>{{ formatParams(currentLog.params) }}</pre>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getOperationLogs } from '@/api/operationLog'
import AppEmpty from '@/components/AppEmpty.vue'
import { formatLocalDateTime, getRecords, getTotal } from '@/utils/apiHelpers'

const userStore = useUserStore()

const methodOptions = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']

const logs = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const currentLog = ref(null)
const timeRange = ref([])

const searchForm = reactive({
  userId: '',
  operation: '',
  method: '',
  status: '',
  url: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const pageTitle = computed(() => userStore.isTeacher ? '学生操作日志' : '操作日志')
const pageSubtitle = computed(() => userStore.isTeacher
  ? '仅展示你所管理课程和教学班下学生的访问记录'
  : '查看全站接口访问记录、状态和耗时')

const parseParams = (value) => {
  if (!value) return {}
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch {
    return { raw: String(value) }
  }
}

const normalizeLog = (log) => ({
  ...log,
  summary: parseParams(log.params)
})

const cleanParams = () => {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }
  const userId = Number(searchForm.userId)
  const status = Number(searchForm.status)

  if (Number.isFinite(userId) && userId > 0) params.userId = userId
  if (searchForm.operation.trim()) params.operation = searchForm.operation.trim()
  if (searchForm.method) params.method = searchForm.method
  if (Number.isFinite(status) && status > 0) params.status = status
  if (searchForm.url.trim()) params.url = searchForm.url.trim()
  if (Array.isArray(timeRange.value) && timeRange.value.length === 2) {
    params.startTime = timeRange.value[0]
    params.endTime = timeRange.value[1]
  }
  return params
}

const loadLogs = async () => {
  loading.value = true
  try {
    const res = await getOperationLogs(cleanParams())
    if (res.code === '200') {
      logs.value = getRecords(res.data).map(normalizeLog)
      pagination.total = getTotal(res.data)
    }
  } catch (e) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadLogs()
}

const handleReset = () => {
  Object.assign(searchForm, {
    userId: '',
    operation: '',
    method: '',
    status: '',
    url: ''
  })
  timeRange.value = []
  pagination.pageNum = 1
  loadLogs()
}

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadLogs()
}

const openDetail = (row) => {
  currentLog.value = row
  detailVisible.value = true
}

const formatDisplayTime = (value) => {
  const formatted = formatLocalDateTime(value)
  return formatted ? formatted.replace('T', ' ') : '-'
}

const formatDuration = (value) => {
  const duration = Number(value)
  if (!Number.isFinite(duration)) return '-'
  return `${duration} ms`
}

const durationClass = (value) => {
  const duration = Number(value)
  if (!Number.isFinite(duration)) return ''
  if (duration >= 1000) return 'duration-slow'
  if (duration >= 300) return 'duration-warn'
  return 'duration-ok'
}

const statusTag = (status) => {
  const code = Number(status)
  if (!Number.isFinite(code)) return 'info'
  if (code >= 500) return 'danger'
  if (code >= 400) return 'warning'
  if (code >= 300) return 'info'
  return 'success'
}

const methodTag = (method) => {
  const map = {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    PATCH: 'warning',
    DELETE: 'danger'
  }
  return map[method] || 'info'
}

const formatParams = (value) => {
  const parsed = parseParams(value)
  return JSON.stringify(parsed, null, 2)
}

onMounted(() => {
  loadLogs()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.card-header h2 {
  margin: 0;
  font-size: 20px;
  line-height: 1.3;
  color: var(--text-primary);
}

.card-header p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.filter-form {
  margin-bottom: 14px;
}

.short-input {
  width: 120px;
}

.medium-input {
  width: 180px;
}

.url-input {
  width: 220px;
}

.time-range {
  width: 360px;
}

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.user-cell small {
  color: var(--text-secondary);
  font-size: 12px;
}

.duration-ok {
  color: #15803d;
}

.duration-warn {
  color: #b45309;
}

.duration-slow {
  color: #b91c1c;
  font-weight: 600;
}

.pagination {
  margin-top: 20px;
  justify-content: center;
}

.detail-section {
  margin-top: 20px;
}

.detail-section h3 {
  margin: 0 0 10px;
  font-size: 15px;
  color: var(--text-primary);
}

pre {
  margin: 0;
  padding: 12px;
  min-height: 120px;
  overflow: auto;
  border-radius: var(--radius-sm);
  background: #111827;
  color: #e5e7eb;
  font-size: 12px;
  line-height: 1.55;
}

@media (max-width: 900px) {
  .card-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .short-input,
  .medium-input,
  .url-input,
  .time-range {
    width: 100%;
  }
}
</style>
