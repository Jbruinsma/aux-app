import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { useNotificationStore } from '@/stores/notification.js'
import { request } from '@/utils/api.js'
import EditPieceDialog from './EditPieceDialog.vue'

vi.mock('@/utils/api.js', () => ({ fetchAPI: vi.fn(), postToAPI: vi.fn(), request: vi.fn() }))

const piece = (overrides) => ({
  musicPieceId: 'm1', name: 'Song', coverUrl: null, artistSummary: { artistId: 'a1', artistName: 'Artist', artistPfpUrl: null },
  uploader: { userId: 'u1', pfpUrl: null, username: 'me' }, isAvailable: true, ...overrides,
})

let wrapper
beforeEach(() => {
  setActivePinia(createPinia())
  request.mockReset()
  HTMLDialogElement.prototype.showModal = vi.fn()
  HTMLDialogElement.prototype.close = vi.fn()
})
afterEach(() => {
  wrapper?.unmount()
  useNotificationStore().hide()
})

async function open(target) {
  wrapper = mount(EditPieceDialog)
  wrapper.vm.open(target)
  await flushPromises()
}

const sent = () => Object.fromEntries(request.mock.calls[0][2].entries())

it('sends only the fields that changed', async () => {
  request.mockResolvedValue(piece({ name: 'New name' }))
  await open(piece())
  const save = wrapper.get('button[type="submit"]')
  expect(save.attributes('disabled')).toBeDefined()
  await wrapper.get('#edit-title').setValue('New name')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(request.mock.calls[0][0]).toBe('PUT')
  expect(request.mock.calls[0][1]).toContain('/api/music-pieces/m1')
  expect(sent()).toEqual({ name: 'New name' })
  expect(useNotificationStore().message).toBe('Saved changes to “New name”.')
  expect(HTMLDialogElement.prototype.close).toHaveBeenCalled()
  expect(wrapper.emitted('saved')[0][0].name).toBe('New name')
})

it("keeps the visibility unless it's picked, and warns before making a piece private", async () => {
  request.mockResolvedValue(piece())
  await open(piece())
  expect(wrapper.text()).toContain('Keep as it is')
  expect(wrapper.find('.warning').exists()).toBe(false)
  await wrapper.findAll('input[type="radio"]')[2].setValue(true)
  expect(wrapper.get('.warning').text()).toContain('unavailable on public playlists')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(sent()).toEqual({ isPublic: 'false' })
})

it('starts on the current visibility when the piece has one', async () => {
  await open(piece({ isPublic: false }))
  expect(wrapper.text()).not.toContain('Keep as it is')
  expect(wrapper.findAll('input[type="radio"]')[1].element.checked).toBe(true)
  expect(wrapper.find('.warning').exists()).toBe(false)
})

it('stays open and explains a server rejection', async () => {
  request.mockRejectedValue({ status: 400, code: 'INVALID_FIELD' })
  await open(piece())
  await wrapper.get('#edit-title').setValue('x')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toContain('Give the song a title')
  expect(HTMLDialogElement.prototype.close).not.toHaveBeenCalled()
})
