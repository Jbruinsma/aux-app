import { ref } from 'vue'

// That choice lives in this browser only; there's no account setting for it yet.
const SKIP_KEY = 'aux-skip-external-warning'

function readSkip() {
  try {
    return localStorage.getItem(SKIP_KEY) === '1'
  } catch {
    return false
  }
}

export const skipExternalWarning = ref(readSkip())

export function setSkipExternalWarning(skip) {
  skipExternalWarning.value = skip
  try {
    if (skip) localStorage.setItem(SKIP_KEY, '1')
    else localStorage.removeItem(SKIP_KEY)
  } catch {
    // Storage can be blocked; the choice still holds until the page reloads
  }
}

export const pendingExternalLink = ref(null)

export function safeExternalUrl(raw) {
  try {
    const url = new URL(String(raw).trim())
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null
  } catch {
    return null
  }
}

export function openExternal(url) {
  window.open(url, '_blank', 'noopener,noreferrer')
}
