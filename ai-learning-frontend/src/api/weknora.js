// Compatibility exports only. No external knowledge service is called.
export { listKnowledgeBases, search, createSession, listSessions, deleteSession, uploadDocument as uploadFile } from './rag'
import { streamChat } from './rag'
export function weKnoraStreamChat(_mode, sessionId, query, knowledgeBaseIds, callbacks = {}) {
  return streamChat({ sessionId, query, knowledgeBaseIds }, {
    onProgress: callbacks.onProgress,
    onDelta: payload => callbacks.onMessage?.({ response_type: 'answer', content: payload.content }),
    onComplete: callbacks.onComplete,
    onDone: callbacks.onDone,
    onCancel: callbacks.onCancel,
    onError: error => callbacks.onError?.({ error: error.message })
  })
}
