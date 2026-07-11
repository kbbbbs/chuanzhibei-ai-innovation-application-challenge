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
          <div class="brand-chip">
            <span class="brand-chip-mark">E</span>
            <span>EduLoop · 智教云环</span>
          </div>

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
              <span class="agent-status"><i /> Agent 在线</span>
              <span>先说出你的目标</span>
            </div>
            <el-input
              v-model="agentPrompt"
              class="agent-prompt-input"
              placeholder="例如：帮我规划本周的数据结构复习任务"
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
          <div class="carousel-topline">
            <span>EDULOOP / PRODUCT TOUR</span>
            <span><i /> 场景实时切换</span>
          </div>

          <div class="carousel-viewport">
            <Transition :name="carouselTransitionName" mode="out-in">
              <article :key="activeShowcaseScene.id" class="showcase-slide" :class="activeShowcaseScene.tone">
                <div class="showcase-copy">
                  <span class="scene-index">0{{ activeShowcaseIndex + 1 }} / 04</span>
                  <span class="scene-kicker">{{ activeShowcaseScene.kicker }}</span>
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
                      <span>learning rate</span>
                      <strong>0.12</strong>
                    </div>
                    <svg viewBox="0 0 520 250" role="img" aria-label="梯度下降收敛曲线预览">
                      <polyline :points="miniCurvePoints" fill="none" stroke="#b6c0cf" stroke-width="2" />
                      <polyline :points="miniDescentPoints" fill="none" stroke="#2563eb" stroke-width="4" />
                      <circle
                        v-for="point in miniDescentDots"
                        :key="point"
                        :cx="point.split(',')[0]"
                        :cy="point.split(',')[1]"
                        r="5"
                        fill="#f59e0b"
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
          <span class="section-index">01 / LEARN</span>
          <div>
            <h2>课程与练习，<br>沿着同一条学习线索出现</h2>
            <p>课程推荐负责确定下一段学习方向，配套习题根据课程关联、薄弱知识点与难度递进同步组织。</p>
          </div>
        </header>

        <div class="learning-layout">
          <figure class="learning-figure">
            <img
              :src="learningRecommendationImage"
              width="1776"
              height="888"
              loading="lazy"
              alt="课程路径连接配套习题的纸艺插画"
            />
            <figcaption>
              <span><i /> 学习方向</span>
              <span><i /> 薄弱知识点</span>
              <span><i /> 难度递进</span>
            </figcaption>
          </figure>

          <div v-loading="recommendationLoading" class="recommendation-columns">
            <section class="recommendation-lane" data-testid="course-recommendations">
              <div class="lane-heading">
                <span><el-icon><Reading /></el-icon></span>
                <div><strong>推荐课程</strong><small>确定下一段学习方向</small></div>
                <em>{{ courseRecommendations.length }}</em>
              </div>

              <div v-if="courseRecommendations.length" class="recommendation-list">
                <article
                  v-for="item in courseRecommendations"
                  :key="item.id"
                  role="link"
                  tabindex="0"
                  data-testid="course-recommendation-item"
                  @click="openPath(item.targetUrl)"
                  @keydown="handlePathKeydown($event, item.targetUrl)"
                >
                  <span class="recommendation-score">{{ formatScore(item.score) }}</span>
                  <div><h3>{{ item.title }}</h3><p>{{ item.reason || item.subtitle }}</p></div>
                  <el-icon><ArrowRight /></el-icon>
                </article>
              </div>
              <div v-else class="rule-ready-state" data-testid="course-readiness">
                <span>规则已就绪</span>
                <h3>{{ recommendationStatusText('course') }}</h3>
                <p>将综合已学课程方向、课程热度、评分与难度递进生成结果。</p>
              </div>
            </section>

            <section class="recommendation-lane exercise-lane" data-testid="exercise-recommendations">
              <div class="lane-heading">
                <span><el-icon><Edit /></el-icon></span>
                <div><strong>课程配套习题</strong><small>把推荐落到可执行练习</small></div>
                <em>{{ exerciseRecommendations.length }}</em>
              </div>

              <div v-if="exerciseRecommendations.length" class="recommendation-list">
                <article
                  v-for="item in exerciseRecommendations"
                  :key="item.id"
                  role="link"
                  tabindex="0"
                  data-testid="exercise-recommendation-item"
                  @click="openPath(item.targetUrl)"
                  @keydown="handlePathKeydown($event, item.targetUrl)"
                >
                  <span class="recommendation-score">{{ formatScore(item.score) }}</span>
                  <div><h3>{{ item.title }}</h3><p>{{ item.subtitle || item.reason }}</p></div>
                  <el-icon><ArrowRight /></el-icon>
                </article>
              </div>
              <div v-else class="rule-ready-state" data-testid="exercise-readiness">
                <span>关联逻辑已就绪</span>
                <h3>{{ recommendationStatusText('exercise') }}</h3>
                <p>课程产生后，按课程 ID、成绩薄弱项和知识点匹配配套练习。</p>
              </div>
            </section>
          </div>
        </div>
      </div>
    </section>

    <section id="home-research-discovery" class="module-section research-section" data-reveal>
      <div class="section-shell research-layout">
        <div class="research-copy">
          <span class="section-index light">02 / RESEARCH</span>
          <h2>从最近所学出发，<br>发现值得追问的论文</h2>
          <p>系统根据课程方向、近期学习与薄弱主题组织论文推荐；你也可以直接输入问题或关键词开始检索。</p>

          <div class="paper-search-box">
            <span class="search-label">RESEARCH SEARCH</span>
            <el-input
              v-model="paperKeyword"
              placeholder="输入论文标题、研究问题或关键词"
              @keyup.enter="openPaperSearch"
            >
              <template #append>
                <el-button aria-label="搜索论文" @click="openPaperSearch"><el-icon><Search /></el-icon></el-button>
              </template>
            </el-input>
          </div>
          <div class="paper-topics" aria-label="常用研究主题">
            <button v-for="topic in paperTopics" :key="topic" type="button" @click="searchPaperTopic(topic)">
              {{ topic }} <span>↗</span>
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
              <span>{{ paper.meta?.year || 'NEW' }}</span>
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
            <span>RELATED RESEARCH</span>
            <strong>从学习记录中提取研究线索</strong>
            <small>本地论文库 · arXiv · OpenAlex · Semantic Scholar</small>
          </figcaption>
        </figure>
      </div>
    </section>

    <section id="home-interactive-lab" class="module-section interactive-section" data-reveal>
      <div class="section-shell interactive-layout">
        <div class="interactive-copy">
          <span class="section-index">03 / EXPERIMENT</span>
          <h2>知识不只用来看，<br>更要亲手改变它</h2>
          <p>互动 JSON 将实验参数、可视化和课时内容组合在一起。拖动学习率，直接观察梯度下降从收敛到震荡的变化。</p>
          <div class="interactive-facts">
            <span><strong>0.01—1.20</strong><small>学习率范围</small></span>
            <span><strong>18</strong><small>轨迹步骤</small></span>
            <span><strong>JSON</strong><small>内容驱动</small></span>
          </div>
          <el-button type="primary" size="large" @click="openPath('/courses/109')">
            查看完整互动课时 <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>

        <div class="home-interactive-preview" data-testid="home-interactive-preview">
          <span class="preview-label">LIVE / INTERACTIVE</span>
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
        <header class="prep-heading">
          <div>
            <span class="section-index">04 / CREATE</span>
            <h2>把资料交给 Agent，<br>把判断留给教师</h2>
          </div>
          <div>
            <p>自然语言、PPT 与课程文件进入同一个备课工作流，生成内容可继续编辑并导入对应教学模块。</p>
            <el-button type="primary" size="large" @click="openPath('/resource-gen')">
              打开智能备课 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </header>

        <div class="agent-hub" aria-hidden="true">
          <span class="hub-line" />
          <div><el-icon><Cpu /></el-icon><strong>Agent</strong><small>理解材料 · 组织结构 · 输出草稿</small></div>
        </div>

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
        <div>
          <span>EDULOOP</span>
          <h2>学习、研究、实验与创造，<br>在一处自然衔接。</h2>
        </div>
        <div class="closing-actions">
          <el-button type="primary" size="large" @click="handleGetStarted">
            {{ userStore.isLoggedIn ? '返回工作台' : '现在开始' }} <el-icon><ArrowRight /></el-icon>
          </el-button>
          <button type="button" @click="handleAgentPrompt">与 Agent 对话</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft, ArrowRight, Cpu, Document, Edit,
  Position, Reading, Search, Tickets
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getMyRecommendations } from '@/api/recommendation'
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
const exerciseRecommendations = ref([])
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
    description: '以梯度下降为例，让学习率、收敛轨迹与代码结果在同一个互动课时中彼此印证。',
    action: '体验互动课时', path: '/courses/109'
  },
  {
    id: 'prep', kicker: '智能备课', visual: 'prep', tone: 'tone-prep',
    title: '从自然语言或文件，推进到教学草稿',
    description: '接入 Agent，将 PPT 与课程材料组织成大纲、课堂讲义和配套题库，再进入对应模块继续编辑。',
    action: '打开智能备课', path: '/resource-gen'
  }
]

