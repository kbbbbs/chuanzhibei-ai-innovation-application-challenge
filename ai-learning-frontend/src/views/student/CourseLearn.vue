<template>
  <div class="course-learn" :class="{ 'sidebar-collapsed': isLessonSidebarCollapsed }" v-loading="loading">
    <div v-if="isLessonSidebarCollapsed" class="floating-sidebar-toggle">
      <el-tooltip content="展开课程目录" placement="right">
        <el-button
          :icon="Expand"
          circle
          aria-label="展开课程目录"
          @click="toggleLessonSidebar"
        />
      </el-tooltip>
    </div>
    <ErrorNotice
      v-if="loadFailed"
      class="learn-load-error"
      title="课程内容未完整加载"
      message="课程、章节或学习进度读取失败，下方内容和解锁状态可能不准确。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadPage"
    />
    <el-row :gutter="20" class="study-layout">
      <!-- Chapter & Lesson List -->
      <el-col
        v-if="!isLessonSidebarCollapsed"
        :span="5"
        class="lesson-sidebar-col"
      >
        <el-card v-loading="loading" class="lesson-sidebar-card">
          <template #header>
            <div class="sidebar-header">
              <span class="course-title">{{ course.title || '课程学习' }}</span>
              <el-tooltip content="收起课程目录" placement="right">
                <el-button
                  :icon="Fold"
                  circle
                  text
                  aria-label="收起课程目录"
                  @click="toggleLessonSidebar"
                />
              </el-tooltip>
            </div>
          </template>

          <el-collapse v-model="activeChapters" accordion>
            <el-collapse-item v-for="chapter in chapters" :key="chapter.id" :name="chapter.id">
              <template #title>
                <strong>{{ chapter.title }}</strong>
              </template>

              <button v-for="lesson in chapter.lessons" :key="lesson.id"
                      type="button"
                      :class="['lesson-item', { active: currentLesson?.id === lesson.id, locked: isLessonLocked(lesson.id) }]"
                      :aria-current="currentLesson?.id === lesson.id ? 'true' : undefined"
                      :aria-disabled="isLessonLocked(lesson.id)"
                      @click="selectLesson(lesson)">
                <span>{{ isLessonLocked(lesson.id) ? '战争迷雾 · ' : '' }}{{ lesson.title }}</span>
                <el-icon v-if="isLessonLocked(lesson.id)" class="lesson-state lesson-state--locked" aria-hidden="true"><Lock /></el-icon>
                <el-icon v-if="isLessonCompleted(lesson.id)" class="lesson-state lesson-state--done" aria-label="已完成"><Check /></el-icon>
              </button>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-col>

      <!-- Lesson Content -->
      <el-col :span="lessonContentSpan" class="lesson-main-col">
        <template v-if="currentLesson">
          <el-card class="lesson-main-card" :data-agent-course-title="course.title || ''">
            <template #header>
              <div class="lesson-header">
                <h1>{{ currentLesson.title }}</h1>
                <div class="lesson-header-actions">
                  <el-button size="small" @click="openLearningMap">查看学习地图</el-button>
                  <el-button size="small" @click="router.push({ path: `/courses/${courseId}`, query: { tab: 'graph' } })">知识图谱</el-button>
                  <el-button v-if="!isLessonCompleted(currentLesson.id)" type="primary" size="small" @click="markComplete">
                    标记完成
                  </el-button>
                </div>
              </div>
            </template>

            <div v-if="isCurrentVideoLesson" class="lesson-content">
              <div v-if="playbackLoading" class="media-state" v-loading="playbackLoading"></div>
              <PlyrVideoPlayer
                v-else-if="canPlayCurrentVideo"
                :key="playerKey"
                :src="playerSource"
                @timeupdate="handleVideoTimeUpdate"
              />
              <div v-else-if="externalVideoUrl" class="external-video-card">
                <div>
                  <h3>外部网课视频</h3>
                  <p>该课时使用 B 站/公开课程链接。为避免播放器误判网页地址，请在新标签打开观看；看完后回到本页继续完成课本、实验和 IDE 任务。</p>
                </div>
                <el-button type="primary" tag="a" :href="externalVideoUrl" target="_blank" rel="noopener noreferrer">
                  打开学习视频
                </el-button>
              </div>
              <el-result
                v-else-if="playbackStatus === 'FAILED'"
                icon="error"
                title="视频下载失败"
                :sub-title="playback?.errorMessage || '请稍后重试'"
              />
              <el-empty v-else :description="playbackStatusText" />
              <LessonPageRenderer
                v-if="hasCurrentLessonPage"
                class="mt-16"
                :lesson="currentRichLesson"
                :page="currentLessonPage"
                :progress="currentLessonProgress"
                :resolve-url="resolveResourceUrl"
              />
            </div>

            <div v-else-if="hasCurrentLessonPage" class="lesson-content">
              <LessonPageRenderer
                :lesson="currentRichLesson"
                :page="currentLessonPage"
                :progress="currentLessonProgress"
                :resolve-url="resolveResourceUrl"
              />
            </div>

            <div v-else class="lesson-content">
              <div v-if="currentLesson.resourceUrl" class="resource-link">
                <el-link :href="resolveResourceUrl(currentLesson.resourceUrl)" target="_blank" type="primary">
                  下载资料
                </el-link>
              </div>
              <el-empty v-else description="该课时暂无学习内容" />
            </div>

            <div class="lesson-actions">
              <el-button @click="prevLesson" :disabled="!hasPrev">上一课时</el-button>
              <el-button type="primary" @click="nextLesson" :disabled="!hasNext">下一课时</el-button>
            </div>
          </el-card>

          <LessonCommentPanel
            v-if="showCommentPanel"
            :key="`comments-${currentLesson.id}`"
            class="lesson-comment-panel"
            :lesson-id="currentLesson.id"
          />
        </template>

        <el-empty v-else-if="!loadFailed" description="请选择课时开始学习" />
      </el-col>

      <el-col v-if="currentLesson" :span="7" class="study-tools-col">
        <el-tabs v-model="activeToolTab" class="tool-tabs">
          <el-tab-pane label="笔记" name="notes">
            <MarkdownNotePanel
              :course-id="courseId"
              :key="`note-${currentLesson.id}`"
              :lesson-id="currentLesson.id"
              :lesson-title="currentLesson.title"
              :current-video-time="currentVideoTime"
              :has-video="canPlayCurrentVideo"
              :video-summary-md="playback?.summaryMd || ''"
              @update:content="noteContent = $event"
            />
          </el-tab-pane>
          <el-tab-pane label="问答" name="chat">
            <LessonQuickChat
              :key="`chat-${currentLesson.id}`"
              :course-id="courseId"
              :lesson-id="currentLesson.id"
              :lesson-title="currentLesson.title"
              :note-content="noteContent"
              :video-summary-md="playback?.summaryMd || ''"
              :current-video-time="currentVideoTime"
            />
          </el-tab-pane>
        </el-tabs>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Expand, Fold, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import LessonCommentPanel from '@/components/comments/LessonCommentPanel.vue'
