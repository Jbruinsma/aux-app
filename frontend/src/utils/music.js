import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

// The backend's limits for an upload (POST /api/music-pieces/create); checked here first to save a 25MB round trip
export const MAX_MP3_BYTES = 25 * 1024 * 1024
export const MIN_SECONDS = 5
export const MAX_SECONDS = 10 * 60
export const TITLE_MAX = 200
export const ARTIST_NAME_MAX = 100

// Messages keyed by the backend's error codes
export const UPLOAD_ERRORS = {
  UNSUPPORTED_AUDIO_TYPE: "That file isn't an MP3. Choose an MP3 file.",
  INVALID_AUDIO: "We couldn't read that MP3. It may be damaged; try another file.",
  AUDIO_TOO_SHORT: 'That song is too short. Songs need to be at least 5 seconds long.',
  AUDIO_TOO_LONG: 'That song is too long. Songs can be up to 10 minutes long.',
  REQUEST_FAILED: 'That file is too big. MP3s can be up to 25MB.',
  STORAGE_QUOTA_EXCEEDED: "This upload would go over your 2GB of storage. Delete some uploads first.",
  ARTIST_NOT_FOUND: "That artist doesn't exist anymore. Choose the artist again.",
}

// Returns a message explaining why the file can't be uploaded, or null, plus its length in seconds
export async function mp3Problem(file) {
  const isMp3 = file.type === 'audio/mpeg' || file.type === 'audio/mp3' || /\.mp3$/i.test(file.name)
  if (!isMp3) return { problem: UPLOAD_ERRORS.UNSUPPORTED_AUDIO_TYPE, seconds: null }
  if (file.size > MAX_MP3_BYTES) return { problem: UPLOAD_ERRORS.REQUEST_FAILED, seconds: null }
  const seconds = await audioLength(file)
  if (seconds === null) return { problem: UPLOAD_ERRORS.INVALID_AUDIO, seconds }
  if (seconds < MIN_SECONDS) return { problem: UPLOAD_ERRORS.AUDIO_TOO_SHORT, seconds }
  if (seconds > MAX_SECONDS) return { problem: UPLOAD_ERRORS.AUDIO_TOO_LONG, seconds }
  return { problem: null, seconds }
}

function audioLength(file) {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const audio = new Audio()
    const done = (seconds) => {
      URL.revokeObjectURL(url)
      resolve(seconds)
    }
    audio.preload = 'metadata'
    audio.onloadedmetadata = () => done(Number.isFinite(audio.duration) ? audio.duration : null)
    audio.onerror = () => done(null)
    audio.src = url
  })
}

// [ArtistSummary], at most 20, sorted by name
export function searchArtists(query) {
  return fetchAPI(`${API_BASE_URL}/api/artists?${new URLSearchParams({ q: query })}`, { notifyOnError: false })
}

// Returns the existing artist with this name (ignoring case), or a new one. 409 CONFLICT: created at the same moment
export function findOrCreateArtist(artistName) {
  return request('POST', `${API_BASE_URL}/api/artists`, { artistName }, { notifyOnError: false })
}

// Returns the new MusicPieceOverview. The server crops the cover; send the original
export function uploadMusicPiece({ name, artistId, isPublic, mp3File, coverImage }) {
  const form = new FormData()
  form.append('name', name)
  form.append('artistId', artistId)
  form.append('isPublic', isPublic)
  form.append('mp3File', mp3File)
  form.append('coverImage', coverImage)
  return request('POST', `${API_BASE_URL}/api/music-pieces/create`, form, { notifyOnError: false })
}

// Partial update of the caller's own piece: only the fields given are sent; the rest stay as they are. Returns the
// updated MusicPieceOverview. The server crops the cover; send the original
export function updateMusicPiece(musicPieceId, { name, artistId, isPublic, coverImage } = {}) {
  const form = new FormData()
  if (name !== undefined) form.append('name', name)
  if (artistId !== undefined) form.append('artistId', artistId)
  if (isPublic !== undefined) form.append('isPublic', isPublic)
  if (coverImage !== undefined) form.append('coverImage', coverImage)
  return request('PUT', `${API_BASE_URL}/api/music-pieces/${encodeURIComponent(musicPieceId)}`, form, { notifyOnError: false })
}

// { uploadedPiecesCount, uploadedMusicPieces: [MusicPieceOverview] }, newest first, public and private
export function fetchUploads() {
  return fetchAPI(`${API_BASE_URL}/api/music-pieces`, { notifyOnError: false })
}

// { results: [MusicPieceOverview], totalResults }; also matches artist names
export function searchMusic(query, { limit = 20, offset = 0 } = {}) {
  const params = new URLSearchParams({ category: 'MUSIC', query, limit, offset })
  return fetchAPI(`${API_BASE_URL}/api/core/search?${params}`, { notifyOnError: false })
}

// At most 100 ids per call. { addedCount, failedCount, totalPieces }. A piece is skipped (counted in failedCount, with no
// reason given) when unknown, already in the playlist, private where it can't go, or the playlist is at 10,000 tracks
export async function addPiecesToPlaylist(playlistId, musicPieceIds) {
  const url = `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/pieces`
  const total = { addedCount: 0, failedCount: 0, totalPieces: null }
  for (let i = 0; i < musicPieceIds.length; i += 100) {
    const result = await request('POST', url, { musicPieceIds: musicPieceIds.slice(i, i + 100) }, { notifyOnError: false })
    total.addedCount += result.addedCount
    total.failedCount += result.failedCount
    total.totalPieces = result.totalPieces
  }
  return total
}

export function additionMessage({ addedCount, failedCount }) {
  const added = addedCount === 1 ? 'Added 1 song.' : `Added ${addedCount} songs.`
  if (!failedCount) return added
  const skipped = failedCount === 1 ? "1 song couldn't be added" : `${failedCount} songs couldn't be added`
  return `${addedCount ? `${added} ` : ''}${skipped} (already in the playlist, private, or the playlist is full).`
}
