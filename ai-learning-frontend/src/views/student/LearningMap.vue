<template>
  <div class="learning-map" v-loading="loading">
    <section class="hero-card">
      <div>
        <p class="eyebrow">Amazing Teaching / 学习路线</p>
        <h1>{{ overview.title || '课程学习路线' }}</h1>
        <p class="hero-copy">{{ overview.tagline || '把课程拆成可见路径：先看全局，再逐节解锁。' }}</p>
        <div class="hero-tags">
          <el-tag effect="dark">{{ overview.domain || '精品课程' }}</el-tag>
          <el-tag type="success" effect="dark">{{ overview.totalLessons || experience.path.length }} 课时</el-tag>
          <el-tag type="warning" effect="dark">约 {{ overview.estimatedMinutes || estimatedMinutes }} 分钟</el-tag>
        </div>
      </div>
      <div class="hero-actions">
        <el-button type="primary" size="large" @click="openContinueLesson">继续学习</el-button>
        <el-button size="large" @click="openFirstUnlocked">从当前节点开始</el-button>
      </div>
    </section>

    <el-row :gutter="18" class="mt-18">
      <el-col :xs="24" :md="8">
        <el-card class="overview-card">
          <template #header>课程完成度</template>
          <el-progress type="dashboard" :percentage="courseProgress" :width="150" />
          <div class="metric-list">
            <div><strong>{{ unlockedCount }}</strong><span>已解锁</span></div>
            <div><strong>{{ doneCount }}</strong><span>已完成</span></div>
            <div><strong>{{ lockedCount }}</strong><span>战争迷雾</span></div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="16">
        <el-card class="outcome-card">
          <template #header>学完你将获得</template>
          <div class="outcome-grid">
            <div v-for="outcome in overview.outcomes || []" :key="outcome" class="outcome-item">{{ outcome }}</div>
          </div>
          <el-alert class="mt-12" type="success" :closable="false" :title="overview.finalProject || '完成一份可展示的学习作品'" />
        </el-card>
      </el-col>
    </el-row>

    <el-card class="mt-18 path-card">
      <template #header>
        <div class="card-header">
          <span>可见学习路径</span>
          <el-tag type="success">{{ unlockedCount }}/{{ experience.path.length }} 已解锁</el-tag>
        </div>
      </template>

      <el-empty v-if="!experience.path.length" description="这门课还没有发布章节课时，请等待教师完善课程内容" />

      <div v-else class="route-map">
        <section v-for="chapter in chapterGroups" :key="chapter.title" class="chapter-lane">
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
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const emptyExperience = () => ({ overview: {}, path: [], textbook: [], experiments: [], challenges: [], codeLabs: [] })
const experience = ref(emptyExperience())

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

const loadExperience = async () => {
  loading.value = true
  try {
    const res = await request.get(`/courses/${route.params.courseId}/experience`, { params: { classId: route.query.classId } })
    if (res.code === '200') {
      experience.value = { ...emptyExperience(), ...res.data }
    } else {
      experience.value = emptyExperience()
    }
  } catch {
    experience.value = emptyExperience()
    ElMessage.error('学习路线加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

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
.learning-map { --ink: #172033; }
.hero-card {
  min-height: 220px;
  border-radius: 30px;
  padding: 30px;
  color: white;
  background: radial-gradient(circle at 16% 20%, rgba(70,209,181,.55), transparent 28%), linear-gradient(135deg, #172033, #1d3b6b 55%, #623c8a);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  box-shadow: 0 18px 45px rgba(23,32,51,.24);
}
.eyebrow { letter-spacing: .18em; text-transform: uppercase; opacity: .78; font-size: 12px; }
.hero-card h1 { margin: 8px 0; font-size: clamp(30px, 4vw, 52px); }
.hero-copy { max-width: 760px; opacity: .9; line-height: 1.8; }
.hero-tags, .hero-actions, .card-header { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.hero-actions { justify-content: flex-end; }
.mt-18 { margin-top: 18px; }
.mt-12 { margin-top: 12px; }
.overview-card { text-align: center; }
.metric-list { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 14px; }
.metric-list div { background: #f8fafc; border-radius: 14px; padding: 10px; display: grid; gap: 4px; }
.metric-list strong { color: #2563eb; font-size: 22px; }
.metric-list span { color: #64748b; font-size: 12px; }
.outcome-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; }
.outcome-item { border: 1px solid #dbeafe; background: #eff6ff; color: #1e3a8a; border-radius: 14px; padding: 12px; line-height: 1.6; }
.route-map { display: grid; gap: 18px; }
.chapter-lane { display: grid; grid-template-columns: 220px 1fr; gap: 16px; align-items: flex-start; }
.chapter-title { position: sticky; top: 82px; display: flex; gap: 10px; align-items: center; padding: 14px; border-radius: 18px; background: #f8fafc; border: 1px solid #e2e8f0; }
.chapter-title > span { width: 34px; height: 34px; border-radius: 50%; background: #2563eb; color: #fff; display: grid; place-items: center; font-weight: 800; }
.chapter-title strong { display: block; color: #172033; }
.chapter-title small { color: #64748b; }
.node-chain { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; }
.map-node {
  min-height: 150px;
  border: 1px solid #dbeafe;
  border-radius: 22px;
  background: linear-gradient(135deg, #eff6ff, #fff);
  cursor: pointer;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 10px;
  padding: 16px;
  text-align: left;
  transition: transform .2s ease, box-shadow .2s ease;
}
.map-node:hover { transform: translateY(-2px); box-shadow: 0 14px 28px rgba(37,99,235,.12); }
.map-node.locked { color: #e2e8f0; background: radial-gradient(circle at 30% 30%, #64748b, #0f172a); border-color: #334155; }
.map-node.done { border-color: #22c55e; box-shadow: inset 0 0 0 2px rgba(34,197,94,.18); }
.map-node.active { outline: 3px solid rgba(245,158,11,.28); }
.node-status { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; background: #2563eb; color: #fff; font-weight: 900; }
.map-node.done .node-status { background: #16a34a; }
.map-node.locked .node-status { background: #475569; }
.node-copy { display: grid; gap: 8px; }
.node-copy small { line-height: 1.6; color: inherit; opacity: .78; }
@media (max-width: 900px) {
  .hero-card { align-items: flex-start; flex-direction: column; }
  .chapter-lane { grid-template-columns: 1fr; }
  .chapter-title { position: static; }
}
</style>
