import { test, expect } from '@playwright/test'

// These browser tests use a controlled graph API. The actual chapter is checked
// separately against the running application before screenshots are delivered.
const concepts = [
  {
    id: 'supervised',
    name: '监督学习',
    type: 'CONCEPT',
    description: '使用带标签的样本学习。',
    aliases: [],
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
    description: '预测连续值。',
    aliases: [],
    evidenceIds: ['chunk1'],
  },
]
const relations = [
  {
    id: 'classification-task',
    source: 'supervised',
    target: 'classification',
    type: 'HAS_TASK',
    label: '任务类型',
    explanation: '监督学习包含分类任务。',
    origin: 'DOCUMENT',
    evidenceIds: ['chunk1'],
  },
  {
    id: 'related-task',
    source: 'supervised',
    target: 'classification',
    type: 'RELATED_TO',
    label: '相关知识',
    explanation: '课件关联的概念。',
    origin: 'DOCUMENT',
    evidenceIds: ['chunk1'],
  },
]

async function openGraph(page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'motion-test-session')
    localStorage.setItem('userId', '7')
    localStorage.setItem('roleCode', 'student')
  })
  const graph = {
    graphId: 'motion',
    title: '机器学习概述',
    courseId: 1,
    chapterId: 2,
    status: 'PUBLISHED',
    revision: 1,
    graphVersion: 1,
    publishedVersion: 1,
    nodes: concepts,
    edges: relations,
    evidence: [],
    truncated: false,
  }
  await page.route('**/*', async (route) => {
    const url = new URL(route.request().url())
    if (url.port === '5173' || !/^\/(api|courses)(\/|$)/.test(url.pathname))
      return route.continue()
    const ok = (data) => route.fulfill({ json: { code: '200', data } })
    if (url.pathname === '/courses')
      return ok({ records: [{ id: 1, title: '人工智能基础' }] })
    if (url.pathname === '/api/rag/graphs') return ok([graph])
    if (url.pathname === '/api/rag/graphs/motion') return ok(graph)
    if (url.pathname.endsWith('/neighbors'))
      return ok({
        nodes: [concepts[0], concepts[2]],
        edges: [
          { ...relations[0], id: 'regression-task', target: 'regression' },
        ],
        truncated: false,
      })
    return ok([])
  })
  await page.goto('/chapter-knowledge-graph?courseId=1&graphId=motion')
  const stage = page.getByTestId('graph-stage')
  await expect(stage).toBeVisible()
  await stage.scrollIntoViewIfNeeded()
  await expect(stage).toHaveAttribute('data-layout-state', 'ready')
  await page.evaluate(async () => {
    await document.fonts.ready
    await new Promise((resolve) =>
      requestAnimationFrame(() => requestAnimationFrame(resolve)),
    )
  })
  return stage
}

async function pixels(page) {
  return page.getByTestId('graph-motion-layer').evaluate((canvas) => {
    const pixels = canvas
      .getContext('2d')
      .getImageData(0, 0, canvas.width, canvas.height).data
    let hash = 2166136261,
      painted = 0
    for (let index = 0; index < pixels.length; index += 4) {
      if (pixels[index + 3]) painted++
      hash = Math.imul(
        hash ^
          pixels[index] ^
          (pixels[index + 1] << 8) ^
          (pixels[index + 2] << 16) ^
          (pixels[index + 3] << 24),
        16777619,
      )
    }
    return { hash: hash >>> 0, painted }
  })
}

async function scrollPageTop(stage) {
  await stage.evaluate((element) => {
    let parent = element.parentElement
    while (parent && parent !== document.body) {
      if (
        parent.scrollHeight > parent.clientHeight &&
        /auto|scroll/.test(getComputedStyle(parent).overflowY)
      ) {
        parent.scrollTo(0, 0)
        return
      }
      parent = parent.parentElement
    }
    window.scrollTo(0, 0)
  })
}

