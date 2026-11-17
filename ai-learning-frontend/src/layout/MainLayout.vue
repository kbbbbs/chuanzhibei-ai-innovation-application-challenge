<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content" @click.prevent="focusMain">跳到主要内容</a>

    <header v-if="isPublicHome" class="public-header">
      <nav ref="homeNavRef" class="home-nav" aria-label="首页模块导航">
        <span class="home-nav-indicator" :style="homeNavIndicatorStyle" aria-hidden="true" />
        <button
          :ref="element => setHomeNavButtonRef(element, 'home-top')"
          class="home-nav-item home-entry"
          :class="{ active: activeHomeSection === 'home-top' }"
          type="button"
          aria-label="EduLoop 智教云环首页"
          :aria-current="activeHomeSection === 'home-top' ? 'page' : undefined"
          @click="scrollToHomeSection('home-top')"
        >
          <BrandMark class="home-brand" />
        </button>
        <button
          v-for="(item, index) in homeNavItems"
          :key="item.id"
          :ref="element => setHomeNavButtonRef(element, item.id)"
          type="button"
          class="home-nav-item"
          :class="{ active: activeHomeSection === item.id }"
          :style="{ '--nav-delay': `${index * 0.07}s` }"
          :aria-current="activeHomeSection === item.id ? 'page' : undefined"
          @click="scrollToHomeSection(item.id)"
        >
          <span>{{ item.label }}</span>
        </button>
      </nav>
      <HeaderActions public-home />
    </header>

    <div class="app-body">
      <aside
        v-if="showSidebar"
        class="app-sidebar"
        :class="{ 'is-rail': sidebarCollapsed }"
        aria-label="工作台导航"
      >
        <WorkspaceNav
          :groups="navigationGroups"
          :active-path="activePath"
          :rail="sidebarCollapsed"
          collapsible
          @toggle-rail="toggleSidebar"
        />
      </aside>

      <div class="app-stage">
        <header v-if="showTopbar" class="app-topbar">
          <div class="topbar-start">
            <button
              v-if="hasNavigation && !showSidebar"
              type="button"
              class="menu-button"
              aria-label="打开导航菜单"
              @click="navigationDrawerOpen = true"
            >
              <el-icon :size="20" aria-hidden="true"><MenuIcon /></el-icon>
            </button>
            <button
              v-if="!showSidebar"
              type="button"
              class="topbar-brand"
              aria-label="EduLoop 智教云环"
              @click="handleBrandEntry"
            >
              <BrandMark :compact="isCompact" :size="30" />
            </button>
            <nav v-if="breadcrumbs.length" class="breadcrumbs" aria-label="当前位置">
              <ol>
                <li v-for="(crumb, index) in breadcrumbs" :key="crumb.title">
                  <router-link v-if="crumb.path && index < breadcrumbs.length - 1" :to="crumb.path">{{ crumb.title }}</router-link>
                  <span v-else :aria-current="index === breadcrumbs.length - 1 ? 'page' : undefined">{{ crumb.title }}</span>
                </li>
              </ol>
            </nav>
          </div>
          <HeaderActions />
        </header>

        <main
          id="main-content"
          ref="layoutMainRef"
          class="layout-main"
          :class="{ 'public-main': hideChrome || isPublicHome, 'full-width-main': route.meta?.fullWidth }"
          tabindex="-1"
          @scroll.passive="handleMainScroll"
          @wheel.passive="cancelHomeScroll"
          @touchstart.passive="cancelHomeScroll"
        >
          <router-view />
        </main>
      </div>
    </div>

    <el-drawer
      v-model="navigationDrawerOpen"
      direction="ltr"
      title="导航菜单"
      size="min(296px, 88vw)"
      :with-header="false"
      class="navigation-drawer"
    >
      <WorkspaceNav
        :groups="navigationGroups"
        :active-path="activePath"
        @navigate="navigationDrawerOpen = false"
      />
    </el-drawer>

    <AgentFloatingAssistant />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { layoutChildren } from '@/router'
