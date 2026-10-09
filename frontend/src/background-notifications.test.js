import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import App from './App.vue'
import { useUserStore } from './stores/user.js'
import { useMusicStore } from './stores/music.js'
import { useNotificationStore } from './stores/notification.js'
import { request } from './utils/api.js'

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/dashboard' }),
  useRouter: () => ({}),
}))
vi.mock('@/components/BottomPlayer.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/ExternalLinkDialog.vue', () => ({ default: { template: '<div />' } }))

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  vi.spyOn(console, 'error').mockImplementation(() => {})
  vi.spyOn(console, 'log').mockImplementation(() => {})
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  useNotificationStore().hide()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

function failFetch(network = false) {
  const fetch = vi.fn()
  if (network) fetch.mockRejectedValue(new TypeError('Failed to fetch'))
  else fetch.mockResolvedValue({
    ok: false, status: 404,
    json: async () => ({ errorDetails: { code: 'NOT_FOUND', message: 'No static resource' } }),
  })
  vi.stubGlobal('fetch', fetch)
  return fetch
}

it.each([false, true])('preserves the rendered login confirmation when playback restoration fails (network=%s)', async network => {
  const fetch = failFetch(network)
  wrapper = mount(App, { global: { stubs: { RouterView: true } } })
  // Same order as Login.vue: update the session, then announce login success.
  useUserStore().login({ username: 'listener', onboardingStep: 'DONE' }, 'token')
  useNotificationStore().success('You are logged in.')
  await flushPromises()
  expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/get-last-playback'), expect.any(Object))
  expect(wrapper.get('.notification-message').text()).toBe('You are logged in.')
  expect(useNotificationStore().type).toBe('success')
  expect(console.log).toHaveBeenCalledWith('Error fetching last playback:', expect.any(Error))
})

it.each([false, true])('preserves action feedback when background playback saving fails (network=%s)', async network => {
  const fetch = failFetch(network)
  useNotificationStore().success('Your details are saved.')
  await useMusicStore().saveLastPlayback('listener')
  expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/update-last-playback'), expect.objectContaining({ method: 'POST' }))
  expect(useNotificationStore().message).toBe('Your details are saved.')
  expect(useNotificationStore().type).toBe('success')
  expect(console.error).toHaveBeenCalledWith('Failed to save last playback:', expect.any(Error))
})

it.each([false, true])('still announces and rejects ordinary action failures (network=%s)', async network => {
  failFetch(network)
  useNotificationStore().success('Earlier success')
  await expect(request('PUT', '/api/users/me/profile-details', {})).rejects.toThrow()
  expect(useNotificationStore().type).toBe('error')
  expect(useNotificationStore().message).toBe(network ? "Couldn't reach the server" : 'No static resource')
})
