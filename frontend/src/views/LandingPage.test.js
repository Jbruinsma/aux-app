import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { fetchAPI } from '@/utils/api.js'
import LandingPage from './LandingPage.vue'

vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const top = (n) => ({ musicPieceId: `m${n}`, name: `Song ${n}`, coverUrl: `/uploads/cover/${n}.webp`, artistSummary: { artistId: `a${n}`, artistName: `Artist ${n}` }, playCount: 10 - n })

function backend(songs) {
  fetchAPI.mockImplementation(async (url) => {
    if (url.endsWith('/api/core/users')) return []
    if (songs.failed) throw { status: 500, code: 'REQUEST_FAILED' }
    return songs
  })
}

async function render() {
  const wrapper = mount(LandingPage, { global: { stubs: { HeroLayout: { template: '<div><slot /><slot name="below" /></div>' }, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  fetchAPI.mockReset()
})

it("shows the first 3 of this week's most played songs", async () => {
  backend([1, 2, 3, 4].map(top))
  const wrapper = await render()
  expect(fetchAPI.mock.calls.some(([url]) => url.endsWith('/api/core/top'))).toBe(true)
  expect(wrapper.findAll('.tile-name').map((name) => name.text())).toEqual(['Song 1', 'Song 2', 'Song 3'])
  expect(wrapper.get('.cover').attributes('src')).toContain('/uploads/cover/1.webp')
  expect(wrapper.text()).toContain('The songs getting the most plays on Aux this week.')
})

it('hides the Top section when nothing was played or the call fails', async () => {
  backend([])
  expect((await render()).find('.top').exists()).toBe(false)
  backend({ failed: true })
  expect((await render()).find('.top').exists()).toBe(false)
})
