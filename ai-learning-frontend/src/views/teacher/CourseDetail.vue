<template>
  <div class="course-detail workspace-page" v-loading="loading">
    <header class="course-workspace-heading">
      <div class="course-heading-copy">
        <p class="course-eyebrow">{{ setupStep ? '新建课程' : '课程工作台' }}</p>
        <div class="course-heading-title">
          <h1>
            <button v-if="canManageCourse && !setupStep" class="editable-course-title" title="编辑基本信息" @click="basicInfoVisible = true">
              {{ course.title }}<el-icon aria-hidden="true"><EditPen /></el-icon>
            </button>
            <template v-else>{{ course?.title || '新建课程' }}</template>
          </h1>
          <el-tag v-if="course.id" :type="course.status === 1 ? 'success' : 'info'">{{ course.status === 1 ? '已发布' : setupStep ? '创建中' : '未发布' }}</el-tag>
        </div>
        <p v-if="course.description" class="course-description">{{ course.description }}</p>
        <p v-if="course.id && !setupStep" class="course-metadata">{{ difficultyLabel }}<span>·</span>{{ chapters.length }} 个章节<span>·</span>{{ lessonCount }} 个课时</p>
      </div>
      <div v-if="!setupStep" class="course-heading-actions">
        <el-button v-if="canManageCourse && workspaceTab !== 'preparation'" @click="selectWorkspaceTab('preparation')">AI 备课</el-button>
        <el-button @click="router.push(isStudent ? '/my-classes' : '/courses')">返回列表</el-button>
      </div>
    </header>

    <ErrorNotice
      v-if="contentFailed"
      title="课程内容未完整加载"
      message="课程或章节读取失败，下方内容可能不完整，这不代表课程没有内容。"
      retry-label="重新加载"
      :busy="loading"
      @retry="reloadContent"
    />

    <section v-if="course.id && isStudent" class="content-panel" aria-label="课程信息">
      <dl class="definition-grid">
        <div>
          <dt>课程名称</dt>
          <dd>{{ course.title }}</dd>
        </div>
        <div>
          <dt>难度</dt>
          <dd>{{ course.difficulty || '—' }}</dd>
        </div>
        <div>
          <dt>章节</dt>
          <dd>{{ chapters.length }} 章</dd>
        </div>
      </dl>

      <div v-if="isStudent && publicCourse" class="student-course-actions">
        <div>
          <strong>{{ joinedClassId ? '已加入公共课' : '这是一门可自助加入的公共课' }}</strong>
          <span>{{ joinedClassId ? `已加入教学班：${publicCourse.className || joinedClassId}` : '加入后即可进入学习页面并记录课时进度。' }}</span>
        </div>
        <el-button
          type="primary"
          :loading="joining"
          :disabled="!joinedClassId && !publicCourse.joinable"
          @click="handleStudentCourseAction"
        >
          {{ joinedClassId ? '进入学习' : publicCourse.joinable ? '加入课程并开始学习' : '暂未开放教学班' }}
        </el-button>
      </div>
    </section>

    <CourseSetup v-if="canManageMaterials && setupStep" :key="courseId" :course-id="courseId" @updated="reloadContent" />
    <template v-else>
      <el-tabs v-if="canManageCourse || canViewGraph" :model-value="workspaceTab" :before-leave="selectWorkspaceTab" data-testid="course-workspace-tabs" class="course-workspace-tabs">
        <el-tab-pane label="课程内容" name="content" />
        <el-tab-pane v-if="canManageMaterials" label="课程资料" name="materials" />
        <el-tab-pane v-if="canManageMaterials" label="智能问答" name="chat" />
        <el-tab-pane v-if="canViewGraph && !canManageMaterials" label="知识图谱" name="graph" />
      </el-tabs>
      <section v-if="canManageMaterials && workspaceTab === 'materials'" aria-label="课程资料">
        <el-tabs v-model="materialTab" data-testid="course-material-tabs">
          <el-tab-pane label="资料文件" name="documents" />
          <el-tab-pane label="检索与验证" name="search" />
          <el-tab-pane label="知识图谱" name="graph" />
        </el-tabs>
        <CourseKnowledgeGraph v-if="materialTab === 'graph'" :key="courseId" :course-id="courseId" />
        <TeacherKnowledgeBase v-else :key="`${courseId}:${materialTab}`" :course-id="courseId" :workspace-tab="materialTab" embedded />
      </section>
      <TeacherKnowledgeBase v-else-if="canManageMaterials && workspaceTab === 'chat'" :key="`course-chat:${courseId}`" :course-id="courseId" workspace-tab="chat" embedded />
      <CourseKnowledgeGraph v-else-if="canViewGraph && workspaceTab === 'graph'" :key="courseId" :course-id="courseId" />
      <IntelligentResourceGen
        v-else-if="canManageCourse && workspaceTab === 'preparation'"
        :key="preparationKey(route)"
        ref="preparationRef"
        :course-id="courseId"
        embedded
        @applied="reloadContent"
      />
    <section v-show="workspaceTab === 'content'" class="content-panel course-content-panel" aria-labelledby="chapter-heading">
      <div class="section-head">
        <div>
          <h2 id="chapter-heading">章节与课时</h2>
          <p>{{ canManageCourse ? '维护章节结构、课时资源，并查看视频下载与知识点处理状态。' : '按章节查看课时内容。' }}</p>
        </div>
      </div>

      <div v-if="canManageCourse" class="course-content-workspace">
        <nav class="course-directory" aria-label="章节课时目录">
          <p class="directory-label">课程目录</p>
          <section v-for="(chapter, chapterIndex) in chapters" :key="chapter.id" class="directory-chapter">
            <div class="directory-chapter-heading">
              <strong><span aria-hidden="true">{{ chapterIndex + 1 }}. </span><span>{{ chapter.title }}</span></strong>
              <el-dropdown trigger="click">
                <el-button text size="small" :aria-label="`${chapter.title}章节操作`">···</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="handleEditChapter(chapter)">编辑章节</el-dropdown-item>
                    <el-dropdown-item @click="handleDeleteChapter(chapter)">删除章节</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <div v-for="(lesson, lessonIndex) in chapter.lessons" :key="lesson.id" class="directory-lesson-row" :class="{ selected: selectedLesson?.id === lesson.id }">
              <button class="directory-lesson" :aria-label="lesson.title" @click="selectDirectoryLesson(lesson)"><span>{{ chapterIndex + 1 }}.{{ lessonIndex + 1 }}</span>{{ lesson.title }}</button>
              <el-button class="directory-delete" text size="small" :aria-label="`删除课时：${lesson.title}`" :icon="Delete" :disabled="submitLoading" @click="handleDeleteLesson(lesson)" />
            </div>
            <div class="chapter-actions">
              <el-button text size="small" @click="handleAddLesson(chapter)">＋ 添加课时</el-button>
            </div>
          </section>
          <el-button class="directory-add-chapter" aria-label="新增章节" text @click="handleAddChapter">＋ 新增章节</el-button>
        </nav>
        <article v-if="selectedLesson" class="course-lesson-preview" data-testid="course-lesson-preview" aria-label="课时内容预览" tabindex="0">
          <div class="lesson-preview-toolbar">
            <span>{{ resourceTypeLabel(selectedLesson.resourceType) }}</span>
            <div v-if="!inlineEditing" class="lesson-preview-actions"><el-button @click="handleEditLesson(selectedLesson)">编辑课时</el-button></div>
          </div>
          <el-form v-if="inlineEditing" ref="inlineLessonFormRef" :model="lessonForm" :rules="lessonRules" label-position="top" class="inline-lesson-editor" data-testid="inline-lesson-editor" :disabled="submitLoading" @submit.prevent>
            <el-form-item label="课时标题" prop="title"><el-input v-model="lessonForm.title" /></el-form-item>
            <MdEditor v-model="markdownDraft" editor-id="course-inline-lesson" language="zh-CN" :theme="resolvedTheme" :preview="false" :toolbars-exclude="['github']" :read-only="submitLoading" class="inline-markdown-editor" />
            <div class="inline-lesson-actions"><el-button :disabled="submitLoading" @click="inlineEditing = false">取消编辑</el-button><el-button type="primary" :loading="submitLoading" @click="handleSaveLesson">保存课时</el-button></div>
          </el-form>
          <template v-else>
          <h2>{{ selectedLesson.title }}</h2>
          <div v-if="selectedLesson.videoResourceId" class="lesson-resource-status">
            <el-tag :type="videoStatusTagType(selectedLesson)" size="small">{{ videoStatusLabel(selectedLesson) }}</el-tag>
            <el-tooltip :content="knowledgeStatusTip(selectedLesson)"><el-tag :type="knowledgeStatusTagType(selectedLesson)" size="small">{{ knowledgeStatusLabel(selectedLesson) }}</el-tag></el-tooltip>
            <el-button v-if="canRetryVideo(selectedLesson)" size="small" :loading="retryingResourceId === selectedLesson.videoResourceId" @click="handleRetryVideo(selectedLesson)">重试</el-button>
          </div>
          <div v-if="selectedLessonMarkdown" class="lesson-preview-markdown" v-html="renderMarkdown(selectedLessonMarkdown)" />
          <div v-else class="lesson-resource-summary">
            <p>{{ selectedLesson.resourceType?.startsWith('interactive_json') ? '互动课本已保存，可在编辑课时中维护内容块。' : selectedLesson.resourceUrl ? '课时资源已关联，可在编辑课时中查看与维护。' : '该课时尚未填写正文，可继续编辑。' }}</p>
          </div>
          </template>
          <div v-if="!inlineEditing" class="lesson-preview-footer"><el-button @click="selectWorkspaceTab('materials')">查看课程参考资料</el-button><el-button text type="primary" @click="selectWorkspaceTab('chat')">向课程助手提问 →</el-button></div>
        </article>
        <div v-else class="empty-lesson-preview"><AppEmpty :description="chapters.length ? '选择章节添加课时，开始准备教学内容' : '从左侧新增章节开始组织课程'" /></div>
      </div>
      <template v-else-if="!canManageCourse">
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

          <el-table :data="chapter.lessons" size="small" v-loading="loading">
            <el-table-column prop="title" label="课时名称" min-width="180" />
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
      </template>
      <AppEmpty v-if="!canManageCourse && !chapters.length && !loading && !contentFailed" description="暂无章节" />
    </section>

    </template>
    <el-dialog v-model="basicInfoVisible" title="编辑基本信息" width="min(680px, 94vw)" destroy-on-close :close-on-click-modal="false">
      <CourseForm :course-id="courseId" dialog-mode @saved="basicInfoSaved" @cancel="basicInfoVisible = false" />
    </el-dialog>
    <!-- Chapter Dialog -->
    <el-dialog v-model="chapterDialogVisible" :title="chapterForm.id ? '编辑章节' : '新增章节'" width="500px">
      <el-form :model="chapterForm" :rules="chapterRules" ref="chapterFormRef" label-position="top">
        <el-form-item label="章节标题" prop="title">
          <el-input v-model="chapterForm.title" maxlength="100" />
        </el-form-item>
        <el-form-item label="章节简介">
          <el-input v-model="chapterForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="chapterForm.sortOrder" :min="1" placeholder="留空排在最后" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chapterDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSaveChapter">保存</el-button>
      </template>
    </el-dialog>

    <!-- Lesson Dialog -->
    <el-dialog v-model="lessonDialogVisible" :title="lessonForm.id ? '编辑课时' : '新增课时'" width="760px">
      <el-form :model="lessonForm" :rules="lessonRules" ref="lessonFormRef" label-position="top">
        <el-form-item label="课时标题" prop="title">
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
        <el-form-item label="排序">
          <el-input-number v-model="lessonForm.sortOrder" :min="1" placeholder="留空排在最后" />
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
import { assistantPageContext } from '@/composables/useAssistant'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import { EditPen, Delete } from '@element-plus/icons-vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useTheme } from '@/composables/useTheme'
import CourseForm from './CourseForm.vue'
import { renderMarkdown } from '@/utils/markdown'
import CourseSetup from '@/components/course/CourseSetup.vue'
import IntelligentResourceGen from './IntelligentResourceGen.vue'
import TeacherKnowledgeBase from './TeacherKnowledgeBase.vue'
import CourseKnowledgeGraph from '@/components/knowledge/CourseKnowledgeGraph.vue'
import { requireSuccess } from '@/utils/apiHelpers'
import { getCourseById } from '@/utils/course'
import {
  getVideoResource,
  retryVideoDownload,
  submitVideoUrl,
  uploadLessonResource
} from '@/api/videoResource'
import request from '@/utils/request'
import { getHomeCourseRecommendations, joinHomeCourseRecommendation } from '@/api/homeCourseRecommendation'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const courseId = computed(() => Number(route.params.id))
const { confirmDelete } = useConfirm()
const { resolvedTheme } = useTheme()
const canManageCourse = computed(() => course.value.id && userStore.roleCode === 'teacher' && Number(course.value.teacherId) === Number(userStore.userId))
const canManageMaterials = canManageCourse
const isStudent = computed(() => userStore.roleCode === 'student')
const canViewGraph = computed(() => Boolean(canManageCourse.value || isStudent.value && course.value.id))

