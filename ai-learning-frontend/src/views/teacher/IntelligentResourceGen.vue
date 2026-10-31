<template>
  <div class="preparation-page">
    <div class="page-heading">
      <div>
        <span class="eyebrow">AI COURSE STUDIO</span>
        <h2>智能备课工作台</h2>
        <p>先生成草稿、逐阶段审核，最后一次性写入课程、课时与题库。</p>
      </div>
      <div class="draft-switcher">
        <el-select v-model="selectedDraftId" clearable filterable placeholder="继续最近的备课草稿" @change="resumeDraft">
          <el-option
            v-for="draft in recentDrafts"
            :key="draft.id"
            :label="`${draft.title} · ${stageLabel(draft.currentStage)}`"
            :value="draft.id"
          />
        </el-select>
        <el-button :icon="Refresh" @click="loadRecentDrafts">刷新</el-button>
        <el-button @click="startNewDraft">新备课</el-button>
      </div>
    </div>

    <el-alert
      v-if="project?.generationStatus === 'FAILED'"
      :title="project.errorMessage || '生成失败，可保留当前草稿并重试本阶段'"
      type="error"
      show-icon
      :closable="false"
      class="status-alert"
    />

    <el-card class="workspace-card" shadow="never">
      <el-steps :active="activeStep" finish-status="success" align-center>
        <el-step title="课程与资料" description="确定生成依据" />
        <el-step title="章节骨架" description="审核课程结构" />
        <el-step title="课时 Markdown" description="审核教学正文" />
        <el-step title="章节题目" description="审核题库内容" />
        <el-step title="发布草稿" description="事务化写入" />
      </el-steps>

      <div v-if="isGenerating" class="generation-banner">
        <div>
          <strong>{{ generatingLabel }}</strong>
          <span>可以离开页面，生成进度会保存在草稿中。</span>
        </div>
        <el-progress :percentage="project?.progress || 0" :stroke-width="10" />
      </div>

      <section v-if="activeStep === 0" class="stage-panel config-stage">
        <div class="section-title">
          <div><span>STEP 01</span><h3>定义新课程</h3></div>
          <p>课程仅在最终发布时创建，当前填写内容都保存在智能备课草稿中。</p>
        </div>

        <el-form ref="configFormRef" :model="config" :rules="configRules" label-position="top">
          <div class="form-grid">
            <el-form-item label="课程名称" prop="title">
              <el-input v-model="config.title" maxlength="200" show-word-limit placeholder="例如：数据结构与算法基础" />
            </el-form-item>
            <el-form-item label="课程分类" prop="categoryId">
              <el-select v-model="config.categoryId" filterable placeholder="选择课程分类">
                <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标对象" prop="audience">
              <el-input v-model="config.audience" placeholder="例如：计算机专业大二本科生" />
            </el-form-item>
            <el-form-item label="课程难度">
              <el-select v-model="config.difficulty">
                <el-option label="初级" value="beginner" />
                <el-option label="中级" value="intermediate" />
                <el-option label="高级" value="advanced" />
              </el-select>
            </el-form-item>
            <el-form-item label="总学时">
              <el-input-number v-model="config.totalHours" :min="1" :max="1000" />
            </el-form-item>
            <el-form-item label="期望章节数">
              <el-input-number v-model="config.chapterCount" :min="1" :max="20" />
            </el-form-item>
            <el-form-item label="每章课时数">
              <el-input-number v-model="config.lessonsPerChapter" :min="1" :max="10" />
            </el-form-item>
            <el-form-item label="每章题目数">
              <el-input-number v-model="config.questionConfig.countPerChapter" :min="1" :max="20" />
            </el-form-item>
          </div>
          <el-form-item label="课程简介">
            <el-input v-model="config.description" type="textarea" :rows="2" placeholder="课程定位与主要内容" />
          </el-form-item>
          <el-form-item label="教学目标">
            <el-input v-model="config.teachingGoals" type="textarea" :rows="3" placeholder="学生完成课程后应掌握的知识与能力" />
          </el-form-item>
          <el-form-item label="备课补充要求">
            <el-input v-model="config.extraInstructions" type="textarea" :rows="3" placeholder="例如：案例驱动，包含代码示例与课堂活动" />
          </el-form-item>

          <div class="subsection">
            <div class="subsection-heading"><h4>题目生成设置</h4><span>题目将在课时完成后按章节生成</span></div>
            <el-form-item label="题型">
              <el-checkbox-group v-model="config.questionConfig.types">
                <el-checkbox v-for="item in questionTypes" :key="item.value" :label="item.value">{{ item.label }}</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <div class="form-grid two-columns">
              <el-form-item label="默认难度">
                <el-select v-model="config.questionConfig.difficulty">
                  <el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /><el-option label="困难" value="HARD" />
                </el-select>
              </el-form-item>
              <el-form-item label="题目补充要求">
                <el-input v-model="config.questionConfig.instructions" placeholder="例如：侧重理解与综合应用" />
              </el-form-item>
            </div>
          </div>

          <div class="subsection source-section">
            <div class="subsection-heading"><h4>参考资料与知识库</h4><span>均为可选；不提供资料时按表单信息生成</span></div>
            <el-form-item label="已有知识库">
              <el-select v-model="config.knowledgeBaseIds" multiple filterable collapse-tags placeholder="选择一个或多个已有知识库">
                <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name || kb.title || kb.id" :value="String(kb.id)" />
              </el-select>
            </el-form-item>
            <el-upload
              :auto-upload="false"
              :file-list="uploadFileList"
              :on-change="handleUploadChange"
              :on-remove="handleUploadRemove"
              multiple
              accept=".pdf,.ppt,.pptx,.doc,.docx,.txt,.md"
            >
              <el-button :icon="Upload">选择教材或课件</el-button>
              <template #tip><div class="el-upload__tip">首次上传时会自动创建本备课项目的专属知识库。</div></template>
            </el-upload>
            <div v-if="project?.sourceFiles?.length" class="source-status-list">
              <div v-for="file in project.sourceFiles" :key="file.knowledgeId || file.name">
                <span>{{ file.name }}</span>
                <el-tag size="small" :type="file.parseStatus === 'success' ? 'success' : file.parseStatus === 'failed' ? 'danger' : 'warning'">
                  {{ sourceStatusLabel(file.parseStatus) }} {{ file.parseProgress || 0 }}%
                </el-tag>
              </div>
              <el-button size="small" link type="primary" @click="refreshProject">刷新解析状态</el-button>
            </div>
          </div>
        </el-form>

        <div class="stage-actions">
          <el-button type="primary" size="large" :loading="submitting" :disabled="isGenerating" @click="saveConfigAndGenerateOutline">
            <el-icon><Lightning /></el-icon> 保存并生成章节骨架
          </el-button>
        </div>
      </section>

      <section v-else-if="activeStep === 1" class="stage-panel">
        <div class="section-title inline-title">
          <div><span>STEP 02</span><h3>审核章节与课时骨架</h3></div>
          <el-button :icon="Plus" @click="addChapter">添加章节</el-button>
        </div>
        <div v-if="outline.chapters.length" class="outline-list">
          <el-card v-for="(chapter, chapterIndex) in outline.chapters" :key="chapter.chapterKey" class="chapter-card" shadow="never">
            <div class="chapter-editor">
              <span class="order-badge">{{ chapterIndex + 1 }}</span>
              <el-input v-model="chapter.title" class="chapter-title-input" placeholder="章节名称" />
              <el-button text type="danger" :icon="Delete" @click="removeChapter(chapterIndex)">删除章节</el-button>
            </div>
            <el-input v-model="chapter.description" type="textarea" :rows="2" placeholder="章节说明" />
            <div class="lesson-skeleton-list">
              <div v-for="(lesson, lessonIndex) in chapter.lessons" :key="lesson.lessonKey" class="lesson-skeleton">
                <span>{{ chapterIndex + 1 }}.{{ lessonIndex + 1 }}</span>
                <el-input v-model="lesson.title" placeholder="课时名称" />
                <el-input v-model="lesson.objectives" placeholder="课时目标" />
                <el-input-number v-model="lesson.durationMinutes" :min="10" :max="240" />
                <el-button text type="danger" :icon="Delete" @click="removeLesson(chapter, lessonIndex)" />
              </div>
              <el-button text type="primary" :icon="Plus" @click="addLesson(chapter)">添加课时</el-button>
            </div>
          </el-card>
        </div>
        <el-empty v-else description="尚未生成章节骨架" />
        <div class="stage-actions between-actions">
          <el-button @click="activeStep = 0">返回配置</el-button>
          <div><el-button @click="retryOutline">重新生成</el-button><el-button type="primary" :loading="submitting" @click="approveOutlineAndGenerateLessons">确认骨架并生成课时</el-button></div>
        </div>
      </section>

      <section v-else-if="activeStep === 2" class="stage-panel lesson-stage">
        <div class="section-title"><div><span>STEP 03</span><h3>审核课时 Markdown</h3></div><p>逐课时检查正文；所有课时均需保留有效内容。</p></div>
        <el-tabs v-if="lessons.length" v-model="activeLessonKey" tab-position="left" class="lesson-tabs">
          <el-tab-pane v-for="lesson in lessons" :key="lesson.lessonKey" :name="lesson.lessonKey" :label="lesson.title">
            <div class="lesson-editor-heading">
              <el-input v-model="lesson.title" placeholder="课时名称" />
              <el-tag>{{ lesson.durationMinutes || 45 }} 分钟</el-tag>
            </div>
            <MdEditor v-model="lesson.contentMd" language="zh-CN" :toolbars-exclude="['github']" class="markdown-editor" />
          </el-tab-pane>
        </el-tabs>
        <el-empty v-else description="尚未生成课时内容" />
        <div class="stage-actions between-actions">
          <el-button @click="activeStep = 1">返回骨架</el-button>
          <div><el-button @click="retryLessons">重新生成全部课时</el-button><el-button type="primary" :loading="submitting" @click="approveLessonsAndGenerateQuestions">确认课时并生成题目</el-button></div>
        </div>
      </section>

      <section v-else-if="activeStep === 3" class="stage-panel">
        <div class="section-title"><div><span>STEP 04</span><h3>审核章节题目</h3></div><p>题目最终统一关联新课程，章节分组只用于本次审核。</p></div>
        <el-collapse v-if="questionGroups.length" v-model="openQuestionGroups">
          <el-collapse-item v-for="group in questionGroups" :key="group.chapterKey" :name="group.chapterKey">
            <template #title><strong>{{ group.chapterTitle }}</strong><el-tag size="small" class="question-count">{{ group.questions.length }} 题</el-tag></template>
            <el-table :data="group.questions" border stripe>
              <el-table-column prop="type" label="题型" width="130"><template #default="{ row }">{{ typeLabel(row.type) }}</template></el-table-column>
              <el-table-column prop="content" label="题干" min-width="320" show-overflow-tooltip />
              <el-table-column prop="difficulty" label="难度" width="90" />
              <el-table-column prop="knowledgePoint" label="知识点" width="160" show-overflow-tooltip />
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row, $index }"><el-button size="small" @click="editQuestion(group, row)">编辑</el-button><el-button size="small" type="danger" @click="group.questions.splice($index, 1)">删除</el-button></template>
              </el-table-column>
            </el-table>
            <el-button text type="primary" :icon="Plus" @click="addQuestion(group)">添加题目</el-button>
          </el-collapse-item>
        </el-collapse>
        <el-empty v-else description="尚未生成题目" />
        <div class="stage-actions between-actions">
          <el-button @click="activeStep = 2">返回课时</el-button>
          <div><el-button @click="retryQuestions">重新生成全部章节题目</el-button><el-button type="primary" :loading="submitting" @click="approveQuestions">确认题目并进入发布</el-button></div>
        </div>
      </section>

      <section v-else class="stage-panel publish-stage">
        <div v-if="project?.projectStatus !== 'PUBLISHED'" class="publish-summary">
          <el-icon><Check /></el-icon>
          <h3>备课内容已审核完成</h3>
          <p>发布时将一次创建课程草稿、章节、Markdown 课时和题库题目。任一项失败都会全部回滚。</p>
          <div class="summary-grid">
            <div><strong>{{ outline.chapters.length }}</strong><span>章节</span></div>
            <div><strong>{{ lessons.length }}</strong><span>课时</span></div>
            <div><strong>{{ totalQuestions }}</strong><span>题目</span></div>
            <div><strong>草稿</strong><span>课程状态</span></div>
          </div>
          <el-button type="primary" size="large" :loading="publishing" @click="publishCourse">发布到课程管理</el-button>
        </div>
        <el-result v-else icon="success" title="课程草稿已发布" :sub-title="`课程 ID：${project.publishedCourseId}`">
          <template #extra><el-button type="primary" @click="openPublishedCourse">查看课程详情</el-button><el-button @click="startNewDraft">继续新备课</el-button></template>
        </el-result>
      </section>
    </el-card>

    <el-dialog v-model="questionDialogVisible" :title="editingQuestionIsNew ? '添加题目' : '编辑题目'" width="760px">
      <el-form v-if="editingQuestion" label-position="top">
        <div class="form-grid two-columns">
          <el-form-item label="题型"><el-select v-model="editingQuestion.type" @change="resetQuestionShape"><el-option v-for="item in questionTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="难度"><el-select v-model="editingQuestion.difficulty"><el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /><el-option label="困难" value="HARD" /></el-select></el-form-item>
        </div>
        <el-form-item label="题干"><el-input v-model="editingQuestion.content" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="知识点"><el-input v-model="editingQuestion.knowledgePoint" maxlength="200" /></el-form-item>
        <el-form-item v-if="isChoiceQuestion(editingQuestion.type)" label="选项">
          <div class="option-editor"><div v-for="(option, index) in editingQuestion.options" :key="index"><el-input v-model="option.optionKey" class="option-key" /><el-input v-model="option.optionContent" /><el-button text type="danger" :icon="Delete" @click="editingQuestion.options.splice(index, 1)" /></div><el-button text type="primary" :icon="Plus" @click="addOption">添加选项</el-button></div>
        </el-form-item>
        <el-form-item label="答案"><el-input v-model="editingAnswerText" type="textarea" :rows="2" :placeholder="editingQuestion.type === 'MULTIPLE_CHOICE' ? '每行一个正确选项，例如 A' : '输入答案；选择题填写选项字母'" /></el-form-item>
        <el-form-item label="解析"><el-input v-model="editingQuestion.analysis" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="questionDialogVisible = false">取消</el-button><el-button type="primary" @click="saveQuestionEdit">保存题目</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Delete, Lightning, Plus, Refresh, Upload } from '@element-plus/icons-vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { getCategories } from '@/utils/course.js'
