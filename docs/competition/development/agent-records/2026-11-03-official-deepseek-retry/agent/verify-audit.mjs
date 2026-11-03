import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const dir = path.dirname(fileURLToPath(import.meta.url))
const original = JSON.parse(fs.readFileSync(path.join(dir, 'business-results.json')))
const action = original.write.reply.data.pendingAction
let token
async function req(method, endpoint, body) {
  const r = await fetch('http://127.0.0.1:8082' + endpoint, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  return { httpStatus: r.status, ...await r.json() }
}
const result = { timestamp: new Date().toISOString(), originalPreserved: 'business-results.json', checks: [] }
try {
  const login = await req('POST', '/auth/login', { username: 'admin', password: '123456' })
  token = login.data?.token
  if (!token) throw Error('Admin login failed')
  const response = await req('GET', '/operation-logs?operation=agent.course.create&userId=2&pageSize=100')
  const own = (response.data?.records || []).filter(row => {
    try { return JSON.parse(row.params || '{}').actionId === action.actionId } catch { return false }
  })
  result.auditQuery = { httpStatus: response.httpStatus, code: response.code, ownMatchedCount: own.length }
  result.ownAudit = own.map(row => ({
    id: row.id, userId: row.userId, operation: row.operation, method: row.method, url: row.url,
    createTime: row.createTime, params: JSON.parse(row.params)
  }))
  result.checks.push({ name: 'admin-can-read-own-synthetic-audit', ok: own.length === 1 })
  result.checks.push({ name: 'audit-action-and-result-match', ok: own.length === 1
    && result.ownAudit[0].params.actionCode === 'course.create'
    && result.ownAudit[0].params.result.courseId === original.confirm.data.data.courseId })
  result.correctedChecks = original.checks.map(check => check.name === 'audit-has-own-action'
    ? { name: check.name, ok: own.length === 1, detail: 'Verified through admin; teacher listing restricts user_id to teacherStudentIds.' }
    : check)
  result.verdict = [...result.checks, ...result.correctedChecks].every(check => check.ok) ? 'PASS' : 'FAIL'
} catch (error) { result.error = error.message; result.verdict = 'FAIL' }
fs.writeFileSync(path.join(dir, 'audit-verification.json'), JSON.stringify(result, null, 2))
console.log(JSON.stringify(result, null, 2))
if (result.verdict !== 'PASS') process.exitCode = 1
