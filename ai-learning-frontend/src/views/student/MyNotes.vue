<template>
  <div class="my-notes">
    <div class="page-header">
      <div>
        <h2>引用与笔记</h2>
        <p>管理研究笔记、复制引用信息，整理你的论文阅读记录。</p>
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
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleFilter">筛选</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="notes-grid" v-loading="loading">
      <template v-if="filteredNotes.length > 0">
        <article v-for="item in filteredNotes" :key="item.paperId" class="note-card">
          <div class="note-card-header">
            <h3>{{ item.title || item.paperId }}</h3>
            <div class="note-card-tags">
              <el-tag v-if="item.source" size="small" effect="plain">{{ sourceLabel(item.source) }}</el-tag>
              <el-tag size="small" :type="item.favorite ? 'warning' : 'info'" effect="plain">
                {{ item.favorite ? '已收藏' : '未收藏' }}
              </el-tag>
              <el-tag size="small" :type="item.readStatus === 'READ' ? 'success' : 'info'" effect="plain">
                {{ item.readStatus === 'READ' ? '已读' : '未读' }}
              </el-tag>
            </div>
          </div>

          <div class="note-card-body">
            <div class="citation-section">
              <div class="section-label">引用信息</div>
              <div class="citation-area">
                <code class="citation-code">{{ buildCitation(item) }}</code>
                <el-button size="small" type="primary" @click="copyMarkCitation(item)">复制</el-button>
              </div>
            </div>

            <div class="note-section">
              <div class="section-label">研究笔记</div>
              <div v-if="item.noteMd" class="note-preview" @click="editNote(item)">
                <MdPreview :modelValue="item.noteMd" previewTheme="default" />
              </div>
              <div v-else class="note-empty" @click="editNote(item)">
                点击添加笔记…
              </div>
            </div>
          </div>

          <div class="note-card-footer">
            <span class="note-date" v-if="item.updatedAt">
              {{ new Date(item.updatedAt).toLocaleString('zh-CN') }}
            </span>
            <div class="note-card-actions">
              <el-button size="small" text @click="openInSearch(item)">检索</el-button>
              <el-button size="small" text type="primary" @click="editNote(item)">编辑笔记</el-button>
              <el-button size="small" text type="danger" @click="handleDelete(item)">删除</el-button>
            </div>
          </div>
        </article>
      </template>
      <el-empty v-else-if="!loading" description="暂无笔记记录">
        <el-button type="primary" @click="$router.push('/paper-search')">去论文搜索</el-button>
      </el-empty>
    </div>

    <el-pagination
      v-if="total > filterForm.size"
      v-model:current-page="filterForm.page"
      v-model:page-size="filterForm.size"
      :total="total"
      :page-sizes="[12, 24, 48]"
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="loadNotes"
      @size-change="loadNotes"
      class="pagination-bar"
    />

    <el-dialog v-model="noteVisible" title="编辑笔记" width="760px" @close="noteVisible = false">
      <template v-if="editingMark">
        <div class="dialog-paper-title">{{ editingMark.title || editingMark.paperId }}</div>
        <div class="dialog-meta" v-if="editingMark.source">
          <el-tag size="small" effect="plain">{{ sourceLabel(editingMark.source) }}</el-tag>
        </div>
        <div class="citation-box">
          <div class="section-label">引用信息</div>
          <div class="citation-area">
            <code class="citation-code">{{ buildCitation(editingMark) }}</code>
            <el-button size="small" type="primary" @click="copyMarkCitation(editingMark)">复制引用</el-button>
          </div>
        </div>
        <div class="section-label">Markdown 笔记</div>
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
import { MdEditor, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import 'md-editor-v3/lib/preview.css'
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
  page: 1,
  size: 12
})

const total = ref(0)

function sourceLabel(src) {
  return {
    arxiv: 'arXiv', openalex: 'OpenAlex',
    local: '本地数据库', semantic_scholar: 'Semantic Scholar'
  }[src] || src || '未知来源'
}

const filteredNotes = computed(() => {
  return marks.value.filter(m => m.noteMd && m.noteMd.trim())
})

async function loadNotes() {
  loading.value = true
  try {
    const res = await listPaperMarks({
      keyword: filterForm.keyword || undefined,
      page: filterForm.page,
      size: filterForm.size
    })
    if (res.code === '200') {
      const data = res.data
      const list = Array.isArray(data) ? data : (data && data.data ? data.data : [])
      marks.value = list
      total.value = (data && data.total) || list.length
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
  loadNotes()
}

async function handleGenerate() {
  generating.value = true
  try {
    const res = await generatePaperMarks()
    if (res.code === '200') {
      const data = res.data
      ElMessage.success(data.message || `已生成 ${data.generated} 条论文资料`)
      loadNotes()
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
      loadNotes()
    } else {
      ElMessage.warning(res.msg || '批量生成失败')
    }
  } catch {
    ElMessage.error('批量生成失败，请稍后重试')
  } finally {
    batchGenerating.value = false
  }
}

function handleReset() {
  filterForm.keyword = ''
  filterForm.page = 1
  loadNotes()
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

onMounted(() => { document.querySelector('.layout-main')?.scrollTo({ top: 0 }); loadNotes() })
</script>

<style scoped>
.my-notes { display: flex; flex-direction: column; gap: 12px; }
.page-header p { margin: 6px 0 0; color: var(--text-secondary); font-size: 13px; }
.pagination-bar { margin-top: 20px; justify-content: center; }

.notes-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(400px, 1fr)); gap: 16px; min-height: 200px; }

.note-card { background: var(--bg-elevated); border: 1px solid var(--border-color); border-radius: 16px; padding: 18px; display: flex; flex-direction: column; gap: 14px; box-shadow: var(--card-shadow); transition: box-shadow .2s; }
.note-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.08); }

.note-card-header h3 { margin: 0; font-size: 15px; color: var(--text-primary); line-height: 1.5; }
.note-card-tags { display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap; }

.section-label { font-size: 12px; color: #909399; margin-bottom: 6px; font-weight: 500; }

.citation-area { display: flex; gap: 8px; align-items: flex-start; }
.citation-code { flex: 1; font-size: 12px; color: #606266; word-break: break-all; background: #f8f9fa; padding: 6px 10px; border-radius: 6px; border: 1px solid #e4e7ed; line-height: 1.5; }

.note-preview { cursor: pointer; border: 1px dashed var(--border-color); border-radius: 8px; padding: 8px 12px; max-height: 160px; overflow: hidden; transition: border-color .2s; }
.note-preview:hover { border-color: var(--primary-color-light); }
.note-preview :deep(.md-editor-preview) { background: transparent; padding: 0; font-size: 13px; }

.note-empty { cursor: pointer; border: 1px dashed var(--border-color); border-radius: 8px; padding: 16px; text-align: center; color: #c0c4cc; font-size: 13px; transition: all .2s; }
.note-empty:hover { border-color: var(--primary-color-light); color: var(--primary-color); }

.note-card-footer { display: flex; justify-content: space-between; align-items: center; margin-top: auto; }
.note-date { font-size: 12px; color: #c0c4cc; }
.note-card-actions { display: flex; gap: 4px; }

.dialog-paper-title { font-size: 15px; font-weight: 600; margin-bottom: 8px; }
.dialog-meta { margin-bottom: 12px; }
.citation-box { background: #f8f9fa; border-radius: 8px; padding: 12px; margin-bottom: 16px; }
.note-editor { height: 320px; }

@media (max-width: 768px) {
  .notes-grid { grid-template-columns: 1fr; }
}
</style>
