<template>
  <div class="student-knowledge-page">
    <div class="page-header"><div><h2>智能学伴</h2><p>查看课程资料、核实原文出处，让学习问答有据可查。</p></div><router-link class="chapter-graph-entry" to="/chapter-knowledge-graph">课程知识图谱</router-link></div>
    <NativeKnowledgeWorkspace />
  </div>
</template>
<script setup>
import NativeKnowledgeWorkspace from '@/components/knowledge/NativeKnowledgeWorkspace.vue'
</script>
<style scoped>
.student-knowledge-page { display:flex; flex-direction:column; gap:20px; }
.page-header { gap:16px; flex-wrap:wrap; }
.chapter-graph-entry { color:var(--primary-color,#2563eb); font-size:14px; text-decoration:none; white-space:nowrap; }
.page-header h2 { margin:0 0 8px; }
.page-header p { margin:0; color:var(--text-secondary,#64748b); font-size:14px; line-height:1.6; }
</style>
