<template>
  <div class="score-trend-chart">
    <div v-show="hasData" ref="chartRef" class="chart-canvas" :style="{ height }" role="img" aria-label="成绩趋势图"></div>
    <p v-if="!hasData" class="chart-empty">暂无成绩数据</p>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useEchart } from '@/composables/useEchart'

const props = defineProps({
  data: { type: Array, default: () => [] },
  height: { type: String, default: '320px' },
  unit: { type: String, default: '分' }
})

const chartRef = ref(null)
const hasData = computed(() => props.data.length > 0)

function buildOption(palette) {
  const sorted = [...props.data].sort((a, b) => new Date(a.date) - new Date(b.date))
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        return `${p.axisValue}<br/>${p.marker} 成绩: <b>${p.value}</b> ${props.unit}`
      }
    },
    grid: { left: 48, right: 24, top: 24, bottom: 48 },
    xAxis: {
      type: 'category',
      data: sorted.map(d => d.date),
      axisLabel: { rotate: 30, fontSize: 11, color: palette.subtle },
      axisLine: { lineStyle: { color: palette.line } }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { fontSize: 11, color: palette.subtle },
      splitLine: { lineStyle: { color: palette.split } }
    },
    series: [{
      type: 'line',
      data: sorted.map(d => d.score),
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: { color: palette.accent, width: 3 },
      itemStyle: { color: palette.accent },
      areaStyle: { color: palette.accent, opacity: 0.12 },
      markLine: {
        silent: true,
        data: [{ type: 'average', name: '平均' }],
        label: { color: palette.subtle },
        lineStyle: { color: palette.caution, type: 'dashed' }
      }
    }]
  }
}

useEchart(chartRef, palette => (hasData.value ? buildOption(palette) : null), () => props.data)
</script>

<style scoped>
.score-trend-chart {
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
