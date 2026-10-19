import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI, request } from '@/utils/api.js'
import NotificationBell from './NotificationBell.vue'

const { push } = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/router/index.js', () => ({ default: { push } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const invite = (id, name) => ({ playlistId: id, playlistName: name, permission: 'EDITOR', invitedBy: { username: 'owner' } })

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  useNotificationStore().hide()
})

async function render() {
  wrapper = mount(NotificationBell, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
  return wrapper
}

it('counts new invitations, clears the count once opened, and remembers that after a reload', async () => {
  fetchAPI.mockResolvedValue({ invites: [invite('p1', 'Mix'), invite('p2', 'Road trip')] })
  await render()
  expect(wrapper.get('.badge').text()).toBe('2')
  await wrapper.get('.bell').trigger('click')
  await flushPromises()
  expect(wrapper.find('.badge').exists()).toBe(false)
  expect(wrapper.findAll('.item')).toHaveLength(2)
  expect(wrapper.get('.item').text()).toContain('owner invited you to edit Mix')

  wrapper.unmount()
  fetchAPI.mockResolvedValue({ invites: [invite('p3', 'New one'), invite('p1', 'Mix'), invite('p2', 'Road trip')] })
  await render()
  expect(wrapper.get('.badge').text()).toBe('1')
})

it('accepts an invitation and opens the playlist, or declines and drops it from the list', async () => {
  fetchAPI.mockResolvedValue({ invites: [invite('p1', 'Mix'), invite('p2', 'Road trip')] })
  request.mockResolvedValue({})
  await render()
  await wrapper.get('.bell').trigger('click')
  await flushPromises()

  const declineSecond = wrapper.findAll('.item')[1].findAll('button').find((b) => b.text() === 'Decline')
  await declineSecond.trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('DELETE', expect.stringContaining('/api/playlists/p2/invites'))
  expect(wrapper.findAll('.item')).toHaveLength(1)

  await wrapper.findAll('button').find((b) => b.text() === 'Accept').trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('POST', expect.stringContaining('/api/playlists/p1/invites/accept'))
  expect(push).toHaveBeenCalledWith({ name: 'Playlist', params: { username: 'owner', id: 'p1' } })
  expect(useNotificationStore().message).toBe('You joined Mix.')
})
