<template>
  <div class="user-manage workspace-page">
    <PageHeader eyebrow="系统配置" title="用户管理" description="查询平台账号，维护角色与启用状态。角色决定账号能进入的任务区。">
      <template #actions>
        <el-button type="primary" @click="handleCreate">新增用户</el-button>
      </template>
    </PageHeader>

    <section class="filter-panel" aria-label="筛选用户">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="请输入用户名、昵称、邮箱或电话" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.roleCode" placeholder="请选择角色" clearable>
            <el-option label="管理员" value="admin" />
            <el-option label="教师" value="teacher" />
            <el-option label="学生" value="student" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <ErrorNotice
      v-if="loadFailed"
      title="用户列表未加载"
      message="请确认网络后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadUsers"
    />

    <section v-else class="table-panel" aria-label="用户列表">
      <el-table :data="users" stripe v-loading="loading">
        <template #empty><AppEmpty /></template>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column prop="roleCode" label="角色" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.roleCode === 'admin'" type="danger">管理员</el-tag>
            <el-tag v-else-if="row.roleCode === 'teacher'" type="success">教师</el-tag>
            <el-tag v-else type="info">学生</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">正常</el-tag>
            <el-tag v-else type="danger">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="narrow ? ROW_SHEET_COLUMN_WIDTH : 200" fixed="right">
          <template #default="{ row }">
            <RowActions :label="row.nickname || row.username || `用户 ${row.id}`">
              <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" :loading="loading" @click="handleDelete(row)">删除</el-button>
            </RowActions>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadUsers"
        @size-change="handleSizeChange"
      />
    </section>

    <!-- Form Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!form.id">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="form.roleCode">
            <el-option label="管理员" value="admin" />
            <el-option label="教师" value="teacher" />
            <el-option label="学生" value="student" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">正常</el-radio>
            <el-radio :label="0">禁用</el-radio>
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
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import RowActions from '@/components/ui/RowActions.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { ROW_SHEET_COLUMN_WIDTH, useNarrowScreen } from '@/composables/useNarrowScreen'
import { getRecords, getTotal, requireSuccess } from '@/utils/apiHelpers'

const { confirmDelete } = useConfirm()

const formRef = ref(null)
const narrow = useNarrowScreen()

const users = ref([])
const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  users.value = []
  pagination.total = 0
})

const searchForm = reactive({
  keyword: '',
  roleCode: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)

const form = reactive({
  id: null,
  username: '',
  password: '',
  nickname: '',
  email: '',
  roleCode: 'student',
  status: 1
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const loadUsers = () => guarded(async (isCurrent) => {
  const params = {
    ...searchForm,
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }
  const res = requireSuccess(await request.get('/users', { params }))
  if (!isCurrent()) return
  users.value = getRecords(res.data)
  pagination.total = getTotal(res.data)
})

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadUsers()
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadUsers()
}

const handleCreate = () => {
  Object.assign(form, {
    id: null,
    username: '',
    password: '',
    nickname: '',
    email: '',
    roleCode: 'student',
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
      res = await request.put(`/users/${form.id}`, form)
    } else {
      res = await request.post('/users', form)
    }

    if (res.code === '200') {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadUsers()
    }
  } catch (err) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const handleDelete = async (row) => {
  const confirmed = await confirmDelete('确认删除此用户？')
  if (!confirmed) return

  loading.value = true
  try {
    const res = await request.delete(`/users/${row.id}`)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadUsers()
    }
  } catch (err) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.roleCode = ''
  pagination.pageNum = 1
  loadUsers()
}

onMounted(() => {
  loadUsers()
})
</script>
