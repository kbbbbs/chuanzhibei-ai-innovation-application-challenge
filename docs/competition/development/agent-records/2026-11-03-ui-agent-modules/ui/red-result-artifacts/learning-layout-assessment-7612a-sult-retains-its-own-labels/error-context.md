# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: learning-layout-assessment.spec.js >> HOMEWORK result retains its own labels
- Location: e2e\learning-layout-assessment.spec.js:70:3

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: locator('.exam-result .card-header')
Expected substring: "作业结果"
Received string:    "考试结果返回"
Timeout: 10000ms

Call log:
  - Expect "toContainText" with timeout 10000ms
  - waiting for locator('.exam-result .card-header')
    20 × locator resolved to <div data-v-0f7fbd9f="" class="card-header">…</div>
       - unexpected value "考试结果返回"

```

```yaml
- text: 考试结果
- button "返回"
```

# Test source

```ts
  1  | import { test, expect } from '@playwright/test'
  2  | 
  3  | // Controlled API contracts; live browser evidence is captured separately.
  4  | async function fixture(page, role = 'student') {
  5  |   await page.addInitScript(role => {
  6  |     localStorage.setItem('token', 'ui-contract')
  7  |     localStorage.setItem('userId', '2')
  8  |     localStorage.setItem('roleCode', role)
  9  |     localStorage.setItem('username', '布局核验')
  10 |     localStorage.removeItem('courseLessonSidebarCollapsed')
  11 |   }, role)
  12 |   await page.route('**/*', async route => {
  13 |     const url = new URL(route.request().url())
  14 |     if (url.port !== '8080') return route.continue()
  15 |     const path = url.pathname.replace(/^\/api/, '')
  16 |     let data = []
  17 |     if (path === '/courses/1') data = { id: 1, title: '算法学习课程' }
  18 |     if (path === '/courses/1/chapters') data = [{ id: 1, title: '第一章' }]
  19 |     if (path === '/chapters/1/lessons') data = [{ id: 1, title: '算法基础', resourceType: 'text', content: '课程正文核验' }]
  20 |     if (path === '/courses/1/experience') data = { textbook: [] }
  21 |     if (path === '/rag/knowledge-bases') data = [{ id: 'kb', name: '课程知识库' }]
  22 |     if (path === '/rag/sessions') data = { id: 'session' }
  23 |     if (path.includes('/notes')) data = { title: '算法基础', contentMd: '已有笔记' }
  24 |     if (/^\/(homeworks|exams)\/1$/.test(path)) data = { id: 1, name: '基础练习' }
  25 |     if (/^\/(homework-submissions|submissions)\/1\/student$/.test(path)) {
  26 |       data = { id: 1, homeworkId: 1, examId: 1, homeworkName: '基础练习', examName: '基础练习',
  27 |         status: 'GRADED', totalScore: 90, objectiveScore: 50, subjectiveScore: 40, answers: [] }
  28 |     }
  29 |     return route.fulfill({ json: { code: '200', data } })
  30 |   })
  31 | }
  32 | 
  33 | for (const width of [375, 768, 1080, 1440]) {
  34 |   test(`lesson tools remain readable and usable at ${width}px`, async ({ page }) => {
  35 |     await page.setViewportSize({ width, height: 960 })
  36 |     await fixture(page)
  37 |     await page.goto('/course-learn/1?classId=1&lessonId=1')
  38 |     await expect(page.locator('.lesson-main-card')).toContainText('课程正文核验')
  39 |     await expect(page.locator('.note-header')).toBeVisible()
  40 |     const sizes = await page.evaluate(() => {
  41 |       const rect = selector => {
  42 |         const e = document.querySelector(selector), r = e.getBoundingClientRect()
  43 |         return { width: r.width, height: r.height, right: r.right, scroll: e.scrollWidth, client: e.clientWidth }
  44 |       }
  45 |       return { viewport: innerWidth, document: document.documentElement.scrollWidth,
  46 |         note: rect('.note-card'), title: rect('.assistant-title'), select: rect('.kb-select'),
  47 |         assistant: rect('.ai-note-assistant') }
  48 |     })
  49 |     expect(sizes.document).toBeLessThanOrEqual(sizes.viewport + 1)
  50 |     expect(sizes.note.width).toBeGreaterThanOrEqual(280)
  51 |     expect(sizes.title.height).toBeLessThanOrEqual(30)
  52 |     expect(sizes.select.width).toBeGreaterThanOrEqual(125)
  53 |     expect(sizes.select.right).toBeLessThanOrEqual(sizes.assistant.right + 1)
  54 |     await page.getByRole('tab', { name: '问答', exact: true }).click()
  55 |     await expect(page.locator('.quick-chat-card')).toBeVisible()
  56 |     await page.getByRole('tab', { name: '笔记', exact: true }).click()
  57 |     await page.getByRole('button', { name: '收起课程目录', exact: true }).click()
  58 |     await page.getByRole('button', { name: '展开课程目录', exact: true }).click()
  59 |     await expect(page.locator('.lesson-sidebar-card')).toBeVisible()
  60 |   })
  61 | }
  62 | 
  63 | for (const kind of ['HOMEWORK', 'EXAM']) {
  64 |   const label = kind === 'HOMEWORK' ? '作业' : '考试'
  65 |   test(`${kind} grading retains its own label`, async ({ page }) => {
  66 |     await fixture(page, 'teacher')
  67 |     await page.goto(kind === 'HOMEWORK' ? '/homework/1/submissions' : '/exams/1/grading')
  68 |     await expect(page.locator('.exam-grading .card-header')).toContainText(`${label}批改：基础练习`)
  69 |   })
  70 |   test(`${kind} result retains its own labels`, async ({ page }) => {
  71 |     await fixture(page)
  72 |     await page.goto(`/exam-result/1${kind === 'HOMEWORK' ? '?assessmentType=HOMEWORK' : ''}`)
> 73 |     await expect(page.locator('.exam-result .card-header')).toContainText(`${label}结果`)
     |                                                             ^ Error: expect(locator).toContainText(expected) failed
  74 |     await expect(page.locator('.el-descriptions')).toContainText(`${label}名称`)
  75 |   })
  76 | }
  77 | 
```