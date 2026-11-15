import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

const quiet = { notifyOnError: false }

// { musicPieces: [MusicPieceOverview], nextCursor }. First page of a playlist's play queue: without shuffle it runs in
// playlist order from `start` (earlier tracks aren't queued); with shuffle `start` plays first, then the rest in a new
// random order. 404 MUSIC_PIECE_NOT_FOUND (start not in it), 404 PLAYLIST_NOT_FOUND, 403 PLAYLIST_INVITE_PENDING
export function fetchQueue(playlistId, { start = null, shuffle = false } = {}) {
  const query = new URLSearchParams({ shuffle })
  if (start) query.set('start', start)
  return fetchAPI(`${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/play?${query}`, quiet)
}

// The next page: only the opaque cursor, sent back unchanged (adding start or shuffle is a 400)
export function fetchQueuePage(playlistId, cursor) {
  const query = new URLSearchParams({ cursor })
  return fetchAPI(`${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/play?${query}`, quiet)
}

// { url, expiresInSeconds, playToken }: a signed MP3 URL and the single-use token for reporting the listen.
// `playlistId` only when playing from a playlist. 404 MUSIC_PIECE_NOT_FOUND for someone else's private piece
export function fetchStream(musicPieceId, playlistId = null) {
  const query = playlistId ? `?${new URLSearchParams({ playlistId })}` : ''
  return fetchAPI(`${API_BASE_URL}/api/music-pieces/${encodeURIComponent(musicPieceId)}/stream${query}`, quiet)
}

// Reports a listen once the stream's minListenSeconds have played, once per playToken.
// 409 PLAY_OVERLAPS, 409 PLAY_ALREADY_RECORDED and 400 PLAY_TOO_SHORT are anti-abuse checks, not user errors
export const IGNORED_PLAY_ERRORS = ['PLAY_OVERLAPS', 'PLAY_ALREADY_RECORDED', 'PLAY_TOO_SHORT']

export function reportPlay(musicPieceId, playToken, listenDurationSeconds) {
  return request('POST', `${API_BASE_URL}/api/music-pieces/${encodeURIComponent(musicPieceId)}/plays`,
    { playToken, listenDurationSeconds }, quiet)
}

export const REPEAT_MODES = ['OFF', 'PLAYLIST', 'PIECE']

// Full replace: both fields are required. Kept across devices and read back from GET /api/users/me/last-playback
export function savePlayPreferences({ shuffleEnabled, repeatPreference }) {
  return request('PUT', `${API_BASE_URL}/api/users/me/play-preferences`, { shuffleEnabled, repeatPreference }, quiet)
}

// What the player is doing, for friends (`nowPlaying` on GET /api/users/me/friends). The server ignores private and
// unknown pieces, and `isPlaying: false` clears it
export function reportNowPlaying(musicPieceId, positionSeconds, isPlaying) {
  return request('PUT', `${API_BASE_URL}/api/users/me/now-playing`, { musicPieceId, positionSeconds, isPlaying }, quiet)
}

// The audio element fires several events for one change (a seek while playing sends seeked, then often playing), so
// reports wait a moment and only the last state is sent. `read()` returns { musicPieceId, positionSeconds, isPlaying },
// or null to send nothing. Fire and forget: a failed report never reaches the player
export function nowPlayingReporter(read, delay = 300) {
  let timer = null
  function send() {
    timer = null
    const state = read()
    if (!state?.musicPieceId) return
    reportNowPlaying(state.musicPieceId, Math.max(0, Math.floor(state.positionSeconds)), state.isPlaying).catch(() => {})
  }
  return {
    schedule() {
      clearTimeout(timer)
      timer = setTimeout(send, delay)
    },
    cancel() {
      clearTimeout(timer)
      timer = null
    },
  }
}
