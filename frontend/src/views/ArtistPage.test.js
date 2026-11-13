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
