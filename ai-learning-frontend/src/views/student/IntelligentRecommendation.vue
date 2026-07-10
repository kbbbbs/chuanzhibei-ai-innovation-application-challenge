<template>
  <div class="recommendation-page">
    <section class="page-header">
      <div>
        <h2>纯打分推荐</h2>
        <p class="subtitle">{{ summaryText }}</p>
      </div>
      <div class="header-actions">
        <el-tag effect="plain" type="success">规则分</el-tag>
        <el-button type="primary" :loading="loading" @click="loadRecommendations">
          <el-icon class="el-icon--left"><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </section>

    <section class="filter-bar">
      <el-radio-group v-model="recommendType" @change="loadRecommendations">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="course">课程</el-radio-button>
        <el-radio-button label="lesson">课时</el-radio-button>
        <el-radio-button label="exercise">练习</el-radio-button>
        <el-radio-button label="paper">论文</el-radio-button>
      </el-radio-group>
      <div class="profile-tags">
        <el-tag v-for="tag in profileTags" :key="tag" effect="plain">{{ tag }}</el-tag>
      </div>
    </section>

    <section class="score-overview">
      <div class="score-panel primary-score">
        <span class="panel-label">平均推荐分</span>
        <strong>{{ scoreStats.average }}</strong>
        <small>{{ scoreStats.total }} 项候选</small>
      </div>
      <div class="score-panel">
        <span class="panel-label">最高推荐</span>
        <strong>{{ scoreStats.topScore }}</strong>
        <small>{{ scoreStats.topTitle }}</small>
      </div>
      <div class="score-panel">
        <span class="panel-label">高优先级</span>
        <strong>{{ scoreStats.highCount }}</strong>
        <small>80 分及以上</small>
      </div>
      <div class="score-panel">
        <span class="panel-label">排序方式</span>
        <strong>分数</strong>
        <small>高分优先</small>
      </div>
    </section>

    <section class="metric-row">
      <button
        v-for="metric in metrics"
        :key="metric.type"
        type="button"
        class="metric-card"
        :class="{ active: recommendType === metric.type }"
        @click="selectType(metric.type)"
      >
        <span class="metric-icon" :class="metric.tone">
          <el-icon><component :is="metric.icon" /></el-icon>
        </span>
        <span>
          <strong>{{ metric.count }}</strong>
          <small>{{ metric.label }}</small>
        </span>
      </button>
    </section>

    <div v-loading="loading" class="recommendation-content">
      <el-empty v-if="!loading && items.length === 0" description="暂无推荐结果" />

      <section v-for="section in visibleSections" :key="section.type" class="rec-section">
        <div class="section-header">
          <div>
            <h3>{{ section.title }}</h3>
            <p>{{ section.description }}</p>
          </div>
          <el-tag effect="plain">{{ section.items.length }} 项</el-tag>
        </div>

        <div class="rec-grid">
          <article v-for="item in section.items" :key="item.id" class="rec-card">
            <div class="rec-card-top">
              <span class="item-icon" :class="section.tone">
                <el-icon><component :is="section.icon" /></el-icon>
              </span>
              <div class="score-stack">
                <div class="score-pill" :class="scoreLevel(item.score).className">{{ formatScore(item.score) }}</div>
                <small>{{ scoreLevel(item.score).label }}</small>
              </div>
            </div>

            <div class="rec-copy">
              <el-tag size="small" effect="plain">{{ section.title }}</el-tag>
              <h4>{{ item.title }}</h4>
              <p class="subtitle-line">{{ item.subtitle || fallbackSubtitle(section.type) }}</p>
              <p class="description">{{ item.description || item.reason }}</p>
            </div>

            <div class="score-bar" aria-hidden="true">
              <span :style="{ width: scorePercent(item.score) }"></span>
            </div>

            <div class="reason-box">
              <span>推荐理由</span>
              <p>{{ item.reason }}</p>
            </div>

            <div class="factor-list">
              <span
                v-for="factor in scoreFactors(item, section.type)"
                :key="factor.label"
                class="factor-chip"
              >
                <b>{{ factor.label }}</b>
                {{ factor.value }}
              </span>
            </div>

            <div class="meta-row">
              <span v-if="item.meta?.progress !== undefined">进度 {{ item.meta.progress }}%</span>
              <span v-if="item.meta?.difficulty">难度 {{ item.meta.difficulty }}</span>
              <span v-if="item.meta?.rating">评分 {{ item.meta.rating }}</span>
              <span v-if="item.meta?.citationCount">引用 {{ item.meta.citationCount }}</span>
              <span v-if="item.meta?.year">{{ item.meta.year }}</span>
            </div>

            <div class="card-actions">
              <el-button type="primary" size="small" @click="openItem(item)">
                {{ item.actionText || '查看' }}
              </el-button>
            </div>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Collection, Document, Edit, Reading, Refresh } from '@element-plus/icons-vue'
import { getMyRecommendations } from '@/api/recommendation'

const router = useRouter()
const recommendType = ref('all')
const loading = ref(false)
const items = ref([])
const counts = ref({})
const summary = ref('')
const profileTags = ref([])

