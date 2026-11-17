import { test, expect } from '@playwright/test'

test('grading stays disabled until the assessment loads, then submits a subjective grade', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'teacher')
    localStorage.setItem('username', 'teacher')
  })
  let assessmentReads = 0
  const grades = []
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    let data = []
    if (path === '/api/exams/7') {
      assessmentReads++
      if (assessmentReads === 1) return route.fulfill({ json: { code: '500', msg: '读取失败' } })
      data = { id: 7, name: '测试考试' }
    } else if (path === '/api/submissions') {
      data = [{ id: 11, examId: 7, studentId: 3, status: 'SUBMITTED', objectiveScore: 4, subjectiveScore: null, totalScore: null }]
    } else if (path === '/api/submissions/11/grading') {
      data = { examId: 7, answers: [{ id: 21, questionId: 31, questionType: 'SHORT_ANSWER', questionContent: '解释概念', answerContent: '学生回答', maxScore: 10 }] }
    } else if (path === '/api/submissions/11/grade-subjective' && request.method() === 'POST') {
      grades.push(request.postDataJSON())
      data = {}
    }
    await route.fulfill({ json: { code: '200', data } })
  })

  await page.goto('/exams/7/grading')
  const gradeButton = page.getByRole('button', { name: '批改', exact: true })
  await expect(gradeButton).toBeDisabled()
  expect(grades).toHaveLength(0)
  await page.getByRole('alert').filter({ hasText: '考试信息未加载' }).getByRole('button', { name: '重新加载' }).click()
  await expect(gradeButton).toBeEnabled()
  await gradeButton.click()
  const dialog = page.getByRole('dialog', { name: '批改主观题' })
  await expect(dialog.getByText('解释概念')).toBeVisible()
  await dialog.getByRole('spinbutton').fill('8')
  await dialog.getByPlaceholder('选填').fill('批注')
  await dialog.getByRole('button', { name: '提交批改' }).click()
  await expect.poll(() => grades.length).toBe(1)
  expect(grades[0]).toEqual({ graderId: 2, grades: [{ submissionAnswerId: 21, score: 8, comment: '批注' }] })
})
