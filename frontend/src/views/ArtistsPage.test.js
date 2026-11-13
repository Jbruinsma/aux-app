import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI } from '@/utils/api.js'
import ArtistsPage from './ArtistsPage.vue'

vi.mock('vue-router', () => ({ useRoute: () => ({ params: {} }), useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push: vi.fn(), replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const artist = (id, name) => ({ artistId: id, artistName: name, artistPfpUrl: null })

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  fetchAPI.mockReset()
  fetchAPI.mockImplementation(async (url) => (url.includes('/favorites/')
    ? [artist('a1', 'Frank Ocean')]
    : [artist('a2', 'Tame Impala'), artist('a3', 'Impala Ray')]))
})
afterEach(() => {
  wrapper?.unmount()
  vi.useRealTimers()
})

async function render() {
  wrapper = mount(ArtistsPage, {
    global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: { props: ['to'], template: '<a :data-to="JSON.stringify(to)"><slot /></a>' } } },
  })
  await flushPromises()
}

it('shows your favorite artists until you search, then the matches, each with Add music', async () => {
  vi.useFakeTimers()
  await render()
  expect(fetchAPI.mock.calls[0][0]).toContain('/api/artists/favorites/me')
  expect(wrapper.text()).toContain('Your favorite artists')
  expect(wrapper.text()).toContain('Frank Ocean')

  await wrapper.get('#artists-search').setValue('impala')
  expect(wrapper.text()).toContain('Searching…')
  await vi.advanceTimersByTimeAsync(300)
  await flushPromises()
  expect(fetchAPI.mock.calls.at(-1)[0]).toMatch(/\/api\/artists\?q=impala$/)
  expect(wrapper.findAll('.name').map((name) => name.text())).toEqual(['Tame Impala', 'Impala Ray'])
  const add = wrapper.get('a[aria-label="Add music to Tame Impala"]')
  expect(JSON.parse(add.attributes('data-to'))).toEqual({ name: 'Upload', query: { artist: 'a2' } })
})
