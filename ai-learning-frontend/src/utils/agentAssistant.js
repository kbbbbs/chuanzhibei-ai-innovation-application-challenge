export const AGENT_ASSISTANT_OPEN_EVENT = 'agent-assistant:open'

export function openAgentAssistant(options = {}) {
  const detail = typeof options === 'string' ? { prompt: options } : options
  window.dispatchEvent(new CustomEvent(AGENT_ASSISTANT_OPEN_EVENT, { detail }))
}
