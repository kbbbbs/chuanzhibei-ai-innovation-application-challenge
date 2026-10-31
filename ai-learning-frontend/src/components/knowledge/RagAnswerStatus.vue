<template>
  <p v-if="modeLabel" class="answer-status" data-testid="answer-retrieval-mode">{{ effectiveMode === 'NONE' ? modeLabel : `本次检索：${modeLabel}` }}</p>
  <p v-if="text" class="answer-status">{{ text }}</p>
  <el-alert v-if="warningText" :title="warningText" type="warning" :closable="false" show-icon class="answer-warning" />
</template>
<script setup>
import { computed } from 'vue'
import { actualRetrievalMode, retrievalModeLabel, retrievalWarning } from '@/utils/retrievalStatus'
const props = defineProps({ status:String, warning:String, mode:String, actualMode:String, degradedReason:String })
const effectiveMode = computed(() => actualRetrievalMode(props.actualMode, props.mode))
const modeLabel = computed(() => retrievalModeLabel(props.actualMode, props.mode))
const warningText = computed(() => retrievalWarning(props.degradedReason, props.warning))
const text = computed(() => ({
  NO_EVIDENCE:'资料中未找到足够依据，可以调整问题或添加资料。',
  CITATION_CHECK_FAILED:'回答引用未通过校验，当前展示本次检索的原文摘录，请核实后使用。',
  MODEL_UNAVAILABLE:'模型暂不可用，请稍后重试。'
}[props.status] || ''))
</script>
<style scoped>
.answer-status { font-size:12px; color:#64748b; line-height:1.6; }.answer-warning { margin-top:10px; }
</style>
