<template>
  <div class="workspace-page workspace-page--narrow">
    <PageHeader eyebrow="课程创建" title="继续准备课程内容" description="选择尚在创建中的课程，继续准备教学内容。" />
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">重新加载</el-button></el-alert>
    <section class="content-panel" v-loading="loading">
      <el-form label-position="top">
        <el-form-item label="所属课程"><el-select v-model="selectedCourseId" filterable placeholder="选择课程"><el-option v-for="course in courses" :key="course.id" :label="course.title" :value="Number(course.id)" /></el-select></el-form-item>
      </el-form>
      <div class="entry-actions"><el-button @click="router.push('/courses/create')">新建课程</el-button><el-button type="primary" :loading="binding" :disabled="!selectedCourseId" @click="enter">{{ projectId ? '关联草稿并继续' : '继续创建' }}</el-button></div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/ui/PageHeader.vue'
import request from '@/utils/request'
import { getRecords, requireSuccess } from '@/utils/apiHelpers'
const route = useRoute(), router = useRouter()
const projectId = computed(() => route.query.projectId || route.query.draftId || route.query.id)
const courses = ref([]), selectedCourseId = ref(null), loading = ref(false), binding = ref(false), error = ref('')
async function destination(courseId, standalonePublication = false) {
  const course = requireSuccess(await request.get(`/courses/${courseId}`)).data
  if (course.setupStage === 'COMPLETED') {
    const query = standalonePublication ? { tab: 'content' } : { tab: 'preparation', projectId: projectId.value }
    return { path: `/courses/${courseId}`, query }
  }
  const workflow = requireSuccess(await request.get(`/courses/${courseId}/setup`)).data
  requireSuccess(await request.put(`/courses/${courseId}/setup`, {
    setupStage: 'CONTENT', contentMode: 'AI', preparationId: projectId.value ? Number(projectId.value) : workflow.preparationId,
    workflowVersion: workflow.workflowVersion
  }))
  return { path: `/courses/${courseId}`, query: { setup: 'content' } }
}
async function load() {
  loading.value = true; error.value = ''
  try {
    if (projectId.value) {
      const project = requireSuccess(await request.get(`/api/preparations/${projectId.value}`)).data
      const targetCourseId = project.config?.targetCourseId || project.targetCourseId
      const courseId = targetCourseId || project.publishedCourseId
      if (courseId) {
        await router.replace(await destination(courseId, !targetCourseId))
        return
      }
    }
    courses.value = getRecords(requireSuccess(await request.get('/courses', { params: { box: 'drafts', pageSize: 1000 } })).data)
    const requestedCourse = Number(route.query.courseId)
    if (requestedCourse && courses.value.some(course => Number(course.id) === requestedCourse)) {
      selectedCourseId.value = requestedCourse
      if (!projectId.value) await router.replace(await destination(requestedCourse))
    }
  } catch (problem) { error.value = problem.message || '课程或备课草稿未读取，可重试' }
  finally { loading.value = false }
}
async function enter() {
  binding.value = true; error.value = ''
  try {
    if (projectId.value) requireSuccess(await request.post(`/api/preparations/${projectId.value}/bind-course`, { courseId: selectedCourseId.value }))
    await router.replace(await destination(selectedCourseId.value))
  } catch (problem) { error.value = problem.message || '草稿关联失败，可重试' }
  finally { binding.value = false }
}
onMounted(load)
</script>

<style scoped>
.entry-actions { display: flex; justify-content: flex-end; gap: 12px; }
.el-select { width: 100%; }
</style>