const loading = ref(false)
const contentFailed = ref(false)
let contentRun = 0
const submitLoading = ref(false)
const uploadLoading = ref(false)
const retryingResourceId = ref(null)
const videoResourcesLoading = ref(false)
const course = ref({})
watch(() => [courseId.value, course.value.title], () => { Object.assign(assistantPageContext, { courseId: courseId.value, lessonId: null, courseTitle: course.value.title || '', lessonTitle: '' }) }, { immediate: true })
onUnmounted(() => { Object.assign(assistantPageContext, { courseId: null, lessonId: null, courseTitle: '', lessonTitle: '' }) })
const materialTabs = ['documents', 'chat', 'search', 'graph']
const preparationRef = ref(null)
const workspaceTab = computed(() => {
  const tab = route.query.tab
  if (canManageMaterials.value && (tab === 'chat' || tab === 'materials' && route.query.material === 'chat')) return 'chat'
  if (canManageMaterials.value && (tab === 'materials' || materialTabs.includes(tab))) return 'materials'
  if (tab === 'preparation' && canManageCourse.value) return 'preparation'
  if (tab === 'graph' && canViewGraph.value) return 'graph'
  return 'content'
})
async function selectWorkspaceTab(tab) {
  if (tab === workspaceTab.value) return true
  await router.replace({ path: route.path, query: { tab } })
  // Selection follows the URL once the router has accepted the change.
  return false
}
const materialTab = computed({
  get: () => {
    if (materialTabs.includes(route.query.tab)) return route.query.tab
    return materialTabs.includes(route.query.material) ? route.query.material : 'documents'
  },
  set: tab => router.replace({ path: route.path, query: { tab: 'materials', ...(tab !== 'documents' ? { material: tab } : {}) } }),
})
const preparationKey = location => `${location.params.id}:${location.query.projectId || location.query.draftId || ''}`
async function flushWorkspacePreparation() {
  if (!preparationRef.value || await preparationRef.value.flushDraft()) return true
  ElMessage.warning('当前备课修改尚未保存，请完成保存后重试')
  return false
}
onBeforeRouteLeave(async () => await allowInlineExit() && await flushWorkspacePreparation())
onBeforeRouteUpdate(async to => {
  if (!await allowInlineExit()) return false
  if (to.query.tab === 'preparation' && preparationKey(to) === preparationKey(route)) return true
  return flushWorkspacePreparation()
})
const setupStep = computed(() => {
  if (!canManageMaterials.value) return null
  if (course.value.setupStage) return course.value.setupStage === 'COMPLETED' ? null : course.value.setupStage.toLowerCase()
  return ['basic', 'materials', 'content'].includes(route.query.setup) ? route.query.setup : null
})

