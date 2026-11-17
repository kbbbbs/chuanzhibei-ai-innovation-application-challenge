import { test, expect } from '@playwright/test'

async function fixture(
  page,
  {
    pending = false,
    role = 'student',
    failOnce = false,
    postFailure = false,
    pendingAction = false,
  } = {},
) {
  await page.addInitScript((role) => {
    localStorage.setItem('token', 'assistant-fixture')
    localStorage.setItem('userId', '8')
    localStorage.setItem('username', '助手测试')
    localStorage.setItem('roleCode', role)
  }, role)
  const calls = [],
    sessions = [],
    messages = new Map()
  let polls = 0,
    sends = 0,
    cancelled = false
  await page.route('**/backend/**', async (route) => {
    const req = route.request(),
      url = new URL(req.url())
    const path = url.pathname.replace(/^\/backend/, ''),
      method = req.method()
    const body = req.postData() ? req.postDataJSON() : null
    calls.push({ path, method, body })
    const ok = (data) => route.fulfill({ json: { code: '200', data } })
    if (path === '/courses')
      return ok({
        records: [
          { id: 41, title: '人工智能基础' },
          { id: 42, title: '机器学习' },
        ],
        total: 2,
      })
    if (path === '/api/assistant/sessions' && method === 'GET')
      return ok(sessions)
    if (path === '/api/assistant/sessions' && method === 'POST') {
      const session = {
        id: sessions.length + 1,
        ...body,
        scope: body.courseId ? 'COURSE' : 'GENERAL',
      }
      sessions.unshift(session)
      messages.set(session.id, [])
      return ok(session)
    }
    const match = path.match(/^\/api\/assistant\/sessions\/(\d+)\/messages$/)
    if (match) {
      const id = Number(match[1])
      if (method === 'GET') return ok(messages.get(id) || [])
      if (
        ![
          'chat',
          'summarize_note',
          'explain_selection',
          'outline_from_video',
          'quiz_from_note',
        ].includes(body.taskType)
      )
        return route.fulfill({
          status: 400,
          json: { code: '400', msg: '任务类型无效' },
        })
      sends++
      messages.set(id, [{ id: 1, role: 'user', content: body.content }])
      if (postFailure && sends === 1) return route.abort('failed')
      return ok({ requestId: body.requestId, status: 'RUNNING' })
    }
    if (/\/requests\//.test(path)) {
      if (path.endsWith('/cancel')) {
        cancelled = true
        return ok({ status: 'CANCELLED' })
      }
      if (cancelled) return ok({ status: 'CANCELLED' })
      polls++
      if (failOnce && polls === 1)
        return ok({ status: 'FAILED', error: '模型超时，请重试' })
      if (pending && polls < 3) return ok({ status: 'RUNNING' })
      const id = Number(path.split('/')[4])
      const message = {
        id: 2,
        role: 'assistant',
        content: '这是通用解释。',
        sections: [{ type: 'GENERAL', content: '这是通用解释。' }],
        citations: [],
        status: 'COMPLETED',
      }
      if (pendingAction)
        message.pendingAction = {
          actionId: 'test-action',
          summary: '修改人工智能基础课程名称',
          parameters: {
            courseId: 41,
            title: '人工智能入门',
            workflowVersion: 3,
          },
        }
      messages.set(id, [
        ...(messages.get(id) || []).filter((item) => item.id !== message.id),
        message,
      ])
      return ok({ status: 'COMPLETED', message })
    }
    if (path === '/api/agent/actions/confirm')
      return ok({ message: '课程名称已修改' })
    return ok([])
  })
  return calls
}

test('student companion chooses courses and answers without a knowledge base picker', async ({
  page,
}) => {
  const calls = await fixture(page)
  await page.goto('/knowledge-base')
  await expect(
    page.getByRole('heading', { name: '智能学伴', exact: true }),
  ).toBeVisible()
  await page.getByRole('combobox', { name: '对话课程' }).press('Enter')
  await page.getByRole('option', { name: '人工智能基础', exact: true }).click()
  await page
    .getByPlaceholder('输入问题')
    .fill('什么是人工智能')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByText('这是通用解释。', { exact: true })).toBeVisible()
  await expect(page.getByText('通用知识补充', { exact: true })).toBeVisible()
  expect(
    calls.find(
      (c) => c.path === '/api/assistant/sessions' && c.method === 'POST',
    )?.body.courseId,
  ).toBe(41)
  expect(calls.some((c) => c.path.includes('knowledge-bases'))).toBe(false)
  await expect(page.getByRole('button', { name: /打开.*助手/ })).toHaveCount(0)
})

test('refresh resumes a durable request without submitting the question again', async ({
  page,
}) => {
  const calls = await fixture(page, { pending: true })
  await page.goto('/knowledge-base?courseId=41')
  await page.getByPlaceholder('输入问题').fill('刷新后继续')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(
    page.getByRole('status').filter({ hasText: '正在处理' }),
  ).toBeVisible()
  await page.reload()
  await expect(page.getByText('这是通用解释。', { exact: true })).toBeVisible()
  expect(
    calls.filter((c) => c.method === 'POST' && c.path.endsWith('/messages')),
  ).toHaveLength(1)
})

