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

              <div v-for="lesson in chapter.lessons" :key="lesson.id"
                   :class="['lesson-item', { active: currentLesson?.id === lesson.id }]"
                   @click="selectLesson(lesson)">
                <span>{{ lesson.title }}</span>
                <el-icon v-if="isLessonCompleted(lesson.id)" color="#67c23a"><Check /></el-icon>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-col>

      <!-- Lesson Content -->
      <el-col :span="lessonContentSpan" class="lesson-main-col">
        <template v-if="currentLesson">
          <el-card class="lesson-main-card">
            <template #header>
              <div class="lesson-header">
                <span>{{ currentLesson.title }}</span>
                <el-button v-if="!isLessonCompleted(currentLesson.id)" type="primary" size="small" @click="markComplete">
                  标记完成
                </el-button>
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
              <el-result
                v-else-if="playbackStatus === 'FAILED'"
                icon="error"
                title="视频下载失败"
                :sub-title="playback?.errorMessage || '请稍后重试'"
              />
              <el-empty v-else :description="playbackStatusText" />
            </div>

            <div v-else-if="currentLesson.resourceType === 'article'" class="lesson-content">
              <div class="article-content">
                {{ currentLesson.resourceUrl || '暂无内容' }}
              </div>
            </div>

            <div v-else class="lesson-content">
              <div class="resource-link">
                <el-link :href="resolveResourceUrl(currentLesson.resourceUrl)" target="_blank" type="primary">
                  下载资料
                </el-link>
              </div>
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

        <el-empty v-else description="请选择课时开始学习" />
      </el-col>

      <el-col v-if="currentLesson" :span="7" class="study-tools-col">
        <MarkdownNotePanel
          :key="`note-${currentLesson.id}`"
          :lesson-id="currentLesson.id"
          :lesson-title="currentLesson.title"
          :current-video-time="currentVideoTime"
          :has-video="canPlayCurrentVideo"
          :video-summary-md="playback?.summaryMd || ''"
          @update:content="noteContent = $event"
        />
        <LessonQuickChat
          :key="`chat-${currentLesson.id}`"
          :lesson-title="currentLesson.title"
          :note-content="noteContent"
          :video-summary-md="playback?.summaryMd || ''"
          :current-video-time="currentVideoTime"
        />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Expand, Fold } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import LessonCommentPanel from '@/components/comments/LessonCommentPanel.vue'
import LessonQuickChat from '@/components/ai/LessonQuickChat.vue'
import MarkdownNotePanel from '@/components/notes/MarkdownNotePanel.vue'
import PlyrVideoPlayer from '@/components/player/PlyrVideoPlayer.vue'
import { getLessonPlayback } from '@/api/videoResource'
import { getCourseById } from '@/utils/course'
import { currentUserId, formatLocalDateTime, getRecords } from '@/utils/apiHelpers'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const courseId = computed(() => Number(route.params.courseId))
const classId = computed(() => Number(route.query.classId))
const loading = ref(false)
const playbackLoading = ref(false)
const isLessonSidebarCollapsed = ref(localStorage.getItem('courseLessonSidebarCollapsed') === 'true')

const course = ref({})
const chapters = ref([])
const activeChapters = ref(null)
const currentLesson = ref(null)
const playback = ref(null)
const currentVideoTime = ref(0)
const noteContent = ref('')
const completedLessons = ref(new Set())
const progressByLessonId = ref(new Map())
const playerRenderVersion = ref(0)
let playbackRequestSeq = 0

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

const allLessons = computed(() => {
  return chapters.value.flatMap(ch => ch.lessons || [])
})

const lessonContentSpan = computed(() => isLessonSidebarCollapsed.value ? 17 : 12)

const toggleLessonSidebar = () => {
  isLessonSidebarCollapsed.value = !isLessonSidebarCollapsed.value
  localStorage.setItem('courseLessonSidebarCollapsed', String(isLessonSidebarCollapsed.value))
}

const hasPrev = computed(() => {
  if (!currentLesson.value) return false
  const idx = allLessons.value.findIndex(l => l.id === currentLesson.value.id)
  return idx > 0
})

