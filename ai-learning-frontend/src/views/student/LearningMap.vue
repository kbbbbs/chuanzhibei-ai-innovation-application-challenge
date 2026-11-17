<template>
  <div class="learning-map workspace-page" v-loading="loading">
    <PageHeader
      eyebrow="学习路线"
      :title="overview.title || '课程学习路线'"
      :description="overview.tagline || '把课程拆成可见路径：先看全局，再逐节解锁。'"
    >
      <template #actions>
        <el-button type="primary" @click="openContinueLesson">继续学习</el-button>
        <el-button @click="openFirstUnlocked">从当前节点开始</el-button>
      </template>
    </PageHeader>

    <ErrorNotice
      v-if="loadFailed"
      title="学习路线未加载"
      message="请确认网络后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadExperience"
    />

    <template v-else>
    <div class="course-facts">
      <StatusBadge :label="overview.domain || '精品课程'" tone="accent" />
      <StatusBadge :label="`${overview.totalLessons || experience.path.length} 课时`" />
      <StatusBadge :label="`约 ${overview.estimatedMinutes || estimatedMinutes} 分钟`" />
    </div>

    <div class="overview-grid">
      <section class="content-panel overview-card" aria-labelledby="progress-heading">
        <h2 id="progress-heading">课程完成度</h2>
        <el-progress type="dashboard" :percentage="courseProgress" :width="132" />
        <div class="metric-list">
          <div><strong>{{ unlockedCount }}</strong><span>已解锁</span></div>
          <div><strong>{{ doneCount }}</strong><span>已完成</span></div>
          <div><strong>{{ lockedCount }}</strong><span>战争迷雾</span></div>
        </div>
      </section>
      <section class="content-panel" aria-labelledby="outcome-heading">
        <h2 id="outcome-heading">学完你将获得</h2>
        <div v-if="overview.outcomes?.length" class="outcome-grid">
          <div v-for="outcome in overview.outcomes" :key="outcome" class="outcome-item">{{ outcome }}</div>
        </div>
        <p class="final-project">{{ overview.finalProject || '完成一份可展示的学习作品' }}</p>
      </section>
    </div>

    <section class="content-panel" aria-labelledby="path-heading">
      <div class="section-head">
        <h2 id="path-heading">可见学习路径</h2>
        <StatusBadge :label="`${unlockedCount}/${experience.path.length} 已解锁`" tone="positive" />
      </div>

      <AppEmpty v-if="!experience.path.length && !loading" description="这门课还没有发布章节课时，请等待教师完善课程内容" />

      <div v-else class="route-map">
        <section v-for="chapter in chapterGroups" :key="chapter.title" class="chapter-lane" :aria-label="chapter.title">
          <div class="chapter-title">
            <span>{{ chapter.index }}</span>
            <div>
              <strong>{{ chapter.title }}</strong>
              <small>{{ chapter.nodes.length }} 个节点</small>
            </div>
          </div>

          <div class="node-chain">
            <button
              v-for="node in chapter.nodes"
              :key="node.id"
              class="map-node"
              :class="{ locked: !node.unlocked, done: node.progress >= 100, active: node.id === continueNode?.id }"
              type="button"
              :aria-disabled="!node.unlocked"
              :aria-current="node.id === continueNode?.id ? 'step' : undefined"
              @click="openLesson(node)"
            >
              <span class="node-status">{{ node.progress >= 100 ? '✓' : node.unlocked ? '▶' : '雾' }}</span>
              <div class="node-copy">
                <strong>{{ node.unlocked ? node.title : '未知区域' }}</strong>
                <small>{{ node.unlocked ? node.summary : '被战争迷雾遮蔽：先完成上一节点' }}</small>
              </div>
              <el-progress :percentage="node.progress || 0" :show-text="false" />
            </button>
          </div>
        </section>
      </div>
    </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { requireSuccess } from '@/utils/apiHelpers'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const emptyExperience = () => ({ overview: {}, path: [], textbook: [], experiments: [], challenges: [], codeLabs: [] })
const experience = ref(emptyExperience())
const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  experience.value = emptyExperience()
})

const overview = computed(() => experience.value.overview || {})
const unlockedCount = computed(() => experience.value.path.filter(node => node.unlocked).length)
const doneCount = computed(() => experience.value.path.filter(node => Number(node.progress) >= 100).length)
const lockedCount = computed(() => Math.max(0, experience.value.path.length - unlockedCount.value))
const estimatedMinutes = computed(() => Math.round(experience.value.path.reduce((sum, node) => sum + Number(node.duration || 0), 0) / 60))
const courseProgress = computed(() => experience.value.path.length ? Math.round(doneCount.value / experience.value.path.length * 100) : 0)
const continueNode = computed(() => experience.value.path.find(node => node.unlocked && Number(node.progress) < 100) || [...experience.value.path].reverse().find(node => node.unlocked) || experience.value.path[0])

