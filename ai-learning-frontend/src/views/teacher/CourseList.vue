<template>
  <div class="course-list workspace-page">
    <PageHeader
      eyebrow="课程与教学"
      :title="isDraftBox ? '草稿箱' : '课程管理'"
      :description="isDraftBox ? '继续未完成的课程准备。' : isAdmin ? '维护课程基本信息与教学班开放范围。' : '从课程资料到教学内容，组织每一门课。'"
    >
      <template #actions>
        <el-button v-if="isDraftBox" @click="router.push('/courses')">返回课程管理</el-button>
        <el-button v-else-if="!isAdmin" @click="router.push('/courses/drafts')">草稿箱</el-button>
        <el-button v-if="!isAdmin" type="primary" :icon="Plus" @click="router.push('/courses/create')">新建课程</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="isDraftBox && localDraft" type="info" show-icon :closable="false" class="local-draft" data-testid="local-course-draft">
      <template #title>还有一门没填完的新课程「{{ localDraft }}」只暂存在本浏览器中，填写课程名称和分类后才会保存为草稿。</template>
      <el-button size="small" type="primary" @click="router.push('/courses/create')">继续填写</el-button>
    </el-alert>
    <section class="table-panel course-list-panel" aria-label="课程列表" data-testid="course-list-panel">
      <section class="course-toolbar" aria-label="筛选课程">
        <el-form :inline="true" :model="searchForm" @submit.prevent>
          <el-form-item class="title-filter">
            <el-input v-model="searchForm.title" :prefix-icon="Search" aria-label="课程名称" placeholder="请输入课程名称" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item class="category-filter">
            <el-select v-model="searchForm.categoryId" aria-label="分类" placeholder="请选择分类" clearable>
              <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
            </el-select>
          </el-form-item>
          <el-form-item class="filter-actions">
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </section>
      <ErrorNotice
        v-if="loadFailed"
        title="课程列表未加载"
        message="请确认网络后重试。"
        retry-label="重新加载"
        :busy="loading"
        @retry="loadCourses"
      />
      <el-table v-else :data="courses" v-loading="loading" class="course-table">
        <el-table-column prop="title" label="课程名称" min-width="270">
          <template #default="{ row }">
            <div class="course-identity">
              <span class="course-monogram" aria-hidden="true">{{ row.title?.slice(0, 1) || '课' }}</span>
              <div class="course-identity-copy">
                <span class="row-title">{{ row.title }}</span>
                <span class="course-metadata">{{ categoryLabel(row) }}<span v-if="row.difficulty"> · {{ difficultyLabel(row.difficulty) }}</span></span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="isDraftBox" label="当前步骤" min-width="150">
          <template #default="{ row }">{{ stageLabel(row.setupStage) }}</template>
        </el-table-column>
        <el-table-column v-if="isDraftBox" prop="updatedAt" label="最近修改" min-width="180" />
        <template v-else>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '已发布' : row.setupStage === 'COMPLETED' ? '未发布' : '创建中' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="学习学生" width="130">
            <template #default="{ row }"><span class="course-students">{{ row.studentCount ?? 0 }} 人</span></template>
          </el-table-column>
        </template>
        <el-table-column label="操作" :width="narrow ? ROW_SHEET_COLUMN_WIDTH : isAdmin || isDraftBox ? 320 : 270" fixed="right">
          <template #default="{ row }">
            <RowActions :label="row.title">
              <el-button v-if="isDraftBox" size="small" link type="primary" @click="router.push(`/courses/${row.id}`)">继续编辑</el-button>
              <el-button v-else-if="!isAdmin" size="small" link type="primary" @click="router.push(`/courses/${row.id}`)">课程详情</el-button>
              <el-button v-if="isAdmin || isDraftBox" size="small" link type="primary" @click="handleEdit(row)">编辑信息</el-button>
              <el-button v-if="isAdmin" size="small" link type="primary" @click="openPublication(row)">调整教学班</el-button>
              <el-button v-else-if="!isDraftBox" size="small" link type="primary" @click="openPublication(row)">
                {{ row.status === 1 ? '发布范围' : '发布' }}
              </el-button>
              <el-button size="small" link type="danger" :loading="deletingId === row.id" @click="handleDelete(row)">删除</el-button>
            </RowActions>
          </template>
        </el-table-column>
        <template #empty><AppEmpty /></template>
      </el-table>
      <el-pagination
        v-if="!loadFailed"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="writeListQuery"
        @size-change="handleSizeChange"
      />
    </section>
    <CoursePublicationDialog
      v-model="publicationVisible"
      :course-id="publicationCourse?.id"
      :course-title="publicationCourse?.title"
      :administrative="isAdmin"
      :published="publicationCourse?.status === 1"
      @saved="loadCourses"
    />
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import RowActions from '@/components/ui/RowActions.vue'
import CoursePublicationDialog from '@/components/course/CoursePublicationDialog.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { ROW_SHEET_COLUMN_WIDTH, useNarrowScreen } from '@/composables/useNarrowScreen'
import { getCourses, deleteCourse, getCategories } from '@/utils/course'
import { getRecords, getTotal, requireSuccess } from '@/utils/apiHelpers'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)
const isDraftBox = computed(() => route.name === 'CourseDrafts')
const { confirmDelete } = useConfirm()
const narrow = useNarrowScreen()
const courses = ref([]), categories = ref([]), deletingId = ref(null)
const publicationVisible = ref(false), publicationCourse = ref(null)
const searchForm = reactive({ title: '', categoryId: null })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  courses.value = []
  pagination.total = 0
})

