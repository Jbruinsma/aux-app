import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { useMusicStore } from './music.js'
import { useNotificationStore } from './notification.js'
import { useUserStore } from './user.js'
import BottomPlayer from '@/components/BottomPlayer.vue'

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  vi.spyOn(console, 'warn').mockImplementation(() => {})
  vi.spyOn(console, 'error').mockImplementation(() => {})
})
afterEach(() => {
  useNotificationStore().hide()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

it('restores the current LastPlayback metadata, paused and without audio until Play', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => ({ musicPieces: [], nextCursor: null }) })))
  const music = useMusicStore()
  await music.updateBottomPlayerAfterLogin({
    playlistId: 'p_123',
    musicPiece: { musicPieceId: 'm_123', name: 'Test song', coverUrl: '/uploads/cover.webp', artistSummary: { artistName: 'Test artist' } },
  })
  expect(music.currentMusicPiece).toMatchObject({ uuid: 'm_123', title: 'Test song', artist: 'Test artist', mp3File: null })
  expect(music.currentPlaylistUUID).toBe('p_123')
  expect(music.playlist).toHaveLength(1)
  expect(music.position).toBe(0)
  expect(music.isPlaying).toBe(false)
  const wrapper = mount(BottomPlayer)
  expect(wrapper.text()).toContain('Test song')
  expect(wrapper.get('button[aria-label="Play"]').attributes('disabled')).toBeUndefined()
  expect(wrapper.find('audio').exists()).toBe(false)
  wrapper.unmount()
})

it('handles absent history and a last track without a playlist or artist', async () => {
  const music = useMusicStore()
  await music.updateBottomPlayerAfterLogin(null)
  await music.updateBottomPlayerAfterLogin({ playlistId: null, musicPiece: { musicPieceId: 'm_123', name: 'Solo', coverUrl: null, artistSummary: null } })
  expect(music.currentPlaylistUUID).toBe('')
  expect(music.currentMusicPiece.artist).toBe('')
  expect(music.currentMusicPiece.cover).toBe('')
  expect(() => { music.toggleShuffle(); music.moveForward(); music.moveBackward() }).not.toThrow()
})

const piece = (n) => ({ musicPieceId: `m${n}`, name: `Song ${n}`, coverUrl: null, artistSummary: { artistName: 'Artist' } })
const stream = (n) => ({ url: `https://audio.example/m${n}?sig=1`, expiresInSeconds: 3600, playToken: `t${n}` })

// Answers GET /play and /stream like the backend. `pages` maps a cursor (or 'first') to a queue page
function backend(pages, overrides = {}) {
  const calls = []
  vi.stubGlobal('fetch', vi.fn(async (url) => {
    calls.push(url)
    const path = new URL(url, 'http://x')
    const answer = (body, status = 200) => ({ ok: status < 400, status, json: async () => body })
    for (const [match, respond] of Object.entries(overrides)) if (url.includes(match)) return respond(answer)
    const streamMatch = path.pathname.match(/music-pieces\/(m\d+)\/stream/)
    if (streamMatch) return answer(stream(streamMatch[1].slice(1)))
    const cursor = path.searchParams.get('cursor')
    return answer(pages[cursor ?? 'first'])
  }))
  return calls
}

it('plays a playlist from the server queue with a signed stream URL', async () => {
  const calls = backend({ first: { musicPieces: [piece(2), piece(3)], nextCursor: null } })
  const music = useMusicStore()
  expect(await music.playPlaylist('p1', { start: 'm2' })).toBe(true)
  expect(calls[0]).toContain('/api/playlists/p1/play?shuffle=false&start=m2')
  expect(calls[1]).toContain('/api/music-pieces/m2/stream?playlistId=p1')
  expect(music.currentMusicPiece).toMatchObject({ uuid: 'm2', title: 'Song 2', mp3File: stream(2).url, playToken: 't2' })
  expect(music.isPlaying).toBe(true)
})