const chapterGroups = computed(() => {
  const groups = []
  const byTitle = new Map()
  for (const node of experience.value.path) {
    const title = node.chapterTitle || '课程路径'
    if (!byTitle.has(title)) {
      const group = { title, nodes: [], index: groups.length + 1 }
      groups.push(group)
      byTitle.set(title, group)
    }
    byTitle.get(title).nodes.push(node)
  }
  return groups
})

const loadExperience = () => guarded(async (isCurrent) => {
  const res = requireSuccess(await request.get(`/courses/${route.params.courseId}/experience`, { params: { classId: route.query.classId } }))
  if (!isCurrent()) return
  experience.value = { ...emptyExperience(), ...res.data }
})

const openContinueLesson = () => {
  if (continueNode.value) openLesson(continueNode.value)
}

const openFirstUnlocked = () => {
  const node = continueNode.value || experience.value.path[0]
  if (node) openLesson(node)
}

const openLesson = node => {
  if (!node?.unlocked) {
    ElMessage.info('先完成前一个节点来驱散战争迷雾')
    return
  }
  router.push({ name: 'CourseLearn', params: { courseId: route.params.courseId }, query: { ...route.query, lessonId: node.id } })
}

onMounted(loadExperience)
</script>

<style scoped>
.course-facts { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.overview-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 2fr); gap: var(--space-5); }
.overview-grid h2 { margin: 0 0 var(--space-3); font-size: var(--text-lg); }
.overview-card { text-align: center; }
.metric-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-2); margin-top: var(--space-3); }
.metric-list div { display: grid; gap: 2px; padding: var(--space-2); background: var(--surface-canvas); border-radius: var(--radius-md); }
.metric-list strong { color: var(--accent); font-size: var(--text-xl); font-weight: 650; }
.metric-list span { color: var(--text-subtle); font-size: var(--text-xs); }
.outcome-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 180px), 1fr)); gap: var(--space-2); }
.outcome-item { padding: var(--space-3); color: var(--text-body); background: var(--surface-accent); border: 1px solid var(--line-accent); border-radius: var(--radius-md); line-height: 1.6; }
.final-project { margin: var(--space-3) 0 0; padding: var(--space-3); color: var(--text-body); background: var(--positive-surface); border: 1px solid var(--positive-line); border-radius: var(--radius-md); }
.route-map { display: grid; gap: var(--space-4); }
.chapter-lane { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: var(--space-4); align-items: flex-start; }
.chapter-title { position: sticky; top: 0; display: flex; gap: var(--space-2); align-items: center; padding: var(--space-3); background: var(--surface-canvas); border: 1px solid var(--line-subtle); border-radius: var(--radius-md); }
.chapter-title > span { flex: 0 0 auto; display: grid; place-items: center; width: 30px; height: 30px; color: #fff; background: var(--accent-solid); border-radius: 50%; font-weight: 650; }
.chapter-title strong { display: block; color: var(--text-strong); font-weight: 600; overflow-wrap: anywhere; }
.chapter-title small { color: var(--text-subtle); font-size: var(--text-xs); }
.node-chain { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr)); gap: var(--space-3); }
.map-node {
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: var(--space-2);
  min-height: 144px;
  padding: var(--space-4);
  color: var(--text-strong);
  text-align: left;
  background: var(--surface-accent);
  border: 1px solid var(--line-accent);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color .15s var(--ease-standard), background-color .15s var(--ease-standard);
}
.map-node:hover { background: var(--surface-accent-strong); }
.map-node.locked { color: var(--text-subtle); background: var(--surface-sunken); border-color: var(--line-subtle); border-style: dashed; cursor: not-allowed; }
.map-node.done, .map-node.done:hover { background: var(--positive-surface); border-color: var(--positive-line); }
.map-node.active { box-shadow: 0 0 0 2px var(--line-accent); }
.node-status { display: grid; place-items: center; width: 30px; height: 30px; color: #fff; background: var(--accent-solid); border-radius: 50%; font-weight: 650; }
.map-node.done .node-status { color: var(--surface-base); background: var(--positive); }
.map-node.locked .node-status { color: var(--surface-base); background: var(--text-subtle); }
.node-copy { display: grid; gap: var(--space-1); min-width: 0; }
.node-copy strong { font-weight: 600; overflow-wrap: anywhere; }
.node-copy small { color: var(--text-subtle); font-size: var(--text-xs); line-height: 1.6; overflow-wrap: anywhere; }
@media (max-width: 900px) {
  .overview-grid, .chapter-lane { grid-template-columns: minmax(0, 1fr); }
  .chapter-title { position: static; }
}
</style>
