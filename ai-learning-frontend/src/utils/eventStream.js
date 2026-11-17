import { useUserStore } from '@/store/user'

// POST SSE: handle frame boundaries, CRLF, split UTF-8, and multiple data lines.
export function streamEventResponse(endpoint, data, callbacks = {}) {
  const controller = new AbortController()
  const token = useUserStore().token
  const finished = (async () => {
    let complete = false
    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(data),
      })
      if (!response.ok) {
        const payload = await response.json().catch(() => ({}))
        throw new Error(
          payload.msg ||
            payload.message ||
            (response.status === 401
              ? '登录已过期，请重新登录'
              : `问答请求失败（${response.status}）`),
        )
      }
      if (
        !response.headers.get('content-type')?.includes('text/event-stream')
      ) {
        const result = await response.json().catch(() => null)
        throw new Error(
          result?.msg || result?.message || '问答服务未返回流式响应',
        )
      }
      const reader = response.body?.getReader()
      if (!reader) throw new Error('无法读取问答响应')
      const decoder = new TextDecoder()
      let buffer = ''
      const processFrame = (frame) => {
        const lines = frame.split('\n')
        const event =
          lines
            .find((line) => line.startsWith('event:'))
            ?.slice(6)
            .trim() || 'message'
        const raw = lines
          .filter((line) => line.startsWith('data:'))
          .map((line) => line.slice(5).trimStart())
          .join('\n')
        if (!raw || raw === '[DONE]') return
        let payload
        try {
          payload = JSON.parse(raw)
        } catch {
          throw new Error('问答响应格式异常，请重试')
        }
        if (event === 'error')
          throw new Error(payload.message || payload.error || '问答生成失败')
        if (event === 'state') callbacks.onState?.(payload)
        else if (event === 'progress') callbacks.onProgress?.(payload)
        else if (event === 'delta') callbacks.onDelta?.(payload)
        else if (event === 'complete') {
          complete = true
          callbacks.onComplete?.(payload)
        }
      }
      while (true) {
        const { done, value } = await reader.read()
        buffer += done
          ? decoder.decode()
          : decoder.decode(value, { stream: true })
        // Concatenate first: CR and LF may be split between network reads.
        buffer = buffer.replace(/\r\n/g, '\n')
        let end
        while ((end = buffer.indexOf('\n\n')) >= 0) {
          processFrame(buffer.slice(0, end))
          buffer = buffer.slice(end + 2)
          if (complete) break
        }
        if (complete) {
          await reader.cancel().catch(() => {})
          callbacks.onDone?.()
          return
        }
        if (done) {
          if (buffer.trim()) processFrame(buffer)
          break
        }
      }
      if (!complete) throw new Error('连接中断，回答尚未完成，请重试')
      callbacks.onDone?.()
    } catch (error) {
      if (controller.signal.aborted) callbacks.onCancel?.()
      else callbacks.onError?.(error)
    }
  })()
  return { abort: () => controller.abort(), finished }
}