it('loads the next queue page with only the cursor once a few songs are left, and keeps going', async () => {
  const calls = backend({
    first: { musicPieces: [1, 2, 3, 4].map(piece), nextCursor: 'c/1 x' },
    'c/1 x': { musicPieces: [5, 6].map(piece), nextCursor: null },
  })
  const music = useMusicStore()
  await music.playPlaylist('p1')
  await vi.waitFor(() => expect(music.playlist).toHaveLength(6))
  expect(calls.find((url) => url.includes('cursor='))).toMatch(/\/play\?cursor=c%2F1\+x$/)
  for (let i = 0; i < 5; i++) await music.moveForward()
  expect(music.currentMusicPiece.uuid).toBe('m6')
  await music.moveForward()
  expect(music.isPlaying).toBe(false)
})

it('shuffles on the server, keeping the current song playing at the top of the new queue', async () => {
  backend({ first: { musicPieces: [1, 2, 3].map(piece), nextCursor: null } })
  const music = useMusicStore()
  await music.playPlaylist('p1')
  await music.moveForward()
  const playing = music.currentMusicPiece
  backend({ first: { musicPieces: [2, 3, 1].map(piece), nextCursor: null } })
  await music.toggleShuffle()
  expect(music.shuffleOn).toBe(true)
  expect(music.currentMusicPiece).toBe(playing)
  expect(music.getCurrentPlaylistIndex()).toBe(0)
  expect(fetch.mock.calls[0][0]).toContain('/play?shuffle=true&start=m2')
})

it("uses the next song's prefetched URL instead of asking again", async () => {
  const calls = backend({ first: { musicPieces: [1, 2].map(piece), nextCursor: null } })
  const music = useMusicStore()
  await music.playPlaylist('p1')
  await music.prefetchNext()
  const streamCalls = () => calls.filter((url) => url.includes('/stream')).length
  expect(streamCalls()).toBe(2)
  await music.moveForward()
  expect(streamCalls()).toBe(2)
  expect(music.currentMusicPiece.mp3File).toBe(stream(2).url)
})

it('gets a fresh URL after an audio error and resumes where it stopped, but only retries once', async () => {
  backend({ first: { musicPieces: [piece(1)], nextCursor: null } })
  const music = useMusicStore()
  await music.playPlaylist('p1')
  music.position = 754
  backend({}, { '/stream': (answer) => answer({ url: 'https://audio.example/m1?sig=2', expiresInSeconds: 3600, playToken: 't1b' }) })
  await music.recoverStream()
  expect(music.currentMusicPiece).toMatchObject({ mp3File: 'https://audio.example/m1?sig=2', playToken: 't1b' })
  expect(music.seekOnLoad).toBe(754)
  await music.recoverStream()
  expect(music.isPlaying).toBe(false)
  expect(useNotificationStore().message).toBe('"Song 1" stopped playing. Try it again in a moment.')
})

it('explains why a playlist could not start', async () => {
  backend({}, { '/play': (answer) => answer({ errorDetails: { code: 'PLAYLIST_INVITE_PENDING' } }, 403) })
  const music = useMusicStore()
  expect(await music.playPlaylist('p1')).toBe(false)
  expect(useNotificationStore().message).toBe('Accept the invitation to play this playlist.')
})

const playsSent = () => fetch.mock.calls.filter(([url]) => url.includes('/plays'))

it('reports a listen once, after 30 seconds actually played, and stays quiet about anti-abuse rejections', async () => {
  backend({ first: { musicPieces: [1, 2].map(piece), nextCursor: null } },
    { '/plays': (answer) => answer({ errorDetails: { code: 'PLAY_OVERLAPS' } }, 409) })
  const music = useMusicStore()
  await music.playPlaylist('p1')
  music.duration = 200
  for (let i = 0; i < 116; i++) music.recordListening(0.25)
  expect(playsSent()).toHaveLength(0)
  for (let i = 0; i < 4; i++) music.recordListening(0.25)
  expect(playsSent()).toHaveLength(1)
  expect(JSON.parse(playsSent()[0][1].body)).toEqual({ playToken: 't1', listenDurationSeconds: 30 })
  music.recordListening(0.25)
  await music.moveForward()
  expect(playsSent()).toHaveLength(1)
  await vi.waitFor(() => expect(console.warn).not.toHaveBeenCalled())
  expect(useNotificationStore().visible).toBe(false)
})

