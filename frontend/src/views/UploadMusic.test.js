import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
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

// The artist from GET /api/artists/a1, its albums, and the caller's uploads (GET /api/music-pieces)
function albumBackend({ albums = [], uploads = [] } = {}) {
  fetchAPI.mockImplementation(async (url) => {
    if (url.endsWith('/api/artists/a1/albums')) return albums
    if (url.endsWith('/api/music-pieces')) return { uploadedMusicPieces: uploads }
    return { artistId: 'a1', artistName: 'Frank Ocean', artistPfpUrl: null }
  })
}
const blonde = { albumId: 'al1', albumName: 'Blonde', coverUrl: '/album-cover/blonde.webp', pieceCount: 3, canEdit: false }
const fields = (form) => Object.fromEntries([...form.keys()].map((key) => [key, typeof form.get(key) === 'string' ? form.get(key) : 'file']))

it("uploads the song onto an existing album with its albumId, leaving the cover out to use the album's", async () => {
  albumBackend({ albums: [blonde] })
  route.query = { artist: 'a1', album: 'al1' }
  request.mockResolvedValueOnce({ musicPieceId: 'm1', name: 'Nights', artistSummary: { artistId: 'a1', artistName: 'Frank Ocean' } })
  wrapper = mount(UploadMusic, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: true } } })
  await flushPromises()
  expect(fetchAPI.mock.calls.map(([url]) => url)).toContainEqual(expect.stringMatching(/\/api\/artists\/a1\/albums$/))
  expect(wrapper.get('#song-album').element.value).toBe('al1')
  expect(wrapper.get('.drop img').attributes('src')).toContain('/album-cover/blonde.webp')
  expect(wrapper.text()).toContain('Using the cover of Blonde')
  Object.defineProperty(wrapper.get('#mp3-input').element, 'files', { value: [new File(['a'], 'Nights.mp3', { type: 'audio/mpeg' })] })
  await wrapper.get('#mp3-input').trigger('change')
  await flushPromises()
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(request).toHaveBeenCalledTimes(1)
  expect(fields(request.mock.calls[0][2])).toEqual({ name: 'Nights', artistId: 'a1', isPublic: 'false', mp3File: 'file', albumId: 'al1' })
  expect(useNotificationStore().message).toBe('Uploaded “Nights” to Blonde.')
  expect(push).toHaveBeenCalledWith({ name: 'Artist', params: { artistId: 'a1' } })
})

it('stops using the album cover once you choose your own, and sends that cover with the albumId', async () => {
  albumBackend({ albums: [blonde] })
  route.query = { artist: 'a1', album: 'al1' }
  request.mockResolvedValueOnce({ musicPieceId: 'm1', name: 'Nights' })
  wrapper = mount(UploadMusic, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: true } } })
  await flushPromises()
  Object.defineProperty(wrapper.get('#cover-input').element, 'files', { value: [new File(['c'], 'mine.png', { type: 'image/png' })] })
  await wrapper.get('#cover-input').trigger('change')
  await flushPromises()
  expect(wrapper.get('.drop img').attributes('src')).toBe('blob:cover')
  expect(wrapper.get('.check input[type="checkbox"]').element.checked).toBe(false)
  Object.defineProperty(wrapper.get('#mp3-input').element, 'files', { value: [new File(['a'], 'Nights.mp3', { type: 'audio/mpeg' })] })
  await wrapper.get('#mp3-input').trigger('change')
  await flushPromises()
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(fields(request.mock.calls[0][2])).toMatchObject({ albumId: 'al1', coverImage: 'file' })
})

