<template>
  <div v-if="embedded" class="embedded-assistant">
    <AssistantConversation
      :course-id="pageScope.courseId"
      :lesson-id="pageScope.lessonId"
      :course-title="assistantPageContext.courseTitle"
      :lesson-title="assistantPageContext.lessonTitle"
      :selected-context="selectedContext"
      :draft-prompt="draftPrompt"
      :draft-version="draftVersion"
      compact
      expandable
      @context-consumed="$emit('context-consumed')"
      @expand="$emit('expand')"
    />
  </div>
  <div v-else class="ai-chat-page assistant-page">
    <aside class="chat-sidebar" role="group" aria-label="会话列表">
      <div class="sidebar-header">
        <h1>{{ user.isStudent ? '智能学伴' : '智能助手' }}</h1>
        <el-button
          type="primary"
          class="new-chat-btn"
          :disabled="Boolean(currentState.requestId)"
          @click="newConversation"
          ><el-icon><Plus /></el-icon><span>新对话</span></el-button
        >
        <el-select
          v-if="!user.isAdmin"
          v-model="selectedCourseId"
          aria-label="对话课程"
          placeholder="选择课程"
          :loading="coursesLoading"
          @change="changeCourse"
        >
          <el-option label="通用对话" :value="0" />
          <el-option
            v-for="course in courses"
            :key="course.id"
            :label="course.title"
            :value="Number(course.id)"
          />
        </el-select>
        <el-button v-if="selectedLessonId" text @click="backToCourse"
          >返回课程对话</el-button
        >
      </div>
      <div class="session-list" aria-label="课程对话记录">
        <button
          v-for="session in courseSessions"
          :key="session.id"
          type="button"
          class="session-item"
          :class="{
            active: String(session.id) === String(currentState.sessionId),
          }"
          :aria-current="
            String(session.id) === String(currentState.sessionId)
              ? 'true'
              : undefined
          "
          :disabled="
            Boolean(currentState.requestId) &&
            String(session.id) !== String(currentState.sessionId)
          "
          @click="openHistory(session.id)"
        >
          <strong>{{ session.title || '新对话' }}</strong>
          <span v-if="session.lessonId" class="session-lesson"
            >课时 {{ session.lessonId }}</span
          >
          <small v-if="session.updatedAt || session.createdAt">{{
            formatSessionDate(session.updatedAt || session.createdAt)
          }}</small>
          <small v-if="session.readOnly">只读历史</small>
        </button>
        <p
          v-if="assistant.workspace.loaded && !courseSessions.length"
          class="empty-history"
        >
          暂无对话记录
        </p>
      </div>
      <router-link
        v-if="user.isAdmin"
        class="assistant-settings"
        to="/settings/assistants"
        >助手设置</router-link
      >
    </aside>
    <main class="chat-main">
      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
        ><el-button @click="loadCourses">重新加载</el-button></el-alert
      >
      <AssistantConversation
        :course-id="selectedCourseId || undefined"
        :course-title="courseTitle"
        :lesson-id="selectedLessonId || undefined"
        :session-id="route.query.sessionId"
        :history-enabled="false"
        sidebar
        @session-change="sessionChanged"
      />
    </main>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import {
  assistantPageContext,
  assistantError,
  useAssistant,
} from '@/composables/useAssistant'
import { getCourses } from '@/utils/course'
import { unwrapAssistant } from '@/api/assistant'
import AssistantConversation from '@/components/assistant/AssistantConversation.vue'
import { Plus } from '@element-plus/icons-vue'
const props = defineProps({
  embedded: Boolean,
  selectedContext: Object,
  draftPrompt: String,
  draftVersion: Number,
})
defineEmits(['context-consumed', 'expand'])
const user = useUserStore(),
  route = useRoute(),
  router = useRouter(),
  assistant = useAssistant()
const selectedCourseId = ref(Number(route.query.courseId) || 0),
  selectedLessonId = ref(Number(route.query.lessonId) || 0)
const courses = ref([]),
  coursesLoading = ref(false),
  loadError = ref('')
