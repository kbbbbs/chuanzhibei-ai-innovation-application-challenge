<template>
  <div class="course-form workspace-page workspace-page--narrow" :class="{ 'course-form--embedded': creationStep, 'course-form--dialog': dialogMode }">
    <PageHeader v-if="!creationStep && !dialogMode"
      eyebrow="课程与班级"
      :title="isEdit ? '编辑信息' : '新建课程'"
      :description="isEdit ? '修改课程名称、分类、简介和封面等基本信息。' : '先保存课程信息，再上传资料并准备课程内容。'"
    />

    <section class="course-form-panel" data-testid="course-form-panel">
      <el-steps v-if="!isEdit && !dialogMode" :active="0" align-center class="course-steps">
        <el-step title="基本信息" /><el-step title="课程资料" /><el-step title="课程内容" />
      </el-steps>
      <ErrorNotice
        v-if="loadFailed"
        title="课程详情未加载"
        message="为避免用空白内容覆盖原课程，编辑和保存已暂停。"
        retry-label="重新加载"
        :busy="loading"
        @retry="retryCourse"
      />
      <ErrorNotice
        v-if="categoriesFailed"
        title="课程分类未加载"
        :message="isEdit ? '可以保留现有分类继续保存；需要更换分类时请重试。' : '分类是必填项，加载成功后才能提交。'"
        retry-label="重新加载"
        :busy="categoriesLoading"
        @retry="loadCategories"
      />

      <section v-loading="loading" class="course-fields" aria-label="课程信息">
        <div v-if="!isEdit || creationStep" class="creation-heading">
          <h2>先认识这门课程</h2>
          <p>保存基本信息后，将自动建立课程专属资料库。</p>
        </div>
        <el-form :model="form" :rules="rules" :disabled="formLocked || submitLoading" ref="formRef" label-position="top" @submit.prevent>
          <el-form-item label="课程名称" prop="title">
            <el-input v-model="form.title" placeholder="请输入课程名称" />
          </el-form-item>

          <div class="field-pair">
            <el-form-item label="课程分类" prop="categoryId">
              <el-select v-model="form.categoryId" :loading="categoriesLoading" placeholder="请选择分类">
                <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
              </el-select>
              <p v-if="categoriesEmpty" class="field-hint">暂无课程分类，请先在分类管理中创建。</p>
            </el-form-item>

            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="form.difficulty" placeholder="请选择难度">
                <el-option label="入门" value="beginner" />
                <el-option label="基础" value="intermediate" />
                <el-option label="进阶" value="advanced" />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item label="课程简介" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="4" placeholder="请输入课程简介" />
          </el-form-item>

          <el-form-item label="课程封面">
            <el-input v-model="form.coverUrl" placeholder="请输入封面URL" />
          </el-form-item>

        </el-form>

      </section>
      <div class="form-actions">
        <el-button :loading="submitLoading" :disabled="submitLoading" @click="handleExit">{{ dialogMode ? '取消' : '退出' }}</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="!canSubmit" @click="handleSubmit(false)">{{ isEdit && !creationStep ? '保存基本信息' : '下一步' }}</el-button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { getCourseById, addCourse, updateCourse, getCategories } from '@/utils/course'
import { currentUserId, requireSuccess } from '@/utils/apiHelpers'
import { readAssistantCoursePrefill, saveAssistantCoursePrefill, clearAssistantCoursePrefill } from '@/utils/assistantPageAction'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref(null)
const props = defineProps({ courseId: Number, creationStep: Boolean, dialogMode: Boolean })
const emit = defineEmits(['saved', 'cancel'])
const returnPath = computed(() => {
  const destination = String(route.query.returnTo || '')
  return /^\/courses(?:\/drafts)?(?:\?.*)?$/.test(destination) ? destination : '/courses'
})

const editingId = computed(() => {
  const id = Number(props.courseId || route.query.id)
  return Number.isFinite(id) && id > 0 ? id : null
})
const isEdit = computed(() => editingId.value !== null)
const { loading, loadFailed, guarded, reset: resetCourseLoad } = useGuardedLoad()
const {
  loading: categoriesLoading,
  loadFailed: categoriesFailed,
  guarded: guardedCategories,
} = useGuardedLoad()
// The course whose saved data is in the form. An update sends every field, so saving
// anything else would overwrite that course with the blank defaults.
const loadedCourseId = ref(null)
let savedBasicFields = ''
const formLocked = computed(() => isEdit.value && loadedCourseId.value !== editingId.value)
const canSubmit = computed(() => (isEdit.value ? !formLocked.value : !categoriesFailed.value))
const submitLoading = ref(false)
const categories = ref([])
let pendingAssistantCategory = null
let activeAssistantDraft = null
const categoriesEmpty = computed(() => !categoriesLoading.value && !categoriesFailed.value && !categories.value.length)
const creationKey = ref(crypto.randomUUID())