it('makes the new album first, uploads the song onto it, then moves your other picked songs', async () => {
  vi.useRealTimers()
  HTMLDialogElement.prototype.showModal = vi.fn()
  HTMLDialogElement.prototype.close = vi.fn(function close() { this.dispatchEvent(new Event('close')) })
  const artistSummary = { artistId: 'a1', artistName: 'Frank Ocean' }
  const single = { musicPieceId: 's1', name: 'Ivy', coverUrl: null, artistSummary, album: null, usesAlbumCover: false }
  const onBlonde = { musicPieceId: 's2', name: 'Nights', coverUrl: null, artistSummary, album: { albumId: 'al1', albumName: 'Blonde' }, usesAlbumCover: true }
  const otherArtist = { musicPieceId: 's3', name: 'Elsewhere', coverUrl: null, artistSummary: { artistId: 'a2', artistName: 'Other' }, album: null }
  albumBackend({ albums: [blonde], uploads: [single, onBlonde, otherArtist] })
  route.query = { artist: 'a1' }
  request
    .mockResolvedValueOnce({ albumId: 'al2', albumName: 'Channel Orange', coverUrl: '/album-cover/co.webp' })
    .mockResolvedValueOnce({ musicPieceId: 'm9', name: 'Pink Matter', artistSummary })
    .mockResolvedValue({})
  wrapper = mount(UploadMusic, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: true } } })
  await flushPromises()
  const pick = async (selector, file) => {
    Object.defineProperty(wrapper.get(selector).element, 'files', { value: [file], configurable: true })
    await wrapper.get(selector).trigger('change')
    await flushPromises()
  }
  await pick('#mp3-input', new File(['a'], 'Pink Matter.mp3', { type: 'audio/mpeg' }))
  await pick('#cover-input', new File(['c'], 'pink.png', { type: 'image/png' }))

  await wrapper.get('#song-album').setValue('new')
  await flushPromises()
  const dialog = wrapper.get('.album-dialog')
  expect(dialog.get('.upload-track').text()).toContain('Pink Matter')
  expect(dialog.get('.cover-pick img').attributes('src')).toBe('blob:cover')
  // Only the caller's songs by this artist; one on another album can still be picked, which moves it
  const rows = dialog.findAll('.song:not(.upload-track)')
  expect(rows.map((row) => row.get('.song-name').text())).toEqual(['Ivy', 'Nights'])
  expect(rows[1].text()).toContain('On Blonde. Picking it moves it here.')
  await rows[0].get('input').setValue(true)
  await rows[1].get('input').setValue(true)
  await dialog.findAll('.song:not(.upload-track)')[1].get('.own-cover input').setValue(false)
  await dialog.get('#album-name').setValue('Channel Orange')
  await dialog.get('form').trigger('submit')
  await flushPromises()
  expect(wrapper.get('.draft-name').text()).toBe('Channel Orange')
  expect(wrapper.get('#song-album').element.value).toBe('new')
  expect(request).not.toHaveBeenCalled()

  await wrapper.get('form.upload-card').trigger('submit')
  await flushPromises()
  const [create, upload, moveIvy, moveNights] = request.mock.calls
  expect(create.slice(0, 2)).toEqual(['POST', expect.stringMatching(/\/api\/artists\/a1\/albums$/)])
  expect(fields(create[2])).toEqual({ albumName: 'Channel Orange', coverImage: 'file' })
  expect(fields(upload[2])).toMatchObject({ albumId: 'al2' })
  expect(upload[2].has('coverImage')).toBe(false)
  expect(moveIvy.slice(0, 2)).toEqual(['PUT', expect.stringContaining('/api/music-pieces/s1')])
  expect(fields(moveIvy[2])).toEqual({ albumId: 'al2', useAlbumCover: 'true' })
  expect(fields(moveNights[2])).toEqual({ albumId: 'al2', useAlbumCover: 'false' })
  expect(useNotificationStore().message).toBe('Uploaded “Pink Matter” and made the album Channel Orange.')
  expect(push).toHaveBeenCalledWith({ name: 'Artist', params: { artistId: 'a1' } })
})

it("doesn't upload when the new album can't be made", async () => {
  vi.useRealTimers()
  HTMLDialogElement.prototype.showModal = vi.fn()
  HTMLDialogElement.prototype.close = vi.fn(function close() { this.dispatchEvent(new Event('close')) })
  albumBackend()
  route.query = { artist: 'a1' }
  request.mockRejectedValueOnce({ status: 400, code: 'IMAGE_TOO_SMALL' })
  wrapper = mount(UploadMusic, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: true } } })
  await flushPromises()
  Object.defineProperty(wrapper.get('#mp3-input').element, 'files', { value: [new File(['a'], 'Pink Matter.mp3', { type: 'audio/mpeg' })] })
  await wrapper.get('#mp3-input').trigger('change')
  Object.defineProperty(wrapper.get('#cover-input').element, 'files', { value: [new File(['c'], 'pink.png', { type: 'image/png' })] })
  await wrapper.get('#cover-input').trigger('change')
  await flushPromises()
  await wrapper.get('#song-album').setValue('new')
  await flushPromises()
  await wrapper.get('#album-name').setValue('Channel Orange')
  await wrapper.get('.album-dialog form').trigger('submit')
  await flushPromises()
  await wrapper.get('form.upload-card').trigger('submit')
  await flushPromises()
  expect(request).toHaveBeenCalledTimes(1)
  expect(wrapper.text()).toContain('Too small.')
  expect(push).not.toHaveBeenCalled()
})

it('goes back to None when the new album dialog is closed without making one', async () => {
  HTMLDialogElement.prototype.showModal = vi.fn()
  HTMLDialogElement.prototype.close = vi.fn(function close() { this.dispatchEvent(new Event('close')) })
  route.query = { artist: 'a1' }
  albumBackend()
  wrapper = mount(UploadMusic, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: true } } })
  await flushPromises()
  await wrapper.get('#song-album').setValue('new')
  await flushPromises()
  await wrapper.findAll('.album-dialog button').find((button) => button.text() === 'Cancel').trigger('click')
  await flushPromises()
  expect(wrapper.get('#song-album').element.value).toBe('')
})

