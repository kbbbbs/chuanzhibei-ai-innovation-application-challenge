import { test, expect } from '@playwright/test'
import { installRouteFixtures } from './helpers/full-route-fixtures'

const dashboardRoles = [
  { role: 'student', title: '学生学习控制台', content: '浏览器测试课程' },
  { role: 'teacher', title: '教师教学控制台', content: '考试样例' },
  { role: 'admin', title: '平台管理控制台', content: '待审测试教师' },
]

for (const width of [375, 768, 1080, 1440]) {
  for (const theme of ['light', 'dark']) {
    for (const item of dashboardRoles) {
      test(`completion console ${item.role} ${width}px ${theme}`, async ({ page }, testInfo) => {
        await page.setViewportSize({ width, height: 900 })
        const unexpected = await installRouteFixtures(page, item.role, theme, { dashboard: true })
        const errors = []
        page.on('pageerror', error => errors.push(error.message))
        await page.goto('/console')
        await expect(page.getByRole('heading', { name: item.title, exact: true })).toBeVisible()
        await expect(page.getByText(item.content, { exact: true }).first()).toBeVisible()
        await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
        await expect(page.locator('html')).toHaveClass(theme === 'dark' ? /dark/ : /^(?!.*\bdark\b).*$/)
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBeTruthy()
        expect(unexpected).toEqual([])
        expect(errors).toEqual([])
        if (width === 1440 && theme === 'light') {
          await page.screenshot({ path: testInfo.outputPath('console.png'), fullPage: true })
        }
      })
    }
  }
}

for (const { role } of dashboardRoles) {
  test(`completion agent redirect ${role}`, async ({ page }) => {
    const unexpected = await installRouteFixtures(page, role, 'light', { dashboard: true })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.goto('/agent-chat?source=completion')
    await expect(page.getByRole('region', { name: 'Agent 侧边助手' })).toBeVisible()
    expect(new URL(page.url()).pathname).toBe('/console')
    expect(new URL(page.url()).searchParams.get('source')).toBe('completion')
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
    expect(unexpected).toEqual([])
    expect(errors).toEqual([])
  })
}

const additionalRoutes = [
  { path: '/', roles: [null], title: '让每一次教与学， 都回到下一次改进。', content: '浏览首页' },
  { path: '/home', roles: [null], title: '让每一次学习， 都通向新的发现', content: '精选课程，为下一段学习打开方向' },
  { path: '/login', roles: [null], title: '登录 EduLoop', content: '密码' },
  { path: '/register', roles: [null], title: '创建账号', content: '密码' },
  { path: '/setup', roles: [null], title: 'EduLoop 已完成安装', content: '前往登录' },
  { path: '/setup/recovery', roles: [null], title: '当前无需恢复', content: '前往登录' },
  { path: '/not-a-real-page', roles: [null], title: '页面走丢了', content: '返回首页' },
  { path: '/settings/models', roles: ['admin'], title: '模型设置', content: '配置版本' },
  { path: '/teacher-applications', roles: ['admin'], title: '教师入驻审核', content: '待审核' },
  { path: '/administrative-classes', roles: ['admin'], title: '行政班管理', content: '新增行政班' },
  { path: '/home-course-recommendations', roles: ['admin'], title: '首页推荐课程', content: '暂无启用的手动推荐' },
  { path: '/work-studies', roles: ['teacher', 'admin'], title: '勤工俭学岗位管理', content: '新增岗位' },
  { path: '/work-study-students', roles: ['teacher', 'admin'], title: '劳务费名单管理', content: '导入名单' },
]

for (const width of [375, 768, 1080, 1440]) {
  for (const theme of ['light', 'dark']) {
    for (const item of additionalRoutes) {
      for (const role of item.roles) {
        test(`additional ${role || 'anonymous'} ${item.path} at ${width}px ${theme}`, async ({ page }, testInfo) => {
          await page.setViewportSize({ width, height: 900 })
          const unexpected = await installRouteFixtures(page, role, theme)
          const errors = []
          page.on('pageerror', error => errors.push(error.message))
          page.on('console', message => {
            if (message.type() === 'error') errors.push(message.text())
          })
          await page.goto(item.path)
          const heading = page.getByRole('heading', { name: item.title, exact: true })
          await expect(heading).toBeVisible()
          await expect(page.getByText(item.content, { exact: true }).first()).toBeVisible()
          await heading.scrollIntoViewIfNeeded()
          expect(await heading.evaluate(element => {
            const rect = element.getBoundingClientRect()
            const hit = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2)
            return !!hit && (element.contains(hit) || hit.contains(element))
          }), 'heading center is covered').toBeTruthy()
          await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
          await expect(page.locator('html')).toHaveClass(theme === 'dark' ? /dark/ : /^(?!.*\bdark\b).*$/)
          await page.screenshot({ path: testInfo.outputPath('page.png'), fullPage: true })
          await page.keyboard.press('Tab')
          expect(await page.evaluate(() => document.activeElement !== document.body)).toBeTruthy()
          expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBeTruthy()
          expect(unexpected).toEqual([])
          expect(errors).toEqual([])
        })
      }
    }
  }
}