async function observePausedPainting(page) {
  // Initial canvas reads differed despite zero draw calls during diagnosis.
  // Warm the readback, then verify both stable pixels and zero paint calls.
  await pixels(page)
  await pixels(page)
  await page.getByTestId('graph-motion-layer').evaluate((canvas) => {
    const context = canvas.getContext('2d')
    const clear = context.clearRect.bind(context)
    canvas.testPaintCount = 0
    context.clearRect = (...args) => {
      canvas.testPaintCount++
      clear(...args)
    }
  })
}

test('directional motion paints changing frames, pauses and resumes without semantic graph mutations', async ({
  page,
}) => {
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  const stage = await openGraph(page)
  await expect(stage).toHaveAttribute('data-motion-state', 'running')
  await expect(
    page.getByRole('button', { name: '暂停动态', exact: true }),
  ).toHaveAttribute('aria-pressed', 'true')
  await expect.poll(async () => (await pixels(page)).painted).toBeGreaterThan(0)
  const first = await pixels(page)
  await expect.poll(async () => (await pixels(page)).hash).not.toBe(first.hash)
  await page.getByRole('button', { name: '暂停动态', exact: true }).click()
  await expect(stage).toHaveAttribute('data-motion-state', 'paused')
  const frozen = await pixels(page)
  await page.waitForTimeout(250)
  expect(await pixels(page)).toEqual(frozen)
  await page.getByRole('button', { name: '播放动态', exact: true }).click()
  await expect(stage).toHaveAttribute('data-motion-state', 'running')
  await expect.poll(async () => (await pixels(page)).hash).not.toBe(frozen.hash)
  const size = await page.getByTestId('knowledge-network').evaluate((el) => ({
    nodes: el._cyreg.cy.nodes().length,
    edges: el._cyreg.cy.edges().length,
  }))
  expect(size).toEqual({ nodes: 3, edges: 2 })
  await page.getByRole('link', { name: '返回课程资料', exact: true }).click()
  await expect(page).toHaveURL(/\/knowledge-base$/)
  await expect(page.getByTestId('graph-motion-layer')).toHaveCount(0)
  expect(errors).toEqual([])
})

test('reduced motion starts with stable frames and no layout or camera animation', async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  const stage = await openGraph(page)
  await expect(stage).toHaveAttribute('data-motion-state', 'reduced')
  await observePausedPainting(page)
  const before = await pixels(page)
  await page.waitForTimeout(250)
  expect(await pixels(page)).toEqual(before)
  expect(
    await page
      .getByTestId('graph-motion-layer')
      .evaluate((canvas) => canvas.testPaintCount),
  ).toBe(0)
  await page
    .getByRole('button', { name: '选择知识点监督学习', exact: true })
    .click()
  const animated = await page
    .getByTestId('knowledge-network')
    .evaluate((el) => ({
      camera: el._cyreg.cy.animated(),
      nodes: el._cyreg.cy.nodes().some((node) => node.animated()),
    }))
  expect(animated).toEqual({ camera: false, nodes: false })
})

test('offscreen graph pauses drawing and restores the users playback choice when visible', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 300 })
  const stage = await openGraph(page)
  await expect(stage).toHaveAttribute('data-motion-state', 'running')
  await scrollPageTop(stage)
  await expect
    .poll(() =>
      page
        .getByTestId('knowledge-network')
        .evaluate(
          (element) => element.getBoundingClientRect().top >= innerHeight,
        ),
    )
    .toBe(true)
  await expect(stage).toHaveAttribute('data-motion-state', 'hidden')
  await observePausedPainting(page)
  const hiddenFrame = await pixels(page)
  await page.waitForTimeout(250)
  expect(await pixels(page)).toEqual(hiddenFrame)
  expect(
    await page
      .getByTestId('graph-motion-layer')
      .evaluate((canvas) => canvas.testPaintCount),
  ).toBe(0)
  await stage.scrollIntoViewIfNeeded()
  await expect(stage).toHaveAttribute('data-motion-state', 'running')
  await page.getByRole('button', { name: '暂停动态', exact: true }).click()
  await scrollPageTop(stage)
  await expect(stage).toHaveAttribute('data-motion-state', 'hidden')
  await stage.scrollIntoViewIfNeeded()
  await expect(stage).toHaveAttribute('data-motion-state', 'paused')
})

