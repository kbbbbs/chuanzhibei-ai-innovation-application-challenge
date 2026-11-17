<template>
  <div ref="pageRoot" class="public-home">
    <section id="home-top" class="hero-section">
      <img
        :src="heroFlowImage"
        width="1776"
        height="888"
        alt=""
        class="hero-art"
        fetchpriority="high"
        aria-hidden="true"
      />
      <div class="hero-wash" aria-hidden="true" />

      <div class="hero-shell">
        <div class="hero-copy">
          <p class="hero-eyebrow">EduLoop · 智教云环</p>

          <h1>
            <span>让每一次学习，</span>
            <em>都通向新的发现</em>
          </h1>
          <p class="hero-subtitle">
            从课程与习题推荐，到科研论文、互动实验与智能备课，EduLoop 把学习、探索和创造组织在同一条路径里。
          </p>

          <div class="hero-actions">
            <el-button type="primary" size="large" class="primary-action" @click="handleGetStarted">
              {{ userStore.isLoggedIn ? '进入我的工作台' : '开启控制台' }}
              <el-icon><ArrowRight /></el-icon>
            </el-button>
            <button type="button" class="text-action" @click="scrollToSection('home-learning-recommendation')">
              探索首页能力 <span aria-hidden="true">↓</span>
            </button>
          </div>

          <div class="agent-composer">
            <div class="composer-heading">
              <strong>向 Agent 说出你的目标</strong>
              <span>{{ userStore.isLoggedIn ? '发送后在侧边助手中继续' : '登录后继续' }}</span>
            </div>
            <el-input
              v-model="agentPrompt"
              class="agent-prompt-input"
              placeholder="例如：帮我规划本周的数据结构复习任务"
              aria-label="向 Agent 描述目标"
              @keyup.enter="handleAgentPrompt"
            >
              <template #append>
                <el-button aria-label="发送给 Agent" @click="handleAgentPrompt">
                  <el-icon><Position /></el-icon>
                </el-button>
              </template>
            </el-input>
            <div class="prompt-suggestions">
              <button type="button" @click="usePrompt('根据最近学习内容推荐课程和配套练习')">推荐学习内容</button>
              <button type="button" @click="usePrompt('推荐与我最近所学相关的论文')">发现相关论文</button>
              <button type="button" @click="usePrompt('生成一份课程教学大纲')">开始智能备课</button>
            </div>
          </div>

          <div class="hero-proof" aria-label="平台能力概览">
            <span><strong>4</strong> 个核心场景</span>
            <span><strong>3</strong> 类规则推荐</span>
            <span><strong>1</strong> 个真实互动实验</span>
          </div>
        </div>

        <div
          class="showcase-carousel"
          role="region"
          aria-roledescription="轮播图"
          aria-label="EduLoop 核心能力"
          tabindex="0"
          @mouseenter="stopShowcaseAutoplay"
          @mouseleave="startShowcaseAutoplay"
          @focusin="stopShowcaseAutoplay"
          @focusout="handleCarouselFocusOut"
          @keydown.left.prevent="showPreviousScene"
          @keydown.right.prevent="showNextScene"
        >
          <div class="carousel-viewport">
            <Transition :name="carouselTransitionName" mode="out-in">
              <article :key="activeShowcaseScene.id" class="showcase-slide" :class="activeShowcaseScene.tone">
                <div class="showcase-copy">
                  <span class="scene-kicker">{{ activeShowcaseScene.kicker }} · 0{{ activeShowcaseIndex + 1 }} / 04</span>
                  <h2>{{ activeShowcaseScene.title }}</h2>
                  <p>{{ activeShowcaseScene.description }}</p>
                </div>

                <div class="showcase-visual">
                  <img
                    v-if="activeShowcaseScene.visual === 'learning'"
                    :src="learningRecommendationImage"
                    width="1776"
                    height="888"
                    alt="课程学习路径连接配套习题的纸艺插画"
                  />
                  <img
                    v-else-if="activeShowcaseScene.visual === 'research'"
                    :src="researchDiscoveryImage"
                    width="1776"
                    height="888"
                    alt="论文与引用关系构成的科研发现纸艺插画"
                  />
                  <div v-else-if="activeShowcaseScene.visual === 'interactive'" class="mini-lab" aria-hidden="true">
                    <div class="mini-lab-head">
                      <span>学习率</span>
                      <strong>0.12</strong>
                    </div>
                    <svg viewBox="0 0 520 250">
                      <polyline class="mini-curve" :points="miniCurvePoints" />
                      <polyline class="mini-descent" :points="miniDescentPoints" />
                      <circle
                        v-for="point in miniDescentDots"
                        :key="point"
                        class="mini-dot"
                        :cx="point.split(',')[0]"
                        :cy="point.split(',')[1]"
                        r="5"
                      />
                    </svg>
                    <div class="mini-slider"><span /><i /></div>
                  </div>
                  <div v-else class="prep-preview" aria-hidden="true">
                    <div class="prep-agent"><el-icon><Cpu /></el-icon><span>Agent</span></div>
                    <div class="prep-preview-row"><span>自然语言 / PPT</span><i>→</i><strong>课程大纲</strong></div>
                    <div class="prep-preview-row"><span>自然语言 / 文件</span><i>→</i><strong>课堂讲义</strong></div>
                    <div class="prep-preview-row"><span>课程材料</span><i>→</i><strong>配套题库</strong></div>
                  </div>
                </div>

                <button type="button" class="showcase-entry" @click="openPath(activeShowcaseScene.path)">
                  {{ activeShowcaseScene.action }}
                  <span><el-icon><ArrowRight /></el-icon></span>
                </button>
              </article>
            </Transition>
          </div>

          <div class="carousel-controls">
            <button type="button" aria-label="上一项能力" @click="showPreviousScene">
              <el-icon><ArrowLeft /></el-icon>
            </button>
            <div class="carousel-dots" role="tablist" aria-label="选择能力场景">
              <button
                v-for="(scene, index) in showcaseScenes"
                :key="scene.id"
                type="button"
                role="tab"
                :aria-label="`切换到${scene.kicker}`"
                :aria-selected="index === activeShowcaseIndex"
                :class="{ active: index === activeShowcaseIndex }"
                @click="setShowcaseScene(index)"
              ><span /></button>
            </div>
            <button type="button" aria-label="下一项能力" @click="showNextScene">
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </div>
      </div>
    </section>

    <section id="home-learning-recommendation" class="module-section learning-section" data-reveal>
      <div class="section-shell">
        <header class="section-heading">
          <div>
            <span class="section-index">01 · 学习</span>
            <h2>精选课程，为下一段学习打开方向</h2>
          </div>
          <p>结合管理员精选与个性化推荐，按优先级展示适合继续学习的课程，点击卡片即可进入课程详情。</p>
        </header>

        <section v-loading="recommendationLoading" class="course-recommendation-board" data-testid="course-recommendations">
          <div v-if="courseRecommendations.length" class="course-card-grid">
            <article
              v-for="(item, index) in courseRecommendations"
              :key="item.id"
              class="course-recommendation-card"
              role="link"
              tabindex="0"
              data-testid="course-recommendation-item"
              @click="openPath(item.targetUrl)"
              @keydown="handlePathKeydown($event, item.targetUrl)"
            >
              <div class="course-card-cover" :class="`cover-tone-${index % 4}`" :style="courseCoverStyle(item)">
                <span class="course-card-mark"><el-icon><Reading /></el-icon></span>
                <span class="course-card-badge">{{ item.meta?.curated ? '精选' : formatScore(item.score) }}</span>
              </div>
              <div class="course-card-body">
                <div class="course-card-meta">
                  <span>{{ item.subtitle || '推荐课程' }}</span>
                  <span v-if="item.meta?.difficulty">{{ item.meta.difficulty }}</span>
                </div>
                <h3>{{ item.title }}</h3>
                <p>{{ item.reason || '适合作为下一阶段的学习方向' }}</p>
                <span class="course-card-entry">查看课程 <el-icon><ArrowRight /></el-icon></span>
              </div>
            </article>
          </div>
          <div v-else class="course-grid-empty" data-testid="course-readiness">
            <span><el-icon><Reading /></el-icon></span>
            <h3>{{ recommendationStatusText('course') }}</h3>
            <p>管理员可在“首页推荐课程”中配置内容，登录后也会根据学习画像自动补足推荐。</p>
          </div>
        </section>
      </div>
    </section>

    <section id="home-research-discovery" class="module-section research-section" data-reveal>
      <div class="section-shell research-layout">
        <div class="research-copy">
          <span class="section-index">02 · 科研</span>
          <h2>从最近所学出发，发现值得追问的论文</h2>
          <p>系统根据课程方向、近期学习与薄弱主题组织论文推荐；你也可以直接输入问题或关键词开始检索。</p>

          <div class="paper-search-box">
            <el-input
              v-model="paperKeyword"
              placeholder="输入论文标题、研究问题或关键词"
              aria-label="搜索论文"
              @keyup.enter="openPaperSearch"
            >
              <template #append>
                <el-button aria-label="搜索论文" @click="openPaperSearch"><el-icon><Search /></el-icon></el-button>
              </template>
            </el-input>
          </div>
          <div class="paper-topics" aria-label="常用研究主题">
            <button v-for="topic in paperTopics" :key="topic" type="button" @click="searchPaperTopic(topic)">
              {{ topic }}
            </button>
          </div>

          <div v-if="paperRecommendations.length" class="paper-recommendations" data-testid="paper-recommendations">
            <article
              v-for="paper in paperRecommendations"
              :key="paper.id"
              role="link"
              tabindex="0"
              @click="openPath(paper.targetUrl)"
              @keydown="handlePathKeydown($event, paper.targetUrl)"
            >
              <span>{{ paper.meta?.year || '新' }}</span>
              <div><strong>{{ paper.title }}</strong><small>{{ paper.reason || paper.subtitle }}</small></div>
              <em>{{ formatScore(paper.score) }}</em>
            </article>
          </div>
          <div v-else class="paper-ready-state" data-testid="paper-readiness">
            <span>{{ recommendationStatusText('paper') }}</span>
            <div><i /> 最近所学</div><div><i /> 课程关键词</div><div><i /> 引用与年份</div>
          </div>
        </div>

        <figure class="research-figure">
          <img
            :src="researchDiscoveryImage"
            width="1776"
            height="888"
            loading="lazy"
            alt="论文与引用关系构成的科研发现纸艺插画"
          />
          <figcaption>
            <strong>从学习记录中提取研究线索</strong>
            <small>本地论文库 · arXiv · OpenAlex · Semantic Scholar</small>
          </figcaption>
        </figure>
      </div>
    </section>

    <section id="home-interactive-lab" class="module-section interactive-section" data-reveal>
      <div class="section-shell interactive-layout">
        <div class="interactive-copy">
          <span class="section-index">03 · 实验</span>
          <h2>知识不只用来看，更要亲手改变它</h2>
          <p>互动 JSON 将实验参数、可视化和课时内容组合在一起。拖动学习率，直接观察梯度下降从收敛到震荡的变化。</p>
          <div class="interactive-facts">
            <span><strong>0.01—1.20</strong><small>学习率范围</small></span>
            <span><strong>18</strong><small>轨迹步骤</small></span>
            <span><strong>JSON</strong><small>内容驱动</small></span>
          </div>
          <el-button type="primary" size="large" @click="openPath(courseEntry.path)">
            {{ courseEntry.action }} <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>

        <div class="home-interactive-preview" data-testid="home-interactive-preview">
          <InteractiveLabPanel
            lesson-id="home-gradient-demo"
            :experiments="homeExperiments"
            :challenges="[]"
          />
        </div>
      </div>
    </section>

    <section id="home-smart-prep" class="module-section prep-section" data-reveal>
      <div class="section-shell">
        <header class="section-heading">
          <div>
            <span class="section-index">04 · 备课</span>
            <h2>把资料交给 Agent，把判断留给教师</h2>
          </div>
          <div class="prep-intro">
            <p>自然语言、PPT 与课程文件进入同一个备课工作流，生成内容可继续编辑并导入对应教学模块。</p>
            <el-button type="primary" size="large" @click="openPath('/resource-gen')">
              打开智能备课 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </header>

        <div class="prep-grid">
          <article
            v-for="(flow, index) in prepFlows"
            :key="flow.title"
            role="link"
            tabindex="0"
            data-testid="prep-flow"
            @click="openPath('/resource-gen')"
            @keydown="handlePathKeydown($event, '/resource-gen')"
          >
            <div class="prep-card-top"><span>0{{ index + 1 }}</span><el-icon><component :is="flow.icon" /></el-icon></div>
            <h3>{{ flow.title }}</h3>
            <p>{{ flow.description }}</p>
            <div class="flow-path">
              <span>{{ flow.input }}</span><i>→</i><strong>{{ flow.output }}</strong><i>→</i><span>{{ flow.destination }}</span>
            </div>
            <span class="prep-card-entry">进入资源生成 <el-icon><ArrowRight /></el-icon></span>
          </article>
        </div>
      </div>
    </section>

    <section class="closing-section" data-reveal>
      <div class="section-shell closing-shell">
        <h2>学习、研究、实验与创造，在一处自然衔接。</h2>
        <div class="closing-actions">
          <el-button type="primary" size="large" @click="handleGetStarted">
            {{ userStore.isLoggedIn ? '返回工作台' : '现在开始' }} <el-icon><ArrowRight /></el-icon>
          </el-button>
          <el-button size="large" @click="handleAgentPrompt">与 Agent 对话</el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft, ArrowRight, Cpu, Document,
  Position, Reading, Search, Tickets
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { resolveResourceUrl } from '@/utils/backendUrl'
import { getMyRecommendations } from '@/api/recommendation'
import { getHomeCourseRecommendations } from '@/api/homeCourseRecommendation'
import { openAgentAssistant } from '@/utils/agentAssistant'
import InteractiveLabPanel from '@/components/learning/InteractiveLabPanel.vue'
import heroFlowImage from '@/assets/home/hero-flow.png'
import learningRecommendationImage from '@/assets/home/learning-recommendation.png'
import researchDiscoveryImage from '@/assets/home/research-discovery.png'

