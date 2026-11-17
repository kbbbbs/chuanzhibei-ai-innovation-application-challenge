import { nextTick, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, MarkLineComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { LabelLayout } from 'echarts/features'
import { CanvasRenderer } from 'echarts/renderers'
import { useTheme } from '@/composables/useTheme'

// Only what the charts use; add a series or component here before using it in an option.
echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, MarkLineComponent,
  TitleComponent, TooltipComponent, LabelLayout, CanvasRenderer])

const TOKEN_NAMES = {
  text: '--text-body',
  subtle: '--text-subtle',
  strong: '--text-strong',
  surface: '--surface-raised',
  line: '--line-default',
  split: '--line-subtle',
  accent: '--accent',
  positive: '--positive',
  caution: '--caution',
  critical: '--critical',
  neutral: '--neutral',
}

// Canvas text cannot read CSS variables, so charts resolve the tokens when they render.
export function chartPalette() {
  const styles = getComputedStyle(document.documentElement)
  return Object.fromEntries(Object.entries(TOKEN_NAMES).map(([key, name]) => [key, styles.getPropertyValue(name).trim()]))
}

function withThemeChrome(option, palette) {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  return {
    animation: !reduceMotion,
    ...option,
    textStyle: { color: palette.text, ...option.textStyle },
    tooltip: {
      backgroundColor: palette.surface,
      borderColor: palette.line,
      textStyle: { color: palette.strong },
      ...option.tooltip,
    },
    legend: option.legend && {
      ...option.legend,
      textStyle: { color: palette.text, ...option.legend.textStyle },
    },
  }
}

// Owns one ECharts instance: renders on data and theme changes, follows its container's
// size (sidebar, tabs and breakpoints included), and clears everything on unmount.
// getOption returns null while there is nothing to draw.
export function useEchart(containerRef, getOption, source) {
  const { resolvedTheme } = useTheme()
  let chart = null
  const observer = new ResizeObserver(() => chart?.resize())

  function render() {
    const element = containerRef.value
    if (!element) return
    const palette = chartPalette()
    const option = getOption(palette)
    if (!option) {
      chart?.clear()
      return
    }
    chart ??= echarts.init(element)
    chart.setOption(withThemeChrome(option, palette), true)
    chart.resize()
  }

  function release() {
    observer.disconnect()
    chart?.dispose()
    chart = null
  }

  watch(containerRef, element => {
    release()
    if (!element) return
    observer.observe(element)
    render()
  }, { flush: 'post' })

  watch(source, () => nextTick(render), { deep: true })
  watch(resolvedTheme, () => nextTick(render))

  onBeforeUnmount(release)

  return { render }
}
