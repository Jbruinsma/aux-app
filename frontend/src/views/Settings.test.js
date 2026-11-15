import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useUserStore } from '@/stores/user.js'
import { useNotificationStore } from '@/stores/notification.js'
import { deleteAccount } from '@/utils/account.js'
import Settings from './Settings.vue'

const replace = vi.fn()
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { tab: 'account' } }), useRouter: () => ({ push: vi.fn(), replace }) }))
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

const submit = () => wrapper.get('dialog.confirm button[type="submit"]')

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
  expect(wrapper.get('dialog.confirm [role="alert"]').text()).toBe('Too many tries. Wait a minute, then try again.')
  expect(useUserStore().loggedIn).toBe(true)
})
