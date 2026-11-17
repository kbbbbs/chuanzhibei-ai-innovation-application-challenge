import { test, expect } from '@playwright/test'

const ok = data => ({ code: '200', data })
const draft = (id, stage) => ({
  id, currentStage: stage, projectStatus: 'DRAFT', generationStatus: 'SUCCEEDED', config: { title: `草稿${id}` },
  outline: { chapters: [{ chapterKey: `chapter-${id}`, title: '示例章节', lessons: [{ lessonKey: `lesson-${id}`, title: '示例课时' }] }] },
  lessons: [{ lessonKey: `lesson-${id}`, chapterKey: `chapter-${id}`, title: '示例课时', contentMd: '# 已完成正文', status: 'GENERATED' }],
  questions: [{ chapterKey: `chapter-${id}`, chapterTitle: '示例章节', status: 'GENERATED', questions: [{ questionKey: `question-${id}`, type: 'SHORT_ANSWER', content: '示例问题', answer: '示例答案' }] }],
})

async function setup(page, handler) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'teacher')
  })
  await page.route('**/backend/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
    if (await handler(route, path)) return
    const data = path === '/api/preparations'
      ? [{ id: 1, title: '草稿一', currentStage: 'READY_TO_PUBLISH' }, { id: 2, title: '草稿二', currentStage: 'READY_TO_PUBLISH' }]
      : /^\/api\/preparations\/[12]$/.test(path) ? draft(Number(path.at(-1)), 'READY_TO_PUBLISH') : []
    await route.fulfill({ json: ok(data) })
  })
  // The legacy resource route now redirects to course creation. Exercise the retained editor directly.
  const source = await (await page.request.get('/src/views/teacher/IntelligentResourceGen.vue')).text()
  const store = await (await page.request.get('/src/store/user.js')).text()
  const dependency = name => (source + store).match(new RegExp('/node_modules/[^"\\s]*' + name + '\\.js\\?v=[a-z0-9]+'))?.[0]
  await page.route('**/__preparation-races', route => route.fulfill({ contentType: 'text/html', body: `<html><body><div id="app"></div><script type="module">
import {createApp} from '${dependency('vue')}'
import {createPinia} from '${dependency('pinia')}'
import ElementPlus from '${dependency('element-plus')}'
import {createRouter,createMemoryHistory} from '${dependency('vue-router')}'
import Component from '/src/views/teacher/IntelligentResourceGen.vue'
const app=createApp(Component)
app.use(createPinia());app.use(ElementPlus);app.use(createRouter({history:createMemoryHistory(),routes:[]}));app.mount('#app')
</script></body></html>` }))
  await page.goto('/__preparation-races')
}

async function selectDraft(page, name) {
  await page.getByRole('combobox', { name: '继续最近的备课草稿' }).click()
  await page.getByRole('option', { name: new RegExp(name) }).click()
}

test('a delayed publish cannot mark a different draft published', async ({ page }) => {
  let release
  const pending = new Promise(resolve => { release = resolve })
  let publishes = 0
  let readsB = 0
  let published = false
  await setup(page, async (route, path) => {
    if (path === '/api/preparations/2') readsB++
    if (path === '/api/preparations/1' && published) {
      await route.fulfill({ json: ok({ ...draft(1, 'PUBLISHED'), projectStatus: 'PUBLISHED', publishedCourseId: 99 }) })
      return true
    }
    if (path !== '/api/preparations/1/publish') return false
    publishes++
    await pending
    published = true
    await route.fulfill({ json: ok({ courseId: 99, chaptersCreated: 1, lessonsCreated: 1, questionsCreated: 1 }) })
    return true
  })
  await selectDraft(page, '草稿一')
  await page.getByRole('button', { name: '发布到课程管理' }).click()
  await expect.poll(() => publishes).toBe(1)
  const picker = page.getByRole('combobox', { name: '继续最近的备课草稿' })
  if (await picker.isEnabled()) {
    await selectDraft(page, '草稿二')
    await expect.poll(() => readsB).toBe(1)
    const response = page.waitForResponse(response => response.url().includes('/api/preparations/1/publish'))
    release()
    await (await response).finished()
    await expect(page.getByRole('button', { name: '发布到课程管理' })).toBeEnabled()
    await expect(page.locator('.el-result').getByText('课程草稿已发布', { exact: true })).toHaveCount(0)
    expect(readsB).toBe(1)
  } else {
    const response = page.waitForResponse(response => response.url().includes('/api/preparations/1/publish'))
    release()
    await (await response).finished()
    await expect(page.locator('.el-result').getByText('课程草稿已发布', { exact: true })).toBeVisible()
  }
  expect(publishes).toBe(1)
})

for (const [stage, button, endpoint] of [
  ['OUTLINE_REVIEW', '重新生成', 'generate-outline'],
  ['LESSON_REVIEW', '重新生成未修改的课时', 'generate-lessons'],
  ['QUESTION_REVIEW', '重新生成未修改的章节题目', 'generate-questions'],
]) {
  test(`${endpoint} network failure is handled`, async ({ page }) => {
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    let attempts = 0
    await setup(page, async (route, path) => {
      if (path === '/api/preparations/1') {
        await route.fulfill({ json: ok(draft(1, stage)) })
        return true
      }
      if (path === `/api/preparations/1/${endpoint}`) {
        attempts++
        await route.fulfill({ status: 500, json: { code: '500', msg: '生成失败' } })
        return true
      }
      return false
    })
    await selectDraft(page, '草稿一')
    const response = page.waitForResponse(response => response.url().includes(`/api/preparations/1/${endpoint}`))
    await page.getByRole('button', { name: button, exact: true }).click()
    await (await response).finished()
    await expect.poll(() => attempts).toBe(1)
    await expect(page.getByText(/生成失败|请求失败|服务器|服务暂时不可用/).first()).toBeVisible()
    await expect(page.getByRole('button', { name: button, exact: true })).toBeEnabled()
    expect(errors).toEqual([])
  })
}