const activeShowcaseScene = computed(() => showcaseScenes[activeShowcaseIndex.value])
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
  if (!userStore.isLoggedIn) {
    courseRecommendations.value = []
    exerciseRecommendations.value = []
    paperRecommendations.value = []
    recommendationLoading.value = false
    return
  }

  recommendationLoading.value = true
  const configs = [
    { type: 'course', limit: 3 },
    { type: 'exercise', limit: 3 },
    { type: 'paper', limit: 3 }
  ]
  const results = await Promise.allSettled(configs.map(config => getMyRecommendations(config)))
  if (requestId !== recommendationRequestId) return

  const values = { course: [], exercise: [], paper: [] }
  results.forEach((result, index) => {
    const type = configs[index].type
    if (result.status === 'fulfilled' && result.value?.code === '200' && Array.isArray(result.value.data?.items)) {
      values[type] = result.value.data.items.map((item, itemIndex) => normalizeRecommendation(item, type, itemIndex))
    } else {
      recommendationErrors.value[type] = true
    }
  })

  courseRecommendations.value = values.course
  exerciseRecommendations.value = values.exercise
  paperRecommendations.value = values.paper
  recommendationLoading.value = false
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
    targetUrl: item?.targetUrl || fallbackPath,
    meta: item?.meta || {}
  }
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
  --ink: #111827;
  --muted: #647084;
  --line: rgba(15, 23, 42, .12);
  --paper: #f7f4ed;
  width: 100%;
  overflow: hidden;
  color: var(--ink);
  background: #fbfcfe;
}

