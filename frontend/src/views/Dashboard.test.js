import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { useEventsStore } from '@/stores/events.js'
import { useMusicStore } from '@/stores/music.js'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI } from '@/utils/api.js'
import Dashboard from './Dashboard.vue'

vi.mock('vue-router', () => ({ useRoute: () => ({ params: {} }), useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push: vi.fn(), replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))
// The samples are switched off on main; turn them on here to check they only fill empty rows
vi.mock('@/utils/dashboardPreview.js', async (original) => ({ ...(await original()), DASHBOARD_PREVIEW: true }))

const piece = (n) => ({
  musicPieceId: `m${n}`, name: `Song ${n}`, coverUrl: null, artistSummary: { artistId: `a${n}`, artistName: `Artist ${n}` },
  uploader: { userId: `u${n}`, pfpUrl: null, username: `user${n}` }, isAvailable: true,
})

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  vi.stubGlobal('IntersectionObserver', class { observe() {} unobserve() {} disconnect() {} })
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  vi.unstubAllGlobals()
})

async function render() {
  wrapper = mount(Dashboard, { global: { stubs: { AppHeader: true, AppFooter: true, CreateDialog: true, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
}

const section = (heading) => wrapper.get(`section[aria-labelledby="${heading}"]`).text()

it('makes no REST calls for its rows, which load from the event stream and update live', async () => {
  const events = useEventsStore()
  events.status = 'connecting'
  await render()
  expect(section('recent-heading')).toContain('Loading your recent listening…')
  expect(section('library-heading')).toContain('Loading your library…')
  expect(section('uploads-heading')).toContain('Loading your uploads…')

  events.recents = { recents: [{ musicPiece: piece(1), playlistId: null }] }
  events.topArtists = { artists: [{ artistId: 'a9', artistName: 'Top One', artistPfpUrl: null }] }
  events.library = [{ playlistId: 'p1', playlistName: 'Mix', playlistCoverUrl: null, ownerUsername: 'me', totalPieces: 2, isSaved: false }]
  events.uploads = { uploadedPiecesCount: 1, uploadedMusicPieces: [piece(5)] }
  await nextTick()
  expect(section('recent-heading')).toContain('Song 1')
  expect(section('artists-heading')).toContain('Top One')
  expect(section('library-heading')).toContain('Mix')
  expect(section('uploads-heading')).toContain('Song 5')

  events.recents = { recents: [{ musicPiece: piece(2), playlistId: 'p1' }, { musicPiece: piece(1), playlistId: null }] }
  await nextTick()
  expect(wrapper.findAll('section[aria-labelledby="recent-heading"] .card').map((card) => card.text()))
    .toEqual(['Song 2Artist 2UUploaded by user2', 'Song 1Artist 1UUploaded by user1'])

  const called = fetchAPI.mock.calls.map(([url]) => url)
  for (const path of ['/recent-listening', '/top-artists', '/users/library', '/api/music-pieces']) {
    expect(called.some((url) => url.includes(path))).toBe(false)
  }
})

it('fills empty rows with the samples, and shows all 20 recents in the scrolling row', async () => {
  const events = useEventsStore()
  events.recents = { recents: [] }
  events.topArtists = { artists: [] }
  await render()
  expect(wrapper.findAll('section[aria-labelledby="recent-heading"] .card').length).toBeGreaterThan(0)
  expect(section('artists-heading')).toContain('Frank Ocean')

  events.recents = { recents: Array.from({ length: 20 }, (_, i) => ({ musicPiece: piece(i), playlistId: null })) }
  await nextTick()
  expect(wrapper.findAll('section[aria-labelledby="recent-heading"] .card')).toHaveLength(20)
  expect(section('recent-heading')).toContain('The last songs you played, newest first.')
})

it('shows an error on rows that have nothing yet while the stream cannot connect', async () => {
  useEventsStore().status = 'error'
  await render()
  expect(section('recent-heading')).toContain("We couldn't load your recent listening.")
  expect(section('recent-heading')).toContain("Check that the server is running; we'll keep trying.")
  expect(wrapper.get('section[aria-labelledby="recent-heading"] [role="alert"]').exists()).toBe(true)
  expect(section('uploads-heading')).toContain("We couldn't load your uploads.")
})

it('plays a recent card from its playlist with the current shuffle, or on its own without one', async () => {
  const events = useEventsStore()
  events.recents = { recents: [{ musicPiece: piece(2), playlistId: 'p1' }, { musicPiece: piece(1), playlistId: null }] }
  await render()
  const music = useMusicStore()
  music.shuffleOn = true
  const playPlaylist = vi.spyOn(music, 'playPlaylist').mockResolvedValue(true)
  const playPieces = vi.spyOn(music, 'playPieces').mockReturnValue(true)
  const cards = wrapper.findAll('section[aria-labelledby="recent-heading"] button.card-main')
  expect(cards[0].attributes('aria-label')).toBe('Play Song 2 by Artist 2')
  await cards[0].trigger('click')
  expect(playPlaylist).toHaveBeenCalledWith('p1', { start: 'm2', shuffle: true, fallback: piece(2) })
  await cards[1].trigger('click')
  expect(playPieces).toHaveBeenCalledWith([piece(1)])
})

it('does not make the sample recents playable', async () => {
  useEventsStore().recents = { recents: [] }
  await render()
  expect(wrapper.find('section[aria-labelledby="recent-heading"] button.card-main').exists()).toBe(false)
})

it('shows uploads as cover cards that play with the rest queued after, and next steps on empty rows', async () => {
  const events = useEventsStore()
  events.uploads = { uploadedPiecesCount: 0, uploadedMusicPieces: [] }
  events.library = []
  await render()
  expect(section('uploads-heading')).toContain('No uploads yet')
  expect(section('uploads-heading')).toContain('Upload music')
  await wrapper.findAll('section[aria-labelledby="library-heading"] .tab')[2].trigger('click')
  expect(section('library-heading')).toContain('Find playlists')

  events.uploads = { uploadedPiecesCount: 2, uploadedMusicPieces: [piece(5), piece(6)] }
  await nextTick()
  const music = useMusicStore()
  const playPieces = vi.spyOn(music, 'playPieces').mockReturnValue(true)
  const cards = wrapper.findAll('section[aria-labelledby="uploads-heading"] .card button.card-main')
  expect(cards).toHaveLength(2)
  await cards[1].trigger('click')
  expect(playPieces).toHaveBeenCalledWith([piece(5), piece(6)], 1)
})
