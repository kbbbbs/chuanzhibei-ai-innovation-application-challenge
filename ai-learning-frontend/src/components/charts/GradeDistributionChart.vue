<template>
  <div class="grade-distribution-chart">
    <div v-show="hasData" ref="chartRef" class="chart-canvas" :style="{ height }" role="img" aria-label="成绩分布图"></div>
    <p v-if="!hasData" class="chart-empty">暂无成绩数据</p>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useEchart } from '@/composables/useEchart'

const props = defineProps({
  data: { type: Array, default: () => [] },
  type: { type: String, default: 'bar' },
  height: { type: String, default: '320px' },
  unit: { type: String, default: '分' }
})

const chartRef = ref(null)
const hasData = computed(() => props.data.length > 0)

const RANGES = ['0-59', '60-69', '70-79', '80-89', '90-100']

const rangeColors = palette => [palette.critical, palette.caution, palette.accent, palette.positive, palette.neutral]

function buildBarOption(palette) {
  const map = Object.fromEntries(props.data.map(d => [d.range, d.count]))
  const values = RANGES.map(r => map[r] || 0)
  const colors = rangeColors(palette)
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => `${params[0].name}<br/>${params[0].marker} 人数: <b>${params[0].value}</b>`
    },
    grid: { left: 48, right: 24, top: 16, bottom: 32 },
    xAxis: {
      type: 'category',
      data: RANGES.map(r => r + props.unit),
      axisLabel: { fontSize: 11, color: palette.subtle },
      axisLine: { lineStyle: { color: palette.line } }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { fontSize: 11, color: palette.subtle },
      splitLine: { lineStyle: { color: palette.split } }
    },
    series: [{
      type: 'bar',
      data: values.map((v, i) => ({
        value: v,
        itemStyle: { color: colors[i], borderRadius: [6, 6, 0, 0] }
      })),
      barWidth: '50%'
    }]
  }
}

function buildPieOption(palette) {
  const total = props.data.reduce((sum, d) => sum + d.count, 0) || 1
  const colors = rangeColors(palette)
  return {
    tooltip: {
      trigger: 'item',
      formatter: (params) => `${params.name}<br/>人数: <b>${params.value}</b> (${Math.round(params.value / total * 100)}%)`
    },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['45%', '72%'],
      center: ['50%', '48%'],
      data: props.data.map((d, i) => ({
        name: d.range + props.unit,
        value: d.count,
        itemStyle: { color: colors[i] }
      })),
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 14, fontWeight: 'bold', color: palette.strong }
      }
    }]
  }
}

useEchart(chartRef, palette => {
  if (!hasData.value) return null
  return props.type === 'pie' ? buildPieOption(palette) : buildBarOption(palette)
}, () => [props.data, props.type, props.unit])
</script>

<style scoped>
.grade-distribution-chart {
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
