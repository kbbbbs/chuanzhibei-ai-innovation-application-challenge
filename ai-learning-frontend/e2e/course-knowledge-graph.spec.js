import { test, expect } from "@playwright/test";

const course = {
  id: 41,
  title: "人工智能基础",
  categoryId: 1,
  teacherId: 7,
  status: 1,
  setupStage: "COMPLETED",
};
const citation = {
  chunkId: "graph-chunk",
  documentId: "graph-document",
  kbId: "course-base",
  title: "人工智能教材",
  content: "监督学习使用带标签的数据。",
  page: 3,
  ordinal: 1,
  citation: "[1]",
  documentVersion: 1,
};
const readyGraph = {
  courseId: 41,
  title: course.title,
  status: "READY",
  canManage: true,
  nodes: [
    {
      id: "n1",
      label: "监督学习",
      description: "从带标签的数据中学习。",
      citations: [citation],
    },
    {
      id: "n2",
      label: "训练数据",
      description: "用于训练模型的数据。",
      citations: [citation],
    },
  ],
  relations: [
    {
      id: "r1",
      source: "n1",
      target: "n2",
      label: "使用",
      citations: [citation],
    },
  ],
  generatedAt: "2026-11-17T01:00:00Z",
  sourceSummary: {
    includedDocuments: 1,
    availableDocuments: 2,
    includedChunks: 24,
    availableChunks: 50,
    truncated: true,
    processingDocuments: 1,
  },
};

test("chapter graph selection sends only the chosen chapter documents and survives reload", async ({
  page,
}) => {
  const calls = await fixture(page, { empty: true });
  await page.route(
    "**/backend/api/courses/41/knowledge-graph/scope-options",
    (route) =>
      route.fulfill({
        json: {
          code: "200",
          data: {
            chapters: [{ id: 101, title: "第一章" }],
            documents: [
              { id: "chapter-file", title: "第一章教材.pdf", chunks: 12 },
              { id: "other-file", title: "第二章教材.pdf", chunks: 20 },
            ],
          },
        },
      }),
  );
  await page.goto("/courses/41?tab=graph");
  await page.locator(".graph-scope .el-select").first().click();
  await page.getByRole("option", { name: "第一章", exact: true }).click();
  await expect(page).toHaveURL(/graphChapter=101/);
  await page.locator(".graph-scope .el-select").nth(1).click();
  await page
    .getByRole("option", { name: "第一章教材.pdf · 12 个片段", exact: true })
    .click();
  await page.getByRole("heading", { name: "知识图谱", exact: true }).click();
  await page.getByRole("button", { name: "生成知识图谱", exact: true }).click();
  await expect
    .poll(
      () =>
        calls.find((call) => call.path.endsWith("/generate"))?.body.chapterId,
    )
    .toBe(101);
  expect(
    calls.find((call) => call.path.endsWith("/generate")).body.documentIds,
  ).toEqual(["chapter-file"]);
  await page.reload();
  await expect(page.locator(".graph-scope .el-select").first()).toContainText(
    "第一章",
  );
  expect(
    calls
      .filter((call) => call.path === "/api/courses/41/knowledge-graph")
      .some((call) => call.query.chapterId === "101"),
  ).toBeTruthy();
});

async function expectCanvasNode(page, name) {
  const network = page.getByTestId("knowledge-network");
  await expect(network).toBeVisible();
  await expect
    .poll(() =>
      network.evaluate(
        (element, name) =>
          element._cyreg?.cy.nodes().some((node) => node.data("name") === name),
        name,
      ),
    )
    .toBe(true);
}