const router = useRouter()
const userStore = useUserStore()
const pageRoot = ref(null)
const agentPrompt = ref('')
const paperKeyword = ref('')
const activeShowcaseIndex = ref(0)
const slideDirection = ref('next')
const prefersReducedMotion = ref(false)
const recommendationLoading = ref(false)
const recommendationErrors = ref({ course: false, exercise: false, paper: false })
const courseRecommendations = ref([])
const paperRecommendations = ref([])

let showcaseTimer = null
let revealObserver = null
let motionQuery = null
let recommendationRequestId = 0

const showcaseScenes = [
  {
    id: 'learning', kicker: '学习推荐', visual: 'learning', tone: 'tone-learning',
    title: '课程与配套练习，同时抵达下一步',
    description: '根据学习方向、薄弱课程与难度递进，分别生成课程推荐和可立即开始的配套习题。',
    action: '查看学习推荐', path: '/recommendation'
  },
  {
    id: 'research', kicker: '科研发现', visual: 'research', tone: 'tone-research',
    title: '把最近所学，延伸为新的研究问题',
    description: '从课程关键词与学习记录出发，发现相关论文，也可以直接检索一个正在思考的问题。',
    action: '进入科研发现', path: '/paper-search'
  },
  {
    id: 'interactive', kicker: '互动实验', visual: 'interactive', tone: 'tone-interactive',
    title: '拖动一个参数，看见知识如何变化',
    description: '以梯度下降为例，让学习率、收敛轨迹与代码结果在同一个互动课时中彼此印证。'
  },
  {
    id: 'prep', kicker: '智能备课', visual: 'prep', tone: 'tone-prep',
    title: '从自然语言或文件，推进到教学草稿',
    description: '接入 Agent，将 PPT 与课程材料组织成大纲、课堂讲义和配套题库，再进入对应模块继续编辑。',
    action: '打开智能备课', path: '/resource-gen'
  }
]

