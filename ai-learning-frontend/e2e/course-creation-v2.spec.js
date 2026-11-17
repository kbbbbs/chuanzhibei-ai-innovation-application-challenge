import { test, expect } from '@playwright/test'

async function setup(page, { stage = 'CONTENT', mode = null, prepared = false, role = 'teacher', generationStatus = 'SUCCEEDED', rolledBackInitially = false } = {}) {
  let course = { id: 41, teacherId: 7, title: '人工智能基础', categoryId: 1, difficulty: 'beginner', status: 2,
    setupStage: stage, contentMode: mode, preparationId: mode === 'AI' ? 21 : null, workflowVersion: 1 }
  let applied = prepared
  let rolledBack = rolledBackInitially
  let lessonContent = '# 正文'
  const calls = []
  const project = () => ({ id: 21, projectStatus: applied ? 'PUBLISHED' : 'DRAFT', currentStage: applied ? 'PUBLISHED' : generationStatus === 'RUNNING' ? 'LESSON_REVIEW' : 'READY_TO_PUBLISH', generationStatus,
    config: { targetCourseId: 41, title: course.title, categoryId: 1, audience: '本科生', includeQuestions: false },
    outline: { chapters: [{ chapterKey: 'c1', title: '人工智能绪论', lessons: [{ lessonKey: 'l1', title: '定义' }] }] },
    lessons: [{ lessonKey: 'l1', chapterKey: 'c1', title: '定义', contentMd: lessonContent, status: 'GENERATED' }], questions: [] })
  await page.addInitScript(role => {
    localStorage.setItem('token', 'course-v2-fixture')
    localStorage.setItem('userId', '7')
    localStorage.setItem('roleCode', role)
  }, role)
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    const body = request.postData() ? request.postDataJSON() : null
    calls.push({ path, method: request.method(), body })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/courses/41/setup' && request.method() === 'PUT') {
      expect(body.workflowVersion).toBe(course.workflowVersion)
      course = { ...course, ...body, workflowVersion: course.workflowVersion + 1 }
      return ok(course)
    }
    if (path === '/courses/41/complete') {
      expect(body.workflowVersion).toBe(course.workflowVersion)
      course = { ...course, setupStage: 'COMPLETED', workflowVersion: course.workflowVersion + 1 }
      return ok(course)
    }
    if (path === '/courses/41' && request.method() === 'PUT') {
      course = { ...course, ...body, workflowVersion: course.workflowVersion + 1 }
      return ok(course)
    }
    if (path === '/courses/41' || path === '/courses/41/setup') return ok(course)
    if (path === '/courses/41/chapters') return ok(applied && !rolledBack ? [{ id: 12, title: '人工智能绪论' }] : [])
    if (path === '/categories') return ok([{ id: 1, name: '人工智能' }])
    if (path === '/api/rag/courses/41/knowledge-base') return ok({ id: 'kb', course_id: 41, owner_id: 7 })
    if (path.endsWith('/documents')) return ok([{ id: 'file', file_name: '课件.pdf', page_count: 2, parse_status: 'success' }])
    if (path === '/api/preparations') return ok([project()])
    if (path === '/api/preparations/21') return ok(project())
    if (path === '/api/preparations/21/lessons') { lessonContent = body[0].contentMd; return ok(project()) }
    if (path === '/api/preparations/21/ready') return ok(project())
    if (path === '/api/preparations/21/apply-preview') return ok({ courseId: 41, courseVersion: 'v1', applicationId: applied ? 21 : null, rolledBack, existingLessons: [] })
    if (path === '/api/preparations/21/apply') { applied = true; return ok({ applicationId: 21, courseId: 41 }) }
    if (path.endsWith('/rollback')) { rolledBack = true; return ok({ applicationId: 21, rolledBack: true }) }
    if (path === '/courses/41/import-materials') return ok({ courseId: 41, chaptersCreated: 1, lessonsCreated: 2 })
    if (path === '/courses') return ok({ records: course.setupStage === 'COMPLETED' ? [course] : [], total: 1 })
    return ok([])
  })
  return calls
}

test('AI preparation stays inside the third creation step and resumes without completing', async ({ page }) => {
  const calls = await setup(page)
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/courses/41')
  await expect(page.getByTestId('course-creation-steps')).toContainText('课程资料')
  await expect(page.getByRole('button', { name: '返回列表', exact: true })).toHaveCount(0)
  await expect(page.getByRole('tab', { name: '课程资料', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '确认并完成备课', exact: true })).toBeVisible()
  await expect.poll(() => calls.some(call => call.path.endsWith('/setup') && call.body?.preparationId === 21)).toBe(true)
  await page.goto('/courses/drafts')
  await expect(page).toHaveURL(/\/courses\/drafts$/)
  await page.goto('/courses/41')
  await expect(page.getByRole('button', { name: '确认并完成备课', exact: true })).toBeVisible()
  expect(calls.some(call => call.path.endsWith('/complete'))).toBe(false)
  expect(errors).toEqual([])
})

