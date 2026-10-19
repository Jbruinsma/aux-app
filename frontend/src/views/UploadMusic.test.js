import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { fetchAPI, request } from '@/utils/api.js'
import UploadMusic from './UploadMusic.vue'

const { route, push } = vi.hoisted(() => ({ route: { query: {} }, push: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push, replace: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push, replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))
vi.mock('@/utils/photo.js', () => ({ PHOTO_ERRORS: { IMAGE_TOO_SMALL: 'Too small.' }, photoProblem: async () => null }))
vi.mock('@/utils/music.js', async (original) => ({ ...(await original()), mp3Problem: async () => ({ problem: null, seconds: 185 }) }))

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  route.query = {}
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

async function fillIn() {
  wrapper = mount(UploadMusic, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: true } } })
  const pick = async (selector, file) => {
    Object.defineProperty(wrapper.get(selector).element, 'files', { value: [file], configurable: true })
    await wrapper.get(selector).trigger('change')
    await flushPromises()
  }
  await pick('#mp3-input', new File(['a'], 'Late Night.mp3', { type: 'audio/mpeg' }))
  await pick('#cover-input', new File(['c'], 'c.png', { type: 'image/png' }))
  expect(wrapper.get('#song-title').element.value).toBe('Late Night')
  expect(wrapper.text()).toContain('Late Night.mp3 · 3:05')
  fetchAPI.mockResolvedValue([])
  await wrapper.get('input[type="search"]').setValue('New Band')
  await vi.advanceTimersByTimeAsync(300)
  await flushPromises()
  expect(wrapper.text()).toContain('Create artist “New Band”')
}

it('creates the typed artist, uploads the song with every field, then goes to the dashboard', async () => {
  request
    .mockResolvedValueOnce({ artistId: 'a1', artistName: 'New Band', artistPfpUrl: null })
    .mockResolvedValueOnce({ musicPieceId: 'm1', name: 'Late Night' })
  await fillIn()
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(request).toHaveBeenNthCalledWith(1, 'POST', expect.stringContaining('/api/artists'), { artistName: 'New Band' }, expect.any(Object))
  const [method, url, form] = request.mock.calls[1]
  expect([method, url]).toEqual(['POST', expect.stringContaining('/api/music-pieces/create')])
  expect(Object.fromEntries([...form.keys()].map((key) => [key, typeof form.get(key) === 'string' ? form.get(key) : 'file'])))
    .toEqual({ name: 'Late Night', artistId: 'a1', isPublic: 'false', mp3File: 'file', coverImage: 'file' })
  expect(useNotificationStore().message).toBe('Uploaded “Late Night”. It\'s in My Uploads on your dashboard.')
  expect(push).toHaveBeenCalledWith({ name: 'Dashboard' })
})

it('adds the new song to the playlist it came from, and explains a rejected upload', async () => {
  route.query = { playlist: 'p1', owner: 'mo' }
  request
    .mockResolvedValueOnce({ artistId: 'a1', artistName: 'New Band' })
    .mockRejectedValueOnce({ code: 'STORAGE_QUOTA_EXCEEDED' })
  await fillIn()
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(wrapper.text()).toContain('This upload would go over your 2GB of storage. Delete some uploads first.')

  request.mockResolvedValueOnce({ musicPieceId: 'm1', name: 'Late Night' }).mockResolvedValueOnce({ addedCount: 1, failedCount: 0 })
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(request).toHaveBeenLastCalledWith('POST', expect.stringContaining('/api/playlists/p1/pieces'), { musicPieceIds: ['m1'] }, expect.any(Object))
  expect(useNotificationStore().message).toBe('Uploaded “Late Night”. Added 1 song.')
  expect(push).toHaveBeenCalledWith({ name: 'Playlist', params: { username: 'mo', id: 'p1' } })
})