// Links follow what this instance really has; signed-out visitors reach login through the route guard.
const courseEntry = computed(() => {
  const recommended = courseRecommendations.value[0]?.targetUrl
  if (recommended) return { path: recommended, action: '查看推荐课程' }
  if (userStore.isTeacher || userStore.isAdmin) return { path: '/courses', action: '浏览课程' }
  return { path: '/recommendation', action: '发现学习内容' }
})

const activeShowcaseScene = computed(() => {
  const scene = showcaseScenes[activeShowcaseIndex.value]
  return scene.id === 'interactive' ? { ...scene, ...courseEntry.value } : scene
})
const carouselTransitionName = computed(() => slideDirection.value === 'next' ? 'carousel-next' : 'carousel-prev')

const paperTopics = ['人工智能教育', '知识图谱', '自适应学习', '大语言模型']
const homeExperiments = [
  {
    id: 'home-gradient-demo',
    title: '梯度下降：学习率与收敛',
    description: '拖动学习率，观察轨迹如何从平稳收敛变成越过谷底后的震荡。',
    kind: 'gradient',
    defaults: { learningRate: 0.12, startX: -4 }
  }
]

const prepFlows = [
  {
    title: '课程教学大纲',
    description: '把课程目标、多个课件与补充要求组织成完整教学结构。',
    input: '自然语言 / 多个 PPT', output: '课程大纲', destination: '课程管理', icon: Reading
  },
  {
    title: '课堂讲义',
    description: '围绕单次授课主题生成可继续编辑的讲解结构与示例。',
    input: '自然语言 / 单个 PPT', output: '课堂讲义', destination: '课时页', icon: Document
  },
  {
    title: '课堂题库',
    description: '从课程文件抽取知识点并组织分层题目与答案解析。',
    input: '自然语言 / 单个文件', output: '课堂题库', destination: '题库', icon: Tickets
  }
]

