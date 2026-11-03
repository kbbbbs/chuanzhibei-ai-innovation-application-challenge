# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: learning-layout-assessment.spec.js >> mobile lesson navigation keeps the first content block nearby
- Location: e2e\learning-layout-assessment.spec.js:82:1

# Error details

```
Error: expect(received).toBeLessThanOrEqual(expected)

Expected: <= 24
Received:    297.046875
```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - generic [ref=e3]:
    - generic [ref=e4]:
      - button "E" [ref=e6] [cursor=pointer]:
        - generic [ref=e7]: E
      - generic [ref=e8]:
        - button [ref=e9] [cursor=pointer]:
          - img [ref=e12]
        - button [ref=e14] [cursor=pointer]:
          - img [ref=e17]
        - button "U" [ref=e22] [cursor=pointer]:
          - generic [ref=e23]: U
    - main [ref=e25]:
      - generic [ref=e27]:
        - generic [ref=e29]:
          - generic [ref=e31]:
            - generic [ref=e32]: 算法学习课程
            - button "收起课程目录" [ref=e33] [cursor=pointer]:
              - img [ref=e35]
          - generic [ref=e39]:
            - button "第一章" [expanded] [ref=e40] [cursor=pointer]:
              - strong [ref=e42]: 第一章
              - img [ref=e44]
            - region "第一章" [ref=e46]:
              - generic [ref=e49] [cursor=pointer]: 算法基础
        - generic [ref=e51]:
          - generic [ref=e53]:
            - generic [ref=e54]: 算法基础
            - generic [ref=e55]:
              - button "查看学习地图" [ref=e56] [cursor=pointer]:
                - generic [ref=e57]: 查看学习地图
              - button "标记完成" [ref=e58] [cursor=pointer]:
                - generic [ref=e59]: 标记完成
          - generic [ref=e60]:
            - article [ref=e62]:
              - generic [ref=e63]:
                - generic [ref=e64]:
                  - paragraph [ref=e65]: 互动课本
                  - heading "算法基础" [level=2] [ref=e66]
                  - paragraph [ref=e67]: 课程正文核验
                - generic [ref=e68]:
                  - generic [ref=e70]: 0%
                  - generic [ref=e71]: 预计 3 分钟
              - generic [ref=e72]:
                - complementary [ref=e73]:
                  - heading "本节导航" [level=3] [ref=e74]
                  - link "核心讲解" [ref=e75] [cursor=pointer]:
                    - /url: "#block-0-核心讲解"
                - generic [ref=e77]:
                  - heading "核心讲解" [level=3] [ref=e78]
                  - paragraph [ref=e80]: 课程正文核验
            - generic [ref=e81]:
              - button "上一课时" [disabled] [ref=e82]:
                - generic [ref=e83]: 上一课时
              - button "下一课时" [disabled] [ref=e84]:
                - generic [ref=e85]: 下一课时
        - generic [ref=e87]:
          - tablist [ref=e91]:
            - tab "笔记" [selected] [ref=e93]
            - tab "问答" [ref=e94]
          - tabpanel "笔记" [ref=e96]:
            - generic [ref=e97]:
              - generic [ref=e99]:
                - generic [ref=e100]: 学习笔记
                - generic [ref=e101]:
                  - button "下载笔记" [ref=e102] [cursor=pointer]:
                    - img [ref=e104]
                  - button "保存" [ref=e106] [cursor=pointer]:
                    - generic [ref=e107]: 保存
              - generic [ref=e108]:
                - textbox [ref=e111]: 算法基础
                - generic [ref=e112]:
                  - generic [ref=e113]:
                    - generic [ref=e114]:
                      - img [ref=e116]
                      - generic [ref=e119]: AI 笔记助手
                    - generic [ref=e120]:
                      - generic [ref=e122] [cursor=pointer]:
                        - generic:
                          - combobox [ref=e124]
                          - generic [ref=e125]: 课程知识库
                        - img [ref=e128]
                      - button "新对话" [ref=e130] [cursor=pointer]:
                        - img [ref=e132]
                  - generic [ref=e134]:
                    - button "总结" [ref=e135] [cursor=pointer]:
                      - img [ref=e137]
                      - generic [ref=e139]: 总结
                    - button "解释" [ref=e140] [cursor=pointer]:
                      - img [ref=e142]
                      - generic [ref=e144]: 解释
                    - button "练习题" [ref=e145] [cursor=pointer]:
                      - img [ref=e147]
                      - generic [ref=e149]: 练习题
                - generic [ref=e150]:
                  - generic [ref=e152]:
                    - generic [ref=e153]:
                      - button "加粗" [ref=e154] [cursor=pointer]:
                        - img [ref=e155]
                      - button "下划线" [ref=e157] [cursor=pointer]:
                        - img [ref=e158]
                      - button "斜体" [ref=e160] [cursor=pointer]:
                        - img [ref=e161]
                      - button "删除线" [ref=e163] [cursor=pointer]:
                        - img [ref=e164]
                      - button "标题" [ref=e168] [cursor=pointer]:
                        - img [ref=e169]
                      - button "下标" [ref=e170] [cursor=pointer]:
                        - img [ref=e171]
                      - button "上标" [ref=e175] [cursor=pointer]:
                        - img [ref=e176]
                      - button "引用" [ref=e180] [cursor=pointer]:
                        - img [ref=e181]
                      - button "无序列表" [ref=e184] [cursor=pointer]:
                        - img [ref=e185]
                      - button "有序列表" [ref=e186] [cursor=pointer]:
                        - img [ref=e187]
                      - button "任务列表" [ref=e190] [cursor=pointer]:
                        - img [ref=e191]
                      - button "行内代码" [ref=e195] [cursor=pointer]:
                        - img [ref=e196]
                      - button "块级代码" [ref=e199] [cursor=pointer]:
                        - img [ref=e200]
                      - button "链接" [ref=e204] [cursor=pointer]:
                        - img [ref=e205]
                      - button "图片" [ref=e208] [cursor=pointer]:
                        - img [ref=e209]
                      - button "表格" [ref=e213] [cursor=pointer]:
                        - img [ref=e214]
                      - button "mermaid图" [ref=e216] [cursor=pointer]:
                        - img [ref=e217]
                      - button "katex公式" [ref=e220] [cursor=pointer]:
                        - img [ref=e221]
                      - button "后退" [ref=e225] [cursor=pointer]:
                        - img [ref=e226]
                      - button "前进" [ref=e229] [cursor=pointer]:
                        - img [ref=e230]
                      - button "保存" [ref=e233] [cursor=pointer]:
                        - img [ref=e234]
                    - generic [ref=e238]:
                      - button "美化" [ref=e239] [cursor=pointer]:
                        - img [ref=e240]
                      - button "浏览器全屏" [ref=e244] [cursor=pointer]:
                        - img [ref=e245]
                      - button "屏幕全屏" [ref=e250] [cursor=pointer]:
                        - img [ref=e251]
                      - button "预览" [ref=e260] [cursor=pointer]:
                        - img [ref=e261]
                  - textbox [ref=e270]
                  - generic [ref=e275]:
                    - generic [ref=e276]: "字数:"
                    - generic [ref=e277]: "0"
  - button "打开 Agent 助手，拖动可调整位置" [ref=e278] [cursor=pointer]:
    - img [ref=e280]
