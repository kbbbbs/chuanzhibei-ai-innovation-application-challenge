<template>
  <div ref="chartRef" class="grade-distribution-chart" v-loading="!hasData"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] },
  type: { type: String, default: 'bar' },
  height: { type: String, default: '320px' }
})

const chartRef = ref(null)
let chartInstance = null
const hasData = ref(false)

const RANGE_COLORS = ['#F56C6C', '#E6A23C', '#409EFF', '#67C23A', '#909399']
const RANGES = ['0-59', '60-69', '70-79', '80-89', '90-100']

function buildBarOption() {
  const map = Object.fromEntries(props.data.map(d => [d.range, d.count]))
  const values = RANGES.map(r => map[r] || 0)
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => `${params[0].name}分<br/>${params[0].marker} 人数: <b>${params[0].value}</b>`
    },
    grid: { left: 48, right: 24, top: 16, bottom: 32 },
    xAxis: {
      type: 'category',
      data: RANGES.map(r => r + '分'),
      axisLabel: { fontSize: 11 },
      axisLine: { lineStyle: { color: '#ccc' } }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { fontSize: 11 },
      splitLine: { lineStyle: { color: '#f0f0f0' } }
    },
    series: [{
      type: 'bar',
      data: values.map((v, i) => ({
        value: v,
        itemStyle: { color: RANGE_COLORS[i], borderRadius: [6, 6, 0, 0] }
      })),
      barWidth: '50%'
    }]
  }
}

function buildPieOption() {
  const total = props.data.reduce((sum, d) => sum + d.count, 0) || 1
  return {
    tooltip: {
      trigger: 'item',
      formatter: (params) => `${params.name}分<br/>人数: <b>${params.value}</b> (${Math.round(params.value / total * 100)}%)`
    },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['45%', '72%'],
      center: ['50%', '48%'],
      data: props.data.map((d, i) => ({
        name: d.range + '分',
        value: d.count,
        itemStyle: { color: RANGE_COLORS[i] }
      })),
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 14, fontWeight: 'bold' }
      }
    }]
  }
}

function initChart() {
  if (!chartRef.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value)
  if (props.data.length) {
    chartInstance.setOption(props.type === 'pie' ? buildPieOption() : buildBarOption())
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

watch([() => props.data, () => props.type], () => {
  nextTick(() => initChart())
}, { deep: true })
</script>

<style scoped>
.grade-distribution-chart {
  width: 100%;
  min-height: 200px;
}
</style>
