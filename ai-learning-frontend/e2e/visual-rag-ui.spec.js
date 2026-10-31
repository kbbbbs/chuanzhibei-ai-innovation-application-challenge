import { test, expect } from '@playwright/test'

// Synthetic contracts only: these do not establish OCR/diagram accuracy.
const kb = '50c983d6-7a9c-4768-ae1e-bcd5c63c09f2'
const doc = '273181c4-cd64-473a-a146-c3469e268809'
const chunk = '5e171ced-74f8-4d77-8b72-91c9b74e321e'
const figure = '1d7bc210-38f7-4cec-aa01-a430301bb781'
const fallback = 'be74883c-c191-48f2-a75d-9bd1e4deff19'
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aSucAAAAASUVORK5CYII=', 'base64')
const picture = { id: figure, documentId: doc, documentVersion: 1, parseRevision: 2, page: 1, ordinal: 1,
  kind: 'FIGURE', origin: 'OCR_GROUNDING', caption: '图1 网络结构 <img src=x onerror=alert(1)>',
  context: '1. 根据网络结构说明输入与输出。', questionNumber: '1', needsReview: true,
  imageUrl: 'https://untrusted.invalid/not-used.png', width: 320, height: 200, bbox: [100, 200, 700, 800] }
const fullPage = { ...picture, id: fallback, page: 2, kind: 'PAGE', origin: 'PAGE_FALLBACK',
  caption: '', context: '2. 请参考原页作答。', questionNumber: '2', bbox: [] }

async function fixture(page, { legacy = false, invalidId = false, questions = false } = {}) {
  const state = { denyImage: false, denyChunk: false, stripChunkVisuals: false, holdFigure: false, releaseFigure: null, calls: [] }
  await page.addInitScript(() => {
    localStorage.setItem('token', 'visual-ui-synthetic-token')
    localStorage.setItem('userId', '7'); localStorage.setItem('roleCode', 'teacher')
    localStorage.setItem('username', '图文核验测试')
    window.__visualRevoked = []
    window.__visualCreated = []
    const original = URL.revokeObjectURL.bind(URL)
    URL.revokeObjectURL = url => { window.__visualRevoked.push(url); original(url) }
    const create = URL.createObjectURL.bind(URL)
    URL.createObjectURL = blob => { const url = create(blob); window.__visualCreated.push(url); return url }
  })
  const diagram = Buffer.from(await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 600; canvas.height = 210
    const context = canvas.getContext('2d')
    context.fillStyle = '#f1f5f9'; context.fillRect(0, 0, 600, 210)
    context.fillStyle = '#1e293b'; context.font = 'bold 22px sans-serif'; context.fillText('Synthetic network example', 30, 40)
    for (const [x, label] of [[30, 'Input x'], [350, 'Output y']]) {
      context.fillStyle = '#dbeafe'; context.fillRect(x, 85, 200, 70)
      context.strokeStyle = '#2563eb'; context.lineWidth = 2; context.strokeRect(x, 85, 200, 70)
      context.fillStyle = '#1e3a8a'; context.font = '24px sans-serif'; context.fillText(label, x + 40, 128)
    }
    context.strokeStyle = '#1e293b'; context.beginPath(); context.moveTo(230, 120); context.lineTo(340, 120)
    context.moveTo(330, 110); context.lineTo(340, 120); context.lineTo(330, 130); context.stroke()
    context.font = '20px sans-serif'; context.fillText('W, b', 260, 95)
    return canvas.toDataURL('image/png').split(',')[1]
  }), 'base64')
  const visuals = legacy ? [] : [{ ...picture, id: invalidId ? '../bad' : figure }]
  const hit = { chunkId: chunk, documentId: doc, kbId: kb, title: '图文课程资料',
    content: String.raw`1. 网络结构与公式 \[y=Wx+b\]`, page: 1, ordinal: 1, score: 1, citation: '[1]', visuals }
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname
    if (!path.startsWith('/api/')) return route.continue()
    state.calls.push({ path, url: request.url(), auth: request.headers().authorization })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/api/rag/knowledge-bases') return ok([{ id: kb, name: '图文课程资料', owner_id: 7, read_only: false, knowledge_count: 1 }])
    if (path.endsWith('/documents')) return ok([{ id: doc, title: '图文课程资料', file_name: 'lesson.pdf', page_count: 2,
      chunk_count: 2, document_version: 1, parse_revision: 2, processing_state: 'READY', parse_status: 'success', active_index_version: 'active' }])
    if (path === `/api/rag/documents/${doc}/pages`) return ok([
      { pageNo: 1, pageNumber: 1, pageStatus: 'OCR_READY', chosenText: hit.content, method: 'OCR', needsReview: true,
        formulaVersion: 1, formulaSource: 'OCR', formulas: [{ ordinal: 1, latex: 'y=Wx+b', display: true, status: 'STRUCTURE_OK' }],
        visualVersion: legacy ? 0 : 1, visuals, visualIssues: [], questions: questions ? [
          { number: '1', text: String.raw`1. 根据网络结构计算输出。
A. \(y=Wx+b\)
B. \(y=x\)
<script>alert(1)</script>
![untrusted](https://untrusted.invalid/question.png)`, visualIds: [figure], needsReview: true },
          { number: '2', text: '2. 说明理由。', visualIds: [], needsReview: true },
        ] : [] },
      { pageNo: 2, pageNumber: 2, pageStatus: 'OCR_READY', chosenText: '2. 请参考原页作答。', method: questions ? 'VISUAL_REFERENCE' : 'OCR',
        formulaVersion: 1, formulas: [], visualVersion: 1, visuals: [fullPage], visualIssues: ['VISUAL_LAYOUT_UNAVAILABLE'] },
    ])
    if (path.endsWith('/preview')) return route.fulfill({ contentType: 'image/png', body: png })
    if (path.startsWith('/api/rag/visuals/')) {
      if (state.holdFigure && path.includes(figure)) await new Promise(resolve => { state.releaseFigure = resolve })
      return (state.denyImage
        ? route.fulfill({ status: 403, json: { code: '403', msg: '图片访问权限已改变' } })
        : route.fulfill({ contentType: 'image/png', body: diagram })).catch(() => {})
    }
    if (path === '/api/rag/search') return ok({ hits: [hit], mode: 'bm25' })
    if (path === `/api/rag/chunks/${chunk}`) return state.denyChunk
      ? route.fulfill({ status: 403, json: { code: '403', msg: '资料访问权限已改变' } }) : ok(state.stripChunkVisuals ? { ...hit, visuals: undefined } : hit)
    return ok([])
  })
  await page.goto('/teacher-knowledge-base')
  await expect(page.getByRole('tab', { name: '资料与出处' })).toBeVisible()
  return state
}

