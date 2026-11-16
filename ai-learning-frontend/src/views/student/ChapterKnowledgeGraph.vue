<template>
  <main class="graph-page">
    <header class="graph-heading">
      <div>
        <h1>课程知识图谱</h1>
        <p>探索知识点之间的联系，查看每条关系的依据。</p>
      </div>
      <router-link
        :to="isTeacher ? '/teacher-knowledge-base' : '/knowledge-base'"
        >返回课程资料</router-link
      >
    </header>

    <section class="scope-bar" aria-label="图谱范围">
      <label
        >课程<select
          v-model="courseId"
          aria-label="课程"
          @change="changeCourse"
        >
          <option value="">请选择课程</option>
          <option
            v-for="course in courses"
            :key="course.id"
            :value="String(course.id)"
          >
            {{ course.title }}
          </option>
        </select></label
      >
      <label
        >章节图谱<select
          v-model="graphId"
          aria-label="章节图谱"
          @change="loadSelectedGraph"
        >
          <option value="">请选择图谱</option>
          <option
            v-for="item in graphs"
            :key="item.graphId"
            :value="item.graphId"
          >
            {{ item.title }}{{ item.publishedVersion ? '' : ' · 草稿' }}
          </option>
        </select></label
      >
      <button v-if="isTeacher && courseId" class="primary" @click="openCreate">
        新建章节图谱
      </button>
      <button v-if="graphId" :disabled="busy" @click="loadSelectedGraph">
        刷新
      </button>
    </section>

    <p v-if="error" role="alert" class="message error">{{ error }}</p>
    <p v-if="notice" role="status" class="message success">{{ notice }}</p>
    <p v-if="busy" role="status" class="loading">正在加载图谱…</p>

    <section
      v-if="creating"
      class="editor create-panel"
      aria-label="新建章节图谱"
    >
      <h2>新建章节图谱</h2>
      <form @submit.prevent="create">
        <div class="form-grid">
          <label
            >图谱标题<input
              v-model="createForm.title"
              aria-label="图谱标题"
              required
              maxlength="200"
          /></label>
          <label
            >所属章节<select
              v-model="createForm.chapterId"
              aria-label="所属章节"
              required
            >
              <option value="">请选择章节</option>
              <option
                v-for="chapter in chapters"
                :key="chapter.id"
                :value="String(chapter.id)"
              >
                {{ chapter.title }}
              </option>
            </select></label
          >
          <label
            >资料库<select
              v-model="baseId"
              aria-label="资料库"
              required
              @change="loadDocuments"
            >
              <option value="">请选择资料库</option>
              <option v-for="base in bases" :key="base.id" :value="base.id">
                {{ base.name }}
              </option>
            </select></label
          >
          <label
            >来源资料<select
              v-model="createForm.documentId"
              aria-label="来源资料"
              required
              @change="selectDocument"
            >
              <option value="">请选择资料</option>
              <option
                v-for="document in documents"
                :key="document.id"
                :value="document.id"
              >
                {{ document.title || document.file_name }}
              </option>
            </select></label
          >
          <label
            >起始页<input
              v-model.number="createForm.pageStart"
              type="number"
              min="1"
              aria-label="起始页"
          /></label>
          <label
            >结束页<input
              v-model.number="createForm.pageEnd"
              type="number"
              min="1"
              aria-label="结束页"
          /></label>
        </div>
        <label class="checkbox grant-control"
          ><input
            v-model="grantRead"
            type="checkbox"
          />将此资料版本设为课程可阅读（关闭原件下载）</label
        >
        <p class="helper">
          资料须已完成解析，并授权给当前课程。已有授权的资料可直接创建。纯文本资料可将页码留空。
        </p>
        <div class="actions">
          <button class="primary" :disabled="working">创建草稿</button
          ><button type="button" @click="creating = false">取消</button>
        </div>
      </form>
    </section>

    <div v-if="!busy && !graph && !creating && !error" class="empty-state">
      <h2>
        {{
          courseId
            ? isTeacher
              ? '本课程暂无图谱'
              : '本课程暂无已发布图谱'
            : '选择课程开始探索'
        }}
      </h2>
      <p>
        {{
          isTeacher
            ? '选择章节与资料，整理知识点及其关系后发布。'
            : '课程图谱发布后，可在这里查看知识关系与来源。'
        }}
      </p>
    </div>

    <section v-if="graph" class="graph-shell">
      <div class="graph-toolbar">
        <div>
          <h2>{{ graph.title }}</h2>
          <span class="version"
            >{{ editing ? '审核草稿' : '已发布版本 ' + graph.graphVersion }} ·
            {{ graph.nodes.length }} 个知识点 ·
            {{ graph.edges.length }} 条关系</span
          >
        </div>
        <div class="actions">
          <button v-if="isTeacher && !editing" @click="editDraft">
            编辑草稿</button
          ><button
            v-if="editing && graph.publishedVersion"
            @click="showPublished"
          >
            查看已发布版本
          </button>
        </div>
      </div>

      <section
        v-if="editing"
        class="editor"
        data-testid="graph-editor"
        aria-label="知识图谱审核"
      >
        <div class="editor-heading">
          <h3>整理与审核</h3>
          <button :disabled="working" @click="extract">从资料提取候选</button>
        </div>
        <p class="helper">
          候选知识点和关系可编辑。发布前请逐项核对来源与关系方向。
        </p>
        <div class="edit-columns">
          <form class="entity-form" @submit.prevent="addNode">
            <h4>知识点</h4>
            <label
              >名称<input
                v-model="nodeForm.name"
                aria-label="知识点名称"
                required
                maxlength="200"
            /></label>
            <label
              >类型<select v-model="nodeForm.type" aria-label="知识点类型">
                <option
                  v-for="(label, type) in typeLabels"
                  :key="type"
                  :value="type"
                >
                  {{ label }}
                </option>
              </select></label
            >
            <label
              >解释<textarea
                v-model="nodeForm.description"
                aria-label="知识点解释"
                required
                rows="2"
              ></textarea>
            </label>
            <label
              >依据<select
                v-model="nodeForm.evidenceId"
                aria-label="知识点依据"
                required
              >
                <option value="">请选择原文切片</option>
                <option
                  v-for="source in sources"
                  :key="source.id"
                  :value="source.chunkId || source.id"
                >
                  {{ sourceLabel(source) }}
                </option>
              </select></label
            >
            <div class="actions">
              <button :disabled="working">
                {{ nodeForm.id ? '更新知识点' : '加入知识点' }}</button><button
                v-if="nodeForm.id"
                type="button"
                @click="resetNodeForm"
              >
                取消编辑
              </button>
            </div>
          </form>
          <form @submit.prevent="addEdge">
            <h4>知识关系</h4>
            <label
              >起点<select
                v-model="edgeForm.source"
                aria-label="关系起点"
                required
              >
                <option value="">请选择</option>
                <option
                  v-for="node in graph.nodes"
                  :key="node.id"
                  :value="node.id"
                >
                  {{ node.name }}
                </option>
              </select></label
            >
            <label
              >关系<select v-model="edgeForm.type" aria-label="关系类型">
                <option
                  v-for="(label, type) in relationLabels"
                  :key="type"
                  :value="type"
                >
                  {{ label }}
                </option>
              </select></label
            >
            <label
              >终点<select
                v-model="edgeForm.target"
                aria-label="关系终点"
                required
              >
                <option value="">请选择</option>
                <option
                  v-for="node in graph.nodes"
                  :key="node.id"
                  :value="node.id"
                >
                  {{ node.name }}
                </option>
              </select></label
            >
            <label
              >关系说明<textarea
                v-model="edgeForm.explanation"
                aria-label="关系说明"
                required
                rows="2"
              ></textarea>
            </label>
            <label
              >关系依据<select
                v-model="edgeForm.evidenceId"
                aria-label="关系依据"
              >
                <option value="">教师建议</option>
                <option
                  v-for="source in sources"
                  :key="source.id"
                  :value="source.chunkId || source.id"
                >
                  {{ sourceLabel(source) }}
                </option>
              </select></label
            >
            <div class="actions">
              <button :disabled="working">
                {{ edgeForm.id ? '更新关系' : '加入关系' }}</button><button
                v-if="edgeForm.id"
                type="button"
                @click="resetEdgeForm"
              >
                取消编辑
              </button>
            </div>
          </form>
        </div>
        <details data-testid="graph-sources">
          <summary>本章来源切片（{{ sources.length }}）</summary>
          <article
            v-for="source in sources"
            :key="source.id"
            class="source-excerpt"
          >
            <strong>{{ sourceLabel(source) }}</strong><code>{{
              source.chunkId || source.id
            }}</code>
            <p>{{ source.content }}</p>
          </article>
        </details>
        <details>
          <summary>批量导入与编辑</summary>
          <p class="helper">
            导入包含 nodes 与 edges 的 JSON，evidenceIds 使用上方真实切片标识。
          </p>
          <label
            >实体与关系 JSON<textarea
              v-model="jsonText"
              aria-label="实体与关系 JSON"
              rows="9"
              spellcheck="false"
            ></textarea></label
          ><label class="import-file"
            >选择 JSON 文件<input
              type="file"
              accept=".json,application/json"
              @change="importFile"
          /></label>
        </details>
        <div class="publish-controls">
          <button class="primary" :disabled="working" @click="saveDraft">
            保存草稿</button
          ><label class="checkbox"
            ><input
              v-model="reviewed"
              type="checkbox"
              aria-label="我已核对实体、关系和来源"
            />我已核对实体、关系和来源</label
          ><button
            class="primary"
            :disabled="working || !reviewed"
            @click="publish"
          >
            审核并发布
          </button>
        </div>
        <div v-if="job" class="job-state" role="status">
          {{ jobMessage }}
          <button
            v-if="job.status === 'FAILED'"
            :disabled="working"
            @click="retryPublish"
          >
            重试发布
          </button>
        </div>
      </section>

      <div class="explore-tools">
        <div class="search-field">
          <label
            >搜索知识点<input
              v-model="query"
              aria-label="搜索知识点"
              placeholder="名称或别名"
              @keydown.enter="matches[0] && selectNode(matches[0].id)"
          /></label>
          <div v-if="query.trim()" class="search-results">
            <button
              v-for="node in matches"
              :key="node.id"
              :aria-label="`定位${node.name}`"
              @click="selectNode(node.id)"
            >
              {{ node.name }}
            </button>
            <p v-if="!matches.length">没有找到匹配的知识点</p>
          </div>
        </div>
        <label
          >显示关系<select v-model="relationType" aria-label="筛选关系">
            <option value="">全部关系</option>
            <option
              v-for="(label, type) in relationLabels"
              :key="type"
              :value="type"
            >
              {{ label }}
            </option>
          </select></label
        >
        <p v-if="graph.truncated" class="helper">
          当前显示部分结果，可选择节点继续展开。
        </p>
      </div>

      <div class="graph-body">
        <KnowledgeGraphCanvas
          :key="`${graph.graphId}:${graph.graphVersion}:${editing}`"
          ref="canvas"
          :title="graph.title"
          :nodes="graph.nodes"
          :edges="graph.edges"
          :selected-id="selectedNodeId || selectedEdgeId"
          :relation-type="relationType"
          :path-ids="pathIds"
          @node-select="selectNode"
          @edge-select="selectEdge"
          @clear-selection="clearSelection"
        />
        <aside
          class="graph-detail"
          data-testid="graph-detail"
          aria-live="polite"
        >
          <template v-if="selectedNode">
            <span class="detail-kind">{{
              typeLabels[selectedNode.type] || selectedNode.type
            }}</span>
            <h3>{{ selectedNode.name }}</h3>
            <p>{{ selectedNode.description }}</p>
            <small v-if="selectedNode.aliases?.length"
              >别名 {{ selectedNode.aliases.join('、') }}</small
            >
            <div class="actions">
              <button v-if="!editing" :disabled="working" @click="expand">
                展开相邻知识</button
              ><button v-if="editing" @click="editNode(selectedNode)">
                修改知识点</button
              ><button v-if="editing" @click="removeNode(selectedNode.id)">
                移除知识点
              </button>
            </div>
          </template>
          <template v-else-if="selectedEdge">
            <span class="detail-kind">关系详情</span>
            <h3>
              {{ nodeNames[selectedEdge.source] }} →
              {{ nodeNames[selectedEdge.target] }}
            </h3>
            <span class="relation-tag">{{
              selectedEdge.label || relationLabels[selectedEdge.type]
            }}</span>
            <p>{{ selectedEdge.explanation }}</p>
            <small>{{
              selectedEdge.origin === 'TEACHER' ? '教师建议' : '资料依据'
            }}</small>
            <small v-if="selectedEdge.reviewedBy"
              >教师 {{ selectedEdge.reviewedBy }} 已确认<span
                v-if="selectedEdge.reviewedAt"
              >
                ·
                {{
                  new Date(selectedEdge.reviewedAt).toLocaleString('zh-CN')
                }}</span
              ></small
            >
            <div v-if="editing" class="actions">
              <button @click="editEdge(selectedEdge)">修改关系</button
              ><button @click="removeEdge(selectedEdge.id)">移除关系</button>
            </div>
          </template>
          <template v-else
            ><h3>查看知识联系</h3>
            <p>选择知识点或关系，查看说明和原文依据。</p></template
          >
          <div v-if="selectedItem?.evidenceIds?.length" class="evidence-links">
            <h4>来源依据</h4>
            <button
              v-for="id in selectedItem.evidenceIds"
              :key="id"
              :aria-label="`查看来源${id}`"
              @click="showEvidence(id)"
            >
              {{ evidenceLabel(id) }}
            </button>
          </div>
          <section
            v-if="evidence"
            data-testid="evidence-detail"
            class="evidence-detail"
          >
            <h4>
              {{ evidence.pageNo ? `第 ${evidence.pageNo} 页` : '原文切片' }}
            </h4>
            <small
              >文档版本 {{ evidence.documentVersion }} · 解析版本
              {{ evidence.parseRevision }}</small
            >
            <p>{{ evidence.content }}</p>
            <small v-if="evidence.charStart != null"
              >原文位置 {{ evidence.charStart }}–{{ evidence.charEnd }}</small
            >
          </section>
          <h4>知识点</h4>
          <div class="node-list">
            <button
              v-for="node in graph.nodes"
              :key="node.id"
              :class="{ active: selectedNodeId === node.id }"
              :aria-label="`选择知识点${node.name}`"
              @click="selectNode(node.id)"
            >
              {{ node.name }}
            </button>
          </div>
          <h4>关系列表</h4>
          <div class="edge-list">
            <button
              v-for="edge in visibleEdges"
              :key="edge.id"
              :aria-label="`查看关系${nodeNames[edge.source]} → ${nodeNames[edge.target]}`"
              @click="selectEdge(edge.id)"
            >
              <span
                >{{ nodeNames[edge.source] }} →
                {{ nodeNames[edge.target] }}</span
              ><small>{{ edge.label || relationLabels[edge.type] }}</small>
            </button>
            <p v-if="!visibleEdges.length">当前筛选下没有关系。</p>
          </div>
        </aside>
      </div>
      <div class="graph-legend">
        <span v-for="(label, type) in typeLabels" :key="type" :class="type"
          ><i></i>{{ label }}</span
        ><span class="legend-note">箭头表示关系方向，虚线表示先修关系</span>
      </div>
      <form
        v-if="!editing && graph.nodes.length"
        class="path-tools"
        @submit.prevent="findPath"
      >
        <label
          >路径起点<select v-model="pathFrom" aria-label="路径起点" required>
            <option value="">请选择</option>
            <option v-for="node in graph.nodes" :key="node.id" :value="node.id">
              {{ node.name }}
            </option>
          </select></label
        ><label
          >路径终点<select v-model="pathTo" aria-label="路径终点" required>
            <option value="">请选择</option>
            <option v-for="node in graph.nodes" :key="node.id" :value="node.id">
              {{ node.name }}
            </option>
          </select></label
        ><label class="checkbox"
          ><input
            v-model="learningOnly"
            type="checkbox"
            aria-label="仅看已审核先修关系"
          />仅看已审核先修关系</label
        ><button :disabled="working">查看路径</button>
      </form>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import request from '@/utils/request'