test('terminal failure retries with a new request identifier', async ({
  page,
}) => {
  const calls = await fixture(page, { failOnce: true })
  await page.goto('/knowledge-base')
  await page.getByPlaceholder('输入问题').fill('失败后重试')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(
    page.getByText('模型超时，请重试', { exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.getByText('这是通用解释。', { exact: true })).toBeVisible()
  const sends = calls.filter(
    (c) => c.method === 'POST' && c.path.endsWith('/messages'),
  )
  expect(sends).toHaveLength(2)
  expect(sends[0].body.requestId).not.toBe(sends[1].body.requestId)
})

test('an uncertain network send retries the same request and explicit stop calls cancellation', async ({
  page,
}) => {
  const calls = await fixture(page, { postFailure: true, pending: true })
  await page.goto('/knowledge-base')
  await page.getByPlaceholder('输入问题').fill('网络重试')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(
    page.getByRole('status').filter({ hasText: '正在处理' }),
  ).toBeVisible()
  const sends = calls.filter(
    (c) => c.method === 'POST' && c.path.endsWith('/messages'),
  )
  expect(sends).toHaveLength(2)
  expect(sends[0].body.requestId).toBe(sends[1].body.requestId)
  await page.getByRole('button', { name: '停止生成', exact: true }).click()
  await expect
    .poll(() => calls.some((c) => c.path.endsWith('/cancel')))
    .toBe(true)
  await expect(
    page.getByPlaceholder('输入问题'),
  ).toBeEnabled()
})

test('teacher previews a concrete operation and confirms it once', async ({
  page,
}) => {
  const calls = await fixture(page, { role: 'teacher', pendingAction: true })
  await page.goto('/knowledge-base')
  await page
    .getByPlaceholder('输入问题')
    .fill('修改课程名称')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await page
    .getByRole('button', { name: '查看并确认操作', exact: true })
    .click()
  const dialog = page.getByRole('dialog', { name: '确认操作', exact: true })
  await expect(dialog.getByText('课程编号', { exact: true })).toBeVisible()
  await expect(dialog.getByText('人工智能入门', { exact: true })).toBeVisible()
  await dialog.getByRole('button', { name: '确认执行', exact: true }).click()
  await expect(page.getByText('课程名称已修改', { exact: true })).toBeVisible()
  await expect(
    page.getByRole('button', { name: '已执行', exact: true }),
  ).toBeDisabled()
  expect(
    calls.filter((c) => c.path === '/api/agent/actions/confirm'),
  ).toHaveLength(1)
  expect(
    calls.find((c) => c.path === '/api/agent/actions/confirm')?.body,
  ).toEqual({
    actionId: 'test-action',
    confirmed: true,
    confirmationText: null,
  })
})

test('teacher course question opens unified chat without loading knowledge management', async ({
  page,
}) => {
  const calls = await fixture(page, { role: 'teacher' })
  await page.route('**/backend/courses/41', (route) =>
    route.fulfill({
      json: {
        code: '200',
        data: {
          id: 41,
          title: '人工智能基础',
          teacherId: 8,
          status: 1,
          setupStage: 'COMPLETED',
        },
      },
    }),
  )
  await page.goto('/courses/41?tab=chat')
  await expect(page.getByTestId('assistant-conversation')).toBeVisible()
  await page
    .getByPlaceholder('输入问题')
    .fill('解释课程内容')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByText('这是通用解释。', { exact: true })).toBeVisible()
  expect(
    calls.find(
      (c) => c.path === '/api/assistant/sessions' && c.method === 'POST',
    )?.body.courseId,
  ).toBe(41)
  expect(calls.some((c) => c.path.includes('/knowledge-base'))).toBe(false)
  await expect(page.getByRole('button', { name: /打开智能助手/ })).toHaveCount(
    0,
  )
})

test('note task and lesson quick chat share the lesson session and append only on click', async ({
  page,
}) => {
  const calls = await fixture(page)
  const ok = (data) => ({ json: { code: '200', data } })
  await page.route('**/backend/courses/41', (route) =>
    route.fulfill(ok({ id: 41, title: '人工智能基础', status: 1 })),
  )
  await page.route('**/backend/courses/41/chapters', (route) =>
    route.fulfill(ok([{ id: 51, title: '概述', sortOrder: 1 }])),
  )
  await page.route('**/backend/chapters/51/lessons', (route) =>
    route.fulfill(
      ok([
        {
          id: 61,
          chapterId: 51,
          title: '人工智能导论',
          resourceType: 'text',
          content: '本课介绍人工智能',
          status: 1,
        },
      ]),
    ),
  )
  let saves = 0
  await page.route('**/backend/lessons/61/note', (route) => {
    if (route.request().method() === 'PUT') saves++
    return route.fulfill(
      ok({ title: '我的笔记', contentMd: '智能系统可以完成感知任务' }),
    )
  })
  await page.goto('/course-learn/41?classId=11&lessonId=61')
  await page.getByRole('button', { name: '总结', exact: true }).click()
  await expect(
    page
      .locator('.note-ai-result')
      .getByText('这是通用解释。', { exact: true }),
  ).toBeVisible()
  const send = calls.find(
    (c) => c.method === 'POST' && c.path.endsWith('/messages'),
  )
  expect(send.body.taskType).toBe('summarize_note')
  expect(send.body.context.noteContent).toBe('智能系统可以完成感知任务')
  expect(
    calls.find(
      (c) => c.path === '/api/assistant/sessions' && c.method === 'POST',
    )?.body,
  ).toMatchObject({ courseId: 41, lessonId: 61 })
  expect(saves).toBe(0)
  await page.getByRole('button', { name: '追加到笔记', exact: true }).click()
  await expect(
    page.getByRole('button', { name: '已追加到笔记', exact: true }),
  ).toBeDisabled()
  await page.getByRole('tab', { name: '问答', exact: true }).click()
  await expect(page.getByTestId('assistant-conversation')).toBeVisible()
  await expect(
    page
      .getByTestId('assistant-conversation')
      .getByText('这是通用解释。', { exact: true }),
  ).toBeVisible()
  expect(
    calls.filter(
      (c) => c.path === '/api/assistant/sessions' && c.method === 'POST',
    ),
  ).toHaveLength(1)
})

test('switching course starts an isolated conversation and full page hides floating duplicate', async ({
  page,
}) => {
  const calls = await fixture(page)
  await page.goto('/knowledge-base?courseId=41')
  await page.getByPlaceholder('输入问题').fill('第一门课程')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByText('这是通用解释。', { exact: true })).toBeVisible()
  await page.getByRole('combobox', { name: '对话课程' }).press('Enter')
  await page.getByRole('option', { name: '机器学习', exact: true }).click()
  await expect(page.getByText('第一门课程', { exact: true })).toHaveCount(0)
  await page.getByPlaceholder('输入问题').fill('第二门课程')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect
    .poll(() =>
      calls
        .filter(
          (c) => c.path === '/api/assistant/sessions' && c.method === 'POST',
        )
        .map((c) => c.body.courseId),
    )
    .toEqual([41, 42])
})

