import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useMusicStore } from '@/stores/music.js'
import { fetchAPI } from '@/utils/api.js'
import ExploreTag from './ExploreTag.vue'

vi.mock('vue-router', () => ({ useRoute: () => ({ params: { tag: 'Lo-Fi' }, query: {} }), useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push: vi.fn(), replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const piece = (n) => ({
  musicPieceId: `m${n}`, name: `Song ${n}`, coverUrl: null, artistSummary: { artistId: `a${n}`, artistName: `Artist ${n}` },
  uploader: { userId: `u${n}`, pfpUrl: null, username: `user${n}` }, isAvailable: true,
})

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  fetchAPI.mockReset()
})
afterEach(() => wrapper?.unmount())

async function render() {
  wrapper = mount(ExploreTag, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
}

it('names the tag, plays its songs, and loads the next page with the cursor', async () => {
  fetchAPI
    .mockResolvedValueOnce({ musicPieces: [piece(1), piece(2)], nextCursor: '2' })
    .mockResolvedValueOnce({ musicPieces: [piece(3)], nextCursor: null })
  await render()
  expect(fetchAPI.mock.calls[0][0]).toMatch(/\/api\/tags\/lo-fi\/music-pieces$/)
  expect(wrapper.get('h1').text()).toBe('Lo-fi')

  const playPieces = vi.spyOn(useMusicStore(), 'playPieces').mockResolvedValue(true)
  await wrapper.findAll('button.play')[1].trigger('click')
  expect(playPieces).toHaveBeenCalledWith([piece(1), piece(2)], 1)

  await wrapper.get('button.more').trigger('click')
  await flushPromises()
  expect(fetchAPI.mock.calls[1][0]).toMatch(/\/api\/tags\/lo-fi\/music-pieces\?cursor=2$/)
  expect(wrapper.findAll('.tr')).toHaveLength(3)
  expect(wrapper.find('button.more').exists()).toBe(false)
})

it('says so when nothing has the tag yet', async () => {
  fetchAPI.mockResolvedValueOnce({ musicPieces: [], nextCursor: null })
  await render()
  expect(wrapper.text()).toContain('No songs tagged lo-fi yet')
})
