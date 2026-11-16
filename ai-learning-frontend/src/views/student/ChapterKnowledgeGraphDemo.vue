<template>
  <main class="chapter-map" :class="{ 'standalone-preview': isPreview }">
    <header class="chapter-heading">
      <div>
        <div class="eyebrow">{{ graph.course }} <span>/</span> 课程知识地图</div>
        <div class="heading-line"><h1>{{ graph.title }}</h1><span class="demo-badge">第2章 · 章节示范</span></div>
        <p>从一个知识点出发，看见本章的结构与联系。</p>
      </div>
      <router-link v-if="!isPreview" class="back-link" :to="isTeacher ? '/teacher-knowledge-base' : '/knowledge-base'">返回资料学习 <span aria-hidden="true">↗</span></router-link>
    </header>

    <section class="map-shell" aria-label="本章知识图谱">
      <div class="map-topbar">
        <div class="map-overview"><span class="status-dot"></span><strong>章节全景</strong><span>{{ graph.nodes.length }} 个知识点 · {{ graph.relations.length }} 条关系</span></div>
        <div class="search-box">
          <el-icon aria-hidden="true"><Search /></el-icon>
          <input v-model="query" placeholder="搜索本章知识点" aria-label="搜索本章知识点" autocomplete="off" @keydown.enter="matches.length && focusConcept(matches[0].id)" @keydown.esc="query = ''" />
          <button v-if="query" class="clear-search" aria-label="清除搜索" @click="query = ''">×</button>
          <div v-if="query.trim()" class="search-results" aria-live="polite">
            <button v-for="item in matches" :key="item.id" :aria-label="`定位知识点：${item.label}`" @click="focusConcept(item.id)"><span>{{ item.label }}</span><small>{{ item.section }}</small></button>
            <p v-if="!matches.length">没有找到匹配的知识点</p>
          </div>
        </div>
      </div>

      <div class="map-body">
        <div class="canvas-column">
          <div class="relation-filters" aria-label="关系类型筛选">
            <button v-for="filter in filters" :key="filter.id" :class="{ active: relationFilter === filter.id }" :aria-label="filter.aria" :aria-pressed="relationFilter === filter.id" @click="changeFilter(filter.id)">{{ filter.label }}</button>
            <span class="canvas-hint">拖动节点，自由探索</span>
          </div>
          <div class="graph-canvas" data-testid="chapter-graph-canvas">
            <VueFlow id="chapter-ml" v-model:nodes="nodes" :edges="edges" :nodes-connectable="false" :edges-updatable="false" :delete-key-code="null" :min-zoom="0.2" :max-zoom="2" :zoom-on-double-click="false" :pan-on-drag="true" :nodes-draggable="true" fit-view-on-init :fit-view-params="{ padding: 0.12 }" @node-click="selectConcept($event.node.id)" @edge-click="selectRelation($event.edge.id)" @pane-click="clearSelection">
              <template #node-concept="{ data, id }">
                <div class="concept-card" :class="[`group-${data.group}`, { chosen: selectedNodeId === id, faded: isDimmed(id) }]" :data-testid="`concept-${id}`" role="button" tabindex="0" :aria-label="`知识点：${data.label}`" @keydown.enter.stop.prevent="selectConcept(id)" @keydown.space.stop.prevent="selectConcept(id)">
                  <Handle id="in" type="target" :position="Position.Left" />
                  <Handle id="out" type="source" :position="Position.Right" />
                  <Handle id="learn-in" type="target" :position="Position.Top" />
                  <Handle id="learn-out" type="source" :position="Position.Bottom" />
                  <span class="concept-kind">{{ groupLabels[data.group] }}</span>
                  <strong>{{ data.label }}</strong>
                  <span class="concept-page">{{ formatPages(data.pages) }}</span>
                </div>
              </template>
            </VueFlow>
            <div class="canvas-controls" aria-label="图谱视图控制">
              <button aria-label="放大图谱" title="放大" @click="zoomIn()">＋</button>
              <button aria-label="缩小图谱" title="缩小" @click="zoomOut()">−</button>
              <span class="control-divider"></span>
              <button @click="fitView({ padding: 0.12, duration: 250 })">适应视图</button>
              <button @click="resetLayout">恢复布局</button>
            </div>
          </div>
          <div class="graph-legend"><span><i class="line-solid"></i>知识关系</span><span><i class="line-dashed"></i>学习建议：先学 → 再学</span><span class="legend-tip">滚轮缩放 · 拖动画布平移</span></div>
        </div>

        <aside class="detail-panel" data-testid="graph-detail" aria-label="知识点与关系详情" aria-live="polite">
          <template v-if="selectedRelation">
            <span class="detail-kicker">关系详情</span>
            <div class="relation-title"><button @click="focusConcept(selectedRelation.source)">{{ nodeById[selectedRelation.source].label }}</button><span aria-hidden="true">↓</span><button @click="focusConcept(selectedRelation.target)">{{ nodeById[selectedRelation.target].label }}</button></div>
            <span class="kind-tag" :class="{ learning: selectedRelation.kind === 'learning' }">{{ selectedRelation.kind === 'learning' ? '学习建议 · 待教师确认' : '课件整理' }} · {{ selectedRelation.label }}</span>
            <p class="detail-description">{{ selectedRelation.description }}</p>
            <div class="source-card"><span>{{ selectedRelation.kind === 'learning' ? '参考内容位置' : '关系依据' }}</span><strong>{{ graph.source.title }}</strong><small>{{ formatPages(selectedRelation.pages) }}</small></div>
          </template>
          <template v-else-if="selectedNode">
            <span class="detail-kicker">知识点详情 <span>{{ selectedNode.section }}</span></span>
            <h2>{{ selectedNode.label }}</h2>
            <p class="detail-description">{{ selectedNode.description }}</p>
            <div class="source-card"><span>课件整理 · 来源位置</span><strong>{{ graph.source.title }}</strong><small>{{ formatPages(selectedNode.pages) }}</small></div>
          </template>
          <template v-else>
            <span class="detail-kicker">探索本章</span><h2>知识之间，如何相连？</h2>
            <p class="detail-description">点击知识点查看说明，点击连线查看关系依据。也可以从下方关系列表开始探索。</p>
          </template>
          <div class="relation-list-heading"><h3>{{ selectedNodeId ? '相连关系' : '关系列表' }}</h3><span>{{ listedRelations.length }}</span><button v-if="selectedNodeId || selectedRelationId" @click="clearSelection">查看全部</button></div>
          <div class="relation-list">
            <button v-for="edge in listedRelations" :key="edge.id" class="relation-item" :class="{ 'selected-relation': selectedRelationId === edge.id }" :aria-label="`查看关系：${nodeById[edge.source].label} → ${nodeById[edge.target].label}`" @click="selectRelation(edge.id)">
              <span>{{ nodeById[edge.source].label }} <span class="arrow">→</span> {{ nodeById[edge.target].label }}</span>
              <small :class="{ 'learning-text': edge.kind === 'learning' }">{{ edge.kind === 'learning' ? '学习建议' : edge.label }}</small>
            </button>
            <p v-if="!listedRelations.length" class="no-relations">此知识点在当前筛选下没有关系。</p>
          </div>
        </aside>
      </div>
      <footer class="map-footer"><span>依据本章课件整理的核心概念示范，学习顺序建议待教师确认。</span><span>来源位置按 PDF 页码标注 · 原件共 36 页</span></footer>
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Handle, MarkerType, Position, VueFlow, useVueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { chapterGraph as graph, formatPages } from '@/data/machineLearningChapterGraph'

