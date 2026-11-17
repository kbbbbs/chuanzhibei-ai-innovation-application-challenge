import { onBeforeUnmount, ref } from 'vue'

// Covers the contract's 30 s drain plus 120 s ready window, with room for the last poll.
// The window is absolute: only an explicit resume starts a new one, never server activity.
const CONFIRMATION_WINDOW_MS = 180000
const POLL_INTERVAL_MS = 2000

// Tracks one lifecycle operation until the server reports a terminal state. Connection loss
// during restart is expected and never means success or failure, so nothing is re-submitted.
export function useOperationTracker(fetchOperation) {
  // Only ever holds a report verified against the tracked operation and target.
  const report = ref(null)
  // From the 202 summary, or frozen from the first verified report after a refresh.
  const targetRevision = ref('')
  // idle | polling | reconnecting | applied | recovery | unconfirmed | unauthorized
  const phase = ref('idle')
  // Why 'unconfirmed' was entered despite a response: operation | target | active. Empty on timeout.
  const mismatch = ref('')
  const lastProblem = ref(null)
  let operationId = ''
  let operationUrl = ''
  let timer = null
  let deadline = 0
  let runId = 0

  function clearTimer() {
    if (timer !== null) clearTimeout(timer)
    timer = null
  }

  function stop() {
    runId += 1
    clearTimer()
  }

  function finish(nextPhase) {
    stop()
    phase.value = nextPhase
  }

  function rejectResponse(reason) {
    mismatch.value = reason
    finish('unconfirmed')
  }

  // A self-consistent report for another operation must never decide this one.
  function bindingProblem(data) {
    if (!data || typeof data !== 'object' || data.operationId !== operationId) return 'operation'
    const returnedTarget = data.targetRevision || ''
    if (targetRevision.value) return returnedTarget === targetRevision.value ? '' : 'target'
    const terminal = data.status === 'APPLIED' || data.status === 'RECOVERY_REQUIRED'
    if (!returnedTarget) return terminal ? 'target' : ''
    targetRevision.value = returnedTarget
    return ''
  }

  function schedule(id) {
    if (Date.now() >= deadline) {
      finish('unconfirmed')
      return
    }
    timer = setTimeout(() => poll(id), POLL_INTERVAL_MS)
  }

  async function poll(id) {
    try {
      const data = await fetchOperation(operationId, operationUrl)
      if (id !== runId) return
      lastProblem.value = null
      const problem = bindingProblem(data)
      if (problem) {
        rejectResponse(problem)
        return
      }
      report.value = data
      if (data.status === 'APPLIED') {
        // Only the handshake revision proves success, not a reachable server.
        if (data.activeRevision && data.activeRevision === targetRevision.value) finish('applied')
        else rejectResponse('active')
        return
      }
      if (data.status === 'RECOVERY_REQUIRED') {
        finish('recovery')
        return
      }
      phase.value = 'polling'
    } catch (problem) {
      if (id !== runId) return
      lastProblem.value = problem
      if (problem.status === 401 || problem.status === 403) {
        finish('unauthorized')
        return
      }
      phase.value = 'reconnecting'
    }
    schedule(id)
  }

  function start(id, statusUrl = '', target = '') {
    stop()
    operationId = id
    operationUrl = statusUrl
    targetRevision.value = target
    report.value = null
    lastProblem.value = null
    mismatch.value = ''
    phase.value = 'polling'
    deadline = Date.now() + CONFIRMATION_WINDOW_MS
    poll(runId)
  }

  // An explicit user check after the window closes; it reads state and submits nothing.
  function resume() {
    if (!operationId) return
    stop()
    mismatch.value = ''
    phase.value = 'polling'
    deadline = Date.now() + CONFIRMATION_WINDOW_MS
    poll(runId)
  }

  function dismiss() {
    stop()
    operationId = ''
    targetRevision.value = ''
    report.value = null
    lastProblem.value = null
    mismatch.value = ''
    phase.value = 'idle'
  }

  onBeforeUnmount(stop)

  return { report, targetRevision, phase, mismatch, lastProblem, start, resume, stop, dismiss }
}