it('counts a short song listened to the end, but not a skip before 30 seconds', async () => {
  backend({ first: { musicPieces: [1, 2, 3].map(piece), nextCursor: null } }, { '/plays': (answer) => answer({ message: 'ok' }) })
  const music = useMusicStore()
  await music.playPlaylist('p1')
  music.duration = 200
  music.recordListening(10)
  await music.moveForward()
  expect(playsSent()).toHaveLength(0)
  music.duration = 20
  for (let i = 0; i < 78; i++) music.recordListening(0.25)
  music.finishListening()
  expect(JSON.parse(playsSent()[0][1].body)).toEqual({ playToken: 't2', listenDurationSeconds: 20 })
})

it('gets a new token for each repeat of the same song', async () => {
  backend({ first: { musicPieces: [piece(1)], nextCursor: null } })
  const music = useMusicStore()
  await music.playPlaylist('p1')
  backend({}, { '/stream': (answer) => answer({ url: 'https://audio.example/m1?sig=2', expiresInSeconds: 3600, playToken: 't1-again' }) })
  await music.startRepeat()
  expect(music.currentMusicPiece.playToken).toBe('t1-again')
})

describe('resuming after login', () => {
  const lastPlayback = { musicPiece: piece(2), playlistId: 'p1' }
  beforeEach(() => useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token'))

  it('saves the song, position, shuffle and rest of the queue per account', async () => {
    backend({ first: { musicPieces: [2, 3].map(piece), nextCursor: 'c2' } })
    const music = useMusicStore()
    await music.playPlaylist('p1', { start: 'm2', shuffle: true })
    music.position = 95.6
    music.saveLastPlayback()
    expect(JSON.parse(localStorage.getItem('aux-playback-u1'))).toEqual({
      musicPieceId: 'm2', playlistId: 'p1', position: 95, playing: true, queue: [2, 3].map(piece), cursor: 'c2',
    })
  })

  it('uses the saved queue and position for the same song, then continues from there on Play', async () => {
    localStorage.setItem('aux-playback-u1', JSON.stringify({
      musicPieceId: 'm2', playlistId: 'p1', position: 95, playing: false, queue: [2, 7, 4].map(piece), cursor: 'c9',
    }))
    const calls = backend({})
    const music = useMusicStore()
    await music.restoreLastPlayback({ ...lastPlayback, playPreferences: { shuffleEnabled: true, repeatPreference: 'OFF' } })
    expect(calls).toHaveLength(0)
    expect(music.playlist.map((p) => p.musicPieceId)).toEqual(['m2', 'm7', 'm4'])
    expect(music.queueCursor).toBe('c9')
    expect(music.position).toBe(95)
    expect(music.shuffleOn).toBe(true)
    expect(music.isPlaying).toBe(false)

    await music.resume()
    expect(calls[0]).toContain('/api/music-pieces/m2/stream?playlistId=p1')
    expect(music.seekOnLoad).toBe(95)
    expect(music.isPlaying).toBe(true)
  })

  it('rebuilds the queue from the server when nothing saved matches, or queues the song alone without a playlist', async () => {
    localStorage.setItem('aux-playback-u1', JSON.stringify({ musicPieceId: 'm9', playlistId: 'p1', position: 40, playing: true, queue: [] }))
    const calls = backend({ first: { musicPieces: [2, 3].map(piece), nextCursor: null } })
    const music = useMusicStore()
    await music.restoreLastPlayback(lastPlayback)
    expect(calls[0]).toContain('/api/playlists/p1/play?shuffle=false&start=m2')
    expect(music.playlist).toHaveLength(2)
    expect(music.position).toBe(0)

    await music.restoreLastPlayback({ musicPiece: piece(5), playlistId: null })
    expect(music.playlist.map((p) => p.musicPieceId)).toEqual(['m5'])
    await music.resume()
    expect(fetch.mock.calls.at(-1)[0]).toMatch(/\/music-pieces\/m5\/stream$/)
  })

  it('keeps only volume on this device; shuffle and repeat come from the server after reset, even with no last song', async () => {
    backend({ first: { musicPieces: [1, 2].map(piece), nextCursor: null } }, { 'play-preferences': (answer) => answer({}) })
    const music = useMusicStore()
    await music.playPlaylist('p1', { shuffle: true })
    music.volume = 0.35
    await nextTick()
    expect(JSON.parse(localStorage.getItem('aux-player-settings-u1'))).toEqual({ volume: 0.35 })

    setActivePinia(createPinia())
    useUserStore().login({ userId: 'u1', username: 'me', onboardingStep: 'DONE' }, 'token')
    const calls = backend({ first: { musicPieces: [piece(9)], nextCursor: null } })
    const fresh = useMusicStore()
    expect(fresh.volume).toBe(0.35)
    expect(fresh.shuffleOn).toBe(false)
    await fresh.restoreLastPlayback({ musicPiece: piece(9), playlistId: 'p1', playPreferences: { shuffleEnabled: true, repeatPreference: 'PIECE' } })
    expect(fresh.shuffleOn).toBe(true)
    expect(fresh.repeatMode).toBe('PIECE')
    expect(calls[0]).toContain('shuffle=true')

    await fresh.restoreLastPlayback({ musicPiece: null, playlistId: null, playPreferences: { shuffleEnabled: false, repeatPreference: 'PLAYLIST' } })
    expect(fresh.shuffleOn).toBe(false)
    expect(fresh.repeatMode).toBe('PLAYLIST')
  })

  it('starts playing again after a reload if it was playing', async () => {
    localStorage.setItem('aux-playback-u1', JSON.stringify({ musicPieceId: 'm2', playlistId: 'p1', position: 12, playing: true, queue: [2, 3].map(piece), cursor: null }))
    const calls = backend({})
    const music = useMusicStore()
    await music.restoreLastPlayback(lastPlayback)
    await vi.waitFor(() => expect(music.isPlaying).toBe(true))
    expect(calls[0]).toContain('/music-pieces/m2/stream')
    expect(music.seekOnLoad).toBe(12)
  })
})

it('shows Play instead of a stuck Pause when the browser refuses to start audio', async () => {
  const blocked = Object.assign(new Error('blocked'), { name: 'NotAllowedError' })
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockRejectedValue(blocked)
  vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {})
  const music = useMusicStore()
  music.playlist = [piece(1)]
  music.currentMusicPiece = { uuid: 'm1', title: 'Song 1', cover: '', artist: '', mp3File: 'https://audio.example/m1', playToken: 't1', expiresAt: 0 }
  const wrapper = mount(BottomPlayer)
  await nextTick()
  music.isPlaying = true
  await vi.waitFor(() => expect(music.isPlaying).toBe(false))
  expect(wrapper.get('button[aria-label="Play"]').exists()).toBe(true)
  wrapper.unmount()
})

