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
                   :class="['lesson-item', { active: currentLesson?.id === lesson.id, locked: isLessonLocked(lesson.id) }]"
                   :aria-disabled="isLessonLocked(lesson.id)"
                   @click="selectLesson(lesson)">
                <span>{{ isLessonLocked(lesson.id) ? '战争迷雾 · ' : '' }}{{ lesson.title }}</span>
                <el-icon v-if="isLessonLocked(lesson.id)" color="#94a3b8"><Lock /></el-icon>
                <el-icon v-if="isLessonCompleted(lesson.id)" color="#67c23a"><Check /></el-icon>
              </div>
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
                <span>{{ currentLesson.title }}</span>
                <div class="lesson-header-actions">
                  <el-button size="small" @click="openLearningMap">查看学习地图</el-button>
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
        <el-tabs v-model="activeToolTab" class="tool-tabs">
          <el-tab-pane label="笔记" name="notes">
            <MarkdownNotePanel
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
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Expand, Fold, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import LessonCommentPanel from '@/components/comments/LessonCommentPanel.vue'
import LessonQuickChat from '@/components/ai/LessonQuickChat.vue'
import MarkdownNotePanel from '@/components/notes/MarkdownNotePanel.vue'
import PlyrVideoPlayer from '@/components/player/PlyrVideoPlayer.vue'
import LessonPageRenderer from '@/components/learning/LessonPageRenderer.vue'
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
const activeToolTab = ref('notes')
const experience = ref({ overview: {}, textbook: [], experiments: [], challenges: [], codeLabs: [] })
let playbackRequestSeq = 0

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

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

const loadCourse = async () => {
  try {
    const res = await getCourseById(courseId.value)
    if (res.code === '200') {
      course.value = res.data
    }
  } catch {}
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
        } catch {
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
  } catch {}
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
  } catch {}
}

const loadExperience = async () => {
  try {
    const res = await request.get(`/courses/${courseId.value}/experience`, { params: { classId: classId.value } })
    if (res.code === '200') {
      const data = res.data || {}
      experience.value = {
        overview: {},
        textbook: [],
        experiments: [],
        challenges: [],
        codeLabs: data.codeLabs?.length ? data.codeLabs : (data.codeLab ? [data.codeLab] : []),
        ...data
      }
    }
  } catch {
    experience.value = { overview: {}, textbook: [], experiments: [], challenges: [], codeLabs: [] }
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
      await loadProgress()
      await loadExperience()
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
  loading.value = true
  try {
    await Promise.all([loadCourse(), loadProgress(), loadExperience()])
    await loadChapters()
  } finally {
    loading.value = false
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
  gap: 12px;
}

.lesson-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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

.lesson-item.locked {
  cursor: not-allowed;
  color: #94a3b8;
  background:
    radial-gradient(circle at 20% 20%, rgba(148, 163, 184, .28), transparent 34%),
    linear-gradient(135deg, #f8fafc, #eef2f7);
}

.lesson-item.locked:hover {
  background:
    radial-gradient(circle at 20% 20%, rgba(148, 163, 184, .34), transparent 34%),
    linear-gradient(135deg, #f8fafc, #eef2f7);
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
  padding: 24px;
  border-radius: 18px;
  border: 1px solid #dbeafe;
  background:
    radial-gradient(circle at 12% 20%, rgba(37, 99, 235, .18), transparent 26%),
    linear-gradient(135deg, #eff6ff, #fff);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.external-video-card h3 {
  margin: 0 0 8px;
  color: #172033;
}

.external-video-card p {
  margin: 0;
  color: #475569;
  line-height: 1.8;
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