import { buildNavigation, findNavigationEntry, resolveActivePath } from '@/layout/navigation'
import AgentFloatingAssistant from '@/components/agent/AgentFloatingAssistant.vue'
import BrandMark from '@/components/ui/BrandMark.vue'
import HeaderActions from '@/layout/HeaderActions.vue'
import WorkspaceNav from '@/layout/WorkspaceNav.vue'
import { Menu as MenuIcon } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const COMPACT_QUERY = '(max-width: 1023px)'
const compactMedia = window.matchMedia(COMPACT_QUERY)
const isCompact = ref(compactMedia.matches)
const sidebarCollapsed = ref(localStorage.getItem('layout-sidebar-collapsed') === 'true')
const navigationDrawerOpen = ref(false)

const hideChrome = computed(() => Boolean(route.meta?.hideChrome))
const isPublicHome = computed(() => Boolean(route.meta?.publicHome))
const isFocusedPage = computed(() => ['CourseLearn', 'ExamTaking'].includes(route.name) || Boolean(route.meta?.fullWidth))

const navigationGroups = computed(() => (userStore.isLoggedIn ? buildNavigation(userStore.roleCode, layoutChildren) : []))
const hasNavigation = computed(() => navigationGroups.value.length > 0)
const activePath = computed(() => resolveActivePath(route))

const showSidebar = computed(() => hasNavigation.value
  && !hideChrome.value
  && !isPublicHome.value
  && !isFocusedPage.value
  && !isCompact.value)
const showTopbar = computed(() => !hideChrome.value && !isPublicHome.value)

const breadcrumbs = computed(() => {
  const entry = findNavigationEntry(navigationGroups.value, activePath.value)
  const crumbs = []
  if (entry && entry.group.id !== 'overview') crumbs.push({ title: entry.group.title })
  if (entry) crumbs.push({ title: entry.item.title, path: entry.item.path })
  const pageTitle = route.name === 'KnowledgeBase' && !userStore.isStudent ? '智能助手' : route.meta?.title
  if (pageTitle && pageTitle !== entry?.item.title) crumbs.push({ title: pageTitle })
  return crumbs
})

const activeHomeSection = ref('home-top')
const homeNavItems = [
  { id: 'home-learning-recommendation', label: '学习推荐' },
  { id: 'home-research-discovery', label: '科研发现' },
  { id: 'home-interactive-lab', label: '互动实验' },
  { id: 'home-smart-prep', label: '智能备课' },
]
const homeNavRef = ref(null)
const layoutMainRef = ref(null)
const homeNavIndicatorStyle = ref({ width: '0px', transform: 'translateX(0)', opacity: 0 })
const homeNavButtonRefs = new Map()
let scrollFrame = null
let homeScrollFrame = null
let programmaticHomeTarget = ''

function focusMain() {
  layoutMainRef.value?.focus()
}

function handleCompactChange(event) {
  isCompact.value = event.matches
}

function setHomeNavButtonRef(element, id) {
  if (element) homeNavButtonRefs.set(id, element)
  else homeNavButtonRefs.delete(id)
}

function updateHomeNavIndicator() {
  const nav = homeNavRef.value
  const button = homeNavButtonRefs.get(activeHomeSection.value)
  if (!nav || !button) {
    homeNavIndicatorStyle.value = { ...homeNavIndicatorStyle.value, opacity: 0 }
    return
  }
  homeNavIndicatorStyle.value = {
    width: `${button.offsetWidth}px`,
    transform: `translateX(${button.offsetLeft}px)`,
    opacity: 1,
  }
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem('layout-sidebar-collapsed', String(sidebarCollapsed.value))
}

function handleBrandEntry() {
  router.push(userStore.isLoggedIn ? '/console' : '/home')
}

async function scrollToHomeSection(id) {
  activeHomeSection.value = id
  const hash = `#${id}`
  if (!isPublicHome.value) {
    await router.push({ path: '/home', hash })
    return
  }
  if (route.hash !== hash) {
    void router.replace({ path: '/home', hash })
  }
  await nextTick()
  animateHomeScroll(id)
}

