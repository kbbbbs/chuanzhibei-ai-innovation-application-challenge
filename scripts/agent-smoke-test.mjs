#!/usr/bin/env node

const DEFAULT_BASE_URL = 'http://localhost:8080'
const TEST_AGENT_CODE = 'agent_smoke_test'

const args = parseArgs(process.argv.slice(2))

const config = {
  baseUrl: valueOf('base-url', 'AGENT_BASE_URL', DEFAULT_BASE_URL).replace(/\/$/, ''),
  username: valueOf('username', 'AGENT_TEST_USERNAME', 'admin'),
  password: "[REDACTED]"
  agentId: numberValue('agent-id', 'AGENT_TEST_AGENT_ID'),
  confirm: boolValue('confirm', 'AGENT_TEST_CONFIRM'),
  requireModel: boolValue('require-model', 'AGENT_TEST_REQUIRE_MODEL'),
  skipHealth: boolValue('skip-health', 'AGENT_TEST_SKIP_HEALTH'),
  chat: valueOf('chat', 'AGENT_TEST_CHAT', '请用一句话说明你能帮助平台用户做什么。'),
  actionPrompt: valueOf(
    'action-prompt',
    'AGENT_TEST_ACTION_PROMPT',
    '请发布一条课程通知，标题是 Agent 冒烟测试通知，内容是这是一条自动化测试通知，请先生成待确认操作计划，不要直接执行。'
  ),
  actionTitle: valueOf('action-title', 'AGENT_TEST_ACTION_TITLE', `Agent 冒烟测试通知 ${new Date().toISOString()}`),
  actionContent: valueOf('action-content', 'AGENT_TEST_ACTION_CONTENT', '这是一条由 Agent 冒烟测试脚本创建的测试通知。')
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
  console.log(`[1/7] 登录 ${config.baseUrl}，账号：${config.username}`)
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
    console.log('\n[2/7] 检查模型健康状态')
    const health = await getJson('/api/agent/model/health', token)
    const healthData = unwrapData(health)
    console.log(format(healthData))
    if (!healthData?.healthy && config.requireModel) {
      const reason = healthData?.message || '模型健康检查未通过'
      throw new Error(`模型健康检查失败：${reason}`)
    }
  } else {
    console.log('\n[2/7] 已跳过模型健康检查')
  }

  let agentId = config.agentId
  if (!agentId) {
    console.log(`\n[3/7] 确保测试 Agent 存在：${TEST_AGENT_CODE}`)
    const agent = await ensureSmokeAgent(token)
    agentId = agent.id
    console.log(`[OK] 使用测试 Agent：id=${agentId}, code=${agent.code}`)
  } else {
    console.log(`\n[3/7] 使用指定 Agent：id=${agentId}`)
  }

  console.log('\n[4/7] 创建 Agent 会话')
  const session = await postJson('/api/agent/sessions', {
    agentId,
    title: 'Agent smoke test'
  }, token)
  const sessionData = unwrapData(session)
  const sessionId = sessionData?.id
  if (!sessionId) {
    throw new Error('创建会话响应中没有 data.id')
  }
  console.log(`[OK] 会话已创建：sessionId=${sessionId}`)

  console.log('\n[5/7] 发送普通非流式聊天请求')
  const normalReply = await chatAgent(sessionId, config.chat, token)
  if (normalReply.error) {
    console.log('[WARN] 普通聊天失败，通常是模型配置或模型服务问题：')
    console.log(format(normalReply.error))
    if (config.requireModel) {
      throw withDetails(new Error('普通非流式聊天失败，且启用了 --require-model'), normalReply.error)
    }
  } else {
    console.log('[OK] 普通聊天完成，回复预览：')
    console.log(preview(normalReply.content))
  }

  console.log('\n[6/7] 发送自然语言写操作计划请求')
  const planReply = await chatAgent(sessionId, config.actionPrompt, token)
  if (planReply.error) {
    throw withDetails(new Error('生成待确认操作计划失败'), planReply.error)
  }
  console.log('[OK] 待确认操作计划已生成，回复预览：')
  console.log(preview(planReply.content))

  const pendingAction = planReply.complete?.pendingAction || null
  const actionId = pendingAction?.actionId || extractActionId(planReply.content)
  if (!actionId) {
    throw new Error('未从 Agent 回复中获取到 actionId，请确认测试 Agent 启用了 notice_create_plan')
  }
  console.log(`[OK] 获取到 actionId=${actionId}`)

  const pendingList = unwrapData(await getJson('/api/agent/actions/pending', token))
  const pendingActions = Array.isArray(pendingList) ? pendingList : []
  if (!pendingActions.some(item => item?.actionId === actionId)) {
    throw new Error(`pending action 列表中未找到 actionId=${actionId}`)
  }
  const pendingDetail = unwrapData(await getJson(`/api/agent/actions/pending/${encodeURIComponent(actionId)}`, token))
  if (pendingDetail?.actionId !== actionId) {
    throw new Error(`pending action 详情不匹配：期望 ${actionId}，实际 ${pendingDetail?.actionId || '空'}`)
  }
  console.log('[OK] pending action 查询和详情接口可用')

  if (!config.confirm) {
    console.log('\n[7/7] 跳过确认执行。需要真正写入一条通知时，加 --confirm。')
    console.log('\n[PASS] Agent 冒烟测试完成：登录、模型健康检查、会话、非流式聊天、写操作计划、pending action 均已覆盖。')
    return
  }

  console.log('\n[7/7] 确认执行待确认动作：notice.create')
  const confirmResult = await postJson('/api/agent/actions/confirm', {
    actionId,
    actionCode: 'notice.create',
    confirmed: true,
    parameters: {
      publisherId: loginData.userId,
      title: config.actionTitle,
      content: config.actionContent,
      noticeType: 'system',
      status: 1
    }
  }, token)
  console.log('[OK] 确认执行结果：')
  console.log(format(unwrapData(confirmResult)))
  console.log('\n[PASS] Agent 冒烟测试完成，且已执行一条测试通知写入。')
}

