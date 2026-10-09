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
function authHeaders() {
  const token = useUserStore().token

  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function request(method, url, body) {
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

    const notification = useNotificationStore()
    notification.error(error.message)

    throw error
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null)

    const error = new ApiError(response.status, errorBody?.errorDetails)

    console.error(`${method} ${url} failed:`, error.code, error.message)

    // GLOBAL ERROR NOTIFICATION
    const notification = useNotificationStore()
    notification.error(error.message)

    throw error
  }

  return response.status === 204 ? null : response.json()
}

export const fetchAPI = (url) => request('GET', url)

export const postToAPI = (url, data) => request('POST', url, data)