function animateHomeScroll(id) {
  const scroller = layoutMainRef.value
  const target = document.getElementById(id)
  if (!scroller || !target) return

  if (homeScrollFrame !== null) window.cancelAnimationFrame(homeScrollFrame)
  const startTop = scroller.scrollTop
  const scrollerTop = scroller.getBoundingClientRect().top
  const destination = id === 'home-top'
    ? 0
    : Math.max(0, startTop + target.getBoundingClientRect().top - scrollerTop)
  const distance = destination - startTop
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const duration = reduceMotion ? 0 : Math.min(420, Math.max(240, Math.abs(distance) * .16))
  const startedAt = performance.now()
  programmaticHomeTarget = id

  const finish = () => {
    scroller.scrollTop = destination
    programmaticHomeTarget = ''
    homeScrollFrame = null
    activeHomeSection.value = id
  }

  if (!duration || Math.abs(distance) < 2) {
    finish()
    return
  }

  const tick = now => {
    const progress = Math.min(1, (now - startedAt) / duration)
    const eased = 1 - Math.pow(1 - progress, 3)
    scroller.scrollTop = startTop + distance * eased
    if (progress < 1) homeScrollFrame = window.requestAnimationFrame(tick)
    else finish()
  }
  homeScrollFrame = window.requestAnimationFrame(tick)
}

function cancelHomeScroll() {
  if (homeScrollFrame === null) return
  window.cancelAnimationFrame(homeScrollFrame)
  homeScrollFrame = null
  programmaticHomeTarget = ''
  updateActiveHomeSection()
}

function updateActiveHomeSection() {
  scrollFrame = null
  if (!isPublicHome.value) return
  if (programmaticHomeTarget) return

  const scrollerTop = layoutMainRef.value?.getBoundingClientRect().top || 64
  let current = 'home-top'
  for (const item of homeNavItems) {
    const section = document.getElementById(item.id)
    if (section && section.getBoundingClientRect().top <= scrollerTop + 120) {
      current = item.id
    }
  }
  activeHomeSection.value = current
}

function handleMainScroll() {
  if (!isPublicHome.value || scrollFrame !== null) return
  scrollFrame = window.requestAnimationFrame(updateActiveHomeSection)
}

watch(
  () => route.fullPath,
  async () => {
    navigationDrawerOpen.value = false
    if (!isPublicHome.value) {
      activeHomeSection.value = 'home-top'
      return
    }

    await nextTick()
    const targetId = route.hash.replace(/^#/, '')
    if (targetId) {
      activeHomeSection.value = targetId
      if (programmaticHomeTarget !== targetId) animateHomeScroll(targetId)
    } else {
      updateActiveHomeSection()
    }
  },
  { immediate: true }
)

watch(activeHomeSection, async () => {
  await nextTick()
  updateHomeNavIndicator()
})

onMounted(async () => {
  compactMedia.addEventListener('change', handleCompactChange)
  window.addEventListener('resize', updateHomeNavIndicator)
  await nextTick()
  updateHomeNavIndicator()
})

onBeforeUnmount(() => {
  if (scrollFrame !== null) window.cancelAnimationFrame(scrollFrame)
  if (homeScrollFrame !== null) window.cancelAnimationFrame(homeScrollFrame)
  compactMedia.removeEventListener('change', handleCompactChange)
  window.removeEventListener('resize', updateHomeNavIndicator)
})
</script>

<style scoped>
.app-shell {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100dvh;
  background: var(--surface-canvas);
}

.skip-link {
  position: absolute;
  top: var(--space-2);
  left: var(--space-2);
  z-index: 3000;
  padding: var(--space-2) var(--space-3);
  color: var(--accent-contrast);
  background: var(--accent);
  border-radius: var(--radius-md);
  font-weight: 600;
  transform: translateY(-200%);
}

.skip-link:focus {
  transform: translateY(0);
}

.app-body {
  flex: 1 1 auto;
  display: flex;
  min-height: 0;
}

.app-sidebar {
  flex: 0 0 var(--sidebar-width);
  width: var(--sidebar-width);
  min-height: 0;
  border-right: 1px solid var(--line-subtle);
  transition: width .18s var(--ease-standard), flex-basis .18s var(--ease-standard);
}

.app-sidebar.is-rail {
  flex-basis: var(--sidebar-rail-width);
  width: var(--sidebar-rail-width);
}

.app-stage {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.app-topbar {
  flex: 0 0 var(--topbar-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  min-width: 0;
  padding: 0 var(--space-6);
  background: var(--surface-base);
  border-bottom: 1px solid var(--line-subtle);
}

.topbar-start {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex: 1 1 auto;
  min-width: 0;
}

.menu-button,
.topbar-brand {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.menu-button {
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  color: var(--text-strong);
}

.menu-button:hover {
  background: var(--surface-hover);
}

.topbar-brand {
  padding: 2px;
  margin-right: var(--space-2);
}

.breadcrumbs {
  min-width: 0;
  overflow: hidden;
}

.breadcrumbs ol {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-sm);
  white-space: nowrap;
}

.breadcrumbs li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--text-subtle);
}

.breadcrumbs li + li::before {
  content: '/';
  color: var(--line-strong);
}

.breadcrumbs li:last-child {
  overflow: hidden;
}

.breadcrumbs li:last-child span {
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-strong);
  font-weight: 600;
}

