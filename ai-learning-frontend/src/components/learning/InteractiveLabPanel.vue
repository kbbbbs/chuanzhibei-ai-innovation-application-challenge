<template>
  <el-card class="interactive-lab-card">
    <template #header>
      <div class="card-header">
        <span>互动实验与闯关</span>
        <el-tag type="primary">{{ visibleExperiments.length + visibleChallenges.length }} 个任务</el-tag>
      </div>
    </template>

    <el-empty v-if="!visibleExperiments.length && !visibleChallenges.length" description="本节暂无实验，完成课本和代码任务即可" />

    <div v-for="experiment in visibleExperiments" :key="experiment.id" class="experiment-block">
      <h3>{{ experiment.title }}</h3>
      <p>{{ experiment.description }}</p>

      <div v-if="experiment.kind === 'gradient'" class="gradient-lab">
        <div class="lab-row">
          <span>学习率：{{ learningRate }}</span>
          <el-tag :type="learningRate > 1 ? 'danger' : 'success'">{{ learningRate > 1 ? '震荡风险' : '稳定观察' }}</el-tag>
        </div>
        <el-slider v-model="learningRate" :min="0.01" :max="1.2" :step="0.01" />
        <svg viewBox="0 0 420 180" class="curve-svg" role="img" aria-label="梯度下降曲线实验">
          <polyline :points="curvePoints" fill="none" stroke="#94a3b8" stroke-width="2" />
          <polyline :points="descentPoints" fill="none" stroke="var(--accent)" stroke-width="3" />
          <circle v-for="p in descentDots" :key="p" :cx="p.split(',')[0]" :cy="p.split(',')[1]" r="4" fill="#f59e0b" />
        </svg>
      </div>

      <div v-else-if="experiment.kind === 'classification'" class="classification-lab">
        <div class="lab-row">
          <span>分类边界：{{ boundary }}</span>
          <el-tag type="success">正确 {{ correctCount }}/6</el-tag>
        </div>
        <el-slider v-model="boundary" :min="0" :max="1" :step="0.01" />
        <div class="class-board">
          <span v-for="point in points" :key="point.x + point.type" class="point" :class="point.type" :style="{ left: `${point.x * 100}%`, top: `${point.y * 100}%` }" />
          <span class="boundary-line" :style="{ left: `${boundary * 100}%` }" />
        </div>
      </div>

      <div v-else-if="experiment.kind === 'counter' || experiment.kind === 'state'" class="counter-lab">
        <el-button @click="counter = Math.max(0, counter - 1)">-</el-button>
        <strong>{{ counter }}</strong>
        <el-button type="primary" @click="counter += 1">+</el-button>
        <p>状态变化会立即反映到界面，这就是“可见的运行过程”。</p>
      </div>

      <div v-else-if="experiment.kind === 'pipeline'" class="pipeline-lab">
        <label>
          输入列表
          <el-input v-model="pipelineInput" placeholder="例如：1,2,3,4" />
        </label>
        <div class="pipeline-steps">
          <div class="pipeline-step">
            <span>原始数据</span>
            <strong>{{ parsedPipelineValues.join(', ') || '无有效数字' }}</strong>
          </div>
          <div class="pipeline-arrow">→</div>
          <div class="pipeline-step">
            <span>平方 map</span>
            <strong>{{ squaredValues.join(', ') || '-' }}</strong>
          </div>
          <div class="pipeline-arrow">→</div>
          <div class="pipeline-step">
            <span>过滤 > 4</span>
            <strong>{{ filteredValues.join(', ') || '-' }}</strong>
          </div>
          <div class="pipeline-arrow">→</div>
          <div class="pipeline-step accent">
            <span>平均值</span>
            <strong>{{ pipelineAverage }}</strong>
          </div>
        </div>
      </div>

      <div v-else-if="experiment.kind === 'join'" class="join-lab">
        <div class="table-pair">
          <div class="mini-table">
            <strong>students</strong>
            <span v-for="row in studentsTable" :key="row.id">{{ row.id }} / {{ row.name }}</span>
          </div>
          <div class="mini-table">
            <strong>submissions</strong>
            <span v-for="row in submissionsTable" :key="row.studentId">{{ row.studentId }} / {{ row.score }} 分</span>
          </div>
        </div>
        <div class="join-result">
          <strong>JOIN 结果</strong>
          <div v-for="row in joinedRows" :key="row.id" class="join-row">
            {{ row.name }} 的实验报告成绩是 {{ row.score }} 分
          </div>
        </div>
      </div>

      <div v-else-if="experiment.kind === 'structure'" class="structure-lab">
        <el-radio-group v-model="structureMode">
          <el-radio-button value="stack">栈：后进先出</el-radio-button>
          <el-radio-button value="queue">队列：先进先出</el-radio-button>
        </el-radio-group>
        <div class="structure-actions">
          <el-button type="primary" @click="pushStructureItem">加入节点</el-button>
          <el-button @click="popStructureItem">取出节点</el-button>
          <el-tag>{{ structureMessage }}</el-tag>
        </div>
        <div class="structure-track">
          <span v-for="item in structureItems" :key="item" class="structure-cell">{{ item }}</span>
        </div>
      </div>

      <div v-else-if="experiment.kind === 'maze'" class="maze-lab">
        <el-radio-group v-model="mazeMode">
          <el-radio-button value="bfs">BFS：一圈圈扩散</el-radio-button>
          <el-radio-button value="dfs">DFS：一条路走到底</el-radio-button>
        </el-radio-group>
        <div class="maze-grid">
          <span
            v-for="cell in mazeCells"
            :key="`${cell.x}-${cell.y}`"
            class="maze-cell"
            :class="{ wall: cell.wall, start: cell.start, goal: cell.goal, visited: cell.order > 0 }"
          >
            {{ cell.start ? '起' : cell.goal ? '终' : cell.order || '' }}
          </span>
        </div>
      </div>

      <div v-else class="card-lab">
        <div v-for="pair in pairs(experiment)" :key="pair" class="pair-card">{{ pair }}</div>
      </div>
    </div>

    <div v-if="visibleChallenges.length" class="challenge-list">
      <div v-for="challenge in visibleChallenges" :key="challenge.id" class="challenge-card">
        <strong>{{ challenge.title }}</strong>
        <p>{{ challenge.prompt }}</p>
        <el-tag>{{ challenge.answer }}</el-tag>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  experiments: { type: Array, default: () => [] },
  challenges: { type: Array, default: () => [] },
  lessonId: { type: [Number, String], default: null },
})