test("saved partial graph shows its retry error together with cache progress", async ({
  page,
}) => {
  await fixture(page);
  await page.route("**/backend/api/courses/41/knowledge-graph", (route) =>
    route.fulfill({
      json: {
        code: "200",
        data: {
          ...readyGraph,
          partial: true,
          job: {
            id: "partial-job",
            status: "FAILED",
            savedChunks: 3,
            currentChunk: 3,
            totalChunks: 8,
            reusedChunks: 2,
            error: "模型响应超时，已保存完成进度，可继续生成。",
          },
        },
      },
    }),
  );
  await page.goto("/courses/41?tab=graph");
  await expect(
    page.getByText("当前展示已完成部分", { exact: false }),
  ).toBeVisible();
  await expect(page.getByText("已复用 2 个片段的抽取结果。")).toBeVisible();
  await expect(
    page.getByText("模型响应超时，已保存完成进度，可继续生成。", {
      exact: true,
    }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "继续生成", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "查找关联路径", exact: true }),
  ).toBeDisabled();
});
async function clickCanvasNode(page, name) {
  await expectCanvasNode(page, name);
  await expect(page.getByTestId("graph-stage")).toHaveAttribute(
    "data-layout-state",
    "ready",
  );
  const network = page.getByTestId("knowledge-network");
  await network.scrollIntoViewIfNeeded();
  await expect
    .poll(() =>
      network.evaluate(
        (element) =>
          element._cyreg.cy.animated() ||
          element._cyreg.cy.nodes().some((node) => node.animated()),
      ),
    )
    .toBe(false);
  const coordinates = await network.evaluate((element, name) => {
    const position = element._cyreg.cy
      .nodes()
      .filter((node) => node.data("name") === name)[0]
      .renderedPosition();
    const box = element.getBoundingClientRect();
    return { x: box.x + position.x, y: box.y + position.y };
  }, name);
  await page.mouse.click(coordinates.x, coordinates.y);
}

