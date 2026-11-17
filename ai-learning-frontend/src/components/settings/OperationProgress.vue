<template>
  <section class="operation-progress surface-card" aria-labelledby="operation-heading">
    <div class="progress-heading">
      <div>
        <h2 id="operation-heading">{{ headline.title }}</h2>
        <p>{{ headline.detail }}</p>
      </div>
      <StatusBadge :label="headline.badge" :tone="headline.tone" />
    </div>

    <p class="visually-hidden" role="status" aria-live="polite">{{ headline.title }}</p>

    <ol v-if="showTimeline" class="progress-steps">
      <li v-for="step in steps" :key="step.status" :class="`is-${step.state}`">
        <span class="step-marker" aria-hidden="true"></span>
        <span>{{ step.label }}</span>
        <span class="visually-hidden">（{{ stepStateLabel[step.state] }}）</span>
      </li>
    </ol>

    <dl v-if="report || targetRevision" class="operation-meta">
      <div>
        <dt>当前阶段</dt>
        <dd>{{ phaseLabel }}</dd>
      </div>
      <div>
        <dt>目标版本</dt>
        <dd><code>{{ report?.targetRevision || targetRevision || '—' }}</code></dd>
      </div>
      <div>
        <dt>已生效版本</dt>
        <dd><code>{{ report?.activeRevision || '尚无' }}</code></dd>
      </div>
    </dl>

    <p v-if="phase === 'reconnecting'" class="reconnect-note">
      服务重启期间连接中断是正常现象，页面会继续等待，不会重复提交。
    </p>

    <div v-if="['polling', 'reconnecting'].includes(phase)" class="progress-actions">
      <el-button size="small" @click="$emit('resume')">立即检查状态</el-button>
      <span class="field-hint">只读取进度，不会重新提交配置。</span>
    </div>

    <ErrorNotice
      v-if="phase === 'recovery'"
      title="配置未能生效"
      :message="report?.error?.message || '目标版本启动失败，服务已进入恢复状态。'"
      :code="report?.error?.code || ''"
    >
      <template #actions><slot name="recovery-actions" /></template>
    </ErrorNotice>

    <ErrorNotice
      v-else-if="phase === 'unconfirmed'"
      title="尚无法确认生效"
      :message="unconfirmedMessage"
      retry-label="继续检查"
      @retry="$emit('resume')"
    >
      <template #actions><slot name="unconfirmed-actions" /></template>
    </ErrorNotice>

    <ErrorNotice
      v-else-if="phase === 'unauthorized'"
      title="需要重新授权才能查看进度"
      message="当前浏览器没有这次操作的授权。操作仍可能在进行，可使用宿主恢复码继续。"
    >
      <template #actions><slot name="unauthorized-actions" /></template>
    </ErrorNotice>

    <div v-if="phase === 'applied'" class="progress-success" role="status">
      <slot name="applied" />
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'

const props = defineProps({
  phase: { type: String, required: true },
  report: { type: Object, default: null },
  targetRevision: { type: String, default: '' },
  mismatch: { type: String, default: '' },
})

defineEmits(['resume'])

const UNCONFIRMED_MESSAGES = {
  operation: '服务返回的进度不属于本次操作，已忽略该响应。',
  target: '服务返回的目标版本与本次操作不一致，已忽略该响应。',
  active: '服务报告已完成，但实际运行版本与目标版本不一致。',
}

const FLOW = [
  { status: 'ACCEPTED', label: '已接受' },
  { status: 'DRAINING', label: '等待业务收尾' },
  { status: 'STARTING', label: '启动目标版本' },
  { status: 'APPLIED', label: '已生效' },
]

const PHASE_LABELS = {
  DB_CHECK: '核对数据库',
  SCHEMA: '初始化数据结构',
  ADMIN: '处理管理员账号',
  SAVE: '保存配置',
  DRAIN: '等待业务收尾',
  BOOT: '启动服务',
  READY: '等待就绪确认',
}

const stepStateLabel = { done: '已完成', current: '进行中', pending: '未开始' }

const unconfirmedMessage = computed(() => (
  `${UNCONFIRMED_MESSAGES[props.mismatch] || '在等待时间内没有收到目标版本的就绪确认。'}配置没有被标记为成功，也没有重复提交。`
))

const showTimeline = computed(() => !['recovery', 'unauthorized'].includes(props.phase))
const phaseLabel = computed(() => PHASE_LABELS[props.report?.phase] || props.report?.phase || '等待服务响应')

const steps = computed(() => {
  const applied = props.phase === 'applied'
  const index = FLOW.findIndex(step => step.status === props.report?.status)
  const current = applied ? FLOW.length : Math.max(index, 0)
  return FLOW.map((step, position) => ({
    ...step,
    state: position < current ? 'done' : position === current ? 'current' : 'pending',
  }))
})

const headline = computed(() => ({
  polling: { title: '正在应用配置', detail: '服务会自动完成重启，请保持此页面打开。', badge: '进行中', tone: 'accent' },
  reconnecting: { title: '正在重新连接', detail: '服务正在重启，连接恢复后会继续读取进度。', badge: '等待连接', tone: 'caution' },
  applied: { title: '配置已生效', detail: '服务已确认运行在目标版本。', badge: '已生效', tone: 'positive' },
  recovery: { title: '需要恢复', detail: '上一版生效配置仍然保留，下一步由你决定。', badge: '需要处理', tone: 'critical' },
  unconfirmed: { title: '尚未确认', detail: '无法判断本次操作的最终结果。', badge: '未确认', tone: 'caution' },
  unauthorized: { title: '无法读取进度', detail: '需要有效的授权才能继续查看。', badge: '需要授权', tone: 'caution' },
}[props.phase] || { title: '等待开始', detail: '', badge: '未开始', tone: 'neutral' }))
</script>

<style scoped>
.operation-progress {
  display: grid;
  gap: var(--space-5);
  padding: var(--space-5);
}

.progress-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-3);
}

h2 {
  margin: 0;
  font-size: var(--text-xl);
}

.progress-heading p {
  margin: var(--space-1) 0 0;
  color: var(--text-subtle);
}

.progress-steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.progress-steps li {
  display: grid;
  gap: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--text-sm);
}

.step-marker {
  height: 4px;
  background: var(--line-default);
  border-radius: var(--radius-pill);
}

.progress-steps .is-done .step-marker,
.progress-steps .is-current .step-marker {
  background: var(--accent);
}

.progress-steps .is-current {
  color: var(--text-strong);
  font-weight: 600;
}

.progress-steps .is-current .step-marker {
  animation: step-pulse 1.6s ease-in-out infinite;
}

@keyframes step-pulse {
  50% { opacity: .45; }
}

.operation-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
}

dt {
  color: var(--text-subtle);
  font-size: var(--text-xs);
}

dd {
  margin: 2px 0 0;
  color: var(--text-strong);
  overflow-wrap: anywhere;
}

code {
  font-size: var(--text-sm);
}

.progress-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.progress-actions .field-hint {
  margin: 0;
}

.reconnect-note {
  margin: 0;
  padding: var(--space-3);
  color: var(--text-body);
  background: var(--caution-surface);
  border: 1px solid var(--caution-line);
  border-radius: var(--radius-md);
}

.progress-success {
  padding: var(--space-3);
  background: var(--positive-surface);
  border: 1px solid var(--positive-line);
  border-radius: var(--radius-md);
}

@media (max-width: 640px) {
  .progress-steps {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .operation-meta {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
