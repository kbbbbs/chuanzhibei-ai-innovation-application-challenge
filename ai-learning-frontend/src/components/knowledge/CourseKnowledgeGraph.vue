<template>
  <section
    class="course-graph"
    data-testid="course-knowledge-graph"
    aria-label="课程知识图谱"
  >
    <header class="graph-head">
      <div>
        <h2>知识图谱</h2>
        <p v-if="graph.nodes.length">
          {{ graph.nodes.length }} 个知识点 ·
          {{ graph.relations.length }} 条关系
          <span v-if="graph.storage === 'NEO4J' && !graph.partial">
            · Neo4j 图数据库</span
          >
        </p>
      </div>
      <div class="graph-actions">
        <el-button @click="showExample = true">章节示范</el-button>
        <el-button
          v-if="canManage && !running"
          type="primary"
          :loading="submitting"
          :disabled="Boolean(chapterId) && !documentIds.length"
          @click="generate"
          >{{ generateLabel }}</el-button
        >
        <el-button v-if="canManage && running" :loading="stopping" @click="stop"
          >停止生成</el-button
        >
      </div>
    </header>

    <div class="graph-scope">
      <el-select
        v-model="chapterId"
        aria-label="图谱范围"
        @change="changeScope"
      >
        <el-option label="整门课程" :value="0" />
        <el-option
          v-for="chapter in scopeOptions.chapters"
          :key="chapter.id"
          :label="chapter.title"
          :value="chapter.id"
        />
      </el-select>
      <el-select
        v-if="chapterId && canManage"
        v-model="documentIds"
        multiple
        collapse-tags
        collapse-tags-tooltip
        aria-label="章节资料"
        placeholder="选择本章对应资料"
        :disabled="running"
      >
        <el-option
          v-for="document in scopeOptions.documents"
          :key="document.id"
          :value="document.id"
          :label="`${document.title} · ${document.chunks} 个片段`"
        />
      </el-select>
      <span v-if="chapterId && canManage"
        >仅处理所选资料，已完成片段自动复用。</span
      >
    </div>
    <p v-if="scopeError" class="graph-status" role="status">{{ scopeError }}</p>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button v-if="!forbidden" link @click="load">重新加载</el-button>
    </el-alert>
    <p v-if="running" class="graph-status" role="status">
      正在并行处理{{ chapterId ? "本章所选资料" : "全部可用课程资料"
      }}<span v-if="progressTotal"
        >，已处理 {{ progressCurrent }}/{{ progressTotal }} 个资料片段</span
      >。可以离开页面，稍后返回查看。
    </p>
    <p v-if="graph.partial" class="graph-status" role="status">
      当前展示已完成部分，生成结束后可查询相邻知识与关联路径。
    </p>
    <p v-if="job.reusedChunks" class="graph-status">
      已复用 {{ job.reusedChunks }} 个片段的抽取结果。
    </p>
    <el-alert
      v-if="jobError"
      :title="jobError"
      type="warning"
      :closable="false"
      show-icon
    />
    <p v-if="!running && retryable && savedChunks" class="graph-status">
      已保存 {{ savedChunks }}/{{ progressTotal }} 个资料片段，可继续生成。
    </p>
    <el-alert
      v-if="graph.stale"
      title="部分课程资料已更新，当前图谱仅展示仍可访问的来源。"
      type="info"
      :closable="false"
    />
    <p v-if="sourceSummary" class="graph-source-summary">{{ sourceSummary }}</p>

    <div v-loading="loading" class="graph-content">
      <el-empty
        v-if="!loading && !error && !graph.nodes.length"
        :description="emptyDescription"
      />
      <template v-if="graph.nodes.length">
        <div class="graph-controls">
          <el-input
            v-model="search"
            clearable
            placeholder="搜索知识点"
            aria-label="搜索知识点"
          />
          <el-select v-model="relationType" aria-label="关系类型">
            <el-option label="全部关系" value="" />
            <el-option
              v-for="type in relationTypes"
              :key="type"
              :label="relationLabels[type] || type"
              :value="type"
            />
          </el-select>
        </div>
        <div class="path-controls" aria-label="知识关联路径">
          <el-select
            v-model="pathFrom"
            filterable
            placeholder="起点知识点"
            aria-label="起点知识点"
          >
            <el-option
              v-for="node in graph.nodes"
              :key="node.id"
              :label="node.label"
              :value="node.id"
            />
          </el-select>
          <span aria-hidden="true">→</span>
          <el-select
            v-model="pathTo"
            filterable
            placeholder="终点知识点"
            aria-label="终点知识点"
          >
            <el-option
              v-for="node in graph.nodes"
              :key="node.id"
              :label="node.label"
              :value="node.id"
            />
          </el-select>
          <el-button
            :disabled="
              graph.partial || !pathFrom || !pathTo || pathFrom === pathTo
            "
            :loading="querying"
            @click="findPath"
            >查找关联路径</el-button
          >
          <el-button v-if="pathIds.length" link @click="clearPath"
            >清除路径</el-button
          >
        </div>
        <p v-if="queryNotice" role="status" class="graph-status">
          {{ queryNotice }}
        </p>
        <div v-if="search.trim()" class="search-matches">
          <el-button
            v-for="node in matchingNodes"
            :key="node.id"
            size="small"
            @click="selectNode(node.id)"
            >{{ node.label }}</el-button
          >
          <span v-if="!matchingNodes.length">未找到匹配知识点</span>
        </div>
        <div class="graph-layout">
          <div class="graph-stage" aria-label="知识点关系画布">
            <KnowledgeGraphCanvas
              class="course-graph-canvas"
              ref="network"
              :title="graph.title || '课程知识图谱'"
              :nodes="canvasNodes"
              :edges="canvasEdges"
              :selected-id="selectedRelationId || selectedId"
              :relation-type="relationType"
              :path-ids="pathIds"
              @node-select="selectNode"
              @edge-select="selectRelation"
              @clear-selection="clearSelection"
            />
          </div>
          <aside class="graph-details">
            <template v-if="selectedRelation">
              <h3>
                {{ nodeLabel(selectedRelation.source) }} →
                {{ nodeLabel(selectedRelation.target) }}
              </h3>
              <p class="node-description">{{ selectedRelation.label }}</p>
              <RagCitations
                :course-id="courseId"
                :citations="selectedRelation.citations || []"
              />
            </template>
            <template v-if="selectedNode">
              <h3>{{ selectedNode.label }}</h3>
              <p class="node-description">
                {{ selectedNode.description || "查看关联关系与参考资料。" }}
              </p>
              <el-button type="primary" plain @click="askAboutNode"
                >围绕此知识点提问</el-button
              >
              <el-button
                :loading="querying"
                :disabled="graph.partial"
                @click="exploreNode"
                >查看相邻知识</el-button
              >
              <RagCitations
                :course-id="courseId"
                :citations="selectedNode.citations || []"
              />
            </template>
            <h3>{{ selectedNode ? "关联关系" : "关系明细" }}</h3>
            <el-empty
              v-if="!visibleRelations.length"
              description="暂无关联关系"
              :image-size="45"
            />
            <article
              v-for="relation in visibleRelations"
              :key="
                relation.id ||
                `${relation.source}-${relation.target}-${relation.label}`
              "
              class="relation-row"
            >
              <button @click="selectNode(relation.source)">
                {{ nodeLabel(relation.source) }}
              </button>
              <span>{{ relation.label || "关联" }} →</span>
              <button @click="selectNode(relation.target)">
                {{ nodeLabel(relation.target) }}
              </button>
              <RagCitations
                v-if="selectedNode || pathIds.length || selectedRelation"
                :course-id="courseId"
                :citations="relation.citations || []"
              />
            </article>
          </aside>
        </div>
      </template>
    </div>

    <el-dialog
      v-model="showExample"
      title="机器学习概述 · 章节示范"
      width="min(1250px, 96vw)"
      destroy-on-close
    >
      <p class="example-note">
        此章节示范来自 d6bb3d9，用于体验原版图谱的动态展示与交互。
      </p>
      <KnowledgeGraphCanvas
        class="course-graph-canvas"
        :title="chapterExample.title"
        :nodes="exampleNodes"
        :edges="exampleEdges"
      />
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import KnowledgeGraphCanvas from "./KnowledgeGraphCanvas.vue";
import { useUserStore } from "@/store/user";
import { useAssistant } from "@/composables/useAssistant";
import {
  cancelCourseGraph,
  generateCourseGraph,
  getCourseGraph,
  getGraphScopeOptions,
  getGraphNeighbors,
  getGraphPath,
} from "@/api/courseGraph";
import { chapterGraph as chapterExample } from "@/data/machineLearningChapterGraph";
import RagCitations from "./RagCitations.vue";

