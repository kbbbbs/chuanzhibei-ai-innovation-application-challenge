import { test, expect } from '@playwright/test'
import { installStudentFixtures } from './helpers/full-route-student-fixtures'

async function tabTo(page, target) {
  for (let i = 0; i < 100; i++) {
    if (await target.evaluate(element => element === document.activeElement)) return
    await page.keyboard.press('Tab')
  }
  await expect(target).toBeFocused()
}

const routes = [
  { path: '/learning-routes', roles: ['student', 'admin'], title: '学习路线', content: '浏览器测试课程' },
  { path: '/learning-map/5', roles: ['student', 'admin'], title: '浏览器测试课程', content: '测试课时' },
  { path: '/course-learn/5', roles: ['student', 'admin'], content: '请选择课时开始学习' },
  { path: '/exam-taking/21', roles: ['student', 'admin'], title: '考试样例', content: '暂无试题' },
  { path: '/exam-result/31', roles: ['student', 'admin'], title: '考试结果', content: '考试样例' },
  { path: '/recommendation', roles: ['student', 'admin'], title: '智能推荐', content: '暂无推荐结果' },
  { path: '/paper-search', roles: ['student', 'teacher', 'admin'], title: '论文查询', content: '请输入关键词搜索论文' },
  { path: '/knowledge-base', roles: ['student', 'teacher', 'admin'], title: '智能学伴', content: '暂无可用知识库' },
  { path: '/knowledge-graph', roles: ['student', 'teacher', 'admin'], title: '知识图谱', content: '请选择一个知识库查看图谱' },
  { path: '/work-study-jobs', roles: ['student', 'admin'], title: '勤工俭学', content: '暂无招聘中的岗位' },
  { path: '/my-work-study', roles: ['student', 'admin'], title: '我的岗位', content: '暂无申请记录' },
]

for (const width of [375, 768, 1080, 1440]) {
  for (const theme of ['light', 'dark']) {
    for (const item of routes) {
      for (const role of item.roles) {
        test(`${role} ${item.path} at ${width}px ${theme}`, async ({ page }, testInfo) => {
          await page.setViewportSize({ width, height: 900 })
          const unexpected = await installStudentFixtures(page, role, theme)
          const errors = []
          page.on('pageerror', error => errors.push(error.message))
          page.on('console', message => {
            if (message.type() === 'error') errors.push(message.text())
          })
          try {
            await page.goto(item.path)
            const adminCourseRedirect = role === 'admin' && ['/learning-map/5', '/course-learn/5'].includes(item.path)
            if (adminCourseRedirect) {
              await expect(page).toHaveURL(/\/courses$/)
              await expect(page.getByRole('heading', { name: '课程管理', exact: true })).toBeVisible()
              await expect(page.getByRole('button', { name: '新建课程', exact: true })).toHaveCount(0)
            } else {
              if (item.title) await expect(page.getByText(item.title, { exact: true }).first()).toBeVisible()
              await expect(page.getByText(item.content, { exact: true }).first()).toBeVisible()
            }
            await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
            await expect(page.locator('html')).toHaveClass(theme === 'dark' ? /dark/ : /^(?!.*\bdark\b).*$/)
            await page.keyboard.press('Tab')
            expect(await page.evaluate(() => document.activeElement !== document.body)).toBeTruthy()
            expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBeTruthy()
            expect(unexpected).toEqual([])
            expect(errors).toEqual([])
          } finally {
            await page.screenshot({ path: testInfo.outputPath('page.png'), fullPage: true })
          }
        })
      }
    }
  }
}

for (const width of [375, 1440]) {
  for (const theme of ['light', 'dark']) {
    for (const scenario of ['course', 'exam', 'knowledge', 'jobs']) {
      test(`populated ${scenario} at ${width}px ${theme}`, async ({ page }, testInfo) => {
        await page.setViewportSize({ width, height: 900 })
        const unexpected = await installStudentFixtures(page, 'student', theme, true)
        const errors = []
        page.on('pageerror', error => errors.push(error.message))
        page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
        try {
          if (scenario === 'course') {
            await page.goto('/course-learn/5')
            await expect(page.getByText('第一章', { exact: true })).toBeVisible()
            const lesson = page.getByRole('button', { name: '测试课时' })
            await expect(lesson).toBeVisible()
            await tabTo(page, lesson)
            await page.keyboard.press('Enter')
            await expect(lesson).toHaveAttribute('aria-current', 'true')
          } else if (scenario === 'exam') {
            await page.goto('/exam-taking/21')
            await expect(page.getByText('测试题目')).toBeVisible()
            await expect(page.getByRole('button', { name: '第 1 题，未答' })).toBeVisible()
            const option = page.getByRole('radio', { name: /选项一/ })
            await tabTo(page, option)
            await page.keyboard.press('Space')
            await expect(page.getByText('已答 1 / 1')).toBeVisible()
          } else if (scenario === 'knowledge') {
            await page.goto('/knowledge-base')
            await expect(page.getByText('测试知识库', { exact: true })).toBeVisible()
            await expect(page.getByText('测试文档', { exact: true })).toBeVisible()
            const tab = page.getByRole('tab', { name: '知识问答' })
            await tabTo(page, page.getByRole('tab', { name: '资料与出处' }))
            await page.keyboard.press('ArrowRight')
            await expect(tab).toHaveAttribute('aria-selected', 'true')
          } else {
            await page.goto('/work-study-jobs')
            await expect(page.getByText('测试岗位', { exact: true })).toBeVisible()
            await expect(page.getByRole('button', { name: '申请岗位' })).toBeVisible()
            const search = page.getByPlaceholder('请输入岗位名称')
            await tabTo(page, search)
            await page.keyboard.type('测试岗位')
            await page.keyboard.press('Enter')
            await expect(search).toHaveValue('测试岗位')
            await expect(page.getByText('测试岗位', { exact: true })).toBeVisible()
          }
          await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
          expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBeTruthy()
          expect(unexpected).toEqual([])
          expect(errors).toEqual([])
        } finally {
          await page.screenshot({ path: testInfo.outputPath('page.png'), fullPage: true })
        }
      })
    }
  }
}
