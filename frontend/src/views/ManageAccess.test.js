import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { fetchAPI, request } from '@/utils/api.js'
import AddFriends from './AddFriends.vue'

const { route } = vi.hoisted(() => ({ route: { params: {} } }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))
vi.mock('@/router/index.js', () => ({ default: { push: vi.fn(), replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  route.params = { username: 'owner', id: 'p1' }
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  useNotificationStore().hide()
})

const playlist = (access) => ({ playlistId: 'p1', playlistName: 'Mix', isPublic: false, playlistOwner: { userId: 'u1', username: 'owner' }, access })
const member = (userId, username, permission = 'LISTENER', status = 'PENDING') => ({ user: { userId, username }, permission, status })

async function render() {
  wrapper = mount(AddFriends, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
  return wrapper
}

it('only lets the owner manage access', async () => {
  fetchAPI.mockResolvedValueOnce(playlist('EDITOR'))
  await render()
  expect(wrapper.text()).toContain("Only the playlist's owner can manage who has access.")
  expect(fetchAPI).toHaveBeenCalledTimes(1)
})

it('lists members and invites someone found by search with the chosen access', async () => {
  vi.useFakeTimers()
  fetchAPI
    .mockResolvedValueOnce(playlist('OWNER'))
    .mockResolvedValueOnce([member('u2', 'pal', 'EDITOR', 'ACCEPTED')])
    .mockResolvedValueOnce({ results: [{ userId: 'u1', username: 'owner' }, { userId: 'u2', username: 'pal' }, { userId: 'u3', username: 'new' }], totalResults: 3 })
  request.mockResolvedValueOnce(member('u3', 'new', 'EDITOR'))
  await render()
  expect(wrapper.text()).toContain('pal')
  await wrapper.get('#invite-permission').setValue('EDITOR')
  await wrapper.get('input[type="search"]').setValue('n')
  await vi.advanceTimersByTimeAsync(300)
  await flushPromises()
  const results = wrapper.findAll('.result')
  expect(results.map((r) => r.text())).toEqual(['NnewAdd'])
  await results[0].trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('POST', expect.stringContaining('/api/playlists/p1/members'), { userId: 'u3', permission: 'EDITOR' }, expect.any(Object))
  expect(wrapper.text()).toContain('Invite pending')
  vi.useRealTimers()
})

it('changes access and removes people, and puts the choice back if the change fails', async () => {
  fetchAPI.mockResolvedValueOnce(playlist('OWNER')).mockResolvedValueOnce([member('u2', 'pal'), member('u3', 'bud', 'LISTENER', 'ACCEPTED')])
  request
    .mockRejectedValueOnce({ code: 'INTERNAL_ERROR' })
    .mockResolvedValueOnce({})
  await render()
  const select = wrapper.get('#permission-u2')
  await select.setValue('EDITOR')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('PUT', expect.stringContaining('/members'), { userId: 'u2', permission: 'EDITOR' })
  expect(select.element.value).toBe('LISTENER')
  await wrapper.findAll('button.remove')[1].trigger('click')
  await flushPromises()
  expect(request).toHaveBeenLastCalledWith('DELETE', expect.stringContaining('/members'), { userId: 'u3' })
  expect(wrapper.text()).not.toContain('bud')
  expect(useNotificationStore().message).toBe('bud no longer has access.')
})
