import { test, expect } from '@playwright/test'

test('switching to homework ignores the late exam response', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'student')
    localStorage.setItem('username', 'student')
  })
  let releaseExam
  const examGate = new Promise(resolve => { releaseExam = resolve })
  let examRequested
  const requested = new Promise(resolve => { examRequested = resolve })
  await page.route('**/backend/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
    let data = []
    if (path === '/class-students/student/2') data = [{ classId: 7 }]
    if (path === '/api/exams') {
      examRequested()
      await examGate
      data = [{ id: 21, classId: 7, name: '迟到的考试', status: 1, startTime: '2020-01-01', endTime: '2099-01-01' }]
    }
    if (path === '/api/homeworks') data = [{ id: 11, classId: 7, name: '当前作业', status: 1, startTime: '2020-01-01', endTime: '2099-01-01' }]
    await route.fulfill({ json: { code: '200', data } }).catch(() => {})
  })
  await page.goto('/my-exams')
  await requested
  await page.getByRole('link', { name: '我的作业' }).click()
  await expect(page.getByText('当前作业')).toBeVisible()
  const examResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/backend/api/exams')
  releaseExam()
  await examResponse
  await page.waitForTimeout(500)
  await expect(page.getByText('迟到的考试')).toHaveCount(0)
  await expect(page.getByText('当前作业')).toBeVisible()
})
