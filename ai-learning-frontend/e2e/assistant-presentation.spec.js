import { test, expect } from '@playwright/test'

const longAnswer =
  '## 梯度下降\n\n用**学习率**控制参数更新。\n\n' +
  '| 参数 | 作用 |\n| --- | --- |\n| 学习率 | 控制步长 |\n\n' +
  '```python\nlearning_rate = 0.01\nprint("a very long but readable line of code that must scroll inside its message")\n```\n\n' +
  '更新公式 $w_{t+1}=w_t-\\eta\\nabla L$。\n\n' +
  '结合例子逐步调整学习率。\n\n'.repeat(18)

async function setup(
  page,
  {
    long = false,
    streaming = false,
    retrieval = false,
    answerText = '我是你的学习助手。',
  } = {},
) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'presentation-fixture')
    localStorage.setItem('userId', '8')
    localStorage.setItem('roleCode', 'teacher')
    localStorage.setItem('username', '版式测试')
  })
  let active = false
  let activeRequestId = null
  let polls = 0
  let cancelled = false
  const calls = []
  const answer = {
    id: 2,
    role: 'assistant',
    content: long ? longAnswer : answerText,
    sections: [{ type: 'GENERAL', content: long ? longAnswer : answerText }],
    citations: [],
    status: 'COMPLETED',
  }
  if (retrieval)
    answer.retrieval = {
      mode: 'bm25',
      actualMode: 'KEYWORD',
      degradedReason: 'EMBEDDING_DISABLED',
    }
  let messages = [{ id: 1, role: 'user', content: '你是谁' }, answer]
  await page.route('**/backend/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    calls.push({ path, method: request.method() })
    const ok = (data) => route.fulfill({ json: { code: '200', data } })
    if (path === '/courses')
      return ok({
        records: [
          {
            id: 41,
            title: '人工智能基础',
            teacherId: 8,
            status: 1,
            setupStage: 'COMPLETED',
          },
        ],
        total: 1,
      })
    if (path === '/courses/41')
      return ok({
        id: 41,
        title: '人工智能基础',
        teacherId: 8,
        status: 1,
        setupStage: 'COMPLETED',
      })
    if (path === '/api/assistant/sessions')
      return ok([
        {
          id: 91,
          title: '学习对话',
          courseId: null,
          activeRequestId: active ? activeRequestId : null,
        },
      ])
    if (path.endsWith('/messages') && request.method() === 'GET')
      return ok(messages)
    if (path.endsWith('/messages') && request.method() === 'POST') {
      active = true
      activeRequestId = request.postDataJSON().requestId
      messages = [
        ...messages,
        { id: 3, role: 'user', content: request.postDataJSON().content },
      ]
      return ok({
        requestId: request.postDataJSON().requestId,
        status: 'RUNNING',
      })
    }
    if (path.endsWith('/stream')) {
      if (!streaming) return ok([])
      const requestId = request.postDataJSON().requestId
      const data = {
        requestId,
        status: 'RUNNING',
        message: {
          role: 'assistant',
          content: '正在逐段生成的答案',
          sections: [{ type: 'GENERAL', content: '正在逐段生成的答案' }],
          citations: [],
          status: 'GENERATING',
        },
      }
      return route.fulfill({
        contentType: 'text/event-stream',
        body: `event: delta\ndata: ${JSON.stringify(data)}\n\n`,
      })
    }
    if (path.endsWith('/cancel')) {
      cancelled = true
      return ok({ status: 'CANCELLED' })
    }
    if (path.includes('/requests/')) {
      polls++
      if (streaming && polls < 2 && !cancelled) {
        await new Promise((resolve) => setTimeout(resolve, 2000))
        return ok({
          status: 'RUNNING',
          message: {
            role: 'assistant',
            content: '正在逐段生成的答案',
            sections: [],
            status: 'GENERATING',
          },
        })
      }
      active = false
      if (cancelled) return ok({ status: 'CANCELLED' })
      const completed = {
        ...answer,
        id: 4,
        content: '完整的流式答案',
        sections: [{ type: 'GENERAL', content: '完整的流式答案' }],
      }
      messages = [...messages, completed]
      return ok({ status: 'COMPLETED', message: completed })
    }
    return ok([])
  })
  return calls
}

