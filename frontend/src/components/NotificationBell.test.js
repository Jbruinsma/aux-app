import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { useEventsStore } from '@/stores/events.js'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI, request } from '@/utils/api.js'
import { timeAgo } from '@/utils/notifications.js'
import NotificationBell from './NotificationBell.vue'

const { push } = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/router/index.js', () => ({ default: { push } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const NOW = new Date().toISOString()
const playlist = { playlistId: 'p1', playlistName: 'Mix', playlistCoverUrl: null }
const note = (id, type, extra = {}) => ({
  notificationId: id, type, actor: { userId: 'u2', username: 'pal', pfpUrl: null }, playlist,
  playlistOwnerUsername: 'owner', permission: null, trackCount: null, isRead: false, createdAt: NOW, ...extra,
})
const firstPage = (notifications, unreadCount, nextCursor = null) => ({ notifications, unreadCount, nextCursor })

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  useNotificationStore().hide()
})

async function render() {
  wrapper = mount(NotificationBell, { global: { stubs: { RouterLink: { props: ['to'], template: '<a :data-to="JSON.stringify(to)"><slot /></a>' } } } })
  await flushPromises()
}

it('shows each type from the live event, linking to the playlist through its owner, not the actor', async () => {
  useEventsStore().notifications = firstPage([
    note('n1', 'PLAYLIST_INVITE', { permission: 'EDITOR', actor: { userId: 'u3', username: 'owner' } }),
    note('n2', 'INVITE_ACCEPTED'),
    note('n3', 'TRACKS_ADDED', { trackCount: 3 }),
  ], 3)
  request.mockResolvedValue(null)
  await render()
  await wrapper.get('.bell').trigger('click')
  const rows = wrapper.findAll('.item')
  expect(rows.map((row) => row.find('.item-text').text())).toEqual([
    'owner invited you to edit Mix',
    'pal accepted your invite to Mix',
    'pal added 3 songs to Mix',
  ])
  expect(rows[0].text()).toContain('Accept')
  expect(rows[1].text()).not.toContain('Accept')
  expect(JSON.parse(rows[1].get('a').attributes('data-to'))).toEqual({ name: 'Playlist', params: { username: 'owner', id: 'p1' } })
  expect(rows[0].text()).toContain('just now')
})

it('shows unreadCount on the badge, clears it on open by marking everything read, and never polls', async () => {
  vi.useFakeTimers()
  const events = useEventsStore()
  events.notifications = firstPage([note('n1', 'INVITE_ACCEPTED')], 12)
  request.mockResolvedValue(null)
  await render()
  expect(wrapper.get('.badge').text()).toBe('9+')
  await wrapper.get('.bell').trigger('click')
  expect(wrapper.find('.badge').exists()).toBe(false)
  expect(request).toHaveBeenCalledWith('PUT', expect.stringContaining('/api/notifications/read-all'), undefined, expect.any(Object))

  window.dispatchEvent(new Event('focus'))
  await vi.advanceTimersByTimeAsync(5 * 60_000)
  expect(fetchAPI).not.toHaveBeenCalled()

  events.notifications = firstPage([note('n4', 'TRACKS_ADDED', { trackCount: 1 }), note('n1', 'INVITE_ACCEPTED', { isRead: true })], 1)
  await nextTick()
  expect(wrapper.get('.badge').text()).toBe('1')
  vi.useRealTimers()
})

it('loads older pages with the cursor, leaving out duplicates', async () => {
  useEventsStore().notifications = firstPage([note('n1', 'INVITE_ACCEPTED')], 0, 'c1')
  fetchAPI.mockResolvedValueOnce(firstPage([note('n1', 'INVITE_ACCEPTED'), note('n0', 'TRACKS_ADDED', { trackCount: 1 })], 0, null))
  await render()
  await wrapper.get('.bell').trigger('click')
  await wrapper.findAll('button').find((b) => b.text() === 'Show older').trigger('click')
  await flushPromises()
  expect(fetchAPI).toHaveBeenCalledWith(expect.stringContaining('/api/notifications?cursor=c1'), expect.any(Object))
  expect(wrapper.findAll('.item')).toHaveLength(2)
  expect(wrapper.findAll('button').some((b) => b.text() === 'Show older')).toBe(false)
})

it('accepts an invitation and opens its playlist, or declines and hides it', async () => {
  useEventsStore().notifications = firstPage([
    note('n1', 'PLAYLIST_INVITE', { permission: 'LISTENER' }),
    note('n2', 'PLAYLIST_INVITE', { permission: 'EDITOR', playlist: { ...playlist, playlistId: 'p2', playlistName: 'Road trip' } }),
  ], 0)
  request.mockResolvedValue({})
  await render()
  await wrapper.get('.bell').trigger('click')
  await wrapper.findAll('.item')[1].findAll('button').find((b) => b.text() === 'Decline').trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('DELETE', expect.stringContaining('/api/playlists/p2/invites'))
  expect(wrapper.findAll('.item')).toHaveLength(1)

  await wrapper.findAll('button').find((b) => b.text() === 'Accept').trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('POST', expect.stringContaining('/api/playlists/p1/invites/accept'))
  expect(push).toHaveBeenCalledWith({ name: 'Playlist', params: { username: 'owner', id: 'p1' } })
  expect(useNotificationStore().message).toBe('You joined Mix.')
})

it('describes when each notification happened', () => {
  const now = Date.parse('2026-10-23T12:00:00Z')
  expect(timeAgo('2026-10-23T11:59:30Z', now)).toBe('just now')
  expect(timeAgo('2026-10-23T11:55:00Z', now)).toBe('5 min ago')
  expect(timeAgo('2026-10-23T09:00:00Z', now)).toBe('3 h ago')
  expect(timeAgo('2026-10-21T12:00:00Z', now)).toBe('2 d ago')
})

it('drops deleted invitations from the bell, including cached older pages, on a live update', async () => {
  const events = useEventsStore()
  events.notifications = firstPage([note('n1', 'PLAYLIST_INVITE')], 1, 'c1')
  request.mockResolvedValue(null)
  fetchAPI.mockResolvedValueOnce(firstPage([note('n0', 'PLAYLIST_INVITE')], 0))
  await render()
  await wrapper.get('.bell').trigger('click')
  await wrapper.findAll('button').find((b) => b.text() === 'Show older').trigger('click')
  await flushPromises()
  expect(wrapper.findAll('.item')).toHaveLength(2)
  events.notifications = firstPage([], 0)
  await nextTick()
  expect(wrapper.findAll('.item')).toHaveLength(0)
  expect(wrapper.find('.badge').exists()).toBe(false)
})
