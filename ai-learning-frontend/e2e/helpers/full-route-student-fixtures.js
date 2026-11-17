export async function installStudentFixtures(page, role, theme, populated = false) {
  const unexpected = []
  await page.addInitScript(({ role, theme }) => {
    localStorage.setItem('eduloop-theme', theme)
    localStorage.setItem('token', 'synthetic-route-token')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', role)
    localStorage.setItem('username', role)
  }, { role, theme })

  const records = {
    '/api/setup/status': { state: 'READY' },
    '/users/me': { id: 2, username: 'fixture-user', nickname: '测试用户' },
    '/courses': { records: [], total: 0 },
    '/categories': [],
    '/class-students/student/2': [{ id: 9, classId: 7, status: 1 }],
    '/teaching-classes/my': { records: [{ id: 7, courseId: 5, className: '测试班级', status: 1, courseOpen: true }], total: 1 },
    '/teaching-classes/7': { id: 7, courseId: 5, className: '测试班级' },
    '/courses/5': { id: 5, title: '浏览器测试课程', description: '测试课程描述' },
    '/courses/5/experience': {
      overview: { title: '浏览器测试课程' },
      path: [{ id: 41, title: '测试课时', chapterTitle: '第一章', unlocked: true, progress: 0 }],
    },
    '/courses/5/chapters': [],
    '/learning-progress': [],
    '/api/recommendations/my': { items: [], counts: {}, summary: '', profileTags: [] },
    '/api/paper/recommend': { data: [] },
    '/api/rag/knowledge-bases': [],
    '/api/rag/sessions': [],
    '/work-studies': { records: [], total: 0 },
    '/work-studies/my-applications': { records: [], total: 0 },
    '/api/exams/21': { id: 21, name: '考试样例', durationMinutes: 120, endTime: '2099-01-01T00:00:00' },
    '/api/submissions/31/student': { id: 31, examName: '考试样例', status: 1, answers: [], startTime: new Date().toISOString() },
  }
  if (populated) {
    Object.assign(records, {
      '/courses/5/chapters': [{ id: 11, title: '第一章' }],
      '/chapters/11/lessons': [{ id: 41, title: '测试课时' }],
      '/lessons/41/note': { content: '' },
      '/api/rag/knowledge-bases': [{ id: 'kb-1', name: '测试知识库', knowledge_count: 1, read_only: true }],
      '/api/rag/knowledge-bases/kb-1/documents': [{ id: 'doc-1', title: '测试文档', file_name: 'lesson.txt' }],
      '/work-studies': { records: [{ id: 1, jobName: '测试岗位', jobDesc: '图书整理', workLocation: '图书馆', workHours: '周末', hourlyRate: 20, maxRecruit: 1 }], total: 1 },
      '/api/submissions/31/student': {
        id: 31, examName: '考试样例', status: 1, startTime: new Date().toISOString(),
        answers: [{ id: 101, questionType: 'SINGLE_CHOICE', questionContent: '测试题目', score: 5, options: [{ optionKey: 'A', optionContent: '选项一' }, { optionKey: 'B', optionContent: '选项二' }] }],
      },
    })
  }
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    if (request.method() === 'GET' && Object.hasOwn(records, path)) {
      await route.fulfill({ json: { code: '200', data: records[path] } })
    } else if (request.method() === 'POST' && path === '/api/exams/21/submissions/start') {
      await route.fulfill({ json: { code: '200', data: { id: 31 } } })
    } else if (populated && request.method() === 'POST' && path === '/api/rag/sessions') {
      await route.fulfill({ json: { code: '200', data: { id: 'session-1' } } })
    } else {
      unexpected.push(`${request.method()} ${path}`)
      await route.fulfill({ status: 404, json: { code: '404', msg: 'unmatched synthetic fixture' } })
    }
  })
  return unexpected
}
