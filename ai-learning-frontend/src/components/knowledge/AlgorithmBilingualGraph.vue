<template>
  <div class="algo-graph-card">
    <!-- header -->
    <div class="algo-graph-head">
      <span class="algo-graph-title">算法双语知识图谱</span>
      <div class="algo-graph-tags">
        <el-tag type="primary" effect="light" size="small">双语</el-tag>
        <el-tag effect="plain" size="small">{{ ALGO_DATA.nodes.length }} 节点</el-tag>
        <el-tag effect="plain" size="small">{{ ALGO_DATA.relations.length }} 关系</el-tag>
      </div>
    </div>

    <!-- toolbar -->
    <div class="algo-graph-toolbar">
      <div class="toolbar-left">
        <el-radio-group v-model="langMode" size="small" @change="rebuildFlow">
          <el-radio-button value="zh">中文</el-radio-button>
          <el-radio-button value="en">English</el-radio-button>
          <el-radio-button value="bilingual">双语</el-radio-button>
        </el-radio-group>
        <el-select v-model="layoutMode" size="small" style="width:110px" @change="applyLayout">
          <el-option label="网格布局" value="grid" />
          <el-option label="力导向" value="force" />
          <el-option label="环形布局" value="circle" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-input v-model="searchText" size="small" placeholder="搜索节点…" clearable style="width:160px"
          :prefix-icon="Search" @input="onSearch" />
        <el-button size="small" @click="resetView">重置视图</el-button>
      </div>
    </div>

    <!-- legend -->
    <div class="legend-bar">
      <span v-for="(color, cat) in CATEGORY_COLORS" :key="cat" class="legend-chip"
        :style="{ background: color }" @click="filterByCategory(cat)">
        {{ cat }}
      </span>
      <el-button v-if="activeCategory" size="small" link @click="activeCategory = ''; rebuildFlow()">
        清除筛选
      </el-button>
    </div>

    <!-- graph area + sidebar -->
    <div class="graph-with-sidebar">
      <div ref="stageRef" class="algo-graph-stage">
        <VueFlow
          v-if="mounted"
          ref="vueFlowRef"
          :nodes="flowNodes"
          :edges="flowEdges"
          class="algo-graph-flow"
          :min-zoom="0.2"
          :max-zoom="2.5"
          :nodes-draggable="true"
          :elements-selectable="true"
          :default-viewport="{ x: 0, y: 0, zoom: 1 }"
          @node-click="handleNodeClick"
          @pane-click="selectedNodeId = ''"
          @pane-ready="onPaneReady"
        >
          <template #node-default="nodeProps">
            <div class="custom-node" :class="{ highlighted: nodeProps.id === selectedNodeId }"
              :style="nodeStyle(nodeProps.id)">
              {{ nodeProps.label }}
            </div>
          </template>
          <template #edge-custom="edgeProps">
            <BaseEdge :path="edgeProps.path" :style="edgeProps.style" />
          </template>
        </VueFlow>
      </div>

      <aside class="graph-sidebar" v-if="sidebarNode">
        <div class="sidebar-head">
          <h4>{{ sidebarNode.labelZh }}</h4>
          <el-button link @click="sidebarNode = null"><el-icon><Close /></el-icon></el-button>
        </div>
        <div class="sidebar-body">
          <div class="sidebar-field">
            <span class="sf-key">English</span>
            <span class="sf-val">{{ sidebarNode.labelEn }}</span>
          </div>
          <div class="sidebar-field" v-if="sidebarNode.category">
            <span class="sf-key">分类</span>
            <el-tag size="small" :color="CATEGORY_COLORS[sidebarNode.category]" effect="dark">
              {{ sidebarNode.category }}
            </el-tag>
          </div>
          <div class="sidebar-field" v-if="sidebarNode.complexity">
            <span class="sf-key">复杂度</span>
            <el-tag size="small" effect="plain">{{ sidebarNode.complexity }}</el-tag>
          </div>
          <div class="sidebar-field">
            <span class="sf-key">关联节点</span>
            <span class="sf-val">{{ relatedNodes.length }} 个</span>
          </div>

          <div class="sidebar-rel" v-if="relatedNodes.length">
            <div class="sf-key" style="margin-bottom:6px">关联关系</div>
            <div v-for="r in relatedEdges" :key="r.id" class="rel-chip"
              @click="highlightPath(r)">
              <span>{{ findNodeLabel(r.source) }}</span>
              <el-tag size="small" type="primary" effect="plain">{{ r.label }}</el-tag>
              <span>{{ findNodeLabel(r.target) }}</span>
            </div>
          </div>

          <div class="sidebar-desc" v-if="sidebarNode.descriptionZh">
            <div class="sf-key">中文描述</div>
            <p>{{ sidebarNode.descriptionZh }}</p>
          </div>
          <div class="sidebar-desc" v-if="sidebarNode.descriptionEn && langMode !== 'zh'">
            <div class="sf-key">English Description</div>
            <p>{{ sidebarNode.descriptionEn }}</p>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch, shallowRef } from 'vue'