import LessonQuickChat from '@/components/ai/LessonQuickChat.vue'
import MarkdownNotePanel from '@/components/notes/MarkdownNotePanel.vue'
import PlyrVideoPlayer from '@/components/player/PlyrVideoPlayer.vue'
import LessonPageRenderer from '@/components/learning/LessonPageRenderer.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import { getLessonPlayback } from '@/api/videoResource'
import { getCourseById } from '@/utils/course'
import { currentUserId, formatLocalDateTime, getRecords, requireSuccess } from '@/utils/apiHelpers'
import { resolveResourceUrl } from '@/utils/backendUrl'
import request from '@/utils/request'
import { assistantPageContext } from '@/composables/useAssistant'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const courseId = computed(() => Number(route.params.courseId))
const classId = computed(() => Number(route.query.classId))
const loading = ref(false)
const loadFailed = ref(false)
let pageRun = 0
const playbackLoading = ref(false)
const isLessonSidebarCollapsed = ref(localStorage.getItem('courseLessonSidebarCollapsed') === 'true')

const course = ref({})
const chapters = ref([])
const activeChapters = ref(null)
const currentLesson = ref(null)
watch(() => [courseId.value, currentLesson.value?.id, currentLesson.value?.title, course.value?.title], () => {
  assistantPageContext.courseId = courseId.value
  assistantPageContext.lessonId = currentLesson.value?.id || null
  assistantPageContext.courseTitle = course.value?.title || ''
  assistantPageContext.lessonTitle = currentLesson.value?.title || ''
}, { immediate: true })
onBeforeUnmount(() => { Object.assign(assistantPageContext, { courseId: null, lessonId: null, courseTitle: '', lessonTitle: '' }) })
const playback = ref(null)
const currentVideoTime = ref(0)
const noteContent = ref('')
const completedLessons = ref(new Set())
const progressByLessonId = ref(new Map())
const playerRenderVersion = ref(0)
const activeToolTab = ref('notes')
const experience = ref({ overview: {}, textbook: [], experiments: [], challenges: [], codeLabs: [] })
let playbackRequestSeq = 0

