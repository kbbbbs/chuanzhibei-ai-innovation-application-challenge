import { test, expect } from '@playwright/test'

// Browser contract tests use controlled HTTP responses. Neo4j is verified separately by backend integration tests.
const nodes = [
  {
    id: 'supervised',
    name: '监督学习',
    type: 'CONCEPT',
    description: '利用有标签样本学习。',
    aliases: ['有监督学习'],
    evidenceIds: ['chunk1'],
  },
  {
    id: 'classification',
    name: '分类',
    type: 'TASK',
    description: '预测离散类别。',
    aliases: [],
    evidenceIds: ['chunk1'],
  },
  {
    id: 'regression',
    name: '回归',
    type: 'TASK',
    description: '预测连续数值。',
    aliases: [],
    evidenceIds: ['chunk1'],
  },
]
const edges = [
  {
    id: 'task1',
    source: 'supervised',
    target: 'classification',
    type: 'HAS_TASK',
    label: '任务类型',
    explanation: '监督学习包括分类任务。',
    origin: 'DOCUMENT',
    evidenceIds: ['chunk1'],
  },
]
const source = {
  id: 'chunk1',
  chunkId: 'chunk1',
  content: '监督学习按任务分为分类和回归。',
  pageNo: 31,
  documentId: 'doc1',
  documentVersion: 1,
  parseRevision: 1,
  indexVersion: 'idx1',
  charStart: 0,
  charEnd: 17,
}

async function setup(
  page,
  { role = 'teacher', unavailable = false, empty = false, dense = false } = {},
) {
  await page.addInitScript((role) => {
    localStorage.setItem('token', 'graph-contract-test')
    localStorage.setItem('userId', '7')
    localStorage.setItem('roleCode', role)
    localStorage.setItem('nickname', '图谱测试')
  }, role)
  const calls = []
  let graph = {
    graphId: 'g1',
    title: '机器学习概述',
    courseId: 1,
    chapterId: 2,
    documentId: 'doc1',
    documentVersion: 1,
    status: 'PUBLISHED',
    revision: 1,
    graphVersion: 1,
    publishedVersion: 1,
    nodes,
    edges,
    evidence: [source],
    truncated: false,
  }
  if (dense) {
    graph.nodes = Array.from({ length: 24 }, (_, index) => ({
      ...nodes[0],
      id: `concept-${index}`,
      name: `知识点${index + 1}`,
    }))
    graph.edges = graph.nodes.slice(1).map((node, index) => ({
      ...edges[0],
      id: `relation-${index}`,
      source: `concept-${Math.floor(index / 2)}`,
      target: node.id,
    }))
  }
  await page.route('**/*', async (route) => {
    const req = route.request(),
      url = new URL(req.url()),
      path = url.pathname
    if (
      url.port === '5173' ||
      !['courses', 'api'].some((prefix) => path.startsWith('/' + prefix))
    )
      return route.continue()
    const body = req.postData() ? req.postDataJSON() : undefined
    calls.push({ path, method: req.method(), body })
    const ok = (data) => route.fulfill({ json: { code: '200', data } })
    if (path === '/courses')
      return ok({
        records: [{ id: 1, title: '人工智能基础', teacherId: 7 }],
        total: 1,
      })
    if (path === '/courses/1/chapters')
      return ok([{ id: 2, title: '机器学习概述' }])
    if (path === '/api/rag/knowledge-bases')
      return ok([{ id: 'kb1', name: '人工智能课件' }])
    if (path === '/api/rag/knowledge-bases/kb1/documents')
      return ok([
        { id: 'doc1', title: '第2章.pdf', document_version: 1, page_count: 36 },
      ])
    if (path === '/api/rag/graphs' && req.method() === 'GET')
      return ok(empty ? [] : [graph])
    if (path === '/api/rag/graphs' && req.method() === 'POST') {
      graph = {
        ...graph,
        ...body,
        status: 'REVIEW_REQUIRED',
        publishedVersion: 0,
        graphVersion: 0,
        nodes: [],
        edges: [],
      }
      return ok(graph)
    }
    if (path === '/api/rag/graphs/g1/sources') return ok({ chunks: [source] })
    if (path === '/api/rag/graphs/g1/draft') {
      graph = {
        ...graph,
        ...body,
        revision: body.revision + 1,
        status: 'REVIEW_REQUIRED',
      }
      return ok(graph)
    }
    if (path === '/api/rag/graphs/g1/publish')
      return ok({ ...graph, status: 'PROJECTING', jobId: 'j1' })
    if (path === '/api/rag/graphs/g1/jobs/j1') {
      graph = {
        ...graph,
        status: 'PUBLISHED',
        graphVersion: 2,
        publishedVersion: 2,
      }
      return ok({ status: 'DONE' })
    }
    if (path === '/api/rag/graphs/g1/evidence/chunk1') return ok(source)
    if (path.includes('/neighbors'))
      return ok({
        nodes: [nodes[0], nodes[2]],
        edges: [{ ...edges[0], id: 'task2', target: 'regression' }],
        truncated: false,
      })
    if (path.includes('/paths'))
      return ok({ nodes: nodes.slice(0, 2), edges, truncated: false })
    if (path === '/api/rag/graphs/g1') {
      if (unavailable)
        return route.fulfill({
          status: 503,
          json: { code: '503', msg: '图谱服务暂不可用' },
        })
      return ok(graph)
    }
    return ok([])
  })
  await page.goto('/chapter-knowledge-graph?courseId=1')
  return calls
}

