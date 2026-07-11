<template>
  <div ref="chartRef" class="class-average-chart" v-loading="!hasData"></div>
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
  const names = props.data.map(d => d.name)
  const myScores = props.data.map(d => d.myScore)
  const avgScores = props.data.map(d => d.avgScore)
  return {
    tooltip: {
      trigger: 'axis',
      formatter: function(params) {
        let html = `<b>${params[0].name}</b><br/>`
        params.forEach(p => {
          html += `${p.marker} ${p.seriesName}: <b>${p.value}</b> 分<br/>`
        })
        return html
      }
    },
    legend: {
      data: ['我的成绩', '班级平均'],
      bottom: 0,
      textStyle: { fontSize: 11 }
    },
    grid: { left: 50, right: 30, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { rotate: 30, fontSize: 10, width: 60, overflow: 'truncate' },
      axisLine: { lineStyle: { color: '#ccc' } }
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: { fontSize: 11 },
      splitLine: { lineStyle: { color: '#f0f0f0' } }
    },
    series: [
      {
        name: '我的成绩',
        type: 'bar',
        data: myScores.map(v => ({ value: v, itemStyle: { color: '#409EFF', borderRadius: [6, 6, 0, 0] } })),
        barWidth: '35%',
        barGap: '20%'
      },
      {
        name: '班级平均',
        type: 'bar',
        data: avgScores.map(v => ({ value: v, itemStyle: { color: '#c0c4cc', borderRadius: [6, 6, 0, 0] } })),
        barWidth: '35%'
      }
    ]
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
.class-average-chart { width: 100%; min-height: 200px; }
</style>