test('page review associates private figures with original question text beside formulas and releases switched images', async ({ page }) => {
  const state = await fixture(page)
  const external = []
  page.on('request', request => { if (request.url().includes('untrusted.invalid')) external.push(request.url()) })
  await page.getByRole('button', { name: '逐页核验', exact: true }).click()
  const review = page.getByTestId('document-page-review')
  await expect(review.locator('.katex')).toHaveCount(1)
  const images = review.getByTestId('visual-evidence')
  await expect(images).toContainText('题号 1')
  await expect(images).toContainText('根据网络结构说明输入与输出')
  await expect(images.getByAltText('资料插图 1')).toBeVisible()
  const oldUrl = await images.getByAltText('资料插图 1').getAttribute('src')
  expect(oldUrl).toMatch(/^blob:/)
  await expect(images).toContainText('<img src=x onerror=alert(1)>')
  await expect(images.locator('img')).toHaveCount(1)
  await images.screenshot({ path: '../.codex_tmp/k02-visuals-ui/page-visual-review.png' })
  await review.getByTestId('review-page-2').click()
  await expect(review.getByTestId('visual-evidence')).toContainText('原页参考图，未定位插图')
  await expect(review.getByAltText('原页参考图 1')).toBeVisible()
  await expect.poll(() => page.evaluate(url => window.__visualRevoked.includes(url), oldUrl)).toBe(true)
  expect(state.calls.filter(call => call.path.startsWith('/api/rag/visuals/')).every(call => call.auth === 'Bearer visual-ui-synthetic-token' && !call.url.includes('token='))).toBe(true)
  expect(external).toEqual([])
})

test('search citations show authenticated figure and discard its object URL on drawer close or lost access', async ({ page }) => {
  const state = await fixture(page)
  await page.getByRole('tab', { name: '检索与验证' }).click()
  await page.getByPlaceholder('输入关键词或问题，查看实际命中的原文').fill('网络结构')
  await page.getByRole('button', { name: '检索', exact: true }).click()
  await expect(page.locator('.search-hit')).toContainText('含图')
  const source = page.getByRole('button', { name: /\[1\] 图文课程资料/ })
  await source.click()
  const image = page.locator('.el-drawer').getByAltText('资料插图 1')
  await expect(image).toBeVisible()
  await page.locator('.el-drawer.open').screenshot({ path: '../.codex_tmp/k02-visuals-ui/citation-visual-review.png' })
  const first = await image.getAttribute('src')
  await page.keyboard.press('Escape')
  await expect(image).not.toBeVisible()
  await expect.poll(() => page.evaluate(url => window.__visualRevoked.includes(url), first)).toBe(true)
  state.denyChunk = true
  await source.click()
  await expect(page.locator('.el-drawer.open')).toContainText('资料访问权限已改变')
  await expect(page.locator('.el-drawer.open').getByAltText('资料插图 1')).toHaveCount(0)
})

