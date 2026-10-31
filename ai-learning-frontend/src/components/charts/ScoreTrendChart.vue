<template>
  <div ref="chartRef" class="score-trend-chart" v-loading="!hasData"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] },
  height: { type: String, default: '320px' }
})

const chartRef = ref(null)
let chartInstance = null
const hasData = ref(false)

function buildOption() {
  const sorted = [...props.data].sort((a, b) => new Date(a.date) - new Date(b.date))
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        return `${p.axisValue}<br/>${p.marker} 成绩: <b>${p.value}</b> 分`
      }
    },
    grid: { left: 48, right: 24, top: 24, bottom: 32 },
    xAxis: {
      type: 'category',
      data: sorted.map(d => d.date),
      axisLabel: { rotate: 30, fontSize: 11 },
      axisLine: { lineStyle: { color: '#ccc' } }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { fontSize: 11 },
      splitLine: { lineStyle: { color: '#f0f0f0' } }
    },
    series: [{
      type: 'line',
      data: sorted.map(d => d.score),
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: { color: '#409EFF', width: 3 },
      itemStyle: { color: '#409EFF' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.25)' },
          { offset: 1, color: 'rgba(64,158,255,0.02)' }
        ])
      },
      markLine: {
        silent: true,
        data: [{ type: 'average', name: '平均分' }],
        lineStyle: { color: '#E6A23C', type: 'dashed' }
      }
    }]
  }
}

function initChart() {
  if (!chartRef.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value)
  if (props.data.length) {
    chartInstance.setOption(buildOption())
    hasData.value = true
  }
}

onMounted(() => {
  chartRef.value.style.height = props.height
  initChart()
  window.addEventListener('resize', () => chartInstance?.resize())
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', () => chartInstance?.resize())
  chartInstance?.dispose()
})

watch(() => props.data, () => {
  nextTick(() => {
    initChart()
  })
}, { deep: true })
</script>

<style scoped>
.score-trend-chart {
  width: 100%;
  min-height: 200px;
}
</style>
