import { test, expect } from '@playwright/test'

async function fixture(page, { role = 'teacher', draft = false } = {}) {
  await page.addInitScript(({ role, draft }) => {
    localStorage.setItem('token', 'synthetic-course-list-layout')
    localStorage.setItem('userId', '7')
    localStorage.setItem('username', '课程布局教师')
    localStorage.setItem('roleCode', role)
    if (draft) localStorage.setItem('eduloop:course-create:7', JSON.stringify({ form: { title: '浏览器中的新课程' } }))
  }, { role, draft })
  const calls = []
  await page.route('**/backend/**', route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/backend/, '')
    const query = Object.fromEntries(url.searchParams)
    calls.push({ path, method: request.method(), query })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/categories') return ok([{ id: 1, name: '计算机科学' }])
    if (path === '/courses') return ok({ records: [{
      id: 41, teacherId: 7, title: '人工智能基础', categoryId: 1, difficulty: 'beginner',
      status: 1, studentCount: 32, setupStage: query.box === 'drafts' ? 'CONTENT' : 'COMPLETED', updatedAt: '2026-11-17T15:30:00',
    }], total: 14 })
    if (path === '/courses/41') return ok({ id: 41, teacherId: 7, title: '人工智能基础', categoryId: 1, status: 1, setupStage: 'COMPLETED' })
    if (path === '/courses/41/publication') return ok({ status: 1, workflowVersion: 3, classIds: [11], classes: [{ id: 11, className: '人工智能一班', semester: '2026秋', studentCount: 32, status: 1, courseOpen: true }] })
    return ok([])
  })
  return calls
}

test('teacher course list combines search and results and directs basic editing to the course detail', async ({ page }) => {
  const calls = await fixture(page)
  await page.goto('/courses?title=人工智能&pageNum=2&pageSize=5')
  const panel = page.getByTestId('course-list-panel')
  await expect(panel.getByRole('region', { name: '筛选课程' })).toBeVisible()
  const row = page.getByRole('row').filter({ hasText: '人工智能基础' })
  await expect(row.locator('.course-metadata')).toHaveText('计算机科学 · 入门')
  await expect(row.getByRole('button', { name: '编辑信息', exact: true })).toHaveCount(0)
  await expect(row.getByRole('button', { name: '课程详情', exact: true })).toBeVisible()
  await row.getByRole('button', { name: '发布范围', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '调整发布范围', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect.poll(() => calls.find(call => call.path === '/courses')?.query).toMatchObject({ title: '人工智能', pageNum: '2', pageSize: '5', box: 'completed' })
  await panel.locator('.category-filter .el-select').click()
  await page.getByRole('option', { name: '计算机科学', exact: true }).click()
  await panel.getByPlaceholder('请输入课程名称').fill('Python')
  await panel.getByPlaceholder('请输入课程名称').press('Enter')
  await expect(page).toHaveURL(/pageNum=1/)
  await expect.poll(() => calls.filter(call => call.path === '/courses').at(-1)?.query).toMatchObject({ title: 'Python', categoryId: '1', pageNum: '1' })
  await panel.getByRole('button', { name: '重置', exact: true }).click()
  await expect.poll(() => calls.filter(call => call.path === '/courses').at(-1)?.query).toEqual({ title: '', pageNum: '1', pageSize: '5', box: 'completed' })
  await page.screenshot({ path: test.info().outputPath('course-management-layout.png'), fullPage: true })
})

test('drafts retain separate basic editing and browser draft recovery', async ({ page }) => {
  await fixture(page, { draft: true })
  await page.goto('/courses/drafts')
  await expect(page.getByTestId('local-course-draft')).toContainText('浏览器中的新课程')
  const row = page.getByRole('row').filter({ hasText: '人工智能基础' })
  await expect(row.getByRole('button', { name: '继续编辑', exact: true })).toBeVisible()
  await row.getByRole('button', { name: '编辑信息', exact: true }).click()
  await expect(page).toHaveURL(/courses\/create\?id=41&returnTo=/)
})

test('administrator retains record editing and class administration', async ({ page }) => {
  await fixture(page, { role: 'admin' })
  await page.goto('/courses')
  const row = page.getByRole('row').filter({ hasText: '人工智能基础' })
  await expect(row.getByRole('button', { name: '编辑信息', exact: true })).toBeVisible()
  await expect(row.getByRole('button', { name: '调整教学班', exact: true })).toBeVisible()
  await expect(row.getByRole('button', { name: '课程详情', exact: true })).toHaveCount(0)
})

test('mobile course list keeps course identity and row actions reachable', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await fixture(page)
  await page.goto('/courses')
  const row = page.getByRole('row').filter({ hasText: '人工智能基础' })
  const title = row.getByText('人工智能基础', { exact: true })
  await expect(title).toBeVisible()
  await expect.poll(() => title.evaluate(element => {
    const rect = element.getBoundingClientRect()
    const hit = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2)
    return Boolean(hit && (element.contains(hit) || hit.contains(element)))
  })).toBe(true)
  await row.getByRole('button', { name: '人工智能基础 的操作', exact: true }).click()
  const sheet = page.locator('.row-action-sheet:visible')
  await expect(sheet.getByRole('button', { name: '课程详情', exact: true })).toBeVisible()
  await expect(sheet.getByRole('button', { name: '发布范围', exact: true })).toBeVisible()
  await expect(sheet.getByRole('button', { name: '编辑信息', exact: true })).toHaveCount(0)
})
