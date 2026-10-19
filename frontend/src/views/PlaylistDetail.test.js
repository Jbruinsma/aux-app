import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { fetchAPI, request } from '@/utils/api.js'
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

it('shows the invitation instead of not-found, and opens the playlist once accepted', async () => {
  fetchAPI
    .mockRejectedValueOnce({ status: 403, code: 'PLAYLIST_INVITE_PENDING' })
    .mockResolvedValueOnce({ invites: [{ playlistId: 'p1', playlistName: 'Mix', permission: 'EDITOR', invitedBy: { username: 'owner' } }] })
    .mockResolvedValueOnce(playlist({ access: 'EDITOR' }))
  request.mockResolvedValue({ status: 'ACCEPTED' })
  await render()
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