const defaultForm = () => ({
  title: '',
  categoryId: null,
  teacherId: currentUserId(userStore),
  difficulty: 'beginner',
  description: '',
  coverUrl: '',
  status: 2,
  publishNotice: false
})

const form = reactive({
  ...defaultForm()
})

function basicFields() {
  return {
    title: form.title, categoryId: form.categoryId, teacherId: form.teacherId,
    difficulty: form.difficulty, description: form.description, coverUrl: form.coverUrl,
  }
}

const rules = {
  title: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
}

const loadCategories = () => guardedCategories(async (isCurrent) => {
  const res = requireSuccess(await getCategories())
  if (isCurrent()) {
    categories.value = Array.isArray(res.data) ? res.data : []
    applyAssistantCategory()
  }
})

function applyAssistantCategory() {
  const pending = pendingAssistantCategory
  if (!pending || isEdit.value) return
  pendingAssistantCategory = null
  if (form.categoryId !== pending.previousValue) return
  const matches = categories.value.filter(category => pending.id
    ? Number(category.id) === pending.id
    : category.name === pending.name)
  form.categoryId = matches.length === 1 ? matches[0].id : null
  if (matches.length !== 1) ElMessage.warning('课程分类未匹配，请手动选择。')
}

function applyAssistantPrefill() {
  if (isEdit.value || props.dialogMode || !userStore.isTeacher) return
  const fields = readAssistantCoursePrefill(userStore.userId, route.query.assistantDraft)
  if (!fields) return
  activeAssistantDraft = route.query.assistantDraft
  const hasDraft = Boolean(form.title || form.description || form.categoryId)
  for (const key of ['title', 'description', 'coverUrl', 'difficulty']) {
    if (typeof fields[key] === 'string') form[key] = fields[key]
  }
  if (fields.categoryId || fields.categoryName) {
    form.categoryId = fields.categoryId || null
    pendingAssistantCategory = { id: fields.categoryId, name: fields.categoryName, previousValue: form.categoryId }
    if (categories.value.length) applyAssistantCategory()
  }
  if (hasDraft) ElMessage.info('已填入助手提供的信息，当前页面未提及的内容已保留，请核对后继续。')
}

function rememberAssistantPrefill() {
  if (isEdit.value || activeAssistantDraft !== route.query.assistantDraft) return
  if (!activeAssistantDraft) return
  const { title, categoryId, difficulty, description, coverUrl } = form
  const fields = { title, categoryId, difficulty, description, coverUrl }
  if (pendingAssistantCategory && categoryId === pendingAssistantCategory.previousValue) fields.categoryName = pendingAssistantCategory.name
  saveAssistantCoursePrefill(userStore.userId, activeAssistantDraft, fields)
}

function discardAssistantPrefill() {
  clearAssistantCoursePrefill(userStore.userId, activeAssistantDraft)
  activeAssistantDraft = null
}

const loadCourse = (id) => guarded(async (isCurrent) => {
  const res = requireSuccess(await getCourseById(id))
  if (!isCurrent()) return
  const course = res.data
  if (!course || typeof course !== 'object' || Array.isArray(course) || String(course.id ?? id) !== String(id)) {
    throw new Error('课程详情无效')
  }
  Object.assign(form, course)
  savedBasicFields = JSON.stringify(basicFields())
  loadedCourseId.value = id
})

const retryCourse = () => loadCourse(editingId.value)

