// Pure model-configuration rules shared by the setup wizard, settings page and recovery view.
export const MODEL_ROLES = ['generation', 'planning', 'embedding', 'ocr']

export const ROLE_META = {
  generation: { title: '内容生成', summary: '资料问答、讲义和题目生成使用的对话模型', timeoutSeconds: 30, protocol: 'chat' },
  planning: { title: 'Agent 规划', summary: 'Agent 判断任务步骤使用的对话模型', timeoutSeconds: 10, protocol: 'chat' },
  embedding: { title: '语义向量', summary: '资料语义检索使用的向量模型；未启用时检索退回关键词', timeoutSeconds: 30, protocol: 'embedding' },
  ocr: { title: 'OCR 识别', summary: '扫描件、数学页和图片的文字识别', timeoutSeconds: 60, protocol: 'ocr' },
}

// The adapters speak OpenAI-compatible chat, embeddings and vision-chat endpoints only.
// A preset fills editable fields; it is not a claim that the service will pass a probe.
export const ADAPTER_PROTOCOL_NOTE = '当前适配器只使用 OpenAI 兼容协议。Anthropic、Gemini 等原生协议尚未接入，需要服务自身提供兼容端点。'

const CUSTOM_PRESET = { id: 'custom', label: '自定义 OpenAI 兼容服务', baseUrl: '', model: '' }

export const ENDPOINT_PRESETS = {
  chat: [
    CUSTOM_PRESET,
    { id: 'openai', label: 'OpenAI', baseUrl: 'https://api.openai.com/v1', model: '' },
    { id: 'deepseek', label: 'DeepSeek（OpenAI 兼容端点）', baseUrl: 'https://api.deepseek.com/v1', model: '' },
  ],
  embedding: [
    CUSTOM_PRESET,
    { id: 'siliconflow-embedding', label: '硅基流动 · 既定向量模型', baseUrl: 'https://api.siliconflow.cn/v1', model: 'Qwen/Qwen3-VL-Embedding-8B' },
  ],
  ocr: [
    CUSTOM_PRESET,
    { id: 'siliconflow-ocr', label: '硅基流动 · 既定 OCR 模型', baseUrl: 'https://api.siliconflow.cn/v1', model: 'deepseek-ai/DeepSeek-OCR' },
  ],
}

const SOURCE_LABELS = {
  ENVIRONMENT: '环境变量',
  PRIVATE_FILE: '实例配置',
  INHERITED: '继承内容生成',
  DEFAULT: '默认值',
}

const CONFIGURATION_LABELS = {
  CONFIGURED_UNVERIFIED: { label: '已配置，未验证', tone: 'accent' },
  MODEL_DISABLED: { label: '已停用', tone: 'neutral' },
  MODEL_NOT_CONFIGURED: { label: '未配置', tone: 'neutral' },
  CONFIGURATION_INCOMPLETE: { label: '配置不完整', tone: 'caution' },
  MODEL_ENDPOINT_INVALID: { label: '地址无效', tone: 'critical' },
  MODEL_BLOCKED: { label: '已被策略拒绝', tone: 'critical' },
}

const PROBE_LABELS = {
  PASS: { label: '探测通过', tone: 'positive' },
  FAIL: { label: '探测失败', tone: 'critical' },
  SKIPPED: { label: '未执行探测', tone: 'caution' },
}

const CHECK_LABELS = {
  CONFIGURATION: '配置检查',
  TEXT: '文本回复',
  JSON_OBJECT: 'JSON 输出',
  STREAM: '流式输出',
  CONTEXT_SAMPLE: '上下文样本',
  EMBEDDING: '向量生成',
  OCR_IMAGE: '图片识别',
}

const CHECK_STATUS_LABELS = { PASS: '通过', FAIL: '失败', SKIPPED: '未执行' }

export function createEmptyDraft(role) {
  return {
    enabled: false,
    baseUrl: '',
    model: '',
    timeoutSeconds: ROLE_META[role].timeoutSeconds,
    contextWindowTokens: 0,
    inheritFrom: null,
    secretAction: 'KEEP',
    secretValue: '',
  }
}

export function createEmptyDrafts() {
  return Object.fromEntries(MODEL_ROLES.map(role => [role, createEmptyDraft(role)]))
}

export function draftFromView(role, view) {
  if (!view) return createEmptyDraft(role)
  return {
    enabled: Boolean(view.enabled),
    baseUrl: view.baseUrl || '',
    model: view.model || '',
    timeoutSeconds: view.timeoutSeconds || ROLE_META[role].timeoutSeconds,
    contextWindowTokens: view.contextWindowTokens || 0,
    inheritFrom: view.inheritFrom || null,
    secretAction: 'KEEP',
    secretValue: '',
  }
}

