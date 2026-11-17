<template>
  <el-dialog :model-value="modelValue" :title="administrative ? '调整教学班' : published ? '调整发布范围' : '发布课程'" width="min(720px, 95vw)" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @update:model-value="emit('update:modelValue', $event)">
    <p>{{ courseTitle }}</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div v-loading="loading" class="publication-classes">
      <el-table v-if="classes.length" :data="classes" row-key="id">
        <el-table-column label="选择" width="70">
          <template #default="{ row }"><el-checkbox :model-value="selectedIds.includes(row.id)" :aria-label="`选择${row.className}`" :disabled="loading || saving || stale" @change="selectClass(row.id, $event)" /></template>
        </el-table-column>
        <el-table-column prop="className" label="教学班" min-width="180" />
        <el-table-column prop="semester" label="学期" min-width="130" />
        <el-table-column prop="studentCount" label="学生人数" width="100" />
      </el-table>
      <el-empty v-else-if="!loading && !error" :description="administrative ? '本课程还没有教学班，请先在教学班管理中创建' : '本课程还没有教学班。教学班由管理员创建，请联系管理员为本课程开设教学班后再发布'" />
    </div>
    <p v-if="published && !administrative" class="field-hint">课程已发布。勾选的教学班可以学习本课程，取消勾选的教学班将不能再进入。</p>
    <p v-if="administrative" class="field-hint">开放范围随教学班成员变化。课程发布后，获准教学班的学生可以进入学习。</p>
    <template #footer>
      <el-button :disabled="saving" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button v-if="error" :loading="loading" :disabled="saving" @click="load(true)">重新加载</el-button>
      <el-button type="primary" :loading="saving" :disabled="loading || !loaded || stale || (!administrative && !selectedIds.length)" @click="save">{{ administrative ? '保存开放范围' : published ? '保存发布范围' : '确认发布' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getCoursePublication, publishCourse, updateCoursePublication } from '@/utils/course'
import { requireSuccess } from '@/utils/apiHelpers'

const props = defineProps({
  modelValue: Boolean,
  courseId: Number,
  courseTitle: { type: String, default: '' },
  administrative: Boolean,
  published: Boolean,
})
const emit = defineEmits(['update:modelValue', 'saved'])
const classes = ref([]), selectedIds = ref([]), workflowVersion = ref(null)
const loading = ref(false), saving = ref(false), loaded = ref(false), stale = ref(false), error = ref('')
let readRun = 0

watch(() => [props.modelValue, props.courseId], ([open]) => {
  readRun += 1
  if (!open) return
  classes.value = []
  selectedIds.value = []
  workflowVersion.value = null
  loaded.value = false
  stale.value = false
  error.value = ''
  load()
})

async function load(keepSelection = false) {
  const run = ++readRun
  const id = props.courseId
  loading.value = true
  error.value = ''
  try {
    const response = requireSuccess(await getCoursePublication(id))
    if (run !== readRun || !props.modelValue || id !== props.courseId) return
    classes.value = response.data.classes || []
    const candidates = new Set(classes.value.map(item => item.id))
    selectedIds.value = (keepSelection ? selectedIds.value : response.data.classIds || []).filter(classId => candidates.has(classId))
    workflowVersion.value = response.data.workflowVersion
    loaded.value = true
    stale.value = false
  } catch (problem) {
    if (run === readRun) error.value = problem.message || '教学班未加载，请重试'
  } finally {
    if (run === readRun) loading.value = false
  }
}

function selectClass(id, selected) {
  selectedIds.value = selected ? [...selectedIds.value, id] : selectedIds.value.filter(value => value !== id)
}

async function save() {
  if (saving.value || loading.value || stale.value || !loaded.value) return
  saving.value = true
  error.value = ''
  try {
    const operation = props.administrative ? updateCoursePublication : publishCourse
    const response = await operation(props.courseId, { classIds: [...selectedIds.value].sort((a, b) => a - b), workflowVersion: workflowVersion.value })
    if (response?.code === '409') stale.value = true
    requireSuccess(response)
    ElMessage.success(props.administrative ? '教学班开放范围已保存' : props.published ? '发布范围已保存' : '课程已发布')
    emit('update:modelValue', false)
    emit('saved')
  } catch (problem) {
    if (problem.response?.status === 409) stale.value = true
    error.value = problem.message || '保存失败，已保留选择，请重试'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.publication-classes { min-height: 100px; margin-top: 16px; }
</style>
