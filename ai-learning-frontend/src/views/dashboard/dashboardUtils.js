import request from '@/utils/request'
import { getRecords } from '@/utils/apiHelpers'

export function exactTotal(payload) {
  const raw = payload?.total ?? payload?.totalElements
  const total = Number(raw)
  return Number.isFinite(total) && total >= 0 ? total : null
}

export function countText(value) {
  if (value === null || value === undefined || value === '') return '—'
  return Number.isFinite(Number(value)) ? Number(value).toLocaleString('zh-CN') : '—'
}

export function formatDateTime(value) {
  if (!value) return '时间待定'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间待定'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function todayText(prefix) {
  const date = new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(new Date())
  return prefix ? `${prefix} · ${date}` : date
}

export async function safeResponse(path, params = {}) {
  try {
    const response = await request.get(path, { params, silentError: true })
    return response?.code === '200' ? response : null
  } catch {
    return null
  }
}

export async function loadExactPage(path, params = {}) {
  const response = await safeResponse(path, params)
  if (!response) return { records: null, total: null }
  return {
    records: getRecords(response.data),
    total: exactTotal(response.data),
  }
}

export async function loadRecords(path, params = {}) {
  const response = await safeResponse(path, params)
  return response ? getRecords(response.data) : null
}
