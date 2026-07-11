<template>
  <Teleport to="body">
    <transition name="agent-panel">
      <section
        v-if="userStore.isLoggedIn && panelOpen"
        ref="panelRef"
        class="agent-floating-panel"
        :class="{ 'is-dragging': dragState.kind === 'panel' }"
        :style="panelStyle"
        aria-label="Agent 侧边助手"
      >
        <header class="floating-panel-header" title="拖动窗口" @pointerdown="startPanelDrag">
          <div class="floating-panel-title">
            <span class="assistant-mark">
              <el-icon><Cpu /></el-icon>
            </span>
            <div>
              <strong>Agent 助手</strong>
              <span>{{ permissionLabel }}</span>
            </div>
          </div>
          <el-button
            text
            circle
            data-agent-no-drag
            aria-label="关闭 Agent 助手"
            @pointerdown.stop
            @click="closePanel"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </header>

        <div class="floating-panel-body">
          <AiChat
            embedded
            :selected-context="selectedContext"
            :draft-prompt="draftPrompt"
            :draft-version="draftVersion"
            @context-consumed="clearSelectedContext"
          />
        </div>
      </section>
    </transition>

    <el-tooltip
      v-if="userStore.isLoggedIn && !panelOpen && !hideLauncher"
      content="打开 Agent 助手"
      placement="left"
    >
      <button
        ref="launcherRef"
        class="agent-floating-launcher"
        :class="{ 'is-dragging': dragState.kind === 'launcher' }"
        :style="launcherStyle"
        type="button"
        aria-label="打开 Agent 助手，拖动可调整位置"
        @pointerdown="startLauncherDrag"
        @click="handleLauncherClick"
      >
        <el-icon><Cpu /></el-icon>
        <span>Agent</span>
        <i v-if="selectedContext" class="context-dot"></i>
      </button>
    </el-tooltip>

    <button
      v-if="selectionAction.visible && userStore.isLoggedIn"
      class="selection-agent-action"
      type="button"
      :style="selectionActionStyle"
      @click="openFromSelection"
    >
      <el-icon><ChatDotRound /></el-icon>
      <span>用 Agent 提问</span>
    </button>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Close, Cpu } from '@element-plus/icons-vue'
import AiChat from '@/views/AiChat.vue'
import { useUserStore } from '@/store/user'
import { AGENT_ASSISTANT_OPEN_EVENT } from '@/utils/agentAssistant'
import {
  AGENT_CONTEXT_MAX_SELECTION_LENGTH,
  normalizeAgentSelectionContext,
  normalizeAgentSelectionText,
} from '@/utils/agentContext'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const panelRef = ref(null)
const launcherRef = ref(null)
const panelOpen = ref(false)
const selectedContext = ref(null)
const draftPrompt = ref('')
const draftVersion = ref(0)
const selectionAction = reactive({ visible: false, left: 0, top: 0 })
const launcherPosition = reactive({ x: 0, y: 0, initialized: false })
const panelPosition = reactive({ x: 0, y: 0 })
const dragState = reactive({
  kind: '',
  pointerId: null,
  startX: 0,
  startY: 0,
  originX: 0,
  originY: 0,
  moved: false,
})
const suppressLauncherClick = ref(false)

const VIEWPORT_GAP = 12
const ANCHOR_GAP = 10
const DEFAULT_LAUNCHER_WIDTH = 104
const DEFAULT_LAUNCHER_HEIGHT = 48

const hideLauncher = computed(() => Boolean(route.meta?.hideChrome) || route.name === 'AgentChat')
const permissionLabel = computed(() => {
  if (userStore.isAdmin) return '管理员 · 全部权限'
  if (userStore.isTeacher) return '教师 · 教学协作权限'
  return '学生 · 仅对话权限'
})
const selectionActionStyle = computed(() => ({
  left: `${selectionAction.left}px`,
  top: `${selectionAction.top}px`,
}))
const panelStyle = computed(() => ({
  left: `${panelPosition.x}px`,
  top: `${panelPosition.y}px`,
}))
const launcherStyle = computed(() => ({
  left: `${launcherPosition.x}px`,
  top: `${launcherPosition.y}px`,
}))

