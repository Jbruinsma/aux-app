import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useUserStore } from '@/stores/user.js'
import { request } from '@/utils/api.js'
import FollowListDialog from './FollowListDialog.vue'

vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  request.mockReset()
  HTMLDialogElement.prototype.showModal = vi.fn()
  HTMLDialogElement.prototype.close = vi.fn(function close() { this.dispatchEvent(new Event('close')) })
})
afterEach(() => wrapper?.unmount())

async function open(which = 'followers') {
  wrapper = mount(FollowListDialog, {
    props: { username: 'pal', counts: { followers: '12', following: '8' } },
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
  })
  wrapper.vm.open(which)
  await flushPromises()
}

it('lists people with a Friends badge for mutual follows, and switches tabs', async () => {
  await open('following')
  expect(wrapper.get('[aria-selected="true"]').text()).toContain('Following 8')
  const people = wrapper.findAll('.person')
  expect(people.length).toBeGreaterThan(0)
  for (const person of people) {
    const button = person.get('button')
    const friend = person.find('.friend').exists()
    // Sample people: friends are the ones both following and followed
    if (friend) expect(button.text()).toBe('Following')
  }
  await wrapper.findAll('[role="tab"]')[0].trigger('click')
  expect(wrapper.get('[aria-selected="true"]').text()).toContain('Followers 12')
})

it('follows someone from the list and tells the profile once the dialog closes', async () => {
  request.mockResolvedValue(null)
  await open()
  const target = wrapper.findAll('.person').find((person) => person.get('button').text() === 'Follow')
  await target.get('button').trigger('click')
  await flushPromises()
  expect(request).toHaveBeenCalledWith('PUT', expect.stringMatching(/\/api\/users\/\w+\/follow$/), undefined, { notifyOnError: false })
  expect(target.get('button').text()).toBe('Following')
  expect(target.find('.friend').exists()).toBe(true)
  await wrapper.get('button[aria-label="Close"]').trigger('click')
  expect(wrapper.emitted('changed')).toHaveLength(1)
})
