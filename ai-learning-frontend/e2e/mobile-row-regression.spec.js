import { test, expect } from '@playwright/test'
import { installRouteFixtures } from './helpers/full-route-fixtures'
import { installTeacherFixtures } from './helpers/teacher-route-fixtures'

const routes = [
  { path: '/courses', roles: ['teacher', 'admin'], identity: '浏览器测试课程', actions: ['课程详情', '发布', '删除'], adminActions: ['编辑信息', '调整教学班', '删除'] },
  { path: '/users', roles: ['admin'], identity: 'fixture-user', actions: ['编辑', '删除'] },
  { path: '/categories', roles: ['admin'], identity: '测试分类', actions: ['编辑', '删除'] },
  { path: '/questions', roles: ['teacher', 'admin'], identity: '验收题目内容', actions: ['编辑', '删除'], teacherFixture: true },
  { path: '/papers', roles: ['teacher', 'admin'], identity: '验收试卷名称', actions: ['编辑', '启用', '删除', '拷贝'], teacherFixture: true },
]

async function expectReadable(locator) {
  await locator.scrollIntoViewIfNeeded()
  await expect(locator).toBeVisible()
  await expect.poll(() => locator.evaluate(element => {
    const range = document.createRange()
    range.selectNodeContents(element)
    const rects = [...range.getClientRects()].filter(rect => rect.width && rect.height)
    return rects.length > 0 && rects.every(rect => {
      if (rect.left < 0 || rect.right > innerWidth || rect.top < 0 || rect.bottom > innerHeight) return false
      return [0.1, 0.5, 0.9].every(fraction => {
        const hit = document.elementFromPoint(rect.left + rect.width * fraction, (rect.top + rect.bottom) / 2)
        return Boolean(hit && (element.contains(hit) || hit.contains(element)))
      })
    })
  }), { message: 'identifying text must be unobscured in the viewport' }).toBe(true)
}

async function expectActionReachable(page, row, label) {
  const trigger = row.getByRole('button', { name: /的操作$/ })
  if (await trigger.count()) {
    await trigger.click()
    const sheet = page.locator('.row-action-sheet:visible')
    await expect(sheet).toBeVisible()
    await sheet.getByRole('button', { name: label, exact: true }).click({ trial: true })
    await page.keyboard.press('Escape')
    await expect(sheet).toBeHidden()
    return
  }
  let action = row.getByRole('button', { name: label, exact: true })
  if (!await action.count()) {
    const menu = row.locator('[aria-haspopup="menu"], .el-dropdown').first()
    await expect(menu).toBeVisible()
    await menu.click()
    action = page.getByRole('menuitem', { name: label, exact: true })
  }
  await expect(action).toBeVisible()
  await action.click({ trial: true })
  await page.keyboard.press('Escape')
}

for (const width of [375, 768]) {
  for (const theme of ['light', 'dark']) {
    for (const item of routes) {
      for (const role of item.roles) {
        test(`mobile row ${role} ${item.path} ${width}px ${theme}`, async ({ page }) => {
          await page.setViewportSize({ width, height: 900 })
          const unexpected = item.teacherFixture
            ? await installTeacherFixtures(page, role, theme, true)
            : await installRouteFixtures(page, role, theme)
          if (item.path === '/courses') {
            await page.route(url => url.pathname === '/backend/courses', async route => {
              if (route.request().method() !== 'GET') return route.fallback()
              await route.fulfill({ json: { code: '200', data: {
                records: [{ id: 5, title: '浏览器测试课程', difficulty: '初级', studentCount: 12, viewCount: 20, status: 0, setupStage: 'COMPLETED', teacherId: 2 }],
                total: 1,
              } } })
            })
          }
          if (item.path === '/questions') {
            await page.route('**/backend/api/questions/31', route => route.fulfill({
              json: { code: '200', data: { id: 31, content: '验收题目内容', type: 1, difficulty: 1 } },
            }))
          }
          const errors = []
          page.on('pageerror', error => errors.push(error.message))
          await page.goto(item.path)
          await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
          await expect(page.locator('html')).toHaveClass(theme === 'dark' ? /dark/ : /^(?!.*\bdark\b).*$/)
          const row = page.locator('tr').filter({ hasText: item.identity }).first()
          await expectReadable(row.getByText(item.identity, { exact: true }).first())
          const actions = role === 'admin' ? item.adminActions || item.actions : item.actions
          for (const action of actions) await expectActionReachable(page, row, action)
          if (width === 375 && item.path === '/questions' && role === 'teacher' && theme === 'light') {
            const trigger = row.getByRole('button', { name: /的操作$/ })
            await trigger.click()
            await page.locator('.row-action-sheet:visible').getByRole('button', { name: '编辑', exact: true }).click()
            const dialog = page.getByRole('dialog', { name: '编辑题目' })
            await expect(dialog).toBeVisible()
            await expect(page.locator('.row-action-sheet:visible')).toHaveCount(0)
            await page.keyboard.press('Tab')
            await expect.poll(() => dialog.evaluate(element => element.contains(document.activeElement))).toBe(true)
            await dialog.getByRole('button', { name: '取消' }).click()
            await expect(dialog).toBeHidden()
            await trigger.click({ trial: true })
          }
          expect(unexpected).toEqual([])
          expect(errors).toEqual([])
        })
      }
    }
  }
}

test('course row admin 740px light remains identifiable', async ({ page }) => {
  await page.setViewportSize({ width: 740, height: 900 })
  const unexpected = await installRouteFixtures(page, 'admin', 'light')
  await page.goto('/courses')
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  const row = page.locator('tr').filter({ hasText: '浏览器测试课程' }).first()
  await expectReadable(row.getByText('浏览器测试课程', { exact: true }).first())
  expect(unexpected).toEqual([])
})
