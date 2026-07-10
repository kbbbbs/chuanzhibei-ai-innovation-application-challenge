<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="820px"
    :close-on-click-modal="false"
    @closed="resetDialog"
  >
    <div class="import-dialog">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls,.csv"
        drag
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
      >
        <div class="upload-text">拖拽 Excel/CSV 文件到此处或点击上传</div>
        <template #tip>
          <div class="el-upload__tip">支持 .xlsx、.xls、.csv；多余列会忽略，缺失列按空值校验</div>
        </template>
      </el-upload>

      <el-alert
        v-if="previewData"
        :title="summaryText"
        :type="previewData.invalidRows > 0 ? 'warning' : 'success'"
        :closable="false"
        class="summary-alert"
      />

      <el-table
        v-if="previewData"
        :data="previewData.items"
        border
        stripe
        max-height="360"
      >
        <el-table-column prop="rowNumber" label="行号" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.valid ? 'success' : 'danger'">
              {{ row.valid ? '可导入' : '有错误' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="错误原因" min-width="220">
          <template #default="{ row }">
            {{ row.errors && row.errors.length ? row.errors.join('；') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="解析结果" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatNormalized(row.normalizedData) }}
          </template>
        </el-table-column>
      </el-table>

      <el-result
        v-if="confirmResult"
        :icon="confirmResult.fail > 0 ? 'warning' : 'success'"
        title="导入完成"
        :sub-title="`成功 ${confirmResult.success} 条，失败 ${confirmResult.fail} 条，共 ${confirmResult.total} 条`"
      />
    </div>

    <template #footer>
      <el-button :loading="templateDownloading" @click="handleTemplateDownload">下载模板</el-button>
      <el-button @click="visible = false">{{ confirmResult ? '关闭' : '取消' }}</el-button>
      <el-button type="primary" :disabled="!selectedFile" :loading="previewing" @click="handlePreview">
        解析预览
      </el-button>
      <el-button
        type="success"
        :disabled="!canConfirm"
        :loading="confirming"
        @click="handleConfirm"
      >
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmImport, downloadImportTemplate, previewImport } from '@/api/imports'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  type: {
    type: String,
    required: true
  },
  title: {
    type: String,
    default: '表格导入'
  }
})

const emit = defineEmits(['update:modelValue', 'imported'])

const uploadRef = ref(null)
const selectedFile = ref(null)
const previewData = ref(null)
const confirmResult = ref(null)
const previewing = ref(false)
const confirming = ref(false)
const templateDownloading = ref(false)

const TEMPLATE_FILE_NAMES = {
  questions: '题库导入模板.xlsx',
  papers: '试卷导入模板.xlsx',
  exams: '考试导入模板.xlsx',
  homeworks: '作业导入模板.xlsx'
}

const FIELD_LABELS = {
  id: 'ID',
  name: '名称',
  title: '标题',
  courseId: '课程ID',
  classId: '教学班ID',
  teacherId: '教师ID',
  paperId: '试卷ID',
  paperName: '试卷名称',
  type: '题型',
  content: '内容',
  questionType: '题型',
  difficulty: '难度',
  score: '分值',
  fullScore: '总分',
  knowledgePoint: '知识点',
  analysis: '解析',
  durationMinutes: '时长',
  status: '状态',
  startTime: '开始时间',
  endTime: '结束时间',
  deadline: '截止时间',
  questionId: '题目ID',
  questionNumber: '题号',
  sortOrder: '排序',
  existingQuestionId: '已有题目ID',
  attachmentUrl: '附件地址'
}

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const summaryText = computed(() => {
  if (!previewData.value) return ''
  const { totalRows, validRows, invalidRows } = previewData.value
  return `共 ${totalRows} 行，可导入 ${validRows} 行，错误 ${invalidRows} 行`
})

const canConfirm = computed(() => {
  return previewData.value &&
    previewData.value.totalRows > 0 &&
    previewData.value.invalidRows === 0 &&
    !confirmResult.value
})

const handleFileChange = (file) => {
  const fileName = file.name || file.raw?.name || ''
  if (!/\.(xlsx|xls|csv)$/i.test(fileName)) {
    ElMessage.error('请选择 .xlsx、.xls 或 .csv 格式的文件')
    uploadRef.value?.clearFiles()
    selectedFile.value = null
    return
  }
  selectedFile.value = file.raw
  previewData.value = null
  confirmResult.value = null
}

const handleFileRemove = () => {
  selectedFile.value = null
  previewData.value = null
  confirmResult.value = null
}

const handlePreview = async () => {
  if (!selectedFile.value) return
  previewing.value = true
  try {
    const res = await previewImport(props.type, selectedFile.value)
    if (res.code === '200') {
      previewData.value = res.data
      confirmResult.value = null
    } else {
      ElMessage.error(res.msg || '解析失败')
    }
  } catch (e) {
    ElMessage.error(errorMessage(e, '解析失败，请检查文件格式'))
  } finally {
    previewing.value = false
  }
}

const handleConfirm = async () => {
  if (!canConfirm.value) return
  confirming.value = true
  try {
    const res = await confirmImport(props.type, previewData.value.items)
    if (res.code === '200') {
      confirmResult.value = res.data
      ElMessage.success('导入完成')
      emit('imported', res.data)
    } else {
      ElMessage.error(res.msg || '导入失败')
    }
  } catch (e) {
    ElMessage.error(errorMessage(e, '导入失败，请重试'))
  } finally {
    confirming.value = false
  }
}

const handleTemplateDownload = async () => {
  templateDownloading.value = true
  try {
    const blob = await downloadImportTemplate(props.type)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = TEMPLATE_FILE_NAMES[props.type] || '导入模板.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(errorMessage(e, '模板下载失败，请稍后重试'))
  } finally {
    templateDownloading.value = false
  }
}

const formatNormalized = (data) => {
  if (!data) return '-'
  return Object.entries(data)
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .slice(0, 10)
    .map(([key, value]) => `${FIELD_LABELS[key] || key}: ${formatValue(value)}`)
    .join('；') || '-'
}

const formatValue = (value) => {
  if (Array.isArray(value)) {
    return `${value.length}项`
  }
  if (typeof value === 'object') {
    if (value.content) return value.content
    if (value.title) return value.title
    return '已解析'
  }
  return String(value)
}

const errorMessage = (error, fallback) => {
  const responseData = error?.response?.data
  if (responseData instanceof Blob) {
    return fallback
  }
  return responseData?.msg ||
    responseData?.message ||
    error?.message ||
    fallback
}

const resetDialog = () => {
  uploadRef.value?.clearFiles()
  selectedFile.value = null
  previewData.value = null
  confirmResult.value = null
}
</script>

<style scoped>
.import-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-text {
  color: #606266;
  line-height: 24px;
}

.summary-alert {
  margin-top: 4px;
}
</style>
