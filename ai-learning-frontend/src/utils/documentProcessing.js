const stages = { pending: 'QUEUED', queued: 'QUEUED', parsing: 'PARSING', success: 'COMPLETED', ready: 'COMPLETED' }
export const documentIdOf = document => document?.documentId || document?.id || document?.document_id
export const jobIdOf = document => document?.jobId || document?.latest_job_id || document?.job_id
export function processingVersionOf(document, job) {
  const versions = [document?.processing_version, document?.processingVersion, job?.targetVersion,
    job?.documentVersion, job?.document_version, document?.document_version, document?.documentVersion]
  return versions.map(Number).find(version => Number.isInteger(version) && version > 0) || 1
}
export const isPagedDocument = document => /\.(pdf|pptx)$/i.test(document?.file_name || document?.filename || '') ||
  ['application/pdf', 'application/vnd.openxmlformats-officedocument.presentationml.presentation'].includes(document?.mime_type || document?.mimeType)
export function processingStage(document, job) {
  const value = String(job?.stage || job?.status || document?.processing_state || document?.parse_status || document?.parseStatus || 'QUEUED')
  return stages[value.toLowerCase()] || value.toUpperCase()
}
export function processingDocumentState(document, job) {
  const state = String(job?.documentState || document?.document_state || '').toUpperCase()
  if (state) return state
  const phase = processingStage(document, job)
  if (phase === 'PARTIAL') return 'PARTIAL'
  if (phase === 'COMPLETED') return 'READY'
  return phase
}
export function isProcessingActive(document, job) {
  return ['QUEUED', 'PARSING', 'OCR', 'CHUNKING', 'EMBEDDING'].includes(processingStage(document, job))
}
export function pageNumbers(values) {
  return Array.isArray(values) ? [...new Set(values.map(Number).filter(value => Number.isInteger(value) && value > 0))] : []
}
export function processingError(code) {
  if (!code) return ''
  if (String(code).startsWith('FORMULA_')) return '公式结构缺失、不完整或超过处理上限，该页未纳入本次检索索引。请对照原页并强制识别；重试仍失败时需人工核验。'
  switch (String(code)) {
    case 'VISUAL_IMAGE_UNAVAILABLE': return '原页图片未能保存，该位置未纳入检索。请对照原件重新解析。'
    case 'OCR_OUTPUT_TRUNCATED': return '识别结果被截断，该页未纳入本次检索索引。请核对原页后重试。'
    case 'OCR_CONTENT_FILTERED': return '识别服务过滤了该页内容，该页未纳入本次检索索引。请核对原页后重试。'
    case 'OCR_OUTPUT_INCOMPLETE': return '识别服务未完整返回结果，该页未纳入本次检索索引。请核对原页后重试。'
    case 'OCR_EMPTY_RESULT': return '识别结果为空，该页未纳入本次检索索引。请核对原页后重试。'
  }
  if (String(code).includes('NOT_CONFIGURED') || String(code).includes('CONFIGURATION')) return '识别服务未配置，请检查设置后重试。'
  if (String(code).includes('TIMEOUT')) return '处理超时，请稍后重试。'
  if (String(code).includes('ACCESS') || String(code).includes('FORBIDDEN') || String(code).includes('ACTOR')) return '资料访问权限已改变，请重新确认授权。'
  if (String(code).includes('LIMIT') || String(code).includes('TOO_LARGE')) return '资料超过处理限制，请检查文件大小和页数。'
  return '处理未完成，请核对原页并重试。'
}