// CourseForm keeps an unfinished new course in this browser only; the server list cannot show it.
const localDraft = computed(() => {
  if (!isDraftBox.value) return ''
  try {
    const saved = JSON.parse(localStorage.getItem(`eduloop:course-create:${userStore.userId}`) || 'null')
    return saved?.form?.title?.trim() || (saved?.form ? '未命名课程' : '')
  } catch { return '' }
})
const stageLabel = stage => ({ BASIC: '基本信息', MATERIALS: '课程资料', CONTENT: '课程内容' }[stage] || '基本信息')
const categoryLabel = course => course.categoryName || categories.value.find(category => Number(category.id) === Number(course.categoryId))?.name || '未分类'
const difficultyLabel = value => ({ beginner: '入门', intermediate: '基础', advanced: '进阶' }[value] || value)

const loadCourses = () => guarded(async isCurrent => {
  const res = requireSuccess(await getCourses({
    ...searchForm, pageNum: pagination.pageNum, pageSize: pagination.pageSize,
    ...(!isAdmin.value ? { box: isDraftBox.value ? 'drafts' : 'completed' } : {}),
  }))
  if (!isCurrent()) return
  courses.value = getRecords(res.data)
  pagination.total = getTotal(res.data)
  if (!courses.value.length && pagination.total > 0 && pagination.pageNum > 1) {
    pagination.pageNum = Math.ceil(pagination.total / pagination.pageSize)
    writeListQuery()
  }
})

function writeListQuery() {
  const query = { pageNum: String(pagination.pageNum), pageSize: String(pagination.pageSize) }
  if (searchForm.title.trim()) query.title = searchForm.title.trim()
  if (searchForm.categoryId) query.categoryId = String(searchForm.categoryId)
  const target = { path: route.path, query }
  if (router.resolve(target).fullPath === route.fullPath) loadCourses()
  else router.replace(target)
}
function handleSearch() {
  pagination.pageNum = 1
  writeListQuery()
}
function handleSizeChange() {
  pagination.pageNum = 1
  writeListQuery()
}
function handleReset() {
  searchForm.title = ''
  searchForm.categoryId = null
  handleSearch()
}
function handleEdit(row) {
  router.push({ name: 'CourseCreate', query: { id: row.id, returnTo: route.fullPath } })
}
function openPublication(row) {
  publicationCourse.value = row
  publicationVisible.value = true
}

async function handleDelete(row) {
  if (deletingId.value !== null || !await confirmDelete('确认删除此课程？')) return
  deletingId.value = row.id
  try {
    requireSuccess(await deleteCourse(row.id))
    ElMessage.success('删除成功')
    await loadCourses()
  } catch { /* The request client reports the failure and the row remains available. */ }
  finally { deletingId.value = null }
}

watch(() => route.fullPath, () => {
  searchForm.title = typeof route.query.title === 'string' ? route.query.title : ''
  searchForm.categoryId = Number(route.query.categoryId) || null
  pagination.pageNum = Math.max(1, Number(route.query.pageNum) || 1)
  pagination.pageSize = [5, 10, 20, 50].includes(Number(route.query.pageSize)) ? Number(route.query.pageSize) : 10
  loadCourses()
}, { immediate: true })
onMounted(async () => {
  try { categories.value = getRecords(requireSuccess(await getCategories()).data) } catch { /* The course list remains available. */ }
})
</script>

<style scoped>
.course-list {
  gap: var(--space-6);
}

.course-list :deep(.workspace-page-header h1) {
  font-size: var(--text-3xl);
  font-weight: 650;
  letter-spacing: -.025em;
}

.course-list :deep(.header-eyebrow) {
  color: var(--text-subtle);
  font-weight: 500;
}

.course-list :deep(.header-description) {
  font-size: var(--text-sm);
}

.course-list-panel {
  border-radius: var(--radius-xl);
  box-shadow: 0 3px 12px rgb(30 49 94 / 2%);
}

.course-toolbar {
  padding: var(--space-5) var(--space-6);
  border-bottom: 1px solid var(--line-subtle);
}

.course-toolbar :deep(.el-form--inline) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.course-toolbar :deep(.el-form-item) {
  margin: 0;
  min-width: 0;
}

.title-filter {
  width: 300px;
}

.category-filter {
  width: 180px;
}

.course-toolbar :deep(.el-input),
.course-toolbar :deep(.el-select) {
  width: 100%;
}

.course-list-panel :deep(.error-notice) {
  margin: var(--space-5);
}

.course-table :deep(th.el-table__cell) {
  padding-block: var(--space-4);
  background: var(--surface-base);
  color: var(--text-subtle);
  font-size: var(--text-xs);
  font-weight: 500;
}

.course-table :deep(td.el-table__cell) {
  padding-block: var(--space-5);
}

.course-table :deep(.el-table__cell:first-child .cell) {
  padding-left: var(--space-6);
}

.course-identity {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.course-monogram {
  display: grid;
  place-items: center;
  flex: 0 0 42px;
  height: 48px;
  border-radius: var(--radius-md);
  background: linear-gradient(145deg, var(--surface-accent), var(--surface-accent-strong));
  color: var(--accent);
  font-size: var(--text-lg);
  font-weight: 650;
}

.course-identity-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.course-metadata,
.course-students {
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.course-metadata {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-table :deep(.row-actions > .el-button) {
  margin-left: 0;
  padding-inline: var(--space-2);
}

.local-draft {
  margin: 0;
}

@media (max-width: 720px) {
  .course-toolbar {
    padding: var(--space-4);
  }

  .title-filter {
    width: 100%;
  }

  .category-filter {
    flex: 1;
  }

  .course-table :deep(.el-table__cell:first-child .cell) {
    padding-left: var(--space-4);
  }

  .course-monogram {
    display: none;
  }
}
</style>
