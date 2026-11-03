import { test, expect } from '@playwright/test'

async function setup(page, firstResponse) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'paper-search-test-token')
    localStorage.setItem('userId', '7')
    localStorage.setItem('roleCode', 'student')
    localStorage.setItem('username', '测试用户')
  })
  let attempts = 0
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.continue()
    if (url.pathname !== '/api/paper/search') {
      return route.fulfill({ json: { code: '200', data: [] } })
    }
    attempts++
    if (attempts === 1 && firstResponse === 'network') return route.abort('failed')
    if (attempts === 1 && firstResponse === 'timeout') {
      return route.fulfill({ json: { code: '504', msg: 'arXiv 请求超时' } })
    }
    const data = firstResponse === 'empty'
      ? { total: 0, data: [] }
      : { total: 1, data: [{ paperId: '1234.5678', title: 'Recovery Paper', authors: [{ name: 'Test Author' }], source: 'arxiv' }] }
    return route.fulfill({ json: { code: '200', data } })
  })
  await page.goto('/paper-search')
  await page.getByPlaceholder('输入论文标题或关键词').fill('machine learning')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
}

for (const failure of ['timeout', 'network']) {
  test(`${failure} remains visible and retry restores results`, async ({ page }) => {
    await setup(page, failure)
    await expect(page.getByText('论文检索失败', { exact: false })).toBeVisible()
    await expect(page.getByRole('button', { name: '重新搜索' })).toBeVisible()
    await expect(page.getByText('未找到结果', { exact: false })).toHaveCount(0)
    if (failure === 'timeout') {
      await page.screenshot({ path: test.info().outputPath('error-state.png') })
    }
    await page.getByRole('button', { name: '重新搜索' }).click()
    await expect(page.getByText('Recovery Paper', { exact: true })).toBeVisible()
    await expect(page.getByText('论文检索失败', { exact: false })).toHaveCount(0)
    if (failure === 'timeout') {
      await page.screenshot({ path: test.info().outputPath('recovered-state.png') })
    }
  })
}

test('reset clears a search failure', async ({ page }) => {
  await setup(page, 'timeout')
  await expect(page.getByText('论文检索失败', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await expect(page.getByText('论文检索失败', { exact: false })).toHaveCount(0)
  await expect(page.getByPlaceholder('输入论文标题或关键词')).toHaveValue('')
})

test('a successful empty response shows the empty state', async ({ page }) => {
  await setup(page, 'empty')
  await expect(page.getByText('未找到结果', { exact: false })).toBeVisible()
  await expect(page.getByText('论文检索失败', { exact: false })).toHaveCount(0)
})
