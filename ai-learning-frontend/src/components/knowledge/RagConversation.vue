<template>
  <div ref="container" class="conversation" aria-live="polite">
    <el-empty v-if="!messages.length" :description="emptyText" />
    <article v-for="(message, index) in messages" :key="message.id || index" class="chat-message" :class="message.role">
      <strong class="message-role">{{ message.role === 'user' ? '你' : '学习助手' }}</strong>
      <blockquote v-if="message.selectedText" class="message-selection">{{ message.selectedText }}</blockquote>
      <div v-if="message.content" v-html="renderMarkdown(message.content)"></div>
      <p v-if="message.streaming" class="muted">{{ progress || '正在生成…' }}</p>
      <el-alert v-if="message.error" :title="message.errorMessage || '回答未完成，请重试'" type="error" :closable="false" />
      <p v-if="message.status === 'cancelled'" class="muted">已停止生成，此回答未完成。</p>
      <p v-if="message.status === 'no_hits'" class="muted">资料中未找到相关内容，可以调整问题或添加资料。</p>
      <p v-if="message.status === 'extractive' || message.status === 'model_unavailable'" class="muted">模型暂不可用，以下为检索到的原文资料。</p>
      <RagCitations :citations="message.citations || []" :course-id="courseId" />
      <RagAnswerStatus :status="message.status" :warning="message.warning" :mode="message.mode" :actual-mode="message.actualMode" :degraded-reason="message.degradedReason" />
    </article>
  </div>
</template>
<script setup>
import { nextTick, ref, watch } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import RagCitations from './RagCitations.vue'
import RagAnswerStatus from './RagAnswerStatus.vue'
const props = defineProps({ messages: { type: Array, default: () => [] }, progress: String, courseId: Number, emptyText: { type: String, default: '向课程资料提问，回答会附原文出处' } })
const container = ref(null)
watch(() => props.messages, async () => { await nextTick(); if (container.value) container.value.scrollTop = container.value.scrollHeight }, { deep: true })
</script>
<style scoped>
.conversation { min-height:260px; max-height:500px; overflow:auto; padding:12px 0; }
.chat-message { padding:14px; margin:10px 0; border:1px solid #e6ebf2; border-radius:12px; overflow-wrap:anywhere; line-height:1.7; }
.chat-message.user { margin-left:40px; background:#eff6ff; }
.message-role { color:#334155; font-size:13px; }
.message-selection { margin:10px 0; border-left:3px solid #93c5fd; padding-left:10px; color:#475569; white-space:pre-wrap; }
.muted { font-size:12px; color:#64748b; }
:deep(pre) { white-space:pre-wrap; overflow-wrap:anywhere; }
:deep(img) { max-width:100%; }
</style>
