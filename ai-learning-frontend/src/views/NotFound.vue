<template>
  <main class="not-found-page">
    <div class="background-orb orb-left" aria-hidden="true"></div>
    <div class="background-orb orb-right" aria-hidden="true"></div>

    <section class="not-found-card" aria-labelledby="not-found-title">
      <div class="error-visual" aria-hidden="true">
        <span class="error-code">404</span>
        <div class="orbit orbit-one"></div>
        <div class="orbit orbit-two"></div>
        <div class="floating-dot dot-one"></div>
        <div class="floating-dot dot-two"></div>
      </div>

      <p class="eyebrow">PAGE NOT FOUND</p>
      <h1 id="not-found-title">页面走丢了</h1>
      <p class="description">
        你访问的页面不存在、已被移动，或网址输入有误。
      </p>

      <el-button type="primary" size="large" class="home-button" @click="goHome">
        <el-icon><HomeFilled /></el-icon>
        返回首页
      </el-button>
    </section>
  </main>
</template>

<script setup>
import { HomeFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.push('/home')
}
</script>

<style scoped>
.not-found-page {
  position: relative;
  display: grid;
  min-height: 100vh;
  padding: 32px;
  place-items: center;
  overflow: hidden;
  background:
    radial-gradient(circle at 20% 20%, rgba(37, 99, 235, 0.12), transparent 32%),
    radial-gradient(circle at 82% 78%, rgba(15, 118, 110, 0.12), transparent 30%),
    linear-gradient(145deg, #f8fafc 0%, #eff6ff 52%, #f0fdfa 100%);
}

.background-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
  opacity: 0.6;
}

.orb-left {
  width: 220px;
  height: 220px;
  top: -80px;
  left: -70px;
  background: rgba(37, 99, 235, 0.14);
}

.orb-right {
  width: 280px;
  height: 280px;
  right: -100px;
  bottom: -120px;
  background: rgba(13, 148, 136, 0.14);
}

.not-found-card {
  position: relative;
  z-index: 1;
  width: min(100%, 620px);
  padding: 56px 48px;
  text-align: center;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 24px;
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(18px);
}

.error-visual {
  position: relative;
  display: grid;
  width: 280px;
  height: 170px;
  margin: 0 auto 12px;
  place-items: center;
}

.error-code {
  position: relative;
  z-index: 2;
  font-size: clamp(5.5rem, 18vw, 8.5rem);
  font-weight: 900;
  line-height: 1;
  letter-spacing: -0.08em;
  color: transparent;
  background: linear-gradient(120deg, #2563eb 15%, #0f766e 85%);
  background-clip: text;
  -webkit-background-clip: text;
  filter: drop-shadow(0 12px 22px rgba(37, 99, 235, 0.18));
}

.orbit {
  position: absolute;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 50%;
  transform: rotate(-12deg);
}

.orbit-one {
  width: 275px;
  height: 108px;
}

.orbit-two {
  width: 228px;
  height: 150px;
  border-color: rgba(15, 118, 110, 0.14);
  transform: rotate(16deg);
}

.floating-dot {
  position: absolute;
  z-index: 3;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  box-shadow: 0 5px 12px rgba(15, 23, 42, 0.16);
}

.dot-one {
  top: 30px;
  right: 24px;
  background: #2563eb;
}

.dot-two {
  bottom: 24px;
  left: 34px;
  background: #0d9488;
}

.eyebrow {
  margin: 0 0 10px;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  color: #2563eb;
}

h1 {
  margin: 0;
  font-size: clamp(2rem, 6vw, 2.75rem);
  line-height: 1.2;
  color: #0f172a;
}

.description {
  max-width: 430px;
  margin: 16px auto 30px;
  font-size: 1rem;
  line-height: 1.75;
  color: #64748b;
}

.home-button {
  min-width: 150px;
  height: 48px;
  padding-inline: 24px;
  border-radius: 12px;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.24);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.home-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 28px rgba(37, 99, 235, 0.28);
}

@media (max-width: 640px) {
  .not-found-page {
    padding: 20px;
  }

  .not-found-card {
    padding: 40px 24px;
    border-radius: 20px;
  }

  .error-visual {
    width: 230px;
    height: 145px;
  }

  .orbit-one {
    width: 230px;
    height: 90px;
  }

  .orbit-two {
    width: 192px;
    height: 126px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-button {
    transition: none;
  }
}
</style>
