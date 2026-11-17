import { test, expect } from '@playwright/test'

test('student cards show only published courses in active teaching classes', async ({ page }) => {
  let published = false
  await page.addInitScript(() => {
    localStorage.setItem('token', 'recording-visibility-fixture')
    localStorage.setItem('userId', '3')
    localStorage.setItem('username', '录制学生')
    localStorage.setItem('roleCode', 'student')
  })
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (!['8080', '18039', '18049'].includes(url.port)) return route.continue()
    const ok = data => route.fulfill({ json: { code: '200', data } })
    const path = url.pathname
    if (path === '/class-students/student/3') return ok([
      { id: 1, classId: 1, status: 1 },
      { id: 2, classId: 2, status: 1 },
      { id: 3, classId: 3, status: 1 }
    ])
    const classMatch = path.match(/^\/teaching-classes\/(\d+)$/)
    if (classMatch) {
      const id = Number(classMatch[1])
      return ok({ id, courseId: id, className: `演示班${id}`, status: id === 3 ? 0 : 1 })
    }
    const courseMatch = path.match(/^\/courses\/(\d+)$/)
    if (courseMatch) {
      const id = Number(courseMatch[1])
      return ok({ id, title: ['','RAG已发布课程','录制待发布草稿','停用班课程'][id], status: id === 2 && !published ? 2 : 1 })
    }
    return ok([])
  })
  await page.goto('/my-classes')
  await expect(page.getByRole('heading', { name: 'RAG已发布课程' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '录制待发布草稿' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '停用班课程' })).toHaveCount(0)
  published = true
  await page.reload()
  await expect(page.getByRole('heading', { name: '录制待发布草稿' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '停用班课程' })).toHaveCount(0)
})