button { color: inherit; }

.hero-section {
  position: relative;
  min-height: calc(100vh - 64px);
  padding: clamp(50px, 6vh, 86px) clamp(24px, 4vw, 72px) clamp(58px, 7vh, 92px);
  isolation: isolate;
  background: #f8fafc;
}

.hero-art,
.hero-wash {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.hero-art {
  z-index: -3;
  object-fit: cover;
  object-position: center;
  opacity: .36;
  animation: hero-drift 18s ease-in-out infinite alternate;
}

.hero-wash {
  z-index: -2;
  background:
    linear-gradient(90deg, rgba(250, 252, 255, .98) 0%, rgba(250, 252, 255, .92) 34%, rgba(250, 252, 255, .64) 63%, rgba(250, 252, 255, .78) 100%),
    linear-gradient(180deg, transparent 60%, #f7f9fc 100%);
}

.hero-shell,
.section-shell {
  width: min(1540px, 100%);
  margin: 0 auto;
}

.hero-shell {
  display: grid;
  grid-template-columns: minmax(430px, .8fr) minmax(680px, 1.2fr);
  align-items: center;
  gap: clamp(48px, 6vw, 108px);
}

.hero-copy { max-width: 680px; }

.brand-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 25px;
  padding: 6px 12px 6px 7px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(255, 255, 255, .78);
  color: #425066;
  font-size: .78rem;
  font-weight: 800;
  letter-spacing: .08em;
  backdrop-filter: blur(12px);
  animation: rise-in .65s ease both;
}

.brand-chip-mark {
  display: grid;
  place-items: center;
  width: 25px;
  height: 25px;
  border-radius: 8px;
  color: white;
  background: #2563eb;
}

.hero-copy h1 {
  margin: 0;
  font-size: clamp(3rem, 3.7vw, 4.45rem);
  line-height: 1.07;
  letter-spacing: -.055em;
  font-weight: 820;
  animation: rise-in .72s .08s ease both;
}

.hero-copy h1 span,
.hero-copy h1 em { display: block; white-space: nowrap; }
.hero-copy h1 em { color: #2563eb; font-style: normal; }

.hero-subtitle {
  max-width: 640px;
  margin: 26px 0 0;
  color: var(--muted);
  font-size: clamp(1rem, 1.08vw, 1.18rem);
  line-height: 1.85;
  animation: rise-in .72s .16s ease both;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 28px;
  margin-top: 32px;
  animation: rise-in .72s .24s ease both;
}

.primary-action {
  height: 52px;
  padding: 0 24px;
  border-radius: 12px;
  box-shadow: 0 14px 30px rgba(37, 99, 235, .2);
}
.primary-action :deep(span) { display: flex; align-items: center; gap: 12px; }

.text-action {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border: 0;
  border-bottom: 1px solid #aeb8c7;
  background: transparent;
  font-weight: 700;
  cursor: pointer;
}

.agent-composer {
  margin-top: 36px;
  padding: 16px;
  border: 1px solid rgba(37, 99, 235, .14);
  border-radius: 18px;
  background: rgba(255, 255, 255, .8);
  box-shadow: 0 18px 50px rgba(15, 23, 42, .08);
  backdrop-filter: blur(18px);
  animation: rise-in .72s .31s ease both;
}

.composer-heading {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #778397;
  font-size: .78rem;
  font-weight: 700;
}

.agent-status { color: #1d4ed8; }
.agent-status i,
.carousel-topline i {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 6px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, .12);
}

.agent-prompt-input :deep(.el-input__wrapper) {
  min-height: 52px;
  padding-left: 16px;
  border-radius: 11px 0 0 11px;
  background: #f8fafc;
  box-shadow: none !important;
}
.agent-prompt-input :deep(.el-input-group__append) {
  width: 56px;
  padding: 0;
  border: 0;
  border-radius: 0 11px 11px 0;
  background: #111827;
  color: white;
  box-shadow: none;
}
.agent-prompt-input :deep(.el-input-group__append .el-button) { width: 100%; height: 52px; color: white; font-size: 20px; }

.prompt-suggestions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.prompt-suggestions button {
  padding: 6px 9px;
  border: 0;
  border-radius: 7px;
  background: #eef2f7;
  color: #64748b;
  font-size: .72rem;
  cursor: pointer;
  transition: color .2s ease, background .2s ease, transform .2s ease;
}
.prompt-suggestions button:hover { color: #1d4ed8; background: #e7efff; transform: translateY(-1px); }

.hero-proof {
  display: flex;
  flex-wrap: wrap;
  gap: 26px;
  margin-top: 22px;
  color: #718096;
  font-size: .77rem;
  animation: rise-in .72s .38s ease both;
}
.hero-proof span { display: flex; align-items: baseline; gap: 6px; }
.hero-proof strong { color: #111827; font-size: 1rem; }

.showcase-carousel {
  min-width: 0;
  padding: 16px;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 28px;
  outline: none;
  background: rgba(255, 255, 255, .76);
  box-shadow: 0 35px 90px rgba(23, 43, 77, .16);
  backdrop-filter: blur(24px);
  animation: board-in .86s .16s ease both;
}
.showcase-carousel:focus-visible { box-shadow: 0 0 0 3px rgba(37, 99, 235, .22), 0 35px 90px rgba(23, 43, 77, .16); }

.carousel-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 38px;
  padding: 0 7px;
  color: #68758a;
  font-size: .66rem;
  font-weight: 800;
  letter-spacing: .1em;
}
.carousel-topline span:last-child { letter-spacing: 0; }

.carousel-viewport { position: relative; min-height: clamp(540px, 59vh, 660px); overflow: hidden; border-radius: 20px; }
.showcase-slide {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  padding: clamp(28px, 3vw, 46px);
  border-radius: 20px;
  overflow: hidden;
}
.showcase-slide::before { content: ""; position: absolute; right: -120px; top: -150px; width: 390px; height: 390px; border-radius: 50%; background: currentColor; opacity: .07; }
.tone-learning { color: #2563eb; background: linear-gradient(145deg, #f7faff, #eaf1ff); }
.tone-research { color: #6652ba; background: linear-gradient(145deg, #fbfaff, #eeeaff); }
.tone-interactive { color: #087f73; background: linear-gradient(145deg, #f5fcfa, #e4f5f0); }
.tone-prep { color: #a86109; background: linear-gradient(145deg, #fffdf8, #fff1ce); }

.showcase-copy { position: relative; z-index: 2; max-width: 630px; }
.scene-index { display: block; color: #8b96a8; font-size: .68rem; font-weight: 800; letter-spacing: .13em; }
.scene-kicker { display: block; margin-top: 18px; color: currentColor; font-size: .72rem; font-weight: 900; letter-spacing: .16em; }
.showcase-copy h2 { margin: 9px 0 10px; color: #172033; font-size: clamp(1.75rem, 2.15vw, 2.65rem); line-height: 1.18; letter-spacing: -.04em; }
.showcase-copy p { max-width: 590px; margin: 0; color: #667085; line-height: 1.72; font-size: .88rem; }

.showcase-visual {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 280px;
  margin-top: 24px;
  border: 1px solid rgba(255, 255, 255, .9);
  border-radius: 18px;
  background: rgba(255, 255, 255, .72);
  box-shadow: 0 22px 54px rgba(31, 51, 81, .11);
  overflow: hidden;
}
.showcase-visual > img { width: 100%; height: 100%; object-fit: cover; display: block; }

.mini-lab { height: 100%; min-height: 280px; padding: 20px; background: #fbfdfd; }
.mini-lab-head { display: flex; justify-content: space-between; color: #5e6b7e; font-size: .72rem; text-transform: uppercase; letter-spacing: .1em; }
.mini-lab-head strong { color: #087f73; font-size: 1rem; }
.mini-lab svg { width: 100%; height: calc(100% - 66px); min-height: 210px; }
.mini-slider { position: relative; height: 5px; border-radius: 99px; background: #d8e3e1; }
.mini-slider span { display: block; width: 38%; height: 100%; border-radius: inherit; background: #087f73; }
.mini-slider i { position: absolute; left: 38%; top: 50%; width: 18px; height: 18px; border: 4px solid white; border-radius: 50%; background: #087f73; box-shadow: 0 3px 10px rgba(8, 127, 115, .3); transform: translate(-50%, -50%); }

.prep-preview { display: grid; align-content: center; gap: 12px; min-height: 100%; padding: 26px; }
.prep-agent { display: inline-grid; grid-template-columns: 34px auto; align-items: center; justify-self: center; gap: 10px; margin-bottom: 10px; padding: 10px 18px; border-radius: 99px; background: #111827; color: white; }
.prep-agent .el-icon { font-size: 22px; }
.prep-agent span { font-weight: 800; }
.prep-preview-row { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 16px; padding: 16px; border-radius: 12px; background: rgba(255, 255, 255, .82); color: #6f7785; font-size: .76rem; }
.prep-preview-row i { color: #a86109; font-style: normal; }
.prep-preview-row strong { color: #2d3544; }

.showcase-entry {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  align-self: flex-end;
  margin-top: 15px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #39465a;
  font-size: .76rem;
  font-weight: 800;
  cursor: pointer;
}
.showcase-entry > span { display: grid; place-items: center; width: 31px; height: 31px; border-radius: 50%; background: #111827; color: white; transition: transform .2s ease; }
.showcase-entry:hover > span { transform: translateX(3px); }

.carousel-controls { display: grid; grid-template-columns: 38px 1fr 38px; align-items: center; gap: 12px; padding: 15px 4px 0; }
.carousel-controls > button { display: grid; place-items: center; width: 36px; height: 36px; border: 1px solid var(--line); border-radius: 50%; background: white; cursor: pointer; transition: color .2s ease, border .2s ease, transform .2s ease; }
.carousel-controls > button:hover { color: #2563eb; border-color: #a9bfee; transform: scale(1.05); }
.carousel-dots { display: flex; justify-content: center; gap: 8px; }
.carousel-dots button { width: 34px; height: 16px; padding: 6px 0; border: 0; background: transparent; cursor: pointer; }
.carousel-dots span { display: block; width: 100%; height: 3px; border-radius: 99px; background: #cbd3df; transform-origin: left; transition: background .25s ease, transform .25s ease; }
.carousel-dots button.active span { background: #2563eb; transform: scaleY(1.7); }

.carousel-next-enter-active, .carousel-next-leave-active,
.carousel-prev-enter-active, .carousel-prev-leave-active { transition: opacity .42s ease, transform .5s cubic-bezier(.22, .8, .24, 1); }
.carousel-next-enter-from { opacity: 0; transform: translateX(46px) scale(.985); }
.carousel-next-leave-to { opacity: 0; transform: translateX(-34px) scale(.985); }
.carousel-prev-enter-from { opacity: 0; transform: translateX(-46px) scale(.985); }
.carousel-prev-leave-to { opacity: 0; transform: translateX(34px) scale(.985); }

.module-section { padding: clamp(94px, 9vw, 148px) clamp(24px, 4vw, 72px); scroll-margin-top: 72px; }
[data-reveal] { opacity: 0; transform: translateY(34px); transition: opacity .7s ease, transform .8s cubic-bezier(.2, .72, .18, 1); }
[data-reveal].is-visible { opacity: 1; transform: translateY(0); }

.section-index { display: block; color: #2563eb; font-size: .68rem; font-weight: 900; letter-spacing: .18em; }
.section-index.light { color: #9db8ff; }
.section-heading { display: grid; grid-template-columns: 220px 1fr; margin-bottom: 58px; }
.section-heading > div { display: flex; justify-content: space-between; align-items: flex-end; gap: 60px; }
.section-heading h2,
.research-copy h2,
.interactive-copy h2,
.prep-heading h2,
.closing-shell h2 { margin: 0; font-size: clamp(2.35rem, 3.35vw, 4.35rem); line-height: 1.12; letter-spacing: -.055em; }
.section-heading p { max-width: 530px; margin: 0 0 4px; color: var(--muted); line-height: 1.8; }

.learning-section { background: #fff; }
.learning-layout { display: grid; grid-template-columns: minmax(380px, .82fr) minmax(650px, 1.18fr); align-items: stretch; gap: clamp(32px, 5vw, 78px); }
.learning-figure { position: relative; min-height: 560px; margin: 0; border-radius: 22px; overflow: hidden; background: var(--paper); }
.learning-figure img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 1.1s cubic-bezier(.2, .7, .2, 1); }
.learning-section.is-visible .learning-figure img { transform: scale(1.035); }
.learning-figure figcaption { position: absolute; left: 20px; right: 20px; bottom: 20px; display: flex; flex-wrap: wrap; gap: 8px; padding: 13px; border-radius: 13px; background: rgba(255, 255, 255, .86); backdrop-filter: blur(12px); }
.learning-figure figcaption span { display: flex; align-items: center; gap: 7px; padding: 6px 9px; border-radius: 99px; background: #f3f6fa; color: #59667a; font-size: .68rem; font-weight: 700; }
.learning-figure figcaption i { width: 6px; height: 6px; border-radius: 50%; background: #2563eb; }

.recommendation-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.recommendation-lane { display: flex; flex-direction: column; min-width: 0; padding: 24px; border: 1px solid var(--line); border-radius: 18px; background: #fbfcfe; }
.exercise-lane { background: #f7fbfa; }
.lane-heading { display: grid; grid-template-columns: 46px 1fr auto; align-items: center; gap: 13px; padding-bottom: 20px; border-bottom: 1px solid var(--line); }
.lane-heading > span { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 13px; background: #eaf1ff; color: #2563eb; font-size: 21px; }
.exercise-lane .lane-heading > span { background: #e4f5f0; color: #087f73; }
.lane-heading div { display: grid; gap: 4px; }
.lane-heading strong { font-size: 1rem; }
.lane-heading small { color: #8490a2; font-size: .68rem; }
.lane-heading em { color: #a0a9b7; font-size: 1.3rem; font-style: normal; font-weight: 800; }

.recommendation-list { display: grid; gap: 0; }
.recommendation-list article { display: grid; grid-template-columns: 46px 1fr 20px; align-items: center; gap: 12px; padding: 18px 4px; border-bottom: 1px solid var(--line); cursor: pointer; transition: transform .2s ease, color .2s ease; }
.recommendation-list article:hover { color: #1d4ed8; transform: translateX(4px); }
.recommendation-score { display: grid; place-items: center; width: 43px; height: 43px; border-radius: 50%; background: #edf3ff; color: #2563eb; font-size: .66rem; font-weight: 900; }
.exercise-lane .recommendation-score { background: #e4f5f0; color: #087f73; }
.recommendation-list h3 { margin: 0 0 5px; font-size: .86rem; line-height: 1.35; }
.recommendation-list p { display: -webkit-box; margin: 0; color: #7d899a; font-size: .68rem; line-height: 1.45; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }

.rule-ready-state { display: flex; flex: 1; flex-direction: column; justify-content: flex-end; min-height: 315px; padding: 22px 4px 4px; }
.rule-ready-state > span { align-self: flex-start; padding: 5px 8px; border: 1px solid #bdd0f8; border-radius: 99px; color: #2563eb; font-size: .62rem; font-weight: 900; }
.exercise-lane .rule-ready-state > span { color: #087f73; border-color: #aad7cd; }
.rule-ready-state h3 { margin: 16px 0 8px; font-size: 1.28rem; line-height: 1.35; }
.rule-ready-state p { margin: 0; color: #758195; font-size: .78rem; line-height: 1.7; }

.research-section { color: white; background: #111827; }
.research-layout { display: grid; grid-template-columns: minmax(480px, .92fr) minmax(600px, 1.08fr); align-items: center; gap: clamp(52px, 7vw, 120px); }
.research-copy h2 { margin-top: 18px; }
.research-copy > p { max-width: 680px; margin: 25px 0 32px; color: #aab5c6; line-height: 1.85; }
.paper-search-box { padding: 14px; border: 1px solid rgba(255, 255, 255, .14); border-radius: 15px; background: rgba(255, 255, 255, .06); }
.search-label { display: block; margin: 0 0 9px 5px; color: #718096; font-size: .62rem; font-weight: 900; letter-spacing: .14em; }
.paper-search-box :deep(.el-input__wrapper) { min-height: 52px; padding-left: 16px; border-radius: 9px 0 0 9px; background: white; box-shadow: none !important; }
.paper-search-box :deep(.el-input-group__append) { width: 58px; padding: 0; border: 0; border-radius: 0 9px 9px 0; background: #3b6ff5; color: white; box-shadow: none; }
.paper-search-box :deep(.el-input-group__append .el-button) { width: 100%; height: 52px; color: white; font-size: 19px; }
.paper-topics { display: flex; flex-wrap: wrap; gap: 10px 22px; margin-top: 18px; }
.paper-topics button { padding: 7px 0; border: 0; border-bottom: 1px solid #4a5568; background: transparent; color: #b9c3d2; font-size: .74rem; cursor: pointer; }
.paper-topics button:hover { color: white; border-color: #8eb4ff; }
.paper-topics span { color: #6d8ee8; }

.paper-recommendations { display: grid; gap: 0; margin-top: 28px; border-top: 1px solid rgba(255, 255, 255, .13); }
.paper-recommendations article { display: grid; grid-template-columns: 48px 1fr auto; align-items: center; gap: 14px; padding: 15px 0; border-bottom: 1px solid rgba(255, 255, 255, .13); cursor: pointer; }
.paper-recommendations article > span { color: #8eb4ff; font-size: .68rem; font-weight: 900; }
.paper-recommendations div { display: grid; gap: 5px; }
.paper-recommendations strong { font-size: .82rem; }
.paper-recommendations small { color: #8f9bad; font-size: .66rem; }
.paper-recommendations em { color: #bdc8d8; font-size: .68rem; font-style: normal; }
.paper-ready-state { display: flex; flex-wrap: wrap; gap: 9px 16px; margin-top: 30px; }
.paper-ready-state > span { width: 100%; color: #bdc8d8; font-size: .76rem; }
.paper-ready-state div { display: flex; align-items: center; gap: 7px; color: #8794a8; font-size: .68rem; }
.paper-ready-state i { width: 6px; height: 6px; border-radius: 50%; background: #8eb4ff; }

.research-figure { position: relative; min-height: 590px; margin: 0; border-radius: 24px; overflow: hidden; background: var(--paper); box-shadow: 0 32px 90px rgba(0, 0, 0, .3); }
.research-figure img { width: 100%; height: 100%; min-height: 590px; object-fit: cover; display: block; transition: transform 1.1s cubic-bezier(.2, .7, .2, 1); }
.research-section.is-visible .research-figure img { transform: scale(1.035); }
.research-figure figcaption { position: absolute; left: 22px; right: 22px; bottom: 22px; display: grid; gap: 6px; padding: 18px; border-radius: 14px; color: #253044; background: rgba(255, 255, 255, .88); backdrop-filter: blur(12px); }
.research-figure figcaption span { color: #6652ba; font-size: .61rem; font-weight: 900; letter-spacing: .13em; }
.research-figure figcaption strong { font-size: 1rem; }
.research-figure figcaption small { color: #778397; font-size: .68rem; }

.interactive-section { background: #f5f1e9; }
.interactive-layout { display: grid; grid-template-columns: minmax(380px, .72fr) minmax(720px, 1.28fr); align-items: center; gap: clamp(55px, 8vw, 130px); }
.interactive-copy h2 { margin-top: 18px; }
.interactive-copy > p { margin: 25px 0 28px; color: var(--muted); line-height: 1.85; }
.interactive-facts { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 30px; }
.interactive-facts span { display: grid; gap: 5px; padding-top: 12px; border-top: 1px solid #c8c0b3; }
.interactive-facts strong { font-size: 1.05rem; }
.interactive-facts small { color: #877f73; font-size: .64rem; }

.home-interactive-preview { position: relative; padding: 20px; border: 1px solid #ded7cb; border-radius: 24px; background: rgba(255, 255, 255, .66); box-shadow: 0 30px 70px rgba(76, 66, 48, .12); }
.preview-label { display: block; margin: 0 0 12px 4px; color: #087f73; font-size: .62rem; font-weight: 900; letter-spacing: .15em; }
.home-interactive-preview :deep(.interactive-lab-card) { border: 0; border-radius: 18px; box-shadow: none !important; background: #fff; }
.home-interactive-preview :deep(.el-card__header) { border-bottom-color: #e8e2d8; }
.home-interactive-preview :deep(.experiment-block) { margin-bottom: 0; padding: 18px; border: 0; background: #fbfcfd; }
.home-interactive-preview :deep(.curve-svg) { height: 260px; }

.prep-section { background: #fff; }
.prep-heading { display: grid; grid-template-columns: 1fr .72fr; align-items: end; gap: 80px; }
.prep-heading h2 { margin-top: 18px; }
.prep-heading > div:last-child { display: grid; justify-items: start; gap: 25px; }
.prep-heading p { margin: 0; color: var(--muted); line-height: 1.8; }
.agent-hub { position: relative; display: grid; place-items: center; margin: 76px 0 34px; }
.hub-line { position: absolute; left: 16%; right: 16%; height: 1px; background: linear-gradient(90deg, transparent, #ccd4df 15%, #ccd4df 85%, transparent); }
.agent-hub > div { position: relative; z-index: 1; display: grid; grid-template-columns: 38px auto; align-items: center; gap: 3px 12px; padding: 14px 22px; border: 1px solid #2a3547; border-radius: 15px; background: #111827; color: white; box-shadow: 0 15px 35px rgba(17, 24, 39, .18); }
.agent-hub .el-icon { grid-row: 1 / 3; font-size: 27px; color: #8eb4ff; }
.agent-hub strong { font-size: .88rem; }
.agent-hub small { color: #97a3b7; font-size: .61rem; }

.prep-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.prep-grid article { position: relative; display: flex; flex-direction: column; min-height: 330px; padding: 28px; border: 1px solid var(--line); border-radius: 18px; background: #fbfcfe; cursor: pointer; overflow: hidden; transition: transform .25s ease, border .25s ease, box-shadow .25s ease; }
.prep-grid article::before { content: ""; position: absolute; right: -70px; top: -80px; width: 170px; height: 170px; border-radius: 50%; background: #eaf1ff; transition: transform .4s ease; }
.prep-grid article:nth-child(2)::before { background: #e4f5f0; }
.prep-grid article:nth-child(3)::before { background: #eeeaff; }
.prep-grid article:hover { transform: translateY(-6px); border-color: rgba(37, 99, 235, .28); box-shadow: 0 24px 55px rgba(15, 23, 42, .1); }
.prep-grid article:hover::before { transform: scale(1.15); }
.prep-card-top { position: relative; z-index: 1; display: flex; justify-content: space-between; color: #8995a7; font-size: .7rem; font-weight: 900; }
.prep-card-top .el-icon { color: #2563eb; font-size: 25px; }
.prep-grid h3 { position: relative; z-index: 1; margin: 42px 0 10px; font-size: 1.35rem; }
.prep-grid p { position: relative; z-index: 1; margin: 0; color: #6c788a; font-size: .78rem; line-height: 1.7; }
.flow-path { position: relative; z-index: 1; display: grid; grid-template-columns: 1fr auto; gap: 8px 10px; margin-top: 22px; padding: 14px; border-radius: 12px; background: white; color: #768296; font-size: .67rem; }
.flow-path i { color: #9ba5b3; font-style: normal; }
.flow-path strong { color: #2e394b; }
.prep-card-entry { position: relative; z-index: 1; display: flex; align-items: center; gap: 8px; margin-top: auto; padding-top: 22px; color: #334056; font-size: .73rem; font-weight: 900; }

.closing-section { padding: 0 clamp(24px, 4vw, 72px) clamp(24px, 4vw, 72px); background: #fff; }
.closing-shell { position: relative; display: flex; align-items: center; justify-content: space-between; gap: 50px; overflow: hidden; padding: clamp(46px, 5vw, 70px); border: 1px solid #dce4ef; border-radius: 24px; color: #172033; background: #f4f7fb; box-shadow: 0 22px 60px rgba(32, 53, 86, .08); }
.closing-shell::before { content: ''; position: absolute; top: 0; bottom: 0; left: 0; width: 9px; background: #2563eb; }
.closing-shell::after { content: ''; position: absolute; width: 340px; height: 340px; top: -210px; right: 17%; border: 1px solid rgba(37, 99, 235, .14); border-radius: 50%; box-shadow: 0 0 0 46px rgba(37, 99, 235, .035), 0 0 0 92px rgba(16, 185, 129, .025); pointer-events: none; }
.closing-shell > * { position: relative; z-index: 1; }
.closing-shell > div:first-child > span { display: block; margin-bottom: 18px; color: #2563eb; font-size: .68rem; font-weight: 900; letter-spacing: .18em; }
.closing-shell h2 { font-size: clamp(2rem, 3vw, 3.7rem); }
.closing-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; padding: 10px; border: 1px solid #dfe6f0; border-radius: 17px; background: rgba(255, 255, 255, .88); box-shadow: 0 12px 30px rgba(42, 60, 92, .08); }
.closing-actions .el-button { height: 48px; padding: 0 20px; color: white; background: #2563eb; border-color: #2563eb; border-radius: 11px; }
.closing-actions .el-button:hover { color: white; background: #1d4ed8; border-color: #1d4ed8; transform: translateY(-1px); }
.closing-actions button { min-height: 48px; padding: 0 17px; border: 0; border-radius: 11px; background: transparent; color: #35425a; font-weight: 800; cursor: pointer; transition: color .18s ease, background .18s ease; }
.closing-actions button:hover { color: #1d4ed8; background: #edf3ff; }

@keyframes rise-in { from { opacity: 0; transform: translateY(18px); } to { opacity: 1; transform: translateY(0); } }
@keyframes board-in { from { opacity: 0; transform: translateX(26px) scale(.985); } to { opacity: 1; transform: translateX(0) scale(1); } }
@keyframes hero-drift { from { transform: scale(1.02) translate3d(0, 0, 0); } to { transform: scale(1.07) translate3d(-1.2%, -.7%, 0); } }

@media (min-width: 1800px) {
  .hero-shell { grid-template-columns: minmax(520px, .82fr) minmax(760px, 1.18fr); }
  .showcase-carousel { transform: scale(1.015); transform-origin: center left; }
}

@media (max-width: 1260px) {
  .hero-shell { grid-template-columns: minmax(380px, .82fr) minmax(550px, 1.18fr); gap: 42px; }
  .learning-layout { grid-template-columns: minmax(320px, .72fr) minmax(590px, 1.28fr); }
  .research-layout { grid-template-columns: minmax(420px, .86fr) minmax(520px, 1.14fr); }
  .interactive-layout { grid-template-columns: minmax(330px, .72fr) minmax(600px, 1.28fr); }
}

@media (max-width: 1024px) {
  .hero-section { min-height: auto; padding-top: 58px; }
  .hero-shell,
  .learning-layout,
  .research-layout,
  .interactive-layout { grid-template-columns: 1fr; }
  .hero-copy { max-width: 760px; }
  .showcase-carousel { max-width: 860px; }
  .section-heading { grid-template-columns: 150px 1fr; }
  .section-heading > div { display: block; }
  .section-heading p { margin-top: 24px; }
  .learning-figure { min-height: 450px; }
  .research-copy { max-width: 760px; }
  .research-figure { max-width: 780px; width: 100%; }
  .interactive-copy { max-width: 700px; }
  .prep-heading { grid-template-columns: 1fr; gap: 28px; }
}

@media (max-width: 760px) {
  .hero-section { padding: 42px 16px 64px; }
  .hero-art { object-position: 68% center; opacity: .27; }
  .hero-wash { background: linear-gradient(180deg, rgba(250, 252, 255, .95), rgba(250, 252, 255, .8)); }
  .hero-copy h1 { font-size: clamp(2.45rem, 11vw, 3.45rem); }
  .hero-copy h1 span,
  .hero-copy h1 em { white-space: normal; }
  .hero-actions { align-items: flex-start; flex-direction: column; gap: 14px; }
  .primary-action { width: 100%; }
  .hero-proof { gap: 10px 20px; }
  .showcase-carousel { padding: 10px; border-radius: 20px; }
  .carousel-topline { padding: 0 5px; }
  .carousel-topline > span:first-child { display: none; }
  .carousel-topline > span:last-child { margin-left: auto; }
  .carousel-viewport { min-height: 600px; }
  .showcase-slide { padding: 24px 18px; }
  .showcase-copy h2 { font-size: 1.72rem; }
  .showcase-visual { min-height: 255px; }
  .prep-preview { padding: 14px; }
  .prep-preview-row { gap: 8px; padding: 12px 10px; font-size: .64rem; }
  .module-section { padding: 78px 16px; scroll-margin-top: 58px; }
  .section-heading { grid-template-columns: 1fr; gap: 18px; margin-bottom: 36px; }
  .section-heading h2,
  .research-copy h2,
  .interactive-copy h2,
  .prep-heading h2 { font-size: 2.25rem; }
  .learning-figure { min-height: 330px; }
  .recommendation-columns { grid-template-columns: 1fr; }
  .rule-ready-state { min-height: 210px; }
  .research-figure,
  .research-figure img { min-height: 380px; }
  .paper-recommendations article { grid-template-columns: 40px 1fr; }
  .paper-recommendations em { grid-column: 2; }
  .interactive-facts { grid-template-columns: 1fr; }
  .home-interactive-preview { padding: 10px; }
  .home-interactive-preview :deep(.experiment-block) { padding: 12px; }
  .home-interactive-preview :deep(.curve-svg) { height: 190px; }
  .agent-hub { margin-top: 48px; }
  .hub-line { left: 0; right: 0; }
  .prep-grid { grid-template-columns: 1fr; }
  .prep-grid article { min-height: 300px; }
  .closing-section { padding: 0 16px 16px; }
  .closing-shell { align-items: flex-start; flex-direction: column; padding: 38px 24px; }
  .closing-shell h2 { font-size: 2rem; }
  .closing-actions { align-items: flex-start; flex-direction: column; width: 100%; gap: 12px; }
  .closing-actions .el-button,
  .closing-actions button { width: 100%; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: .01ms !important; animation-iteration-count: 1 !important; scroll-behavior: auto !important; transition-duration: .01ms !important; }
  [data-reveal] { opacity: 1; transform: none; }
  .hero-art { transform: none; }
}
</style>
