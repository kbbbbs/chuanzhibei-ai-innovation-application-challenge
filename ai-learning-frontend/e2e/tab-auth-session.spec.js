import { test, expect } from '@playwright/test'

const teacher = { token: 'fixture-teacher-token', userId: 36, username: 'fixture_teacher', roleCode: 'teacher' }
const student = { token: 'fixture-student-token', userId: 37, username: 'fixture_student', roleCode: 'student' }
const anotherTeacher = { token: 'fixture-another-teacher-token', userId: 38, username: 'fixture_teacher_two', roleCode: 'teacher' }

async function mockBackend(context) {
  await context.route('**/backend/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/backend/, '')
    const ok = data => route.fulfill({ json: { code: '200', data } })
    if (path === '/auth/login') return ok([teacher, student, anotherTeacher].find(actor => actor.username === request.postDataJSON().username))
    if (path === '/api/setup/status') return ok({ state: 'READY' })
    if (path === '/api/learning-analysis/templates') {
      if (request.headers().authorization !== `Bearer ${teacher.token}`) {
        return route.fulfill({ status: 403, json: { code: '403', msg: '仅教师或管理员可操作' } })
      }
      return ok([{ id: 'default', name: '默认模板', fields: [] }])
    }
    if (path === '/courses') return ok({ records: [], total: 0 })
    if (path === '/session-expired') return route.fulfill({ status: 401, json: { code: '401' } })
    if (path === '/stream-auth-probe') return route.fulfill({ contentType: 'text/event-stream', body: 'event: complete\ndata: {}\n\n' })
    return ok([])
  })
}

async function login(page, actor) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill(actor.username)
  await page.getByPlaceholder('请输入密码').fill('fixture-password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/console$/)
}

async function authState(page) {
  await page.waitForFunction(() => document.querySelector('#app')?.__vue_app__?.config.globalProperties.$pinia?._s.has('user'))
  return page.evaluate(() => {
    const user = document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('user')
    return { roleCode: user.roleCode, userId: String(user.userId), isLoggedIn: user.isLoggedIn }
  })
}

async function requestAsCurrentUser(page, path = '/auth-probe') {
  const observed = page.waitForRequest(request => new URL(request.url()).pathname === `/backend${path}`)
  await page.evaluate(async path => {
    const { default: request } = await import('/src/utils/request.js')
    await request.get(path).catch(() => {})
  }, path)
  return (await observed).headers().authorization
}

test('teacher and student tabs keep their own requests, refresh and logout sessions', async ({ context }) => {
  await mockBackend(context)
  const teacherPage = await context.newPage()
  await login(teacherPage, teacher)
  const studentPage = await context.newPage()
  await login(studentPage, student)

  const templates = teacherPage.waitForRequest('**/backend/api/learning-analysis/templates')
  await teacherPage.evaluate(() => document.querySelector('#app').__vue_app__.config.globalProperties.$router.push('/grade-analysis'))
  await expect(teacherPage.getByRole('heading', { name: '智能学情分析', exact: true })).toBeVisible()
  expect((await templates).headers().authorization).toBe(`Bearer ${teacher.token}`)
  await expect(teacherPage.getByText('仅教师或管理员可操作', { exact: true })).toHaveCount(0)
  expect(await requestAsCurrentUser(studentPage)).toBe(`Bearer ${student.token}`)
  const stream = teacherPage.waitForRequest('**/backend/stream-auth-probe')
  await teacherPage.evaluate(async () => {
    const { streamEventResponse } = await import('/src/utils/eventStream.js')
    await streamEventResponse('/backend/stream-auth-probe', {}).finished
  })
  expect((await stream).headers().authorization).toBe(`Bearer ${teacher.token}`)

  await teacherPage.reload()
  expect(await authState(teacherPage)).toMatchObject({ roleCode: 'teacher', userId: '36', isLoggedIn: true })
  await studentPage.reload()
  expect(await authState(studentPage)).toMatchObject({ roleCode: 'student', userId: '37', isLoggedIn: true })

  await studentPage.evaluate(() => document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('user').logout())
  expect(await requestAsCurrentUser(studentPage)).toBeUndefined()
  await studentPage.reload()
  expect(await authState(studentPage)).toMatchObject({ isLoggedIn: false })
  await teacherPage.reload()
  expect(await authState(teacherPage)).toMatchObject({ roleCode: 'teacher', isLoggedIn: true })
  expect(await requestAsCurrentUser(teacherPage)).toBe(`Bearer ${teacher.token}`)
})

