import { test, expect } from '@playwright/test'

async function setup(page, { stage = 'CONTENT', mode = null, prepared = false, role = 'teacher', generationStatus = 'SUCCEEDED' } = {}) {
  let course = { id: 41, teacherId: 7, title: '人工智能基础', categoryId: 1, difficulty: 'beginner', status: 2,
    setupStage: stage, contentMode: mode, preparationId: mode === 'AI' ? 21 : null, workflowVersion: 1 }
  let applied = prepared
  let rolledBack = false
  let lessonContent = '# 正文'
  const calls = []
  const project = () => ({ id: 21, projectStatus: applied ? 'PUBLISHED' : 'DRAFT', currentStage: applied ? 'PUBLISHED' : generationStatus === 'RUNNING' ? 'LESSON_REVIEW' : 'READY_TO_PUBLISH', generationStatus,
    config: { targetCourseId: 41, title: course.title, categoryId: 1, audience: '本科生', includeQuestions: false },
    outline: { chapters: [{ chapterKey: 'c1', title: '人工智能绪论', lessons: [{ lessonKey: 'l1', title: '定义' }] }] },
    lessons: [{ lessonKey: 'l1', chapterKey: 'c1', title: '定义', contentMd: lessonContent, status: 'GENERATED' }], questions: [] })
  await page.addInitScript(role => {
    localStorage.setItem('token', 'course-v2-fixture')
    localStorage.setItem('userId', '7')
    localStorage.setItem('roleCode', role)
  }, role)
  await page.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    const body = request.postData() ? request.postDataJSON() : null
    calls.push({ path, method: request.method(), body })
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/courses/41/setup' && request.method() === 'PUT') {
      expect(body.workflowVersion).toBe(course.workflowVersion)
      course = { ...course, ...body, workflowVersion: course.workflowVersion + 1 }
      return ok(course)
    }
    if (path === '/courses/41/complete') {
      expect(body.workflowVersion).toBe(course.workflowVersion)
      course = { ...course, setupStage: 'COMPLETED', workflowVersion: course.workflowVersion + 1 }
      return ok(course)
    }
    if (path === '/courses/41' && request.method() === 'PUT') {
      course = { ...course, ...body, workflowVersion: course.workflowVersion + 1 }
      return ok(course)
    }
    if (path === '/courses/41' || path === '/courses/41/setup') return ok(course)
    if (path === '/courses' && request.method() === 'POST') { course={...course,...body,setupStage:'MATERIALS'};return ok(course) }
    if (path === '/categories') return ok([{ id: 1, name: '人工智能' }])
    if (path === '/api/rag/courses/41/knowledge-base') return ok({ id: 'kb', course_id: 41, owner_id: 7 })
    if (path.endsWith('/documents')) return ok([{ id: 'file', file_name: '课件.pdf', page_count: 2, parse_status: 'success' }])
    if (path === '/api/preparations') return ok([project()])
    if (path === '/api/preparations/21') return ok(project())
    if (path === '/api/preparations/21/lessons') { lessonContent = body[0].contentMd; return ok(project()) }
    if (path === '/api/preparations/21/ready') return ok(project())
    if (path === '/api/preparations/21/apply-preview') return ok({ courseId: 41, courseVersion: 'v1', applicationId: applied ? 21 : null, rolledBack, existingLessons: [] })
    if (path === '/api/preparations/21/apply') { applied = true; return ok({ applicationId: 21, courseId: 41 }) }
    if (path.endsWith('/rollback')) { rolledBack = true; return ok({ applicationId: 21, rolledBack: true }) }
    if (path === '/courses/41/import-materials') return ok({ courseId: 41, chaptersCreated: 1, lessonsCreated: 2 })
    if (path === '/courses') return ok({ records: course.setupStage === 'COMPLETED' ? [course] : [], total: 1 })
    return ok([])
  })
  return calls
}

test('creation basic step has only Exit and Next and saves a draft on exit',async({page})=>{
 const calls=await setup(page,{stage:'BASIC'});await page.goto('/courses/41')
 await expect(page.locator('.form-actions').getByRole('button')).toHaveText(['退出','下一步'])
 await page.getByRole('button',{name:'退出',exact:true}).click();await expect(page).toHaveURL(/courses\/drafts$/)
 expect(calls.some(c=>c.path==='/courses/41'&&c.method==='PUT')).toBe(true)
})
test('materials next opens generation directly and cancelling skips to explicit completion',async({page})=>{
 const calls=await setup(page,{stage:'MATERIALS'});await page.goto('/courses/41')
 await page.getByRole('button',{name:'下一步，准备课程内容',exact:true}).click()
 await expect(page.getByRole('button',{name:'确认并完成备课',exact:true})).toBeVisible()
 await expect(page.locator('.content-choices')).toHaveCount(0)
 for(const name of ['返回课程资料','更换准备方式','保存草稿并退出'])await expect(page.locator('.setup-actions').getByRole('button',{name,exact:true})).toHaveCount(0)
 await page.getByRole('button',{name:'取消生成',exact:true}).click()
 await expect(page.getByRole('button',{name:'完成创建',exact:true})).toBeEnabled()
 expect(calls.some(c=>c.path.endsWith('/complete'))).toBe(false)
 await page.getByRole('button',{name:'完成创建',exact:true}).click();await expect(page).toHaveURL(/courses$/)
 expect(calls.some(c=>c.body?.contentMode==='MANUAL')).toBe(true)
})

test('new course has two actions and Exit saves valid fields as a server draft',async({page})=>{
 const calls=await setup(page);await page.goto('/courses/create')
 await expect(page.locator('.form-actions').getByRole('button')).toHaveText(['退出','下一步'])
 await page.getByPlaceholder('请输入课程名称').fill('退出保存测试')
 await page.locator('.el-form-item').filter({hasText:'课程分类'}).locator('.el-select__wrapper').click();await page.getByRole('option',{name:'人工智能',exact:true}).click()
 await page.getByRole('button',{name:'退出',exact:true}).click();await expect(page).toHaveURL(/courses\/drafts$/)
 expect(calls.filter(c=>c.path==='/courses'&&c.method==='POST')).toHaveLength(1)
 expect(calls.find(c=>c.path==='/courses'&&c.method==='POST').body.status).toBe(2)
})
test('exiting an unfinished form keeps local fields and allows leaving',async({page})=>{
 await setup(page);await page.goto('/courses/create');await page.getByPlaceholder('请输入课程名称').fill('尚未选择分类')
 await page.getByRole('button',{name:'退出',exact:true}).click();await expect(page).toHaveURL(/courses\/drafts$/)
 await page.goto('/courses/create');await expect(page.getByPlaceholder('请输入课程名称')).toHaveValue('尚未选择分类')
})
