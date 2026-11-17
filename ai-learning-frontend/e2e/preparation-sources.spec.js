import { test, expect } from '@playwright/test'

// Synthetic responses only: partially usable materials must not block a preparation the teacher already started.
const ok = data => ({ code: '200', data })
const config = { title: '人工智能导论', categoryId: 1, audience: '本科生', chapterCount: 1, lessonsPerChapter: 1 }
const project = sourceFiles => ({
  id: 1, currentStage: 'CONFIG', projectStatus: 'DRAFT', config, knowledgeBaseIds: [], ownedKnowledgeBaseId: 'kb-owned',
  outline: { chapters: [] }, lessons: [], questions: [], sourceFiles,
  sourcesReady: !sourceFiles.some(file => file.availability === 'PROCESSING') && sourceFiles.some(file => ['USABLE', 'PARTIAL'].includes(file.availability)),
})
const usable = { name: '教材.pdf', knowledgeId: 'doc-1', availability: 'USABLE', parseProgress: 100 }
const partial = { name: '课件.pdf', knowledgeId: 'doc-2', availability: 'PARTIAL', missingPages: [3], parseProgress: 100 }
const processing = { ...partial, availability: 'PROCESSING', missingPages: [], parseProgress: 40 }

test('outline generation starts by itself once uploaded materials stop processing, using partially usable ones', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'teacher')
  })
  let reads = 0, generated = 0
  await page.route('**/backend/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
    const method = route.request().method()
    if (path === '/categories') return route.fulfill({ json: ok([{ id: 1, name: '人工智能' }]) })
    if (path === '/api/preparations' && method === 'GET') return route.fulfill({ json: ok([{ id: 1, title: '人工智能导论', currentStage: 'CONFIG' }]) })
    if (path === '/api/preparations/1' && method === 'GET') {
      reads++
      return route.fulfill({ json: ok(project(reads <= 2 ? [usable, processing] : [usable, partial])) })
    }
    if (path === '/api/preparations/1/config') return route.fulfill({ json: ok(project([usable, processing])) })
    if (path === '/api/preparations/1/generate-outline' || path === '/api/preparations/1/generate-all') {
      generated++
      return route.fulfill({ json: ok({ ...project([usable, partial]), currentStage: 'OUTLINE_REVIEW', generationStatus: 'RUNNING' }) })
    }
    return route.fulfill({ json: ok([]) })
  })
  await page.goto('/resource-gen')
  await page.getByRole('combobox', { name: '继续最近的备课草稿' }).click()
  await page.getByRole('option', { name: /人工智能导论/ }).click()
  const sources = page.getByTestId('source-status-list')
  await expect(sources).toContainText('处理中 40%')
  await page.getByRole('button', { name: /保存并生成/ }).click()
  await expect(sources).toContainText('有可用内容后会自动开始生成')
  expect(generated).toBe(0)
  await expect.poll(() => generated, { timeout: 15000 }).toBe(1)
})

test('material status names usable, partial and failed files and links to the knowledge base', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'teacher')
  })
  const failed = { name: '扫描件.pdf', knowledgeId: 'doc-3', availability: 'FAILED', errorMessage: '识别服务未配置' }
  await page.route('**/backend/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
    if (path === '/api/preparations') return route.fulfill({ json: ok([{ id: 1, title: '人工智能导论', currentStage: 'CONFIG' }]) })
    if (path === '/api/preparations/1') return route.fulfill({ json: ok(project([usable, partial, failed])) })
    return route.fulfill({ json: ok([]) })
  })
  await page.goto('/resource-gen')
  await page.getByRole('combobox', { name: '继续最近的备课草稿' }).click()
  await page.getByRole('option', { name: /人工智能导论/ }).click()
  const sources = page.getByTestId('source-status-list')
  await expect(sources).toContainText('可用')
  await expect(sources).toContainText('部分可用 · 第 3 页未取得文字')
  await expect(sources).toContainText('未取得文字，生成时不使用')
  await expect(sources).toContainText('识别服务未配置')
  await sources.getByRole('button', { name: '到知识库补处理' }).click()
  await expect(page).toHaveURL(/teacher-knowledge-base\?kbId=kb-owned/)
})