const user = useUserStore()
const route = useRoute()
const isPreview = computed(() => route.meta.chapterGraphPreview === true)
const isTeacher = computed(() => ['teacher', 'admin'].includes(user.roleCode))
const { fitView, zoomIn, zoomOut, setCenter } = useVueFlow({ id: 'chapter-ml' })
const groupLabels = { overview: '章节主题', basic: '基本概念', elements: '学习要素', types: '学习类型' }
const filters = [{ id: 'all', label: '全部关系', aria: '全部关系' }, { id: 'knowledge', label: '知识关系', aria: '只看知识关系' }, { id: 'learning', label: '学习建议', aria: '只看学习建议' }]
const nodeById = Object.fromEntries(graph.nodes.map(node => [node.id, node]))
// Keep positions in mutable view state. Selection and filters never reconstruct the nodes.
const initialNodes = () => graph.nodes.map(node => ({ id: node.id, type: 'concept', position: { ...node.position }, data: { ...node }, deletable: false, connectable: false }))
const nodes = ref(initialNodes())
const query = ref('')
const relationFilter = ref('all')
const selectedNodeId = ref('')
const selectedRelationId = ref('')
const selectedNode = computed(() => nodeById[selectedNodeId.value])
const selectedRelation = computed(() => graph.relations.find(edge => edge.id === selectedRelationId.value))
const matches = computed(() => graph.nodes.filter(node => `${node.label} ${node.section}`.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase())))
const filteredRelations = computed(() => graph.relations.filter(edge => relationFilter.value === 'all' || edge.kind === relationFilter.value))
const listedRelations = computed(() => filteredRelations.value.filter(edge => !selectedNodeId.value || edge.source === selectedNodeId.value || edge.target === selectedNodeId.value))
const relatedIds = computed(() => new Set([selectedNodeId.value, ...listedRelations.value.flatMap(edge => [edge.source, edge.target])]))
const isDimmed = id => Boolean(selectedNodeId.value && !relatedIds.value.has(id))
const edges = computed(() => filteredRelations.value.map(edge => {
  const learning = edge.kind === 'learning'
  const active = selectedRelationId.value ? selectedRelationId.value === edge.id : !selectedNodeId.value || edge.source === selectedNodeId.value || edge.target === selectedNodeId.value
  const color = learning ? '#8b5ab4' : '#7a94b2'
  return { id: edge.id, source: edge.source, target: edge.target, sourceHandle: learning ? 'learn-out' : 'out', targetHandle: learning ? 'learn-in' : 'in', type: 'smoothstep', label: edge.label,
    markerEnd: { type: MarkerType.ArrowClosed, color, width: 16, height: 16 },
    style: { stroke: color, strokeWidth: active ? 1.7 : 1, strokeDasharray: learning ? '5 5' : undefined, opacity: active ? 1 : 0.22 },
    labelStyle: { fill: active ? '#475569' : '#94a3b8', fontSize: 12 }, labelBgStyle: { fill: '#f8fafc', fillOpacity: 0.96 }, labelBgPadding: [5, 3], labelBgBorderRadius: 4, interactionWidth: 24, deletable: false }
}))
function selectConcept(id) { selectedNodeId.value = id; selectedRelationId.value = '' }
function selectRelation(id) { selectedRelationId.value = id; selectedNodeId.value = '' }
function clearSelection() { selectedNodeId.value = ''; selectedRelationId.value = '' }
function changeFilter(id) { relationFilter.value = id; selectedRelationId.value = '' }
function focusConcept(id) {
  selectConcept(id)
  query.value = ''
  const node = nodes.value.find(item => item.id === id)
  if (node) setCenter(node.position.x + 87, node.position.y + 40, { zoom: 1, duration: 250 })
}
async function resetLayout() {
  clearSelection()
  nodes.value = initialNodes()
  await nextTick()
  fitView({ padding: 0.12, duration: 250 })
}
</script>

