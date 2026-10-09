import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useNotificationStore } from '@/stores/notification.js'
import { setPlaylistSaved } from './playlist.js'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.spyOn(console, 'error').mockImplementation(() => {})
})
afterEach(() => {
  useNotificationStore().hide()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

it('treats an already unsaved playlist as success without a global error', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: false, status: 404,
    json: async () => ({ errorDetails: { code: 'SAVED_PLAYLIST_NOT_FOUND', message: 'Not saved' } }),
  }))
  expect(await setPlaylistSaved('playlist', false)).toBe(false)
  expect(useNotificationStore().visible).toBe(false)
})

it('still reports and rejects unexpected failures', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: false, status: 403,
    json: async () => ({ errorDetails: { code: 'FORBIDDEN', message: 'Access denied' } }),
  }))
  await expect(setPlaylistSaved('playlist', false)).rejects.toThrow('Access denied')
  expect(useNotificationStore().type).toBe('error')
  expect(useNotificationStore().message).toBe('Access denied')
})
