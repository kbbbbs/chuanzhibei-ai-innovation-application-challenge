// Applies the saved theme before first paint. Only the preference word is stored.
// Keep DEFAULT_THEME and STORAGE_KEY in sync with src/composables/useTheme.js.
(function () {
  var STORAGE_KEY = 'eduloop-theme'
  var DEFAULT_THEME = 'light'
  var preference = DEFAULT_THEME
  try {
    var stored = window.localStorage.getItem(STORAGE_KEY)
    if (stored === 'light' || stored === 'dark' || stored === 'system') preference = stored
  } catch (error) {
    preference = DEFAULT_THEME
  }
  var dark = preference === 'dark'
    || (preference === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  var root = document.documentElement
  root.classList.toggle('dark', dark)
  root.style.colorScheme = dark ? 'dark' : 'light'
  root.style.backgroundColor = dark ? '#11141a' : '#f5f6f8'
})()
