<template>
  <main class="login-landing">
    <section class="brand-half" aria-label="智教云环 EduLoop">
      <div class="brand-lockup">
        <div class="brand-symbol" aria-hidden="true">E</div>
        <h1><span>智教云环</span> EduLoop</h1>
      </div>
    </section>

    <section class="action-half" aria-label="账号入口">
      <div class="entry-actions">
        <el-button type="primary" class="entry-button" @click="router.push('/login')">
          登录
        </el-button>
        <el-button class="entry-button register-button" @click="router.push('/register')">
          注册
        </el-button>
      </div>
    </section>
  </main>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.login-landing {
  width: 100%;
  height: 100vh;
  display: grid;
  grid-template-rows: 1fr 1fr;
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(239, 246, 255, 0.82), rgba(240, 253, 250, 0.58)),
    #f8fafc;
  position: relative;
  isolation: isolate;
}

.login-landing::before {
  content: "";
  position: absolute;
  inset: 10% 8%;
  z-index: -1;
  border: 1px solid rgba(255, 255, 255, 0.72);
  background: rgba(255, 255, 255, 0.28);
  backdrop-filter: blur(22px);
}

.brand-half,
.action-half {
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px;
}

.brand-lockup {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 28px;
  max-width: 100%;
}

.brand-symbol {
  width: clamp(96px, 11vw, 156px);
  aspect-ratio: 1;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: var(--radius-lg);
  color: white;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  box-shadow: 0 24px 56px rgba(37, 99, 235, 0.22);
  font-size: clamp(3.8rem, 7vw, 6.2rem);
  font-weight: 800;
}

.brand-lockup h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: clamp(3.2rem, 7vw, 6.4rem);
  line-height: 1.05;
  letter-spacing: 0;
  white-space: nowrap;
}

.brand-lockup h1 span {
  color: var(--primary-color);
}

.action-half {
  align-items: flex-start;
  padding-top: clamp(54px, 8vh, 96px);
}

.entry-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  width: 100%;
}

.entry-button {
  width: min(190px, 38vw);
  height: 54px;
  padding: 0 24px;
  border-radius: var(--radius-md);
  font-size: 1.2rem;
  font-weight: 700;
  box-shadow: 0 12px 26px rgba(37, 99, 235, 0.16);
}

.register-button {
  margin-left: 0;
  border-color: rgba(37, 99, 235, 0.28);
  color: var(--primary-color);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
}

@media (max-width: 720px) {
  .brand-lockup {
    flex-direction: column;
    gap: 18px;
  }

  .brand-lockup h1 {
    font-size: clamp(2.3rem, 10vw, 4rem);
    white-space: normal;
    text-align: center;
  }

  .entry-actions {
    flex-direction: column;
    gap: 14px;
  }

  .entry-button {
    width: min(260px, 76vw);
    height: 48px;
  }
}
</style>
