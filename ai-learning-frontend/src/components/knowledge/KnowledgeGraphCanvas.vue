<template>
  <div
    ref="stage"
    class="network-wrap"
    :class="{ 'is-presenting': presenting, 'is-entering': entering }"
    data-testid="graph-stage"
    :data-motion-state="motionState"
    :data-layout-state="layoutState"
    :data-presentation="String(presenting)"
    :role="presenting ? 'dialog' : undefined"
    :aria-modal="presenting ? true : undefined"
    :aria-label="presenting ? title : undefined"
    @keydown="onStageKey"
  >
    <header v-if="presenting" class="presentation-heading">
      <div>
        <h2>{{ title }}</h2>
        <p>{{ nodes.length }} 个知识点 · {{ edges.length }} 条关系</p>
      </div>
      <label
        >定位知识点<select
          aria-label="展示模式定位知识点"
          @change="locateInPresentation($event.target.value)"
        >
          <option value="">请选择知识点</option>
          <option v-for="node in nodes" :key="node.id" :value="node.id">
            {{ node.name }}
          </option>
        </select></label
      >
    </header>
    <div
      ref="container"
      class="knowledge-network"
      data-testid="knowledge-network"
      role="img"
      aria-label="可拖动、缩放的知识关系网络，知识点和关系也可在右侧列表中选择"
    ></div>
    <canvas
      ref="effects"
      class="motion-layer"
      data-testid="graph-motion-layer"
      aria-hidden="true"
    ></canvas>
    <div
      v-if="hoverInfo"
      class="hover-card"
      :style="hoverCardStyle"
      aria-hidden="true"
    >
      <i :style="{ background: hoverInfo.colour }"></i
      ><span
        >{{ hoverInfo.name }}<small>{{ hoverInfo.type }}</small></span
      >
    </div>
    <div class="view-controls" aria-label="图谱视图控制">
      <button aria-label="放大图谱" @click="zoomBy(1.25)">＋</button>
      <button aria-label="缩小图谱" @click="zoomBy(0.8)">−</button>
      <span class="control-divider"></span>
      <button @click="fit">适应视图</button>
      <button :disabled="layoutState === 'settling'" @click="layout()">
        重新布局
      </button>
      <button v-if="selectedId || pathIds.length" @click="clearFocus">
        查看全部关联
      </button>
      <button :aria-pressed="animationWanted" @click="toggleMotion">
        <span class="motion-icon" aria-hidden="true">{{
          animationWanted ? 'Ⅱ' : '▷'
        }}</span>{{ animationWanted ? '暂停动态' : '播放动态' }}
      </button>
      <button
        ref="presentationButton"
        class="presentation-control"
        @click="togglePresentation"
      >
        {{ presenting ? '退出展示' : '全屏展示' }}
      </button>
    </div>
    <span class="canvas-instruction">拖动节点 · 滚轮缩放 · 拖动画布平移</span>
    <span v-if="motionState === 'reduced'" class="motion-preference"
      >已按系统偏好减少动态</span
    >
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import cytoscape from 'cytoscape'

const props = defineProps({
  title: { type: String, default: '知识图谱' },
  nodes: { type: Array, default: () => [] },
  edges: { type: Array, default: () => [] },
  selectedId: { type: String, default: '' },
  relationType: { type: String, default: '' },
  pathIds: { type: Array, default: () => [] },
})
const emit = defineEmits(['node-select', 'edge-select', 'clear-selection'])
const container = ref(null),
  effects = ref(null),
  stage = ref(null),
  presentationButton = ref(null)
const presenting = ref(false),
  entering = ref(false),
  layoutState = ref('ready')
const motionRequested = ref(true),
  reducedMotion = ref(false),
  explicitPlay = ref(false)
const documentHidden = ref(false),
  inView = ref(true),
  hoverInfo = ref(null)