const curvePoint = x => `${260 + x * 46},${224 - x * x * 9}`
const miniCurvePoints = Array.from({ length: 81 }, (_, index) => -5 + index * 0.125).map(curvePoint).join(' ')
const miniDescentDots = computed(() => {
  let x = -4
  const points = []
  for (let index = 0; index < 16; index += 1) {
    points.push(curvePoint(x))
    x -= 0.12 * 2 * x
  }
  return points
})
const miniDescentPoints = computed(() => miniDescentDots.value.join(' '))

function setShowcaseScene(index) {
  if (index === activeShowcaseIndex.value) return
  const current = activeShowcaseIndex.value
  const forwardDistance = (index - current + showcaseScenes.length) % showcaseScenes.length
  slideDirection.value = forwardDistance <= showcaseScenes.length / 2 ? 'next' : 'prev'
  activeShowcaseIndex.value = index
  restartShowcaseAutoplay()
}

function showNextScene(restart = true) {
  slideDirection.value = 'next'
  activeShowcaseIndex.value = (activeShowcaseIndex.value + 1) % showcaseScenes.length
  if (restart) restartShowcaseAutoplay()
}

function showPreviousScene() {
  slideDirection.value = 'prev'
  activeShowcaseIndex.value = (activeShowcaseIndex.value - 1 + showcaseScenes.length) % showcaseScenes.length
  restartShowcaseAutoplay()
}

function startShowcaseAutoplay() {
  stopShowcaseAutoplay()
  if (prefersReducedMotion.value || document.hidden) return
  showcaseTimer = window.setInterval(() => showNextScene(false), 5000)
}

function restartShowcaseAutoplay() {
  startShowcaseAutoplay()
}

function stopShowcaseAutoplay() {
  if (!showcaseTimer) return
  window.clearInterval(showcaseTimer)
  showcaseTimer = null
}

function handleCarouselFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) startShowcaseAutoplay()
}

function handleVisibilityChange() {
  if (document.hidden) stopShowcaseAutoplay()
  else startShowcaseAutoplay()
}

function handleMotionChange(event) {
  prefersReducedMotion.value = event.matches
  if (event.matches) stopShowcaseAutoplay()
  else startShowcaseAutoplay()
}

function setupRevealObserver() {
  const targets = pageRoot.value?.querySelectorAll('[data-reveal]') || []
  if (prefersReducedMotion.value || !('IntersectionObserver' in window)) {
    targets.forEach(target => target.classList.add('is-visible'))
    return
  }
  revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return
      entry.target.classList.add('is-visible')
      revealObserver?.unobserve(entry.target)
    })
  }, { threshold: 0.12, rootMargin: '0px 0px -8% 0px' })
  targets.forEach(target => revealObserver.observe(target))
}

async function loadHomepageRecommendations() {
  const requestId = ++recommendationRequestId
  recommendationErrors.value = { course: false, exercise: false, paper: false }
  const curatedResult = await Promise.allSettled([getHomeCourseRecommendations()])
  if (requestId !== recommendationRequestId) return
  const curatedResponse = curatedResult[0]?.status === 'fulfilled' ? curatedResult[0].value : null
  const curatedCourses = curatedResponse?.code === '200' && Array.isArray(curatedResponse.data)
    ? curatedResponse.data.slice(0, 8).map((item, index) => normalizeCuratedCourse(item, index))
    : []

  if (!userStore.isLoggedIn) {
    courseRecommendations.value = curatedCourses
    paperRecommendations.value = []
    recommendationLoading.value = false
    return
  }

  recommendationLoading.value = true
  const configs = [
    { type: 'course', limit: 8 },
    { type: 'paper', limit: 3 }
  ]
  const results = await Promise.allSettled(configs.map(config => getMyRecommendations(config)))
  if (requestId !== recommendationRequestId) return

  const values = { course: [], paper: [] }
  results.forEach((result, index) => {
    const type = configs[index].type
    if (result.status === 'fulfilled' && result.value?.code === '200' && Array.isArray(result.value.data?.items)) {
      values[type] = result.value.data.items.map((item, itemIndex) => normalizeRecommendation(item, type, itemIndex))
    } else {
      recommendationErrors.value[type] = true
    }
  })

  courseRecommendations.value = mergeCourseRecommendations(curatedCourses, values.course)
  paperRecommendations.value = values.paper
  recommendationLoading.value = false
}