async function openPanel(options = {}) {
  if (!userStore.isLoggedIn) return
  if (typeof options.prompt === 'string' && options.prompt.trim()) {
    draftPrompt.value = options.prompt.trim().slice(0, 4000)
    draftVersion.value += 1
  }
  if (options.context?.text) {
    selectedContext.value = normalizeContext(options.context)
  }
  const shouldAnchor = !panelOpen.value
  const anchor = shouldAnchor ? launcherAnchor() : null
  if (shouldAnchor) {
    positionPanelFromAnchor(anchor, estimatedPanelSize())
  }
  panelOpen.value = true
  selectionAction.visible = false
  await nextTick()
  if (shouldAnchor) {
    positionPanelFromAnchor(anchor)
  } else {
    constrainPanelToViewport()
  }
}

function closePanel() {
  panelOpen.value = false
}

function handleLauncherClick(event) {
  if (suppressLauncherClick.value) {
    event.preventDefault()
    return
  }
  openPanel()
}

function openFromSelection() {
  openPanel()
}

function handleOpenEvent(event) {
  openPanel(event?.detail || {})
}

function selectionRoot(selection) {
  const node = selection?.anchorNode
  const element = node?.nodeType === Node.ELEMENT_NODE ? node : node?.parentElement
  const root = element?.closest?.('[data-agent-selectable]')
  if (!root || !root.contains(selection.focusNode)) return null
  return root
}

function captureSelection(event) {
  const target = event?.target
  if (panelRef.value?.contains(target) || target?.closest?.('.selection-agent-action, .agent-floating-launcher')) return
  window.requestAnimationFrame(() => {
    const selection = window.getSelection?.()
    const root = selectionRoot(selection)
    const text = normalizeAgentSelectionText(selection?.toString())
    if (!root || !text || selection.rangeCount === 0) {
      selectionAction.visible = false
      return
    }

    const clipped = text.slice(0, AGENT_CONTEXT_MAX_SELECTION_LENGTH)
    const courseRoot = root.closest('[data-agent-course-title]')
    selectedContext.value = normalizeAgentSelectionContext({
      text: clipped,
      truncated: text.length > AGENT_CONTEXT_MAX_SELECTION_LENGTH,
      lessonTitle: root.dataset.agentLessonTitle || '',
      courseTitle: courseRoot?.dataset.agentCourseTitle || '',
    })

    const rect = selection.getRangeAt(0).getBoundingClientRect()
    if (!rect || (!rect.width && !rect.height)) {
      selectionAction.visible = false
      return
    }
    selectionAction.left = clamp(rect.left + rect.width / 2, 92, window.innerWidth - 92)
    selectionAction.top = Math.max(12, rect.top - 48)
    selectionAction.visible = true
  })
}

function normalizeContext(context) {
  return normalizeAgentSelectionContext(context)
}

function clearSelectedContext() {
  selectedContext.value = null
  selectionAction.visible = false
  window.getSelection?.()?.removeAllRanges?.()
}

function hideSelectionAction() {
  selectionAction.visible = false
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max)
}

function viewportSize() {
  return { width: window.innerWidth, height: window.innerHeight }
}

function launcherSize() {
  return {
    width: launcherRef.value?.offsetWidth || (window.innerWidth <= 900 ? 48 : DEFAULT_LAUNCHER_WIDTH),
    height: launcherRef.value?.offsetHeight || DEFAULT_LAUNCHER_HEIGHT,
  }
}

function estimatedPanelSize() {
  const viewport = viewportSize()
  return {
    width: Math.min(Math.max(viewport.width * 0.34, 400), 480, viewport.width - VIEWPORT_GAP * 2),
    height: Math.min(Math.max(viewport.height * 0.54, 420), 560, viewport.height - VIEWPORT_GAP * 2),
  }
}

function ensureLauncherPosition() {
  if (launcherPosition.initialized) return
  const viewport = viewportSize()
  const size = launcherSize()
  launcherPosition.x = Math.max(VIEWPORT_GAP, viewport.width - size.width - 24)
  launcherPosition.y = Math.max(VIEWPORT_GAP, viewport.height - size.height - 28)
  launcherPosition.initialized = true
}

function launcherAnchor() {
  ensureLauncherPosition()
  const size = launcherSize()
  return {
    left: launcherPosition.x,
    top: launcherPosition.y,
    width: size.width,
    height: size.height,
  }
}

