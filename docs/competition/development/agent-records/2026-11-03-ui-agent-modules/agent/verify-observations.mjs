import fs from 'node:fs'
import path from 'node:path'
const dir = path.dirname(new URL(import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1')
const results = { timestamp: new Date().toISOString(), checks: [] }
const prefix = 'agent-retest-20261103'
const read = JSON.parse(fs.readFileSync(path.join(dir, 'baseline-read-results.json')))
results.checks.push({
  name: 'read-result-reassessment',
  ok: read.reply.data.pendingAction === null && !/超时|未返回结果|暂时不可用/.test(read.reply.data.content) && read.reply.data.content.includes(prefix),
  note: 'Original generic harness incorrectly required course.create for a READ request. Original FAIL artifact is retained; this correction uses its response and stage logs, without another model request.',
  elapsedMs: read.reply.elapsedMs, sessionId: read.sessionId
})
for (const base of ['http://127.0.0.1:8080', 'http://127.0.0.1:8082']) {
  const request = async (method, url, body, token) => {
    const r = await fetch(base + url, {
      method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body)
    })
    return { httpStatus: r.status, ...await r.json() }
  }
  const teacher = (await request('POST', '/auth/login', { username: 'teacher', password: '123456' })).data
  const courses = await request('GET', `/courses?title=${prefix}&pageSize=100`, undefined, teacher.token)
  results.checks.push({ base, name: 'no-synthetic-course-created', ok: courses.code === '200' && courses.data.total === 0, total: courses.data?.total })
  const pending = await request('GET', '/api/agent/actions/pending', undefined, teacher.token)
  results.checks.push({
    base, name: 'no-own-pending-action',
    ok: pending.code === '200' && !pending.data.some(x => JSON.stringify(x).includes(prefix)),
    note: 'No plan was returned by the tested WRITE requests; pending listing is read-only.'
  })
  const student = (await request('POST', '/auth/login', { username: 'student', password: '123456' })).data
  const denied = await request('POST', '/api/agent/actions/confirm', {
    actionId: `${prefix}-nonexistent`, actionCode: 'course.create', confirmed: true,
    parameters: { title: `${prefix}-must-not-create`, categoryId: 1 }
  }, student.token)
  results.checks.push({
    base, name: 'student-confirm-role-gate', ok: String(denied.code) === '403',
    response: denied, note: 'Tests role gate with a nonexistent synthetic action ID, not ownership of a real teacher plan.'
  })
}
results.verdict = results.checks.every(x => x.ok) ? 'PASS' : 'FAIL'
fs.writeFileSync(path.join(dir, 'verification-results.json'), JSON.stringify(results, null, 2))
console.log(JSON.stringify(results, null, 2))
if (results.verdict !== 'PASS') process.exitCode = 1
