# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: learning-layout-assessment.spec.js >> lesson tools remain readable and usable at 375px
- Location: e2e\learning-layout-assessment.spec.js:34:3

# Error details

```
Error: expect(received).toBeGreaterThanOrEqual(expected)

Expected: >= 280
Received:    85.875
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
            - generic: 算法学习课程
            - button "收起课程目录" [ref=e32] [cursor=pointer]:
              - img [ref=e34]
          - generic [ref=e38]:
            - button "第一章" [expanded] [ref=e39] [cursor=pointer]:
              - strong [ref=e41]: 第一章
              - img [ref=e43]
            - region "第一章" [ref=e45]:
              - generic [ref=e48] [cursor=pointer]: 算法基础
        - generic [ref=e50]:
          - generic [ref=e52]:
            - generic [ref=e53]: 算法基础
            - generic [ref=e54]:
              - button "查看学习地图" [ref=e55] [cursor=pointer]:
                - generic [ref=e56]: 查看学习地图
              - button "标记完成" [ref=e57] [cursor=pointer]:
                - generic [ref=e58]: 标记完成
          - generic [ref=e59]:
            - article [ref=e61]:
              - generic [ref=e62]:
                - generic [ref=e63]:
                  - paragraph [ref=e64]: 互动课本
                  - heading "算法基础" [level=2] [ref=e65]
                  - paragraph [ref=e66]: 课程正文核验
                - generic [ref=e67]:
                  - generic [ref=e69]: 0%
                  - generic [ref=e70]: 预计 3 分钟
              - generic [ref=e71]:
                - complementary [ref=e72]:
                  - heading "本节导航" [level=3] [ref=e73]
                  - link "核心讲解" [ref=e74] [cursor=pointer]:
                    - /url: "#block-0-核心讲解"
                - generic [ref=e76]:
                  - heading "核心讲解" [level=3] [ref=e77]
                  - paragraph [ref=e79]: 课程正文核验
            - generic [ref=e80]:
              - button "上一课时" [disabled] [ref=e81]:
                - generic [ref=e82]: 上一课时
              - button "下一课时" [disabled] [ref=e83]:
                - generic [ref=e84]: 下一课时
        - generic [ref=e86]:
          - generic [ref=e88]:
            - img [ref=e91]
            - img [ref=e95] [cursor=pointer]
            - tablist [ref=e98]:
              - tab "笔记" [selected] [ref=e100]
              - tab "问答" [ref=e101]
          - tabpanel "笔记" [ref=e103]:
            - generic [ref=e104]:
              - generic [ref=e106]:
                - generic [ref=e107]: 学习笔记
                - generic [ref=e108]:
                  - button "下载笔记" [ref=e109] [cursor=pointer]:
                    - img [ref=e111]
                  - button "保存" [ref=e113] [cursor=pointer]:
                    - generic [ref=e114]: 保存
              - generic [ref=e115]:
                - textbox [ref=e118]: 算法基础
                - generic [ref=e119]:
                  - generic [ref=e120]:
                    - generic [ref=e121]:
                      - img [ref=e123]
                      - generic [ref=e126]: AI 笔记助手
                    - generic [ref=e127]:
                      - generic [ref=e129] [cursor=pointer]:
                        - generic:
                          - combobox [ref=e131]
                          - generic [ref=e132]: 课程知识库
                        - img [ref=e135]
                      - button "新对话" [ref=e137] [cursor=pointer]:
                        - img [ref=e139]
                  - generic [ref=e141]:
                    - button "总结" [ref=e142] [cursor=pointer]:
                      - img [ref=e144]
                      - generic [ref=e146]: 总结
                    - button "解释" [ref=e147] [cursor=pointer]:
                      - img [ref=e149]
                      - generic [ref=e151]: 解释
                    - button "练习题" [ref=e152] [cursor=pointer]:
                      - img [ref=e154]
                      - generic [ref=e156]: 练习题
                - generic [ref=e157]:
                  - generic [ref=e159]:
                    - generic [ref=e160]:
                      - button "加粗" [ref=e161] [cursor=pointer]:
                        - img [ref=e162]
                      - button "下划线" [ref=e164] [cursor=pointer]:
                        - img [ref=e165]
                      - button "斜体" [ref=e167] [cursor=pointer]:
                        - img [ref=e168]
                      - button "删除线" [ref=e170] [cursor=pointer]:
                        - img [ref=e171]
                      - button "标题" [ref=e175] [cursor=pointer]:
                        - img [ref=e176]
                      - button "下标" [ref=e177] [cursor=pointer]:
                        - img [ref=e178]
                      - button "上标" [ref=e182] [cursor=pointer]:
                        - img [ref=e183]
                      - button "引用" [ref=e187] [cursor=pointer]:
                        - img [ref=e188]
                      - button "无序列表" [ref=e191] [cursor=pointer]:
                        - img [ref=e192]
                      - button "有序列表" [ref=e193] [cursor=pointer]:
                        - img [ref=e194]
                      - button "任务列表" [ref=e197] [cursor=pointer]:
                        - img [ref=e198]
                      - button "行内代码" [ref=e202] [cursor=pointer]:
                        - img [ref=e203]
                      - button "块级代码" [ref=e206] [cursor=pointer]:
                        - img [ref=e207]
                      - button "链接" [ref=e211] [cursor=pointer]:
                        - img [ref=e212]
                      - button "图片" [ref=e215] [cursor=pointer]:
                        - img [ref=e216]
                      - button "表格" [ref=e220] [cursor=pointer]:
                        - img [ref=e221]
                      - button "mermaid图" [ref=e223] [cursor=pointer]:
                        - img [ref=e224]
                      - button "katex公式" [ref=e227] [cursor=pointer]:
                        - img [ref=e228]
                      - button "后退" [ref=e232] [cursor=pointer]:
                        - img [ref=e233]
                      - button "前进" [ref=e236] [cursor=pointer]:
                        - img [ref=e237]
                      - button "保存" [ref=e240] [cursor=pointer]:
                        - img [ref=e241]
                    - generic [ref=e245]:
                      - button "美化" [ref=e246] [cursor=pointer]:
                        - img [ref=e247]
                      - button "浏览器全屏" [ref=e251] [cursor=pointer]:
                        - img [ref=e252]
                      - button "屏幕全屏" [ref=e257] [cursor=pointer]:
                        - img [ref=e258]
                      - button "预览" [ref=e267] [cursor=pointer]:
                        - img [ref=e268]
                  - textbox [ref=e277]
                  - generic [ref=e282]:
                    - generic [ref=e283]: "字数:"
                    - generic [ref=e284]: "0"
  - button "打开 Agent 助手，拖动可调整位置" [ref=e285] [cursor=pointer]:
    - img [ref=e287]
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
> 50 |     expect(sizes.note.width).toBeGreaterThanOrEqual(280)
     |                              ^ Error: expect(received).toBeGreaterThanOrEqual(expected)
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
  71 |     await expect(page.locator('.exam-result .card-header')).toContainText(`${label}结果`)
  72 |     await expect(page.locator('.el-descriptions')).toContainText(`${label}名称`)
  73 |   })
  74 | }
  75 | 
```