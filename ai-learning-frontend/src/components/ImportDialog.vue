<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="920px"
    :close-on-click-modal="false"
    @closed="resetDialog"
  >
    <div class="import-dialog">
      <el-steps :active="currentStep" simple finish-status="success">
        <el-step title="下载模板" />
        <el-step :title="type === 'papers' ? '填写试卷与题目' : '填写题目'" />
        <el-step title="上传预览" />
        <el-step title="确认导入" />
      </el-steps>

      <el-alert :title="guide.title" type="info" :closable="false">
        <template #default>
          <ul class="guide-list">
            <li v-for="rule in guide.rules" :key="rule">{{ rule }}</li>
          </ul>
        </template>
      </el-alert>

      <el-form v-if="type === 'papers'" :model="paperForm" label-width="88px" class="paper-form">
        <el-form-item label="所属课程" required>
          <el-select v-model="paperForm.courseId" placeholder="请选择课程" clearable>
            <el-option v-for="course in courses" :key="course.id" :label="course.title" :value="course.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="试卷名称" required>
          <el-input v-model.trim="paperForm.name" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="考试时长" required>
          <el-input-number v-model="paperForm.durationMinutes" :min="1" :max="600" />
          <span class="field-suffix">分钟</span>
        </el-form-item>
        <el-form-item label="试卷状态" required>
          <el-radio-group v-model="paperForm.status">
            <el-radio value="DRAFT">草稿</el-radio>
            <el-radio value="PUBLISHED">已发布</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls,.csv"
        drag
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
      >
        <div class="upload-text">上传已填写的模板文件</div>
        <template #tip>
          <div class="el-upload__tip">Excel 文件必须包含“题目列表”工作表；CSV 需使用与模板相同的表头。</div>
        </template>
      </el-upload>

      <el-alert
        v-if="previewData"
        :title="summaryText"
        :type="previewData.invalidRows > 0 ? 'warning' : 'success'"
        :closable="false"
      />

      <el-table v-if="previewData" :data="previewData.items" border stripe max-height="360">
        <el-table-column prop="rowNumber" label="行号" width="72" />
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag :type="row.valid ? 'success' : 'danger'">{{ row.valid ? '可导入' : '需修正' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="100">
          <template #default="{ row }">{{ previewQuestion(row).type || '-' }}</template>
        </el-table-column>
        <el-table-column label="题干" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ previewQuestion(row).content || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="type === 'papers'" label="分值" width="90">
          <template #default="{ row }">{{ row.normalizedData?.score ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="错误原因" min-width="260">
          <template #default="{ row }">{{ row.errors?.length ? row.errors.join('；') : '-' }}</template>
        </el-table-column>
      </el-table>

      <el-result
        v-if="confirmResult"
        :icon="confirmResult.fail > 0 ? 'warning' : 'success'"
        title="导入完成"
        :sub-title="`成功 ${confirmResult.success} 项，失败 ${confirmResult.fail} 项`"
      />
    </div>

    <template #footer>
      <el-button :loading="templateDownloading" @click="handleTemplateDownload">下载模板</el-button>
      <el-button @click="visible = false">{{ confirmResult ? '关闭' : '取消' }}</el-button>
      <el-button type="primary" :disabled="!canPreview" :loading="previewing" @click="handlePreview">解析预览</el-button>
      <el-button type="success" :disabled="!canConfirm" :loading="confirming" @click="handleConfirm">确认导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmImport, downloadImportTemplate, previewImport } from '@/api/imports'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  type: { type: String, required: true },
  title: { type: String, default: '表格导入' },
  courses: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'imported'])

const uploadRef = ref(null)
const selectedFile = ref(null)
const previewData = ref(null)
const confirmResult = ref(null)
const previewing = ref(false)
const confirming = ref(false)
const templateDownloading = ref(false)
const paperForm = reactive({
  courseId: null,
  name: '',
  durationMinutes: 60,
  status: 'DRAFT'
})

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const isPaperMetadataComplete = computed(() => props.type !== 'papers' || (
  Boolean(paperForm.courseId) && Boolean(paperForm.name.trim()) && Number(paperForm.durationMinutes) > 0 && Boolean(paperForm.status)
))

