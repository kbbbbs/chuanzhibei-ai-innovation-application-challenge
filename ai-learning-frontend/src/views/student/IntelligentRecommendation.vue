<template>
  <div class="skeleton-page">
    <div class="page-header">
      <h2>个性化智能推荐</h2>
      <p class="subtitle">根据您的学习进度和薄弱环节，为您量身定制学习资源</p>
    </div>

    <div class="filter-section">
      <el-radio-group v-model="recommendType" class="custom-radio">
        <el-radio-button label="all">全部推荐</el-radio-button>
        <el-radio-button label="course">课程</el-radio-button>
        <el-radio-button label="exercise">习题与测验</el-radio-button>
        <el-radio-button label="material">拓展资料</el-radio-button>
      </el-radio-group>

      <el-button type="primary" :loading="isGenerating" @click="generateRecommendations" class="gen-btn">
        <el-icon class="el-icon--left"><Refresh /></el-icon>
        重新生成推荐
      </el-button>
    </div>

    <div class="recommendations-container" v-loading="isGenerating" element-loading-text="AI 正在分析您的学习画像...">
      <div class="rec-section">
        <h3 class="section-title">
          <span class="icon-wrapper"><el-icon><Reading /></el-icon></span>
          核心薄弱点突破
        </h3>
        <div class="rec-grid">
          <div class="rec-card" v-for="i in 3" :key="i">
            <div class="rec-cover course-cover"></div>
            <div class="rec-content">
              <span class="rec-tag tag-red">易错知识点</span>
              <h4>数据结构 - 树与二叉树的遍历</h4>
              <p class="rec-desc">基于您在近期测试中错题率较高（45%）的知识点推荐。</p>
              <div class="rec-actions">
                <el-button type="primary" size="small">立即复习</el-button>
                <el-button size="small">加入计划</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="rec-section">
        <h3 class="section-title">
          <span class="icon-wrapper"><el-icon><Edit /></el-icon></span>
          巩固练习推荐
        </h3>
        <div class="rec-grid">
          <div class="rec-card" v-for="i in 2" :key="i">
            <div class="rec-cover exercise-cover"></div>
            <div class="rec-content">
              <span class="rec-tag tag-blue">进阶测验</span>
              <h4>计算机网络综合应用题突破</h4>
              <p class="rec-desc">匹配您当前的技能水平，预计需要 30 分钟完成。</p>
              <div class="rec-actions">
                <el-button type="primary" size="small">开始挑战</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Refresh, Reading, Edit } from '@element-plus/icons-vue'

const recommendType = ref('all')
const isGenerating = ref(false)

const generateRecommendations = () => {
  isGenerating.value = true
  setTimeout(() => {
    isGenerating.value = false
  }, 1500)
}
</script>

<style scoped>
.skeleton-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 32px;
}

.page-header h2 {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.subtitle {
  color: var(--text-secondary);
  font-size: 1rem;
}

.filter-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  background: white;
  padding: 16px 24px;
  border-radius: 16px;
  box-shadow: var(--card-shadow);
}

.gen-btn {
  border-radius: 8px;
  font-weight: 600;
}

.rec-section {
  margin-bottom: 48px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: #EEF2FF;
  color: var(--primary-color);
  border-radius: 8px;
}

.rec-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 24px;
}

.rec-card {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--card-shadow);
  border: 1px solid rgba(0,0,0,0.03);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.rec-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--hover-shadow);
}

.rec-cover {
  height: 140px;
  background-size: cover;
  background-position: center;
}

.course-cover {
  background: linear-gradient(135deg, #E0E7FF 0%, #C7D2FE 100%);
}

.exercise-cover {
  background: linear-gradient(135deg, #ECFEFF 0%, #A5F3FC 100%);
}

.rec-content {
  padding: 20px;
}

.rec-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 600;
  margin-bottom: 12px;
}

.tag-red { background: #FEE2E2; color: #EF4444; }
.tag-blue { background: #E0F2FE; color: #0EA5E9; }

.rec-content h4 {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
  line-height: 1.4;
}

.rec-desc {
  font-size: 0.9rem;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 20px;
  height: 40px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.rec-actions {
  display: flex;
  gap: 12px;
}
</style>