import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useNotificationStore } from '@/stores/notification.js'
import { deletePlaylist, setPlaylistSaved } from './playlist.js'

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

it.each([204, 404])('deletes through the real endpoint and accepts status %s without an error notice', async (status) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: status === 204, status,
    json: async () => ({ errorDetails: { code: 'PLAYLIST_NOT_FOUND' } }),
  }))
  await expect(deletePlaylist('p /1')).resolves.toBeUndefined()
  expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/api/playlists/p%20%2F1'), expect.objectContaining({ method: 'DELETE' }))
  expect(useNotificationStore().visible).toBe(false)
})

it('rejects a failed deletion for the dialog to report', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500, json: async () => ({}) }))
  await expect(deletePlaylist('p1')).rejects.toMatchObject({ status: 500 })
})
