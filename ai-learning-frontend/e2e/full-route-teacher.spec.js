import { test, expect } from '@playwright/test'
import { installTeacherFixtures } from './helpers/teacher-route-fixtures'

const routes = [
  ['/teaching-classes', '教学班管理'],
  ['/notices', '通知管理'],
  ['/questions', '题库管理'],
  ['/papers', '试卷管理'],
  ['/homework-papers', '作业题卷'],
  ['/exams', '考试管理'],
  ['/homeworks', '作业管理'],
  ['/homework/11/submissions', '作业批改：测试作业'],
  ['/exams/21/grading', '考试批改：测试考试'],
  ['/exams/21/results', '考试详情：测试考试'],
  ['/grade-analysis', '成绩分析'],
  ['/resource-gen', '智能备课工作台'],
  ['/teacher-knowledge-base', '知识库管理'],
  ['/courses/5', '浏览器测试课程'],
  ['/operation-logs', null],
]

for (const width of [375, 768, 1080, 1440]) {
  for (const theme of ['light', 'dark']) {
    for (const [path, title] of routes) {
      for (const role of ['teacher', 'admin']) {
        test(`${role} ${path} ${width}px ${theme}`, async ({ page }, testInfo) => {
          await page.setViewportSize({ width, height: 900 })
          const unexpected = await installTeacherFixtures(page, role, theme)
          const errors = []
          page.on('pageerror', error => errors.push(error.message))
          page.on('console', message => {
            if (message.type() === 'error') errors.push(message.text())
          })
          try {
            await page.goto(path)
            const adminCourseRedirect = role === 'admin' && path === '/courses/5'
            if (adminCourseRedirect) await expect(page).toHaveURL(/\/courses$/)
            await expect(page.getByRole('heading', {
              name: adminCourseRedirect ? '课程管理' : title || (role === 'teacher' ? '学生操作日志' : '操作日志'),
              exact: true,
            })).toBeVisible()
            await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
            await expect(page.locator('html')).toHaveClass(theme === 'dark' ? /dark/ : /^(?!.*\bdark\b).*$/)
            await page.keyboard.press('Tab')
            expect(await page.evaluate(() => document.activeElement !== document.body)).toBeTruthy()
            expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBeTruthy()
            expect(unexpected).toEqual([])
            expect(errors).toEqual([])
          } finally {
            await page.screenshot({ path: testInfo.outputPath('page.png'), fullPage: true }).catch(() => {})
          }
        })
      }
    }
  }
}

for (const role of ['teacher', 'admin']) {
  for (const width of [375, 1440]) {
    for (const [path, text, action] of [
      ['/questions', '验收题目内容', '新建题目'],
      ['/papers', '验收试卷名称', '创建试卷'],
      ['/exams/21/grading', '验收学生', null],
      ['/courses/5', '验收章节', null],
      ['/grade-analysis', '班级成绩矩阵', null],
    ]) {
      test(`populated ${role} ${path} ${width}px`, async ({ page }, testInfo) => {
        await page.setViewportSize({ width, height: 900 })
        const unexpected = await installTeacherFixtures(page, role, 'light', true)
        try {
          await page.goto(path)
          const adminCourseRedirect = role === 'admin' && path === '/courses/5'
          if (adminCourseRedirect) {
            await expect(page).toHaveURL(/\/courses$/)
            await expect(page.getByRole('heading', { name: '课程管理', exact: true })).toBeVisible()
            await expect(page.getByText('验收章节', { exact: true })).toHaveCount(0)
          }
          if (path === '/grade-analysis') {
            await page.locator('.class-select').click()
            await page.getByRole('option', { name: /验收教学班/ }).click()
            await expect(page.locator('.overview-table')).toContainText('验收学生')
            await expect(page.locator('.overview-table')).toContainText('85')
          }
          await expect(page.getByText(adminCourseRedirect ? '浏览器测试课程' : text, { exact: true }).first()).toBeVisible()
          if (path === '/courses/5' && !adminCourseRedirect) {
            await page.getByText('验收章节', { exact: true }).click()
            await expect(page.getByText('验收课时', { exact: true })).toBeVisible()
          }
          if (action) {
            await page.getByRole('button', { name: action, exact: true }).click()
            await expect(page.getByRole('dialog')).toBeVisible()
            await page.keyboard.press('Escape')
            await expect(page.getByRole('dialog')).toBeHidden()
          }
          if (path === '/exams/21/grading') {
            await page.getByRole('button', { name: '批改', exact: true }).click()
            await expect(page.getByRole('dialog', { name: '批改主观题' })).toBeVisible()
            await page.getByRole('dialog').getByRole('button', { name: '取消' }).click()
            await expect(page.getByRole('dialog')).toBeHidden()
          }
          if (width === 375 && ['/questions', '/papers', '/exams/21/grading'].includes(path)) {
            const target = page.getByText(text, { exact: true }).first()
            await target.scrollIntoViewIfNeeded()
            const unobscured = await target.evaluate(el => {
              const rect = el.getBoundingClientRect()
              const x = Math.max(0, rect.left) + Math.min(rect.width, innerWidth - Math.max(0, rect.left)) / 2
              const top = document.elementFromPoint(x, rect.top + rect.height / 2)
              return Boolean(top && (el.contains(top) || top.contains(el)))
            })
            expect(unobscured, `${text} 被固定列或其他元素遮挡`).toBeTruthy()
          }
          expect(unexpected).toEqual([])
        } finally {
          await page.screenshot({ path: testInfo.outputPath('page.png'), fullPage: true }).catch(() => {})
        }
      })
    }
  }
}