test('full assistant restores the session sidebar and bounded avatar bubbles', async ({
  page,
}) => {
  await setup(page)
  await page.setViewportSize({ width: 1440, height: 960 })
  await page.goto('/knowledge-base')
  await expect(page.getByRole('group', { name: '会话列表' })).toBeVisible()
  const user = page.locator('.conversation-message.user .message-bubble')
  await expect(user).toBeVisible()
  expect(
    await user.evaluate((el) => getComputedStyle(el).backgroundColor),
  ).not.toBe('rgba(0, 0, 0, 0)')
  const assistant = page.locator('.conversation-message.assistant')
  await expect(assistant.locator('.message-avatar')).toBeVisible()
  await expect(
    assistant.getByRole('button', { name: '复制回答' }),
  ).toBeVisible()
  const dimensions = await assistant
    .locator('.message-bubble')
    .evaluate((el) => ({
      width: el.getBoundingClientRect().width,
      parent: el.closest('.conversation-messages').getBoundingClientRect()
        .width,
    }))
  expect(dimensions.width).toBeLessThan(dimensions.parent * 0.9)
  await page.screenshot({
    path: '../docs/competition/development/agent-records/2026-11-17-assistant-ui-repair/frontend/validation/full-short.png',
    fullPage: true,
  })
})

test('long markdown stays readable and scrolls messages above a visible input', async ({
  page,
}) => {
  await setup(page, { long: true })
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto('/knowledge-base')
  await expect(page.locator('.markdown-body table')).toBeAttached()
  await expect(page.locator('.markdown-body pre')).toBeAttached()
  await expect(page.locator('.markdown-body .katex').first()).toBeAttached()
  const box = await page.locator('.conversation-input').boundingBox()
  expect(box.y + box.height).toBeLessThanOrEqual(800)
  const overflow = await page
    .locator('.conversation-messages')
    .evaluate((el) => ({
      scroll: el.scrollHeight,
      client: el.clientHeight,
      width: el.scrollWidth,
      clientWidth: el.clientWidth,
    }))
  expect(overflow.scroll).toBeGreaterThan(overflow.client)
  expect(overflow.width).toBeLessThanOrEqual(overflow.clientWidth + 2)
  await page.locator('.conversation-messages').evaluate((el) => {
    el.scrollTop = 0
  })
  await page.screenshot({
    path: '../docs/competition/development/agent-records/2026-11-17-assistant-ui-repair/frontend/validation/full-long.png',
    fullPage: true,
  })
})

test('floating compact conversation keeps toolbar messages and input inside its panel', async ({
  page,
}) => {
  await setup(page)
  await page.setViewportSize({ width: 1150, height: 700 })
  await page.goto('/courses')
  await page.getByRole('button', { name: /打开智能助手/ }).click()
  await expect(
    page.locator('.agent-floating-panel .message-avatar').first(),
  ).toBeVisible()
  const geometry = await page
    .locator('.agent-floating-panel')
    .evaluate((panel) => {
      const area = panel.getBoundingClientRect()
      const input = panel
        .querySelector('.conversation-input')
        .getBoundingClientRect()
      const history = panel
        .querySelector('.conversation-toolbar')
        .getBoundingClientRect()
      return {
        bottom: area.bottom,
        inputBottom: input.bottom,
        inputTop: input.top,
        top: area.top,
        toolbarHeight: history.height,
        scroll: panel.scrollWidth,
        client: panel.clientWidth,
      }
    })
  expect(geometry.inputBottom).toBeLessThanOrEqual(geometry.bottom)
  expect(geometry.inputTop).toBeGreaterThan(geometry.top + 100)
  expect(geometry.toolbarHeight).toBeLessThanOrEqual(68)
  expect(geometry.scroll).toBeLessThanOrEqual(geometry.client + 2)
  await expect(page.locator('.agent-floating-panel')).toHaveCSS('opacity', '1')
  await page.screenshot({
    path: '../docs/competition/development/agent-records/2026-11-17-assistant-ui-repair/frontend/validation/floating-short.png',
    fullPage: true,
  })
})