function normalizeCuratedCourse(item, index) {
  return {
    id: `curated-course-${item.id || item.courseId || index}`,
    type: 'course',
    title: item.courseTitle || '待命名课程',
    subtitle: item.teacher || item.difficulty || '',
    reason: item.reason || item.courseDescription || '管理员精选课程',
    score: 0,
    coverUrl: item.coverUrl || '',
    targetUrl: item.targetUrl || `/courses/${item.courseId}`,
    meta: { courseId: item.courseId, classId: item.classId, difficulty: item.difficulty, curated: true }
  }
}

function mergeCourseRecommendations(curated, automatic) {
  const merged = []
  const courseIds = new Set()
  for (const item of [...curated, ...automatic]) {
    const courseId = String(item?.meta?.courseId || item?.itemId || item?.id || '')
    if (courseIds.has(courseId)) continue
    courseIds.add(courseId)
    merged.push(item)
    if (merged.length === 8) break
  }
  return merged
}

function normalizeRecommendation(item, fallbackType, index) {
  const type = item?.type || fallbackType
  const fallbackPath = type === 'paper' ? '/paper-search' : type === 'exercise' ? '/recommendation' : '/courses'
  return {
    id: item?.id || `${type}-${item?.itemId || index}`,
    type,
    title: item?.title || '待命名推荐内容',
    subtitle: item?.subtitle || '',
    reason: item?.reason || item?.description || '',
    score: Number(item?.score || 0),
    coverUrl: item?.coverUrl || item?.meta?.coverUrl || '',
    targetUrl: item?.targetUrl || fallbackPath,
    meta: item?.meta || {}
  }
}

function courseCoverStyle(item) {
  const url = resolveResourceUrl(item?.coverUrl)
  if (!url) return undefined
  return { backgroundImage: `linear-gradient(180deg, rgba(15, 23, 42, .06), rgba(15, 23, 42, .4)), url("${url.replace(/"/g, '\\"')}")` }
}

function recommendationStatusText(type) {
  if (!userStore.isLoggedIn) return type === 'paper' ? '登录后根据最近所学生成论文线索' : '登录并开始学习后自动生成'
  if (recommendationErrors.value[type]) return '推荐服务暂时不可用，规则配置已保留'
  if (type === 'exercise') return '等待课程与成绩数据后自动匹配'
  if (type === 'paper') return '等待近期学习记录后自动推荐'
  return '等待课程与学习画像后自动推荐'
}

function formatScore(score) {
  const value = Number(score || 0)
  return value > 0 ? `${Math.round(value)}分` : '推荐'
}

function scrollToSection(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: prefersReducedMotion.value ? 'auto' : 'smooth', block: 'start' })
}

function usePrompt(prompt) {
  agentPrompt.value = prompt
}

function handleGetStarted() {
  router.push(userStore.isLoggedIn ? '/console' : '/login')
}

function openPath(path) {
  if (!path) return
  router.push(path)
}

function handlePathKeydown(event, path) {
  if (!['Enter', ' '].includes(event.key)) return
  event.preventDefault()
  openPath(path)
}

function openPaperSearch() {
  const keyword = paperKeyword.value.trim()
  router.push({ path: '/paper-search', query: keyword ? { keyword } : {} })
}

function searchPaperTopic(topic) {
  paperKeyword.value = topic
  openPaperSearch()
}

function handleAgentPrompt() {
  const prompt = agentPrompt.value.trim()
  if (userStore.isLoggedIn) {
    openAgentAssistant({ prompt })
    return
  }
  const target = router.resolve({ path: '/home', query: { openAgent: '1', ...(prompt ? { prompt } : {}) } }).fullPath
  router.push({ path: '/login', query: { redirect: target } })
}

watch(() => userStore.isLoggedIn, loadHomepageRecommendations, { immediate: true })

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  prefersReducedMotion.value = motionQuery.matches
  motionQuery.addEventListener?.('change', handleMotionChange)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  setupRevealObserver()
  startShowcaseAutoplay()
})

onBeforeUnmount(() => {
  stopShowcaseAutoplay()
  revealObserver?.disconnect()
  motionQuery?.removeEventListener?.('change', handleMotionChange)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.public-home {
  width: 100%;
  overflow: hidden;
  color: var(--text-body);
  background: var(--surface-base);
}

.hero-section {
  position: relative;
  min-height: calc(100vh - 64px);
  padding: clamp(40px, 6vh, 72px) clamp(16px, 4vw, 64px);
  isolation: isolate;
  background: var(--surface-canvas);
}

.hero-art,
.hero-wash {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.hero-art {
  z-index: -2;
  object-fit: cover;
  opacity: .22;
}

html.dark .hero-art {
  opacity: .08;
}

.hero-wash {
  z-index: -1;
  background: linear-gradient(180deg, transparent 55%, var(--surface-canvas) 100%);
}

.hero-shell,
.section-shell {
  width: min(1320px, 100%);
  margin: 0 auto;
}

.hero-shell {
  display: grid;
  grid-template-columns: minmax(0, .88fr) minmax(0, 1.12fr);
  align-items: center;
  gap: clamp(32px, 5vw, 80px);
}

.hero-copy {
  min-width: 0;
}

.hero-eyebrow {
  margin: 0 0 var(--space-4);
  color: var(--accent);
  font-size: var(--text-sm);
  font-weight: 650;
}

.hero-copy h1 {
  margin: 0;
  color: var(--text-strong);
  font-size: clamp(2.25rem, 3.6vw, 3.5rem);
  font-weight: 700;
  line-height: 1.16;
}

.hero-copy h1 span,
.hero-copy h1 em {
  display: block;
}

.hero-copy h1 em {
  color: var(--accent);
  font-style: normal;
}

.hero-subtitle {
  max-width: 600px;
  margin: var(--space-5) 0 0;
  color: var(--text-subtle);
  font-size: var(--text-lg);
  line-height: 1.75;
}

.hero-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-5);
  margin-top: var(--space-6);
}

.primary-action :deep(span) {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.text-action {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) 0;
  color: var(--text-body);
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--line-strong);
  font-weight: 600;
  cursor: pointer;
}

