const SESSION_KEY = 'eduloop:user-session'
const EMPTY_USER = {
  token: '', userId: '', username: '', nickname: '', email: '', phone: '', avatar: '', roleCode: '',
}

function snapshot(user) {
  return Object.fromEntries(Object.keys(EMPTY_USER).map(key => [key, user[key] || '']))
}

export function loadUserSession() {
  const saved = sessionStorage.getItem(SESSION_KEY)
  if (saved !== null) {
    try {
      return snapshot(JSON.parse(saved) || {})
    } catch {
      return { ...EMPTY_USER }
    }
  }
  // Migrate the remembered login once. Existing tabs then keep their own identity.
  const user = Object.fromEntries(Object.keys(EMPTY_USER).map(key => [key, localStorage.getItem(key) || '']))
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(user))
  return user
}

export function saveUserSession(user, remember = false) {
  const saved = snapshot(user)
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(saved))
  if (remember || localStorage.getItem('token') === saved.token) {
    for (const [key, value] of Object.entries(saved)) localStorage.setItem(key, value)
  }
}

export function clearUserSession(token) {
  // Keep an empty snapshot so refresh cannot inherit another tab's login.
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(EMPTY_USER))
  if (localStorage.getItem('token') === token) {
    for (const key of Object.keys(EMPTY_USER)) localStorage.removeItem(key)
  }
}
