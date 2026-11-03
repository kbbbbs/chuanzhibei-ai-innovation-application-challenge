<template>
  <div class="paper-search">
    <el-card>
      <template #header>
        <div class="header-row">
          <span>论文查询</span>
          <el-button text @click="advancedVisible = !advancedVisible">
            {{ advancedVisible ? '收起高级筛选' : '展开高级筛选' }}
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="输入论文标题或关键词"
            clearable
            style="width: 360px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="searchForm.source" style="width: 160px" @change="resetPage">
            <el-option label="本地数据库" value="local" />
            <el-option label="arXiv" value="arxiv" />
            <el-option label="OpenAlex" value="openalex" />
            <el-option label="Semantic Scholar" value="semantic_scholar" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="searchForm.sortBy" style="width: 150px" @change="resetPage">
            <el-option label="相关优先" value="relevance" />
            <el-option label="最新优先" value="latest" />
            <el-option label="引用优先" value="citations" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button v-if="searchForm.source !== 'local'" :loading="importing" @click="handleImport">
            导入到本地
          </el-button>
          <el-button v-if="!showRecommendations" @click="showRecommendations = true">显示推荐</el-button>
        </el-form-item>
      </el-form>

      <el-form v-show="advancedVisible" :inline="true" :model="searchForm" class="advanced-form">
        <el-form-item label="作者">
          <el-input v-model="searchForm.author" placeholder="作者名" style="width: 180px" @change="resetPage" />
        </el-form-item>
        <el-form-item label="期刊/会议">
          <el-input v-model="searchForm.venue" placeholder="来源名称" style="width: 180px" @change="resetPage" />
        </el-form-item>
        <el-form-item label="年份范围">
          <div class="year-range">
            <el-input-number v-model="searchForm.yearFrom" :min="1900" :max="2100" controls-position="right" @change="resetPage" />
            <span>—</span>
            <el-input-number v-model="searchForm.yearTo" :min="1900" :max="2100" controls-position="right" @change="resetPage" />
          </div>
        </el-form-item>
        <el-form-item label="最少引用">
          <el-input-number v-model="searchForm.minCitations" :min="0" controls-position="right" @change="resetPage" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="searchForm.openAccessOnly" @change="resetPage">仅看可读全文</el-checkbox>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top:16px" v-if="showRecommendations">
      <template #header>
        <div class="card-header">
          <span>📖 为你推荐</span>
          <div class="recommend-actions">
            <el-button size="small" :loading="recLoading" @click="loadRecommend">刷新</el-button>
            <el-button size="small" @click="showRecommendations = false">隐藏</el-button>
          </div>
        </div>
      </template>
      <div v-loading="recLoading">
        <div v-if="recPapers.length === 0 && !recLoading" class="recommend-empty">暂无推荐</div>
        <div v-for="p in recPapers" :key="p.paperId" class="rec-item" @click="handleDetail(p)">
          <div class="rec-title-row">
            <span class="rec-title">{{ p.title }}</span>
            <div class="rec-tags">
              <el-tag size="small" type="success" effect="plain">{{ p.citationCount || 0 }} 引用</el-tag>
              <el-tag size="small" effect="plain">{{ sourceLabel(p.source) }}</el-tag>
            </div>
          </div>
          <div class="rec-meta">
            {{ p.authors?.map(a => a.name).slice(0, 3).join(', ') || '未知' }}
            <template v-if="p.year"> · {{ p.year }}</template>
            <template v-if="p.venue"> · {{ p.venue }}</template>
          </div>
          <div class="rec-abstract" v-if="p.abstractText">{{ p.abstractText.slice(0, 200) }}…</div>
        </div>
      </div>
    </el-card>

    <el-card style="margin-top: 16px" v-loading="loading">
      <div v-if="searchError && !loading" class="search-error" role="alert">
        <strong>论文检索失败</strong>
        <p>{{ searchError }}</p>
        <el-button type="primary" @click="handleSearch">重新搜索</el-button>
        <p class="search-error-hint">也可以在上方更换论文来源或关键词后搜索。</p>
      </div>
      <div v-else-if="hasSearched && total === 0 && !loading" class="search-empty">
        <template v-if="searchForm.source !== 'local' && isChinese(searchForm.keyword)">
          「{{ searchForm.keyword }}」是中文关键词，{{ sourceLabel(searchForm.source) }} 为英文论文库，中文检索结果有限。
          <el-button type="primary" link @click="switchToLocal">切换到本地数据库</el-button>
          <span style="margin:0 8px;color:#c0c4cc">|</span>
          或尝试英文关键词，如 Machine Learning / Deep Learning / Neural Network
        </template>
        <template v-else-if="searchForm.source !== 'local'">
          「{{ searchForm.keyword }}」在 {{ sourceLabel(searchForm.source) }} 中未找到结果，建议尝试其他来源或更换关键词。
        </template>
        <template v-else>
          本地数据库中未找到「{{ searchForm.keyword }}」相关论文，可从 arXiv、OpenAlex 或 Semantic Scholar 检索后再导入本地。
        </template>
      </div>

      <el-table :data="papers" border stripe v-if="!hasSearched || total > 0">
        <template #empty>
          <AppEmpty v-if="!loading && !hasSearched" description="请输入关键词搜索论文" />
        </template>
        <el-table-column prop="title" label="论文标题" min-width="280" show-overflow-tooltip />
        <el-table-column label="作者" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.authors?.map(a => a.name).join(', ') || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="year" label="年份" width="86" />
        <el-table-column label="引用" width="86">
          <template #default="{ row }">
            {{ row.citationCount ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="source-cell">
              <span>{{ row.venue || '-' }}</span>
              <el-tag size="small" effect="plain">{{ sourceLabel(row.source || searchForm.source) }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button size="small" type="primary" @click="handleDetail(row)">详情</el-button>
              <el-button v-if="row.openAccessPdf?.url || row.url" size="small" @click="openPdf(row)">原文</el-button>
              <el-button size="small" text @click="toggleFavorite(row)">
                {{ isFavorite(row.paperId) ? '已收藏' : '收藏' }}
              </el-button>
              <el-button size="small" text @click="toggleReadStatus(row)">
                {{ readStatusLabel(row.paperId) }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="searchForm.page"
        v-model:page-size="searchForm.size"
        :total="total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handleSearch"
        @size-change="handleSizeChange"
        class="pagination-bar"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="论文详情" width="860px">
      <template v-if="detail">
        <div class="detail-top">
          <div>
            <h3 class="detail-title">{{ detail.title }}</h3>
            <div class="detail-tags">
              <el-tag size="small" effect="plain">{{ sourceLabel(detail.source || detailSource) }}</el-tag>
              <el-tag v-if="detail.year" size="small" effect="plain">{{ detail.year }}</el-tag>
              <el-tag v-if="detail.citationCount !== undefined" size="small" type="success" effect="plain">
                被引 {{ detail.citationCount || 0 }} 次
              </el-tag>
              <el-tag v-if="detail.venue" size="small" type="info" effect="plain">{{ detail.venue }}</el-tag>
            </div>
          </div>
          <el-switch
            :model-value="detailMark.favorite"
            inline-prompt
            active-text="已收藏"
            inactive-text="收藏"
            @change="updateDetailFavorite"
          />
          <el-segmented
            v-model="detailMark.readStatus"
            :options="[
              { label: '未读', value: 'UNREAD' },
              { label: '已读', value: 'READ' }
            ]"
            @change="saveDetailMark(false)"
          />
        </div>

        <div class="detail-block">
          <div class="detail-label">作者</div>
          <div class="detail-value">{{ detail.authors?.map(a => a.name).join(', ') || '未知作者' }}</div>
        </div>

        <div class="detail-block" v-if="detail.doi || detail.publishedDate">
          <div class="detail-label">补充信息</div>
          <div class="detail-value">
            <span v-if="detail.doi">DOI：{{ detail.doi }}</span>
            <span v-if="detail.publishedDate" class="detail-inline">发布日期：{{ detail.publishedDate }}</span>
          </div>
        </div>

        <div class="detail-actions">
          <el-button v-if="detail.openAccessPdf?.url" type="primary" @click="windowOpen(detail.openAccessPdf.url)">查看 PDF</el-button>
          <el-button v-if="detail.url" @click="windowOpen(detail.url)">查看原文</el-button>
          <el-button @click="copyCitation(detail)">复制引用信息</el-button>
        </div>

        <el-divider />
        <div class="detail-block">
          <div class="detail-label">摘要</div>
          <p class="detail-abstract">{{ detail.abstractText || '暂无摘要' }}</p>
        </div>

        <el-divider />
        <div class="detail-block">
          <div class="detail-label">个人笔记</div>
          <MdEditor
            v-model="detailMark.noteMd"
            language="zh-CN"
            class="note-editor"
            :preview="false"
            :html-preview="false"
            :toolbars-exclude="['previewOnly', 'htmlPreview', 'catalog', 'github']"
          />
          <div class="note-actions-row">
            <el-button type="primary" :loading="markSaving" @click="saveDetailMark">保存笔记</el-button>
            <el-button v-if="detailMark.favorite || detailMark.noteMd" @click="clearDetailMark">清除标记</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import {
  deletePaperMark,
  getPaperDetail,
  getPaperMark,
  getPaperMarks,
  getPaperRecommendations,
  importPapers,
  savePaperMark,
  searchPapers
} from '@/api/paper'

const userStore = useUserStore()
const route = useRoute()
const advancedVisible = ref(false)
const DEFAULT_SEARCH_SOURCE = 'arxiv'

const searchForm = reactive({
  keyword: '',
  source: DEFAULT_SEARCH_SOURCE,
  sortBy: 'relevance',
  author: '',
  venue: '',
  yearFrom: undefined,
  yearTo: undefined,
  minCitations: undefined,
  openAccessOnly: false,
  page: 1,
  size: 10
})

const papers = ref([])
const total = ref(0)
const loading = ref(false)
const searchError = ref('')
const importing = ref(false)
const recPapers = ref([])
const recLoading = ref(false)
const hasSearched = ref(false)
const showRecommendations = ref(true)
const markSaving = ref(false)
const marksMap = ref({})

const detailVisible = ref(false)
const detail = ref(null)
const detailSource = ref('local')
const detailMark = reactive({ favorite: false, readStatus: 'UNREAD', noteMd: '' })

const loadRecommend = async () => {
  recLoading.value = true
  try {
    const isStudent = userStore.roleCode === 'student'
    const params = isStudent ? { studentId: userStore.userId } : { teacherId: userStore.userId }
    const res = await getPaperRecommendations(params)
    recPapers.value = res.code === '200' && res.data ? (res.data.data || []) : []
  } catch {
    recPapers.value = []
  } finally {
    recLoading.value = false
  }
}

function resetPage() {
  searchForm.page = 1
}

function handleSizeChange() {
  searchForm.page = 1
  handleSearch()
}

function buildSearchParams() {
  return {
    keyword: searchForm.keyword.trim(),
    source: searchForm.source,
    sortBy: searchForm.sortBy,
    author: searchForm.author?.trim() || undefined,
    venue: searchForm.venue?.trim() || undefined,
    yearFrom: searchForm.yearFrom || undefined,
    yearTo: searchForm.yearTo || undefined,
    minCitations: searchForm.minCitations ?? undefined,
    openAccessOnly: searchForm.openAccessOnly || undefined,
    page: searchForm.page,
    size: searchForm.size
  }
}

async function loadVisibleMarks() {
  const ids = papers.value.map(item => item.paperId).filter(Boolean)
  if (!ids.length) return
  try {
    const res = await getPaperMarks(ids)
    if (res.code === '200' && res.data) {
      const nextMap = { ...marksMap.value }
      Object.entries(res.data).forEach(([paperId, mark]) => {
        nextMap[paperId] = normalizeMark(mark)
      })
      marksMap.value = nextMap
    }
  } catch {
    // ignore
  }
}

const handleSearch = async () => {
  if (!searchForm.keyword.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  hasSearched.value = true
  showRecommendations.value = false
  searchError.value = ''
  loading.value = true
  try {
    const res = await searchPapers(buildSearchParams())
    if (res.code === '200') {
      const data = res.data
      if (data && data.data) {
        papers.value = data.data
        total.value = data.total || 0
      } else if (Array.isArray(data)) {
        papers.value = data
        total.value = data.length
      } else {
        papers.value = []
        total.value = 0
      }
      await loadVisibleMarks()
    } else {
      papers.value = []
      total.value = 0
      searchError.value = res.msg || '暂时无法完成检索，请稍后重试。'
    }
  } catch (error) {
    papers.value = []
    total.value = 0
    searchError.value = error.code === 'ECONNABORTED'
      ? '检索请求超时，请稍后重试或切换论文来源。'
      : (error.response?.data?.msg || '网络连接失败，请检查网络后重新搜索。')
  } finally {
    loading.value = false
  }
}

const handleImport = async () => {
  if (!searchForm.keyword.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  importing.value = true
  try {
    const res = await importPapers(searchForm.keyword.trim(), 20)
    if (res.code === '200') {
      ElMessage.success('导入成功')
      searchForm.source = 'local'
      handleSearch()
    } else {
      ElMessage.error(res.msg || '导入失败')
    }
  } catch {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}

function isChinese(str) {
  return /[一-龥]/.test(str)
}

function sourceLabel(src) {
  return {
    arxiv: 'arXiv',
    openalex: 'OpenAlex',
    local: '本地数据库',
    semantic_scholar: 'Semantic Scholar'
  }[src] || src || '未知来源'
}

function switchToLocal() {
  searchForm.source = 'local'
  handleSearch()
}

function windowOpen(url) {
  if (url) window.open(url, '_blank')
}

function openPdf(row) {
  windowOpen(row.openAccessPdf?.url || row.url)
}

function normalizeMark(mark) {
  return {
    favorite: Boolean(mark?.favorite),
    readStatus: mark?.readStatus === 'READ' ? 'READ' : 'UNREAD',
    noteMd: mark?.noteMd || ''
  }
}

async function loadMark(paperId) {
  try {
    const res = await getPaperMark(paperId)
    if (res.code === '200') {
      const mark = normalizeMark(res.data)
      marksMap.value = { ...marksMap.value, [paperId]: mark }
      return mark
    }
  } catch {
    // ignore
  }
  return { favorite: false, readStatus: 'UNREAD', noteMd: '' }
}

function isFavorite(paperId) {
  return Boolean(marksMap.value[paperId]?.favorite)
}

function readStatusLabel(paperId) {
  return marksMap.value[paperId]?.readStatus === 'READ' ? '已读' : '未读'
}

async function toggleFavorite(row) {
  const current = marksMap.value[row.paperId] || await loadMark(row.paperId)
  const nextFavorite = !current.favorite
  try {
    const res = await savePaperMark(row.paperId, {
      favorite: nextFavorite,
      readStatus: current.readStatus,
      noteMd: current.noteMd || '',
      title: row.title,
      source: row.source || searchForm.source
    })
    if (res.code === '200') {
      marksMap.value = {
        ...marksMap.value,
        [row.paperId]: normalizeMark(res.data)
      }
      ElMessage.success(nextFavorite ? '已收藏' : '已取消收藏')
    }
  } catch {
    // global handled
  }
}

async function toggleReadStatus(row) {
  const current = marksMap.value[row.paperId] || await loadMark(row.paperId)
  const nextReadStatus = current.readStatus === 'READ' ? 'UNREAD' : 'READ'
  try {
    const res = await savePaperMark(row.paperId, {
      favorite: current.favorite,
      readStatus: nextReadStatus,
      noteMd: current.noteMd || '',
      title: row.title,
      source: row.source || searchForm.source
    })
    if (res.code === '200') {
      marksMap.value = {
        ...marksMap.value,
        [row.paperId]: normalizeMark(res.data)
      }
      ElMessage.success(nextReadStatus === 'READ' ? '已标记为已读' : '已标记为未读')
    }
  } catch {
    // global handled
  }
}

async function handleDetail(row) {
  detailSource.value = row.source || searchForm.source
  try {
    const [detailRes, mark] = await Promise.all([
      getPaperDetail(row.paperId, detailSource.value, { silent: true, silentError: true }),
      loadMark(row.paperId)
    ])
    detail.value = detailRes.code === '200' && detailRes.data ? detailRes.data : row
    detailMark.favorite = mark.favorite
    detailMark.readStatus = mark.readStatus
    detailMark.noteMd = mark.noteMd
  } catch {
    detail.value = row
    detailMark.favorite = false
    detailMark.readStatus = 'UNREAD'
    detailMark.noteMd = ''
  }
  detailVisible.value = true
}

async function updateDetailFavorite(value) {
  if (!detail.value?.paperId) return
  detailMark.favorite = Boolean(value)
  await saveDetailMark(false)
}

async function saveDetailMark(showMessage = true) {
  if (!detail.value?.paperId) return
  markSaving.value = true
  try {
    const res = await savePaperMark(detail.value.paperId, {
      favorite: detailMark.favorite,
      readStatus: detailMark.readStatus,
      noteMd: detailMark.noteMd,
      title: detail.value.title,
      source: detail.value.source || detailSource.value
    })
    if (res.code === '200') {
      marksMap.value = {
        ...marksMap.value,
        [detail.value.paperId]: normalizeMark(res.data)
      }
      if (showMessage) ElMessage.success('标记已保存')
    }
  } finally {
    markSaving.value = false
  }
}

async function clearDetailMark() {
  if (!detail.value?.paperId) return
  markSaving.value = true
  try {
    const res = await deletePaperMark(detail.value.paperId)
    if (res.code === '200') {
      detailMark.favorite = false
      detailMark.readStatus = 'UNREAD'
      detailMark.noteMd = ''
      marksMap.value = {
        ...marksMap.value,
        [detail.value.paperId]: { favorite: false, readStatus: 'UNREAD', noteMd: '' }
      }
      ElMessage.success('标记已清除')
    }
  } finally {
    markSaving.value = false
  }
}

async function copyCitation(row) {
  const citation = [
    row.authors?.map(a => a.name).join(', ') || '未知作者',
    row.year ? `(${row.year})` : '',
    row.title || '',
    row.venue || '',
    row.doi ? `DOI: ${row.doi}` : (row.url || '')
  ].filter(Boolean).join('. ')

  try {
    await navigator.clipboard.writeText(citation)
    ElMessage.success('引用信息已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.source = DEFAULT_SEARCH_SOURCE
  searchForm.sortBy = 'relevance'
  searchForm.author = ''
  searchForm.venue = ''
  searchForm.yearFrom = undefined
  searchForm.yearTo = undefined
  searchForm.minCitations = undefined
  searchForm.openAccessOnly = false
  searchForm.page = 1
  hasSearched.value = false
  searchError.value = ''
  papers.value = []
  total.value = 0
  showRecommendations.value = true
  loadRecommend()
}

onMounted(() => {
  const initialKeyword = typeof route.query.keyword === 'string' ? route.query.keyword.trim() : ''
  if (initialKeyword) {
    searchForm.keyword = initialKeyword
    requestAnimationFrame(() => handleSearch())
    return
  }

  // defer recommendation load so the page renders first
  requestAnimationFrame(() => {
    setTimeout(() => loadRecommend(), 100)
  })
})
</script>

<style scoped>
.search-error {
  padding: 20px;
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: 8px;
  background: var(--el-color-danger-light-9);
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
}

.search-error p {
  margin: 12px 0;
  line-height: 1.6;
}

.search-error .search-error-hint {
  margin-bottom: 0;
  color: var(--el-text-color-regular);
}

.header-row,
.card-header,
.rec-title-row,
.detail-top,
.detail-actions,
.note-actions-row,
.source-cell {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.advanced-form {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.year-range,
.rec-tags,
.detail-tags,
.action-btns {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pagination-bar {
  margin-top: 20px;
  justify-content: center;
}

.recommend-empty {
  color: #909399;
  text-align: center;
  padding: 20px;
}

.rec-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background .15s;
}

.rec-item:last-child {
  border-bottom: none;
}

.rec-item:hover {
  background: #fbf9f5;
  margin: 0 -8px;
  padding: 12px 8px;
  border-radius: 4px;
}

.rec-title {
  font-weight: 500;
  font-size: 14px;
  color: #303133;
}

.rec-meta,
.rec-abstract {
  font-size: 12px;
  color: #909399;
}

.rec-meta {
  margin-bottom: 4px;
}

.rec-abstract,
.detail-abstract {
  line-height: 1.7;
}

.search-empty {
  text-align: center;
  padding: 32px 20px;
  color: #909399;
  font-size: 14px;
  line-height: 1.8;
}

.detail-title {
  margin: 0 0 10px;
}

.detail-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.detail-label {
  font-size: 13px;
  color: #909399;
}

.detail-value {
  color: #303133;
  line-height: 1.7;
}

.detail-inline {
  margin-left: 16px;
}

.note-editor {
  height: 280px;
}

.note-actions-row {
  justify-content: flex-end;
  margin-top: 12px;
}

.source-cell {
  justify-content: flex-start;
  flex-wrap: wrap;
}
</style>