.text-action:hover {
  color: var(--accent);
  border-color: var(--accent);
}

.agent-composer {
  margin-top: var(--space-8);
  padding: var(--space-4);
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-2);
}

.composer-heading {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-3);
  margin-bottom: var(--space-3);
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.composer-heading strong {
  color: var(--text-strong);
  font-size: var(--text-sm);
}

.agent-prompt-input :deep(.el-input__wrapper),
.paper-search-box :deep(.el-input__wrapper) {
  min-height: 44px;
}

.agent-prompt-input :deep(.el-input-group__append),
.paper-search-box :deep(.el-input-group__append) {
  width: 52px;
  padding: 0;
  color: var(--text-on-accent);
  background: var(--accent-solid);
  border: 0;
  box-shadow: none;
}

.agent-prompt-input :deep(.el-input-group__append .el-button),
.paper-search-box :deep(.el-input-group__append .el-button) {
  width: 100%;
  height: 44px;
  margin: 0;
  color: var(--text-on-accent);
  font-size: 18px;
}

.prompt-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.prompt-suggestions button {
  padding: 6px 10px;
  color: var(--text-subtle);
  background: var(--surface-sunken);
  border: 0;
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  cursor: pointer;
  transition: color .15s var(--ease-standard), background .15s var(--ease-standard);
}

.prompt-suggestions button:hover {
  color: var(--accent);
  background: var(--surface-accent);
}

.hero-proof {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-6);
  margin-top: var(--space-5);
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.hero-proof span {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.hero-proof strong {
  color: var(--text-strong);
  font-size: var(--text-md);
}

.showcase-carousel {
  min-width: 0;
  padding: var(--space-3);
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-3);
}

.showcase-carousel:focus-visible {
  outline: 2px solid var(--focus-ring);
  outline-offset: 2px;
}

.carousel-viewport {
  position: relative;
  min-height: clamp(480px, 58vh, 600px);
  overflow: hidden;
  border-radius: var(--radius-lg);
}

.showcase-slide {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  padding: clamp(20px, 2.4vw, 32px);
  background: var(--surface-canvas);
}

.tone-learning,
.tone-research {
  --scene-color: var(--accent);
}

.tone-interactive {
  --scene-color: var(--positive);
}

.tone-prep {
  --scene-color: var(--caution);
}

.scene-kicker {
  display: block;
  color: var(--scene-color);
  font-size: var(--text-xs);
  font-weight: 650;
}

.showcase-copy h2 {
  margin: var(--space-2) 0;
  color: var(--text-strong);
  font-size: clamp(1.35rem, 1.9vw, 1.75rem);
  line-height: 1.3;
}

.showcase-copy p {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--text-sm);
  line-height: 1.7;
}

.showcase-visual {
  flex: 1;
  min-height: 200px;
  margin-top: var(--space-4);
  overflow: hidden;
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-md);
}

.showcase-visual > img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mini-lab {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--space-4);
}

.mini-lab-head {
  display: flex;
  justify-content: space-between;
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.mini-lab-head strong {
  color: var(--positive);
  font-size: var(--text-md);
}

.mini-lab svg {
  flex: 1;
  width: 100%;
  min-height: 150px;
}

.mini-curve {
  fill: none;
  stroke: var(--line-strong);
  stroke-width: 2;
}

.mini-descent {
  fill: none;
  stroke: var(--accent);
  stroke-width: 4;
}

.mini-dot {
  fill: var(--caution);
}

.mini-slider {
  position: relative;
  height: 5px;
  flex-shrink: 0;
  background: var(--surface-sunken);
  border-radius: var(--radius-pill);
}

.mini-slider span {
  display: block;
  width: 38%;
  height: 100%;
  background: var(--positive);
  border-radius: inherit;
}

.mini-slider i {
  position: absolute;
  top: 50%;
  left: 38%;
  width: 16px;
  height: 16px;
  background: var(--positive);
  border: 3px solid var(--surface-base);
  border-radius: 50%;
  transform: translate(-50%, -50%);
}

.prep-preview {
  display: grid;
  align-content: center;
  gap: var(--space-2);
  min-height: 100%;
  padding: var(--space-5);
}

.prep-agent {
  display: inline-flex;
  align-items: center;
  justify-self: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  padding: var(--space-2) var(--space-4);
  color: var(--text-on-accent);
  background: var(--accent-solid);
  border-radius: var(--radius-pill);
  font-weight: 650;
}

.prep-preview-row {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  color: var(--text-subtle);
  background: var(--surface-canvas);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
}

.prep-preview-row i {
  color: var(--caution);
  font-style: normal;
}

.prep-preview-row strong {
  color: var(--text-strong);
}

.showcase-entry {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  align-self: flex-end;
  margin-top: var(--space-3);
  padding: 0;
  color: var(--text-body);
  background: transparent;
  border: 0;
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
}

.showcase-entry > span {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  color: var(--text-on-accent);
  background: var(--accent-solid);
  border-radius: 50%;
  transition: transform .15s var(--ease-standard);
}

.showcase-entry:hover > span {
  transform: translateX(3px);
}

.carousel-controls {
  display: grid;
  grid-template-columns: 36px 1fr 36px;
  align-items: center;
  gap: var(--space-3);
  padding-top: var(--space-3);
}

.carousel-controls > button {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  color: var(--text-body);
  background: var(--surface-base);
  border: 1px solid var(--line-default);
  border-radius: 50%;
  cursor: pointer;
}

.carousel-controls > button:hover {
  color: var(--accent);
  border-color: var(--line-accent);
}

.carousel-dots {
  display: flex;
  justify-content: center;
  gap: var(--space-2);
}

.carousel-dots button {
  width: 34px;
  height: 24px;
  padding: 10px 0;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.carousel-dots span {
  display: block;
  width: 100%;
  height: 4px;
  background: var(--line-default);
  border-radius: var(--radius-pill);
  transition: background .2s var(--ease-standard);
}

.carousel-dots button.active span {
  background: var(--accent);
}

.carousel-next-enter-active,
.carousel-next-leave-active,
.carousel-prev-enter-active,
.carousel-prev-leave-active {
  transition: opacity .28s var(--ease-standard), transform .28s var(--ease-standard);
}

.carousel-next-enter-from,
.carousel-prev-leave-to {
  opacity: 0;
  transform: translateX(28px);
}

.carousel-next-leave-to,
.carousel-prev-enter-from {
  opacity: 0;
  transform: translateX(-28px);
}

.module-section {
  padding: clamp(56px, 7vw, 104px) clamp(16px, 4vw, 64px);
  scroll-margin-top: 72px;
}

[data-reveal] {
  opacity: 0;
  transform: translateY(24px);
  transition: opacity .5s var(--ease-standard), transform .5s var(--ease-standard);
}

[data-reveal].is-visible {
  opacity: 1;
  transform: translateY(0);
}

.section-index {
  display: block;
  margin-bottom: var(--space-3);
  color: var(--accent);
  font-size: var(--text-sm);
  font-weight: 650;
}

.section-heading {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, .9fr);
  align-items: end;
  gap: var(--space-4) clamp(32px, 6vw, 80px);
  margin-bottom: var(--space-8);
}

.section-heading h2,
.research-copy h2,
.interactive-copy h2,
.closing-shell h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: clamp(1.625rem, 2.6vw, 2.5rem);
  line-height: 1.25;
}

