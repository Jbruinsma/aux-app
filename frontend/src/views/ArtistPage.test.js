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

// GET /api/artists/{id}, then its songs from GET /api/artists/{id}/music-pieces. `pages` maps a cursor (or 'first').
// `albums` is GET /api/artists/{id}/albums, `albumSongs` maps an albumId to its songs, and `uploads` is the caller's
function backend(artist, pages = { first: { musicPieces: [], nextCursor: null } }, { albums = [], albumSongs = {}, uploads = [] } = {}) {
  fetchAPI.mockImplementation(async (url) => {
    const { pathname, searchParams } = new URL(url, 'http://x')
    if (pathname.endsWith('/albums')) return albums
    const album = pathname.match(/\/api\/albums\/([^/]+)\/music-pieces$/)
    if (album) return { musicPieces: albumSongs[album[1]] ?? [], nextCursor: null }
    if (pathname.endsWith('/api/music-pieces')) return { uploadedMusicPieces: uploads }
    if (!pathname.includes('/music-pieces')) return artist
    return pages[searchParams.get('cursor') ?? 'first']
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
const blonde = { albumId: 'al1', albumName: 'Blonde', coverUrl: '/album-cover/blonde.webp', pieceCount: 2, canEdit: true }
const onAlbum = (piece, album = blonde, usesAlbumCover = true) => ({ ...piece, album: { albumId: album.albumId, albumName: album.albumName }, usesAlbumCover })
const fields = (form) => Object.fromEntries([...form.keys()].map((key) => [key, typeof form.get(key) === 'string' ? form.get(key) : 'file']))

it("groups songs into albums from each album's own list, split from the singles", async () => {
  signIn()
  const songs = [song(1), onAlbum(song(2), blonde, false), onAlbum({ ...song(3), coverUrl: '/album-cover/blonde.webp' }), song(4)]
  backend(details(), { first: { musicPieces: songs, nextCursor: null } }, { albums: [blonde], albumSongs: { al1: [songs[2], songs[1]] } })
  await render()
  expect(fetchAPI.mock.calls.map(([url]) => url)).toContainEqual(expect.stringMatching(/\/api\/albums\/al1\/music-pieces$/))
  const sections = wrapper.findAll('section.album')
  expect(sections).toHaveLength(2)
  expect(sections[0].get('h3').text()).toBe('Blonde')
  expect(sections[0].text()).toContain('2 songs')
  expect(sections[0].findAll('.ti').map((title) => title.text())).toEqual(['Song 3', 'Song 2'])
  // The server sends the album cover as the song's coverUrl when it uses it; Song 2 keeps its own (none here)
  expect(sections[0].findAll('.tr')[0].get('img.art').attributes('src')).toContain('/album-cover/blonde.webp')
  expect(sections[0].findAll('.tr')[1].find('img.art').exists()).toBe(false)
  expect(sections[1].get('h3').text()).toBe('Singles')
  expect(sections[1].findAll('.ti').map((title) => title.text())).toEqual(['Song 1', 'Song 4'])
  expect(sections[0].text()).toContain('Edit')

  const music = useMusicStore()
  const playPieces = vi.spyOn(music, 'playPieces').mockReturnValue(true)
  await sections[0].get('.album-actions .btn.primary').trigger('click')
  expect(playPieces.mock.calls[0][0].map((piece) => piece.musicPieceId)).toEqual(['m3', 'm2'])
})

async function openNewAlbum() {
  await wrapper.findAll('button').find((button) => button.text() === 'New album').trigger('click')
  await flushPromises()
  const dialog = wrapper.get('.album-dialog')
  await dialog.get('#album-name').setValue('Channel Orange')
  const input = dialog.get('#album-cover-input')
  Object.defineProperty(input.element, 'files', { value: [new File(['img'], 'cover.png', { type: 'image/png' })] })
  vi.stubGlobal('createImageBitmap', vi.fn(async () => ({ width: 600, height: 600, close: vi.fn() })))
  await input.trigger('change')
  await flushPromises()
  vi.unstubAllGlobals()
  return dialog
}

it('makes an album and puts your picked songs on it, offering only your own songs by this artist', async () => {
  signIn()
  const mine = [song(1), onAlbum(song(3)), song(7, 'a2')]
  backend(details(), { first: { musicPieces: [song(1), song(2), song(3)], nextCursor: null } }, { albums: [blonde], uploads: mine })
  request.mockResolvedValueOnce({ albumId: 'al2', albumName: 'Channel Orange', coverUrl: '/album-cover/co.webp' }).mockResolvedValue({})
  await render()
  const dialog = await openNewAlbum()
  const rows = dialog.findAll('.song')
  expect(rows.map((row) => row.get('.song-name').text())).toEqual(['Song 1', 'Song 3'])
  expect(rows[1].text()).toContain('On Blonde. Picking it moves it here.')
  await rows[1].get('.song-main input').setValue(true)
  await rows[0].get('.song-main input').setValue(true)
  await dialog.get('form').trigger('submit')
  await flushPromises()
  const [create, ...moves] = request.mock.calls
  expect(create.slice(0, 2)).toEqual(['POST', expect.stringMatching(/\/api\/artists\/a1\/albums$/)])
  expect(fields(create[2])).toEqual({ albumName: 'Channel Orange', coverImage: 'file' })
  expect(moves.map(([method, url, form]) => [method, url.split('/').at(-1), fields(form)])).toEqual([
    ['PUT', 'm1', { albumId: 'al2', useAlbumCover: 'true' }],
    ['PUT', 'm3', { albumId: 'al2', useAlbumCover: 'true' }],
  ])
  expect(useNotificationStore().message).toBe('Made the album Channel Orange.')
  // The albums and songs are read again
  expect(fetchAPI.mock.calls.filter(([url]) => url.endsWith('/api/artists/a1/albums'))).toHaveLength(2)
})

it('uses the album the artist already has with that name', async () => {
  signIn()
  const existing = { ...blonde, albumName: 'Channel Orange' }
  backend(details(), { first: { musicPieces: [song(1)], nextCursor: null } }, { albums: [existing], uploads: [song(1)] })
  request.mockResolvedValueOnce({ ...existing }).mockResolvedValue({})
  await render()
  const dialog = await openNewAlbum()
  await dialog.get('.song-main input').setValue(true)
  await dialog.get('form').trigger('submit')
  await flushPromises()
  expect(request.mock.calls[1][1]).toContain('/api/music-pieces/m1')
  expect(fields(request.mock.calls[1][2])).toEqual({ albumId: 'al1', useAlbumCover: 'true' })
  expect(useNotificationStore().message).toBe('Frank Ocean already had Channel Orange, so your songs went on it.')
})

it('edits an album: renames it, takes unpicked songs off and changes cover choices', async () => {
  signIn()
  const kept = onAlbum(song(1))
  const dropped = onAlbum(song(2))
  backend(details(), { first: { musicPieces: [kept, dropped], nextCursor: null } }, { albums: [blonde], albumSongs: { al1: [kept, dropped] }, uploads: [kept, dropped] })
  request.mockResolvedValueOnce({ ...blonde, albumName: 'Blond' }).mockResolvedValue({})
  await render()
  await wrapper.get('section.album .album-actions').findAll('button').find((button) => button.text().startsWith('Edit')).trigger('click')
  await flushPromises()
  const dialog = wrapper.get('.album-dialog')
  expect(dialog.get('#album-name').element.value).toBe('Blonde')
  expect(dialog.text()).toContain("A new cover changes it for every song using it, including other people's.")
  const rows = dialog.findAll('.song')
  expect(rows.map((row) => row.get('.song-main input').element.checked)).toEqual([true, true])
  await rows[0].get('.own-cover input').setValue(false)
  await rows[1].get('.song-main input').setValue(false)
  await dialog.get('#album-name').setValue('Blond')
  await dialog.get('form').trigger('submit')
  await flushPromises()
  expect(request.mock.calls.map(([method, url, form]) => [method, url.split('/api/')[1], fields(form)])).toEqual([
    ['PUT', 'albums/al1', { albumName: 'Blond' }],
    ['PUT', 'music-pieces/m1', { useAlbumCover: 'false' }],
    ['PUT', 'music-pieces/m2', { removeFromAlbum: 'true' }],
  ])
  expect(useNotificationStore().message).toBe('Saved Blond.')
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
