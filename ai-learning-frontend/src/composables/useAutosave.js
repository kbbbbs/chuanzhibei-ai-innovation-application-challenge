import { computed, onBeforeUnmount, ref } from 'vue'

/**
 * Debounced server saves with a local backup in between.
 *
 * `touch()` writes the local copy at once and schedules a save; the copy is removed only after the
 * server accepted that exact change. Offline edits stay local and are sent when the browser is
 * back online. `save()` resolves to true on success and must not show its own error toasts.
 */
export function useAutosave({ key, snapshot, save, delay = 1500 }) {
  const state = ref('idle') // idle | pending | saving | saved | offline | error
  const savedAt = ref(null)
  let timer = null, revision = 0, inFlight = null, stopped = false

  const storageKey = () => key()
  function writeLocal() {
    try { if (storageKey()) localStorage.setItem(storageKey(), JSON.stringify({ at: Date.now(), value: snapshot() })) } catch { /* storage unavailable */ }
  }
  function clearLocal() {
    try { if (storageKey()) localStorage.removeItem(storageKey()) } catch { /* storage unavailable */ }
  }
  function readLocal() {
    try {
      const stored = storageKey() && JSON.parse(localStorage.getItem(storageKey()) || 'null')
      return stored && 'value' in stored ? stored.value : null
    } catch { return null }
  }

  async function flush() {
    clearTimeout(timer); timer = null
    if (inFlight) await inFlight
    if (stopped || !['pending', 'offline', 'error'].includes(state.value)) return state.value !== 'error'
    if (typeof navigator !== 'undefined' && navigator.onLine === false) { state.value = 'offline'; return false }
    const sending = revision
    state.value = 'saving'
    inFlight = (async () => {
      let ok = false
      try { ok = await save() } catch { ok = false }
      if (stopped) return ok
      if (!ok) {
        state.value = typeof navigator !== 'undefined' && navigator.onLine === false ? 'offline' : 'error'
        // Offline waits for the 'online' event; a server error is retried on its own.
        if (state.value === 'error') schedule(10000)
        return false
      }
      savedAt.value = new Date()
      // A newer edit arrived while saving: keep its local copy and save again.
      if (sending === revision) { clearLocal(); state.value = 'saved' } else { state.value = 'pending'; schedule() }
      return true
    })()
    try { return await inFlight } finally { inFlight = null }
  }
  function schedule(wait = delay) {
    clearTimeout(timer)
    timer = setTimeout(flush, wait)
  }
  function touch() {
    if (stopped) return
    revision += 1
    writeLocal()
    state.value = 'pending'
    schedule()
  }
  // Stop saving (e.g. after a final submission) and drop the local copy.
  function finish() { stopped = true; clearTimeout(timer); clearLocal(); state.value = 'idle' }
  function reset() { stopped = false; clearTimeout(timer); revision = 0; state.value = 'idle'; savedAt.value = null }

  const retryOnline = () => { if (['offline', 'error', 'pending'].includes(state.value)) flush() }
  window.addEventListener('online', retryOnline)
  onBeforeUnmount(() => {
    window.removeEventListener('online', retryOnline)
    if (timer && !stopped) flush()
  })

  const label = computed(() => {
    const time = savedAt.value ? savedAt.value.toLocaleTimeString('zh-CN', { hour12: false }) : ''
    return {
      pending: '有修改，即将自动保存',
      saving: '正在保存…',
      saved: `已自动保存 ${time}`,
      offline: '网络断开，修改已保存在本机，恢复后自动补存',
      error: '保存失败，修改已保存在本机',
    }[state.value] || (time ? `已保存 ${time}` : '')
  })

  return { state, savedAt, label, touch, flush, finish, reset, readLocal, clearLocal }
}
