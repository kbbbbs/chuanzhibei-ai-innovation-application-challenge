<template>
  <div class="subject-breakdown-chart">
    <div v-show="hasData" ref="chartRef" class="chart-canvas" :style="{ height }" role="img" aria-label="科目得分图"></div>
    <p v-if="!hasData" class="chart-empty">暂无科目数据</p>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useEchart } from '@/composables/useEchart'

const props = defineProps({
  data: { type: Array, default: () => [] },
  height: { type: String, default: '300px' },
  unit: { type: String, default: '分' }
})

const chartRef = ref(null)
const hasData = computed(() => props.data.length > 0)

function buildOption(palette) {
  const sorted = [...props.data].sort((a, b) => b.score - a.score)
  const names = sorted.map(d => d.name)
  const scores = sorted.map(d => d.score)
  const avg = scores.length ? Math.round(scores.reduce((a, b) => a + b, 0) / scores.length) : 0
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        return `<b>${p.name}</b><br/>${p.marker} 得分: <b>${p.value}</b> ${props.unit}`
      }
    },
    grid: { left: 100, right: 40, top: 20, bottom: 40 },
    xAxis: {
      type: 'value',
      max: 100,
      axisLabel: { fontSize: 11, formatter: `{value}${props.unit}`, color: palette.subtle },
      splitLine: { lineStyle: { color: palette.split } }
    },
    yAxis: {
      type: 'category',
      data: names,
      axisLabel: { fontSize: 12, width: 90, overflow: 'truncate', color: palette.subtle },
      axisLine: { lineStyle: { color: palette.line } }
    },
    series: [{
      type: 'bar',
      data: scores.map(v => {
        const color = v >= 80 ? palette.positive : v >= 60 ? palette.accent : v >= 40 ? palette.caution : palette.critical
        return { value: v, itemStyle: { color, borderRadius: [0, 6, 6, 0] } }
      }),
      barWidth: '55%',
      markLine: {
        silent: true,
        data: [{ xAxis: avg, name: '平均', label: { formatter: `平均 {c}${props.unit}`, color: palette.subtle } }],
        lineStyle: { color: palette.neutral, type: 'dashed' },
        symbol: 'none'
      }
    }]
  }
}

useEchart(chartRef, palette => (hasData.value ? buildOption(palette) : null), () => props.data)
</script>

<style scoped>
.subject-breakdown-chart {
  width: 100%;
  min-width: 0;
}

.chart-canvas {
  width: 100%;
  min-height: 200px;
}

.chart-empty {
  margin: 0;
  padding: var(--space-8) var(--space-4);
  color: var(--text-subtle);
  font-size: var(--text-sm);
  text-align: center;
}
</style>