.breadcrumbs a {
  color: inherit;
  text-decoration: none;
}

.breadcrumbs a:hover {
  color: var(--accent);
}

.layout-main {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  padding: var(--space-6) var(--space-8);
  overflow: hidden auto;
  background: var(--surface-canvas);
}

.layout-main:focus {
  outline: none;
}

.public-main {
  padding: 0;
  background: var(--surface-base);
}

.full-width-main {
  padding: var(--space-6);
}

/* Public home keeps its section tabs; only its treatment follows the shared tokens. */
.public-header {
  flex: 0 0 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  min-width: 0;
  padding: 0 var(--space-6);
  background: var(--surface-base);
  border-bottom: 1px solid var(--line-subtle);
}

.home-nav {
  position: relative;
  align-self: center;
  height: 52px;
  display: flex;
  align-items: stretch;
  flex: 1;
  min-width: 0;
  margin: 0 var(--space-4) 0 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.home-nav::-webkit-scrollbar {
  display: none;
}

.home-nav-indicator {
  position: absolute;
  z-index: 2;
  bottom: 0;
  left: 0;
  height: 2px;
  background: var(--accent);
  border-radius: 2px 2px 0 0;
  pointer-events: none;
  will-change: transform, width;
  transition: transform .2s var(--ease-standard), width .2s var(--ease-standard), opacity .12s ease;
}

.home-nav-item {
  position: relative;
  z-index: 1;
  flex: 1 1 0;
  min-width: 96px;
  padding: 0 var(--space-3);
  color: var(--text-subtle);
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  font-size: var(--text-md);
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: color .15s var(--ease-standard), background-color .15s var(--ease-standard);
  animation: header-nav-in .4s var(--ease-standard) both;
  animation-delay: calc(.06s + var(--nav-delay, 0s));
}

.home-nav-item:hover,
.home-nav-item.active {
  color: var(--accent);
}

.home-nav-item:hover {
  background: var(--surface-hover);
}

.home-nav-item.home-entry {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  min-width: 196px;
  padding-right: var(--space-5);
}

@keyframes header-nav-in {
  from { opacity: 0; transform: translateY(-6px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 1023px) {
  .app-topbar,
  .public-header {
    padding: 0 var(--space-4);
  }

  .layout-main {
    padding: var(--space-5) var(--space-5);
  }

  .home-nav-item {
    min-width: 76px;
    padding: 0 var(--space-2);
    font-size: var(--text-sm);
  }
}

@media (max-width: 760px) {
  .app-topbar,
  .public-header {
    padding: 0 var(--space-3);
  }

  .layout-main,
  .full-width-main {
    padding: var(--space-4);
  }

  .layout-main.public-main {
    padding: 0;
  }

  .breadcrumbs li:not(:last-child) {
    display: none;
  }

  .breadcrumbs li + li::before {
    content: none;
  }

  .home-nav {
    margin: 0;
    overflow: visible;
  }

  .home-nav .home-nav-item:not(.home-entry),
  .home-nav-indicator {
    display: none;
  }

  .home-nav-item.home-entry {
    min-width: 0;
    padding: 0;
  }
}

@media (max-width: 480px) {
  .home-brand :deep(.brand-words span) {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-nav-item {
    animation: none;
  }

  .home-nav-indicator,
  .app-sidebar {
    transition: none;
  }
}
</style>

<style>
.navigation-drawer .el-drawer__body {
  padding: 0;
}
</style>
