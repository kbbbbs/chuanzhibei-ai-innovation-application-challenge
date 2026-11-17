<template>
  <span class="brand-mark" :class="{ 'brand-mark--compact': compact }">
    <svg class="brand-glyph" :width="size" :height="size" viewBox="0 0 64 64" aria-hidden="true" focusable="false">
      <rect width="64" height="64" rx="16" class="glyph-plate" />
      <path class="glyph-loop" d="M45 21.5A17 17 0 1 0 49 32H24" />
      <circle class="glyph-node" cx="45" cy="21.5" r="4.5" />
    </svg>
    <span v-if="!compact" class="brand-words">
      <strong>EduLoop</strong>
      <span>智教云环</span>
    </span>
  </span>
</template>

<script setup>
defineProps({
  compact: { type: Boolean, default: false },
  size: { type: Number, default: 34 },
})
</script>

<style scoped>
.brand-mark {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.brand-glyph {
  flex: 0 0 auto;
}

.glyph-plate {
  fill: var(--accent-solid);
}

.glyph-loop {
  fill: none;
  stroke: #ffffff;
  stroke-width: 7;
  stroke-linecap: round;
}

.glyph-node {
  fill: #bcd0ff;
}

.brand-words {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-2);
  white-space: nowrap;
}

.brand-words strong {
  color: var(--text-strong);
  font-size: var(--text-xl);
  font-weight: 700;
  letter-spacing: -.01em;
}

.brand-words span {
  color: var(--text-subtle);
  font-size: var(--text-sm);
}
</style>