async function fixture(
  page,
  {
    role = "teacher",
    empty = false,
    running = false,
    failed = false,
    denied = false,
    graphEvidence = false,
    large = false,
    recoverable = false,
  } = {},
) {
  await page.addInitScript((role) => {
    for (const [key, value] of Object.entries({
      token: "graph-fixture",
      userId: "7",
      roleCode: role,
      username: "图谱验证",
    }))
      localStorage.setItem(key, value);
  }, role);
  const calls = [];
  let generated = running,
    reads = 0,
    failure = failed;
  await page.route("**/backend/**", async (route) => {
    const req = route.request(),
      path = new URL(req.url()).pathname.replace(/^\/backend/, ""),
      body = req.postData() ? req.postDataJSON() : null;
    calls.push({
      path,
      method: req.method(),
      body,
      query: Object.fromEntries(new URL(req.url()).searchParams),
    });
    const ok = (data) => route.fulfill({ json: { code: "200", data } });
    if (path === "/courses")
      return ok({
        records: [course, { ...course, id: 42, title: "机器学习" }],
        total: 2,
      });
    if (path === "/categories") return ok([{ id: 1, name: "计算机" }]);
    if (path === "/courses/41" || path === "/courses/41/setup")
      return ok(course);
    if (path === "/courses/41/chapters") return ok([]);
    if (path === "/courses/42" || path === "/courses/42/setup")
      return ok({ ...course, id: 42, title: "机器学习" });
    if (path === "/courses/42/chapters") return ok([]);
    if (
      path === "/api/courses/41/knowledge-graph/neighbors" ||
      path === "/api/courses/41/knowledge-graph/paths"
    )
      return ok({
        nodes: readyGraph.nodes,
        relations: readyGraph.relations,
        storage: "NEO4J",
        truncated: false,
      });
    if (path === "/api/courses/42/knowledge-graph")
      return ok({
        ...readyGraph,
        courseId: 42,
        title: "机器学习",
        nodes: [{ id: "other", label: "概率分布", citations: [] }],
        relations: [],
      });
    if (path === "/api/courses/41/knowledge-graph") {
      if (recoverable && !generated)
        return ok({
          ...readyGraph,
          nodes: [],
          relations: [],
          generatedAt: null,
          sourceSummary: { includedChunks: 0, includedDocuments: 0 },
          job: {
            id: "failed-job",
            status: "FAILED",
            currentChunk: 9,
            totalChunks: 50,
            savedChunks: 9,
            error: "模型响应超时，已保存进度，可以继续生成。",
          },
        });
      if (denied)
        return route.fulfill({
          status: 403,
          json: { code: "403", msg: "没有课程图谱访问资格" },
        });
      if (failure) {
        failure = false;
        return route.fulfill({
          status: 400,
          json: { code: "400", msg: "图谱暂时无法读取" },
        });
      }
      if (generated && ++reads < 3)
        return ok({
          ...readyGraph,
          nodes: [],
          relations: [],
          status: "GENERATING",
          job: {
            id: "job-1",
            status: "RUNNING",
            currentChunk: recoverable ? 9 : 1,
            totalChunks: recoverable ? 50 : 24,
          },
        });
      return ok({
        ...readyGraph,
        canManage: role === "teacher",
        ...(large
          ? {
              nodes: Array.from({ length: 360 }, (_, index) => ({
                id: `concept-${index}`,
                label: `课程概念${index}`,
                description: `第${index}个概念说明`,
                citations: [],
              })),
              relations: [],
            }
          : {}),
        ...(empty && !generated
          ? { status: "EMPTY", nodes: [], relations: [] }
          : {}),
      });
    }
    if (path === "/api/courses/41/knowledge-graph/generate") {
      generated = true;
      reads = 0;
      return ok({ id: "job-1", status: "RUNNING" });
    }
    if (path.endsWith("/jobs/job-1/cancel")) {
      generated = false;
      return ok({ status: "CANCELLED" });
    }
    if (path === "/api/rag/knowledge-bases/old-base")
      return ok({ id: "old-base", course_id: 41 });
    if (path === "/api/assistant/sessions")
      return ok([{ id: 51, title: "课程问答", courseId: 41 }]);
    const graphPaths = graphEvidence
      ? [
          {
            nodes: [
              { id: "n1", label: "监督学习" },
              { id: "n2", label: "训练数据" },
            ],
            relations: [{ source: "n1", target: "n2", label: "使用" }],
            citations: [citation],
          },
        ]
      : [];
    if (path === "/api/assistant/sessions/51/messages")
      return ok([
        {
          id: 52,
          role: "assistant",
          content: "监督学习使用训练数据。",
          graphPaths,
          citations: [],
        },
      ]);
    if (path === "/api/rag/courses/41/knowledge-base")
      return ok({ id: "course-base", name: course.title, owner_id: 7 });
    if (path === "/api/rag/search")
      return ok({ hits: [citation], mode: "bm25", graphPaths });
    if (
      path === "/api/rag/chunks/graph-chunk" ||
      path === "/api/rag/courses/41/chunks/graph-chunk"
    )
      return ok(citation);
    return ok([]);
  });
  return calls;
}

