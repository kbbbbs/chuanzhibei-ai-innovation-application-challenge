<template>
  <div class="work-study-student-manage">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>劳务费名单管理</span>
          <div>
            <el-button type="success" @click="handleImport">导入名单</el-button>
            <el-button type="warning" :loading="loading" @click="handleExport('xlsx')">导出Excel</el-button>
            <el-button :loading="loading" @click="handleExport('csv')">导出CSV</el-button>
            <el-button type="primary" @click="handleCreate">新增记录</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm">
        <el-form-item label="岗位">
          <el-select v-model="searchForm.workStudyId" placeholder="请选择岗位" clearable>
            <el-option
              v-for="item in workStudyOptions"
              :key="item.id"
              :label="item.jobName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学生编号">
          <el-input v-model="searchForm.studentId" placeholder="请输入学生编号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="学生姓名、用户名或岗位" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="工作月份">
          <el-input v-model="searchForm.workMonth" placeholder="例如：2026-07" clearable @keyup.enter="handleSearch" />
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
        <el-table-column prop="studentId" label="学生编号" width="100" />
        <el-table-column prop="studentName" label="学生姓名" />
        <el-table-column prop="workMonth" label="工作月份" width="120" />
        <el-table-column prop="workHours" label="工作时长(小时)" width="130" />
        <el-table-column prop="hourlyRate" label="时薪(元)" width="100" />
        <el-table-column prop="totalAmount" label="劳务费(元)" width="120">
          <template #default="{ row }">
            <el-tag type="success">{{ row.totalAmount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">正常</el-tag>
            <el-tag v-else type="info">停用</el-tag>
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

    <!-- Form Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑记录' : '新增记录'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="岗位" prop="workStudyId">
          <el-select v-model="form.workStudyId" placeholder="请选择岗位" @change="onWorkStudyChange">
            <el-option
              v-for="item in workStudyOptions"
              :key="item.id"
              :label="item.jobName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学生编号" prop="studentId">
          <el-input v-model.number="form.studentId" />
        </el-form-item>
        <el-form-item label="工作月份" prop="workMonth">
          <el-input v-model="form.workMonth" placeholder="例如：2026-07" />
        </el-form-item>
        <el-form-item label="工作时长" prop="workHours">
          <el-input-number v-model="form.workHours" :min="0" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="适用时薪" prop="hourlyRate">
          <el-input-number v-model="form.hourlyRate" :min="0" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">正常</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- Import Dialog -->
    <el-dialog v-model="importDialogVisible" title="导入劳务费名单" width="500px">
      <el-form label-width="120px">
        <el-form-item label="选择岗位" prop="importWorkStudyId">
          <el-select v-model="importWorkStudyId" placeholder="请选择岗位">
            <el-option
              v-for="item in workStudyOptions"
              :key="item.id"
              :label="item.jobName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="表格文件">
          <el-upload
            ref="uploadRef"
            action=""
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            accept=".xlsx,.xls,.csv"
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">请上传 xlsx/xls/csv 格式文件；多余列会忽略，缺失列按空值校验</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleImportSubmit">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import { getRecords, getTotal } from '@/utils/apiHelpers'
import {
  getWorkStudyStudents,
  createWorkStudyStudent,
  updateWorkStudyStudent,
  deleteWorkStudyStudent,
  importWorkStudyStudents,
  exportWorkStudyStudents,
  getWorkStudies
} from '@/api/workStudy'

const { confirmDelete } = useConfirm()

const formRef = ref(null)
const uploadRef = ref(null)

const list = ref([])
const loading = ref(false)
const workStudyOptions = ref([])

const searchForm = reactive({
  workStudyId: null,
  studentId: null,
  keyword: '',
  workMonth: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)
const importDialogVisible = ref(false)
const importWorkStudyId = ref(null)
const importFile = ref(null)

const form = reactive({
  id: null,
  workStudyId: null,
  studentId: null,
  workMonth: '',
  workHours: 0,
  hourlyRate: 0,
  status: 1
})

const rules = {
  workStudyId: [{ required: true, message: '请选择岗位', trigger: 'change' }],
  studentId: [{ required: true, message: '请输入学生编号', trigger: 'blur' }],
  workMonth: [{ required: true, message: '请输入工作月份', trigger: 'blur' }],
  workHours: [{ required: true, message: '请输入工作时长', trigger: 'change' }],
  hourlyRate: [{ required: true, message: '请输入适用时薪', trigger: 'change' }]
}

const loadWorkStudyOptions = async () => {
  try {
    const res = await getWorkStudies({ pageNum: 1, pageSize: 999 })
    if (res.code === '200') {
      workStudyOptions.value = getRecords(res.data)
    }
  } catch (err) {
    // 全局已提示
  }
}

const loadData = async () => {
  const params = {
    ...searchForm,
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  }
  loading.value = true
  try {
    const res = await getWorkStudyStudents(params)
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

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const onWorkStudyChange = (workStudyId) => {
  const selected = workStudyOptions.value.find(item => item.id === workStudyId)
  if (selected && selected.hourlyRate) {
    form.hourlyRate = selected.hourlyRate
  }
}

const handleCreate = () => {
  Object.assign(form, {
    id: null,
    workStudyId: null,
    studentId: null,
    workMonth: '',
    workHours: 0,
    hourlyRate: 0,
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
      res = await updateWorkStudyStudent(form.id, form)
    } else {
      res = await createWorkStudyStudent(form)
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
  const confirmed = await confirmDelete('确认删除此记录？')
  if (!confirmed) return

  loading.value = true
  try {
    const res = await deleteWorkStudyStudent(row.id)
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
  searchForm.workStudyId = null
  searchForm.studentId = null
  searchForm.keyword = ''
  searchForm.workMonth = ''
  pagination.pageNum = 1
  loadData()
}

const handleImport = () => {
  importWorkStudyId.value = null
  importFile.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
  importDialogVisible.value = true
}

const handleFileChange = (file) => {
  const fileName = file.name || file.raw?.name || ''
  if (!/\.(xlsx|xls|csv)$/i.test(fileName)) {
    ElMessage.error('请选择 xlsx、xls 或 csv 格式文件')
    uploadRef.value?.clearFiles()
    importFile.value = null
    return
  }
  importFile.value = file.raw
}

const handleImportSubmit = async () => {
  if (!importWorkStudyId.value) {
    ElMessage.warning('请选择岗位')
    return
  }
  if (!importFile.value) {
    ElMessage.warning('请选择文件')
    return
  }

  loading.value = true
  try {
    const res = await importWorkStudyStudents(importWorkStudyId.value, importFile.value)
    if (res.code === '200') {
      ElMessage.success('导入成功')
      importDialogVisible.value = false
      loadData()
    }
  } catch (err) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const handleExport = async (format) => {
  loading.value = true
  try {
    const res = await exportWorkStudyStudents({
      workStudyId: searchForm.workStudyId,
      studentId: searchForm.studentId,
      workMonth: searchForm.workMonth,
      keyword: searchForm.keyword,
      format
    })
    const contentType = format === 'csv' ? 'text/csv;charset=utf-8' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    const blob = new Blob([res], { type: contentType })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = `劳务费名单_${new Date().toISOString().slice(0, 10)}.${format}`
    link.click()
    URL.revokeObjectURL(link.href)
    ElMessage.success('导出成功')
  } catch (err) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadWorkStudyOptions()
  loadData()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  justify-content: center;
}
</style>
