import { test, expect } from '@playwright/test'

// Empty fixtures exercise rendering, not backend response contracts.
const paths = ['/', '/home', '/login', '/register', '/missing-route', '/console', '/courses',
  '/courses/create', '/courses/1', '/teaching-classes', '/notices', '/questions', '/papers',
  '/exams', '/homeworks', '/homework-papers', '/homework/1/submissions', '/exams/1/grading',
  '/exams/1/results', '/grade-analysis', '/resource-gen', '/teacher-knowledge-base', '/profile',
  '/operation-logs', '/my-classes', '/learning-routes', '/learning-map/1', '/course-learn/1',
  '/my-homeworks', '/my-exams', '/exam-taking/1', '/exam-result/1', '/my-grades',
  '/recommendation', '/paper-search', '/knowledge-base', '/knowledge-graph', '/work-study-jobs',
  '/my-work-study', '/users', '/teacher-applications', '/categories', '/administrative-classes',
  '/home-course-recommendations', '/work-studies', '/work-study-students']

for (const width of [375, 768, 1080, 1440]) {
  for (const theme of ['light', 'dark']) {
    test(`${width}px ${theme} route matrix`, async ({ page }, testInfo) => {
      test.setTimeout(180000)
      await page.setViewportSize({ width, height: 900 })
      await page.addInitScript(theme => {
        localStorage.setItem('token', 'mock-route-smoke')
        localStorage.setItem('userId', '2')
        localStorage.setItem('roleCode', 'admin')
        localStorage.setItem('username', 'Route fixture')
        localStorage.setItem('eduloop-theme', theme)
      }, theme)
      await page.route('**/backend/**', route => route.fulfill({ json: { code: '200', data: [] } }))
      const issues = []
      let currentErrors = []
      page.on('pageerror', error => currentErrors.push(`pageerror: ${error.message}`))
      page.on('console', message => {
        if (message.type() === 'error') currentErrors.push(`console: ${message.text()}`)
      })
      for (const path of paths) {
        currentErrors = []
        try {
          await page.goto(path, { waitUntil: 'domcontentloaded' })
          if (['/courses/create', '/courses/1', '/learning-map/1', '/course-learn/1'].includes(path)) {
            await expect(page).toHaveURL(/\/courses$/)
          }
          await page.waitForTimeout(250)
          const state = await page.evaluate(() => ({
            rendered: !!document.querySelector('#app')?.getBoundingClientRect().height &&
              !!document.body.innerText.trim() && !document.querySelector('vite-error-overlay'),
            scrollWidth: document.documentElement.scrollWidth,
            clientWidth: document.documentElement.clientWidth,
            dark: document.documentElement.classList.contains('dark'),
          }))
          const errors = [...currentErrors]
          if (!state.rendered || state.scrollWidth > state.clientWidth + 1 ||
              state.dark !== (theme === 'dark') || errors.length) {
            issues.push({ path, ...state, errors })
          }
        } catch (error) {
          issues.push({ path, navigationError: error.message, errors: [...currentErrors] })
        }
      }
      await testInfo.attach('route-matrix.json', {
        body: JSON.stringify({ width, theme, checked: paths.length, issues }, null, 2),
        contentType: 'application/json',
      })
      expect(issues, JSON.stringify(issues, null, 2)).toEqual([])
    })
  }
}
