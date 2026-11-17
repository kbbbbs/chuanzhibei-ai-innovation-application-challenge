export async function installTeacherFixtures(page, role, theme, populated = false) {
  const unexpected = []
  await page.addInitScript(({ role, theme }) => {
    localStorage.setItem('eduloop-theme', theme)
    localStorage.setItem('token', 'synthetic-route-token')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', role)
    localStorage.setItem('username', role)
  }, { role, theme })

  const emptyPage = { records: [], total: 0 }
  const records = {
    '/api/setup/status': { state: 'READY' },
    '/teaching-classes': emptyPage,
    '/notices': emptyPage,
    '/operation-logs': emptyPage,
    '/courses': emptyPage,
    '/categories': [],
    '/api/questions': emptyPage,
    '/api/papers': emptyPage,
    '/api/homework-papers': emptyPage,
    '/api/exams': emptyPage,
    '/api/homeworks': emptyPage,
    '/api/submissions': emptyPage,
    '/api/homework-submissions': emptyPage,
    '/api/homeworks/11': { id: 11, title: '测试作业' },
    '/api/exams/21': { id: 21, title: '测试考试' },
    '/api/exams/21/results': { examName: '测试考试', studentScores: [], fullScore: 100 },
    '/courses/5': { id: 5, title: '浏览器测试课程', description: '测试课程描述' },
    '/courses/5/chapters': [],
    '/learning-content/templates': { templates: [] },
    '/api/home-course-recommendations': [],
    '/api/rag/knowledge-bases': [],
    '/api/rag/sessions': [],
    '/api/preparations': emptyPage,
  }
  if (populated) {
    Object.assign(records, {
      '/teaching-classes': { records: [{ id: 7, className: '验收教学班', courseId: 5 }], total: 1 },
      '/class-students/class/7': [{ studentId: 42 }],
      '/users/42': { id: 42, nickname: '验收学生' },
      '/courses': { records: [{ id: 5, title: '浏览器测试课程', teacherId: 2 }], total: 1 },
      '/api/questions': { records: [{ id: 31, content: '验收题目内容', type: 1, difficulty: 1 }], total: 1 },
      '/api/papers': { records: [{ id: 41, title: '验收试卷名称', totalScore: 100, duration: 60, status: 0 }], total: 1 },
      '/api/exams': { records: [{ id: 21, title: '验收考试', totalScore: 100, status: 3 }], total: 1 },
      '/api/submissions': { records: [{ id: 51, studentId: 42, status: 2, objectiveScore: 60, subjectiveScore: 25, totalScore: 85 }], total: 1 },
      '/api/submissions/51/grading': { answers: [] },
      '/courses/5/chapters': [{ id: 61, title: '验收章节' }],
      '/chapters/61/lessons': [{ id: 71, title: '验收课时', resourceType: 'TEXT', duration: 120 }],
    })
  }
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    if (request.method() === 'GET' && Object.hasOwn(records, path)) {
      await route.fulfill({ json: { code: '200', data: records[path] } })
    } else {
      unexpected.push(`${request.method()} ${path}`)
      await route.fulfill({ status: 404, json: { code: '404', msg: 'unmatched synthetic fixture' } })
    }
  })
  return unexpected
}