const learningRate = ref(0.12)
const boundary = ref(0.52)
const counter = ref(1)
const pipelineInput = ref('1, 2, 3, 4, 5')
const structureMode = ref('stack')
const structureItems = ref(['A', 'B', 'C'])
const structureMessage = ref('准备操作')
const mazeMode = ref('bfs')

const lessonId = computed(() => Number(props.lessonId))
const visibleExperiments = computed(() => scopedItems(props.experiments))
const visibleChallenges = computed(() => scopedItems(props.challenges))

const points = [
  { x: 0.18, y: 0.25, type: 'red' },
  { x: 0.22, y: 0.70, type: 'red' },
  { x: 0.42, y: 0.48, type: 'red' },
  { x: 0.62, y: 0.18, type: 'blue' },
  { x: 0.76, y: 0.58, type: 'blue' },
  { x: 0.86, y: 0.34, type: 'blue' },
]

const correctCount = computed(() => points.filter(p => (p.type === 'red' && p.x < boundary.value) || (p.type === 'blue' && p.x >= boundary.value)).length)
const f = x => x * x
const toPoint = x => `${210 + x * 40},${160 - f(x) * 8}`
const curvePoints = computed(() => Array.from({ length: 81 }, (_, i) => -5 + i * 0.125).map(toPoint).join(' '))
const descentDots = computed(() => {
  let x = -4
  const output = []
  for (let i = 0; i < 18; i += 1) {
    output.push(toPoint(x))
    x = x - learningRate.value * 2 * x
  }
  return output
})
const descentPoints = computed(() => descentDots.value.join(' '))
const parsedPipelineValues = computed(() => pipelineInput.value.split(/[,\s]+/).map(Number).filter(Number.isFinite))
const squaredValues = computed(() => parsedPipelineValues.value.map(value => value * value))
const filteredValues = computed(() => squaredValues.value.filter(value => value > 4))
const pipelineAverage = computed(() => {
  if (!filteredValues.value.length) return '-'
  return (filteredValues.value.reduce((sum, value) => sum + value, 0) / filteredValues.value.length).toFixed(2)
})
const studentsTable = [
  { id: 1, name: '林同学' },
  { id: 2, name: '周同学' },
  { id: 3, name: '王同学' },
]
const submissionsTable = [
  { studentId: 1, score: 92 },
  { studentId: 2, score: 85 },
  { studentId: 3, score: 97 },
]
const joinedRows = computed(() => submissionsTable.map(submission => ({
  ...submission,
  id: submission.studentId,
  name: studentsTable.find(student => student.id === submission.studentId)?.name || '未知学生'
})))
const mazeWalls = new Set(['1,1', '2,1', '3,3', '1,4'])
const mazeCells = computed(() => {
  const order = mazeOrder(mazeMode.value)
  return Array.from({ length: 25 }, (_, index) => {
    const x = index % 5
    const y = Math.floor(index / 5)
    const key = `${x},${y}`
    return {
      x,
      y,
      wall: mazeWalls.has(key),
      start: x === 0 && y === 0,
      goal: x === 4 && y === 4,
      order: order.get(key) || 0
    }
  })
})

