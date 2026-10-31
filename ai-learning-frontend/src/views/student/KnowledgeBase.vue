<template>
  <div class="student-knowledge-page">
    <div class="page-header"><h2>智能学伴</h2><p>查看课程资料、核实原文出处，让学习问答有据可查。</p></div>
    <NativeKnowledgeWorkspace />
  </div>
</template>
<script setup>
import NativeKnowledgeWorkspace from '@/components/knowledge/NativeKnowledgeWorkspace.vue'
</script>
<style scoped>
.student-knowledge-page { display:flex; flex-direction:column; gap:20px; }
.page-header h2 { margin:0 0 8px; }
.page-header p { margin:0; color:var(--text-secondary,#64748b); font-size:14px; line-height:1.6; }
</style>
