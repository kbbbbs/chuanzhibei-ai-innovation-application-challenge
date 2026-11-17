<template>
  <details v-if="events.length" class="kernel-panel" :open="!initiallyCollapsed">
    <summary>Agent 运行过程</summary>
    <ol v-if="events.length" class="kernel-timeline" aria-label="Agent 实际运行状态">
      <li v-for="event in events" :key="event.sequence" data-testid="kernel-event">
        <strong>{{ event.sequence }} · {{ awaitingConfirmation && event.state === 'COMPLETED' ? '方案已生成，等待确认' : kernelStateLabels[event.state] }}</strong>
        <span v-if="event.result?.itemCount != null" class="kernel-time">返回 {{ event.result.itemCount }} 项</span>
      </li>
    </ol>
    <details v-if="events.length" class="kernel-detail">
      <summary>运行明细（工具、结果 ID 与耗时）</summary>
      <ol class="kernel-timeline">
        <li v-for="event in events" :key="event.sequence">
          <span>{{ event.sequence }}</span>
          <code v-if="event.toolName">{{ event.toolName }}</code>
          <span class="kernel-time">{{ (event.elapsedMillis / 1000).toFixed(2) }} 秒</span>
          <div v-if="event.result?.entityIds?.length" class="kernel-tool-result">结果 ID（最多 10 项）：{{ event.result.entityIds.join('、') }}</div>
        </li>
      </ol>
    </details>
  </details>
</template>

<script setup>
// Timeline/detail markup and styles restored from a1b688c AgentKernelPanel.
// The unified conversation owns the run; this component only displays its events.
import { kernelStateLabels } from '@/utils/kernelStream'
defineProps({ events: { type: Array, default: () => [] }, initiallyCollapsed: Boolean, awaitingConfirmation: Boolean })
</script>

<style scoped>
.kernel-panel { flex: 0 1 auto; min-height: 0; max-height: 60%; overflow-y: auto; margin: 10px 16px; padding: 12px 16px; color: var(--text-body); border: 1px solid var(--line-subtle); border-radius: var(--radius-md); background: var(--surface-canvas); }
.kernel-panel > summary { font-weight: 650; color: var(--text-strong); cursor: pointer; }
.kernel-timeline { max-height: 300px; overflow: auto; padding: 0; list-style: none; font-size: var(--text-sm); }
.kernel-tool-result { margin: 4px 0 0 20px; color: var(--text-subtle); overflow-wrap: anywhere; }
.kernel-timeline li { padding: 5px 0; }
.kernel-timeline code { display: inline-block; margin: 0 10px; color: var(--accent); overflow-wrap: anywhere; }
.kernel-time { margin-left: 12px; color: var(--text-subtle); font-size: var(--text-xs); }
.kernel-detail { margin: 4px 0 8px; font-size: var(--text-xs); }
.kernel-detail > summary { color: var(--text-subtle); cursor: pointer; }
/* The original panel now belongs to a message, rather than owning another chat. */
.kernel-panel { margin: 10px 0; max-height: none; }
</style>
