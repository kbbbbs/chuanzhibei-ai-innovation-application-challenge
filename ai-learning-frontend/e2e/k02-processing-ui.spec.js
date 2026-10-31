import { test, expect } from '@playwright/test'

// Controlled UI contracts only. No OCR/model service or private teaching material is used.
const kbId = '640d254a-e30b-453c-9b0e-d7b94d06111b'
const docId = '7918f4ce-26d6-44f9-9dd8-dbd0b99803a1'
const jobId = 'fd79898d-7a90-4d96-a5c6-a5639e47f829'
const retriedJob = 'dfe4716d-71db-4d73-9604-6be8a76b67b2'
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aSucAAAAASUVORK5CYII=', 'base64')

async function fixture(page, { state = 'OCR', empty = false, textSource = false, pendingReplacement = false, formula = false } = {}) {
  page.on('pageerror', error => console.error('UI runtime error:', error.message))
  await page.addInitScript(() => {
    localStorage.setItem('token', 'k02-ui-contract-token')
    localStorage.setItem('userId', '7')
    localStorage.setItem('roleCode', 'teacher')
    localStorage.setItem('username', 'K02界面测试')
  })
  const calls = []
  let uploaded = !empty
  let activeJob = jobId
  let phase = state
  let cancelRequested = false
  const document = () => ({
    id: docId, documentId: docId, jobId: activeJob, title: textSource ? '位置资料' : '扫描资料',
    file_name: textSource ? 'notes.md' : 'scan.pdf', mime_type: textSource ? 'text/markdown' : 'application/pdf',
    document_version: 1, processing_state: phase, parse_status: phase.toLowerCase(),
    processing_version: pendingReplacement ? 2 : null,
    page_count: textSource ? 0 : 2, chunk_count: phase === 'COMPLETED' ? 1 : 0,
    document_state: phase === 'PARTIAL' ? 'PARTIAL' : phase === 'COMPLETED' ? 'READY' : 'PROCESSING',
    active_index_version: phase === 'PARTIAL' || phase === 'COMPLETED' ? 'active-fixture-index' : null,
  })
  const job = () => ({
    id: activeJob, jobId: activeJob, documentId: docId, documentVersion: pendingReplacement ? 2 : 1, parseRevision: 1,
    stage: phase === 'PARTIAL' ? 'COMPLETED' : phase,
    status: phase === 'PARTIAL' ? 'COMPLETED' : phase,
    documentState: phase === 'PARTIAL' ? 'PARTIAL' : phase === 'COMPLETED' ? 'READY' : 'PROCESSING',
    progress: ['PARTIAL', 'COMPLETED', 'CANCELLED'].includes(phase) ? 100 : 35,
    totalPages: textSource ? 1 : 2, completedPages: phase === 'PARTIAL' ? 1 : phase === 'COMPLETED' ? (textSource ? 1 : 2) : 0,
    failedPages: phase === 'PARTIAL' ? [2] : [], needsReviewPages: phase === 'PARTIAL' ? [2] : [],
    cancelRequested, errorCode: null,
  })
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    // Vite imports /src/api/*.js; those are application modules, not backend API requests.
    if (!path.startsWith('/api/')) return route.continue()
    const isJson = request.headers()['content-type']?.includes('application/json')
    calls.push({ path, method: request.method(), body: isJson && request.postData() ? request.postDataJSON() : null,
      auth: request.headers().authorization, url: request.url() })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/api/rag/knowledge-bases') return ok([{ id: kbId, name: 'K02处理测试库', description: '合成资料', owner_id: 7, read_only: false, shared: false, knowledge_count: uploaded ? 1 : 0 }])
    if (path === `/api/rag/knowledge-bases/${kbId}/documents`) {
      if (request.method() === 'POST') {
        uploaded = true
        phase = 'OCR'
        return ok({ ...document(), processing_state: 'QUEUED', parse_status: 'queued' })
      }
      return ok(uploaded ? [document()] : [])
    }
    if (path === `/api/rag/jobs/${jobId}/cancel`) {
      cancelRequested = true
      phase = 'CANCELLED'
      return ok(job())
    }
    if (path.startsWith('/api/rag/jobs/')) return ok(job())
    if (path === `/api/rag/documents/${docId}/pages` && pendingReplacement) return ok([{
      pageNo: 1, pageNumber: 1, pageStatus: 'READY', rawText: '', ocrText: '', ocrRawText: '',
      chosenText: new URL(request.url()).searchParams.get('version') === '2' ? 'PENDING_VERSION_CONTENT' : 'OLD_VERSION_CONTENT',
      method: 'OCR', confidence: null, needsReview: true, location: '第 1 页',
    }])
    if (path === `/api/rag/documents/${docId}/pages` && formula) return ok([
      { pageNo: 1, pageNumber: 1, pageStatus: 'OCR_READY', method: 'OCR', needsReview: true,
        chosenText: String.raw`损失 $$L=\frac{1}{n}\sum_i x_i^2$$`, formulaVersion: 1, formulaSource: 'OCR',
        formulas: [{ ordinal: 1, start: 3, end: 33, latex: String.raw`L=\frac{1}{n}\sum_i x_i^2`, display: true, status: 'STRUCTURE_OK' }] },
      { pageNo: 2, pageNumber: 2, pageStatus: 'FAILED', method: 'OCR', needsReview: true, chosenText: '',
        errorCode: 'FORMULA_UNCLOSED', formulaVersion: 1, formulaSource: 'OCR',
        formulas: [{ ordinal: 1, start: 0, end: 10, latex: String.raw`\frac{x}{y`, display: true, status: 'INVALID', errorCode: 'FORMULA_UNCLOSED' }] },
    ])
    if (path === `/api/rag/documents/${docId}/pages`) return ok(textSource ? [{
      pageNo: 1, pageNumber: null, pageStatus: 'READY', rawText: '# Heading\nText section.',
      ocrRawText: null, ocrText: null, chosenText: '# Heading\nText section.', method: 'TEXT_LAYER',
      confidence: null, errorCode: null, needsReview: false, location: '字符 0–23',
    }] : [
      { pageNo: 1, pageNumber: 1, pageStatus: 'READY', rawText: '文字层标题', ocrRawText: '<|ref|>识别正文<|/ref|>', ocrText: '识别正文', chosenText: '文字层标题\n识别正文', method: 'MERGED', confidence: null, errorCode: null, needsReview: true, location: '第 1 页' },
      { pageNo: 2, pageNumber: 2, pageStatus: 'FAILED', rawText: '', ocrRawText: null, ocrText: null, chosenText: '', method: 'OCR', confidence: null, errorCode: 'OCR_NOT_CONFIGURED', needsReview: true, location: '第 2 页' },
    ])
    if (path.includes(`/api/rag/documents/${docId}/pages/`) && path.endsWith('/preview')) return route.fulfill({ status: 200, contentType: 'image/png', body: png })
    if (path === `/api/rag/documents/${docId}/parse-retry`) {
      activeJob = retriedJob
      phase = 'QUEUED'
      return ok({ ...document(), ...job() })
    }
    if (path === `/api/rag/documents/${docId}/reindex`) return ok(document())
    if (path.endsWith('/chunks')) return ok([])
    if (path.endsWith('/sessions')) return ok([])
    return ok([])
  })
  await page.goto('/teacher-knowledge-base')
  await expect(page.getByRole('tab', { name: '资料与出处' })).toBeVisible()
  return calls
}

