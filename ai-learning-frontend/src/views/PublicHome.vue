<template>
  <div class="public-home">
    <section class="hero-section">
      <div class="hero-content">
        <h1 class="hero-title">
          <span class="gradient-text">智教云环</span> EduLoop
        </h1>
        <p class="hero-subtitle">
          下一代智能教学全流程平台，融合AI大模型、知识图谱与智能体，打造个性化学习与高效教学新体验。
        </p>

        <div class="hero-actions">
          <el-button type="primary" size="large" class="btn-primary" @click="handleGetStarted">
            开启控制台
          </el-button>
          <span class="action-divider">或</span>
          <div class="agent-prompt-row">
            <el-input
              v-model="agentPrompt"
              size="large"
              class="agent-prompt-input"
              placeholder="与 Agent 对话"
              @keyup.enter="handleAgentPrompt"
            >
              <template #append>
                <el-button @click="handleAgentPrompt">
                  <el-icon><Position /></el-icon>
                </el-button>
              </template>
            </el-input>
          </div>
        </div>

        <div class="scene-showcase" @mouseenter="stopShowcaseAutoplay" @mouseleave="startShowcaseAutoplay">
          <div class="showcase-carousel">
            <button class="showcase-arrow arrow-prev" type="button" aria-label="上一张" @click="showPreviousScene">
              <el-icon><ArrowLeft /></el-icon>
            </button>
            <div class="showcase-track">
              <article
                v-for="(scene, index) in showcaseScenes"
                :key="scene.title"
                class="showcase-slide"
                :class="getShowcasePosition(index)"
                @click="setShowcaseScene(index)"
              >
                <div class="showcase-stage" :class="scene.tone">
                  <div class="showcase-copy">
                    <span class="showcase-kicker">{{ scene.kicker }}</span>
                    <h2>{{ scene.title }}</h2>
                    <p>{{ scene.description }}</p>
                  </div>
                  <div class="showcase-visual">
                    <img v-if="scene.image" :src="scene.image" :alt="scene.title" />
                  </div>
                </div>
              </article>
            </div>
            <button class="showcase-arrow arrow-next" type="button" aria-label="下一张" @click="showNextScene">
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
          <div class="showcase-indicators" aria-hidden="true">
            <span
              v-for="(_, index) in showcaseScenes"
              :key="index"
              :class="{ active: index === activeShowcaseIndex }"
            />
          </div>
        </div>
      </div>
    </section>

    <section id="home-recommendation" class="module-band recommendation-band">
      <div class="module-inner">
        <div class="module-header module-header-split">
          <div>
            <span class="module-kicker">智能推荐</span>
            <h2>沿着当前进度，找到下一项值得学习的内容</h2>
            <p>结合课程方向、学习进度与薄弱知识点，组织课程、课时、练习和论文资料。</p>
          </div>
          <el-button size="large" @click="openPath('/recommendation')">查看全部推荐</el-button>
        </div>

        <div class="featured-grid">
          <article class="feature-card" v-for="course in featuredCourses" :key="course.title">
            <div class="card-visual" :class="course.visualClass">
              <el-icon><component :is="course.icon" /></el-icon>
            </div>
            <div class="card-content">
              <div class="card-tags">
                <span class="tag tag-blue">{{ course.category }}</span>
                <span class="tag tag-green">{{ course.badge }}</span>
              </div>
              <h3 class="card-title">{{ course.title }}</h3>
              <p class="card-author">{{ course.teacher }}</p>
              <div class="card-footer">
                <div class="stats">
                  <el-icon><User /></el-icon> {{ course.learners }} 在学
                </div>
                <el-button type="primary" link @click="openPath(course.path)">查看详情</el-button>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <section id="home-resource-gen" class="module-band resource-band">
      <div class="module-inner">
        <div class="module-header module-header-split">
          <div>
            <span class="module-kicker">资源生成</span>
            <h2>从一个知识主题，生成可继续编辑的教学资产</h2>
            <p>复用教师工作台中的资源生成能力，覆盖大纲、讲义、练习题库与项目实战案例。</p>
          </div>
          <el-button type="primary" size="large" @click="openPath('/resource-gen')">
            <el-icon><Lightning /></el-icon>
            打开资源生成
          </el-button>
        </div>

        <div class="resource-grid">
          <article v-for="item in resourceTypes" :key="item.title" class="resource-item">
            <span class="resource-icon" :class="item.tone">
              <el-icon><component :is="item.icon" /></el-icon>
            </span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.description }}</p>
            <ul>
              <li v-for="detail in item.details" :key="detail">{{ detail }}</li>
            </ul>
            <el-button link type="primary" @click="openPath('/resource-gen')">开始生成</el-button>
          </article>
        </div>
      </div>
    </section>

    <section id="home-paper-search" class="module-band paper-band">
      <div class="module-inner paper-layout">
        <div class="paper-copy">
          <span class="module-kicker">论文查询</span>
          <h2>检索论文、整理引用，并沉淀课程研究资料</h2>
          <p>支持本地数据库、arXiv、OpenAlex 与 Semantic Scholar，进入完整工具后可筛选、收藏和记录笔记。</p>

          <div class="paper-search-tool">
            <el-input
              v-model="paperKeyword"
              size="large"
              placeholder="输入论文标题或研究关键词"
              @keyup.enter="openPaperSearch"
            >
              <template #append>
                <el-button :icon="Search" aria-label="搜索论文" @click="openPaperSearch" />
              </template>
            </el-input>
          </div>

          <div class="paper-topics" aria-label="常用研究主题">
            <button v-for="topic in paperTopics" :key="topic" type="button" @click="searchPaperTopic(topic)">
              {{ topic }}
            </button>
          </div>
        </div>

        <div class="paper-capabilities">
          <article>
            <el-icon><Search /></el-icon>
            <div><strong>多来源检索</strong><span>按相关度、年份和引用量筛选</span></div>
          </article>
          <article>
            <el-icon><Collection /></el-icon>
            <div><strong>个人资料库</strong><span>收藏论文并维护阅读状态</span></div>
          </article>
          <article>
            <el-icon><Document /></el-icon>
            <div><strong>引用与笔记</strong><span>复制引用信息并记录研究笔记</span></div>
          </article>
        </div>
      </div>
    </section>

    <section id="home-teaching-loop" class="module-band loop-band">
      <div class="module-inner">
        <div class="module-header">
          <span class="module-kicker">教学闭环</span>
          <h2>从内容交付到评价反馈，形成连续的教学过程</h2>
          <p>课程、作业、考试与成绩不再是分散入口，每个环节都能回到下一步教学调整。</p>
        </div>

        <div class="loop-grid">
          <article v-for="(step, index) in teachingLoopSteps" :key="step.title" class="loop-step">
            <span class="loop-index">0{{ index + 1 }}</span>
            <span class="loop-icon"><el-icon><component :is="step.icon" /></el-icon></span>
            <h3>{{ step.title }}</h3>
            <p>{{ step.description }}</p>
            <el-button link type="primary" @click="openPath(step.path)">进入模块</el-button>
          </article>
        </div>
      </div>
    </section>

    <section class="features-section">
      <h2 class="section-title">核心特性</h2>
      <div class="features-grid">
        <div class="feature-item">
          <div class="feature-icon icon-ai">
            <el-icon><Cpu /></el-icon>
          </div>
          <h3>AI 知识助手</h3>
          <p>基于专属知识库的智能问答答疑系统，7x24小时伴随式学习助理，支持多模态解析与代码运行分析。</p>
        </div>
        <div class="feature-item">
          <div class="feature-icon icon-learn">
            <el-icon><DataLine /></el-icon>
          </div>
          <h3>个性化自适应推荐</h3>
          <p>根据您的薄弱知识点和学习记录，动态生成个性化的习题与学习资源路径，精准提升短板。</p>
        </div>
        <div class="feature-item">
          <div class="feature-icon icon-teach">
            <el-icon><Connection /></el-icon>
          </div>
          <h3>智能资源生成</h3>
          <p>赋能教师高效备课，一键生成教学大纲、课程讲义与测试题库，极大减轻教学负担。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import {
  ArrowLeft, ArrowRight, Checked, Collection, Connection, Cpu,
  DataLine, Document, Edit, Lightning, Position, Reading, Search, Tickets, User
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const agentPrompt = ref('')
const paperKeyword = ref('')
const activeShowcaseIndex = ref(0)
let showcaseTimer = null

const showcaseScenes = [
  {
    kicker: 'AI 学习',
    title: '智能学伴实时答疑',
    description: '围绕课程知识库、课堂内容和个人学习记录发起对话，快速获得解释、总结和下一步建议。',
    image: '',
    tone: 'tone-learning'
  },
  {
    kicker: '教师工作台',
    title: '备课资源一站式生成',
    description: '辅助教师生成课程大纲、章节内容、习题和考试材料，把重复性的备课工作沉淀为可复用资产。',
    image: '',
    tone: 'tone-teaching'
  },
  {
    kicker: '知识图谱',
    title: '学习路径清晰展开',
    description: '通过知识点关系、薄弱项和掌握进度组织学习路线，让学生知道当前应该补什么、练什么。',
    image: '',
    tone: 'tone-graph'
  },
  {
    kicker: '教学闭环',
    title: '作业考试与反馈联动',
    description: '把作业、考试、成绩和反馈串联起来，帮助教师追踪交付进度，也帮助学生持续调整学习节奏。',
    image: '',
    tone: 'tone-feedback'
  }
]

const lastShowcaseIndex = computed(() => showcaseScenes.length - 1)
const previousShowcaseIndex = computed(() => (
  activeShowcaseIndex.value === 0 ? lastShowcaseIndex.value : activeShowcaseIndex.value - 1
))
const nextShowcaseIndex = computed(() => (
  activeShowcaseIndex.value === lastShowcaseIndex.value ? 0 : activeShowcaseIndex.value + 1
))

const featuredCourses = [
  {
    title: '大模型应用基础与提示工程',
    teacher: '主讲教师：张教授',
    category: 'AI 应用',
    badge: '智能推荐',
    learners: '1,240+',
    icon: Cpu,
    visualClass: 'visual-ai',
    path: '/recommendation'
  },
  {
    title: '数据结构核心考点精讲',
    teacher: '主讲教师：李老师',
    category: '计算机基础',
    badge: '薄弱补强',
    learners: '980+',
    icon: DataLine,
    visualClass: 'visual-data',
    path: '/recommendation'
  },
  {
    title: '数据库系统与知识图谱实践',
    teacher: '主讲教师：王教授',
    category: '数据库',
    badge: '项目实践',
    learners: '860+',
    icon: Collection,
    visualClass: 'visual-kb',
    path: '/recommendation'
  },
  {
    title: '教学资源生成与试卷设计',
    teacher: '主讲教师：陈老师',
    category: '智能教学',
    badge: '教师精选',
    learners: '720+',
    icon: Document,
    visualClass: 'visual-doc',
    path: '/resource-gen'
  }
]

const resourceTypes = [
  {
    title: '课程教学大纲',
    description: '把主题拆成目标、章节和教学进度。',
    details: ['教学目标', '章节安排', '评价方式'],
    icon: Reading,
    tone: 'tone-blue'
  },
  {
    title: '课堂讲义骨架',
    description: '生成可编辑的讲解结构与示例。',
    details: ['概念引入', '核心机制', '课堂示例'],
    icon: Document,
    tone: 'tone-green'
  },
  {
    title: '课后练习题库',
    description: '围绕知识点组织分层练习。',
    details: ['基础巩固', '综合应用', '答案解析'],
    icon: Edit,
    tone: 'tone-amber'
  },
  {
    title: '项目实战案例',
    description: '从真实场景生成任务与交付标准。',
    details: ['项目背景', '实施步骤', '验收标准'],
    icon: Lightning,
    tone: 'tone-cyan'
  }
]

const paperTopics = ['人工智能教育', '知识图谱', '自适应学习', '大语言模型']

const teachingLoopSteps = computed(() => {
  if (userStore.roleCode === 'student') {
    return [
      { title: '学习计划', description: '按课程和知识点组织学习路径。', path: '/learning-routes', icon: Reading },
      { title: '作业巩固', description: '提交作业并接收教师反馈。', path: '/my-homeworks', icon: Tickets },
      { title: '考试检验', description: '完成考试并查看答题结果。', path: '/my-exams', icon: Checked },
      { title: '成绩反馈', description: '根据成绩定位薄弱环节。', path: '/my-grades', icon: DataLine },
    ]
  }

  return [
    { title: '课程设计', description: '建设章节、课时与教学资源。', path: '/courses', icon: Reading },
    { title: '作业交付', description: '布置作业并跟进提交批改。', path: '/homeworks', icon: Tickets },
    { title: '考试评价', description: '配置试卷、发布考试与评分。', path: '/exams', icon: Checked },
    { title: '反馈调整', description: '根据成绩与记录调整教学。', path: '/console', icon: DataLine },
  ]
})

const handleGetStarted = () => {
  if (userStore.isLoggedIn) {
    router.push('/console')
  } else {
    router.push('/login')
  }
}

const openPath = (path) => {
  router.push(path)
}

const openPaperSearch = () => {
  const keyword = paperKeyword.value.trim()
  router.push({ path: '/paper-search', query: keyword ? { keyword } : {} })
}

const searchPaperTopic = (topic) => {
  paperKeyword.value = topic
  openPaperSearch()
}

const handleAgentPrompt = () => {
  const prompt = agentPrompt.value.trim()
  router.push(prompt ? { path: '/agent-chat', query: { prompt } } : '/agent-chat')
}

function setShowcaseScene(index) {
  activeShowcaseIndex.value = index
}

function showPreviousScene() {
  activeShowcaseIndex.value = previousShowcaseIndex.value
}

function showNextScene() {
  activeShowcaseIndex.value = nextShowcaseIndex.value
}

function getShowcasePosition(index) {
  if (index === activeShowcaseIndex.value) return 'is-active'
  if (index === previousShowcaseIndex.value) return 'is-prev'
  if (index === nextShowcaseIndex.value) return 'is-next'
  return 'is-hidden'
}

function startShowcaseAutoplay() {
  stopShowcaseAutoplay()
  showcaseTimer = window.setInterval(showNextScene, 4200)
}

function stopShowcaseAutoplay() {
  if (!showcaseTimer) return
  window.clearInterval(showcaseTimer)
  showcaseTimer = null
}

onMounted(startShowcaseAutoplay)
onBeforeUnmount(stopShowcaseAutoplay)
</script>

<style scoped>
.public-home {
  width: 100%;
  overflow: hidden;
}

.hero-section {
  min-height: calc(100vh - 64px);
  padding: 34px 24px 64px;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  text-align: center;
  position: relative;
  isolation: isolate;
  background: rgba(248, 250, 252, 0.74);
  overflow: hidden;
}

.hero-section::before,
.hero-section::after {
  content: "";
  position: absolute;
  pointer-events: none;
}

.hero-section::before {
  inset: -90px -80px -120px;
  z-index: -2;
  background:
    linear-gradient(118deg, rgba(37, 99, 235, 0.20) 0%, rgba(37, 99, 235, 0.06) 28%, transparent 52%),
    linear-gradient(238deg, rgba(15, 118, 110, 0.18) 0%, rgba(15, 118, 110, 0.06) 34%, transparent 58%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(241, 245, 249, 0.66) 100%);
  filter: blur(36px);
  transform: scale(1.04);
}

.hero-section::after {
  inset: 0;
  z-index: -1;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.68), rgba(255, 255, 255, 0.30) 54%, rgba(248, 250, 252, 0.58)),
    rgba(255, 255, 255, 0.26);
  backdrop-filter: blur(18px);
}

