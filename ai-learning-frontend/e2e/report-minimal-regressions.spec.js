import { test, expect } from '@playwright/test'

async function mockWorkspace(page, { categoryError } = {}) {
  const writes = []
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-report-regression')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'admin')
    localStorage.setItem('username', 'admin')
  })
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    if (path === '/categories' && categoryError) {
      await route.fulfill({
        status: categoryError.status,
        json: { code: String(categoryError.code), msg: categoryError.message, data: null },
      })
      return
    }
    if (request.method() !== 'GET') {
      writes.push({ path, body: request.postDataJSON() })
    }
    let data = []
    if (path === '/api/setup/status') data = { state: 'READY' }
    if (path === '/courses') data = { records: [{ id: 5, title: '回归课程', teacherId: 2 }], total: 1 }
    if (path === '/notices') data = {
      records: [{ id: 8, title: '已有通知', content: '通知正文', noticeType: 'exam', status: 1 }],
      total: 1,
    }
    await route.fulfill({ json: { code: '200', data } })
  })
  return writes
}

for (const questionType of ['单选题', '多选题', '判断题', '填空题']) {
  test(`${questionType} uses Chinese required-answer feedback`, async ({ page }) => {
    const writes = await mockWorkspace(page)
    await page.goto('/questions')
    await page.getByRole('button', { name: '新建题目', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '新建题目' })
    await dialog.getByLabel('课程', { exact: true }).press('Enter')
    await page.getByRole('option', { name: '回归课程', exact: true }).click()
    if (questionType !== '单选题') {
      await dialog.getByLabel('题目类型', { exact: true }).press('Enter')
      await page.getByRole('option', { name: questionType, exact: true }).click()
    }
    await dialog.getByLabel('题目内容', { exact: true }).fill('回归题目内容')
    await dialog.getByRole('button', { name: '保存', exact: true }).click()
    const message = questionType === '填空题' ? '请输入正确答案'
      : questionType === '多选题' ? '多选题请至少选择两个正确答案' : '请选择正确答案'
    await expect(dialog.locator('.el-form-item__error').filter({ hasText: message })).toBeVisible()
    await expect(dialog.getByText(/is required/)).toHaveCount(0)
    expect(writes).toHaveLength(0)
  })
}

for (const questionType of ['单选题', '填空题']) {
  test(`${questionType} submits the selected or entered answer`, async ({ page }) => {
    const writes = await mockWorkspace(page)
    await page.goto('/questions')
    await page.getByRole('button', { name: '新建题目', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '新建题目' })
    await dialog.getByLabel('课程', { exact: true }).press('Enter')
    await page.getByRole('option', { name: '回归课程', exact: true }).click()
    await dialog.getByLabel('题目内容', { exact: true }).fill('回归题目内容')
    let expectedAnswer
    if (questionType === '填空题') {
      await dialog.getByLabel('题目类型', { exact: true }).press('Enter')
      await page.getByRole('option', { name: '填空题', exact: true }).click()
      await dialog.getByLabel('正确答案', { exact: true }).fill('填空答案')
      expectedAnswer = '填空答案'
    } else {
      await dialog.getByPlaceholder('选项A', { exact: true }).fill('第一选项')
      await dialog.getByPlaceholder('选项B', { exact: true }).fill('第二选项')
      await dialog.getByLabel('正确答案', { exact: true }).press('Enter')
      await page.getByRole('option', { name: '第一选项', exact: true }).click()
      expectedAnswer = 'A'
    }
    await dialog.getByRole('button', { name: '保存', exact: true }).click()
    await expect(dialog).toBeHidden()
    expect(writes.find(item => item.path === '/api/questions')?.body.answers).toEqual([
      { answerContent: expectedAnswer, sortOrder: 1 },
    ])
  })
}

test('a new notice can publish while withdrawal remains an edit action', async ({ page }) => {
  const writes = await mockWorkspace(page)
  await page.goto('/notices')
  await page.getByRole('button', { name: '发布通知', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '发布通知' })
  await expect(dialog.getByRole('radio', { name: '已撤回', exact: true })).toHaveCount(0)
  await dialog.getByLabel('通知标题', { exact: true }).fill('正常发布的通知')
  await dialog.getByLabel('通知正文', { exact: true }).fill('通知正文内容')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).toBeHidden()
  expect(writes.find(item => item.path === '/notices')?.body).toMatchObject({
    title: '正常发布的通知', content: '通知正文内容', status: 1,
  })
  await page.getByRole('row').filter({ hasText: '已有通知' }).getByRole('button', { name: '编辑', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '编辑通知' }).getByRole('radio', { name: '已撤回', exact: true })).toBeVisible()
})

test('notice types display Chinese labels and submit their existing API values', async ({ page }) => {
  const writes = await mockWorkspace(page)
  await page.goto('/notices')
  const existing = page.getByRole('row').filter({ hasText: '已有通知' })
  await expect(existing.getByRole('cell', { name: '考试', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '发布通知', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '发布通知' })
  await dialog.getByLabel('通知标题', { exact: true }).fill('考试通知')
  await dialog.getByLabel('通知正文', { exact: true }).fill('考试安排')
  await dialog.getByLabel('通知类型', { exact: true }).press('Enter')
  await page.getByRole('option', { name: '考试', exact: true }).click()
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).toBeHidden()
  expect(writes.find(item => item.path === '/notices')?.body.noticeType).toBe('exam')
})

for (const failure of [
  { status: 200, code: 500, message: 'No static resource api/homeworks.' },
  { status: 404, code: 404, message: 'No static resource categories.' },
  { status: 500, code: 500, message: 'java.lang.IllegalStateException: internal failure' },
]) {
  test(`technical errors are hidden for HTTP ${failure.status}`, async ({ page }) => {
    await mockWorkspace(page, { categoryError: failure })
    await page.goto('/categories')
    await expect(page.getByText('分类列表未加载', { exact: true })).toBeVisible()
    await expect(page.locator('.el-message').first()).toContainText('服务暂时不可用，请稍后重试')
    await expect(page.locator('.el-message')).not.toContainText(/No static resource|java\.lang/)
    await expect(page.getByRole('button', { name: '重新加载', exact: true })).toBeVisible()
  })
}

test('actionable business feedback is preserved', async ({ page }) => {
  await mockWorkspace(page, { categoryError: { status: 200, code: 409, message: '分类名称已存在' } })
  await page.goto('/categories')
  await expect(page.locator('.el-message')).toContainText('分类名称已存在')
})