const handleSubmit = async (exitAfterSave = false, notifyParent = true) => {
  if (submitLoading.value) return false
  submitLoading.value = true
  try {
    try {
      await formRef.value.validate()
    } catch {
      return false
    }

    // Checked after validation, which awaits, so a route change cannot redirect this save.
    if (!canSubmit.value) return false
    const courseId = editingId.value
    const fields = basicFields()
    const data = courseId ? fields : { ...fields, status: 2, publishNotice: false, creationKey: creationKey.value }
    const res = requireSuccess(await (courseId ? updateCourse(courseId, data) : addCourse(data)))

    if (courseId) {
      savedBasicFields = JSON.stringify(fields)
      ElMessage.success('基本信息已保存')
      if (!notifyParent) return true
      if (props.dialogMode) emit('saved', { courseId })
      else if (props.creationStep) emit('saved', { exit: exitAfterSave })
      else router.push(returnPath.value)
    } else {
      const id = Number(res.data?.id || res.data?.courseId)
      if (!id) throw new Error('课程保存结果缺少课程编号，请重试')
      discardAssistantPrefill()
      ElMessage.success('课程基本信息已保存')
      router.replace(exitAfterSave ? '/courses/drafts' : { path: `/courses/${id}`, query: { setup: 'materials' } })
    }
    return true
  } catch (e) {
    ElMessage.error(e.message || '课程保存失败，填写内容已保留，可重试')
    return false
  } finally {
    submitLoading.value = false
  }
}

function saveForNavigation() {
  if (formLocked.value || submitLoading.value) return false
  if (JSON.stringify(basicFields()) === savedBasicFields) return true
  return handleSubmit(false, false)
}

defineExpose({ saveForNavigation })

const handleExit = async () => {
  if (props.dialogMode) return emit('cancel')
  if (isEdit.value && !props.creationStep) return router.push(returnPath.value)
  if (!isEdit.value) {
    discardAssistantPrefill()
    return router.push('/courses/drafts')
  }
  await handleSubmit(true)
}

onMounted(() => {
  loadCategories()
})

watch(
  editingId,
  (id) => {
    resetCourseLoad()
    loadedCourseId.value = null
    formRef.value?.clearValidate?.()
    Object.assign(form, defaultForm())
    if (id) loadCourse(id)
    else creationKey.value = crypto.randomUUID()
  },
  { immediate: true }
)

watch(() => [route.query.assistantDraft, editingId.value], applyAssistantPrefill, { immediate: true })
watch(form, rememberAssistantPrefill, { deep: true })

</script>

<style scoped>
.course-form {
  width: min(100%, 1120px);
  padding-bottom: 96px;
}

.course-form-panel {
  min-width: 0;
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-xl);
  overflow: hidden;
}

.course-steps {
  width: min(100%, 780px);
  margin: var(--space-8) auto var(--space-4);
  padding-inline: var(--space-5);
}

.course-steps :deep(.el-step__line) {
  background: var(--line-subtle);
}

.course-steps :deep(.el-step__head.is-process .el-step__icon) {
  color: var(--text-on-accent);
  background: var(--accent);
  border-color: var(--accent);
}

.course-steps :deep(.el-step__title.is-process) {
  color: var(--accent);
}

.course-fields {
  width: min(100%, 800px);
  margin-inline: auto;
  padding: var(--space-8) var(--space-10);
}

.creation-heading {
  margin-bottom: var(--space-8);
}

.creation-heading h2 {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 650;
}

.creation-heading p {
  margin: var(--space-2) 0 0;
  color: var(--text-subtle);
  font-size: var(--text-sm);
  line-height: 1.7;
}

.creation-heading .field-hint {
  font-size: var(--text-xs);
}

.course-fields :deep(.el-form-item) {
  margin-bottom: var(--space-6);
}

.course-fields :deep(.el-input__wrapper),
.course-fields :deep(.el-select__wrapper) {
  min-height: 40px;
}

.course-form-panel > .error-notice {
  margin: var(--space-5);
}

.course-form--embedded,
.course-form--dialog {
  width: 100%;
  padding-bottom: 0;
  gap: 0;
}

.course-form--embedded .course-form-panel,
.course-form--dialog .course-form-panel {
  border: 0;
  border-radius: 0;
}

.course-form--embedded .course-fields {
  padding-top: 0;
}

.course-form--dialog .course-fields {
  width: 100%;
  padding: 0;
}
.field-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-6);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  padding: var(--space-5) var(--space-8);
  border-top: 1px solid var(--line-subtle);
}

.form-actions .el-button + .el-button {
  margin-left: 0;
}

.course-form--dialog .form-actions {
  padding: var(--space-5) 0 0;
}

@media (max-width: 560px) {
  .course-steps {
    padding-inline: var(--space-2);
    margin-top: var(--space-6);
  }

  .course-steps :deep(.el-step__title) {
    font-size: var(--text-xs);
  }

  .course-fields {
    padding: var(--space-6) var(--space-4);
  }

  .form-actions {
    padding: var(--space-4);
    flex-wrap: wrap;
  }

  .field-pair {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }
}
</style>
