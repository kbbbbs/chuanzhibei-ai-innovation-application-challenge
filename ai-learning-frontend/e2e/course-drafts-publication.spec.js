import { test, expect } from '@playwright/test'

const completedCourse = { id: 41, title: '人工智能基础', teacherId: 7, categoryId: 1, status: 2, setupStage: 'COMPLETED', workflowVersion: 3 }

async function fixture(page, { role = 'teacher', userId = '7', classes = null, publishFailure = false } = {}) {
  await page.addInitScript(({ role, userId }) => {
    localStorage.setItem('token', 'course-publication-fixture')
    localStorage.setItem('userId', userId)
    localStorage.setItem('username', '流程教师')
    localStorage.setItem('roleCode', role)
  }, { role, userId })
  const calls = []
  let published = false
  let attempts = 0
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/backend/, '')
    const body = request.postData() ? request.postDataJSON() : null
    calls.push({ path, method: request.method(), query: Object.fromEntries(url.searchParams), body })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/categories') return ok([{ id: 1, name: '计算机' }])
    if (path === '/courses') return ok({ records: url.searchParams.get('box') === 'drafts'
      ? [{ ...completedCourse, title: '待创建课程', setupStage: 'CONTENT', updatedAt: '2026-11-17T11:00:00' }]
      : [{ ...completedCourse, status: published ? 1 : 2 }, { ...completedCourse, id: 42, title: '已发布课程', status: 1 }], total: 2 })
    if (path === '/courses/41') return ok(completedCourse)
    if (path === '/courses/41/publication' && request.method() === 'GET') return ok({
      status: published ? 1 : 2, workflowVersion: 3, classIds: published ? [11] : [], classes: classes ?? [
        { id: 11, className: '人工智能一班', semester: '2026秋季', studentCount: 2, status: 1, courseOpen: false },
        { id: 12, className: '人工智能二班', semester: '2026秋季', studentCount: 0, status: 1, courseOpen: false },
      ],
    })
    if (path === '/courses/41/publish') {
      attempts++
      if (publishFailure && attempts === 1) return route.fulfill({ status: 409, json: { code: '409', msg: '课程已变更，请重新加载' } })
      published = true
      return ok({ ...completedCourse, status: 1 })
    }
    if (path === '/courses/41/publication' && request.method() === 'PUT') return ok({ ...completedCourse, status: 1 })
    if (path === '/teaching-classes') return ok({ records: [{ id: 11, courseId: 41, className: '人工智能一班', semester: '2026秋季', status: 1, courseOpen: true }], total: 1 })
    if (path === '/class-students/class/11') return ok({ records: [{ id: 91, studentId: 8, studentName: '测试学生', status: 1 }], total: 1 })
    return ok([])
  })
  return calls
}

test('completed courses and drafts expose distinct actions and preserve list filters with distinct basic editing entries', async ({ page }) => {
  const calls = await fixture(page)
  await page.goto('/courses?title=人工智能&pageNum=2&pageSize=5')
  await expect(page.getByRole('button', { name: '草稿箱', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '继续准备', exact: true })).toHaveCount(0)
  await expect.poll(() => calls.find(call => call.path === '/courses')?.query).toMatchObject({ box: 'completed', pageNum: '2', title: '人工智能' })
  const courseRow = page.getByRole('row').filter({ hasText: '人工智能基础' })
  await expect(courseRow.getByRole('button', { name: '课程详情', exact: true })).toBeVisible()
  // A published course keeps a way to change its classes, so an emptied class list is never a dead end.
  await expect(page.getByRole('button', { name: '发布范围', exact: true }).first()).toBeEnabled()
  await expect(courseRow.getByRole('button', { name: '编辑信息', exact: true })).toHaveCount(0)
  await page.goto('/courses/drafts')
  await expect(page.getByRole('heading', { name: '草稿箱', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '继续编辑', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '编辑信息', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '课程详情', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '发布', exact: true })).toHaveCount(0)
})

test('publishing selects teaching classes and accepts an empty class', async ({ page }) => {
  const calls = await fixture(page)
  await page.goto('/courses')
  await page.getByRole('button', { name: '发布', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '发布课程', exact: true })
  await expect(dialog.getByRole('button', { name: '确认发布', exact: true })).toBeDisabled()
  await dialog.getByRole('row').filter({ hasText: '人工智能二班' }).locator('.el-checkbox').click()
  await dialog.getByRole('button', { name: '确认发布', exact: true }).click()
  await expect.poll(() => calls.find(call => call.path.endsWith('/publish'))?.body).toEqual({ classIds: [12], workflowVersion: 3 })
  await expect(page.getByRole('button', { name: '发布范围', exact: true })).toHaveCount(2)
})

test('a published course reopens its class selection instead of being locked', async ({ page }) => {
  await fixture(page)
  await page.goto('/courses')
  await page.getByRole('button', { name: '发布范围', exact: true }).first().click()
  const dialog = page.getByRole('dialog', { name: '调整发布范围', exact: true })
  await expect(dialog).toContainText('课程已发布')
  await expect(dialog.getByRole('button', { name: '保存发布范围', exact: true })).toBeVisible()
})

test('no teaching classes explains how to proceed and cannot publish', async ({ page }) => {
  await fixture(page, { classes: [] })
  await page.goto('/courses')
  await page.getByRole('button', { name: '发布', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '发布课程', exact: true })
  await expect(dialog.getByText('教学班由管理员创建，请联系管理员为本课程开设教学班后再发布')).toBeVisible()
  await expect(dialog.getByRole('button', { name: '确认发布', exact: true })).toBeDisabled()
})