function scopedItems(items) {
  const exact = (items || []).filter(item => !item.lessonId || Number(item.lessonId) === lessonId.value)
  return exact.length ? exact : (items || []).slice(0, 2)
}

function pairs(experiment) {
  const value = experiment?.defaults?.pairs || experiment?.defaults?.values || experiment?.defaults?.items || []
  return Array.isArray(value) ? value : Object.entries(value).map(([key, item]) => `${key}=${item}`)
}

function pushStructureItem() {
  const next = String.fromCharCode(65 + structureItems.value.length)
  structureItems.value = [...structureItems.value, next]
  structureMessage.value = `${next} 已加入${structureMode.value === 'stack' ? '栈顶' : '队尾'}`
}

function popStructureItem() {
  if (!structureItems.value.length) {
    structureMessage.value = '结构已为空'
    return
  }
  const items = [...structureItems.value]
  const value = structureMode.value === 'stack' ? items.pop() : items.shift()
  structureItems.value = items
  structureMessage.value = `${value} 被取出：${structureMode.value === 'stack' ? '后进先出' : '先进先出'}`
}

function mazeOrder(mode) {
  const start = [0, 0]
  const goal = '4,4'
  const output = new Map()
  const seen = new Set(['0,0'])
  const queue = [start]
  let index = 1
  while (queue.length) {
    const [x, y] = mode === 'dfs' ? queue.pop() : queue.shift()
    const key = `${x},${y}`
    output.set(key, index++)
    if (key === goal) break
    const next = [[1, 0], [0, 1], [-1, 0], [0, -1]]
      .map(([dx, dy]) => [x + dx, y + dy])
      .filter(([nx, ny]) => nx >= 0 && nx < 5 && ny >= 0 && ny < 5)
      .filter(([nx, ny]) => !mazeWalls.has(`${nx},${ny}`) && !seen.has(`${nx},${ny}`))
    for (const point of next) {
      seen.add(`${point[0]},${point[1]}`)
      queue.push(point)
    }
  }
  return output
}
</script>

