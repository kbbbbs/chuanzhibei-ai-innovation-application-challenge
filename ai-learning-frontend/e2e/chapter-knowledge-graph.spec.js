import { test, expect } from '@playwright/test'

// The graph uses the shipped chapter data. Only login and unrelated shell APIs are controlled.
async function openGraph(page, role = 'teacher') {
  await page.addInitScript(role => {
    localStorage.setItem('token', 'chapter-graph-ui-test')
    localStorage.setItem('userId', '7')
    localStorage.setItem('roleCode', role)
    localStorage.setItem('nickname', '课程体验')
  }, role)
  await page.route('**/api/**', route => new URL(route.request().url()).pathname.startsWith('/api/')
    ? route.fulfill({ json: { code: '200', data: [] } }) : route.continue())
  await page.goto('/chapter-knowledge-graph-preview')
  await expect(page.getByRole('heading', { name: '机器学习概述', exact: true })).toBeVisible()
  await expect(page.locator('.vue-flow__node')).toHaveCount(15)
}

test('chapter graph shows real chapter concepts, labelled directional relations and their evidence', async ({ page }) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await openGraph(page)
  await expect(page.getByText('第2章 · 章节示范')).toBeVisible()
  await expect(page.locator('.vue-flow__edge')).toHaveCount(17)
  await expect(page.locator('.vue-flow__edge-path[marker-end]').first()).toBeVisible()
  await page.getByTestId('concept-loss').click()
  await expect(page.getByTestId('graph-detail')).toContainText('损失函数')
  await expect(page.getByTestId('graph-detail')).toContainText('第 17–18 页')
  await page.locator('.vue-flow__edge[data-id="criterion-loss"]').click()
  await expect(page.getByTestId('graph-detail')).toContainText('包含')
  await expect(page.getByTestId('graph-detail')).toContainText('课件整理')
  expect(errors).toEqual([])
})

test('node dragging moves connected edges and survives selection and filtering; reset restores layout', async ({ page }) => {
  await openGraph(page)
  const node = page.locator('.vue-flow__node[data-id="loss"]')
  const path = page.locator('.vue-flow__edge[data-id="criterion-loss"] .vue-flow__edge-path')
  const before = await node.evaluate(element => element.style.transform)
  const oldPath = await path.getAttribute('d')
  const box = await node.boundingBox()
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2 + 65, box.y + box.height / 2 - 35, { steps: 12 })
  await page.mouse.up()
  await expect.poll(() => node.evaluate(element => element.style.transform)).not.toBe(before)
  await expect(path).not.toHaveAttribute('d', oldPath)
  const moved = await node.evaluate(element => element.style.transform)
  await page.getByRole('button', { name: '只看知识关系', exact: true }).click()
  await page.getByTestId('concept-model').click()
  await expect.poll(() => node.evaluate(element => element.style.transform)).toBe(moved)
  await page.getByRole('button', { name: '恢复布局', exact: true }).click()
  await expect.poll(() => node.evaluate(element => element.style.transform)).toBe(before)
})

test('search finds a concept, handles no match, and zoom controls work', async ({ page }) => {
  await openGraph(page)
  const viewport = page.locator('.vue-flow__transformationpane')
  const initial = await viewport.getAttribute('style')
  await page.getByRole('button', { name: '放大图谱', exact: true }).click()
  await expect(viewport).not.toHaveAttribute('style', initial)
  await page.getByPlaceholder('搜索本章知识点').fill('梯度')
  await page.getByRole('button', { name: '定位知识点：梯度下降' }).click()
  await expect(page.getByTestId('graph-detail')).toContainText('梯度下降')
  await page.getByPlaceholder('搜索本章知识点').fill('不存在的知识点')
  await expect(page.getByText('没有找到匹配的知识点')).toBeVisible()
  await page.getByPlaceholder('搜索本章知识点').fill('')
  await page.getByRole('button', { name: '适应视图', exact: true }).click()
  await expect(page.getByTestId('concept-ml')).toBeVisible()
})

test('learning suggestions are distinguished and filtering does not invent relations', async ({ page }) => {
  await openGraph(page)
  await page.getByRole('button', { name: '只看学习建议', exact: true }).click()
  await expect(page.locator('.vue-flow__edge')).toHaveCount(3)
  await page.getByRole('button', { name: '查看关系：模型 → 学习准则' }).click()
  await expect(page.getByTestId('graph-detail')).toContainText('学习建议')
  await expect(page.getByTestId('graph-detail')).toContainText('待教师确认')
  await page.getByRole('button', { name: '全部关系', exact: true }).click()
  await expect(page.locator('.vue-flow__edge')).toHaveCount(17)
})

test('student can use graph at narrow width without page overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await openGraph(page, 'student')
  await expect(page.getByTestId('chapter-graph-canvas')).toBeVisible()
  await page.getByPlaceholder('搜索本章知识点').fill('损失')
  await page.getByRole('button', { name: '定位知识点：损失函数' }).click()
  await expect(page.getByTestId('graph-detail')).toContainText('损失函数')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
})

test('knowledge workspace exposes the chapter demo and anonymous access keeps login boundary', async ({ page }) => {
  await page.goto('/chapter-knowledge-graph')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await openGraph(page)
  await page.goto('/teacher-knowledge-base')
  await page.getByRole('link', { name: '课程知识图谱', exact: true }).click()
  await expect(page.getByRole('heading', { name: '课程知识图谱', exact: true })).toBeVisible()
})

test('local demonstration is interactive without a backend or a login session', async ({ page }) => {
  const apiRequests = []
  page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/')) apiRequests.push(request.url()) })
  await page.goto('/chapter-knowledge-graph-preview')
  await expect(page.getByRole('heading', { name: '机器学习概述', exact: true })).toBeVisible()
  await page.getByTestId('concept-model').focus()
  await page.keyboard.press('Enter')
  await expect(page.getByTestId('graph-detail')).toContainText('线性模型')
  await expect(page.locator('.vue-flow__node')).toHaveCount(15)
  expect(apiRequests).toEqual([])
})
