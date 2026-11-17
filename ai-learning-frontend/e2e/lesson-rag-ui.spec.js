import { test, expect } from '@playwright/test'

// Controlled HTTP responses validate page wiring, not actual model quality.
async function fixture(page, { empty = false, delayed = false, teacher = false, history = false, failOnce = false } = {}) {
  const calls = []
  let granted = !teacher
  let chatAttempts = 0
  await page.addInitScript(role => {
    localStorage.setItem('token', 'lesson-ui-test')
    localStorage.setItem('userId', role === 'teacher' ? '7' : '2')
    localStorage.setItem('roleCode', role)
    localStorage.setItem('username', '课时核验')
  }, teacher ? 'teacher' : 'student')
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url())
    if (url.port !== '8080') return route.continue()
    const path = url.pathname.startsWith('/api/') ? url.pathname : '/api' + url.pathname
    calls.push({ path, method: request.method(), body: request.postData() ? request.postDataJSON() : null })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/api/courses/1') return ok({ id: 1, title: '算法课程' })
    if (path === '/api/courses') return ok({ records: [{ id: 1, title: '算法课程', teacherId: 7 }] })
    if (path === '/api/courses/1/chapters') return ok([{ id: 1, title: '基础' }])
    if (path === '/api/chapters/1/lessons') return ok([{ id: 11, title: '搜索算法', resourceType: 'text', content: '二分查找每次将搜索区间缩小一半。' }, { id: 12, title: '排序算法', resourceType: 'text', content: '排序将元素按顺序排列。' }])
    if (path === '/api/courses/1/experience') return ok({ textbook: [] })
    if (path === '/api/rag/documents/d1/grants' && request.method() === 'POST') { granted = true; return ok({}) }
    if (path === '/api/rag/documents/d1/grants/1/versions/1' && request.method() === 'DELETE') { granted = false; return ok({}) }
    if (path === '/api/rag/courses/1/documents') return ok(empty || !granted ? [] : [{ id: 'd1', documentId: 'd1', documentVersion: 1, title: '算法讲义' }])
    if (path === '/api/rag/courses/1/documents/d1/versions/1') return ok({ documentId: 'd1', documentVersion: 1, title: '算法讲义', chunks: [{ chunkId: 'c1', content: '二分查找每轮减半。', page: 3 }] })
    if (path === '/api/rag/knowledge-bases') return ok(teacher ? [{ id: 'kb1', owner_id: 7, name: '教师资料', knowledge_count: 1 }] : [])
    if (path === '/api/rag/knowledge-bases/kb1/documents') return ok([{ id: 'd1', title: '算法讲义', file_name: '算法.pdf', document_version: 1, parse_status: 'success', chunk_count: 1 }])
    if (path === '/api/rag/sessions/saved/messages') return ok([{ role: 'user', content: '已保存的问题', selectedText: '已保存选文' }, { role: 'assistant', content: '已保存回答', mode: 'selection', actualMode: 'NONE' }])
    if (path === '/api/rag/sessions') return ok(history ? [{ id: 'saved', title: '历史课时问答', courseId: 1, lessonId: 11, mode: 'course' }] : [])
    if (path === '/api/rag/chat/stream') {
      chatAttempts++
      if (failOnce && chatAttempts === 1) return route.fulfill({ status: 503, json: { msg: '模型暂时不可用，请重试' } })
      if (delayed) await new Promise(resolve => setTimeout(resolve, 800))
      const result = { sessionId: 's1', answer: empty ? '选中文本说明了逐次减半。' : '二分查找每轮减半。[1]', mode: empty ? 'selection' : 'course', status: empty ? 'SELECTION_ONLY' : 'ANSWERED', actualMode: empty ? 'NONE' : 'HYBRID', citations: empty ? [] : [{ chunkId: 'c1', documentId: 'd1', documentVersion: 1, title: '算法讲义', content: '二分查找每轮减半。', page: 3, citation: '[1]' }] }
      return route.fulfill({ contentType: 'text/event-stream', body: `event: complete\ndata: ${JSON.stringify(result)}\n\nevent: done\ndata: {}\n\n` }).catch(() => {})
    }
    if (path.includes('/notes')) return ok({ title: '笔记', contentMd: '已有笔记' })
    return ok([])
  })
  await page.goto(teacher ? '/teacher-knowledge-base' : '/course-learn/1?classId=1&lessonId=11')
  return calls
}

async function selectLessonText(page) {
  await page.locator('.lesson-main-card .page-block').first().evaluate(el => {
    const paragraph = [...el.querySelectorAll('p')].find(p => p.textContent.includes('二分查找'))
    const range = document.createRange()
    range.selectNodeContents(paragraph)
    const selection = window.getSelection()
    selection.removeAllRanges()
    selection.addRange(range)
    paragraph.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
  })
  await page.getByRole('button', { name: '解释这段', exact: true }).click()
}