const publicCourse = ref(null)
const joinedClassId = ref(null)
const joining = ref(false)
const chapters = ref([])
const activeChapters = ref([])
const basicInfoVisible = ref(false)
const selectedLessonId = ref(null)
const lessonCount = computed(() => chapters.value.reduce((count, chapter) => count + (chapter.lessons?.length || 0), 0))
const difficultyLabel = computed(() => ({ beginner: '入门', intermediate: '基础', advanced: '进阶' }[course.value.difficulty] || course.value.difficulty || '课程内容'))
const selectedLesson = computed(() => {
  const lessons = chapters.value.flatMap(chapter => chapter.lessons || [])
  return lessons.find(lesson => lesson.id === selectedLessonId.value) || lessons[0] || null
})
const selectedLessonMarkdown = computed(() => {
  const lesson = selectedLesson.value
  if (!lesson || !['markdown', 'article'].includes(lesson.resourceType)) return ''
  const page = parseLessonJson(lesson.content)
  if (Array.isArray(page?.blocks)) return page.blocks.filter(block => !block.type || block.type === 'markdown').map(block => block.content || '').join('\n\n')
  return lesson.content || ''
})
async function basicInfoSaved() {
  basicInfoVisible.value = false
  await loadCourse()
}
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
const inlineEditing = ref(false)
const inlineLessonFormRef = ref(null)
let inlineInitial = ''
async function allowInlineExit() {
  if (!inlineEditing.value) return true
  if (submitLoading.value) return false
  const changed = JSON.stringify([lessonForm.title, markdownDraft.value]) !== inlineInitial
  if (changed && !await confirmDelete('课时有未保存的修改，确认放弃修改？', { confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' })) return false
  inlineEditing.value = false
  return true
}
async function selectDirectoryLesson(lesson) {
  if (selectedLesson.value?.id === lesson.id) return
  if (await allowInlineExit()) selectedLessonId.value = lesson.id
}
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
  const id = courseId.value
  try {
    const res = requireSuccess(await getCourseById(id))
    if (id === courseId.value) {
      course.value = res.data
      if (canManageCourse.value && !setupStep.value && route.query.tab === 'settings') {
        await router.replace({ name: 'CourseCreate', query: { id, returnTo: '/courses' } })
      } else if (!setupStep.value && route.query.setup) {
        const query = { ...route.query }
        delete query.setup
        await router.replace({ path: route.path, query })
      }
    }
  } catch {
    if (id === courseId.value) contentFailed.value = true
  }
}

const loadPublicCourseState = async () => {
  publicCourse.value = null
  joinedClassId.value = null
  if (!isStudent.value || !courseId.value) return
  try {
    const res = await getHomeCourseRecommendations()
    if (res.code !== '200' || !Array.isArray(res.data)) return
    publicCourse.value = res.data.find(item => Number(item.courseId) === courseId.value) || null
    if (publicCourse.value?.enrolled && publicCourse.value.classId) {
      joinedClassId.value = Number(publicCourse.value.classId)
    }
  } catch {}
}

const handleStudentCourseAction = async () => {
  if (joinedClassId.value) {
    router.push({ name: 'CourseLearn', params: { courseId: courseId.value }, query: { classId: joinedClassId.value } })
    return
  }
  if (!publicCourse.value?.joinable) {
    ElMessage.warning('该公共课暂未开放教学班')
    return
  }
  joining.value = true
  try {
    const res = await joinHomeCourseRecommendation(courseId.value)
    if (res.code === '200' && res.data?.classId) {
      publicCourse.value = res.data
      joinedClassId.value = Number(res.data.classId)
      ElMessage.success('加入课程成功，正在进入学习页面')
      router.push({ name: 'CourseLearn', params: { courseId: courseId.value }, query: { classId: joinedClassId.value } })
    }
  } finally {
    joining.value = false
  }
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
  const run = ++contentRun
  loading.value = true
  try {
    const res = requireSuccess(await request.get(`/courses/${courseId.value}/chapters`))
    if (run !== contentRun) return
    chapters.value = res.data
    for (const chapter of chapters.value) {
      try {
        const lessonsRes = requireSuccess(await request.get(`/chapters/${chapter.id}/lessons`, { silentError: true }))
        chapter.lessons = lessonsRes.data
      } catch {
        // Keep the chapter, but an unread lesson list must not pass as an empty chapter.
        chapter.lessons = []
        contentFailed.value = true
      }
      if (run !== contentRun) return
    }
    await loadVideoResources()
  } catch {
    if (run !== contentRun) return
    chapters.value = []
    contentFailed.value = true
  } finally {
    if (run === contentRun) loading.value = false
  }
}

const reloadContent = async () => {
  contentFailed.value = false
  await loadCourse()
  if (setupStep.value) return
  return loadChapters()
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
    sortOrder: null
  })
  chapterDialogVisible.value = true
  chapterFormRef.value?.clearValidate?.()
}

