<template>
  <el-card class="quick-chat-card"
    ><template #header><strong>课时快问</strong></template
    ><AssistantConversation
      :course-id="courseId"
      :lesson-id="lessonId"
      :lesson-title="lessonTitle"
      :task-context="{ currentVideoTime, selectedText: '' }"
      compact
  /></el-card>
</template>
<script setup>
import AssistantConversation from '@/components/assistant/AssistantConversation.vue'
defineProps({
  courseId: Number,
  lessonId: Number,
  lessonTitle: String,
  noteContent: String,
  videoSummaryMd: String,
  currentVideoTime: Number,
})
</script>
<style scoped>
.quick-chat-card {
  height: 650px;
}
:deep(.el-card__body) {
  height: calc(100% - 62px);
  box-sizing: border-box;
}
</style>