const allLessons = computed(() => {
  return chapters.value.flatMap(ch => ch.lessons || [])
})

const richLessonsById = computed(() => {
  const map = new Map()
  for (const chapter of experience.value.textbook || []) {
    for (const lesson of chapter.lessons || []) {
      map.set(Number(lesson.id), lesson)
    }
  }
  return map
})

const currentRichLesson = computed(() => {
  if (!currentLesson.value) return null
  return richLessonsById.value.get(Number(currentLesson.value.id)) || currentLesson.value
})

const currentLessonPage = computed(() => {
  const richLesson = currentRichLesson.value
  if (richLesson?.page?.blocks?.length) return richLesson.page
  const fallbackContent = richLesson?.content || currentLesson.value?.content || currentLesson.value?.resourceUrl || ''
  if (!fallbackContent) return null
  return {
    title: richLesson?.title || currentLesson.value?.title,
    summary: String(fallbackContent).replace(/\s+/g, ' ').slice(0, 120),
    blocks: [{ type: 'markdown', title: '核心讲解', content: fallbackContent }]
  }
})

const hasCurrentLessonPage = computed(() => Boolean(currentLessonPage.value?.blocks?.length))
const currentLessonProgress = computed(() => Number(currentRichLesson.value?.progress ?? currentLesson.value?.progress ?? 0))

const pathNodeByLessonId = computed(() => {
  const map = new Map()
  for (const node of experience.value.path || []) {
    map.set(Number(node.id), node)
  }
  return map
})

const lessonContentSpan = computed(() => isLessonSidebarCollapsed.value ? 17 : 12)

const toggleLessonSidebar = () => {
  isLessonSidebarCollapsed.value = !isLessonSidebarCollapsed.value
  localStorage.setItem('courseLessonSidebarCollapsed', String(isLessonSidebarCollapsed.value))
}

const hasPrev = computed(() => {
  if (!currentLesson.value) return false
  const idx = allLessons.value.findIndex(l => l.id === currentLesson.value.id)
  return allLessons.value.slice(0, idx).some(lesson => !isLessonLocked(lesson.id))
})

const hasNext = computed(() => {
  if (!currentLesson.value) return false
  const idx = allLessons.value.findIndex(l => l.id === currentLesson.value.id)
  return allLessons.value.slice(idx + 1).some(lesson => !isLessonLocked(lesson.id))
})

const isCurrentVideoLesson = computed(() => isVideoLesson(currentLesson.value))

const fallbackVideoUrl = computed(() => {
  if (!currentLesson.value) return ''
  const type = currentLesson.value.resourceType
  if (type === 'video_file') {
    return currentLesson.value.videoUrl || currentLesson.value.resourceUrl || ''
  }
  if (type === 'video_link') {
    return currentLesson.value.resourceUrl || currentLesson.value.videoUrl || ''
  }
  return ''
})

const playerSource = computed(() => {
  const url = playback.value?.playUrl || fallbackVideoUrl.value
  return resolveResourceUrl(url)
})

const externalVideoUrl = computed(() => {
  if (!isCurrentVideoLesson.value) return ''
  const url = fallbackVideoUrl.value
  if (!url || isDirectMediaUrl(url)) return ''
  return resolveResourceUrl(url)
})

const playerKey = computed(() => `${currentLesson.value?.id || 'none'}:${playerRenderVersion.value}:${playerSource.value}`)

const playbackStatus = computed(() => {
  if (playback.value?.status) return playback.value.status
  return playerSource.value ? 'READY' : 'PENDING'
})

const canPlayCurrentVideo = computed(() => {
  return playbackStatus.value === 'READY' && Boolean(playerSource.value) && isDirectMediaUrl(playerSource.value)
})

