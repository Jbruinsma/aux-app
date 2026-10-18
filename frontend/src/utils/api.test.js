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