import { listKnowledgeBases } from '@/api/knowledgeBase.js'
import {
  createPreparation, generatePreparationLessons, generatePreparationOutline, generatePreparationQuestions,
  getPreparation, listPreparations, publishPreparation, savePreparationLessons, savePreparationOutline,
  savePreparationQuestions, updatePreparationConfig, uploadPreparationSources
} from '@/api/preparation.js'

const router = useRouter()
const configFormRef = ref()
const activeStep = ref(0)
const project = ref(null)
const recentDrafts = ref([])
const selectedDraftId = ref(null)
const categories = ref([])
const knowledgeBases = ref([])
const uploadFiles = ref([])
const uploadFileList = ref([])
const outline = reactive({ courseDescription: '', chapters: [] })
const lessons = ref([])
const questionGroups = ref([])
const activeLessonKey = ref('')
const openQuestionGroups = ref([])
const submitting = ref(false)
const publishing = ref(false)
let pollTimer = null
let lastShownGenerationErrorKey = ''

const defaultConfig = () => ({
  title: '', categoryId: null, description: '', difficulty: 'beginner', audience: '', totalHours: 32,
  chapterCount: 6, lessonsPerChapter: 3, teachingGoals: '', extraInstructions: '', knowledgeBaseIds: [],
  questionConfig: { countPerChapter: 5, types: ['SINGLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER'], difficulty: 'MEDIUM', instructions: '' }
})
const config = reactive(defaultConfig())
const configRules = {
  title: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择课程分类', trigger: 'change' }],
  audience: [{ required: true, message: '请输入目标对象', trigger: 'blur' }]
}
const questionTypes = [
  { label: '单选题', value: 'SINGLE_CHOICE' }, { label: '多选题', value: 'MULTIPLE_CHOICE' },
  { label: '判断题', value: 'TRUE_FALSE' }, { label: '填空题', value: 'FILL_BLANK' }, { label: '简答题', value: 'SHORT_ANSWER' }
]

