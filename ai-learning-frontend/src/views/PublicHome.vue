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
            立即开启学习之旅
          </el-button>
          <el-button size="large" class="btn-secondary" @click="handleExplore">
            探索推荐
          </el-button>
        </div>
      </div>

      <!-- AI Assistant Card on Homepage -->
      <div class="hero-ai-widget">
        <AiChatWidget :inline="true" />
      </div>
    </section>

    <!-- Recommendations / Featured Content -->
    <section class="featured-section">
      <h2 class="section-title">为您推荐</h2>
      <p class="section-desc">基于您的学习路径与兴趣智能推荐的精选资源</p>

      <div class="featured-grid">
        <div class="feature-card" v-for="i in 4" :key="i">
          <div class="card-image-placeholder"></div>
          <div class="card-content">
            <div class="card-tags">
              <span class="tag tag-blue">计算机科学</span>
              <span class="tag tag-purple">AI 推荐</span>
            </div>
            <h3 class="card-title">高级人工智能与大模型应用 - 第{{ i }}期</h3>
            <p class="card-author">主讲教师：张教授</p>
            <div class="card-footer">
              <div class="stats">
                <el-icon><User /></el-icon> 1,200+ 在学
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
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { Cpu, DataLine, Connection, User } from '@element-plus/icons-vue'
import AiChatWidget from '@/components/AiChatWidget.vue'

const router = useRouter()
const userStore = useUserStore()

const handleGetStarted = () => {
  if (userStore.isLoggedIn) {
    if (userStore.roleCode === 'student') {
      router.push('/my-classes')
    } else if (userStore.roleCode === 'teacher') {
      router.push('/courses')
    } else {
      router.push('/users')
    }
  } else {
    router.push('/login')
  }
}

const handleExplore = () => {
  if (userStore.isLoggedIn) {
    router.push('/recommendation')
  } else {
    router.push('/login')
  }
}
</script>

<style scoped>
.public-home {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
}

.hero-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 60px;
  min-height: 85vh;
  padding: 60px 0;
}

.hero-content {
  flex: 1;
  max-width: 600px;
}

.hero-title {
  font-size: 4rem;
  font-weight: 800;
  line-height: 1.1;
  margin-bottom: 24px;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.gradient-text {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-color-hover));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-subtitle {
  font-size: 1.25rem;
  line-height: 1.6;
  color: var(--text-secondary);
  margin-bottom: 40px;
}

.hero-actions {
  display: flex;
  gap: 16px;
}

.btn-primary {
  padding: 12px 32px;
  font-size: 1.1rem;
  border-radius: 12px;
  background-color: var(--primary-color);
  border: none;
  box-shadow: 0 4px 14px 0 rgba(79, 70, 229, 0.39);
  transition: all 0.3s ease;
  color: white;
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(79, 70, 229, 0.23);
  background-color: var(--primary-color-hover);
  color: white;
}

.btn-secondary {
  padding: 12px 32px;
  font-size: 1.1rem;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background-color: white;
  color: var(--text-primary);
  transition: all 0.3s ease;
}

.btn-secondary:hover {
  background-color: var(--bg-hover);
  border-color: var(--text-secondary);
}

.hero-ai-widget {
  flex: 1;
  max-width: 500px;
  height: 600px;
  display: flex;
  justify-content: center;
  align-items: center;
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
  padding: 80px 0;
}

.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 32px;
}

.feature-card {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--card-shadow);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--hover-shadow);
}

.card-image-placeholder {
  height: 160px;
  background: linear-gradient(135deg, #f3f4f6 0%, #e5e7eb 100%);
  position: relative;
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
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
}

.tag-blue { background: #E0E7FF; color: #4F46E5; }
.tag-purple { background: #F3E8FF; color: #9333EA; }

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
  padding: 80px 0 120px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 40px;
  margin-top: 60px;
}

.feature-item {
  text-align: center;
  padding: 32px;
  background: white;
  border-radius: 20px;
  box-shadow: var(--card-shadow);
  border: 1px solid rgba(0,0,0,0.03);
  transition: transform 0.3s ease;
}

.feature-item:hover {
  transform: translateY(-5px);
}

.feature-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  margin: 0 auto 24px;
}

.icon-ai { background: #EEF2FF; color: #4F46E5; }
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
    flex-direction: column;
    text-align: center;
    gap: 40px;
  }
  .hero-actions {
    justify-content: center;
  }
  .features-grid {
    grid-template-columns: 1fr;
  }
  .hero-ai-widget {
    width: 100%;
    height: 500px;
  }
}
</style>