import { VueFlow, BaseEdge } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { Search, Close } from '@element-plus/icons-vue'

const langMode = ref('bilingual')
const layoutMode = ref('grid')
const searchText = ref('')
const activeCategory = ref('')
const mounted = ref(false)
const vueFlowRef = shallowRef(null)
const stageRef = ref(null)
const selectedNodeId = ref('')
const sidebarNode = ref(null)
const highlightEdges = ref([])

const CATEGORY_COLORS = {
  '基础算法': '#3b82f6',
  '排序': '#8b5cf6',
  '搜索': '#06b6d4',
  '图算法': '#f59e0b',
  '高级算法': '#ef4444',
  '动态规划': '#ec4899',
  '算法策略': '#10b981',
  '贪心': '#84cc16',
  '数据结构': '#6366f1',
  '树': '#14b8a6',
}

const ALGO_DATA = {
  nodes: [
    { id: 'sorting', labelZh: '排序', labelEn: 'Sorting', category: '基础算法', complexity: 'O(n log n)', descriptionZh: '将数据按特定顺序排列的算法族，是计算机科学中最基础的问题之一。', descriptionEn: 'Algorithms that arrange data in a particular order. Core to computer science.' },
    { id: 'quick_sort', labelZh: '快速排序', labelEn: 'Quick Sort', category: '排序', complexity: 'O(n log n)', descriptionZh: '分治策略的代表算法。选取基准元素，将数组分为小于和大于基准的两部分，递归排序。实际应用中常优于其他O(n log n)算法。', descriptionEn: 'Divide-and-conquer classic. Pick a pivot and partition the array, then recurse. Often the fastest in practice.' },
    { id: 'merge_sort', labelZh: '归并排序', labelEn: 'Merge Sort', category: '排序', complexity: 'O(n log n)', descriptionZh: '稳定排序算法，将数组递归对半拆分到单元素，再两两合并。适合外部排序和链表排序。', descriptionEn: 'Stable sort. Recursively split arrays in half, then merge back. Great for external sorting and linked lists.' },
    { id: 'heap_sort', labelZh: '堆排序', labelEn: 'Heap Sort', category: '排序', complexity: 'O(n log n)', descriptionZh: '利用最大堆数据结构的选择排序。先建堆O(n)，再依次弹出堆顶O(n log n)。原地排序，不稳定。', descriptionEn: 'Selection sort using max-heap. Build heap in O(n), then extract max n times. In-place, not stable.' },
    { id: 'bubble_sort', labelZh: '冒泡排序', labelEn: 'Bubble Sort', category: '排序', complexity: 'O(n²)', descriptionZh: '最简单的排序算法之一。相邻元素两两比较，大的往后沉。教学意义大于实际价值。', descriptionEn: 'Simplest sort. Compare and swap adjacent elements. More educational than practical.' },
    { id: 'searching', labelZh: '搜索', labelEn: 'Searching', category: '基础算法', descriptionZh: '在数据结构中查找目标元素。不同数据结构适用不同搜索策略。', descriptionEn: 'Finding target elements in data structures. Strategy depends on data structure.' },
    { id: 'binary_search', labelZh: '二分查找', labelEn: 'Binary Search', category: '搜索', complexity: 'O(log n)', descriptionZh: '在有序数组中每次折半排除一半元素。对数时间复杂度，是效率最高的查找算法之一。前提是数据有序。', descriptionEn: 'Halve the search space each step in a sorted array. O(log n). Requires sorted data.' },
    { id: 'bfs', labelZh: '广度优先搜索', labelEn: 'BFS', category: '搜索', complexity: 'O(V+E)', descriptionZh: '使用队列逐层遍历图或树。天然适合找最短路径（无权图）、层级遍历等场景。', descriptionEn: 'Queue-based level-order traversal. Best for shortest paths in unweighted graphs.' },
    { id: 'dfs', labelZh: '深度优先搜索', labelEn: 'DFS', category: '搜索', complexity: 'O(V+E)', descriptionZh: '使用栈（递归）沿分支走到底再回溯。适合拓扑排序、连通分量、迷宫求解等。', descriptionEn: 'Stack-based deep exploration. Ideal for topological sort, connected components, maze solving.' },
    { id: 'graph', labelZh: '图算法', labelEn: 'Graph Algorithms', category: '高级算法', descriptionZh: '图是描述元素间关系的强大模型。涵盖最短路、最小生成树、网络流等核心问题。', descriptionEn: 'Powerful model for relationships. Covers shortest paths, MST, network flow, etc.' },
    { id: 'dijkstra', labelZh: 'Dijkstra 最短路径', labelEn: "Dijkstra's Algorithm", category: '图算法', complexity: 'O(V²)', descriptionZh: '单源最短路径贪心算法。每次选距离最小的未处理节点，用优先队列优化可达O(E log V)。加权图最核心算法之一。', descriptionEn: 'Greedy single-source shortest path. Use a priority queue for O(E log V). Fundamental for weighted graphs.' },
    { id: 'kruskal', labelZh: 'Kruskal 最小生成树', labelEn: 'Kruskal MST', category: '图算法', complexity: 'O(E log E)', descriptionZh: '边排序后贪心选取，用并查集(DSU)判环。与Prim算法同为MST经典算法，适合边稀疏的图。', descriptionEn: 'Sort edges, pick greedily with DSU for cycle detection. Best for sparse graphs.' },
    { id: 'prim', labelZh: 'Prim 最小生成树', labelEn: "Prim's Algorithm", category: '图算法', complexity: 'O(E log V)', descriptionZh: '从任意节点出发，每次将最小权边加入生成树。适合边稠密的图，与Dijkstra代码结构相似。', descriptionEn: 'Grow MST from a starting node by adding the minimum-weight edge. Similar code structure to Dijkstra.' },
    { id: 'dp', labelZh: '动态规划', labelEn: 'Dynamic Programming', category: '高级算法', descriptionZh: '通过把原问题分解为重叠子问题求解。核心要素：最优子结构、状态转移方程、记忆化。算法竞赛和面试的常客。', descriptionEn: 'Break problem into overlapping subproblems. Key: optimal substructure, state transition, memoization.' },
    { id: 'knapsack', labelZh: '背包问题', labelEn: 'Knapsack Problem', category: '动态规划', complexity: 'O(nW)', descriptionZh: 'DP经典问题。在容量W限制下选物品最大化总价值。0-1背包/完全背包/多重背包是重要变体。', descriptionEn: 'Classic DP. Maximize value under capacity W. Variants: 0-1, unbounded, multiple knapsacks.' },
    { id: 'lcs', labelZh: '最长公共子序列', labelEn: 'LCS', category: '动态规划', complexity: 'O(mn)', descriptionZh: '求两个序列的最长公共子序列。DP表格填充后回溯。是序列比对(如diff工具)的基础。', descriptionEn: 'DP table fill + backtrack for longest common subsequence. Foundation of diff tools and bioinformatics.' },
    { id: 'greedy', labelZh: '贪心算法', labelEn: 'Greedy Algorithms', category: '算法策略', descriptionZh: '每步做当前看起来最优的选择。不保证全局最优，但某些问题(如MST、Huffman)中贪心即最优。', descriptionEn: 'Choose the locally optimal step. Works globally for problems with matroid structure (MST, Huffman).' },
    { id: 'huffman', labelZh: '哈夫曼编码', labelEn: 'Huffman Coding', category: '贪心', complexity: 'O(n log n)', descriptionZh: '最优前缀编码，贪心构建二叉树：频率低的离根远。ZIP/GZIP/JPEG/PNG等格式的核心压缩技术。', descriptionEn: 'Optimal prefix coding. Greedy binary tree: low-frequency symbols far from root. Core of ZIP/JPEG/PNG.' },
    { id: 'divide_conquer', labelZh: '分治法', labelEn: 'Divide & Conquer', category: '算法策略', descriptionZh: '将原问题分解为多个独立子问题，分别求解后合并。归并排序、快速排序、二分查找均属此类。', descriptionEn: 'Split into independent subproblems, solve, combine. Merge sort, quick sort, binary search.' },
    { id: 'backtracking', labelZh: '回溯法', labelEn: 'Backtracking', category: '算法策略', descriptionZh: '系统搜索所有可能解，发现不满足约束时回退。N皇后、数独、子集和等问题的标准解法。', descriptionEn: 'Systematic trial: explore branches and backtrack on failure. Standard for N-Queens, Sudoku, subset sum.' },
    { id: 'tree', labelZh: '树', labelEn: 'Tree', category: '数据结构', descriptionZh: 'n个节点的n-1条边连通无环图。根、叶、深度、高度是基本概念。文件系统、DOM、数据库索引都用树。', descriptionEn: 'Connected acyclic graph with n-1 edges. File systems, DOM, DB indexes all use trees.' },
    { id: 'bst', labelZh: '二叉搜索树', labelEn: 'BST', category: '树', complexity: 'O(log n)', descriptionZh: '左子树<根<右子树。查找/插入/删除平均O(log n)，最坏O(n)。平衡是核心问题。', descriptionEn: 'Left < root < right. Search/insert/delete avg O(log n). The core challenge is maintaining balance.' },
    { id: 'avl', labelZh: 'AVL 树', labelEn: 'AVL Tree', category: '树', complexity: 'O(log n)', descriptionZh: '首个自平衡BST。每个节点维护平衡因子(-1/0/1)，失衡时旋转修复。查找快于红黑树，插入稍慢。', descriptionEn: 'First self-balancing BST. Balance factor -1/0/1 per node, rotate on imbalance. Faster lookup than Red-Black.' },
    { id: 'hash', labelZh: '哈希表', labelEn: 'Hash Table', category: '数据结构', complexity: 'O(1)', descriptionZh: '通过哈希函数将键映射到桶。平均O(1)增删查。冲突解决：拉链法/开放寻址法。数据库、缓存、去重的基础结构。', descriptionEn: 'Map keys to buckets via hash function. Average O(1) ops. Chaining or open addressing for collisions.' },
    { id: 'heap', labelZh: '堆', labelEn: 'Heap', category: '数据结构', complexity: 'O(log n)', descriptionZh: '完全二叉树实现的优先队列。父节点总大(小)于子节点。Top-K、Dijkstra、Huffman都依赖堆。', descriptionEn: 'Complete binary tree priority queue. Parent always larger/smaller than children. Powers Top-K, Dijkstra.' },
  ],
  relations: [
    { source: 'sorting', target: 'quick_sort', label: '包含' },
    { source: 'sorting', target: 'merge_sort', label: '包含' },
    { source: 'sorting', target: 'heap_sort', label: '包含' },
    { source: 'sorting', target: 'bubble_sort', label: '包含' },
    { source: 'searching', target: 'binary_search', label: '包含' },
    { source: 'searching', target: 'bfs', label: '包含' },
    { source: 'searching', target: 'dfs', label: '包含' },
    { source: 'graph', target: 'dijkstra', label: '包含' },
    { source: 'graph', target: 'kruskal', label: '包含' },
    { source: 'graph', target: 'prim', label: '包含' },
    { source: 'graph', target: 'bfs', label: '算法' },
    { source: 'graph', target: 'dfs', label: '算法' },
    { source: 'dp', target: 'knapsack', label: '经典问题' },
    { source: 'dp', target: 'lcs', label: '经典问题' },
    { source: 'greedy', target: 'huffman', label: '应用' },
    { source: 'greedy', target: 'dijkstra', label: '应用' },
    { source: 'greedy', target: 'kruskal', label: '应用' },
    { source: 'tree', target: 'bst', label: '子类' },
    { source: 'tree', target: 'avl', label: '子类' },
    { source: 'tree', target: 'heap', label: '子类' },
    { source: 'divide_conquer', target: 'quick_sort', label: '实例' },
    { source: 'divide_conquer', target: 'merge_sort', label: '实例' },
    { source: 'divide_conquer', target: 'binary_search', label: '实例' },
    { source: 'backtracking', target: 'dfs', label: '关联' },
    { source: 'heap', target: 'heap_sort', label: '应用' },
    { source: 'heap', target: 'dijkstra', label: '优化' },
    { source: 'sorting', target: 'divide_conquer', label: '策略' },
    { source: 'dp', target: 'divide_conquer', label: '相似' },
    { source: 'dp', target: 'greedy', label: '对比' },
  ]
}

