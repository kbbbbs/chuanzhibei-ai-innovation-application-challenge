<template>
  <div class="recommendation-manage workspace-page">
    <PageHeader
      eyebrow="教学运营"
      title="首页推荐课程"
      description="设置首页公共课及展示优先级；学生可先查看课程详情，再自助加入该课程的启用教学班并开始学习。"
    >
      <template #actions>
        <el-button @click="openHomepage">查看首页</el-button>
        <el-button type="primary" @click="openCreate">添加推荐课程</el-button>
      </template>
    </PageHeader>

    <ErrorNotice
      v-if="loadFailed"
      title="推荐配置未加载"
      message="推荐或课程列表读取失败，请确认网络后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadData"
    />

    <div v-else class="workspace-grid">
      <el-card class="manage-card" shadow="never">
        <template #header>
          <div class="card-title">
            <div><strong>推荐配置</strong><small>共 {{ recommendations.length }} 门，启用 {{ activeCount }} 门</small></div>
            <el-button text :loading="loading" @click="loadData"><el-icon><Refresh /></el-icon>刷新</el-button>
          </div>
        </template>

        <el-table v-loading="loading" :data="recommendations" row-key="id" stripe>
          <el-table-column label="顺序" width="72" align="center">
            <template #default="{ row }"><span class="sort-pill">{{ row.sortOrder }}</span></template>
          </el-table-column>
          <el-table-column label="课程" min-width="240">
            <template #default="{ row }">
              <div class="course-cell">
                <strong>{{ row.courseTitle || `课程 #${row.courseId}` }}</strong>
                <small>ID {{ row.courseId }} · {{ row.teacher || '暂无教师信息' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="首页推荐理由" min-width="260" show-overflow-tooltip />
          <el-table-column label="课程状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.courseStatus === 1 ? 'success' : 'warning'" effect="plain">
                {{ row.courseStatus === 1 ? '已上架' : '未上架' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="首页展示" width="100" align="center">
            <template #default="{ row }">
              <el-switch :model-value="row.status === 1" :loading="savingId === row.id" :aria-label="`在首页展示 ${row.courseTitle || row.courseId}`" @change="toggleStatus(row, $event)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" :width="narrow ? ROW_SHEET_COLUMN_WIDTH : 150" fixed="right">
            <template #default="{ row }">
              <RowActions :label="row.courseTitle || `课程 #${row.courseId}`">
                <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button text type="danger" @click="removeItem(row)">删除</el-button>
              </RowActions>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="尚未配置手动推荐，首页会继续使用自动推荐">
              <el-button type="primary" @click="openCreate">添加第一门课程</el-button>
            </el-empty>
          </template>
        </el-table>
      </el-card>

      <el-card class="preview-card" shadow="never">
        <template #header>
          <div class="card-title"><div><strong>首页实时预览</strong><small>按启用状态和排序展示前 8 门</small></div></div>
        </template>
        <div class="preview-lane">
          <div class="preview-heading">
            <span><el-icon><Reading /></el-icon></span>
            <div><strong>推荐课程</strong><small>管理员精选学习方向</small></div>
            <em>{{ previewItems.length }}</em>
          </div>
          <article v-for="item in previewItems" :key="item.id">
            <span class="preview-badge">精选</span>
            <div><h3>{{ item.courseTitle }}</h3><p>{{ item.reason || item.courseDescription || '管理员精选课程' }}</p></div>
            <el-icon><ArrowRight /></el-icon>
          </article>
          <el-empty v-if="!previewItems.length" :image-size="72" description="暂无启用的手动推荐" />
        </div>
        <el-alert type="info" :closable="false" show-icon title="启用的推荐会作为公共课展示；课程必须有启用教学班，学生加入后即可进入学习页。" />
      </el-card>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑首页推荐' : '添加首页推荐'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="推荐课程" prop="courseId">
          <el-select v-model="form.courseId" filterable placeholder="输入课程名称搜索" class="full-width">
            <el-option
              v-for="course in selectableCourses"
              :key="course.id"
              :label="`${course.title}（ID ${course.id}）`"
              :value="course.id"
              :disabled="course.status !== 1"
            >
              <div class="course-option"><span>{{ course.title }}</span><small>{{ course.status === 1 ? '已上架' : '未上架' }}</small></div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="推荐理由" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="4" maxlength="255" show-word-limit placeholder="例如：从基础概念到项目实践，适合作为本周学习主线" />
        </el-form-item>
        <el-form-item label="展示顺序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="1" :max="999" />
          <span class="form-tip">数字越小越靠前，首页最多展示前 8 门。</span>
        </el-form-item>
        <el-form-item label="首页展示">
          <el-radio-group v-model="form.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Reading, Refresh } from '@element-plus/icons-vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import RowActions from '@/components/ui/RowActions.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { ROW_SHEET_COLUMN_WIDTH, useNarrowScreen } from '@/composables/useNarrowScreen'
import { requireSuccess } from '@/utils/apiHelpers'
import request from '@/utils/request'
import {
  createHomeCourseRecommendation,
  deleteHomeCourseRecommendation,
  getHomeCourseRecommendationsForAdmin,
  updateHomeCourseRecommendation
} from '@/api/homeCourseRecommendation'

const router = useRouter()
const narrow = useNarrowScreen()
const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  recommendations.value = []
  courses.value = []
})
const saving = ref(false)
const savingId = ref(null)
const dialogVisible = ref(false)
const formRef = ref(null)
const recommendations = ref([])
const courses = ref([])
const form = reactive({ id: null, courseId: null, reason: '', sortOrder: 1, status: 1 })
const rules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  reason: [{ required: true, message: '请填写首页推荐理由', trigger: 'blur' }],
  sortOrder: [{ required: true, message: '请设置展示顺序', trigger: 'change' }]
}

const activeCount = computed(() => recommendations.value.filter(item => item.status === 1 && item.courseStatus === 1).length)
const previewItems = computed(() => recommendations.value
  .filter(item => item.status === 1 && item.courseStatus === 1)
  .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
  .slice(0, 8))
const selectableCourses = computed(() => courses.value.filter(course => {
  const owner = recommendations.value.find(item => item.courseId === course.id)
  return !owner || owner.id === form.id
}))

function recordsOf(response) {
  const data = response?.data
  if (Array.isArray(data)) return data
  return Array.isArray(data?.records) ? data.records : []
}

function loadData() {
  return guarded(async (isCurrent) => {
    const [recommendationRes, courseRes] = await Promise.all([
      getHomeCourseRecommendationsForAdmin(),
      request.get('/courses', { params: { pageNum: 1, pageSize: 1000 } })
    ])
    if (!isCurrent()) return
    recommendations.value = recordsOf(requireSuccess(recommendationRes))
    courses.value = recordsOf(requireSuccess(courseRes))
  })
}

function resetForm(item = {}) {
  Object.assign(form, { id: null, courseId: null, reason: '', sortOrder: recommendations.value.length + 1, status: 1 }, item)
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm({ id: row.id, courseId: row.courseId, reason: row.reason || '', sortOrder: row.sortOrder, status: row.status })
  dialogVisible.value = true
}

async function saveItem() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = { courseId: form.courseId, reason: form.reason.trim(), sortOrder: form.sortOrder, status: form.status }
    const response = form.id
      ? await updateHomeCourseRecommendation(form.id, payload)
      : await createHomeCourseRecommendation(payload)
    if (response.code === '200') {
      ElMessage.success('首页推荐已保存')
      dialogVisible.value = false
      await loadData()
    }
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row, enabled) {
  savingId.value = row.id
  try {
    const response = await updateHomeCourseRecommendation(row.id, {
      courseId: row.courseId,
      reason: row.reason || '',
      sortOrder: row.sortOrder,
      status: enabled ? 1 : 0
    })
    if (response.code === '200') {
      row.status = enabled ? 1 : 0
      ElMessage.success(enabled ? '已在首页展示' : '已停止首页展示')
    }
  } finally {
    savingId.value = null
  }
}

