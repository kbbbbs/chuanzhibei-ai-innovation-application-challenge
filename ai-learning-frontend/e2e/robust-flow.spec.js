import { test, expect, request as playwrightRequest } from '@playwright/test'

const API_BASE = 'http://localhost:8080'
const APP_BASE = 'http://127.0.0.1:5173'

test.describe.configure({ mode: 'serial' })

let api
let adminToken
let teacher
let student
let category
let suffix
const created = {}

function localDateTime(offsetMs = 0) {
  const date = new Date(Date.now() + offsetMs)
  const pad = (num) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function records(payload) {
  if (!payload) return []
  if (Array.isArray(payload)) return payload
  return payload.records || payload.content || payload.data || []
}

async function apiCall(path, options = {}) {
  const method = options.method || 'GET'
  const headers = {
    'Content-Type': 'application/json',
    ...(adminToken ? { Authorization: `Bearer ${adminToken}` } : {}),
    ...(options.headers || {}),
  }
  const response = await api.fetch(`${API_BASE}${path}`, {
    method,
    headers,
    data: options.data,
    params: options.params,
  })
  const body = await response.json().catch(() => null)
  const normalized = body && typeof body.success === 'boolean'
    ? { code: body.success ? '200' : '500', data: body.data, msg: body.message }
    : { ...body, code: body?.code == null ? String(response.status()) : String(body.code), msg: body?.msg || body?.message }

  if (!options.allowFail && (!response.ok() || normalized.code !== '200')) {
    throw new Error(`${method} ${path} failed: http=${response.status()} code=${normalized.code} ${normalized.msg || ''}`)
  }
  return normalized
}

async function loginByApi(username, password) {
  const context = await playwrightRequest.newContext()
  const response = await context.post(`${API_BASE}/auth/login`, {
    data: { username, password },
  })
  const body = await response.json()
  await context.dispose()
  return body.data
}

async function loginByUi(page, username, password) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/home/)
  await expect(page.locator('.header-title')).toHaveText('AI智学平台')
}

async function logoutIfNeeded(page) {
  await page.goto('/home')
  if (await page.locator('.user-dropdown').count()) {
    await page.locator('.user-dropdown').click()
    await page.getByText('退出登录').click()
    await expect(page).toHaveURL(/\/login/)
  }
}

async function formItem(page, label) {
  const item = page.locator('.el-form-item:visible').filter({ hasText: label }).last()
  await expect(item).toBeVisible()
  return item
}

async function fillFormInput(page, label, value) {
  const item = await formItem(page, label)
  const input = item.locator('input, textarea').first()
  await input.fill(String(value))
}

async function selectFormOption(page, label, optionText) {
  const item = await formItem(page, label)
  await item.locator('.el-select').click()
  const option = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: optionText }).first()
  await expect(option).toBeVisible()
  await option.click()
}

async function saveDialog(page) {
  await page.locator('.el-dialog:visible').getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-dialog:visible')).toHaveCount(0)
}

async function confirmDelete(page) {
  await page.locator('.el-message-box:visible').getByRole('button', { name: '确定' }).click()
}

async function clickRowButton(page, rowText, buttonText) {
  const row = page.locator('.el-table__body-wrapper tr:visible').filter({ hasText: rowText }).first()
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: buttonText }).click()
}

async function expectNoVisibleError(page) {
  await expect(page.locator('.el-message--error:visible')).toHaveCount(0)
}

