<template>
  <section class="ai-note-assistant">
    <strong>笔记助手</strong>
    <div class="note-ai-actions">
      <el-button
        v-for="task in tasks"
        :key="task.type"
        size="small"
        :disabled="!state.ready || Boolean(state.requestId)"
        @click="run(task)"
        >{{ task.label }}</el-button
      >
      <el-button
        v-if="state.requestId"
        size="small"
        @click="assistant.cancel(state)"
        >停止生成</el-button
      >
    </div>
    <p v-if="state.generating" role="status">
      正在处理，可切换到课时快问查看进度
    </p>
    <el-alert
      v-if="state.error"
      :title="state.error"
      type="error"
      :closable="false"
      ><el-button v-if="state.canRetry" @click="assistant.retry(state)"
        >重试</el-button
      ></el-alert
    >
    <div v-if="result" class="note-ai-result">
      <template v-if="result.sections?.length">
        <section v-for="(section, index) in result.sections" :key="index">
          <strong>{{ assistantSectionLabel(section.type) }}</strong>
          <div v-html="renderMarkdown(section.content || '')" />
        </section>
      </template>
      <div v-else v-html="renderMarkdown(result.content || '')" />
      <RagCitations :course-id="courseId" :citations="result.citations || []" />
      <el-button
        size="small"
        :disabled="appendedId === result.id"
        @click="append"
        >{{
          appendedId === result.id ? '已追加到笔记' : '追加到笔记'
        }}</el-button
      >
    </div>
  </section>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { useAssistant, assistantSectionLabel } from '@/composables/useAssistant'
import RagCitations from '@/components/knowledge/RagCitations.vue'
import { renderMarkdown } from '@/utils/markdown'
const props = defineProps({
  courseId: Number,
  lessonId: Number,
  lessonTitle: String,
  noteContent: String,
  videoSummaryMd: String,
  currentVideoTime: Number,
  hasVideo: Boolean,
})
const emit = defineEmits(['append-result'])
const assistant = useAssistant(),
  requested = ref(false),
  appendedId = ref(null),
  taskMessageIndex = ref(0)
const scope = computed(() => ({
  courseId: props.courseId,
  lessonId: props.lessonId,
}))
const state = computed(() => assistant.stateFor(scope.value))
const tasks = computed(() => [
  {
    type: 'summarize_note',
    label: '总结',
    prompt: '请总结当前笔记，整理要点。',
  },
  {
    type: 'explain_selection',
    label: '解释',
    prompt: '请解释选中的内容，未选中文字时解释笔记中的主要概念。',
  },
  ...(props.hasVideo
    ? [
        {
          type: 'outline_from_video',
          label: '提纲',
          prompt: '请结合当前课时和提供的笔记整理学习提纲。',
        },
      ]
    : []),
  {
    type: 'quiz_from_note',
    label: '练习题',
    prompt: '请根据当前笔记和课时生成练习题，并附答案和解析。',
  },
])
const result = computed(() =>
  requested.value && !state.value.generating
    ? state.value.messages
        .slice(taskMessageIndex.value)
        .filter((item) => item.role === 'assistant' && item.content)
        .at(-1)
    : null,
)
watch(
  () => [props.courseId, props.lessonId],
  async () => {
    requested.value = false
    try {
      await assistant.open(scope.value)
    } catch {
      /* visible state error */
    }
  },
  { immediate: true },
)
async function run(task) {
  requested.value = true
  appendedId.value = null
  taskMessageIndex.value = state.value.messages.length
  try {
    await assistant.send(scope.value, task.prompt, task.type, {
      noteContent: props.noteContent || '',
      selectedText: window.getSelection?.().toString()?.trim() || '',
      currentVideoTime: props.currentVideoTime || 0,
    })
  } catch {
    /* shared error and retry */
  }
}
function append() {
  if (result.value && appendedId.value !== result.value.id) {
    const text = result.value.sections?.length
      ? result.value.sections
          .map(
            (section) =>
              `### ${assistantSectionLabel(section.type)}\n\n${section.content}`,
          )
          .join('\n\n')
      : result.value.content
    emit('append-result', text)
    appendedId.value = result.value.id
  }
}
</script>
<style scoped>
.ai-note-assistant {
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid var(--line-subtle);
  border-radius: var(--radius-md);
}
.note-ai-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}
.note-ai-actions .el-button {
  margin-left: 0;
}
.note-ai-result {
  margin-top: 12px;
  overflow-wrap: anywhere;
}
:deep(pre) {
  overflow: auto;
}
</style>