.hero-content {
  max-width: 1180px;
  width: 100%;
}

.hero-title {
  font-size: clamp(4.8rem, 10vw, 8.2rem);
  font-weight: 800;
  line-height: 1.02;
  margin: 0 0 18px;
  color: var(--text-primary);
  letter-spacing: 0;
}

.gradient-text {
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-subtitle {
  max-width: 820px;
  font-size: 1.32rem;
  line-height: 1.6;
  color: var(--text-secondary);
  margin: 0 auto 34px;
}

.hero-actions {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 22px;
  width: min(980px, 100%);
  margin: 0 auto;
}

.btn-primary {
  min-width: 238px;
  height: 72px;
  min-height: 72px;
  padding: 0 12px;
  font-size: 2.68rem;
  line-height: 1.08;
  border-radius: var(--radius-md);
  background-color: var(--primary-color);
  border: none;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.20);
  transition: all 0.3s ease;
  color: white;
}

.action-divider {
  color: var(--text-muted);
  font-size: 2.4rem;
  line-height: 1;
  font-weight: 700;
  flex-shrink: 0;
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 30px rgba(37, 99, 235, 0.24);
  background-color: var(--primary-color-hover);
  color: white;
}

.agent-prompt-row {
  flex: 1;
  min-width: 360px;
  max-width: 660px;
  opacity: 0.84;
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.agent-prompt-row:hover,
.agent-prompt-row:focus-within {
  opacity: 1;
  transform: translateY(-2px);
}

.agent-prompt-input {
  width: 100%;
}

.agent-prompt-input :deep(.el-input__wrapper) {
  min-height: 72px;
  background: rgba(255, 255, 255, 0.78);
  border-radius: var(--radius-md) 0 0 var(--radius-md);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08), 0 0 0 1px var(--border-color) inset;
  padding-left: 26px;
  transition: background 0.22s ease, box-shadow 0.22s ease;
}

