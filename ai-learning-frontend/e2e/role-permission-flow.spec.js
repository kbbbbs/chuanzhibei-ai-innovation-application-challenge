import { test, expect, request as playwrightRequest } from '@playwright/test'
import {
  addClassStudentFixture,
  apiCall as callApi,
  clickRowButton,
  createCourseFixture,
  createHomeworkFixture,
  createTeachingClassFixture,
  expectMessage,
  fillFormInput,
  loadSeedData,
  loginByApi,
  loginByUi,
  logout,
} from './helpers.js'

test.describe.configure({ mode: 'serial' })

let api
let adminToken
let teacher
let student
let category
let suffix
const created = {}

async function apiCall(path, options = {}) {
  return callApi(api, adminToken, path, options)
}

async function setupFixtures() {
  suffix = `role_${Date.now()}`
  const login = await loginByApi('admin', '123456')
  adminToken = login.token
  api = await playwrightRequest.newContext()

  ;({ teacher, student, category } = await loadSeedData(apiCall))

  const courseName = `角色权限课程_${suffix}`
  created.course = await createCourseFixture(apiCall, {
    title: courseName,
    categoryId: category.id,
    teacherId: teacher.id,
    description: 'role permission e2e course',
  })

  created.class = await createTeachingClassFixture(apiCall, {
    courseId: created.course.id,
    teacherId: teacher.id,
    className: `角色权限班级_${suffix}`,
    description: 'role permission e2e class',
  })

  await addClassStudentFixture(apiCall, { classId: created.class.id, studentId: student.id })

  created.homework = await createHomeworkFixture(apiCall, {
    classId: created.class.id,
    teacherId: teacher.id,
    title: `角色权限作业_${suffix}`,
    content: '请通过学生端提交，再由老师端批改',
  })
}

async function cleanupFixtures() {
  if (!api) return
  if (created.homework?.id) await apiCall(`/homeworks/${created.homework.id}`, { method: 'DELETE', allowFail: true })
  if (created.class?.id) await apiCall(`/teaching-classes/${created.class.id}`, { method: 'DELETE', allowFail: true })
  if (created.course?.id) await apiCall(`/courses/${created.course.id}`, { method: 'DELETE', allowFail: true })
  await api.dispose()
}

test.beforeAll(setupFixtures)
test.afterAll(cleanupFixtures)

test('老师和学生权限隔离、越权提示、学生提交与老师批改', async ({ page }) => {
  await loginByUi(page, 'teacher', '123456', { clearStorage: true })
  await expect(page.getByRole('heading', { name: /教师工作台/ })).toBeVisible()
  await expect(page.getByText('不展示学生端“我的考试”')).toHaveCount(0)
  await expect(page.getByText('教师权限约束')).toHaveCount(0)
  await expect(page.locator('.layout-aside').getByText('课程管理')).toBeVisible()
  await expect(page.locator('.layout-aside').getByText('作业管理')).toBeVisible()
  await expect(page.locator('.layout-aside').getByText('用户管理')).toHaveCount(0)
  await expect(page.locator('.layout-aside').getByText('我的班级')).toHaveCount(0)
  await expect(page.locator('.layout-aside').getByText('我的考试')).toHaveCount(0)

  await page.goto('/users')
  await expectMessage(page, 'error', '无权限访问此页面')
  await expect(page).toHaveURL(/\/$/)

  await page.goto('/my-classes')
  await expectMessage(page, 'error', '无权限访问此页面')
  await expect(page).toHaveURL(/\/$/)

  await logout(page)

  await loginByUi(page, 'student', '123456', { clearStorage: true })
  await expect(page.getByRole('heading', { name: /学生|同学/ })).toBeVisible()
  await expect(page.getByText('教师工作台')).toHaveCount(0)
  await expect(page.locator('.layout-aside').getByText('我的班级')).toBeVisible()
  await expect(page.locator('.layout-aside').getByText('我的作业')).toBeVisible()
  await expect(page.locator('.layout-aside').getByText('题库管理')).toHaveCount(0)
  await expect(page.locator('.layout-aside').getByText('作业管理')).toHaveCount(0)

  await page.goto('/questions')
  await expectMessage(page, 'error', '无权限访问此页面')
  await expect(page).toHaveURL(/\/$/)

  await page.goto(`/homework/${created.homework.id}/submissions`)
  await expectMessage(page, 'error', '无权限访问此页面')
  await expect(page).toHaveURL(/\/$/)

  await page.goto('/my-homeworks')
  await clickRowButton(page, `角色权限作业_${suffix}`, '提交作业')
  await fillFormInput(page, '作业内容', `学生角色权限提交_${suffix}`)
  await page.locator('.el-dialog:visible').getByRole('button', { name: '提交' }).click()
  await page.locator('.el-message-box:visible').getByRole('button', { name: '确定' }).click()
  await expectMessage(page, 'success', '提交成功')
  await page.getByRole('tab', { name: '已提交' }).click()
  await expect(page.locator('.el-table__body-wrapper tr:visible').filter({ hasText: `角色权限作业_${suffix}` }).first()).toBeVisible()

  await logout(page)

  await loginByUi(page, 'teacher', '123456', { clearStorage: true })
  await page.goto(`/homework/${created.homework.id}/submissions`)
  await clickRowButton(page, `学生角色权限提交_${suffix}`, '批改')
  await fillFormInput(page, '得分', 91)
  await fillFormInput(page, '批改意见', '老师账号批改通过')
  await page.locator('.el-dialog:visible').getByRole('button', { name: '提交批改' }).click()
  await expectMessage(page, 'success', '批改成功')
  await expect(page.getByText('已批改')).toBeVisible()
})
