// Single source of the browser-to-backend address. The default is the same-origin
// /backend prefix, which nginx and the Vite dev proxy strip exactly once.
const env = import.meta.env || {}
export const API_BASE_URL = String(env.VITE_API_BASE_URL || '/backend').replace(/\/+$/, '')

const HAS_SCHEME_OR_HOST = /^([a-z][a-z\d+.-]*:|\/\/)/i
const SCRIPT_SCHEME = /^\s*(javascript|vbscript):/i
const DEFAULT_PREFIX = /^\/backend(?=\/|$)/
const BACKEND_RESOURCE_ROUTE = /^\/(uploads|media|files|api)(\/|$)/

// Stored rows can still hold the development backend's address. Only these origins are
// rewritten, and only on backend routes, so public videos, papers and images keep theirs.
const KNOWN_BACKEND_ORIGINS = new Set(['http://localhost:8080', 'http://127.0.0.1:8080'])
if (/^https?:\/\//i.test(API_BASE_URL)) KNOWN_BACKEND_ORIGINS.add(new URL(API_BASE_URL).origin)

// Adds the prefix to a backend path once; already-prefixed and absolute URLs are returned as is.
export function backendUrl(path = '') {
  const value = String(path)
  if (/^https?:\/\//i.test(value)) return value
  const rooted = value.startsWith('/') ? value : `/${value}`
  if (rooted === API_BASE_URL || rooted.startsWith(`${API_BASE_URL}/`)) return rooted
  return `${API_BASE_URL}${rooted.replace(DEFAULT_PREFIX, '')}`
}

function legacyBackendPath(value) {
  if (!/^(https?:)?\/\//i.test(value)) return ''
  let url
  try {
    url = new URL(value, 'http://localhost')
  } catch {
    return ''
  }
  if (!KNOWN_BACKEND_ORIGINS.has(url.origin)) return ''
  const path = url.pathname.replace(DEFAULT_PREFIX, '')
  return BACKEND_RESOURCE_ROUTE.test(path) ? `${path}${url.search}${url.hash}` : ''
}

// Rooted paths are backend resources (/uploads, /media, ...). External video, paper and
// cover links, as well as data: and blob: URLs, keep their original address.
export function resolveResourceUrl(url) {
  if (!url) return ''
  const value = String(url).trim()
  if (SCRIPT_SCHEME.test(value)) return ''
  const legacyPath = legacyBackendPath(value)
  if (legacyPath) return backendUrl(legacyPath)
  if (HAS_SCHEME_OR_HOST.test(value) || !value.startsWith('/')) return value
  return backendUrl(value)
}
