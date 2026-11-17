import { test, expect } from '@playwright/test'

async function fixture(page, { role = 'teacher', failSave = false, failLessonSave = false, wrappedMarkdown = false, emptyChapters = false } = {}) {
  let course = { id: 41, title: '人工智能基础', categoryId: 1, teacherId: 7, difficulty: 'beginner', description: '课程简介', coverUrl: '', status: 1, setupStage: 'COMPLETED' }
  const calls = []
  const lessons = [
    { id: 111, chapterId: 11, title: '认识人工智能', resourceType: 'markdown', duration: 1800, content: '## 学习目标\n认识智能系统的基本概念。' },
    { id: 112, chapterId: 11, title: '机器学习基础', resourceType: 'markdown', duration: 2700, content: '## 核心讲解\n从数据中发现规律。' },
  ]
  if (wrappedMarkdown) lessons[0].content = JSON.stringify({ schemaVersion: 1, summary: '原摘要', blocks: [{ type: 'markdown', content: '# 原正文' }, { type: 'resource', title: '补充课件', url: '/example.pdf' }] })
  await page.addInitScript(role => {
    localStorage.setItem('token', 'synthetic-course-layout')
    localStorage.setItem('userId', role === 'student' ? '9' : '7')
    localStorage.setItem('roleCode', role)
  }, role)
  await page.route('**/backend/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    const method = request.method(), body = request.postData() ? request.postDataJSON() : null
    calls.push({ path, method, body })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/courses/41') {
      if (method === 'PUT') {
        if (failSave) return route.fulfill({ status: 409, json: { code: '409', msg: '课程已被修改，请重试' } })
        course = { ...course, ...body }
      }
      return ok(course)
    }
    if (path === '/categories') return ok([{ id: 1, name: '计算机科学' }])
    if (path === '/courses/41/chapters') return ok(emptyChapters ? [] : [{ id: 11, courseId: 41, title: '走近人工智能' }])
    if (path === '/chapters/11/lessons') return ok(lessons)
    if (path.startsWith('/lessons/') && method === 'PUT') {
      if (failLessonSave) return route.fulfill({ status: 500, json: { code: '500', msg: '保存暂时失败' } })
      const lesson = lessons.find(item => item.id === Number(path.split('/').pop()))
      Object.assign(lesson, body)
      return ok(lesson)
    }
    if (path === '/api/rag/courses/41/knowledge-base') return ok({ id: 'course-kb', owner_id: 7, name: course.title })
    return ok([])
  })
  await page.goto('/courses/41')
  return calls
}

test('course title opens the existing basic form and saves without leaving the workspace', async ({ page }) => {
  const calls = await fixture(page)
  await page.getByRole('button', { name: '人工智能基础', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '编辑基本信息' })
  await expect(dialog.getByPlaceholder('请输入课程名称')).toHaveValue('人工智能基础')
  await expect(dialog.getByRole('button', { name: '保存基本信息' })).toBeEnabled()
  await expect(dialog.locator('.el-loading-mask')).toHaveCount(0)
  await page.screenshot({ path: test.info().outputPath('course-title-editor.png'), fullPage: true })
  await dialog.getByPlaceholder('请输入课程名称').fill('人工智能基础（修订）')
  await dialog.getByRole('button', { name: '保存基本信息' }).click()
  await expect(dialog).not.toBeVisible()
  await expect(page.getByRole('heading', { name: '人工智能基础（修订）', exact: true })).toBeVisible()
  await expect(page).toHaveURL(/\/courses\/41$/)
  const update = calls.find(call => call.method === 'PUT' && call.path === '/courses/41')
  expect(update.body).toMatchObject({ title: '人工智能基础（修订）', categoryId: 1, teacherId: 7 })
  expect(update.body).not.toHaveProperty('status')
  expect(calls.filter(call => call.method === 'POST')).toHaveLength(0)
})

test('course content uses a directory and preview while keeping chapter and lesson operations', async ({ page }) => {
  await fixture(page)
  const directory = page.getByRole('navigation', { name: '章节课时目录' })
  await expect(directory).toBeVisible()
  await directory.getByRole('button', { name: '机器学习基础', exact: true }).click()
  await expect(page.getByTestId('course-lesson-preview')).toContainText('从数据中发现规律。')
  await page.screenshot({ path: test.info().outputPath('course-layout-desktop.png'), fullPage: true })
  await page.getByRole('button', { name: '编辑课时', exact: true }).click()
  await expect(page.getByTestId('inline-lesson-editor')).toBeVisible()
  await expect(page.getByRole('dialog', { name: '编辑课时' })).toHaveCount(0)
  await page.getByRole('button', { name: '取消编辑', exact: true }).click()
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(directory).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: test.info().outputPath('course-layout-mobile.png'), fullPage: true })
})