import { getCourses } from '@/utils/course'
import { listKnowledgeBases, listDocuments } from '@/api/rag'
import * as api from '@/api/knowledgeGraph'
import KnowledgeGraphCanvas from '@/components/knowledge/KnowledgeGraphCanvas.vue'

const route = useRoute(),
  router = useRouter(),
  user = useUserStore()
const isTeacher = computed(() => ['teacher', 'admin'].includes(user.roleCode))
const typeLabels = {
  CONCEPT: '概念',
  ALGORITHM: '算法',
  TASK: '学习任务',
  MODEL: '模型',
  METRIC: '评价指标',
}
const relationLabels = {
  HAS_TASK: '任务类型',
  HAS_EXAMPLE: '算法示例',
  IS_A: '属于',
  USED_FOR: '用于',
  MEASURED_BY: '评价方式',
  PREREQUISITE_FOR: '建议先学',
  RELATED_TO: '相关',
  COVERS: '包含',
}
const courses = ref([]),
  chapters = ref([]),
  graphs = ref([]),
  bases = ref([]),
  documents = ref([]),
  sources = ref([])
const courseId = ref(String(route.query.courseId || '')),
  graphId = ref(String(route.query.graphId || '')),
  baseId = ref('')
const graph = ref(null),
  canvas = ref(null),
  error = ref(''),
  notice = ref(''),
  busy = ref(false),
  working = ref(false),
  creating = ref(false),
  editing = ref(false),
  reviewed = ref(false)
