export const AGENT_CONTEXT_MAX_SELECTION_LENGTH = 1500

const CONTEXT_OPEN = '<lesson_selection>'
const CONTEXT_CLOSE = '</lesson_selection>'
const QUESTION_OPEN = '<user_question>'
const QUESTION_CLOSE = '</user_question>'

export function normalizeAgentSelectionText(value) {
  return String(value || '')
    .replace(/\r/g, '')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

export function normalizeAgentSelectionContext(context, maxLength = AGENT_CONTEXT_MAX_SELECTION_LENGTH) {
  const sourceText = normalizeAgentSelectionText(context?.text)
  return {
    text: sourceText.slice(0, maxLength),
    truncated: Boolean(context?.truncated) || sourceText.length > maxLength,
    lessonTitle: String(context?.lessonTitle || '').trim(),
    courseTitle: String(context?.courseTitle || '').trim(),
  }
}

export function buildAgentContextPrompt(question, context) {
  const normalizedQuestion = String(question || '').trim()
  const normalizedContext = normalizeAgentSelectionContext(context)
  if (!normalizedContext.text) return normalizedQuestion

  const metadata = [
    normalizedContext.courseTitle ? `课程：${normalizedContext.courseTitle}` : '',
    normalizedContext.lessonTitle ? `课时：${normalizedContext.lessonTitle}` : '',
  ].filter(Boolean)
  return [
    CONTEXT_OPEN,
    ...metadata,
    '选中原文：',
    normalizedContext.text,
    CONTEXT_CLOSE,
    QUESTION_OPEN,
    normalizedQuestion,
    QUESTION_CLOSE,
  ].join('\n')
}

export function parseAgentContextPrompt(content) {
  const value = String(content || '')
  if (!value.startsWith(CONTEXT_OPEN)) return null
  const contextEnd = value.indexOf(CONTEXT_CLOSE)
  const questionStart = value.indexOf(QUESTION_OPEN)
  const questionEnd = value.lastIndexOf(QUESTION_CLOSE)
  if (contextEnd < 0 || questionStart < 0 || questionEnd < questionStart) return null

  const contextBlock = value.slice(CONTEXT_OPEN.length, contextEnd).trim()
  const selectionMarker = '选中原文：\n'
  const markerIndex = contextBlock.indexOf(selectionMarker)
  const contextText = markerIndex >= 0
    ? contextBlock.slice(markerIndex + selectionMarker.length).trim()
    : contextBlock
  const displayContent = value.slice(questionStart + QUESTION_OPEN.length, questionEnd).trim()
  return { contextText, displayContent }
}
