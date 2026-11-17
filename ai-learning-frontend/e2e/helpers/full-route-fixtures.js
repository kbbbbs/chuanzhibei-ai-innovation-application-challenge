export async function installRouteFixtures(page, role, theme = 'light', options = {}) {
  const unexpected = []
  await page.addInitScript(({ role, theme }) => {
    localStorage.setItem('eduloop-theme', theme)
    if (!role) return
    localStorage.setItem('token', 'synthetic-route-token')
    localStorage.setItem('userId', '2')
    localStorage.setItem('roleCode', role)
    localStorage.setItem('username', role)
  }, { role, theme })

  const records = {
    '/api/setup/status': { state: 'READY' },
    '/categories': [{ id: 3, name: '测试分类', description: '测试分类描述' }],
    '/courses': { records: [{ id: 5, title: '浏览器测试课程', difficulty: '初级', studentCount: 12, viewCount: 20, status: 1 }], total: 1 },
    '/users': { records: [{ id: 8, username: 'fixture-user', nickname: '测试用户', email: 'fixture@example.test', roleCode: 'student', status: 1 }], total: 1 },
    '/users/me': { id: 2, username: 'fixture-user', nickname: '测试用户', email: 'fixture@example.test' },
    '/class-students/student/2': [{ id: 9, classId: 7, status: 1 }],
    '/teaching-classes/my': { records: [{ id: 7, courseId: 5, className: '测试班级', status: 1, courseOpen: true }], total: 1 },
    '/teaching-classes/7': { id: 7, courseId: 5, className: '测试班级' },
    '/courses/5': { id: 5, title: '浏览器测试课程', description: '测试课程描述' },
    '/api/paper/course-directions': { paperCount: 0, directions: [] },
    '/api/homeworks': [{ id: 11, classId: 7, name: '进行中的作业', status: 1, startTime: '2020-01-01', endTime: '2099-01-01' }],
    '/api/exams': [{ id: 21, classId: 7, name: '考试样例', status: 1, startTime: '2020-01-01', endTime: '2099-01-01' }],
    '/api/submissions': [],
    '/api/homework-submissions': [],
    '/api/home-course-recommendations': [],
    '/api/home-course-recommendations/admin': [],
    '/api/admin/settings/models': { roles: [] },
    '/api/admin/models/status': [],
    '/teacher-applications': { records: [], total: 0 },
    '/administrative-classes': { records: [], total: 0 },
    '/work-studies': { records: [], total: 0 },
    '/work-studies/students': { records: [], total: 0 },
    '/work-studies/pending-applications': [],
  }
  if (options.dashboard) {
    Object.assign(records, {
      '/work-studies/my-applications': { records: [], total: 0 },
      '/teaching-classes': { records: [{ id: 7, className: '测试班级' }], total: 1 },
      '/api/questions': { records: [{ id: 1 }], total: 1 },
      '/api/papers': [],
      '/operation-logs': { records: [], total: 0 },
      '/teacher-applications': { records: [{ id: 1, nickname: '待审测试教师', reason: '测试审核申请' }], total: 1 },
      '/api/agent/definitions': [],
      '/api/agent/sessions': [],
    })
  }
  await page.route('**/backend/**', async route => {
    const path = new URL(route.request().url()).pathname.replace(/^\/backend/, '')
    if (route.request().method() === 'GET' && Object.hasOwn(records, path)) {
      await route.fulfill({ json: { code: '200', data: records[path] } })
    } else {
      unexpected.push(`${route.request().method()} ${path}`)
      await route.fulfill({ status: 404, json: { code: '404', msg: 'unmatched synthetic fixture' } })
    }
  })
  return unexpected
}