<style scoped>
.card-header, .lab-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.experiment-block { border: 1px solid var(--line-subtle); border-radius: 16px; padding: 14px; margin-bottom: 14px; background: var(--surface-base); }
.experiment-block h3 { margin: 0 0 8px; }
.experiment-block p { color: var(--text-subtle); line-height: 1.7; }
.curve-svg { width: 100%; height: 190px; border-radius: 14px; background: var(--surface-canvas); }
.class-board { position: relative; height: 170px; border-radius: 16px; background: linear-gradient(90deg, rgba(248,113,113,.16), rgba(96,165,250,.16)); overflow: hidden; border: 1px solid var(--line-subtle); }
.point { position: absolute; width: 16px; height: 16px; margin: -8px 0 0 -8px; border-radius: 50%; border: 2px solid #fff; box-shadow: 0 2px 8px rgba(15,23,42,.25); }
.point.red { background: #ef4444; }
.point.blue { background: var(--accent); }
.boundary-line { position: absolute; top: 0; bottom: 0; width: 3px; background: var(--text-strong); }
.counter-lab { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.counter-lab strong { font-size: 28px; min-width: 44px; text-align: center; }
.pipeline-lab { display: grid; gap: 14px; }
.pipeline-lab label { display: grid; gap: 8px; color: var(--text-body); font-weight: 700; }
.pipeline-steps { display: grid; grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr; gap: 8px; align-items: stretch; }
.pipeline-step { display: grid; gap: 6px; padding: 12px; border-radius: 14px; background: var(--surface-canvas); border: 1px solid var(--line-subtle); }
.pipeline-step span { color: var(--text-subtle); font-size: 12px; }
.pipeline-step strong { color: var(--text-strong); word-break: break-word; }
.pipeline-step.accent { background: var(--positive-surface); border-color: var(--positive-line); }
.pipeline-arrow { display: grid; place-items: center; color: var(--accent); font-weight: 900; }
.table-pair { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.mini-table, .join-result { display: grid; gap: 8px; padding: 12px; border-radius: 14px; background: var(--surface-canvas); border: 1px solid var(--line-subtle); }
.mini-table strong, .join-result strong { color: var(--text-strong); }
.mini-table span, .join-row { padding: 8px; border-radius: 10px; background: var(--surface-base); color: var(--text-body); }
.join-result { margin-top: 10px; background: var(--surface-accent); border-color: var(--line-accent); }
.structure-lab { display: grid; gap: 12px; }
.structure-actions { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.structure-track { display: flex; gap: 10px; min-height: 70px; align-items: center; padding: 12px; border-radius: var(--radius-lg); background: var(--surface-accent); border: 1px solid var(--line-accent); overflow-x: auto; }
.structure-cell { min-width: 48px; height: 48px; border-radius: var(--radius-md); display: grid; place-items: center; background: var(--accent-solid); color: #fff; font-weight: 700; }
.maze-lab { display: grid; gap: 12px; }
.maze-grid { display: grid; grid-template-columns: repeat(5, 42px); gap: 6px; }
.maze-cell { width: 42px; height: 42px; display: grid; place-items: center; border-radius: var(--radius-md); background: var(--surface-sunken); color: var(--text-body); font-size: 12px; font-weight: 700; }
.maze-cell.wall { background: var(--text-strong); color: transparent; }
.maze-cell.visited { background: var(--surface-accent-strong); color: var(--accent); }
.maze-cell.start { background: #0d7350; color: #fff; }
.maze-cell.goal { background: #8a5300; color: #fff; }
.card-lab { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 10px; }
.pair-card, .challenge-card { border: 1px solid var(--line-accent); background: var(--surface-accent); border-radius: 12px; padding: 12px; line-height: 1.6; }
.challenge-list { display: grid; gap: 10px; }
.challenge-card p { margin: 8px 0; color: var(--text-body); }
@media (max-width: 860px) {
  .pipeline-steps, .table-pair { grid-template-columns: 1fr; }
  .pipeline-arrow { transform: rotate(90deg); }
}
</style>