const createForm = reactive({
  title: '',
  chapterId: '',
  documentId: '',
  documentVersion: 1,
  pageStart: null,
  pageEnd: null,
})
const grantRead = ref(false)
const jsonText = ref(''),
  query = ref(''),
  relationType = ref(''),
  selectedNodeId = ref(''),
  selectedEdgeId = ref(''),
  evidence = ref(null)
const nodeForm = reactive({
  id: '',
  name: '',
  type: 'CONCEPT',
  description: '',
  evidenceId: '',
})
const edgeForm = reactive({
  id: '',
  source: '',
  target: '',
  type: 'HAS_TASK',
  explanation: '',
  evidenceId: '',
})
const pathFrom = ref(''),
  pathTo = ref(''),
  learningOnly = ref(false),
  pathIds = ref([]),
  job = ref(null)
let generation = 0,
  pollTimer,
  searchTimer,
  alive = true
const remoteMatches = ref([])
const selectedNode = computed(() =>
  graph.value?.nodes.find((node) => node.id === selectedNodeId.value),
)
const selectedEdge = computed(() =>
  graph.value?.edges.find((edge) => edge.id === selectedEdgeId.value),
)
const selectedItem = computed(() => selectedNode.value || selectedEdge.value)
const nodeNames = computed(() =>
  Object.fromEntries(
    (graph.value?.nodes || []).map((node) => [node.id, node.name]),
  ),
)
const matches = computed(() =>
  [
    ...new Map(
      [...(graph.value?.nodes || []), ...remoteMatches.value].map((node) => [
        node.id,
        node,
      ]),
    ).values(),
  ]
    .filter((node) =>
      `${node.name} ${(node.aliases || []).join(' ')}`
        .toLocaleLowerCase()
        .includes(query.value.trim().toLocaleLowerCase()),
    )
    .slice(0, 20),
)
const visibleEdges = computed(() =>
  (graph.value?.edges || []).filter(
    (edge) => !relationType.value || edge.type === relationType.value,
  ),
)
const jobMessage = computed(() =>
  job.value?.status === 'FAILED'
    ? job.value.error || job.value.errorMessage || '发布失败，可以重试。'
    : '图谱正在入库，完成后显示已发布版本。',
)
const records = (data) =>
  Array.isArray(data) ? data : data?.records || data?.list || []
