import { test, expect } from '@playwright/test'

async function setup(page, courses) {
  const requests = []
  await page.addInitScript(() => {
    localStorage.setItem('token', 'class-before-course-fixture')
    localStorage.setItem('userId', '2')
    localStorage.setItem('username', '演示教师')
    localStorage.setItem('roleCode', 'teacher')
  })
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.port !== '8080') return route.continue()
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (url.pathname === '/courses') return ok({ records: courses, total: courses.length })
    if (route.request().method() === 'POST' || route.request().method() === 'PUT') {
      requests.push({ path: url.pathname, body: route.request().postDataJSON() })
      return ok({})
    }
    if (url.pathname === '/teaching-classes') return ok({
      records: [{ id: 1, courseId: null, teacherId: 2, className: '人工智能一班', semester: '2026秋', status: 1 }],
      total: 1
    })
    return ok([])
  })
  await page.goto('/teaching-classes')
  return requests
}

test('teacher can prepare a class before any course exists', async ({ page }) => {
  const requests = await setup(page, [])
  await page.getByRole('button', { name: '创建教学班', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '创建教学班' })
  await expect(dialog).toBeVisible()
  await dialog.locator('.el-form-item').filter({ hasText: '班级名称' }).getByRole('textbox').fill('人工智能二班')
  await dialog.getByPlaceholder('如：2026春季').fill('2026秋')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => requests.length).toBe(1)
  expect(requests[0]).toMatchObject({ path: '/teaching-classes', body: { courseId: null, className: '人工智能二班', teacherId: 2 } })
})

test('teacher can assign a newly created course to the pre-enrolled class', async ({ page }) => {
  const requests = await setup(page, [{ id: 10, title: '人工智能基础', teacherId: 2 }])
  const row = page.locator('tr').filter({ hasText: '人工智能一班' })
  await expect(row).toContainText('待关联课程')
  await row.getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '编辑教学班' })
  await dialog.locator('.el-select').click()
  await page.getByRole('option', { name: '人工智能基础', exact: true }).click()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => requests.length).toBe(1)
  expect(requests[0]).toMatchObject({ path: '/teaching-classes/1', body: { courseId: 10, className: '人工智能一班', teacherId: 2 } })
})