test('a stale publication preserves selection and requires refreshing the preview', async ({ page }) => {
  const calls = await fixture(page, { publishFailure: true })
  await page.goto('/courses')
  await page.getByRole('button', { name: '发布', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '发布课程', exact: true })
  const checkbox = dialog.getByRole('row').filter({ hasText: '人工智能一班' }).getByRole('checkbox')
  await dialog.getByRole('row').filter({ hasText: '人工智能一班' }).locator('.el-checkbox').click()
  await dialog.getByRole('button', { name: '确认发布', exact: true }).click()
  await expect(dialog.getByText(/课程已变更/)).toBeVisible()
  await expect(checkbox).toBeChecked()
  await expect(dialog.getByRole('button', { name: '确认发布', exact: true })).toBeDisabled()
  await dialog.getByRole('button', { name: '重新加载', exact: true }).click()
  await expect(checkbox).toBeChecked()
  await dialog.getByRole('button', { name: '确认发布', exact: true }).click()
  await expect.poll(() => calls.filter(call => call.path.endsWith('/publish')).length).toBe(2)
})

test('administrators maintain basic records and class access without opening course content or creation', async ({ page }) => {
  const calls = await fixture(page, { role: 'admin' })
  await page.goto('/courses')
  for (const name of ['新建课程', '草稿箱', '课程详情', '发布']) await expect(page.getByRole('button', { name, exact: true })).toHaveCount(0)
  await page.getByRole('row').filter({ hasText: '人工智能基础' }).getByRole('button', { name: '调整教学班', exact: true }).click()
  await page.getByRole('dialog').getByRole('row').filter({ hasText: '人工智能二班' }).locator('.el-checkbox').click()
  await page.getByRole('button', { name: '保存开放范围', exact: true }).click()
  await expect.poll(() => calls.find(call => call.path === '/courses/41/publication' && call.method === 'PUT')?.body?.classIds).toEqual([12])
  await page.goto('/courses/41')
  await expect(page).toHaveURL(/\/courses$/)
  await page.goto('/courses/create')
  await expect(page).toHaveURL(/\/courses$/)
  await page.goto('/course-learn/41?classId=11')
  await expect(page).toHaveURL(/\/courses$/)
})

test('teachers can read teaching class membership while only administrators can maintain it', async ({ page }) => {
  await fixture(page)
  await page.goto('/teaching-classes')
  await expect(page.getByRole('button', { name: '创建教学班', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '编辑', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '查看学生', exact: true }).click()
  await expect(page.getByRole('dialog').getByText('测试学生')).toBeVisible()
  await expect(page.getByRole('button', { name: '添加学生', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '移除', exact: true })).toHaveCount(0)
})

test('student learning entries use the server-authorized classes and keep the course and class identifiers', async ({ page }) => {
  const calls = await fixture(page, { role: 'student' })
  await page.route('**/backend/teaching-classes/my?**', route => route.fulfill({ json: { code: '200', data: {
    records: [{ id: 11, courseId: 41, className: '已开放教学班', semester: '2026秋季', status: 1, courseOpen: true }], total: 1,
  } } }))
  await page.goto('/my-classes')
  await expect(page.getByRole('heading', { name: '人工智能基础', exact: true })).toBeVisible()
  expect(calls.some(call => call.path.startsWith('/class-students/student/'))).toBe(false)
  await expect(page.getByText('部分班级未加载', { exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '继续学习', exact: true }).click()
  await expect(page).toHaveURL(/\/course-learn\/41\?classId=11/)
})

test('administrator creates a teaching class for a draft course using its teacher instead of the administrator', async ({ page }) => {
  const calls = await fixture(page, { role: 'admin', userId: '1' })
  await page.route('**/backend/courses?**', route => {
    const includeDrafts = new URL(route.request().url()).searchParams.get('box') === 'all'
    return route.fulfill({ json: { code: '200', data: {
      records: includeDrafts ? [{ id: 53, title: '草稿人工智能', teacherId: 2, setupStage: 'MATERIALS', status: 2 }] : [], total: includeDrafts ? 1 : 0,
    } } })
  })
  await page.goto('/teaching-classes')
  await page.getByRole('button', { name: '创建教学班', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '创建教学班', exact: true })
  await dialog.locator('.el-form-item').filter({ hasText: '课程' }).locator('.el-select__wrapper').click()
  await page.getByRole('option', { name: '草稿人工智能', exact: true }).click()
  await dialog.locator('.el-form-item').filter({ hasText: '班级名称' }).getByRole('textbox').fill('人工智能测试班')
  await dialog.locator('.el-form-item').filter({ hasText: '学期' }).getByRole('textbox').fill('2026秋季')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect.poll(() => calls.find(call => call.path === '/teaching-classes' && call.method === 'POST')?.body).toMatchObject({ courseId: 53, teacherId: 2, className: '人工智能测试班', courseOpen: false })
})

test('an unfinished new course kept only in this browser is announced in the draft box', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('eduloop:course-create:7', JSON.stringify({ form: { title: '只填了名字的课程' }, creationKey: 'k' })))
  await fixture(page)
  await page.goto('/courses/drafts')
  const notice = page.getByTestId('local-course-draft')
  await expect(notice).toContainText('只填了名字的课程')
  await notice.getByRole('button', { name: '继续填写' }).click()
  await expect(page).toHaveURL(/\/courses\/create$/)
  await expect(page.getByPlaceholder('请输入课程名称')).toHaveValue('只填了名字的课程')
})
