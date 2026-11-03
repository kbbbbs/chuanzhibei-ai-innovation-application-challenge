import { test, expect } from '@playwright/test'

// Controlled API contracts; live browser evidence is captured separately.
async function fixture(page, role = 'student') {
  const requests = []
  await page.addInitScript(role => {
    localStorage.setItem('token', 'ui-contract')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', role)
    localStorage.setItem('username', '布局核验')
    localStorage.removeItem('courseLessonSidebarCollapsed')
  }, role)
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.port !== '8080') return route.continue()
    const path = url.pathname.replace(/^\/api/, '')
    requests.push(path)
    let data = []
    if (path === '/courses/1') data = { id: 1, title: '算法学习课程' }
    if (path === '/courses/1/chapters') data = [{ id: 1, title: '第一章' }]
    if (path === '/chapters/1/lessons') data = [{ id: 1, title: '算法基础', resourceType: 'text', content: '课程正文核验' }]
    if (path === '/courses/1/experience') data = { textbook: [] }
    if (path === '/rag/knowledge-bases') data = [{ id: 'kb', name: '课程知识库' }]
    if (path === '/rag/sessions') data = { id: 'session' }
    if (path.includes('/notes')) data = { title: '算法基础', contentMd: '已有笔记' }
    if (/^\/(homeworks|exams)\/1$/.test(path)) data = { id: 1, name: '基础练习' }
    if (/^\/(homework-submissions|submissions)\/1\/student$/.test(path)) {
      data = { id: 1, homeworkId: 1, examId: 1, homeworkName: '基础练习', examName: '基础练习',
        status: 'GRADED', totalScore: 90, objectiveScore: 50, subjectiveScore: 40, answers: [] }
    }
    return route.fulfill({ json: { code: '200', data } })
  })
  return requests
}

for (const width of [375, 768, 1080, 1440]) {
  test(`lesson tools remain readable and usable at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 960 })
    await fixture(page)
    await page.goto('/course-learn/1?classId=1&lessonId=1')
    await expect(page.locator('.lesson-main-card')).toContainText('课程正文核验')
    await expect(page.locator('.note-header')).toBeVisible()
    const sizes = await page.evaluate(() => {
      const rect = selector => {
        const e = document.querySelector(selector), r = e.getBoundingClientRect()
        return { width: r.width, height: r.height, right: r.right, scroll: e.scrollWidth, client: e.clientWidth }
      }
      return { viewport: innerWidth, document: document.documentElement.scrollWidth,
        main: rect('.lesson-main-card'),
        note: rect('.note-card'), title: rect('.assistant-title'), select: rect('.kb-select'),
        assistant: rect('.ai-note-assistant') }
    })
    expect(sizes.document).toBeLessThanOrEqual(sizes.viewport + 1)
    expect(sizes.main.width).toBeGreaterThanOrEqual(280)
    expect(sizes.note.width).toBeGreaterThanOrEqual(280)
    expect(sizes.title.height).toBeLessThanOrEqual(30)
    expect(sizes.select.width).toBeGreaterThanOrEqual(125)
    expect(sizes.select.right).toBeLessThanOrEqual(sizes.assistant.right + 1)
    await page.getByRole('tab', { name: '问答', exact: true }).click()
    await expect(page.locator('.quick-chat-card')).toBeVisible()
    await page.getByRole('tab', { name: '笔记', exact: true }).click()
    await page.getByRole('button', { name: '收起课程目录', exact: true }).click()
    await page.getByRole('button', { name: '展开课程目录', exact: true }).click()
    await expect(page.locator('.lesson-sidebar-card')).toBeVisible()
  })
}

test('same submission id switches result API when assessment query changes', async ({ page }) => {
  const requests = await fixture(page)
  await page.goto('/exam-result/1')
  await expect(page.locator('.exam-result .card-header')).toContainText('考试结果')
  await expect.poll(() => requests.includes('/submissions/1/student')).toBe(true)
  await page.evaluate(() => {
    history.pushState({}, '', '/exam-result/1?assessmentType=HOMEWORK')
    window.dispatchEvent(new PopStateEvent('popstate'))
  })
  await expect(page.locator('.exam-result .card-header')).toContainText('作业结果')
  await expect.poll(() => requests.includes('/homework-submissions/1/student')).toBe(true)
  await expect(page.locator('.el-descriptions')).toContainText('作业名称')
})

test('mobile lesson navigation keeps the first content block nearby', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 960 })
  await fixture(page)
  await page.goto('/course-learn/1?classId=1&lessonId=1')
  await expect(page.locator('.page-block')).toContainText('课程正文核验')
  const gap = await page.evaluate(() => document.querySelector('.block-stack').getBoundingClientRect().top -
    document.querySelector('.page-nav').getBoundingClientRect().bottom)
  expect(gap).toBeLessThanOrEqual(24)
})

for (const kind of ['HOMEWORK', 'EXAM']) {
  const label = kind === 'HOMEWORK' ? '作业' : '考试'
  test(`${kind} grading retains its own label`, async ({ page }) => {
    await fixture(page, 'teacher')
    await page.goto(kind === 'HOMEWORK' ? '/homework/1/submissions' : '/exams/1/grading')
    await expect(page.locator('.exam-grading .card-header')).toContainText(`${label}批改：基础练习`)
  })
  test(`${kind} result retains its own labels`, async ({ page }) => {
    await fixture(page)
    await page.goto(`/exam-result/1${kind === 'HOMEWORK' ? '?assessmentType=HOMEWORK' : ''}`)
    await expect(page.locator('.exam-result .card-header')).toContainText(`${label}结果`)
    await expect(page.locator('.el-descriptions')).toContainText(`${label}名称`)
  })
}
