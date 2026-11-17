import { test, expect } from '@playwright/test'

// Synthetic responses only: notes and answers are kept without a save click and survive refreshes and outages.
const ok = data => ({ code: '200', data })
async function login(page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-autosave')
    localStorage.setItem('userId', '3')
    localStorage.setItem('roleCode', 'student')
  })
}

test('lesson notes save themselves, survive a failed save and a refresh, and catch up after going offline', async ({ page, context }) => {
  await login(page)
  const saves = []
  let failSaves = false
  await page.route('**/backend/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
    const method = route.request().method()
    if (path === '/lessons/1/note' && method === 'PUT') {
      if (failSaves) return route.fulfill({ status: 500, json: { code: '500', msg: 'down' } })
      saves.push(route.request().postDataJSON())
      return route.fulfill({ json: ok(null) })
    }
    if (path === '/lessons/1/note') return route.fulfill({ json: ok({ title: '算法基础', contentMd: '服务器上的笔记' }) })
    if (path === '/courses/1') return route.fulfill({ json: ok({ id: 1, title: '算法学习课程' }) })
    if (path === '/courses/1/chapters') return route.fulfill({ json: ok([{ id: 1, title: '第一章' }]) })
    if (path === '/chapters/1/lessons') return route.fulfill({ json: ok([{ id: 1, title: '算法基础', resourceType: 'text', content: '课程正文' }]) })
    return route.fulfill({ json: ok([]) })
  })
  await page.goto('/course-learn/1?classId=1&lessonId=1')
  const editor = page.locator('.note-editor [contenteditable="true"]')
  const status = page.getByTestId('note-save-status')
  await expect(editor).toHaveText('服务器上的笔记')
  await editor.click()
  await page.keyboard.press('End')
  await page.keyboard.type('，第一次补充')
  await expect(status).toContainText('已自动保存')
  expect(saves.at(-1).contentMd).toBe('服务器上的笔记，第一次补充')

  failSaves = true
  await page.keyboard.type('，第二次补充')
  await expect(status).toContainText('保存失败')
  await page.reload()
  await expect(editor).toHaveText('服务器上的笔记，第一次补充，第二次补充')

  failSaves = false
  await expect.poll(() => saves.at(-1)?.contentMd).toBe('服务器上的笔记，第一次补充，第二次补充')
  await context.setOffline(true)
  await editor.click()
  await page.keyboard.press('End')
  await page.keyboard.type('，离线补充')
  await expect(status).toContainText('网络断开')
  await context.setOffline(false)
  await expect.poll(() => saves.at(-1)?.contentMd).toBe('服务器上的笔记，第一次补充，第二次补充，离线补充')
  await expect(status).toContainText('已自动保存')
})

test('exam answers are drafted automatically, restored after a refresh, and never submitted by autosave', async ({ page }) => {
  await login(page)
  const drafts = [], submits = []
  let failDrafts = true
  await page.route('**/backend/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
    if (path === '/api/exams/5') return route.fulfill({ json: ok({ id: 5, name: '随堂测验', durationMinutes: 30 }) })
    if (path === '/api/exams/5/submissions/start') return route.fulfill({ json: ok({ id: 9 }) })
    if (path === '/api/submissions/9/student') return route.fulfill({ json: ok({ id: 9, startedAt: new Date().toISOString(), answers: [
      { id: 1, paperQuestionId: 11, questionType: 'SHORT_ANSWER', questionContent: '简述梯度下降。', score: 5 },
    ] }) })
    if (path === '/api/submissions/9/answers') {
      if (failDrafts) return route.fulfill({ status: 503, json: { code: '503', msg: 'down' } })
      drafts.push(route.request().postDataJSON())
      return route.fulfill({ json: ok(null) })
    }
    if (/\/submit$|\/auto-submit$/.test(path)) { submits.push(path); return route.fulfill({ json: ok(null) }) }
    return route.fulfill({ json: ok([]) })
  })
  await page.goto('/exam-taking/5')
  const answer = page.getByLabel('第 1 题答案')
  const status = page.getByTestId('answer-save-status')
  await expect(status).toContainText('作答会自动暂存')
  await answer.fill('沿负梯度方向更新参数')
  await expect(status).toContainText('保存失败')

  failDrafts = false
  await page.reload()
  await expect(page.getByLabel('第 1 题答案')).toHaveValue('沿负梯度方向更新参数')
  await expect.poll(() => drafts.at(-1)?.answers?.[0]?.answerContent).toBe('沿负梯度方向更新参数')
  await expect(status).toContainText('已自动保存')
  expect(submits).toEqual([])
})
