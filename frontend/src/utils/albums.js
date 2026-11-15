import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

const quiet = { notifyOnError: false }
const artistAlbumsUrl = (artistId) => `${API_BASE_URL}/api/artists/${encodeURIComponent(artistId)}/albums`
const albumUrl = (albumId) => `${API_BASE_URL}/api/albums/${encodeURIComponent(albumId)}`

// Albums aren't in the API yet (BACKEND_REQUESTS.md, "Albums"). While this is true they're kept in this browser, with
// the cover shrunk to a data URL, and only the artist page shows the album cover on its songs; set it to false once
// the album endpoints exist
export const ALBUMS_MOCKED = true

export const ALBUM_NAME_MAX = 100

// Album: { albumId, name, coverUrl, canEdit, tracks: [{ musicPiece: MusicPieceOverview, useAlbumCover }] }, in the
// order they were made. A piece is on at most one album; the artist's other songs are singles
export async function fetchArtistAlbums(artistId, userId) {
  if (!ALBUMS_MOCKED) return (await fetchAPI(artistAlbumsUrl(artistId), quiet))?.albums ?? []
  return readStored(artistId).map((album) => ({ ...album, canEdit: Boolean(userId) && album.createdBy === userId }))
}

// { name, coverImage (File, required to create), tracks: [{ musicPiece, useAlbumCover }] }; returns the Album.
// 409 ALREADY_ON_ALBUM when a track is on another album
export async function createAlbum(artistId, { name, coverImage, tracks }, userId) {
  if (!ALBUMS_MOCKED) return request('POST', artistAlbumsUrl(artistId), albumForm({ name, coverImage, tracks }), quiet)
  const albums = readStored(artistId)
  checkFree(albums, tracks, null)
  const album = {
    albumId: `local-${Date.now().toString(36)}`,
    name,
    coverUrl: await shrinkCover(coverImage),
    createdBy: userId,
    tracks: tracks.map(storedTrack),
  }
  writeStored(artistId, [...albums, album])
  return { ...album, canEdit: true }
}

// Partial: a missing coverImage keeps the cover. Returns the Album
export async function updateAlbum(artistId, albumId, { name, coverImage, tracks }) {
  if (!ALBUMS_MOCKED) return request('PUT', albumUrl(albumId), albumForm({ name, coverImage, tracks }), quiet)
  const albums = readStored(artistId)
  const album = albums.find((item) => item.albumId === albumId)
  if (!album) throw Object.assign(new Error('Album not found'), { status: 404, code: 'ALBUM_NOT_FOUND' })
  checkFree(albums, tracks, albumId)
  const updated = { ...album, name, tracks: tracks.map(storedTrack) }
  if (coverImage) updated.coverUrl = await shrinkCover(coverImage)
  writeStored(artistId, albums.map((item) => (item.albumId === albumId ? updated : item)))
  return { ...updated, canEdit: true }
}

// Its songs go back to being singles
export async function deleteAlbum(artistId, albumId) {
  if (!ALBUMS_MOCKED) return request('DELETE', albumUrl(albumId), undefined, quiet)
  writeStored(artistId, readStored(artistId).filter((item) => item.albumId !== albumId))
  return null
}

// Puts a just-uploaded piece at the end of an album. Once the API has albums the upload sends `albumId` itself
// (BACKEND_REQUESTS.md, "Albums"), so this is only needed while ALBUMS_MOCKED
export async function addToAlbum(artistId, albumId, musicPiece, useAlbumCover = true) {
  const albums = readStored(artistId)
  const album = albums.find((item) => item.albumId === albumId)
  if (!album) throw Object.assign(new Error('Album not found'), { status: 404, code: 'ALBUM_NOT_FOUND' })
  checkFree(albums, [{ musicPiece }], albumId)
  const tracks = [...album.tracks.filter((track) => track.musicPiece.musicPieceId !== musicPiece.musicPieceId), storedTrack({ musicPiece, useAlbumCover })]
  writeStored(artistId, albums.map((item) => (item.albumId === albumId ? { ...album, tracks } : item)))
}