const isGenerating = computed(() => project.value?.generationStatus === 'RUNNING')
const generatingLabel = computed(() => {
  const stage = project.value?.currentStage
  if (stage === 'CONFIG') return '正在设计课程骨架'
  if (stage === 'OUTLINE_REVIEW') return '正在生成课时 Markdown'
  return '正在按章节生成题目'
})
const totalQuestions = computed(() => questionGroups.value.reduce((sum, group) => sum + (group.questions?.length || 0), 0))

function records(data) { return Array.isArray(data) ? data : data?.records || data?.items || data?.list || [] }
function newKey(prefix) { return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}` }
function stageLabel(stage) {
  return ({ CONFIG: '课程配置', OUTLINE_REVIEW: '审核骨架', LESSON_REVIEW: '审核课时', QUESTION_REVIEW: '审核题目', READY_TO_PUBLISH: '待发布', PUBLISHED: '已发布' })[stage] || stage
}
function stepFromStage(stage) {
  return ({ CONFIG: 0, OUTLINE_REVIEW: 1, LESSON_REVIEW: 2, QUESTION_REVIEW: 3, READY_TO_PUBLISH: 4, PUBLISHED: 4 })[stage] ?? 0
}
function sourceStatusLabel(status) { return ({ success: '解析完成', failed: '解析失败', parsing: '解析中', pending: '等待解析' })[status] || '等待解析' }
function typeLabel(type) { return questionTypes.find(item => item.value === type)?.label || type }

async function loadInitialData() {
  const [categoryRes, kbRes] = await Promise.allSettled([getCategories(), listKnowledgeBases()])
  if (categoryRes.status === 'fulfilled' && categoryRes.value.code === '200') categories.value = records(categoryRes.value.data)
  if (kbRes.status === 'fulfilled' && kbRes.value.code === '200') knowledgeBases.value = records(kbRes.value.data)
  await loadRecentDrafts()
}
async function loadRecentDrafts() {
  try { const res = await listPreparations(); if (res.code === '200') recentDrafts.value = records(res.data).slice(0, 20) } catch {}
}
async function resumeDraft(id) {
  if (!id) return
  const res = await getPreparation(id)
  if (res.code === '200') applyProject(res.data)
}
function applyProject(data) {
  project.value = data
  selectedDraftId.value = data.id
  Object.assign(config, defaultConfig(), data.config || {})
  config.questionConfig = { ...defaultConfig().questionConfig, ...(data.config?.questionConfig || {}) }
  config.knowledgeBaseIds = [...(data.knowledgeBaseIds || data.config?.knowledgeBaseIds || [])]
  Object.assign(outline, { courseDescription: data.outline?.courseDescription || '', chapters: structuredClone(data.outline?.chapters || []) })
  lessons.value = structuredClone(data.lessons || [])
  questionGroups.value = structuredClone(data.questions || [])
  activeLessonKey.value = lessons.value[0]?.lessonKey || ''
  openQuestionGroups.value = questionGroups.value.map(group => group.chapterKey)
  activeStep.value = stepFromStage(data.currentStage)
  if (data.generationStatus === 'RUNNING') {
    lastShownGenerationErrorKey = ''
    schedulePoll()
  }
}
function startNewDraft() {
  stopPoll(); project.value = null; selectedDraftId.value = null; activeStep.value = 0
  lastShownGenerationErrorKey = ''
  Object.assign(config, defaultConfig()); Object.assign(outline, { courseDescription: '', chapters: [] })
  lessons.value = []; questionGroups.value = []; uploadFiles.value = []; uploadFileList.value = []
}
async function refreshProject() {
  if (!project.value?.id) return
  const res = await getPreparation(project.value.id)
  if (res.code === '200') applyProject(res.data)
}
function schedulePoll() {
  stopPoll()
  pollTimer = window.setTimeout(async () => {
    try {
      const res = await getPreparation(project.value.id)
      if (res.code === '200') {
        applyProject(res.data)
        if (res.data.generationStatus === 'FAILED') {
          const message = res.data.errorMessage || '生成失败'
          const errorKey = `${res.data.id}:${res.data.updatedAt || ''}:${message}`
          if (errorKey !== lastShownGenerationErrorKey) {
            lastShownGenerationErrorKey = errorKey
            ElMessage.error(message)
          }
        }
      }
    } catch { schedulePoll() }
  }, 1600)
}
function stopPoll() { if (pollTimer) window.clearTimeout(pollTimer); pollTimer = null }

function handleUploadChange(file) {
  if (file.raw && !uploadFiles.value.some(item => item.uid === file.uid)) uploadFiles.value.push(file.raw)
  uploadFileList.value = uploadFiles.value.map((raw, index) => ({ name: raw.name, uid: index, raw }))
}
function handleUploadRemove(file) {
  uploadFiles.value = uploadFiles.value.filter(raw => raw !== file.raw && raw.name !== file.name)
  uploadFileList.value = uploadFiles.value.map((raw, index) => ({ name: raw.name, uid: index, raw }))
}
async function saveConfigAndGenerateOutline() {
  try { await configFormRef.value.validate() } catch { return }
  if (!config.questionConfig.types.length) return ElMessage.warning('请至少选择一种题型')
  submitting.value = true
  try {
    let res = project.value?.id ? await updatePreparationConfig(project.value.id, config) : await createPreparation(config)
    if (res.code !== '200') return
    applyProject(res.data)
    if (uploadFiles.value.length) {
      res = await uploadPreparationSources(project.value.id, uploadFiles.value)
      if (res.code !== '200') return
      applyProject(res.data); uploadFiles.value = []; uploadFileList.value = []
      if (!res.data.sourcesReady) return ElMessage.warning('资料已上传，待解析完成后刷新状态并继续生成')
    }
    if (project.value.sourceFiles?.length && !project.value.sourcesReady) return ElMessage.warning('资料仍在解析，请稍后刷新状态')
    res = await generatePreparationOutline(project.value.id)
    if (res.code === '200') { applyProject(res.data); schedulePoll() }
  } finally { submitting.value = false; loadRecentDrafts() }
}

function addChapter() { outline.chapters.push({ chapterKey: newKey('chapter'), title: '新章节', description: '', lessons: [{ lessonKey: newKey('lesson'), title: '新课时', objectives: '', knowledgePoints: [], durationMinutes: 45 }] }) }
function removeChapter(index) { if (outline.chapters.length <= 1) return ElMessage.warning('至少保留一个章节'); outline.chapters.splice(index, 1) }
function addLesson(chapter) { chapter.lessons.push({ lessonKey: newKey('lesson'), title: '新课时', objectives: '', knowledgePoints: [], durationMinutes: 45 }) }
function removeLesson(chapter, index) { if (chapter.lessons.length <= 1) return ElMessage.warning('每章至少保留一个课时'); chapter.lessons.splice(index, 1) }
async function retryOutline() { const res = await generatePreparationOutline(project.value.id); if (res.code === '200') { applyProject(res.data); schedulePoll() } }
async function approveOutlineAndGenerateLessons() {
  if (!outline.chapters.length || outline.chapters.some(chapter => !chapter.title?.trim() || !chapter.lessons?.length || chapter.lessons.some(lesson => !lesson.title?.trim()))) return ElMessage.warning('请补全章节和课时名称')
  submitting.value = true
  try {
    let res = await savePreparationOutline(project.value.id, outline)
    if (res.code !== '200') return
    res = await generatePreparationLessons(project.value.id)
    if (res.code === '200') { applyProject(res.data); schedulePoll() }
  } finally { submitting.value = false }
}
async function retryLessons() { const res = await generatePreparationLessons(project.value.id); if (res.code === '200') { applyProject(res.data); schedulePoll() } }
async function approveLessonsAndGenerateQuestions() {
  if (!lessons.value.length || lessons.value.some(lesson => !lesson.contentMd?.trim())) return ElMessage.warning('请确保每个课时都有 Markdown 内容')
  submitting.value = true
  try {
    let res = await savePreparationLessons(project.value.id, lessons.value)
    if (res.code !== '200') return
    res = await generatePreparationQuestions(project.value.id)
    if (res.code === '200') { applyProject(res.data); schedulePoll() }
  } finally { submitting.value = false }
}
async function retryQuestions() { const res = await generatePreparationQuestions(project.value.id); if (res.code === '200') { applyProject(res.data); schedulePoll() } }
async function approveQuestions() {
  if (!questionGroups.value.length || questionGroups.value.some(group => !group.questions?.length)) return ElMessage.warning('每个章节至少保留一道题')
  submitting.value = true
  try { const res = await savePreparationQuestions(project.value.id, questionGroups.value); if (res.code === '200') applyProject(res.data) }
  finally { submitting.value = false }
}

const questionDialogVisible = ref(false)
const editingQuestion = ref(null)
const editingQuestionGroup = ref(null)
const editingQuestionIndex = ref(-1)
const editingQuestionIsNew = ref(false)
const editingAnswerText = ref('')
function isChoiceQuestion(type) { return ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(type) }
function editQuestion(group, question) {
  editingQuestionGroup.value = group; editingQuestionIndex.value = group.questions.indexOf(question); editingQuestionIsNew.value = false; editingQuestion.value = structuredClone(question)
  editingQuestion.value.options ||= []; editingAnswerText.value = (editingQuestion.value.answers || []).map(item => item.answerContent).join('\n'); questionDialogVisible.value = true
}
function addQuestion(group) {
  editingQuestionGroup.value = group; editingQuestionIndex.value = -1; editingQuestionIsNew.value = true
  editingQuestion.value = { content: '', type: 'SHORT_ANSWER', difficulty: 'MEDIUM', knowledgePoint: group.chapterTitle, analysis: '', options: [], answers: [] }
  editingAnswerText.value = ''; questionDialogVisible.value = true
}
function resetQuestionShape(type) {
  if (type === 'TRUE_FALSE') editingQuestion.value.options = [{ optionKey: 'A', optionContent: '正确', sortOrder: 1 }, { optionKey: 'B', optionContent: '错误', sortOrder: 2 }]
  else if (['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(type) && !editingQuestion.value.options.length) editingQuestion.value.options = ['A', 'B', 'C', 'D'].map((key, index) => ({ optionKey: key, optionContent: '', sortOrder: index + 1 }))
  else if (!isChoiceQuestion(type)) editingQuestion.value.options = []
}
function addOption() { const index = editingQuestion.value.options.length; editingQuestion.value.options.push({ optionKey: String.fromCharCode(65 + index), optionContent: '', sortOrder: index + 1 }) }
function saveQuestionEdit() {
  const question = editingQuestion.value
  if (!question.content?.trim()) return ElMessage.warning('请输入题干')
  const answers = editingAnswerText.value.split(/\r?\n|,/).map(item => item.trim()).filter(Boolean).map((answerContent, index) => ({ answerContent, sortOrder: index + 1 }))
  if (!answers.length) return ElMessage.warning('请输入答案')
  question.answers = answers
  question.options = isChoiceQuestion(question.type) ? (question.options || []).filter(item => item.optionContent?.trim()).map((item, index) => ({ ...item, sortOrder: index + 1 })) : []
  if (isChoiceQuestion(question.type) && question.options.length < 2) return ElMessage.warning('选择题至少需要两个选项')
  if (editingQuestionIsNew.value) editingQuestionGroup.value.questions.push(question)
  else if (editingQuestionIndex.value >= 0) editingQuestionGroup.value.questions.splice(editingQuestionIndex.value, 1, question)
  questionDialogVisible.value = false
}

async function publishCourse() {
  publishing.value = true
  try {
    const res = await publishPreparation(project.value.id)
    if (res.code === '200') { await refreshProject(); ElMessage.success(`课程草稿已发布：${res.data.chaptersCreated}章 / ${res.data.lessonsCreated}课时 / ${res.data.questionsCreated}题`) }
  } finally { publishing.value = false }
}
function openPublishedCourse() { router.push(`/courses/${project.value.publishedCourseId}`) }

onMounted(loadInitialData)
onBeforeUnmount(stopPoll)
</script>

<style scoped>
.preparation-page { max-width: 1480px; margin: 0 auto; }
.page-heading { display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; margin-bottom: 22px; }
.eyebrow, .section-title span { color: var(--primary-color); font-size: 12px; font-weight: 800; letter-spacing: .14em; }
.page-heading h2 { margin: 5px 0 6px; font-size: 30px; color: var(--text-primary); }
.page-heading p, .section-title p { margin: 0; color: var(--text-secondary); }
.draft-switcher { display: flex; gap: 10px; align-items: center; }
.draft-switcher .el-select { width: 300px; }
.workspace-card { border: 1px solid #e8edf5; border-radius: 18px; }
.status-alert { margin-bottom: 16px; }
.stage-panel { margin-top: 34px; min-height: 520px; padding: 8px 10px 0; }
.section-title { display: flex; justify-content: space-between; align-items: flex-end; gap: 20px; margin-bottom: 24px; }
.section-title h3 { margin: 5px 0 0; font-size: 23px; }
.inline-title { align-items: center; }
.form-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0 18px; }
.form-grid.two-columns { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.subsection { margin-top: 18px; padding: 20px; border: 1px solid #e8edf5; border-radius: 14px; background: #fafcff; }
.subsection-heading { display: flex; align-items: baseline; gap: 12px; margin-bottom: 18px; }
.subsection-heading h4 { margin: 0; font-size: 17px; }.subsection-heading span { color: var(--text-secondary); font-size: 13px; }
.source-status-list { margin-top: 14px; padding: 12px; background: white; border-radius: 10px; }
.source-status-list > div { display: flex; justify-content: space-between; margin-bottom: 8px; }
.stage-actions { display: flex; justify-content: flex-end; padding: 28px 0 8px; }
.between-actions { justify-content: space-between; }.between-actions > div { display: flex; gap: 10px; }
.generation-banner { margin: 26px 0 0; padding: 16px 20px; border-radius: 12px; background: #eff6ff; display: grid; grid-template-columns: minmax(250px, .7fr) 1.3fr; align-items: center; gap: 25px; }
.generation-banner strong, .generation-banner span { display: block; }.generation-banner span { margin-top: 4px; color: #64748b; font-size: 13px; }
.outline-list { display: grid; gap: 14px; }.chapter-card { border-radius: 14px; }
.chapter-editor { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }.chapter-title-input { max-width: 520px; }
.order-badge { width: 32px; height: 32px; display: grid; place-items: center; border-radius: 9px; color: white; background: var(--primary-color); font-weight: 700; }
.lesson-skeleton-list { margin-top: 14px; padding-left: 44px; }.lesson-skeleton { display: grid; grid-template-columns: 55px 1fr 1.3fr 130px 40px; gap: 10px; align-items: center; margin-bottom: 9px; }
.lesson-tabs { min-height: 590px; }.lesson-tabs :deep(.el-tabs__header) { width: 230px; }.lesson-editor-heading { display: flex; gap: 12px; align-items: center; margin-bottom: 14px; }.markdown-editor { height: 560px; }
.question-count { margin-left: 10px; }.publish-stage { display: grid; place-items: center; }.publish-summary { max-width: 760px; text-align: center; }.publish-summary > .el-icon { font-size: 64px; color: #16a34a; }.publish-summary h3 { font-size: 28px; margin: 14px 0 8px; }.publish-summary p { color: var(--text-secondary); line-height: 1.7; }
.summary-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin: 28px 0; }.summary-grid div { padding: 20px 12px; background: #f8fafc; border-radius: 12px; }.summary-grid strong, .summary-grid span { display: block; }.summary-grid strong { font-size: 25px; }.summary-grid span { margin-top: 5px; color: #64748b; }
.option-editor { width: 100%; }.option-editor > div { display: flex; gap: 8px; margin-bottom: 8px; }.option-key { width: 70px; }
@media (max-width: 1100px) { .form-grid { grid-template-columns: repeat(2, minmax(0,1fr)); }.page-heading { align-items: flex-start; flex-direction: column; }.lesson-skeleton { grid-template-columns: 45px 1fr 1fr; }.lesson-skeleton > :nth-child(n+4) { grid-column: auto; } }
@media (max-width: 720px) { .form-grid, .form-grid.two-columns { grid-template-columns: 1fr; }.draft-switcher { width: 100%; flex-wrap: wrap; }.draft-switcher .el-select { width: 100%; }.generation-banner { grid-template-columns: 1fr; }.summary-grid { grid-template-columns: repeat(2, 1fr); }.lesson-tabs :deep(.el-tabs__header) { width: 130px; } }
</style>