.agent-prompt-row:hover .agent-prompt-input :deep(.el-input__wrapper),
.agent-prompt-row:focus-within .agent-prompt-input :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 20px 46px rgba(15, 23, 42, 0.12), 0 0 0 1px var(--primary-color) inset;
}

.agent-prompt-input :deep(.el-input__inner) {
  font-size: 2.48rem;
  line-height: 1.08;
}

.agent-prompt-input :deep(.el-input-group__append) {
  border: none;
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: rgba(37, 99, 235, 0.86);
  color: white;
  box-shadow: 0 16px 40px rgba(37, 99, 235, 0.18);
  padding: 0 32px;
  transition: background 0.22s ease;
}

.agent-prompt-row:hover .agent-prompt-input :deep(.el-input-group__append),
.agent-prompt-row:focus-within .agent-prompt-input :deep(.el-input-group__append) {
  background: var(--primary-color);
}

.agent-prompt-input :deep(.el-input-group__append .el-button) {
  color: white;
  font-size: 34px;
}

.scene-showcase {
  width: min(1180px, 100%);
  margin: 42px auto 0;
  perspective: 1400px;
}

.showcase-carousel {
  position: relative;
}

.showcase-track {
  position: relative;
  height: clamp(560px, 58vh, 680px);
  overflow: hidden;
  border-radius: var(--radius-md);
}

