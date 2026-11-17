import { test, expect } from '@playwright/test'

const course = { id: 41, title: '课程流程测试', categoryId: 1, teacherId: 7, difficulty: 'beginner', status: 2, description: '可恢复课程草稿' }
async function fixture(page, { owner = 7, role = 'teacher', materialFiles = [], setupStage = 'COMPLETED' } = {}) {
  const calls = []
  let workflow = { ...course, teacherId: owner, setupStage, contentMode: null, preparationId: null, workflowVersion: 1 }
  await page.addInitScript(({ role }) => {
    localStorage.setItem('token', 'course-ui-fixture')
    localStorage.setItem('userId', '7')
    localStorage.setItem('roleCode', role)
    localStorage.setItem('username', '流程教师')
  }, { role })
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    calls.push({ path, method: request.method(), data: request.postData() })
    let data = []
    if (path === '/categories') data = [{ id: 1, name: '计算机' }]
    if (path === '/courses' && request.method() === 'POST') { workflow.setupStage = 'MATERIALS'; data = { id: 41, courseId: 41, knowledgeBaseId: 'course-base' } }
    else if (path === '/courses' || path === '/courses/my') data = { records: [workflow], total: 1 }
    if (path === '/courses/41' || path === '/courses/41/setup') {
      if (request.method() === 'PUT') workflow = { ...workflow, ...request.postDataJSON(), workflowVersion: workflow.workflowVersion + 1 }
      data = workflow
    }
    if (path === '/courses/41/complete') { workflow.setupStage = 'COMPLETED'; data = workflow }
    if (path === '/api/rag/courses/41/knowledge-base') data = { id: 'course-base', name: course.title, owner_id: owner, course_id: 41 }
    if (path === '/api/rag/knowledge-bases') data = [{ id: 'course-base', name: course.title, owner_id: owner, course_id: 41 }]
    if (path === '/api/rag/legacy-knowledge-bases') data = [{ id: 'old-base', name: '旧教材库', documentCount: 2, suggestedCourseId: 41 }]
    if (path === '/api/rag/legacy-knowledge-bases/old-base/preview') data = { documents: [{ id: 'old-doc', file_name: '原始讲义.pdf', document_version: 2 }], existingGrants: [], copiesRequired: 0 }
    if (path === '/api/rag/legacy-knowledge-bases/old-base/rollback') data = { restored: true }
    if (path === '/api/rag/legacy-knowledge-bases/old-base/assign') data = { courseId: 41 }
    if (path === '/api/rag/knowledge-bases/course-base/documents') data = materialFiles
    if (path === '/api/rag/knowledge-bases/course-base') data = { id: 'course-base', course_id: 41 }
    if (path === '/courses/41/import-materials') data = { courseId: 41, chaptersCreated: 1, lessonsCreated: 2, warnings: [] }
    if (path === '/api/preparations/11') data = { id: 11, title: '旧备课草稿' }
    if (path === '/api/preparations/11/bind-course') data = { id: 11, targetCourseId: 41 }
    if (path === '/api/preparations') data = []
    await route.fulfill({ json: { code: '200', data } })
  })
  return calls
}

test('create saves a draft and resumes its materials step after refresh', async ({ page }) => {
  const calls = await fixture(page)
  await page.goto('/courses/create')
  await page.getByPlaceholder('请输入课程名称').fill(course.title)
  await page.locator('.el-form-item').filter({ hasText: '课程分类' }).locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: '计算机' }).click()
  await page.getByRole('button', { name: '下一步' }).click()
  await expect(page).toHaveURL(/courses\/41\?setup=materials/)
  const create = calls.find(call => call.path === '/courses' && call.method === 'POST')
  expect(JSON.parse(create.data)).toMatchObject({ status: 2, publishNotice: false })
  expect(JSON.parse(create.data).creationKey).toBeTruthy()
  await page.reload()
  await expect(page.getByRole('button', { name: '下一步，准备课程内容' })).toBeVisible()
  await page.getByRole('button', { name: '下一步，准备课程内容' }).click()
  await expect(page.getByRole('button', { name: '取消生成', exact: true })).toBeVisible()
  await page.screenshot({ path: 'D:/ai-learning-github-latest/.codex_tmp/course-workflow/content-choices.png', fullPage: true })
  await page.getByRole('button', { name: '取消生成', exact: true }).click()
  await page.getByRole('button', { name: '完成创建', exact: true }).click()
  await expect(page).toHaveURL(/\/courses$/)
  expect(calls.filter(call => call.path === '/courses' && call.method === 'POST')).toHaveLength(1)
})

