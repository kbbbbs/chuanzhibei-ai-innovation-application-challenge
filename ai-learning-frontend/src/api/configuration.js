// Installation, model settings and recovery endpoints from API-CONTRACT v1.
// This client is separate from utils/request: these flows need the contract's error shape,
// must not trigger the global 401 logout, and must never reach a logger or global toast.
import axios from 'axios'
import { useUserStore } from '@/store/user'
import { API_BASE_URL, backendUrl } from '@/utils/backendUrl'

const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  withCredentials: true,
})

// Generous enough for every per-check budget; the server enforces its own total budget.
const PROBE_TIMEOUT_MS = 600000
const POLL_TIMEOUT_MS = 8000
const TRANSIENT_STATUSES = new Set([408, 502, 503, 504])

// Memory only. Install and recovery sessions carry separate tokens; a refresh recovers them
// from their session endpoints, and an install token never authorizes a recovery write.
let setupCsrf = ''
let recoveryCsrf = ''

export class ConfigurationError extends Error {
  constructor({ message, status = 0, errorCode = '', fieldErrors = [], retryable = false, transient = false }) {
    super(message)
    this.name = 'ConfigurationError'
    this.status = status
    this.errorCode = errorCode
    this.fieldErrors = fieldErrors
    this.retryable = retryable
    this.transient = transient
  }
}

function fromEnvelope(body, status) {
  const data = body?.data && typeof body.data === 'object' ? body.data : {}
  return new ConfigurationError({
    message: body?.msg || '请求未完成',
    status,
    errorCode: data.errorCode || '',
    fieldErrors: Array.isArray(data.fieldErrors) ? data.fieldErrors : [],
    retryable: Boolean(data.retryable) || TRANSIENT_STATUSES.has(status),
    transient: TRANSIENT_STATUSES.has(status),
  })
}

function toConfigurationError(error) {
  if (error instanceof ConfigurationError) return error
  const response = error?.response
  if (!response) {
    return new ConfigurationError({
      message: '暂时无法连接到服务',
      errorCode: error?.code === 'ECONNABORTED' ? 'REQUEST_TIMEOUT' : 'NETWORK_UNREACHABLE',
      retryable: true,
      transient: true,
    })
  }
  const status = response.status
  if (response.data && typeof response.data === 'object' && 'code' in response.data) {
    return fromEnvelope(response.data, status)
  }
  return new ConfigurationError({
    message: TRANSIENT_STATUSES.has(status) ? '服务暂不可用' : '服务返回了无法识别的响应',
    status,
    retryable: TRANSIENT_STATUSES.has(status),
    transient: TRANSIENT_STATUSES.has(status),
  })
}

function isCancelled(error) {
  return axios.isCancel(error) || error?.code === 'ERR_CANCELED'
}

async function call(config) {
  try {
    const { data: body, status } = await client.request(config)
    if (!body || typeof body !== 'object' || !('code' in body)) {
      throw new ConfigurationError({ message: '服务返回了无法识别的响应', status, errorCode: 'INVALID_RESPONSE', retryable: true })
    }
    if (!['200', '202'].includes(String(body.code))) throw fromEnvelope(body, Number(body.code) || status)
    return body.data
  } catch (error) {
    if (isCancelled(error)) throw error
    throw toConfigurationError(error)
  }
}

function rememberSetupSession(session) {
  setupCsrf = session?.csrfToken || ''
  return session
}

function rememberRecoverySession(session) {
  recoveryCsrf = session?.csrfToken || ''
  return session
}

// A 202 rebinds the recovery cookie to the new operation, so any older token is stale.
function rememberOperation(summary) {
  recoveryCsrf = summary?.recoveryCsrfToken || ''
  return summary
}

function setupHeaders() {
  return setupCsrf ? { 'X-CSRF-Token': setupCsrf } : {}
}

async function recoveryHeaders() {
  if (!recoveryCsrf) await getRecoverySession()
  return recoveryCsrf ? { 'X-CSRF-Token': recoveryCsrf } : {}
}

