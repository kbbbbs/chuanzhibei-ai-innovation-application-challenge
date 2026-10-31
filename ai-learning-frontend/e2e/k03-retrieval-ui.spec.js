import { test, expect } from '@playwright/test'

// Controlled API fixtures validate display contracts; no live backend/model quality claim.
const kb = 'c93a48f0-383b-4db2-a79a-b6277f9e4b41'
const doc = 'ddfcf410-09d1-4877-9537-69e86cf71476'
const chunk = '91e286ca-bab4-41a6-b34c-55d5ec4d05e6'
const index = 'aa141543-6d60-4ee3-abcb-585adbf144d6'
const hit = { chunkId: chunk, documentId: doc, kbId: kb, title: 'K03算法讲义', content: '二分查找逐步将查找区间减半。',
  page: 3, ordinal: 2, score: 0.0164, citation: '[1]', indexVersion: index, documentVersion: 2, parseRevision: 3 }

async function fixture(page, { response = {}, chunkResponse = hit, secondFailure = false, answer = {}, searchDelay = 0 } = {}) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'k03-ui-contract-token')
    localStorage.setItem('userId', '7'); localStorage.setItem('roleCode', 'teacher'); localStorage.setItem('username', '检索核验')
  })
  let searches = 0
  await page.route('**/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/api/rag/knowledge-bases') return ok([{ id: kb, name: 'K03算法讲义', description: '检索核验', owner_id: 7, knowledge_count: 1 }])
    if (path.endsWith('/documents')) return ok([{ id: doc, title: hit.title, file_name: '讲义.pdf', parse_status: 'success', page_count: 4, chunk_count: 1 }])
    if (path === '/api/rag/search') {
      searches++
      if (searchDelay) await new Promise(resolve => setTimeout(resolve, searchDelay))
      if (secondFailure && searches > 1) return route.fulfill({ status: 503, json: { code: '503', msg: '本次检索失败' } })
      return ok({ hits: [hit], mode: 'hybrid', warning: '', actualMode: 'HYBRID', degradedReason: null, indexVersions: [
        { documentId: doc, indexVersion: index, documentVersion: 2, parseRevision: 3, embeddingModel: 'test-fixture', dimensions: 4096, normalized: true, chunkRevision: 'fixture' }
      ], ...response }).catch(problem => { if (!searchDelay) throw problem })
    }
    if (path === `/api/rag/chunks/${chunk}`) return ok(chunkResponse)
    if (path === '/api/rag/chat/stream') {
      const result = { answer: '每次减半。[1]', citations: [hit], mode: 'bm25', actualMode: 'HYBRID', degradedReason: null, status: 'ANSWERED', sessionId: 's1', ...answer }
      return route.fulfill({ contentType: 'text/event-stream', body: `event: complete\ndata: ${JSON.stringify(result)}\n\n` })
    }
    return ok([])
  })
  await page.goto('/teacher-knowledge-base')
  await expect(page.getByRole('tab', { name: '检索与验证' })).toBeVisible()
}
async function search(page) {
  await page.getByRole('tab', { name: '检索与验证' }).click()
  await page.getByPlaceholder('输入关键词或问题，查看实际命中的原文').fill('怎样缩小查找范围')
  await page.getByRole('button', { name: '检索', exact: true }).click()
}

test('explicit actual mode wins over legacy mode and gives a clear keyword degradation reason', async ({ page }) => {
  await fixture(page, { response: { actualMode: 'KEYWORD', degradedReason: 'EMBEDDING_UNAVAILABLE', mode: 'hybrid' } })
  await search(page)
  await expect(page.getByTestId('retrieval-mode')).toContainText('关键词检索')
  await expect(page.getByTestId('retrieval-mode')).not.toContainText('联合')
  await expect(page.getByText('语义服务暂不可用，本次按关键词检索。', { exact: true })).toBeVisible()
  await expect(page.locator('.search-hit')).not.toContainText('%')
  await page.screenshot({ path: '../.codex_tmp/k03-ui/keyword-degradation-review.png', fullPage: true })
})

test('hybrid retrieval retains source version and an inspectable index snapshot', async ({ page }) => {
  await fixture(page, { response: { mode: 'bm25', actualMode: 'HYBRID' } })
  await search(page)
  await expect(page.getByTestId('retrieval-mode')).toContainText('关键词与语义联合检索')
  await expect(page.locator('.search-hit')).toContainText('资料版本 2 · 解析修订 3')
  await page.getByRole('button', { name: '[1] K03算法讲义 · 第 3 页', exact: true }).click()
  await expect(page.getByTestId('source-version')).toContainText('资料版本 2 · 解析修订 3')
  await page.getByText('索引快照', { exact: true }).click()
  await expect(page.getByTestId('source-index-version')).toHaveText(index)
  await page.screenshot({ path: '../.codex_tmp/k03-ui/hybrid-source-review.png', fullPage: true })
})