const hasNext = computed(() => {
  if (!currentLesson.value) return false
  const idx = allLessons.value.findIndex(l => l.id === currentLesson.value.id)
  return idx < allLessons.value.length - 1
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

const playerKey = computed(() => `${currentLesson.value?.id || 'none'}:${playerRenderVersion.value}:${playerSource.value}`)

const playbackStatus = computed(() => {
  if (playback.value?.status) return playback.value.status
  return playerSource.value ? 'READY' : 'PENDING'
})

const canPlayCurrentVideo = computed(() => {
  return playbackStatus.value === 'READY' && Boolean(playerSource.value)
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

const loadCourse = async () => {
  try {
    const res = await getCourseById(courseId.value)
    if (res.code === '200') {
      course.value = res.data
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  }
}

const loadChapters = async () => {
  try {
    const res = await request.get(`/courses/${courseId.value}/chapters`)
    if (res.code === '200') {
      chapters.value = res.data
      for (const chapter of chapters.value) {
        try {
          const lessonsRes = await request.get(`/chapters/${chapter.id}/lessons`)
          if (lessonsRes.code === '200') {
            chapter.lessons = lessonsRes.data
          }
        } catch (e) {
          chapter.lessons = []
        }
      }

      const defaultLesson = findInitialLesson()
      if (defaultLesson) {
        const chapter = chapters.value.find(ch => (ch.lessons || []).some(lesson => lesson.id === defaultLesson.id))
        activeChapters.value = chapter?.id || chapters.value[0].id
        await selectLesson(defaultLesson)
      }
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  }
}

const loadProgress = async () => {
  if (!Number.isFinite(classId.value) || classId.value <= 0) {
    completedLessons.value = new Set()
    progressByLessonId.value = new Map()
    return
  }

  try {
    const res = await request.get('/learning-progress', {
      params: {
        studentId: currentUserId(userStore),
        classId: classId.value,
        pageSize: 200
      }
    })
    if (res.code === '200') {
      const records = getRecords(res.data)
      progressByLessonId.value = new Map(records.map(p => [Number(p.lessonId), p]))
      completedLessons.value = new Set(
        records
          .filter(p => Number(p.progress) >= 100)
          .map(p => Number(p.lessonId))
      )
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  }
}

const isLessonCompleted = (lessonId) => {
  return completedLessons.value.has(Number(lessonId))
}

const selectLesson = async (lesson, options = { syncRoute: true }) => {
  if (!lesson) return
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
  } catch (e) {
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
      nextLesson()
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  }
}

const prevLesson = () => {
  const idx = allLessons.value.findIndex(l => l.id === currentLesson.value.id)
  if (idx > 0) {
    selectLesson(allLessons.value[idx - 1])
  }
}

const nextLesson = () => {
  const idx = allLessons.value.findIndex(l => l.id === currentLesson.value.id)
  if (idx < allLessons.value.length - 1) {
    selectLesson(allLessons.value[idx + 1])
  }
}

const handleVideoTimeUpdate = (seconds) => {
  currentVideoTime.value = seconds
}

const loadPage = () => {
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
  loading.value = true
  Promise.all([loadCourse(), loadChapters(), loadProgress()]).finally(() => {
    loading.value = false
  })
}

const findInitialLesson = () => {
  const queryLessonId = Number(route.query.lessonId)
  if (Number.isFinite(queryLessonId) && queryLessonId > 0) {
    const matched = allLessons.value.find(lesson => Number(lesson.id) === queryLessonId)
    if (matched) return matched
  }
  return allLessons.value[0] || null
}

const isVideoLesson = (lesson) => {
  return ['video_url', 'video_file', 'video_link'].includes(lesson?.resourceType)
}

const resolveResourceUrl = (url) => {
  if (!url) return ''
  if (/^(https?:)?\/\//i.test(url) || /^(data|blob):/i.test(url)) {
    return url
  }
  if (url.startsWith('/')) {
    return `${apiBaseUrl}${url}`
  }
  return url
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

.study-layout {
  align-items: flex-start;
}

.lesson-sidebar-col,
.course-learn :deep(.el-col) {
  transition: width 0.2s ease, max-width 0.2s ease, flex-basis 0.2s ease;
}

.lesson-sidebar-card {
  height: 100%;
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
}

.lesson-item {
  padding: 10px;
  cursor: pointer;
  border-radius: 4px;
  margin-bottom: 5px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.lesson-item:hover {
  background: #f5f5f5;
}

.lesson-item.active {
  background: #e6f7ff;
  color: #1890ff;
}

.lesson-content {
  min-height: 400px;
}

.media-state {
  min-height: 320px;
}

.lesson-content :deep(.plyr) {
  border-radius: 6px;
  overflow: hidden;
}

.empty-media {
  padding: 50px;
  text-align: center;
  color: #999;
}

.article-content {
  padding: 20px;
  line-height: 1.8;
}

.resource-link {
  padding: 50px;
  text-align: center;
}

.lesson-actions {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
}
</style>
