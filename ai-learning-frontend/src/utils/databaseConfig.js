// DatabaseDraft rules from API-CONTRACT v1. Only structured fields exist: no JDBC strings or paths.
export const DATABASE_CLASSIFICATIONS = {
  EMPTY: {
    title: '空数据库，可以初始化',
    detail: '将创建 EduLoop 所需的数据结构和首个管理员。',
    tone: 'positive',
  },
  KNOWN_EXISTING: {
    title: '检测到已有的 EduLoop 数据库',
    detail: '不会创建新管理员。需要用现有管理员账号认领，账户、课程和资料都会保留。',
    tone: 'accent',
  },
  UNKNOWN_NONEMPTY: {
    title: '数据库里有无法识别的数据',
    detail: '为避免破坏现有数据，不能在这里安装。请确认连接目标，或换用一个空数据库。',
    tone: 'critical',
  },
  INCOMPATIBLE: {
    title: '数据库版本不兼容',
    detail: '当前版本无法使用这个数据库。安装没有写入任何内容，请换用兼容的数据库。',
    tone: 'critical',
  },
  MANAGED: {
    title: '该数据库已属于一个 EduLoop 实例',
    detail: '不能在这里重复安装。已安装实例的问题请通过恢复流程处理，或换用一个空数据库。',
    tone: 'critical',
  },
}

export const UNRECOGNIZED_DATABASE = {
  title: '无法识别检查结果',
  detail: '服务返回了当前页面不认识的数据库状态，因此不能继续。请确认前后端版本一致。',
  tone: 'caution',
}

export const DATABASE_FIELD_LABELS = {
  mode: '数据库位置',
  host: '主机',
  port: '端口',
  database: '数据库名称',
  username: '用户名',
  password: '密码',
  tls: '传输加密',
}

export function createDatabaseDraft() {
  return {
    mode: 'MANAGED',
    host: '',
    port: 3306,
    database: '',
    username: '',
    passwordAction: 'REPLACE',
    passwordValue: '',
    tls: 'REQUIRED',
  }
}

export function databaseDraftFromView(view) {
  return {
    ...createDatabaseDraft(),
    mode: view?.mode || 'MANAGED',
    host: view?.host || '',
    port: view?.port || 3306,
    database: view?.database || '',
    username: view?.username || '',
    passwordAction: view?.passwordAction || 'KEEP',
    tls: view?.tls || 'REQUIRED',
  }
}

export function toDatabasePayload(draft) {
  if (draft.mode === 'MANAGED') return { mode: 'MANAGED' }
  const payload = {
    mode: 'EXTERNAL',
    host: draft.host.trim(),
    port: Number(draft.port),
    database: draft.database.trim(),
    username: draft.username.trim(),
    passwordAction: draft.passwordAction,
    tls: draft.tls,
  }
  if (draft.passwordAction === 'REPLACE') payload.passwordValue = draft.passwordValue
  return payload
}

// Fields the environment owns are not the user's to fill in, so they are never reported.
export function validateDatabaseDraft(draft, view = null) {
  if (draft.mode === 'MANAGED') return {}
  const locked = new Set(view?.lockedFields || [])
  const errors = {}
  const port = Number(draft.port)
  if (!locked.has('host') && !draft.host.trim()) errors.host = '请填写数据库主机'
  if (!locked.has('port') && (!Number.isInteger(port) || port < 1 || port > 65535)) errors.port = '端口需为 1 到 65535 的整数'
  if (!locked.has('database') && !draft.database.trim()) errors.database = '请填写数据库名称'
  if (!locked.has('username') && !draft.username.trim()) errors.username = '请填写用户名'
  if (!locked.has('password') && draft.passwordAction === 'REPLACE' && !draft.passwordValue) errors.passwordValue = '请填写数据库密码'
  return errors
}

export const isExternallyManagedConflict = problem => problem?.errorCode === 'FIELD_EXTERNALLY_MANAGED'

export function clearDatabaseSecrets(draft) {
  draft.passwordValue = ''
}
