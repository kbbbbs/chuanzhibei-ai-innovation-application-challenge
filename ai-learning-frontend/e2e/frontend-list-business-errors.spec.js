import { test, expect } from '@playwright/test'

for (const failingPath of ['/class-students/student/2', '/api/exams', '/api/submissions']) {
  test(`exam list reports business failure from ${failingPath}`, async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('token', 'synthetic-acceptance')
      localStorage.setItem('userId', '2')
      localStorage.setItem('roleCode', 'student')
      localStorage.setItem('username', 'student')
    })
    await page.route('**/backend/**', async route => {
      const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
      if (path === failingPath) {
        await route.fulfill({ json: { code: '500', message: 'synthetic failure', data: null } })
        return
      }
      let data = []
      if (path === '/class-students/student/2') data = [{ classId: 7 }]
      if (path === '/api/exams') data = [
        { id: 21, classId: 7, name: '已提交考试', status: 1, startTime: '2020-01-01', endTime: '2099-01-01' },
      ]
      if (path === '/api/submissions') data = [{ id: 31, examId: 21, status: 'GRADED' }]
      await route.fulfill({ json: { code: '200', data } })
    })
    await page.goto('/my-exams')
    await expect(page.getByText('考试列表未加载')).toBeVisible()
    await expect(page.getByRole('button', { name: '开始考试' })).toHaveCount(0)
  })
}
