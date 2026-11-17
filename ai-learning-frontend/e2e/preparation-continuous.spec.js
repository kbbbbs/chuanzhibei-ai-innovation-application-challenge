import { test, expect } from '@playwright/test'

// Synthetic responses only: one-run generation, optional questions, per-lesson recovery and the separate ready step.
const ok = data => ({ json: { code: '200', data } })
const outline = { courseDescription: '', chapters: [{ chapterKey: 'c1', title: '人工智能绪论', description: '', lessons: [
  { lessonKey: 'l1', title: '智能的定义', durationMinutes: 45 }, { lessonKey: 'l2', title: '图灵测试', durationMinutes: 45 },
] }] }
const draft = (overrides = {}, config = {}) => ({ id: 1, title: '人工智能导论', projectStatus: 'DRAFT', generationStatus: 'SUCCEEDED', version: 3,
  config: { title: '人工智能导论', categoryId: 1, audience: '本科生', includeQuestions: true, generationMode: 'CONTINUOUS', ...config },
  outline: null, lessons: [], questions: [], sourceFiles: [], sourcesReady: true, currentStage: 'CONFIG', ...overrides })

async function open(page, current, props = {}) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'teacher')
  })
  const calls = []
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const url = new URL(request.url()), path = url.pathname.replace(/^\/backend/, '')
    calls.push({ path, method: request.method(), query: Object.fromEntries(url.searchParams), body: request.postData() ? request.postDataJSON() : null })
    if (path === '/categories') return route.fulfill(ok([{ id: 1, name: '人工智能' }]))
    if (path === '/courses/12') return route.fulfill(ok({ id: 12, title: '人工智能导论', categoryId: 1 }))
    if (path === '/api/rag/courses/12/knowledge-base') return route.fulfill(ok({ id: 'course-library' }))
    if (path === '/api/preparations' && request.method() === 'GET') return route.fulfill(ok([{ id: 1, title: '人工智能导论', currentStage: current().currentStage }]))
    if (path === '/api/preparations/1') return route.fulfill(ok(current()))
    if (path.startsWith('/api/preparations/1/')) return route.fulfill(ok({ ...current(), generationStatus: 'SUCCEEDED' }))
    return route.fulfill(ok([]))
  })
  // Mount the retained editor directly because the legacy route now redirects to course assignment.
  const source = await (await page.request.get('/src/views/teacher/IntelligentResourceGen.vue')).text()
  const store = await (await page.request.get('/src/store/user.js')).text()
  const dep = name => (source + store).match(new RegExp('/node_modules/[^"\\s]*' + name + '\\.js\\?v=[a-z0-9]+'))?.[0]
  await page.route('**/__preparation-continuous', route => route.fulfill({ contentType: 'text/html', body: `<html><body><div id="app"></div><script type="module">
import {createApp} from '${dep('vue')}'
import {createPinia} from '${dep('pinia')}'
import ElementPlus from '${dep('element-plus')}'
import {createRouter,createMemoryHistory} from '${dep('vue-router')}'
import Component from '/src/views/teacher/IntelligentResourceGen.vue'
const app=createApp(Component, ${JSON.stringify(props.courseId ? { ...props, initialProjectId: 1 } : props)})
app.use(createPinia());app.use(ElementPlus);app.use(createRouter({history:createMemoryHistory(),routes:[]}));app.mount('#app')
</script></body></html>` }))
  await page.goto('/__preparation-continuous')
  if (props.courseId) await expect(page.getByRole('heading', { name: '设置生成内容' })).toBeVisible()
  else {
    await page.getByRole('combobox', { name: '继续最近的备课草稿' }).click()
    await page.getByRole('option', { name: /人工智能导论/ }).click()
  }
  return calls
}

test('course creation keeps question settings and the complete draft defaults without the delivery card', async ({ page }) => {
  const calls = await open(page, () => draft({ config: { targetCourseId: 12, audience: '本科生' } }), { courseId: 12, creationMode: true })
  await expect(page.getByTestId('generation-options')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '题目生成设置' })).toBeVisible()
  await page.getByRole('button', { name: '保存并生成完整草稿' }).click()
  await expect.poll(() => calls.filter(call => call.path.endsWith('/generate-all')).length).toBe(1)
  expect(calls.find(call => call.path.endsWith('/config'))?.body).toMatchObject({ includeQuestions: true, generationMode: 'CONTINUOUS' })
})

