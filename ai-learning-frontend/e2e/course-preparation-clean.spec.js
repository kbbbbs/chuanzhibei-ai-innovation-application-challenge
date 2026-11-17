import { test, expect } from '@playwright/test'

async function fixture(page, { applied = false, rejectApply = false, includeQuestions = false } = {}) {
  const calls = []
  const project = () => ({ id: 21, projectStatus: applied ? 'PUBLISHED' : 'DRAFT', currentStage: applied ? 'PUBLISHED' : 'READY_TO_PUBLISH', generationStatus: 'SUCCEEDED', publishedCourseId: applied ? 41 : null,
    config: { targetCourseId: 41, title: '操作系统原理', categoryId: 1, audience: '本科生', includeQuestions },
    outline: { chapters: [{ chapterKey: 'c1', title: '进程', lessons: [{ lessonKey: 'l1', title: '进程概念' }] }] }, lessons: [{ lessonKey: 'l1', title: '进程概念', contentMd: '# 进程概念', status: 'GENERATED' }], questions: includeQuestions ? [{ chapterKey: 'c1', chapterTitle: '进程', questions: [{ content: '什么是进程？', type: 'SHORT_ANSWER' }] }] : [] })
  await page.addInitScript(() => { localStorage.clear(); localStorage.setItem('token', 'synthetic-clean-preparation'); localStorage.setItem('roleCode', 'teacher'); localStorage.setItem('userId', '7') })
  await page.route('**/backend/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    calls.push({ path, method: request.method() })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    const course = { id: 41, teacherId: 7, title: '操作系统原理', categoryId: 1, status: 2, setupStage: 'CONTENT', contentMode: 'AI', preparationId: 21, workflowVersion: 1 }
    if (path === '/courses/41' || path === '/courses/41/setup') return ok(course)
    if (path === '/categories') return ok([{ id: 1, name: '计算机' }])
    if (path === '/api/preparations') return ok([project()])
    if (path === '/api/preparations/21' || path.endsWith('/ready') || path.endsWith('/lessons') || path.endsWith('/questions')) return ok(project())
    if (path.endsWith('/apply-preview')) return ok({ courseId: 41, courseVersion: 'v1', applicationId: applied ? 21 : null, alreadyApplied: applied, existingLessons: [] })
    if (path.endsWith('/apply')) {
      if (rejectApply) return route.fulfill({ status: 409, json: { code: '409', msg: '课程版本已变化' } })
      applied = true; return ok({ applicationId: 21, courseId: 41 })
    }
    if (path === '/api/rag/courses/41/knowledge-base') return ok({ id: 'kb', owner_id: 7 })
    return ok([])
  })
  await page.goto('/courses/41')
  return calls
}

test('applied creation shows a clean completion step without the redundant course draft header', async ({ page }) => {
  await fixture(page, { applied: true })
  await expect(page.getByRole('button', { name: '生成完成', exact: true })).toHaveAttribute('aria-current', 'step')
  await expect(page.getByRole('heading', { name: '准备课程内容', exact: true })).toHaveCount(0)
  await expect(page.getByLabel('继续最近的备课草稿')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '刷新', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '新备课', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '回退本次应用' })).toHaveCount(0)
  await expect(page.locator('.creation-complete .el-result__icon')).toBeVisible()
  await expect(page.getByRole('button', { name: '完成创建', exact: true })).toBeVisible()
  await page.screenshot({ path: test.info().outputPath('clean-completion.png'), fullPage: true })
})

test('last lesson confirmation writes once and completes without a second application page', async ({ page }) => {
  const calls = await fixture(page)
  const confirm = page.getByRole('button', { name: '确认并完成备课', exact: true })
  await expect(confirm).toBeVisible()
  expect(calls.filter(call => call.path.endsWith('/apply'))).toHaveLength(0)
  await confirm.click()
  await expect(page.locator('.creation-complete')).toBeVisible()
  expect(calls.filter(call => call.path.endsWith('/apply'))).toHaveLength(1)
  expect(calls.filter(call => call.path.endsWith('/complete'))).toHaveLength(0)
  await page.reload()
  await expect(page.locator('.creation-complete')).toBeVisible()
  expect(calls.filter(call => call.path.endsWith('/apply'))).toHaveLength(1)
})

test('failed course write keeps the review step and does not display success', async ({ page }) => {
  await fixture(page, { rejectApply: true })
  await page.getByRole('button', { name: '确认并完成备课', exact: true }).click()
  await expect(page.getByText('内容写入未完成，请重试确认', { exact: true })).toBeVisible()
  await expect(page.locator('.creation-complete')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '确认并完成备课', exact: true })).toBeEnabled()
})

test('question confirmation also completes course preparation with a single write', async ({ page }) => {
  const calls = await fixture(page, { includeQuestions: true })
  await expect(page.getByRole('button', { name: '章节题目', exact: true })).toHaveAttribute('aria-current', 'step')
  await page.getByRole('button', { name: '确认并完成备课', exact: true }).click()
  await expect(page.locator('.creation-complete')).toBeVisible()
  expect(calls.filter(call => call.path.endsWith('/apply'))).toHaveLength(1)
})
