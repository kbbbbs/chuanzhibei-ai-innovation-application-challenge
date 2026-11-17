import { test, expect } from '@playwright/test'

test('one click creates a course report and exposes saved data and exports', async ({ page }) => {
  let created = false
  await page.addInitScript(() => {
    localStorage.setItem('token', 'controlled-learning-report-test')
    localStorage.setItem('userId', '1003901')
    localStorage.setItem('roleCode', 'teacher')
    localStorage.setItem('username', '演示教师')
  })
  const report = {
    id: 'report-fixture', courseId: 1004911, courseTitle: '机器学习演示课程', templateId: 'default',
    targetWords: 1000, wordCount: 1002, status: 'SUCCESS', content: '课程与教学班概况\n本课程包含两个教学班。\n\n教学改进建议\n跟进待批改作业。',
    createdAt: '2026-11-17 19:00:00', model: 'controlled-fixture',
    snapshot: { classes: [], assessments: [], studentCount: 16, capturedAt: '2026-11-17T19:00:00', exam: {}, homework: {} },
  }
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace(/^\/backend/, '')
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/courses') return ok({ records: [{ id: 1004911, title: report.courseTitle }], total: 1 })
    if (path === '/api/learning-analysis/templates') return ok([{ id: 'default', name: '默认模板', fields: ['course_overview'] }])
    if (path === '/api/learning-analysis/reports') {
      if (route.request().method() === 'POST') {
        expect(route.request().postDataJSON()).toMatchObject({ courseId: 1004911, targetWords: 1000, templateId: 'default' })
        created = true
        return ok({ ...report, status: 'QUEUED', content: null })
      }
      return ok(created ? [report] : [])
    }
    if (path === '/api/learning-analysis/reports/report-fixture') return ok(report)
    return route.continue()
  })
  await page.goto('/grade-analysis')
  await expect(page.getByRole('heading', { name: '智能学情分析', exact: true })).toBeVisible()
  await page.getByRole('combobox', { name: '课程', exact: true }).click()
  await page.getByRole('option', { name: '机器学习演示课程', exact: true }).click()
  await page.getByRole('button', { name: '一键生成学情报告' }).click()
  await expect(page.getByText('本课程包含两个教学班。', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: '导出 Word', exact: true })).toBeEnabled()
  await expect(page.getByRole('button', { name: '导出 CSV', exact: true })).toBeEnabled()
  await page.getByText('查看分析数据', { exact: true }).click()
  await expect(page.getByText('数据截止时间', { exact: false })).toBeVisible()
})
