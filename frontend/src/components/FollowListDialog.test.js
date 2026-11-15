import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI, request } from '@/utils/api.js'
import FollowListDialog from './FollowListDialog.vue'

vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const friend = (userId, username, pfpUrl) => ({ userId, username, pfpUrl, isOnline: false, nowPlaying: null, lastPlayed: null })

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  request.mockReset()
  fetchAPI.mockReset()
  fetchAPI.mockResolvedValue({ totalResults: 2, friends: [friend('f1', 'justin', '/uploads/pfp/j.webp'), friend('f2', 'nia', null)] })
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

it('shows your real friends, with their pictures and profile links, on both of your lists', async () => {
  await open('following')
  expect(fetchAPI.mock.calls[0][0]).toMatch(/\/api\/users\/me\/friends\?limit=50&offset=0$/)
  expect(wrapper.get('[aria-selected="true"]').text()).toContain('Following 8')
  const people = wrapper.findAll('.person')
  expect(people.map((person) => person.get('.name').text())).toEqual(['justin', 'nia'])
  expect(people[0].get('img').attributes('src')).toContain('/uploads/pfp/j.webp')
  expect(JSON.parse(people[0].get('a').attributes('data-to'))).toEqual({ name: 'Profile', params: { username: 'justin' } })
  expect(people.every((person) => person.find('.friend').exists())).toBe(true)
  expect(wrapper.get('.preview').text()).toContain('Only your friends')
  await wrapper.findAll('[role="tab"]')[0].trigger('click')
  await flushPromises()
  expect(wrapper.get('[aria-selected="true"]').text()).toContain('Followers 12')
  expect(wrapper.findAll('.person')).toHaveLength(2)
})

it("doesn't show made-up people on someone else's lists", async () => {
  await open('followers', 'pal')
  expect(fetchAPI).not.toHaveBeenCalled()
  expect(wrapper.findAll('.person')).toHaveLength(0)
  expect(wrapper.text()).toContain('Follower lists are coming soon')
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