// ---- computed ----
function findNodeLabel(id) {
  const n = ALGO_DATA.nodes.find(x => x.id === id)
  return n ? buildSingleLabel(n) : id
}
function buildSingleLabel(n) {
  if (langMode.value === 'en') return n.labelEn
  if (langMode.value === 'bilingual') return `${n.labelZh}/${n.labelEn}`
  return n.labelZh
}

const filteredNodes = computed(() => {
  let nodes = ALGO_DATA.nodes
  if (activeCategory.value) nodes = nodes.filter(n => n.category === activeCategory.value)
  if (searchText.value.trim()) {
    const kw = searchText.value.trim().toLowerCase()
    nodes = nodes.filter(n =>
      n.labelZh.toLowerCase().includes(kw) ||
      n.labelEn.toLowerCase().includes(kw) ||
      n.id.toLowerCase().includes(kw)
    )
  }
  return nodes
})

const filteredRelations = computed(() => {
  const ids = new Set(filteredNodes.value.map(n => n.id))
  return ALGO_DATA.relations.filter(r => ids.has(r.source) && ids.has(r.target))
})

const relatedEdges = computed(() => {
  if (!selectedNodeId.value) return []
  return ALGO_DATA.relations.filter(r => r.source === selectedNodeId.value || r.target === selectedNodeId.value)
})
const relatedNodes = computed(() => {
  const set = new Set()
  relatedEdges.value.forEach(r => {
    if (r.source !== selectedNodeId.value) set.add(r.source)
    if (r.target !== selectedNodeId.value) set.add(r.target)
  })
  return [...set].map(id => ALGO_DATA.nodes.find(n => n.id === id)).filter(Boolean)
})

