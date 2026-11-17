import { test, expect } from "@playwright/test";

const nodes = [
  {
    id: "learning",
    label: "监督学习",
    description: "根据带标签的样本学习。",
    category: "CONCEPT",
    citations: [],
  },
  {
    id: "classification",
    label: "分类",
    description: "预测离散类别。",
    category: "TASK",
    citations: [],
  },
  {
    id: "regression",
    label: "回归",
    description: "预测连续值。",
    category: "TASK",
    citations: [],
  },
];

async function openGraph(page) {
  await page.addInitScript(() => {
    for (const [key, value] of Object.entries({
      token: "legacy-graph-test",
      userId: "7",
      roleCode: "teacher",
    }))
      localStorage.setItem(key, value);
  });
  await page.route("**/backend/**", async (route) => {
    const path = new URL(route.request().url()).pathname.replace(
      /^\/backend/,
      "",
    );
    const course = {
      id: 41,
      title: "人工智能基础",
      teacherId: 7,
      status: 1,
      setupStage: "COMPLETED",
    };
    let data = [];
    if (path === "/courses/41" || path === "/courses/41/setup") data = course;
    if (path === "/api/courses/41/knowledge-graph")
      data = {
        courseId: 41,
        title: course.title,
        status: "READY",
        canManage: true,
        nodes,
        relations: [
          {
            id: "classifies",
            source: "learning",
            target: "classification",
            label: "包含任务",
            type: "HAS_TASK",
            citations: [],
          },
          {
            id: "regresses",
            source: "learning",
            target: "regression",
            label: "包含任务",
            type: "HAS_TASK",
            citations: [],
          },
        ],
        generatedAt: "2026-11-17T07:00:00Z",
        sourceSummary: { includedChunks: 3, availableChunks: 3 },
      };
    await route.fulfill({ json: { code: "200", data } });
  });
  await page.goto("/courses/41?tab=graph");
  const stage = page.getByTestId("graph-stage");
  await expect(stage).toBeVisible();
  await stage.scrollIntoViewIfNeeded();
  await expect(stage).toHaveAttribute("data-layout-state", "ready");
  return stage;
}

async function pixels(page) {
  return page.getByTestId("graph-motion-layer").evaluate((canvas) => {
    const data = canvas
      .getContext("2d")
      .getImageData(0, 0, canvas.width, canvas.height).data;
    let hash = 2166136261;
    for (let index = 0; index < data.length; index += 4)
      hash = Math.imul(
        hash ^
          data[index] ^
          (data[index + 1] << 8) ^
          (data[index + 2] << 16) ^
          (data[index + 3] << 24),
        16777619,
      );
    return hash >>> 0;
  });
}

test("course graph restores the original Cytoscape motion and presentation controls", async ({
  page,
}) => {
  const stage = await openGraph(page);
  await expect(stage).toHaveAttribute("data-motion-state", "running");
  await expect(stage).toHaveAttribute("data-layout-state", "ready");
  const layer = page.getByTestId("graph-motion-layer");
  await expect
    .poll(() =>
      layer.evaluate((canvas) =>
        [
          ...canvas
            .getContext("2d")
            .getImageData(0, 0, canvas.width, canvas.height).data,
        ].some((value) => value > 0),
      ),
    )
    .toBe(true);
  const active = await pixels(page);
  await page.waitForTimeout(180);
  expect(await pixels(page)).not.toBe(active);
  await page.getByRole("button", { name: "暂停动态", exact: true }).click();
  await expect(stage).toHaveAttribute("data-motion-state", "paused");
  await page.waitForTimeout(180);
  const paused = await pixels(page);
  await page.waitForTimeout(180);
  expect(await pixels(page)).toBe(paused);
  await page.getByRole("button", { name: "播放动态", exact: true }).click();
  await page.getByRole("button", { name: "全屏展示", exact: true }).click();
  await expect(stage).toHaveAttribute("data-presentation", "true");
  await expect(stage.locator(".presentation-heading h2")).toHaveCSS("color", "rgb(233, 241, 255)");
  const box = await stage.boundingBox(),
    viewport = page.viewportSize();
  expect(box.x).toBe(0);
  expect(box.y).toBe(0);
  expect(box.width).toBe(viewport.width);
  expect(box.height).toBe(viewport.height);
  await page.keyboard.press("Escape");
  await expect(stage).toHaveAttribute("data-presentation", "false");
});

test("dragging a restored Cytoscape concept moves its relationship geometry without changing graph facts", async ({
  page,
}) => {
  await openGraph(page);
  const network = page.getByTestId("knowledge-network");
  await expect
    .poll(() =>
      network.evaluate(
        (el) =>
          el._cyreg.cy.animated() ||
          el._cyreg.cy.nodes().some((node) => node.animated()),
      ),
    )
    .toBe(false);
  const before = await network.evaluate((el) => {
    const cy = el._cyreg.cy,
      node = cy.getElementById("node:learning"),
      box = el.getBoundingClientRect();
    return {
      position: node.position(),
      rendered: node.renderedPosition(),
      box: { x: box.x, y: box.y },
      endpoint: cy.getElementById("edge:classifies").renderedSourceEndpoint(),
    };
  });
  const x = before.box.x + before.rendered.x,
    y = before.box.y + before.rendered.y;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + 70, y + 40, { steps: 12 });
  await page.mouse.up();
  const after = await network.evaluate((el) => {
    const cy = el._cyreg.cy;
    return {
      position: cy.getElementById("node:learning").position(),
      endpoint: cy.getElementById("edge:classifies").renderedSourceEndpoint(),
      nodes: cy.nodes().length,
      edges: cy.edges().length,
    };
  });
  expect(
    Math.hypot(
      after.position.x - before.position.x,
      after.position.y - before.position.y,
    ),
  ).toBeGreaterThan(10);
  expect(
    Math.hypot(
      after.endpoint.x - before.endpoint.x,
      after.endpoint.y - before.endpoint.y,
    ),
  ).toBeGreaterThan(10);
  expect(after.nodes).toBe(3);
  expect(after.edges).toBe(2);
});