test('no index or empty retrieval never claims hybrid work happened', async ({ page }) => {
  await fixture(page, { response: { hits: [], actualMode: 'NONE', mode: 'hybrid', degradedReason: 'EMPTY_INDEX', indexVersions: [] } })
  await search(page)
  await expect(page.getByTestId('retrieval-mode')).toHaveText('本次未执行检索')
  await expect(page.getByText('暂无可用索引，请先完成资料解析。', { exact: true })).toBeVisible()
  await expect(page.getByText('关键词与语义联合检索', { exact: false })).toHaveCount(0)
})

test('a failed new search clears the previous actual mode and its source snapshot', async ({ page }) => {
  await fixture(page, { secondFailure: true })
  await search(page)
  await expect(page.getByTestId('retrieval-mode')).toContainText('联合检索')
  await page.getByRole('button', { name: '检索', exact: true }).click()
  await expect(page.getByText('本次检索失败', { exact: true })).toBeVisible()
  await expect(page.getByTestId('retrieval-mode')).toHaveCount(0)
  await expect(page.locator('.search-hit')).toHaveCount(0)
})

test('completed answers show only actual returned retrieval mode', async ({ page }) => {
  await fixture(page)
  await page.getByRole('tab', { name: '知识问答' }).click()
  await page.getByPlaceholder('例如：这节课的核心概念是什么？').fill('二分查找如何工作')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.chat-message.assistant')).toContainText('本次检索：关键词与语义联合检索')
  await expect(page.locator('.chat-message.assistant')).not.toContainText('本次检索：关键词检索')
})

test('a citation cannot relabel a newer index as the original retrieved source', async ({ page }) => {
  await fixture(page, { chunkResponse: { ...hit, indexVersion: '34e99c55-d5bc-47a4-beb4-5d7b9958bd5b', content: 'NEWER_SOURCE' } })
  await search(page)
  await page.getByRole('button', { name: '[1] K03算法讲义 · 第 3 页', exact: true }).click()
  await expect(page.getByText('引用对应的索引版本已改变，请重新检索。', { exact: true })).toBeVisible()
  await expect(page.getByText('NEWER_SOURCE', { exact: true })).toHaveCount(0)
})

test('partial semantic coverage stays hybrid while explaining the keyword contribution', async ({ page }) => {
  await fixture(page, { response: { actualMode: 'HYBRID', degradedReason: 'PARTIAL_VECTOR_COVERAGE' } })
  await search(page)
  await expect(page.getByTestId('retrieval-mode')).toContainText('关键词与语义联合检索')
  await expect(page.getByText('部分资料暂无可用语义向量，已结合关键词查找。', { exact: true })).toBeVisible()
})

test('a fresh legacy source without version fields does not inherit cached version claims', async ({ page }) => {
  const { indexVersion, documentVersion, parseRevision, ...legacy } = hit
  await fixture(page, { chunkResponse: legacy })
  await search(page)
  await page.getByRole('button', { name: '[1] K03算法讲义 · 第 3 页', exact: true }).click()
  await expect(page.locator('.el-drawer.open .source-text')).toHaveText(hit.content)
  await expect(page.getByTestId('source-version')).toHaveCount(0)
  await expect(page.getByTestId('source-index-version')).toHaveCount(0)
})

test('stopping a pending search is shown as incomplete instead of a completed empty result', async ({ page }) => {
  await fixture(page, { searchDelay: 1500 })
  await search(page)
  await page.getByRole('button', { name: '停止检索', exact: true }).click()
  await expect(page.getByText('已停止检索，本次未完成。', { exact: true })).toBeVisible()
  await expect(page.getByText('未检索到相关资料，请调整关键词或上传资料', { exact: true })).toHaveCount(0)
  await expect(page.getByTestId('retrieval-mode')).toHaveCount(0)
  await expect(page.locator('.search-hit')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '检索', exact: true })).toBeEnabled()
  await page.screenshot({ path: '../.codex_tmp/k03-ui-finish/cancel-incomplete-green.png', fullPage: true })
  await page.getByRole('button', { name: '检索', exact: true }).click()
  await expect(page.getByText('已停止检索，本次未完成。', { exact: true })).toHaveCount(0)
  await expect(page.locator('.search-hit')).toContainText(hit.content)
  await expect(page.getByTestId('retrieval-mode')).toContainText('关键词与语义联合检索')
})