const props = defineProps({ courseId: { type: Number, required: true } });
const user = useUserStore(),
  router = useRouter(),
  assistant = useAssistant();
const route = useRoute();
const chapterId = ref(Number(route.query.graphChapter) || 0);
const documentIds = ref([]);
const scopeOptions = ref({ chapters: [], documents: [] });
const scopeError = ref("");
const graph = ref({ nodes: [], relations: [], canManage: false });
const loading = ref(false),
  submitting = ref(false),
  stopping = ref(false);
const error = ref(""),
  forbidden = ref(false),
  search = ref(""),
  selectedId = ref("");
const relationType = ref(""),
  network = ref(null),
  selectedRelationId = ref(""),
  showExample = ref(false);
const pathFrom = ref(""),
  pathTo = ref(""),
  pathIds = ref([]),
  querying = ref(false),
  queryNotice = ref("");
const relationLabels = {
  HAS_CHAPTER: "包含章节",
  COVERS: "涉及知识",
  IS_A: "属于",
  HAS_TASK: "任务类型",
  HAS_EXAMPLE: "示例",
  USED_FOR: "用于",
  MEASURED_BY: "评价指标",
  PREREQUISITE_FOR: "先修关系",
  RELATED_TO: "相关知识",
};
const exampleNodes = chapterExample.nodes.map((node) => ({
  ...node,
  name: node.label,
  type: ["optimization", "gradient"].includes(node.id)
    ? "ALGORITHM"
    : node.id === "model"
      ? "MODEL"
      : "CONCEPT",
}));
const exampleEdges = chapterExample.relations.map((edge) => ({
  ...edge,
  type: edge.kind === "learning" ? "PREREQUISITE_FOR" : "RELATED_TO",
}));
let version = 0,
  timer = null,
  controller = null,
  querySequence = 0;