const animationWanted = computed(
  () => motionRequested.value && (!reducedMotion.value || explicitPlay.value),
)
const motionState = computed(() => {
  if (documentHidden.value || !inView.value) return 'hidden'
  if (reducedMotion.value && !explicitPlay.value) return 'reduced'
  if (!motionRequested.value) return 'paused'
  return 'running'
})
const hoverCardStyle = computed(() =>
  hoverInfo.value
    ? { left: `${hoverInfo.value.x}px`, top: `${hoverInfo.value.y}px` }
    : {},
)
const typeNames = {
  CONCEPT: '概念',
  TASK: '学习任务',
  ALGORITHM: '算法',
  MODEL: '模型',
  METRIC: '评价指标',
}
const nodeId = (id) => `node:${id}`
const edgeId = (id) => `edge:${id}`
const colour = (type) =>
  ({
    CONCEPT: '#72a7ff',
    TASK: '#49d7c5',
    ALGORITHM: '#b399ff',
    MODEL: '#ffc67d',
    METRIC: '#ff8fb5',
  })[type] || '#99b7d7'
let cy,
  resizeObserver,
  intersectionObserver,
  motionQuery,
  currentLayout,
  layoutDestinations
let frameId = 0,
  redrawId = 0,
  lastFrame = 0,
  elapsed = 0,
  layoutSequence = 0
let hoveredId = '',
  previousFocus = null,
  previousOverflow = '',
  unmounted = false

function syncElements() {
  if (!cy) return
  const firstLoad = cy.nodes().empty()
  const existingIds = new Set(cy.nodes().map((node) => node.id()))
  const added = []
  const wanted = new Set([
    ...props.nodes.map((node) => nodeId(node.id)),
    ...props.edges.map((edge) => edgeId(edge.id)),
  ])
  cy.batch(() => {
    cy.elements()
      .filter((element) => !wanted.has(element.id()))
      .remove()
    props.nodes.forEach((node, index) => {
      const id = nodeId(node.id),
        current = cy.getElementById(id)
      const data = {
        ...node,
        id,
        originalId: node.id,
        colour: colour(node.type),
      }
      if (current.length) current.data(data)
      else {
        const angle = (index / Math.max(props.nodes.length, 1)) * Math.PI * 2
        const link = props.edges.find(
          (edge) =>
            (edge.source === node.id && existingIds.has(nodeId(edge.target))) ||
            (edge.target === node.id && existingIds.has(nodeId(edge.source))),
        )
        const anchor = link
          ? cy
              .getElementById(
                nodeId(link.source === node.id ? link.target : link.source),
              )
              .position()
          : { x: 300, y: 300 }
        const position = {
          x: anchor.x + (firstLoad ? 240 : 140) * Math.cos(angle),
          y: anchor.y + (firstLoad ? 240 : 140) * Math.sin(angle),
        }
        added.push(cy.add({ group: 'nodes', data, position }))
      }
    })
    for (const edge of props.edges) {
      const id = edgeId(edge.id),
        current = cy.getElementById(id)
      const data = {
        ...edge,
        id,
        originalId: edge.id,
        source: nodeId(edge.source),
        target: nodeId(edge.target),
      }
      if (
        !cy.getElementById(data.source).length ||
        !cy.getElementById(data.target).length
      )
        continue
      if (
        current.length &&
        (current.data('source') !== data.source ||
          current.data('target') !== data.target)
      )
        current.remove()
      const existing = cy.getElementById(id)
      if (existing.length) existing.data(data)
      else added.push(cy.add({ group: 'edges', data }))
    }
  })
  syncStyles()
  if (firstLoad && props.nodes.length) layout(true)
  else if (motionState.value === 'running') {
    for (const item of added)
      item.style('opacity', 0).animate(
        { style: { opacity: 1 } },
        {
          duration: 360,
          queue: false,
          complete: () => item.removeStyle('opacity'),
        },
      )
  }
  requestRedraw()
}

