import { expect, request as playwrightRequest } from '@playwright/test'

export const API_BASE = 'http://localhost:8080'

export function localDateTime(offsetMs = 0) {
  const date = new Date(Date.now() + offsetMs)
  const pad = (num) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function records(payload) {
  if (!payload) return []
  if (Array.isArray(payload)) return payload
  return payload.records || payload.content || payload.data || []
}

export function normalizeApiResponse(body, status) {
  if (body && typeof body.success === 'boolean') {
    return {
      code: body.success ? '200' : '500',
      data: body.data,
      msg: body.message,
    }
  }
  return {
    ...body,
    code: body?.code == null ? String(status) : String(body.code),
    msg: body?.msg || body?.message,
  }
}

export async function apiCall(api, adminToken, path, options = {}) {
  const method = options.method || 'GET'
  const response = await api.fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(adminToken ? { Authorization: `Bearer ${adminToken}` } : {}),
      ...(options.headers || {}),
    },
    data: options.data,
    params: options.params,
  })
  const body = await response.json().catch(() => null)
  const normalized = normalizeApiResponse(body, response.status())

  if (!options.allowFail && (!response.ok() || normalized.code !== '200')) {
    throw new Error(`${method} ${path} failed: http=${response.status()} code=${normalized.code} ${normalized.msg || ''}`)
  }
  return normalized
}

export async function loginByApi(username, password) {
  const context = await playwrightRequest.newContext()
  const response = await context.post(`${API_BASE}/auth/login`, {
    data: { username, password },
  })
  const body = await response.json()
  await context.dispose()
  return body.data
}

export async function loginByUi(page, username = 'admin', password = '[REDACTED]', options = {}) {
  await page.goto('/login')
  if (options.clearStorage) {
    await page.evaluate(() => localStorage.clear())
    await page.reload()
  }
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/home/)
  if (options.expectHomeHeading !== false) {
    await expect(page.locator('.welcome-header')).toBeVisible()
  }
}

export async function logout(page, options = {}) {
  if (options.fromHome) {
    await page.goto('/home')
  }
  const trigger = page.locator('.user-profile-trigger')
  if (options.ifNeeded && await trigger.count() === 0) {
    return
  }
  await trigger.click()
  await page.getByText('退出登录').click()
  await expect(page).toHaveURL(/\/(?:login)?$/)
}

async function findLoggedInUser(username) {
  const login = await loginByApi(username, '123456')
  return {
    id: login.userId,
    username: login.username,
    nickname: login.nickname,
    roleCode: login.roleCode,
  }
}

export async function loadSeedData(apiCall) {
  const teacher = await findLoggedInUser('teacher')
  const student = await findLoggedInUser('student')
  const category = (await apiCall('/categories')).data[0]
  if (!teacher?.id || !student?.id || !category) {
    throw new Error('Seeded teacher/student/category data is required')
  }
  return { teacher, student, category }
}

export async function createCourseFixture(apiCall, { title, categoryId, teacherId, description = 'browser e2e course' }) {
  await apiCall('/courses', {
    method: 'POST',
    data: {
      title,
      description,
      categoryId,
      teacherId,
      difficulty: 'beginner',
      status: 1,
    },
  })
  return records((await apiCall('/courses', { params: { title, pageSize: 20 } })).data)
    .find(item => item.title === title)
}

export async function createTeachingClassFixture(apiCall, { courseId, teacherId, className, description = 'browser e2e class' }) {
  await apiCall('/teaching-classes', {
    method: 'POST',
    data: {
      courseId,
      teacherId,
      className,
      semester: '2026春季',
      description,
      status: 1,
    },
  })
  return records((await apiCall('/teaching-classes', { params: { className, pageSize: 20 } })).data)
    .find(item => item.className === className)
}

export function addClassStudentFixture(apiCall, { classId, studentId }) {
  return apiCall('/class-students', {
    method: 'POST',
    data: { classId, studentId, status: 1 },
  })
}

export async function createHomeworkFixture(apiCall, { classId, teacherId, title, content, fullScore = 100 }) {
  await apiCall('/homeworks', {
    method: 'POST',
    data: {
      classId,
      teacherId,
      title,
      content,
      fullScore,
      startTime: localDateTime(-60000),
      deadline: localDateTime(86400000),
      status: 1,
    },
  })
  return records((await apiCall('/homeworks', { params: { title, pageSize: 20 } })).data)
    .find(item => item.title === title)
}

export async function formItem(page, label) {
  const item = page.locator('.el-form-item:visible').filter({ hasText: label }).last()
  await expect(item).toBeVisible()
  return item
}

export async function fillFormInput(page, label, value) {
  const item = await formItem(page, label)
  await item.locator('input, textarea').first().fill(String(value))
}

export async function selectFormOption(page, label, optionText) {
  const item = await formItem(page, label)
  await item.locator('.el-select').click()
  const option = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: optionText }).first()
  await expect(option).toBeVisible()
  await option.click()
}

export async function saveDialog(page) {
  await page.locator('.el-dialog:visible').getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-dialog:visible')).toHaveCount(0)
}

export async function confirmDelete(page) {
  await page.locator('.el-message-box:visible').getByRole('button', { name: '确定' }).click()
}

export async function clickRowButton(page, rowText, buttonText) {
  const row = page.locator('.el-table__body-wrapper tr:visible').filter({ hasText: rowText }).first()
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: buttonText }).click()
}

export async function expectMessage(page, type, text) {
  await expect(page.locator(`.el-message--${type}`).filter({ hasText: text }).last()).toBeVisible()
}