test('official graph reads API nodes and evidence, expands neighbours and searches aliases', async ({
  page,
}) => {
  const calls = await setup(page)
  await expect(
    page.getByRole('heading', { name: '课程知识图谱', exact: true }),
  ).toBeVisible()
  await expect(
    page.getByTestId('knowledge-network').locator('canvas').first(),
  ).toBeVisible()
  await page.getByLabel('搜索知识点').fill('有监督')
  await page.getByRole('button', { name: '定位监督学习', exact: true }).click()
  await expect(page.getByTestId('graph-detail')).toContainText(
    '利用有标签样本学习',
  )
  await page.getByRole('button', { name: '展开相邻知识', exact: true }).click()
  await expect(
    page.getByRole('button', { name: '查看关系监督学习 → 回归', exact: true }),
  ).toBeVisible()
  await page
    .getByRole('button', { name: '查看关系监督学习 → 分类', exact: true })
    .click()
  await expect(page.getByTestId('graph-detail')).toContainText(
    '监督学习包括分类任务',
  )
  await page
    .getByRole('button', { name: '查看来源chunk1', exact: true })
    .click()
  await expect(page.getByTestId('evidence-detail')).toContainText('第 31 页')
  await expect(page.getByTestId('evidence-detail')).toContainText(
    source.content,
  )
  expect(calls.some((call) => call.path.endsWith('/neighbors'))).toBe(true)
  expect(calls.some((call) => call.path.endsWith('/evidence/chunk1'))).toBe(
    true,
  )
})

test('teacher creates a chapter draft, imports real source ids, saves and publishes', async ({
  page,
}) => {
  const calls = await setup(page, { empty: true })
  await page.getByRole('button', { name: '新建章节图谱', exact: true }).click()
  await page.getByLabel('图谱标题').fill('第2章知识网络')
  await page.getByLabel('所属章节').selectOption('2')
  await page.getByLabel('资料库').selectOption('kb1')
  await page.getByLabel('来源资料').selectOption('doc1')
  await page.getByRole('button', { name: '创建草稿', exact: true }).click()
  await expect(page.getByTestId('graph-editor')).toBeVisible()
  await expect(page.getByTestId('graph-sources')).toContainText('chunk1')
  await page.getByText('批量导入与编辑', { exact: true }).click()
  await page.getByLabel('实体与关系 JSON').fill('{invalid')
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByTestId('graph-editor')).toBeVisible()
  await page
    .getByLabel('实体与关系 JSON')
    .fill(JSON.stringify({ nodes, edges }))
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page.getByText('草稿已保存')).toBeVisible()
  await page.getByLabel('我已核对实体、关系和来源').check()
  await page.getByRole('button', { name: '审核并发布', exact: true }).click()
  await expect(page.getByText('图谱已发布')).toBeVisible()
  expect(
    calls.find(
      (call) => call.method === 'POST' && call.path === '/api/rag/graphs',
    ).body,
  ).toMatchObject({
    courseId: 1,
    chapterId: 2,
    documentId: 'doc1',
    documentVersion: 1,
  })
  expect(
    calls.find((call) => call.path.endsWith('/draft')).body.nodes[0]
      .evidenceIds,
  ).toEqual(['chunk1'])
})

