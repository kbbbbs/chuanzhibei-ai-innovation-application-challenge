import { computed, ref } from 'vue'

// Keep both constants in sync with public/theme-boot.js.
const STORAGE_KEY = 'eduloop-theme'
const DEFAULT_THEME = 'light'
const PREFERENCES = ['light', 'dark', 'system']

function readPreference() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return PREFERENCES.includes(stored) ? stored : DEFAULT_THEME
  } catch {
    return DEFAULT_THEME
  }
}

const preference = ref(readPreference())
const systemDark = ref(false)
const resolvedTheme = computed(() => {
  if (preference.value === 'system') return systemDark.value ? 'dark' : 'light'
  return preference.value
})

function applyTheme() {
  const dark = resolvedTheme.value === 'dark'
  const root = document.documentElement
  root.classList.toggle('dark', dark)
  root.style.colorScheme = dark ? 'dark' : 'light'
  // theme-boot.js paints an inline background; from here on the tokens own it.
  root.style.backgroundColor = ''
}

function setThemePreference(value) {
  if (!PREFERENCES.includes(value)) return
  preference.value = value
  try {
    localStorage.setItem(STORAGE_KEY, value)
  } catch {
    // Storage may be unavailable; the choice still applies to this page.
  }
  applyTheme()
}

export function initTheme() {
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  systemDark.value = media.matches
  media.addEventListener('change', event => {
    systemDark.value = event.matches
    applyTheme()
  })
  window.addEventListener('storage', event => {
    if (event.key !== STORAGE_KEY) return
    preference.value = readPreference()
    applyTheme()
  })
  applyTheme()
}

export function useTheme() {
  return { preference, resolvedTheme, setThemePreference }
}