test("course management reaches the graph through course details", async ({
  page,
}) => {
  const calls = await fixture(page);
  await page.goto("/courses");
  await expect(
    page.getByRole("button", { name: "知识图谱", exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole("row")
    .filter({ hasText: course.title })
    .getByRole("button", { name: "课程详情", exact: true })
    .click();
  await page.getByRole("tab", { name: "课程资料", exact: true }).click();
  await page.getByRole("tab", { name: "知识图谱", exact: true }).click();
  await expect(page).toHaveURL(/courses\/41/);
  await expect(
    page.getByRole("tab", { name: "知识图谱", exact: true }),
  ).toBeVisible();
  await expectCanvasNode(page, "监督学习");
  expect(
    calls.some((call) => call.path === "/api/courses/41/knowledge-graph"),
  ).toBeTruthy();
  expect(
    calls.some((call) => call.path === "/api/rag/knowledge-bases"),
  ).toBeFalsy();
});

test("returning to a failed graph restores saved progress and offers continue", async ({
  page,
}) => {
  const calls = await fixture(page, { recoverable: true });
  await page.goto("/courses/41?tab=graph");
  await expect(
    page.getByText("已保存 9/50 个资料片段，可继续生成。", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("已保存部分进度，点击继续生成完成知识图谱。", {
      exact: true,
    }),
  ).toBeVisible();
  await expect(page.getByText(/已覆盖片段 0\/0/)).toHaveCount(0);
  await page.goto("/courses/42?tab=graph");
  await page.goto("/courses/41?tab=graph");
  await expect(
    page.getByRole("button", { name: "继续生成", exact: true }),
  ).toBeVisible();
  expect(calls.filter((call) => call.path.endsWith("/generate"))).toHaveLength(
    0,
  );
  await page.getByRole("button", { name: "继续生成", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("9/50");
  await expectCanvasNode(page, "监督学习");
  expect(calls.filter((call) => call.path.endsWith("/generate"))).toHaveLength(
    1,
  );
});

test("teacher generates course graph and refreshing resumes the job", async ({
  page,
}) => {
  const calls = await fixture(page, { empty: true });
  await page.goto("/courses/41?tab=graph");
  await page.getByRole("button", { name: "生成知识图谱", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("1/24");
  await page.reload();
  await expectCanvasNode(page, "监督学习");
  expect(
    calls.filter(
      (call) => call.method === "POST" && call.path.endsWith("/generate"),
    ),
  ).toHaveLength(1);
  expect(
    calls.find((call) => call.path.endsWith("/generate")).body.requestId,
  ).toBeTruthy();
});

test("node details show authorized sources and start a course assistant question", async ({
  page,
}) => {
  await fixture(page);
  await page.goto("/courses/41?tab=graph");
  await clickCanvasNode(page, "监督学习");
  await expect(
    page.getByText("从带标签的数据中学习。", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText(/已覆盖片段 24\/50/)).toBeVisible();
  await expect(
    page.getByRole("button", { name: /人工智能教材/ }).first(),
  ).toBeVisible();
  await page.screenshot({
    path: test.info().outputPath("course-graph-desktop.png"),
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "围绕此知识点提问", exact: true })
    .click();
  await expect(page).toHaveURL(/knowledge-base\?courseId=41/);
  await expect(page.getByPlaceholder("输入问题")).toHaveValue(/监督学习/);
});

test("student sees course graph without generation or private library controls", async ({
  page,
}) => {
  const calls = await fixture(page, { role: "student" });
  await page.goto("/courses/41?tab=graph");
  await expectCanvasNode(page, "监督学习");
  await expect(
    page.getByRole("button", { name: /生成知识图谱|更新知识图谱/ }),
  ).toHaveCount(0);
  expect(
    calls.some((call) => call.path === "/api/rag/knowledge-bases"),
  ).toBeFalsy();
});

test("graph errors have a retry", async ({ page }) => {
  await fixture(page, { failed: true });
  await page.goto("/courses/41?tab=graph");
  await expect(
    page.getByText("图谱暂时无法读取", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "重新加载", exact: true }).click();
  await expectCanvasNode(page, "监督学习");
});

test("denied course graph reveals no cached nodes", async ({ page }) => {
  await fixture(page, { role: "student", denied: true });
  await page.goto("/courses/41?tab=graph");
  await expect(
    page.getByText("没有课程图谱访问资格", { exact: true }),
  ).toBeVisible();
  await expect(page.getByTestId("knowledge-network")).toHaveCount(0);
});

test("legacy graph link resolves the owning course", async ({ page }) => {
  await fixture(page);
  await page.goto("/knowledge-graph?kbId=old-base");
  await expect(page).toHaveURL(/courses\/41/);
  await expect(
    page.getByRole("tab", { name: "知识图谱", exact: true }),
  ).toBeVisible();
});

test("graph search and narrow layout remain usable and examples stay explicit", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await fixture(page);
  await page.goto("/courses/41?tab=graph");
  await page
    .getByRole("textbox", { name: "搜索知识点", exact: true })
    .fill("监督");
  await expect(
    page.getByRole("button", { name: /监督学习/ }).first(),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: test.info().outputPath("course-graph-mobile.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "章节示范", exact: true }).click();
  await expect(
    page
      .getByRole("dialog")
      .getByText("机器学习概述 · 章节示范", { exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: test.info().outputPath("course-graph-example-mobile.png"),
    fullPage: true,
  });
});

test("assistant displays the graph path actually used by the answer", async ({
  page,
}) => {
  await fixture(page, { graphEvidence: true });
  await page.goto("/knowledge-base?courseId=41&sessionId=51");
  const paths = page.getByTestId("graph-evidence-paths");
  await paths.getByText("图谱关联依据", { exact: true }).click();
  await expect(paths).toContainText("监督学习");
  await expect(paths).toContainText("使用");
  await expect(paths).toContainText("训练数据");
});

test("ordinary answers do not claim to use graph relationships", async ({
  page,
}) => {
  await fixture(page);
  await page.goto("/knowledge-base?courseId=41&sessionId=51");
  await expect(
    page.getByText("监督学习使用训练数据。", { exact: true }),
  ).toBeVisible();
  await expect(page.getByTestId("graph-evidence-paths")).toHaveCount(0);
});

test("search and verification displays actual graph retrieval paths", async ({
  page,
}) => {
  await fixture(page, { graphEvidence: true });
  await page.goto("/courses/41?tab=search");
  await page
    .getByPlaceholder("输入关键词或问题，查看实际命中的原文")
    .fill("监督学习");
  await page.getByRole("button", { name: "检索", exact: true }).click();
  const paths = page
    .locator(".native-kb-workspace #pane-search")
    .getByTestId("graph-evidence-paths");
  await paths.getByText("图谱关联依据", { exact: true }).click();
  await expect(paths).toContainText("训练数据");
});

test("administrator course management keeps teaching graphs private", async ({
  page,
}) => {
  await fixture(page, { role: "admin" });
  await page.goto("/courses");
  await expect(page.getByText(course.title, { exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "知识图谱", exact: true }),
  ).toHaveCount(0);
  await page.goto("/courses/41?tab=graph");
  await expect(page).toHaveURL(/\/courses$/);
});

test("late generation response after changing course does not restart the previous graph poll", async ({
  page,
}) => {
  const calls = await fixture(page, { empty: true });
  let release, started;
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  const dispatch = new Promise((resolve) => {
    started = resolve;
  });
  await page.route(
    "**/api/courses/41/knowledge-graph/generate",
    async (route) => {
      started();
      await gate;
      await route.fulfill({
        json: { code: "200", data: { id: "late-job", status: "RUNNING" } },
      });
    },
  );
  await page.goto("/courses/41?tab=graph");
  const response = page.waitForResponse((value) =>
    value.url().endsWith("/api/courses/41/knowledge-graph/generate"),
  );
  await page.getByRole("button", { name: "生成知识图谱", exact: true }).click();
  await dispatch;
  await page
    .getByLabel("页面导航")
    .getByRole("link", { name: "课程管理", exact: true })
    .click();
  await page
    .getByRole("row")
    .filter({ hasText: "机器学习" })
    .getByRole("button", { name: "课程详情", exact: true })
    .click();
  await page.getByRole("tab", { name: "课程资料", exact: true }).click();
  await page.getByRole("tab", { name: "知识图谱", exact: true }).click();
  await expectCanvasNode(page, "概率分布");
  const previousReads = calls.filter(
    (call) => call.path === "/api/courses/41/knowledge-graph",
  ).length;
  release();
  await response;
  await page.evaluate(() => new Promise(requestAnimationFrame));
  expect(
    calls.filter((call) => call.path === "/api/courses/41/knowledge-graph"),
  ).toHaveLength(previousReads);
  await expect(page.getByRole("status")).toHaveCount(0);
});

test("large course graphs keep a bounded layout and focus searched concepts", async ({
  page,
}) => {
  await fixture(page, { large: true });
  await page.goto("/courses/41?tab=graph");
  const network = page.getByTestId("knowledge-network");
  await expect
    .poll(() =>
      network.evaluate((element) => element._cyreg?.cy.nodes().length),
    )
    .toBe(360);
  const viewport = await network.boundingBox();
  expect(viewport.height).toBeLessThanOrEqual(640);
  await page
    .getByRole("textbox", { name: "搜索知识点", exact: true })
    .fill("课程概念359");
  await page.getByRole("button", { name: "课程概念359", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "课程概念359", exact: true }),
  ).toBeVisible();
  await expect
    .poll(() =>
      network.evaluate((element) => {
        const cy = element._cyreg.cy;
        const node = cy
          .nodes()
          .filter((item) => item.data("name") === "课程概念359")[0];
        if (!node) return false;
        const box = node.renderedBoundingBox();
        return (
          box.x1 >= 0 &&
          box.y1 >= 0 &&
          box.x2 <= cy.width() &&
          box.y2 <= cy.height() &&
          box.w > 100
        );
      }),
    )
    .toBeTruthy();
});

test("graph generation works when the deployment does not provide randomUUID", async ({
  page,
}) => {
  await page.addInitScript(() =>
    Object.defineProperty(globalThis.crypto, "randomUUID", {
      value: undefined,
    }),
  );
  const calls = await fixture(page, { empty: true });
  await page.goto("/courses/41?tab=graph");
  await page.getByRole("button", { name: "生成知识图谱", exact: true }).click();
  await expect
    .poll(
      () =>
        calls.filter((call) => call.path.endsWith("/knowledge-graph/generate"))
          .length,
    )
    .toBe(1);
});

test("course graph uses native neighbor and path queries and highlights the returned relationships", async ({
  page,
}) => {
  const calls = await fixture(page);
  await page.goto("/courses/41?tab=graph");
  await clickCanvasNode(page, "监督学习");
  await page.getByRole("button", { name: "查看相邻知识", exact: true }).click();
  await expect(
    page.getByText("已找到 1 条关联关系", { exact: true }),
  ).toBeVisible();
  await expect
    .poll(() =>
      page
        .getByTestId("knowledge-network")
        .evaluate((element) => element._cyreg.cy.elements(".path").length),
    )
    .toBe(3);
  const query = calls.find((call) =>
    call.path.endsWith("/knowledge-graph/neighbors"),
  );
  expect(query.query.entityId).toBe("n1");
  await page.getByRole("combobox", { name: "起点知识点", exact: true }).click();
  await page
    .getByRole("listbox", { name: "起点知识点", exact: true })
    .getByRole("option", { name: "监督学习", exact: true })
    .click();
  await page.getByRole("combobox", { name: "终点知识点", exact: true }).click();
  await page
    .getByRole("listbox", { name: "终点知识点", exact: true })
    .getByRole("option", { name: "训练数据", exact: true })
    .click();
  await page.getByRole("button", { name: "查找关联路径", exact: true }).click();
  await expect
    .poll(
      () =>
        calls.filter((call) => call.path.endsWith("/knowledge-graph/paths"))
          .length,
    )
    .toBe(1);
  const path = calls.find((call) =>
    call.path.endsWith("/knowledge-graph/paths"),
  );
  expect(path.query).toMatchObject({ from: "n1", to: "n2" });
  await page.getByRole("button", { name: "清除路径", exact: true }).click();
  await expect
    .poll(() =>
      page
        .getByTestId("knowledge-network")
        .evaluate((element) => element._cyreg.cy.elements(".path").length),
    )
    .toBe(0);
});
