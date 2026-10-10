import { readonly, ref } from 'vue'

const STORAGE_KEY = 'aux-theme'
const preference = ref('system')
let stopListening

function normalize(value) {
  return value === 'light' || value === 'dark' ? value : 'system'
}

function readPreference() {
  try { return normalize(localStorage.getItem(STORAGE_KEY)) } catch { return 'system' }
}

function applyTheme() {
  const dark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
  const theme = preference.value === 'system' ? (dark ? 'dark' : 'light') : preference.value
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
}

export function initializeTheme() {
  stopListening?.()
  preference.value = readPreference()
  applyTheme()
  const media = window.matchMedia?.('(prefers-color-scheme: dark)')
  const onSystemChange = () => { if (preference.value === 'system') applyTheme() }
  const onStorage = (event) => {
    if (event.key !== STORAGE_KEY && event.key !== null) return
    preference.value = readPreference()
    applyTheme()
  }
  media?.addEventListener('change', onSystemChange)
  window.addEventListener('storage', onStorage)
  stopListening = () => {
    media?.removeEventListener('change', onSystemChange)
    window.removeEventListener('storage', onStorage)
  }
  return stopListening
}

export function setThemePreference(value) {
  preference.value = normalize(value)
  try {
    if (preference.value === 'system') localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, preference.value)
  } catch { /* The current tab still works when storage is unavailable. */ }
  applyTheme()
}

export const themePreference = readonly(preference)