test('course owner accesses chat, search and graph under course materials, including old links', async ({ page }) => {
  await fixture(page)
  await page.goto('/courses/41?tab=chat')
  const workspace = page.getByTestId('course-workspace-tabs')
  const materials = page.getByTestId('course-material-tabs')
  await expect(workspace.getByRole('tab')).toHaveText(['课程内容', '课程资料', '智能问答'])
  await expect(workspace.getByRole('tab', { name: '智能问答' })).toHaveAttribute('aria-selected', 'true')
  await workspace.getByRole('tab', { name: '课程资料' }).click()
  await expect(materials.getByRole('tab')).toHaveText(['资料文件', '检索与验证', '知识图谱'])
  await materials.getByRole('tab', { name: '检索与验证' }).click()
  await expect(page).toHaveURL(/tab=materials&material=search$/)
  await page.reload()
  await expect(materials.getByRole('tab', { name: '检索与验证' })).toHaveAttribute('aria-selected', 'true')
  await materials.getByRole('tab', { name: '知识图谱' }).click()
  await expect(materials.getByRole('tab', { name: '知识图谱' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('button', { name: '课程设置与发布' })).toHaveCount(0)
})

test('another teacher cannot see course management tabs', async ({ page }) => {
  await fixture(page, { owner: 8 })
  await page.goto('/courses/41?tab=materials')
  await expect(page.getByRole('heading', { name: '章节与课时' })).toBeVisible()
  await expect(page.getByRole('tab', { name: '课程资料', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '新增章节' })).toHaveCount(0)
})

test('course material tabs have comfortable target widths and visible gaps', async ({ page }) => {
  await fixture(page)
  await page.goto('/courses/41?tab=materials')
  const tabs = page.getByTestId('course-material-tabs').getByRole('tab')
  await expect(tabs).toHaveCount(3)
  const boxes = await Promise.all([0, 1, 2].map(index => tabs.nth(index).boundingBox()))
  for (const box of boxes) {
    expect(box.width).toBeGreaterThanOrEqual(128)
    expect(box.height).toBeGreaterThanOrEqual(44)
  }
  expect(boxes[1].x - boxes[0].x - boxes[0].width).toBeGreaterThanOrEqual(8)
  expect(boxes[2].x - boxes[1].x - boxes[1].width).toBeGreaterThanOrEqual(8)
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(tabs.nth(2)).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('legacy materials can be assigned to a course from their old entry', async ({ page }) => {
  const calls = await fixture(page)
  await page.goto('/teacher-knowledge-base')
  await expect(page.getByRole('heading', { name: '资料归属整理' })).toBeVisible()
  await expect(page.getByText('旧教材库')).toBeVisible()
  await page.getByRole('button', { name: '确认归属', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认归属', exact: true }).click()
  await expect(page.getByRole('button', { name: '撤销刚才归属' })).toBeVisible()
  await page.getByRole('button', { name: '进入课程', exact: true }).click()
  await expect(page).toHaveURL(/courses\/41\?tab=materials/)
  expect(calls.some(call => call.path.endsWith('/old-base/assign') && call.method === 'POST')).toBe(true)
})


test('entering content uses existing uploaded files without uploading or creating another course', async ({ page }) => {
 const calls=await fixture(page,{setupStage:'CONTENT', materialFiles:[{id:'file-a', file_name:'lesson.pdf',page_count:2}]})
 await page.goto('/courses/41?setup=content')
 await expect(page.getByRole('button',{name:'取消生成',exact:true})).toBeVisible()
 await expect(page.locator('.content-choices')).toHaveCount(0)
 expect(calls.some(c=>c.path==='/courses'&&c.method==='POST')).toBe(false)
 expect(calls.some(c=>c.path.endsWith('/sources')&&c.method==='POST')).toBe(false)
})

test('student course detail does not expose teacher tabs', async ({ page }) => {
  await fixture(page, { role: 'student' })
  await page.goto('/courses/41?tab=preparation')
  await expect(page.getByRole('heading', { name: '章节与课时' })).toBeVisible()
  await expect(page.getByRole('tab', { name: 'AI 备课', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '课程设置与发布' })).toHaveCount(0)
})

test('old owned knowledge link lands in its assigned course', async ({ page }) => {
  await fixture(page)
  await page.goto('/teacher-knowledge-base?kbId=course-base')
  await expect(page).toHaveURL(/courses\/41\?tab=materials/)
})

test('a legacy preparation draft binds to the chosen course before continuing', async ({ page }) => {
  const calls = await fixture(page, { setupStage: 'CONTENT' })
  await page.goto('/resource-gen?projectId=11')
  await page.locator('.el-select__wrapper').last().click()
  await page.getByRole('option', { name: course.title }).click()
  await page.getByRole('button', { name: '关联草稿并继续', exact: true }).click()
  await expect(page).toHaveURL(/courses\/41\?setup=content/)
  expect(calls.find(call => call.path.endsWith('/11/bind-course'))?.data).toBe('{"courseId":41}')
})

test('administrator course detail entry returns to course administration', async ({ page }) => {
  await fixture(page, { role: 'admin', owner: 8 })
  await page.goto('/courses/41?tab=materials')
  await expect(page).toHaveURL(/\/courses$/)
  await expect(page.getByRole('tab', { name: '课程资料', exact: true })).toHaveCount(0)
  await expect(page.getByRole('tab', { name: 'AI 备课', exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '章节与课时' })).toHaveCount(0)
})

test('legacy assignment previews original versions and can be rolled back', async ({ page }) => {
  const calls = await fixture(page)
  await page.goto('/teacher-knowledge-base')
  await page.getByRole('button', { name: '确认归属', exact: true }).click()
  await expect(page.getByRole('dialog').getByText('原始讲义.pdf')).toBeVisible()
  expect(calls.some(call => call.path.endsWith('/preview') && call.method === 'POST')).toBe(true)
  await page.getByRole('dialog').getByRole('button', { name: '确认归属', exact: true }).click()
  await page.getByRole('button', { name: '撤销刚才归属' }).click()
  await expect(page.getByRole('button', { name: '确认归属', exact: true })).toBeVisible()
  expect(calls.some(call => call.path.endsWith('/rollback') && call.method === 'POST')).toBe(true)
})

for (const [setupStage, query] of [['CONTENT', 'setup=content'], ['COMPLETED', 'tab=preparation&projectId=11']]) {
  test(`legacy preparation links open the requested draft for a ${setupStage} course`, async ({ page }) => {
    await fixture(page, { setupStage })
    const courseBinding = setupStage === 'CONTENT' ? { config: { targetCourseId: 41 } } : { targetCourseId: 41 }
    await page.route('**/backend/api/preparations/11', route => route.fulfill({ json: { code: '200', data: {
      id: 11, title: '指定草稿', teacherId: 7, projectStatus: 'DRAFT', currentStage: 'CONFIG', generationStatus: 'IDLE',
      ...courseBinding,
      config: { ...courseBinding.config, title: '课程流程测试', categoryId: 1, audience: '指定草稿对象', difficulty: 'beginner', questionConfig: { countPerChapter: 5, types: [] } },
      sourceFiles: [], knowledgeBaseIds: [], lessons: [], questions: [],
    } } }))
    await page.goto('/resource-gen?projectId=11')
    await expect(page).toHaveURL(`/courses/41?${query}`)
    await expect(page.getByPlaceholder('例如：计算机专业大二本科生')).toHaveValue('指定草稿对象')
  })
}