export function draftsFromViews(views) {
  return Object.fromEntries(MODEL_ROLES.map(role => [role, draftFromView(role, views?.[role])]))
}

export function toRolePayload(role, draft) {
  const inherits = role === 'planning' && draft.inheritFrom === 'generation'
  // REPLACE only exists with a value; an untouched secret field means nothing changed.
  const replacing = !inherits && draft.secretAction === 'REPLACE' && Boolean(draft.secretValue)
  const payload = {
    enabled: Boolean(draft.enabled),
    baseUrl: inherits ? '' : draft.baseUrl.trim(),
    model: inherits ? '' : draft.model.trim(),
    timeoutSeconds: Number(draft.timeoutSeconds),
    contextWindowTokens: Number(draft.contextWindowTokens) || 0,
    inheritFrom: inherits ? 'generation' : null,
    secretAction: replacing || (!inherits && draft.secretAction === 'CLEAR') ? draft.secretAction : 'KEEP',
  }
  if (replacing) payload.secretValue = draft.secretValue
  return payload
}

export function toModelDrafts(drafts) {
  return Object.fromEntries(MODEL_ROLES.map(role => [role, toRolePayload(role, drafts[role])]))
}

export function clearDraftSecrets(drafts) {
  for (const role of MODEL_ROLES) {
    drafts[role].secretValue = ''
    drafts[role].secretAction = 'KEEP'
  }
}

function endpointError(value) {
  let url
  try {
    url = new URL(value)
  } catch {
    return '请输入完整的接口地址，例如 https://example.com/v1'
  }
  if (!['http:', 'https:'].includes(url.protocol)) return '接口地址只支持 http 或 https'
  if (url.username || url.password) return '接口地址不能包含账号信息，请在凭证栏填写'
  if (url.search || url.hash) return '接口地址不能包含查询参数或片段'
  return ''
}

// Client checks mirror the contract so problems show before a request; the server stays authoritative.
export function validateRoleDraft(role, draft, view = null) {
  const errors = {}
  const timeout = Number(draft.timeoutSeconds)
  if (!Number.isInteger(timeout) || timeout < 1 || timeout > 300) errors.timeoutSeconds = '超时需为 1 到 300 秒的整数'
  if (!draft.enabled || draft.inheritFrom) return errors

  const locked = new Set(view?.lockedFields || [])
  if (!locked.has('baseUrl')) {
    if (!draft.baseUrl.trim()) errors.baseUrl = '启用时需要填写接口地址'
    else {
      const problem = endpointError(draft.baseUrl.trim())
      if (problem) errors.baseUrl = problem
    }
  }
  if (!locked.has('model') && !draft.model.trim()) errors.model = '启用时需要填写模型名称'
  if (!locked.has('secret')) {
    if (draft.secretAction === 'REPLACE' && !draft.secretValue) errors.secret = '请输入新的凭证'
    if (draft.secretAction === 'CLEAR') errors.secret = '清除凭证后该能力无法启用，请停用它或填写新凭证'
    if (draft.secretAction === 'KEEP' && !view?.secretConfigured) errors.secret = '启用时需要提供凭证'
  }
  return errors
}

export function validateDrafts(drafts, views = null) {
  const errors = {}
  for (const role of MODEL_ROLES) {
    const roleErrors = validateRoleDraft(role, drafts[role], views?.[role])
    if (Object.keys(roleErrors).length) errors[role] = roleErrors
  }
  if (drafts.planning.enabled && drafts.planning.inheritFrom && !drafts.generation.enabled) {
    errors.planning = { ...errors.planning, inheritFrom: '继承内容生成时，需要先启用内容生成' }
  }
  return errors
}

export function configurationState(view) {
  if (!view) return { label: '未配置', tone: 'neutral' }
  return CONFIGURATION_LABELS[view.configuration] || { label: '配置状态待确认', tone: 'caution' }
}

export function draftConfigurationState(draft) {
  if (!draft.enabled) return { label: '未启用', tone: 'neutral' }
  return { label: '已填写，待验证', tone: 'accent' }
}

export function probeState(report) {
  if (!report) return { label: '未探测', tone: 'neutral' }
  return PROBE_LABELS[report.status] || { label: '探测结果待确认', tone: 'caution' }
}

export const sourceLabel = source => SOURCE_LABELS[source] || ''
export const checkLabel = name => CHECK_LABELS[name] || name
export const checkStatusLabel = status => CHECK_STATUS_LABELS[status] || status

export function fieldErrorsForRole(fieldErrors, role) {
  return fieldErrors.filter(item => String(item.field || '').split(/[.[\]]/).includes(role))
}
