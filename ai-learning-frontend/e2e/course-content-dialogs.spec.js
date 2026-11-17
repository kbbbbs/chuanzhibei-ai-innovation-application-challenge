import { test, expect } from '@playwright/test'

// Synthetic responses only: the course workspace dialogs validate inline and never create a lesson twice.
const ok = data => ({ json: { code: '200', data } })

async function fixture(page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-course-content')
    localStorage.setItem('userId', '7')
    localStorage.setItem('roleCode', 'teacher')
  })
  const calls = []
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    const method = request.method()
    calls.push({ path, method, body: request.postData() ? request.postDataJSON() : null })
    if (path === '/courses/41') return route.fulfill(ok({ id: 41, title: '人工智能基础', teacherId: 7, status: 2, setupStage: 'COMPLETED' }))
    if (path === '/courses/41/chapters') return route.fulfill(ok([{ id: 1, courseId: 41, title: '第一章', sortOrder: 1 }]))
    if (path === '/chapters/1/lessons') return route.fulfill(ok([]))
    if (path === '/lessons' && method === 'POST') return route.fulfill(ok({ id: 77, chapterId: 1, title: request.postDataJSON().title }))
    if (path === '/lessons/77' && method === 'PUT') return route.fulfill(ok({ id: 77, chapterId: 1 }))
    if (path === '/video-resources/url') return route.fulfill({ status: 400, json: { code: '400', msg: '视频地址无法访问' } })
    return route.fulfill(ok([]))
  })
  await page.goto('/courses/41')
  await expect(page.getByRole('button', { name: '新增章节' })).toBeVisible()
  return calls
}

test('an empty chapter title is caught in the dialog without a request', async ({ page }) => {
  const calls = await fixture(page)
  await page.getByRole('button', { name: '新增章节' }).click()
  const dialog = page.getByRole('dialog', { name: '新增章节' })
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toContainText('请输入章节标题')
  expect(calls.some(call => call.path === '/chapters' && call.method === 'POST')).toBe(false)
})

test('a video lesson whose download is refused is saved once and the next save updates it', async ({ page }) => {
  const calls = await fixture(page)
  await page.locator('.chapter-actions').getByRole('button', { name: '添加课时' }).click()
  const dialog = page.getByRole('dialog', { name: '新增课时' })
  await dialog.locator('input').first().fill('视频课')
  await dialog.locator('.el-select').first().click()
  await page.getByRole('option', { name: '视频 URL' }).click()
  await dialog.getByPlaceholder('https://example.com/video').fill('ftp://example.com/a.mp4')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-message').first()).toContainText('http:// 或 https://')
  expect(calls.some(call => call.path === '/lessons')).toBe(false)

  await dialog.getByPlaceholder('https://example.com/video').fill('https://example.com/a.mp4')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-message', { hasText: '课时已保存，但视频没有进入下载队列' })).toBeVisible()
  await expect(page.getByRole('dialog', { name: '编辑课时' })).toBeVisible()
  await page.getByRole('dialog', { name: '编辑课时' }).getByRole('button', { name: '保存' }).click()
  await expect.poll(() => calls.filter(call => call.path === '/lessons/77' && call.method === 'PUT').length).toBe(1)
  expect(calls.filter(call => call.path === '/lessons' && call.method === 'POST').length).toBe(1)
  expect(calls.find(call => call.path === '/lessons' && call.method === 'POST').body.sortOrder).toBeNull()
})
