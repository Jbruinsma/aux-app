import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { useMusicStore } from './music.js'
import { useNotificationStore } from './notification.js'
import BottomPlayer from '@/components/BottomPlayer.vue'

beforeEach(() => setActivePinia(createPinia()))
afterEach(() => {
  useNotificationStore().hide()
  vi.unstubAllGlobals()
})

it('restores the current LastPlayback metadata without inventing a queue or audio', () => {
  const music = useMusicStore()
  music.updateBottomPlayerAfterLogin({
    playlistId: 'p_123',
    musicPiece: { musicPieceId: 'm_123', name: 'Test song', coverUrl: '/uploads/cover.webp', artistSummary: { artistName: 'Test artist' } },
  })
  expect(music.currentMusicPiece).toMatchObject({ uuid: 'm_123', title: 'Test song', artist: 'Test artist', mp3File: null })
  expect(music.currentPlaylistUUID).toBe('p_123')
  expect(music.playlist).toEqual([])
  expect(music.position).toBe(0)
  expect(music.isPlaying).toBe(false)
  const wrapper = mount(BottomPlayer)
  expect(wrapper.text()).toContain('Test song')
  expect(wrapper.get('button[aria-label="Play"]').attributes('disabled')).toBeDefined()
  expect(wrapper.find('audio').exists()).toBe(false)
  wrapper.unmount()
})

it('handles absent history and a last track without a playlist or artist', () => {
  const music = useMusicStore()
  expect(() => music.updateBottomPlayerAfterLogin(null)).not.toThrow()
  music.updateBottomPlayerAfterLogin({ playlistId: null, musicPiece: { musicPieceId: 'm_123', name: 'Solo', coverUrl: null, artistSummary: null } })
  expect(music.currentPlaylistUUID).toBe('')
  expect(music.currentMusicPiece.artist).toBe('')
  expect(music.currentMusicPiece.cover).toBe('')
  expect(() => { music.toggleShuffle(); music.moveForward(); music.moveBackward() }).not.toThrow()
})

it('never sends retired play, shuffle, or update-last-playback requests', async () => {
  const fetch = vi.fn()
  vi.stubGlobal('fetch', fetch)
  const music = useMusicStore()
  expect(await music.loadPlaylist('p_123', 'listener', 0)).toBe(false)
  expect(await music.loadPlaylist('p_123', 'listener', null)).toBe(false)
  expect(await music.saveLastPlayback('listener')).toBe(false)
  expect(fetch).not.toHaveBeenCalled()
  expect(useNotificationStore().message).toBe('Playback is temporarily unavailable.')
})
