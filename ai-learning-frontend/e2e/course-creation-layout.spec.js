import { test, expect } from '@playwright/test'

async function fixture(page, stage = 'BASIC') {
  let course = { id: 41, teacherId: 7, title: '人工智能基础', categoryId: 1, difficulty: 'beginner', status: 2, setupStage: stage, workflowVersion: 1 }
  const calls = []
  await page.addInitScript(() => {
    localStorage.clear()
    localStorage.setItem('token', 'course-creation-layout-fixture')
    localStorage.setItem('userId', '7')
    localStorage.setItem('roleCode', 'teacher')
  })
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    const body = request.postData() ? request.postDataJSON() : null
    calls.push({ path, method: request.method(), body })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/categories') return ok([{ id: 1, name: '人工智能' }])
    if (path === '/courses/41/setup' && request.method() === 'PUT') {
      course = { ...course, ...body, workflowVersion: course.workflowVersion + 1 }
      return ok(course)
    }
    if (path === '/courses/41' || path === '/courses/41/setup') return ok(course)
    if (path === '/api/rag/courses/41/knowledge-base') return ok({ id: 'course-base', name: course.title, owner_id: 7 })
    return ok([])
  })
  return calls
}

test('new course basic form places the progress and fields inside one creation panel', async ({ page }) => {
  await fixture(page)
  await page.goto('/courses/create')
  const panel = page.getByTestId('course-form-panel')
  await expect(panel.getByText('课程资料', { exact: true })).toBeVisible()
  await expect(panel.getByRole('heading', { name: '先认识这门课程', exact: true })).toBeVisible()
  await expect(panel.getByPlaceholder('请输入课程名称')).toBeVisible()
  await expect(panel).toHaveCSS('border-top-style', 'solid')
  await expect(panel.getByRole('button', { name: '下一步', exact: true })).toBeEnabled()
  await page.screenshot({ path: test.info().outputPath('creation-basic.png'), fullPage: true })
})

test('persisted creation keeps its clickable steps and footer around the materials component', async ({ page }) => {
  const calls = await fixture(page, 'MATERIALS')
  await page.goto('/courses/41?setup=materials')
  const panel = page.getByTestId('course-creation-panel')
  await expect(panel.getByTestId('course-creation-steps')).toBeVisible()
  await expect(panel.getByRole('heading', { name: '准备课程资料', exact: true })).toBeVisible()
  await expect(panel.getByRole('button', { name: '保存草稿并退出', exact: true })).toBeVisible()
  await page.screenshot({ path: test.info().outputPath('creation-materials.png'), fullPage: true })
  await panel.getByRole('button', { name: '返回基本信息', exact: true }).click()
  await expect(page).toHaveURL(/setup=basic$/)
  await expect(panel.getByRole('heading', { name: '先认识这门课程', exact: true })).toBeVisible()
  expect(calls.filter(call => call.method === 'PUT').map(call => call.body.workflowVersion)).toEqual([1])
})

test('the new creation form fits a narrow viewport without losing its footer actions', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await fixture(page)
  await page.goto('/courses/create')
  await expect(page.getByTestId('course-form-panel')).toBeVisible()
  await page.getByRole('button', { name: '下一步', exact: true }).scrollIntoViewIfNeeded()
  await expect(page.getByRole('button', { name: '下一步', exact: true })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