async function ensureSmokeAgent(token) {
  const list = await getJson(`/api/agent/definitions?keyword=${encodeURIComponent(TEST_AGENT_CODE)}`, token)
  const agents = Array.isArray(unwrapData(list)) ? unwrapData(list) : []
  const existing = agents.find((agent) => agent.code === TEST_AGENT_CODE)
  if (existing) {
    if (existing.status === 1 && includesTool(existing.enabledTools, 'notice_create_plan')) {
      return existing
    }
    const updated = await putJson(`/api/agent/definitions/${existing.id}`, smokeAgentPayload(), token)
    return unwrapData(updated)
  }

  const created = await postJson('/api/agent/definitions', smokeAgentPayload(), token)
  return unwrapData(created)
}

function smokeAgentPayload() {
  return {
    code: TEST_AGENT_CODE,
    name: 'Agent 冒烟测试助手',
    description: '用于本地验证 Agent 会话、非流式聊天和待确认写操作。',
    systemPrompt: '你是 AI 智学平台的 Agent 冒烟测试助手。请用简洁中文回答，并严格遵守写操作先生成待确认计划的规则。',
    temperature: 0.2,
    knowledgeBaseIds: '[]',
    enabledTools: '["notice_create_plan"]',
    status: 1
  }
}

function includesTool(value, expectedTool) {
  try {
    const tools = typeof value === 'string' ? JSON.parse(value) : value
    return Array.isArray(tools) && tools.includes(expectedTool)
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

function boolValue(name, envName) {
  const value = valueOf(name, envName, false)
  if (value === true || value === 'true' || value === '1' || value === 'yes') return true
  return false
}

function assertFetch() {
  if (typeof fetch !== 'function') {
    throw new Error('当前 Node 版本不支持 fetch，请使用 Node.js 18 或更高版本')
  }
}

function printHelp() {
  console.log(`Agent smoke test

Usage:
  node scripts/agent-smoke-test.mjs [options]

Options:
  --base-url <url>        Backend base URL. Default: ${DEFAULT_BASE_URL}
  --username <name>       Login username. Default: admin
  --password <password>   Login password. Default: 123456
  --agent-id <id>         Use an existing agent instead of creating ${TEST_AGENT_CODE}
  --confirm              Execute the generated pending notice action
  --require-model        Fail when model health or normal chat fails
  --skip-health          Skip GET /api/agent/model/health
  --chat <text>          Normal chat prompt
  --action-prompt <text> Natural-language write operation prompt
  --action-title <text>  Notice title used with --confirm
  --action-content <txt> Notice content used with --confirm
`)
}
