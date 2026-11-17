import { test, expect } from '@playwright/test'

const runningEvents = [
  { sequence: 1, state: 'REQUEST_ACCEPTED', elapsedMillis: 0 },
  { sequence: 2, state: 'CONTEXT_VALIDATING', elapsedMillis: 12 },
  { sequence: 3, state: 'TOOL_RUNNING', toolName: 'course.search', elapsedMillis: 120 },
  { sequence: 4, state: 'TOOL_SUCCEEDED', toolName: 'course.search', elapsedMillis: 360, result: { itemCount: 2, entityIds: ['41', '42'] } },
  { sequence: 5, state: 'MODEL_RUNNING', elapsedMillis: 500 },
]
const completedEvents = [...runningEvents, { sequence: 6, state: 'COMPLETED', elapsedMillis: 1850 }]

async function fixture(page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'original-interaction-fixture')
    localStorage.setItem('userId', '8')
    localStorage.setItem('roleCode', 'teacher')
    localStorage.setItem('username', '交互测试')
  })
  let requestId = null, active = false, complete = false, preview = false, restricted = false, cancelledRestricted = false
  let messages = []
  const calls = []
  const answer = () => ({ id: 2, role: 'assistant', content: '**完整答案**', sections: [{ type: 'GENERAL', content: '**完整答案**' }], events: completedEvents, status: 'COMPLETED' })
  const snapshot = () => cancelledRestricted ? { requestId, status: 'CANCELLED', events: [], eventsRestricted: true } : restricted ? { requestId, status: 'RUNNING', events: [], message: { role: 'assistant', content: '相关内容已无权访问', status: 'RESTRICTED' } } : ({ requestId, status: complete ? 'COMPLETED' : 'RUNNING', events: complete ? completedEvents : runningEvents,
    ...(complete ? { message: answer() } : preview ? { message: { role: 'assistant', content: '**正在输出的正文**', sections: [{ type: 'GENERAL', content: '**正在输出的正文**' }], status: 'GENERATING' } } : {}) })
  await page.route('**/backend/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    calls.push({ path, method: request.method() })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/courses') return ok({ records: [{ id: 41, title: '人工智能基础', teacherId: 8, status: 1, setupStage: 'COMPLETED' }], total: 1 })
    if (path === '/api/assistant/sessions') return ok([{ id: 91, title: '原版交互验证', activeRequestId: active ? requestId : null }, { id: 92, courseId: 41, title: '另一门课程' }])
    if (path.endsWith('/messages') && request.method() === 'GET') return ok(path.includes('/92/') ? [] : messages)
    if (path.endsWith('/messages') && request.method() === 'POST') {
      requestId = request.postDataJSON().requestId; active = true
      messages = [{ id: 1, role: 'user', content: request.postDataJSON().content }]
      return ok(snapshot())
    }
    if (path.endsWith('/stream')) return route.fulfill({ contentType: 'text/event-stream', body: runningEvents.map(event => `event: state\ndata: ${JSON.stringify(event)}\n\n`).join('') })
    if (path.includes('/requests/')) {
      await new Promise(resolve => setTimeout(resolve, 200))
      if (complete) { active = false; messages = [messages[0], answer()] }
      return ok(snapshot())
    }
    return ok([])
  })
  return { calls, showPreview: () => { preview = true }, complete: () => { complete = true }, revoke: () => { restricted = true }, revokeCancelled: () => { cancelledRestricted = true; active = false } }
}
async function send(page) {
  await page.getByPlaceholder('输入问题').fill('查询课程后解释课程内容')
  await page.getByRole('button', { name: '发送', exact: true }).click()
}

test('shows current stage during generation and keeps answer history after refresh', async ({page}) => {
 const run=await fixture(page);await page.goto('/knowledge-base');await send(page)
 await expect(page.locator('.generation-status')).toHaveText('调用模型')
 run.showPreview();await expect(page.getByText('正在输出的正文',{exact:true})).toBeVisible()
 run.complete();await expect(page.getByText('完整答案',{exact:true})).toBeVisible()
 await expect(page.locator('.generation-status,.kernel-panel')).toHaveCount(0)
 await page.reload();await expect(page.getByText('完整答案',{exact:true})).toBeVisible()
})

test('same run survives floating close expand refresh without duplicate sends',async({page})=>{
 const run=await fixture(page);await page.goto('/courses');await page.getByRole('button',{name:/打开智能助手/}).click();await send(page)
 await expect(page.locator('.generation-status')).toHaveText('调用模型')
 await page.getByRole('button',{name:'关闭智能助手'}).click();await page.getByRole('button',{name:/打开智能助手/}).click()
 await expect(page.locator('.generation-status')).toHaveText('调用模型')
 await page.getByRole('button',{name:'展开对话',exact:true}).click();await expect(page.locator('.agent-floating-panel')).toHaveCount(0)
 await page.reload();await expect(page.locator('.generation-status')).toHaveText('调用模型')
 expect(run.calls.filter(c=>c.path.endsWith('/messages')&&c.method==='POST')).toHaveLength(1)
})

test('switching courses clears the current progress display',async({page})=>{
 const run=await fixture(page);await page.goto('/knowledge-base');await send(page);run.complete()
 await expect(page.getByText('完整答案',{exact:true})).toBeVisible()
 await page.goto('/knowledge-base?courseId=41&sessionId=92');await expect(page.locator('.generation-status,.kernel-panel')).toHaveCount(0)
})

test('restores the original selected lesson text card and explicit remove action', async ({ page }) => {
  await fixture(page)
  await page.goto('/courses')
  await expect(page.getByRole('button', { name: /打开智能助手/ })).toBeVisible()
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('agent-assistant:open', { detail: {
    context: { text: '这段课程选文将作为提问上下文。', courseTitle: '人工智能基础', lessonTitle: '监督学习', truncated: true },
  } })))
  const card = page.locator('.selected-context-card')
  await expect(card).toBeVisible()
  await expect(card.getByText('已加入课时选文')).toBeVisible()
  await expect(card.getByText('人工智能基础 · 监督学习')).toBeVisible()
  await expect(card.getByText('这段课程选文将作为提问上下文。')).toBeVisible()
  await expect(card.getByText('选文较长，已截取前 1500 字作为上下文。')).toBeVisible()
  await card.getByRole('button', { name: '移除' }).click()
  await expect(card).toHaveCount(0)
})

test('a revoked snapshot removes previous tools and preview immediately', async ({ page }) => {
  const run = await fixture(page)
  await page.goto('/knowledge-base'); await send(page)
  run.showPreview()
  await expect(page.locator('.streaming-status,.generation-status').first()).toHaveText('调用模型')
  await expect(page.getByText('正在输出的正文', { exact: true })).toBeVisible()
  run.revoke()
  await expect(page.locator('.kernel-panel')).toHaveCount(0)
  await expect(page.getByText('正在输出的正文', { exact: true })).toHaveCount(0)
  await expect(page.getByText('相关内容已无权访问', { exact: true })).toBeVisible()
})

test('cancelled revoked event scope clears the old timeline even without an answer message', async ({ page }) => {
  const run = await fixture(page)
  await page.goto('/knowledge-base'); await send(page)
  await expect(page.locator('.streaming-status,.generation-status').first()).toHaveText('调用模型')
  run.revokeCancelled()
  await expect(page.locator('.kernel-panel')).toHaveCount(0)
  await expect(page.getByText('结果 ID（最多 10 项）：41、42')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '发送', exact: true })).toBeVisible()
})
