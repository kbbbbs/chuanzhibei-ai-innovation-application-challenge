<template>
  <div class="course-detail" v-loading="loading">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>课程详情：{{ course?.title }}</span>
          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>

      <el-descriptions :column="2" border v-if="course.id">
        <el-descriptions-item label="课程名称">{{ course.title }}</el-descriptions-item>
        <el-descriptions-item label="难度">{{ course.difficulty }}</el-descriptions-item>
        <el-descriptions-item label="课程简介" :span="2">{{ course.description }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="mt-20">
      <template #header>
        <div class="card-header">
          <span>章节与课时</span>
          <el-button v-if="canManageCourse" type="primary" size="small" @click="handleAddChapter">新增章节</el-button>
        </div>
      </template>

      <el-collapse v-model="activeChapters">
        <el-collapse-item v-for="chapter in chapters" :key="chapter.id" :name="chapter.id">
          <template #title>
            <div class="chapter-title">
              <span>{{ chapter.title }}</span>
              <div v-if="canManageCourse" class="chapter-actions" @click.stop>
                <el-button size="small" @click="handleAddLesson(chapter)">添加课时</el-button>
                <el-button size="small" type="primary" @click="handleEditChapter(chapter)">编辑</el-button>
                <el-button size="small" type="danger" @click="handleDeleteChapter(chapter)">删除</el-button>
              </div>
            </div>
          </template>

          <el-table :data="chapter.lessons" border size="small" v-loading="loading">
            <el-table-column prop="title" label="课时名称" />
            <el-table-column prop="resourceType" label="资源类型" width="120" />
            <el-table-column prop="duration" label="时长(秒)" width="100" />
            <el-table-column v-if="canManageCourse" label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="handleEditLesson(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="handleDeleteLesson(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <AppEmpty />
            </template>
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <!-- Chapter Dialog -->
    <el-dialog v-model="chapterDialogVisible" :title="chapterForm.id ? '编辑章节' : '新增章节'" width="500px">
      <el-form :model="chapterForm" :rules="chapterRules" ref="chapterFormRef" label-width="100px">
        <el-form-item label="章节标题">
          <el-input v-model="chapterForm.title" />
        </el-form-item>
        <el-form-item label="章节简介">
          <el-input v-model="chapterForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="chapterForm.sortOrder" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chapterDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSaveChapter">保存</el-button>
      </template>
    </el-dialog>

    <!-- Lesson Dialog -->
    <el-dialog v-model="lessonDialogVisible" :title="lessonForm.id ? '编辑课时' : '新增课时'" width="500px">
      <el-form :model="lessonForm" :rules="lessonRules" ref="lessonFormRef" label-width="100px">
        <el-form-item label="课时标题">
          <el-input v-model="lessonForm.title" />
        </el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="lessonForm.resourceType">
            <el-option label="视频链接" value="video_link" />
            <el-option label="图文内容" value="article" />
            <el-option label="资料链接" value="file_link" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源地址">
          <el-input v-model="lessonForm.resourceUrl" />
        </el-form-item>
        <el-form-item label="时长(秒)">
          <el-input-number v-model="lessonForm.duration" :min="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="lessonForm.sortOrder" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lessonDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSaveLesson">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import { getCourseById } from '@/utils/course'
import request from '@/utils/request'

const route = useRoute()
const userStore = useUserStore()
const courseId = computed(() => Number(route.params.id))
const { confirmDelete } = useConfirm()
const canManageCourse = computed(() => userStore.isAdmin || userStore.isTeacher)

const loading = ref(false)
const submitLoading = ref(false)
const course = ref({})
const chapters = ref([])
const activeChapters = ref([])

const chapterDialogVisible = ref(false)
const chapterFormRef = ref(null)
const chapterForm = reactive({
  id: null,
  courseId: null,
  title: '',
  description: '',
  sortOrder: 1
})
const chapterRules = {
  title: [{ required: true, message: '请输入章节标题', trigger: 'blur' }]
}

const lessonDialogVisible = ref(false)
const lessonFormRef = ref(null)
const lessonForm = reactive({
  id: null,
  chapterId: null,
  title: '',
  resourceType: 'video_link',
  resourceUrl: '',
  duration: 0,
  sortOrder: 1
})
const lessonRules = {
  title: [{ required: true, message: '请输入课时标题', trigger: 'blur' }]
}

const loadCourse = async () => {
  try {
    const res = await getCourseById(courseId.value)
    if (res.code === '200') {
      course.value = res.data
    }
  } catch (e) {
    // 全局已提示
  }
}

const loadChapters = async () => {
  loading.value = true
  try {
    const res = await request.get(`/courses/${courseId.value}/chapters`)
    if (res.code === '200') {
      chapters.value = res.data
      // Load lessons for each chapter
      for (const chapter of chapters.value) {
        const lessonsRes = await request.get(`/chapters/${chapter.id}/lessons`)
        if (lessonsRes.code === '200') {
          chapter.lessons = lessonsRes.data
        }
      }
    }
  } catch (e) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const handleAddChapter = () => {
  Object.assign(chapterForm, {
    id: null,
    courseId: courseId.value,
    title: '',
    description: '',
    sortOrder: 1
  })
  chapterDialogVisible.value = true
}

const handleEditChapter = (chapter) => {
  Object.assign(chapterForm, chapter)
  chapterDialogVisible.value = true
}

const handleSaveChapter = async () => {
  await chapterFormRef.value.validate()
  submitLoading.value = true
  try {
    const url = chapterForm.id ? `/chapters/${chapterForm.id}` : '/chapters'
    const method = chapterForm.id ? 'put' : 'post'
    const res = await request[method](url, chapterForm)
    if (res.code === '200') {
      ElMessage.success('保存成功')
      chapterDialogVisible.value = false
      loadChapters()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    submitLoading.value = false
  }
}

const handleDeleteChapter = async (chapter) => {
  try {
    const confirmed = await confirmDelete('确认删除此章节？')
    if (!confirmed) return
    const res = await request.delete(`/chapters/${chapter.id}`)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadChapters()
    }
  } catch (e) {
    // 全局已提示
  }
}

const handleAddLesson = (chapter) => {
  Object.assign(lessonForm, {
    id: null,
    chapterId: chapter.id,
    title: '',
    resourceType: 'video_link',
    resourceUrl: '',
    duration: 0,
    sortOrder: 1
  })
  lessonDialogVisible.value = true
}

const handleEditLesson = (lesson) => {
  Object.assign(lessonForm, lesson)
  lessonDialogVisible.value = true
}

const handleSaveLesson = async () => {
  await lessonFormRef.value.validate()
  submitLoading.value = true
  try {
    const url = lessonForm.id ? `/lessons/${lessonForm.id}` : '/lessons'
    const method = lessonForm.id ? 'put' : 'post'
    const res = await request[method](url, lessonForm)
    if (res.code === '200') {
      ElMessage.success('保存成功')
      lessonDialogVisible.value = false
      loadChapters()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    submitLoading.value = false
  }
}

const handleDeleteLesson = async (lesson) => {
  try {
    const confirmed = await confirmDelete('确认删除此课时？')
    if (!confirmed) return
    const res = await request.delete(`/lessons/${lesson.id}`)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadChapters()
    }
  } catch (e) {
    // 全局已提示
  }
}

const loadPage = () => {
  course.value = {}
  chapters.value = []
  activeChapters.value = []
  loadCourse()
  loadChapters()
}

watch(
  () => route.params.id,
  loadPage,
  { immediate: true }
)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mt-20 {
  margin-top: 20px;
}

.chapter-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 20px;
}

.chapter-actions {
  display: flex;
  gap: 8px;
}
</style>
