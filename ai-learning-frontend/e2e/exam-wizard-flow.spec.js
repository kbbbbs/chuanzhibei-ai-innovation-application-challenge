import { test, expect, request as playwrightRequest } from '@playwright/test'
import {
  apiCall as callApi,
  createCourseFixture,
  createTeachingClassFixture,
  expectMessage,
  fillFormInput,
  localDateTime,
  loginByApi,
  loginByUi,
  loadSeedData,
  records,
  selectFormOption,
} from './helpers.js'

test.describe.configure({ mode: 'serial' })

let api
let adminToken
let teacher
let category
let suffix
const created = {}

async function apiCall(path, options = {}) {
  return callApi(api, adminToken, path, options)
}

async function createQuestionFixture() {
  return (await apiCall('/api/questions', {
    method: 'POST',
    data: {
      courseId: created.course.id,
      teacherId: teacher.id,
      type: 'SINGLE_CHOICE',
      content: `分步流程题目_${suffix}`,
      difficulty: 'EASY',
      knowledgePoint: 'wizard',
      analysis: 'wizard flow answer',
      status: 'ACTIVE',
      options: [
        { optionKey: 'A', optionContent: '正确选项', sortOrder: 1 },
        { optionKey: 'B', optionContent: '错误选项', sortOrder: 2 },
      ],
      answers: [{ answerContent: 'A', sortOrder: 1 }],
    },
  })).data
}

async function setupFixtures() {
  suffix = `wizard_${Date.now()}`
  const login = await loginByApi('admin', '123456')
  adminToken = login.token
  api = await playwrightRequest.newContext()

  ;({ teacher, category } = await loadSeedData(apiCall))
  created.course = await createCourseFixture(apiCall, {
    title: `分步流程课程_${suffix}`,
    categoryId: category.id,
    teacherId: teacher.id,
  })
  created.class = await createTeachingClassFixture(apiCall, {
    courseId: created.course.id,
    teacherId: teacher.id,
    className: `分步流程班级_${suffix}`,
  })
  created.question = await createQuestionFixture()
}

async function cleanupFixtures() {
  if (!api) return
  if (created.exam?.id) {
    await apiCall(`/api/exams/${created.exam.id}/close`, { method: 'POST', allowFail: true })
    await apiCall(`/api/exams/${created.exam.id}`, { method: 'DELETE', allowFail: true })
  }
  if (created.paper?.id) await apiCall(`/api/papers/${created.paper.id}`, { method: 'DELETE', allowFail: true })
  if (created.question?.id) await apiCall(`/api/questions/${created.question.id}`, { method: 'DELETE', allowFail: true })
  if (created.class?.id) await apiCall(`/teaching-classes/${created.class.id}`, { method: 'DELETE', allowFail: true })
  if (created.course?.id) await apiCall(`/courses/${created.course.id}`, { method: 'DELETE', allowFail: true })
  await api.dispose()
}

test.beforeAll(setupFixtures)
test.afterAll(cleanupFixtures)

test('老师用分步弹窗创建试卷并发布考试', async ({ page }) => {
  await loginByUi(page, 'teacher', '123456', { clearStorage: true, expectHomeHeading: false })

  const paperName = `分步流程试卷_${suffix}`
  await page.goto('/papers')
  await page.getByRole('button', { name: '创建试卷' }).click()
  await expect(page.locator('.el-dialog:visible')).toContainText('基本信息')
  await expect(page.locator('.el-dialog:visible')).toContainText('选择题目')
  await expect(page.locator('.el-dialog:visible')).toContainText('确认保存')

  await fillFormInput(page, '试卷名称', paperName)
  await selectFormOption(page, '课程', created.course.title)
  await page.locator('.el-dialog:visible .el-radio').filter({ hasText: '启用' }).click()
  await page.locator('.el-dialog:visible').getByRole('button', { name: '下一步' }).click()
  await expect(page.locator('.el-dialog:visible')).toContainText('题库')

  await page.locator('.el-dialog:visible .el-table__body-wrapper tr').filter({ hasText: created.question.content }).first().click()
  await expect(page.locator('.el-dialog:visible')).toContainText('已选题目 (1)')
  await page.locator('.el-dialog:visible .el-input-number input').last().fill('5')

  const paperDialog = page.locator('.el-dialog:visible').filter({ hasText: '已选题目' })
  await paperDialog.getByRole('button', { name: '添加自定义题目' }).click()
  const customQuestionDialog = page.locator('.el-dialog:visible').filter({ hasText: '添加自定义题目' })
  await fillFormInput(page, '题目内容', `自定义题目_${suffix}`)
  await customQuestionDialog.locator('.option-row input').nth(0).fill('正确选项')
  await customQuestionDialog.locator('.option-row input').nth(1).fill('错误选项')
  await selectFormOption(page, '正确答案', '正确选项')
  await customQuestionDialog.getByRole('button', { name: '添加到试卷' }).click()
  await expect(paperDialog).toContainText('已选题目 (2)')

  await page.locator('.el-dialog:visible').getByRole('button', { name: '下一步' }).click()
  await expect(page.locator('.el-dialog:visible')).toContainText(paperName)
  await expect(page.locator('.el-dialog:visible')).toContainText(created.course.title)

  await page.locator('.el-dialog:visible').getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-dialog:visible').filter({ hasText: '组卷检查' })).toBeVisible()
  await page.locator('.el-dialog:visible').getByRole('button', { name: '忽略问题并保存' }).click()
  await expectMessage(page, 'success', '保存成功')
  await expect(page.locator('.el-dialog:visible')).toHaveCount(0)
  created.paper = records((await apiCall('/api/papers')).data).find(item => item.name === paperName)
  expect(created.paper?.id).toBeTruthy()
  expect(created.paper.status).toBe('PUBLISHED')

  const examName = `分步流程考试_${suffix}`
  await page.goto('/exams')
  await page.waitForLoadState('networkidle')
  await page.getByRole('button', { name: '发布考试' }).click()
  const examDialog = page.locator('.el-dialog:visible').filter({ hasText: '发布对象' })
  await expect(examDialog).toContainText('发布对象')
  await expect(examDialog).toContainText('考试设置')
  await expect(examDialog).toContainText('确认发布')

  await fillFormInput(page, '考试名称', examName)
  await selectFormOption(page, '试卷', paperName)
  await selectFormOption(page, '教学班', created.class.className)
  await examDialog.getByRole('button', { name: '下一步' }).click()
  await expect(examDialog).toContainText('考试时长')

  await examDialog.locator('.el-input-number input').fill('60')
  const dateInputs = examDialog.locator('.el-date-editor input')
  await dateInputs.nth(0).fill(localDateTime(3600000).replace('T', ' '))
  await examDialog.getByRole('button', { name: '下一步' }).click()
  await expect(examDialog).toContainText(examName)
  await expect(examDialog).toContainText(created.class.className)

  await examDialog.getByRole('button', { name: '保存' }).click()
  await expectMessage(page, 'success', '保存成功')
  await expect(page.locator('.el-dialog:visible')).toHaveCount(0)
  created.exam = records((await apiCall('/api/exams')).data).find(item => item.name === examName)
  expect(created.exam?.id).toBeTruthy()
})