const message = (failure) =>
  failure.response?.data?.msg || failure.message || '图谱操作失败'

async function action(callback) {
  working.value = true
  error.value = ''
  notice.value = ''
  try {
    await callback()
  } catch (failure) {
    error.value = message(failure)
    if ([401, 403, 404, 409].includes(failure.status)) {
      graph.value = null
      sources.value = []
      evidence.value = null
    }
  } finally {
    working.value = false
  }
}
function clearSelection() {
  selectedNodeId.value = ''
  selectedEdgeId.value = ''
  evidence.value = null
  query.value = ''
  pathIds.value = []
  remoteMatches.value = []
}
function updateJson() {
  jsonText.value = JSON.stringify(
    { nodes: graph.value.nodes, edges: graph.value.edges },
    null,
    2,
  )
  reviewed.value = false
}
function applyGraph(data) {
  graph.value = { ...data, nodes: data.nodes || [], edges: data.edges || [] }
  graphId.value = data.graphId
  if (data.courseId) courseId.value = String(data.courseId)
  clearSelection()
  updateJson()
}
async function loadGraph(id, draft = false) {
  if (!id) return
  const current = ++generation
  busy.value = true
  graph.value = null
  error.value = ''
  sources.value = []
  editing.value = draft
  clearTimeout(pollTimer)
  job.value = null
  try {
    const result = await api.getGraph(id, draft)
    if (!alive || current !== generation) return
    applyGraph(result)
    // New graphs have no published version and are opened directly as a teacher draft.
    editing.value = isTeacher.value && (draft || !result.publishedVersion)
    const nextSources = editing.value
      ? (await api.getGraphSources(id)).chunks || []
      : result.evidence || []
    if (current !== generation || !alive) return
    sources.value = nextSources
    await router.replace({ query: { courseId: courseId.value, graphId: id } })
  } catch (failure) {
    if (current === generation && alive) {
      graph.value = null
      sources.value = []
      error.value = message(failure)
    }
  } finally {
    if (current === generation && alive) busy.value = false
  }
}
const loadSelectedGraph = () =>
  graphId.value ? loadGraph(graphId.value) : (graph.value = null)
