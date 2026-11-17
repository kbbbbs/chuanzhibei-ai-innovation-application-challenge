<template>
  <details
    v-if="paths.length"
    class="graph-evidence-paths"
    data-testid="graph-evidence-paths"
  >
    <summary>图谱关联依据</summary>
    <div
      v-for="(path, index) in paths"
      :key="index"
      class="graph-evidence-path"
    >
      <p
        v-for="(relation, relationIndex) in path.relations || []"
        :key="relationIndex"
      >
        <strong>{{ nodeLabel(path, relation.source) }}</strong>
        <span> {{ relation.label || '关联' }} → </span>
        <strong>{{ nodeLabel(path, relation.target) }}</strong>
      </p>
      <p v-if="!path.relations?.length">
        {{ (path.nodes || []).map((node) => node.label).join(' → ') }}
      </p>
      <RagCitations :course-id="courseId" :citations="path.citations || []" />
    </div>
  </details>
</template>

<script setup>
import RagCitations from '@/components/knowledge/RagCitations.vue'
defineProps({ paths: { type: Array, default: () => [] }, courseId: Number })
const nodeLabel = (path, id) =>
  path.nodes?.find((node) => String(node.id) === String(id))?.label ||
  '相关知识点'
</script>

<style scoped>
.graph-evidence-paths {
  margin: 12px 0;
  padding: 12px;
  background: var(--surface-muted);
  border-radius: var(--radius-md);
}
summary {
  cursor: pointer;
  color: var(--text-body);
}
.graph-evidence-path {
  margin-top: 12px;
  overflow-wrap: anywhere;
}
.graph-evidence-path p {
  margin: 6px 0;
}
</style>
