import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI, request } from '@/utils/api.js'
import UploadMusic from './UploadMusic.vue'
import TagPicker from '@/components/TagPicker.vue'

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

it('sends individual tag fields and keeps server tag errors underneath the picker', async () => {
  await fillIn()
  const picker = wrapper.getComponent(TagPicker)
  picker.vm.$emit('update:modelValue', ['chill', 'r&b'])
  await flushPromises()
  request.mockResolvedValueOnce({ artistId: 'a1', artistName: 'New Band' })
    .mockRejectedValueOnce({ status: 400, code: 'INVALID_FIELD', parameter: 'tags', message: 'Choose another tag' })
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(request.mock.calls[1][2].getAll('tags')).toEqual(['chill', 'r&b'])
  expect(picker.props('error')).toBe('Choose another tag')
  expect(wrapper.get('#song-title').element.value).toBe('Late Night')
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

it("adds the song to an existing album of the artist, sending the album's cover", async () => {
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  localStorage.setItem('aux-albums-a1', JSON.stringify([
    { albumId: 'al1', name: 'Blonde', coverUrl: 'data:image/jpeg;base64,AAA', createdBy: 'u1', tracks: [] },
  ]))
  route.query = { artist: 'a1', album: 'al1' }
  fetchAPI.mockResolvedValue({ artistId: 'a1', artistName: 'Frank Ocean', artistPfpUrl: null })
  vi.stubGlobal('fetch', vi.fn(async () => ({ blob: async () => new Blob(['img'], { type: 'image/jpeg' }) })))
  request.mockResolvedValueOnce({ musicPieceId: 'm1', name: 'Nights', coverUrl: '/uploads/c.webp', artistSummary: { artistId: 'a1', artistName: 'Frank Ocean' } })
  wrapper = mount(UploadMusic, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: true } } })
  await flushPromises()
  expect(wrapper.get('#song-album').element.value).toBe('al1')
  expect(wrapper.get('.drop img').attributes('src')).toBe('data:image/jpeg;base64,AAA')
  expect(wrapper.text()).toContain('Using the cover of Blonde')
  Object.defineProperty(wrapper.get('#mp3-input').element, 'files', { value: [new File(['a'], 'Nights.mp3', { type: 'audio/mpeg' })] })
  await wrapper.get('#mp3-input').trigger('change')
  await flushPromises()
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  const form = request.mock.calls[0][2]
  expect(form.get('coverImage').name).toBe('Blonde.jpg')
  const tracks = JSON.parse(localStorage.getItem('aux-albums-a1'))[0].tracks
  expect(tracks.map((track) => [track.musicPiece.musicPieceId, track.useAlbumCover])).toEqual([['m1', true]])
  expect(push).toHaveBeenCalledWith({ name: 'Artist', params: { artistId: 'a1' } })
  localStorage.clear()
})

it('stops using the album cover once you choose your own', async () => {
  localStorage.setItem('aux-albums-a1', JSON.stringify([{ albumId: 'al1', name: 'Blonde', coverUrl: 'data:image/jpeg;base64,AAA', createdBy: 'u1', tracks: [] }]))
  route.query = { artist: 'a1', album: 'al1' }
  fetchAPI.mockResolvedValue({ artistId: 'a1', artistName: 'Frank Ocean', artistPfpUrl: null })
  wrapper = mount(UploadMusic, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: true } } })
  await flushPromises()
  Object.defineProperty(wrapper.get('#cover-input').element, 'files', { value: [new File(['c'], 'mine.png', { type: 'image/png' })] })
  await wrapper.get('#cover-input').trigger('change')
  await flushPromises()
  expect(wrapper.get('.drop img').attributes('src')).toBe('blob:cover')
  expect(wrapper.get('.check input[type="checkbox"]').element.checked).toBe(false)
  localStorage.clear()
})
