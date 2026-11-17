import { test, expect } from '@playwright/test'

async function fixture(page, { historyAction, draft } = {}) {
  await page.addInitScript(draft => {
    for (const [key, value] of Object.entries({ token: 'page-handoff-fixture', userId: '36', roleCode: 'teacher', username: '页面助手测试' })) localStorage.setItem(key, value)
    if (draft && !localStorage.getItem('eduloop:course-create:36')) localStorage.setItem('eduloop:course-create:36', JSON.stringify({ form: draft, creationKey: 'previous-draft' }))
  }, draft)
  const calls = []
  let action = historyAction
  let messages = historyAction ? [{ id: 20, role: 'assistant', content: '已准备页面', pageAction: historyAction }] : []
  let nextId = 20
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    const body = request.postData() ? request.postDataJSON() : null
    calls.push({ path, method: request.method(), body })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/api/setup/status') return ok({ state: 'READY' })
    if (path === '/courses') return ok({ records: [{ id: 41, title: '人工智能基础' }, { id: 42, title: '机器学习' }], total: 2 })
    if (path === '/categories') return ok([{ id: 5, name: '人工智能' }, { id: 6, name: '计算机基础' }])
    if (path === '/api/learning-analysis/templates') return ok([{ id: 'default', name: '默认模板', fields: [] }])
    if (path === '/api/learning-analysis/reports') return ok([])
    if (path === '/api/assistant/sessions') return ok(request.method() === 'POST' ? { id: 1 } : [{ id: 1, title: '页面准备', scope: 'GENERAL' }])
    if (path.endsWith('/messages')) {
      if (request.method() === 'POST') {
        messages.push({ id: ++nextId, role: 'user', content: body.content })
        messages.push({ id: ++nextId, role: 'assistant', content: '已准备页面，请核对后继续。', pageAction: action, requestId: body.requestId })
        return ok({ status: 'RUNNING', requestId: body.requestId })
      }
      return ok(messages)
    }
    if (path.endsWith('/stream')) return route.fulfill({ contentType: 'text/event-stream', body: 'event: complete\ndata: {"status":"COMPLETED"}\n\n' })
    if (path.includes('/requests/')) return ok({ status: 'COMPLETED', message: messages.at(-1) })
    return ok([])
  })
  return { calls, setAction: value => { action = value } }
}

async function ask(page, text) {
  await page.getByRole('button', { name: '打开智能助手，拖动可调整位置' }).click()
  const panel = page.getByRole('region', { name: '智能助手', exact: true })
  await panel.getByPlaceholder('输入问题').fill(text)
  await panel.getByRole('button', { name: '发送', exact: true }).click()
}

test('floating assistant selects the requested learning-analysis course and leaves generation to the teacher', async ({ page }) => {
  const state = await fixture(page)
  state.setAction({ type: 'learning-analysis', courseId: 41, courseTitle: '人工智能基础' })
  await page.goto('/courses')
  await ask(page, '帮我分析一下人工智能基础的学情')
  await expect(page).toHaveURL(/\/grade-analysis\?courseId=41$/)
  await expect(page.locator('.report-form')).toContainText('人工智能基础')
  await expect(page.getByRole('button', { name: '一键生成学情报告' })).toBeEnabled()
  expect(state.calls.find(call => call.path.endsWith('/messages') && call.method === 'POST').body.context.pageHandoff).toBe(true)
  expect(state.calls.some(call => call.path === '/api/learning-analysis/reports' && call.method === 'POST')).toBe(false)
  state.setAction({ type: 'learning-analysis', courseId: 42, courseTitle: '机器学习' })
  await ask(page, '再看机器学习的学情')
  await expect(page).toHaveURL(/courseId=42$/)
  await expect(page.locator('.report-form')).toContainText('机器学习')
})

