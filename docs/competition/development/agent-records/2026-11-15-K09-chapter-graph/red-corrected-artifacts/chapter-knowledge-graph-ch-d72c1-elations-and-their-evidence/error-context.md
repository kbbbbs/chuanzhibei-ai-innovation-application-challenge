# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: chapter-knowledge-graph.spec.js >> chapter graph shows real chapter concepts, labelled directional relations and their evidence
- Location: e2e\chapter-knowledge-graph.spec.js:18:1

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

```yaml
- main:
  - region "页面走丢了":
    - paragraph: PAGE NOT FOUND
    - heading "页面走丢了" [level=1]
    - paragraph: 你访问的页面不存在、已被移动，或网址输入有误。
    - button "返回首页":
      - img
      - text: 返回首页
```

# Test source

```ts
  1   | import { test, expect } from '@playwright/test'
  2   | 
  3   | // The graph uses the shipped chapter data. Only login and unrelated shell APIs are controlled.
  4   | async function openGraph(page, role = 'teacher') {
  5   |   await page.addInitScript(role => {
  6   |     localStorage.setItem('token', 'chapter-graph-ui-test')
  7   |     localStorage.setItem('userId', '7')
  8   |     localStorage.setItem('roleCode', role)
  9   |     localStorage.setItem('nickname', '课程体验')
  10  |   }, role)
  11  |   await page.route('**/api/**', route => new URL(route.request().url()).pathname.startsWith('/api/')
  12  |     ? route.fulfill({ json: { code: '200', data: [] } }) : route.continue())
  13  |   await page.goto('/chapter-knowledge-graph')
> 14  |   await expect(page.getByRole('heading', { name: '机器学习概述', exact: true })).toBeVisible()
      |                                                                            ^ Error: expect(locator).toBeVisible() failed
  15  |   await expect(page.locator('.vue-flow__node')).toHaveCount(15)
  16  | }
  17  | 
  18  | test('chapter graph shows real chapter concepts, labelled directional relations and their evidence', async ({ page }) => {
  19  |   const errors = []
  20  |   page.on('pageerror', error => errors.push(error.message))
  21  |   await openGraph(page)
  22  |   await expect(page.getByText('第2章 · 章节示范')).toBeVisible()
  23  |   await expect(page.locator('.vue-flow__edge')).toHaveCount(17)
  24  |   await expect(page.locator('.vue-flow__edge-path[marker-end]').first()).toBeVisible()
  25  |   await page.getByTestId('concept-loss').click()
  26  |   await expect(page.getByTestId('graph-detail')).toContainText('损失函数')
  27  |   await expect(page.getByTestId('graph-detail')).toContainText('第 17–18 页')
  28  |   await page.getByRole('button', { name: '查看关系：学习准则 → 损失函数' }).click()
  29  |   await expect(page.getByTestId('graph-detail')).toContainText('包含')
  30  |   await expect(page.getByTestId('graph-detail')).toContainText('课件整理')
  31  |   expect(errors).toEqual([])
  32  | })
  33  | 
  34  | test('node dragging moves connected edges and survives selection and filtering; reset restores layout', async ({ page }) => {
  35  |   await openGraph(page)
  36  |   const node = page.locator('.vue-flow__node[data-id="loss"]')
  37  |   const path = page.locator('.vue-flow__edge[data-id="criterion-loss"] .vue-flow__edge-path')
  38  |   const before = await node.getAttribute('style')
  39  |   const oldPath = await path.getAttribute('d')
  40  |   const box = await node.boundingBox()
  41  |   await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  42  |   await page.mouse.down()
  43  |   await page.mouse.move(box.x + box.width / 2 + 65, box.y + box.height / 2 - 35, { steps: 12 })
  44  |   await page.mouse.up()
  45  |   await expect(node).not.toHaveAttribute('style', before)
  46  |   await expect(path).not.toHaveAttribute('d', oldPath)
  47  |   const moved = await node.getAttribute('style')
  48  |   await page.getByRole('button', { name: '只看知识关系', exact: true }).click()
  49  |   await page.getByTestId('concept-model').click()
  50  |   await expect(node).toHaveAttribute('style', moved)
  51  |   await page.getByRole('button', { name: '恢复布局', exact: true }).click()
  52  |   await expect(node).toHaveAttribute('style', before)
  53  | })
  54  | 
  55  | test('search finds a concept, handles no match, and zoom controls work', async ({ page }) => {
  56  |   await openGraph(page)
  57  |   const viewport = page.locator('.vue-flow__transformationpane')
  58  |   const initial = await viewport.getAttribute('style')
  59  |   await page.getByRole('button', { name: '放大图谱', exact: true }).click()
  60  |   await expect(viewport).not.toHaveAttribute('style', initial)
  61  |   await page.getByPlaceholder('搜索本章知识点').fill('梯度')
  62  |   await page.getByRole('button', { name: '定位知识点：梯度下降' }).click()
  63  |   await expect(page.getByTestId('graph-detail')).toContainText('梯度下降')
  64  |   await page.getByPlaceholder('搜索本章知识点').fill('不存在的知识点')
  65  |   await expect(page.getByText('没有找到匹配的知识点')).toBeVisible()
  66  |   await page.getByPlaceholder('搜索本章知识点').fill('')
  67  |   await page.getByRole('button', { name: '适应视图', exact: true }).click()
  68  |   await expect(page.getByTestId('concept-ml')).toBeVisible()
  69  | })
  70  | 
  71  | test('learning suggestions are distinguished and filtering does not invent relations', async ({ page }) => {
  72  |   await openGraph(page)
  73  |   await page.getByRole('button', { name: '只看学习建议', exact: true }).click()
  74  |   await expect(page.locator('.vue-flow__edge')).toHaveCount(3)
  75  |   await page.getByRole('button', { name: '查看关系：模型 → 学习准则' }).click()
  76  |   await expect(page.getByTestId('graph-detail')).toContainText('学习建议')
  77  |   await expect(page.getByTestId('graph-detail')).toContainText('待教师确认')
  78  |   await page.getByRole('button', { name: '全部关系', exact: true }).click()
  79  |   await expect(page.locator('.vue-flow__edge')).toHaveCount(17)
  80  | })
  81  | 
  82  | test('student can use graph at narrow width without page overflow', async ({ page }) => {
  83  |   await page.setViewportSize({ width: 390, height: 844 })
  84  |   await openGraph(page, 'student')
  85  |   await expect(page.getByTestId('chapter-graph-canvas')).toBeVisible()
  86  |   await page.getByPlaceholder('搜索本章知识点').fill('损失')
  87  |   await page.getByRole('button', { name: '定位知识点：损失函数' }).click()
  88  |   await expect(page.getByTestId('graph-detail')).toContainText('损失函数')
  89  |   expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  90  | })
  91  | 
  92  | test('knowledge workspace exposes the chapter demo and anonymous access keeps login boundary', async ({ page }) => {
  93  |   await page.goto('/chapter-knowledge-graph')
  94  |   await expect(page).toHaveURL(/\/login\?redirect=/)
  95  |   await openGraph(page)
  96  |   await page.goto('/teacher-knowledge-base')
  97  |   await page.getByRole('link', { name: '体验章节知识图谱' }).click()
  98  |   await expect(page.getByRole('heading', { name: '机器学习概述', exact: true })).toBeVisible()
  99  | })
  100 | 
```