test('queued upload shows background work and cancellation never means parsed successfully', async ({ page }) => {
  const calls = await fixture(page, { empty: true })
  await expect(page.getByText('单文件20 MB，PDF/PPTX最多200页')).toBeVisible()
  await page.locator('input[type=file]').setInputFiles({ name: 'scan.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-UI-fixture-only') })
  await expect(page.getByText('资料已上传，等待后台处理')).toBeVisible()
  const status = page.getByTestId(`processing-status-${docId}`)
  await expect(status).toContainText('正在识别图中文字')
  await expect(status).not.toContainText('解析完成')
  await status.getByRole('button', { name: '取消处理', exact: true }).click()
  await expect(status).toContainText('已取消')
  expect(calls.some(call => call.path === `/api/rag/jobs/${jobId}/cancel` && call.method === 'POST')).toBe(true)
  expect(calls.filter(call => call.path.startsWith('/api/rag/') && call.auth !== 'Bearer k02-ui-contract-token')).toEqual([])
})

test('partial processing preserves missing pages and authenticates page review before forcing OCR', async ({ page }) => {
  const calls = await fixture(page, { state: 'PARTIAL' })
  const status = page.getByTestId(`processing-status-${docId}`)
  await expect(status).toContainText('部分可用')
  await expect(status).toContainText('缺失页：2')
  await page.getByRole('button', { name: '逐页核验', exact: true }).click()
  const review = page.getByTestId('document-page-review')
  await expect(review).toContainText('原页与提取结果')
  await expect(review.getByAltText('原始页面预览')).toBeVisible()
  await expect(review).toContainText('服务未提供置信度')
  await review.getByTestId('review-page-2').click()
  await expect(review).toContainText('该页尚无可用文本')
  await review.getByRole('button', { name: '强制识别本页', exact: true }).click()
  await expect.poll(() => calls.filter(call => call.path.endsWith('/parse-retry')).length).toBe(1)
  expect(calls.find(call => call.path.endsWith('/parse-retry')).body).toEqual({ pages: [2], forceOcr: true })
  const previews = calls.filter(call => call.path.endsWith('/preview'))
  expect(previews.length).toBeGreaterThan(0)
  expect(previews.every(call => call.auth === 'Bearer k02-ui-contract-token' && !call.url.includes('token='))).toBe(true)
})

