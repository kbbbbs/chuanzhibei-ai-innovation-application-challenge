<template>
  <div class="class-average-chart">
    <div v-show="hasData" ref="chartRef" class="chart-canvas" :style="{ height }" role="img" aria-label="我的成绩与班级平均对比图"></div>
    <p v-if="!hasData" class="chart-empty">暂无对比数据</p>
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
  const names = props.data.map(d => d.name)
  const myScores = props.data.map(d => d.myScore)
  const avgScores = props.data.map(d => d.avgScore)
  return {
    tooltip: {
      trigger: 'axis',
      formatter: function(params) {
        let html = `<b>${params[0].name}</b><br/>`
        params.forEach(p => {
          html += `${p.marker} ${p.seriesName}: <b>${p.value}</b> ${props.unit}<br/>`
        })
        return html
      }
    },
    legend: {
      data: ['我的成绩', '班级平均'],
      bottom: 0,
      textStyle: { fontSize: 11 }
    },
    grid: { left: 50, right: 30, top: 20, bottom: 72 },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { rotate: 30, fontSize: 10, width: 60, overflow: 'truncate', color: palette.subtle },
      axisLine: { lineStyle: { color: palette.line } }
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: { fontSize: 11, color: palette.subtle },
      splitLine: { lineStyle: { color: palette.split } }
    },
    series: [
      {
        name: '我的成绩',
        type: 'bar',
        itemStyle: { color: palette.accent, borderRadius: [6, 6, 0, 0] },
        data: myScores,
        barWidth: '35%',
        barGap: '20%'
      },
      {
        name: '班级平均',
        type: 'bar',
        itemStyle: { color: palette.neutral, borderRadius: [6, 6, 0, 0] },
        data: avgScores,
        barWidth: '35%'
      }
    ]
  }
}

useEchart(chartRef, palette => (hasData.value ? buildOption(palette) : null), () => props.data)
</script>

<style scoped>
.class-average-chart {
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