.section-heading p {
  margin: 0;
  color: var(--text-subtle);
  line-height: 1.75;
}

.learning-section,
.prep-section {
  background: var(--surface-base);
}

.course-recommendation-board {
  min-height: 240px;
}

.course-card-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-5);
}

.course-recommendation-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: border-color .15s var(--ease-standard), box-shadow .15s var(--ease-standard);
}

.course-recommendation-card:hover {
  border-color: var(--line-accent);
  box-shadow: var(--shadow-2);
}

.course-recommendation-card:focus-visible {
  outline: 2px solid var(--focus-ring);
  outline-offset: 2px;
}

.course-card-cover {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  min-height: 128px;
  padding: var(--space-3);
  color: #fff;
  background-position: center;
  background-size: cover;
}

.cover-tone-0 { background-color: #2a4fb4; }
.cover-tone-1 { background-color: #0a6f68; }
.cover-tone-2 { background-color: #54429f; }
.cover-tone-3 { background-color: #8c5211; }

.course-card-mark {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  background: rgba(255, 255, 255, .16);
  border: 1px solid rgba(255, 255, 255, .4);
  border-radius: var(--radius-md);
  font-size: 20px;
}

.course-card-badge {
  padding: 3px 8px;
  color: #1c2a4a;
  background: rgba(255, 255, 255, .92);
  border-radius: var(--radius-pill);
  font-size: var(--text-xs);
  font-weight: 650;
}

.course-card-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: var(--space-4);
}

.course-card-meta {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.course-card-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-card-meta span:first-child {
  flex: 1;
}

.course-card-body h3 {
  display: -webkit-box;
  margin: var(--space-2) 0 var(--space-1);
  overflow: hidden;
  color: var(--text-strong);
  font-size: var(--text-md);
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.course-card-body p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--text-subtle);
  font-size: var(--text-xs);
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.course-card-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: var(--space-3);
  color: var(--accent);
  font-size: var(--text-sm);
  font-weight: 600;
}

.course-grid-empty {
  display: grid;
  justify-items: center;
  place-content: center;
  min-height: 240px;
  padding: var(--space-8);
  text-align: center;
  background: var(--surface-canvas);
  border: 1px dashed var(--line-default);
  border-radius: var(--radius-lg);
}

.course-grid-empty > span {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  color: var(--accent);
  background: var(--surface-accent);
  border-radius: var(--radius-md);
  font-size: 22px;
}

.course-grid-empty h3 {
  margin: var(--space-3) 0 var(--space-1);
  color: var(--text-strong);
  font-size: var(--text-lg);
}

.course-grid-empty p {
  max-width: 560px;
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--text-sm);
  line-height: 1.7;
}

.research-section,
.interactive-section {
  background: var(--surface-canvas);
}

.research-section {
  border-block: 1px solid var(--line-subtle);
}

.research-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: center;
  gap: clamp(32px, 6vw, 80px);
}

.research-copy,
.interactive-copy {
  min-width: 0;
}

.research-copy > p,
.interactive-copy > p {
  margin: var(--space-4) 0 var(--space-5);
  color: var(--text-subtle);
  line-height: 1.75;
}

.paper-topics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.paper-topics button {
  padding: 6px 12px;
  color: var(--text-body);
  background: var(--surface-base);
  border: 1px solid var(--line-default);
  border-radius: var(--radius-pill);
  font-size: var(--text-xs);
  cursor: pointer;
}

.paper-topics button:hover {
  color: var(--accent);
  border-color: var(--line-accent);
}

.paper-recommendations {
  margin-top: var(--space-5);
  border-top: 1px solid var(--line-subtle);
}

.paper-recommendations article {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--line-subtle);
  cursor: pointer;
}