test('expired old tab clears its own identity and preserves a different remembered login', async ({ context }) => {
  await mockBackend(context)
  const teacherPage = await context.newPage()
  await login(teacherPage, teacher)
  const studentPage = await context.newPage()
  await login(studentPage, student)
  await requestAsCurrentUser(teacherPage, '/session-expired')
  await expect(teacherPage).toHaveURL(/\/login$/)
  expect(await authState(teacherPage)).toMatchObject({ isLoggedIn: false })
  await teacherPage.reload()
  expect(await authState(teacherPage)).toMatchObject({ isLoggedIn: false })
  await studentPage.reload()
  expect(await authState(studentPage)).toMatchObject({ roleCode: 'student', isLoggedIn: true })
  const newPage = await context.newPage()
  await newPage.goto('/console')
  expect(await authState(newPage)).toMatchObject({ roleCode: 'student', isLoggedIn: true })
})

test('legacy remembered identity migrates once and survives another tab login', async ({ context }) => {
  await mockBackend(context)
  const legacyPage = await context.newPage()
  await legacyPage.goto('/login')
  await legacyPage.evaluate(actor => {
    sessionStorage.clear()
    for (const [key, value] of Object.entries(actor)) localStorage.setItem(key, String(value))
  }, teacher)
  await legacyPage.reload()
  expect(await authState(legacyPage)).toMatchObject({ roleCode: 'teacher', isLoggedIn: true })
  const studentPage = await context.newPage()
  await login(studentPage, student)
  await legacyPage.reload()
  expect(await authState(legacyPage)).toMatchObject({ roleCode: 'teacher', isLoggedIn: true })
  expect(await requestAsCurrentUser(legacyPage)).toBe(`Bearer ${teacher.token}`)
})

test('different teachers keep their own identity and profile updates preserve the remembered account', async ({ context }) => {
  await mockBackend(context)
  const firstPage = await context.newPage()
  await login(firstPage, teacher)
  const secondPage = await context.newPage()
  await login(secondPage, anotherTeacher)
  expect(await requestAsCurrentUser(firstPage)).toBe(`Bearer ${teacher.token}`)
  await firstPage.evaluate(() => document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('user').updateProfile({ nickname: 'Teacher One' }))
  await firstPage.reload()
  expect(await authState(firstPage)).toMatchObject({ userId: '36', roleCode: 'teacher' })
  const newPage = await context.newPage()
  await newPage.goto('/console')
  expect(await authState(newPage)).toMatchObject({ userId: '38', roleCode: 'teacher' })
  await firstPage.evaluate(() => document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('user').logout())
  await secondPage.reload()
  expect(await authState(secondPage)).toMatchObject({ userId: '38', isLoggedIn: true })
  expect(await requestAsCurrentUser(secondPage)).toBe(`Bearer ${anotherTeacher.token}`)
})

test('a delayed 401 from the previous identity preserves a newer login in the same tab', async ({ context }) => {
  await mockBackend(context)
  let releaseResponse
  const released = new Promise(resolve => { releaseResponse = resolve })
  await context.route('**/backend/delayed-session-expired', async route => {
    await released
    await route.fulfill({ status: 401, json: { code: '401' } })
  })
  const page = await context.newPage()
  await login(page, teacher)
  const requested = page.waitForRequest('**/backend/delayed-session-expired')
  const pendingRequest = page.evaluate(async () => {
    const { default: request } = await import('/src/utils/request.js')
    await request.get('/delayed-session-expired').catch(() => {})
  })
  await requested
  await page.evaluate(actor => document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('user').setUser(actor), student)
  releaseResponse()
  await pendingRequest
  await expect(page).toHaveURL(/\/console$/)
  expect(await authState(page)).toMatchObject({ roleCode: 'student', userId: '37', isLoggedIn: true })
  await page.reload()
  expect(await requestAsCurrentUser(page)).toBe(`Bearer ${student.token}`)
})
