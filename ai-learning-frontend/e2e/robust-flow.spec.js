import { test, expect, request as playwrightRequest } from '@playwright/test'
import {
  API_BASE,
  addClassStudentFixture,
  apiCall as callApi,
  clickRowButton,
  confirmDelete,
  createCourseFixture,
  createHomeworkFixture,
  createTeachingClassFixture,
  fillFormInput,
  localDateTime,
  loginByApi,
  loginByUi,
  loadSeedData,
  logout,
  records,
  saveDialog,
  selectFormOption,
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

async function expectNoVisibleError(page) {
  await expect(page.locator('.el-message--error:visible')).toHaveCount(0)
}

async function setupFixtures() {
  suffix = `pw_${Date.now()}`
  const login = await loginByApi('admin', '123456')
  adminToken = login.token
  api = await playwrightRequest.newContext()

  ;({ teacher, student, category } = await loadSeedData(apiCall))

  const courseName = `自动化课程_${suffix}`
  created.course = await createCourseFixture(apiCall, {
    title: courseName,
    categoryId: category.id,
    teacherId: teacher.id,
  })

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

  created.class = await createTeachingClassFixture(apiCall, {
    courseId: created.course.id,
    teacherId: teacher.id,
    className: `自动化班级_${suffix}`,
  })

  await addClassStudentFixture(apiCall, { classId: created.class.id, studentId: student.id })

  created.homework = await createHomeworkFixture(apiCall, {
    classId: created.class.id,
    teacherId: teacher.id,
    title: `自动化作业_${suffix}`,
    content: '请提交自动化作业',
  })

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

  created.fixturePaper = (await apiCall('/api/papers', {
    method: 'POST',
    data: {
      name: `自动化试卷_${suffix}`,
      courseId: created.course.id,
      teacherId: teacher.id,
      description: 'browser e2e paper',
      durationMinutes: 60,
      status: 'PUBLISHED',
      questions: [{
        questionId: created.question.id,
        sortOrder: 1,
        score: 5,
        requiredAnswer: true,
      }],
    },
  })).data

  created.exam = (await apiCall('/api/exams', {
    method: 'POST',
    data: {
      classId: created.class.id,
      teacherId: teacher.id,
      paperId: created.fixturePaper.id,
      name: `自动化考试_${suffix}`,
      description: 'browser e2e exam',
      startTime: localDateTime(-60000),
      endTime: localDateTime(7200000),
      durationMinutes: 60,
      status: 'PUBLISHED',
    },
  })).data
}

async function cleanupFixtures() {
  if (!api) return
  if (created.exam?.id) {
    await apiCall(`/api/exams/${created.exam.id}/close`, { method: 'POST', allowFail: true })
    await apiCall(`/api/exams/${created.exam.id}`, { method: 'DELETE', allowFail: true })
  }
  if (created.fixturePaper?.id) await apiCall(`/api/papers/${created.fixturePaper.id}`, { method: 'DELETE', allowFail: true })
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
  await expect(page.locator('.layout-aside').getByText('课程管理')).toBeVisible()
  await expect(page.locator('.layout-aside').getByText('用户管理')).toBeVisible()

  for (const path of ['/courses', '/questions', '/papers', '/exams', '/homeworks', '/teaching-classes', '/users', '/categories', '/my-classes', '/learning-routes', '/my-homeworks', '/my-exams', '/my-grades', '/home']) {
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
  await fillFormInput(page, 'Markdown', 'UI课时内容')
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
  await expect(page.locator('.el-dialog:visible')).toBeVisible()
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
  await expect(page.getByText(`自动化题目_${suffix}`)).toBeVisible()
  await page.getByRole('button', { name: '新建题目' }).click()
  await expect(page.locator('.el-dialog:visible')).toBeVisible()
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
  await page.locator('.el-dialog:visible').getByRole('button', { name: '下一步' }).click()
  await page.locator('.el-dialog:visible .el-table__body-wrapper tr').filter({ hasText: `自动化题目_${suffix}` }).first().click()
  await page.locator('.el-dialog:visible').getByRole('button', { name: '下一步' }).click()
  await saveDialog(page)
  await expect(page.locator('.el-table__body-wrapper tr:visible').filter({ hasText: `UI试卷_${suffix}` }).first()).toBeVisible()
  created.uiPaperId = (await apiCall('/api/papers')).data.find(item => item.name === `UI试卷_${suffix}`)?.id
  await clickRowButton(page, `UI试卷_${suffix}`, '编辑')
  await fillFormInput(page, '试卷名称', `UI试卷_${suffix}_改`)
  await page.locator('.el-dialog:visible').getByRole('button', { name: '下一步' }).click()
  await page.locator('.el-dialog:visible').getByRole('button', { name: '下一步' }).click()
  await saveDialog(page)
  await expect(page.locator('.el-table__body-wrapper tr:visible').filter({ hasText: `UI试卷_${suffix}_改` }).first()).toBeVisible()
  await clickRowButton(page, `UI试卷_${suffix}_改`, '删除')
  await confirmDelete(page)
  await expect(page.locator('.el-table__body-wrapper tr:visible').filter({ hasText: `UI试卷_${suffix}_改` })).toHaveCount(0)

  for (const path of ['/homeworks', '/courses', '/questions', '/teaching-classes', '/papers', '/exams', '/homeworks']) {
    await page.goto(path)
    await page.waitForLoadState('networkidle')
    await expectNoVisibleError(page)
  }

  await logout(page, { fromHome: true, ifNeeded: true })
  await loginByUi(page, 'student', '123456')
  await expect(page.locator('.layout-aside').getByText('我的班级')).toBeVisible()
  await expect(page.locator('.layout-aside').getByText('学习路线')).toBeVisible()
  await expect(page.locator('.layout-aside').getByText('课程管理')).toHaveCount(0)

  await page.goto('/learning-routes')
  await expect(page.getByRole('heading', { name: '学习路线地图集' })).toBeVisible()
  await expect(page.getByText(created.course.title)).toBeVisible()
  await expect(page.getByRole('button', { name: '打开完整学习地图' }).first()).toBeVisible()

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

  await logout(page, { fromHome: true, ifNeeded: true })
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
