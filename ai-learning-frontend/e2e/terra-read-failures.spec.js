import { test, expect } from '@playwright/test'

async function setup(page, resource, original) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'teacher')
    localStorage.setItem('username', 'teacher')
  })
  let reads = 0
  const writes = []
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    if (path === resource && request.method() === 'GET') {
      reads++
      return route.fulfill({ json: reads === 1
        ? { code: '500', msg: '读取失败' }
        : { code: '200', data: original } })
    }
    if (path === resource && request.method() === 'PUT') writes.push(request.postDataJSON())
    const data = path === '/categories' ? [{ id: 1, name: '分类' }] : []
    await route.fulfill({ json: { code: '200', data } })
  })
  return { writes, reads: () => reads }
}

test('course edit blocks updates after a failed read until retry loads the original', async ({ page }) => {
  const state = await setup(page, '/courses/1', {
    id: 1, title: '原课程', categoryId: 1, difficulty: 'beginner',
    description: '原简介', coverUrl: '', status: 2,
  })
  await page.goto('/courses/create?id=1')
  await expect.poll(state.reads).toBe(1)
  await expect(page.getByRole('button', { name: '提交' })).toBeDisabled()
  expect(state.writes).toHaveLength(0)
  await expect(page.getByText(/加载失败|未加载/).first()).toBeVisible()
  await page.getByRole('button', { name: /重试|重新加载/ }).click()
  await expect(page.getByRole('textbox', { name: '课程名称' })).toHaveValue('原课程')
  await expect(page.getByRole('button', { name: '提交' })).toBeEnabled()
  expect(state.writes).toHaveLength(0)
})

test('profile blocks updates after a failed read until retry loads the original', async ({ page }) => {
  const state = await setup(page, '/users/me', {
    username: 'teacher', nickname: '原昵称', email: 'teacher@example.test',
    phone: '123456', avatar: '/avatar.png',
  })
  await page.goto('/profile')
  await expect.poll(state.reads).toBe(1)
  await expect(page.getByRole('button', { name: '保存设置' })).toBeDisabled()
  expect(state.writes).toHaveLength(0)
  await expect(page.getByText(/加载失败|未加载/).first()).toBeVisible()
  await page.getByRole('button', { name: /重试|重新加载/ }).click()
  await expect(page.getByRole('textbox', { name: '昵称' })).toHaveValue('原昵称')
  await expect(page.getByRole('textbox', { name: '邮箱' })).toHaveValue('teacher@example.test')
  await expect(page.getByRole('button', { name: '保存设置' })).toBeEnabled()
  expect(state.writes).toHaveLength(0)
})