const job = computed(() => graph.value.job || {});
const running = computed(() =>
  ["RUNNING", "PENDING", "QUEUED", "GENERATING"].includes(
    String(job.value.status || graph.value.status || "").toUpperCase(),
  ),
);
const canManage = computed(
  () => user.isTeacher && graph.value.canManage === true,
);
const retryable = computed(() =>
  ["FAILED", "INTERRUPTED", "CANCELLED"].includes(job.value.status),
);
const savedChunks = computed(() => Number(job.value.savedChunks || 0));
const generateLabel = computed(() =>
  retryable.value
    ? savedChunks.value
      ? "继续生成"
      : "重新生成"
    : graph.value.nodes.length
      ? "更新知识图谱"
      : "生成知识图谱",
);
const emptyDescription = computed(() => {
  if (running.value) return "图谱生成中";
  if (!canManage.value) return "当前课程暂无已开放的知识图谱";
  if (retryable.value && savedChunks.value)
    return "已保存部分进度，点击继续生成完成知识图谱。";
  if (retryable.value) return "上次生成未完成，点击重新生成。";
  if (job.value.status === "COMPLETED")
    return "已处理全部可用资料，未提取到知识点，可更新资料后重新生成。";
  return "点击生成知识图谱，使用课程中已处理完成的资料。";
});
const jobError = computed(() =>
  ["FAILED", "INTERRUPTED", "CANCELLED"].includes(
    String(job.value.status).toUpperCase(),
  )
    ? job.value.error ||
      job.value.errorMessage ||
      (job.value.status === "CANCELLED"
        ? "图谱生成已停止，可以重新生成。"
        : "图谱生成未完成，请重新生成。")
    : "",
);
const progressCurrent = computed(() =>
  Number(
    job.value.currentChunk ?? job.value.current_chunk ?? job.value.current ?? 0,
  ),
);
const progressTotal = computed(() =>
  Number(
    job.value.totalChunks ?? job.value.total_chunks ?? job.value.total ?? 0,
  ),
);
const sourceSummary = computed(() => {
  if (!graph.value.generatedAt && !graph.value.nodes.length) return "";
  const summary = graph.value.sourceSummary;
  if (typeof summary === "string") return summary;
  if (!summary || typeof summary !== "object") return "";
  const included =
    summary.includedChunks ?? summary.included_chunks ?? summary.included ?? 0;
  const available =
    summary.availableChunks ??
    summary.available_chunks ??
    summary.available ??
    included;
  const docs =
    summary.includedDocuments ?? summary.includedDocs ?? summary.included_docs;
  const processing =
    summary.processingDocuments ??
    summary.processingDocs ??
    summary.processing_docs ??
    0;
  return `${docs == null ? "" : `已使用 ${docs} 份资料，`}已覆盖片段 ${included}/${available}${summary.truncated ? "，本次采用部分片段生成" : ""}${processing ? `，另有 ${processing} 份资料正在处理` : ""}`;
});
const selectedNode = computed(() =>
  graph.value.nodes.find(
    (node) => String(node.id) === String(selectedId.value),
  ),
);
const matchingNodes = computed(() =>
  graph.value.nodes
    .filter((node) =>
      `${node.label} ${node.description || ""}`
        .toLowerCase()
        .includes(search.value.trim().toLowerCase()),
    )
    .slice(0, 30),
);
const visibleRelations = computed(() =>
  graph.value.relations.filter(
    (relation) =>
      (!relationType.value ||
        (relation.type || "RELATED_TO") === relationType.value) &&
      (pathIds.value.length
        ? pathIds.value.includes(relation.id)
        : selectedRelationId.value
          ? relation.id === selectedRelationId.value
          : !selectedId.value ||
            String(relation.source) === String(selectedId.value) ||
            String(relation.target) === String(selectedId.value)),
  ),
);
const nodeLabel = (id) =>
  graph.value.nodes.find((node) => String(node.id) === String(id))?.label ||
  "知识点";
