<template>
  <div class="recommendation-page workspace-page">
    <PageHeader eyebrow="AI 学伴" title="智能推荐" :description="summaryText">
      <template #actions>
        <StatusBadge label="规则打分，非模型生成" tone="positive" />
        <el-button type="primary" :loading="loading" @click="loadRecommendations">
          <el-icon class="el-icon--left"><Refresh /></el-icon>
          刷新
        </el-button>
      </template>
    </PageHeader>

    <ErrorNotice
      v-if="loadFailed"
      title="推荐未加载"
      message="推荐服务暂时不可用，请稍后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadRecommendations"
    />

    <section class="filter-panel filter-row" aria-label="筛选推荐">
      <el-radio-group v-model="recommendType" aria-label="推荐类型" @change="loadRecommendations">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="course">课程</el-radio-button>
        <el-radio-button label="lesson">课时</el-radio-button>
        <el-radio-button label="exercise">练习</el-radio-button>
        <el-radio-button label="paper">论文</el-radio-button>
      </el-radio-group>
      <div v-if="profileTags.length" class="profile-tags" aria-label="学习画像">
        <el-tag v-for="tag in profileTags" :key="tag" effect="plain">{{ tag }}</el-tag>
      </div>
    </section>

    <section class="metric-grid" aria-label="推荐概览">
      <div class="metric-tile"><span>平均推荐分</span><strong>{{ scoreStats.average }}</strong><small>{{ scoreStats.total }} 项候选</small></div>
      <div class="metric-tile"><span>最高推荐</span><strong>{{ scoreStats.topScore }}</strong><small class="tile-truncate">{{ scoreStats.topTitle }}</small></div>
      <div class="metric-tile"><span>高优先级</span><strong>{{ scoreStats.highCount }}</strong><small>80 分及以上</small></div>
      <div class="metric-tile"><span>排序方式</span><strong>分数</strong><small>高分优先</small></div>
    </section>

    <section class="metric-row" aria-label="按类型查看">
      <button
        v-for="metric in metrics"
        :key="metric.type"
        type="button"
        class="metric-card"
        :class="{ active: recommendType === metric.type }"
        :aria-pressed="recommendType === metric.type"
        @click="selectType(metric.type)"
      >
        <span class="metric-icon" :class="metric.tone">
          <el-icon aria-hidden="true"><component :is="metric.icon" /></el-icon>
        </span>
        <span>
          <strong>{{ metric.count }}</strong>
          <small>{{ metric.label }}</small>
        </span>
      </button>
    </section>

    <div v-loading="loading" class="recommendation-content">
      <div v-if="!loading && items.length === 0 && !loadFailed" class="empty-panel">
        <AppEmpty description="暂无推荐结果" />
      </div>

      <section v-for="section in visibleSections" :key="section.type" class="rec-section" :aria-label="section.title">
        <div class="section-head">
          <div>
            <h2>{{ section.title }}</h2>
            <p>{{ section.description }}</p>
          </div>
          <StatusBadge :label="`${section.items.length} 项`" />
        </div>

        <div class="rec-grid">
          <article v-for="item in section.items" :key="item.id" class="rec-card">
            <div class="rec-card-top">
              <span class="item-icon" :class="section.tone">
                <el-icon aria-hidden="true"><component :is="section.icon" /></el-icon>
              </span>
              <div class="score-stack">
                <div class="score-pill" :class="scoreLevel(item.score).className">{{ formatScore(item.score) }}</div>
                <small>{{ scoreLevel(item.score).label }}</small>
              </div>
            </div>

            <div class="rec-copy">
              <h3>{{ item.title }}</h3>
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
import { Collection, Document, Edit, Reading, Refresh } from '@element-plus/icons-vue'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { getMyRecommendations } from '@/api/recommendation'
import { requireSuccess } from '@/utils/apiHelpers'

