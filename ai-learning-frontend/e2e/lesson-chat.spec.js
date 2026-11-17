import { test, expect } from '@playwright/test'

// Synthetic responses only: lesson Q&A draws on the course's granted materials and resumes per lesson.
const ok = data => ({ json: { code: '200', data } })

async function fixture(page, { materials }) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-lesson-chat')
    localStorage.setItem('userId', '3')
    localStorage.setItem('roleCode', 'student')
  })
  const calls = []
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    calls.push({ path, method: request.method(), body: request.postData() ? request.postDataJSON() : null })
    if (path === '/courses/1') return route.fulfill(ok({ id: 1, title: '人工智能导论' }))
    if (path === '/courses/1/chapters') return route.fulfill(ok([{ id: 1, title: '第一章' }]))
    if (path === '/chapters/1/lessons') return route.fulfill(ok([
      { id: 1, title: '什么是智能', resourceType: 'text', content: '第一课正文' },
      { id: 2, title: '图灵测试', resourceType: 'text', content: '第二课正文' },
    ]))
    if (path === '/api/rag/courses/1/materials') return route.fulfill(ok(materials))
    if (path === '/api/rag/sessions' && request.method() === 'GET') return route.fulfill(ok([
      { id: 's-lesson-2', title: '课时快问', courseId: 1, lessonId: 2, knowledgeBaseIds: [] },
    ]))
    if (path === '/api/rag/sessions/s-lesson-2/messages') return route.fulfill(ok([
      { role: 'user', content: '图灵测试测什么？' }, { role: 'assistant', content: '上次的回答：判断机器能否表现出与人类相当的智能。', citations: [] },
    ]))
    if (path === '/api/rag/sessions' && request.method() === 'POST') return route.fulfill(ok({ id: 's-new', courseId: 1, lessonId: 1 }))
    if (path === '/api/rag/chat/stream') {
      const result = { answer: '依据讲义回答。', citations: [], mode: 'bm25', status: 'ANSWERED', sessionId: 's-new' }
      return route.fulfill({ contentType: 'text/event-stream', body: `event: complete\ndata: ${JSON.stringify(result)}\n\n` })
    }
    return route.fulfill(ok([]))
  })
  return calls
}

test('a student asks about a lesson without choosing a knowledge base and the answer uses the course scope', async ({ page }) => {
  const calls = await fixture(page, { materials: [{ document_id: 'd1', title: '人工智能导论讲义.pdf', document_version: 1, searchable: 1 }] })
  await page.goto('/course-learn/1?classId=1&lessonId=1')
  await page.getByRole('tab', { name: '问答', exact: true }).click()
  const chat = page.locator('.quick-chat-card')
  await expect(chat.getByTestId('lesson-chat-scope')).toContainText('依据课程资料回答：《人工智能导论讲义.pdf》')
  await chat.getByPlaceholder('针对本课时提问').fill('智能如何定义？')
  await chat.getByRole('button', { name: '发送' }).click()
  await expect(chat).toContainText('依据讲义回答。')
  expect(calls.find(call => call.path === '/api/rag/sessions' && call.method === 'POST').body).toMatchObject({ courseId: 1, lessonId: 1 })
  expect(calls.find(call => call.path === '/api/rag/chat/stream').body).toMatchObject({ courseId: 1, lessonId: 1, knowledgeBaseIds: [], sessionId: 's-new' })
})

test('returning to a lesson resumes its conversation', async ({ page }) => {
  await fixture(page, { materials: [{ document_id: 'd1', title: '讲义.pdf', document_version: 1, searchable: 1 }] })
  await page.goto('/course-learn/1?classId=1&lessonId=2')
  await page.getByRole('tab', { name: '问答', exact: true }).click()
  await expect(page.locator('.quick-chat-card')).toContainText('上次的回答')
})

test('a course without granted materials says so instead of offering an unrelated library', async ({ page }) => {
  await fixture(page, { materials: [] })
  await page.goto('/course-learn/1?classId=1&lessonId=1')
  await page.getByRole('tab', { name: '问答', exact: true }).click()
  const chat = page.locator('.quick-chat-card')
  await expect(chat.getByTestId('lesson-chat-scope')).toContainText('本课程还没有可用于问答的资料')
  await expect(chat.getByPlaceholder('针对本课时提问')).toBeDisabled()
})