function syncStyles() {
  if (!cy) return
  cy.batch(() => {
    cy.elements().removeClass('chosen related dimmed path filtered hovered')
    cy.edges().forEach((edge) =>
      edge.toggleClass(
        'filtered',
        Boolean(props.relationType && edge.data('type') !== props.relationType),
      ),
    )
    const selectedNode = cy.getElementById(nodeId(props.selectedId))
    const selected = selectedNode.length
      ? selectedNode
      : cy.getElementById(edgeId(props.selectedId))
    if (selected.length) {
      selected.addClass('chosen')
      if (selected.isEdge()) selected.connectedNodes().addClass('related')
    }
    const hovered = cy.getElementById(hoveredId)
    if (hovered.length) hovered.addClass('hovered')
    const focus = hovered.length ? hovered : selected
    if (focus.length) {
      const neighborhood = focus.isNode()
        ? focus.closedNeighborhood()
        : focus.union(focus.connectedNodes())
      cy.elements().difference(neighborhood).addClass('dimmed')
      neighborhood.nodes().difference(focus).addClass('related')
    }
    const path = new Set(props.pathIds)
    cy.elements().forEach((element) =>
      element.toggleClass('path', path.has(element.data('originalId'))),
    )
  })
  requestRedraw()
}

function layout(entrance = false) {
  if (!cy || !cy.nodes().length) return
  interruptLayout()
  const sequence = ++layoutSequence
  const originals = new Map(
    cy.nodes().map((node) => [node.id(), { ...node.position() }]),
  )
  let destinations
  // Calculate targets in one batch, then transition from the current view.
  cy.batch(() => {
    cy.nodes().forEach((node, index) => {
      const angle = (index / cy.nodes().length) * Math.PI * 2
      node.position({
        x: 300 + 240 * Math.cos(angle),
        y: 300 + 240 * Math.sin(angle),
      })
    })
    cy.layout({
      name: 'cose',
      animate: false,
      fit: false,
      nodeRepulsion: () => 12000,
      idealEdgeLength: () => 55,
      gravity: 1,
      componentSpacing: 60,
      randomize: false,
    }).run()
    orientToViewport()
    destinations = Object.fromEntries(
      cy.nodes().map((node) => [node.id(), { ...node.position() }]),
    )
    cy.nodes().forEach((node) => node.position(originals.get(node.id())))
  })
  layoutDestinations = destinations
  const animate = motionState.value === 'running'
  entering.value = entrance && animate
  layoutState.value = animate ? 'settling' : 'ready'
  currentLayout = cy.layout({
    name: 'preset',
    positions: destinations,
    fit: true,
    padding: 40,
    animate,
    animationDuration: 700,
    animationEasing: 'ease-out-cubic',
    stop: () => {
      if (unmounted || sequence !== layoutSequence) return
      layoutState.value = 'ready'
      entering.value = false
      currentLayout = null
      requestRedraw()
    },
  })
  currentLayout.run()
}

function orientToViewport() {
  const elements = cy.elements(':visible')
  const normalPositions = new Map(
    cy.nodes().map((node) => [node.id(), { ...node.position() }]),
  )
  const bounds = elements.boundingBox({ useCache: false })
  const normalFit = cy.getFitViewport(bounds, 40)
  if (!normalFit) return
  const center = {
    x: (bounds.x1 + bounds.x2) / 2,
    y: (bounds.y1 + bounds.y2) / 2,
  }
  // A relationship network has no fixed reading axis. Compare both orientations
  // with upright labels so the whole chapter fits the available canvas clearly.
  cy.nodes().positions((node) => {
    const point = normalPositions.get(node.id())
    return {
      x: center.x - (point.y - center.y),
      y: center.y + (point.x - center.x),
    }
  })
  const rotatedFit = cy.getFitViewport(
    elements.boundingBox({ useCache: false }),
    40,
  )
  if (!rotatedFit || rotatedFit.zoom < normalFit.zoom * 1.08) {
    cy.nodes().positions((node) => normalPositions.get(node.id()))
  }
}

