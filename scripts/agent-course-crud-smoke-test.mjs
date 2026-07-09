#!/usr/bin/env node

const DEFAULT_BASE_URL = 'http://localhost:8080'
const TEST_AGENT_CODE = 'agent_course_crud_test'
const COURSE_TOOLS = [
  'course_search',
  'course_create_plan',
  'course_update_plan',
  'course_status_plan'
]
const COURSE_STATUS_CONFIRMATION_TEXT = '确认变更课程状态'

const args = parseArgs(process.argv.slice(2))

const config = {
  baseUrl: valueOf('base-url', 'AGENT_BASE_URL', DEFAULT_BASE_URL).replace(/\/$/, ''),
  username: valueOf('username', 'AGENT_TEST_USERNAME', 'admin'),
  password: "[REDACTED]"
  agentId: numberValue('agent-id', 'AGENT_TEST_AGENT_ID'),
  confirm: boolValue('confirm', 'AGENT_COURSE_TEST_CONFIRM'),
  skipHealth: boolValue('skip-health', 'AGENT_TEST_SKIP_HEALTH'),
  requireModel: boolValue('require-model', 'AGENT_TEST_REQUIRE_MODEL'),
  categoryId: numberValue('category-id', 'AGENT_COURSE_TEST_CATEGORY_ID') || 1,
  courseId: numberValue('course-id', 'AGENT_COURSE_TEST_COURSE_ID') || 1,
  status: numberValueAllowZero('status', 'AGENT_COURSE_TEST_STATUS', 2),
  title: valueOf('title', 'AGENT_COURSE_TEST_TITLE', `Agent 课程 CRUD 测试 ${new Date().toISOString()}`)
}

if (args.help || args.h) {
  printHelp()
  process.exit(0)
}

main().catch((error) => {
  console.error(`\n[FAIL] ${error.message}`)
  if (error.details) {
    console.error(format(error.details))
  }
  process.exit(1)
})

