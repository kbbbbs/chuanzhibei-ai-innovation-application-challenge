<template>
  <div class="homework-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>作业管理</span>
          <div class="header-actions">
            <el-button type="warning" @click="importDialogVisible = true">导入</el-button>
            <el-button :loading="exporting" @click="handleExport('xlsx')">导出Excel</el-button>
            <el-button :loading="exporting" @click="handleExport('csv')">导出CSV</el-button>
            <el-button type="primary" @click="handleCreate">发布作业</el-button>
          </div>
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
        <el-table-column label="附件" width="90">
          <template #default="{ row }">
            <el-link v-if="row.attachmentUrl" :href="row.attachmentUrl" target="_blank" type="primary">查看</el-link>
            <span v-else>-</span>
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
        <el-form-item label="作业附件">
          <div class="attachment-uploader">
            <el-upload
              ref="homeworkUploadRef"
              action="#"
              :auto-upload="false"
              :limit="1"
              :disabled="attachmentUploading"
              :on-change="handleAttachmentChange"
              :on-remove="handleAttachmentRemove"
              :on-exceed="handleAttachmentExceed"
            >
              <el-button :loading="attachmentUploading">
                <el-icon><Upload /></el-icon>
                选择文件
              </el-button>
              <template #tip>
                <div class="el-upload__tip">可上传题目说明、模板、数据集或参考材料，单个文件不超过 100MB</div>
              </template>
            </el-upload>
            <el-link v-if="form.attachmentUrl" :href="form.attachmentUrl" target="_blank" type="success">
              {{ attachmentName || '查看已上传附件' }}
            </el-link>
          </div>
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

    <ImportDialog
      v-model="importDialogVisible"
      type="homeworks"
      title="作业导入"
      @imported="loadHomework"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import ImportDialog from '@/components/ImportDialog.vue'
import { exportImportData } from '@/api/imports'
import { uploadHomeworkAttachment } from '@/api/homework'
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
const exporting = ref(false)
const attachmentUploading = ref(false)
const homeworkUploadRef = ref(null)
const attachmentName = ref('')
const homeworkList = ref([])
const classList = ref([])

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)
const importDialogVisible = ref(false)

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

const handleExport = async (format) => {
  exporting.value = true
  try {
    const blob = await exportImportData('homeworks', scopedTeacherParams(userStore, { format }))
    downloadBlob(blob, `作业_${new Date().toISOString().slice(0, 10)}.${format}`)
    ElMessage.success('导出成功')
  } catch (e) {
    // 全局已提示
  } finally {
    exporting.value = false
  }
}

const downloadBlob = (blob, fileName) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
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
  attachmentName.value = ''
  homeworkUploadRef.value?.clearFiles()
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  Object.assign(form, row)
  if (form.deadline) {
    form.deadline = new Date(form.deadline)
  }
  attachmentName.value = fileNameFromUrl(form.attachmentUrl)
  homeworkUploadRef.value?.clearFiles()
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value.validate()
  if (attachmentUploading.value) {
    ElMessage.warning('附件仍在上传，请稍候')
    return
  }

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

const handleAttachmentChange = async (file) => {
  if (!file?.raw) return
  attachmentUploading.value = true
  try {
    const res = await uploadHomeworkAttachment(file.raw)
    if (res.code === '200' && res.data?.url) {
      form.attachmentUrl = res.data.url
      attachmentName.value = res.data.originalName || file.name || '已上传附件'
      ElMessage.success('附件上传成功')
    } else {
      handleAttachmentRemove()
      ElMessage.error(res.msg || '附件上传失败')
    }
  } catch (e) {
    handleAttachmentRemove()
  } finally {
    attachmentUploading.value = false
  }
}

const handleAttachmentRemove = () => {
  form.attachmentUrl = ''
  attachmentName.value = ''
}

const handleAttachmentExceed = () => {
  ElMessage.warning('每次只能上传一个附件')
}

const fileNameFromUrl = (url) => {
  if (!url) return ''
  const parts = String(url).split('/')
  return parts[parts.length - 1] || ''
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

.header-actions {
  display: flex;
  gap: 10px;
}

.pagination-bar {
  margin-top: 20px;
  justify-content: center;
}

.attachment-uploader {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
</style>