test('mobile assistant keeps one conversation and a visible composer', async ({
  page,
}) => {
  await setup(page, { long: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/knowledge-base')
  await expect(
    page.getByRole('button', { name: '发送', exact: true }),
  ).toBeVisible()
  const box = await page.locator('.conversation-input').boundingBox()
  expect(box.y + box.height).toBeLessThanOrEqual(844)
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth),
  ).toBeLessThanOrEqual(390)
  await page.locator('.conversation-messages').evaluate((el) => {
    el.scrollTop = 0
  })
  await page.screenshot({
    path: '../docs/competition/development/agent-records/2026-11-17-assistant-ui-repair/frontend/validation/mobile-long.png',
    fullPage: true,
  })
})

test('streamed answer is rendered before completion and replaced exactly once', async ({
  page,
}) => {
  const calls = await setup(page, { streaming: true })
  await page.goto('/knowledge-base')
  await page.getByPlaceholder('输入问题').fill('请流式解释')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(
    page.getByText('正在逐段生成的答案', { exact: true }),
  ).toBeVisible()
  expect(calls.some((call) => call.path.endsWith('/stream'))).toBe(true)
  await expect(page.getByText('完整的流式答案', { exact: true })).toBeVisible()
  await expect(
    page.getByText('正在逐段生成的答案', { exact: true }),
  ).toHaveCount(0)
  expect(
    calls.filter(
      (call) => call.path.endsWith('/messages') && call.method === 'POST',
    ),
  ).toHaveLength(1)
})

test('stream preview survives collapsing and expanding the same floating conversation', async ({
  page,
}) => {
  const calls = await setup(page, { streaming: true })
  await page.goto('/courses')
  await page.getByRole('button', { name: /打开智能助手/ }).click()
  await page
    .getByPlaceholder('输入问题')
    .fill('保持当前流式对话')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(
    page.getByText('正在逐段生成的答案', { exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: '关闭智能助手' }).click()
  await page.getByRole('button', { name: /打开智能助手/ }).click()
  await expect(
    page.getByText('正在逐段生成的答案', { exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: '展开对话', exact: true }).click()
  await expect(page.getByText('完整的流式答案', { exact: true })).toBeVisible()
  expect(
    calls.filter(
      (call) => call.path.endsWith('/messages') && call.method === 'POST',
    ),
  ).toHaveLength(1)
  await expect(page.locator('.agent-floating-panel')).toHaveCount(0)
})

test('stopping removes the incomplete stream preview', async ({ page }) => {
  await setup(page, { streaming: true })
  await page.goto('/knowledge-base')
  await page
    .getByPlaceholder('输入问题')
    .fill('停止当前流式对话')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(
    page.getByText('正在逐段生成的答案', { exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: '停止生成', exact: true }).click()
  await expect(
    page.getByText('正在逐段生成的答案', { exact: true }),
  ).toHaveCount(0)
  await page.waitForTimeout(2200)
  expect(
    await page.getByText('正在逐段生成的答案', { exact: true }).count(),
  ).toBe(0)
  await expect(
    page.getByPlaceholder('输入问题'),
  ).toBeEnabled()
})

test('reading older messages prevents an arriving answer from forcing scroll to bottom', async ({
  page,
}) => {
  await setup(page, { long: true, streaming: true })
  await page.goto('/knowledge-base')
  await page.getByPlaceholder('输入问题').fill('继续解释')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(
    page.getByText('正在逐段生成的答案', { exact: true }),
  ).toBeVisible()
  await page.locator('.conversation-messages').evaluate((el) => {
    el.scrollTop = 0
    el.dispatchEvent(new Event('scroll'))
  })
  await expect(page.getByText('完整的流式答案', { exact: true })).toBeAttached()
  expect(
    await page.locator('.conversation-messages').evaluate((el) => el.scrollTop),
  ).toBeLessThan(80)
})

test('mobile floating conversation stays within a small viewport', async ({
  page,
}) => {
  await setup(page)
  await page.setViewportSize({ width: 390, height: 640 })
  await page.goto('/courses')
  await page.getByRole('button', { name: /打开智能助手/ }).click()
  await expect(
    page.locator('.agent-floating-panel .message-bubble.assistant'),
  ).toBeVisible()
  await expect(page.locator('.agent-floating-panel')).toHaveCSS('opacity', '1')
  const input = await page
    .locator('.agent-floating-panel .conversation-input')
    .boundingBox()
  expect(input.y + input.height).toBeLessThanOrEqual(640)
  expect(input.x).toBeGreaterThanOrEqual(0)
  expect(input.x + input.width).toBeLessThanOrEqual(390)
  await expect(page.locator('.agent-floating-panel')).toHaveCSS('opacity', '1')
  await page.screenshot({
    path: '../docs/competition/development/agent-records/2026-11-17-assistant-ui-repair/frontend/validation/floating-mobile.png',
    fullPage: true,
  })
})

test('copying an answer works without the secure clipboard API and restores focus', async ({
  page,
}) => {
  await setup(page)
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: undefined,
    })
    document.execCommand = (command) => {
      window.copiedAnswer = document.activeElement.value
      return command === 'copy'
    }
  })
  await page.goto('/knowledge-base')
  await page.getByPlaceholder('输入问题').focus()
  await page.getByRole('button', { name: '复制回答' }).click()
  await expect(page.getByText('已复制', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => window.copiedAnswer)).toBe(
    '我是你的学习助手。',
  )
  expect(await page.locator('textarea[data-assistant-copy]').count()).toBe(0)
  await expect(page.getByRole('button', { name: '复制回答' })).toBeFocused()
})