test('floating assistant prefills course fields, preserves manual fields and waits for Next', async ({ page }) => {
  const state = await fixture(page)
  state.setAction({ type: 'course-create', fields: { title: '人工智能基础', categoryId: 5, categoryName: '人工智能', difficulty: 'intermediate', teacherId: 999, status: 1 } })
  await page.goto('/courses/create')
  await page.getByPlaceholder('请输入课程名称').fill('原课程草稿')
  await page.getByPlaceholder('请输入课程简介').fill('原草稿简介')
  await ask(page, '新建人工智能基础，分类人工智能，难度基础')
  await expect(page).toHaveURL(/\/courses\/create\?assistantDraft=/)
  await expect(page.getByPlaceholder('请输入课程名称')).toHaveValue('人工智能基础')
  await expect(page.getByPlaceholder('请输入课程简介')).toHaveValue('原草稿简介')
  await expect(page.locator('.field-pair')).toContainText('人工智能')
  await expect(page.locator('.field-pair')).toContainText('基础')
  await expect(page.getByRole('button', { name: '下一步', exact: true })).toBeEnabled()
  expect(state.calls.some(call => call.path === '/courses' && call.method === 'POST')).toBe(false)
  await page.reload()
  await expect(page.getByPlaceholder('请输入课程名称')).toHaveValue('人工智能基础')
  await expect(page.getByPlaceholder('请输入课程简介')).toHaveValue('原草稿简介')
  await page.getByPlaceholder('请输入课程简介').fill('人工修改简介')
  state.setAction({ type: 'course-create', fields: { title: '机器学习入门' } })
  await ask(page, '把课程名填成机器学习入门')
  await expect(page.getByPlaceholder('请输入课程名称')).toHaveValue('机器学习入门')
  await expect(page.getByPlaceholder('请输入课程简介')).toHaveValue('人工修改简介')
  expect(state.calls.some(call => call.path === '/courses' && call.method === 'POST')).toBe(false)
})

test('slow categories cannot overwrite manual edits and explicit Exit discards the assisted draft', async ({ page }) => {
  const state = await fixture(page)
  let releaseCategories
  const released = new Promise(resolve => { releaseCategories = resolve })
  await page.route('**/backend/categories', async route => {
    await released
    await route.fulfill({ json: { code: '200', data: [{ id: 5, name: '人工智能' }, { id: 6, name: '计算机基础' }] } })
  })
  state.setAction({ type: 'course-create', fields: { title: '人工智能基础', categoryName: '人工智能', description: '助手简介' } })
  await page.goto('/courses')
  await ask(page, '新建人工智能基础，分类人工智能，简介助手简介')
  await expect(page.getByPlaceholder('请输入课程名称')).toHaveValue('人工智能基础')
  await page.getByPlaceholder('请输入课程名称').fill('人工确认后的名称')
  await page.getByPlaceholder('请输入课程简介').fill('人工确认后的简介')
  releaseCategories()
  await expect(page.locator('.field-pair')).toContainText('人工智能')
  await expect(page.getByPlaceholder('请输入课程名称')).toHaveValue('人工确认后的名称')
  await expect(page.getByPlaceholder('请输入课程简介')).toHaveValue('人工确认后的简介')
  await page.reload()
  await expect(page.getByPlaceholder('请输入课程名称')).toHaveValue('人工确认后的名称')
  await page.getByRole('button', { name: '退出', exact: true }).click()
  await expect(page).toHaveURL(/\/courses\/drafts$/)
  await page.goto('/courses/create')
  await expect(page.getByPlaceholder('请输入课程名称')).toHaveValue('')
  expect(state.calls.some(call => call.path === '/courses' && call.method === 'POST')).toBe(false)
})

test('course edit data and historical report links retain their entry behavior', async ({ page }) => {
  const state = await fixture(page)
  await page.route('**/backend/courses/41', route => route.fulfill({ json: { code: '200', data: { id: 41, title: '原有已保存课程', categoryId: 6, difficulty: 'advanced', description: '原课程简介' } } }))
  state.setAction({ type: 'course-create', fields: { title: '全新课程' } })
  await page.goto('/courses/create?id=41')
  await expect(page.getByPlaceholder('请输入课程名称')).toHaveValue('原有已保存课程')
  await ask(page, '新建一门全新课程')
  await expect(page).toHaveURL(/\/courses\/create\?assistantDraft=/)
  await expect(page.getByPlaceholder('请输入课程名称')).toHaveValue('全新课程')
  await expect(page.getByPlaceholder('请输入课程简介')).toHaveValue('')
  expect(state.calls.some(call => call.path === '/courses/41' && call.method === 'PUT')).toBe(false)
  await page.route('**/backend/api/learning-analysis/reports/saved-report', route => route.fulfill({ json: { code: '200', data: { id: 'saved-report', courseId: 42, courseTitle: '机器学习', templateId: 'default', targetWords: 1000, wordCount: 10, status: 'SUCCESS', content: '历史学情报告正文' } } }))
  await page.goto('/grade-analysis?reportId=saved-report&courseId=41')
  await expect(page.locator('.report-form')).toContainText('机器学习')
  await expect(page.getByText('历史学情报告正文', { exact: true })).toBeVisible()
  await page.goto('/grade-analysis?courseId=999')
  await expect(page.getByText('课程不存在或无权分析，请选择自己的课程。', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '一键生成学情报告' })).toBeDisabled()
})