test('parse retry is separate from vector rebuilding and does not hide a partial state', async ({ page }) => {
  const calls = await fixture(page, { state: 'PARTIAL' })
  const status = page.getByTestId(`processing-status-${docId}`)
  await status.getByRole('button', { name: '重新解析全部', exact: true }).click()
  await expect.poll(() => calls.filter(call => call.path.endsWith('/parse-retry')).length).toBe(1)
  expect(calls.find(call => call.path.endsWith('/parse-retry')).body).toEqual({ pages: [], forceOcr: false })
  expect(calls.some(call => call.path.endsWith('/reindex'))).toBe(false)
  await expect(status).toContainText('排队等待处理')
})

test('text sources display character positions and do not fabricate PDF pages or previews', async ({ page }) => {
  const calls = await fixture(page, { state: 'COMPLETED', textSource: true })
  await page.getByRole('button', { name: '逐页核验', exact: true }).click()
  const review = page.getByTestId('document-page-review')
  await expect(review).toContainText('字符 0–23')
  await expect(review).toContainText('Text section.')
  await expect(review.getByAltText('原始页面预览')).toHaveCount(0)
  await expect(review.getByRole('button', { name: '强制识别本页', exact: true })).toHaveCount(0)
  expect(calls.some(call => call.path.endsWith('/preview'))).toBe(false)
})

test('replacement review reads the pending source version instead of the previously active original', async ({ page }) => {
  const calls = await fixture(page, { state: 'OCR', pendingReplacement: true })
  await page.getByRole('button', { name: '逐页核验', exact: true }).click()
  const review = page.getByTestId('document-page-review')
  await expect(review).toContainText('PENDING_VERSION_CONTENT')
  await expect(review).not.toContainText('OLD_VERSION_CONTENT')
  await expect(review.getByAltText('原始页面预览')).toBeVisible()
  const pageRequests = calls.filter(call => call.path.endsWith('/pages') || call.path.endsWith('/preview'))
  expect(pageRequests.every(call => new URL(call.url).searchParams.get('version') === '2')).toBe(true)
})


test('formula review shows local math rendering, provenance and incomplete source without claiming correctness', async ({ page }) => {
  await fixture(page, { state: 'PARTIAL', formula: true })
  await page.getByRole('button', { name: '逐页核验', exact: true }).click()
  const review = page.getByTestId('document-page-review')
  await expect(review.getByTestId('formula-1').locator('.katex')).toHaveCount(1)
  await expect(review).toContainText('不代表数学内容正确')
  await expect(review).toContainText('图像识别')
  await expect(review.getByTestId('formula-1').locator('pre')).toContainText('\\frac')
  await review.getByTestId('review-page-2').click()
  await expect(review).toContainText('公式结构缺失、不完整或超过处理上限')
  await expect(review.locator('.katex')).toHaveCount(0)
  await expect(review.getByTestId('formula-1').locator('pre')).toContainText('\\frac{x}{y')
})

test('legacy parsing results visibly require a new formula check', async ({ page }) => {
  await fixture(page, { state: 'COMPLETED' })
  await page.getByRole('button', { name: '逐页核验', exact: true }).click()
  await expect(page.getByTestId('formula-review')).toContainText('旧解析结果尚未检查公式')
})
