import { test, expect } from '@playwright/test'

const templatePage = {
  schemaVersion: 1,
  key: 'ml.gradient-descent',
  title: '梯度下降：学习率与收敛',
  subject: '机器学习',
  summary: '用曲线、滑杆和 Python 循环理解学习率。',
  blocks: [
    { type: 'markdown', title: '核心讲解', content: '## 学习率\n\n学习率决定每一步走多远。' },
    { type: 'experiment', title: '拖动学习率', description: '观察轨迹。', kind: 'gradient', defaults: { learningRate: 0.12 } },
    { type: 'ide', title: 'Python：梯度下降', language: 'python', starterCode: 'print("gradient")', expectedOutputHint: '输出 gradient。' },
    { type: 'checkpoint', title: '完成检查', items: ['我能解释学习率'] },
  ],
}

const mockApi = async (page, urlPattern, handler) => {
  await page.route(urlPattern, async route => {
    const url = new URL(route.request().url())
    if (url.origin !== 'http://localhost:8080') {
      await route.continue()
      return
    }
    await handler(route)
  })
}

test('teacher lesson editor saves built-in interactive JSON as lesson content', async ({ page }) => {
  const consoleErrors = []
  let savedPayload = null
  page.on('console', msg => {
    if (msg.type() === 'error') consoleErrors.push(msg.text())
  })

  await page.addInitScript(() => {
    localStorage.setItem('token', 'teacher-token')
    localStorage.setItem('userId', '2')
    localStorage.setItem('username', 'teacher')
    localStorage.setItem('nickname', '教师')
    localStorage.setItem('roleCode', 'teacher')
  })

  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin === 'http://localhost:8080') {
      await route.fulfill({ json: { code: '200', data: null, msg: 'mocked' } })
      return
    }
    await route.continue()
  })
  await mockApi(page, '**/learning-content/templates', async route => {
    await route.fulfill({ json: { code: '200', data: { templates: [{ key: 'ml.gradient-descent', title: templatePage.title, subject: '机器学习', summary: templatePage.summary, tags: ['机器学习', 'Python'] }] } } })
  })
  await mockApi(page, '**/learning-content/templates/ml.gradient-descent', async route => {
    await route.fulfill({ json: { code: '200', data: templatePage } })
  })
  await mockApi(page, '**/courses/777/chapters', async route => {
    await route.fulfill({ json: { code: '200', data: [{ id: 7701, courseId: 777, title: '第一章', description: '互动课本', sortOrder: 1, lessons: [] }] } })
  })
  await mockApi(page, '**/chapters/7701/lessons', async route => {
    await route.fulfill({ json: { code: '200', data: [] } })
  })
  await mockApi(page, '**/courses/777', async route => {
    await route.fulfill({ json: { code: '200', data: { id: 777, title: '机器学习', difficulty: '入门', description: '互动课程' } } })
  })
  await mockApi(page, '**/lessons', async route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/lessons' && route.request().method() === 'POST') {
      savedPayload = route.request().postDataJSON()
      await route.fulfill({ json: { code: '200', data: { id: 8801, ...savedPayload } } })
      return
    }
    await route.fallback()
  })

  await page.goto('/courses/777')
  await expect(page.getByText('章节与课时')).toBeVisible()
  await page.getByRole('button', { name: '添加课时', exact: true }).click()
  const dialog = page.locator('.el-dialog').filter({ hasText: '新增课时' })
  await expect(dialog).toBeVisible()

  await dialog.locator('.el-select').first().click()
  await page.getByRole('option', { name: '内置互动 JSON' }).click()
  await dialog.locator('.interactive-editor .el-select').click()
  await page.getByRole('option', { name: /梯度下降：学习率与收敛/ }).click()
  await expect(dialog.getByText('用曲线、滑杆和 Python 循环理解学习率。')).toBeVisible()
  await expect(dialog.locator('textarea')).toHaveValue(/"type": "ide"/)

  await dialog.getByRole('button', { name: '保存' }).click()
  await expect.poll(() => savedPayload?.resourceType).toBe('interactive_json_builtin')
  const savedPage = JSON.parse(savedPayload.content)
  expect(savedPage.blocks.some(block => block.type === 'experiment')).toBeTruthy()
  expect(savedPage.blocks.some(block => block.type === 'ide')).toBeTruthy()
  expect(savedPayload.resourceUrl).toBe('')
  expect(consoleErrors).toEqual([])
})
