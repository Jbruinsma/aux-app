import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { useEventsStore } from '@/stores/events.js'
import { useMusicStore } from '@/stores/music.js'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI } from '@/utils/api.js'
import ExplorePage from './ExplorePage.vue'

const route = { params: {}, query: {} }
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push: vi.fn(), replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const piece = (n) => ({ tags: [],
  musicPieceId: `m${n}`, name: `Song ${n}`, coverUrl: null, artistSummary: { artistId: `a${n}`, artistName: `Artist ${n}` },
  uploader: { userId: `u${n}`, pfpUrl: null, username: `user${n}` }, isAvailable: true,
})
const topPiece = (n) => ({ ...piece(n), name: `Top ${n}`, playCount: 20 - n })

// Answers each GET by its path
function backend(answers) {
  fetchAPI.mockImplementation(async (url) => {
    const path = new URL(url, 'http://x').pathname
    const answer = Object.entries(answers).find(([key]) => path.endsWith(key))
    if (!answer) throw new Error(`Unexpected call: ${url}`)
    return typeof answer[1] === 'function' ? answer[1](url) : answer[1]
  })
}

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  route.query = {}
  fetchAPI.mockReset()
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  vi.useRealTimers()
})

async function render() {
  wrapper = mount(ExplorePage, {
    global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: { props: ['to'], template: '<a :data-to="JSON.stringify(to)"><slot /></a>' } } },
  })
  await flushPromises()
}

const section = (heading) => wrapper.find(`section[aria-labelledby="${heading}"]`)

it.each(['MUSIC', 'PLAYLISTS'])('shows tags on %s search results', async (category) => {
  vi.useFakeTimers()
  route.query = { category }
  const item = category === 'MUSIC' ? { ...piece(1), tags: ['chill', 'r&b'] }
    : { playlistId: 'p1', playlistName: 'Mix', ownerUsername: 'pal', tags: ['chill', 'r&b'] }
  backend({ '/api/tags': [], '/api/core/top': [], '/api/core/search': { results: [item], totalResults: 1 } })
  await render()
  await wrapper.get('#explore-search').setValue('mix')
  await vi.advanceTimersByTimeAsync(400)
  await flushPromises()
  const selector = category === 'MUSIC' ? '.piece-tags' : '.playlist-tags'
  expect(wrapper.get(selector).text()).toContain('r&b')
  expect(wrapper.get(selector).text()).toContain('chill')
})

it('shows genre tiles that link to their tag page, and this week\'s top songs ranked across two lists', async () => {
  backend({
    '/api/tags': ['chill', 'lo-fi', 'r&b'],
    '/api/core/top': Array.from({ length: 10 }, (_, i) => topPiece(i + 1)),
  })
  await render()
  const tiles = section('tags-heading').findAll('a.tag')
  expect(tiles.map((t) => t.text())).toEqual(['Chill', 'Lo-fi', 'R&b'])
  expect(JSON.parse(tiles[1].attributes('data-to'))).toEqual({ name: 'ExploreTag', params: { tag: 'lo-fi' } })

  const ranks = section('top-heading').findAll('.rk').map((r) => r.text())
  expect(ranks).toEqual(['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'])
  expect(section('top-heading').findAll('button.play')).toHaveLength(10)
  expect(section('top-heading').find('.ct').text()).toBe('19 plays')
})

it('plays a top song with the rest queued', async () => {
  backend({ '/api/tags': [], '/api/core/top': [topPiece(1), topPiece(2)] })
  await render()
  const music = useMusicStore()
  const playPieces = vi.spyOn(music, 'playPieces').mockResolvedValue(true)
  await section('top-heading').findAll('button.play')[1].trigger('click')
  expect(playPieces).toHaveBeenCalledWith([topPiece(1), topPiece(2)], 1)
  // No tags: the genre section is left out
  expect(section('tags-heading').exists()).toBe(false)
})

it('suggests songs like the last one played, from Discover, and lists matches from the event stream', async () => {
  backend({ '/api/tags': [], '/api/core/top': [], '/m7/discover': { musicPieces: [piece(8), piece(9)] } })
  const events = useEventsStore()
  await render()
  expect(section('similar-heading').exists()).toBe(false)

  events.recents = { recents: [{ musicPiece: piece(7), playlistId: null }] }
  events.matches = { matches: [{ user: { userId: 'u5', username: 'jordan', pfpUrl: null }, compatibility: 87, sharedArtists: [], sharedTags: ['chill', 'r&b'] }] }
  await flushPromises()
  expect(section('similar-heading').text()).toContain('More like Song 7')
  expect(section('similar-heading').text()).toContain('Song 8')
  expect(section('match-heading').text()).toContain('jordan')
  expect(section('match-heading').text()).toContain('Super')
  expect(section('match-heading').text()).toContain('You both listen to chill and r&b.')
})

it('searches from Explore, opening on the tab named in the link, and Cancel goes back to browsing', async () => {
  vi.useFakeTimers()
  route.query = { category: 'PLAYLISTS' }
  backend({
    '/api/tags': ['chill'],
    '/api/core/top': [],
    '/api/core/search': (url) => {
      expect(new URL(url, 'http://x').searchParams.get('category')).toBe('PLAYLISTS')
      return { results: [{ playlistId: 'p1', playlistName: 'Road trip', playlistCoverUrl: null, ownerUsername: 'maya', totalPieces: 3 }], totalResults: 1 }
    },
  })
  await render()
  await wrapper.get('#explore-search').setValue('road')
  await vi.advanceTimersByTimeAsync(400)
  await flushPromises()
  expect(section('results-heading').text()).toContain('Road trip')
  expect(section('tags-heading').exists()).toBe(false)

  await wrapper.get('button.cancel').trigger('click')
  await nextTick()
  expect(section('results-heading').exists()).toBe(false)
  expect(section('tags-heading').exists()).toBe(true)
})
