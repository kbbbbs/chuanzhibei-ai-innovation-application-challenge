import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const dir = path.dirname(fileURLToPath(import.meta.url))
const course = JSON.parse(fs.readFileSync(path.join(dir, 'business-results.json'))).confirm.data.data
const title = 'official-ds-20261103-chapter'
const result = { timestamp: new Date().toISOString(), executedBy: 'Sol subagent', businessChatRequests: 1, checks: [] }
let token
async function req(method, endpoint, body) {
  const start = performance.now()
  const r = await fetch('http://127.0.0.1:8082' + endpoint, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(90000)
  })
  return { httpStatus: r.status, elapsedMs: Math.round(performance.now() - start), ...await r.json() }
}
function check(name, ok, detail) { result.checks.push({ name, ok: Boolean(ok), detail }) }
try {
  token = (await req('POST', '/auth/login', { username: 'teacher', password: '123456' })).data?.token
  if (!token) throw Error('Teacher login failed')
  const agent = (await req('GET', '/api/agent/definitions')).data.find(x => x.code === 'learning_assistant')
  const session = await req('POST', '/api/agent/sessions', { agentId: agent.id, title })
  result.sessionId = session.data.id
  result.prompt = `请为课程ID为${course.courseId}、课程标题为${course.title}的课程创建章节，章节标题为${title}，排序值为1。先生成待确认操作计划，不要执行。`
  result.reply = await req('POST', `/api/agent/sessions/${result.sessionId}/chat`, { content: result.prompt })
  const action = result.reply.data?.pendingAction
  check('structured-pending-chapter-plan', action?.actionCode === 'chapter.create' && Boolean(action.actionId),
    { actionCode: action?.actionCode, actionId: action?.actionId })
  result.beforeConfirm = await req('GET', `/courses/${course.courseId}/chapters`)
  check('no-chapter-before-confirm', Array.isArray(result.beforeConfirm.data) && !result.beforeConfirm.data.some(x => x.title === title))
  if (action?.actionCode === 'chapter.create' && action.actionId) {
    const confirm = { actionId: action.actionId, actionCode: action.actionCode, confirmed: true }
    result.confirm = await req('POST', '/api/agent/actions/confirm', confirm)
    result.afterConfirm = await req('GET', `/courses/${course.courseId}/chapters`)
    const matching = (result.afterConfirm.data || []).filter(x => x.title === title)
    check('one-chapter-after-confirm', matching.length === 1 && matching[0].courseId === course.courseId, { count: matching.length })
    result.duplicate = await req('POST', '/api/agent/actions/confirm', confirm)
    check('duplicate-action-rejected', String(result.duplicate.code) !== '200')
    token = (await req('POST', '/auth/login', { username: 'admin', password: '123456' })).data?.token
    const audit = await req('GET', '/operation-logs?operation=agent.chapter.create&userId=2&pageSize=100')
    const own = (audit.data?.records || []).filter(x => JSON.parse(x.params || '{}').actionId === action.actionId)
    result.audit = own.map(x => ({ id: x.id, operation: x.operation, userId: x.userId, params: JSON.parse(x.params), createTime: x.createTime }))
    check('own-chapter-audit-exists', own.length === 1)
  }
} catch (error) { result.error = error.message }
result.verdict = !result.error && result.checks.every(x => x.ok) ? 'PASS' : 'FAIL'
fs.writeFileSync(path.join(dir, 'chapter-results.json'), JSON.stringify(result, null, 2))
console.log(JSON.stringify({ timestamp: result.timestamp, sessionId: result.sessionId, elapsedMs: result.reply?.elapsedMs, checks: result.checks, error: result.error, verdict: result.verdict }, null, 2))
if (result.verdict !== 'PASS') process.exitCode = 1
