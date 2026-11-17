import { test, expect } from '@playwright/test'
import { installTeacherFixtures } from './helpers/teacher-route-fixtures.js'

// useEchart registers ECharts parts on demand; an unregistered series or component
// only shows up at runtime as an "[ECharts] ... is used but not imported" console error.
function collectErrors(page) {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error' || /ECharts/.test(message.text())) errors.push(message.text())
  })
  return errors
}

test('exam results draws the pass donut and score distribution', async ({ page }) => {
  await installTeacherFixtures(page, 'teacher', 'light', true)
  await page.route('**/backend/api/exams/21/results', route => route.fulfill({ json: { code: '200', data: {
    examName: '验收考试', fullScore: 100,
    studentScores: [{ studentId: 1, studentName: '甲', totalScore: 92 }, { studentId: 2, studentName: '乙', totalScore: 55 },
      { studentId: 3, studentName: '丙', totalScore: 74 }],
  } } }))
  const errors = collectErrors(page)
  await page.goto('/exams/21/results')
  await expect(page.locator('.pass-donut canvas')).toHaveCount(1)
  await expect(page.locator('canvas')).toHaveCount(2)
  await page.waitForTimeout(500)
  expect(errors).toEqual([])
})

test('grade analysis draws class charts', async ({ page }) => {
  await installTeacherFixtures(page, 'teacher', 'light', true)
  const errors = collectErrors(page)
  await page.goto('/grade-analysis')
  await page.locator('.class-select').click()
  await page.getByRole('option', { name: /验收教学班/ }).click()
  await expect(page.locator('.overview-table')).toContainText('85')
  await expect(page.locator('canvas').first()).toBeVisible()
  await page.waitForTimeout(500)
  expect(errors).toEqual([])
})

test('student grades draw trend, distribution, subject and comparison charts', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'student')
    localStorage.setItem('username', 'student')
  })
  await page.route('**/backend/**', route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
    const data = path === '/api/submissions' ? [
      { id: 1, examName: '期中考试', courseName: '数据结构', status: 'GRADED', totalScore: 80, answers: [{ maxScore: 100 }] },
      { id: 2, examName: '期末考试', courseName: '操作系统', status: 'GRADED', totalScore: 65, answers: [{ maxScore: 100 }] },
    ] : path === '/api/setup/status' ? { state: 'READY' } : []
    return route.fulfill({ json: { code: '200', data } })
  })
  const errors = collectErrors(page)
  await page.goto('/my-grades')
  await expect(page.getByText('期末考试').first()).toBeVisible()
  await expect(page.locator('canvas').first()).toBeVisible()
  console.log('my-grades canvases:', await page.locator('canvas').count())
  await page.waitForTimeout(500)
  expect(errors).toEqual([])
})
