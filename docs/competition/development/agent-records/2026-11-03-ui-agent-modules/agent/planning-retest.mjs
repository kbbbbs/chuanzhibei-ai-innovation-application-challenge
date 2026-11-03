import fs from 'node:fs'
import path from 'node:path'

const base = process.env.RETEST_BASE_URL || 'http://127.0.0.1:8080'
const label = process.env.RETEST_LABEL || 'baseline-course'
const dir = path.dirname(new URL(import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1')
const prefix = 'agent-retest-20261103'
const result = { timestamp: new Date().toISOString(), base, label, checks: [] }
let token
async function req(method, url, body) {
  const start = performance.now()
  const r = await fetch(base + url, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(130000)
  })
  const data = await r.json()
  return { httpStatus: r.status, elapsedMs: Math.round(performance.now() - start), ...data }
}
function check(name, ok, detail) {
  result.checks.push({ name, ok: Boolean(ok), detail })
}
try {
  const login = await req('POST', '/auth/login', { username: 'teacher', password: '123456' })
  token = login.data?.token
  if (!token) throw Error('Teacher login failed')
  result.actor = { userId: login.data.userId, role: login.data.roleCode }
  const definitions = await req('GET', '/api/agent/definitions')
  const agent = definitions.data.find(x => x.code === 'learning_assistant')
  if (!agent) throw Error('Default agent unavailable')
  const session = await req('POST', '/api/agent/sessions', { agentId: agent.id, title: `${prefix}-${label}` })
  result.sessionId = session.data?.id
  if (!result.sessionId) throw Error('Session creation failed')
  const content = process.env.RETEST_PROMPT || `请创建课程，标题为${prefix}-${label}，分类ID为1，难度为beginner，状态为草稿。先生成待确认操作计划，不要执行。`
  result.prompt = content
  const reply = await req('POST', `/api/agent/sessions/${result.sessionId}/chat`, { content })
  result.reply = reply
  const action = reply.data?.pendingAction
  check('structured-pending-course-plan', action?.actionCode === 'course.create' && Boolean(action?.actionId), {
    httpStatus: reply.httpStatus, elapsedMs: reply.elapsedMs, actionCode: action?.actionCode || null,
    actionId: action?.actionId || null, code: reply.code, message: reply.msg || reply.message || null
  })
  result.verdict = result.checks.every(x => x.ok) ? 'PASS' : 'FAIL'
} catch (error) {
  result.error = error.message
  result.verdict = 'FAIL'
} finally {
  fs.writeFileSync(path.join(dir, `${label}-results.json`), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result, null, 2))
  if (result.verdict !== 'PASS') process.exitCode = 1
}
