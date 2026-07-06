<template>
  <div class="skeleton-page">
    <div class="page-header">
      <h2>智能资源生成</h2>
      <p class="subtitle">赋能教师高效备课，一键生成高质量教学资料</p>
    </div>

    <div class="generator-layout">
      <!-- Input Panel -->
      <div class="config-panel">
        <el-card class="glass-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>生成配置</span>
            </div>
          </template>

          <el-form :model="config" label-position="top">
            <el-form-item label="资源类型">
              <el-select v-model="config.type" placeholder="请选择生成的资源类型">
                <el-option label="课程教学大纲" value="syllabus" />
                <el-option label="课堂讲义骨架" value="lecture" />
                <el-option label="课后练习题库" value="quiz" />
                <el-option label="项目实战案例" value="project" />
              </el-select>
            </el-form-item>

            <el-form-item label="核心知识点 / 主题">
              <el-input
                v-model="config.topic"
                placeholder="例如：Vue3 响应式原理与组合式API应用"
              />
            </el-form-item>

            <el-form-item label="目标人群维度">
              <el-radio-group v-model="config.level">
                <el-radio-button label="beginner">初学者</el-radio-button>
                <el-radio-button label="intermediate">进阶/本科生</el-radio-button>
                <el-radio-button label="advanced">高阶/研究生</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="生成要求补充 (可选)">
              <el-input
                v-model="config.extra"
                type="textarea"
                :rows="4"
                placeholder="请描述您的具体要求，例如：侧重实践应用，包含至少3个代码示例..."
              />
            </el-form-item>

            <el-button
              type="primary"
              class="generate-btn"
              size="large"
              :loading="isGenerating"
              @click="handleGenerate"
            >
              <el-icon class="el-icon--left"><Lightning /></el-icon>
              {{ isGenerating ? 'AI 深度生成中...' : '开始智能生成' }}
            </el-button>
          </el-form>
        </el-card>
      </div>

      <!-- Output Panel -->
      <div class="output-panel">
        <el-card class="glass-card result-card" shadow="never">
          <template #header>
            <div class="card-header result-header">
              <span>生成结果预览</span>
              <div class="result-actions" v-if="generatedContent">
                <el-button size="small"><el-icon><DocumentCopy /></el-icon> 复制内容</el-button>
                <el-button size="small" type="primary"><el-icon><Download /></el-icon> 导出文档</el-button>
              </div>
            </div>
          </template>

          <div class="empty-state" v-if="!isGenerating && !generatedContent">
            <el-icon class="empty-icon"><Document /></el-icon>
            <p>在左侧配置参数后，点击生成按钮获取内容</p>
          </div>

          <div class="generating-state" v-else-if="isGenerating">
            <div class="skeleton-item title-skeleton"></div>
            <div class="skeleton-item text-skeleton" v-for="i in 5" :key="i" :style="{ width: Math.random() * 40 + 60 + '%' }"></div>
            <div class="skeleton-item block-skeleton"></div>
            <div class="skeleton-item text-skeleton" v-for="i in 3" :key="'b'+i"></div>
          </div>

          <div class="content-display" v-else>
            <!-- Mock Markdown-like display -->
            <div class="mock-markdown">
              <h1>{{ config.topic || '生成的教学资源' }}</h1>
              <p><em>目标难度：{{ config.level }} | 资源类型：{{ config.type }}</em></p>

              <h2>1. 教学目标</h2>
              <ul>
                <li>掌握核心概念与基础理论框架</li>
                <li>能够独立完成相关技术栈的环境搭建与基础配置</li>
                <li>培养学生解决实际工程问题的能力</li>
              </ul>

              <h2>2. 课程内容大纲</h2>
              <h3>2.1 引入与概念剖析 (15分钟)</h3>
              <p>讲解该技术的历史背景、要解决的痛点及业界主流应用场景。通过生活中的类比（如：餐厅点餐模型）引入抽象概念。</p>

              <h3>2.2 核心机制深度解析 (30分钟)</h3>
              <p>详细拆解底层运行逻辑，配合图表展示数据流向。</p>
              <div class="code-block">
                // 示例代码占位
                function initCore() {
                  console.log("Core initialized");
                }
              </div>

              <h2>3. 课后练习建议</h2>
              <p>布置 2 道基础巩固题，1 道综合实战题...</p>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Lightning, Document, DocumentCopy, Download } from '@element-plus/icons-vue'

const config = ref({
  type: 'lecture',
  topic: '',
  level: 'intermediate',
  extra: ''
})

const isGenerating = ref(false)
const generatedContent = ref(false)

const handleGenerate = () => {
  if (!config.value.topic) {
    config.value.topic = 'Vue3 响应式原理'
  }
  isGenerating.value = true
  generatedContent.value = false

  // Simulate AI generation time
  setTimeout(() => {
    isGenerating.value = false
    generatedContent.value = true
  }, 2500)
}
</script>

<style scoped>
.skeleton-page {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
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

.generator-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.config-panel {
  width: 380px;
  flex-shrink: 0;
}

.output-panel {
  flex: 1;
}

.glass-card {
  border-radius: 16px;
  background: white;
  border: 1px solid rgba(0,0,0,0.05);
  box-shadow: var(--card-shadow);
}

.card-header {
  font-weight: 600;
  font-size: 1.1rem;
  color: var(--text-primary);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result-actions {
  display: flex;
  gap: 8px;
}

.generate-btn {
  width: 100%;
  margin-top: 16px;
  border-radius: 8px;
  font-weight: 600;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-color-hover));
  border: none;
}

.result-card {
  min-height: 600px;
  display: flex;
  flex-direction: column;
}

:deep(.result-card .el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.empty-icon {
  font-size: 48px;
  color: #E2E8F0;
  margin-bottom: 16px;
}

.generating-state {
  padding: 20px;
}

.skeleton-item {
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: loading 1.5s infinite;
  border-radius: 4px;
  margin-bottom: 16px;
}

.title-skeleton {
  height: 32px;
  width: 40%;
  margin-bottom: 24px;
}

.text-skeleton {
  height: 16px;
}

.block-skeleton {
  height: 120px;
  margin: 24px 0;
  border-radius: 8px;
}

@keyframes loading {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.mock-markdown {
  padding: 10px;
  color: var(--text-primary);
  line-height: 1.7;
}

.mock-markdown h1 {
  font-size: 1.8rem;
  margin-bottom: 8px;
}

.mock-markdown h2 {
  font-size: 1.4rem;
  margin-top: 32px;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color);
}

.mock-markdown h3 {
  font-size: 1.1rem;
  margin-top: 24px;
  margin-bottom: 12px;
}

.mock-markdown ul {
  padding-left: 20px;
  margin-bottom: 16px;
}

.mock-markdown p {
  margin-bottom: 16px;
}

.code-block {
  background: #1E293B;
  color: #E2E8F0;
  padding: 16px;
  border-radius: 8px;
  font-family: monospace;
  margin: 16px 0;
  white-space: pre;
}
</style>