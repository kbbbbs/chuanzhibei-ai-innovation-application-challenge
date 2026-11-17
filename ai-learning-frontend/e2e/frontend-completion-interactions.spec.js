import { test, expect } from '@playwright/test'

async function fixtures(page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'student')
    localStorage.setItem('username', 'student')
  })
  const requests = []
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/backend/, '')
    requests.push({ path, method: request.method(), body: request.postDataJSON() })
    let data = []
    if (path === '/class-students/student/2') data = [{ classId: 7 }]
    if (path === '/api/homeworks') data = [
      { id: 11, classId: 7, name: '进行中的作业', status: 1, startTime: '2020-01-01', endTime: '2099-01-01' },
      { id: 12, classId: 7, name: '未来作业', status: 1, startTime: '2098-01-01', endTime: '2099-01-01' },
      { id: 13, classId: 7, name: '已完成作业', status: 1 },
      { id: 14, classId: 8, name: '其他班作业', status: 1 },
    ]
    if (path === '/api/homework-submissions') data = [
      { id: 31, homeworkId: 13, status: 'GRADED', totalScore: 88 },
    ]
    if (path === '/api/exams') data = [
      { id: 21, classId: 7, name: '考试样例', status: 1, startTime: '2020-01-01', endTime: '2099-01-01' },
    ]
    if (path === '/users/me') {
      data = request.method() === 'PUT'
        ? { username: 'student', ...request.postDataJSON() }
        : { username: 'student', nickname: '原昵称', email: 'student@example.test' }
    }
    await route.fulfill({ json: { code: '200', data } })
  })
  return requests
}

test('homework mode filters class and completion, gates future work, and preserves result type', async ({ page }) => {
  const requests = await fixtures(page)
  await page.goto('/my-homeworks')
  await expect(page.getByText('进行中的作业')).toBeVisible()
  await expect(page.getByText('其他班作业')).toHaveCount(0)
  await expect(page.getByText('已完成作业')).toBeHidden()
  await expect(page.getByRole('row').filter({ hasText: '未来作业' }).getByRole('button', { name: '未到时间' })).toBeDisabled()
  await page.getByRole('tab', { name: '已完成' }).click()
  await expect(page.getByText('已完成作业')).toBeVisible()
  await page.getByRole('row').filter({ hasText: '已完成作业' }).getByRole('button', { name: '查看' }).click()
  await expect(page).toHaveURL(/\/exam-result\/31\?assessmentType=HOMEWORK$/)
  expect(requests.some(item => item.path === '/api/homeworks')).toBeTruthy()
  expect(requests.some(item => item.path === '/api/homework-submissions')).toBeTruthy()
  expect(requests.some(item => item.path === '/api/exams')).toBeFalsy()
})

test('exam mode uses exam endpoints and opens the exam without homework type', async ({ page }) => {
  const requests = await fixtures(page)
  await page.goto('/my-exams')
  await expect(page.getByText('考试样例')).toBeVisible()
  await page.getByRole('row').filter({ hasText: '考试样例' }).getByRole('button', { name: '开始考试' }).click()
  await expect(page).toHaveURL(/\/exam-taking\/21$/)
  expect(requests.some(item => item.path === '/api/exams')).toBeTruthy()
  expect(requests.some(item => item.path === '/api/submissions')).toBeTruthy()
  expect(requests.some(item => item.path === '/api/homeworks')).toBeFalsy()
})

test('profile validates email, resets edits, and saves the changed profile', async ({ page }) => {
  const requests = await fixtures(page)
  await page.goto('/profile')
  const nickname = page.getByRole('textbox', { name: '昵称' })
  const email = page.getByRole('textbox', { name: '邮箱' })
  await expect(nickname).toHaveValue('原昵称')
  await email.fill('invalid')
  await page.getByRole('button', { name: '保存设置' }).click()
  await expect(page.getByText('请输入有效的邮箱地址')).toBeVisible()
  expect(requests.filter(item => item.path === '/users/me' && item.method === 'PUT')).toHaveLength(0)
  await nickname.fill('临时修改')
  await page.getByRole('button', { name: '撤销修改' }).click()
  await expect(nickname).toHaveValue('原昵称')
  await expect(email).toHaveValue('student@example.test')
  await nickname.fill('更新昵称')
  await page.getByRole('button', { name: '保存设置' }).click()
  await expect.poll(() => requests.filter(item => item.path === '/users/me' && item.method === 'PUT').length).toBe(1)
  expect(requests.find(item => item.path === '/users/me' && item.method === 'PUT').body).toMatchObject({
    nickname: '更新昵称', email: 'student@example.test', currentPassword: null, newPassword: null,
  })
  await expect(page.getByText('请求成功')).toBeVisible()
})
