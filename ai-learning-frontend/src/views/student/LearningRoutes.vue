<template>
  <div class="learning-routes" v-loading="loading">
    <section class="routes-hero">
      <div>
        <p class="eyebrow">Amazing Teaching / Learning atlas</p>
        <h1>学习路线地图集</h1>
        <p>
          每门课都有独立地图、战争迷雾、阶段目标和继续学习入口，帮助你沿着清晰路线逐步点亮能力节点。
        </p>
      </div>
      <div class="hero-stats">
        <div>
          <strong>{{ routes.length }}</strong>
          <span>已加入课程</span>
        </div>
        <div>
          <strong>{{ totalUnlocked }}</strong>
          <span>已解锁节点</span>
        </div>
        <div>
          <strong>{{ totalDone }}</strong>
          <span>已完成节点</span>
        </div>
      </div>
    </section>

    <el-empty
      v-if="!loading && !routes.length"
      description="你还没有加入教学班。加入班级后，课程路线会在这里形成地图。"
    />

    <section v-else class="atlas-grid">
      <article v-for="item in routes" :key="`${item.classId}-${item.courseId}`" class="route-card">
        <div class="route-card-head">
          <div>
            <el-tag effect="dark">{{ item.domain || '精品课程' }}</el-tag>
            <h2>{{ item.courseName }}</h2>
            <p>{{ item.tagline || item.courseDescription || '按章节解锁课本、实验、练习和项目。' }}</p>
          </div>
          <el-progress type="dashboard" :percentage="item.progressPercent" :width="112" />
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
            @click="openNode(item, node)"
          >
            <span>{{ node.progress >= 100 ? '✓' : node.unlocked ? '▶' : '雾' }}</span>
            <strong>{{ node.unlocked ? node.title : '未知区域' }}</strong>
            <small>{{ node.chapterTitle }}</small>
          </button>
          <div v-if="item.path.length > 10" class="more-node">+{{ item.path.length - 10 }} 节</div>
        </div>

        <div class="outcomes" v-if="item.outcomes.length">
          <span v-for="outcome in item.outcomes.slice(0, 4)" :key="outcome">{{ outcome }}</span>
        </div>

        <div class="route-actions">
          <el-button type="primary" size="large" @click="openMap(item)">打开完整学习地图</el-button>
          <el-button size="large" @click="continueLearn(item)">进入当前课时</el-button>
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
import { getRecords } from '@/utils/apiHelpers'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const routes = ref([])

const totalUnlocked = computed(() => routes.value.reduce((sum, item) => sum + item.path.filter(node => node.unlocked).length, 0))
const totalDone = computed(() => routes.value.reduce((sum, item) => sum + item.path.filter(node => Number(node.progress) >= 100).length, 0))

const loadRoutes = async () => {
  loading.value = true
  try {
    const membershipsRes = await request.get(`/class-students/student/${userStore.userId}`)
    const memberships = membershipsRes.code === '200' ? getRecords(membershipsRes.data).filter(isActiveMembership) : []
    const rows = []
    for (const membership of memberships) {
      const teachingClass = await loadTeachingClass(membership.classId)
      if (!teachingClass?.courseId) continue
      const [course, experience] = await Promise.all([
        loadCourse(teachingClass.courseId),
        loadExperience(teachingClass.courseId, membership.classId),
      ])
      if (!course?.id || !course?.title) continue
      const path = experience.path || []
      const done = path.filter(node => Number(node.progress) >= 100).length
      rows.push({
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
      })
    }
    routes.value = rows
  } finally {
    loading.value = false
  }
}

const loadTeachingClass = async (classId) => {
  try {
    const res = await request.get(`/teaching-classes/${classId}`, { silentError: true })
    return res.code === '200' ? res.data : null
  } catch {
    return null
  }
}

const loadCourse = async (courseId) => {
  try {
    const res = await request.get(`/courses/${courseId}`, { silentError: true })
    return res.code === '200' ? res.data || {} : {}
  } catch {
    return {}
  }
}