const handleEditChapter = (chapter) => {
  const { id, courseId: chapterCourseId, title, description, sortOrder } = chapter
  Object.assign(chapterForm, { id, courseId: chapterCourseId, title, description, sortOrder })
  chapterDialogVisible.value = true
}

const handleSaveChapter = async () => {
  try { await chapterFormRef.value.validate() } catch { return }
  submitLoading.value = true
  try {
    const url = chapterForm.id ? `/chapters/${chapterForm.id}` : '/chapters'
    const method = chapterForm.id ? 'put' : 'post'
    const res = await request[method](url, { ...chapterForm })
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

const handleAddLesson = async (chapter) => {
  if (!await allowInlineExit()) return
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
    sortOrder: null
  })
  resetLessonContentState()
  lessonDialogVisible.value = true
  lessonFormRef.value?.clearValidate?.()
}

const handleEditLesson = async (lesson) => {
  if (!await allowInlineExit()) return
  const resourceType = normalizeLessonResourceType(lesson.resourceType)
  Object.assign(lessonForm, {
    ...lesson,
    resourceType
  })
  resetLessonContentState({ keepContent: true })
  if (resourceType === 'markdown') {
    markdownDraft.value = markdownFromLesson(lesson)
    selectedLessonId.value = lesson.id
    inlineInitial = JSON.stringify([lessonForm.title, markdownDraft.value])
    inlineEditing.value = true
    lessonDialogVisible.value = false
    return
  }
  lessonDialogVisible.value = true
}

