import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const dir = path.dirname(fileURLToPath(import.meta.url))
const base = 'http://127.0.0.1:8082'
const prefix = 'official-ds-20261103'
const result = { timestamp: new Date().toISOString(), base, prefix, checks: [], businessChatRequests: 0 }
let token
async function req(method, endpoint, body) {
  const started = performance.now()
  const r = await fetch(base + endpoint, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(90000)
  })
  return { httpStatus: r.status, elapsedMs: Math.round(performance.now() - started), ...await r.json() }
}
function check(name, ok, detail) { result.checks.push({ name, ok: Boolean(ok), detail }) }
async function chat(agentId, mode, content) {
  const session = await req('POST', '/api/agent/sessions', { agentId, title: `${prefix}-${mode}` })
  if (!session.data?.id) throw Error('Session creation failed')
  result.businessChatRequests++
  const reply = await req('POST', `/api/agent/sessions/${session.data.id}/chat`, { content })
  return { sessionId: session.data.id, prompt: content, reply }
}
try {
  const login = await req('POST', '/auth/login', { username: 'teacher', password: '123456' })
  token = login.data?.token
  if (!token) throw Error('Teacher login failed')
  const teacherToken = token
  const definitions = await req('GET', '/api/agent/definitions')
  const agent = definitions.data.find(x => x.code === 'learning_assistant')
  result.before = await req('GET', `/courses?title=${prefix}&pageSize=100`)
  result.write = await chat(agent.id, 'write', `请创建课程，标题为${prefix}-write，分类ID为1，难度为beginner，状态为草稿。先生成待确认操作计划，不要执行。`)
  const action = result.write.reply.data?.pendingAction
  check('structured-pending-course-plan', action?.actionCode === 'course.create' && Boolean(action.actionId), {
    actionCode: action?.actionCode || null, actionId: action?.actionId || null
  })
  result.beforeConfirm = await req('GET', `/courses?title=${prefix}&pageSize=100`)
  check('no-write-before-confirm', result.beforeConfirm.data?.total === 0, { total: result.beforeConfirm.data?.total })
  if (action?.actionCode === 'course.create' && action.actionId) {
    const confirm = { actionId: action.actionId, actionCode: action.actionCode, confirmed: true }
    result.confirm = await req('POST', '/api/agent/actions/confirm', confirm)
    result.afterConfirm = await req('GET', `/courses?title=${prefix}&pageSize=100`)
    check('course-exists-after-confirm', result.afterConfirm.data?.total === 1, { total: result.afterConfirm.data?.total })
    result.duplicate = await req('POST', '/api/agent/actions/confirm', confirm)
    check('duplicate-action-rejected', String(result.duplicate.code) !== '200', result.duplicate)
    result.audit = await req('GET', '/operation-logs?operation=agent.course.create&pageSize=100')
    check('audit-has-own-action', JSON.stringify(result.audit.data || {}).includes(action.actionId), { code: result.audit.code })
  } else result.confirmationChecks = 'BLOCKED: no real model-generated pendingAction'
  result.read = await chat(agent.id, 'read', `请查询标题包含${prefix}的课程，只读展示结果，不要创建或修改任何数据。`)
  check('read-no-pending-write', result.read.reply.data?.pendingAction == null, { pendingAction: result.read.reply.data?.pendingAction || null })
  check('read-business-answer', Boolean(result.read.reply.data?.content) && !/超时|未返回结果|暂时不可用|规划失败/.test(result.read.reply.data.content), { content: result.read.reply.data?.content })
  const student = await req('POST', '/auth/login', { username: 'student', password: '123456' })
  token = student.data?.token
  result.studentGate = await req('POST', '/api/agent/actions/confirm', { actionId: `${prefix}-nonexistent`, actionCode: 'course.create', confirmed: true })
  check('student-role-gate', String(result.studentGate.code) === '403', result.studentGate)
  token = teacherToken
  result.finalCourses = await req('GET', `/courses?title=${prefix}&pageSize=100`)
} catch (error) { result.error = error.message }
result.verdict = !result.error && result.checks.every(x => x.ok) ? 'PASS' : 'FAIL'
fs.writeFileSync(path.join(dir, 'business-results.json'), JSON.stringify(result, null, 2))
console.log(JSON.stringify(result, null, 2))
if (result.verdict !== 'PASS') process.exitCode = 1
