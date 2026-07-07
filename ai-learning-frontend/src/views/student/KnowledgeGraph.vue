<template>
  <div class="knowledge-graph-page">
    <div class="page-header">
      <div>
        <h2>知识图谱</h2>
        <p>查看知识库中已构建的节点与关系，并支持拖拽、缩放与节点联动。</p>
      </div>
      <div class="header-actions">
        <el-select
          v-model="selectedKbId"
          placeholder="请选择知识库"
          filterable
          clearable
          style="width: 260px"
          @change="handleKbChange"
        >
          <el-option
            v-for="kb in knowledgeBases"
            :key="kb.id"
            :label="kb.name"
            :value="kb.id"
          />
        </el-select>
      </div>
    </div>

    <el-card shadow="never" v-loading="loading" class="summary-card">
      <template v-if="selectedKb">
        <div class="summary-row">
          <div class="summary-main">
            <div class="kb-title-row">
              <h3>{{ selectedKb.name }}</h3>
              <el-tag :type="graphEnabled ? 'success' : 'info'" effect="light">
                {{ graphEnabled ? '图谱已启用' : '未启用图谱' }}
              </el-tag>
            </div>
            <p class="kb-desc">{{ selectedKb.description || '暂无知识库描述' }}</p>
          </div>
          <div class="summary-stats">
            <div class="stat-card">
              <span class="stat-label">节点数</span>
              <span class="stat-value">{{ normalizedNodes.length }}</span>
            </div>
            <div class="stat-card">
              <span class="stat-label">关系数</span>
              <span class="stat-value">{{ normalizedRelations.length }}</span>
            </div>
          </div>
        </div>
      </template>
      <el-empty v-else description="请选择一个知识库查看图谱" />
    </el-card>

    <div v-if="selectedKb" class="graph-layout">
      <el-card shadow="never" class="graph-card">
        <template #header>
          <div class="card-header">
            <span>关系图</span>
            <div class="graph-header-tags">
              <el-tag size="small" effect="plain">可拖拽 / 缩放</el-tag>
              <el-tag v-if="selectedNode" size="small" type="primary" effect="light">
                已选节点：{{ selectedNode.label }}
              </el-tag>
            </div>
          </div>
        </template>

        <div v-if="!graphEnabled" class="empty-wrap">
          <el-empty description="当前知识库未开启图谱抽取" />
        </div>
        <div v-else-if="!normalizedNodes.length" class="empty-wrap">
          <el-empty description="当前知识库暂无可展示的图谱节点" />
        </div>
        <div v-else class="graph-stage">
          <div class="graph-toolbar">
            <span>拖动画布可平移，滚轮可缩放，拖动节点可调整布局。</span>
            <el-button v-if="selectedNodeId" link type="primary" @click="clearSelection">
              清除节点筛选
            </el-button>
          </div>

          <VueFlow
            :nodes="flowNodes"
            :edges="flowEdges"
            class="interactive-graph"
            fit-view-on-init
            :min-zoom="0.2"
            :max-zoom="2"
            :zoom-on-scroll="true"
            :pan-on-drag="true"
            :nodes-draggable="true"
            :elements-selectable="true"
            @node-click="handleNodeClick"
            @pane-click="clearSelection"
          />
        </div>
      </el-card>

      <el-card shadow="never" class="relation-card">
        <template #header>
          <div class="card-header">
            <span>关系明细</span>
            <div class="graph-header-tags">
              <el-tag v-if="selectedNode" size="small" type="primary" effect="light">
                {{ selectedNode.label }} 相关
              </el-tag>
              <el-tag size="small" effect="plain">{{ displayedRelations.length }} 条</el-tag>
            </div>
          </div>
        </template>

        <div v-if="!graphEnabled" class="empty-wrap">
          <el-empty description="当前知识库未开启图谱抽取" />
        </div>
        <div v-else-if="!normalizedRelations.length" class="empty-wrap">
          <el-empty description="当前知识库暂无可展示的关系" />
        </div>
        <div v-else-if="!displayedRelations.length" class="empty-wrap">
          <el-empty description="当前节点暂无关联关系" />
        </div>
        <div v-else class="relation-list">
          <div v-for="relation in displayedRelations" :key="relation.id" class="relation-item">
            <div class="relation-top">
              <span class="relation-node" :class="{ active: relation.source === selectedNodeId }">{{ relation.source }}</span>
              <el-tag size="small" type="primary" effect="plain">{{ relation.label }}</el-tag>
              <span class="relation-node" :class="{ active: relation.target === selectedNodeId }">{{ relation.target }}</span>
            </div>
            <div v-if="relation.rawText" class="relation-raw">{{ relation.rawText }}</div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { VueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import * as kbApi from '@/api/knowledgeBase'

function unwrapData(data) {
  while (data && typeof data === 'object' && !Array.isArray(data) && data.data !== undefined)
    data = data.data
  return data
}

function unwrapList(data) {
  const unwrapped = unwrapData(data)
  if (Array.isArray(unwrapped)) return unwrapped
  return unwrapped?.items || unwrapped?.list || unwrapped?.documents || unwrapped?.results || unwrapped?.records || []
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const knowledgeBases = ref([])
const selectedKbId = ref('')
const selectedNodeId = ref('')
const graphData = ref({ enabled: false, nodes: [], relations: [] })

const selectedKb = computed(() => knowledgeBases.value.find(kb => kb.id === selectedKbId.value) || null)
const graphEnabled = computed(() => Boolean(graphData.value?.enabled))

function pickNodeLabel(node, fallback) {
  if (typeof node === 'string') return node.trim()
  if (!node || typeof node !== 'object') return fallback
  return String(node.label || node.name || node.id || fallback).trim()
}

const normalizedGraph = computed(() => {
  const rawNodes = Array.isArray(graphData.value?.nodes) ? graphData.value.nodes : []
  const rawRelations = Array.isArray(graphData.value?.relations) ? graphData.value.relations : []
  const nodeMap = new Map()

  rawNodes.forEach((node, index) => {
    const label = pickNodeLabel(node, `节点${index + 1}`)
    if (!label) return
    const id = typeof node === 'object' && node ? String(node.id || node.name || label) : label
    if (!nodeMap.has(id)) {
      nodeMap.set(id, {
        id,
        label,
        raw: node
      })
    }
  })

  const relations = []
  rawRelations.forEach((relation, index) => {
    if (!relation) return

    let source = ''
    let target = ''
    let label = '关系'
    let rawText = ''

    if (typeof relation === 'string') {
      rawText = relation
      const matched = relation.match(/^(.+?)\s*(?:->|→|-|—|:|：)\s*(.+)$/)
      if (matched) {
        source = matched[1].trim()
        target = matched[2].trim()
      }
    } else if (typeof relation === 'object') {
      source = String(relation.node1 || relation.source || relation.from || relation.start || relation.subject || '').trim()
      target = String(relation.node2 || relation.target || relation.to || relation.end || relation.object || '').trim()
      label = String(relation.type || relation.label || relation.name || relation.relation || '关系').trim()
    }

    if (!source || !target) return
    if (!nodeMap.has(source)) nodeMap.set(source, { id: source, label: source, inferred: true })
    if (!nodeMap.has(target)) nodeMap.set(target, { id: target, label: target, inferred: true })

    relations.push({
      id: `edge-${index}-${source}-${target}-${label}`,
      source,
      target,
      label,
      rawText
    })
  })

  return {
    nodes: Array.from(nodeMap.values()),
    relations
  }
})

const normalizedNodes = computed(() => normalizedGraph.value.nodes)
const normalizedRelations = computed(() => normalizedGraph.value.relations)
const selectedNode = computed(() => normalizedNodes.value.find(node => node.id === selectedNodeId.value) || null)

const displayedRelations = computed(() => {
  if (!selectedNodeId.value) return normalizedRelations.value
  return normalizedRelations.value.filter(relation => relation.source === selectedNodeId.value || relation.target === selectedNodeId.value)
})

const flowNodes = computed(() => {
  const nodes = normalizedNodes.value
  if (!nodes.length) return []

  const centerX = 360
  const centerY = 220
  const radius = Math.max(140, Math.min(280, 70 + nodes.length * 18))

  if (nodes.length === 1) {
    const node = nodes[0]
    return [{
      id: node.id,
      label: truncate(node.label, 18),
      position: { x: centerX, y: centerY },
      data: { fullLabel: node.label },
      class: selectedNodeId.value === node.id ? 'is-selected' : ''
    }]
  }

  return nodes.map((node, index) => {
    const angle = (Math.PI * 2 * index) / nodes.length - Math.PI / 2
    return {
      id: node.id,
      label: truncate(node.label, 18),
      position: {
        x: centerX + radius * Math.cos(angle),
        y: centerY + radius * Math.sin(angle)
      },
      data: { fullLabel: node.label },
      class: selectedNodeId.value === node.id ? 'is-selected' : ''
    }
  })
})

const flowEdges = computed(() => {
  const nodeIds = new Set(flowNodes.value.map(node => node.id))

  return normalizedRelations.value
    .filter(edge => nodeIds.has(edge.source) && nodeIds.has(edge.target))
    .map(edge => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      label: edge.label,
      class: !selectedNodeId.value || edge.source === selectedNodeId.value || edge.target === selectedNodeId.value ? 'is-active' : 'is-dimmed',
      labelStyle: {
        fill: '#475569',
        fontSize: '11px',
        fontWeight: 600
      },
      style: {
        stroke: !selectedNodeId.value || edge.source === selectedNodeId.value || edge.target === selectedNodeId.value ? '#3b82f6' : '#cbd5e1',
        strokeWidth: !selectedNodeId.value || edge.source === selectedNodeId.value || edge.target === selectedNodeId.value ? 2 : 1.4
      }
    }))
})