function bearerHeaders() {
  const token = useUserStore().token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export function isRequestCancelled(error) {
  return isCancelled(error)
}

export function createIdempotencyKey() {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  // randomUUID requires a secure context; plain-HTTP intranet access still has getRandomValues.
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('')
}

// OperationSummary.statusUrl is a backend-relative path; backendUrl adds the prefix once.
export const getOperationAt = (statusUrl, { admin = false } = {}) => call({
  method: 'get', baseURL: '', url: backendUrl(statusUrl), headers: admin ? bearerHeaders() : {}, timeout: POLL_TIMEOUT_MS,
})

// Public lifecycle state
export const getSetupStatus = (config = {}) => call({ method: 'get', url: '/api/setup/status', ...config })

// Installation session
export const createSetupSession = async code => rememberSetupSession(await call({ method: 'post', url: '/api/setup/session', data: { code } }))
export const getSetupSession = async () => rememberSetupSession(await call({ method: 'get', url: '/api/setup/session' }))
export const checkSetupDatabase = database => call({
  method: 'post', url: '/api/setup/database/check', data: { database }, headers: setupHeaders(),
})
export const probeSetupModel = (role, roles, { signal } = {}) => call({
  method: 'post', url: '/api/setup/models/probe', data: { role, roles }, headers: setupHeaders(), timeout: PROBE_TIMEOUT_MS, signal,
})
export const completeSetup = async (payload, idempotencyKey) => rememberOperation(await call({
  method: 'post', url: '/api/setup/complete', data: payload,
  headers: { ...setupHeaders(), 'Idempotency-Key': idempotencyKey },
}))
export const getSetupOperation = id => call({
  method: 'get', url: `/api/setup/operations/${encodeURIComponent(id)}`, timeout: POLL_TIMEOUT_MS,
})

// Administrator settings
export const getModelSettings = () => call({ method: 'get', url: '/api/admin/settings/models', headers: bearerHeaders() })
export const saveModelSettings = payload => call({
  method: 'put', url: '/api/admin/settings/models', data: payload, headers: bearerHeaders(),
})
export const probeModelDraft = (role, roles, { signal } = {}) => call({
  method: 'post', url: '/api/admin/settings/models/probe', data: { role, roles }, headers: bearerHeaders(), timeout: PROBE_TIMEOUT_MS, signal,
})
export const probeActiveModel = (role, { signal } = {}) => call({
  method: 'post', url: `/api/admin/models/${encodeURIComponent(role)}/probe`, headers: bearerHeaders(), timeout: PROBE_TIMEOUT_MS, signal,
})
export const getModelStatus = () => call({ method: 'get', url: '/api/admin/models/status', headers: bearerHeaders() })
export const applyModelSettings = async (payload, idempotencyKey) => rememberOperation(await call({
  method: 'post', url: '/api/admin/settings/apply', data: payload,
  headers: { ...bearerHeaders(), 'Idempotency-Key': idempotencyKey },
}))
export const getSettingsOperation = id => call({
  method: 'get', url: `/api/admin/settings/operations/${encodeURIComponent(id)}`, headers: bearerHeaders(), timeout: POLL_TIMEOUT_MS,
})

// Recovery session
export const createRecoverySession = async code => rememberRecoverySession(await call({ method: 'post', url: '/api/setup/recovery/session', data: { code } }))
export async function getRecoverySession() {
  return rememberRecoverySession(await call({ method: 'get', url: '/api/setup/recovery/session' }))
}
export const getRecoveryConfig = () => call({ method: 'get', url: '/api/setup/recovery/config' })
export const saveRecoveryConfig = async payload => call({
  method: 'put', url: '/api/setup/recovery/config', data: payload, headers: await recoveryHeaders(),
})
export const applyRecovery = async (payload, idempotencyKey) => rememberOperation(await call({
  method: 'post', url: '/api/setup/recovery/apply', data: payload,
  headers: { ...await recoveryHeaders(), 'Idempotency-Key': idempotencyKey },
}))
export const rollbackRecovery = async (payload, idempotencyKey) => rememberOperation(await call({
  method: 'post', url: '/api/setup/recovery/rollback', data: payload,
  headers: { ...await recoveryHeaders(), 'Idempotency-Key': idempotencyKey },
}))