.showcase-arrow {
  position: absolute;
  top: 50%;
  z-index: 20;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(226, 232, 240, 0.78);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.62);
  color: var(--text-primary);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(14px);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translateY(-50%);
  transition: background 0.2s ease, transform 0.2s ease;
}

.showcase-arrow:hover {
  background: rgba(255, 255, 255, 0.88);
  transform: translateY(-50%) scale(1.04);
}

.arrow-prev {
  left: 20px;
}

.arrow-next {
  right: 20px;
}

.showcase-indicators {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 18px;
}

.showcase-indicators span {
  width: 28px;
  height: 4px;
  border-radius: 999px;
  background: var(--border-strong);
  transition: background 0.2s ease, width 0.2s ease;
}

.showcase-indicators span.active {
  width: 38px;
  background: var(--primary-color);
}

.showcase-slide {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 82%;
  text-align: left;
  opacity: 0;
  pointer-events: none;
  transform-style: preserve-3d;
  transition:
    left 0.45s ease,
    opacity 0.32s ease,
    transform 0.45s ease,
    filter 0.32s ease;
}

.showcase-slide.is-active {
  left: 50%;
  z-index: 10;
  opacity: 1;
  pointer-events: auto;
  transform: translateX(-50%) translateZ(80px);
}

.showcase-slide.is-prev {
  left: 0;
  z-index: 4;
  opacity: 0.62;
  pointer-events: auto;
  transform: translateX(-54%) rotateY(17deg) scale(0.88);
  cursor: pointer;
  filter: saturate(0.86);
}