test('student sees published graph without draft controls and can request a learning path', async ({
  page,
}) => {
  const calls = await setup(page, { role: 'student' })
  await expect(page.getByTestId('knowledge-network')).toBeVisible()
  await expect(
    page.getByRole('button', { name: '新建章节图谱', exact: true }),
  ).toHaveCount(0)
  await expect(
    page.getByRole('button', { name: '编辑草稿', exact: true }),
  ).toHaveCount(0)
  await page.getByLabel('路径起点').selectOption('supervised')
  await page.getByLabel('路径终点').selectOption('classification')
  await page.getByLabel('仅看已审核先修关系').check()
  await page.getByRole('button', { name: '查看路径', exact: true }).click()
  await expect(page.getByText('已高亮查询路径')).toBeVisible()
  expect(calls.some((call) => call.path.endsWith('/paths'))).toBe(true)
})

test('network dragging changes node and edge positions, filters and expansion keep the moved node', async ({
  page,
}) => {
  await setup(page)
  const network = page.getByTestId('knowledge-network')
  await expect(network.locator('canvas').first()).toBeVisible()
  await network.scrollIntoViewIfNeeded()
  await expect(page.getByTestId('graph-stage')).toHaveAttribute('data-layout-state', 'ready')
  // Read the third-party canvas instance for geometry, then perform drag through actual mouse input.
  const geometry = () =>
    network.evaluate((element) => {
      const cy = element._cyreg.cy
      const node = cy.getElementById('node:supervised'),
        edge = cy.getElementById('edge:task1')
      return {
        model: node.position(),
        rendered: node.renderedPosition(),
        edge: edge.sourceEndpoint(),
      }
    })
  const before = await geometry(),
    bounds = await network.boundingBox()
  await page.mouse.move(
    bounds.x + before.rendered.x,
    bounds.y + before.rendered.y,
  )
  await page.mouse.down()
  await page.mouse.move(
    bounds.x + before.rendered.x + 55,
    bounds.y + before.rendered.y + 40,
    { steps: 12 },
  )
  await page.mouse.up()
  const moved = await geometry()
  expect(moved.model).not.toEqual(before.model)
  expect(moved.edge).not.toEqual(before.edge)
  await page.getByLabel('筛选关系').selectOption('HAS_TASK')
  await page
    .getByRole('button', { name: '选择知识点分类', exact: true })
    .click()
  expect((await geometry()).model).toEqual(moved.model)
  await page
    .getByRole('button', { name: '选择知识点监督学习', exact: true })
    .click()
  await page.getByRole('button', { name: '展开相邻知识', exact: true }).click()
  await expect(
    page.getByRole('button', { name: '查看关系监督学习 → 回归', exact: true }),
  ).toBeVisible()
  expect((await geometry()).model).toEqual(moved.model)
})

test('graph service failure shows its status and does not substitute chapter demo', async ({
  page,
}) => {
  await setup(page, { unavailable: true })
  await expect(page.getByRole('alert')).toContainText('图谱服务暂不可用')
  await expect(page.getByTestId('knowledge-network')).toHaveCount(0)
  await expect(page.locator('.vue-flow')).toHaveCount(0)
})

test('chapter-sized network remains readable and selection zooms into the chosen knowledge point', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await setup(page, { dense: true })
  const network = page.getByTestId('knowledge-network')
  await expect(network.locator('canvas').first()).toBeVisible()
  await expect(page.getByTestId('graph-stage')).toHaveAttribute('data-layout-state', 'ready')
  const metrics = await network.evaluate((el) => {
    const cy = el._cyreg.cy
    return {
      nodeWidth: cy.nodes().first().renderedWidth(),
      fontSize: cy.nodes().first().numericStyle('font-size') * cy.zoom(),
    }
  })
  expect(metrics.nodeWidth).toBeGreaterThan(35)
  expect(metrics.fontSize).toBeGreaterThanOrEqual(9)
  await page
    .getByRole('button', { name: '选择知识点知识点1', exact: true })
    .click()
  await expect
    .poll(() => network.evaluate((el) => el._cyreg.cy.zoom()))
    .toBeGreaterThanOrEqual(0.89)
})

test('empty student chapter is readable on mobile and has no sample fallback', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await setup(page, { role: 'student', empty: true })
  await expect(page.getByText('本课程暂无已发布图谱')).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBe(true)
})