function truncate(text, max) {
  if (!text) return ''
  return text.length > max ? `${text.slice(0, max)}…` : text
}

function syncRoute(kbId) {
  const query = kbId ? { kbId } : {}
  router.replace({ query })
}

function handleKbChange(kbId) {
  syncRoute(kbId)
}

function handleNodeClick(event) {
  selectedNodeId.value = event?.node?.id || ''
}

function clearSelection() {
  selectedNodeId.value = ''
}

async function loadGraph(kbId) {
  selectedNodeId.value = ''

  if (!kbId) {
    graphData.value = { enabled: false, nodes: [], relations: [] }
    return
  }

  loading.value = true
  try {
    const res = await kbApi.getKnowledgeGraph(kbId)
    if (res.code === '200') {
      const data = unwrapData(res.data) || {}
      graphData.value = {
        enabled: Boolean(data.enabled),
        nodes: Array.isArray(data.nodes) ? data.nodes : [],
        relations: Array.isArray(data.relations) ? data.relations : []
      }
      return
    }
    graphData.value = { enabled: false, nodes: [], relations: [] }
  } finally {
    loading.value = false
  }
}

async function loadKnowledgeBases() {
  loading.value = true
  try {
    const res = await kbApi.listKnowledgeBases()
    if (res.code === '200') {
      knowledgeBases.value = unwrapList(res.data)
      const queryKbId = typeof route.query.kbId === 'string' ? route.query.kbId : ''
      if (queryKbId && knowledgeBases.value.some(kb => kb.id === queryKbId)) {
        selectedKbId.value = queryKbId
      } else if (knowledgeBases.value.length > 0) {
        selectedKbId.value = knowledgeBases.value[0].id
        syncRoute(selectedKbId.value)
      } else {
        graphData.value = { enabled: false, nodes: [], relations: [] }
      }
    }
  } finally {
    loading.value = false
  }
}

