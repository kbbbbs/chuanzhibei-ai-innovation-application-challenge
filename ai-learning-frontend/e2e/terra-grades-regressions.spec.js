import { test, expect } from '@playwright/test'

async function authenticate(page, role) {
  await page.addInitScript(role => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', role)
    localStorage.setItem('username', 'student')
  }, role)
}

for (const failingPath of ['/api/submissions', '/api/homework-submissions']) {
  test(`grades reports business failure from ${failingPath}`, async ({ page }) => {
    await authenticate(page, 'student')
    await page.route('**/backend/**', route => {
      const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
      return route.fulfill({ json: path === failingPath
        ? { code: '500', message: 'synthetic failure', data: null }
        : { code: '200', data: [] } })
    })
    await page.goto('/my-grades')
    await expect(page.getByText('成绩未加载')).toBeVisible()
  })
}

test('student statistics compare scores against each known full score', async ({ page }) => {
  await authenticate(page, 'student')
  await page.route('**/backend/**', route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
    const data = path === '/api/submissions' ? [
      { id: 1, examName: '五十分考试', status: 'GRADED', totalScore: 30, answers: [{ maxScore: 20 }, { maxScore: 30 }] },
      { id: 2, examName: '百分考试', status: 'GRADED', totalScore: 80, answers: [{ maxScore: 100 }] },
      { id: 3, examName: '未知满分考试', status: 'GRADED', totalScore: 0, answers: [{ maxScore: null }] },
    ] : []
    return route.fulfill({ json: { code: '200', data } })
  })
  await page.goto('/my-grades')
  await expect(page.getByText('未知满分考试')).toBeVisible()
  const metrics = page.getByRole('region', { name: '成绩概览' })
  await expect(metrics.locator('.metric-tile').filter({ hasText: /平均/ })).toContainText('70')
  await expect(metrics.locator('.metric-tile').filter({ hasText: /及格率|合格率/ })).toContainText('100%')
})

test('repeated refresh recovers from business failure with the latest results', async ({ page }) => {
  await authenticate(page, 'student')
  let examRequests = 0
  await page.route('**/backend/**', route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
    if (path !== '/api/submissions') return route.fulfill({ json: { code: '200', data: [] } })
    examRequests += 1
    if (examRequests === 2) return route.fulfill({ json: { code: '500', message: 'synthetic failure', data: null } })
    const name = examRequests === 1 ? '第一次成绩' : '最新成绩'
    return route.fulfill({ json: { code: '200', data: [
      { id: examRequests, examName: name, status: 'GRADED', totalScore: 60, answers: [{ maxScore: 100 }] },
    ] } })
  })
  await page.goto('/my-grades')
  await expect(page.getByText('第一次成绩')).toBeVisible()
  await page.getByRole('button', { name: '刷新' }).click()
  await expect(page.getByText('成绩未加载')).toBeVisible()
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByText('最新成绩')).toBeVisible()
  await expect(page.getByText('成绩未加载')).toHaveCount(0)
  await expect(page.getByText('第一次成绩')).toHaveCount(0)
  expect(examRequests).toBe(3)
})

test('mixed full scores use each assessment passing threshold', async ({ page }) => {
  await authenticate(page, 'teacher')
  await page.route('**/backend/**', route => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace(/^\/backend/, '')
    let data = []
    if (path === '/teaching-classes') data = [{ id: 7, className: '测试班' }]
    if (path === '/class-students/class/7') data = [{ studentId: 2 }]
    if (path === '/users/2') data = { id: 2, username: '测试学生' }
    if (path === '/api/exams') data = [
      { id: 1, title: '五十分考试', totalScore: 50 },
      { id: 2, title: '百分考试', totalScore: 100 },
    ]
    if (path === '/api/submissions') data = [{
      studentId: 2, status: 'GRADED', totalScore: url.searchParams.get('examId') === '1' ? 30 : 60,
      answers: [{ maxScore: url.searchParams.get('examId') === '1' ? 50 : 100 }],
    }]
    return route.fulfill({ json: { code: '200', data } })
  })
  await page.goto('/grade-analysis')
  await page.locator('.class-select').click()
  await page.getByRole('option', { name: /测试班/ }).click()
  await expect(page.locator('.exam-card').filter({ hasText: '五十分考试' })).toBeVisible()
  await expect(page.locator('.metric-tile').filter({ hasText: '已评分合格率' })).toContainText('100%')
  await expect(page.locator('.exam-card').filter({ hasText: '五十分考试' })).toContainText('100%')
})