.paper-recommendations article:hover strong {
  color: var(--accent);
}

.paper-recommendations article:focus-visible {
  outline: 2px solid var(--focus-ring);
  outline-offset: 2px;
}

.paper-recommendations article > span {
  color: var(--accent);
  font-size: var(--text-xs);
  font-weight: 650;
}

.paper-recommendations div {
  display: grid;
  gap: 2px;
}

.paper-recommendations strong {
  color: var(--text-strong);
  font-size: var(--text-sm);
  overflow-wrap: anywhere;
}

.paper-recommendations small {
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.paper-recommendations em {
  color: var(--text-subtle);
  font-size: var(--text-xs);
  font-style: normal;
}

.paper-ready-state {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  margin-top: var(--space-5);
}

.paper-ready-state > span {
  width: 100%;
  color: var(--text-body);
  font-size: var(--text-sm);
}

.paper-ready-state div {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.paper-ready-state i {
  width: 6px;
  height: 6px;
  background: var(--accent);
  border-radius: 50%;
}

.research-figure {
  position: relative;
  margin: 0;
  overflow: hidden;
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-xl);
}

.research-figure img {
  display: block;
  width: 100%;
  height: clamp(280px, 38vw, 480px);
  object-fit: cover;
}

.research-figure figcaption {
  display: grid;
  gap: 2px;
  padding: var(--space-4);
}

.research-figure figcaption strong {
  color: var(--text-strong);
  font-size: var(--text-md);
}

.research-figure figcaption small {
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.interactive-section {
  border-bottom: 1px solid var(--line-subtle);
}

.interactive-layout {
  display: grid;
  grid-template-columns: minmax(0, .8fr) minmax(0, 1.2fr);
  align-items: center;
  gap: clamp(32px, 6vw, 80px);
}

.interactive-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}

.interactive-facts span {
  display: grid;
  gap: 2px;
  padding-top: var(--space-3);
  border-top: 1px solid var(--line-default);
}

.interactive-facts strong {
  color: var(--text-strong);
  font-size: var(--text-md);
}

.interactive-facts small {
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.home-interactive-preview {
  min-width: 0;
}

.home-interactive-preview :deep(.interactive-lab-card) {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-2) !important;
}

.home-interactive-preview :deep(.experiment-block) {
  margin-bottom: 0;
  border: 0;
}

.home-interactive-preview :deep(.curve-svg) {
  height: 260px;
}

.prep-intro {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
}

.prep-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-5);
}

.prep-grid article {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 300px;
  padding: var(--space-6);
  background: var(--surface-canvas);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: border-color .15s var(--ease-standard), box-shadow .15s var(--ease-standard);
}

.prep-grid article:hover {
  border-color: var(--line-accent);
  box-shadow: var(--shadow-2);
}

.prep-grid article:focus-visible {
  outline: 2px solid var(--focus-ring);
  outline-offset: 2px;
}

.prep-card-top {
  display: flex;
  justify-content: space-between;
  color: var(--text-subtle);
  font-size: var(--text-sm);
  font-weight: 650;
}

.prep-card-top .el-icon {
  color: var(--accent);
  font-size: 22px;
}

.prep-grid h3 {
  margin: var(--space-5) 0 var(--space-2);
  color: var(--text-strong);
  font-size: var(--text-xl);
}

.prep-grid p {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--text-sm);
  line-height: 1.7;
}

.flow-path {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--space-1) var(--space-2);
  margin-top: var(--space-4);
  padding: var(--space-3);
  color: var(--text-subtle);
  background: var(--surface-base);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
}

.flow-path i {
  color: var(--text-subtle);
  font-style: normal;
}

.flow-path strong {
  color: var(--text-strong);
}

.prep-card-entry {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: var(--space-4);
  color: var(--accent);
  font-size: var(--text-sm);
  font-weight: 600;
}

.closing-section {
  padding: 0 clamp(16px, 4vw, 64px) clamp(32px, 5vw, 64px);
  background: var(--surface-base);
}

.closing-shell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-5);
  padding: clamp(24px, 4vw, 48px);
  background: var(--surface-accent);
  border: 1px solid var(--line-accent);
  border-radius: var(--radius-xl);
}

.closing-shell h2 {
  flex: 1 1 360px;
  font-size: clamp(1.375rem, 2.2vw, 2rem);
}

.closing-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.closing-actions .el-button {
  margin: 0;
}

@media (max-width: 1260px) {
  .course-card-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1024px) {
  .hero-section {
    min-height: auto;
  }

  .hero-shell,
  .research-layout,
  .interactive-layout,
  .section-heading {
    grid-template-columns: minmax(0, 1fr);
  }

  .course-card-grid,
  .prep-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .hero-actions {
    align-items: stretch;
    flex-direction: column;
    gap: var(--space-2);
  }

  .primary-action {
    width: 100%;
  }

  .text-action {
    align-self: flex-start;
  }

  .carousel-viewport {
    min-height: 560px;
  }

  .prep-preview {
    padding: var(--space-3);
  }

  .prep-preview-row {
    gap: var(--space-2);
    padding: var(--space-2);
  }

  .module-section {
    scroll-margin-top: 58px;
  }

  .course-card-grid,
  .prep-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .paper-recommendations article {
    grid-template-columns: 40px minmax(0, 1fr);
  }

  .paper-recommendations em {
    grid-column: 2;
  }

  .home-interactive-preview :deep(.curve-svg) {
    height: 190px;
  }

  .prep-grid article {
    min-height: 0;
  }

  .closing-actions,
  .closing-actions .el-button {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: .01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: .01ms !important;
  }

  [data-reveal] {
    opacity: 1;
    transform: none;
  }
}
</style>