test('markdown saves inline, preserves duration and places directory actions beside lessons', async ({ page }) => {
  const calls = await fixture(page)
  const directory = page.getByRole('navigation', { name: '章节课时目录' })
  await expect(directory.getByRole('button', { name: '删除课时：认识人工智能' })).toBeVisible()
  await expect(directory.getByRole('button', { name: '新增章节', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '编辑课时', exact: true }).click()
  await page.locator('[data-testid="inline-lesson-editor"] .cm-content').fill('## 新的正文\n教师已修订。')
  await expect(page.getByText('时长(秒)', { exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '保存课时', exact: true }).click()
  await expect(page.getByTestId('inline-lesson-editor')).toHaveCount(0)
  await expect(page.getByTestId('course-lesson-preview')).toContainText('教师已修订。')
  const update = calls.find(call => call.path === '/lessons/111' && call.method === 'PUT')
  expect(update.body.duration).toBe(1800)
  expect(JSON.parse(update.body.content).blocks[0].content).toContain('教师已修订。')
})

test('a failed inline save keeps the editor and typed content', async ({ page }) => {
  await fixture(page, { failLessonSave: true })
  await page.getByRole('button', { name: '编辑课时', exact: true }).click()
  const editor = page.getByTestId('inline-lesson-editor')
  await editor.locator('.cm-content').fill('尚未保存的文字')
  await page.getByRole('button', { name: '保存课时', exact: true }).click()
  await expect(editor).toBeVisible()
  await expect(editor.locator('.cm-content')).toHaveText('尚未保存的文字')
})

test('inline editing preserves other existing content blocks', async ({ page }) => {
  const calls = await fixture(page, { wrappedMarkdown: true })
  await page.getByRole('button', { name: '编辑课时', exact: true }).click()
  await page.getByTestId('inline-lesson-editor').locator('.cm-content').fill('# 修订正文')
  await page.getByRole('button', { name: '保存课时', exact: true }).click()
  await expect(page.getByTestId('inline-lesson-editor')).toHaveCount(0)
  const payload = JSON.parse(calls.find(call => call.path === '/lessons/111' && call.method === 'PUT').body.content)
  expect(payload.blocks).toEqual([{ type: 'markdown', title: '认识人工智能', content: '# 修订正文' }, { type: 'resource', title: '补充课件', url: '/example.pdf' }])
  expect(payload.summary).toBe('原摘要')
})

test('an empty course can still create its first chapter from the directory', async ({ page }) => {
  await fixture(page, { emptyChapters: true })
  const directory = page.getByRole('navigation', { name: '章节课时目录' })
  await directory.getByRole('button', { name: '新增章节', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '新增章节', exact: true })).toBeVisible()
})

test('switching lessons asks before discarding an unsaved inline edit', async ({ page }) => {
  const calls = await fixture(page)
  await page.getByRole('button', { name: '编辑课时', exact: true }).click()
  await page.getByTestId('inline-lesson-editor').locator('.cm-content').fill('尚未保存的修改')
  await page.getByRole('navigation', { name: '章节课时目录' }).getByRole('button', { name: '机器学习基础', exact: true }).click()
  await page.getByRole('button', { name: '继续编辑', exact: true }).click()
  await expect(page.getByTestId('inline-lesson-editor').locator('.cm-content')).toHaveText('尚未保存的修改')
  await page.getByRole('navigation', { name: '章节课时目录' }).getByRole('button', { name: '机器学习基础', exact: true }).click()
  await page.getByRole('button', { name: '放弃修改', exact: true }).click()
  await expect(page.getByTestId('inline-lesson-editor')).toHaveCount(0)
  await expect(page.getByTestId('course-lesson-preview')).toContainText('从数据中发现规律。')
  expect(calls.filter(call => call.method === 'PUT')).toHaveLength(0)
})

test('failed basic info save retains the form and current course data', async ({ page }) => {
  await fixture(page, { failSave: true })
  await page.getByRole('button', { name: '人工智能基础', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '编辑基本信息' })
  await dialog.getByPlaceholder('请输入课程名称').fill('编辑尚未保存')
  await dialog.getByRole('button', { name: '保存基本信息' }).click()
  await expect(dialog).toBeVisible()
  await expect(dialog.getByPlaceholder('请输入课程名称')).toHaveValue('编辑尚未保存')
  await expect(page.getByRole('heading', { name: '人工智能基础', exact: true })).toBeVisible()
})

test('students see the course heading without the teacher title editor', async ({ page }) => {
  await fixture(page, { role: 'student' })
  await expect(page.getByRole('heading', { name: '人工智能基础', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '人工智能基础', exact: true })).toHaveCount(0)
})

test('editing the title keeps the mounted course conversation and current scope', async ({ page }) => {
  const calls = await fixture(page)
  await page.goto('/courses/41?tab=chat')
  const conversation = page.getByTestId('assistant-conversation')
  await expect(conversation).toBeVisible()
  await conversation.evaluate(element => { element.dataset.layoutSession = 'preserved' })
  await page.getByRole('button', { name: '人工智能基础', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '编辑基本信息' })
  await dialog.getByPlaceholder('请输入课程名称').fill('人工智能基础（更新）')
  await dialog.getByRole('button', { name: '保存基本信息' }).click()
  await expect(dialog).not.toBeVisible()
  await expect(page).toHaveURL(/tab=chat$/)
  await expect(conversation).toHaveAttribute('data-layout-session', 'preserved')
  expect(calls.filter(call => call.method === 'POST')).toHaveLength(0)
})