async function setupFixtures() {
  suffix = `pw_${Date.now()}`
  const login = await loginByApi('admin', '123456')
  adminToken = login.token
  api = await playwrightRequest.newContext()

  teacher = records((await apiCall('/users', { params: { roleCode: 'teacher', pageSize: 50 } })).data)
    .find(item => item.username === 'teacher')
  student = records((await apiCall('/users', { params: { roleCode: 'student', pageSize: 50 } })).data)
    .find(item => item.username === 'student')
  if (!teacher || !student) {
    throw new Error('Seeded teacher/student accounts are required for robust-flow e2e')
  }
  category = (await apiCall('/categories')).data[0]

  const courseName = `自动化课程_${suffix}`
  await apiCall('/courses', {
    method: 'POST',
    data: {
      title: courseName,
      description: 'browser e2e course',
      categoryId: category.id,
      teacherId: teacher.id,
      difficulty: 'beginner',
      status: 1,
    },
  })
  created.course = records((await apiCall('/courses', { params: { title: courseName, pageSize: 20 } })).data)
    .find(item => item.title === courseName)

  await apiCall('/chapters', {
    method: 'POST',
    data: { courseId: created.course.id, title: `学习章节_${suffix}`, description: 'chapter', sortOrder: 1 },
  })
  created.chapter = (await apiCall(`/courses/${created.course.id}/chapters`)).data.find(item => item.title === `学习章节_${suffix}`)

  await apiCall('/lessons', {
    method: 'POST',
    data: {
      chapterId: created.chapter.id,
      title: `学习课时_${suffix}`,
      resourceType: 'article',
      resourceUrl: '自动化学习内容',
      duration: 60,
      sortOrder: 1,
      status: 1,
    },
  })
  created.lesson = (await apiCall(`/chapters/${created.chapter.id}/lessons`)).data.find(item => item.title === `学习课时_${suffix}`)

  await apiCall('/teaching-classes', {
    method: 'POST',
    data: {
      courseId: created.course.id,
      teacherId: teacher.id,
      className: `自动化班级_${suffix}`,
      semester: '2026春季',
      description: 'browser e2e class',
      status: 1,
    },
  })
  created.class = records((await apiCall('/teaching-classes', { params: { className: `自动化班级_${suffix}`, pageSize: 20 } })).data)
    .find(item => item.className === `自动化班级_${suffix}`)

  await apiCall('/class-students', {
    method: 'POST',
    data: { classId: created.class.id, studentId: student.id, status: 1 },
  })

  await apiCall('/homeworks', {
    method: 'POST',
    data: {
      classId: created.class.id,
      teacherId: teacher.id,
      title: `自动化作业_${suffix}`,
      content: '请提交自动化作业',
      fullScore: 100,
      startTime: localDateTime(-60000),
      deadline: localDateTime(86400000),
      status: 1,
    },
  })
  created.homework = records((await apiCall('/homeworks', { params: { title: `自动化作业_${suffix}`, pageSize: 20 } })).data)
    .find(item => item.title === `自动化作业_${suffix}`)

  created.question = (await apiCall('/api/questions', {
    method: 'POST',
    data: {
      courseId: created.course.id,
      teacherId: teacher.id,
      type: 'SINGLE_CHOICE',
      content: `自动化题目_${suffix}`,
      difficulty: 'EASY',
      score: 5,
      knowledgePoint: 'E2E',
      analysis: '自动化解析',
      status: 'ACTIVE',
      options: [
        { optionKey: 'A', optionContent: '正确选项', sortOrder: 1 },
        { optionKey: 'B', optionContent: '错误选项', sortOrder: 2 },
      ],
      answers: [{ answerContent: 'A', sortOrder: 1 }],
    },
  })).data

  const publishedPaper = (await apiCall('/api/papers')).data.find(item => {
    const status = typeof item.status === 'string' ? item.status : item.status?.name
    return status === 'PUBLISHED'
  })
  if (publishedPaper) {
    created.exam = (await apiCall('/api/exams', {
      method: 'POST',
      data: {
        classId: created.class.id,
        teacherId: teacher.id,
        paperId: publishedPaper.id,
        name: `自动化考试_${suffix}`,
        description: 'browser e2e exam',
        startTime: localDateTime(-60000),
        endTime: localDateTime(7200000),
        durationMinutes: 60,
        status: 'PUBLISHED',
      },
    })).data
  }
}

