import { watch } from 'vue'
import { useUserStore } from '@/stores/user.js'
import { adoptStoredTokens, endSession, refreshSession } from '@/utils/api.js'

// How long before the token expires to refresh it. Background tabs can run timers up to a minute late, so leave room
export const REFRESH_AHEAD_MS = 2 * 60_000
// After a refresh that failed for a passing reason (offline, server down), how soon to try again
export const RETRY_MS = 30_000

// Keeps the session alive for as long as the app is open, so a long listening session never gets logged out.
// Refreshes a little before the token expires, and right away when the tab comes back after a sleep or a lost
// connection. Requests that still meet an expired token refresh on their own (see request in api.js).
// Returns a function that stops it
export function keepSessionAlive() {
  const userStore = useUserStore()
  let timer = null

  async function refreshNow() {
    clearTimeout(timer)
    timer = null
    const outcome = await refreshSession()
    if (outcome === 'ended') await endSession()
    // 'ok' moves expiresAt, and the watch below schedules the next one
    else if (outcome === 'failed') timer = setTimeout(refreshNow, RETRY_MS)
  }

  function schedule() {
    clearTimeout(timer)
    timer = null
    if (!userStore.loggedIn || !userStore.refreshToken) return
    const delay = userStore.expiresAt - REFRESH_AHEAD_MS - Date.now()
    if (delay <= 0) refreshNow()
    else timer = setTimeout(refreshNow, delay)
  }

  // Timers stall while a laptop sleeps: check again when the app is looked at or reconnects
  function onWake() {
    if (document.visibilityState === 'visible') schedule()
  }

  // Another tab refreshed: take its tokens so this tab doesn't spend the old refresh token
  function onStorage(event) {
    if (event.key === 'user') adoptStoredTokens()
  }

  const stopWatch = watch(() => [userStore.loggedIn, userStore.refreshToken, userStore.expiresAt], schedule, { immediate: true })
  document.addEventListener('visibilitychange', onWake)
  window.addEventListener('online', schedule)
  window.addEventListener('storage', onStorage)

  return () => {
    stopWatch()
    clearTimeout(timer)
    document.removeEventListener('visibilitychange', onWake)
    window.removeEventListener('online', schedule)
    window.removeEventListener('storage', onStorage)
  }
}
