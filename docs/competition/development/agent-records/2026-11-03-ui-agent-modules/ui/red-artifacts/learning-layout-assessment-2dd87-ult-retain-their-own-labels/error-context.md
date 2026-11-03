# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: learning-layout-assessment.spec.js >> EXAM grading and result retain their own labels
- Location: e2e\learning-layout-assessment.spec.js:65:3

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: locator('.exam-result .card-header')
Expected substring: "考试结果"
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toContainText" with timeout 10000ms
  - waiting for locator('.exam-result .card-header')

```

```yaml
- button "E EduLoop 智教云环"
- button "首页":
  - img
  - text: 首页
- button "通知":
  - img
  - text: 通知
- button "U 教师导师"
- complementary:
  - text: 管理导航
  - button "收起导航":
    - img
  - menubar:
    - menuitem "教学管理" [expanded]:
      - img
      - text: 教学管理
      - img
      - menu:
        - menuitem "课程管理":
          - img
          - text: 课程管理
        - menuitem "教学班管理":
          - img
          - text: 教学班管理
        - menuitem "题库管理":
          - img
          - text: 题库管理
        - menuitem "试卷管理":
          - img
          - text: 试卷管理
        - menuitem "作业题卷":
          - img
          - text: 作业题卷
        - menuitem "考试管理":
          - img
          - text: 考试管理
        - menuitem "作业管理":
          - img
          - text: 作业管理
        - menuitem "成绩分析":
          - img
          - text: 成绩分析
        - menuitem "智能资源生成":
          - img
          - text: 智能资源生成
    - menuitem "勤工俭学管理":
      - img
      - text: 勤工俭学管理
      - img
    - menuitem "知识库管理":
      - img
      - text: 知识库管理
    - menuitem "通知管理":
      - img
      - text: 通知管理
    - menuitem "日志管理":
      - img
      - text: 日志管理
- main:
  - main:
    - paragraph: 教师端 · 11月3日星期二
    - heading "教师教学控制台" [level=1]
    - paragraph: 布局核验，这里聚焦课程建设、班级教学、考核评价与教学智能工具。
    - button "课程管理":
      - img
      - text: 课程管理
    - button "成绩分析":
      - img
      - text: 成绩分析
    - button "刷新数据":
      - img
      - text: 刷新数据
    - region "教学概览":
      - button "— 我的课程":
        - img
        - strong: —
        - text: 我的课程
      - button "— 教学班":
        - img
        - strong: —
        - text: 教学班
      - button "— 题库题目":
        - img
        - strong: —
        - text: 题库题目
      - button "0 试卷":
        - img
        - strong: "0"
        - text: 试卷
      - button "0 已发布考试":
        - img
        - strong: "0"
        - text: 已发布考试
      - button "0 已发布作业":
        - img
        - strong: "0"
        - text: 已发布作业
    - paragraph: 教师业务域
    - heading "课程与课堂" [level=2]
    - paragraph: 围绕课程内容、教学班和课堂通知组织日常教学。
    - button "课程管理 建设章节、课时与互动内容":
      - img
      - strong: 课程管理
      - text: 建设章节、课时与互动内容
      - img
    - button "教学班管理 维护授课班级与学生名单":
      - img
      - strong: 教学班管理
      - text: 维护授课班级与学生名单
      - img
    - button "通知管理 发布课堂通知和学习提醒":
      - img
      - strong: 通知管理
      - text: 发布课堂通知和学习提醒
      - img
    - button "资源生成 生成大纲、讲义与练习":
      - img
      - strong: 资源生成
      - text: 生成大纲、讲义与练习
      - img
    - paragraph: 考核评价域
    - heading "题库、试卷与考核" [level=2]
    - paragraph: 作业入口统一使用新版考核模型，首页与管理页保持同一数据来源。
    - button "题库管理 维护题目、难度与知识点":
      - img
      - strong: 题库管理
      - text: 维护题目、难度与知识点
      - img
    - button "试卷管理 组卷并检查题目分值":
      - img
      - strong: 试卷管理
      - text: 组卷并检查题目分值
      - img
    - button "考试管理 发布考试、批改与汇总结果":
      - img
      - strong: 考试管理
      - text: 发布考试、批改与汇总结果
      - img
    - button "作业管理 发布新版作业并查看提交":
      - img
      - strong: 作业管理
      - text: 发布新版作业并查看提交
      - img
    - button "成绩分析 按教学班分析考核结果":
      - img
      - strong: 成绩分析
      - text: 按教学班分析考核结果
      - img
    - paragraph: 近期安排
    - heading "已发布考核" [level=2]
    - paragraph: 按开始时间展示最近的考试和作业。
    - img
    - paragraph: 暂无已发布考核
    - complementary:
      - img
      - paragraph: 教学智能域
      - heading "知识库与 Agent" [level=2]
      - paragraph: 维护课程知识资产，辅助生成教学任务、检索资料和组织备课内容。
      - button "知识库管理"
      - button "打开 Agent 助手"
      - paragraph: 数据说明
      - heading "可信统计" [level=2]
      - list:
        - listitem: 课程与班级 分页总数
        - listitem: 题库题目 totalElements
        - listitem: 考试与作业 新版考核接口
      - paragraph: “—”表示接口暂时不可用，不会用当前页条数冒充总数。
      - paragraph: 学生支持域
      - heading "教学与实践支持" [level=2]
      - button "通知管理":
        - img
        - text: 通知管理
      - button "勤工岗位":
        - img
        - text: 勤工岗位
      - button "操作日志":
        - img
        - text: 操作日志
- button "打开 Agent 助手，拖动可调整位置":
  - img
  - text: Agent
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
  65 |   test(`${kind} grading and result retain their own labels`, async ({ page }) => {
  66 |     await fixture(page, 'teacher')
  67 |     await page.goto(kind === 'HOMEWORK' ? '/homework/1/submissions' : '/exams/1/grading')
  68 |     await expect(page.locator('.exam-grading .card-header')).toContainText(`${label}批改：基础练习`)
  69 |     await page.evaluate(() => localStorage.setItem('roleCode', 'student'))
  70 |     await page.goto(`/exam-result/1${kind === 'HOMEWORK' ? '?assessmentType=HOMEWORK' : ''}`)
> 71 |     await expect(page.locator('.exam-result .card-header')).toContainText(`${label}结果`)
     |                                                             ^ Error: expect(locator).toContainText(expected) failed
  72 |     await expect(page.locator('.el-descriptions')).toContainText(`${label}名称`)
  73 |   })
  74 | }
  75 | 
```