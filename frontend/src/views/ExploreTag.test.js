import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import ExploreTag from './ExploreTag.vue'

vi.mock('vue-router', () => ({ useRoute: () => ({ params: { tag: 'Lo-Fi' }, query: {} }), useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push: vi.fn(), replace: vi.fn() } }))

let wrapper
beforeEach(() => setActivePinia(createPinia()))
afterEach(() => wrapper?.unmount())

it('names the tag and shows sample songs, labeled and not playable, until the endpoint ships', async () => {
  wrapper = mount(ExploreTag, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
  expect(wrapper.get('h1').text()).toBe('Lo-fi')
  expect(wrapper.text()).toContain("Preview: browsing by genre isn't live yet")
  expect(wrapper.findAll('.tr').length).toBeGreaterThan(0)
  expect(wrapper.find('button.play').exists()).toBe(false)
})