async function cleanupFixtures() {
  if (!api) return
  if (created.exam?.id) {
    await apiCall(`/api/exams/${created.exam.id}/close`, { method: 'POST', allowFail: true })
    await apiCall(`/api/exams/${created.exam.id}`, { method: 'DELETE', allowFail: true })
  }
  if (created.uiPaperId) await apiCall(`/api/papers/${created.uiPaperId}`, { method: 'DELETE', allowFail: true })
  if (created.uiQuestionId) await apiCall(`/api/questions/${created.uiQuestionId}`, { method: 'DELETE', allowFail: true })
  if (created.uiUserId) await apiCall(`/users/${created.uiUserId}`, { method: 'DELETE', allowFail: true })
  if (created.uiCategoryId) await apiCall(`/categories/${created.uiCategoryId}`, { method: 'DELETE', allowFail: true })
  if (created.class?.id) await apiCall(`/teaching-classes/${created.class.id}`, { method: 'DELETE', allowFail: true })
  if (created.course?.id) await apiCall(`/courses/${created.course.id}`, { method: 'DELETE', allowFail: true })
  await api.dispose()
}

function watchForClientFailures(page) {
  const failures = []
  page.on('pageerror', error => failures.push(`pageerror: ${error.message}`))
  page.on('console', message => {
    if (message.type() === 'error') {
      const location = message.location()
      const source = location.url ? ` (${location.url}:${location.lineNumber}:${location.columnNumber})` : ''
      failures.push(`console: ${message.text()}${source}`)
    }
  })
  page.on('response', response => {
    const url = response.url()
    if (url.startsWith(API_BASE) && response.status() >= 400) {
      failures.push(`http ${response.status()}: ${response.request().method()} ${url}`)
    }
  })
  return failures
}

test.beforeAll(async () => {
  await setupFixtures()
})

test.afterAll(async () => {
  await cleanupFixtures()
})

