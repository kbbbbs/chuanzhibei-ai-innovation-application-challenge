<template>
  <span class="status-badge" :class="`status-badge--${tone}`">
    <span class="status-dot" aria-hidden="true"></span>
    <span><slot>{{ label }}</slot></span>
  </span>
</template>

<script setup>
// State is always carried by the text; the tone only reinforces it.
defineProps({
  label: { type: String, default: '' },
  tone: {
    type: String,
    default: 'neutral',
    validator: value => ['neutral', 'accent', 'positive', 'caution', 'critical'].includes(value),
  },
})
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 2px 8px;
  border: 1px solid var(--badge-line);
  border-radius: var(--radius-pill);
  color: var(--badge-text);
  background: var(--badge-surface);
  font-size: var(--text-xs);
  font-weight: 600;
  line-height: 1.5;
}

.status-dot {
  flex: 0 0 auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-badge--neutral { --badge-text: var(--neutral); --badge-surface: var(--neutral-surface); --badge-line: var(--neutral-line); }
.status-badge--accent { --badge-text: var(--accent); --badge-surface: var(--surface-accent); --badge-line: var(--line-accent); }
.status-badge--positive { --badge-text: var(--positive); --badge-surface: var(--positive-surface); --badge-line: var(--positive-line); }
.status-badge--caution { --badge-text: var(--caution); --badge-surface: var(--caution-surface); --badge-line: var(--caution-line); }
.status-badge--critical { --badge-text: var(--critical); --badge-surface: var(--critical-surface); --badge-line: var(--critical-line); }
</style>
