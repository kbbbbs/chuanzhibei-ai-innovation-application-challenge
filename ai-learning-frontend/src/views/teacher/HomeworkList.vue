<template>
  <div class="homework-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>作业管理</span>
          <el-button type="primary" @click="handleCreate">发布作业</el-button>
        </div>
      </template>

      <el-table :data="homeworkList" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="作业标题" />
        <el-table-column prop="classId" label="教学班ID" width="120" />
        <el-table-column label="截止时间" width="180">
          <template #default="{ row }">
            {{ row.deadline ? new Date(row.deadline).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">进行中</el-tag>
            <el-tag v-else type="info">已结束</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleSubmissions(row)">提交列表</el-button>
            <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
        layout="total, prev, pager, next"
        @current-change="loadHomework"
        class="pagination-bar"
      />
    </el-card>

    <!-- Form Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑作业' : '发布作业'" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="教学班" prop="classId">
          <el-select v-model="form.classId" placeholder="请选择教学班">
            <el-option v-for="cls in classList" :key="cls.id" :label="cls.className" :value="cls.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="作业标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="作业内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="附件地址">
          <el-input v-model="form.attachmentUrl" placeholder="作业附件URL（可选）" />
        </el-form-item>
        <el-form-item label="截止时间" prop="deadline">
          <el-date-picker v-model="form.deadline" type="datetime" placeholder="选择截止时间" />
        </el-form-item>
        <el-form-item label="总分" prop="fullScore">
          <el-input-number v-model="form.fullScore" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">进行中</el-radio>
            <el-radio :label="0">已结束</el-radio>
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import request from '@/utils/request'
import { getTeachingClasses } from '@/api/teachingClass'
import { currentUserId, formatLocalDateTime, getRecords, getTotal, scopedTeacherParams } from '@/utils/apiHelpers'

const router = useRouter()
const userStore = useUserStore()
const { confirmDelete } = useConfirm()
const formRef = ref(null)

const loading = ref(false)
const submitLoading = ref(false)
const deleteLoading = ref(false)
const homeworkList = ref([])
const classList = ref([])

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)

const form = reactive({
  id: null,
  classId: null,
  teacherId: currentUserId(userStore),
  title: '',
  content: '',
  attachmentUrl: '',
  deadline: null,
  fullScore: 100,
  status: 1
})

const rules = {
  classId: [{ required: true, message: '请选择教学班', trigger: 'change' }],
  title: [{ required: true, message: '请输入作业标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入作业内容', trigger: 'blur' }],
  deadline: [{ required: true, message: '请选择截止时间', trigger: 'change' }],
  fullScore: [{ required: true, message: '请输入总分', trigger: 'blur' }]
}

const loadHomework = async () => {
  loading.value = true
  try {
    const params = scopedTeacherParams(userStore, {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    const res = await request.get('/homeworks', { params })
    if (res.code === '200') {
      homeworkList.value = getRecords(res.data)
      pagination.total = getTotal(res.data)
    }
  } catch (e) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const loadClasses = async () => {
  try {
    const res = await getTeachingClasses(scopedTeacherParams(userStore, { pageSize: 100 }))
    if (res.code === '200') {
      classList.value = getRecords(res.data)
    }
  } catch (e) {
    // 全局已提示
  }
}

const handleCreate = () => {
  if (!classList.value || classList.value.length === 0) {
    ElMessage.warning('暂无教学班，请先创建教学班后再发布作业')
    return
  }
  Object.assign(form, {
    id: null,
    classId: null,
    teacherId: currentUserId(userStore),
    title: '',
    content: '',
    attachmentUrl: '',
    deadline: null,
    fullScore: 100,
    status: 1
  })
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  Object.assign(form, row)
  if (form.deadline) {
    form.deadline = new Date(form.deadline)
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value.validate()

  const data = { ...form }
  const selectedClass = classList.value.find(item => item.id === form.classId)
  data.teacherId = Number(selectedClass?.teacherId || data.teacherId || currentUserId(userStore))
  if (data.deadline) {
    data.deadline = formatLocalDateTime(data.deadline)
  }

  submitLoading.value = true
  try {
    let res
    if (form.id) {
      res = await request.put(`/homeworks/${form.id}`, data)
    } else {
      res = await request.post('/homeworks', data)
    }

    if (res.code === '200') {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadHomework()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    const confirmed = await confirmDelete('确认删除此作业？')
    if (!confirmed) return
    deleteLoading.value = true
    const res = await request.delete(`/homeworks/${row.id}`)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadHomework()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    deleteLoading.value = false
  }
}

const handleSubmissions = (row) => {
  router.push(`/homework/${row.id}/submissions`)
}

onMounted(() => {
  loadClasses()
  loadHomework()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-bar {
  margin-top: 20px;
  justify-content: center;
}
</style>