test('权限、导航、增删改、学生流程和反复跳转保持稳定', async ({ page }) => {
  const failures = watchForClientFailures(page)

  await page.goto('/courses')
  await expect(page).toHaveURL(/\/login/)
  await expect(page.locator('.layout-aside')).toHaveCount(0)

  await loginByUi(page, 'admin', '123456')
  await expect(page.getByText('课程管理')).toBeVisible()
  await expect(page.getByText('用户管理')).toBeVisible()

  for (const path of ['/courses', '/questions', '/papers', '/exams', '/homeworks', '/teaching-classes', '/users', '/categories', '/my-classes', '/my-homeworks', '/my-exams', '/my-grades', '/home']) {
    await page.goto(path)
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.layout-aside')).toBeVisible()
    await expectNoVisibleError(page)
  }

  await page.goto('/categories')
  await page.getByRole('button', { name: '新增分类' }).click()
  await fillFormInput(page, '分类名称', `UI分类_${suffix}`)
  await fillFormInput(page, '描述', 'UI分类描述')
  await saveDialog(page)
  await expect(page.getByText(`UI分类_${suffix}`)).toBeVisible()
  created.uiCategoryId = (await apiCall('/categories')).data.find(item => item.name === `UI分类_${suffix}`)?.id
  await clickRowButton(page, `UI分类_${suffix}`, '编辑')
  await fillFormInput(page, '分类名称', `UI分类_${suffix}_改`)
  await saveDialog(page)
  await expect(page.getByText(`UI分类_${suffix}_改`)).toBeVisible()
  await clickRowButton(page, `UI分类_${suffix}_改`, '删除')
  await confirmDelete(page)
  await expect(page.getByText(`UI分类_${suffix}_改`)).toHaveCount(0)

  await page.goto('/users')
  await page.getByRole('button', { name: '新增用户' }).click()
  await fillFormInput(page, '用户名', `ui_student_${suffix}`)
  await fillFormInput(page, '密码', '123456')
  await fillFormInput(page, '昵称', `UI学生_${suffix}`)
  await fillFormInput(page, '邮箱', `ui_${suffix}@example.com`)
  await selectFormOption(page, '角色', '学生')
  await saveDialog(page)
  created.uiUserId = records((await apiCall('/users', { params: { username: `ui_student_${suffix}`, pageSize: 5 } })).data)[0]?.id
  await fillFormInput(page, '用户名', `ui_student_${suffix}`)
  await page.getByRole('button', { name: '查询' }).click()
  await expect(page.getByText(`UI学生_${suffix}`)).toBeVisible()
  await clickRowButton(page, `ui_student_${suffix}`, '编辑')
  await fillFormInput(page, '昵称', `UI学生_${suffix}_改`)
  await saveDialog(page)
  await expect(page.getByText(`UI学生_${suffix}_改`)).toBeVisible()
  await clickRowButton(page, `ui_student_${suffix}`, '删除')
  await confirmDelete(page)
  await expect(page.getByText(`ui_student_${suffix}`)).toHaveCount(0)

  await page.goto('/courses')
  await fillFormInput(page, '课程名称', created.course.title)
  await page.getByRole('button', { name: '查询' }).click()
  await clickRowButton(page, created.course.title, '详情')
  await expect(page).toHaveURL(/\/courses\/\d+/)
  await page.getByRole('button', { name: '新增章节' }).click()
  await fillFormInput(page, '章节标题', `UI章节_${suffix}`)
  await fillFormInput(page, '章节简介', 'UI章节简介')
  await saveDialog(page)
  await expect(page.getByText(`UI章节_${suffix}`)).toBeVisible()
  await page.locator('.el-collapse-item').filter({ hasText: `UI章节_${suffix}` }).locator('.chapter-actions').getByRole('button', { name: '添加课时' }).click()
  await fillFormInput(page, '课时标题', `UI课时_${suffix}`)
  await fillFormInput(page, '资源地址', 'UI课时内容')
  await saveDialog(page)
  await page.locator('.el-collapse-item').filter({ hasText: `UI章节_${suffix}` }).locator('.el-collapse-item__header').click()
  await expect(page.getByText(`UI课时_${suffix}`)).toBeVisible()
  await clickRowButton(page, `UI课时_${suffix}`, '删除')
  await confirmDelete(page)
  await expect(page.getByText(`UI课时_${suffix}`)).toHaveCount(0)
  await page.locator('.el-collapse-item').filter({ hasText: `UI章节_${suffix}` }).locator('.chapter-actions').getByRole('button', { name: '删除' }).click()
  await confirmDelete(page)
  await expect(page.getByText(`UI章节_${suffix}`)).toHaveCount(0)

  await page.goto('/teaching-classes')
  await page.getByRole('button', { name: '创建教学班' }).click()
  await selectFormOption(page, '课程', created.course.title)
  await fillFormInput(page, '班级名称', `UI班级_${suffix}`)
  await fillFormInput(page, '学期', '2026春季')
  await saveDialog(page)
  await expect(page.getByText(`UI班级_${suffix}`)).toBeVisible()
  await clickRowButton(page, `UI班级_${suffix}`, '学生管理')
  await page.locator('.el-dialog:visible input').first().fill(student.username)
  await page.locator('.el-dialog:visible').getByRole('button', { name: '添加学生' }).click()
  const studentDisplayName = student.nickname || student.username || `学生${student.id}`
  const studentRow = page.locator('.el-dialog:visible .el-table__body-wrapper tr')
    .filter({ hasText: String(student.id) })
    .filter({ hasText: studentDisplayName })
    .first()
  await expect(studentRow).toBeVisible()
  await studentRow.getByRole('button', { name: '移除' }).click()
  await confirmDelete(page)
  await expect(studentRow).toHaveCount(0)
  await page.keyboard.press('Escape')
  await clickRowButton(page, `UI班级_${suffix}`, '删除')
  await confirmDelete(page)
  await expect(page.getByText(`UI班级_${suffix}`)).toHaveCount(0)

  await page.goto('/questions')
  await page.getByRole('button', { name: '新建题目' }).click()
  await selectFormOption(page, '课程', created.course.title)
  await fillFormInput(page, '题目内容', `UI题目_${suffix}`)
  const optionInputs = page.locator('.el-dialog:visible .option-row input')
  await optionInputs.nth(0).fill('正确选项')
  await optionInputs.nth(1).fill('错误选项')
  await selectFormOption(page, '正确答案', '正确选项')
  await saveDialog(page)
  await expect(page.getByText(`UI题目_${suffix}`)).toBeVisible()
  created.uiQuestionId = records((await apiCall('/api/questions', { params: { courseId: created.course.id, size: 200 } })).data)
    .find(item => item.content === `UI题目_${suffix}`)?.id
  await clickRowButton(page, `UI题目_${suffix}`, '编辑')
  await fillFormInput(page, '题目内容', `UI题目_${suffix}_改`)
  await saveDialog(page)
  await expect(page.getByText(`UI题目_${suffix}_改`)).toBeVisible()

  await page.goto('/papers')
  await page.getByRole('button', { name: '创建试卷' }).click()
  await fillFormInput(page, '试卷名称', `UI试卷_${suffix}`)
  await selectFormOption(page, '课程', created.course.title)
  await page.locator('.el-dialog:visible .el-table__body-wrapper tr').filter({ hasText: `自动化题目_${suffix}` }).first().click()
  await saveDialog(page)
  await expect(page.getByText(`UI试卷_${suffix}`)).toBeVisible()
  created.uiPaperId = (await apiCall('/api/papers')).data.find(item => item.name === `UI试卷_${suffix}`)?.id
  await clickRowButton(page, `UI试卷_${suffix}`, '编辑')
  await fillFormInput(page, '试卷名称', `UI试卷_${suffix}_改`)
  await saveDialog(page)
  await expect(page.getByText(`UI试卷_${suffix}_改`)).toBeVisible()
  await clickRowButton(page, `UI试卷_${suffix}_改`, '删除')
  await confirmDelete(page)
  await expect(page.getByText(`UI试卷_${suffix}_改`)).toHaveCount(0)

  for (const path of ['/homeworks', '/courses', '/questions', '/teaching-classes', '/papers', '/exams', '/homeworks']) {
    await page.goto(path)
    await page.waitForLoadState('networkidle')
    await expectNoVisibleError(page)
  }

  await logoutIfNeeded(page)
  await loginByUi(page, 'student', '123456')
  await expect(page.getByText('我的班级')).toBeVisible()
  await expect(page.getByText('课程管理')).toHaveCount(0)

  await page.goto('/my-classes')
  await clickRowButton(page, created.course.title, '进入学习')
  await expect(page).toHaveURL(/\/course-learn\//)
  await page.getByRole('button', { name: '标记完成' }).click()
  await expect(page.locator('.el-message--error:visible')).toHaveCount(0)

  await page.goto('/my-homeworks')
  await clickRowButton(page, `自动化作业_${suffix}`, '提交作业')
  await fillFormInput(page, '作业内容', `学生UI提交_${suffix}`)
  await page.locator('.el-dialog:visible').getByRole('button', { name: '提交' }).click()
  await page.locator('.el-message-box:visible').getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-dialog:visible')).toHaveCount(0)
  await expect(page.getByRole('tab', { name: '已提交' })).toBeVisible()
  await page.getByRole('tab', { name: '已提交' }).click()
  await expect(page.locator('.el-table__body-wrapper tr:visible').filter({ hasText: `自动化作业_${suffix}` }).first()).toBeVisible()

  if (created.exam?.id) {
    await page.goto('/my-exams')
    await clickRowButton(page, `自动化考试_${suffix}`, '开始考试')
    await expect(page).toHaveURL(/\/exam-taking\//)
    const firstOption = page.locator('.question-block .el-radio').first()
    if (await firstOption.count()) await firstOption.click()
    await page.getByRole('button', { name: '提交试卷' }).click()
    await page.locator('.el-message-box:visible').getByRole('button', { name: '确定' }).click()
    await expect(page).toHaveURL(/\/my-exams/)
    await page.getByRole('tab', { name: '已完成' }).click()
    await expect(page.getByText(`自动化考试_${suffix}`)).toBeVisible()
  }

  await logoutIfNeeded(page)
  await loginByUi(page, 'admin', '123456')
  await page.goto(`/homework/${created.homework.id}/submissions`)
  await clickRowButton(page, `学生UI提交_${suffix}`, '批改')
  await fillFormInput(page, '得分', 88)
  await fillFormInput(page, '批改意见', 'UI批改通过')
  await page.locator('.el-dialog:visible').getByRole('button', { name: '提交批改' }).click()
  await expect(page.getByText('已批改')).toBeVisible()

  await page.goto('/questions')
  await clickRowButton(page, `UI题目_${suffix}_改`, '删除')
  await confirmDelete(page)
  await expect(page.getByText(`UI题目_${suffix}_改`)).toHaveCount(0)

  expect(failures).toEqual([])
})
