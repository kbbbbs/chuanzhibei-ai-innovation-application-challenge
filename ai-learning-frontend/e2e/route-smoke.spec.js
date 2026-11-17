import { test, expect } from '@playwright/test'

// These empty responses exercise route loading only, not backend contracts.
const groups = {
  public: ['/', '/home', '/login', '/register', '/missing-route'],
  teacher: ['/console', '/courses', '/courses/create', '/courses/1', '/teaching-classes',
    '/notices', '/questions', '/papers', '/exams', '/homeworks', '/homework-papers',
    '/homework/1/submissions', '/exams/1/grading', '/exams/1/results', '/grade-analysis',
    '/resource-gen', '/teacher-knowledge-base', '/profile', '/operation-logs'],
  student: ['/my-classes', '/learning-routes', '/learning-map/1', '/course-learn/1',
    '/my-homeworks', '/my-exams', '/exam-taking/1', '/exam-result/1', '/my-grades',
    '/recommendation', '/paper-search', '/knowledge-base', '/knowledge-graph',
    '/work-study-jobs', '/my-work-study'],
  admin: ['/users', '/teacher-applications', '/categories', '/administrative-classes',
    '/home-course-recommendations', '/work-studies', '/work-study-students'],
}

for (const [group, paths] of Object.entries(groups)) {
  test(`mocked ${group} deep links render without a Vite overlay`, async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('token', 'mock-route-smoke')
      localStorage.setItem('userId', '2')
      localStorage.setItem('roleCode', 'admin')
      localStorage.setItem('username', 'Route fixture')
    })
    await page.route('**/backend/**', route => route.fulfill({
      json: { code: '200', data: [] },
    }))
    for (const path of paths) {
      await test.step(path, async () => {
        await page.goto(path)
        const expectedPath = ['/courses/create', '/courses/1', '/learning-map/1', '/course-learn/1'].includes(path) ? '/courses' : path
        await expect(page).toHaveURL(new RegExp(`${expectedPath.replaceAll('/', '\\/').replace('?', '\\?')}$`))
        await expect(page.locator('#app')).toBeVisible()
        await expect(page.locator('vite-error-overlay')).toHaveCount(0)
        await expect(page.locator('body')).not.toBeEmpty()
      })
    }
  })
}

test('mocked agent redirect preserves the query', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'mock-route-smoke')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'admin')
  })
  await page.route('**/backend/**', route => route.fulfill({ json: { code: '200', data: [] } }))
  await page.goto('/agent-chat?source=smoke')
  await expect(page).toHaveURL(/\/console\?source=smoke$/)
  await expect(page.getByRole('region', { name: 'Agent 侧边助手' })).toBeVisible()
  await expect(page.locator('vite-error-overlay')).toHaveCount(0)
})