// ---- flow builders ----
function nodeDegree(id) {
  return ALGO_DATA.relations.filter(r => r.source === id || r.target === id).length
}

function buildFlowNodes() {
  const pos = computePositions(filteredNodes.value.length)
  const maxDeg = Math.max(...filteredNodes.value.map(n => nodeDegree(n.id)), 1)
  return filteredNodes.value.map((node, i) => {
    const deg = nodeDegree(node.id)
    const size = 44 + Math.round((deg / maxDeg) * 28) // 44-72px based on degree
    const color = CATEGORY_COLORS[node.category] || '#6366f1'
    const isSearchHit = searchText.value.trim() && (
      node.labelZh.includes(searchText.value.trim()) ||
      node.labelEn.toLowerCase().includes(searchText.value.trim().toLowerCase())
    )
    return {
      id: node.id,
      type: 'default',
      data: { raw: node, category: node.category, degree: deg },
      label: buildSingleLabel(node),
      position: pos[i],
      style: {
        width: `${size}px`, height: `${size}px`,
        background: color,
        color: '#fff', fontSize: '10px', fontWeight: '700',
        border: isSearchHit ? '3px solid #fbbf24' : `2px solid ${color}`,
        borderRadius: '50%',
        boxShadow: isSearchHit ? `0 0 14px ${color}, 0 0 6px #fbbf24` : `0 3px 8px ${color}44`,
        cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
        textAlign: 'center', zIndex: isSearchHit ? 20 : 1,
      },
    }
  })
}

