<template>
  <div class="auth-shell">
    <aside class="auth-story" aria-hidden="true">
      <BrandMark />
      <div class="story-copy">
        <p class="story-title">学、练、评、教，<br />在一个环里持续改进。</p>
        <ol class="story-steps">
          <li v-for="step in steps" :key="step.name">
            <strong>{{ step.name }}</strong>
            <span>{{ step.detail }}</span>
          </li>
        </ol>
      </div>
    </aside>

    <main class="auth-main">
      <header class="auth-topline">
        <router-link to="/" class="auth-home-link" aria-label="返回 EduLoop 入口">
          <BrandMark class="auth-brand-inline" />
        </router-link>
        <ThemeToggle />
      </header>

      <section class="auth-panel" :aria-labelledby="headingId">
        <h1 :id="headingId">{{ title }}</h1>
        <p v-if="description" class="auth-description">{{ description }}</p>
        <slot />
      </section>
    </main>
  </div>
</template>

<script setup>
import BrandMark from '@/components/ui/BrandMark.vue'
import ThemeToggle from '@/components/ui/ThemeToggle.vue'

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  headingId: { type: String, default: 'auth-heading' },
})

const steps = [
  { name: '学', detail: '课程、笔记与资料问答集中在一处' },
  { name: '练', detail: '作业、考试与互动实验各自独立' },
  { name: '评', detail: '批改和成绩分析回到每一位学生' },
  { name: '教', detail: '知识库与 AI 备课，来源可追溯' },
]
</script>

<style scoped>
.auth-shell {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  min-height: 100dvh;
  background: var(--surface-canvas);
}

.auth-story {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: var(--space-10);
  padding: var(--space-10);
  background: var(--surface-base);
  border-right: 1px solid var(--line-subtle);
}

.story-title {
  margin: 0 0 var(--space-8);
  color: var(--text-strong);
  font-size: var(--text-3xl);
  font-weight: 650;
  line-height: 1.35;
}

.story-steps {
  display: grid;
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.story-steps li {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: center;
  gap: var(--space-3);
  color: var(--text-subtle);
}

.story-steps strong {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  color: var(--accent);
  background: var(--surface-accent);
  border-radius: 50%;
  font-weight: 650;
}

.auth-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: var(--space-6) var(--space-8);
}

.auth-topline {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 40px;
}

.auth-home-link {
  display: none;
  text-decoration: none;
  border-radius: var(--radius-md);
}

.auth-panel {
  width: min(100%, 420px);
  margin: auto;
  padding: var(--space-8) 0;
}

h1 {
  margin: 0;
  font-size: var(--text-3xl);
}

.auth-description {
  margin: var(--space-2) 0 var(--space-6);
  color: var(--text-subtle);
}

@media (max-width: 900px) {
  .auth-shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .auth-story {
    display: none;
  }

  .auth-topline {
    justify-content: space-between;
  }

  .auth-home-link {
    display: inline-flex;
  }

  .auth-main {
    padding: var(--space-4) var(--space-5);
  }
}
</style>