```

# Test source

```ts
  1   | import { test, expect } from '@playwright/test'
  2   | 
  3   | // Controlled API contracts; live browser evidence is captured separately.
  4   | async function fixture(page, role = 'student') {
  5   |   const requests = []
  6   |   await page.addInitScript(role => {
  7   |     localStorage.setItem('token', 'ui-contract')
  8   |     localStorage.setItem('userId', '2')
  9   |     localStorage.setItem('roleCode', role)
  10  |     localStorage.setItem('username', '布局核验')
  11  |     localStorage.removeItem('courseLessonSidebarCollapsed')
  12  |   }, role)
  13  |   await page.route('**/*', async route => {
  14  |     const url = new URL(route.request().url())
  15  |     if (url.port !== '8080') return route.continue()
  16  |     const path = url.pathname.replace(/^\/api/, '')
  17  |     requests.push(path)
  18  |     let data = []
  19  |     if (path === '/courses/1') data = { id: 1, title: '算法学习课程' }
  20  |     if (path === '/courses/1/chapters') data = [{ id: 1, title: '第一章' }]
  21  |     if (path === '/chapters/1/lessons') data = [{ id: 1, title: '算法基础', resourceType: 'text', content: '课程正文核验' }]
  22  |     if (path === '/courses/1/experience') data = { textbook: [] }
  23  |     if (path === '/rag/knowledge-bases') data = [{ id: 'kb', name: '课程知识库' }]
  24  |     if (path === '/rag/sessions') data = { id: 'session' }
  25  |     if (path.includes('/notes')) data = { title: '算法基础', contentMd: '已有笔记' }
  26  |     if (/^\/(homeworks|exams)\/1$/.test(path)) data = { id: 1, name: '基础练习' }
  27  |     if (/^\/(homework-submissions|submissions)\/1\/student$/.test(path)) {
  28  |       data = { id: 1, homeworkId: 1, examId: 1, homeworkName: '基础练习', examName: '基础练习',
  29  |         status: 'GRADED', totalScore: 90, objectiveScore: 50, subjectiveScore: 40, answers: [] }
  30  |     }
  31  |     return route.fulfill({ json: { code: '200', data } })
  32  |   })
  33  |   return requests
  34  | }
  35  | 
  36  | for (const width of [375, 768, 1080, 1440]) {
  37  |   test(`lesson tools remain readable and usable at ${width}px`, async ({ page }) => {
  38  |     await page.setViewportSize({ width, height: 960 })
  39  |     await fixture(page)
  40  |     await page.goto('/course-learn/1?classId=1&lessonId=1')
  41  |     await expect(page.locator('.lesson-main-card')).toContainText('课程正文核验')
  42  |     await expect(page.locator('.note-header')).toBeVisible()
  43  |     const sizes = await page.evaluate(() => {
  44  |       const rect = selector => {
  45  |         const e = document.querySelector(selector), r = e.getBoundingClientRect()
  46  |         return { width: r.width, height: r.height, right: r.right, scroll: e.scrollWidth, client: e.clientWidth }
  47  |       }
  48  |       return { viewport: innerWidth, document: document.documentElement.scrollWidth,
  49  |         main: rect('.lesson-main-card'),
  50  |         note: rect('.note-card'), title: rect('.assistant-title'), select: rect('.kb-select'),
  51  |         assistant: rect('.ai-note-assistant') }
  52  |     })
  53  |     expect(sizes.document).toBeLessThanOrEqual(sizes.viewport + 1)
  54  |     expect(sizes.main.width).toBeGreaterThanOrEqual(280)
  55  |     expect(sizes.note.width).toBeGreaterThanOrEqual(280)
  56  |     expect(sizes.title.height).toBeLessThanOrEqual(30)
  57  |     expect(sizes.select.width).toBeGreaterThanOrEqual(125)
  58  |     expect(sizes.select.right).toBeLessThanOrEqual(sizes.assistant.right + 1)
  59  |     await page.getByRole('tab', { name: '问答', exact: true }).click()
  60  |     await expect(page.locator('.quick-chat-card')).toBeVisible()
  61  |     await page.getByRole('tab', { name: '笔记', exact: true }).click()
  62  |     await page.getByRole('button', { name: '收起课程目录', exact: true }).click()
  63  |     await page.getByRole('button', { name: '展开课程目录', exact: true }).click()
  64  |     await expect(page.locator('.lesson-sidebar-card')).toBeVisible()
  65  |   })
  66  | }
  67  | 
  68  | test('same submission id switches result API when assessment query changes', async ({ page }) => {
  69  |   const requests = await fixture(page)
  70  |   await page.goto('/exam-result/1')
  71  |   await expect(page.locator('.exam-result .card-header')).toContainText('考试结果')
  72  |   await expect.poll(() => requests.includes('/submissions/1/student')).toBe(true)
  73  |   await page.evaluate(() => {
  74  |     history.pushState({}, '', '/exam-result/1?assessmentType=HOMEWORK')
  75  |     window.dispatchEvent(new PopStateEvent('popstate'))
  76  |   })
  77  |   await expect(page.locator('.exam-result .card-header')).toContainText('作业结果')
  78  |   await expect.poll(() => requests.includes('/homework-submissions/1/student')).toBe(true)
  79  |   await expect(page.locator('.el-descriptions')).toContainText('作业名称')
  80  | })
  81  | 
  82  | test('mobile lesson navigation keeps the first content block nearby', async ({ page }) => {
  83  |   await page.setViewportSize({ width: 375, height: 960 })
  84  |   await fixture(page)
  85  |   await page.goto('/course-learn/1?classId=1&lessonId=1')
  86  |   await expect(page.locator('.page-block')).toContainText('课程正文核验')
  87  |   const gap = await page.evaluate(() => document.querySelector('.block-stack').getBoundingClientRect().top -
  88  |     document.querySelector('.page-nav').getBoundingClientRect().bottom)
> 89  |   expect(gap).toBeLessThanOrEqual(24)
      |               ^ Error: expect(received).toBeLessThanOrEqual(expected)
  90  | })
  91  | 
  92  | for (const kind of ['HOMEWORK', 'EXAM']) {
  93  |   const label = kind === 'HOMEWORK' ? '作业' : '考试'
  94  |   test(`${kind} grading retains its own label`, async ({ page }) => {
  95  |     await fixture(page, 'teacher')
  96  |     await page.goto(kind === 'HOMEWORK' ? '/homework/1/submissions' : '/exams/1/grading')
  97  |     await expect(page.locator('.exam-grading .card-header')).toContainText(`${label}批改：基础练习`)
  98  |   })
  99  |   test(`${kind} result retains its own labels`, async ({ page }) => {
  100 |     await fixture(page)
  101 |     await page.goto(`/exam-result/1${kind === 'HOMEWORK' ? '?assessmentType=HOMEWORK' : ''}`)
  102 |     await expect(page.locator('.exam-result .card-header')).toContainText(`${label}结果`)
  103 |     await expect(page.locator('.el-descriptions')).toContainText(`${label}名称`)
  104 |   })
  105 | }
  106 | 
```