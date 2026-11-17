<template>
  <div class="workspace-page">
    <PageHeader eyebrow="课程资料" title="资料归属整理" description="给已有资料库指定所属课程，后续从课程工作台继续管理。">
      <template #actions><el-button @click="router.push('/courses')">课程管理</el-button><el-button type="primary" @click="router.push('/courses/create')">新建课程</el-button></template>
    </PageHeader>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">重新加载</el-button></el-alert>
    <section v-if="completed.length" class="content-panel" aria-label="已完成的资料归属">
      <article v-for="item in completed" :key="item.id" class="completed-assignment">
        <span>{{ item.name }} 已归入 {{ item.courseTitle }}</span>
        <el-button @click="router.push({ path: `/courses/${item.courseId}`, query: { tab: 'materials' } })">进入课程</el-button>
        <el-button :loading="savingId === item.id" @click="rollback(item)">撤销刚才归属</el-button>
      </article>
    </section>
    <section class="content-panel" v-loading="loading">
      <el-empty v-if="!loading && !error && !bases.length" description="资料已完成归属，请从课程管理进入课程资料" />
      <article v-for="base in bases" :key="base.id" class="assignment-row">
        <div><h2>{{ base.name }}</h2><p>{{ base.documentCount ?? base.knowledge_count ?? 0 }} 份资料</p></div>
        <el-form label-position="top">
          <el-form-item label="所属课程">
            <el-select v-model="assignments[base.id]" placeholder="选择所属课程" filterable aria-label="所属课程">
              <el-option v-for="course in courses" :key="course.id" :label="course.title" :value="Number(course.id)" />
              <el-option label="新建课程草稿承接" value="new" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="assignments[base.id] === 'new'" label="新课程名称"><el-input v-model="newTitles[base.id]" /><p>新课程暂用默认分类，可在课程设置中调整。</p></el-form-item>
          <p v-if="rowErrors[base.id]" class="error-text">{{ rowErrors[base.id] }}</p>
          <el-button type="primary" :loading="savingId === base.id" :disabled="!!savingId || !assignments[base.id] || (assignments[base.id] === 'new' && !newTitles[base.id]?.trim())" @click="preview(base)">确认归属</el-button>
        </el-form>
      </article>
    </section>
    <el-dialog v-model="previewVisible" title="确认资料归属" width="min(720px, 95vw)" :close-on-click-modal="!savingId">
      <template v-if="previewData">
        <p>将 {{ previewBase?.name }} 归入 {{ previewTarget?.title }}，共 {{ previewData.documents?.length || 0 }} 份资料。</p>
        <p v-if="previewData.copiesRequired">原资料已授权给其他课程，将为相关课程建立独立副本。</p>
        <el-table :data="previewData.documents || []" max-height="280">
          <el-table-column prop="file_name" label="原始资料" />
          <el-table-column prop="document_version" label="保留版本" width="100" />
        </el-table>
        <p>本次归属会保存恢复记录，资料后续发生修改前可以撤销。</p>
      </template>
      <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" />
      <template #footer><el-button :disabled="!!savingId" @click="previewVisible = false">返回检查</el-button><el-button type="primary" :loading="!!savingId" :disabled="!previewData" @click="assign">确认归属</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/ui/PageHeader.vue'
import request from '@/utils/request'
import { getRecords, requireSuccess } from '@/utils/apiHelpers'

const router = useRouter(), route = useRoute()
const loading = ref(false), error = ref(''), bases = ref([]), courses = ref([]), savingId = ref('')
const assignments = reactive({}), newTitles = reactive({}), rowErrors = reactive({})
const completed = ref([]), previewVisible = ref(false), previewData = ref(null), previewError = ref(''), previewBase = ref(null), previewTarget = ref(null)
async function load() {
  loading.value = true; error.value = ''
  try {
    if (route.query.kbId) {
      const existing = requireSuccess(await request.get(`/api/rag/knowledge-bases/${route.query.kbId}`)).data
      const courseId = existing?.course_id || existing?.courseId
      if (courseId) {
        await router.replace({ path: `/courses/${courseId}`, query: { tab: 'materials' } })
        return
      }
    }
    const [baseResponse, courseResponse] = await Promise.all([
      request.get('/api/rag/legacy-knowledge-bases'), request.get('/courses/my'),
    ])
    bases.value = getRecords(requireSuccess(baseResponse).data)
    courses.value = getRecords(requireSuccess(courseResponse).data)
    for (const base of bases.value) {
      assignments[base.id] = base.suggestedCourseId ? Number(base.suggestedCourseId) : null
      newTitles[base.id] = base.name
    }
  } catch (problem) { error.value = problem.message || '资料归属信息加载失败' }
  finally { loading.value = false }
}
async function preview(base) {
  const selected = assignments[base.id]
  const title = selected === 'new' ? newTitles[base.id]?.trim() : courses.value.find(course => Number(course.id) === selected)?.title
  const body = selected === 'new' ? { createCourseTitle: title } : { courseId: selected }
  savingId.value = base.id; rowErrors[base.id] = ''
  try {
    const result = requireSuccess(await request.post(`/api/rag/legacy-knowledge-bases/${base.id}/preview`, body))
    previewData.value = result.data; previewBase.value = base; previewTarget.value = { title, body }
    previewError.value = ''; previewVisible.value = true
  } catch (problem) { rowErrors[base.id] = problem.message || '归属预览未加载，可重试' }
  finally { savingId.value = '' }
}
async function assign() {
  const base = previewBase.value
  if (!base || savingId.value) return
  savingId.value = base.id; previewError.value = ''
  try {
    const result = requireSuccess(await request.post(`/api/rag/legacy-knowledge-bases/${base.id}/assign`, previewTarget.value.body))
    completed.value.push({ id: base.id, name: base.name, courseId: result.data.courseId, courseTitle: previewTarget.value.title })
    bases.value = bases.value.filter(item => item.id !== base.id)
    previewVisible.value = false
    ElMessage.success('资料归属已保存')
  } catch (problem) { previewError.value = problem.message || '归属未完成，可检查后重试' }
  finally { savingId.value = '' }
}
async function rollback(item) {
  if (savingId.value) return
  savingId.value = item.id; error.value = ''
  try {
    requireSuccess(await request.post(`/api/rag/legacy-knowledge-bases/${item.id}/rollback`))
    completed.value = completed.value.filter(entry => entry.id !== item.id)
    await load()
    ElMessage.success('已恢复原资料归属')
  } catch (problem) { error.value = problem.message || '恢复未完成，可重试' }
  finally { savingId.value = '' }
}
onMounted(load)
</script>

<style scoped>
.completed-assignment { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }.completed-assignment span { flex: 1; }
.assignment-row { display: grid; grid-template-columns: 1fr minmax(260px, 420px); gap: 28px; padding: 20px 0; border-bottom: 1px solid var(--line-subtle); }
.assignment-row h2 { margin: 0 0 8px; font-size: var(--text-lg); }
.assignment-row p { color: var(--text-subtle); }
.assignment-row .error-text { color: var(--danger); }
.assignment-row .el-select { width: 100%; }
@media(max-width: 640px) { .assignment-row { grid-template-columns: 1fr; gap: 8px; } }
</style>
