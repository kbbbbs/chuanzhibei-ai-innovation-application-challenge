# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: chapter-knowledge-graph.spec.js >> student can use graph at narrow width without page overflow
- Location: e2e\chapter-knowledge-graph.spec.js:81:1

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByRole('heading', { name: '机器学习概述', exact: true })
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for getByRole('heading', { name: '机器学习概述', exact: true })

```

# Test source

```ts
  1  | import { test, expect } from '@playwright/test'
  2  | 
  3  | // The graph uses the shipped chapter data. Only login and unrelated shell APIs are controlled.
  4  | async function openGraph(page, role = 'teacher') {
  5  |   await page.addInitScript(role => {
  6  |     localStorage.setItem('token', 'chapter-graph-ui-test')
  7  |     localStorage.setItem('userId', '7')
  8  |     localStorage.setItem('roleCode', role)
  9  |     localStorage.setItem('nickname', '课程体验')
  10 |   }, role)
  11 |   await page.route('**/api/**', route => route.fulfill({ json: { code: '200', data: [] } }))
  12 |   await page.goto('/chapter-knowledge-graph')
> 13 |   await expect(page.getByRole('heading', { name: '机器学习概述', exact: true })).toBeVisible()
     |                                                                            ^ Error: expect(locator).toBeVisible() failed
  14 |   await expect(page.locator('.vue-flow__node')).toHaveCount(15)
  15 | }
  16 | 
  17 | test('chapter graph shows real chapter concepts, labelled directional relations and their evidence', async ({ page }) => {
  18 |   const errors = []
  19 |   page.on('pageerror', error => errors.push(error.message))
  20 |   await openGraph(page)
  21 |   await expect(page.getByText('第2章 · 章节示范')).toBeVisible()
  22 |   await expect(page.locator('.vue-flow__edge')).toHaveCount(17)
  23 |   await expect(page.locator('.vue-flow__edge-path[marker-end]').first()).toBeVisible()
  24 |   await page.getByTestId('concept-loss').click()
  25 |   await expect(page.getByTestId('graph-detail')).toContainText('损失函数')
  26 |   await expect(page.getByTestId('graph-detail')).toContainText('第 17–18 页')
  27 |   await page.getByRole('button', { name: '查看关系：学习准则 → 损失函数' }).click()
  28 |   await expect(page.getByTestId('graph-detail')).toContainText('包含')
  29 |   await expect(page.getByTestId('graph-detail')).toContainText('课件整理')
  30 |   expect(errors).toEqual([])
  31 | })
  32 | 
  33 | test('node dragging moves connected edges and survives selection and filtering; reset restores layout', async ({ page }) => {
  34 |   await openGraph(page)
  35 |   const node = page.locator('.vue-flow__node[data-id="loss"]')
  36 |   const path = page.locator('.vue-flow__edge[data-id="criterion-loss"] .vue-flow__edge-path')
  37 |   const before = await node.getAttribute('style')
  38 |   const oldPath = await path.getAttribute('d')
  39 |   const box = await node.boundingBox()
  40 |   await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  41 |   await page.mouse.down()
  42 |   await page.mouse.move(box.x + box.width / 2 + 65, box.y + box.height / 2 - 35, { steps: 12 })
  43 |   await page.mouse.up()
  44 |   await expect(node).not.toHaveAttribute('style', before)
  45 |   await expect(path).not.toHaveAttribute('d', oldPath)
  46 |   const moved = await node.getAttribute('style')
  47 |   await page.getByRole('button', { name: '只看知识关系', exact: true }).click()
  48 |   await page.getByTestId('concept-model').click()
  49 |   await expect(node).toHaveAttribute('style', moved)
  50 |   await page.getByRole('button', { name: '恢复布局', exact: true }).click()
  51 |   await expect(node).toHaveAttribute('style', before)
  52 | })
  53 | 
  54 | test('search finds a concept, handles no match, and zoom controls work', async ({ page }) => {
  55 |   await openGraph(page)
  56 |   const viewport = page.locator('.vue-flow__transformationpane')
  57 |   const initial = await viewport.getAttribute('style')
  58 |   await page.getByRole('button', { name: '放大图谱', exact: true }).click()
  59 |   await expect(viewport).not.toHaveAttribute('style', initial)
  60 |   await page.getByPlaceholder('搜索本章知识点').fill('梯度')
  61 |   await page.getByRole('button', { name: '定位知识点：梯度下降' }).click()
  62 |   await expect(page.getByTestId('graph-detail')).toContainText('梯度下降')
  63 |   await page.getByPlaceholder('搜索本章知识点').fill('不存在的知识点')
  64 |   await expect(page.getByText('没有找到匹配的知识点')).toBeVisible()
  65 |   await page.getByPlaceholder('搜索本章知识点').fill('')
  66 |   await page.getByRole('button', { name: '适应视图', exact: true }).click()
  67 |   await expect(page.getByTestId('concept-ml')).toBeVisible()
  68 | })
  69 | 
  70 | test('learning suggestions are distinguished and filtering does not invent relations', async ({ page }) => {
  71 |   await openGraph(page)
  72 |   await page.getByRole('button', { name: '只看学习建议', exact: true }).click()
  73 |   await expect(page.locator('.vue-flow__edge')).toHaveCount(3)
  74 |   await page.getByRole('button', { name: '查看关系：模型 → 学习准则' }).click()
  75 |   await expect(page.getByTestId('graph-detail')).toContainText('学习建议')
  76 |   await expect(page.getByTestId('graph-detail')).toContainText('待教师确认')
  77 |   await page.getByRole('button', { name: '全部关系', exact: true }).click()
  78 |   await expect(page.locator('.vue-flow__edge')).toHaveCount(17)
  79 | })
  80 | 
  81 | test('student can use graph at narrow width without page overflow', async ({ page }) => {
  82 |   await page.setViewportSize({ width: 390, height: 844 })
  83 |   await openGraph(page, 'student')
  84 |   await expect(page.getByTestId('chapter-graph-canvas')).toBeVisible()
  85 |   await page.getByPlaceholder('搜索本章知识点').fill('损失')
  86 |   await page.getByRole('button', { name: '定位知识点：损失函数' }).click()
  87 |   await expect(page.getByTestId('graph-detail')).toContainText('损失函数')
  88 |   expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  89 | })
  90 | 
  91 | test('knowledge workspace exposes the chapter demo and anonymous access keeps login boundary', async ({ page }) => {
  92 |   await page.goto('/chapter-knowledge-graph')
  93 |   await expect(page).toHaveURL(/\/login\?redirect=/)
  94 |   await openGraph(page)
  95 |   await page.goto('/teacher-knowledge-base')
  96 |   await page.getByRole('link', { name: '体验章节知识图谱' }).click()
  97 |   await expect(page.getByRole('heading', { name: '机器学习概述', exact: true })).toBeVisible()
  98 | })
  99 | 
```