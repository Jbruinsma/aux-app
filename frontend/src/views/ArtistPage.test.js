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
})
afterEach(() => {
  wrapper?.unmount()
  useNotificationStore().hide()
})

async function render() {
  wrapper = mount(ArtistPage, {
    global: { stubs: { AppHeader: true, AppFooter: true, ArtistImagesDialog: true, RouterLink: { template: '<a><slot /></a>' } } },
  })
  await flushPromises()
}

// GET /api/artists/{id}, then the music search used to find the artist's songs
function backend(artist, searchResults = []) {
  fetchAPI.mockImplementation(async (url) => (url.includes('/api/core/search')
    ? { results: searchResults, totalResults: searchResults.length }
    : artist))
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

it("lists the artist's songs from search, leaving out other artists with a similar name, and plays them", async () => {
  backend(details(), [song(1), song(2, 'a2'), song(3)])
  await render()
  const search = fetchAPI.mock.calls.map(([url]) => url).find((url) => url.includes('/api/core/search'))
  expect(search).toContain('category=MUSIC')
  expect(search).toContain('query=Frank+Ocean')
  const rows = wrapper.findAll('.track')
  expect(rows.map((row) => row.get('.track-name').text())).toEqual(['Song 1', 'Song 3'])
  const music = useMusicStore()
  const playPieces = vi.spyOn(music, 'playPieces').mockReturnValue(true)
  await rows[1].get('.track-main').trigger('click')
  expect(playPieces).toHaveBeenCalledWith([song(1), song(3)], 1)
})