const pageScope = computed(() => ({
  courseId:
    Number(
      route.params.courseId ||
        (route.name === 'CourseDetail' ? route.params.id : null),
    ) || undefined,
  lessonId:
    route.name === 'CourseLearn'
      ? Number(route.query.lessonId) ||
        assistantPageContext.lessonId ||
        undefined
      : undefined,
}))
const courseTitle = computed(
  () =>
    courses.value.find((course) => Number(course.id) === selectedCourseId.value)
      ?.title,
)
const courseSessions = computed(() =>
  assistant.workspace.sessions.filter(
    (session) => Number(session.courseId || 0) === selectedCourseId.value,
  ),
)
const currentState = computed(() =>
  assistant.stateFor({
    courseId: selectedCourseId.value || null,
    lessonId: selectedLessonId.value || null,
  }),
)
function formatSessionDate(value) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? String(value).slice(0, 16)
    : date.toLocaleString('zh-CN', {
        month: 'numeric',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
}
async function newConversation() {
  try {
    await assistant.newSession({
      courseId: selectedCourseId.value || null,
      lessonId: selectedLessonId.value || null,
    })
    const query = { ...route.query }
    delete query.sessionId
    await router.replace({ path: route.path, query })
  } catch (problem) {
    loadError.value = assistantError(problem)
  }
}
function sessionChanged(id) {
  if (!id && route.query.sessionId) {
    const query = { ...route.query }
    delete query.sessionId
    router.replace({ path: route.path, query })
  }
}
function openHistory(id) {
  const session = assistant.workspace.sessions.find(
    (item) => String(item.id) === String(id),
  )
  if (!session) return
  router.replace({
    path: '/knowledge-base',
    query: {
      sessionId: session.id,
      courseId: session.courseId || undefined,
      lessonId: session.lessonId || undefined,
    },
  })
}
async function loadCourses() {
  if (props.embedded || user.isAdmin) return
  coursesLoading.value = true
  loadError.value = ''
  try {
    const result = []
    let pageNum = 1,
      total
    do {
      const data = unwrapAssistant(
        await getCourses({
          pageNum,
          pageSize: 100,
          box: user.isTeacher ? 'all' : 'completed',
        }),
      )
      const rows = Array.isArray(data) ? data : data?.records || []
      result.push(...rows)
      total = Number(data?.total ?? result.length)
      pageNum++
      if (!rows.length) break
    } while (result.length < total)
    courses.value = result
  } catch (error) {
    loadError.value = assistantError(error)
  } finally {
    coursesLoading.value = false
  }
}
function changeCourse() {
  selectedLessonId.value = 0
  router.replace({
    path: '/knowledge-base',
    query: selectedCourseId.value ? { courseId: selectedCourseId.value } : {},
  })
}
function backToCourse() {
  selectedLessonId.value = 0
  router.replace({
    path: '/knowledge-base',
    query: { courseId: selectedCourseId.value },
  })
}
watch(
  () => [route.query.courseId, route.query.lessonId, route.query.sessionId],
  async () => {
    selectedCourseId.value = Number(route.query.courseId) || 0
    selectedLessonId.value = Number(route.query.lessonId) || 0
    if (route.query.sessionId && !route.query.courseId) {
      try {
        const sessions = await assistant.loadSessions(),
          session = sessions.find(
            (item) => String(item.id) === String(route.query.sessionId),
          )
        if (session?.courseId) {
          selectedCourseId.value = Number(session.courseId)
          selectedLessonId.value = Number(session.lessonId) || 0
        }
      } catch (error) {
        loadError.value = assistantError(error)
      }
    }
  },
  { immediate: true },
)
loadCourses()
</script>
<style scoped>
.ai-chat-page {
  display: flex;
  width: 100%;
  height: calc(100dvh - 116px);
  min-height: 460px;
  overflow: hidden;
  border: 1px solid var(--line-default);
  border-radius: var(--radius-lg);
  background: var(--surface-base);
  box-shadow: var(--shadow-1);
}
.chat-sidebar {
  flex: none;
  width: 244px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface-canvas);
  border-right: 1px solid var(--line-subtle);
}
.sidebar-header {
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: none;
  padding: 20px 16px 16px;
  border-bottom: 1px solid var(--line-subtle);
}
.sidebar-header h1 {
  font-size: 20px;
  line-height: 1.5;
  margin: 0 0 2px;
  color: var(--text-strong);
}
.new-chat-btn {
  width: 100%;
  height: 38px;
}
.new-chat-btn .el-icon {
  margin-right: 6px;
}
.sidebar-header .el-select {
  width: 100%;
}
.session-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 10px;
}
.session-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
  width: 100%;
  margin-bottom: 6px;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: var(--text-body);
}
.session-item:hover {
  background: var(--surface-hover);
}
.session-item.active {
  background: var(--surface-accent);
  border-color: var(--line-accent);
  color: var(--accent);
}
.session-item strong {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  line-height: 1.5;
  font-weight: 600;
}
.session-item small,
.session-lesson {
  font-size: 11px;
  color: var(--text-subtle);
}
.session-item:disabled {
  cursor: default;
  opacity: 0.65;
}
.empty-history {
  padding: 12px;
  font-size: 12px;
  color: var(--text-subtle);
}
.assistant-settings {
  display: block;
  padding: 14px 20px;
  color: var(--accent);
  font-size: 13px;
  border-top: 1px solid var(--line-subtle);
}
.chat-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
}
.chat-main :deep(.assistant-conversation) {
  height: 100%;
  min-height: 0;
  border: 0;
  border-radius: 0;
}
.embedded-assistant {
  height: 100%;
  min-height: 0;
}
@media (max-width: 900px) {
  .chat-sidebar {
    width: 208px;
  }
  .sidebar-header {
    padding: 16px 12px;
  }
}
@media (max-width: 600px) {
  .ai-chat-page {
    height: calc(100dvh - 108px);
    min-height: 360px;
    flex-direction: column;
  }
  .chat-sidebar {
    width: 100%;
    border-right: 0;
    border-bottom: 1px solid var(--line-subtle);
    flex: none;
  }
  .sidebar-header {
    display: grid;
    grid-template-columns: 1fr auto;
    gap: 10px;
    padding: 12px;
    border: 0;
  }
  .sidebar-header h1 {
    font-size: 17px;
    margin: 0;
    align-self: center;
  }
  .sidebar-header .new-chat-btn {
    width: auto;
    height: 32px;
  }
  .sidebar-header .el-select {
    grid-column: 1 / -1;
  }
  .session-list {
    display: flex;
    gap: 6px;
    flex: none;
    max-height: 68px;
    padding: 0 12px 10px;
    overflow-x: auto;
  }
  .session-item {
    flex: none;
    width: 155px;
    margin: 0;
    padding: 7px 10px;
    gap: 2px;
  }
  .session-item strong {
    font-size: 12px;
  }
  .session-item small {
    font-size: 10px;
  }
  .empty-history {
    margin: 0;
    padding: 3px 0;
  }
  .assistant-settings {
    padding: 8px 12px;
  }
}
</style>