function interruptLayout(complete = false) {
  const destinations = complete && currentLayout ? layoutDestinations : null
  ++layoutSequence
  currentLayout?.stop()
  currentLayout = null
  cy?.stop(true, false)
  cy?.elements().stop(true, false).removeStyle('opacity')
  if (destinations && cy) {
    cy.nodes().positions((node) => destinations[node.id()] || node.position())
    cy.fit(cy.elements(':visible'), 40)
  }
  layoutState.value = 'ready'
  entering.value = false
}

function fit() {
  if (!cy) return
  interruptLayout()
  if (motionState.value === 'running')
    cy.animate(
      { fit: { eles: cy.elements(':visible'), padding: 40 } },
      { duration: 300 },
    )
  else cy.fit(cy.elements(':visible'), 40)
}
function zoomBy(factor) {
  if (cy)
    cy.zoom({
      level: Math.max(0.15, Math.min(3, cy.zoom() * factor)),
      renderedPosition: { x: cy.width() / 2, y: cy.height() / 2 },
    })
}
function focusElements(elements) {
  if (!cy || !elements?.length) return
  interruptLayout()
  const zoom = Math.max(cy.zoom(), 0.9)
  if (motionState.value === 'running')
    cy.animate(
      { center: { eles: elements }, zoom },
      { duration: 280, easing: 'ease-out-cubic' },
    )
  else {
    cy.zoom(zoom)
    cy.center(elements)
  }
}
function focus(id) {
  focusElements(cy?.getElementById(nodeId(id)))
}
function focusRelation(id) {
  focusElements(cy?.getElementById(edgeId(id)).connectedNodes())
}
function locateInPresentation(id) {
  if (id) {
    focus(id)
    emit('node-select', id)
  }
}
defineExpose({ focus, focusRelation })

function toggleMotion() {
  if (animationWanted.value) motionRequested.value = false
  else {
    explicitPlay.value = true
    motionRequested.value = true
  }
}
async function togglePresentation() {
  presenting.value = !presenting.value
  if (presenting.value) {
    previousFocus = document.activeElement
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  } else document.body.style.overflow = previousOverflow
  await nextTick()
  cy?.resize()
  fit()
  if (presenting.value) presentationButton.value?.focus()
  else if (previousFocus?.isConnected) previousFocus.focus()
}
function onStageKey(event) {
  if (!presenting.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    togglePresentation()
  }
  if (event.key !== 'Tab') return
  const controls = [
    ...stage.value.querySelectorAll('button:not(:disabled),select'),
  ].filter((control) => control.offsetParent !== null)
  const first = controls[0],
    last = controls[controls.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}

function updateHover(event) {
  hoveredId = event.target.id()
  syncStyles()
  updateHoverCard()
}
function updateHoverCard() {
  const item = cy?.getElementById(hoveredId)
  if (!item?.length || !item.isNode()) {
    hoverInfo.value = null
    return
  }
  const position = item.renderedPosition()
  hoverInfo.value = {
    name: item.data('name'),
    type: typeNames[item.data('type')] || item.data('type'),
    colour: item.data('colour'),
    x: Math.max(12, Math.min(cy.width() - 190, position.x + 25)),
    y: Math.max(54, position.y - 32),
  }
}
function clearHover() {
  hoveredId = ''
  hoverInfo.value = null
  syncStyles()
}
async function clearFocus() {
  const hadStageFocus = stage.value?.contains(document.activeElement)
  clearHover()
  emit('clear-selection')
  await nextTick()
  if (
    presenting.value &&
    hadStageFocus &&
    !stage.value?.contains(document.activeElement)
  ) {
    presentationButton.value?.focus()
  }
}

function resizeEffects() {
  if (!effects.value || !container.value) return
  const ratio = Math.min(window.devicePixelRatio || 1, 2)
  effects.value.width = Math.round(container.value.clientWidth * ratio)
  effects.value.height = Math.round(container.value.clientHeight * ratio)
  effects.value.getContext('2d')?.setTransform(ratio, 0, 0, ratio, 0, 0)
  requestRedraw()
}
const midpoint = (a, b) => ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 })
function edgeGeometry(edge) {
  const source = edge.sourceEndpoint(),
    target = edge.targetEndpoint()
  if (
    !source ||
    !target ||
    !Number.isFinite(source.x + source.y + target.x + target.y)
  )
    return null
  const pan = cy.pan(),
    zoom = cy.zoom()
  const rendered = (point) => ({
    x: point.x * zoom + pan.x,
    y: point.y * zoom + pan.y,
  })
  const start = rendered(source),
    end = rendered(target)
  // A middle bundled edge is straight and has no control points in Cytoscape.
  const controls = (edge.controlPoints() || []).map(rendered)
  return { start, end, controls }
}
function pointOnEdge(geometry, progress) {
  const { start, end, controls } = geometry
  if (!controls.length)
    return {
      x: start.x + (end.x - start.x) * progress,
      y: start.y + (end.y - start.y) * progress,
    }
  const index = Math.min(
    controls.length - 1,
    Math.floor(progress * controls.length),
  )
  const local = progress * controls.length - index,
    inverse = 1 - local
  const a = index === 0 ? start : midpoint(controls[index - 1], controls[index])
  const b =
    index === controls.length - 1
      ? end
      : midpoint(controls[index], controls[index + 1])
  const control = controls[index]
  return {
    x:
      inverse * inverse * a.x +
      2 * inverse * local * control.x +
      local * local * b.x,
    y:
      inverse * inverse * a.y +
      2 * inverse * local * control.y +
      local * local * b.y,
  }
}

