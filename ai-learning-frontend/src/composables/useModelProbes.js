import { reactive, shallowReactive } from 'vue'
import { isRequestCancelled } from '@/api/configuration'

export const DRAFT_PROBE_SCOPE = '当前草稿'

// Probes run only from an explicit click and may cost money, so a page never starts one itself.
export function useModelProbes() {
  const probes = reactive({})
  const probeControllers = shallowReactive({})

  async function runProbe(role, scope, request) {
    probeControllers[role]?.abort()
    const controller = new AbortController()
    probeControllers[role] = controller
    probes[role] = { scope, report: null, error: null }
    try {
      const report = await request(controller.signal)
      if (probeControllers[role] === controller) probes[role] = { scope, report, error: null }
    } catch (problem) {
      if (probeControllers[role] !== controller) return
      if (isRequestCancelled(problem)) delete probes[role]
      else probes[role] = { scope, report: null, error: problem }
    } finally {
      if (probeControllers[role] === controller) delete probeControllers[role]
    }
  }

  function cancelProbe(role) {
    probeControllers[role]?.abort()
  }

  // A draft result only describes the draft it ran against.
  function invalidateDraftProbe(role) {
    if (probes[role]?.scope !== DRAFT_PROBE_SCOPE) return
    probeControllers[role]?.abort()
    delete probeControllers[role]
    delete probes[role]
  }

  function cancelAllProbes() {
    Object.values(probeControllers).forEach(controller => controller.abort())
  }

  return { probes, probeControllers, runProbe, cancelProbe, invalidateDraftProbe, cancelAllProbes }
}
