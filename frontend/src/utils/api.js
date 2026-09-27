import { useUserStore } from '@/stores/user.js'

// Java backend errors look like {errorDetails: {code, message, parameter}}; `code` is what callers should branch on
export class ApiError extends Error {
  constructor(status, details = {}) {
    super(details.message ?? `HTTP error! status: ${status}`)
    this.status = status
    this.code = details.code ?? null
    this.parameter = details.parameter ?? null
  }
}

// Sends the logged-in user's session token so the Java backend knows who is asking
function authHeaders() {
  const token = useUserStore().token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

// FormData goes as multipart (the browser sets the boundary), anything else as JSON
export async function request(method, url, body) {
  const options = { method, headers: authHeaders() }
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
    throw new ApiError(0, { code: 'NETWORK_ERROR', message: "Couldn't reach the server" })
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null)
    const error = new ApiError(response.status, errorBody?.errorDetails)
    console.error(`${method} ${url} failed:`, error.code, error.message)
    throw error
  }

  return response.status === 204 ? null : response.json()
}

export const fetchAPI = (url) => request('GET', url)
export const postToAPI = (url, data) => request('POST', url, data)
