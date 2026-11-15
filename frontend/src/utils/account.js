import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

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

// { email, username, profilePictureUrl, bannerUrl, profileDetails } of the logged-in user
export function fetchSettings() {
  return fetchAPI(`${API_BASE_URL}/api/users/settings`)
}

// Returns the updated UserSummary
export function changeUsername(username) {
  return request('PUT', `${API_BASE_URL}/api/users/me/username`, { username })
}

// Returns nothing (204). Needs the current password: 403 WRONG_PASSWORD (not a 401, so the session stays),
// 429 RATE_LIMITED. The session token stops working once the account is gone
export function deleteAccount(currentPassword) {
  return request('DELETE', `${API_BASE_URL}/api/users/me`, { currentPassword }, { notifyOnError: false })
}

// Email and password changes are two steps: the request call emails a 6-digit code, the confirm call spends it.
// Errors: 403 WRONG_PASSWORD, 409 EMAIL_TAKEN, 400 INVALID_OTP (wrong, expired or used code), 429 OTP_COOLDOWN

// Returns nothing (204); the code goes to the new address
export function requestEmailChange(newEmail, currentPassword) {
  return request('POST', `${API_BASE_URL}/api/auth/email/request`, { newEmail, currentPassword })
}

// Returns the updated UserSummary
export function confirmEmailChange(code) {
  return request('POST', `${API_BASE_URL}/api/auth/email/confirm`, { code })
}

// Returns nothing (204); the code goes to the current address
export function requestPasswordChange(currentPassword) {
  return request('POST', `${API_BASE_URL}/api/auth/password/request`, { currentPassword })
}

// Returns nothing (204)
export function confirmPasswordChange(code, newPassword) {
  return request('POST', `${API_BASE_URL}/api/auth/password/confirm`, { code, newPassword })
}