it('plays uploads as a queue of their own, streaming without a playlist', async () => {
  const calls = backend({})
  const music = useMusicStore()
  expect(await music.playPieces([1, 2, 3].map(piece), 1)).toBe(true)
  expect(calls[0]).toMatch(/\/music-pieces\/m2\/stream$/)
  expect(music.currentPlaylistUUID).toBe('')
  await music.moveForward()
  expect(music.currentMusicPiece.uuid).toBe('m3')
})

const preferencesSent = () => fetch.mock.calls
  .filter(([url]) => url.includes('/play-preferences'))
  .map(([, options]) => [options.method, JSON.parse(options.body)])

it.each(['recoverStream', 'prefetchNext', 'toggleShuffle', 'startRepeat', 'moveForward'])('stops a deleted playlist during %s and does not retry it', async (action) => {
  backend({ first: { musicPieces: [1, 2].map(piece), nextCursor: null } })
  const music = useMusicStore()
  await music.playPlaylist('p1')
  backend({}, {
    '/stream': (answer) => answer({ errorDetails: { code: 'PLAYLIST_NOT_FOUND' } }, 404),
    '/play': (answer) => answer({ errorDetails: { code: 'PLAYLIST_NOT_FOUND' } }, 404),
  })
  await music[action]()
  expect(music.isEmpty()).toBe(true)
  const count = fetch.mock.calls.length
  await music.recoverStream()
  await music.moveForward()
  expect(fetch.mock.calls).toHaveLength(count)
})

