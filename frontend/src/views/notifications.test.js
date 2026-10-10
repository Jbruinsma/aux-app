import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI, postToAPI, request } from '@/utils/api.js'
import Settings from './Settings.vue'
import Login from './Login.vue'
import PlaylistDetail from './PlaylistDetail.vue'

const { route, push, replace } = vi.hoisted(() => ({
  route: { params: {}, query: {} }, push: vi.fn(), replace: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push, replace }) }))
vi.mock('@/router/index.js', () => ({ default: { push, replace } }))
vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  route.params = { username: 'listener', tab: 'profile', id: 'playlist' }
  useUserStore().login({ username: 'listener', onboardingStep: 'DONE' })
  fetchAPI.mockResolvedValue({ email: 'old@example.com', profileDetails: {} })
  request.mockResolvedValue({})
})
afterEach(() => {
  wrapper?.unmount()
  useNotificationStore().hide()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

async function render(component) {
  wrapper = mount(component, {
    global: { stubs: { AppHeader: true, AppFooter: true, ProfileCard: true, RouterLink: true, HeroLayout: { template: '<div><slot /></div>' } } },
  })
  await flushPromises()
  return wrapper
}

it('shows profile success only after the save resolves, with no inline duplicate', async () => {
  let finish
  request.mockReturnValue(new Promise(resolve => { finish = resolve }))
  await render(Settings)
  await wrapper.get('#display-name').setValue('Listener')
  await wrapper.get('form.details').trigger('submit')
  expect(useNotificationStore().visible).toBe(false)
  finish({ displayName: 'Listener' })
  await flushPromises()
  expect(useNotificationStore().message).toBe('Your details are saved.')
  expect(useNotificationStore().type).toBe('success')
  expect(wrapper.text()).not.toContain('Your details are saved.')
})

it('keeps validation feedback and never announces a rejected save as success', async () => {
  request.mockRejectedValue({ code: 'INVALID_FIELD', message: 'Choose a different name.' })
  await render(Settings)
  await wrapper.get('form.details').trigger('submit')
  await flushPromises()
  expect(wrapper.text()).toContain('Choose a different name.')
  expect(useNotificationStore().visible).toBe(false)
})

it('confirms both email code delivery and the completed email change', async () => {
  route.params.tab = 'account'
  await render(Settings)
  await wrapper.get('#new-email').setValue('new@example.com')
  await wrapper.get('#email-password').setValue('password123')
  await wrapper.findAll('form')[1].trigger('submit')
  await flushPromises()
  expect(useNotificationStore().message).toContain('verification code was sent')
  await wrapper.get('#email-code').setValue('123456')
  await wrapper.findAll('form')[1].trigger('submit')
  await flushPromises()
  expect(useNotificationStore().message).toBe('Your email address is now new@example.com.')
  expect(wrapper.find('#email-code').exists()).toBe(false)
})

it('confirms both password code delivery and the completed password change', async () => {
  route.params.tab = 'account'
  await render(Settings)
  await wrapper.get('#current-password').setValue('password123')
  await wrapper.get('#new-password').setValue('newpassword123')
  await wrapper.get('#confirm-password').setValue('newpassword123')
  await wrapper.findAll('form')[2].trigger('submit')
  await flushPromises()
  expect(useNotificationStore().message).toContain('verification code was sent')
  await wrapper.get('#password-code').setValue('123456')
  await wrapper.findAll('form')[2].trigger('submit')
  await flushPromises()
  expect(useNotificationStore().message).toBe('Your password is changed.')
})

it('uses a neutral notice for mocked account deletion and keeps the session', async () => {
  route.params.tab = 'account'
  vi.useFakeTimers()
  await render(Settings)
  const dialog = wrapper.get('dialog').element
  dialog.showModal = vi.fn()
  dialog.close = vi.fn()
  await wrapper.findAll('button').find(b => b.text() === 'Delete account').trigger('click')
  await vi.advanceTimersByTimeAsync(6000)
  await wrapper.get('dialog .btn.primary').trigger('click')
  await vi.advanceTimersByTimeAsync(400)
  expect(useNotificationStore().type).toBe('neutral')
  expect(useNotificationStore().message).toContain("account wasn't deleted")
  expect(useUserStore().loggedIn).toBe(true)
})

it('keeps login success in the global store when the login view unmounts', async () => {
  postToAPI.mockResolvedValue({ user: { username: 'listener' }, token: 'token' })
  await render(Login)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(push).toHaveBeenCalledWith('/dashboard')
  wrapper.unmount()
  wrapper = null
  expect(useNotificationStore().message).toBe('You are logged in.')
  expect(useNotificationStore().visible).toBe(true)
})

it('confirms saving and unsaving a playlist after each completed request', async () => {
  fetchAPI.mockResolvedValue({ playlistId: 'playlist', playlistName: 'Favorites', playlistOwner: { username: 'someone' }, isSaved: false, musicPieces: [] })
  request.mockResolvedValueOnce({ isSaved: true }).mockResolvedValueOnce({ isSaved: false })
  await render(PlaylistDetail)
  await wrapper.get('button[aria-pressed]').trigger('click')
  await flushPromises()
  expect(useNotificationStore().message).toBe('Playlist saved to your library.')
  await wrapper.get('button[aria-pressed]').trigger('click')
  await flushPromises()
  expect(useNotificationStore().message).toBe('Playlist removed from your library.')
})