const selectedRelation = computed(() =>
  graph.value.relations.find((item) => item.id === selectedRelationId.value),
);
const relationTypes = computed(() => [
  ...new Set(graph.value.relations.map((item) => item.type || "RELATED_TO")),
]);
const canvasNodes = computed(() =>
  graph.value.nodes.map((node) => ({
    ...node,
    name: node.label,
    type: ["CONCEPT", "TASK", "ALGORITHM", "MODEL", "METRIC"].includes(
      node.type || node.category,
    )
      ? node.type || node.category
      : "CONCEPT",
  })),
);
const canvasEdges = computed(() =>
  graph.value.relations.map((edge, index) => ({
    ...edge,
    id: String(edge.id || `relation-${index}`),
    type: edge.type || "RELATED_TO",
  })),
);

function clearSelection() {
  selectedId.value = "";
  selectedRelationId.value = "";
  clearPath();
}
function selectNode(id) {
  if (querying.value) clearPath();
  selectedRelationId.value = "";
  selectedId.value = String(id);
  nextTick(() => network.value?.focus(String(id)));
}
function selectRelation(id) {
  selectedId.value = "";
  selectedRelationId.value = String(id);
  nextTick(() => network.value?.focusRelation(String(id)));
}
function clearPath() {
  querySequence++;
  pathIds.value = [];
  queryNotice.value = "";
  querying.value = false;
}
async function queryGraph(request) {
  const current = version;
  const sequence = ++querySequence;
  querying.value = true;
  queryNotice.value = "";
  try {
    const result = await request();
    if (current !== version || sequence !== querySequence) return;
    pathIds.value = [
      ...result.nodes.map((node) => node.id),
      ...result.relations.map((edge) => edge.id),
    ];
    queryNotice.value = pathIds.value.length
      ? `已找到 ${result.relations.length} 条关联关系${result.truncated ? "，本次仅显示部分结果，可缩小关系范围继续查看" : ""}`
      : "未找到可访问的关联路径";
  } catch (problem) {
    if (current === version && sequence === querySequence)
      queryNotice.value = problem.message || "关联查询失败，请重试";
  } finally {
    if (current === version && sequence === querySequence)
      querying.value = false;
  }
}
function exploreNode() {
  if (selectedId.value)
    queryGraph(() =>
      getGraphNeighbors(
        props.courseId,
        selectedId.value,
        1,
        relationType.value,
        chapterId.value || undefined,
      ),
    );
}
function findPath() {
  queryGraph(() =>
    getGraphPath(
      props.courseId,
      pathFrom.value,
      pathTo.value,
      chapterId.value || undefined,
    ),
  );
}
watch([pathFrom, pathTo, relationType], clearPath);
function askAboutNode() {
  if (!selectedNode.value) return;
  assistant.stateFor({ courseId: props.courseId }).draft =
    `请结合本课程资料解释“${selectedNode.value.label}”，并说明它与相关知识点的关系。`;
  router.push({
    path: "/knowledge-base",
    query: { courseId: String(props.courseId) },
  });
}
function schedule() {
  clearTimeout(timer);
  if (running.value) timer = setTimeout(() => load(false), 1200);
}
async function load(initial = true) {
  clearTimeout(timer);
  const current = version,
    courseId = props.courseId;
  controller?.abort();
  controller = new AbortController();
  if (initial) loading.value = true;
  error.value = "";
  forbidden.value = false;
  try {
    const result = await getCourseGraph(courseId, {
      signal: controller.signal,
      params: chapterId.value ? { chapterId: chapterId.value } : {},
    });
    if (current !== version || courseId !== props.courseId) return;
    graph.value = {
      ...result,
      nodes: Array.isArray(result.nodes) ? result.nodes : [],
      relations: Array.isArray(result.relations) ? result.relations : [],
    };
    if (initial) documentIds.value = result.documentIds || [];
    if (!selectedNode.value) selectedId.value = "";
    schedule();
  } catch (problem) {
    if (current !== version || problem.code === "ERR_CANCELED") return;
    graph.value = { nodes: [], relations: [], canManage: false };
    selectedId.value = "";
    error.value =
      problem?.response?.data?.msg ||
      problem.message ||
      "知识图谱读取失败，请重试";
    forbidden.value =
      String(problem?.response?.status || problem.code) === "403";
  } finally {
    if (current === version) loading.value = false;
  }
}
async function generate() {
  const current = version,
    courseId = props.courseId;
  submitting.value = true;
  error.value = "";
  try {
    const requestId =
      globalThis.crypto?.randomUUID?.() ||
      `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const scope = chapterId.value
      ? { chapterId: chapterId.value, documentIds: documentIds.value }
      : {};
    const created = await generateCourseGraph(courseId, requestId, scope);
    if (current !== version || courseId !== props.courseId) return;
    graph.value = { ...graph.value, job: created.job || created };
    await load(false);
  } catch (problem) {
    if (current === version)
      error.value =
        problem?.response?.data?.msg ||
        problem.message ||
        "无法开始生成，请重试";
  } finally {
    if (current === version) submitting.value = false;
  }
}
async function stop() {
  if (!job.value.id) return;
  const current = version,
    courseId = props.courseId,
    jobId = job.value.id;
  stopping.value = true;
  try {
    await cancelCourseGraph(courseId, jobId);
    if (current === version && courseId === props.courseId) await load(false);
  } catch (problem) {
    if (current === version)
      error.value =
        problem?.response?.data?.msg || problem.message || "停止失败，请重试";
  } finally {
    if (current === version) stopping.value = false;
  }
}
async function changeScope() {
  version++;
  controller?.abort();
  clearTimeout(timer);
  clearSelection();
  documentIds.value = [];
  graph.value = { nodes: [], relations: [], canManage: false };
  const query = { ...route.query };
  if (chapterId.value) query.graphChapter = String(chapterId.value);
  else delete query.graphChapter;
  await router.replace({ query });
  load();
}
async function loadScopeOptions() {
  const courseId = props.courseId;
  scopeError.value = "";
  try {
    const result = await getGraphScopeOptions(courseId);
    if (courseId === props.courseId)
      scopeOptions.value = {
        chapters: result.chapters || [],
        documents: result.documents || [],
      };
  } catch (problem) {
    if (courseId === props.courseId)
      scopeError.value = problem.message || "章节资料范围读取失败";
  }
}
watch(
  () => route.query.graphChapter,
  (value) => {
    const target = Number(value) || 0;
    if (target === chapterId.value) return;
    chapterId.value = target;
    version++;
    clearSelection();
    graph.value = { nodes: [], relations: [], canManage: false };
    load();
  },
);
watch(
  () => props.courseId,
  () => {
    version++;
    clearSelection();
    pathFrom.value = "";
    pathTo.value = "";
    querying.value = false;
    search.value = "";
    submitting.value = false;
    stopping.value = false;
    chapterId.value = Number(route.query.graphChapter) || 0;
    documentIds.value = [];
    scopeOptions.value = { chapters: [], documents: [] };
    graph.value = { nodes: [], relations: [], canManage: false };
    loadScopeOptions();
    load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  version++;
  clearTimeout(timer);
  controller?.abort();
});
</script>

<style scoped>
.course-graph {
  min-width: 0;
}
.graph-scope {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}
.graph-scope .el-select {
  width: min(340px, 100%);
}
.graph-scope span {
  color: var(--text-secondary);
  font-size: 13px;
}
.course-graph-canvas :deep(.presentation-heading h2) {
  color: #e9f1ff;
}
.graph-head,
.graph-actions,
.graph-controls {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.path-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.path-controls .el-select {
  width: min(220px, 100%);
}
.graph-stage :deep(.network-wrap:not(.is-presenting)) {
  border-radius: var(--radius-lg);
}
.graph-head {
  justify-content: space-between;
  margin-bottom: 18px;
}
.graph-head h2 {
  margin: 0;
}
.graph-head p,
.graph-source-summary,
.graph-status,
.example-note {
  color: var(--text-muted);
}
.graph-actions .el-button + .el-button {
  margin-left: 0;
}
.graph-controls {
  margin: 18px 0;
}
.graph-controls .el-input {
  width: min(280px, 100%);
}
.graph-controls .el-select {
  width: 150px;
}
.graph-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  gap: 18px;
}
.graph-stage {
  min-width: 0;
  border: 1px solid var(--line-default);
  border-radius: var(--radius-lg);
  background: var(--surface-base);
}
.graph-details {
  min-width: 0;
  max-height: 640px;
  overflow: auto;
  padding: 16px;
  border: 1px solid var(--line-default);
  border-radius: var(--radius-lg);
  background: var(--surface-base);
}
.graph-details h3 {
  margin: 0 0 14px;
}
.graph-details h3:not(:first-child) {
  margin-top: 24px;
}
.node-description {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.7;
}
.relation-row {
  margin: 12px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line-default);
  overflow-wrap: anywhere;
}
.relation-row button {
  border: 0;
  background: transparent;
  color: var(--accent);
  padding: 3px;
  cursor: pointer;
  font: inherit;
}
.search-matches {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
@media (max-width: 950px) {
  .graph-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .graph-stage {
    min-width: 0;
  }
  .graph-details {
    max-height: none;
  }
}
@media (max-width: 600px) {
  .graph-head {
    align-items: flex-start;
  }
  .graph-controls {
    gap: 8px;
  }
  .graph-stage {
    min-width: 0;
  }
  .graph-actions {
    gap: 6px;
  }
}
</style>