async function main() {
  assertFetch()
  console.log(`[1/8] 登录 ${config.baseUrl}，账号：${config.username}`)
  const login = await postJson('/auth/login', {
    username: config.username,
    password: "[REDACTED]"
  })
  const loginData = unwrapData(login)
  const token = loginData?.token
  if (!token) {
    throw new Error('登录成功响应中没有 data.token')
  }
  console.log(`[OK] 登录成功：userId=${loginData.userId}, role=${loginData.roleCode}`)

  if (!config.skipHealth) {
    console.log('\n[2/8] 检查模型健康状态')
    const health = unwrapData(await getJson('/api/agent/model/health', token))
    console.log(format(health))
    if (!health?.healthy && config.requireModel) {
      throw new Error(`模型健康检查失败：${health?.message || 'unknown'}`)
    }
  } else {
    console.log('\n[2/8] 已跳过模型健康检查')
  }

  let agentId = config.agentId
  if (!agentId) {
    console.log(`\n[3/8] 确保课程 CRUD 测试 Agent 存在：${TEST_AGENT_CODE}`)
    const agent = await ensureCourseCrudAgent(token)
    agentId = agent.id
    console.log(`[OK] 使用测试 Agent：id=${agentId}, code=${agent.code}`)
  } else {
    console.log(`\n[3/8] 使用指定 Agent：id=${agentId}`)
  }

  console.log('\n[4/8] 创建 Agent 会话')
  const session = unwrapData(await postJson('/api/agent/sessions', {
    agentId,
    title: 'Agent course CRUD smoke test'
  }, token))
  const sessionId = session?.id
  if (!sessionId) {
    throw new Error('创建会话响应中没有 data.id')
  }
  console.log(`[OK] 会话已创建：sessionId=${sessionId}`)

  console.log('\n[5/8] 验证创建课程计划：course.create')
  const createPlan = await assertPlan(sessionId, token, {
    actionCode: 'course.create',
    prompt: `帮我创建一门课程，标题是${config.title}，分类ID是${config.categoryId}，先生成待确认操作计划，不要直接执行。`,
    parameters: {
      title: config.title,
      categoryId: config.categoryId
    },
    missingRequiredFields: []
  })

  let targetCourseId = config.courseId
  if (config.confirm) {
    console.log('[WRITE] 确认执行创建课程')
    const createResult = unwrapData(await postJson('/api/agent/actions/confirm', {
      actionId: createPlan.actionId,
      actionCode: 'course.create',
      confirmed: true,
      parameters: {
        title: config.title,
        categoryId: config.categoryId,
        description: 'Agent 课程 CRUD 冒烟测试创建的课程。',
        difficulty: 'beginner',
        status: 1
      }
    }, token))
    targetCourseId = createResult?.data?.courseId || createResult?.courseId || targetCourseId
    console.log(`[OK] 创建课程执行完成：courseId=${targetCourseId}`)
  }

  console.log('\n[6/8] 验证修改课程计划：course.update')
  const updatePlan = await assertPlan(sessionId, token, {
    actionCode: 'course.update',
    prompt: `帮我修改 ${targetCourseId} 号课程，把标题改成 ${config.title} - 已修改，先生成待确认操作计划。`
  })

  if (config.confirm) {
    console.log('[WRITE] 确认执行修改课程')
    const updateResult = unwrapData(await postJson('/api/agent/actions/confirm', {
      actionId: updatePlan.actionId,
      actionCode: 'course.update',
      confirmed: true,
      parameters: {
        courseId: targetCourseId,
        title: `${config.title} - 已修改`
      }
    }, token))
    console.log('[OK] 修改课程执行完成：')
    console.log(format(updateResult))
  }

  console.log('\n[7/8] 验证课程状态变更计划：course.status')
  const statusPlan = await assertPlan(sessionId, token, {
    actionCode: 'course.status',
    prompt: `帮我下架 ${targetCourseId} 号课程，先生成待确认操作计划。`
  })
  if (statusPlan.confirmationText !== COURSE_STATUS_CONFIRMATION_TEXT) {
    throw new Error(`课程状态计划确认文本不匹配：${statusPlan.confirmationText || '空'}`)
  }

  if (config.confirm) {
    console.log('[WRITE] 确认执行课程状态变更')
    const statusResult = unwrapData(await postJson('/api/agent/actions/confirm', {
      actionId: statusPlan.actionId,
      actionCode: 'course.status',
      confirmed: true,
      confirmationText: COURSE_STATUS_CONFIRMATION_TEXT,
      parameters: {
        courseId: targetCourseId,
        status: config.status,
        reason: 'Agent 课程 CRUD 冒烟测试'
      }
    }, token))
    console.log('[OK] 课程状态变更执行完成：')
    console.log(format(statusResult))
  }

  console.log('\n[8/8] 验证 pending action 列表仍可查询')
  const pendingList = unwrapData(await getJson('/api/agent/actions/pending', token))
  const pendingActions = Array.isArray(pendingList) ? pendingList : []
  console.log(`[OK] 当前用户 pending action 数量：${pendingActions.length}`)

  if (config.confirm) {
    console.log('\n[PASS] 课程 CRUD Agent 冒烟测试完成，已执行创建、修改、状态变更。')
  } else {
    console.log('\n[PASS] 课程 CRUD Agent 冒烟测试完成：创建、修改、状态变更计划和 pending action 均已覆盖。')
    console.log('提示：默认不执行写操作；需要真实写入时加 --confirm，可用 --category-id 指定课程分类。')
  }
}