function computePositions(count) {
  switch (layoutMode.value) {
    case 'circle': return circleLayout(count)
    case 'force': return forceLayout(count)
    default: return gridLayout(count)
  }
}
function gridLayout(count) {
  const cols = Math.max(3, Math.ceil(Math.sqrt(count * 1.6)))
  const sx = 145, sy = 110
  return Array.from({ length: count }, (_, i) => ({
    x: (i % cols) * sx + 30,
    y: Math.floor(i / cols) * sy + 30,
  }))
}
function circleLayout(count) {
  const cx = 580, cy = 420, r = Math.min(380, 80 + count * 14)
  return Array.from({ length: count }, (_, i) => {
    const angle = (Math.PI * 2 * i) / count - Math.PI / 2
    return { x: cx + r * Math.cos(angle), y: cy + r * Math.sin(angle) }
  })
}
function forceLayout(count) {
  const cx = 580, cy = 420
  const pos = gridLayout(count)
  // simple simulated annealing toward center with repulsion
  for (let iter = 0; iter < 12; iter++) {
    for (let i = 0; i < count; i++) {
      let fx = 0, fy = 0
      for (let j = 0; j < count; j++) {
        if (i === j) continue
        const dx = pos[i].x - pos[j].x
        const dy = pos[i].y - pos[j].y
        const d = Math.max(30, Math.sqrt(dx * dx + dy * dy))
        const rep = 2800 / (d * d)
        fx += (dx / d) * rep
        fy += (dy / d) * rep
      }
      // attract toward center
      fx += (cx - pos[i].x) * 0.01
      fy += (cy - pos[i].y) * 0.01
      // damp
      pos[i].x += fx * 0.25
      pos[i].y += fy * 0.25
    }
  }
  return pos
}