async function changeCourse() {
  ++generation
  clearTimeout(pollTimer)
  graph.value = null
  graphId.value = ''
  graphs.value = []
  error.value = ''
  creating.value = false
  if (!courseId.value) return
  const scope = courseId.value
  busy.value = true
  try {
    const result = await api.listGraphs(Number(scope))
    if (scope !== courseId.value || !alive) return
    graphs.value = records(result)
    const chapterMatch = graphs.value.find(
      (item) => String(item.chapterId) === String(route.query.chapterId),
    )
    graphId.value = chapterMatch?.graphId || graphs.value[0]?.graphId || ''
    if (graphId.value) await loadGraph(graphId.value)
    else await router.replace({ query: { courseId: scope } })
  } catch (failure) {
    error.value = message(failure)
  } finally {
    busy.value = false
  }
}
async function openCreate() {
  await action(async () => {
    const [chapterResult, baseResult] = await Promise.all([
      request.get(`/courses/${courseId.value}/chapters`),
      listKnowledgeBases(),
    ])
    if (String(chapterResult.code) !== '200')
      throw new Error(chapterResult.msg || '无法读取章节')
    chapters.value = records(chapterResult.data)
    bases.value = records(baseResult.data)
    creating.value = true
    createForm.title = ''
    createForm.chapterId = String(route.query.chapterId || '')
    createForm.documentId = ''
    baseId.value = ''
    documents.value = []
    grantRead.value = false
  })
}
async function loadDocuments() {
  createForm.documentId = ''
  documents.value = []
  if (baseId.value)
    await action(async () => {
      documents.value = records((await listDocuments(baseId.value)).data)
    })
}
function selectDocument() {
  const document = documents.value.find(
    (item) => item.id === createForm.documentId,
  )
  createForm.documentVersion = Number(
    document?.document_version || document?.documentVersion || 1,
  )
  createForm.pageStart = document?.page_count ? 1 : null
  createForm.pageEnd = Number(document?.page_count) || null
}
async function create() {
  await action(async () => {
    if (grantRead.value) {
      const grant = await request.post(
        `/api/rag/documents/${encodeURIComponent(createForm.documentId)}/grants`,
        {
          courseId: Number(courseId.value),
          documentVersion: createForm.documentVersion,
          readExcerpt: true,
          downloadAllowed: false,
        },
        { silentError: true },
      )
      if (String(grant.code) !== '200')
        throw new Error(grant.msg || '课程资料授权失败')
    }
    const result = await api.createGraph({
      ...createForm,
      pageStart: Number(createForm.pageStart) || 0,
      pageEnd: Number(createForm.pageEnd) || 0,
      courseId: Number(courseId.value),
      chapterId: Number(createForm.chapterId),
    })
    graphs.value = [...graphs.value, result]
    creating.value = false
    await loadGraph(result.graphId, true)
  })
}
async function editDraft() {
  reviewed.value = false
  await loadGraph(graphId.value, true)
}
async function showPublished() {
  await loadGraph(graphId.value)
}
function sourceLabel(source) {
  return `${source.pageNo ? '第 ' + source.pageNo + ' 页' : '原文切片'} · ${String(source.content || '').slice(0, 35)}`
}
function evidenceLabel(id) {
  const item = sources.value.find(
    (source) => (source.chunkId || source.id) === id,
  )
  return item ? sourceLabel(item) : '查看原文依据'
}
function selectNode(id) {
  const found = remoteMatches.value.find((node) => node.id === id)
  if (found && !graph.value.nodes.some((node) => node.id === id)) {
    graph.value.nodes = [...graph.value.nodes, found]
  }
  selectedNodeId.value = id
  selectedEdgeId.value = ''
  evidence.value = null
  canvas.value?.focus(id)
}
function selectEdge(id) {
  selectedEdgeId.value = id
  selectedNodeId.value = ''
  evidence.value = null
  canvas.value?.focusRelation(id)
}
async function showEvidence(id) {
  await action(async () => {
    evidence.value = await api.getGraphEvidence(graphId.value, id)
  })
}
async function expand() {
  const id = selectedNodeId.value
  await action(async () => {
    const result = await api.getGraphNeighbors(
      graphId.value,
      id,
      relationType.value,
    )
    mergeSlice(result)
    if (result.truncated)
      notice.value = '部分相邻知识已显示，可继续选择节点展开。'
  })
}
function mergeSlice(result) {
  graph.value.nodes = [
    ...new Map(
      [...graph.value.nodes, ...(result.nodes || [])].map((node) => [
        node.id,
        node,
      ]),
    ).values(),
  ]
  graph.value.edges = [
    ...new Map(
      [...graph.value.edges, ...(result.edges || [])].map((edge) => [
        edge.id,
        edge,
      ]),
    ).values(),
  ]
}
async function findPath() {
  await action(async () => {
    const result = await api.getGraphPaths(
      graphId.value,
      pathFrom.value,
      pathTo.value,
      learningOnly.value,
    )
    mergeSlice(result)
    pathIds.value = [...(result.nodes || []), ...(result.edges || [])].map(
      (item) => item.id,
    )
    notice.value = result.edges?.length
      ? '已高亮查询路径'
      : '当前范围未找到符合条件的路径'
  })
}
function resetNodeForm() {
  Object.assign(nodeForm, {
    id: '',
    name: '',
    type: 'CONCEPT',
    description: '',
    evidenceId: '',
  })
}
function resetEdgeForm() {
  Object.assign(edgeForm, {
    id: '',
    source: '',
    target: '',
    type: 'HAS_TASK',
    explanation: '',
    evidenceId: '',
  })
}
function editNode(node) {
  Object.assign(nodeForm, { ...node, evidenceId: node.evidenceIds?.[0] || '' })
}
function editEdge(edge) {
  Object.assign(edgeForm, { ...edge, evidenceId: edge.evidenceIds?.[0] || '' })
}
function addNode() {
  const previous = graph.value.nodes.find((node) => node.id === nodeForm.id)
  const node = {
    id: nodeForm.id || crypto.randomUUID(),
    name: nodeForm.name.trim(),
    type: nodeForm.type,
    description: nodeForm.description.trim(),
    aliases: previous?.aliases || [],
    evidenceIds: [
      ...new Set([
        nodeForm.evidenceId,
        ...(previous?.evidenceIds?.slice(1) || []),
      ]),
    ].filter(Boolean),
  }
  graph.value.nodes = previous
    ? graph.value.nodes.map((item) => (item.id === node.id ? node : item))
    : [...graph.value.nodes, node]
  resetNodeForm()
  updateJson()
}
function addEdge() {
  const previous = graph.value.edges.find((edge) => edge.id === edgeForm.id)
  const edge = {
    id: edgeForm.id || crypto.randomUUID(),
    source: edgeForm.source,
    target: edgeForm.target,
    type: edgeForm.type,
    label: relationLabels[edgeForm.type],
    explanation: edgeForm.explanation.trim(),
    origin: edgeForm.evidenceId ? 'DOCUMENT' : 'TEACHER',
    evidenceIds: edgeForm.evidenceId
      ? [
          ...new Set([
            edgeForm.evidenceId,
            ...(previous?.evidenceIds?.slice(1) || []),
          ]),
        ]
      : [],
  }
  graph.value.edges = previous
    ? graph.value.edges.map((item) => (item.id === edge.id ? edge : item))
    : [...graph.value.edges, edge]
  resetEdgeForm()
  updateJson()
}
function removeNode(id) {
  graph.value.nodes = graph.value.nodes.filter((node) => node.id !== id)
  graph.value.edges = graph.value.edges.filter(
    (edge) => edge.source !== id && edge.target !== id,
  )
  clearSelection()
  updateJson()
}
function removeEdge(id) {
  graph.value.edges = graph.value.edges.filter((edge) => edge.id !== id)
  clearSelection()
  updateJson()
}
async function importFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  await action(async () => {
    if (file.size > 2 * 1024 * 1024) throw new Error('导入文件不能超过 2 MB')
    jsonText.value = await file.text()
    reviewed.value = false
  })
  event.target.value = ''
}
function parseDraft() {
  const data = JSON.parse(jsonText.value)
  if (!Array.isArray(data.nodes) || !Array.isArray(data.edges))
    throw new Error('导入内容须包含 nodes 和 edges 数组')
  return {
    revision: graph.value.revision,
    nodes: data.nodes,
    edges: data.edges,
  }
}
async function persistDraft() {
  const result = await api.saveGraphDraft(graphId.value, parseDraft())
  applyGraph(result)
  editing.value = true
}
async function saveDraft() {
  await action(async () => {
    await persistDraft()
    notice.value = '草稿已保存'
  })
}
async function extract() {
  await action(async () => {
    const result = await api.extractGraphDraft(
      graphId.value,
      graph.value.revision,
    )
    applyGraph(result)
    editing.value = true
    notice.value = '候选已提取，请逐项核对后发布'
  })
}
async function publish() {
  if (!reviewed.value) return
  await action(async () => {
    await persistDraft()
    const result = await api.publishGraph(graphId.value, graph.value.revision)
    job.value = { ...result, status: result.status || 'PROJECTING' }
    if (!result.jobId) throw new Error('发布任务未返回任务标识，请刷新检查状态')
    pollJob(graphId.value, result.jobId)
  })
}
async function pollJob(id, jobId) {
  try {
    const result = await api.getGraphJob(id, jobId)
    if (!alive || graphId.value !== id) return
    job.value = { ...result, jobId }
    if (result.status === 'DONE') {
      await loadGraph(id)
      notice.value = '图谱已发布'
      return
    }
    if (result.status !== 'FAILED')
      pollTimer = setTimeout(() => pollJob(id, jobId), 1500)
  } catch (failure) {
    if (alive) {
      job.value = { jobId, status: 'FAILED', error: message(failure) }
      error.value = message(failure)
    }
  }
}
async function retryPublish() {
  await action(async () => {
    const id = job.value.jobId
    await api.retryGraphJob(graphId.value, id)
    pollJob(graphId.value, id)
  })
}
watch(jsonText, () => {
  reviewed.value = false
})
watch(query, (keyword) => {
  clearTimeout(searchTimer)
  remoteMatches.value = []
  if (!keyword.trim() || editing.value || !graph.value) return
  const id = graphId.value,
    current = generation
  searchTimer = setTimeout(async () => {
    try {
      const found = await api.searchGraphEntities(id, keyword.trim())
      if (
        alive &&
        current === generation &&
        graphId.value === id &&
        query.value === keyword
      ) {
        remoteMatches.value = records(found)
      }
    } catch (failure) {
      if (alive && current === generation) {
        error.value = message(failure)
        if ([401, 403, 404, 409].includes(failure.status)) graph.value = null
      }
    }
  }, 300)
})
onMounted(async () => {
  busy.value = true
  try {
    const result = await getCourses({ pageNum: 1, pageSize: 100 })
    if (String(result.code) !== '200')
      throw new Error(result.msg || '无法读取课程')
    courses.value = records(result.data)
    if (graphId.value) {
      await loadGraph(graphId.value)
      if (courseId.value)
        graphs.value = records(await api.listGraphs(Number(courseId.value)))
    } else if (courseId.value) await changeCourse()
  } catch (failure) {
    error.value = message(failure)
  } finally {
    busy.value = false
  }
})
onBeforeUnmount(() => {
  alive = false
  ++generation
  clearTimeout(pollTimer)
  clearTimeout(searchTimer)
})
</script>

