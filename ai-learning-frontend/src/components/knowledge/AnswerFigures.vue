<template>
  <section v-if="items.length" class="answer-figures" data-testid="answer-figures" aria-label="参考插图">
    <figure v-for="item in items" :key="item.visual.id">
      <p v-if="item.loading" role="status">正在加载插图…</p>
      <p v-else-if="item.error" class="figure-error" role="alert">{{ item.error }}</p>
      <button v-else-if="item.url" class="figure-image" aria-label="放大插图" @click="preview = item.url">
        <img :src="item.url" :alt="item.visual.caption || '资料插图'" />
      </button>
      <figcaption>
        <span v-if="item.visual.caption">{{ item.visual.caption }}</span>
        <div class="figure-source">
          <span>{{ item.source.citation }} {{ item.source.title }} · 第 {{ item.visual.page || item.source.page }} 页</span>
          <el-button size="small" text @click="sources.openSource(item.source)">查看原页</el-button>
        </div>
      </figcaption>
    </figure>
    <el-image-viewer v-if="preview" :url-list="[preview]" teleported @close="preview = ''" />
    <RagCitations ref="sources" class="figure-citations" :citations="citations" :course-id="courseId" />
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getVisualImage } from '@/api/rag'
import { useUserStore } from '@/store/user'
import RagCitations from './RagCitations.vue'

const props = defineProps({ citations: { type: Array, default: () => [] }, courseId: Number })
const user = useUserStore()
const items = ref([]), preview = ref(''), sources = ref(null)
let controller, generation = 0
const figures = computed(() => {
  const seen = new Set(), result = []
  for (const source of props.citations) {
    for (const visual of source.visuals || []) {
      if (visual.kind !== 'FIGURE' || visual.origin === 'PAGE_FALLBACK' || seen.has(visual.id)) continue
      if (visual.documentId !== source.documentId || visual.documentVersion !== source.documentVersion
        || visual.parseRevision !== source.parseRevision || visual.page !== source.page) continue
      seen.add(visual.id)
      result.push({ visual, source })
    }
  }
  return result
})

function release() {
  generation++
  controller?.abort()
  preview.value = ''
  for (const item of items.value) if (item.url) URL.revokeObjectURL(item.url)
  items.value = []
}
function deny(message) {
  generation++
  controller?.abort()
  preview.value = ''
  for (const item of items.value) {
    if (item.url) URL.revokeObjectURL(item.url)
    item.url = ''; item.loading = false; item.error = message
  }
}
async function load() {
  release()
  const current = generation
  const pending = new AbortController()
  controller = pending
  items.value = figures.value.map(item => ({ ...item, loading: true, url: '', error: '' }))
  // Bound image fetches per answer while retaining every associated figure.
  let next = 0
  async function worker() {
    while (next < items.value.length && current === generation) {
      const item = items.value[next++]
      try {
        const blob = await getVisualImage(item.visual.id, {
          courseId: user.isStudent ? props.courseId : undefined, signal: pending.signal,
        })
        if (current === generation) item.url = URL.createObjectURL(blob)
      } catch (error) {
        if (current !== generation) return
        if ([401, 403].includes(error.status)) { deny(error.message); return }
        item.error = error.message || '插图加载失败，请查看原页'
      } finally { if (current === generation) item.loading = false }
    }
  }
  await Promise.all([worker(), worker()])
}
watch([figures, () => props.courseId], load, { immediate: true })
watch(() => user.token, () => deny('登录状态已改变，请重新打开对话'))
onBeforeUnmount(release)
</script>

<style scoped>
.answer-figures { display: grid; gap: 16px; margin-top: 18px; min-width: 0; }
figure { margin: 0; padding: 12px; border: 1px solid var(--line-subtle); border-radius: var(--radius-md); }
.figure-image { display: block; width: 100%; padding: 0; background: white; border: 0; cursor: zoom-in; }
.figure-image img { display: block; max-width: 100%; max-height: 360px; margin: auto; object-fit: contain; }
figcaption { margin-top: 10px; line-height: 1.6; font-size: var(--text-sm); overflow-wrap: anywhere; }
.figure-source { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 4px 12px; color: var(--text-subtle); font-size: var(--text-xs); }
.figure-error { color: var(--caution); }
.figure-citations { display: none; }
</style>