const loadExperience = async (courseId, classId) => {
  try {
    const res = await request.get(`/courses/${courseId}/experience`, {
      params: { classId },
      silentError: true,
    })
    return res.code === '200' ? res.data || { overview: {}, path: [] } : { overview: {}, path: [] }
  } catch {
    return { overview: {}, path: [] }
  }
}

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
.learning-routes {
  display: grid;
  gap: 22px;
}

.routes-hero {
  min-height: 240px;
  padding: 34px;
  border-radius: 34px;
  color: #fff;
  background:
    radial-gradient(circle at 16% 20%, rgba(99, 232, 255, .45), transparent 26%),
    radial-gradient(circle at 82% 18%, rgba(250, 204, 21, .34), transparent 24%),
    linear-gradient(135deg, #102033, #234e8d 52%, #6b3fa0);
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 26px;
  box-shadow: 0 20px 48px rgba(15, 23, 42, .22);
}

.eyebrow {
  margin: 0 0 10px;
  letter-spacing: .18em;
  text-transform: uppercase;
  font-size: 12px;
  opacity: .76;
}

.routes-hero h1 {
  margin: 0;
  font-size: clamp(34px, 5vw, 64px);
  letter-spacing: -.04em;
}

.routes-hero p {
  max-width: 760px;
  margin: 12px 0 0;
  line-height: 1.85;
  opacity: .9;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(92px, 1fr));
  gap: 10px;
  min-width: 330px;
}

.hero-stats div {
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, .22);
  border-radius: 20px;
  background: rgba(255, 255, 255, .1);
  backdrop-filter: blur(10px);
  display: grid;
  gap: 4px;
}

.hero-stats strong {
  font-size: 30px;
}

.hero-stats span {
  font-size: 12px;
  opacity: .76;
}

.atlas-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 18px;
}

.route-card {
  padding: 22px;
  border-radius: 28px;
  border: 1px solid #dbeafe;
  background: linear-gradient(135deg, #fff, #f8fbff);
  box-shadow: 0 16px 34px rgba(15, 23, 42, .08);
  display: grid;
  gap: 16px;
}

.route-card-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.route-card h2 {
  margin: 12px 0 8px;
  font-size: 26px;
  color: #172033;
}

.route-card p {
  margin: 0;
  color: #475569;
  line-height: 1.75;
}

.class-strip,
.route-actions,
.outcomes {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.class-strip {
  color: #64748b;
  font-size: 13px;
}

.class-strip span {
  padding: 7px 10px;
  border-radius: 999px;
  background: #f1f5f9;
}

.mini-map {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}

.mini-node {
  min-height: 118px;
  padding: 12px;
  border: 1px solid #bfdbfe;
  border-radius: 18px;
  background: #eff6ff;
  color: #172033;
  cursor: pointer;
  display: grid;
  gap: 8px;
  text-align: left;
  transition: transform .18s ease, box-shadow .18s ease;
}

.mini-node:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(37, 99, 235, .13);
}

.mini-node span {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #2563eb;
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 900;
}

.mini-node strong {
  line-height: 1.35;
}

.mini-node small {
  color: #64748b;
  line-height: 1.4;
}

.mini-node.done {
  border-color: #22c55e;
  background: #f0fdf4;
}

.mini-node.done span {
  background: #16a34a;
}

.mini-node.current {
  outline: 3px solid rgba(245, 158, 11, .28);
}

.mini-node.locked {
  color: #e2e8f0;
  background: radial-gradient(circle at 26% 24%, #64748b, #0f172a);
  border-color: #334155;
}

.mini-node.locked span {
  background: #475569;
}

.mini-node.locked small {
  color: #cbd5e1;
}

.more-node {
  min-height: 118px;
  border-radius: 18px;
  border: 1px dashed #93c5fd;
  color: #2563eb;
  background: #f8fbff;
  display: grid;
  place-items: center;
  font-weight: 800;
}

.outcomes span {
  padding: 8px 10px;
  border-radius: 12px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 13px;
}

@media (max-width: 900px) {
  .routes-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-stats {
    min-width: 0;
    width: 100%;
  }
}

@media (max-width: 560px) {
  .routes-hero,
  .route-card {
    border-radius: 22px;
    padding: 18px;
  }

  .hero-stats {
    grid-template-columns: 1fr;
  }

  .atlas-grid {
    grid-template-columns: 1fr;
  }
}
</style>