function buildFlowEdges() {
  const hl = new Set(highlightEdges.value)
  return filteredRelations.value.map((r, i) => {
    const isHL = hl.has(`algo-edge-${i}`)
    return {
      id: `algo-edge-${i}`,
      source: r.source, target: r.target,
      label: langMode.value === 'en' ? '' : r.label,
      animated: isHL,
      style: { stroke: isHL ? '#3b82f6' : '#c8d2e0', strokeWidth: isHL ? 2.5 : 1.2 },
      labelStyle: { fill: '#94a3b8', fontSize: '10px', fontWeight: '500' },
      labelBgStyle: { fill: '#fff', fillOpacity: 0.88 },
      markerEnd: { type: 'arrowclosed', color: isHL ? '#3b82f6' : '#c8d2e0', width: 14, height: 14 },
    }
  })
}

const flowNodes = ref([])
const flowEdges = ref([])

function rebuildFlow() {
  flowNodes.value = buildFlowNodes()
  flowEdges.value = buildFlowEdges()
  highlightEdges.value = []
  nextTick(() => {
    setTimeout(() => vueFlowRef.value?.fitView({ padding: 0.2, duration: 400 }), 150)
  })
}

function applyLayout() { rebuildFlow() }

function onSearch() {
  activeCategory.value = ''
  rebuildFlow()
}
function filterByCategory(cat) {
  activeCategory.value = activeCategory.value === cat ? '' : cat
  searchText.value = ''
  rebuildFlow()
}

function handleNodeClick({ node }) {
  selectedNodeId.value = node.id
  const raw = node.data?.raw || ALGO_DATA.nodes.find(n => n.id === node.id)
  sidebarNode.value = raw ? { ...raw } : null
}

function highlightPath(rel) {
  highlightEdges.value = [`algo-edge-${ALGO_DATA.relations.findIndex(r => r.source === rel.source && r.target === rel.target)}`]
  flowEdges.value = buildFlowEdges()
}

function onPaneReady() {
  nextTick(() => setTimeout(() => vueFlowRef.value?.fitView({ padding: 0.2 }), 80))
}
function resetView() {
  vueFlowRef.value?.fitView({ padding: 0.2, duration: 400 })
}