test('failed image authorization clears previous content and explains unavailable figure', async ({ page }) => {
  const state = await fixture(page)
  await page.getByRole('button', { name: '逐页核验', exact: true }).click()
  const review = page.getByTestId('document-page-review')
  await expect(review.getByAltText('资料插图 1')).toBeVisible()
  state.denyImage = true
  await review.getByTestId('review-page-2').click()
  await expect(review.getByTestId('visual-evidence')).toContainText('图片访问权限已改变')
  await expect(review.getByTestId('visual-evidence').locator('img')).toHaveCount(0)
})

test('old parsing results require image reparse instead of claiming no image exists', async ({ page }) => {
  await fixture(page, { legacy: true })
  await page.getByRole('button', { name: '逐页核验', exact: true }).click()
  await expect(page.getByTestId('document-page-review')).toContainText('旧解析结果尚未整理插图，请重新解析本页')
})

test('unsafe visual identifiers never become network paths', async ({ page }) => {
  const state = await fixture(page, { invalidId: true })
  await page.getByRole('button', { name: '逐页核验', exact: true }).click()
  await expect(page.getByTestId('visual-evidence')).toContainText('图片标识无效')
  expect(state.calls.filter(call => call.path.includes('/visuals/'))).toEqual([])
})

test('a delayed previous-page image cannot reappear after selecting another page', async ({ page }) => {
  const state = await fixture(page)
  state.holdFigure = true
  await page.getByRole('button', { name: '逐页核验', exact: true }).click()
  await expect.poll(() => typeof state.releaseFigure).toBe('function')
  const review = page.getByTestId('document-page-review')
  await review.getByTestId('review-page-2').click()
  await expect(review.getByAltText('原页参考图 1')).toBeVisible()
  const created = await page.evaluate(() => window.__visualCreated.length)
  state.releaseFigure()
  await expect(review.getByAltText('资料插图 1')).toHaveCount(0)
  await expect(review.getByTestId('visual-evidence')).toContainText('题号 2')
  expect(await page.evaluate(() => window.__visualCreated.length)).toBe(created)
})

test('fresh source metadata replaces the cached image list even when it has no visual field', async ({ page }) => {
  const state = await fixture(page)
  state.stripChunkVisuals = true
  await page.getByRole('tab', { name: '检索与验证' }).click()
  await page.getByPlaceholder('输入关键词或问题，查看实际命中的原文').fill('网络结构')
  await page.getByRole('button', { name: '检索', exact: true }).click()
  await page.getByRole('button', { name: /\[1\] 图文课程资料/ }).click()
  await expect(page.locator('.el-drawer.open .source-formula')).toBeVisible()
  await expect(page.locator('.el-drawer.open').getByTestId('visual-evidence')).toHaveCount(0)
  expect(state.calls.filter(call => call.path.startsWith('/api/rag/visuals/'))).toEqual([])
})

test('question review preserves original options and formulas while showing association counts and pending verification', async ({ page }) => {
  await fixture(page, { questions: true })
  const external = []
  page.on('request', request => { if (request.url().includes('untrusted.invalid')) external.push(request.url()) })
  await page.getByRole('button', { name: '逐页核验', exact: true }).click()
  const review = page.getByTestId('document-page-review'), questions = review.getByTestId('question-review')
  await expect(questions).toContainText('按题号整理（待核验）')
  await questions.getByText('按题号整理（待核验）', { exact: true }).click()
  await expect(questions).toContainText('题号 1')
  await expect(questions).toContainText('关联插图 1 张')
  await expect(questions).toContainText('关联插图 0 张')
  await expect(questions.locator('.katex')).toHaveCount(2)
  await expect(questions).toContainText('A.')
  await expect(questions).toContainText('<script>alert(1)</script>')
  await expect(questions.locator('script')).toHaveCount(0)
  await expect(questions.locator('img')).toHaveCount(0)
  expect(external).toEqual([])
  await expect(questions).not.toContainText(figure)
  await expect(questions.locator('article').nth(1).locator('ol')).toHaveAttribute('start', '2')
  await questions.screenshot({ path: '../.codex_tmp/k02-visuals-ui/questions-review.png' })
  await review.getByTestId('review-page-2').click()
  await expect(review).toContainText('原页图像参考')
  await expect(review.getByTestId('question-review')).toHaveCount(0)
})