function positionPanelFromAnchor(anchor, size) {
  if (!anchor) return
  const viewport = viewportSize()
  const panelSize = size || {
    width: panelRef.value?.offsetWidth || estimatedPanelSize().width,
    height: panelRef.value?.offsetHeight || estimatedPanelSize().height,
  }
  const anchorCenterX = anchor.left + anchor.width / 2
  const anchorCenterY = anchor.top + anchor.height / 2
  const desiredX = anchorCenterX <= viewport.width / 2
    ? anchor.left
    : anchor.left + anchor.width - panelSize.width
  const desiredY = anchorCenterY <= viewport.height / 2
    ? anchor.top + anchor.height + ANCHOR_GAP
    : anchor.top - panelSize.height - ANCHOR_GAP

  panelPosition.x = clamp(desiredX, VIEWPORT_GAP, Math.max(VIEWPORT_GAP, viewport.width - panelSize.width - VIEWPORT_GAP))
  panelPosition.y = clamp(desiredY, VIEWPORT_GAP, Math.max(VIEWPORT_GAP, viewport.height - panelSize.height - VIEWPORT_GAP))
}

function constrainPosition(position, size) {
  const viewport = viewportSize()
  position.x = clamp(position.x, VIEWPORT_GAP, Math.max(VIEWPORT_GAP, viewport.width - size.width - VIEWPORT_GAP))
  position.y = clamp(position.y, VIEWPORT_GAP, Math.max(VIEWPORT_GAP, viewport.height - size.height - VIEWPORT_GAP))
}

function constrainPanelToViewport() {
  if (!panelRef.value) return
  constrainPosition(panelPosition, {
    width: panelRef.value.offsetWidth,
    height: panelRef.value.offsetHeight,
  })
}

function constrainLauncherToViewport() {
  ensureLauncherPosition()
  constrainPosition(launcherPosition, launcherSize())
}

function startDrag(kind, event) {
  if (event.button !== 0) return
  if (kind === 'panel' && event.target?.closest?.('[data-agent-no-drag]')) return
  const position = kind === 'panel' ? panelPosition : launcherPosition
  dragState.kind = kind
  dragState.pointerId = event.pointerId
  dragState.startX = event.clientX
  dragState.startY = event.clientY
  dragState.originX = position.x
  dragState.originY = position.y
  dragState.moved = false
  document.body.classList.add('agent-assistant-dragging')
  event.preventDefault()
}

function startPanelDrag(event) {
  startDrag('panel', event)
}

function startLauncherDrag(event) {
  ensureLauncherPosition()
  startDrag('launcher', event)
}

function handlePointerMove(event) {
  if (!dragState.kind || event.pointerId !== dragState.pointerId) return
  const deltaX = event.clientX - dragState.startX
  const deltaY = event.clientY - dragState.startY
  if (Math.abs(deltaX) + Math.abs(deltaY) > 4) {
    dragState.moved = true
  }
  const position = dragState.kind === 'panel' ? panelPosition : launcherPosition
  const size = dragState.kind === 'panel'
    ? { width: panelRef.value?.offsetWidth || 0, height: panelRef.value?.offsetHeight || 0 }
    : launcherSize()
  position.x = dragState.originX + deltaX
  position.y = dragState.originY + deltaY
  constrainPosition(position, size)
}

function endPointerDrag(event) {
  if (!dragState.kind || event.pointerId !== dragState.pointerId) return
  const draggedLauncher = dragState.kind === 'launcher' && dragState.moved
  dragState.kind = ''
  dragState.pointerId = null
  document.body.classList.remove('agent-assistant-dragging')
  if (draggedLauncher) {
    suppressLauncherClick.value = true
    window.setTimeout(() => {
      suppressLauncherClick.value = false
    }, 0)
  }
}

function handleViewportResize() {
  constrainLauncherToViewport()
  if (panelOpen.value) {
    nextTick(constrainPanelToViewport)
  }
}

async function consumeRouteOpenRequest() {
  if (!userStore.isLoggedIn || String(route.query.openAgent || '') !== '1') return
  const promptValue = Array.isArray(route.query.prompt) ? route.query.prompt[0] : route.query.prompt
  const query = { ...route.query }
  const path = route.path
  const hash = route.hash
  delete query.openAgent
  delete query.prompt
  await router.replace({ path, query, hash })
  await nextTick()
  openPanel({ prompt: typeof promptValue === 'string' ? promptValue : '' })
}

watch(
  () => [route.fullPath, userStore.isLoggedIn],
  async ([fullPath, loggedIn], [previousFullPath, previousLoggedIn] = []) => {
    selectionAction.visible = false
    if (!loggedIn) {
      panelOpen.value = false
      selectedContext.value = null
      return
    }
    if (previousFullPath && fullPath !== previousFullPath) {
      selectedContext.value = null
    }
    if (loggedIn && !previousLoggedIn) {
      await consumeRouteOpenRequest()
      return
    }
    await consumeRouteOpenRequest()
  },
  { immediate: true }
)