// Requests here are silent, so each failure is reported exactly once below.
const failureText = (error, fallback) => error?.response?.data?.msg || error?.message || fallback
const handleSaveLesson = async () => {
  try { await (inlineEditing.value ? inlineLessonFormRef.value : lessonFormRef.value).validate() } catch { return }
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
    const res = await request[method](url, payload, { silent: true })
    if (res.code !== '200') {
      ElMessage.error(res.msg || '保存失败')
      return
    }
    const savedLesson = res.data || lessonForm
    // From here the lesson exists: a later retry must update it, never create a second one.
    if (savedLesson.id) lessonForm.id = savedLesson.id
    await loadChapters()
    if (lessonForm.resourceType === 'video_url') {
      try {
        const videoRes = await submitVideoUrl(savedLesson.id, lessonForm.resourceUrl, { silent: true })
        if (videoRes.code !== '200' || !videoRes.data) throw new Error(videoRes.msg || '视频地址未提交')
        lessonForm.videoResourceId = videoRes.data.id
        videoResources.value = new Map(videoResources.value).set(Number(videoRes.data.id), videoRes.data)
        syncVideoResourcePolling()
      } catch (error) {
        ElMessage.warning(`课时已保存，但视频没有进入下载队列：${failureText(error, '请检查视频地址')}。修改地址后再次保存即可。`)
        return
      }
    }
    ElMessage.success(lessonForm.resourceType === 'video_url' ? '保存成功，视频已进入下载队列，可在列表查看处理状态' : '保存成功')
    lessonDialogVisible.value = false
    inlineEditing.value = false
  } catch (error) {
    ElMessage.error(failureText(error, '保存失败'))
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
      if (inlineEditing.value && lessonForm.id === lesson.id) inlineEditing.value = false
      ElMessage.success('删除成功')
      loadChapters()
    }
  } catch {}
}

