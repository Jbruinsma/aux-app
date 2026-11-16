import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user.js'
import { keepSessionAlive, REFRESH_AHEAD_MS, RETRY_MS } from './session.js'

const router = vi.hoisted(() => ({ currentRoute: { value: { fullPath: '/music' } }, replace: vi.fn() }))
vi.mock('@/router/index.js', () => ({ default: router }))

const NOW = Date.parse('2026-11-16T12:00:00Z')
const HOUR = 60 * 60_000
const session = (n) => ({
  user: { userId: 'u1', username: 'me', onboardingStep: 'DONE' },
  token: `token-${n}`,
  expiresAt: new Date(NOW + n * HOUR).toISOString(),
  refreshToken: `refresh-${n}`,
})
const refreshCalls = (fetch) => fetch.mock.calls.filter(([url]) => url.endsWith('/api/auth/refresh'))

let stop
beforeEach(() => {
  vi.useFakeTimers({ now: NOW })
  setActivePinia(createPinia())
  router.replace.mockReset()
  vi.spyOn(console, 'warn').mockImplementation(() => {})
  useUserStore().startSession(session(1))
})
afterEach(() => {
  stop?.()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

it('refreshes shortly before the token expires, and again before the next one does', async () => {
  let n = 1
  const fetch = vi.fn(async () => ({ ok: true, status: 200, json: async () => session(++n) }))
  vi.stubGlobal('fetch', fetch)
  stop = keepSessionAlive()

  await vi.advanceTimersByTimeAsync(HOUR - REFRESH_AHEAD_MS - 1)
  expect(refreshCalls(fetch)).toHaveLength(0)
  await vi.advanceTimersByTimeAsync(1)
  expect(refreshCalls(fetch)).toHaveLength(1)
  expect(useUserStore().token).toBe('token-2')

  await vi.advanceTimersByTimeAsync(HOUR)
  expect(refreshCalls(fetch)).toHaveLength(2)
  expect(useUserStore().token).toBe('token-3')
  expect(useUserStore().loggedIn).toBe(true)
})

it('keeps trying while offline instead of logging out', async () => {
  const fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
  vi.stubGlobal('fetch', fetch)
  stop = keepSessionAlive()

  await vi.advanceTimersByTimeAsync(HOUR - REFRESH_AHEAD_MS)
  await vi.advanceTimersByTimeAsync(RETRY_MS)
  expect(refreshCalls(fetch)).toHaveLength(2)
  expect(useUserStore().loggedIn).toBe(true)
})

it('logs out when the server refuses the refresh token', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({}) }))
  stop = keepSessionAlive()

  await vi.advanceTimersByTimeAsync(HOUR - REFRESH_AHEAD_MS)
  expect(useUserStore().loggedIn).toBe(false)
  expect(router.replace).toHaveBeenCalledWith({ name: 'Login', query: { redirect: '/music' } })
})
