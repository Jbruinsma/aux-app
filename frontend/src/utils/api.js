import { useUserStore } from '@/stores/user.js'
import { useNotificationStore } from '@/stores/notification'

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

export async function request(method, url, body, { ignoredErrorCodes = [], notifyOnError = true } = {}) {
  const options = {
    method,
    headers: authHeaders(),
  }

  if (body instanceof FormData) {
    options.body = body
  } else if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }

  let response

  try {
    response = await fetch(url, options)
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

    // INVALID_TOKEN is the only 401 that means the session ended (a wrong login is INVALID_CREDENTIALS)
    if (error.code === 'INVALID_TOKEN') {
      error.sessionEnded = true
      await endSession()
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