const sectionConfig = {
  course: {
    title: '课程推荐',
    description: '根据已学课程方向、课程热度和难度递进生成。',
    icon: Reading,
    tone: 'tone-blue'
  },
  lesson: {
    title: '继续学习',
    description: '优先推荐未完成课时和需要补强的课程内容。',
    icon: Collection,
    tone: 'tone-green'
  },
  exercise: {
    title: '巩固练习',
    description: '根据成绩薄弱课程和题库知识点生成练习方向。',
    icon: Edit,
    tone: 'tone-amber'
  },
  paper: {
    title: '论文资料',
    description: '从本地论文库中召回与当前学习方向相关的拓展资料。',
    icon: Document,
    tone: 'tone-cyan'
  }
}

const metrics = computed(() => [
  { type: 'all', label: '全部推荐', count: items.value.length, icon: Collection, tone: 'tone-slate' },
  { type: 'course', label: '课程', count: counts.value.course || 0, icon: Reading, tone: 'tone-blue' },
  { type: 'lesson', label: '课时', count: counts.value.lesson || 0, icon: Collection, tone: 'tone-green' },
  { type: 'exercise', label: '练习', count: counts.value.exercise || 0, icon: Edit, tone: 'tone-amber' },
  { type: 'paper', label: '论文', count: counts.value.paper || 0, icon: Document, tone: 'tone-cyan' }
])

const summaryText = computed(() => summary.value || '正在根据你的课程、进度和成绩生成推荐。')

const scoreStats = computed(() => {
  const list = items.value
  if (!list.length) {
    return {
      total: 0,
      average: '0',
      topScore: '0',
      topTitle: '暂无推荐',
      highCount: 0
    }
  }
  const scores = list.map(item => Number(item.score || 0))
  const topItem = list.reduce((best, item) => Number(item.score || 0) > Number(best.score || 0) ? item : best, list[0])
  const average = scores.reduce((sum, score) => sum + score, 0) / scores.length
  return {
    total: list.length,
    average: Math.round(average).toString(),
    topScore: Math.round(Number(topItem.score || 0)).toString(),
    topTitle: topItem.title || '暂无标题',
    highCount: scores.filter(score => score >= 80).length
  }
})

const visibleSections = computed(() => {
  const grouped = items.value.reduce((acc, item) => {
    const type = item.type || 'course'
    if (!acc[type]) acc[type] = []
    acc[type].push(item)
    return acc
  }, {})

  return Object.entries(sectionConfig)
    .filter(([type]) => recommendType.value === 'all' || recommendType.value === type)
    .map(([type, config]) => ({
      type,
      ...config,
      items: grouped[type] || []
    }))
    .filter(section => section.items.length > 0)
})

async function loadRecommendations() {
  loading.value = true
  try {
    const res = await getMyRecommendations({
      type: recommendType.value,
      limit: 24
    })
    if (res.code === '200' && res.data) {
      items.value = Array.isArray(res.data.items) ? res.data.items : []
      counts.value = res.data.counts || {}
      summary.value = res.data.summary || ''
      profileTags.value = Array.isArray(res.data.profileTags) ? res.data.profileTags : []
    } else {
      items.value = []
      counts.value = {}
    }
  } catch {
    items.value = []
    counts.value = {}
    ElMessage.error('推荐加载失败')
  } finally {
    loading.value = false
  }
}

function selectType(type) {
  recommendType.value = type
  loadRecommendations()
}

function openItem(item) {
  if (!item?.targetUrl) return
  router.push(item.targetUrl)
}

function formatScore(score) {
  const value = Number(score || 0)
  return `${Math.round(value)}分`
}

function scorePercent(score) {
  const value = Math.max(0, Math.min(100, Number(score || 0)))
  return `${value}%`
}

function scoreLevel(score) {
  const value = Number(score || 0)
  if (value >= 85) return { label: '强推荐', className: 'score-strong' }
  if (value >= 75) return { label: '优先看', className: 'score-good' }
  if (value >= 60) return { label: '可补充', className: 'score-normal' }
  return { label: '低优先', className: 'score-low' }
}

function scoreFactors(item, type) {
  const meta = item.meta || {}
  const factors = []
  if (type === 'course') {
    if (meta.rating) factors.push({ label: '评分', value: meta.rating })
    if (meta.studentCount !== undefined) factors.push({ label: '学习人数', value: meta.studentCount })
    if (meta.difficulty) factors.push({ label: '难度', value: meta.difficulty })
  } else if (type === 'lesson') {
    if (meta.progress !== undefined) factors.push({ label: '进度', value: `${meta.progress}%` })
    if (meta.duration) factors.push({ label: '时长', value: `${Math.max(1, Math.round(Number(meta.duration) / 60))} 分钟` })
  } else if (type === 'exercise') {
    if (meta.difficulty) factors.push({ label: '难度', value: meta.difficulty })
    if (meta.courseId) factors.push({ label: '课程', value: `#${meta.courseId}` })
  } else if (type === 'paper') {
    if (meta.citationCount !== undefined) factors.push({ label: '引用', value: meta.citationCount })
    if (meta.year) factors.push({ label: '年份', value: meta.year })
  }
  factors.push({ label: '总分', value: formatScore(item.score) })
  return factors.slice(0, 4)
}

