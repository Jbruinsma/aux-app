import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { fetchAPI, request } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'
import { useNotificationStore } from '@/stores/notification.js'
import TagPicker from '@/components/TagPicker.vue'
import EditPlaylist from './EditPlaylist.vue'

const { push } = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { username: 'me', id: 'p1' }, query: {} }), useRouter: () => ({ push, replace: vi.fn() }), onBeforeRouteLeave: vi.fn() }))
vi.mock('@/router/index.js', () => ({ default: { push, replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), request: vi.fn() }))
vi.mock('@/utils/motion.js', () => ({ animationsDone: async () => {} }))
let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  useUserStore().login({ username: 'me' }, 'token')
  request.mockReset()
  request.mockResolvedValue({ playlistId: 'p1', playlistName: 'Mix' })
})
afterEach(() => { wrapper?.unmount(); useNotificationStore().hide() })
async function render(access = 'OWNER') {
  fetchAPI.mockImplementation(async (url) => url.includes('/api/tags') ? [] : { playlistId: 'p1', playlistName: 'Mix', isPublic: false, access, tags: ['chill'] })
  wrapper = mount(EditPlaylist, { global: { stubs: { AppHeader: true, AppFooter: true, StepBanner: true, RouterLink: true } } })
  await flushPromises()
}
const submit = async () => { await wrapper.get('form').trigger('submit'); await flushPromises() }

it('omits unchanged tags and never sends tags for an editor', async () => {
  await render('EDITOR')
  expect(wrapper.findComponent(TagPicker).exists()).toBe(false)
  await wrapper.get('#playlist-name').setValue('New mix')
  await submit()
  expect(request.mock.calls[0][2].has('tags')).toBe(false)
})

it.each([['r&b', 'late night'], []])('replaces or clears owner tags with repeated fields: %j', async (...values) => {
  // each row is an array of tag values (including the empty row).
  await render()
  wrapper.getComponent(TagPicker).vm.$emit('update:modelValue', values)
  await flushPromises()
  await submit()
  expect(request.mock.calls[0][2].getAll('tags')).toEqual(values.length ? values : [''])
  expect(wrapper.vm.original.tags).toEqual(values)
})

it('leaves unchanged owner tags out and displays server tag errors by the picker', async () => {
  await render()
  await wrapper.get('#playlist-name').setValue('New mix')
  request.mockRejectedValueOnce({ status: 400, code: 'INVALID_FIELD', parameter: 'tags', message: 'Choose another tag' })
  await submit()
  expect(request.mock.calls[0][2].has('tags')).toBe(false)
  expect(wrapper.getComponent(TagPicker).props('error')).toBe('Choose another tag')
})