test('each preparation step opens independently without generating or applying incomplete content', async ({ page }) => {
  const calls = await open(page, () => draft({ config: { targetCourseId: 12, audience: '本科生' } }), { courseId: 12, creationMode: true })
  const steps = page.getByRole('navigation', { name: '备课步骤' })
  const outlineStep = steps.getByRole('button', { name: '章节骨架', exact: true })
  await outlineStep.getByText('2', { exact: true }).click()
  await expect(outlineStep).toHaveAttribute('aria-current', 'step')
  await expect(page.getByText('尚未生成章节骨架')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认骨架并生成课时' })).toBeDisabled()
  await steps.getByText('课时 Markdown', { exact: true }).click()
  await expect(page.getByText('尚未生成课时内容')).toBeVisible()
  await steps.getByRole('button', { name: '章节题目', exact: true }).press('Enter')
  await expect(page.getByText('尚未生成题目')).toBeVisible()
  await expect(steps.getByRole('button', { name: '生成完成', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '预览并应用到课程' })).toHaveCount(0)
  await steps.getByRole('button', { name: '生成设置', exact: true }).click()
  await expect(page.getByRole('heading', { name: '设置生成内容' })).toBeVisible()
  expect(calls.filter(call => call.method !== 'GET')).toEqual([])
})

test('generation progress leaves the manually opened preparation panel in view', async ({ page }) => {
  let current = draft({ currentStage: 'OUTLINE_REVIEW', generationStatus: 'RUNNING', outline })
  const calls = await open(page, () => current)
  const steps = page.getByRole('navigation', { name: '备课步骤' })
  const questionsStep = steps.getByRole('button', { name: '章节题目', exact: true })
  await questionsStep.click()
  await expect(page.getByText('尚未生成题目')).toBeVisible()
  const reads = calls.filter(call => call.path === '/api/preparations/1').length
  const lessons = outline.chapters[0].lessons.map(lesson => ({ ...lesson, contentMd: '# 草稿正文', status: 'GENERATED' }))
  current = { ...current, currentStage: 'LESSON_REVIEW', generationStatus: 'SUCCEEDED', lessons }
  await expect.poll(() => calls.filter(call => call.path === '/api/preparations/1').length).toBeGreaterThan(reads)
  await expect(page.getByText('可以离开页面，生成进度会保存在草稿中。')).toHaveCount(0)
  await expect(questionsStep).toHaveAttribute('aria-current', 'step')
  await expect(page.getByText('尚未生成题目')).toBeVisible()
  await steps.getByRole('button', { name: '课时 Markdown', exact: true }).click()
  await expect(page.locator('.markdown-editor .cm-content')).toHaveText('# 草稿正文')
  expect(calls.filter(call => call.method !== 'GET')).toEqual([])
})

test('the default flow generates the whole draft in one run and can leave out questions', async ({ page }) => {
  const calls = await open(page, () => draft())
  await page.getByTestId('generation-options').getByText('章节题目', { exact: true }).click()
  await expect(page.getByText('题目生成设置')).toHaveCount(0)
  await page.getByRole('button', { name: /保存并生成完整草稿/ }).click()
  await expect.poll(() => calls.filter(call => call.path === '/api/preparations/1/generate-all').length).toBe(1)
  expect(calls.find(call => call.path === '/api/preparations/1/config').body).toMatchObject({ includeQuestions: false, generationMode: 'CONTINUOUS' })
  expect(calls.some(call => call.path.endsWith('/generate-outline'))).toBe(false)
})

test('a failed lesson is marked, can be filled in or rewritten alone, and the previous version can be restored', async ({ page }) => {
  const lessons = [
    { lessonKey: 'l1', title: '智能的定义', contentMd: '第二版正文', previousContentMd: '第一版正文', status: 'GENERATED', durationMinutes: 45 },
    { lessonKey: 'l2', title: '图灵测试', contentMd: '', status: 'FAILED', errorMessage: '模型暂不可用', durationMinutes: 45 },
  ]
  const calls = await open(page, () => draft({ currentStage: 'LESSON_REVIEW', generationStatus: 'FAILED', errorMessage: '1 个课时未生成成功：模型暂不可用。已完成的内容已保存', outline, lessons }))
  await expect(page.getByText('1 个课时未生成成功')).toBeVisible()
  await expect(page.getByRole('tab', { name: '⚠ 图灵测试' })).toBeVisible()
  await page.getByTestId('fill-missing-lessons').click()
  await expect.poll(() => calls.find(call => call.path === '/api/preparations/1/generate-lessons')?.query.mode).toBe('missing')

  await page.getByTestId('rewrite-lesson-l1').click()
  await expect.poll(() => calls.filter(call => call.path === '/api/preparations/1/lessons/l1/regenerate').length).toBe(1)

  await page.getByText('上一版本', { exact: true }).click()
  await page.getByRole('button', { name: '恢复为上一版本' }).click()
  await expect.poll(() => calls.filter(call => call.path === '/api/preparations/1/lessons' && call.method === 'PUT').at(-1)?.body?.[0]?.contentMd).toBe('第一版正文')
})

test('without questions, confirming lessons goes straight to the ready check instead of generating questions', async ({ page }) => {
  const lessons = outline.chapters[0].lessons.map(lesson => ({ ...lesson, contentMd: `${lesson.title}正文`, status: 'GENERATED' }))
  const calls = await open(page, () => draft({ currentStage: 'LESSON_REVIEW', outline, lessons }, { includeQuestions: false }))
  await page.getByRole('button', { name: '确认课时，进入发布' }).click()
  await expect.poll(() => calls.filter(call => call.path === '/api/preparations/1/ready').length).toBe(1)
  expect(calls.some(call => call.path.endsWith('/generate-questions'))).toBe(false)
})

test('partially generated lessons with null content remain navigable through polling and completion', async ({ page }) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const lessons = Array.from({ length: 18 }, (_, index) => ({
    lessonKey: `l${index + 1}`, chapterKey: 'c1', title: `课时 ${index + 1}`, durationMinutes: 45,
    contentMd: index === 0 ? '# 已生成正文' : null, status: index === 0 ? 'GENERATED' : 'PENDING',
  }))
  let current = draft({ currentStage: 'LESSON_REVIEW', generationStatus: 'RUNNING', outline, lessons })
  const calls = await open(page, () => current)
  const editor = page.locator('.markdown-editor .cm-content')
  await expect(editor).toHaveCount(1)
  await expect(editor).toHaveText('# 已生成正文')
  await expect(editor).toHaveAttribute('aria-readonly', 'true')
  await editor.click()
  await editor.press('x')
  await expect(editor).toHaveText('# 已生成正文')
  await page.getByRole('tab', { name: '⚠ 课时 2', exact: true }).click()
  await expect(editor).toHaveText('')
  const reads = calls.filter(call => call.path === '/api/preparations/1').length
  await expect.poll(() => calls.filter(call => call.path === '/api/preparations/1').length).toBeGreaterThan(reads)
  await expect(page.getByRole('tab', { name: '⚠ 课时 2', exact: true })).toHaveAttribute('aria-selected', 'true')
  expect(calls.filter(call => call.method === 'PUT')).toHaveLength(0)

  current = { ...current, generationStatus: 'SUCCEEDED' }
  await expect(editor).toBeEditable()
  await editor.fill('人工补充课时正文')
  await expect.poll(() => calls.findLast(call => call.path === '/api/preparations/1/lessons')?.body?.[1]?.contentMd).toBe('人工补充课时正文')
  await page.getByRole('button', { name: '返回骨架', exact: true }).click()
  await expect(page.getByRole('button', { name: '返回配置', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '返回配置', exact: true }).click()
  await expect(page.getByRole('button', { name: /保存并生成完整草稿/ })).toBeVisible()
  expect(errors).toEqual([])
})

test('a locally restored legacy lesson with null content can be edited and saved', async ({ page }) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => {
    localStorage.setItem('eduloop:preparation:1:LESSON_REVIEW', JSON.stringify({ at: Date.now(), value: [
      { lessonKey: 'l1', title: '恢复的课时', contentMd: null, status: 'FAILED' },
    ] }))
  })
  const calls = await open(page, () => draft({ currentStage: 'LESSON_REVIEW', outline, lessons: [
    { lessonKey: 'l1', title: '服务器课时', contentMd: '已有正文', status: 'GENERATED' },
  ] }))
  const editor = page.locator('.markdown-editor .cm-content')
  await expect(page.getByPlaceholder('课时名称')).toHaveValue('恢复的课时')
  await expect(editor).toHaveText('')
  await editor.fill('恢复后编辑')
  await expect.poll(() => calls.findLast(call => call.path === '/api/preparations/1/lessons')?.body?.[0]?.contentMd).toBe('恢复后编辑')
  expect(errors).toEqual([])
})

test('outline generation allows viewing while keeping unsavable edits disabled', async ({ page }) => {
  const calls = await open(page, () => draft({ currentStage: 'OUTLINE_REVIEW', generationStatus: 'RUNNING', outline }))
  await expect(page.getByPlaceholder('章节名称')).toBeDisabled()
  await expect(page.getByPlaceholder('课时名称').first()).toBeDisabled()
  await expect(page.getByRole('button', { name: '添加章节', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '确认骨架并生成课时', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '返回配置', exact: true })).toBeEnabled()
  expect(calls.filter(call => call.method === 'PUT')).toHaveLength(0)
})
