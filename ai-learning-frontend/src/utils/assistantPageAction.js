const prefillKey = (userId, requestId) => `assistant:course-prefill:${userId}:${requestId}`
const positiveId = value => Number.isSafeInteger(Number(value)) && Number(value) > 0 ? Number(value) : null

export function assistantPageDestination(action, user, requestId) {
  if (!action || !user.isLoggedIn) return null
  if (action.type === 'learning-analysis' && (user.isTeacher || user.isAdmin)) {
    const courseId = positiveId(action.courseId)
    return courseId ? { path: '/grade-analysis', query: { courseId } } : null
  }
  if (action.type !== 'course-create' || !user.isTeacher || !requestId) return null
  const input = action.fields || {}
  const fields = {}
  for (const key of ['title', 'description', 'coverUrl', 'categoryName']) {
    if (typeof input[key] === 'string') fields[key] = input[key]
  }
  if (positiveId(input.categoryId)) fields.categoryId = positiveId(input.categoryId)
  if (['beginner', 'intermediate', 'advanced'].includes(input.difficulty)) fields.difficulty = input.difficulty
  saveAssistantCoursePrefill(user.userId, requestId, fields)
  return { path: '/courses/create', query: { assistantDraft: requestId } }
}

export function readAssistantCoursePrefill(userId, requestId) {
  if (!requestId || typeof requestId !== 'string') return null
  const key = prefillKey(userId, requestId)
  const saved = sessionStorage.getItem(key)
  try {
    return saved ? JSON.parse(saved) : null
  } catch {
    return null
  }
}

export function saveAssistantCoursePrefill(userId, requestId, fields) {
  sessionStorage.setItem(prefillKey(userId, requestId), JSON.stringify(fields))
}

export function clearAssistantCoursePrefill(userId, requestId) {
  if (requestId) sessionStorage.removeItem(prefillKey(userId, requestId))
}
