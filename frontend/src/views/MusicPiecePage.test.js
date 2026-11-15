import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'
import { useMusicStore } from '@/stores/music.js'
import { fetchAPI, request } from '@/utils/api.js'
import MusicPiecePage from './MusicPiecePage.vue'

const { route } = vi.hoisted(() => ({ route: { params: {} } }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push: vi.fn(), replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const details = (overrides) => ({
  musicPieceId: 'm1', name: 'Let It Happen', coverUrl: null,
  artistSummary: { artistId: 'a1', artistName: 'Tame Impala', artistPfpUrl: null },
  album: { albumId: 'l1', albumName: 'Currents' }, usesAlbumCover: false,
  uploader: { userId: 'u_up', pfpUrl: null, username: 'justin' },
  tags: ['psychedelic', 'synth'], isPublic: true, isFavorite: false, isAvailable: true,
  durationSeconds: 467, playCount: 1280, createdAt: '2026-10-03T21:28:57Z', ...overrides,
})

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  route.params = { musicPieceId: 'm1' }
  fetchAPI.mockReset()
  request.mockReset()
})
afterEach(() => {
  wrapper?.unmount()
  useNotificationStore().hide()
})

async function render(piece = details()) {
  fetchAPI.mockResolvedValue(piece)
  wrapper = mount(MusicPiecePage, {
    global: { stubs: { AppHeader: true, AppFooter: true, EditPieceDialog: true, RouterLink: { template: '<a><slot /></a>' } } },
  })
  await flushPromises()
}

const button = (text) => wrapper.findAll('button').find((b) => b.text() === text)

it('shows the song with its details, and asks visitors to log in to favorite', async () => {
  await render()
  expect(fetchAPI).toHaveBeenCalledWith(expect.stringContaining('/api/music-pieces/m1'), { notifyOnError: false })
  const text = wrapper.text()
  for (const part of ['Let It Happen', 'Tame Impala', 'Currents', 'justin', 'psychedelic', 'synth', '7:47', '1,280', 'Oct 3, 2026']) {
    expect(text).toContain(part)
  }
  expect(text).toContain('Log in to favorite')
  expect(button('Edit')).toBeUndefined()
  expect(text).not.toContain('Private')
})

it('leaves out the length when an old upload has none', async () => {
  await render(details({ durationSeconds: null }))
  expect(wrapper.text()).not.toContain('Length')
  expect(wrapper.text()).toContain('Plays')
})

it('plays the song on its own, then pauses and resumes it', async () => {
  await render()
  const music = useMusicStore()
  const playPieces = vi.spyOn(music, 'playPieces').mockImplementation(() => {
    music.currentMusicPiece = { ...music.currentMusicPiece, uuid: 'm1' }
    music.isPlaying = true
    return true
  })
  await button('Play').trigger('click')
  expect(playPieces).toHaveBeenCalledWith([details()])
  await button('Pause').trigger('click')
  expect(music.isPlaying).toBe(false)
  await button('Play').trigger('click')
  expect(music.isPlaying).toBe(true)
  expect(playPieces).toHaveBeenCalledTimes(1)
})

it('favorites and unfavorites the song', async () => {
  useUserStore().login({ userId: 'u_me', username: 'me', onboardingStep: 'DONE' }, 'token')
  await render()
  request.mockResolvedValue(null)
  await button('Favorite').trigger('click')
  await flushPromises()
  expect(request).toHaveBeenLastCalledWith('PUT', expect.stringContaining('/api/music-pieces/m1/favorite'), undefined, { notifyOnError: false })
  expect(button('Favorited').attributes('aria-pressed')).toBe('true')
  expect(useNotificationStore().message).toBe('Let It Happen is in your favorites.')
  await button('Favorited').trigger('click')
  await flushPromises()
  expect(request).toHaveBeenLastCalledWith('DELETE', expect.stringContaining('/api/music-pieces/m1/favorite'), undefined, { notifyOnError: false })
  expect(button('Favorite').attributes('aria-pressed')).toBe('false')
})

it('keeps the heart as it was when favoriting fails', async () => {
  useUserStore().login({ userId: 'u_me', username: 'me', onboardingStep: 'DONE' }, 'token')
  await render()
  request.mockRejectedValue(Object.assign(new Error('down'), { status: 0, code: 'NETWORK_ERROR' }))
  await button('Favorite').trigger('click')
  await flushPromises()
  expect(button('Favorite').attributes('aria-pressed')).toBe('false')
  expect(wrapper.get('[role="alert"]').text()).toContain("We couldn't favorite this song.")
})

it('lets the uploader edit, and badges their private song', async () => {
  useUserStore().login({ userId: 'u_up', username: 'justin', onboardingStep: 'DONE' }, 'token')
  await render(details({ isPublic: false }))
  expect(button('Edit')).toBeDefined()
  expect(wrapper.get('.badge').text()).toBe('Private')
})

it('says the song is not on Aux on a 404, and offers a retry on other errors', async () => {
  fetchAPI.mockRejectedValue(Object.assign(new Error('Not found'), { status: 404, code: 'MUSIC_PIECE_NOT_FOUND' }))
  wrapper = mount(MusicPiecePage, { global: { stubs: { AppHeader: true, AppFooter: true, EditPieceDialog: true, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
  expect(wrapper.text()).toContain("This song isn't on Aux")
  wrapper.unmount()

  fetchAPI.mockRejectedValueOnce(Object.assign(new Error('down'), { status: 500 })).mockResolvedValueOnce(details())
  wrapper = mount(MusicPiecePage, { global: { stubs: { AppHeader: true, AppFooter: true, EditPieceDialog: true, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
  expect(wrapper.text()).toContain("We couldn't load this song")
  await button('Try again').trigger('click')
  await flushPromises()
  expect(wrapper.get('h1').text()).toBe('Let It Happen')
})