function drawEffects() {
  if (!cy || !effects.value) return
  const context = effects.value.getContext('2d')
  if (!context) return
  context.clearRect(0, 0, cy.width(), cy.height())
  // Decorative motion is limited to visible graph elements and never creates graph facts.
  const visibleEdges = cy
    .edges(':visible')
    .toArray()
    .sort(
      (a, b) =>
        Number(b.hasClass('chosen') || b.hasClass('path')) -
        Number(a.hasClass('chosen') || a.hasClass('path')),
    )
    .slice(0, 100)
  visibleEdges.forEach((edge, index) => {
    const geometry = edgeGeometry(edge)
    if (!geometry) return
    const colour = edge.hasClass('path')
      ? '#ffd997'
      : edge.hasClass('chosen') || edge.hasClass('hovered')
        ? '#ffffff'
        : '#8fe7ff'
    const opacity = edge.hasClass('dimmed') ? 0.15 : 0.9
    for (let particle = 0; particle < 2; particle++) {
      const progress = (elapsed / 4300 + index * 0.173 + particle * 0.5) % 1
      const point = pointOnEdge(geometry, progress)
      if (!point) continue
      context.strokeStyle = colour
      for (let tail = 4; tail > 0; tail--) {
        const a = pointOnEdge(geometry, Math.max(0, progress - tail * 0.012))
        const b = pointOnEdge(
          geometry,
          Math.max(0, progress - (tail - 1) * 0.012),
        )
        if (!a || !b) continue
        context.globalAlpha = opacity * (1 - tail / 5) * 0.7
        context.lineWidth = 1.6
        context.beginPath()
        context.moveTo(a.x, a.y)
        context.lineTo(b.x, b.y)
        context.stroke()
      }
      context.globalAlpha = opacity
      context.shadowColor = colour
      context.shadowBlur = 9
      context.fillStyle = colour
      context.beginPath()
      context.arc(point.x, point.y, 2.2, 0, Math.PI * 2)
      context.fill()
      context.shadowBlur = 0
    }
  })
  cy.nodes(':visible')
    .slice(0, 120)
    .forEach((node, index) => {
      const position = node.renderedPosition(),
        radius = node.renderedWidth() / 2
      if (
        position.x < -radius ||
        position.y < -radius ||
        position.x > cy.width() + radius ||
        position.y > cy.height() + radius
      )
        return
      const pulse = (Math.sin(elapsed / 1100 + index * 0.8) + 1) / 2
      const selected =
        node.hasClass('chosen') ||
        node.hasClass('hovered') ||
        node.hasClass('path')
      context.globalAlpha = node.hasClass('dimmed')
        ? 0.08
        : selected
          ? 0.65
          : 0.17 + pulse * 0.13
      context.strokeStyle = node.data('colour')
      context.lineWidth = selected ? 1.8 : 1
      context.beginPath()
      context.arc(
        position.x,
        position.y,
        radius + 5 + pulse * 3,
        0,
        Math.PI * 2,
      )
      context.stroke()
      if (selected) {
        context.globalAlpha *= 0.5
        context.setLineDash([3, 7])
        context.lineDashOffset = -elapsed / 55
        context.beginPath()
        context.arc(position.x, position.y, radius + 13, 0, Math.PI * 2)
        context.stroke()
        context.setLineDash([])
      }
    })
  context.globalAlpha = 1
}
function requestRedraw() {
  if (redrawId || unmounted || motionState.value === 'hidden') return
  redrawId = requestAnimationFrame(() => {
    redrawId = 0
    drawEffects()
    updateHoverCard()
  })
}
function animateFrame(time) {
  frameId = 0
  if (unmounted || motionState.value !== 'running') return
  if (!lastFrame) lastFrame = time
  const difference = time - lastFrame
  if (difference >= 1000 / 30) {
    elapsed += Math.min(difference, 100)
    lastFrame = time
    drawEffects()
  }
  frameId = requestAnimationFrame(animateFrame)
}
function updateAnimation() {
  cancelAnimationFrame(frameId)
  frameId = 0
  lastFrame = 0
  cancelAnimationFrame(redrawId)
  redrawId = 0
  if (cy)
    cy.style()
      .selector('edge')
      .style(
        'transition-duration',
        motionState.value === 'running' ? '160ms' : '0ms',
      )
      .update()
  if (motionState.value === 'running')
    frameId = requestAnimationFrame(animateFrame)
  else interruptLayout(true)
  if (motionState.value !== 'hidden') requestRedraw()
}
function onVisibility() {
  documentHidden.value = document.hidden
}
function onMotionPreference(event) {
  reducedMotion.value = event.matches
  explicitPlay.value = false
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = motionQuery.matches
  documentHidden.value = document.hidden
  motionQuery.addEventListener('change', onMotionPreference)
  document.addEventListener('visibilitychange', onVisibility)
  cy = cytoscape({
    container: container.value,
    elements: [],
    minZoom: 0.15,
    maxZoom: 3,
    wheelSensitivity: 0.25,
    style: [
      {
        selector: 'node',
        style: {
          shape: 'ellipse',
          width: 68,
          height: 68,
          'background-color': 'data(colour)',
          'background-opacity': 0.94,
          label: 'data(name)',
          color: '#e9f2ff',
          'font-size': 18,
          'font-family': 'sans-serif',
          'font-weight': 500,
          'text-valign': 'bottom',
          'text-margin-y': 12,
          'text-wrap': 'wrap',
          'text-max-width': 130,
          'text-outline-color': '#0c172d',
          'text-outline-width': 2,
          'border-width': 2.5,
          'border-color': 'data(colour)',
          'border-opacity': 0.65,
          'overlay-opacity': 0,
        },
      },
      {
        selector: 'edge',
        style: {
          width: 1.6,
          'curve-style': 'bezier',
          'line-color': '#4d779a',
          'target-arrow-color': '#90bad8',
          'target-arrow-shape': 'triangle',
          'arrow-scale': 1.05,
          label: 'data(label)',
          color: '#a6c2d9',
          'font-size': 14,
          'text-background-color': '#101d34',
          'text-background-opacity': 0.9,
          'text-background-padding': 4,
          'text-rotation': 'autorotate',
          'transition-property': 'line-color, width',
          'transition-duration': '160ms',
        },
      },
      {
        selector: 'edge[type = "PREREQUISITE_FOR"]',
        style: {
          'line-style': 'dashed',
          'line-color': '#a48cdb',
          'target-arrow-color': '#bea4f7',
        },
      },
      {
        selector: 'node.chosen, node.hovered',
        style: {
          'border-color': '#ffffff',
          'border-width': 4,
          'border-opacity': 1,
        },
      },
      {
        selector: 'node.related',
        style: { 'border-width': 3, 'border-opacity': 1 },
      },
      {
        selector: 'edge.chosen, edge.hovered',
        style: {
          'line-color': '#a5eaff',
          'target-arrow-color': '#d7f7ff',
          width: 3,
          color: '#effcff',
        },
      },
      { selector: '.dimmed', style: { opacity: 0.18 } },
      {
        selector: '.path',
        style: {
          'border-color': '#ffd997',
          'line-color': '#f5c778',
          'target-arrow-color': '#ffe2a6',
          'border-width': 4,
          opacity: 1,
        },
      },
      { selector: '.filtered', style: { display: 'none' } },
    ],
  })
  cy.on('tap', 'node', (event) =>
    emit('node-select', event.target.data('originalId')),
  )
  cy.on('tap', 'edge', (event) =>
    emit('edge-select', event.target.data('originalId')),
  )
  cy.on('tap', (event) => {
    if (event.target === cy) clearFocus()
  })
  cy.on('mouseover', 'node,edge', updateHover)
  cy.on('mouseout', 'node,edge', clearHover)
  cy.on('grab', () => interruptLayout())
  cy.on('position pan zoom render', requestRedraw)
  resizeObserver = new ResizeObserver(() => {
    cy?.resize()
    resizeEffects()
  })
  resizeObserver.observe(container.value)
  intersectionObserver = new IntersectionObserver(
    (entries) => {
      inView.value = entries[0]?.isIntersecting ?? true
    },
    { threshold: 0.02 },
  )
  intersectionObserver.observe(container.value)
  syncElements()
  resizeEffects()
  updateAnimation()
})
watch(() => [props.nodes, props.edges], syncElements, { deep: true })
watch(() => [props.selectedId, props.relationType, props.pathIds], syncStyles, {
  deep: true,
})
watch(motionState, updateAnimation)
onBeforeUnmount(() => {
  unmounted = true
  cancelAnimationFrame(frameId)
  cancelAnimationFrame(redrawId)
  interruptLayout()
  resizeObserver?.disconnect()
  intersectionObserver?.disconnect()
  motionQuery?.removeEventListener('change', onMotionPreference)
  document.removeEventListener('visibilitychange', onVisibility)
  if (presenting.value) document.body.style.overflow = previousOverflow
  cy?.destroy()
  cy = null
})
</script>

