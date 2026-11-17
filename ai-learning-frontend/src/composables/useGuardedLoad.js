import { onBeforeUnmount, ref } from 'vue'

// Runs a page's necessary reads so only the newest run may change the page, and a failed read
// stays visible instead of looking like an empty result. Writes must not go through this.
export function useGuardedLoad(onFailure) {
  const loading = ref(false)
  const loadFailed = ref(false)
  let run = 0

  async function guarded(load) {
    const current = ++run
    const isCurrent = () => current === run
    loading.value = true
    loadFailed.value = false
    try {
      await load(isCurrent)
    } catch {
      if (!isCurrent()) return
      onFailure?.()
      loadFailed.value = true
    } finally {
      if (isCurrent()) loading.value = false
    }
  }

  // Drops any run in flight, for when the page no longer wants its result.
  function reset() {
    run += 1
    loading.value = false
    loadFailed.value = false
  }

  onBeforeUnmount(() => { run += 1 })

  return { loading, loadFailed, guarded, reset }
}
