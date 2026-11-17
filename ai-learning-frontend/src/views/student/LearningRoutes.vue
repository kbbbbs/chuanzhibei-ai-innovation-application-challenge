<template>
  <div class="learning-routes workspace-page" v-loading="loading">
    <PageHeader
      eyebrow="学习"
      title="学习路线"
      description="每门课都有独立地图、战争迷雾、阶段目标和继续学习入口，沿着清晰路线逐步点亮能力节点。"
    />

    <ErrorNotice
      v-if="loadFailed"
      title="学习路线未加载"
      message="请确认网络后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadRoutes"
    />
    <ErrorNotice
      v-if="skippedCount"
      title="部分课程路线未加载"
      :message="`有 ${skippedCount} 门课程读取失败，下方路线不完整，路线概览已隐藏。`"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadRoutes"
    />

    <section v-if="!loadFailed && !skippedCount" class="metric-grid" aria-label="路线概览">
      <div class="metric-tile"><span>已加入课程</span><strong>{{ routes.length }}</strong></div>
      <div class="metric-tile"><span>已解锁节点</span><strong>{{ totalUnlocked }}</strong></div>
      <div class="metric-tile"><span>已完成节点</span><strong>{{ totalDone }}</strong></div>
    </section>

    <div v-if="!loading && !routes.length && !loadFailed && !skippedCount" class="empty-panel">
      <AppEmpty description="你还没有加入教学班。加入班级后，课程路线会在这里形成地图。" />
    </div>

    <section v-else-if="routes.length" class="atlas-grid" aria-label="课程路线">
      <article v-for="item in routes" :key="`${item.classId}-${item.courseId}`" class="route-card">
        <div class="route-card-head">
          <div class="route-title">
            <StatusBadge :label="item.domain || '精品课程'" tone="accent" />
            <h2>{{ item.courseName }}</h2>
            <p>{{ item.tagline || item.courseDescription || '按章节解锁课本、实验、练习和项目。' }}</p>
          </div>
          <el-progress type="dashboard" :percentage="item.progressPercent" :width="92" />
        </div>

        <div class="class-strip">
          <span>班级：{{ item.className }}</span>
          <span>{{ item.semester || '当前学期' }}</span>
          <span>{{ item.path.length }} 个节点</span>
        </div>

        <div class="mini-map" :aria-label="`${item.courseName} 学习路线预览`">
          <button
            v-for="node in item.path.slice(0, 10)"
            :key="node.id"
            type="button"
            class="mini-node"
            :class="{ locked: !node.unlocked, done: node.progress >= 100, current: node.id === item.currentNode?.id }"
            :aria-disabled="!node.unlocked"
            @click="openNode(item, node)"
          >
            <span class="node-mark">{{ node.progress >= 100 ? '✓' : node.unlocked ? '▶' : '雾' }}</span>
            <strong>{{ node.unlocked ? node.title : '未知区域' }}</strong>
            <small>{{ node.chapterTitle }}</small>
          </button>
          <div v-if="item.path.length > 10" class="more-node">+{{ item.path.length - 10 }} 节</div>
        </div>

        <div v-if="item.outcomes.length" class="outcomes">
          <span v-for="outcome in item.outcomes.slice(0, 4)" :key="outcome">{{ outcome }}</span>
        </div>

        <div class="route-actions">
          <el-button type="primary" @click="openMap(item)">打开完整学习地图</el-button>
          <el-button @click="continueLearn(item)">进入当前课时</el-button>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { getRecords, requireSuccess } from '@/utils/apiHelpers'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()
const routes = ref([])
const skippedCount = ref(0)
const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  routes.value = []
  skippedCount.value = 0
})

const totalUnlocked = computed(() => routes.value.reduce((sum, item) => sum + item.path.filter(node => node.unlocked).length, 0))
const totalDone = computed(() => routes.value.reduce((sum, item) => sum + item.path.filter(node => Number(node.progress) >= 100).length, 0))

const loadRouteRow = async (membership) => {
  const classRes = requireSuccess(await request.get(`/teaching-classes/${membership.classId}`, { silentError: true }))
  const teachingClass = classRes.data
  if (!teachingClass?.courseId) return null
  const [courseRes, experienceRes] = await Promise.all([
    request.get(`/courses/${teachingClass.courseId}`, { silentError: true }),
    request.get(`/courses/${teachingClass.courseId}/experience`, {
      params: { classId: membership.classId },
      silentError: true,
    }),
  ])
  const course = requireSuccess(courseRes).data
  const experience = requireSuccess(experienceRes).data || { overview: {}, path: [] }
  if (!course?.id || !course?.title) return null
  const path = experience.path || []
  const done = path.filter(node => Number(node.progress) >= 100).length
  return {
    classId: membership.classId,
    courseId: teachingClass.courseId,
    className: teachingClass.className || `教学班 ${membership.classId}`,
    semester: teachingClass.semester || '',
    courseName: course.title || experience.overview?.title || '未命名课程',
    courseDescription: course.description || '',
    domain: experience.overview?.domain || '',
    tagline: experience.overview?.tagline || '',
    outcomes: experience.overview?.outcomes || [],
    path,
    currentNode: findCurrentNode(path),
    progressPercent: path.length ? Math.round((done / path.length) * 100) : 0,
  }
}

