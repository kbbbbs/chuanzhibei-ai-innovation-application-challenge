// Local test fixture. Credentials stay in the user's private temporary directory.
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const crypto = require('node:crypto')

async function seed() {
  const base = process.env.EDULOOP_BASE_URL || 'http://127.0.0.1:8082'
  if (!['127.0.0.1', 'localhost'].includes(new URL(base).hostname)) throw new Error('测试预置仅支持本地服务')
  const courseId = Number(process.argv[2])
  if (!courseId) throw new Error('请提供人工智能基础课程编号')
  let token
  async function api(url, method = 'GET', body) {
    const response = await fetch(base + url, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    })
    const result = await response.json()
    if (!response.ok || result.code !== '200') throw new Error(`${url} ${result.msg || result.message || response.status}`)
    return result.data
  }
  if (!process.env.EDULOOP_ADMIN_USERNAME || !process.env.EDULOOP_ADMIN_PASSWORD) throw new Error('请通过环境变量提供本地管理员登录信息')
  token = (await api('/auth/login', 'POST', { username: process.env.EDULOOP_ADMIN_USERNAME, password: process.env.EDULOOP_ADMIN_PASSWORD })).token
  const course = await api(`/courses/${courseId}`)
  if (course.title !== '人工智能基础') throw new Error('目标课程名称与人工智能基础不一致')
  const credentialPath = path.join(os.tmpdir(), 'eduloop-course-publication-demo.json')
  const credentials = fs.existsSync(credentialPath) ? JSON.parse(fs.readFileSync(credentialPath, 'utf8')) : {}
  const students = []
  for (let index = 1; index <= 4; index += 1) {
    const username = `course_ai_demo_s${index}`
    const nickname = `人工智能测试学生${index}`
    let user = (await api(`/users?username=${username}&pageSize=100`)).records.find(row => row.username === username)
    if (!user) {
      credentials[username] = crypto.randomBytes(18).toString('base64url')
      // Persist before creating so an interrupted retry keeps the same credential.
      fs.writeFileSync(credentialPath, JSON.stringify(credentials, null, 2), { mode: 0o600 })
      await api('/users', 'POST', { username, nickname, password: credentials[username], roleCode: 'student', status: 1 })
      user = (await api(`/users?username=${username}&pageSize=100`)).records.find(row => row.username === username)
    }
    if (user?.roleCode !== 'student' || user.nickname !== nickname || !credentials[username]) throw new Error('测试账号标识冲突或本机凭据缺失')
    students.push({ id: user.id, username })
  }
  const classes = []
  for (let index = 0; index < 2; index += 1) {
    const className = `人工智能测试${index === 0 ? '一' : '二'}班`
    let teachingClass = (await api(`/teaching-classes?courseId=${courseId}&pageSize=100`)).records.find(row => row.className === className)
    if (!teachingClass) {
      await api('/teaching-classes', 'POST', { courseId, teacherId: course.teacherId, className, semester: '2026秋', description: '课程发布流程本地测试班', status: 1 })
      teachingClass = (await api(`/teaching-classes?courseId=${courseId}&pageSize=100`)).records.find(row => row.className === className)
    }
    if (teachingClass.teacherId !== course.teacherId) throw new Error('测试教学班教师关联不一致')
    const members = await api(`/teaching-classes/${teachingClass.id}/students`)
    for (const student of students.slice(index * 2, index * 2 + 2)) {
      const existing = members.find(member => member.studentId === student.id)
      if (!existing) await api('/class-students', 'POST', { classId: teachingClass.id, studentId: student.id, status: 1 })
      else if (existing.status !== 1) await api(`/class-students/${existing.id}`, 'PUT', { classId: teachingClass.id, studentId: student.id, status: 1 })
    }
    classes.push({ id: teachingClass.id, className, studentIds: students.slice(index * 2, index * 2 + 2).map(student => student.id) })
  }
  const after = await api(`/courses/${courseId}`)
  if (after.status !== course.status) throw new Error('测试预置意外改变课程发布状态')
  console.log(JSON.stringify({ courseId, courseStatus: after.status, students, classes, credentialPath }, null, 2))
}
seed().catch(error => { console.error(error.message); process.exitCode = 1 })