// The album's cover as a File, to send as an upload's coverImage while ALBUMS_MOCKED (the upload needs one)
export async function albumCoverFile(album) {
  const blob = await (await fetch(album.coverUrl)).blob()
  const type = ['image/jpeg', 'image/png'].includes(blob.type) ? blob.type : 'image/jpeg'
  return new File([blob], `${album.name}.${type === 'image/png' ? 'png' : 'jpg'}`, { type })
}

// The cover a track shows: the album's when it uses it, its own otherwise
export function trackCover(album, track) {
  return track.useAlbumCover ? album.coverUrl : track.musicPiece.coverUrl
}

// Splits the artist's loaded songs into albums and singles. An album's own track list wins over the loaded copy only
// for songs that aren't loaded yet (they're past the first page), so loaded songs stay current
export function arrangeByAlbum(albums, pieces) {
  const loaded = new Map(pieces.map((piece) => [piece.musicPieceId, piece]))
  const onAlbum = new Set()
  const sections = albums.map((album) => {
    const tracks = album.tracks.map((track) => {
      onAlbum.add(track.musicPiece.musicPieceId)
      const musicPiece = loaded.get(track.musicPiece.musicPieceId) ?? track.musicPiece
      return { ...track, musicPiece }
    })
    const shown = tracks.map((track) => ({ ...track.musicPiece, coverUrl: trackCover(album, track) }))
    return { album, tracks, pieces: shown }
  })
  return { albums: sections, singles: pieces.filter((piece) => !onAlbum.has(piece.musicPieceId)) }
}

function albumForm({ name, coverImage, tracks }) {
  const form = new FormData()
  form.append('name', name)
  if (coverImage) form.append('coverImage', coverImage)
  for (const track of tracks) {
    form.append('musicPieceIds', track.musicPiece.musicPieceId)
    if (!track.useAlbumCover) form.append('ownCoverIds', track.musicPiece.musicPieceId)
  }
  return form
}

const storageKey = (artistId) => `aux-albums-${artistId}`

function readStored(artistId) {
  try {
    const albums = JSON.parse(localStorage.getItem(storageKey(artistId)) ?? '[]')
    return Array.isArray(albums) ? albums : []
  } catch {
    return []
  }
}

function writeStored(artistId, albums) {
  try {
    localStorage.setItem(storageKey(artistId), JSON.stringify(albums))
  } catch {
    throw Object.assign(new Error('Storage full'), { code: 'STORAGE_FULL' })
  }
}

// Only what the row needs, so the stored copy stays small
function storedTrack({ musicPiece, useAlbumCover }) {
  const { musicPieceId, name, coverUrl, artistSummary, uploader, isPublic, isAvailable } = musicPiece
  return { musicPiece: { musicPieceId, name, coverUrl, artistSummary, uploader, isPublic, isAvailable }, useAlbumCover: useAlbumCover !== false }
}

// A piece is on at most one album: one already on another (not `albumId`) is refused, not moved
function checkFree(albums, tracks, albumId) {
  const wanted = new Set(tracks.map((track) => track.musicPiece.musicPieceId))
  const clash = albums.some((album) => album.albumId !== albumId && album.tracks.some((track) => wanted.has(track.musicPiece.musicPieceId)))
  if (clash) throw Object.assign(new Error('Already on an album'), { status: 409, code: 'ALREADY_ON_ALBUM' })
}

const COVER_SIDE = 400

// A square JPEG data URL, center-cropped, small enough for localStorage. Falls back to the file as it is when the
// browser can't draw it (tests)
async function shrinkCover(file) {
  try {
    const bitmap = await createImageBitmap(file)
    const side = Math.min(bitmap.width, bitmap.height)
    const canvas = document.createElement('canvas')
    canvas.width = COVER_SIDE
    canvas.height = COVER_SIDE
    canvas.getContext('2d').drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, COVER_SIDE, COVER_SIDE)
    return canvas.toDataURL('image/jpeg', 0.85)
  } catch {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result)
      reader.onerror = () => reject(reader.error)
      reader.readAsDataURL(file)
    })
  }
}