.showcase-slide.is-next {
  left: 100%;
  z-index: 4;
  opacity: 0.62;
  pointer-events: auto;
  transform: translateX(-46%) rotateY(-17deg) scale(0.88);
  cursor: pointer;
  filter: saturate(0.86);
}

.showcase-slide.is-hidden {
  left: 50%;
  opacity: 0;
  transform: translateX(-50%) scale(0.72);
}

.showcase-stage {
  height: 100%;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(226, 232, 240, 0.78);
  border-radius: var(--radius-md);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0.34)),
    rgba(255, 255, 255, 0.26);
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.14);
  overflow: hidden;
  backdrop-filter: blur(18px);
}

.showcase-stage::before {
  content: "";
  position: absolute;
  inset: 20px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(10px);
}

.showcase-copy {
  position: absolute;
  left: 48px;
  right: 48px;
  top: 36px;
  z-index: 5;
  display: grid;
  grid-template-columns: 1fr;
  row-gap: 10px;
  align-items: baseline;
  width: auto;
  min-height: 168px;
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  transition: opacity 0.28s ease, transform 0.28s ease;
}

.showcase-slide:not(.is-active) .showcase-copy {
  opacity: 0;
  transform: translateY(-12px);
}

.showcase-kicker {
  display: inline-flex;
  align-self: flex-start;
  margin: 0;
  padding: 0;
  border-radius: 0;
  background: transparent;
  color: var(--primary-color);
  font-size: 1.68rem;
  line-height: 1.45;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.showcase-copy h2 {
  grid-column: 1;
  margin: 0;
  color: var(--text-primary);
  font-size: 2rem;
  line-height: 1.2;
  letter-spacing: 0;
}

.showcase-copy p {
  grid-column: 1;
  max-width: 880px;
  margin: 2px 0 0;
  color: var(--text-secondary);
  font-size: 1.68rem;
  line-height: 1.45;
}

.showcase-visual {
  position: absolute;
  left: 50%;
  bottom: 42px;
  z-index: 3;
  min-width: 0;
  width: min(860px, 78%);
  height: min(390px, 58%);
  border: 1px solid rgba(226, 232, 240, 0.88);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 30px 72px rgba(15, 23, 42, 0.18);
  overflow: hidden;
  backdrop-filter: blur(8px);
  transform: translateX(-50%);
  transition: width 0.32s ease, height 0.32s ease, bottom 0.32s ease;
}

.showcase-slide:not(.is-active) .showcase-visual {
  width: 78%;
  height: 70%;
  bottom: 15%;
}

.showcase-visual img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.tone-learning {
  background:
    linear-gradient(135deg, rgba(239, 246, 255, 0.80), rgba(240, 253, 250, 0.42)),
    rgba(255, 255, 255, 0.32);
}

.tone-teaching {
  background:
    linear-gradient(135deg, rgba(240, 253, 250, 0.78), rgba(255, 251, 235, 0.42)),
    rgba(255, 255, 255, 0.32);
}

.tone-graph {
  background:
    linear-gradient(135deg, rgba(255, 251, 235, 0.80), rgba(240, 249, 255, 0.44)),
    rgba(255, 255, 255, 0.32);
}

.tone-feedback {
  background:
    linear-gradient(135deg, rgba(240, 249, 255, 0.82), rgba(239, 246, 255, 0.44)),
    rgba(255, 255, 255, 0.32);
}

.module-band {
  width: 100%;
  padding: 96px 24px;
  border-top: 1px solid var(--border-color);
  scroll-margin-top: 0;
}

.module-inner {
  width: min(1180px, 100%);
  margin: 0 auto;
}

.recommendation-band {
  background: #f7f9fc;
}

.resource-band {
  background: #ffffff;
}

.paper-band {
  background: #f0fdfa;
}

.loop-band {
  background: #f8fafc;
}

.module-header {
  max-width: 760px;
  margin-bottom: 44px;
}

.module-header-split {
  max-width: none;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 40px;
}

.module-header-split > div {
  max-width: 760px;
}

.module-kicker {
  display: block;
  margin-bottom: 10px;
  color: var(--primary-color);
  font-size: 0.92rem;
  font-weight: 750;
}

.module-header h2,
.paper-copy h2 {
  margin: 0 0 14px;
  color: var(--text-primary);
  font-size: 2.35rem;
  line-height: 1.25;
  letter-spacing: 0;
}

.module-header p,
.paper-copy > p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 1.08rem;
  line-height: 1.75;
}