const showCommentPanel = computed(() => {
  return Boolean(currentLesson.value) && canPlayCurrentVideo.value
})

const playbackStatusText = computed(() => {
  const labels = {
    PENDING: '视频待下载',
    DOWNLOADING: '视频下载中',
    READY: '暂无可播放地址',
    FAILED: '视频下载失败'
  }
  return labels[playbackStatus.value] || '暂无可播放地址'
})

// Lesson locks and completion come from these reads, so a failed one is reported, never assumed.
const markLoadFailed = (run) => {
  if (run === pageRun) loadFailed.value = true
}

const loadCourse = async (run) => {
  try {
    const res = requireSuccess(await getCourseById(courseId.value))
    if (run === pageRun) course.value = res.data
  } catch {
    markLoadFailed(run)
  }
}

const loadChapters = async (run) => {
  try {
    const res = requireSuccess(await request.get(`/courses/${courseId.value}/chapters`))
    if (run !== pageRun) return
    chapters.value = res.data
    for (const chapter of chapters.value) {
      try {
        const lessonsRes = requireSuccess(await request.get(`/chapters/${chapter.id}/lessons`))
        chapter.lessons = lessonsRes.data
      } catch {
        chapter.lessons = []
        markLoadFailed(run)
      }
      if (run !== pageRun) return
    }

    const defaultLesson = findInitialLesson()
    if (defaultLesson) {
      const chapter = chapters.value.find(ch => (ch.lessons || []).some(lesson => lesson.id === defaultLesson.id))
      activeChapters.value = chapter?.id || chapters.value[0].id
      await selectLesson(defaultLesson)
    }
  } catch {
    markLoadFailed(run)
  }
}

const loadProgress = async (run) => {
  if (!Number.isFinite(classId.value) || classId.value <= 0) {
    completedLessons.value = new Set()
    progressByLessonId.value = new Map()
    return
  }

  try {
    const res = requireSuccess(await request.get('/learning-progress', {
      params: {
        studentId: currentUserId(userStore),
        classId: classId.value,
        pageSize: 200
      }
    }))
    if (run !== pageRun) return
    const records = getRecords(res.data)
    progressByLessonId.value = new Map(records.map(p => [Number(p.lessonId), p]))
    completedLessons.value = new Set(
      records
        .filter(p => Number(p.progress) >= 100)
        .map(p => Number(p.lessonId))
    )
  } catch {
    markLoadFailed(run)
  }
}

const loadExperience = async (run) => {
  try {
    const res = requireSuccess(await request.get(`/courses/${courseId.value}/experience`, { params: { classId: classId.value } }))
    if (run !== pageRun) return
    const data = res.data || {}
    experience.value = {
      overview: {},
      textbook: [],
      experiments: [],
      challenges: [],
      codeLabs: data.codeLabs?.length ? data.codeLabs : (data.codeLab ? [data.codeLab] : []),
      ...data
    }
  } catch {
    markLoadFailed(run)
  }
}

const isLessonCompleted = (lessonId) => {
  return completedLessons.value.has(Number(lessonId))
}

const isLessonLocked = (lessonId) => {
  const node = pathNodeByLessonId.value.get(Number(lessonId))
  return node ? !node.unlocked : false
}

const selectLesson = async (lesson, options = { syncRoute: true }) => {
  if (!lesson) return
  if (isLessonLocked(lesson.id)) {
    ElMessage.info('先完成前一个节点来驱散战争迷雾')
    return
  }
  const previousLessonId = currentLesson.value?.id
  currentLesson.value = lesson
  if (Number(previousLessonId) !== Number(lesson.id)) {
    playerRenderVersion.value += 1
  }
  currentVideoTime.value = 0
  noteContent.value = ''
  if (options.syncRoute) {
    syncLessonQuery(lesson.id)
  }
  await loadPlayback(lesson)
}

const syncLessonQuery = (lessonId) => {
  const nextLessonId = String(lessonId)
  if (String(route.query.lessonId || '') === nextLessonId) return
  router.replace({
    name: 'CourseLearn',
    params: { courseId: route.params.courseId },
    query: {
      ...route.query,
      lessonId: nextLessonId
    }
  })
}