it('stops instead of retrying a deleted playlist queue page', async () => {
  backend({ first: { musicPieces: [1, 2].map(piece), nextCursor: 'c1' } }, {
    'cursor=': (answer) => answer({ errorDetails: { code: 'PLAYLIST_NOT_FOUND' } }, 404),
  })
  const music = useMusicStore()
  await music.playPlaylist('p1')
  await vi.waitFor(() => expect(music.isEmpty()).toBe(true))
  const count = fetch.mock.calls.length
  await music.moveForward()
  expect(fetch.mock.calls).toHaveLength(count)
})

it('cycles repeat OFF → PLAYLIST → PIECE → OFF, updating the player before the server answers', async () => {
  backend({}, { 'play-preferences': () => new Promise(() => {}) })
  const music = useMusicStore()
  expect(music.repeatMode).toBe('OFF')
  music.cycleRepeat()
  expect(music.repeatMode).toBe('PLAYLIST')
  music.cycleRepeat()
  expect(music.repeatMode).toBe('PIECE')
  music.cycleRepeat()
  expect(music.repeatMode).toBe('OFF')
  expect(preferencesSent()).toEqual([
    ['PUT', { shuffleEnabled: false, repeatPreference: 'PLAYLIST' }],
    ['PUT', { shuffleEnabled: false, repeatPreference: 'PIECE' }],
    ['PUT', { shuffleEnabled: false, repeatPreference: 'OFF' }],
  ])
})

it('saves shuffle when it changes, and not when it stays the same', async () => {
  backend({ first: { musicPieces: [1, 2].map(piece), nextCursor: null } }, { 'play-preferences': (answer) => answer({}) })
  const music = useMusicStore()
  await music.playPlaylist('p1')
  expect(preferencesSent()).toEqual([])
  await music.toggleShuffle()
  expect(music.shuffleOn).toBe(true)
  expect(preferencesSent()).toEqual([['PUT', { shuffleEnabled: true, repeatPreference: 'OFF' }]])
})

it('with repeat PLAYLIST, asks /play for the queue again at the end, keeping shuffle; a lone song plays again', async () => {
  const calls = backend({ first: { musicPieces: [1, 2].map(piece), nextCursor: null } }, { 'play-preferences': (answer) => answer({}) })
  const music = useMusicStore()
  music.applyPlayPreferences({ shuffleEnabled: true, repeatPreference: 'PLAYLIST' })
  await music.playPlaylist('p1', { shuffle: true })
  await music.moveForward()
  await music.moveForward()
  expect(calls.filter((url) => url.includes('/play?')).at(-1)).toContain('/api/playlists/p1/play?shuffle=true')
  expect(music.currentMusicPiece.uuid).toBe('m1')
  expect(music.isPlaying).toBe(true)

  await music.playPieces([piece(7)])
  await music.moveForward()
  expect(music.currentMusicPiece.uuid).toBe('m7')
  expect(calls.filter((url) => url.includes('/m7/stream'))).toHaveLength(2)
  expect(music.isPlaying).toBe(true)
})

it('labels the repeat button with its mode and moves to the next mode on click', async () => {
  backend({}, { 'play-preferences': (answer) => answer({}) })
  const music = useMusicStore()
  music.playlist = [piece(1)]
  music.currentMusicPiece = { uuid: 'm1', title: 'Song 1', cover: '', artist: '', mp3File: null, playToken: null, expiresAt: 0 }
  const wrapper = mount(BottomPlayer)
  const repeat = () => wrapper.get('button[aria-label^="Repeat"]')
  const labels = [repeat().attributes('aria-label')]
  for (let i = 0; i < 3; i++) {
    await repeat().trigger('click')
    labels.push(repeat().attributes('aria-label'))
  }
  expect(labels).toEqual(['Repeat: off', 'Repeat: playlist', 'Repeat: this song', 'Repeat: off'])
  wrapper.unmount()
})
