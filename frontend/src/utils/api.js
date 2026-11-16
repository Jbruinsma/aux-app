import { useUserStore } from '@/stores/user.js'
import { useNotificationStore } from '@/stores/notification'
import { API_BASE_URL } from '@/utils/variables.js'

// Java backend errors look like:
// { errorDetails: { code, message, parameter } }
//
// `code` is still available so callers can branch on specific errors.
export class ApiError extends Error {
  constructor(status, details = {}) {
    super(details.message ?? `HTTP error! status: ${status}`)
    this.status = status
    this.code = details.code ?? null
    this.parameter = details.parameter ?? null
  }
}

// Sends the logged-in user's session token
// so the Java backend knows who is asking.
export function authHeaders() {
  const token = useUserStore().token

  return token ? { Authorization: `Bearer ${token}` } : {}
}

// The key pinia-plugin-persistedstate saves the user store under (its store id)
const STORED_USER_KEY = 'user'

// Copies another tab's tokens into this one when they're newer. Every tab shares one session: once a refresh token has
// been traded, the old one is spent, so a tab that kept it would end the session for everyone on its next refresh
export function adoptStoredTokens() {
  const userStore = useUserStore()
  let stored
  try {
    stored = JSON.parse(localStorage.getItem(STORED_USER_KEY))
  } catch {
    return false
  }
  if (!userStore.loggedIn || !stored?.loggedIn || stored.userData?.userId !== userStore.userData.userId) return false
  if (!stored.refreshToken || !(stored.expiresAt > userStore.expiresAt)) return false
  userStore.token = stored.token
  userStore.refreshToken = stored.refreshToken
  userStore.expiresAt = stored.expiresAt
  return true
}

let refreshing = null

// Trades the refresh token for a new pair. Resolves to 'ok' (new tokens are in the store), 'ended' (the session is over:
// log in again) or 'failed' (offline, rate limited or a server error; the session may still be fine, try again later).
// Calls made while one is running share it
export function refreshSession() {
  refreshing ??= withRefreshLock(refreshOnce).finally(() => {
    refreshing = null
  })
  return refreshing
}

// One refresh at a time across every tab of the app (Web Locks), so two tabs never spend the same refresh token
function withRefreshLock(task) {
  const tokenBefore = useUserStore().token
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined
  return locks?.request ? locks.request('aux-session-refresh', () => task(tokenBefore)) : task(tokenBefore)
}

async function refreshOnce(tokenBefore) {
  const userStore = useUserStore()
  // Another tab refreshed while this one waited for the lock: use its tokens instead
  if (adoptStoredTokens() || (userStore.token && userStore.token !== tokenBefore)) return 'ok'
  const refreshToken = userStore.refreshToken
  if (!userStore.loggedIn || !refreshToken) return 'ended'

  let response
  try {
    response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
  } catch (err) {
    console.warn("Couldn't refresh the session:", err)
    return 'failed'
  }
  if (response.status === 401) return 'ended'
  if (!response.ok) {
    console.warn(`Refreshing the session failed with ${response.status}`)
    return 'failed'
  }
  const session = await response.json()
  // Logged out (or in as someone else) while the request was out
  if (!userStore.loggedIn || userStore.refreshToken !== refreshToken) return 'failed'
  userStore.setTokens(session)
  userStore.updateUser(session.user)
  return 'ok'
}

let endingSession = false

// Logs out and sends the user to log in, then back to this page. Several requests can fail at once; only the first acts
export async function endSession() {
  if (endingSession) return
  endingSession = true
  try {
    const { default: router } = await import('@/router/index.js')
    const userStore = useUserStore()
    if (!userStore.loggedIn) return
    userStore.logout()
    useNotificationStore().neutral('Your session ended. Log in again to continue.')
    const { fullPath } = router.currentRoute.value
    await router.replace({ name: 'Login', query: fullPath === '/' ? {} : { redirect: fullPath } })
  } finally {
    endingSession = false
  }
}

export async function request(method, url, body, options = {}) {
  const { ignoredErrorCodes = [], notifyOnError = true, retried = false } = options

  const init = {
    method,
    headers: authHeaders(),
  }

  if (body instanceof FormData) {
    init.body = body
  } else if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }

  let response

  try {
    response = await fetch(url, init)
  } catch (err) {
    console.error(`Network error on ${method} ${url}:`, err)

    const error = new ApiError(0, {
      code: 'NETWORK_ERROR',
      message: "Couldn't reach the server",
    })

    if (notifyOnError) useNotificationStore().error(error.message)

    throw error
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null)

    const error = new ApiError(response.status, errorBody?.errorDetails)

    console.error(`${method} ${url} failed:`, error.code, error.message)

    // INVALID_TOKEN is the only 401 that means the token is no good (a wrong login is INVALID_CREDENTIALS). Usually it
    // just expired: refresh and send the request again, once. Only a refresh the server refuses ends the session
    if (error.code === 'INVALID_TOKEN') {
      const outcome = retried ? 'ended' : await refreshSession()
      if (outcome === 'ok') return request(method, url, body, { ...options, retried: true })
      if (outcome === 'ended') {
        error.sessionEnded = true
        await endSession()
        throw error
      }
      if (notifyOnError) useNotificationStore().error("Couldn't reach the server")
      throw error
    }

    // Callers can handle an expected API result without flashing a false error.
    if (notifyOnError && !ignoredErrorCodes.includes(error.code)) {
      useNotificationStore().error(error.message)
    }

    throw error
  }

  return response.status === 204 ? null : response.json()
}

export const fetchAPI = (url, options) => request('GET', url, undefined, options)

export const postToAPI = (url, data) => request('POST', url, data)
