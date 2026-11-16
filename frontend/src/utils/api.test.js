import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user.js'
import { useNotificationStore } from '@/stores/notification.js'
import { fetchAPI, postToAPI } from './api.js'

const router = vi.hoisted(() => ({
  currentRoute: { value: { fullPath: '/playlist/mo/p_1' } },
  replace: vi.fn(),
}))
vi.mock('@/router/index.js', () => ({ default: router }))

beforeEach(() => {
  setActivePinia(createPinia())
  router.replace.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
  useUserStore().login({ username: 'mo', onboardingStep: 'DONE' }, 'token')
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

function respondWith(status, code) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: false, status,
    json: async () => ({ errorDetails: { code, message: 'nope' } }),
  }))
}

it('logs out and sends the user to log in, then back, when the token is no longer valid', async () => {
  respondWith(401, 'INVALID_TOKEN')
  await expect(fetchAPI('/api/users/library')).rejects.toMatchObject({ status: 401, sessionEnded: true })
  expect(useUserStore().loggedIn).toBe(false)
  expect(router.replace).toHaveBeenCalledWith({ name: 'Login', query: { redirect: '/playlist/mo/p_1' } })
  expect(useNotificationStore().message).toBe('Your session ended. Log in again to continue.')
})

it('ends the session only once when several requests fail together', async () => {
  respondWith(401, 'INVALID_TOKEN')
  await Promise.allSettled([fetchAPI('/a'), fetchAPI('/b'), fetchAPI('/c')])
  expect(router.replace).toHaveBeenCalledTimes(1)
})

it('keeps the user logged in after a wrong email or password', async () => {
  respondWith(401, 'INVALID_CREDENTIALS')
  const err = await postToAPI('/api/auth/login', {}).catch((e) => e)
  expect(err.sessionEnded).toBeUndefined()
  expect(useUserStore().loggedIn).toBe(true)
  expect(router.replace).not.toHaveBeenCalled()
})

// A session with a refresh token, as login now returns it
function withRefreshToken() {
  useUserStore().startSession({
    user: { userId: 'u1', username: 'mo', onboardingStep: 'DONE' },
    token: 'old-token',
    expiresAt: '2026-11-16T12:00:00Z',
    refreshToken: 'refresh-1',
  })
}
const ok = (body) => ({ ok: true, status: 200, json: async () => body })
const fail = (status, code) => ({ ok: false, status, json: async () => ({ errorDetails: { code, message: 'nope' } }) })
const refreshed = {
  user: { userId: 'u1', username: 'mo', onboardingStep: 'DONE' },
  token: 'new-token',
  expiresAt: '2026-11-16T13:00:00Z',
  refreshToken: 'refresh-2',
}

it('refreshes an expired token and sends the request again', async () => {
  withRefreshToken()
  const fetch = vi.fn()
    .mockResolvedValueOnce(fail(401, 'INVALID_TOKEN'))
    .mockResolvedValueOnce(ok(refreshed))
    .mockResolvedValueOnce(ok({ songs: [] }))
  vi.stubGlobal('fetch', fetch)

  await expect(fetchAPI('/api/users/library')).resolves.toEqual({ songs: [] })

  expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({ refreshToken: 'refresh-1' })
  expect(fetch.mock.calls[2][1].headers).toMatchObject({ Authorization: 'Bearer new-token' })
  const user = useUserStore()
  expect(user.loggedIn).toBe(true)
  expect([user.token, user.refreshToken, user.expiresAt]).toEqual(['new-token', 'refresh-2', Date.parse(refreshed.expiresAt)])
  expect(router.replace).not.toHaveBeenCalled()
})

it('refreshes once for several requests that expire together', async () => {
  withRefreshToken()
  const fetch = vi.fn((url, init) => {
    if (url.endsWith('/api/auth/refresh')) return Promise.resolve(ok(refreshed))
    return Promise.resolve(init.headers.Authorization === 'Bearer new-token' ? ok({}) : fail(401, 'INVALID_TOKEN'))
  })
  vi.stubGlobal('fetch', fetch)

  await Promise.all([fetchAPI('/a'), fetchAPI('/b'), fetchAPI('/c')])

  expect(fetch.mock.calls.filter(([url]) => url.endsWith('/api/auth/refresh'))).toHaveLength(1)
})

it('logs out when the server refuses the refresh token', async () => {
  withRefreshToken()
  vi.stubGlobal('fetch', vi.fn()
    .mockResolvedValueOnce(fail(401, 'INVALID_TOKEN'))
    .mockResolvedValueOnce(fail(401, 'INVALID_REFRESH_TOKEN'))
    .mockResolvedValue(ok(null)))

  await expect(fetchAPI('/api/users/library')).rejects.toMatchObject({ sessionEnded: true })
  expect(useUserStore().loggedIn).toBe(false)
  expect(router.replace).toHaveBeenCalledTimes(1)
})

it('stays logged in when the refresh fails because the server is unreachable', async () => {
  withRefreshToken()
  vi.stubGlobal('fetch', vi.fn()
    .mockResolvedValueOnce(fail(401, 'INVALID_TOKEN'))
    .mockRejectedValueOnce(new TypeError('Failed to fetch')))
  vi.spyOn(console, 'warn').mockImplementation(() => {})

  const err = await fetchAPI('/api/users/library').catch((e) => e)

  expect(err.sessionEnded).toBeUndefined()
  expect(useUserStore().loggedIn).toBe(true)
  expect(useUserStore().refreshToken).toBe('refresh-1')
  expect(router.replace).not.toHaveBeenCalled()
})

it('takes newer tokens another tab saved instead of spending its own refresh token', async () => {
  withRefreshToken()
  localStorage.setItem('user', JSON.stringify({
    loggedIn: true, userData: { userId: 'u1' }, token: 'new-token', refreshToken: 'refresh-2', expiresAt: Date.parse(refreshed.expiresAt),
  }))
  const fetch = vi.fn()
    .mockResolvedValueOnce(fail(401, 'INVALID_TOKEN'))
    .mockResolvedValueOnce(ok({}))
  vi.stubGlobal('fetch', fetch)

  await fetchAPI('/a')

  expect(fetch).toHaveBeenCalledTimes(2)
  expect(fetch.mock.calls[1][1].headers).toMatchObject({ Authorization: 'Bearer new-token' })
  localStorage.removeItem('user')
})

it('ends the session on logout on the server too', () => {
  withRefreshToken()
  const fetch = vi.fn().mockResolvedValue(ok(null))
  vi.stubGlobal('fetch', fetch)

  useUserStore().logout()

  expect(fetch).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({ body: JSON.stringify({ refreshToken: 'refresh-1' }) }))
})
