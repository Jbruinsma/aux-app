import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'
import { updateMusicPiece } from '@/utils/music.js'

const quiet = { notifyOnError: false }
const artistAlbumsUrl = (artistId) => `${API_BASE_URL}/api/artists/${encodeURIComponent(artistId)}/albums`
const albumUrl = (albumId) => `${API_BASE_URL}/api/albums/${encodeURIComponent(albumId)}`

export const ALBUM_NAME_MAX = 100

// [AlbumOverview]: { albumId, albumName, coverUrl, artistSummary, pieceCount, canEdit }, sorted by name, not paged.
// Albums with no song the caller can play are left out unless the caller can edit them
export async function fetchArtistAlbums(artistId) {
  return (await fetchAPI(artistAlbumsUrl(artistId), quiet)) ?? []
}

// { musicPieces: [MusicPieceOverview], nextCursor }: the songs the caller can play, most played first, 50 a page.
// 404 ALBUM_NOT_FOUND
export function fetchAlbumPieces(albumId, cursor = null) {
  const query = cursor ? `?${new URLSearchParams({ cursor })}` : ''
  return fetchAPI(`${albumUrl(albumId)}/music-pieces${query}`, quiet)
}

// Find or create: returns AlbumDetails. When the artist already has an album with that name (any case), that one comes
// back unchanged and the cover is ignored. The cover is required for a new album (400 INVALID_FIELD without it);
// 409 CONFLICT when someone made the same album at the same moment
export function findOrCreateAlbum(artistId, { albumName, coverImage = null }) {
  return request('POST', artistAlbumsUrl(artistId), albumForm({ albumName, coverImage }), quiet)
}

// Partial: a missing cover keeps it. A new cover changes it for every song that uses it, including other people's.
// Returns AlbumDetails. 409 ALBUM_NAME_TAKEN, 403 NOT_ALBUM_EDITOR, 404 ALBUM_NOT_FOUND
export function updateAlbum(albumId, { albumName, coverImage = null }) {
  return request('PUT', albumUrl(albumId), albumForm({ albumName, coverImage }), quiet)
}

// 204. Its songs become singles and keep the cover they show
export function deleteAlbum(albumId) {
  return request('DELETE', albumUrl(albumId), undefined, quiet)
}

// Puts one of the caller's own songs on an album (moving it off any other), or makes it a single with a null albumId.
// `useAlbumCover` true follows the album's cover; false keeps the image it shows now as its own. Returns the updated
// MusicPieceOverview
export function setPieceAlbum(musicPieceId, albumId, useAlbumCover = true) {
  if (!albumId) return updateMusicPiece(musicPieceId, { removeFromAlbum: true })
  return updateMusicPiece(musicPieceId, { albumId, useAlbumCover })
}

// Splits an artist's songs into albums and singles. `albumPieces` maps albumId to { items } of that album's loaded
// songs; a song with an `album` is never a single, even before its album's list has loaded
export function arrangeByAlbum(albums, albumPieces, pieces) {
  return {
    albums: albums.map((album) => ({ album, pieces: albumPieces[album.albumId]?.items ?? [] })),
    singles: pieces.filter((piece) => !piece.album),
  }
}

function albumForm({ albumName, coverImage }) {
  const form = new FormData()
  if (albumName !== undefined) form.append('albumName', albumName)
  if (coverImage) form.append('coverImage', coverImage)
  return form
}