const loadPlayback = async (lesson) => {
  const requestId = ++playbackRequestSeq
  playback.value = null
  if (!isVideoLesson(lesson)) {
    playbackLoading.value = false
    return
  }

  playbackLoading.value = true
  try {
    const res = await getLessonPlayback(lesson.id)
    if (requestId !== playbackRequestSeq) return
    if (res.code === '200') {
      playback.value = res.data
    }
  } catch {
    if (requestId !== playbackRequestSeq) return
    playback.value = null
  } finally {
    if (requestId === playbackRequestSeq) {
      playbackLoading.value = false
    }
  }
}

const markComplete = async () => {
  if (!currentLesson.value) return

  if (!Number.isFinite(classId.value) || classId.value <= 0) {
    ElMessage.error('缺少教学班信息，请从“我的班级”进入课程学习')
    return
  }

  const lessonId = Number(currentLesson.value.id)
  const existingProgress = progressByLessonId.value.get(lessonId)
  const duration = Number(playback.value?.duration || currentLesson.value.duration || 0)
  const data = {
    studentId: currentUserId(userStore),
    classId: classId.value,
    lessonId,
    progress: 100,
    learnedSeconds: duration,
    lastPosition: Math.max(duration, currentVideoTime.value),
    lastLearnTime: formatLocalDateTime(new Date())
  }

  try {
    const res = existingProgress?.id
      ? await request.put(`/learning-progress/${existingProgress.id}`, data)
      : await request.post('/learning-progress', data)
    if (res.code === '200') {
      const nextProgress = new Map(progressByLessonId.value)
      nextProgress.set(lessonId, { ...existingProgress, ...data, id: existingProgress?.id })
      progressByLessonId.value = nextProgress
      completedLessons.value = new Set([...completedLessons.value, lessonId])
      ElMessage.success('已标记完成')
      await loadProgress(pageRun)
      await loadExperience(pageRun)
      nextLesson()
    }
  } catch {}
}

const prevLesson = () => {
  const idx = allLessons.value.findIndex(l => l.id === currentLesson.value.id)
  const target = [...allLessons.value.slice(0, idx)].reverse().find(lesson => !isLessonLocked(lesson.id))
  if (target) {
    selectLesson(target)
  }
}

const nextLesson = () => {
  const idx = allLessons.value.findIndex(l => l.id === currentLesson.value.id)
  const target = allLessons.value.slice(idx + 1).find(lesson => !isLessonLocked(lesson.id))
  if (target) {
    selectLesson(target)
  }
}

const handleVideoTimeUpdate = (seconds) => {
  currentVideoTime.value = seconds
}

const loadPage = async () => {
  const run = ++pageRun
  loadFailed.value = false
  course.value = {}
  chapters.value = []
  activeChapters.value = null
  currentLesson.value = null
  playback.value = null
  currentVideoTime.value = 0
  noteContent.value = ''
  playerRenderVersion.value = 0
  completedLessons.value = new Set()
  progressByLessonId.value = new Map()
  activeToolTab.value = 'notes'
  experience.value = { overview: {}, textbook: [], experiments: [], challenges: [], codeLabs: [] }
  // The instance also sees navigation away from this route; that must load nothing.
  if (!route.params.courseId) return
  loading.value = true
  try {
    await Promise.all([loadCourse(run), loadProgress(run), loadExperience(run)])
    if (run === pageRun) await loadChapters(run)
  } finally {
    if (run === pageRun) loading.value = false
  }
}

const openLearningMap = () => {
  router.push({
    name: 'LearningMap',
    params: { courseId: route.params.courseId },
    query: { classId: route.query.classId }
  })
}

const findInitialLesson = () => {
  const queryLessonId = Number(route.query.lessonId)
  if (Number.isFinite(queryLessonId) && queryLessonId > 0) {
    const matched = allLessons.value.find(lesson => Number(lesson.id) === queryLessonId)
    if (matched && !isLessonLocked(matched.id)) return matched
  }
  return allLessons.value.find(lesson => !isLessonLocked(lesson.id)) || allLessons.value[0] || null
}

const isVideoLesson = (lesson) => {
  return ['video_url', 'video_file', 'video_link'].includes(lesson?.resourceType)
}

const isDirectMediaUrl = (url) => {
  if (!url) return false
  const clean = String(url).split('?')[0].toLowerCase()
  return /(\.mp4|\.webm|\.mov|\.m3u8)$/.test(clean) || clean.startsWith('blob:') || clean.startsWith('data:video/')
}

