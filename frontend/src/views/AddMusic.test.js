import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { fetchAPI, request } from '@/utils/api.js'
import AddMusic from './AddMusic.vue'

const { route, push } = vi.hoisted(() => ({ route: { params: {} }, push: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push, replace: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push, replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  route.params = { username: 'mo', id: 'p1' }
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  useNotificationStore().hide()
})

const piece = (n) => ({ musicPieceId: `m${n}`, name: `Song ${n}`, coverUrl: null, artistSummary: { artistName: 'Artist' }, uploader: { userId: 'u9', pfpUrl: null, username: 'uploader' }, isAvailable: true })
const playlist = (access) => ({ playlistId: 'p1', playlistName: 'Mix', access, musicPieces: [piece(1)] })

async function render() {
  wrapper = mount(AddMusic, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
}

it('turns away listeners and people outside the playlist', async () => {
  fetchAPI.mockResolvedValueOnce(playlist('LISTENER'))
  await render()
  expect(wrapper.text()).toContain("Only the playlist's owner and editors can add music.")
})

it('adds the chosen uploads, marks songs already in the playlist, and reports the result', async () => {
  fetchAPI.mockResolvedValueOnce(playlist('EDITOR')).mockResolvedValueOnce({ uploadedMusicPieces: [1, 2, 3].map(piece) })
  request.mockResolvedValueOnce({ addedCount: 1, failedCount: 1, totalPieces: 2 })
  await render()
  const boxes = wrapper.findAll('input[type="checkbox"]')
  expect(boxes[0].element.disabled).toBe(true)
  expect(wrapper.findAll('.song')[0].text()).toContain('In playlist')
  await boxes[1].setValue(true)
  await boxes[2].setValue(true)
  expect(wrapper.text()).toContain('2 songs chosen.')
  await wrapper.findAll('button').find((b) => b.text() === 'Add 2 songs').trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('POST', expect.stringContaining('/api/playlists/p1/pieces'), { musicPieceIds: ['m2', 'm3'] }, expect.any(Object))
  expect(useNotificationStore().message).toBe("Added 1 song. 1 song couldn't be added (already in the playlist, private, or the playlist is full).")
  expect(push).toHaveBeenCalledWith({ name: 'Playlist', params: { username: 'mo', id: 'p1' } })
})