<style scoped>
.chapter-map { max-width: 1680px; margin: 0 auto; color: #172b42; }
.standalone-preview { padding: 32px 24px; min-height: 100vh; }
.chapter-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 24px; }
.eyebrow { color: #64748b; font-size: 13px; margin-bottom: 12px; }.eyebrow span { margin: 0 10px; color: #c0cad6; }
.heading-line { display: flex; gap: 14px; align-items: center; flex-wrap: wrap; }.heading-line h1 { margin: 0; font-size: 29px; letter-spacing: -0.5px; }
.demo-badge { background: #eaf2ff; color: #3262a5; border-radius: 6px; font-size: 12px; padding: 5px 8px; }
.chapter-heading p { color: #64748b; font-size: 14px; margin: 10px 0 0; }.back-link { color: #426b9f; text-decoration: none; white-space: nowrap; font-size: 13px; }.back-link span { margin-left: 8px; }
.map-shell { background: white; border: 1px solid #dce4ed; border-radius: 14px; overflow: hidden; box-shadow: 0 8px 28px #28466b08; }
.map-topbar { min-height: 76px; display: flex; align-items: center; justify-content: space-between; padding: 16px 22px; gap: 16px; border-bottom: 1px solid #e6ecf3; }
.map-overview { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }.map-overview strong { font-size: 15px; }.map-overview > span:last-child { color: #738398; font-size: 12px; margin-left: 5px; }.status-dot { width: 7px; height: 7px; border-radius: 50%; background: #3e8b83; }
.search-box { display: flex; align-items: center; position: relative; gap: 8px; width: 280px; min-width: 0; border: 1px solid #dce4ed; border-radius: 8px; padding: 9px 12px; color: #8494a6; background: #f9fbfd; }.search-box:focus-within { border-color: #6994c4; }.search-box input { width: 100%; min-width: 0; background: transparent; border: 0; outline: 0; font-size: 13px; color: #172b42; }.clear-search { background: none; border: 0; cursor: pointer; }
.search-results { position: absolute; right: 0; left: 0; top: calc(100% + 8px); background: white; box-shadow: 0 8px 28px #16345720; border: 1px solid #dce4ed; border-radius: 8px; z-index: 15; max-height: 300px; overflow-y: auto; }.search-results button { width: 100%; display: flex; flex-direction: column; gap: 5px; padding: 12px; text-align: left; background: white; border: 0; cursor: pointer; color: #172b42; }.search-results button:hover { background: #f0f6fc; }.search-results small { color: #78879b; font-size: 11px; }.search-results p { font-size: 13px; padding: 8px 12px; }
.map-body { display: grid; grid-template-columns: minmax(0, 1fr) 294px; }.canvas-column { min-width: 0; background: #f8fafc; }.relation-filters { display: flex; align-items: center; gap: 5px; padding: 14px 18px; border-bottom: 1px solid #ecf0f5; }.relation-filters button { border: 1px solid transparent; border-radius: 6px; color: #64748b; padding: 6px 11px; background: transparent; cursor: pointer; font-size: 12px; white-space: nowrap; }.relation-filters button.active { color: #315b8d; border-color: #dce6f3; background: white; box-shadow: 0 1px 3px #33415508; }.canvas-hint { margin-left: auto; font-size: 11px; color: #8796a8; }
.graph-canvas { height: 650px; position: relative; background-image: radial-gradient(#d9e2ee 0.8px, transparent 0.8px); background-size: 18px 18px; }.canvas-controls { position: absolute; bottom: 16px; left: 18px; z-index: 5; display: flex; align-items: center; border: 1px solid #dce4ed; background: white; padding: 4px; border-radius: 8px; box-shadow: 0 3px 12px #23405b0a; }.canvas-controls button { border: 0; background: none; color: #465b73; padding: 7px 9px; font-size: 12px; cursor: pointer; border-radius: 4px; }.canvas-controls button:hover { background: #edf3fa; }.control-divider { height: 18px; width: 1px; background: #e5eaf0; margin: 0 4px; }
.concept-card { width: 174px; min-height: 80px; padding: 11px 14px; border: 1px solid #cbd9e9; border-left: 3px solid #7099c7; border-radius: 9px; background: #fff; box-shadow: 0 3px 8px #20456809; text-align: left; transition: opacity 0.15s, box-shadow 0.15s; cursor: grab; }.concept-card:active { cursor: grabbing; }.concept-kind { display: block; font-size: 10px; color: #72859b; margin-bottom: 5px; }.concept-card strong { display: block; font-size: 16px; line-height: 1.4; color: #263e59; }.concept-page { display: block; font-size: 10px; color: #8695a6; margin-top: 5px; }.group-elements { border-left-color: #4c9a94; background: #fcfefe; }.group-types { border-left-color: #d8aa59; background: #fffefa; }.group-overview { background: #244c76; border-color: #244c76; }.group-overview strong { color: white; }.group-overview .concept-kind, .group-overview .concept-page { color: #c7d9ed; }.concept-card.chosen { outline: 2px solid #6e9dd0; outline-offset: 3px; box-shadow: 0 3px 14px #305b8d20; }.concept-card.faded { opacity: 0.4; }.concept-card:focus-visible { outline: 3px solid #729cd0; outline-offset: 3px; }
:deep(.vue-flow__handle) { width: 5px; height: 5px; min-width: 5px; min-height: 5px; background: #94abc2; border: 1px solid white; pointer-events: none; }:deep(.vue-flow__node) { border: none; padding: 0; background: none; }:deep(.vue-flow__edge) { cursor: pointer; }
.graph-legend { display: flex; align-items: center; gap: 20px; padding: 13px 18px; font-size: 11px; color: #6f8196; border-top: 1px solid #e9eef4; }.graph-legend > span { display: flex; align-items: center; gap: 7px; }.line-solid, .line-dashed { width: 22px; border-top: 2px solid #7a94b2; }.line-dashed { border-top: 2px dashed #8b5ab4; }.legend-tip { margin-left: auto; }
.detail-panel { border-left: 1px solid #e5ebf3; padding: 24px 20px; min-width: 0; }.detail-kicker { font-size: 11px; color: #6e8197; display: flex; flex-wrap: wrap; gap: 7px; }.detail-kicker > span { color: #96a2b2; }.detail-panel h2 { font-size: 21px; line-height: 1.5; margin: 14px 0 12px; }.detail-description { line-height: 1.9; font-size: 13px; color: #607188; margin: 14px 0 18px; }.source-card { display: flex; flex-direction: column; gap: 8px; border: 1px solid #e5ebf3; border-radius: 8px; background: #f8fafd; padding: 12px; }.source-card span { font-size: 10px; color: #7c8b9e; }.source-card strong { font-size: 12px; line-height: 1.7; font-weight: 500; overflow-wrap: anywhere; }.source-card small { color: #3c709e; font-size: 12px; }
.relation-list-heading { margin-top: 26px; display: flex; align-items: center; gap: 7px; }.relation-list-heading h3 { font-size: 12px; margin: 0; font-weight: 600; }.relation-list-heading > span { font-size: 11px; color: #7d8ea3; }.relation-list-heading button { margin-left: auto; background: none; border: 0; color: #527caf; font-size: 11px; cursor: pointer; }.relation-list { max-height: 320px; overflow-y: auto; margin-top: 12px; }.relation-item { display: flex; flex-direction: column; gap: 7px; width: 100%; text-align: left; padding: 11px 8px; border: 0; border-bottom: 1px solid #edf1f6; background: none; color: #475d76; font-size: 12px; cursor: pointer; }.relation-item:hover, .selected-relation { background: #f2f6fc; }.relation-item small { color: #8b99aa; font-size: 10px; }.relation-item .learning-text { color: #9267b2; }.arrow { color: #9aabba; margin: 0 3px; }.no-relations { color: #8191a4; font-size: 12px; }.relation-title { display: flex; flex-direction: column; gap: 8px; margin: 18px 0; }.relation-title button { text-align: left; font-size: 18px; color: #274969; padding: 0; background: none; border: none; cursor: pointer; }.relation-title > span { color: #9baabd; }.kind-tag { display: inline-block; font-size: 10px; padding: 5px 7px; color: #4b7290; background: #eef4fa; border-radius: 4px; }.kind-tag.learning { color: #8560a5; background: #f4eff9; }
.map-footer { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; padding: 14px 22px; font-size: 11px; line-height: 1.7; color: #8491a4; border-top: 1px solid #e5ebf3; }
button:focus-visible, .back-link:focus-visible { outline: 2px solid #719bc8; outline-offset: 2px; }
@media (max-width: 1200px) { .map-body { grid-template-columns: minmax(0, 1fr) 260px; }.detail-panel { padding: 20px 16px; }.canvas-hint, .legend-tip { display: none !important; } }
@media (max-width: 900px) { .map-body { grid-template-columns: 1fr; }.detail-panel { border-left: 0; border-top: 1px solid #e5ebf3; }.graph-canvas { height: 580px; }.relation-list { max-height: 220px; } }
@media (max-width: 600px) { .chapter-heading { align-items: flex-start; flex-direction: column; gap: 14px; }.heading-line h1 { font-size: 25px; }.map-topbar { align-items: stretch; flex-direction: column; padding: 14px; gap: 14px; }.search-box { width: 100%; }.map-overview { gap: 6px; }.map-overview > span:last-child { font-size: 11px; }.relation-filters { padding: 12px 10px; gap: 2px; }.relation-filters button { padding: 6px 10px; }.graph-canvas { height: 460px; }.graph-legend { padding: 12px 10px; gap: 12px; flex-wrap: wrap; }.canvas-controls { left: 10px; bottom: 12px; }.canvas-controls button { padding: 7px; font-size: 11px; }.map-footer { padding: 12px 14px; }.chapter-heading p { font-size: 13px; } }
@media (prefers-reduced-motion: reduce) { .concept-card { transition: none; } }
@media (max-width: 600px) { .standalone-preview { padding: 24px 12px; } }
</style>
