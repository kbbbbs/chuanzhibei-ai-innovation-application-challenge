export function enumName(value) {
  if (!value) return ''
  if (typeof value === 'string') return value
  if (typeof value.name === 'string') return value.name
  return String(value)
}

export function getRecords(payload) {
  if (!payload) return []
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload.records)) return payload.records
  if (Array.isArray(payload.content)) return payload.content
  if (Array.isArray(payload.data)) return payload.data
  return []
}

export function getTotal(payload, fallback = 0) {
  if (!payload) return fallback
  if (typeof payload.total === 'number') return payload.total
  if (typeof payload.totalElements === 'number') return payload.totalElements
  const records = getRecords(payload)
  return records.length || fallback
}

export function paginateRecords(records, pageNum = 1, pageSize = 10) {
  const list = Array.isArray(records) ? records : []
  const current = Number(pageNum) > 0 ? Number(pageNum) : 1
  const size = Number(pageSize) > 0 ? Number(pageSize) : 10
  const start = (current - 1) * size
  return list.slice(start, start + size)
}

export function scopedTeacherParams(userStore, params = {}) {
  const nextParams = { ...params }
  if (userStore?.roleCode !== 'admin' && userStore?.userId) {
    nextParams.teacherId = userStore.userId
  }
  return nextParams
}

export function currentUserId(userStore) {
  const id = Number(userStore?.userId)
  return Number.isFinite(id) && id > 0 ? id : null
}

export function formatLocalDateTime(value) {
  if (!value) return null
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return null

  const pad = (num) => String(num).padStart(2, '0')
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join('-') + 'T' + [
    pad(date.getHours()),
    pad(date.getMinutes()),
    pad(date.getSeconds()),
  ].join(':')
}

export function normalizeStatus(value, map, fallback = 0) {
  const name = enumName(value)
  if (Object.prototype.hasOwnProperty.call(map, name)) {
    return map[name]
  }
  if (typeof value === 'number') return value
  return fallback
}
