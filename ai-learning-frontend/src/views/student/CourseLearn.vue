<template>
  <div class="course-learn" v-loading="loading">
    <el-row :gutter="20">
      <!-- Chapter & Lesson List -->
      <el-col :span="6">
        <el-card v-loading="loading">
          <template #header>
            <span>{{ course.title || '课程学习' }}</span>
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
      <el-col :span="18">
        <el-card v-if="currentLesson">
          <template #header>
            <div class="lesson-header">
              <span>{{ currentLesson.title }}</span>
              <el-button v-if="!isLessonCompleted(currentLesson.id)" type="primary" size="small" @click="markComplete">
                标记完成
              </el-button>
            </div>
          </template>

          <div v-if="currentLesson.resourceType === 'video_link'" class="lesson-content">
            <video v-if="currentLesson.resourceUrl" :src="currentLesson.resourceUrl" controls class="video-player"></video>
            <div v-else class="empty-media">
              视频地址：{{ currentLesson.resourceUrl || '暂无' }}
            </div>
          </div>

          <div v-else-if="currentLesson.resourceType === 'article'" class="lesson-content">
            <div class="article-content">
              {{ currentLesson.resourceUrl || '暂无内容' }}
            </div>
          </div>

          <div v-else class="lesson-content">
            <div class="resource-link">
              <el-link :href="currentLesson.resourceUrl" target="_blank" type="primary">
                下载资料
              </el-link>
            </div>
          </div>

          <div class="lesson-actions">
            <el-button @click="prevLesson" :disabled="!hasPrev">上一课时</el-button>
            <el-button type="primary" @click="nextLesson" :disabled="!hasNext">下一课时</el-button>
          </div>
        </el-card>

        <el-empty v-else description="请选择课时开始学习" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getCourseById } from '@/utils/course'
import { currentUserId, formatLocalDateTime, getRecords } from '@/utils/apiHelpers'
import request from '@/utils/request'

const route = useRoute()
const userStore = useUserStore()

const courseId = computed(() => Number(route.params.courseId))
const classId = computed(() => Number(route.query.classId))
const loading = ref(false)

const course = ref({})
const chapters = ref([])
const activeChapters = ref(null)
const currentLesson = ref(null)
const completedLessons = ref(new Set())
const progressByLessonId = ref(new Map())

const allLessons = computed(() => {
  return chapters.value.flatMap(ch => ch.lessons || [])
})

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

      if (chapters.value.length > 0 && chapters.value[0].lessons?.length > 0) {
        activeChapters.value = chapters.value[0].id
        currentLesson.value = chapters.value[0].lessons[0]
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

const selectLesson = (lesson) => {
  currentLesson.value = lesson
}

const markComplete = async () => {
  if (!currentLesson.value) return

  if (!Number.isFinite(classId.value) || classId.value <= 0) {
    ElMessage.error('缺少教学班信息，请从“我的班级”进入课程学习')
    return
  }

  const lessonId = Number(currentLesson.value.id)
  const existingProgress = progressByLessonId.value.get(lessonId)
  const duration = Number(currentLesson.value.duration || 0)
  const data = {
    studentId: currentUserId(userStore),
    classId: classId.value,
    lessonId,
    progress: 100,
    learnedSeconds: duration,
    lastPosition: duration,
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
    currentLesson.value = allLessons.value[idx - 1]
  }
}

const nextLesson = () => {
  const idx = allLessons.value.findIndex(l => l.id === currentLesson.value.id)
  if (idx < allLessons.value.length - 1) {
    currentLesson.value = allLessons.value[idx + 1]
  }
}

const loadPage = () => {
  course.value = {}
  chapters.value = []
  activeChapters.value = null
  currentLesson.value = null
  completedLessons.value = new Set()
  progressByLessonId.value = new Map()
  loading.value = true
  Promise.all([loadCourse(), loadChapters(), loadProgress()]).finally(() => {
    loading.value = false
  })
}

watch(
  () => [route.params.courseId, route.query.classId],
  loadPage,
  { immediate: true }
)
</script>

<style scoped>
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

.video-player {
  width: 100%;
  max-height: 500px;
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
