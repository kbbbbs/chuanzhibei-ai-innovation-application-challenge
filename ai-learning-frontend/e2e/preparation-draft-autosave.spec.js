import { test, expect } from '@playwright/test'

// Synthetic responses only: edits in a review stage are saved on their own and survive a failed save and a refresh.
const ok = data => ({ json: { code: '200', data } })
const outline = { courseDescription: '', chapters: [{ chapterKey: 'c1', title: '人工智能绪论', description: '', lessons: [{ lessonKey: 'l1', title: '智能的定义', objectives: '', durationMinutes: 45 }] }] }
const draft = (overrides = {}) => ({ id: 1, title: '人工智能导论', currentStage: 'OUTLINE_REVIEW', projectStatus: 'DRAFT', generationStatus: 'SUCCEEDED',
  config: { title: '人工智能导论', categoryId: 1, audience: '本科生' }, outline, lessons: [], questions: [], version: 3, ...overrides })

test('outline edits save as a draft without approving the stage, and a failed save is restored after refresh', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-acceptance')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', 'teacher')
  })
  const saves = []
  let failing = false, generated = 0
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    if (path === '/api/preparations' && request.method() === 'GET') return route.fulfill(ok([{ id: 1, title: '人工智能导论', currentStage: 'OUTLINE_REVIEW' }]))
    if (path === '/api/preparations/1') return route.fulfill(ok(draft()))
    if (path === '/api/preparations/1/outline') {
      if (failing) return route.fulfill({ status: 503, json: { code: '503', msg: 'down' } })
      saves.push(request.postDataJSON())
      return route.fulfill(ok(draft({ outline: request.postDataJSON(), version: 4 })))
    }
    if (path.endsWith('/generate-lessons')) { generated++; return route.fulfill(ok(draft())) }
    return route.fulfill(ok([]))
  })
  // The public resource-gen route now opens the course picker; mount the editor it used to display.
  const source = await (await page.request.get('/src/views/teacher/IntelligentResourceGen.vue')).text()
  const store = await (await page.request.get('/src/store/user.js')).text()
  const dependency = name => (source + store).match(new RegExp('/node_modules/[^"\\s]*' + name + '\\.js\\?v=[a-z0-9]+'))?.[0]
  await page.route('**/__preparation-draft-autosave', route => route.fulfill({ contentType: 'text/html', body: `<html><body><div id="app"></div><script type="module">
import {createApp} from '${dependency('vue')}'
import {createPinia} from '${dependency('pinia')}'
import ElementPlus from '${dependency('element-plus')}'
import {createRouter,createMemoryHistory} from '${dependency('vue-router')}'
import Component from '/src/views/teacher/IntelligentResourceGen.vue'
const app = createApp(Component)
app.use(createPinia())
app.use(ElementPlus)
app.use(createRouter({history:createMemoryHistory(),routes:[]}))
app.mount('#app')
</script></body></html>` }))
  await page.goto('/__preparation-draft-autosave')
  await page.getByRole('combobox', { name: '继续最近的备课草稿' }).click()
  await page.getByRole('option', { name: /人工智能导论/ }).click()
  const title = page.getByPlaceholder('章节名称')
  await expect(title).toHaveValue('人工智能绪论')
  await title.fill('人工智能绪论（修订）')
  const status = page.getByTestId('draft-save-status')
  await expect(status).toContainText('已自动保存')
  expect(saves.at(-1).chapters[0].title).toBe('人工智能绪论（修订）')
  expect(generated).toBe(0)

  failing = true
  await title.fill('人工智能绪论（二次修订）')
  await expect(status).toContainText('保存失败')
  failing = false
  page.on('dialog', dialog => dialog.accept())
  await page.reload()
  await page.getByRole('combobox', { name: '继续最近的备课草稿' }).click()
  await page.getByRole('option', { name: /人工智能导论/ }).click()
  await expect(page.getByPlaceholder('章节名称')).toHaveValue('人工智能绪论（二次修订）')
  await expect.poll(() => saves.at(-1)?.chapters?.[0]?.title).toBe('人工智能绪论（二次修订）')
})
