import { test, expect } from '@playwright/test'

const visualId = '11111111-1111-4111-8111-111111111111'
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1ZkAAAAASUVORK5CYII=', 'base64')

async function fixture(page, role, denied = false) {
  await page.addInitScript(role => {
    localStorage.setItem('token', 'figure-fixture')
    localStorage.setItem('userId', '8')
    localStorage.setItem('roleCode', role)
  }, role)
  const source = { chunkId: 'chunk', documentId: 'doc', title: '神经网络.pdf', page: 2, documentVersion: 1,
    parseRevision: 1, indexVersion: 'index-1', citation: '[1]', content: '网络结构由输入层和输出层组成。',
    visuals: [{ id: visualId, kind: 'FIGURE', origin: 'OCR_GROUNDING', caption: '图1 网络结构', page: 2,
      documentId: 'doc', documentVersion: 1, parseRevision: 1, imageUrl: 'https://untrusted.invalid/image.png' },
      { id: '22222222-2222-4222-8222-222222222222', kind: 'PAGE', origin: 'PAGE_FALLBACK' }] }
  const requests = []
  await page.route('**/backend/**', route => {
    const url = new URL(route.request().url()), path = url.pathname.replace(/^\/backend/, '')
    requests.push(url)
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/courses') return ok({ records: [{ id: 41, title: '人工智能基础', teacherId: 8 }], total: 1 })
    if (path === '/api/assistant/sessions') return ok([{ id: 91, title: '网络结构', courseId: 41, scope: 'COURSE' }])
    if (path.endsWith('/messages')) return ok([{ id: 2, role: 'assistant', content: '网络结构见资料[1]。',
      sections: [{ type: 'COURSE_MATERIAL', content: '网络结构见资料[1]。' }], status: 'COMPLETED',
      citations: [source, { ...source, chunkId: 'chunk-2', citation: '[2]' }] }])
    if (path.includes('/visuals/')) return denied
      ? route.fulfill({ status: 403, json: { code: '403', msg: '资料授权已撤销' } })
      : route.fulfill({ contentType: 'image/png', body: png })
    if (path.includes('/chunks/')) return ok(source)
    if (path.endsWith('/preview')) return route.fulfill({ contentType: 'image/png', body: png })
    return ok([])
  })
  await page.goto('/knowledge-base?courseId=41&sessionId=91')
  return requests
}

for (const role of ['student', 'teacher']) {
  test(`${role} answer shows unique source figures and retains full page preview`, async ({ page }) => {
    const requests = await fixture(page, role)
    const figures = page.getByTestId('answer-figures')
    await expect(figures.getByRole('img', { name: '图1 网络结构', exact: true })).toBeVisible()
    await expect(figures.locator('figure')).toHaveCount(1)
    expect(requests.filter(url => url.pathname.includes('/visuals/'))).toHaveLength(1)
    expect(requests.find(url => url.pathname.includes('/visuals/')).searchParams.get('courseId')).toBe(role === 'student' ? '41' : null)
    await figures.getByRole('button', { name: '放大插图' }).click()
    await expect(page.locator('.el-image-viewer__wrapper')).toBeVisible()
    await page.keyboard.press('Escape')
    await figures.getByRole('button', { name: '查看原页' }).click()
    await expect(page.locator('.el-drawer .original-page img')).toBeVisible()
    await page.reload()
    await expect(page.getByTestId('answer-figures').getByRole('img', { name: '图1 网络结构', exact: true })).toBeVisible()
  })
}

test('revoked figure reports access failure without displaying source pixels', async ({ page }) => {
  await fixture(page, 'student', true)
  const figures = page.getByTestId('answer-figures')
  await expect(figures.getByText('资料授权已撤销')).toBeVisible()
  await expect(figures.locator('img')).toHaveCount(0)
})
