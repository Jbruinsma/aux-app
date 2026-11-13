import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

const artistUrl = (artistId) => `${API_BASE_URL}/api/artists/${encodeURIComponent(artistId)}`

// ArtistDetails: { artistId, artistName, artistPfpUrl, artistBannerUrl, isFavorite, canEdit }. Public; with no token
// isFavorite and canEdit are false. 404 ARTIST_NOT_FOUND
export function fetchArtist(artistId) {
  return fetchAPI(artistUrl(artistId), { notifyOnError: false })
}

// 204 either way; favoriting twice or unfavoriting a non-favorite does nothing
export function setArtistFavorite(artistId, favorite) {
  return request(favorite ? 'PUT' : 'DELETE', `${artistUrl(artistId)}/favorite`, undefined, { notifyOnError: false })
}

// [ArtistSummary], newest favorite first. Public. 404 USER_NOT_FOUND
export function fetchFavoriteArtists(username) {
  return fetchAPI(`${API_BASE_URL}/api/artists/favorites/${encodeURIComponent(username)}`, { notifyOnError: false })
}

// Creator only (403 NOT_ARTIST_CREATOR). Same rules as the user's profile picture. Returns { profilePictureUrl }
export function uploadArtistPicture(artistId, file) {
  const form = new FormData()
  form.append('file', file)
  return request('PUT', `${artistUrl(artistId)}/pfp`, form, { notifyOnError: false })
}

// Creator only. The server crops, so this sends the original file plus the visible area in pixels of the original
// image, as the user banner does. Returns { bannerUrl }
export function uploadArtistBanner(artistId, file, rect) {
  const query = new URLSearchParams({ cropX: rect.x, cropY: rect.y, cropWidth: rect.width, cropHeight: rect.height })
  const form = new FormData()
  form.append('file', file)
  return request('PUT', `${artistUrl(artistId)}/banner?${query}`, form, { notifyOnError: false })
}

// { musicPieces: [MusicPieceOverview], nextCursor }: pieces credited to the artist that the caller can play on their own
// (every public one, plus the caller's own private ones), most played first, 50 per page. Public; play them with
// /stream and no playlistId
export function fetchArtistPieces(artistId, cursor = null) {
  const query = cursor ? `?${new URLSearchParams({ cursor })}` : ''
  return fetchAPI(`${artistUrl(artistId)}/music-pieces${query}`, { notifyOnError: false })
}

// Listing every artist isn't in the API yet (BACKEND_REQUESTS.md §7): GET /api/artists needs `q` and returns at most 20.
// While this is true the Artists tab only searches and shows favorites; set it to false once the paged list exists
export const ARTIST_DIRECTORY_MOCKED = true

// { artists: [ArtistSummary], nextCursor }: every artist, 50 per page
export function fetchAllArtists(cursor = null) {
  const query = cursor ? `?${new URLSearchParams({ cursor })}` : ''
  return fetchAPI(`${API_BASE_URL}/api/artists/all${query}`, { notifyOnError: false })
}