<style scoped>
.network-wrap {
  position: relative;
  min-width: 0;
  isolation: isolate;
  color: #dcecff;
  background:
    radial-gradient(ellipse at 18% 25%, #16335c80, transparent 56%),
    radial-gradient(ellipse at 84% 75%, #27315266, transparent 50%),
    radial-gradient(circle at 1px 1px, #7ba3d017 1px, transparent 0) 0 0/28px
      28px,
    #0b162b;
}
.network-wrap::before {
  content: '';
  position: absolute;
  inset: 0;
  box-shadow: inset 0 0 95px #050b194a;
  pointer-events: none;
  z-index: 1;
}
.knowledge-network {
  height: 640px;
  width: 100%;
  min-width: 0;
}
.is-entering .knowledge-network {
  animation: network-enter 700ms ease-out both;
}
.motion-layer {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 2;
}
.view-controls {
  position: absolute;
  left: 22px;
  bottom: 22px;
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 5px;
  border: 1px solid #779ac633;
  border-radius: 10px;
  background: #12223bef;
  box-shadow: 0 8px 30px #02091440;
  z-index: 4;
}
button {
  border: 0;
  background: transparent;
  padding: 9px 11px;
  border-radius: 6px;
  color: #c4d9f1;
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
  font-family: inherit;
}
button:hover {
  color: white;
  background: #7daee21a;
}
button:focus-visible,
select:focus-visible {
  outline: 2px solid #8be5ef;
  outline-offset: 2px;
}
button:disabled {
  opacity: 0.45;
  cursor: wait;
}
.control-divider {
  height: 18px;
  width: 1px;
  background: #8facd137;
  margin: 0 3px;
}
.motion-icon {
  display: inline-block;
  width: 14px;
  margin-right: 5px;
  color: #79ded9;
}
.presentation-control {
  background: #688bc323;
}
.canvas-instruction {
  position: absolute;
  top: 18px;
  left: 24px;
  color: #819bb9;
  font-size: 11px;
  letter-spacing: 0.5px;
  pointer-events: none;
  z-index: 3;
}
.motion-preference {
  position: absolute;
  right: 18px;
  top: 18px;
  font-size: 11px;
  color: #97abc3;
  z-index: 3;
}
.hover-card {
  position: absolute;
  display: flex;
  gap: 9px;
  align-items: center;
  z-index: 4;
  border: 1px solid #789cc943;
  border-radius: 8px;
  padding: 10px 13px;
  background: #15233cf2;
  box-shadow: 0 8px 24px #0003;
  pointer-events: none;
  font-size: 13px;
  max-width: 180px;
}
.hover-card i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex: none;
}
.hover-card small {
  display: block;
  font-size: 10px;
  color: #8fa9c8;
  margin-top: 4px;
}
.is-presenting {
  position: fixed;
  inset: 0;
  z-index: 4000;
  display: flex;
  flex-direction: column;
}
.is-presenting .knowledge-network {
  flex: 1;
  height: 0;
}
.is-presenting .motion-layer {
  top: 86px;
  height: calc(100% - 86px);
}
.is-presenting .canvas-instruction {
  top: 105px;
}
.is-presenting .motion-preference {
  top: 105px;
}
.is-presenting .hover-card {
  transform: translateY(86px);
}
.presentation-heading {
  height: 86px;
  flex: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 30px;
  border-bottom: 1px solid #8aafd31c;
  box-sizing: border-box;
}
.presentation-heading h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 550;
}
.presentation-heading p {
  margin: 7px 0 0;
  color: #829dbd;
  font-size: 12px;
}
.presentation-heading label {
  font-size: 11px;
  color: #95adc8;
  display: flex;
  gap: 10px;
  align-items: center;
}
.presentation-heading select {
  max-width: 210px;
  background: #14253f;
  color: #dcecff;
  border: 1px solid #688fbe55;
  border-radius: 6px;
  padding: 8px;
}
@keyframes network-enter {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
@media (min-width: 1200px) {
  .is-presenting .view-controls {
    top: 22px;
    bottom: auto;
    left: 50%;
    transform: translateX(-50%);
  }
  .presentation-heading > div,
  .presentation-heading > label {
    max-width: calc(50% - 300px);
  }
  .presentation-heading h2 {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .presentation-heading select {
    min-width: 0;
  }
}
@media (max-width: 700px) {
  .knowledge-network {
    height: 500px;
  }
  .view-controls {
    left: 10px;
    right: 10px;
    bottom: 12px;
    flex-wrap: wrap;
    gap: 1px;
    width: max-content;
    max-width: calc(100% - 20px);
  }
  button {
    font-size: 11px;
    padding: 9px 8px;
  }
  .canvas-instruction {
    left: 14px;
    font-size: 10px;
  }
  .motion-preference {
    right: 14px;
    top: 38px;
    font-size: 10px;
  }
  .presentation-heading {
    padding: 0 16px;
    gap: 12px;
  }
  .presentation-heading h2 {
    font-size: 16px;
  }
  .presentation-heading label {
    display: none;
  }
  .is-presenting .motion-preference {
    top: 125px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .is-entering .knowledge-network {
    animation: none;
  }
}
</style>