watch(
  () => [route.params.courseId, route.query.classId],
  loadPage,
  { immediate: true }
)

watch(
  () => route.query.lessonId,
  async (lessonId) => {
    if (!chapters.value.length) return
    const id = Number(lessonId)
    if (!Number.isFinite(id) || id <= 0) return
    if (Number(currentLesson.value?.id) === id) return
    const matched = allLessons.value.find(lesson => Number(lesson.id) === id)
    if (matched) {
      await selectLesson(matched, { syncRoute: false })
    }
  }
)
</script>

<style scoped>
.course-learn {
  position: relative;
}

.learn-load-error {
  margin-bottom: var(--space-4);
}

.study-layout {
  display: grid;
  grid-template-columns: minmax(160px, 200px) minmax(0, 1fr) minmax(320px, 360px);
  gap: 20px;
  margin: 0 !important;
  align-items: flex-start;
}

.study-layout > .el-col {
  min-width: 0;
  width: 100%;
  max-width: 100%;
  padding: 0 !important;
}

.sidebar-collapsed .study-layout {
  grid-template-columns: minmax(0, 1fr) minmax(320px, 360px);
}

.lesson-sidebar-col {
  position: sticky;
  top: var(--space-4);
  max-height: calc(100vh - 96px);
  overflow-y: auto;
}

.lesson-main-col,
.study-tools-col {
  display: flex;
  flex-direction: column;
}

.lesson-main-card,
.lesson-comment-panel {
  width: 100%;
}

.lesson-comment-panel {
  margin-top: 16px;
}

.study-tools-col {
  gap: 16px;
}

.study-tools-col :deep(.note-card),
.study-tools-col :deep(.quick-chat-card) {
  margin: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.course-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.floating-sidebar-toggle {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 20;
}

.sidebar-collapsed :deep(.el-card__header) {
  padding-left: 56px;
}

.lesson-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  color: var(--text-strong);
  font-weight: 600;
}

.lesson-header h1 {
  min-width: 0;
  margin: 0;
  font-size: var(--text-lg);
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.lesson-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.lesson-item {
  width: 100%;
  padding: 8px 10px;
  color: var(--text-body);
  background: transparent;
  border: 0;
  cursor: pointer;
  border-radius: var(--radius-md);
  margin-bottom: 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-size: var(--text-sm);
  line-height: 1.5;
  text-align: left;
  transition: background-color .12s var(--ease-standard), color .12s var(--ease-standard);
}

.lesson-item:hover {
  background: var(--surface-hover);
}

.lesson-item.active {
  background: var(--surface-accent);
  color: var(--accent);
  font-weight: 600;
}

.lesson-item.locked {
  cursor: not-allowed;
  color: var(--text-subtle);
  background: var(--surface-sunken);
}

.lesson-item.locked:hover {
  background: var(--surface-sunken);
}

.lesson-state {
  flex: 0 0 auto;
}

.lesson-state--locked {
  color: var(--text-subtle);
}

.lesson-state--done {
  color: var(--positive);
}

.lesson-content {
  min-height: 400px;
}

.mt-16 {
  margin-top: 16px;
}

.tool-tabs {
  width: 100%;
}

.tool-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

.media-state {
  min-height: 320px;
}

.external-video-card {
  min-height: 220px;
  padding: var(--space-6);
  border-radius: var(--radius-lg);
  border: 1px solid var(--line-accent);
  background: var(--surface-accent);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.external-video-card h3 {
  margin: 0 0 8px;
}

.external-video-card p {
  margin: 0;
  color: var(--text-body);
  line-height: 1.8;
}

.lesson-content :deep(.plyr) {
  border-radius: var(--radius-md);
  overflow: hidden;
}

.resource-link {
  padding: 50px;
  text-align: center;
}

.lesson-actions {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 1350px) {
  .study-layout {
    grid-template-columns: 200px minmax(0, 1fr);
  }

  .study-tools-col {
    grid-column: 2;
  }

  .sidebar-collapsed .study-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .sidebar-collapsed .study-tools-col {
    grid-column: 1;
  }
}

@media (max-width: 900px) {
  .study-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .lesson-sidebar-col {
    position: static;
    max-height: 320px;
  }

  .study-tools-col {
    grid-column: 1;
  }

  .external-video-card {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
