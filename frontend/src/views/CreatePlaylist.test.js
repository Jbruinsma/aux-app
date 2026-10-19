import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI, postToAPI, request } from '@/utils/api.js'
import CreatePlaylist from './CreatePlaylist.vue'

const { push } = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: {} }), useRouter: () => ({ push, replace: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push, replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))
vi.mock('@/utils/photo.js', () => ({ PHOTO_ERRORS: {}, photoProblem: async () => null }))
vi.mock('@/utils/motion.js', () => ({ animationsDone: async () => {}, slideFrom: () => {} }))

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  vi.stubGlobal('URL', { ...URL, createObjectURL: () => 'blob:cover', revokeObjectURL: () => {} })
  vi.useFakeTimers()
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  useNotificationStore().hide()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

it('creates the playlist, then sends each invite with its own access', async () => {
  fetchAPI.mockResolvedValue({ results: [{ userId: 'u1', username: 'me' }, { userId: 'u2', username: 'pal' }, { userId: 'u3', username: 'bud' }] })
  postToAPI.mockResolvedValue({ playlistId: 'p9', playlistOwner: { username: 'me' } })
  request.mockResolvedValueOnce({}).mockRejectedValueOnce({ code: 'INTERNAL_ERROR' })
  wrapper = mount(CreatePlaylist, { global: { stubs: { AppHeader: true, AppFooter: true, StepBanner: true, RouterLink: true } } })

  await wrapper.get('#playlist-name').setValue('Road trip')
  await wrapper.get('form').trigger('submit')
  Object.defineProperty(wrapper.get('#cover-input').element, 'files', { value: [new File(['x'], 'c.png', { type: 'image/png' })] })
  await wrapper.get('#cover-input').trigger('change')
  await flushPromises()
  await wrapper.get('form').trigger('submit')
  await flushPromises()

  await wrapper.get('input[type="search"]').setValue('u')
  await vi.advanceTimersByTimeAsync(300)
  await flushPromises()
  expect(wrapper.findAll('.result').map((r) => r.find('.name').text())).toEqual(['pal', 'bud'])
  await wrapper.findAll('.result')[0].trigger('click')
  await wrapper.get('input[type="search"]').setValue('b')
  await vi.advanceTimersByTimeAsync(300)
  await flushPromises()
  await wrapper.findAll('.result')[0].trigger('click')
  await wrapper.get('#invite-u2').setValue('EDITOR')

  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(postToAPI).toHaveBeenCalledWith(expect.stringContaining('/api/playlists'), expect.any(FormData))
  expect(request).toHaveBeenNthCalledWith(1, 'POST', expect.stringContaining('/api/playlists/p9/members'), { userId: 'u2', permission: 'EDITOR' }, { notifyOnError: false })
  expect(request).toHaveBeenNthCalledWith(2, 'POST', expect.stringContaining('/api/playlists/p9/members'), { userId: 'u3', permission: 'LISTENER' }, { notifyOnError: false })
  expect(useNotificationStore().message).toBe("Playlist created, but the invite to bud didn't send. Try again from Manage access.")
  expect(push).toHaveBeenCalledWith({ name: 'Playlist', params: { username: 'me', id: 'p9' } })
})
