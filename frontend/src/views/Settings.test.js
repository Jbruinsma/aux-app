import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useUserStore } from '@/stores/user.js'
import { useNotificationStore } from '@/stores/notification.js'
import { deleteAccount, fetchSettings } from '@/utils/account.js'
import { request } from '@/utils/api.js'
import Settings from './Settings.vue'

const replace = vi.fn()
let tab
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { tab } }), useRouter: () => ({ push: vi.fn(), replace }) }))
vi.mock('@/router/index.js', () => ({ default: { push: vi.fn(), replace: vi.fn() } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))
vi.mock('@/utils/account.js', async (original) => ({
  ...(await original()),
  fetchSettings: vi.fn().mockResolvedValue({ email: 'me@example.com', username: 'me', profileDetails: {} }),
  deleteAccount: vi.fn(),
}))

let wrapper
beforeEach(() => {
  vi.useFakeTimers()
  tab = 'account'
  request.mockReset()
  setActivePinia(createPinia())
  useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
  replace.mockClear()
  deleteAccount.mockReset()
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  vi.useRealTimers()
})

// Opens the delete dialog and waits out the countdown
async function openDelete() {
  wrapper = mount(Settings, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
  const dialog = wrapper.get('dialog.confirm').element
  dialog.showModal = vi.fn()
  dialog.close = vi.fn()
  await wrapper.get('.delete-field button').trigger('click')
  await vi.advanceTimersByTimeAsync(6000)
  return dialog
}

const submit = () => wrapper.get('dialog.confirm .confirm-actions .btn.primary')

async function openFollowSettings(settings = {}) {
  tab = 'profile'
  fetchSettings.mockResolvedValueOnce({ email: 'me@example.com', username: 'me', profileDetails: {}, ...settings })
  wrapper = mount(Settings, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
  return wrapper.get('section[aria-labelledby="follows-heading"]')
}

it('shows follower privacy as unavailable until the server sends a setting', async () => {
  const section = await openFollowSettings()
  expect(section.text()).toContain('Follow-list privacy settings aren’t available yet.')
  expect(section.findAll('input[type="radio"]')).toHaveLength(0)
  expect(request).not.toHaveBeenCalled()
})

it('loads the server choice and saves a change through the visibility API', async () => {
  const section = await openFollowSettings({ followsVisibility: 'FRIENDS' })
  expect(section.get('input[value="FRIENDS"]').element.checked).toBe(true)
  request.mockResolvedValueOnce({ followsVisibility: 'NO_ONE' })
  await section.get('input[value="NO_ONE"]').setValue()
  await flushPromises()
  expect(request).toHaveBeenCalledWith('PUT', expect.stringContaining('/api/users/me/follows-visibility'),
    { followsVisibility: 'NO_ONE' }, { notifyOnError: false })
  expect(section.get('input[value="NO_ONE"]').element.checked).toBe(true)
  expect(useNotificationStore().message).toBe('Follower visibility saved.')
})

it('restores the last confirmed choice when the server does not confirm the save', async () => {
  const section = await openFollowSettings({ followsVisibility: 'FRIENDS' })
  request.mockResolvedValueOnce({})
  await section.get('input[value="NO_ONE"]').setValue()
  await flushPromises()
  expect(section.get('input[value="FRIENDS"]').element.checked).toBe(true)
  expect(section.get('[role="alert"]').text()).toContain("We couldn't save who can see your followers.")
  expect(useNotificationStore().message).not.toBe('Follower visibility saved.')
})

it('needs the countdown and the current password before deleting', async () => {
  wrapper = mount(Settings, { global: { stubs: { AppHeader: true, AppFooter: true, RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
  const dialog = wrapper.get('dialog.confirm').element
  dialog.showModal = vi.fn()
  await wrapper.get('.delete-field button').trigger('click')
  await wrapper.get('#delete-password').setValue('hunter22!')
  expect(submit().attributes('disabled')).toBeDefined()
  await vi.advanceTimersByTimeAsync(6000)
  expect(submit().attributes('disabled')).toBeUndefined()
  await wrapper.get('#delete-password').setValue('')
  expect(submit().attributes('disabled')).toBeDefined()
})

it('deletes with the current password, then logs out and goes to the landing page', async () => {
  deleteAccount.mockResolvedValue(null)
  const dialog = await openDelete()
  await wrapper.get('#delete-password').setValue('hunter22!')
  await wrapper.get('dialog.confirm form').trigger('submit')
  await flushPromises()
  expect(deleteAccount).toHaveBeenCalledWith('hunter22!')
  expect(dialog.close).toHaveBeenCalled()
  expect(useUserStore().loggedIn).toBe(false)
  expect(replace).toHaveBeenCalledWith({ name: 'Home' })
})

it('shows a wrong password on the field and stays logged in', async () => {
  deleteAccount.mockRejectedValue(Object.assign(new Error('Wrong password'), { status: 403, code: 'WRONG_PASSWORD' }))
  const dialog = await openDelete()
  const notify = vi.spyOn(useNotificationStore(), 'success')
  await wrapper.get('#delete-password').setValue('not-it-123')
  await wrapper.get('dialog.confirm form').trigger('submit')
  await flushPromises()
  expect(wrapper.get('#delete-password-error').text()).toBe("That isn't your current password. Try again.")
  expect(wrapper.get('#delete-password').attributes('aria-invalid')).toBe('true')
  expect(dialog.close).not.toHaveBeenCalled()
  expect(useUserStore().loggedIn).toBe(true)
  expect(notify).not.toHaveBeenCalled()
  await wrapper.get('#delete-password').setValue('another-try')
  expect(wrapper.find('#delete-password-error').exists()).toBe(false)
})

it('asks the user to wait when rate limited', async () => {
  deleteAccount.mockRejectedValue(Object.assign(new Error('Too many'), { status: 429, code: 'RATE_LIMITED' }))
  await openDelete()
  await wrapper.get('#delete-password').setValue('hunter22!')
  await wrapper.get('dialog.confirm form').trigger('submit')
  await flushPromises()
  expect(wrapper.get('dialog.confirm [role="alert"]').text()).toBe("You've tried too many times. Wait a minute, then try again.")
  expect(useUserStore().loggedIn).toBe(true)
})
