<template>
  <div ref="chartRef" class="subject-breakdown-chart" v-loading="!hasData"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] },
  height: { type: String, default: '300px' }
})

const chartRef = ref(null)
let chartInstance = null
const hasData = ref(false)

function buildOption() {
  const sorted = [...props.data].sort((a, b) => b.score - a.score)
  const names = sorted.map(d => d.name)
  const scores = sorted.map(d => d.score)
  const avg = scores.length ? Math.round(scores.reduce((a, b) => a + b, 0) / scores.length) : 0
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        return `<b>${p.name}</b><br/>${p.marker} 得分: <b>${p.value}</b> 分`
      }
    },
    grid: { left: 100, right: 40, top: 20, bottom: 40 },
    xAxis: {
      type: 'value',
      max: 100,
      axisLabel: { fontSize: 11, formatter: '{value}分' },
      splitLine: { lineStyle: { color: '#f0f0f0' } }
    },
    yAxis: {
      type: 'category',
      data: names,
      axisLabel: { fontSize: 12, width: 90, overflow: 'truncate' },
      axisLine: { lineStyle: { color: '#ccc' } }
    },
    series: [{
      type: 'bar',
      data: scores.map((v, i) => {
        const color = v >= 80 ? '#67C23A' : v >= 60 ? '#409EFF' : v >= 40 ? '#E6A23C' : '#F56C6C'
        return { value: v, itemStyle: { color, borderRadius: [0, 6, 6, 0] } }
      }),
      barWidth: '55%',
      markLine: {
        silent: true,
        data: [{ xAxis: avg, name: '平均', label: { formatter: '平均 {c}分' } }],
        lineStyle: { color: '#909399', type: 'dashed' },
        symbol: 'none'
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
watch(() => props.data, () => nextTick(() => initChart()), { deep: true })
</script>

<style scoped>
.subject-breakdown-chart { width: 100%; min-height: 200px; }
</style>