test('applying AI content waits for an explicit complete creation click', async ({ page }) => {
  const calls = await setup(page, { mode: 'AI' })
  await page.goto('/courses/41')
  await page.getByRole('button', { name: '确认并完成备课', exact: true }).click()
  await expect(page.getByRole('button', { name: '完成创建', exact: true })).toBeEnabled()
  expect(calls.some(call => call.path.endsWith('/complete'))).toBe(false)
  await page.reload()
  await expect(page.getByRole('button', { name: '完成创建', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: '完成创建', exact: true }).click()
  await expect(page).toHaveURL(/\/courses$/)
  expect(calls.filter(call => call.path.endsWith('/complete'))).toHaveLength(1)
})

test('previously imported course contents retain explicit completion', async ({ page }) => {
  const calls = await setup(page, { mode: 'IMPORT' })
  await page.goto('/courses/41')
  await expect(page.getByText('课件内容已组织完成', {exact:true})).toBeVisible()
  await expect(page.getByRole('button', {name:'完成创建', exact:true})).toBeEnabled()
  expect(calls.some(call=>call.path.endsWith('/complete'))).toBe(false)
  await page.reload()
  await page.getByRole('button', {name:'完成创建', exact:true}).click()
  await expect(page).toHaveURL(/courses$/)
})

test('completed course has three workspace tabs and basic edit returns to its list without publishing', async ({ page }) => {
  const calls = await setup(page, { stage: 'COMPLETED' })
  await page.goto('/courses/41')
  await expect(page.getByRole('tab')).toHaveCount(3)
  await expect(page.locator('.course-detail').getByRole('button', { name: 'AI 备课', exact: true })).toBeVisible()
  await expect(page.getByRole('tab', { name: '课程设置', exact: true })).toHaveCount(0)
  await page.goto('/courses/create?id=41&returnTo=%2Fcourses%3Fpage%3D2')
  await expect(page.getByRole('radio', { name: '发布', exact: true })).toHaveCount(0)
  await page.getByPlaceholder('请输入课程名称').fill('人工智能基础修订')
  await page.getByRole('button', { name: '保存基本信息', exact: true }).click()
  await expect(page).toHaveURL(/\/courses\?page=2$/)
  const update = calls.find(call => call.path === '/courses/41' && call.method === 'PUT')
  expect(update.body.title).toBe('人工智能基础修订')
  expect(update.body).not.toHaveProperty('status')
  expect(update.body).not.toHaveProperty('publishNotice')
})

test('completed course preparation applies to the same course and refreshes its chapters', async ({ page }) => {
  const calls = await setup(page, { stage: 'COMPLETED', mode: 'MANUAL' })
  await page.goto('/courses/41')
  await page.locator('.course-detail').getByRole('button', { name: 'AI 备课', exact: true }).click()
  await expect(page.getByRole('button', { name: '生成设置', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '生成设置', exact: true }).click()
  await expect(page.getByRole('heading', { name: '题目生成设置' })).toBeVisible()
  await expect(page.getByTestId('generation-options')).toHaveCount(0)
  await page.getByRole('button', { name: '生成完成', exact: true }).click()
  await page.getByRole('button', { name: '预览并应用到课程', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认应用到当前课程', exact: true }).click()
  await page.getByRole('button', { name: '查看课程内容', exact: true }).click()
  await expect(page.getByText('人工智能绪论', { exact: true })).toBeVisible()
  expect(calls.filter(call => call.path.endsWith('/apply'))).toHaveLength(1)
  expect(calls.some(call => call.path.endsWith('/complete') || (call.path.endsWith('/setup') && call.method === 'PUT'))).toBe(false)
})

test('returning to basic information reloads the new workflow version before continuing', async ({ page }) => {
  const calls = await setup(page, { stage: 'MATERIALS' })
  await page.goto('/courses/41')
  await page.getByRole('button', { name: '返回基本信息', exact: true }).click()
  await expect(page.getByTestId('course-creation-steps')).toBeVisible()
  await page.getByPlaceholder('请输入课程名称').fill('人工智能基础更新')
  await page.getByRole('button', { name: '下一步', exact: true }).click()
  await expect(page.getByRole('button', { name: '下一步，准备课程内容', exact: true })).toBeVisible()
  const versions = calls.filter(call => call.path.endsWith('/setup') && call.method === 'PUT').map(call => call.body.workflowVersion)
  expect(versions).toEqual([1, 3])
})

test('creation step numbers and labels navigate and retain basic information edits', async ({ page }) => {
  const calls = await setup(page, { mode: 'AI' })
  await page.goto('/courses/41?setup=content')
  const steps = page.getByTestId('course-creation-steps')
  await steps.getByText('1', { exact: true }).click()
  await expect(page).toHaveURL(/setup=basic$/)
  await page.getByPlaceholder('请输入课程名称').fill('新课程名称')
  await steps.getByText('课程资料', { exact: true }).click()
  await expect(page).toHaveURL(/setup=materials$/)
  await expect(page.getByRole('button', { name: '下一步，准备课程内容', exact: true })).toBeVisible()
  expect(calls.find(call => call.path === '/courses/41' && call.method === 'PUT')?.body.title).toBe('新课程名称')
  await steps.getByText('3', { exact: true }).click()
  await expect(page).toHaveURL(/setup=content$/)
  await expect(page.getByRole('button', { name: '生成设置', exact: true })).toBeVisible()
})

test('skipping preparation opens the manual chapter editor without publishing', async ({ page }) => {
  const calls = await setup(page, { mode: 'AI' })
  await page.goto('/courses/41?setup=content')
  await page.getByRole('button', { name: '跳过备课', exact: true }).click()
  await expect(page).toHaveURL(/\/courses\/41\?tab=content$/)
  await expect(page.getByRole('button', { name: '新增章节', exact: true })).toBeEnabled()
  expect(calls.some(call => call.path.endsWith('/setup') && call.body?.contentMode === 'MANUAL')).toBe(true)
  expect(calls.filter(call => call.path.endsWith('/complete'))).toHaveLength(1)
  expect(calls.some(call => /\/(?:publish|apply|generate-all)$/.test(call.path))).toBe(false)
})

test('a version conflict on completion reloads the progress once and completes without a second click', async ({ page }) => {
  const calls = await setup(page, { mode: 'MANUAL' })
  await page.goto('/courses/41')
  await page.route('**/backend/courses/41/complete', async route => {
    await route.fulfill({ status: 409, json: { code: '409', msg: '课程已修改，请重新加载进度' } })
    await page.unroute('**/backend/courses/41/complete')
  })
  const readsBefore = calls.filter(call => call.path.endsWith('/setup') && call.method === 'GET').length
  await page.getByRole('button', { name: '完成创建', exact: true }).click()
  await expect(page).toHaveURL(/\/courses$/)
  expect(calls.filter(call => call.path.endsWith('/setup') && call.method === 'GET').length).toBeGreaterThan(readsBefore)
  expect(calls.filter(call => call.path.endsWith('/complete'))).toHaveLength(1)
})

test('a completion that keeps failing stays on the creation page with the reason', async ({ page }) => {
  await setup(page, { mode: 'MANUAL' })
  await page.goto('/courses/41')
  await page.route('**/backend/courses/41/complete', route => route.fulfill({ status: 409, json: { code: '409', msg: '课程已修改，请重新加载进度' } }))
  await page.getByRole('button', { name: '完成创建', exact: true }).click()
  await expect(page.getByText('课程已修改，请重新加载进度').first()).toBeVisible()
  await expect(page).toHaveURL(/\/courses\/41$/)
})

test('a running background preparation can save and exit to the draft box', async ({ page }) => {
  const calls = await setup(page, { mode: 'AI', generationStatus: 'RUNNING' })
  await page.goto('/courses/41')
  await expect(page.getByText('可以离开页面，生成进度会保存在草稿中。')).toBeVisible()
  await page.goto('/courses/drafts')
  await expect(page).toHaveURL(/\/courses\/drafts$/)
  expect(calls.some(call => call.path.endsWith('/complete'))).toBe(false)
  expect(calls.some(call => call.path.endsWith('/generate-all'))).toBe(false)
})

test('ready AI content can be read and edited before applying', async ({ page }) => {
  const calls = await setup(page, { mode: 'AI' })
  await page.goto('/courses/41')
  await expect(page.locator('.markdown-editor .cm-content')).toHaveText('# 正文')
  await page.locator('.markdown-editor .cm-content').fill('教师修订正文')
  await page.getByRole('button', { name: '章节骨架', exact: true }).click()
  await page.getByRole('button', { name: '课时 Markdown', exact: true }).click()
  await expect(page.locator('.markdown-editor .cm-content')).toHaveText('教师修订正文')
  expect(calls.find(call => call.path.endsWith('/lessons') && call.method === 'PUT')?.body?.[0]?.contentMd).toBe('教师修订正文')
  expect(calls.some(call => call.path.endsWith('/apply'))).toBe(false)
})

test('a previously rolled back application stays incomplete after refresh', async ({ page }) => {
  await setup(page, { mode: 'AI', prepared: true, rolledBackInitially: true })
  await page.goto('/courses/41')
  await expect(page.getByText('本次应用已回退', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '完成创建', exact: true })).toHaveCount(0)
  await page.reload()
  await expect(page.getByText('本次应用已回退', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '完成创建', exact: true })).toHaveCount(0)
})