const routes = [
  { path: '/my-grades', roles: ['student', 'admin'], title: '我的成绩', content: '暂无成绩记录' },
  { path: '/my-homeworks', roles: ['student', 'admin'], title: '我的作业', content: '进行中的作业' },
  { path: '/my-exams', roles: ['student', 'admin'], title: '我的考试', content: '考试样例' },
  { path: '/courses', roles: ['teacher', 'admin'], title: '课程管理', content: '浏览器测试课程' },
  { path: '/courses/create', roles: ['teacher', 'admin'], title: '新建课程', content: '课程名称' },
  { path: '/users', roles: ['admin'], title: '用户管理', content: 'fixture-user' },
  { path: '/profile', roles: ['student', 'teacher', 'admin'], title: '个人设置', content: '测试用户' },
  { path: '/categories', roles: ['admin'], title: '分类管理', content: '测试分类' },
  { path: '/my-classes', roles: ['student', 'admin'], title: '我的班级', content: '浏览器测试课程' },
]

for (const width of [375, 768, 1080, 1440]) {
  for (const theme of ['light', 'dark']) {
  for (const item of routes) {
   for (const role of item.roles) {
    test(`${role} ${item.path} renders content at ${width}px ${theme}`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height: 900 })
      const unexpected = await installRouteFixtures(page, role, theme)
      const errors = []
      page.on('pageerror', error => errors.push(error.message))
      page.on('console', message => {
        if (message.type() === 'error') errors.push(message.text())
      })
      await page.goto(item.path)
      const adminCreationRedirect = role === 'admin' && item.path === '/courses/create'
      if (adminCreationRedirect) await expect(page).toHaveURL(/\/courses$/)
      await expect(page.getByRole('heading', { name: adminCreationRedirect ? '课程管理' : item.title, exact: true })).toBeVisible()
      await expect(page.locator('main').getByText(adminCreationRedirect ? '浏览器测试课程' : item.content, { exact: true }).first()).toBeVisible()
      await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
      await expect(page.locator('html')).toHaveClass(theme === 'dark' ? /dark/ : /^(?!.*\bdark\b).*$/)
      await page.screenshot({ path: testInfo.outputPath('page.png'), fullPage: true })
      await page.keyboard.press('Tab')
      expect(await page.evaluate(() => document.activeElement !== document.body)).toBeTruthy()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBeTruthy()
      expect(unexpected).toEqual([])
      expect(errors).toEqual([])
    })
   }
  }
  }
}

for (const [role, path] of [['student', '/courses'], ['teacher', '/users'], ['student', '/settings/models']]) {
  test(`${role} cannot access ${path}`, async ({ page }) => {
    await installRouteFixtures(page, role)
    await page.goto(path)
    await expect(page).toHaveURL(/\/console$/)
  })
}

test('anonymous protected route retains login redirect', async ({ page }) => {
  await installRouteFixtures(page, null)
  await page.goto('/my-grades')
  await expect.poll(() => new URL(page.url()).searchParams.get('redirect')).toBe('/my-grades')
  await expect(page).toHaveURL(/\/login\?/)
})

for (const path of ['/courses', '/users', '/categories']) {
  test(`mobile table diagnostic ${path}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width: 375, height: 900 })
    await installRouteFixtures(page, 'admin', 'light')
    await page.goto(path)
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
    const table = page.locator('.el-table').first()
    await expect(table).toBeVisible()
    const measure = () => table.evaluate(element => {
      const scroller = element.querySelector('.el-scrollbar__wrap')
      const rect = node => {
        const r = node.getBoundingClientRect()
        return { left: Math.round(r.left), right: Math.round(r.right), width: Math.round(r.width) }
      }
      return {
        scrollLeft: scroller?.scrollLeft,
        scrollWidth: scroller?.scrollWidth,
        clientWidth: scroller?.clientWidth,
        cells: [...element.querySelectorAll('tbody tr:first-child td')].map(cell => {
          const r = cell.getBoundingClientRect()
          const x = Math.max(0, Math.min(innerWidth - 1, (r.left + r.right) / 2))
          const y = Math.max(0, Math.min(innerHeight - 1, (r.top + r.bottom) / 2))
          return { text: cell.innerText.trim(), ...rect(cell), topElement: document.elementFromPoint(x, y)?.closest('td')?.innerText.trim() }
        }),
      }
    })
    const initial = await measure()
    await page.screenshot({ path: testInfo.outputPath('initial.png') })
    await table.locator('.el-scrollbar__wrap').first().evaluate(node => { node.scrollLeft = node.scrollWidth })
    await page.waitForTimeout(200)
    const end = await measure()
    await page.screenshot({ path: testInfo.outputPath('end.png') })
    console.log(JSON.stringify({ path, initial, end }))
  })
}

test('mobile course name remains readable beside fixed actions', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 375, height: 900 })
  await installRouteFixtures(page, 'admin', 'light')
  await page.goto('/courses')
  const name = page.getByText('浏览器测试课程', { exact: true }).first()
  await expect(name).toBeVisible()
  await name.scrollIntoViewIfNeeded()
  await page.screenshot({ path: testInfo.outputPath('course-name-obscured.png') })
  const unobscured = await name.evaluate(element => {
    const r = element.getBoundingClientRect()
    const top = document.elementFromPoint((r.left + r.right) / 2, (r.top + r.bottom) / 2)
    return top === element || element.contains(top)
  })
  expect(unobscured, 'course name is covered by the fixed actions column').toBe(true)
})
