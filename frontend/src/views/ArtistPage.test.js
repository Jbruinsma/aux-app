import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'
import { useMusicStore } from '@/stores/music.js'
import { fetchAPI, request } from '@/utils/api.js'
import ArtistPage from './ArtistPage.vue'

const { route } = vi.hoisted(() => ({ route: { params: {} } }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push: vi.fn(), replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const details = (overrides) => ({
  artistId: 'a1', artistName: 'Frank Ocean', artistPfpUrl: null, artistBannerUrl: null, isFavorite: false, canEdit: false, ...overrides,
})

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  route.params = { artistId: 'a1' }
  fetchAPI.mockReset()
  request.mockReset()
  localStorage.clear()
  HTMLDialogElement.prototype.showModal = vi.fn()
  HTMLDialogElement.prototype.close = vi.fn(function close() { this.dispatchEvent(new Event('close')) })
})
afterEach(() => {
  wrapper?.unmount()
  useNotificationStore().hide()
})

async function render() {
  wrapper = mount(ArtistPage, {
    global: { stubs: { AppHeader: true, AppFooter: true, ArtistImagesDialog: true, EditPieceDialog: { template: '<div />', methods: { open: vi.fn() } }, RouterLink: { template: '<a><slot /></a>' } } },
  })
  await flushPromises()
}

// GET /api/artists/{id}, then its songs from GET /api/artists/{id}/music-pieces. `pages` maps a cursor (or 'first')
function backend(artist, pages = { first: { musicPieces: [], nextCursor: null } }) {
  fetchAPI.mockImplementation(async (url) => {
    if (!url.includes('/music-pieces')) return artist
    return pages[new URL(url, 'http://x').searchParams.get('cursor') ?? 'first']
  })
}

const song = (n, artistId = 'a1') => ({ musicPieceId: `m${n}`, name: `Song ${n}`, coverUrl: null, artistSummary: { artistId, artistName: 'Frank Ocean' } })

it('shows the artist like a profile, and asks visitors to log in to favorite', async () => {
  backend(details())
  await render()
  expect(fetchAPI.mock.calls[0][0]).toContain('/api/artists/a1')
  expect(wrapper.get('.name').text()).toBe('Frank Ocean')
  expect(wrapper.get('.meta').text()).toBe('Artist')
  expect(wrapper.text()).toContain('Songs by Frank Ocean')
  expect(wrapper.text()).toContain('No songs yet')
  expect(wrapper.text()).toContain('Log in to favorite')
  expect(wrapper.text()).not.toContain('Edit picture and banner')
})

it('favorites and unfavorites the artist', async () => {
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  backend(details())
  request.mockResolvedValue(null)
  await render()
  const button = () => wrapper.get('button[aria-pressed]')
  await button().trigger('click')
  await flushPromises()
  expect(request).toHaveBeenLastCalledWith('PUT', expect.stringContaining('/api/artists/a1/favorite'), undefined, { notifyOnError: false })
  expect(button().attributes('aria-pressed')).toBe('true')
  expect(button().text()).toBe('Favorited')
  await button().trigger('click')
  await flushPromises()
  expect(request).toHaveBeenLastCalledWith('DELETE', expect.stringContaining('/api/artists/a1/favorite'), undefined, { notifyOnError: false })
  expect(button().attributes('aria-pressed')).toBe('false')
})

it("offers image editing to the artist's creator only", async () => {
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  backend(details({ canEdit: true }))
  await render()
  expect(wrapper.text()).toContain('Edit picture and banner')
})

it('says when the artist does not exist', async () => {
  fetchAPI.mockRejectedValue({ status: 404, code: 'ARTIST_NOT_FOUND' })
  await render()
  expect(wrapper.text()).toContain("This artist isn't on Aux")
})

it("lists the artist's songs most played first, with play counts, pages through them and plays them", async () => {
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  backend(details(), {
    first: { musicPieces: [{ ...song(1), playCount: 40 }, { ...song(2), playCount: 7 }], nextCursor: 'c1' },
    c1: { musicPieces: [{ ...song(3), playCount: 2 }], nextCursor: null },
  })
  await render()
  expect(fetchAPI.mock.calls.map(([url]) => url)).toContainEqual(expect.stringMatching(/\/api\/artists\/a1\/music-pieces$/))
  expect(wrapper.text()).toContain('40 plays')
  await wrapper.findAll('button').find((b) => b.text() === 'Show more').trigger('click')
  await flushPromises()
  expect(fetchAPI.mock.calls.at(-1)[0]).toContain('/api/artists/a1/music-pieces?cursor=c1')
  const music = useMusicStore()
  const playPieces = vi.spyOn(music, 'playPieces').mockReturnValue(true)
  const plays = wrapper.findAll('button.play')
  expect(plays).toHaveLength(3)
  await plays[2].trigger('click')
  expect(playPieces).toHaveBeenCalledWith([{ ...song(1), playCount: 40 }, { ...song(2), playCount: 7 }, { ...song(3), playCount: 2 }], 2)
  expect(wrapper.text()).toContain('Add music')
})

