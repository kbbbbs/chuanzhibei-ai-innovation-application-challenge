<script setup>
import { ref, reactive } from 'vue'
import { Search, CollectionTag, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '../api/index'

const loading = ref(false)
const papers = ref([])
const total = ref(0)
const hasSearched = ref(false)

const activeSource = ref('local')

const sourceOptions = [
  { value: 'local', label: '本地数据库', desc: '已导入论文 · 全文检索 · 离线可用' },
  { value: 'semantic_scholar', label: 'Semantic Scholar', desc: '2 亿+ 论文 · 引用数据强' },
  { value: 'arxiv', label: 'arXiv', desc: '预印本 · CS/数学/物理为主' },
  { value: 'openalex', label: 'OpenAlex', desc: '2.5 亿+ 学术作品 · 覆盖最广' },
]

const currentSource = ref(sourceOptions[0])

const filters = reactive({
  keyword: '',
  page: 1,
  size: 10,
})

function switchSource(opt) {
  activeSource.value = opt.value
  currentSource.value = opt
  if (filters.keyword.trim()) {
    search()
  }
}

async function search() {
  if (!filters.keyword.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  loading.value = true
  hasSearched.value = true
  try {
    const res = await api.get('/paper/search', {
      params: {
        keyword: filters.keyword,
        source: activeSource.value,
        page: filters.page,
        size: filters.size,
      }
    })
    papers.value = res.data?.data || []
    total.value = res.data?.total || 0
  } catch (error) {
    papers.value = []
    const errorMsg = error.response?.data?.message || error.message || '搜索失败'
    if (errorMsg.includes('429')) {
      ElMessage.error('API请求过于频繁，请稍后再试或切换到其他数据源(arXiv/OpenAlex)')
    } else {
      ElMessage.error(errorMsg)
    }
  } finally {
    loading.value = false
  }
}

function handlePageChange(page) {
  filters.page = page
  search()
}

function getAuthorNames(authors) {
  if (!authors || authors.length === 0) return '未知作者'
  return authors.map(a => a.name).join(', ')
}

function getSourceTag(source) {
  const map = { local: 'primary', semantic_scholar: '', arxiv: 'info', openalex: 'success' }
  return map[source] || ''
}

function getSourceLabel(source) {
  const map = { local: '本地', semantic_scholar: 'S2', arxiv: 'arXiv', openalex: 'OpenAlex' }
  return map[source] || source
}
</script>

<template>
  <div class="paper-search-container">
    <div class="search-header">
      <h2>学术论文搜索</h2>
      <p class="subtitle">{{ currentSource.desc }}</p>

      <div class="source-tabs">
        <div
          v-for="opt in sourceOptions"
          :key="opt.value"
          class="source-tab"
          :class="{ active: activeSource === opt.value }"
          @click="switchSource(opt)"
        >
          <span class="tab-label">{{ opt.label }}</span>
          <span class="tab-desc">{{ opt.desc }}</span>
        </div>
      </div>

      <div class="search-bar">
        <el-input
          v-model="filters.keyword"
          placeholder="输入关键词搜索论文，如：machine learning, deep learning..."
          size="large"
          clearable
          @keyup.enter="search"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" size="large" :loading="loading" @click="search">
          搜索
        </el-button>
      </div>
    </div>

    <div class="search-results" v-if="hasSearched">
      <div class="result-info" v-if="!loading && total > 0">
        找到 <strong>{{ total }}</strong> 篇论文，当前第 {{ filters.page }} 页
      </div>

      <el-skeleton :loading="loading" animated :count="5">
        <template #template>
          <div v-for="i in 5" :key="i" class="paper-card-skeleton">
            <el-skeleton-item variant="text" style="width: 60%; height: 24px; margin-bottom: 8px" />
            <el-skeleton-item variant="text" style="width: 30%; height: 16px; margin-bottom: 12px" />
            <el-skeleton-item variant="text" style="width: 100%; height: 48px" />
          </div>
        </template>

        <template #default>
          <el-empty v-if="!loading && papers.length === 0" description="未找到相关论文，请更换关键词或数据源" />

          <div class="paper-list">
            <el-card
              v-for="paper in papers"
              :key="paper.paperId"
              class="paper-card"
              shadow="hover"
            >
              <div class="paper-content">
                <div class="paper-main">
                  <h3 class="paper-title">
                    <a :href="paper.url" target="_blank" rel="noopener">{{ paper.title }}</a>
                    <el-tag
                      v-if="paper.source"
                      size="small"
                      :type="getSourceTag(paper.source)"
                      class="source-badge"
                    >
                      {{ getSourceLabel(paper.source) }}
                    </el-tag>
                  </h3>
                  <div class="paper-meta">
                    <span class="paper-authors">
                      <el-icon><CollectionTag /></el-icon>
                      {{ getAuthorNames(paper.authors) }}
                    </span>
                    <span v-if="paper.year" class="paper-year">{{ paper.year }}</span>
                    <el-tag v-if="paper.venue" size="small" type="info">{{ paper.venue }}</el-tag>
                  </div>
                  <p class="paper-abstract">
                    {{ paper.abstractText || '暂无摘要' }}
                  </p>
                </div>
                <div class="paper-actions">
                  <el-link
                    v-if="paper.openAccessPdf?.url"
                    :href="paper.openAccessPdf.url"
                    target="_blank"
                    type="primary"
                    :icon="Download"
                    :underline="false"
                  >
                    下载 PDF
                  </el-link>
                  <div v-if="paper.citationCount > 0" class="citation-count">
                    被引 {{ paper.citationCount }} 次
                  </div>
                </div>
              </div>
            </el-card>
          </div>

          <div class="pagination-wrapper" v-if="total > filters.size">
            <el-pagination
              v-model:current-page="filters.page"
              :page-size="filters.size"
              :total="total"
              layout="prev, pager, next"
              @current-change="handlePageChange"
            />
          </div>
        </template>
      </el-skeleton>
    </div>
  </div>
</template>

<style scoped>
.paper-search-container {
  max-width: 960px;
  margin: 0 auto;
  padding: 30px 20px;
}

.search-header {
  text-align: center;
  margin-bottom: 32px;
}

.search-header h2 {
  font-size: 28px;
  color: #303133;
  margin-bottom: 8px;
}

.subtitle {
  color: #909399;
  font-size: 14px;
  margin-bottom: 20px;
}

.source-tabs {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 24px;
}

.source-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 24px;
  border: 2px solid #e4e7ed;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s;
  background: #fff;
  min-width: 160px;
}

.source-tab:hover {
  border-color: #a0cfff;
  background: #ecf5ff;
}

.source-tab.active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.15);
}

.tab-label {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.source-tab.active .tab-label {
  color: #409eff;
}

.tab-desc {
  font-size: 12px;
  color: #909399;
}

.search-bar {
  display: flex;
  gap: 12px;
  max-width: 640px;
  margin: 0 auto;
}

.result-info {
  color: #606266;
  font-size: 14px;
  margin-bottom: 16px;
}

.paper-card {
  margin-bottom: 16px;
}

.paper-card-skeleton {
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 16px;
  border: 1px solid #ebeef5;
}

.paper-content {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.paper-main {
  flex: 1;
  min-width: 0;
}

.paper-title {
  font-size: 17px;
  margin: 0 0 8px 0;
  line-height: 1.4;
}

.paper-title a {
  color: #303133;
  text-decoration: none;
}

.paper-title a:hover {
  color: #409eff;
}

.source-badge {
  margin-left: 8px;
  vertical-align: middle;
}

.paper-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
  color: #909399;
  font-size: 13px;
}

.paper-authors {
  display: flex;
  align-items: center;
  gap: 4px;
}

.paper-year {
  color: #c0c4cc;
}

.paper-abstract {
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.paper-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.citation-count {
  color: #c0c4cc;
  font-size: 12px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
