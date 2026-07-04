<template>
  <div class="paper-search">
    <el-card>
      <template #header>
        <span>论文查询</span>
      </template>

      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="输入论文标题或关键词" clearable
            style="width: 360px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="searchForm.source" style="width: 150px">
            <el-option label="本地数据库" value="local" />
            <el-option label="arXiv" value="arxiv" />
            <el-option label="OpenAlex" value="openalex" />
            <el-option label="Semantic Scholar" value="semantic_scholar" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button v-if="searchForm.source !== 'local'" :loading="importing" @click="handleImport">
            导入到本地
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 为你推荐 -->
    <el-card style="margin-top:16px" v-if="!hasSearched && (recPapers.length > 0 || recLoading)">
      <template #header>
        <div class="card-header">
          <span>📖 为你推荐</span>
          <el-button size="small" :loading="recLoading" @click="loadRecommend">刷新</el-button>
        </div>
      </template>
      <div v-loading="recLoading">
        <div v-if="recPapers.length === 0 && !recLoading" style="color:#909399;text-align:center;padding:20px">暂无推荐</div>
        <div v-for="p in recPapers" :key="p.paperId" class="rec-item" @click="handleDetail(p)">
          <div class="rec-title-row">
            <span class="rec-title">{{ p.title }}</span>
            <el-tag size="small" type="success" effect="plain">{{ p.citationCount || 0 }} 引用</el-tag>
          </div>
          <div class="rec-meta">
            {{ p.authors?.map(a => a.name).slice(0,3).join(', ') || '未知' }}
            <template v-if="p.year"> · {{ p.year }}</template>
            <template v-if="p.venue"> · {{ p.venue }}</template>
          </div>
          <div class="rec-abstract" v-if="p.abstractText">{{ p.abstractText.slice(0, 200) }}…</div>
        </div>
      </div>
    </el-card>

    <el-card style="margin-top: 16px" v-loading="loading">
      <el-table :data="papers" border stripe>
        <template #empty>
          <AppEmpty v-if="!loading" description="请搜索论文关键词" />
        </template>
        <el-table-column prop="title" label="论文标题" min-width="280" show-overflow-tooltip />
        <el-table-column label="作者" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.authors?.map(a => a.name).join(', ') || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="year" label="年份" width="80" />
        <el-table-column label="引用" width="80">
          <template #default="{ row }">
            {{ row.citationCount ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="venue" label="来源" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleDetail(row)">详情</el-button>
            <el-button v-if="row.url" size="small" @click="window.open(row.url, '_blank')">
              原文
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="searchForm.page"
        :page-size="searchForm.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handleSearch"
        class="pagination-bar"
      />
    </el-card>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailVisible" title="论文详情" width="720px">
      <template v-if="detail">
        <h3 style="margin: 0 0 8px">{{ detail.title }}</h3>
        <p style="color: #909399; margin: 0 0 16px">
          {{ detail.authors?.map(a => a.name).join(', ') }}
          <template v-if="detail.year"> · {{ detail.year }}</template>
          <template v-if="detail.venue"> · {{ detail.venue }}</template>
          <template v-if="detail.citationCount"> · 被引 {{ detail.citationCount }} 次</template>
        </p>
        <el-divider />
        <h4>摘要</h4>
        <p style="line-height: 1.8; color: #303133">{{ detail.abstractText || '暂无摘要' }}</p>
        <div style="margin-top: 16px" v-if="detail.openAccessPdf?.url || detail.url">
          <el-button type="primary" @click="window.open(detail.openAccessPdf?.url || detail.url, '_blank')">
            查看全文
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import request from '@/utils/request'

const userStore = useUserStore()

const searchForm = reactive({
  keyword: '',
  source: 'local',
  page: 1,
  size: 10
})

const papers = ref([])
const total = ref(0)
const loading = ref(false)
const importing = ref(false)
const recPapers = ref([])
const recLoading = ref(false)
const hasSearched = ref(false)

const detailVisible = ref(false)
const detail = ref(null)

const loadRecommend = async () => {
  recLoading.value = true
  try {
    const isStudent = userStore.roleCode === 'student'
    const params = isStudent ? { studentId: userStore.userId } : { teacherId: userStore.userId }
    const res = await request.get('/api/paper/recommend', { params })
    if (res.code === '200' && res.data) {
      recPapers.value = res.data.data || []
    }
  } catch { recPapers.value = [] }
  finally { recLoading.value = false }
}

const handleSearch = async () => {
  if (!searchForm.keyword.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  hasSearched.value = true
  loading.value = true
  try {
    const res = await request.get('/api/paper/search', {
      params: {
        keyword: searchForm.keyword.trim(),
        source: searchForm.source,
        page: searchForm.page,
        size: searchForm.size
      }
    })
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
    } else if (res.code === '429' && searchForm.source === 'semantic_scholar') {
      ElMessage.warning('Semantic Scholar 请求频率限制，已自动切换为 arXiv，请重试')
      searchForm.source = 'arxiv'
    } else {
      papers.value = []
      total.value = 0
    }
  } catch (e) {
    papers.value = []
    total.value = 0
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
    const res = await request.post('/api/paper/import', null, {
      params: { keyword: searchForm.keyword.trim(), maxResults: 20 }
    })
    if (res.code === '200') {
      ElMessage.success('导入成功')
      searchForm.source = 'local'
      handleSearch()
    } else {
      ElMessage.error(res.msg || '导入失败')
    }
  } catch (e) {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}

const handleDetail = async (row) => {
  try {
    const res = await request.get(`/api/paper/detail/${row.paperId}`)
    if (res.code === '200' && res.data) {
      detail.value = res.data
    } else {
      detail.value = row
    }
  } catch (e) {
    detail.value = row
  }
  detailVisible.value = true
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.page = 1
  hasSearched.value = false
  papers.value = []
  total.value = 0
  loadRecommend()
}

onMounted(() => { loadRecommend() })
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-bar { margin-top: 20px; justify-content: center; }
.rec-item {
  padding: 12px 0; border-bottom: 1px solid #f0f0f0; cursor: pointer;
  transition: background .15s;
}
.rec-item:last-child { border-bottom: none; }
.rec-item:hover { background: #fbf9f5; margin: 0 -8px; padding: 12px 8px; border-radius: 4px; }
.rec-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.rec-title { font-weight: 500; font-size: 14px; color: #303133; }
.rec-meta { font-size: 12px; color: #909399; margin-bottom: 4px; }
.rec-abstract { font-size: 12px; color: #909399; line-height: 1.6; }
</style>