const router = useRouter()
const recommendType = ref('all')
const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  items.value = []
  counts.value = {}
})
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

const summaryText = computed(() => summary.value || '根据你的课程、进度和成绩，按规则分排序生成推荐。')

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

function loadRecommendations() {
  return guarded(async (isCurrent) => {
    const res = requireSuccess(await getMyRecommendations({
      type: recommendType.value,
      limit: 24
    }))
    if (!isCurrent()) return
    const data = res.data || {}
    items.value = Array.isArray(data.items) ? data.items : []
    counts.value = data.counts || {}
    summary.value = data.summary || ''
    profileTags.value = Array.isArray(data.profileTags) ? data.profileTags : []
  })
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
.filter-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.tile-truncate {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-3);
}

.metric-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-3);
  color: inherit;
  text-align: left;
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: border-color .15s var(--ease-standard), background-color .15s var(--ease-standard);
}

.metric-card:hover {
  background: var(--surface-hover);
}

.metric-card.active {
  background: var(--surface-accent);
  border-color: var(--line-accent);
}

.metric-icon,
.item-icon {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
}

.metric-card strong {
  display: block;
  color: var(--text-strong);
  font-size: var(--text-xl);
  font-weight: 650;
  line-height: 1.2;
}

.metric-card small {
  display: block;
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.recommendation-content {
  display: grid;
  gap: var(--space-6);
  min-height: 240px;
}

.rec-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  gap: var(--space-3);
}

.rec-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-4);
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
}

.rec-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.score-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.score-stack small {
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.score-pill {
  min-width: 52px;
  padding: 2px var(--space-2);
  color: var(--neutral);
  text-align: center;
  background: var(--neutral-surface);
  border-radius: var(--radius-pill);
  font-size: var(--text-xs);
  font-weight: 650;
}

.score-strong { color: var(--positive); background: var(--positive-surface); }
.score-good { color: var(--accent); background: var(--surface-accent); }
.score-normal { color: var(--caution); background: var(--caution-surface); }
.score-low { color: var(--neutral); background: var(--neutral-surface); }

.rec-copy h3 {
  margin: 0 0 var(--space-1);
  font-size: var(--text-lg);
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.subtitle-line {
  margin: 0 0 var(--space-1);
  color: var(--text-subtle);
  font-size: var(--text-sm);
}

.description {
  margin: 0;
  color: var(--text-body);
  font-size: var(--text-sm);
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.score-bar {
  height: 6px;
  overflow: hidden;
  background: var(--surface-sunken);
  border-radius: var(--radius-pill);
}

.score-bar span {
  display: block;
  height: 100%;
  background: var(--accent);
  border-radius: inherit;
}

.reason-box {
  padding: var(--space-2) var(--space-3);
  color: var(--text-body);
  background: var(--surface-canvas);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  line-height: 1.55;
}

.reason-box span {
  display: block;
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.reason-box p {
  margin: 2px 0 0;
  overflow-wrap: anywhere;
}

.factor-list,
.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}

.factor-chip {
  padding: 2px var(--space-2);
  color: var(--text-subtle);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-pill);
  font-size: var(--text-xs);
}

.factor-chip b {
  margin-right: var(--space-1);
  color: var(--text-strong);
  font-weight: 600;
}

.meta-row span {
  padding: 2px var(--space-2);
  color: var(--text-subtle);
  background: var(--surface-sunken);
  border-radius: var(--radius-pill);
  font-size: var(--text-xs);
}

.card-actions {
  margin-top: auto;
}

.tone-blue { color: var(--accent); background: var(--surface-accent); }
.tone-green { color: var(--positive); background: var(--positive-surface); }
.tone-amber { color: var(--caution); background: var(--caution-surface); }
.tone-cyan { color: var(--accent); background: var(--surface-accent-strong); }
.tone-slate { color: var(--neutral); background: var(--neutral-surface); }

@media (max-width: 960px) {
  .metric-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .metric-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
