import { test, expect } from '@playwright/test'

// Screenshots use synthetic data and do not exercise a real backend.
const pages = [
  ['setup', '/setup', '安装码'],
  ['models', '/settings/models', '内容生成'],
  ['lesson', '/course-learn/1?classId=1&lessonId=1', '课程正文核验'],
  ['teacher-knowledge', '/teacher-knowledge-base', null],
  ['login', '/login', null],
]

test('capture mocked representative pages in both themes', async ({ browser }) => {
  test.setTimeout(180000)
  for (const theme of ['light', 'dark']) {
    for (const width of [375, 768, 1080, 1440]) {
      for (const [name, route, marker] of pages) {
      if (process.env.VISUAL_PAGE && process.env.VISUAL_PAGE !== name) continue
      const context = await browser.newContext({ viewport: { width, height: 960 } })
      await context.addInitScript(({ theme, name }) => {
        localStorage.setItem('eduloop-theme', theme)
        if (name !== 'login' && name !== 'setup') {
          localStorage.setItem('token', 'mock-visual-token')
          localStorage.setItem('userId', '2')
          localStorage.setItem('roleCode', 'admin')
          localStorage.setItem('username', 'Mock user')
        }
      }, { theme, name })
      const page = await context.newPage()
      await page.route('**/backend/**', async route => {
        const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '').replace(/^\/api/, '')
        let data = []
        const role = { enabled: true, baseUrl: 'https://example.com/v1', model: 'mock-model',
          timeoutSeconds: 30, secretConfigured: true, configuration: 'CONFIGURED_UNVERIFIED' }
        if (path === '/setup/session' && route.request().method() === 'GET') {
          return route.fulfill({ status: 401, json: { code: '401', msg: 'unauthorized', data: {} } })
        }
        if (path === '/setup/status') data = name === 'setup'
          ? { state: 'UNINITIALIZED', setupAllowed: true }
          : { state: 'READY', setupAllowed: false }
        if (path === '/admin/settings/models') data = { editableRevision: 'mock-editable', activeRevision: 'mock-active',
          lifecycleSupported: true, roles: { generation: role, planning: role, embedding: role, ocr: role } }
        if (path === '/courses/1') data = { id: 1, title: '算法学习课程' }
        if (path === '/courses/1/chapters') data = [{ id: 1, title: '第一章' }]
        if (path === '/chapters/1/lessons') data = [{ id: 1, title: '算法基础', resourceType: 'text', content: '课程正文核验' }]
        if (path === '/courses/1/experience') data = { textbook: [] }
        if (path === '/rag/knowledge-bases') data = [{ id: 'kb', name: 'Mock 课程知识库' }]
        if (path === '/rag/sessions') data = { id: 'mock-session' }
        if (path === '/lessons/1/note') data = { title: '算法基础', contentMd: 'Mock 笔记' }
        await route.fulfill({ json: { code: '200', data } })
      })
        await page.goto(route)
        await expect(page).toHaveURL(new RegExp(route.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$'))
        await expect(page.locator('#app')).toBeVisible()
        if (name === 'setup') await expect(page.getByLabel('安装码', { exact: true })).toBeVisible()
        else if (marker) await expect(page.getByText(marker, { exact: false }).first()).toBeVisible()
        if (name === 'lesson') {
          await expect(page.locator('.course-learn .el-loading-mask:visible')).toHaveCount(0)
          await expect(page.locator('.note-editor [contenteditable="true"]')).toHaveText('Mock 笔记')
        }
        await expect(page.locator('html')).toHaveClass(theme === 'dark' ? /dark/ : /^(?!.*dark).*$/)
        await expect(page.locator('vite-error-overlay')).toHaveCount(0)
        await page.screenshot({ path: test.info().outputPath(`mocked-${name}-${theme}-${width}.png`), fullPage: true })
        const dimensions = await page.evaluate(() => ({ viewport: document.documentElement.clientWidth,
          content: document.documentElement.scrollWidth }))
        console.log(JSON.stringify({ name, theme, width, ...dimensions }))
      await context.close()
      }
    }
  }
})