test('opening old page-action history never navigates and unrecognized actions stay in chat', async ({ page }) => {
  const state = await fixture(page, { historyAction: { type: 'learning-analysis', courseId: 41 } })
  await page.goto('/courses')
  await page.getByRole('button', { name: '打开智能助手，拖动可调整位置' }).click()
  await expect(page.getByText('已准备页面', { exact: true })).toBeVisible()
  await expect(page).toHaveURL(/\/courses$/)
  state.setAction({ type: 'external-url', url: 'https://example.invalid' })
  await page.getByPlaceholder('输入问题').fill('继续')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByText('已准备页面，请核对后继续。', { exact: true })).toBeVisible()
  await expect(page).toHaveURL(/\/courses$/)
})

test('a successful new action still opens its page when the history refresh fails', async ({ page }) => {
  const state = await fixture(page)
  state.setAction({ type: 'learning-analysis', courseId: 41 })
  let reads = 0
  await page.route('**/backend/api/assistant/sessions/1/messages', async route => {
    if (route.request().method() === 'GET' && ++reads === 2) {
      return route.fulfill({ status: 503, json: { code: '503', msg: 'temporary history failure' } })
    }
    return route.fallback()
  })
  await page.goto('/courses')
  await ask(page, '分析人工智能基础学情')
  await expect(page).toHaveURL(/\/grade-analysis\?courseId=41$/)
  await expect(page.locator('.report-form')).toContainText('人工智能基础')
  await page.reload()
  await expect(page).toHaveURL(/\/grade-analysis\?courseId=41$/)
  expect(state.calls.filter(call => call.method === 'POST' && call.path.endsWith('/messages'))).toHaveLength(1)
})

test('resuming a request from history cannot replay a navigation', async ({ page }) => {
  const state = await fixture(page, { historyAction: { type: 'learning-analysis', courseId: 41 } })
  await page.route('**/backend/api/assistant/sessions', async route => {
    if (route.request().method() === 'GET') return route.fulfill({ json: { code: '200', data: [{ id: 1, title: '恢复中的历史', activeRequestId: 'old-run' }] } })
    return route.fallback()
  })
  await page.goto('/courses')
  await page.getByRole('button', { name: '打开智能助手，拖动可调整位置' }).click()
  await expect.poll(() => state.calls.some(call => call.path.endsWith('/requests/old-run'))).toBe(true)
  await expect(page.getByText('已准备页面', { exact: true })).toBeVisible()
  await expect(page).toHaveURL(/\/courses$/)
})

test('page actions enforce role, destination and form-field allowlists', async ({ page }) => {
  await fixture(page)
  await page.goto('/courses')
  const result = await page.evaluate(async () => {
    const { assistantPageDestination } = await import('/src/utils/assistantPageAction.js')
    const student = { userId: 37, isLoggedIn: true, isTeacher: false, isAdmin: false }
    const admin = { userId: 1, isLoggedIn: true, isTeacher: false, isAdmin: true }
    const teacher = { userId: 36, isLoggedIn: true, isTeacher: true, isAdmin: false }
    const action = { type: 'course-create', fields: { title: '新课程', categoryId: 5, teacherId: 999, status: 1, id: 41 } }
    const destination = assistantPageDestination(action, teacher, 'allowlist-check')
    return {
      student: assistantPageDestination({ type: 'learning-analysis', courseId: 41 }, student, 'student-check'),
      adminCreate: assistantPageDestination(action, admin, 'admin-check'),
      invalidId: assistantPageDestination({ type: 'learning-analysis', courseId: -1 }, teacher, 'invalid-check'),
      destination,
      fields: JSON.parse(sessionStorage.getItem('assistant:course-prefill:36:allowlist-check')),
    }
  })
  expect(result).toEqual({
    student: null, adminCreate: null, invalidId: null,
    destination: { path: '/courses/create', query: { assistantDraft: 'allowlist-check' } },
    fields: { title: '新课程', categoryId: 5 },
  })
})