test('floating collapse and full-page expansion keep one request and one session', async ({
  page,
}) => {
  const calls = await fixture(page, { pending: true, role: 'teacher' })
  await page.goto('/courses')
  await page.getByRole('button', { name: /打开智能助手/ }).click()
  await page
    .getByPlaceholder('输入问题')
    .fill('解释梯度下降')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await page.getByRole('button', { name: '关闭智能助手', exact: true }).click()
  await page.getByRole('button', { name: /打开智能助手/ }).click()
  await page.getByRole('button', { name: '展开对话', exact: true }).click()
  await expect(page).toHaveURL(/knowledge-base.*sessionId=1/)
  await expect(page.getByText('这是通用解释。', { exact: true })).toBeVisible()
  expect(
    calls.filter((c) => c.method === 'POST' && c.path.endsWith('/messages')),
  ).toHaveLength(1)
  expect(
    calls.filter(
      (c) => c.method === 'POST' && c.path === '/api/assistant/sessions',
    ),
  ).toHaveLength(1)
})

test('assistant remains usable on a narrow student screen', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await fixture(page)
  await page.goto('/knowledge-base?courseId=41')
  await page.getByPlaceholder('输入问题').fill('解释人工智能')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByText('这是通用解释。', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: test.info().outputPath('student-companion-mobile.png'), fullPage: true })
})

test('assistant configuration is accessible through administrator settings', async ({ page }) => {
  await fixture(page, { role: 'admin' })
  await page.goto('/settings/assistants')
  await expect(page.getByRole('heading', { name: '助手设置', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '新增助手配置', exact: true }).click()
  await expect(page.getByRole('dialog').getByText('系统提示词', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.goto('/knowledge-base')
  await expect(page.getByRole('combobox', { name: '对话课程' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '新增助手配置', exact: true })).toHaveCount(0)
})

test('session listing failure ends loading and explains why history cannot open', async ({ page }) => {
  await fixture(page)
  await page.route('**/backend/api/assistant/sessions', route => route.fulfill({
    json: { code: '400', msg: '课程不存在' },
  }))
  await page.goto('/knowledge-base?courseId=41&sessionId=1')
  await expect(page.getByText('课程不存在', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '重试', exact: true })).toBeVisible()
  await expect(page.getByPlaceholder('输入问题')).toBeDisabled()
  await expect(page.locator('.conversation-messages > .el-loading-mask')).toHaveCount(0)
})
