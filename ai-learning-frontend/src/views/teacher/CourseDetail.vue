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
            <el-table-column label="资源类型" width="120">
              <template #default="{ row }">
                {{ resourceTypeLabel(row.resourceType) }}
              </template>
            </el-table-column>
            <el-table-column label="下载状态" width="140">
              <template #default="{ row }">
                <el-tag v-if="row.videoResourceId" :type="videoStatusTagType(row)" size="small">
                  {{ videoStatusLabel(row) }}
                </el-tag>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="知识库" width="150">
              <template #default="{ row }">
                <el-tooltip v-if="row.videoResourceId" :content="knowledgeStatusTip(row)" placement="top">
                  <el-tag :type="knowledgeStatusTagType(row)" size="small">
                    {{ knowledgeStatusLabel(row) }}
                  </el-tag>
                </el-tooltip>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="duration" label="时长(秒)" width="100" />
            <el-table-column v-if="canManageCourse" label="操作" width="240">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="handleEditLesson(row)">编辑</el-button>
                <el-button
                  v-if="canRetryVideo(row)"
                  size="small"
                  type="warning"
                  :loading="retryingResourceId === row.videoResourceId"
                  @click="handleRetryVideo(row)"
                >
                  重试
                </el-button>
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
<!--        <el-form-item label="排序">-->
<!--          <el-input-number v-model="chapterForm.sortOrder" :min="1" />-->
<!--        </el-form-item>-->
      </el-form>
      <template #footer>
        <el-button @click="chapterDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSaveChapter">保存</el-button>
      </template>
    </el-dialog>

    <!-- Lesson Dialog -->
    <el-dialog v-model="lessonDialogVisible" :title="lessonForm.id ? '编辑课时' : '新增课时'" width="760px">
      <el-form :model="lessonForm" :rules="lessonRules" ref="lessonFormRef" label-width="100px">
        <el-form-item label="课时标题">
          <el-input v-model="lessonForm.title" />
        </el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="lessonForm.resourceType" @change="handleResourceTypeChange">
            <el-option
              v-for="item in resourceTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="lessonForm.resourceType === 'video_url'" label="视频 URL">
          <el-input v-model="lessonForm.resourceUrl" placeholder="https://example.com/video" />
        </el-form-item>
        <el-form-item v-if="lessonForm.resourceType === 'video_url'" label="处理状态">
          <div v-if="currentVideoResource" class="video-process-status">
            <div class="status-line">
              <span class="status-label">下载</span>
              <el-tag :type="videoStatusTagTypeByResource(currentVideoResource)" size="small">
                {{ videoStatusLabelByResource(currentVideoResource) }}
              </el-tag>
              <span v-if="currentVideoResource.updatedAt" class="status-time">
                更新于 {{ formatDateTime(currentVideoResource.updatedAt) }}
              </span>
            </div>
            <div class="status-line">
              <span class="status-label">知识库</span>
              <el-tooltip :content="knowledgeStatusTipByResource(currentVideoResource)" placement="top">
                <el-tag :type="knowledgeStatusTagTypeByResource(currentVideoResource)" size="small">
                  {{ knowledgeStatusLabelByResource(currentVideoResource) }}
                </el-tag>
              </el-tooltip>
              <span v-if="hasKnowledgeSummary(currentVideoResource)" class="status-note">已生成视频知识点摘要</span>
            </div>
            <div v-if="currentVideoResource.title" class="status-extra">
              {{ currentVideoResource.title }}
            </div>
            <div v-if="currentVideoResource.errorMessage" class="status-error">
              {{ currentVideoResource.errorMessage }}
            </div>
          </div>
          <span v-else-if="lessonForm.videoResourceId" class="text-muted">状态加载中...</span>
          <span v-else class="text-muted">保存后进入下载队列，并在课时列表中显示处理状态</span>
        </el-form-item>
        <el-form-item v-if="lessonForm.resourceType === 'markdown'" label="Markdown">
          <el-input
            v-model="markdownDraft"
            type="textarea"
            :rows="12"
            placeholder="用 Markdown 编写课本正文、例子、练习说明。保存后学生会在互动课本中阅读。"
          />
        </el-form-item>
        <el-form-item v-else-if="lessonForm.resourceType === 'interactive_json_builtin'" label="内置课本">
          <div class="interactive-editor">
            <el-select
              v-model="selectedTemplateKey"
              filterable
              placeholder="选择一个内置互动课本页面"
              :loading="templateLoading"
              @change="handleTemplateSelect"
            >
              <el-option
                v-for="item in templateOptions"
                :key="item.key"
                :label="`${item.subject} · ${item.title}`"
                :value="item.key"
              >
                <div class="template-option">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.summary }}</span>
                </div>
              </el-option>
            </el-select>
            <div v-if="selectedTemplateInfo" class="template-preview">
              <strong>{{ selectedTemplateInfo.title }}</strong>
              <span>{{ selectedTemplateInfo.summary }}</span>
              <el-tag v-for="tag in selectedTemplateInfo.tags || []" :key="tag" size="small">{{ tag }}</el-tag>
            </div>
            <el-input
              v-model="lessonForm.content"
              type="textarea"
              :rows="10"
              spellcheck="false"
              placeholder="选择模板后可在这里微调 JSON 内容"
            />
          </div>
        </el-form-item>
        <el-form-item v-else-if="lessonForm.resourceType === 'interactive_json_upload'" label="上传 JSON">
          <div class="interactive-editor">
            <el-upload
              ref="jsonUploadRef"
              action="#"
              :auto-upload="false"
              :limit="1"
              accept=".json,application/json"
              :on-change="handleJsonFileChange"
              :on-remove="handleJsonFileRemove"
              :on-exceed="handleFileExceed"
            >
              <el-button type="primary">选择 JSON 文件</el-button>
            </el-upload>
            <div v-if="uploadedJsonName" class="template-preview">
              <strong>{{ uploadedJsonName }}</strong>
              <span>{{ lessonJsonBlockCount }} 个内容块</span>
            </div>
            <el-input
              v-model="lessonForm.content"
              type="textarea"
              :rows="12"
              spellcheck="false"
              placeholder="上传后会显示 JSON 内容，也可以直接粘贴符合 blocks 结构的 JSON"
            />
          </div>
        </el-form-item>
        <el-form-item v-else-if="isUploadResourceType" label="资源文件">
          <div class="resource-upload">
            <el-upload
              ref="uploadRef"
              action="#"
              :auto-upload="false"
              :limit="1"
              :accept="uploadAccept"
              :disabled="uploadLoading"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              :on-exceed="handleFileExceed"
            >
              <el-button type="primary" :loading="uploadLoading">选择文件</el-button>
            </el-upload>
            <el-input v-model="lessonForm.resourceUrl" placeholder="资源地址" />
          </div>
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
import { computed, onUnmounted, ref, reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import { getCourseById } from '@/utils/course'
import {
  getVideoResource,
  retryVideoDownload,
  submitVideoUrl,
  uploadLessonResource
} from '@/api/videoResource'
import request from '@/utils/request'

const route = useRoute()
const userStore = useUserStore()
const courseId = computed(() => Number(route.params.id))
const { confirmDelete } = useConfirm()
const canManageCourse = computed(() => userStore.isAdmin || userStore.isTeacher)

const loading = ref(false)
const submitLoading = ref(false)
const uploadLoading = ref(false)
const retryingResourceId = ref(null)
const videoResourcesLoading = ref(false)
const course = ref({})
const chapters = ref([])
const activeChapters = ref([])
const videoResources = ref(new Map())
let videoResourcePollTimer = null

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
const uploadRef = ref(null)
const jsonUploadRef = ref(null)
const markdownDraft = ref('')
const selectedTemplateKey = ref('')
const uploadedJsonName = ref('')
const templateLoading = ref(false)
const templateOptions = ref([])
const lessonForm = reactive({
  id: null,
  chapterId: null,
  title: '',
  resourceType: 'markdown',
  resourceUrl: '',
  content: '',
  videoUrl: '',
  videoResourceId: null,
  duration: 0,
  sortOrder: 1
})
const lessonRules = {
  title: [{ required: true, message: '请输入课时标题', trigger: 'blur' }]
}

const resourceTypeOptions = [
  { label: 'Markdown 课本', value: 'markdown' },
  { label: '内置互动 JSON', value: 'interactive_json_builtin' },
  { label: '上传互动 JSON', value: 'interactive_json_upload' },
  { label: '视频 URL', value: 'video_url' },
  { label: '本地视频', value: 'video_file' },
  { label: 'PPT 课件', value: 'ppt' },
  { label: '资料文件', value: 'file_link' }
]

const uploadAcceptMap = {
  video_file: '.mp4,.webm,.mov,.avi,.mkv',
  ppt: '.ppt,.pptx',
  file_link: '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.zip,.rar,.7z'
}

const videoStatusLabels = {
  PENDING: '待下载',
  DOWNLOADING: '下载中',
  READY: '可播放',
  FAILED: '失败'
}

const videoStatusTagTypes = {
  PENDING: 'info',
  DOWNLOADING: 'warning',
  READY: 'success',
  FAILED: 'danger'
}

const uploadAccept = computed(() => uploadAcceptMap[lessonForm.resourceType] || '')
const isUploadResourceType = computed(() => ['video_file', 'ppt', 'file_link'].includes(lessonForm.resourceType))
const selectedTemplateInfo = computed(() => templateOptions.value.find(item => item.key === selectedTemplateKey.value) || null)
const lessonJsonBlockCount = computed(() => {
  const page = parseLessonJson(lessonForm.content)
  return page?.blocks?.length || 0
})
const currentVideoResource = computed(() => {
  if (!lessonForm.videoResourceId) return null
  return videoResources.value.get(Number(lessonForm.videoResourceId)) || null
})

const loadCourse = async () => {
  try {
    const res = await getCourseById(courseId.value)
    if (res.code === '200') {
      course.value = res.data
    }
  } catch {}
}

const loadTemplateLibrary = async () => {
  if (templateLoading.value || templateOptions.value.length) return
  templateLoading.value = true
  try {
    const res = await request.get('/learning-content/templates')
    if (res.code === '200') {
      templateOptions.value = res.data?.templates || []
    }
  } catch {} finally {
    templateLoading.value = false
  }
}

const loadChapters = async () => {
  loading.value = true
  try {
    const res = await request.get(`/courses/${courseId.value}/chapters`)
    if (res.code === '200') {
      chapters.value = res.data
      for (const chapter of chapters.value) {
        try {
          const lessonsRes = await request.get(`/chapters/${chapter.id}/lessons`, { silentError: true })
          if (lessonsRes.code === '200') {
            chapter.lessons = lessonsRes.data
          } else {
            console.warn(`Failed to load lessons for chapter ${chapter.id}: ${lessonsRes.msg}`)
            chapter.lessons = []
          }
        } catch (error) {
          console.error(`Error loading lessons for chapter ${chapter.id}:`, error)
          chapter.lessons = []
        }
      }
      await loadVideoResources()
    }
  } catch (error) {
    console.error('Error loading chapters:', error)
    ElMessage.error('加载章节失败')
  } finally {
    loading.value = false
  }
}

const loadVideoResources = async () => {
  if (videoResourcesLoading.value) return
  const ids = chapters.value
    .flatMap(chapter => chapter.lessons || [])
    .map(lesson => lesson.videoResourceId)
    .filter(Boolean)
  const uniqueIds = [...new Set(ids)]
  if (uniqueIds.length === 0) {
    videoResources.value = new Map()
    stopVideoResourcePolling()
    return
  }

  videoResourcesLoading.value = true
  const nextMap = new Map()
  try {
    await Promise.all(uniqueIds.map(async id => {
      try {
        const res = await getVideoResource(id)
        if (res.code === '200' && res.data) {
          nextMap.set(Number(id), res.data)
        }
      } catch {}
    }))
    videoResources.value = nextMap
    syncVideoResourcePolling()
  } finally {
    videoResourcesLoading.value = false
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
  } catch {} finally {
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
  } catch {}
}

const handleAddLesson = (chapter) => {
  Object.assign(lessonForm, {
    id: null,
    chapterId: chapter.id,
    title: '',
    resourceType: 'markdown',
    resourceUrl: '',
    content: '',
    videoUrl: '',
    videoResourceId: null,
    duration: 0,
    sortOrder: 1
  })
  resetLessonContentState()
  lessonDialogVisible.value = true
}

const handleEditLesson = (lesson) => {
  const resourceType = normalizeLessonResourceType(lesson.resourceType)
  Object.assign(lessonForm, {
    ...lesson,
    resourceType
  })
  resetLessonContentState({ keepContent: true })
  if (resourceType === 'markdown') {
    markdownDraft.value = markdownFromLesson(lesson)
  }
  lessonDialogVisible.value = true
}

const handleSaveLesson = async () => {
  await lessonFormRef.value.validate()
  const validationMessage = validateLessonResource()
  if (validationMessage) {
    ElMessage.error(validationMessage)
    return
  }
  submitLoading.value = true
  try {
    const url = lessonForm.id ? `/lessons/${lessonForm.id}` : '/lessons'
    const method = lessonForm.id ? 'put' : 'post'
    const payload = buildLessonPayload()
    const res = await request[method](url, payload)
    if (res.code === '200') {
      const savedLesson = res.data || lessonForm
      if (lessonForm.resourceType === 'video_url') {
        const videoRes = await submitVideoUrl(savedLesson.id, lessonForm.resourceUrl)
        if (videoRes.code === '200' && videoRes.data) {
          lessonForm.videoResourceId = videoRes.data.id
          videoResources.value = new Map(videoResources.value).set(Number(videoRes.data.id), videoRes.data)
          syncVideoResourcePolling()
        }
      }
      ElMessage.success(lessonForm.resourceType === 'video_url' ? '保存成功，视频已进入下载队列，可在列表查看处理状态' : '保存成功')
      lessonDialogVisible.value = false
      loadChapters()
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (error) {
    console.error('Save lesson error:', error)
    ElMessage.error(error.response?.data?.msg || error.message || '保存失败')
  } finally {
    submitLoading.value = false
  }
}

const handleResourceTypeChange = () => {
  lessonForm.resourceUrl = ''
  lessonForm.content = ''
  lessonForm.videoUrl = ''
  lessonForm.videoResourceId = null
  resetLessonContentState()
  if (lessonForm.resourceType === 'interactive_json_builtin') {
    loadTemplateLibrary()
  }
}

const handleFileChange = async (uploadFile) => {
  if (!uploadFile?.raw) return
  uploadLoading.value = true
  try {
    const res = await uploadLessonResource(uploadFile.raw)
    if (res.code === '200' && res.data?.url) {
      lessonForm.resourceUrl = res.data.url
      if (lessonForm.resourceType === 'video_file') {
        lessonForm.videoUrl = res.data.url
      }
      ElMessage.success('上传成功')
    }
  } catch {
    clearUploadFiles()
  } finally {
    uploadLoading.value = false
  }
}

const handleFileRemove = () => {
  lessonForm.resourceUrl = ''
  if (lessonForm.resourceType === 'video_file') {
    lessonForm.videoUrl = ''
  }
}

const handleFileExceed = () => {
  ElMessage.warning('每次只能选择一个文件')
}

const handleTemplateSelect = async (key) => {
  if (!key) return
  templateLoading.value = true
  try {
    const res = await request.get(`/learning-content/templates/${encodeURIComponent(key)}`)
    if (res.code === '200' && res.data) {
      lessonForm.content = JSON.stringify(res.data, null, 2)
      if (!lessonForm.title?.trim()) {
        lessonForm.title = res.data.title || ''
      }
    }
  } catch {} finally {
    templateLoading.value = false
  }
}

const handleJsonFileChange = async (uploadFile) => {
  if (!uploadFile?.raw) return
  try {
    const text = await readTextFile(uploadFile.raw)
    const page = parseLessonJson(text)
    if (!page?.blocks?.length) {
      ElMessage.error('JSON 需要包含非空 blocks 数组')
      jsonUploadRef.value?.clearFiles()
      return
    }
    lessonForm.content = JSON.stringify(page, null, 2)
    uploadedJsonName.value = uploadFile.name || uploadFile.raw.name || '互动课本 JSON'
    if (!lessonForm.title?.trim()) {
      lessonForm.title = page.title || ''
    }
    ElMessage.success('JSON 已读取')
  } catch {
    ElMessage.error('JSON 文件格式不正确，请检查后重新上传')
    jsonUploadRef.value?.clearFiles()
  }
}

const handleJsonFileRemove = () => {
  uploadedJsonName.value = ''
}

const handleRetryVideo = async (lesson) => {
  retryingResourceId.value = lesson.videoResourceId
  try {
    const res = await retryVideoDownload(lesson.videoResourceId)
    if (res.code === '200' && res.data) {
      videoResources.value = new Map(videoResources.value).set(Number(res.data.id), res.data)
      syncVideoResourcePolling()
      ElMessage.success('已重新提交下载')
    }
  } catch {} finally {
    retryingResourceId.value = null
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
  } catch {}
}

const loadPage = () => {
  stopVideoResourcePolling()
  course.value = {}
  chapters.value = []
  activeChapters.value = []
  videoResources.value = new Map()
  loadCourse()
  loadTemplateLibrary()
  loadChapters()
}

const buildLessonPayload = () => {
  const payload = { ...lessonForm }
  if (payload.resourceType === 'markdown') {
    payload.content = JSON.stringify(markdownPagePayload(), null, 2)
    payload.resourceUrl = ''
  } else if (isInteractiveJsonType(payload.resourceType)) {
    const parsed = parseLessonJson(payload.content)
    if (!parsed) {
      console.error('Failed to parse interactive JSON content:', payload.content)
      throw new Error('互动课本 JSON 格式不正确')
    }
    payload.content = JSON.stringify(parsed, null, 2)
    payload.resourceUrl = ''
  } else {
    payload.content = payload.content || ''
  }
  if (payload.resourceType === 'video_url') {
    payload.videoUrl = ''
    payload.videoResourceId = null
  } else if (payload.resourceType === 'video_file') {
    payload.videoUrl = payload.resourceUrl
    payload.videoResourceId = null
  } else if (payload.resourceType !== 'video_url') {
    payload.videoUrl = ''
    payload.videoResourceId = null
  }
  return payload
}

const validateLessonResource = () => {
  if (lessonForm.resourceType === 'markdown' && !markdownDraft.value.trim()) {
    return '请输入 Markdown 课本内容'
  }
  if (isInteractiveJsonType(lessonForm.resourceType) && !parseLessonJson(lessonForm.content)?.blocks?.length) {
    return '互动课本 JSON 需要包含非空 blocks 数组'
  }
  if (lessonForm.resourceType === 'video_url' && !lessonForm.resourceUrl?.trim()) {
    return '请输入视频 URL'
  }
  if (['video_file', 'ppt', 'file_link'].includes(lessonForm.resourceType) && !lessonForm.resourceUrl?.trim()) {
    return '请上传或填写资源地址'
  }
  return ''
}

const clearUploadFiles = () => {
  uploadRef.value?.clearFiles()
  jsonUploadRef.value?.clearFiles()
}

const resetLessonContentState = ({ keepContent = false } = {}) => {
  clearUploadFiles()
  markdownDraft.value = keepContent ? markdownFromLesson(lessonForm) : ''
  selectedTemplateKey.value = ''
  uploadedJsonName.value = ''
  if (!keepContent) {
    lessonForm.content = ''
  }
}

const normalizeLessonResourceType = (resourceType) => {
  if (resourceType === 'video_link') return 'video_url'
  if (resourceType === 'article') return 'markdown'
  return resourceType || 'markdown'
}

const isInteractiveJsonType = (resourceType) => {
  return ['interactive_json_builtin', 'interactive_json_upload'].includes(resourceType)
}

const markdownPagePayload = () => ({
  schemaVersion: 1,
  title: lessonForm.title,
  summary: markdownDraft.value.replace(/\s+/g, ' ').slice(0, 120),
  blocks: [
    {
      type: 'markdown',
      title: lessonForm.title || '课本正文',
      content: markdownDraft.value
    }
  ]
})

const markdownFromLesson = (lesson) => {
  const page = parseLessonJson(lesson?.content || '')
  const firstMarkdown = page?.blocks?.find(block => (block.type || 'markdown') === 'markdown')
  if (firstMarkdown?.content) return firstMarkdown.content
  return lesson?.content || lesson?.resourceUrl || ''
}

const parseLessonJson = (content) => {
  if (!content?.trim()) return null
  try {
    const parsed = JSON.parse(content)
    const page = Array.isArray(parsed) ? { blocks: parsed } : parsed
    if (!Array.isArray(page?.blocks)) return null
    return page
  } catch {
    return null
  }
}

const readTextFile = (file) => new Promise((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => resolve(String(reader.result || ''))
  reader.onerror = () => reject(reader.error)
  reader.readAsText(file, 'utf-8')
})

const resourceTypeLabel = (value) => {
  const matched = resourceTypeOptions.find(item => item.value === value)
  if (matched) return matched.label
  if (value === 'video_link') return '视频链接'
  if (value === 'article') return 'Markdown 课本'
  return value || '-'
}

const videoResourceOf = (lesson) => {
  if (!lesson.videoResourceId) return null
  return videoResources.value.get(Number(lesson.videoResourceId))
}

const videoStatusLabelByResource = (resource) => {
  const status = resource?.status || 'PENDING'
  return videoStatusLabels[status] || status
}

const videoStatusTagTypeByResource = (resource) => {
  const status = resource?.status || 'PENDING'
  return videoStatusTagTypes[status] || 'info'
}

const videoStatusLabel = (lesson) => videoStatusLabelByResource(videoResourceOf(lesson))

const videoStatusTagType = (lesson) => videoStatusTagTypeByResource(videoResourceOf(lesson))

const canRetryVideo = (lesson) => {
  return videoResourceOf(lesson)?.status === 'FAILED'
}

const isVideoProcessing = (resource) => {
  const status = resource?.status || 'PENDING'
  return ['PENDING', 'DOWNLOADING'].includes(status)
}

const hasKnowledgeSummary = (resource) => {
  return Boolean(resource?.summaryMd?.trim())
}

const knowledgeStatusLabelByResource = (resource) => {
  if (!resource) return '等待视频'
  if (['PENDING', 'DOWNLOADING'].includes(resource.status)) return '等待视频'
  if (resource.status === 'FAILED') return '待重试'
  if (hasKnowledgeSummary(resource)) return '已有知识点'
  return '未录入'
}

const knowledgeStatusTagTypeByResource = (resource) => {
  if (!resource || ['PENDING', 'DOWNLOADING'].includes(resource.status)) return 'info'
  if (resource.status === 'FAILED') return 'danger'
  if (hasKnowledgeSummary(resource)) return 'success'
  return 'warning'
}

const knowledgeStatusTipByResource = (resource) => {
  if (!resource) return '视频保存后才会进入下载与知识点处理流程'
  if (['PENDING', 'DOWNLOADING'].includes(resource.status)) return '视频仍在下载，知识点处理需等待视频完成'
  if (resource.status === 'FAILED') return '视频下载失败，暂未进入知识点处理'
  if (hasKnowledgeSummary(resource)) return '已生成视频知识点摘要'
  return '尚未发现知识点摘要或知识库录入记录'
}

const knowledgeStatusLabel = (lesson) => knowledgeStatusLabelByResource(videoResourceOf(lesson))

const knowledgeStatusTagType = (lesson) => knowledgeStatusTagTypeByResource(videoResourceOf(lesson))

const knowledgeStatusTip = (lesson) => knowledgeStatusTipByResource(videoResourceOf(lesson))

const hasProcessingVideoResources = () => {
  return chapters.value.some(chapter =>
    (chapter.lessons || []).some(lesson => lesson.videoResourceId && isVideoProcessing(videoResourceOf(lesson)))
  )
}

const syncVideoResourcePolling = () => {
  if (!hasProcessingVideoResources()) {
    stopVideoResourcePolling()
    return
  }
  if (videoResourcePollTimer) return
  videoResourcePollTimer = window.setInterval(() => {
    loadVideoResources()
  }, 5000)
}

const stopVideoResourcePolling = () => {
  if (!videoResourcePollTimer) return
  window.clearInterval(videoResourcePollTimer)
  videoResourcePollTimer = null
}

const formatDateTime = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

watch(
  () => route.params.id,
  loadPage,
  { immediate: true }
)

onUnmounted(() => {
  stopVideoResourcePolling()
})
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

.resource-upload {
  display: grid;
  gap: 10px;
  width: 100%;
}

.interactive-editor {
  display: grid;
  gap: 12px;
  width: 100%;
}

.template-option {
  display: grid;
  gap: 2px;
  line-height: 1.4;
}

.template-option span {
  color: #909399;
  font-size: 12px;
}

.template-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #f8fbff;
}

.template-preview span {
  color: #606266;
  line-height: 1.5;
}

.text-muted {
  color: #909399;
}

.video-process-status {
  display: grid;
  gap: 8px;
  width: 100%;
}

.status-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.status-label {
  width: 48px;
  color: #606266;
  font-weight: 600;
}

.status-time,
.status-note,
.status-extra {
  color: #909399;
  font-size: 12px;
}

.status-error {
  color: #f56c6c;
  font-size: 12px;
  line-height: 1.5;
}
</style>
