import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { useEventsStore } from '@/stores/events.js'
import { useMusicStore } from '@/stores/music.js'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI } from '@/utils/api.js'
import Dashboard from './Dashboard.vue'
import EditPieceDialog from '@/components/EditPieceDialog.vue'

vi.mock('vue-router', () => ({ useRoute: () => ({ params: {} }), useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push: vi.fn(), replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const piece = (n) => ({ tags: [],
  musicPieceId: `m${n}`, name: `Song ${n}`, coverUrl: null, artistSummary: { artistId: `a${n}`, artistName: `Artist ${n}` },
  uploader: { userId: `u${n}`, pfpUrl: null, username: `user${n}` }, isAvailable: true,
})

const openCreate = vi.fn()
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
  wrapper = mount(Dashboard, {
    global: {
      stubs: {
        AppHeader: true,
        AppFooter: true,
        CreateDialog: { template: '<div />', methods: { open: openCreate } },
        RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
      },
    },
  })
  await flushPromises()
}

const section = (heading) => wrapper.get(`section[aria-labelledby="${heading}"]`).text()

it('offers Add tags for untagged uploads and opens the edit picker', async () => {
  useEventsStore().uploads = { uploadedMusicPieces: [piece(1), { ...piece(2), tags: ['chill'] }] }
  await render()
  const dialog = wrapper.getComponent(EditPieceDialog)
  const links = wrapper.findAll('.add-tags')
  expect(links).toHaveLength(1)
  await links[0].trigger('click')
  await flushPromises()
  expect(dialog.get('#edit-title').element.value).toBe('Song 1')
  expect(dialog.get('.tag-picker .hint').text()).toContain('Add tags to help people find this')
})

it('plays an upload from its cover and opens its page from its title', async () => {
  useEventsStore().uploads = { uploadedMusicPieces: [piece(1), piece(2)] }
  await render()
  const playPieces = vi.spyOn(useMusicStore(), 'playPieces').mockReturnValue(true)
  const card = wrapper.findAll('section[aria-labelledby="uploads-heading"] li.card')[1]
  await card.get('button.card-art-btn').trigger('click')
  expect(playPieces).toHaveBeenCalledWith([piece(1), piece(2)], 1)
  const link = card.findComponent({ name: 'RouterLink' })
  expect(link.props('to')).toEqual({ name: 'Piece', params: { musicPieceId: 'm2' } })
  expect(link.text()).toContain('Song 2')
})

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

it('shows all 20 recents in the scrolling row', async () => {
  useEventsStore().recents = { recents: Array.from({ length: 20 }, (_, i) => ({ musicPiece: piece(i), playlistId: null })) }
  await render()
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
  events.recents = { recents: [{ musicPiece: piece(2), playlistId: 'p1', playlistOwnerUsername: 'owner', playlistName: 'Mix' }, { musicPiece: piece(1), playlistId: null }] }
  await render()
  const music = useMusicStore()
  music.shuffleOn = true
  const playPlaylist = vi.spyOn(music, 'playPlaylist').mockResolvedValue(true)
  const playPieces = vi.spyOn(music, 'playPieces').mockReturnValue(true)
  const cards = wrapper.findAll('section[aria-labelledby="recent-heading"] button.card-main')
  expect(cards[0].attributes('aria-label')).toBe('Play Song 2 by Artist 2')
  await cards[0].trigger('click')
  expect(playPlaylist).toHaveBeenCalledWith('p1', { start: 'm2', shuffle: true, fallback: piece(2), name: 'Mix', ownerUsername: 'owner' })
  await cards[1].trigger('click')
  expect(playPieces).toHaveBeenCalledWith([piece(1)])
})

it('links a recent card to the playlist it was played from, and nothing without one', async () => {
  useEventsStore().recents = { recents: [{ musicPiece: piece(2), playlistId: 'p1', playlistOwnerUsername: 'owner', playlistName: 'Mix' }, { musicPiece: piece(1), playlistId: null }] }
  wrapper = mount(Dashboard, { global: { stubs: { AppHeader: true, AppFooter: true, CreateDialog: true, RouterLink: { props: ['to'], template: '<a :data-to="JSON.stringify(to)"><slot /></a>' } } } })
  await flushPromises()
  const links = wrapper.findAll('section[aria-labelledby="recent-heading"] .card-playlist')
  expect(links).toHaveLength(1)
  expect(links[0].text()).toBe('From Mix')
  expect(JSON.parse(links[0].attributes('data-to'))).toEqual({ name: 'Playlist', params: { username: 'owner', id: 'p1' } })
})

it('gives a new account a next step in every empty section and library filter', async () => {
  const events = useEventsStore()
  events.status = 'open'
  events.recents = { recents: [] }
  events.library = []
  events.uploads = { uploadedPiecesCount: 0, uploadedMusicPieces: [] }
  events.topArtists = { artists: [] }
  await render()

  const empty = (heading) => wrapper.get(`section[aria-labelledby="${heading}"] .empty`)
  for (const [heading, title, label, to] of [
    ['recent-heading', 'No plays yet', 'Find songs', { name: 'Explore', query: { category: 'MUSIC' } }],
    ['uploads-heading', 'No uploads yet', 'Upload music', { name: 'Upload' }],
    ['artists-heading', 'No top artists yet', 'Find artists', { name: 'Artists' }],
  ]) {
    const state = empty(heading)
    expect(state.get('.title').text()).toBe(title)
    const link = state.getComponent({ name: 'RouterLink' })
    expect(link.text()).toBe(label)
    expect(link.props('to')).toEqual(to)
  }

  const library = () => empty('library-heading')
  const findPlaylists = () => library().getComponent({ name: 'RouterLink' })
  expect(library().get('.title').text()).toBe('No playlists yet')
  expect(findPlaylists().text()).toBe('Find playlists')
  await library().get('button').trigger('click')
  expect(openCreate).toHaveBeenCalledOnce()

  const tabs = wrapper.findAll('[aria-label="Library filter"] [role="tab"]')
  await tabs[1].trigger('click')
  expect(library().get('button').text()).toBe('Create playlist')
  expect(library().find('a').exists()).toBe(false)
  await tabs[2].trigger('click')
  expect(library().get('.title').text()).toBe('Nothing saved yet')
  expect(library().find('button').exists()).toBe(false)
  expect(findPlaylists().props('to')).toEqual({ name: 'Explore', query: { category: 'PLAYLISTS' } })
})

it('shows uploads as cover cards that play with the rest queued after', async () => {
  useEventsStore().uploads = { uploadedPiecesCount: 2, uploadedMusicPieces: [piece(5), piece(6)] }
  await render()
  const music = useMusicStore()
  const playPieces = vi.spyOn(music, 'playPieces').mockReturnValue(true)
  const cards = wrapper.findAll('section[aria-labelledby="uploads-heading"] .card button.card-main')
  expect(cards).toHaveLength(2)
  await cards[1].trigger('click')
  expect(playPieces).toHaveBeenCalledWith([piece(5), piece(6)], 1)
})

it('orders the sections recent, library, uploads, top artists, with no search or matches', async () => {
  await render()
  const headings = wrapper.findAll('h2').map((h) => h.text())
  expect(headings).toEqual(['Recent Listening', 'Your Library', 'My Uploads', 'Your Top Artists'])
  expect(wrapper.find('input[type="search"]').exists()).toBe(false)
})

it('ranks top artists, five at first, with Show all for the rest', async () => {
  useEventsStore().topArtists = {
    artists: Array.from({ length: 8 }, (_, i) => ({ artistId: `a${i}`, artistName: `Artist ${i}`, artistPfpUrl: null })),
  }
  await render()
  const rows = () => wrapper.findAll('.artists li')
  expect(rows()).toHaveLength(5)
  expect(rows()[0].text()).toContain('1')
  expect(rows()[0].text()).toContain('Artist 0')
  await wrapper.get('section[aria-labelledby="artists-heading"] button.more').trigger('click')
  expect(rows()).toHaveLength(8)
})
