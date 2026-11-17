import { test, expect } from '@playwright/test'

const ok = data => ({ code: '200', data })

test('failed session refresh preserves the conversation and offers a retry', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('roleCode', 'student')
  })
  let sessionReads = 0
  await page.route('**/backend/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
    let body = ok([])
    if (path === '/api/setup/status') body = ok({ state: 'READY' })
    else if (path === '/api/agent/definitions') body = ok([{ id: 1, name: '学习助手', code: 'learning_assistant' }])
    else if (path === '/api/agent/sessions') {
      sessionReads++
      body = sessionReads === 2 ? { code: '500', msg: '会话读取失败' } : ok([{ id: 10, agentId: 1, title: '已保存的对话' }])
    } else if (path === '/api/agent/sessions/10/messages') {
      body = ok([{ id: 1, role: 'user', content: '保留的历史消息' }])
    }
    await route.fulfill({ json: body })
  })
  await page.goto('/console')
  await page.getByLabel('打开 Agent 助手，拖动可调整位置').click()
  const panel = page.getByRole('region', { name: 'Agent 侧边助手' })
  await expect(panel.getByText('保留的历史消息')).toBeVisible()
  await panel.getByRole('button', { name: '刷新会话' }).click()
  await expect.poll(() => sessionReads).toBe(2)
  await expect(panel.getByText('保留的历史消息')).toBeVisible()
  await expect(panel.getByRole('alert').getByText('对话列表未加载')).toBeVisible()
  await panel.getByRole('button', { name: /重试|重新加载/ }).click()
  await expect.poll(() => sessionReads).toBe(3)
  await expect(panel.getByText('保留的历史消息')).toBeVisible()
  await expect(panel.getByText('已保存的对话')).toBeVisible()
})

test('failed draft read preserves unsaved editing and offers a retry', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'teacher')
  })
  let draftReads = 0
  await page.route('**/backend/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
    let body = ok([])
    if (path === '/api/preparations') body = ok([{ id: 12, title: '已有草稿', currentStage: 'CONFIG' }])
    else if (path === '/api/preparations/12') {
      draftReads++
      body = draftReads === 1 ? { code: '500', msg: '草稿读取失败' } : ok({
        id: 12, currentStage: 'CONFIG', config: { title: '已恢复草稿' },
      })
    }
    await route.fulfill({ json: body })
  })
  await page.goto('/resource-gen')
  const title = page.getByRole('textbox', { name: '课程名称' })
  await title.fill('尚未保存的编辑')
  await page.getByRole('combobox', { name: '继续最近的备课草稿' }).click()
  await page.getByRole('option', { name: /已有草稿/ }).click()
  await expect.poll(() => draftReads).toBe(1)
  await expect(title).toHaveValue('尚未保存的编辑')
  await expect(page.getByText(/草稿.*加载失败|草稿.*读取失败/).first()).toBeVisible()
  await page.getByRole('alert').filter({ hasText: '备课草稿未读取' }).getByRole('button', { name: '重新读取' }).click()
  await expect.poll(() => draftReads).toBe(2)
  await expect(title).toHaveValue('已恢复草稿')
})