onMounted(() => {
  ensureLauncherPosition()
  window.addEventListener(AGENT_ASSISTANT_OPEN_EVENT, handleOpenEvent)
  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', endPointerDrag)
  window.addEventListener('pointercancel', endPointerDrag)
  window.addEventListener('resize', handleViewportResize)
  document.addEventListener('pointerup', captureSelection)
  document.addEventListener('mouseup', captureSelection)
  document.addEventListener('keyup', captureSelection)
  document.addEventListener('scroll', hideSelectionAction, true)
})

onBeforeUnmount(() => {
  window.removeEventListener(AGENT_ASSISTANT_OPEN_EVENT, handleOpenEvent)
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', endPointerDrag)
  window.removeEventListener('pointercancel', endPointerDrag)
  window.removeEventListener('resize', handleViewportResize)
  document.removeEventListener('pointerup', captureSelection)
  document.removeEventListener('mouseup', captureSelection)
  document.removeEventListener('keyup', captureSelection)
  document.removeEventListener('scroll', hideSelectionAction, true)
  document.body.classList.remove('agent-assistant-dragging')
})
</script>

<style scoped>
.agent-floating-panel {
  position: fixed;
  z-index: 2100;
  width: clamp(400px, 34vw, 480px);
  height: clamp(420px, 54vh, 560px);
  max-width: calc(100vw - 24px);
  max-height: calc(100vh - 24px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, .32);
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 28px 80px rgba(15, 23, 42, .24), 0 8px 24px rgba(37, 99, 235, .12);
}

.floating-panel-header {
  min-height: 52px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #fff;
  background: linear-gradient(135deg, #312e81, #2563eb 58%, #0891b2);
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.agent-floating-panel.is-dragging .floating-panel-header {
  cursor: grabbing;
}

.floating-panel-header :deep(.el-button) {
  color: #fff;
}

.floating-panel-title,
.floating-panel-title > div {
  display: flex;
  align-items: center;
}

.floating-panel-title {
  gap: 10px;
}

.floating-panel-title > div {
  align-items: flex-start;
  flex-direction: column;
  gap: 2px;
}

.floating-panel-title strong {
  font-size: 15px;
}

.floating-panel-title span {
  color: rgba(255, 255, 255, .76);
  font-size: 11px;
}

.assistant-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, .28);
  border-radius: 11px;
  background: rgba(255, 255, 255, .14);
}

.assistant-mark :deep(.el-icon) {
  font-size: 20px;
}

.floating-panel-body {
  flex: 1;
  min-height: 0;
}

.agent-floating-launcher {
  position: fixed;
  z-index: 1900;
  min-width: 104px;
  height: 48px;
  padding: 0 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 0;
  border-radius: 24px;
  color: #fff;
  background: linear-gradient(135deg, #4338ca, #2563eb 62%, #0891b2);
  box-shadow: 0 14px 30px rgba(37, 99, 235, .32);
  font-weight: 700;
  cursor: pointer;
  touch-action: none;
  user-select: none;
  transition: transform .2s ease, box-shadow .2s ease;
}

.agent-floating-launcher:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 36px rgba(37, 99, 235, .38);
}

.agent-floating-launcher.is-dragging {
  cursor: grabbing;
  transform: scale(1.03);
  transition: none;
}

.context-dot {
  position: absolute;
  top: 4px;
  right: 8px;
  width: 9px;
  height: 9px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #f59e0b;
}

.selection-agent-action {
  position: fixed;
  z-index: 2200;
  transform: translateX(-50%);
  height: 36px;
  padding: 0 13px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #bfdbfe;
  border-radius: 18px;
  color: #1d4ed8;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, .2);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.agent-panel-enter-active,
.agent-panel-leave-active {
  transition: opacity .22s ease, transform .22s ease;
}

.agent-panel-enter-from,
.agent-panel-leave-to {
  opacity: 0;
  transform: translateX(28px) scale(.985);
}

@media (max-width: 900px) {
  .agent-floating-panel {
    width: min(400px, calc(100vw - 24px));
    height: min(520px, calc(100vh - 24px));
    border-radius: 16px;
  }

  .agent-floating-launcher {
    min-width: 48px;
    width: 48px;
    padding: 0;
  }

  .agent-floating-launcher span {
    display: none;
  }
}
</style>
