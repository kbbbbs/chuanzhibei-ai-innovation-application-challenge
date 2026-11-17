import { test, expect } from '@playwright/test'

// Synthetic responses only: knowledge-base question generation runs as a task whose draft survives closing the dialog.
const kbId = '640d254a-e30b-453c-9b0e-d7b94d06111b'
const ok = data => ({ json: { code: '200', data } })
const question = (n, extra = {}) => ({ draftId: `d${n}`, content: `题目 ${n}`, type: 'SHORT_ANSWER', difficulty: 'MEDIUM', score: 5, answers: [{ answerContent: '答案', sortOrder: 1 }], ...extra })
const task = (overrides = {}) => ({ id: 't1', knowledgeBaseId: kbId, courseId: 3, status: 'READY', requested: 2, generated: 2, saved: 0,
  createdAt: '2026-11-16T10:00:00', questions: [question(1), question(2)], ...overrides })

async function open(page, handlers) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-question-task')
    localStorage.setItem('userId', '7')
    localStorage.setItem('roleCode', 'teacher')
  })
  const calls = []
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    calls.push({ path, method: request.method(), body: request.postData() ? request.postDataJSON() : null })
    const handled = await handlers(route, path, request)
    if (handled) return
    if (path === '/api/rag/knowledge-bases') return route.fulfill(ok([{ id: kbId, name: '人工智能讲义库', owner_id: 7, read_only: false, knowledge_count: 0 }]))
    if (path === '/courses') return route.fulfill(ok({ records: [{ id: 3, title: '人工智能导论' }] }))
    return route.fulfill(ok([]))
  })
  await page.goto('/teacher-knowledge-base')
  await page.getByRole('button', { name: '根据资料出题' }).click()
  return calls
}

test('a started task keeps generating after the dialog closes and its draft is reopened from the task list', async ({ page }) => {
  let reads = 0
  const calls = await open(page, async (route, path, request) => {
    if (path === '/api/questions/generation-tasks' && request.method() === 'POST') return route.fulfill(ok(task({ status: 'RUNNING', generated: 0, questions: [] }))), true
    if (path === '/api/questions/generation-tasks') return route.fulfill(ok(reads ? [task()] : [])), true
    if (path === '/api/questions/generation-tasks/t1') { reads++; return route.fulfill(ok(reads < 2 ? task({ status: 'RUNNING', generated: 1, questions: [question(1)] }) : task())), true }
    if (path === '/api/questions/generation-tasks/t1/save') return route.fulfill(ok({ ...task({ status: 'SAVED', saved: 2 }), questions: [question(1, { saved: true }), question(2, { saved: true })], savedNow: 2, failedNow: 0 })), true
    return false
  })
  const dialog = page.getByRole('dialog', { name: '根据知识库出题' })
  await dialog.getByText('请选择课程').click()
  await page.getByRole('option', { name: '人工智能导论' }).click()
  await dialog.getByRole('button', { name: '开始生成' }).click()
  await expect(dialog).toContainText('可以关闭窗口，生成会在后台继续')
  await expect(dialog).toContainText('题目 2')
  await dialog.getByRole('button', { name: '关闭', exact: true }).click()

  await page.getByRole('button', { name: '根据资料出题' }).click()
  await expect(dialog.getByTestId('gen-task-list')).toContainText('已生成 2/2 题')
  await dialog.getByTestId('gen-task-list').getByRole('button', { name: '打开' }).click()
  await expect(dialog).toContainText('题目 1')
  await dialog.getByRole('button', { name: '保存 2 道题到题库' }).click()
  await expect(dialog).toContainText('本次保存 2 条')
  expect(calls.some(call => call.path === '/api/questions/batch')).toBe(false)
})

test('an incomplete task keeps its questions and offers to generate only the remainder', async ({ page }) => {
  const calls = await open(page, async (route, path, request) => {
    if (path === '/api/questions/generation-tasks' && request.method() === 'GET') return route.fulfill(ok([task({ status: 'INCOMPLETE', requested: 5, generated: 2, errorMessage: '模型暂不可用' })])), true
    if (path === '/api/questions/generation-tasks/t1') return route.fulfill(ok(task({ status: 'INCOMPLETE', requested: 5, generated: 2, errorMessage: '模型暂不可用' }))), true
    if (path === '/api/questions/generation-tasks/t1/resume') return route.fulfill(ok(task({ status: 'RUNNING', requested: 5 }))), true
    return false
  })
  const dialog = page.getByRole('dialog', { name: '根据知识库出题' })
  await dialog.getByTestId('gen-task-list').getByRole('button', { name: '打开' }).click()
  await expect(dialog).toContainText('已生成 2/5 道题：模型暂不可用')
  await expect(dialog).toContainText('题目 2')
  await dialog.getByTestId('resume-gen-task').click()
  await expect.poll(() => calls.filter(call => call.path === '/api/questions/generation-tasks/t1/resume').length).toBe(1)
})