async function assertPlan(sessionId, token, expected) {
  const reply = await chatAgent(sessionId, expected.prompt, token)
  if (reply.error) {
    throw withDetails(new Error(`生成 ${expected.actionCode} 待确认计划失败`), reply.error)
  }
  console.log('[OK] 回复预览：')
  console.log(preview(reply.content))

  const pendingAction = reply.complete?.pendingAction || null
  const actionId = pendingAction?.actionId || extractActionId(reply.content)
  if (!actionId) {
    throw new Error(`未获取到 ${expected.actionCode} 的 actionId，请确认测试 Agent 启用了课程计划工具`)
  }
  if (pendingAction?.actionCode && pendingAction.actionCode !== expected.actionCode) {
    throw new Error(`actionCode 不匹配：期望 ${expected.actionCode}，实际 ${pendingAction.actionCode}`)
  }

  const detail = unwrapData(await getJson(`/api/agent/actions/pending/${encodeURIComponent(actionId)}`, token))
  if (detail?.actionId !== actionId) {
    throw new Error(`pending action 详情不匹配：期望 ${actionId}，实际 ${detail?.actionId || '空'}`)
  }
  if (detail?.actionCode !== expected.actionCode) {
    throw new Error(`pending action actionCode 不匹配：期望 ${expected.actionCode}，实际 ${detail?.actionCode || '空'}`)
  }
  assertExpectedParameters(detail.parameters || pendingAction?.parameters, expected.parameters)
  assertExpectedMissingFields(detail.parameters?.missingRequiredFields, expected.missingRequiredFields)
  console.log(`[OK] ${expected.actionCode} actionId=${actionId}`)
  return {
    actionId,
    actionCode: detail.actionCode,
    confirmationText: detail.confirmationText || pendingAction?.confirmationText || null
  }
}

function assertExpectedParameters(actual, expected) {
  if (!expected) return
  if (!actual || typeof actual !== 'object') {
    throw new Error('pending action 详情中没有 parameters，无法验证自动参数抽取')
  }
  for (const [key, expectedValue] of Object.entries(expected)) {
    const actualValue = actual[key]
    if (String(actualValue) !== String(expectedValue)) {
      throw new Error(`参数抽取不匹配：${key} 期望 ${expectedValue}，实际 ${actualValue ?? '空'}`)
    }
  }
  console.log(`[OK] 自动参数抽取：${Object.keys(expected).map((key) => `${key}=${actual[key]}`).join(', ')}`)
}

function assertExpectedMissingFields(actual, expected) {
  if (!expected) return
  const actualList = Array.isArray(actual) ? actual : []
  if (actualList.length !== expected.length || expected.some((item) => !actualList.includes(item))) {
    throw new Error(`缺失字段不匹配：期望 ${JSON.stringify(expected)}，实际 ${JSON.stringify(actualList)}`)
  }
  console.log(`[OK] 缺失必填字段：${actualList.length ? actualList.join(', ') : '无'}`)
}

async function ensureCourseCrudAgent(token) {
  const list = await getJson(`/api/agent/definitions?keyword=${encodeURIComponent(TEST_AGENT_CODE)}`, token)
  const agents = Array.isArray(unwrapData(list)) ? unwrapData(list) : []
  const existing = agents.find((agent) => agent.code === TEST_AGENT_CODE)
  if (existing) {
    if (existing.status === 1 && includesAllTools(existing.enabledTools, COURSE_TOOLS)) {
      return existing
    }
    const updated = await putJson(`/api/agent/definitions/${existing.id}`, courseCrudAgentPayload(), token)
    return unwrapData(updated)
  }

  const created = await postJson('/api/agent/definitions', courseCrudAgentPayload(), token)
  return unwrapData(created)
}

function courseCrudAgentPayload() {
  return {
    code: TEST_AGENT_CODE,
    name: 'Agent 课程 CRUD 测试助手',
    description: '用于本地验证课程查询、创建、修改和状态变更 Agent 链路。',
    systemPrompt: '你是 AI 智学平台的课程 CRUD 测试助手。请用简洁中文回答，所有写操作必须先生成待确认计划，不得直接执行。',
    temperature: 0.2,
    knowledgeBaseIds: '[]',
    enabledTools: JSON.stringify(COURSE_TOOLS),
    status: 1
  }
}

function includesAllTools(value, expectedTools) {
  try {
    const tools = typeof value === 'string' ? JSON.parse(value) : value
    return Array.isArray(tools) && expectedTools.every((tool) => tools.includes(tool))
  } catch {
    return false
  }
}

async function chatAgent(sessionId, content, token) {
  const response = await fetch(`${config.baseUrl}/api/agent/sessions/${sessionId}/chat`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json;charset=utf-8'
    },
    body: JSON.stringify({ content })
  })

  const text = await response.text()
  const payload = parseMaybeJson(text)
  if (!response.ok || (payload?.code && String(payload.code) !== '200')) {
    return { content: '', complete: null, error: payload || `HTTP ${response.status} ${text}` }
  }
  const complete = unwrapData(payload)
  return { content: complete?.content || '', complete, error: null }
}