function fallbackSubtitle(type) {
  return sectionConfig[type]?.title || '推荐内容'
}

onMounted(() => {
  loadRecommendations()
})
</script>

<style scoped>
.recommendation-page {
  max-width: 1180px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0;
}

.subtitle {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.6;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  background: white;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: var(--card-shadow);
  margin-bottom: 16px;
}

.score-overview {
  display: grid;
  grid-template-columns: 1.25fr 1.5fr 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}

.score-panel {
  min-height: 92px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: white;
  box-shadow: var(--card-shadow);
}

.primary-score {
  border-color: rgba(37, 99, 235, 0.28);
  background: linear-gradient(180deg, #FFFFFF 0%, #EFF6FF 100%);
}

.panel-label {
  display: block;
  color: var(--text-secondary);
  font-size: 0.82rem;
  margin-bottom: 8px;
}

.score-panel strong {
  display: block;
  color: var(--text-primary);
  font-size: 1.7rem;
  line-height: 1;
}

.score-panel small {
  display: block;
  margin-top: 8px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.metric-card {
  min-height: 78px;
  border: 1px solid var(--border-color);
  background: white;
  border-radius: 8px;
  padding: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
  cursor: pointer;
  box-shadow: var(--card-shadow);
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.metric-card:hover,
.metric-card.active {
  border-color: var(--primary-color);
  transform: translateY(-2px);
}

.metric-icon,
.item-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}

.metric-card strong {
  display: block;
  font-size: 1.35rem;
  color: var(--text-primary);
  line-height: 1;
}

.metric-card small {
  display: block;
  margin-top: 6px;
  color: var(--text-secondary);
}

.recommendation-content {
  min-height: 360px;
}

.rec-section {
  margin-bottom: 28px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 12px;
}

.section-header h3 {
  margin: 0 0 4px;
  font-size: 1.15rem;
  color: var(--text-primary);
  letter-spacing: 0;
}

.section-header p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 0.92rem;
}

.rec-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}

.rec-card {
  min-height: 306px;
  display: flex;
  flex-direction: column;
  background: white;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 16px;
  box-shadow: var(--card-shadow);
}

.rec-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.score-pill {
  min-width: 52px;
  text-align: center;
  padding: 4px 8px;
  border-radius: 999px;
  background: #F8FAFC;
  color: var(--text-secondary);
  font-size: 0.8rem;
  font-weight: 600;
}

.score-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.score-stack small {
  color: var(--text-secondary);
  font-size: 0.74rem;
}

.score-strong {
  background: #ECFDF5;
  color: #047857;
}

.score-good {
  background: #EFF6FF;
  color: #1D4ED8;
}

.score-normal {
  background: #FFFBEB;
  color: #B45309;
}

.score-low {
  background: #F1F5F9;
  color: #64748B;
}

.rec-copy h4 {
  min-height: 48px;
  margin: 10px 0 6px;
  color: var(--text-primary);
  font-size: 1rem;
  line-height: 1.45;
  letter-spacing: 0;
}

.subtitle-line {
  margin: 0 0 8px;
  color: var(--text-secondary);
  font-size: 0.86rem;
}

.description {
  min-height: 42px;
  margin: 0;
  color: var(--text-secondary);
  font-size: 0.9rem;
  line-height: 1.55;
}

.score-bar {
  height: 6px;
  margin-top: 14px;
  overflow: hidden;
  border-radius: 999px;
  background: #E2E8F0;
}

.score-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563EB 0%, #059669 100%);
}

.reason-box {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #F8FAFC;
  color: var(--text-primary);
  font-size: 0.88rem;
  line-height: 1.55;
}

.reason-box span {
  display: block;
  margin-bottom: 4px;
  color: var(--text-secondary);
  font-size: 0.78rem;
}

.reason-box p {
  margin: 0;
}

.factor-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.factor-chip {
  padding: 5px 8px;
  border: 1px solid #E2E8F0;
  border-radius: 999px;
  color: var(--text-secondary);
  font-size: 0.78rem;
  line-height: 1;
  background: white;
}

.factor-chip b {
  margin-right: 4px;
  color: var(--text-primary);
  font-weight: 600;
}

.meta-row {
  min-height: 24px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.meta-row span {
  padding: 3px 8px;
  border-radius: 999px;
  background: #F1F5F9;
  color: var(--text-secondary);
  font-size: 0.78rem;
}

.card-actions {
  margin-top: auto;
  padding-top: 16px;
}

.tone-blue { background: #EFF6FF; color: #2563EB; }
.tone-green { background: #ECFDF5; color: #059669; }
.tone-amber { background: #FFFBEB; color: #D97706; }
.tone-cyan { background: #ECFEFF; color: #0891B2; }
.tone-slate { background: #F1F5F9; color: #475569; }

@media (max-width: 960px) {
  .page-header,
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .profile-tags {
    justify-content: flex-start;
  }

  .metric-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .score-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .metric-row,
  .score-overview,
  .rec-grid {
    grid-template-columns: 1fr;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
