import { ApiError, fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

// Only check-username exists on the backend. The rest are requested in BACKEND_REQUESTS.md and answer from mock
// data until then; flip this off once they ship, since the real calls below already use the requested shapes
export const ACCOUNT_MOCKED = true

const mock = { email: 'you@example.com' }
const MOCK_TAKEN_EMAIL = 'taken@example.com'
const MOCK_WRONG_PASSWORD = 'wrongpassword'
const wait = () => new Promise((resolve) => setTimeout(resolve, 400))

export const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,16}$/
// Same limits as registration
export const EMAIL_MIN = 3
export const EMAIL_MAX = 254
export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 32

// { exists }; true when taken ignoring case, or reserved
export function checkUsername(username) {
  return fetchAPI(`${API_BASE_URL}/api/users/check-username/${encodeURIComponent(username)}`)
}

// { email } of the logged-in user
export async function fetchAccount() {
  if (!ACCOUNT_MOCKED) return fetchAPI(`${API_BASE_URL}/api/users/me/account`)
  await wait()
  return { email: mock.email }
}

// { exists }; true when another account has this email, ignoring case
export async function checkEmail(email) {
  if (!ACCOUNT_MOCKED) return fetchAPI(`${API_BASE_URL}/api/users/me/check-email?${new URLSearchParams({ email })}`)
  await wait()
  return { exists: email.toLowerCase() === MOCK_TAKEN_EMAIL }
}

// Returns the updated UserSummary
export async function changeUsername(username) {
  if (!ACCOUNT_MOCKED) return request('PUT', `${API_BASE_URL}/api/users/me/username`, { username })
  const { exists } = await checkUsername(username)
  if (exists) throw new ApiError(409, { code: 'USERNAME_TAKEN', message: 'Username already exists', parameter: 'username' })
  return { username }
}

// Returns { email }
export async function changeEmail(email) {
  if (!ACCOUNT_MOCKED) return request('PUT', `${API_BASE_URL}/api/users/me/email`, { email })
  await wait()
  if (email.toLowerCase() === MOCK_TAKEN_EMAIL) {
    throw new ApiError(409, { code: 'EMAIL_TAKEN', message: 'Email already registered', parameter: 'email' })
  }
  mock.email = email
  return { email }
}

// Returns nothing (204). The session token stops working once the account is gone
export async function deleteAccount() {
  if (!ACCOUNT_MOCKED) return request('DELETE', `${API_BASE_URL}/api/users/me`)
  await wait()
  return null
}

// Returns nothing (204)
export async function changePassword(currentPassword, newPassword) {
  if (!ACCOUNT_MOCKED) return request('PUT', `${API_BASE_URL}/api/users/me/password`, { currentPassword, newPassword })
  await wait()
  if (currentPassword === MOCK_WRONG_PASSWORD) {
    throw new ApiError(403, { code: 'WRONG_PASSWORD', message: 'Current password is wrong', parameter: 'currentPassword' })
  }
  return null
}
