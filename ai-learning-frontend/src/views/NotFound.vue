<template>
  <main class="not-found-page">
    <section class="not-found-card" aria-labelledby="not-found-title">
      <BrandMark class="not-found-brand" />
      <p class="error-code" aria-hidden="true">404</p>
      <h1 id="not-found-title">页面走丢了</h1>
      <p class="description">你访问的页面不存在、已被移动，或网址输入有误。</p>
      <el-button type="primary" size="large" @click="goHome">
        <el-icon aria-hidden="true"><HomeFilled /></el-icon>
        <span>返回首页</span>
      </el-button>
    </section>
  </main>
</template>

<script setup>
import { HomeFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import BrandMark from '@/components/ui/BrandMark.vue'

const router = useRouter()

const goHome = () => {
  router.push('/home')
}
</script>

<style scoped>
.not-found-page {
  display: grid;
  min-height: 100dvh;
  padding: var(--space-6);
  place-items: center;
  background: var(--surface-canvas);
}

.not-found-card {
  width: min(100%, 480px);
  padding: var(--space-10) var(--space-8);
  text-align: center;
  background: var(--surface-base);
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-2);
}

.error-code {
  margin: var(--space-6) 0 var(--space-2);
  color: var(--accent);
  font-size: clamp(3.5rem, 12vw, 5rem);
  font-weight: 700;
  line-height: 1;
}

h1 {
  margin: 0;
  font-size: var(--text-2xl);
}

.description {
  margin: var(--space-3) 0 var(--space-6);
  color: var(--text-subtle);
  line-height: 1.75;
}
</style>