watch(selectedKbId, kbId => {
  loadGraph(kbId)
})

watch(
  () => route.query.kbId,
  queryKbId => {
    const nextKbId = typeof queryKbId === 'string' ? queryKbId : ''
    if (!nextKbId) {
      if (selectedKbId.value) selectedKbId.value = ''
      return
    }
    if (knowledgeBases.value.some(kb => kb.id === nextKbId) && selectedKbId.value !== nextKbId) {
      selectedKbId.value = nextKbId
    }
  }
)

onMounted(loadKnowledgeBases)
</script>

<style scoped>
.knowledge-graph-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  color: #1f2937;
}

.page-header p {
  margin: 8px 0 0;
  color: #6b7280;
}

.summary-card,
.graph-card,
.relation-card {
  border-radius: 18px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.kb-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.kb-title-row h3 {
  margin: 0;
  font-size: 20px;
  color: #111827;
}

.kb-desc {
  margin: 0;
  color: #6b7280;
  line-height: 1.6;
}

.summary-stats {
  display: flex;
  gap: 12px;
}

.stat-card {
  min-width: 112px;
  padding: 14px 18px;
  border-radius: 14px;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.graph-layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr);
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.graph-header-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.graph-stage {
  width: 100%;
  min-height: 560px;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fbff 0%, #f3f6fb 100%);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.graph-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px 0;
  color: #64748b;
  font-size: 13px;
  flex-wrap: wrap;
}

.interactive-graph {
  width: 100%;
  height: 520px;
}

.relation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 560px;
  overflow: auto;
}

.relation-item {
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 12px;
  background: #fff;
}

.relation-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.relation-node {
  font-weight: 600;
  color: #1f2937;
}

.relation-node.active {
  color: #2563eb;
}

.relation-raw {
  margin-top: 8px;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.5;
}

.empty-wrap {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.vue-flow__pane) {
  cursor: grab;
}

:deep(.vue-flow__pane.dragging) {
  cursor: grabbing;
}

:deep(.vue-flow__node) {
  min-width: 88px;
  max-width: 180px;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 10px 16px;
  background: #2563eb;
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.18);
}

:deep(.vue-flow__node.is-selected) {
  background: #1d4ed8;
  border-color: #93c5fd;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.18);
}

:deep(.vue-flow__edge-textbg) {
  fill: rgba(255, 255, 255, 0.92);
}

:deep(.vue-flow__edge.is-dimmed path) {
  opacity: 0.55;
}

:deep(.vue-flow__edge.is-active path) {
  opacity: 1;
}

@media (max-width: 1100px) {
  .graph-layout {
    grid-template-columns: 1fr;
  }

  .summary-row,
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .summary-stats {
    width: 100%;
  }

  .stat-card {
    flex: 1;
  }

  .interactive-graph {
    height: 460px;
  }
}
</style>
