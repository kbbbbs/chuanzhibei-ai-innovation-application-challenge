<template>
  <section class="course-setup" data-testid="course-creation-panel" v-loading="loading">
    <el-steps :active="stepIndex" align-center data-testid="course-creation-steps">
      <el-step
        v-for="step in steps"
        :key="step.stage"
        :title="step.title"
        :aria-label="step.title"
        :aria-current="workflow?.setupStage === step.stage ? 'step' : undefined"
        :aria-disabled="!workflow || busy"
        role="button"
        tabindex="0"
        @click="setStep(step.stage)"
        @keydown.enter.prevent="setStep(step.stage)"
        @keydown.space.prevent="setStep(step.stage)"
      />
    </el-steps>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <el-button link @click="load">重新加载进度</el-button>
    </el-alert>
    <template v-if="workflow">
      <CourseForm v-if="workflow.setupStage === 'BASIC'" ref="basicFormRef" :course-id="courseId" creation-step @saved="basicSaved" />
      <template v-else-if="workflow.setupStage === 'MATERIALS'">
        <div class="setup-stage setup-stage--materials">
          <header class="stage-heading">
            <h2>准备课程资料</h2>
            <p>教材、课件与参考资料将共同用于课程问答、检索和智能备课。</p>
          </header>
          <TeacherKnowledgeBase :key="courseId" :course-id="courseId" workspace-tab="documents" embedded />
        </div>
        <div class="setup-actions">
          <el-button :disabled="busy" @click="setStep('BASIC')">返回基本信息</el-button>
          <el-button :disabled="busy" @click="exitDraft">保存草稿并退出</el-button>
          <el-button type="primary" :loading="saving" :disabled="busy" @click="setStep('CONTENT')">下一步，准备课程内容</el-button>
        </div>
      </template>
      <template v-else>
        <div class="setup-stage setup-stage--content">
          <IntelligentResourceGen
            v-if="contentMode === 'AI'"
            ref="preparationRef"
            :course-id="courseId"
            :initial-project-id="workflow.preparationId"
            creation-mode
            embedded
            @project-change="projectChanged"
            @materials="setStep('MATERIALS')"
          >
            <template v-if="!aiApplied" #secondary-actions>
              <el-button link class="skip-preparation" :disabled="busy" @click="switchToManual(true)">跳过备课</el-button>
            </template>
          </IntelligentResourceGen>
          <el-result v-else-if="contentMode === 'IMPORT'" icon="success" title="课件内容已组织完成" sub-title="完成创建后，可在课程详情中维护章节与课时。" />
          <p v-else class="manual-note">完成创建后，可在课程详情中手动添加章节和课时。</p>
        </div>
        <div class="setup-actions">
          <el-button v-if="contentMode === 'AI' && !aiApplied" :disabled="busy" @click="switchToManual(false)">取消生成</el-button>
          <el-button v-if="canComplete" type="primary" :loading="saving" :disabled="busy" @click="finishSetup">完成创建</el-button>
        </div>
      </template>
    </template>

  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CourseForm from '@/views/teacher/CourseForm.vue'
import TeacherKnowledgeBase from '@/views/teacher/TeacherKnowledgeBase.vue'
import IntelligentResourceGen from '@/views/teacher/IntelligentResourceGen.vue'
import request from '@/utils/request'
import { requireSuccess } from '@/utils/apiHelpers'

const props = defineProps({ courseId: { type: Number, required: true } })
const emit = defineEmits(['updated'])
const router = useRouter()
const workflow = ref(null), loading = ref(false), saving = ref(false), error = ref('')
const basicFormRef = ref(null), navigating = ref(false), skippedPreparation = ref(false)
const preparationRef = ref(null), aiApplied = ref(false), activeProjectId = ref(null)
const steps = [
  { stage: 'BASIC', title: '基本信息' },
  { stage: 'MATERIALS', title: '课程资料' },
  { stage: 'CONTENT', title: '课程内容' },
]
const stepIndex = computed(() => steps.findIndex(step => step.stage === workflow.value?.setupStage))
const busy = computed(() => loading.value || saving.value || navigating.value)
// A content step without a recorded mode shows AI preparation; the mode is written when the teacher acts.
const contentMode = computed(() => workflow.value?.contentMode || 'AI')
const canComplete = computed(() => ['MANUAL', 'IMPORT'].includes(contentMode.value) || (contentMode.value === 'AI' && aiApplied.value))
let projectSave = Promise.resolve(true)