const canPreview = computed(() => Boolean(selectedFile.value) && isPaperMetadataComplete.value)
const canConfirm = computed(() => previewData.value?.totalRows > 0 && previewData.value.invalidRows === 0 && !confirmResult.value)
const currentStep = computed(() => confirmResult.value ? 4 : previewData.value ? 3 : selectedFile.value ? 2 : 0)
const summaryText = computed(() => `共 ${previewData.value.totalRows} 行，可导入 ${previewData.value.validRows} 行，错误 ${previewData.value.invalidRows} 行`)
const guide = computed(() => props.type === 'papers'
  ? {
      title: '一个 Excel 文件只创建一张试卷',
      rules: [
        '先填写试卷课程、名称、时长和状态，再上传题目模板。',
        '题目列表每行是一道新题，题号和分值必填。',
        '单选填一个答案键，多选填至少两个答案键，判断填“正确”或“错误”。',
        '预览中存在任何错误时，必须修正后才能确认导入。'
      ]
    }
  : {
      title: '按模板逐行导入题库',
      rules: [
        '每行是一道题；课程ID、题型、题干和正确答案必填。',
        '单选填一个答案键，多选填至少两个答案键，判断填“正确”或“错误”。',
        '选择题至少填写两个选项；非选择题不填写选项。',
        '预览中存在任何错误时，必须修正后才能确认导入。'
      ]
    })

const handleFileChange = file => {
  const fileName = file.name || file.raw?.name || ''
  if (!/\.(xlsx|xls|csv)$/i.test(fileName)) {
    ElMessage.error('请选择 .xlsx、.xls 或 .csv 格式的文件')
    uploadRef.value?.clearFiles()
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
  if (!canPreview.value) return
  previewing.value = true
  try {
    const res = await previewImport(props.type, selectedFile.value, props.type === 'papers' ? { courseId: paperForm.courseId } : {})
    if (res.code === '200') {
      previewData.value = res.data
      confirmResult.value = null
    } else {
      ElMessage.error(res.msg || '解析失败')
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '解析失败，请检查文件格式和填写内容'))
  } finally {
    previewing.value = false
  }
}

const handleConfirm = async () => {
  if (!canConfirm.value) return
  confirming.value = true
  try {
    const payload = props.type === 'papers'
      ? { ...paperForm, items: previewData.value.items }
      : { items: previewData.value.items }
    const res = await confirmImport(props.type, payload)
    if (res.code === '200') {
      confirmResult.value = res.data
      if (res.data?.fail > 0) {
        ElMessage.error(res.data.items?.[0]?.error || '导入失败，未创建试卷')
      } else {
        ElMessage.success('导入完成')
        emit('imported', res.data)
      }
    } else {
      ElMessage.error(res.msg || '导入失败')
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '导入失败，请重试'))
  } finally {
    confirming.value = false
  }
}

const handleTemplateDownload = async () => {
  templateDownloading.value = true
  try {
    const blob = await downloadImportTemplate(props.type)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = props.type === 'papers' ? '试卷导入模板.xlsx' : '题库导入模板.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(errorMessage(error, '模板下载失败，请稍后重试'))
  } finally {
    templateDownloading.value = false
  }
}

const previewQuestion = row => row.normalizedData?.question || row.normalizedData || {}
const errorMessage = (error, fallback) => error?.response?.data?.msg || error?.response?.data?.message || error?.message || fallback

const resetDialog = () => {
  uploadRef.value?.clearFiles()
  selectedFile.value = null
  previewData.value = null
  confirmResult.value = null
  Object.assign(paperForm, { courseId: null, name: '', durationMinutes: 60, status: 'DRAFT' })
}
</script>

<style scoped>
.import-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.guide-list {
  margin: 6px 0 0;
  padding-left: 18px;
  line-height: 1.8;
}

.paper-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
}

.paper-form :deep(.el-form-item:nth-child(2)) {
  grid-column: span 2;
}

.field-suffix {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

.upload-text {
  color: var(--el-text-color-regular);
  line-height: 24px;
}

@media (max-width: 720px) {
  .paper-form {
    grid-template-columns: 1fr;
  }

  .paper-form :deep(.el-form-item:nth-child(2)) {
    grid-column: auto;
  }
}
</style>
