<template>
  <div class="work-study-manage">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>勤工俭学岗位管理</span>
          <div class="header-actions">
            <el-badge :value="pendingPagination.total" :hidden="pendingPagination.total === 0">
              <el-button type="warning" @click="openAuditDrawer">申请审核</el-button>
            </el-badge>
            <el-button type="primary" @click="handleCreate">新增岗位</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm">
        <el-form-item label="岗位名称">
          <el-input v-model="searchForm.jobName" placeholder="请输入岗位名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="招聘中" :value="1" />
            <el-option label="已停止" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" border stripe v-loading="loading">
        <template #empty><AppEmpty /></template>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="jobName" label="岗位名称" />
        <el-table-column prop="jobDesc" label="岗位描述" show-overflow-tooltip />
        <el-table-column prop="workLocation" label="工作地点" />
        <el-table-column prop="workHours" label="工作时间" />
        <el-table-column prop="hourlyRate" label="时薪标准(元)" width="120">
          <template #default="{ row }">
            {{ row.hourlyRate }}
          </template>
        </el-table-column>
        <el-table-column prop="maxRecruit" label="招聘人数" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">招聘中</el-tag>
            <el-tag v-else type="info">已停止</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" :loading="loading" @click="handleDelete(row)">删除</el-button>
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

    <el-drawer v-model="auditDrawerVisible" title="勤工俭学申请审核" size="760px">
      <el-table :data="pendingApplications" border stripe v-loading="pendingLoading">
        <template #empty><AppEmpty description="暂无待审核申请" /></template>
        <el-table-column prop="jobName" label="申请岗位" min-width="170" />
        <el-table-column label="申请学生" min-width="150">
          <template #default="{ row }">
            <div>{{ row.studentName || `学生 ${row.studentId}` }}</div>
            <small class="muted-text">ID：{{ row.studentId }}</small>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="180">
          <template #default="{ row }">
            {{ row.applyTime ? new Date(row.applyTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <el-tag type="warning">待审核</el-tag>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="success"
              :loading="auditSubmittingId === row.id && auditSubmittingStatus === 1"
              :disabled="auditSubmittingId === row.id && auditSubmittingStatus === 2"
              @click="handleAudit(row, 1)"
            >通过</el-button>
            <el-button
              size="small"
              type="danger"
              :loading="auditSubmittingId === row.id && auditSubmittingStatus === 2"
              :disabled="auditSubmittingId === row.id && auditSubmittingStatus === 1"
              @click="handleAudit(row, 2)"
            >拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        v-model:current-page="pendingPagination.pageNum"
        v-model:page-size="pendingPagination.pageSize"
        :total="pendingPagination.total"
        :page-sizes="[5, 10, 20]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadPendingApplications"
        @size-change="handlePendingSizeChange"
      />
    </el-drawer>

    <!-- Form Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑岗位' : '新增岗位'" width="550px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="岗位名称" prop="jobName">
          <el-input v-model="form.jobName" />
        </el-form-item>
        <el-form-item label="岗位描述">
          <el-input v-model="form.jobDesc" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="工作地点">
          <el-input v-model="form.workLocation" />
        </el-form-item>
        <el-form-item label="工作时间">
          <el-input v-model="form.workHours" placeholder="例如：周一至周五 14:00-17:00" />
        </el-form-item>
        <el-form-item label="时薪标准" prop="hourlyRate">
          <el-input-number v-model="form.hourlyRate" :min="0" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="招聘人数" prop="maxRecruit">
          <el-input-number v-model="form.maxRecruit" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">招聘中</el-radio>
            <el-radio :label="0">已停止</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useConfirm } from '@/composables/useConfirm'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import { currentUserId, getRecords, getTotal } from '@/utils/apiHelpers'
import {
  getWorkStudies,
  createWorkStudy,
  updateWorkStudy,
  deleteWorkStudy,
  getPendingApplications,
  auditApplication
} from '@/api/workStudy'

const { confirmDelete } = useConfirm()
const userStore = useUserStore()

const formRef = ref(null)

const list = ref([])
const loading = ref(false)
const auditDrawerVisible = ref(false)
const pendingApplications = ref([])
const pendingLoading = ref(false)
const auditSubmittingId = ref(null)
const auditSubmittingStatus = ref(null)

const searchForm = reactive({
  jobName: '',
  status: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const pendingPagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)

const form = reactive({
  id: null,
  jobName: '',
  jobDesc: '',
  workLocation: '',
  workHours: '',
  hourlyRate: 0,
  maxRecruit: 1,
  status: 1
})

const rules = {
  jobName: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  hourlyRate: [{ required: true, message: '请设置时薪标准', trigger: 'change' }],
  maxRecruit: [{ required: true, message: '请设置招聘人数', trigger: 'change' }]
}

const loadData = async () => {
  const params = {
    ...searchForm,
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

const loadPendingApplications = async () => {
  pendingLoading.value = true
  try {
    const res = await getPendingApplications({
      pageNum: pendingPagination.pageNum,
      pageSize: pendingPagination.pageSize
    })
    if (res.code === '200') {
      pendingApplications.value = getRecords(res.data)
      pendingPagination.total = getTotal(res.data)
    }
  } catch (err) {
    pendingApplications.value = []
    pendingPagination.total = 0
  } finally {
    pendingLoading.value = false
  }
}

const openAuditDrawer = () => {
  pendingPagination.pageNum = 1
  auditDrawerVisible.value = true
  loadPendingApplications()
}

const handlePendingSizeChange = () => {
  pendingPagination.pageNum = 1
  loadPendingApplications()
}

const handleAudit = async (row, auditStatus) => {
  let rejectReason = ''
  if (auditStatus === 1) {
    try {
      await ElMessageBox.confirm(
        `确认通过 ${row.studentName || `学生 ${row.studentId}`} 的“${row.jobName || '勤工俭学岗位'}”申请？`,
        '申请审核',
        { type: 'success', confirmButtonText: '确认通过', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  } else {
    try {
      const result = await ElMessageBox.prompt(
        `请输入拒绝 ${row.studentName || `学生 ${row.studentId}`} 本次申请的原因`,
        '拒绝申请',
        {
          type: 'warning',
          confirmButtonText: '确认拒绝',
          cancelButtonText: '取消',
          inputPlaceholder: '请输入拒绝原因',
          inputValidator: value => Boolean(value && value.trim()) || '拒绝原因不能为空'
        }
      )
      rejectReason = result.value.trim()
    } catch {
      return
    }
  }

  const auditorId = currentUserId(userStore)
  if (!auditorId) {
    ElMessage.error('当前审核人信息无效，请重新登录')
    return
  }

  auditSubmittingId.value = row.id
  auditSubmittingStatus.value = auditStatus
  try {
    const res = await auditApplication({
      applicationId: row.id,
      auditStatus,
      auditorId,
      rejectReason: auditStatus === 2 ? rejectReason : null
    })
    if (res.code === '200') {
      ElMessage.success(auditStatus === 1 ? '申请已通过' : '申请已拒绝')
      if (pendingApplications.value.length === 1 && pendingPagination.pageNum > 1) {
        pendingPagination.pageNum -= 1
      }
      await loadPendingApplications()
    }
  } catch (err) {
    // 全局已提示
  } finally {
    auditSubmittingId.value = null
    auditSubmittingStatus.value = null
  }
}

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadData()
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleCreate = () => {
  Object.assign(form, {
    id: null,
    jobName: '',
    jobDesc: '',
    workLocation: '',
    workHours: '',
    hourlyRate: 0,
    maxRecruit: 1,
    status: 1
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value.validate()

  loading.value = true
  try {
    let res
    if (form.id) {
      res = await updateWorkStudy(form.id, form)
    } else {
      res = await createWorkStudy(form)
    }

    if (res.code === '200') {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadData()
    }
  } catch (err) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const handleDelete = async (row) => {
  const confirmed = await confirmDelete('确认删除此岗位？')
  if (!confirmed) return

  loading.value = true
  try {
    const res = await deleteWorkStudy(row.id)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadData()
    }
  } catch (err) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchForm.jobName = ''
  searchForm.status = null
  pagination.pageNum = 1
  loadData()
}

onMounted(() => {
  loadData()
  loadPendingApplications()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.muted-text {
  color: #909399;
}

.pagination {
  margin-top: 20px;
  justify-content: center;
}
</style>
