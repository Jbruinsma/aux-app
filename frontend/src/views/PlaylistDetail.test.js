import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { fetchAPI, request } from '@/utils/api.js'
import { useEventsStore } from '@/stores/events.js'
import { useMusicStore } from '@/stores/music.js'
import PlaylistDetail from './PlaylistDetail.vue'

const { route, push } = vi.hoisted(() => ({ route: { params: {} }, push: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push, replace: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push, replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

let observe
let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  route.params = { username: 'owner', id: 'p1' }
  observe = null
  vi.stubGlobal('IntersectionObserver', class {
    constructor(callback) { observe = () => callback([{ isIntersecting: true }]) }
    observe() {}
    unobserve() {}
    disconnect() {}
  })
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  useNotificationStore().hide()
  vi.unstubAllGlobals()
})

const track = (n) => ({ musicPieceId: `m${n}`, name: `Song ${n}`, artistSummary: { artistName: 'Artist' } })
const playlist = (overrides) => ({
  playlistId: 'p1', playlistName: 'Mix', playlistOwner: { username: 'owner' }, isSaved: false,
  totalPieces: 1, musicPieces: [track(1)], nextCursor: null, editors: [], access: null, ...overrides,
})

async function render() {
  wrapper = mount(PlaylistDetail, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
  return wrapper
}

it.each([
  [null, true, false],
  ['LISTENER', false, false],
  ['EDITOR', false, true],
  ['OWNER', false, true],
])('with access %s, shows Save: %s and the edit menu: %s', async (access, save, menu) => {
  fetchAPI.mockResolvedValue(playlist({ access }))
  await render()
  expect(wrapper.find('button[aria-pressed]').exists()).toBe(save)
  expect(wrapper.find('button[aria-label="More options"]').exists()).toBe(menu)
})

it('keeps Manage access and Delete for the owner only', async () => {
  fetchAPI.mockResolvedValue(playlist({ access: 'EDITOR', editors: [{ userId: 'u2', username: 'pal' }] }))
  await render()
  await wrapper.get('button[aria-label="More options"]').trigger('click')
  const menu = wrapper.get('#playlist-menu').text()
  expect(menu).toContain('Edit playlist')
  expect(menu).not.toContain('Manage access')
  expect(menu).not.toContain('Delete playlist')
  expect(wrapper.get('.editors').text()).toContain('pal')
})

it('shows the invitation from the live invites, instead of not-found, and opens the playlist once accepted', async () => {
  fetchAPI
    .mockRejectedValueOnce({ status: 403, code: 'PLAYLIST_INVITE_PENDING' })
    .mockResolvedValueOnce(playlist({ access: 'EDITOR' }))
  request.mockResolvedValue({ status: 'ACCEPTED' })
  await render()
  expect(wrapper.text()).toContain("You're invited to this playlist")
  useEventsStore().invites = { invites: [{ playlistId: 'p1', playlistName: 'Mix', permission: 'EDITOR', invitedBy: { username: 'owner' } }] }
  await flushPromises()
  expect(fetchAPI.mock.calls.some(([url]) => url.includes('/api/playlists/invites'))).toBe(false)
  expect(wrapper.text()).toContain("You're invited to Mix")
  expect(wrapper.text()).toContain('@owner invited you to listen to and edit this private playlist.')
  await wrapper.findAll('button').find((b) => b.text() === 'Accept invitation').trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('POST', expect.stringContaining('/api/playlists/p1/invites/accept'))
  expect(wrapper.text()).toContain('Song 1')
})

it('loads the next page of songs with the cursor it was given until there are none left', async () => {
  fetchAPI
    .mockResolvedValueOnce(playlist({ totalPieces: 3, nextCursor: 'c 1/x' }))
    .mockResolvedValueOnce({ musicPieces: [track(2)], nextCursor: 'c2' })
    .mockResolvedValueOnce({ musicPieces: [track(3)], nextCursor: null })
  await render()
  expect(wrapper.text()).toContain('3 songs')
  observe()
  await flushPromises()
  expect(fetchAPI).toHaveBeenLastCalledWith(expect.stringContaining('/api/playlists/p1/pieces?cursor=c+1%2Fx'))
  observe()
  await flushPromises()
  expect(wrapper.findAll('.track')).toHaveLength(3)
  expect(wrapper.find('.more').exists()).toBe(false)
})

it("removes a song from an editor's row menu without touching the next-page cursor", async () => {
  fetchAPI
    .mockResolvedValueOnce(playlist({ access: 'EDITOR', totalPieces: 3, musicPieces: [track(1), track(2)], nextCursor: 'c1' }))
    .mockResolvedValueOnce({ musicPieces: [track(3)], nextCursor: null })
  request.mockResolvedValue(null)
  await render()
  expect(wrapper.text()).toContain('3 songs')
  await wrapper.get('button[aria-label="More options for Song 1"]').trigger('click')
  await wrapper.get('#track-menu-m1 button').trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('DELETE', expect.stringContaining('/api/playlists/p1/pieces/m1'), undefined, { notifyOnError: false })
  expect(wrapper.findAll('.track-name').map((name) => name.text())).toEqual(['Song 2'])
  expect(wrapper.text()).toContain('2 songs')
  expect(wrapper.find('#track-menu-m1').exists()).toBe(false)
  observe()
  await flushPromises()
  expect(fetchAPI).toHaveBeenLastCalledWith(expect.stringContaining('/api/playlists/p1/pieces?cursor=c1'))
})

it('gives listeners a row menu with Discover but not Remove', async () => {
  fetchAPI.mockResolvedValue(playlist({ access: 'LISTENER' }))
  await render()
  await wrapper.get('button[aria-label="More options for Song 1"]').trigger('click')
  const menu = wrapper.get('#track-menu-m1').text()
  expect(menu).toContain('Discover similar songs')
  expect(menu).not.toContain('Remove from playlist')
})

it('offers Discover above Remove to editors, and no Discover on unavailable songs', async () => {
  fetchAPI.mockResolvedValue(playlist({ access: 'EDITOR', musicPieces: [track(1), { ...track(2), isAvailable: false }] }))
  await render()
  await wrapper.get('button[aria-label="More options for Song 1"]').trigger('click')
  expect(wrapper.findAll('#track-menu-m1 li').map((item) => item.text())).toEqual(['Discover similar songs', 'Remove from playlist'])
  await wrapper.get('button[aria-label="More options for Song 2"]').trigger('click')
  expect(wrapper.get('#track-menu-m2').text()).not.toContain('Discover')
})

it('has no row menu for a listener on an unavailable song', async () => {
  fetchAPI.mockResolvedValue(playlist({ access: 'LISTENER', musicPieces: [{ ...track(1), isAvailable: false }] }))
  await render()
  expect(wrapper.find('.track-more').exists()).toBe(false)
})

it.each([null, { status: 404, code: 'PLAYLIST_NOT_FOUND' }])('closes deletion, resets its player and goes home after %s', async (error) => {
  fetchAPI.mockResolvedValue(playlist({ access: 'OWNER' }))
  if (error) request.mockRejectedValueOnce(error)
  else request.mockResolvedValueOnce(null)
  const music = useMusicStore()
  music.currentPlaylistUUID = 'p1'
  music.isPlaying = true
  await render()
  const dialog = wrapper.get('dialog').element
  dialog.showModal = vi.fn()
  dialog.close = vi.fn()
  await wrapper.get('button[aria-label="More options"]').trigger('click')
  await wrapper.findAll('#playlist-menu button').find((b) => b.text() === 'Delete playlist').trigger('click')
  await wrapper.get('.confirm-actions .primary').trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('DELETE', expect.stringContaining('/api/playlists/p1'), undefined, { notifyOnError: false })
  expect(dialog.close).toHaveBeenCalled()
  expect(music.isEmpty()).toBe(true)
  expect(push).toHaveBeenCalledWith({ name: 'Dashboard' })
})

it.each(['LISTENER', 'EDITOR', null])('shows not-found to a former member or saver (%s)', async (access) => {
  fetchAPI.mockResolvedValueOnce(playlist({ access, isSaved: access === null }))
  await render()
  fetchAPI.mockRejectedValueOnce({ status: 404, code: 'PLAYLIST_NOT_FOUND' })
  await wrapper.vm.loadPlaylist()
  expect(wrapper.text()).toContain("This playlist isn't available")
  expect(wrapper.find('.tracks').exists()).toBe(false)
})

it('keeps the dialog open on a server failure and leaves a different playlist playing', async () => {
  fetchAPI.mockResolvedValue(playlist({ access: 'OWNER' }))
  request.mockRejectedValueOnce({ status: 500 })
  const music = useMusicStore()
  music.currentPlaylistUUID = 'p2'
  music.isPlaying = true
  await render()
  const dialog = wrapper.get('dialog').element
  dialog.close = vi.fn()
  await wrapper.get('.confirm-actions .primary').trigger('click')
  await flushPromises()
  expect(dialog.close).not.toHaveBeenCalled()
  expect(wrapper.get('.confirm .error').text()).toContain("We couldn't delete")
  expect(music.currentPlaylistUUID).toBe('p2')
  expect(music.isPlaying).toBe(true)
  expect(push).not.toHaveBeenCalled()
})
