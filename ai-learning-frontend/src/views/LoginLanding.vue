<template>
  <div class="landing">
    <header class="landing-bar">
      <BrandMark />
      <nav class="landing-links" aria-label="公开入口">
        <router-link to="/home" class="landing-link">浏览首页</router-link>
        <ThemeToggle />
      </nav>
    </header>

    <div class="landing-main">
      <section class="landing-copy" aria-labelledby="landing-title">
        <p class="landing-eyebrow">智教云环 · AI 教学工作台</p>
        <h1 id="landing-title">让每一次教与学，<br />都回到下一次改进。</h1>
        <p class="landing-summary">
          课程学习、作业考试、知识库问答与智能备课在同一个工作台完成。AI 的回答附带资料出处，需要写入的操作先由你确认。
        </p>
        <div class="landing-actions">
          <template v-if="userStore.isLoggedIn">
            <el-button type="primary" size="large" class="entry-button" @click="router.push('/console')">进入工作台</el-button>
          </template>
          <template v-else>
            <el-button type="primary" size="large" class="entry-button" @click="router.push('/login')">登录</el-button>
            <el-button size="large" class="entry-button" @click="router.push('/register')">注册</el-button>
          </template>
        </div>
      </section>

      <section class="loop-panel" aria-label="EduLoop 的教学闭环">
        <svg class="loop-ring" viewBox="0 0 320 320" aria-hidden="true" focusable="false">
          <circle class="ring-track" cx="160" cy="160" r="118" />
          <path class="ring-arc" d="M160 42a118 118 0 1 1-118 118" />
          <circle class="ring-node" cx="42" cy="160" r="7" />
        </svg>
        <ol class="loop-steps">
          <li v-for="step in loopSteps" :key="step.name" :class="`step-${step.position}`">
            <strong>{{ step.name }}</strong>
            <span>{{ step.detail }}</span>
          </li>
        </ol>
      </section>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import BrandMark from '@/components/ui/BrandMark.vue'
import ThemeToggle from '@/components/ui/ThemeToggle.vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const loopSteps = [
  { name: '学', detail: '课本、笔记、资料问答', position: 'top' },
  { name: '练', detail: '作业与实验', position: 'right' },
  { name: '评', detail: '批改与成绩分析', position: 'bottom' },
  { name: '教', detail: '知识库与智能备课', position: 'left' },
]
</script>

<style scoped>
.landing {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  background: var(--surface-canvas);
}

.landing-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) clamp(var(--space-5), 5vw, 72px);
}

.landing-links {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.landing-link {
  padding: 6px 10px;
  color: var(--text-body);
  border-radius: var(--radius-md);
  font-weight: 600;
  text-decoration: none;
}

.landing-link:hover {
  color: var(--accent);
  background: var(--surface-hover);
}

.landing-main {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 5fr);
  align-items: center;
  gap: clamp(var(--space-8), 5vw, 72px);
  width: min(100%, 1240px);
  margin: 0 auto;
  padding: var(--space-8) clamp(var(--space-5), 5vw, 72px) var(--space-12);
}

.landing-eyebrow {
  margin: 0 0 var(--space-4);
  color: var(--accent);
  font-size: var(--text-md);
  font-weight: 650;
}

h1 {
  margin: 0;
  font-size: var(--text-display);
  line-height: 1.22;
  letter-spacing: -.01em;
}

.landing-summary {
  max-width: 54ch;
  margin: var(--space-5) 0 var(--space-8);
  color: var(--text-subtle);
  font-size: var(--text-lg);
  line-height: 1.8;
}

.landing-actions {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.landing-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.entry-button {
  min-width: 128px;
}

.loop-panel {
  position: relative;
  width: min(100%, 400px);
  aspect-ratio: 1;
  justify-self: center;
}

.loop-ring {
  position: absolute;
  inset: 12%;
  width: 76%;
  height: 76%;
}

.ring-track {
  fill: none;
  stroke: var(--line-default);
  stroke-width: 2;
}

.ring-arc {
  fill: none;
  stroke: var(--accent);
  stroke-width: 3;
  stroke-linecap: round;
}

.ring-node {
  fill: var(--accent);
}

.loop-steps {
  position: absolute;
  inset: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.loop-steps li {
  position: absolute;
  display: grid;
  justify-items: center;
  gap: 2px;
  width: 136px;
  padding: var(--space-2) var(--space-3);
  text-align: center;
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-2);
}

.loop-steps strong {
  color: var(--accent);
  font-size: var(--text-xl);
}

.loop-steps span {
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

.step-top { top: 0; left: 50%; transform: translateX(-50%); }
.step-right { top: 50%; right: 0; transform: translateY(-50%); }
.step-bottom { bottom: 0; left: 50%; transform: translateX(-50%); }
.step-left { top: 50%; left: 0; transform: translateY(-50%); }

@media (max-width: 900px) {
  .landing-main {
    grid-template-columns: minmax(0, 1fr);
    align-items: start;
    padding-top: var(--space-6);
  }

  .loop-panel {
    width: 100%;
    aspect-ratio: auto;
    max-width: 560px;
    justify-self: start;
  }

  .loop-ring {
    display: none;
  }

  .loop-steps {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-3);
  }

  .loop-steps li {
    position: static;
    width: auto;
    transform: none;
    box-shadow: none;
  }
}

@media (max-width: 480px) {
  .entry-button {
    flex: 1 1 120px;
  }
}
</style>