onMounted(() => {
  mounted.value = true
  nextTick(() => { flowNodes.value = buildFlowNodes(); flowEdges.value = buildFlowEdges() })
  nextTick(() => setTimeout(() => vueFlowRef.value?.fitView({ padding: 0.2 }), 300))
})

watch(langMode, rebuildFlow)
</script>

<style>
.algo-graph-stage .vue-flow__node {
  transition: transform .15s ease, box-shadow .2s ease;
}
.algo-graph-stage .vue-flow__node:hover {
  transform: scale(1.1);
  z-index: 20;
}
.algo-graph-stage .vue-flow__node.selected {
  box-shadow: 0 0 0 4px rgba(59,130,246,0.35) !important;
}
.algo-graph-stage .vue-flow__edge.animated .vue-flow__edge-path {
  stroke-dasharray: 5;
  animation: dashdraw .6s linear infinite;
}
@keyframes dashdraw {
  to { stroke-dashoffset: -10; }
}
</style>

<style scoped>
.algo-graph-card {
  display: flex; flex-direction: column; gap: 10px;
  min-height: 500px;
}
.algo-graph-head {
  display: flex; align-items: center; justify-content: space-between;
  gap: 12px; flex-wrap: wrap; flex-shrink: 0;
}
.algo-graph-title { font-size: 14px; font-weight: 700; color: #1f2937; }
.algo-graph-tags { display: flex; align-items: center; gap: 6px; }

.algo-graph-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; flex-shrink: 0; flex-wrap: wrap;
}
.toolbar-left, .toolbar-right { display: flex; align-items: center; gap: 8px; }

.legend-bar {
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
  padding: 4px 0; flex-shrink: 0;
}
.legend-chip {
  padding: 3px 10px; border-radius: 12px; color: #fff;
  font-size: 11px; font-weight: 600; cursor: pointer;
  transition: all .15s; white-space: nowrap;
}
.legend-chip:hover { transform: scale(1.08); opacity: 0.9; }
.legend-chip:active { transform: scale(0.95); }

.graph-with-sidebar {
  display: flex; gap: 0; flex: 1; min-height: 420px;
}
.algo-graph-stage {
  flex: 1; min-height: 420px; border-radius: 12px;
  overflow: hidden;
  background: linear-gradient(135deg, #f8faff 0%, #eff2f9 100%);
  border: 1px solid #e0e4ef;
}
.algo-graph-flow { width: 100%; height: 100%; }

.graph-sidebar {
  width: 260px; flex-shrink: 0;
  border: 1px solid #e0e4ef; border-left: none;
  border-radius: 0 12px 12px 0;
  background: #fafbfd;
  display: flex; flex-direction: column;
  max-height: 420px; overflow-y: auto;
}
.sidebar-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 14px; border-bottom: 1px solid #e0e4ef;
  position: sticky; top: 0; background: #fafbfd; z-index: 5;
}
.sidebar-head h4 { margin: 0; font-size: 14px; color: #1f2937; }
.sidebar-body { padding: 12px 14px; display: flex; flex-direction: column; gap: 10px; }
.sidebar-field { display: flex; gap: 10px; align-items: baseline; }
.sf-key { width: 64px; flex-shrink: 0; font-size: 11px; color: #909399; }
.sf-val { font-size: 13px; color: #303133; font-weight: 500; }
.sidebar-rel { }
.rel-chip {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 10px; margin-bottom: 4px;
  border-radius: 8px; border: 1px solid #e0e4ef;
  background: #fff; cursor: pointer; transition: all .15s;
  font-size: 12px; color: #303133;
}
.rel-chip:hover { border-color: #3b82f6; background: #eff6ff; }
.sidebar-desc { margin-top: 4px; }
.sidebar-desc p { margin: 4px 0 0; font-size: 12px; color: #606266; line-height: 1.7; }

@media (max-width: 1100px) {
  .graph-with-sidebar { flex-direction: column; }
  .graph-sidebar { width: 100%; border-left: 1px solid #e0e4ef; border-top: none; border-radius: 0 0 12px 12px; max-height: 260px; }
}
</style>