async function removeItem(row) {
  try {
    await ElMessageBox.confirm(`确定移除“${row.courseTitle}”的首页推荐吗？`, '移除推荐', { type: 'warning' })
  } catch {
    return
  }
  const response = await deleteHomeCourseRecommendation(row.id)
  if (response.code === '200') {
    ElMessage.success('已移除')
    await loadData()
  }
}

function openHomepage() {
  router.push({ path: '/home', hash: '#home-learning-recommendation' })
}

onMounted(loadData)
</script>

<style scoped>
.workspace-grid { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(320px, .8fr); gap: var(--space-5); align-items: start; }
.manage-card, .preview-card { min-width: 0; }
.card-title { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); }
.card-title > div { display: grid; gap: 2px; }
.card-title strong { font-size: var(--text-lg); color: var(--text-strong); font-weight: 650; }
.card-title small, .course-cell small { color: var(--text-subtle); font-size: var(--text-xs); }
.sort-pill { display: inline-grid; place-items: center; min-width: 28px; height: 28px; padding: 0 var(--space-2); border-radius: var(--radius-md); color: var(--accent); background: var(--surface-accent); font-weight: 650; }
.course-cell { display: grid; gap: 2px; }
.course-cell strong { color: var(--text-strong); font-weight: 600; }
.preview-lane { margin-bottom: var(--space-4); padding: var(--space-4); border: 1px solid var(--line-subtle); border-radius: var(--radius-lg); background: var(--surface-canvas); }
.preview-heading { display: grid; grid-template-columns: 36px 1fr auto; align-items: center; gap: var(--space-2); padding-bottom: var(--space-3); border-bottom: 1px solid var(--line-subtle); }
.preview-heading > span { display: grid; place-items: center; width: 36px; height: 36px; border-radius: var(--radius-md); color: var(--accent); background: var(--surface-accent); }
.preview-heading > div { display: grid; gap: 2px; }
.preview-heading strong { color: var(--text-strong); font-weight: 600; }
.preview-heading small { color: var(--text-subtle); font-size: var(--text-xs); }
.preview-heading em { color: var(--text-subtle); font-style: normal; }
.preview-lane article { display: grid; grid-template-columns: 42px minmax(0, 1fr) 18px; align-items: center; gap: var(--space-3); padding: var(--space-3) 0; border-bottom: 1px solid var(--line-subtle); }
.preview-lane article:last-of-type { border-bottom: 0; }
.preview-badge { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; color: var(--accent); background: var(--surface-accent); font-size: var(--text-xs); font-weight: 650; }
.preview-lane h3 { margin: 0 0 2px; font-size: var(--text-md); }
.preview-lane p { display: -webkit-box; margin: 0; overflow: hidden; color: var(--text-subtle); font-size: var(--text-xs); line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.full-width { width: 100%; }
.course-option { display: flex; justify-content: space-between; gap: var(--space-5); }
.course-option small, .form-tip { color: var(--text-subtle); font-size: var(--text-xs); }
.form-tip { margin-left: var(--space-3); }
@media (max-width: 1100px) { .workspace-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
