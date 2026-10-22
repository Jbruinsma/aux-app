import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { fetchAPI } from '@/utils/api.js'
import Discover from './Discover.vue'

const { route, push } = vi.hoisted(() => ({ route: { params: {} }, push: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ back: vi.fn(), push }) }))
vi.mock('@/router/index.js', () => ({ default: { push, replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), request: vi.fn() }))

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  route.params = { id: 'm_seed' }
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  vi.unstubAllGlobals()
})

const piece = (n) => ({ musicPieceId: `m${n}`, name: `Song ${n}`, coverUrl: `/c${n}.webp`, artistSummary: { artistName: 'Artist' }, tags: [] })
const response = (count) => ({ seed: piece('seed'), musicPieces: Array.from({ length: count }, (_, i) => piece(i)) })

async function render() {
  wrapper = mount(Discover, { global: { stubs: { AppHeader: true, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
  return wrapper
}

it('asks for uploads when there are too few songs to recommend', async () => {
  fetchAPI.mockResolvedValue(response(3))
  await render()
  expect(fetchAPI).toHaveBeenCalledWith(expect.stringContaining('/api/music-pieces/m_seed/discover'), { notifyOnError: false })
  expect(wrapper.text()).toContain('Not enough songs to recommend yet')
  expect(wrapper.text()).toContain('Upload song')
  expect(wrapper.find('.viewport').exists()).toBe(false)
})

it('shows the grid once there are enough songs', async () => {
  fetchAPI.mockResolvedValue(response(8))
  await render()
  expect(wrapper.find('.viewport').exists()).toBe(true)
  expect(wrapper.text()).toContain('Songs like Song seed')
})

it("explains a song that can't be used", async () => {
  fetchAPI.mockRejectedValue({ status: 404, code: 'MUSIC_PIECE_NOT_FOUND' })
  await render()
  expect(wrapper.text()).toContain("This song can't be used for Discover")
})