<style scoped>
.graph-page {
  max-width: 1600px;
  margin: auto;
  padding: 26px 28px;
  color: #263750;
}
.graph-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 25px;
}
h1 {
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -0.5px;
  margin: 0 0 9px;
}
h2 {
  margin: 0;
  font-size: 19px;
}
h3 {
  margin: 0 0 12px;
  font-size: 17px;
}
h4 {
  margin: 20px 0 10px;
  font-size: 14px;
}
p {
  line-height: 1.75;
}
.graph-heading p {
  margin: 0;
  color: #7a879a;
  font-size: 14px;
}
a {
  text-decoration: none;
  color: #4263a8;
  font-size: 14px;
}
.scope-bar,
.graph-toolbar,
.explore-tools,
.actions,
.publish-controls,
.path-tools,
.editor-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.scope-bar {
  padding: 17px 20px;
  background: white;
  border: 1px solid #e0e7f0;
  border-radius: 12px;
  margin-bottom: 18px;
}
label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  font-size: 13px;
  color: #566780;
  min-width: 0;
}
.scope-bar label {
  min-width: 180px;
  flex: 1;
  max-width: 360px;
}
input,
select,
textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #d7e0ee;
  background: white;
  padding: 9px 11px;
  border-radius: 7px;
  color: #263750;
  font: inherit;
  min-width: 0;
}
textarea {
  resize: vertical;
  line-height: 1.6;
}
button {
  border: 1px solid #d7e0ee;
  border-radius: 7px;
  background: white;
  color: #435a7b;
  padding: 9px 13px;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
button:hover {
  background: #edf3fc;
}
button:disabled {
  opacity: 0.5;
  cursor: default;
}
button.primary {
  background: #3156a4;
  border-color: #3156a4;
  color: white;
}
button:focus-visible,
input:focus-visible,
select:focus-visible,
textarea:focus-visible {
  outline: 2px solid #517dc9;
  outline-offset: 2px;
}
.scope-bar > button {
  align-self: flex-end;
}
.message {
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 14px;
}
.error {
  background: #fff1ef;
  color: #a43c36;
}
.success {
  background: #eef7f3;
  color: #267153;
}
.loading {
  color: #6d7c91;
  padding: 20px;
}
.empty-state {
  text-align: center;
  padding: 90px 15px;
  color: #78869a;
}
.empty-state h2 {
  color: #445571;
}
.graph-shell {
  background: white;
  border: 1px solid #dfe6f0;
  border-radius: 14px;
  overflow: hidden;
}
.graph-toolbar {
  justify-content: space-between;
  padding: 20px 22px;
}
.version {
  display: inline-block;
  margin-top: 7px;
  color: #7e8ba0;
  font-size: 12px;
}
.explore-tools {
  border-top: 1px solid #e9edf3;
  border-bottom: 1px solid #e9edf3;
  padding: 15px 22px;
  background: #fcfdff;
}
.search-field {
  position: relative;
  min-width: 240px;
  flex: 1;
  max-width: 400px;
}
.search-results {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: white;
  box-shadow: 0 5px 20px #1832521a;
  border: 1px solid #e0e7f0;
  border-radius: 8px;
  z-index: 5;
  padding: 6px;
  max-height: 250px;
  overflow: auto;
}
.search-results button {
  display: block;
  border: 0;
  width: 100%;
  text-align: left;
}
.search-results p {
  margin: 8px;
  font-size: 13px;
}
.graph-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  min-width: 0;
}
.graph-detail {
  border-left: 1px solid #e3eaf4;
  padding: 24px 20px;
  max-height: 640px;
  overflow: auto;
  min-width: 0;
}
.graph-detail h3 {
  overflow-wrap: anywhere;
  margin-top: 10px;
}
.graph-detail p {
  color: #66768b;
  font-size: 14px;
}
.graph-detail small {
  display: block;
  color: #8a6b9f;
  line-height: 1.7;
}
.detail-kind {
  font-size: 12px;
  color: #8491a6;
}
.relation-tag {
  background: #eef3fc;
  color: #4a68a0;
  padding: 5px 9px;
  border-radius: 4px;
  font-size: 12px;
}
.node-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.node-list button {
  padding: 6px 9px;
}
.node-list button.active {
  color: #3156a4;
  border-color: #3156a4;
}
.edge-list {
  display: grid;
  gap: 5px;
}
.edge-list button {
  text-align: left;
  line-height: 1.6;
}
.edge-list small {
  display: block;
  color: #8092aa;
}
.evidence-links {
  margin-top: 18px;
  display: grid;
  gap: 5px;
}
.evidence-links button {
  text-align: left;
  word-break: break-word;
}
.evidence-detail {
  background: #f6f8fc;
  border-radius: 8px;
  padding: 1px 12px 12px;
  margin-top: 16px;
}
.evidence-detail p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.graph-legend {
  padding: 16px 22px;
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #7a879a;
  border-top: 1px solid #e4ebf4;
}
.graph-legend span {
  display: flex;
  align-items: center;
  gap: 6px;
}
.graph-legend i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #72a7ff;
}
.graph-legend .TASK i {
  background: #49d7c5;
}
.graph-legend .ALGORITHM i {
  background: #b399ff;
}
.graph-legend .MODEL i {
  background: #ffc67d;
}
.graph-legend .METRIC i {
  background: #ff8fb5;
}
.legend-note {
  margin-left: auto;
}
.path-tools {
  border-top: 1px solid #e7ecf5;
  padding: 18px 22px;
  align-items: flex-end;
}
.path-tools > label:not(.checkbox) {
  flex: 1;
  max-width: 240px;
}
.checkbox {
  flex-direction: row;
  align-items: center;
  line-height: 1.6;
}
.checkbox input {
  width: auto;
}
.path-tools .checkbox {
  align-self: center;
}
.editor {
  padding: 22px;
  border-top: 1px solid #e1e8f3;
  border-bottom: 1px solid #e1e8f3;
  background: #f9fbff;
}
.create-panel {
  border: 1px solid #e1e8f3;
  border-radius: 12px;
  margin-bottom: 20px;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 15px;
  margin-top: 20px;
}
.helper {
  color: #7b899e;
  font-size: 12px;
}
.editor-heading {
  justify-content: space-between;
}
.edit-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 25px;
  margin-bottom: 20px;
}
.edit-columns form {
  display: grid;
  gap: 10px;
  align-content: start;
}
.edit-columns h4 {
  margin: 5px 0;
}
.editor details {
  padding: 13px 0;
  border-top: 1px solid #e0e7f2;
  font-size: 13px;
}
.editor summary {
  cursor: pointer;
  color: #46658f;
}
.source-excerpt {
  background: white;
  border: 1px solid #e3e9f3;
  border-radius: 7px;
  padding: 13px;
  margin-top: 10px;
  max-height: 220px;
  overflow: auto;
}
.source-excerpt strong,
.source-excerpt code {
  display: block;
  margin-bottom: 8px;
}
.source-excerpt code {
  overflow-wrap: anywhere;
  color: #7e8ba0;
  font-size: 11px;
}
.source-excerpt p {
  white-space: pre-wrap;
}
.publish-controls {
  margin-top: 18px;
}
.import-file {
  margin-top: 10px;
}
.job-state {
  margin-top: 15px;
  font-size: 13px;
  color: #7f6a35;
}
@media (max-width: 900px) {
  .graph-body {
    grid-template-columns: minmax(0, 1fr) 270px;
  }
  .graph-page {
    padding: 20px 15px;
  }
  .legend-note {
    margin-left: 0;
  }
}
@media (max-width: 700px) {
  .graph-page {
    padding: 15px 10px;
  }
  .graph-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }
  h1 {
    font-size: 24px;
  }
  .scope-bar {
    padding: 14px;
    gap: 13px;
  }
  .scope-bar label {
    max-width: none;
    min-width: 130px;
  }
  .graph-body {
    display: flex;
    flex-direction: column;
  }
  .graph-detail {
    border-left: 0;
    border-top: 1px solid #e3eaf4;
    max-height: none;
  }
  .graph-toolbar,
  .explore-tools,
  .graph-legend,
  .path-tools,
  .editor {
    padding: 16px;
  }
  .search-field {
    min-width: 0;
    max-width: none;
    width: 100%;
  }
  .form-grid,
  .edit-columns {
    grid-template-columns: minmax(0, 1fr);
  }
  .path-tools > label:not(.checkbox) {
    min-width: 115px;
  }
  .publish-controls {
    align-items: flex-start;
  }
  .publish-controls .checkbox {
    order: 3;
    width: 100%;
  }
  .scope-bar > button {
    flex: 1;
  }
}
</style>
