import { test, expect } from '@playwright/test'

const ok = data => ({ code: '200', msg: 'ok', data })

for (const scenario of [
  { name: 'rejects a different operation', returnedId: 'other-operation', confirmed: false },
  { name: 'rejects recovery actions for a different operation', returnedId: 'other-operation', status: 'RECOVERY_REQUIRED', confirmed: false },
  { name: 'confirms the matching operation after refresh', returnedId: 'recovery-operation', confirmed: true },
]) test(`recovery session with only an operation ID ${scenario.name}`, async ({ page }) => {
  const requests = []
  await page.route('**/backend/api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/backend', '')
    requests.push(path)
    let body
    if (path === '/api/setup/status') body = ok({ state: 'PENDING_RESTART' })
    else if (path === '/api/setup/recovery/session') body = ok({ operationId: 'recovery-operation' })
    else if (path === '/api/setup/operations/recovery-operation') body = ok({
      operationId: scenario.returnedId, status: scenario.status || 'APPLIED', targetRevision: 'revision-1', activeRevision: 'revision-1', canRetry: true, canRollback: true,
    })
    else return route.fulfill({ status: 404, json: { code: '404', msg: 'unexpected mock request' } })
    await route.fulfill({ json: body })
  })
  await page.goto('/setup')
  await expect.poll(() => requests.filter(path => path === '/api/setup/operations/recovery-operation').length).toBeGreaterThanOrEqual(1)
  await page.reload()
  if (scenario.confirmed) {
    await expect(page.getByText('配置已由服务确认生效', { exact: false })).toBeVisible()
  } else {
    await expect(page.getByText('尚无法确认生效', { exact: true })).toBeVisible()
    await expect(page.getByText('配置已由服务确认生效', { exact: false })).toHaveCount(0)
    await expect(page.getByText('选择恢复方式', { exact: true })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '回滚', exact: true })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '重新应用', exact: true })).toHaveCount(0)
  }
  expect(requests.filter(path => path === '/api/setup/recovery/session').length).toBeGreaterThanOrEqual(2)
})

test('mobile embedded assistant exposes saved sessions and new conversation', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await page.addInitScript(() => {
    localStorage.setItem('token', 'mock-token')
    localStorage.setItem('roleCode', 'student')
  })
  await page.route('**/backend/api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/backend', '')
    let data = []
    if (path === '/api/setup/status') data = { state: 'READY' }
    else if (path === '/api/agent/definitions') data = [{ id: 1, name: '学习助手', code: 'learning_assistant' }]
    else if (path === '/api/agent/sessions' && route.request().method() === 'POST') data = { id: 11, agentId: 1, title: '新会话' }
    else if (path === '/api/agent/sessions') data = [{ id: 10, agentId: 1, title: '已保存的对话' }]
    else if (path === '/api/agent/sessions/10/messages') data = [{ id: 1, role: 'user', content: '历史消息内容' }]
    await route.fulfill({ json: ok(data) })
  })
  await page.goto('/courses')
  const sessionResponse = page.waitForResponse(response => response.url().includes('/api/agent/sessions') && response.request().method() === 'GET')
  await page.getByLabel('打开 Agent 助手，拖动可调整位置').click()
  const panel = page.getByRole('region', { name: 'Agent 侧边助手' })
  await sessionResponse
  const savedSession = page.getByText('已保存的对话', { exact: true })
  if (!(await savedSession.isVisible())) {
    await panel.getByRole('button', { name: /^(会话|会话列表|对话|对话列表|对话历史|聊天记录|历史会话|打开.*(会话|对话|历史))$/ }).click()
  }
  await expect(savedSession).toBeVisible()
  await savedSession.click()
  await expect(panel.getByText('历史消息内容', { exact: true })).toBeVisible()
  const newConversation = page.getByRole('button', { name: '新对话' })
  if (!(await newConversation.isVisible())) {
    await panel.getByRole('button', { name: /^(会话|会话列表|对话|对话列表|对话历史|聊天记录|历史会话|打开.*(会话|对话|历史))$/ }).click()
  }
  const createRequest = page.waitForRequest(request => request.url().includes('/api/agent/sessions') && request.method() === 'POST')
  await newConversation.click()
  await createRequest
})