async function load() {
  loading.value = true; error.value = ''
  try {
    // Opening the page only reads; it does not change the course.
    workflow.value = requireSuccess(await request.get(`/courses/${props.courseId}/setup`)).data
    if (workflow.value.setupStage === 'COMPLETED') return router.replace('/courses')
    if (activeProjectId.value) projectSave = persistProject()
    return true
  } catch (problem) { error.value = problem.message || '创建进度未加载，请重试'; return false }
  finally { loading.value = false }
}
const isConflict = problem => problem?.response?.status === 409 || String(problem?.response?.data?.code || problem?.code) === '409'
async function saveWorkflow(changes, retried = false) {
  saving.value = true; error.value = ''
  try {
    const mode = workflow.value.setupStage === 'CONTENT' || changes.setupStage === 'CONTENT' ? contentMode.value : workflow.value.contentMode
    const data = { setupStage: workflow.value.setupStage, contentMode: mode, preparationId: workflow.value.preparationId, ...changes, workflowVersion: workflow.value.workflowVersion }
    workflow.value = requireSuccess(await request.put(`/courses/${props.courseId}/setup`, data, { silent: true })).data
    return true
  } catch (problem) {
    // Saving basic information elsewhere only moves the version on; reload once and apply the same step.
    if (!retried && isConflict(problem) && await load()) return saveWorkflow(changes, true)
    error.value = problem.message || '创建进度未保存，请重试'; return false
  }
  finally { saving.value = false }
}
async function flushPreparation() {
  if (!await projectSave) return false
  if (preparationRef.value && !await preparationRef.value.flushDraft()) {
    ElMessage.warning('当前备课修改尚未保存，请完成保存后重试')
    return false
  }
  // Flushing configuration can create a preparation and queue its course association.
  return await projectSave
}
async function navigate(action) {
  if (!workflow.value || busy.value) return
  navigating.value = true
  try { await action() }
  finally { navigating.value = false }
}
async function setStep(stage, basicAlreadySaved = false) {
  if (workflow.value?.setupStage === stage) return
  await navigate(async () => {
    const leavingBasic = workflow.value.setupStage === 'BASIC'
    if (leavingBasic && !basicAlreadySaved) {
      if (!await basicFormRef.value?.saveForNavigation() || !await load()) return
    }
    if (!await flushPreparation()) return
    const changes = { setupStage: stage }
    if (stage === 'CONTENT' && !workflow.value.contentMode) changes.contentMode = 'AI'
    if (!await saveWorkflow(changes)) return
    await router.replace({ path: `/courses/${props.courseId}`, query: { setup: stage.toLowerCase() } })
    if (leavingBasic) emit('updated')
  })
}
async function basicSaved({ exit }) {
  if (!await load()) return
  if (exit) return exitDraft()
  await setStep('MATERIALS', true)
}
async function switchToManual(openEditor) {
  await navigate(async () => {
    if (!await flushPreparation() || aiApplied.value || !await saveWorkflow({ contentMode: 'MANUAL' })) return
    if (openEditor) {
      skippedPreparation.value = true
      await completeCourse(true)
    }
  })
}
function projectChanged(state) {
  aiApplied.value = state.applied
  activeProjectId.value = state.id
  projectSave = projectSave.then(persistProject)
}
async function persistProject() {
  if (Number(workflow.value.preparationId) === Number(activeProjectId.value)) return true
  return saveWorkflow({ preparationId: activeProjectId.value })
}
async function exitDraft() {
  if (!await flushPreparation()) return
  await router.push('/courses/drafts')
}
async function finishSetup() {
  await navigate(async () => {
    if (canComplete.value && await flushPreparation()) await completeCourse(skippedPreparation.value)
  })
}
async function completeCourse(openEditor, retried = false) {
  if (!workflow.value.contentMode && !await saveWorkflow({ contentMode: contentMode.value })) return
  saving.value = true; error.value = ''
  try {
    try {
      requireSuccess(await request.post(`/courses/${props.courseId}/complete`, { workflowVersion: workflow.value.workflowVersion }, { silent: true }))
    } catch (problem) {
      if (!retried && isConflict(problem) && await load()) { saving.value = false; return completeCourse(openEditor, true) }
      throw problem
    }
    ElMessage.success('课程创建完成')
    if (openEditor) {
      await router.replace({ path: `/courses/${props.courseId}`, query: { tab: 'content' } })
      emit('updated')
    } else {
      await router.replace('/courses')
    }
  } catch (problem) { error.value = problem.message || '完成创建失败，请重试' }
  finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.course-setup {
  min-width: 0;
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-xl);
}

.course-setup > .el-steps {
  width: min(100%, 780px);
  margin: var(--space-8) auto;
  padding-inline: var(--space-5);
}

.course-setup :deep(.el-step__line) {
  background: var(--line-subtle);
}

.course-setup :deep(.el-step__head.is-process .el-step__icon) {
  color: var(--text-on-accent);
  background: var(--accent);
  border-color: var(--accent);
}

.course-setup :deep(.el-step__title.is-process) {
  color: var(--accent);
}

.course-setup > .el-alert {
  width: auto;
  margin: 0 var(--space-6) var(--space-6);
}

.setup-stage {
  min-width: 0;
  padding: var(--space-4) var(--space-8) var(--space-8);
}

.stage-heading {
  margin-bottom: var(--space-6);
}

.stage-heading h2 {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 650;
}

.stage-heading p {
  margin: var(--space-2) 0 0;
  color: var(--text-subtle);
  font-size: var(--text-sm);
  line-height: 1.7;
}

.course-setup :deep(.el-step[role='button'][aria-disabled='false']) { cursor: pointer; }
.course-setup :deep(.el-step[role='button']:focus-visible) { outline: 2px solid var(--line-accent); outline-offset: 4px; }
.setup-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  flex-wrap: wrap;
  padding: var(--space-5) var(--space-8);
  border-top: 1px solid var(--line-subtle);
}

.setup-actions .el-button + .el-button {
  margin-left: 0;
}

.skip-preparation { min-height: 32px; padding-inline: var(--space-2); }
.manual-note { color: var(--text-body); margin: 28px 0; }

@media (max-width: 640px) {
  .course-setup > .el-steps {
    margin-block: var(--space-6);
    padding-inline: var(--space-2);
  }

  .course-setup :deep(.el-step__title) {
    font-size: var(--text-xs);
  }

  .setup-stage {
    padding: var(--space-2) var(--space-4) var(--space-6);
  }

  .setup-actions {
    padding: var(--space-4);
    gap: var(--space-2);
  }
}
</style>
