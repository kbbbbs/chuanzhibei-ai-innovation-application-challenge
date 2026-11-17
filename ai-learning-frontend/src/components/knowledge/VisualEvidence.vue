<template>
  <section class="visual-evidence" data-testid="visual-evidence">
    <h4>图片与关联文字</h4>
    <p v-if="version === 0" class="visual-warning">旧解析结果尚未整理插图，请重新解析本页。</p>
    <p v-else-if="!visuals.length" class="visual-note">未提取到独立插图；这不代表原页没有图片。</p>
    <p v-if="visuals.length" class="visual-note">请对照原页核验图片和关联文字，题号与相邻文字不代表图意已经审校。</p>
    <p v-if="issues.length" class="visual-warning">部分图片位置或关联未能确认，请结合原页核验。</p>
    <p v-if="visuals.length > 6" class="visual-note">此处展示前 6 张图片，其余内容请查看原页。</p>
    <figure v-for="(item, index) in items" :key="`${item.visual.id}-${index}`" class="visual-card" :data-testid="`visual-${index + 1}`">
      <figcaption>
        <strong>{{ isPage(item.visual) ? '原页参考图，未定位插图' : `资料插图 ${item.visual.ordinal || index + 1}` }}</strong>
        <span v-if="item.visual.questionNumber != null && item.visual.questionNumber !== ''"> · 题号 {{ item.visual.questionNumber }}</span>
        <p v-if="item.visual.caption" class="visual-caption">{{ item.visual.caption }}</p>
      </figcaption>
      <p v-if="item.loading" class="visual-note" role="status">正在加载图片…</p>
      <p v-else-if="item.error" class="visual-warning" role="alert">{{ item.error }}</p>
      <img v-else-if="item.url" :src="item.url" :alt="`${isPage(item.visual) ? '原页参考图' : '资料插图'} ${item.visual.ordinal || index + 1}`" />
      <details v-if="item.visual.context" open><summary>关联原文</summary><pre>{{ item.visual.context }}</pre></details>
    </figure>
  </section>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { getVisualImage } from '@/api/rag'
import { useUserStore } from '@/store/user'
const props = defineProps({ visuals: { type: Array, default: () => [] }, version: { type: Number, default: null },
  issues: { type: Array, default: () => [] }, active: { type: Boolean, default: true } })
const items = ref([]), user = useUserStore()
let controller = null, generation = 0
const isPage = visual => visual.kind === 'PAGE' || visual.origin === 'PAGE_FALLBACK'
function release() {
  generation++; controller?.abort(); controller = null
  for (const item of items.value) if (item.url) URL.revokeObjectURL(item.url)
  items.value = []
}
function denyAll(message) {
  generation++; controller?.abort(); controller = null
  for (const item of items.value) {
    if (item.url) URL.revokeObjectURL(item.url)
    item.url = ''; item.loading = false; item.error = message
  }
}
async function load() {
  release()
  if (!props.active) return
  const version = generation, pending = new AbortController(); controller = pending
  items.value = props.visuals.slice(0, 6).map(visual => ({ visual, url: '', loading: true, error: '' }))
  await Promise.all(items.value.map(async item => {
    try {
      const blob = await getVisualImage(item.visual.id, { signal: pending.signal })
      if (version !== generation || pending.signal.aborted) return
      item.url = URL.createObjectURL(blob)
    } catch (problem) {
      if (version !== generation || pending.signal.aborted) return
      const message = problem?.message || '无法读取资料图片，请核对权限后重新打开'
      if ([401, 403].includes(problem?.status)) { denyAll(message); return }
      item.error = message
    } finally { if (version === generation) item.loading = false }
  }))
}
watch([() => props.visuals, () => props.active], load, { immediate: true, deep: true })
watch(() => user.token, () => denyAll('登录状态已改变，请重新打开资料'))
onBeforeUnmount(release)
</script>

<style scoped>
.visual-evidence { border-top: 1px solid var(--line-subtle); margin-top: 20px; padding-top: 4px; }.visual-evidence h4 { margin: 12px 0 8px; }
.visual-note,.visual-warning { font-size: var(--text-xs); line-height: 1.7; }.visual-note { color: var(--text-subtle); }.visual-warning { color: var(--caution); }
.visual-card { margin: 12px 0; padding: 12px; background: var(--surface-canvas); border: 1px solid var(--line-subtle); border-radius: var(--radius-md); min-width: 0; }
figcaption { font-size: var(--text-sm); line-height: 1.7; overflow-wrap: anywhere; }.visual-caption { margin: 4px 0; white-space: pre-wrap; }
.visual-card img { display: block; max-width: 100%; max-height: 520px; margin: 10px auto; object-fit: contain; background: white; }
details { margin-top: 10px; }summary { color: var(--text-body); cursor: pointer; font-size: var(--text-xs); }pre { white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; font-size: var(--text-sm); line-height: 1.7; }
</style>
