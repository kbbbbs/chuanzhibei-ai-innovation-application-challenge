<template>
  <div class="public-home">
    <!-- Hero Section -->
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
      </div>
    </section>

    <!-- Recommendations / Featured Content -->
    <section class="featured-section">
      <h2 class="section-title">为您推荐</h2>
      <p class="section-desc">基于您的学习路径与兴趣智能推荐的精选资源</p>

      <div class="featured-grid">
        <div class="feature-card" v-for="course in featuredCourses" :key="course.title">
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
              <el-button type="primary" link>查看详情</el-button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Platform Features -->
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
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { Collection, Connection, Cpu, DataLine, Document, Position, User } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const agentPrompt = ref('')
const featuredCourses = [
  {
    title: '大模型应用基础与提示工程',
    teacher: '主讲教师：张教授',
    category: 'AI 应用',
    badge: '智能推荐',
    learners: '1,240+',
    icon: Cpu,
    visualClass: 'visual-ai'
  },
  {
    title: '数据结构核心考点精讲',
    teacher: '主讲教师：李老师',
    category: '计算机基础',
    badge: '薄弱补强',
    learners: '980+',
    icon: DataLine,
    visualClass: 'visual-data'
  },
  {
    title: '数据库系统与知识图谱实践',
    teacher: '主讲教师：王教授',
    category: '数据库',
    badge: '项目实践',
    learners: '860+',
    icon: Collection,
    visualClass: 'visual-kb'
  },
  {
    title: '教学资源生成与试卷设计',
    teacher: '主讲教师：陈老师',
    category: '智能教学',
    badge: '教师精选',
    learners: '720+',
    icon: Document,
    visualClass: 'visual-doc'
  }
]

const handleGetStarted = () => {
  if (userStore.isLoggedIn) {
    if (userStore.roleCode === 'student') {
      router.push('/home')
    } else if (userStore.roleCode === 'teacher') {
      router.push('/courses')
    } else {
      router.push('/users')
    }
  } else {
    router.push('/login')
  }
}

const handleAgentPrompt = () => {
  const prompt = agentPrompt.value.trim()
  router.push(prompt ? { path: '/agent-chat', query: { prompt } } : '/agent-chat')
}
</script>

<style scoped>
.public-home {
  width: 100%;
  overflow: hidden;
}

.hero-section {
  min-height: calc(100vh - 64px);
  padding: 56px 24px 96px;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  text-align: center;
  background:
    linear-gradient(180deg, #FFFFFF 0%, #F8FAFC 100%);
}

.hero-content {
  max-width: 920px;
  width: 100%;
}

.hero-title {
  font-size: clamp(3.6rem, 8vw, 6.2rem);
  font-weight: 800;
  line-height: 1.1;
  margin: 0 0 24px;
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
  font-size: 1.45rem;
  line-height: 1.6;
  color: var(--text-secondary);
  margin: 0 auto 52px;
}

.hero-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}

.btn-primary {
  padding: 12px 32px;
  font-size: 1.1rem;
  border-radius: var(--radius-md);
  background-color: var(--primary-color);
  border: none;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.20);
  transition: all 0.3s ease;
  color: white;
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 30px rgba(37, 99, 235, 0.24);
  background-color: var(--primary-color-hover);
  color: white;
}

.agent-prompt-row {
  width: min(720px, 100%);
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
  min-height: 54px;
  background: rgba(255, 255, 255, 0.78);
  border-radius: var(--radius-md) 0 0 var(--radius-md);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08), 0 0 0 1px var(--border-color) inset;
  padding-left: 20px;
  transition: background 0.22s ease, box-shadow 0.22s ease;
}

.agent-prompt-row:hover .agent-prompt-input :deep(.el-input__wrapper),
.agent-prompt-row:focus-within .agent-prompt-input :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 20px 46px rgba(15, 23, 42, 0.12), 0 0 0 1px var(--primary-color) inset;
}

.agent-prompt-input :deep(.el-input__inner) {
  font-size: 1rem;
}

.agent-prompt-input :deep(.el-input-group__append) {
  border: none;
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: rgba(37, 99, 235, 0.86);
  color: white;
  box-shadow: 0 16px 40px rgba(37, 99, 235, 0.18);
  padding: 0 20px;
  transition: background 0.22s ease;
}

.agent-prompt-row:hover .agent-prompt-input :deep(.el-input-group__append),
.agent-prompt-row:focus-within .agent-prompt-input :deep(.el-input-group__append) {
  background: var(--primary-color);
}

.agent-prompt-input :deep(.el-input-group__append .el-button) {
  color: white;
  font-size: 18px;
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

.featured-section {
  max-width: 1180px;
  margin: 0 auto;
  padding: 88px 24px;
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
    padding: 80px 0;
  }

  .hero-title {
    font-size: 3.8rem;
  }

  .hero-actions {
    align-items: stretch;
  }

  .agent-prompt-row {
    width: 100%;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }
}
</style>
