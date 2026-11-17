<template>
  <div class="workspace-nav" :class="{ 'is-rail': rail }">
    <div class="nav-brand">
      <router-link to="/console" class="brand-link" aria-label="EduLoop 智教云环，返回控制台" @click="$emit('navigate')">
        <BrandMark :compact="rail" />
      </router-link>
    </div>

    <div v-if="!rail" class="nav-search">
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        clearable
        placeholder="搜索页面"
        aria-label="搜索页面"
        @keydown.enter.prevent="openFirstMatch"
        @keydown.esc="keyword = ''"
      />
    </div>

    <nav class="nav-scroll" aria-label="页面导航">
      <p v-if="!visibleGroups.length" class="nav-empty" role="status">没有匹配的页面</p>
      <section v-for="group in visibleGroups" :key="group.id" class="nav-group">
        <button
          v-if="showGroupTitle(group)"
          type="button"
          class="nav-group-title"
          :aria-expanded="isOpen(group)"
          :aria-controls="`nav-group-${group.id}`"
          @click="toggleGroup(group)"
        >
          <span>{{ group.title }}</span>
          <el-icon :class="{ 'is-open': isOpen(group) }" aria-hidden="true"><ArrowRight /></el-icon>
        </button>
        <ul v-show="rail || isOpen(group)" :id="`nav-group-${group.id}`">
          <li v-for="item in group.items" :key="item.path">
            <router-link
              :to="item.path"
              class="nav-item"
              :class="{ 'is-active': item.path === activePath }"
              :aria-current="item.path === activePath ? 'page' : undefined"
              :title="rail ? item.title : undefined"
              @click="$emit('navigate')"
            >
              <el-icon :size="18" aria-hidden="true"><component :is="item.icon" /></el-icon>
              <span :class="rail ? 'visually-hidden' : 'nav-label'">{{ item.title }}</span>
            </router-link>
          </li>
        </ul>
      </section>
    </nav>

    <div v-if="collapsible" class="nav-footer">
      <button
        type="button"
        class="nav-collapse"
        :aria-label="rail ? '展开导航' : '收起导航'"
        :title="rail ? '展开导航' : '收起导航'"
        @click="$emit('toggle-rail')"
      >
        <el-icon :size="18" aria-hidden="true"><component :is="rail ? Expand : Fold" /></el-icon>
        <span v-if="!rail">收起导航</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Expand, Fold, Search } from '@element-plus/icons-vue'
import BrandMark from '@/components/ui/BrandMark.vue'
import { filterNavigation } from '@/layout/navigation'

const props = defineProps({
  groups: { type: Array, required: true },
  activePath: { type: String, required: true },
  rail: { type: Boolean, default: false },
  collapsible: { type: Boolean, default: false },
})

const emit = defineEmits(['navigate', 'toggle-rail'])

const router = useRouter()
const keyword = ref('')
const openOverrides = reactive({})

const visibleGroups = computed(() => filterNavigation(props.groups, keyword.value))
const searching = computed(() => Boolean(keyword.value.trim()))

function hasActiveItem(group) {
  return group.items.some(item => item.path === props.activePath)
}

function showGroupTitle(group) {
  return !props.rail && group.id !== 'overview'
}

function isOpen(group) {
  if (searching.value || group.id === 'overview') return true
  if (openOverrides[group.id] !== undefined) return openOverrides[group.id]
  return hasActiveItem(group) || !group.collapsed
}

function toggleGroup(group) {
  openOverrides[group.id] = !isOpen(group)
}

function openFirstMatch() {
  const first = visibleGroups.value[0]?.items[0]
  if (!searching.value || !first) return
  keyword.value = ''
  router.push(first.path)
  emit('navigate')
}
</script>

<style scoped>
.workspace-nav {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--surface-base);
}

.nav-brand {
  display: flex;
  align-items: center;
  height: var(--topbar-height);
  padding: 0 var(--space-4);
  flex: 0 0 auto;
}

.is-rail .nav-brand {
  justify-content: center;
  padding: 0;
}

.brand-link {
  display: inline-flex;
  min-width: 0;
  text-decoration: none;
  border-radius: var(--radius-md);
}

.nav-search {
  padding: 0 var(--space-3) var(--space-3);
  flex: 0 0 auto;
}

.nav-scroll {
  flex: 1 1 auto;
  min-height: 0;
  padding: 0 var(--space-3) var(--space-3);
  overflow-y: auto;
  scrollbar-width: thin;
}

.is-rail .nav-scroll {
  padding-inline: var(--space-2);
}

.nav-empty {
  margin: var(--space-4) var(--space-2);
  color: var(--text-subtle);
  font-size: var(--text-sm);
}

.nav-group + .nav-group {
  margin-top: var(--space-3);
}

.is-rail .nav-group + .nav-group {
  margin-top: var(--space-2);
  padding-top: var(--space-2);
  border-top: 1px solid var(--line-subtle);
}

.nav-group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 6px var(--space-2);
  color: var(--text-subtle);
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  font-weight: 600;
  cursor: pointer;
}

.nav-group-title:hover {
  color: var(--text-strong);
}

.nav-group-title .el-icon {
  transition: transform .15s var(--ease-standard);
}

.nav-group-title .el-icon.is-open {
  transform: rotate(90deg);
}

ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  margin-top: 2px;
  padding: 6px var(--space-2);
  color: var(--text-body);
  border-radius: var(--radius-md);
  font-size: var(--text-md);
  text-decoration: none;
  transition: background-color .12s var(--ease-standard), color .12s var(--ease-standard);
}

.nav-item:hover {
  color: var(--text-strong);
  background: var(--surface-hover);
}

.nav-item.is-active {
  color: var(--accent);
  background: var(--surface-accent);
  font-weight: 600;
}

.is-rail .nav-item {
  justify-content: center;
  padding-inline: 0;
}

.nav-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-footer {
  flex: 0 0 auto;
  padding: var(--space-2) var(--space-3);
  border-top: 1px solid var(--line-subtle);
}

.is-rail .nav-footer {
  padding-inline: var(--space-2);
}

.nav-collapse {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 36px;
  padding: 6px var(--space-2);
  color: var(--text-subtle);
  background: transparent;
  border: 0;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.is-rail .nav-collapse {
  justify-content: center;
  padding-inline: 0;
}

.nav-collapse:hover {
  color: var(--text-strong);
  background: var(--surface-hover);
}
</style>
