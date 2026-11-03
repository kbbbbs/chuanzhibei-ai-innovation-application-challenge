# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: learning-layout-assessment.spec.js >> lesson tools remain readable and usable at 1080px
- Location: e2e\learning-layout-assessment.spec.js:34:3

# Error details

```
Error: expect(received).toBeLessThanOrEqual(expected)

Expected: <= 30
Received:    105
```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - generic [ref=e3]:
    - generic [ref=e4]:
      - button "E EduLoop 智教云环" [ref=e6] [cursor=pointer]:
        - generic [ref=e7]: E
        - generic [ref=e8]: EduLoop 智教云环
      - generic [ref=e9]:
        - button [ref=e10] [cursor=pointer]:
          - img [ref=e13]
        - button [ref=e15] [cursor=pointer]:
          - img [ref=e18]
        - button "U" [ref=e23] [cursor=pointer]:
          - generic [ref=e24]: U
    - main [ref=e26]:
      - generic [ref=e28]:
        - generic [ref=e30]:
          - generic [ref=e32]:
            - generic [ref=e33]: 算法学习课程
            - button "收起课程目录" [ref=e34] [cursor=pointer]:
              - img [ref=e36]
          - generic [ref=e40]:
            - button "第一章" [expanded] [ref=e41] [cursor=pointer]:
              - strong [ref=e43]: 第一章
              - img [ref=e45]
            - region "第一章" [ref=e47]:
              - generic [ref=e50] [cursor=pointer]: 算法基础
        - generic [ref=e52]:
          - generic [ref=e54]:
            - generic [ref=e55]: 算法基础
            - generic [ref=e56]:
              - button "查看学习地图" [ref=e57] [cursor=pointer]:
                - generic [ref=e58]: 查看学习地图
              - button "标记完成" [ref=e59] [cursor=pointer]:
                - generic [ref=e60]: 标记完成
          - generic [ref=e61]:
            - article [ref=e63]:
              - generic [ref=e64]:
                - generic [ref=e65]:
                  - paragraph [ref=e66]: 互动课本
                  - heading "算法基础" [level=2] [ref=e67]
                  - paragraph [ref=e68]: 课程正文核验
                - generic [ref=e69]:
                  - generic [ref=e71]: 0%
                  - generic [ref=e72]: 预计 3 分钟
              - generic [ref=e73]:
                - complementary [ref=e74]:
                  - heading "本节导航" [level=3] [ref=e75]
                  - link "核心讲解" [ref=e76] [cursor=pointer]:
                    - /url: "#block-0-核心讲解"
                - generic [ref=e78]:
                  - heading "核心讲解" [level=3] [ref=e79]
                  - paragraph [ref=e81]: 课程正文核验
            - generic [ref=e82]:
              - button "上一课时" [disabled] [ref=e83]:
                - generic [ref=e84]: 上一课时
              - button "下一课时" [disabled] [ref=e85]:
                - generic [ref=e86]: 下一课时
        - generic [ref=e88]:
          - tablist [ref=e92]:
            - tab "笔记" [selected] [ref=e94]
            - tab "问答" [ref=e95]
          - tabpanel "笔记" [ref=e97]:
            - generic [ref=e98]:
              - generic [ref=e100]:
                - generic [ref=e101]: 学习笔记
                - generic [ref=e102]:
                  - button "下载笔记" [ref=e103] [cursor=pointer]:
                    - img [ref=e105]
                  - button "保存" [ref=e107] [cursor=pointer]:
                    - generic [ref=e108]: 保存
              - generic [ref=e109]:
                - textbox [ref=e112]: 算法基础
                - generic [ref=e113]:
                  - generic [ref=e114]:
                    - generic [ref=e115]:
                      - img [ref=e117]
                      - generic [ref=e120]: AI 笔记助手
                    - generic [ref=e121]:
                      - generic [ref=e123] [cursor=pointer]:
                        - generic:
                          - combobox [ref=e125]
                          - generic [ref=e126]: 课程知识库
                        - img [ref=e129]
                      - button "新对话" [ref=e131] [cursor=pointer]:
                        - img [ref=e133]
                  - generic [ref=e135]:
                    - button "总结" [ref=e136] [cursor=pointer]:
                      - img [ref=e138]
                      - generic [ref=e140]: 总结
                    - button "解释" [ref=e141] [cursor=pointer]:
                      - img [ref=e143]
                      - generic [ref=e145]: 解释
                    - button "练习题" [ref=e146] [cursor=pointer]:
                      - img [ref=e148]
                      - generic [ref=e150]: 练习题
                - generic [ref=e151]:
                  - generic [ref=e153]:
                    - generic [ref=e154]:
                      - button "加粗" [ref=e155] [cursor=pointer]:
                        - img [ref=e156]
                      - button "下划线" [ref=e158] [cursor=pointer]:
                        - img [ref=e159]
                      - button "斜体" [ref=e161] [cursor=pointer]:
                        - img [ref=e162]
                      - button "删除线" [ref=e164] [cursor=pointer]:
                        - img [ref=e165]
                      - button "标题" [ref=e169] [cursor=pointer]:
                        - img [ref=e170]
                      - button "下标" [ref=e171] [cursor=pointer]:
                        - img [ref=e172]
                      - button "上标" [ref=e176] [cursor=pointer]:
                        - img [ref=e177]
                      - button "引用" [ref=e181] [cursor=pointer]:
                        - img [ref=e182]
                      - button "无序列表" [ref=e185] [cursor=pointer]:
                        - img [ref=e186]
                      - button "有序列表" [ref=e187] [cursor=pointer]:
                        - img [ref=e188]
                      - button "任务列表" [ref=e191] [cursor=pointer]:
                        - img [ref=e192]
                      - button "行内代码" [ref=e196] [cursor=pointer]:
                        - img [ref=e197]
                      - button "块级代码" [ref=e200] [cursor=pointer]:
                        - img [ref=e201]
                      - button "链接" [ref=e205] [cursor=pointer]:
                        - img [ref=e206]
                      - button "图片" [ref=e209] [cursor=pointer]:
                        - img [ref=e210]
                      - button "表格" [ref=e214] [cursor=pointer]:
                        - img [ref=e215]
                      - button "mermaid图" [ref=e217] [cursor=pointer]:
                        - img [ref=e218]
                      - button "katex公式" [ref=e221] [cursor=pointer]:
                        - img [ref=e222]
                      - button "后退" [ref=e226] [cursor=pointer]:
                        - img [ref=e227]
                      - button "前进" [ref=e230] [cursor=pointer]:
                        - img [ref=e231]
                      - button "保存" [ref=e234] [cursor=pointer]:
                        - img [ref=e235]
                    - generic [ref=e239]:
                      - button "美化" [ref=e240] [cursor=pointer]:
                        - img [ref=e241]
                      - button "浏览器全屏" [ref=e245] [cursor=pointer]:
                        - img [ref=e246]
                      - button "屏幕全屏" [ref=e251] [cursor=pointer]:
                        - img [ref=e252]
                      - button "预览" [ref=e261] [cursor=pointer]:
                        - img [ref=e262]
                  - textbox [ref=e271]
                  - generic [ref=e276]:
                    - generic [ref=e277]: "字数:"
                    - generic [ref=e278]: "0"
  - button "打开 Agent 助手，拖动可调整位置" [ref=e279] [cursor=pointer]:
    - img [ref=e281]
    - generic [ref=e284]: Agent
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
> 51 |     expect(sizes.title.height).toBeLessThanOrEqual(30)
     |                                ^ Error: expect(received).toBeLessThanOrEqual(expected)
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
  71 |     await expect(page.locator('.exam-result .card-header')).toContainText(`${label}结果`)
  72 |     await expect(page.locator('.el-descriptions')).toContainText(`${label}名称`)
  73 |   })
  74 | }
  75 | 
```