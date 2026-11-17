import { test, expect } from '@playwright/test'

async function openPreparation(page, { status = 'RUNNING', progress = 0, mode = 'CONTINUOUS' } = {}) {
  const calls = []
  let project = {
    id: 67,
    projectStatus: 'DRAFT',
    currentStage: 'CONFIG',
    generationStatus: status,
    progress,
    errorMessage: status === 'FAILED' ? '智能备课模型暂不可用或输出格式异常，请检查模型配置后重试' : null,
    config: {
      targetCourseId: 41, title: '人工智能基础', categoryId: 5, audience: '本科生',
      chapterCount: 8, lessonsPerChapter: 4, includeQuestions: true, generationMode: mode,
      questionConfig: { countPerChapter: 5 },
    },
    outline: null, lessons: [], questions: [], sourceFiles: [], sourcesReady: true,
  }
  await page.addInitScript(() => {
    localStorage.setItem('token', 'controlled-skeleton-progress-token')
    localStorage.setItem('roleCode', 'teacher')
    localStorage.setItem('userId', '7')
  })
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/backend/, '')
    calls.push({ path, method: request.method(), query: Object.fromEntries(url.searchParams) })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    const course = { id: 41, teacherId: 7, title: '人工智能基础', categoryId: 5, status: 2, setupStage: 'CONTENT', contentMode: 'AI', preparationId: 67, workflowVersion: 1 }
    if (path === '/courses/41' || path === '/courses/41/setup') return ok(course)
    if (path === '/categories') return ok([{ id: 5, name: '人工智能' }])
    if (path === '/api/preparations') return ok([project])
    if (path === '/api/preparations/67') return ok(project)
    if (['/api/preparations/67/generate-all', '/api/preparations/67/generate-outline'].includes(path)) {
      project = { ...project, generationStatus: 'RUNNING', errorMessage: null, progress: 0 }
      return ok(project)
    }
    if (path === '/api/rag/courses/41/knowledge-base') return ok({ id: 'controlled-skeleton-kb', owner_id: 7 })
    return ok([])
  })
  await page.goto('/courses/41')
  return { calls, update: data => { project = { ...project, ...data } } }
}

for (const progress of [0, 5]) {
  test(`outline waiting uses an indeterminate state for stored progress ${progress}`, async ({ page }) => {
    const state = await openPreparation(page, { progress })
    const banner = page.locator('.generation-banner')
    await expect(banner).toContainText('等待模型返回课程骨架')
    await expect(banner.getByRole('progressbar')).toHaveCount(0)
    await expect(banner.locator('[aria-busy="true"]')).toBeVisible()

    state.update({ currentStage: 'OUTLINE_REVIEW', progress: 20 })
    await expect(banner).toContainText('正在生成课时 Markdown')
    await expect(banner.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '20')
  })
}

for (const mode of ['CONTINUOUS', 'STEP']) {
  test(`failed empty outline offers one manual retry for ${mode} on the same project`, async ({ page }) => {
    const { calls } = await openPreparation(page, { status: 'FAILED', mode })
    const label = mode === 'CONTINUOUS' ? '重试生成完整草稿' : '重试课程骨架'
    const button = page.getByRole('button', { name: label, exact: true })
    await expect(button).toBeVisible()
    expect(calls.filter(call => call.method !== 'GET')).toEqual([])
    await button.click()
    await expect(page.locator('.generation-banner')).toContainText('等待模型返回课程骨架')
    const writes = calls.filter(call => call.method !== 'GET')
    expect(writes).toEqual([{
      path: `/api/preparations/67/${mode === 'CONTINUOUS' ? 'generate-all' : 'generate-outline'}`,
      method: 'POST', query: mode === 'CONTINUOUS' ? { restart: 'false' } : {},
    }])
    await expect(button).toHaveCount(0)
  })
}