.resource-icon,
.loop-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: var(--radius-md);
  background: var(--primary-color-soft);
  color: var(--primary-color);
  font-size: 26px;
}

.resource-icon.tone-green {
  background: #ecfdf5;
  color: #059669;
}

.resource-icon.tone-amber {
  background: #fffbeb;
  color: #d97706;
}

.resource-icon.tone-cyan {
  background: #ecfeff;
  color: #0891b2;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.resource-item {
  min-width: 0;
  padding: 26px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: white;
  box-shadow: var(--card-shadow);
}

.resource-item h3 {
  margin: 20px 0 10px;
  color: var(--text-primary);
  font-size: 1.18rem;
}

.resource-item p {
  min-height: 48px;
  margin: 0 0 18px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.resource-item ul {
  min-height: 88px;
  margin: 0 0 16px;
  padding-left: 18px;
  color: var(--text-secondary);
  line-height: 1.8;
}

.paper-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  align-items: center;
  gap: 72px;
}

.paper-copy > p {
  max-width: 720px;
}

.paper-search-tool {
  max-width: 700px;
  margin-top: 30px;
}

.paper-search-tool :deep(.el-input__wrapper) {
  min-height: 54px;
  padding-left: 20px;
  background: rgba(255, 255, 255, 0.9);
}