test('answers show the actual retrieval mode without a yellow warning block', async ({
  page,
}) => {
  await setup(page, { retrieval: true })
  await page.goto('/knowledge-base')
  await expect(page.getByTestId('answer-retrieval-mode')).toHaveText(
    '本次检索：关键词检索',
  )
  await expect(
    page.locator('.answer-warning'),
  ).toHaveCount(0)
})

test('a short question and answer both remain fully inside the default desktop floating message area', async ({
  page,
}) => {
  await setup(page, {
    answerText:
      '我是AI智学平台里的学习助手，可以帮你解答问题、梳理知识点、提供学习建议。你也可以选择课程，结合已有资料提问，并查看回答的参考出处。',
  })
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.goto('/courses')
  await page.getByRole('button', { name: /打开智能助手/ }).click()
  await expect(page.locator('.agent-floating-panel')).toHaveCSS('opacity', '1')
  const bounds = await page
    .locator('.agent-floating-panel .conversation-messages')
    .evaluate((messages) => {
      const area = messages.getBoundingClientRect()
      const bubbles = [...messages.querySelectorAll('.message-bubble')].map(
        (bubble) => {
          const box = bubble.getBoundingClientRect()
          return { top: box.top, bottom: box.bottom }
        },
      )
      return {
        top: area.top,
        bottom: area.bottom,
        scrollHeight: messages.scrollHeight,
        height: messages.clientHeight,
        bubbles,
      }
    })
  expect(bounds.bubbles).toHaveLength(2)
  expect(bounds.scrollHeight).toBeLessThanOrEqual(bounds.height + 1)
  for (const bubble of bounds.bubbles) {
    expect(bubble.top).toBeGreaterThanOrEqual(bounds.top)
    expect(bubble.bottom).toBeLessThanOrEqual(bounds.bottom)
  }
  await page.screenshot({
    path: '../docs/competition/development/agent-records/2026-11-17-assistant-ui-repair/frontend/validation/floating-brief-qa.png',
    fullPage: true,
  })
})
