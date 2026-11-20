import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI, request } from '@/utils/api.js'
import FollowListDialog from './FollowListDialog.vue'

vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const person = (userId, username, pfpUrl, isFollowing = true, followsMe = true) => ({ userId, username, pfpUrl, isFollowing, followsMe })

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  request.mockReset()
  fetchAPI.mockReset()
  fetchAPI.mockResolvedValue({ users: [person('f1', 'justin', '/uploads/pfp/j.webp'), person('f2', 'nia', null, false, true)], nextCursor: null })
  HTMLDialogElement.prototype.showModal = vi.fn()
  HTMLDialogElement.prototype.close = vi.fn(function close() { this.dispatchEvent(new Event('close')) })
})
afterEach(() => wrapper?.unmount())

async function open(which = 'followers', username = 'me') {
  wrapper = mount(FollowListDialog, {
    props: { username, counts: { followers: '12', following: '8' } },
    global: { stubs: { RouterLink: { props: ['to'], template: '<a :data-to="JSON.stringify(to)"><slot /></a>' } } },
  })
  wrapper.vm.open(which)
  await flushPromises()
}

it('lists followers and following with pictures and profile links, and marks friends', async () => {
  await open('following', 'pal')
  expect(fetchAPI.mock.calls[0][0]).toMatch(/\/api\/users\/pal\/following$/)
  expect(wrapper.get('[aria-selected="true"]').text()).toContain('Following 8')
  const people = wrapper.findAll('.person')
  expect(people.map((p) => p.get('.name').text())).toEqual(['justin', 'nia'])
  expect(people[0].get('img').attributes('src')).toContain('/uploads/pfp/j.webp')
  expect(JSON.parse(people[0].get('a').attributes('data-to'))).toEqual({ name: 'Profile', params: { username: 'justin' } })
  expect(people[0].find('.friend').exists()).toBe(true)
  expect(people[1].find('.friend').exists()).toBe(false)
  expect(people[1].get('button').text()).toBe('Follow')
  await wrapper.findAll('[role="tab"]')[0].trigger('click')
  await flushPromises()
  expect(fetchAPI.mock.calls[1][0]).toMatch(/\/api\/users\/pal\/followers$/)
  expect(wrapper.get('[aria-selected="true"]').text()).toContain('Followers 12')
})

it('loads the next page with the cursor', async () => {
  fetchAPI.mockResolvedValueOnce({ users: [person('f1', 'justin', null)], nextCursor: 'abc' })
  fetchAPI.mockResolvedValueOnce({ users: [person('f2', 'nia', null)], nextCursor: null })
  await open('followers', 'pal')
  await wrapper.get('button.more').trigger('click')
  await flushPromises()
  expect(fetchAPI.mock.calls[1][0]).toMatch(/\/api\/users\/pal\/followers\?cursor=abc$/)
  expect(wrapper.findAll('.person').map((p) => p.get('.name').text())).toEqual(['justin', 'nia'])
  expect(wrapper.find('button.more').exists()).toBe(false)
})

it('says the list is private on 403 FOLLOWS_HIDDEN', async () => {
  fetchAPI.mockRejectedValue(Object.assign(new Error('hidden'), { code: 'FOLLOWS_HIDDEN' }))
  await open('followers', 'pal')
  expect(wrapper.text()).toContain('@pal keeps their followers private')
})

it('shows an empty list', async () => {
  fetchAPI.mockResolvedValue({ users: [], nextCursor: null })
  await open('following', 'pal')
  expect(wrapper.text()).toContain("@pal doesn't follow anyone yet")
})

it('unfollows a friend from the list and tells the profile once the dialog closes', async () => {
  request.mockResolvedValue(null)
  await open()
  const target = wrapper.findAll('.person')[0]
  await target.get('button').trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('DELETE', expect.stringMatching(/\/api\/users\/justin\/follow$/), undefined, { notifyOnError: false })
  expect(target.get('button').text()).toBe('Follow')
  expect(target.find('.friend').exists()).toBe(false)
  await wrapper.get('button[aria-label="Close"]').trigger('click')
  expect(wrapper.emitted('changed')).toHaveLength(1)
})