async function getJson(path, token) {
  return requestJson('GET', path, undefined, token)
}

async function postJson(path, body, token) {
  return requestJson('POST', path, body, token)
}

async function putJson(path, body, token) {
  return requestJson('PUT', path, body, token)
}

async function requestJson(method, path, body, token) {
  const response = await fetch(`${config.baseUrl}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      'Content-Type': 'application/json;charset=utf-8'
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await response.text()
  const payload = parseMaybeJson(text)
  if (!response.ok) {
    throw withDetails(new Error(`${method} ${path} 失败：HTTP ${response.status}`), payload)
  }
  if (payload?.code && String(payload.code) !== '200') {
    throw withDetails(new Error(`${method} ${path} 失败：${payload.msg || payload.message || payload.code}`), payload)
  }
  return payload
}

function extractActionId(text) {
  return (text || '').match(/actionId\s*[=：:]\s*([A-Za-z0-9_-]+)/i)?.[1] || ''
}

function unwrapData(payload) {
  let data = payload
  while (data && typeof data === 'object' && !Array.isArray(data) && data.data !== undefined) {
    data = data.data
  }
  return data
}

function parseMaybeJson(value) {
  if (!value || typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

function preview(value) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  return text.length > 500 ? `${text.slice(0, 500)}...` : text
}

function format(value) {
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2)
}

function withDetails(error, details) {
  error.details = details
  return error
}

function parseArgs(argv) {
  const result = {}
  for (let index = 0; index < argv.length; index += 1) {
    const item = argv[index]
    if (!item.startsWith('--')) continue
    const raw = item.slice(2)
    const equalIndex = raw.indexOf('=')
    if (equalIndex !== -1) {
      result[raw.slice(0, equalIndex)] = raw.slice(equalIndex + 1)
      continue
    }
    const next = argv[index + 1]
    if (!next || next.startsWith('--')) {
      result[raw] = true
    } else {
      result[raw] = next
      index += 1
    }
  }
  return result
}

function valueOf(name, envName, fallback) {
  return args[name] ?? process.env[envName] ?? fallback
}

function numberValue(name, envName) {
  const value = valueOf(name, envName, '')
  if (value === '' || value === undefined || value === null) return null
  const number = Number(value)
  if (!Number.isFinite(number) || number <= 0) {
    throw new Error(`${name} 必须是正整数`)
  }
  return Math.trunc(number)
}

function numberValueAllowZero(name, envName, fallback) {
  const value = valueOf(name, envName, fallback)
  const number = Number(value)
  if (!Number.isFinite(number) || number < 0) {
    throw new Error(`${name} 必须是非负整数`)
  }
  return Math.trunc(number)
}

function boolValue(name, envName) {
  const value = valueOf(name, envName, false)
  return value === true || value === 'true' || value === '1' || value === 'yes'
}

function assertFetch() {
  if (typeof fetch !== 'function') {
    throw new Error('当前 Node 版本不支持 fetch，请使用 Node.js 18 或更高版本')
  }
}

function printHelp() {
  console.log(`Agent course CRUD smoke test

Usage:
  node scripts/agent-course-crud-smoke-test.mjs [options]

Options:
  --base-url <url>        Backend base URL. Default: ${DEFAULT_BASE_URL}
  --username <name>       Login username. Default: admin
  --password <password>   Login password. Default: 123456
  --agent-id <id>         Use an existing agent instead of creating ${TEST_AGENT_CODE}
  --skip-health          Skip GET /api/agent/model/health
  --require-model        Fail when model health fails
  --confirm              Execute course create, update and status change
  --category-id <id>     Course category id used with --confirm. Default: 1
  --course-id <id>       Existing course id used without --confirm. Default: 1
  --status <number>      Target status used with --confirm status change. Default: 2
  --title <text>         Course title used by prompts and --confirm
`)
}