.paper-search-tool :deep(.el-input-group__append) {
  padding: 0 22px;
  color: white;
  background: var(--primary-color);
  border-color: var(--primary-color);
}

.paper-topics {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.paper-topics button {
  padding: 7px 10px;
  border: 0;
  border-bottom: 1px solid var(--border-strong);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.paper-topics button:hover {
  color: var(--primary-color);
  border-color: var(--primary-color);
}

.paper-capabilities {
  display: grid;
  gap: 14px;
}

.paper-capabilities article {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  padding: 20px;
  border: 1px solid rgba(15, 118, 110, 0.16);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.76);
}

.paper-capabilities .el-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: #ffffff;
  color: var(--secondary-color);
  font-size: 22px;
}

.paper-capabilities article div {
  display: grid;
  gap: 5px;
}

.paper-capabilities strong {
  color: var(--text-primary);
}

.paper-capabilities span {
  color: var(--text-secondary);
  font-size: 0.9rem;
}

.loop-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.loop-step {
  position: relative;
  min-width: 0;
  padding: 26px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: white;
  box-shadow: var(--card-shadow);
}

.loop-index {
  position: absolute;
  top: 20px;
  right: 22px;
  color: var(--border-strong);
  font-size: 1.25rem;
  font-weight: 800;
}

.loop-icon {
  background: #f1f5f9;
  color: var(--text-primary);
}

.loop-step h3 {
  margin: 20px 0 10px;
  color: var(--text-primary);
  font-size: 1.18rem;
}

.loop-step p {
  min-height: 52px;
  margin: 0 0 16px;
  color: var(--text-secondary);
  line-height: 1.65;
}

.section-title {
  font-size: 2.25rem;
  font-weight: 700;
  text-align: center;
  margin-bottom: 12px;
  color: var(--text-primary);
}

.section-desc {
  text-align: center;
  color: var(--text-secondary);
  font-size: 1.1rem;
  margin-bottom: 48px;
}

.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.feature-card {
  background: white;
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--card-shadow);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  border: 1px solid var(--border-color);
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--hover-shadow);
}

.card-visual {
  height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52px;
  border-bottom: 1px solid var(--border-color);
}