const signIn = () => useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
const stored = (pieces) => pieces.map((piece) => ({ musicPiece: piece, useAlbumCover: piece.musicPieceId !== 'm2' }))

it('groups songs into albums with the album cover, split from the singles', async () => {
  signIn()
  const songs = [1, 2, 3, 4].map((n) => song(n))
  localStorage.setItem('aux-albums-a1', JSON.stringify([
    { albumId: 'al1', name: 'Blonde', coverUrl: 'data:image/jpeg;base64,AAA', createdBy: 'u1', tracks: stored([songs[2], songs[1]]) },
  ]))
  backend(details(), { first: { musicPieces: songs, nextCursor: null } })
  await render()
  const sections = wrapper.findAll('section.album')
  expect(sections).toHaveLength(2)
  expect(sections[0].get('h3').text()).toBe('Blonde')
  expect(sections[0].findAll('.ti').map((title) => title.text())).toEqual(['Song 3', 'Song 2'])
  // Song 2 keeps its own cover (null here), Song 3 shows the album's
  expect(sections[0].findAll('.tr')[0].get('img.art').attributes('src')).toBe('data:image/jpeg;base64,AAA')
  expect(sections[0].findAll('.tr')[1].find('img.art').exists()).toBe(false)
  expect(sections[1].get('h3').text()).toBe('Singles')
  expect(sections[1].findAll('.ti').map((title) => title.text())).toEqual(['Song 1', 'Song 4'])
  expect(sections[0].text()).toContain('Edit')

  const music = useMusicStore()
  const playPieces = vi.spyOn(music, 'playPieces').mockReturnValue(true)
  await sections[0].get('.album-actions .btn.primary').trigger('click')
  expect(playPieces.mock.calls[0][0].map((piece) => piece.musicPieceId)).toEqual(['m3', 'm2'])
})

it('makes an album from the picked songs', async () => {
  signIn()
  const songs = [1, 2, 3].map((n) => song(n))
  backend(details(), { first: { musicPieces: songs, nextCursor: null } })
  await render()
  expect(wrapper.findAll('section.album')).toHaveLength(1)
  await wrapper.findAll('button').find((button) => button.text() === 'New album').trigger('click')
  const dialog = wrapper.get('.album-dialog')
  await dialog.get('#album-name').setValue('Channel Orange')
  const cover = new File(['img'], 'cover.png', { type: 'image/png' })
  vi.stubGlobal('createImageBitmap', vi.fn(async () => ({ width: 600, height: 600, close: vi.fn() })))
  const input = dialog.get('#album-cover-input')
  Object.defineProperty(input.element, 'files', { value: [cover] })
  await input.trigger('change')
  await flushPromises()
  const boxes = dialog.findAll('.song-main input')
  await boxes[2].setValue(true)
  await boxes[0].setValue(true)
  await dialog.get('form').trigger('submit')
  // The cover is read as a data URL (no canvas here), which takes a real tick
  await vi.waitFor(() => expect(localStorage.getItem('aux-albums-a1')).not.toBeNull())
  await flushPromises()
  vi.unstubAllGlobals()
  const saved = JSON.parse(localStorage.getItem('aux-albums-a1'))
  expect(saved[0].name).toBe('Channel Orange')
  expect(saved[0].tracks.map((track) => track.musicPiece.musicPieceId)).toEqual(['m3', 'm1'])
  const sections = wrapper.findAll('section.album')
  expect(sections[0].get('h3').text()).toBe('Channel Orange')
  expect(sections[1].findAll('.ti').map((title) => title.text())).toEqual(['Song 2'])
})

it('shows who uploaded each song, and lets the uploader change the cover of their own singles only', async () => {
  signIn()
  const mine = { ...song(1), uploader: { userId: 'u1', username: 'me', pfpUrl: null } }
  const theirs = { ...song(2), uploader: { userId: 'u9', username: 'pal', pfpUrl: null } }
  backend(details(), { first: { musicPieces: [mine, theirs], nextCursor: null } })
  await render()
  expect(wrapper.findAll('.row-edit').map((button) => button.attributes('aria-label'))).toEqual(['Change the cover of Song 1'])
  expect(wrapper.findAll('.up').map((uploader) => uploader.get('.up-name').text())).toEqual(['Uploaded by me', 'Uploaded by pal'])
})