const loadRoutes = () => guarded(async (isCurrent) => {
  const membershipsRes = requireSuccess(await request.get(`/class-students/student/${userStore.userId}`))
  if (!isCurrent()) return
  const memberships = getRecords(membershipsRes.data).filter(isActiveMembership)
  const rows = []
  let skipped = 0
  for (const membership of memberships) {
    try {
      const row = await loadRouteRow(membership)
      if (row) rows.push(row)
    } catch {
      // A course that fails must not read as zero progress, nor hide the other routes.
      skipped += 1
    }
    if (!isCurrent()) return
  }
  routes.value = rows
  skippedCount.value = skipped
})

const isActiveMembership = (item) => Number(item.status ?? 1) === 1

const findCurrentNode = (path) => {
  return path.find(node => node.unlocked && Number(node.progress) < 100)
    || [...path].reverse().find(node => node.unlocked)
    || path[0]
}

const openMap = (item) => {
  if (!item?.courseId || !item?.classId) {
    ElMessage.warning('课程信息不完整，暂不能打开学习地图')
    return
  }
  router.push({ name: 'LearningMap', params: { courseId: item.courseId }, query: { classId: item.classId } })
}

const continueLearn = (item) => {
  if (!item?.courseId || !item?.classId) {
    ElMessage.warning('课程信息不完整，暂不能进入当前课时')
    return
  }
  const lessonId = item.currentNode?.id
  router.push({
    name: 'CourseLearn',
    params: { courseId: item.courseId },
    query: lessonId ? { classId: item.classId, lessonId } : { classId: item.classId },
  })
}

const openNode = (item, node) => {
  if (!node.unlocked) {
    ElMessage.info('先完成前一个节点来驱散战争迷雾')
    return
  }
  router.push({
    name: 'CourseLearn',
    params: { courseId: item.courseId },
    query: { classId: item.classId, lessonId: node.id },
  })
}

onMounted(loadRoutes)
</script>

<style scoped>
.atlas-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr));
  gap: var(--space-4);
}

.route-card {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
  padding: var(--space-5);
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
}

.route-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-4);
}

.route-title {
  display: grid;
  justify-items: start;
  gap: var(--space-2);
  min-width: 0;
}

.route-card h2 {
  margin: 0;
  font-size: var(--text-xl);
  overflow-wrap: anywhere;
}

.route-card p {
  margin: 0;
  color: var(--text-subtle);
  line-height: 1.7;
}

.class-strip,
.route-actions,
.outcomes {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.route-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.class-strip span {
  padding: 2px var(--space-2);
  color: var(--text-body);
  background: var(--surface-sunken);
  border-radius: var(--radius-pill);
  font-size: var(--text-xs);
}

.mini-map {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--space-2);
}

.mini-node {
  display: grid;
  align-content: start;
  gap: var(--space-1);
  min-height: 112px;
  padding: var(--space-3);
  color: var(--text-strong);
  text-align: left;
  background: var(--surface-accent);
  border: 1px solid var(--line-accent);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color .15s var(--ease-standard), background-color .15s var(--ease-standard);
}

.mini-node:hover {
  background: var(--surface-accent-strong);
}

.node-mark {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  color: #fff;
  background: var(--accent-solid);
  border-radius: 50%;
  font-size: var(--text-xs);
  font-weight: 650;
}

.mini-node strong {
  font-size: var(--text-sm);
  font-weight: 600;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.mini-node small {
  color: var(--text-subtle);
  font-size: var(--text-xs);
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.mini-node.done {
  background: var(--positive-surface);
  border-color: var(--positive-line);
}

.mini-node.done:hover {
  background: var(--positive-surface);
}

.mini-node.done .node-mark {
  color: var(--surface-base);
  background: var(--positive);
}

.mini-node.current {
  box-shadow: 0 0 0 2px var(--line-accent);
}

.mini-node.locked {
  color: var(--text-subtle);
  background: var(--surface-sunken);
  border-color: var(--line-subtle);
  border-style: dashed;
  cursor: not-allowed;
}

.mini-node.locked .node-mark {
  color: var(--surface-base);
  background: var(--text-subtle);
}

.more-node {
  display: grid;
  place-items: center;
  min-height: 112px;
  color: var(--accent);
  background: var(--surface-canvas);
  border: 1px dashed var(--line-accent);
  border-radius: var(--radius-md);
  font-weight: 650;
}

.outcomes span {
  padding: 2px var(--space-2);
  color: var(--text-body);
  background: var(--surface-canvas);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  line-height: 1.6;
}

@media (max-width: 560px) {
  .route-card-head {
    flex-direction: column;
  }
}
</style>