test('selected text survives focus and is sent with current course RAG scope', async ({ page }) => {
  const calls = await fixture(page)
  await selectLessonText(page)
  await expect(page.locator('.selection-card')).toContainText('二分查找')
  await page.getByPlaceholder('输入问题，或直接解释选中文本').fill('为什么每次减半？')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.quick-chat-card .chat-message.assistant')).toContainText('二分查找每轮减半。')
  const sent = calls.find(call => call.path === '/api/rag/chat/stream').body
  expect(sent.courseId).toBe(1)
  expect(sent.lessonId).toBe(11)
  expect(sent.selectedText).toContain('二分查找')
  expect(sent.query).toBe('为什么每次减半？')
  expect(sent.knowledgeBaseIds || []).toEqual([])
  expect(calls.filter(call => call.path === '/api/rag/sessions' && call.method === 'POST')).toHaveLength(0)
  await page.locator('.quick-chat-card').getByRole('button', { name: /算法讲义/ }).click()
  await expect(page.locator('.source-text')).toContainText('二分查找每轮减半。')
  expect(calls.some(call => call.path === '/api/rag/chunks/c1')).toBe(false)
})

test('empty course still explains selected text with explicit selection-only mode', async ({ page }) => {
  await fixture(page, { empty: true })
  await selectLessonText(page)
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.quick-chat-card .chat-message.assistant')).toContainText('仅依据选中文本')
  await expect(page.locator('.quick-chat-card .rag-citations')).toHaveCount(0)
})

test('switching lessons cancels old answer and clears selected text', async ({ page }) => {
  await fixture(page, { delayed: true })
  await selectLessonText(page)
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await page.getByText('排序算法', { exact: true }).click()
  await page.getByRole('tab', { name: '问答', exact: true }).click()
  await page.waitForTimeout(1000)
  await expect(page.locator('.quick-chat-card .chat-message')).toHaveCount(0)
  await expect(page.locator('.selection-card')).toHaveCount(0)
})

test('teacher authorizes document to a course with original download disabled', async ({ page }) => {
  const calls = await fixture(page, { teacher: true })
  await page.getByRole('button', { name: '课程授权', exact: true }).click()
  await page.getByRole('combobox', { name: '选择课程' }).press('Enter')
  await page.getByRole('option', { name: '算法课程', exact: true }).click()
  await page.getByRole('button', { name: '授权阅读', exact: true }).click()
  await expect.poll(() => calls.some(call => call.path === '/api/rag/documents/d1/grants')).toBe(true)
  expect(calls.find(call => call.path === '/api/rag/documents/d1/grants').body).toEqual({ courseId: 1, documentVersion: 1, readExcerpt: true, downloadAllowed: false })
  await page.getByRole('button', { name: '撤销授权', exact: true }).click()
  await expect(page.getByRole('button', { name: '授权阅读', exact: true })).toBeVisible()
  expect(calls.some(call => call.path === '/api/rag/documents/d1/grants/1/versions/1' && call.method === 'DELETE')).toBe(true)
})


test('selection can be removed and saved course history can be restored', async ({ page }) => {
  const calls = await fixture(page, { history: true })
  await selectLessonText(page)
  await page.getByRole('button', { name: '移除选中文本', exact: true }).click()
  await expect(page.locator('.selection-card')).toHaveCount(0)
  await page.locator('.quick-chat-card').getByRole('combobox').press('Enter')
  await page.getByRole('option', { name: '历史课时问答', exact: true }).click()
  await expect(page.locator('.quick-chat-card .chat-message.user')).toContainText('已保存选文')
  await expect(page.locator('.quick-chat-card .chat-message.assistant')).toContainText('已保存回答')
  expect(calls.some(call => call.path === '/api/rag/sessions/saved/messages')).toBe(true)
  await page.getByRole('button', { name: '新对话', exact: true }).click()
  await expect(page.locator('.quick-chat-card .chat-message')).toHaveCount(0)
})

test('stopping a lesson response leaves it incomplete and new conversation clears selection', async ({ page }) => {
  await fixture(page, { delayed: true })
  await selectLessonText(page)
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await page.getByRole('button', { name: '停止生成', exact: true }).click()
  await expect(page.locator('.quick-chat-card .chat-message.assistant')).toContainText('已停止生成')
  await page.getByRole('button', { name: '新对话', exact: true }).click()
  await page.waitForTimeout(900)
  await expect(page.locator('.quick-chat-card .chat-message')).toHaveCount(0)
  await expect(page.locator('.selection-card')).toHaveCount(0)
})

test('model errors retain the question and selection for retry', async ({ page }) => {
  await fixture(page, { failOnce: true })
  await selectLessonText(page)
  await page.getByPlaceholder('输入问题，或直接解释选中文本').fill('解释这个过程')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.quick-chat-card .chat-message.assistant')).toContainText('模型暂时不可用')
  await expect(page.getByPlaceholder('输入问题，或直接解释选中文本')).toHaveValue('解释这个过程')
  await expect(page.locator('.selection-card')).toContainText('二分查找')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.quick-chat-card .chat-message.assistant').last()).toContainText('二分查找每轮减半。')
})


test('selected note editor text can be explained after switching to the chat tab', async ({ page }) => {
  const calls = await fixture(page, { empty: true })
  const editor = page.locator('.note-editor .cm-content')
  await editor.fill('笔记中的梯度下降方法')
  await editor.evaluate(el => {
    const range = document.createRange()
    range.selectNodeContents(el)
    const selection = window.getSelection()
    selection.removeAllRanges()
    selection.addRange(range)
    el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))
  })
  await page.getByRole('button', { name: '解释这段', exact: true }).click()
  await expect(page.locator('.selection-card')).toContainText('笔记中的梯度下降方法')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect.poll(() => calls.some(call => call.path === '/api/rag/chat/stream')).toBe(true)
  expect(calls.find(call => call.path === '/api/rag/chat/stream').body.selectedText).toBe('笔记中的梯度下降方法')
})