// YouTube import: GET /api/imports/youtube says whether the tabs show; polls answer from `polls` in order
const VIDEO = 'https://youtu.be/dQw4w9WgXcQ?si=share'
const pending = (status) => ({ importId: 'i1', status, videoId: 'dQw4w9WgXcQ', title: null, durationSeconds: null, thumbnailUrl: 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg', error: null, expiresAt: null })
const ready = { ...pending('READY'), title: 'Never Gonna Give You Up', durationSeconds: 213, expiresAt: '2026-11-15T06:00:00Z' }

function youtubeBackend({ available = true, polls = [] } = {}) {
  fetchAPI.mockImplementation(async (url) => {
    if (url.endsWith('/api/imports/youtube')) return { available }
    if (url.includes('/api/imports/youtube/')) return polls.shift()
    if (url.endsWith('/api/artists/a1/albums')) return []
    return { artistId: 'a1', artistName: 'Rick Astley', artistPfpUrl: null }
  })
  request.mockImplementation(async (method, url) => {
    if (method === 'POST' && url.endsWith('/api/imports/youtube')) return pending('QUEUED')
    if (method === 'DELETE') return null
    return { musicPieceId: 'm1', name: 'Never Gonna Give You Up' }
  })
}

async function mountWithArtist() {
  route.query = { artist: 'a1' }
  wrapper = mount(UploadMusic, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: true } } })
  await flushPromises()
}

async function getAudio() {
  await wrapper.findAll('[role="tab"]')[1].trigger('click')
  await wrapper.get('#youtube-url').setValue(VIDEO)
  await wrapper.findAll('button').find((b) => b.text() === 'Get audio').trigger('click')
  await flushPromises()
}

it('only shows the YouTube tab to callers the server allows', async () => {
  youtubeBackend({ available: false })
  await mountWithArtist()
  expect(wrapper.find('[role="tablist"]').exists()).toBe(false)
  expect(wrapper.text()).toContain('MP3 file')
})

it('imports a YouTube video, fills in its title, and uploads with the importId instead of a file', async () => {
  youtubeBackend({ polls: [pending('DOWNLOADING'), ready] })
  await mountWithArtist()
  await getAudio()
  expect(request).toHaveBeenCalledWith('POST', expect.stringMatching(/\/api\/imports\/youtube$/), { url: VIDEO }, expect.any(Object))
  expect(wrapper.text()).toContain('Waiting for a free slot…')
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()

  await vi.advanceTimersByTimeAsync(2000)
  await flushPromises()
  expect(wrapper.text()).toContain('Getting the audio…')
  await vi.advanceTimersByTimeAsync(2000)
  await flushPromises()
  expect(fetchAPI).toHaveBeenCalledWith(expect.stringMatching(/\/api\/imports\/youtube\/i1$/), expect.any(Object))
  expect(wrapper.text()).toContain('3:33 · Ready to upload')
  expect(wrapper.get('#song-title').element.value).toBe('Never Gonna Give You Up')

  Object.defineProperty(wrapper.get('#cover-input').element, 'files', { value: [new File(['c'], 'c.png', { type: 'image/png' })] })
  await wrapper.get('#cover-input').trigger('change')
  await flushPromises()
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  const create = request.mock.calls.find(([, url]) => url.includes('/api/music-pieces/create'))
  expect(fields(create[2])).toEqual({ name: 'Never Gonna Give You Up', artistId: 'a1', isPublic: 'false', importId: 'i1', coverImage: 'file' })
  // Used up by the upload, so leaving the page doesn't try to discard it
  wrapper.unmount()
  wrapper = null
  expect(request.mock.calls.some(([method]) => method === 'DELETE')).toBe(false)
})

it("explains a failed import and keeps the link so it can be fixed", async () => {
  youtubeBackend({ polls: [{ ...pending('FAILED'), error: { code: 'AUDIO_TOO_LONG', message: 'Track must be at most 10 minutes' } }] })
  await mountWithArtist()
  await getAudio()
  await vi.advanceTimersByTimeAsync(2000)
  await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toBe('That video is too long. Songs can be up to 10 minutes long.')
  expect(wrapper.get('#youtube-url').element.value).toBe(VIDEO)
})

it("shows the server's reason when an import can't start", async () => {
  youtubeBackend()
  request.mockImplementation(async () => {
    throw Object.assign(new Error('bad'), { code: 'INVALID_YOUTUBE_URL' })
  })
  await mountWithArtist()
  await getAudio()
  expect(wrapper.get('[role="alert"]').text()).toContain("That isn't a link to a YouTube video")
})

it('cancels a running import when you cancel it or leave the page', async () => {
  youtubeBackend({ polls: [pending('DOWNLOADING'), pending('DOWNLOADING')] })
  await mountWithArtist()
  await getAudio()
  await wrapper.findAll('button').find((b) => b.text() === 'Cancel').trigger('click')
  expect(request).toHaveBeenCalledWith('DELETE', expect.stringMatching(/\/api\/imports\/youtube\/i1$/), undefined, expect.any(Object))
  // No more polling once it's cancelled
  const polls = () => fetchAPI.mock.calls.filter(([url]) => url.includes('/api/imports/youtube/')).length
  await vi.advanceTimersByTimeAsync(6000)
  expect(polls()).toBe(0)

  await getAudio()
  request.mockClear()
  wrapper.unmount()
  wrapper = null
  expect(request).toHaveBeenCalledWith('DELETE', expect.stringMatching(/\/api\/imports\/youtube\/i1$/), undefined, expect.any(Object))
})