const loadPage = async () => {
  stopVideoResourcePolling()
  basicInfoVisible.value = false
  selectedLessonId.value = null
  inlineEditing.value = false
  course.value = {}
  chapters.value = []
  activeChapters.value = []
  videoResources.value = new Map()
  contentFailed.value = false
  // The instance also sees navigation away from this route; that must load nothing.
  if (!route.params.id) return
  await loadCourse()
  if (setupStep.value || userStore.roleCode === 'admin') return
  loadPublicCourseState()
  loadTemplateLibrary()
  loadChapters()
}

const buildLessonPayload = () => {
  const payload = { ...lessonForm }
  if (payload.resourceType === 'markdown') {
    const page = inlineEditing.value ? parseLessonJson(payload.content) : null
    const firstMarkdown = page?.blocks?.find(block => !block.type || block.type === 'markdown')
    if (firstMarkdown) {
      firstMarkdown.content = markdownDraft.value
      firstMarkdown.title = lessonForm.title
      page.title = lessonForm.title
      payload.content = JSON.stringify(page, null, 2)
    } else payload.content = JSON.stringify(markdownPagePayload(), null, 2)
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
  if (lessonForm.resourceType === 'video_url' && !/^https?:\/\//i.test(lessonForm.resourceUrl.trim())) {
    return '视频 URL 需以 http:// 或 https:// 开头'
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
.course-detail {
  gap: 0;
  padding-bottom: 100px;
}
.course-detail > .content-panel[aria-label='课程信息'] {
  margin-bottom: 24px;
}
.course-detail > .error-notice {
  margin-bottom: 20px;
}
.course-workspace-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin: 8px 0 28px;
}
.course-heading-copy {
  min-width: 0;
}
.course-eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--text-subtle);
}
.course-heading-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}
.course-heading-title h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.4;
  letter-spacing: -0.7px;
  overflow-wrap: anywhere;
}
.editable-course-title {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
  font: inherit;
  text-align: left;
  overflow-wrap: anywhere;
}
.editable-course-title .el-icon {
  font-size: 16px;
  color: var(--text-subtle);
  flex-shrink: 0;
}
.editable-course-title:hover {
  color: var(--accent);
}
.editable-course-title:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 5px;
  border-radius: 4px;
}
.course-description {
  margin: 9px 0 0;
  color: var(--text-body);
  font-size: 13px;
  line-height: 1.8;
  max-width: 75ch;
}
.course-metadata {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--text-subtle);
  font-size: 12px;
  margin: 12px 0 0;
}
.course-heading-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
  padding-top: 25px;
}
.course-heading-actions .el-button + .el-button {
  margin-left: 0;
}
.course-workspace-tabs {
  margin-bottom: 24px;
}
.course-workspace-tabs :deep(.el-tabs__item) {
  font-size: 14px;
  padding-block: 12px;
  height: 52px;
}
.course-workspace-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
[data-testid='course-material-tabs'] {
  margin-bottom: 24px;
}
[data-testid='course-material-tabs'] :deep(.el-tabs__nav-wrap::after) {
  display: none;
}
[data-testid='course-material-tabs'] :deep(.el-tabs__active-bar) {
  display: none;
}
[data-testid='course-material-tabs'] :deep(.el-tabs__nav) {
  display: flex;
  gap: 8px;
  padding: 6px;
  background: var(--surface-sunken);
  border-radius: 12px;
}
[data-testid='course-material-tabs'] :deep(.el-tabs__item) {
  min-width: 136px;
  justify-content: center;
  padding: 0 24px;
  height: 44px;
  font-size: 14px;
  border-radius: 8px;
}
[data-testid='course-material-tabs'] :deep(.el-tabs__item.is-active) {
  background: var(--surface-base);
  box-shadow: 0 1px 5px #2030530a;
}
.course-content-panel {
  padding: 0;
  overflow: hidden;
}
.course-content-panel > .section-head {
  padding: 22px 26px;
  margin: 0;
  border-bottom: 1px solid var(--line-default);
}
.course-content-panel > .section-head h2 {
  font-size: 16px;
}
.course-content-panel > .section-head p {
  font-size: 12px;
}
.course-content-workspace {
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr);
  height: min(760px, calc(100dvh - 340px));
  min-height: 480px;
  overflow: hidden;
}
.course-directory {
  min-height: 0;
  overflow: auto;
  padding: 22px 16px;
  background: var(--surface-canvas);
  border-right: 1px solid var(--line-default);
}
.directory-label {
  color: var(--text-subtle);
  font-size: 12px;
  padding: 0 10px;
  margin: 0 0 17px;
}
.directory-chapter + .directory-chapter {
  margin-top: 14px;
}
.directory-chapter-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 6px;
  padding: 8px 6px 8px 10px;
}
.directory-chapter-heading strong {
  font-size: 13px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.directory-chapter-heading :deep(.el-button) {
  padding: 2px 5px;
  min-height: 24px;
}
.directory-lesson-row { display: flex; align-items: center; border-radius: 7px; }
.directory-lesson-row.selected { background: var(--surface-accent); }
.directory-lesson-row .directory-lesson { flex: 1; min-width: 0; }
.directory-delete { flex-shrink: 0; color: var(--text-subtle); margin-right: 3px; }
.directory-delete:hover { color: var(--critical); }
.directory-add-chapter { margin-top: 16px; width: 100%; justify-content: flex-start; color: var(--accent); font-size: 12px; }
.inline-lesson-editor { min-width: 0; }
.inline-markdown-editor { height: 520px; max-height: 70vh; }
.inline-lesson-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 16px; }
.directory-lesson {
  display: flex;
  gap: 8px;
  width: 100%;
  background: transparent;
  color: var(--text-body);
  border: 0;
  border-radius: 7px;
  font: inherit;
  font-size: 12px;
  padding: 11px 12px;
  cursor: pointer;
  text-align: left;
  overflow-wrap: anywhere;
  line-height: 1.7;
}
.directory-lesson span {
  flex-shrink: 0;
  color: var(--text-subtle);
}
.directory-lesson:hover {
  background: var(--surface-sunken);
}
.directory-lesson-row.selected .directory-lesson {
  color: var(--accent);
  background: var(--surface-accent);
}
.directory-lesson-row.selected .directory-lesson span {
  color: var(--accent);
}
.course-directory .chapter-actions {
  padding: 5px 6px;
}
.course-directory .chapter-actions .el-button {
  font-size: 12px;
  color: var(--text-subtle);
}
.course-lesson-preview {
  padding: 28px 36px 36px;
  min-width: 0;
  min-height: 0;
  overflow: auto;
}
.lesson-preview-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 23px;
}
.lesson-preview-toolbar > span {
  font-size: 12px;
  color: var(--text-subtle);
}
.lesson-preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.course-lesson-preview > h2 {
  font-size: 25px;
  margin: 0 0 23px;
  overflow-wrap: anywhere;
}
.lesson-preview-markdown {
  font-size: 14px;
  line-height: 1.95;
  color: var(--text-body);
  overflow-wrap: anywhere;
}
.lesson-preview-markdown :deep(h2),
.lesson-preview-markdown :deep(h3) {
  margin: 24px 0 12px;
  color: var(--text-strong);
}
.lesson-preview-markdown :deep(img) {
  max-width: 100%;
}
.lesson-preview-markdown :deep(pre) {
  white-space: pre-wrap;
  padding: 16px;
  background: var(--surface-sunken);
  border-radius: 8px;
}
.lesson-preview-markdown :deep(table) {
  display: block;
  max-width: 100%;
  overflow: auto;
}
.lesson-preview-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid var(--line-subtle);
}
.lesson-preview-footer .el-button + .el-button {
  margin-left: 0;
}
.lesson-resource-status {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.lesson-resource-summary {
  color: var(--text-subtle);
  min-height: 150px;
  font-size: 13px;
  line-height: 1.9;
}
.empty-lesson-preview {
  display: grid;
  place-items: center;
}
@media (max-width: 900px) {
  .course-content-workspace {
    grid-template-columns: 220px minmax(0, 1fr);
  }
  .course-lesson-preview {
    padding: 24px;
  }
}
@media (max-width: 680px) {
  [data-testid='course-material-tabs'] :deep(.el-tabs__nav) {
    width: 100%;
    gap: 4px;
    padding: 4px;
  }
  [data-testid='course-material-tabs'] :deep(.el-tabs__item) {
    flex: 1;
    min-width: 0;
    padding-inline: 12px;
    font-size: 13px;
  }
  .course-workspace-heading {
    flex-direction: column;
    gap: 14px;
  }
  .course-heading-title h1 {
    font-size: 23px;
  }
  .course-heading-actions {
    padding-top: 0;
    flex-wrap: wrap;
  }
  .course-content-workspace {
    display: block;
    height: auto;
    overflow: visible;
  }
  .course-directory {
    border-right: 0;
    border-bottom: 1px solid var(--line-default);
    max-height: 340px;
    overflow: auto;
  }
  .course-content-panel > .section-head {
    padding: 18px;
  }
  .course-lesson-preview {
    padding: 22px 18px;
    overflow: visible;
  }
  .lesson-preview-toolbar {
    flex-wrap: wrap;
  }
  .course-lesson-preview > h2 {
    font-size: 21px;
  }
}
.student-course-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  margin-top: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--line-accent);
  border-radius: var(--radius-lg);
  background: var(--surface-accent);
}

.student-course-actions > div {
  display: grid;
  gap: 2px;
}

.student-course-actions strong {
  color: var(--text-strong);
}

.student-course-actions span {
  color: var(--text-body);
  font-size: var(--text-sm);
}

@media (max-width: 680px) {
  .student-course-actions {
    align-items: stretch;
    flex-direction: column;
  }
}

.chapter-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  width: 100%;
  min-width: 0;
  padding-right: var(--space-4);
  color: var(--text-strong);
  font-weight: 600;
}

.chapter-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}

.chapter-actions .el-button + .el-button {
  margin-left: 0;
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
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.template-preview {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  padding: var(--space-3);
  border: 1px solid var(--line-accent);
  border-radius: var(--radius-md);
  background: var(--surface-accent);
}

.template-preview span {
  color: var(--text-body);
  line-height: 1.5;
}

.text-muted {
  color: var(--text-subtle);
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
  color: var(--text-body);
  font-weight: 600;
}

.status-time,
.status-note,
.status-extra {
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.status-error {
  color: var(--critical);
  font-size: var(--text-xs);
  line-height: 1.5;
}
</style>
