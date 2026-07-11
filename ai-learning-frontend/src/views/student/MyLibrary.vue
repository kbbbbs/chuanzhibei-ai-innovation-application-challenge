<template>
  <div class="my-library">
    <div class="page-header">
      <div>
        <h2>个人资料库</h2>
        <p>管理你收藏的论文、阅读状态与笔记。</p>
      </div>
      <div class="page-header-actions">
        <el-button type="primary" :loading="generating" @click="handleGenerate">
          根据学习内容生成资料
        </el-button>
        <el-button v-if="userStore.isAdmin" type="warning" :loading="batchGenerating" @click="handleBatchGenerate">
          批量生成全部用户资料
        </el-button>
      </div>
    </div>

    <el-card>
      <el-form :inline="true" :model="filterForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="filterForm.keyword"
            placeholder="搜索论文标题"
            clearable
            style="width: 260px"
            @keyup.enter="handleFilter"
          />
        </el-form-item>
        <el-form-item label="阅读状态">
          <el-select v-model="filterForm.readStatus" style="width: 140px" @change="resetAndFilter">
            <el-option label="全部" value="" />
            <el-option label="未读" value="UNREAD" />
            <el-option label="已读" value="READ" />
          </el-select>
        </el-form-item>
        <el-form-item label="收藏状态">
          <el-select v-model="filterForm.favorite" style="width: 140px" @change="resetAndFilter">
            <el-option label="全部" value="" />
            <el-option label="已收藏" value="true" />
            <el-option label="未收藏" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleFilter">筛选</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top:16px" v-loading="loading">
      <el-table :data="filteredMarks" border stripe>
        <template #empty>
          <AppEmpty v-if="!loading" description="暂无收藏论文，在论文搜索中收藏论文后这里就会显示。" />
        </template>
        <el-table-column label="论文标题" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mark-title" @click="openInSearch(row)">{{ row.title || row.paperId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" v-if="row.source">{{ sourceLabel(row.source) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收藏" width="80" align="center">
          <template #default="{ row }">
            <el-icon :size="18" :color="row.favorite ? '#f59e0b' : '#c0c4cc'" @click="toggleFavorite(row)">
              <StarFilled v-if="row.favorite" />
              <Star v-else />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column label="阅读状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.readStatus === 'READ'"
              inline-prompt
              active-text="已读"
              inactive-text="未读"
              @change="toggleReadStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="笔记" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.noteMd ? 'primary' : 'info'" effect="plain">
              {{ row.noteMd ? '有' : '无' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">
            {{ row.updatedAt ? new Date(row.updatedAt).toLocaleString('zh-CN') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button size="small" type="primary" link @click="openInSearch(row)">检索</el-button>
              <el-button size="small" text @click="editNote(row)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button size="small" text type="danger" @click="handleDelete(row)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > filterForm.size"
        v-model:current-page="filterForm.page"
        v-model:page-size="filterForm.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadMarks"
        @size-change="loadMarks"
        class="pagination-bar"
      />
    </el-card>

    <el-dialog v-model="noteVisible" title="编辑笔记" width="700px" @close="noteVisible = false">
      <template v-if="editingMark">
        <div class="note-paper-title">{{ editingMark.title || editingMark.paperId }}</div>
        <div class="note-meta" v-if="editingMark.source">
          <el-tag size="small" effect="plain">{{ sourceLabel(editingMark.source) }}</el-tag>
        </div>
        <div class="note-citation-box">
          <div class="citation-label">引用信息</div>
          <div class="citation-row">
            <code class="citation-text">{{ buildCitation(editingMark) }}</code>
            <el-button size="small" type="primary" @click="copyMarkCitation(editingMark)">复制引用</el-button>
          </div>
        </div>
        <MdEditor
          v-model="editingNoteMd"
          language="zh-CN"
          class="note-editor"
          :preview="false"
          :html-preview="false"
          :toolbars-exclude="['previewOnly', 'htmlPreview', 'catalog', 'github']"
        />
      </template>
      <template #footer>
        <el-button @click="noteVisible = false">取消</el-button>
        <el-button type="primary" :loading="noteSaving" @click="saveNote">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Star, StarFilled } from '@element-plus/icons-vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import AppEmpty from '@/components/AppEmpty.vue'
import { useUserStore } from '@/store/user'
import { deletePaperMark, listPaperMarks, savePaperMark, generatePaperMarks, batchGeneratePaperMarks } from '@/api/paper'

const router = useRouter()
const userStore = useUserStore()
const marks = ref([])
const loading = ref(false)
const generating = ref(false)
const batchGenerating = ref(false)
const noteVisible = ref(false)
const editingMark = ref(null)
const editingNoteMd = ref('')
const noteSaving = ref(false)

const filterForm = reactive({
  keyword: '',
  readStatus: '',
  favorite: '',
  page: 1,
  size: 10
})

const total = ref(0)

function sourceLabel(src) {
  return {
    arxiv: 'arXiv',
    openalex: 'OpenAlex',
    local: '本地数据库',
    semantic_scholar: 'Semantic Scholar'
  }[src] || src || '未知来源'
}

const filteredMarks = computed(() => {
  let list = marks.value
  if (filterForm.readStatus) {
    list = list.filter(m => m.readStatus === filterForm.readStatus)
  }
  if (filterForm.favorite !== '') {
    const want = filterForm.favorite === 'true'
    list = list.filter(m => m.favorite === want)
  }
  return list
})

async function loadMarks() {
  loading.value = true
  try {
    const res = await listPaperMarks({
      keyword: filterForm.keyword || undefined,
      page: filterForm.page,
      size: filterForm.size
    })
    if (res.code === '200') {
      const data = res.data
      if (Array.isArray(data)) {
        marks.value = data
        total.value = data.length
      } else if (data && data.data) {
        marks.value = data.data
        total.value = data.total || data.data.length
      } else {
        marks.value = []
        total.value = 0
      }
    } else {
      marks.value = []
      total.value = 0
    }
  } catch {
    marks.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleFilter() {
  filterForm.page = 1
  loadMarks()
}

async function handleGenerate() {
  generating.value = true
  try {
    const res = await generatePaperMarks()
    if (res.code === '200') {
      const data = res.data
      ElMessage.success(data.message || `已生成 ${data.generated} 条论文资料`)
      loadMarks()
    } else {
      ElMessage.warning(res.msg || '生成失败')
    }
  } catch {
    ElMessage.error('生成失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

async function handleBatchGenerate() {
  batchGenerating.value = true
  try {
    const res = await batchGeneratePaperMarks()
    if (res.code === '200') {
      const data = res.data
      ElMessage.success(data.message || `已为 ${data.generatedUsers} 个用户生成 ${data.totalPapers} 条论文资料`)
      loadMarks()
    } else {
      ElMessage.warning(res.msg || '批量生成失败')
    }
  } catch {
    ElMessage.error('批量生成失败，请稍后重试')
  } finally {
    batchGenerating.value = false
  }
}

function resetAndFilter() {
  filterForm.page = 1
  loadMarks()
}

function handleReset() {
  filterForm.keyword = ''
  filterForm.readStatus = ''
  filterForm.favorite = ''
  filterForm.page = 1
  loadMarks()
}

async function toggleFavorite(row) {
  const nextFavorite = !row.favorite
  try {
    const res = await savePaperMark(row.paperId, {
      favorite: nextFavorite,
      readStatus: row.readStatus,
      noteMd: row.noteMd || '',
      title: row.title,
      source: row.source
    })
    if (res.code === '200') {
      row.favorite = nextFavorite
      ElMessage.success(nextFavorite ? '已收藏' : '已取消收藏')
    }
  } catch { /* ignore */ }
}

async function toggleReadStatus(row) {
  const nextRead = row.readStatus === 'READ' ? 'UNREAD' : 'READ'
  try {
    const res = await savePaperMark(row.paperId, {
      favorite: row.favorite,
      readStatus: nextRead,
      noteMd: row.noteMd || '',
      title: row.title,
      source: row.source
    })
    if (res.code === '200') {
      row.readStatus = nextRead
      ElMessage.success(nextRead === 'READ' ? '已标记为已读' : '已标记为未读')
    }
  } catch { /* ignore */ }
}

function editNote(row) {
  editingMark.value = row
  editingNoteMd.value = row.noteMd || ''
  noteVisible.value = true
}

async function saveNote() {
  if (!editingMark.value) return
  noteSaving.value = true
  try {
    const res = await savePaperMark(editingMark.value.paperId, {
      favorite: editingMark.value.favorite,
      readStatus: editingMark.value.readStatus,
      noteMd: editingNoteMd.value,
      title: editingMark.value.title,
      source: editingMark.value.source
    })
    if (res.code === '200') {
      editingMark.value.noteMd = editingNoteMd.value
      noteVisible.value = false
      ElMessage.success('笔记已保存')
    }
  } catch { /* ignore */ } finally {
    noteSaving.value = false
  }
}

function buildCitation(row) {
  const parts = [row.title || '']
  if (row.source) parts.push(`来源: ${sourceLabel(row.source)}`)
  return parts.filter(Boolean).join(' — ')
}

async function copyMarkCitation(row) {
  try {
    await navigator.clipboard.writeText(buildCitation(row))
    ElMessage.success('引用信息已复制')
  } catch {
    ElMessage.warning('复制失败')
  }
}

function openInSearch(row) {
  router.push({ path: '/paper-search', query: { keyword: row.title || row.paperId } })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.title || row.paperId}」的记录？`, '确认', { type: 'warning' })
  } catch { return }
  try {
    const res = await deletePaperMark(row.paperId)
    if (res.code === '200') {
      marks.value = marks.value.filter(m => m.paperId !== row.paperId)
      total.value--
      ElMessage.success('已删除')
    }
  } catch { /* ignore */ }
}

onMounted(() => { document.querySelector('.layout-main')?.scrollTo({ top: 0 }); loadMarks() })
</script>

<style scoped>
.my-library { display: flex; flex-direction: column; gap: 12px; }
.page-header p { margin: 6px 0 0; color: var(--text-secondary); font-size: 13px; }
.action-btns { display: flex; align-items: center; gap: 4px; }
.mark-title { color: var(--primary-color); cursor: pointer; }
.mark-title:hover { text-decoration: underline; }
.pagination-bar { margin-top: 20px; justify-content: center; }
.note-paper-title { font-size: 15px; font-weight: 600; margin-bottom: 8px; }
.note-meta { margin-bottom: 12px; }
.note-citation-box { background: #f8f9fa; border-radius: 8px; padding: 12px; margin-bottom: 16px; }
.citation-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
.citation-row { display: flex; gap: 8px; align-items: flex-start; }
.citation-text { flex: 1; font-size: 12px; color: #606266; word-break: break-all; background: #fff; padding: 6px 8px; border-radius: 4px; border: 1px solid #e4e7ed; }
.note-editor { height: 300px; }
</style>
