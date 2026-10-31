const labels = { HYBRID: '关键词与语义联合检索', KEYWORD: '关键词检索', NONE: '本次未执行检索' }
const reasons = {
  EMPTY_INDEX: '暂无可用索引，请先完成资料解析。',
  EMPTY_QUERY_TERMS: '问题缺少可检索的实词，请补充具体概念。',
  EMBEDDING_DISABLED: '当前按关键词查找资料，语义匹配未启用。',
  EMBEDDING_NOT_CONFIGURED: '语义服务配置不完整，本次按关键词检索。',
  NO_COMPATIBLE_VECTORS: '现有索引与当前语义模型不匹配，本次按关键词检索。',
  EMBEDDING_UNAVAILABLE: '语义服务暂不可用，本次按关键词检索。',
  INCOMPATIBLE_VECTORS: '索引向量不可用，本次按关键词检索，请重新建立索引。',
  PARTIAL_VECTOR_COVERAGE: '部分资料暂无可用语义向量，已结合关键词查找。',
}

// An explicit server result always wins. Unknown explicit modes must not claim hybrid capability.
export function actualRetrievalMode(actualMode, legacyMode) {
  if (actualMode != null && actualMode !== '') return Object.hasOwn(labels, actualMode) ? actualMode : 'UNKNOWN'
  return { hybrid: 'HYBRID', bm25: 'KEYWORD', none: 'NONE' }[legacyMode] || ''
}
export function retrievalModeLabel(actualMode, legacyMode) {
  const mode = actualRetrievalMode(actualMode, legacyMode)
  return mode === 'UNKNOWN' ? '本次检索方式待确认' : labels[mode] || ''
}
export function retrievalWarning(reason, warning) {
  if (/^当前使用\s*BM25\s*关键词检索；未启用语义向量检索。$/.test(warning || '')) return '当前按关键词查找资料，语义匹配暂不可用。'
  if (warning) return warning
  return reasons[reason] || (reason ? '本次检索存在限制，请核对资料索引后重试。' : '')
}
const positive = value => Number.isInteger(Number(value)) && Number(value) > 0 ? Number(value) : null
export function sourceVersionLabel(source) {
  const version = positive(source?.documentVersion ?? source?.document_version)
  const revision = positive(source?.parseRevision ?? source?.parse_revision)
  return [version ? `资料版本 ${version}` : '', revision ? `解析修订 ${revision}` : ''].filter(Boolean).join(' · ')
}
