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

test('current stage replaces timeline and message labels', async ({page}) => {
 const run=await fixture(page); await page.goto('/knowledge-base'); await send(page)
 await expect(page.locator('.generation-status')).toHaveText('调用模型')
 await expect(page.locator('.kernel-panel,.message-role')).toHaveCount(0)
 run.showPreview(); await expect(page.locator('.markdown-body strong')).toHaveText('正在输出的正文')
 run.complete(); await expect(page.locator('.markdown-body strong')).toHaveText('完整答案')
 await expect(page.locator('.streaming-status,.generation-status,.answer-warning')).toHaveCount(0)
 await expect(page.locator('.message-avatar.assistant')).toHaveText('AI')
 await expect(page.getByText(/Ctrl \+ Enter/)).toHaveCount(0)
})
test('Enter sends and Shift Enter inserts a newline',async({page})=>{
 const run=await fixture(page);await page.goto('/knowledge-base')
 const input=page.locator('textarea');await input.fill('第一行');await input.press('Shift+Enter');await input.press('x')
 expect(run.calls.filter(c=>c.path.endsWith('/messages')&&c.method==='POST')).toHaveLength(0)
 await expect(input).toHaveValue('第一行\nx');await input.press('Enter')
 await expect.poll(()=>run.calls.filter(c=>c.path.endsWith('/messages')&&c.method==='POST').length).toBe(1)
})
test('floating is larger and preserves the same streaming preview on expansion',async({page})=>{
 const run=await fixture(page);await page.setViewportSize({width:1440,height:1000});await page.goto('/courses')
 await page.getByRole('button',{name:/打开智能助手/}).click()
 const bounds=await page.locator('.agent-floating-panel').boundingBox();expect(bounds.width).toBeGreaterThanOrEqual(590);expect(bounds.height).toBeGreaterThanOrEqual(740)
 await send(page);run.showPreview();await expect(page.locator('.markdown-body strong')).toHaveText('正在输出的正文')
 const type=await page.locator('.message-bubble.assistant').evaluate(el=>({font:getComputedStyle(el).fontSize,line:getComputedStyle(el).lineHeight}));expect(parseFloat(type.font)).toBeGreaterThanOrEqual(15);expect(parseFloat(type.line)).toBeGreaterThanOrEqual(27)
 await page.getByRole('button',{name:'展开对话',exact:true}).click();await expect(page.locator('.agent-floating-panel')).toHaveCount(0);await expect(page.locator('.markdown-body strong')).toHaveText('正在输出的正文')
 expect(run.calls.filter(c=>c.path.endsWith('/messages')&&c.method==='POST')).toHaveLength(1)
})

test('input composition Enter does not send prematurely',async({page})=>{
 const run=await fixture(page);await page.goto('/knowledge-base');const input=page.locator('textarea');await input.fill('输入法选字')
 await input.dispatchEvent('keydown',{key:'Enter',code:'Enter',isComposing:true,keyCode:229})
 expect(run.calls.filter(c=>c.path.endsWith('/messages')&&c.method==='POST')).toHaveLength(0)
 await input.press('Enter');await expect.poll(()=>run.calls.filter(c=>c.path.endsWith('/messages')&&c.method==='POST').length).toBe(1)
})
