<template>
  <div class="graph-preview-card" v-loading="loading">
    <div class="graph-preview-head">
      <div class="graph-preview-title">
        <span>知识图谱预览</span>
        <el-tag :type="graphEnabled ? 'success' : 'info'" effect="light" size="small">
          {{ graphEnabled ? '已启用' : '未启用' }}
        </el-tag>
        <el-tag v-if="graphSource === 'wiki'" type="primary" effect="light" size="small">Wiki</el-tag>
        <el-tag v-if="graphMeta?.truncated" type="warning" effect="light" size="small">
          显示 {{ graphMeta?.returned }}/{{ graphMeta?.total }}
        </el-tag>
      </div>
      <div class="graph-preview-stats" v-if="graphEnabled">
        <span>{{ normalizedNodes.length }} 节点</span>
        <span>{{ normalizedRelations.length }} 关系</span>
      </div>
    </div>

    <div v-if="!kbId" class="graph-preview-empty">
      <el-empty description="请选择知识库查看图谱" :image-size="72" />
    </div>
    <div v-else-if="!graphEnabled" class="graph-preview-empty">
      <el-empty description="当前知识库暂无可用的资料关系视图" :image-size="72" />
    </div>
    <div v-else-if="!normalizedNodes.length" class="graph-preview-empty">
      <el-empty description="当前知识库暂无可展示的图谱节点" :image-size="72" />
    </div>
    <div v-else class="graph-preview-stage" @click="handleStageClick">
      <VueFlow
        ref="vueFlowRef"
        :nodes="flowNodes"
        :edges="flowEdges"
        class="graph-preview-flow"
        fit-view-on-init
        :min-zoom="0.6"
        :max-zoom="1.4"
        :nodes-draggable="false"
        :elements-selectable="false"
        @node-click="handleNodeClick"
      />
    </div>

    <!-- Wiki page dialog -->
    <el-dialog
      v-model="wikiVisible"
      :title="wikiPage?.title || '加载中...'"
      width="680px"
      destroy-on-close
    >
      <div v-if="wikiPage" class="wiki-page-body markdown-body" v-html="wikiHtml" />
      <div v-else v-loading="wikiLoading" style="min-height:200px" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { VueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import * as kbApi from '@/api/knowledgeBase'
import { renderWikiMarkdown } from '@/utils/wikiMarkdown'

const props = defineProps({
  kbId: { type: String, default: '' },
  refreshKey: { type: Number, default: 0 }
})

const emit = defineEmits(['loaded'])

const loading = ref(false)
const vueFlowRef = ref(null)
const graphData = ref({ enabled: false, nodes: [], relations: [] })
const graphSource = ref('')
const graphMeta = ref(null)

const wikiVisible = ref(false)
const wikiLoading = ref(false)
const wikiPage = ref(null)
const wikiHtml = ref('')

function unwrapData(data) {
  while (data && typeof data === 'object' && !Array.isArray(data) && data.data !== undefined)
    data = data.data
  return data
}

function pickNodeLabel(node, fallback) {
  if (typeof node === 'string') return node.trim()
  if (!node || typeof node !== 'object') return fallback
  return String(node.label || node.name || node.id || fallback).trim()
}

const graphEnabled = computed(() => Boolean(graphData.value?.enabled))

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

    if (typeof relation === 'string') {
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
      id: `preview-edge-${index}-${source}-${target}-${label}`,
      source,
      target,
      label
    })
  })

  return {
    nodes: Array.from(nodeMap.values()),
    relations
  }
})

const normalizedNodes = computed(() => normalizedGraph.value.nodes)
const normalizedRelations = computed(() => normalizedGraph.value.relations)

const flowNodes = computed(() => {
  const nodes = normalizedNodes.value
  if (!nodes.length) return []

  const centerX = 250
  const centerY = 150
  const radius = Math.max(90, Math.min(170, 40 + nodes.length * 16))

  if (nodes.length === 1) {
    const node = nodes[0]
    return [{
      id: node.id,
      label: truncate(node.label, 14),
      position: { x: centerX, y: centerY }
    }]
  }

  return nodes.map((node, index) => {
    const angle = (Math.PI * 2 * index) / nodes.length - Math.PI / 2
    return {
      id: node.id,
      label: truncate(node.label, 14),
      position: {
        x: centerX + radius * Math.cos(angle),
        y: centerY + radius * Math.sin(angle)
      }
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
      style: { stroke: 'var(--line-strong)', strokeWidth: 1 }
    }))
})

function truncate(text, max) {
  if (!text) return ''
  return text.length > max ? `${text.slice(0, max)}…` : text
}

async function loadGraph() {
  if (!props.kbId) {
    graphData.value = { enabled: false, nodes: [], relations: [] }
    graphSource.value = ''
    graphMeta.value = null
    emitLoaded()
    return
  }

  loading.value = true
  try {
    const res = await kbApi.getKnowledgeGraph(props.kbId)
    if (res.code === '200') {
      const data = unwrapData(res.data) || {}
      graphData.value = {
        enabled: Boolean(data.enabled),
        nodes: Array.isArray(data.nodes) ? data.nodes : [],
        relations: Array.isArray(data.relations) ? data.relations : []
      }
      graphSource.value = data.source || ''
      graphMeta.value = data.meta || null
    } else {
      graphData.value = { enabled: false, nodes: [], relations: [] }
      graphSource.value = ''
      graphMeta.value = null
    }
  } finally {
    loading.value = false
    emitLoaded()
  }
}

async function handleNodeClick({ node }) {
  if (!props.kbId || graphSource.value !== 'wiki') return

  wikiVisible.value = true
  wikiPage.value = null
  wikiHtml.value = ''
  wikiLoading.value = true
  try {
    const res = await kbApi.getWikiPage(props.kbId, node.id)
    if (res.code === '200' && res.data) {
      wikiPage.value = res.data
      wikiHtml.value = renderWikiMarkdown(res.data.content || '')
    }
  } finally {
    wikiLoading.value = false
  }
}

function emitLoaded() {
  emit('loaded', {
    enabled: graphEnabled.value,
    nodeCount: normalizedNodes.value.length,
    relationCount: normalizedRelations.value.length
  })
}

watch(() => [props.kbId, props.refreshKey], loadGraph, { immediate: true })
</script>

<style scoped>
.graph-preview-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.graph-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.graph-preview-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-strong);
}

.graph-preview-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-subtle);
  font-size: 12px;
}

.graph-preview-stage {
  height: 320px;
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--surface-canvas);
}

.graph-preview-flow {
  width: 100%;
  height: 100%;
}

.graph-preview-empty {
  min-height: 220px;
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
  min-width: 52px;
  max-width: 100px;
  border: none;
  border-radius: 6px;
  padding: 3px 8px;
  background: var(--accent-solid);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  text-align: center;
  box-shadow: var(--shadow-1);
  cursor: pointer;
}

:deep(.vue-flow__edge-textbg) {
  fill: var(--surface-base);
}

.wiki-page-body {
  line-height: 1.8;
  font-size: 14px;
  color: var(--text-body);
}

.wiki-page-body h2 { font-size: 18px; margin: 16px 0 8px; }
.wiki-page-body h3 { font-size: 16px; margin: 14px 0 6px; }
.wiki-page-body h4 { font-size: 14px; margin: 12px 0 6px; }
.wiki-page-body p { margin: 8px 0; }
.wiki-page-body strong { color: var(--text-strong); }
.wiki-page-body .wiki-link { color: var(--accent); text-decoration: underline; cursor: pointer; }
</style>
