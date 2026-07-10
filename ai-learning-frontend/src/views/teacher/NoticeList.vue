<template>
  <div class="notice-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>通知管理</span>
          <el-button v-if="canEdit" type="primary" @click="handleCreate">发布通知</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="filter" class="filter-bar" @submit.prevent>
        <el-form-item label="通知标题">
          <el-input v-model="filter.title" placeholder="请输入通知标题" clearable @keyup.enter="resetAndLoad" />
        </el-form-item>
        <el-form-item label="通知类型">
          <el-select v-model="filter.noticeType" placeholder="通知类型" clearable>
            <el-option label="全部" value="" />
            <el-option label="通用" value="general" />
            <el-option label="考试" value="exam" />
            <el-option label="作业" value="homework" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="resetAndLoad">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="noticeList" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="通知标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="noticeType" label="类型" width="110">
          <template #default="{ row }">
            {{ row.noticeType || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="教学班" width="120">
          <template #default="{ row }">
            {{ row.classId ? row.classId : '全站' }}
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="180">
          <template #default="{ row }">
            {{ formatLocalDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">已发布</el-tag>
            <el-tag v-else type="info">已撤回</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="canEdit ? 260 : 120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleView(row)">查看</el-button>
            <el-button v-if="canEdit" size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="canEdit" size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadNotices"
        @size-change="handleSizeChange"
        class="pagination-bar"
      />
    </el-card>

    <el-drawer v-model="detailVisible" title="通知详情" size="480px">
      <div v-loading="detailLoading" class="notice-detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.noticeType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="教学班">{{ detail.classId ? detail.classId : '全站' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="detail.status === 1" type="success">已发布</el-tag>
            <el-tag v-else type="info">已撤回</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ formatLocalDateTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="正文">
            <div class="notice-content">{{ detail.content }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑通知' : '发布通知'" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="通知标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="通知类型">
          <el-input v-model="form.noticeType" placeholder="例如 general、exam、homework（可选）" />
        </el-form-item>
        <el-form-item label="教学班">
          <el-select v-model="form.classId" placeholder="不选表示全站通知" clearable>
            <el-option v-for="cls in classList" :key="cls.id" :label="cls.className" :value="cls.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">已发布</el-radio>
            <el-radio :label="0">已撤回</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import { getNotices, getNoticeById, createNotice, updateNotice, deleteNotice } from '@/api/notice'
import { getTeachingClasses } from '@/api/teachingClass'
import { formatLocalDateTime, getRecords, getTotal, scopedTeacherParams } from '@/utils/apiHelpers'

const userStore = useUserStore()
const { confirmDelete } = useConfirm()
const formRef = ref(null)

const loading = ref(false)
const submitLoading = ref(false)
const noticeList = ref([])
const classList = ref([])

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const filter = reactive({
  title: '',
  noticeType: ''
})

const dialogVisible = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = reactive({})

const canEdit = computed(() => {
  const role = userStore.role
  return role === 'teacher' || role === 'admin'
})

const form = reactive({
  id: null,
  classId: null,
  title: '',
  content: '',
  noticeType: '',
  status: 1
})

const rules = {
  title: [{ required: true, message: '请输入通知标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入通知正文', trigger: 'blur' }]
}

const resetAndLoad = () => {
  pagination.pageNum = 1
  loadNotices()
}

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadNotices()
}

const loadNotices = async () => {
  loading.value = true
  try {
    const params = scopedTeacherParams(userStore, {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    if (filter.noticeType) {
      params.noticeType = filter.noticeType
    }
    if (filter.title && filter.title.trim()) {
      params.title = filter.title.trim()
    }
    const res = await getNotices(params)
    if (res.code === '200') {
      noticeList.value = getRecords(res.data)
      pagination.total = getTotal(res.data)
    }
  } catch (e) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  filter.title = ''
  filter.noticeType = ''
  pagination.pageNum = 1
  loadNotices()
}

const loadClasses = async () => {
  try {
    const res = await getTeachingClasses(scopedTeacherParams(userStore, { pageNum: 1, pageSize: 100 }))
    if (res.code === '200') {
      classList.value = getRecords(res.data)
    }
  } catch (e) {
    // 忽略
  }
}

const handleView = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getNoticeById(row.id)
    if (res.code === '200') {
      Object.assign(detail, res.data)
    }
  } catch (e) {
    // 全局已提示
  } finally {
    detailLoading.value = false
  }
}

const resetForm = () => {
  form.id = null
  form.classId = null
  form.title = ''
  form.content = ''
  form.noticeType = ''
  form.status = 1
}

const handleCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  resetForm()
  Object.assign(form, {
    id: row.id,
    classId: row.classId,
    title: row.title,
    content: row.content,
    noticeType: row.noticeType || '',
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const payload = {
        title: form.title,
        content: form.content,
        noticeType: form.noticeType || null,
        classId: form.classId || null,
        status: form.status
      }
      const res = form.id ? await updateNotice(form.id, payload) : await createNotice(payload)
      if (res.code === '200') {
        ElMessage.success(form.id ? '通知已修改' : '通知已发布')
        dialogVisible.value = false
        resetAndLoad()
      }
    } catch (e) {
      // 全局已提示
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDelete = async (row) => {
  await confirmDelete(`确定删除通知「${row.title}」吗？`, async () => {
    try {
      const res = await deleteNotice(row.id)
      if (res.code === '200') {
        ElMessage.success('通知已删除')
        resetAndLoad()
      }
    } catch (e) {
      // 全局已提示
    }
  })
}

onMounted(async () => {
  if (canEdit.value) {
    await loadClasses()
  }
  await loadNotices()
})
</script>

<style scoped>
.notice-list {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.filter-bar {
  margin-bottom: 16px;
}
.pagination-bar {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
.notice-detail .notice-content {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