.visual-ai {
  background: linear-gradient(135deg, #EFF6FF 0%, #DFF7F2 100%);
  color: var(--primary-color);
}

.visual-data {
  background: linear-gradient(135deg, #F0FDFA 0%, #ECFEFF 100%);
  color: var(--secondary-color);
}

.visual-kb {
  background: linear-gradient(135deg, #F8FAFC 0%, #E0F2FE 100%);
  color: #0284C7;
}

.visual-doc {
  background: linear-gradient(135deg, #FFFBEB 0%, #F8FAFC 100%);
  color: var(--accent-color);
}

.card-content {
  padding: 24px;
}

.card-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.tag-blue { background: var(--primary-color-soft); color: var(--primary-color); }
.tag-green { background: var(--secondary-color-soft); color: var(--secondary-color); }

.card-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
  line-height: 1.4;
}

.card-author {
  font-size: 0.9rem;
  color: var(--text-secondary);
  margin-bottom: 24px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid var(--border-color);
  padding-top: 16px;
}

.stats {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
  font-size: 0.85rem;
}

.features-section {
  max-width: 1180px;
  margin: 0 auto;
  padding: 80px 24px 120px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 60px;
}

.feature-item {
  text-align: center;
  padding: 32px;
  background: white;
  border-radius: var(--radius-md);
  box-shadow: var(--card-shadow);
  border: 1px solid var(--border-color);
  transition: transform 0.3s ease;
}

.feature-item:hover {
  transform: translateY(-5px);
}

.feature-icon {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  margin: 0 auto 24px;
}

.icon-ai { background: var(--primary-color-soft); color: var(--primary-color); }
.icon-learn { background: #ECFEFF; color: #0891B2; }
.icon-teach { background: #F0FDF4; color: #16A34A; }

.feature-item h3 {
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--text-primary);
}

.feature-item p {
  color: var(--text-secondary);
  line-height: 1.6;
  font-size: 0.95rem;
}

@media (max-width: 1024px) {
  .hero-section {
    min-height: auto;
    padding: 56px 24px 64px;
  }

  .hero-title {
    font-size: 4.4rem;
  }

  .hero-actions {
    width: 100%;
  }

  .agent-prompt-row {
    min-width: 360px;
  }

  .scene-showcase {
    width: 100%;
    margin-top: 42px;
  }

  .showcase-track {
    height: 520px;
  }

  .showcase-slide {
    width: 86%;
  }

  .showcase-copy {
    left: 28px;
    right: 28px;
    top: 28px;
    width: auto;
    min-height: 168px;
    padding: 0;
  }

  .showcase-visual {
    width: 82%;
    height: 48%;
    bottom: 34px;
  }

  .showcase-copy h2 {
    font-size: 1.82rem;
  }

  .showcase-copy p,
  .showcase-kicker {
    font-size: 1.45rem;
    line-height: 1.42;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }

  .resource-grid,
  .loop-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .paper-layout {
    gap: 42px;
  }
}

@media (max-width: 820px) {
  .hero-actions {
    flex-direction: column;
    gap: 18px;
  }

  .btn-primary {
    width: 100%;
  }

  .agent-prompt-row {
    width: 100%;
    min-width: 0;
    max-width: none;
  }

  .module-header-split {
    align-items: flex-start;
    flex-direction: column;
    gap: 24px;
  }

  .paper-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .hero-section {
    padding: 52px 16px 56px;
  }

  .hero-title {
    font-size: 3.45rem;
  }

  .hero-subtitle {
    font-size: 1.12rem;
  }

  .btn-primary {
    width: 100%;
  }

  .hero-actions {
    flex-direction: column;
    gap: 14px;
  }

  .agent-prompt-row {
    width: 100%;
    min-width: 0;
    max-width: none;
  }

  .showcase-track {
    height: 520px;
  }

  .showcase-slide {
    width: 100%;
  }

  .showcase-slide.is-prev,
  .showcase-slide.is-next {
    opacity: 0;
    pointer-events: none;
  }

  .showcase-stage::before {
    inset: 12px;
  }

  .showcase-copy {
    left: 16px;
    right: 16px;
    top: 16px;
    grid-template-columns: 1fr;
    width: auto;
    min-height: 168px;
    padding: 0;
  }

  .showcase-copy h2,
  .showcase-copy p {
    grid-column: 1;
  }

  .showcase-copy h2 {
    font-size: 1.48rem;
  }

  .showcase-copy p,
  .showcase-kicker {
    font-size: 1.12rem;
    line-height: 1.42;
  }

  .showcase-visual,
  .showcase-slide:not(.is-active) .showcase-visual {
    width: 82%;
    height: 38%;
    bottom: 34px;
  }

  .showcase-arrow {
    width: 36px;
    height: 36px;
  }

  .arrow-prev {
    left: 8px;
  }

  .arrow-next {
    right: 8px;
  }

  .module-band {
    padding: 68px 16px;
  }

  .module-header {
    margin-bottom: 32px;
  }

  .module-header h2,
  .paper-copy h2 {
    font-size: 1.82rem;
  }

  .resource-grid,
  .loop-grid {
    grid-template-columns: 1fr;
  }

  .resource-item p,
  .resource-item ul,
  .loop-step p {
    min-height: 0;
  }
}
</style>