test('presentation mode expands the graph and Escape restores normal controls on mobile', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const stage = await openGraph(page)
  await page.getByRole('button', { name: '全屏展示', exact: true }).click()
  await expect(stage).toHaveAttribute('data-presentation', 'true')
  const bounds = await stage.boundingBox()
  expect(bounds.width).toBeGreaterThanOrEqual(385)
  expect(bounds.height).toBeGreaterThanOrEqual(800)
  await expect(
    page.getByRole('button', { name: '退出展示', exact: true }),
  ).toBeVisible()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: '放大图谱', exact: true })).toBeFocused()
  await page.keyboard.press('Shift+Tab')
  await expect(page.getByRole('button', { name: '退出展示', exact: true })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(stage).toHaveAttribute('data-presentation', 'false')
  await expect(
    page.getByRole('button', { name: '全屏展示', exact: true }),
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: '全屏展示', exact: true }),
  ).toBeFocused()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBe(true)
})

test('explicit relayout interpolates nodes and settles before further graph interactions', async ({
  page,
}) => {
  const stage = await openGraph(page)
  await page.getByRole('button', { name: '重新布局', exact: true }).click()
  await expect(stage).toHaveAttribute('data-layout-state', 'settling')
  const animated = await page
    .getByTestId('knowledge-network')
    .evaluate((el) => el._cyreg.cy.nodes().some((node) => node.animated()))
  expect(animated).toBe(true)
  await expect(stage).toHaveAttribute('data-layout-state', 'ready')
})

test('clear associations restores the complete network without moving knowledge points', async ({
  page,
}) => {
  await openGraph(page)
  const network = page.getByTestId('knowledge-network')
  const positions = () =>
    network.evaluate((element) =>
      element._cyreg.cy
        .nodes()
        .map((node) => ({ id: node.id(), position: node.position() })),
    )
  const before = await positions()
  await page
    .getByRole('button', { name: '选择知识点监督学习', exact: true })
    .click()
  const clear = page.getByRole('button', { name: '查看全部关联', exact: true })
  await expect(clear).toBeVisible()
  await clear.click()
  await expect
    .poll(() =>
      network.evaluate(
        (element) => element._cyreg.cy.elements('.chosen,.dimmed,.path').length,
      ),
    )
    .toBe(0)
  expect(await positions()).toEqual(before)
})

test('particle layer follows actual node dragging and graph selection keeps user positions', async ({
  page,
}) => {
  const stage = await openGraph(page)
  await page.getByRole('button', { name: '暂停动态', exact: true }).click()
  const network = page.getByTestId('knowledge-network')
  const geometry = () =>
    network.evaluate((el) => {
      const cy = el._cyreg.cy,
        node = cy.getElementById('node:supervised')
      return {
        model: node.position(),
        rendered: node.renderedPosition(),
        endpoint: cy
          .getElementById('edge:classification-task')
          .renderedSourceEndpoint(),
      }
    })
  const before = await geometry(),
    bounds = await network.boundingBox(),
    paintBefore = await pixels(page)
  await page.mouse.move(
    bounds.x + before.rendered.x,
    bounds.y + before.rendered.y,
  )
  await page.mouse.down()
  await page.mouse.move(
    bounds.x + before.rendered.x + 45,
    bounds.y + before.rendered.y + 30,
    { steps: 10 },
  )
  await page.mouse.up()
  const after = await geometry()
  expect(after.model).not.toEqual(before.model)
  expect(after.endpoint).not.toEqual(before.endpoint)
  await expect
    .poll(async () => (await pixels(page)).hash)
    .not.toBe(paintBefore.hash)
  await page.getByLabel('筛选关系').selectOption('HAS_TASK')
  await page
    .getByRole('button', { name: '选择知识点监督学习', exact: true })
    .click()
  await page.getByRole('button', { name: '展开相邻知识', exact: true }).click()
  await expect(
    page.getByRole('button', { name: '查看关系监督学习 → 回归', exact: true }),
  ).toBeVisible()
  expect((await geometry()).model).toEqual(after.model)
  await expect(stage).toHaveAttribute('data-layout-state', 'ready')
})
