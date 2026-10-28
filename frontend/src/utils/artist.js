import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'
import { searchMusic } from '@/utils/music.js'

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

const SEARCH_PAGE = 50
const SEARCH_MAX_PAGES = 4

// [MusicPieceOverview] credited to the artist. There's no artist endpoint yet (BACKEND_REQUESTS.md §3), so this runs a
// music search for the artist's name, which also matches artist names, and keeps the pieces whose artistId is this
// one. Search shows public pieces plus the caller's own private ones, sorted by name. Stops after 200 results, which only
// matters for a name that's part of many other names
export async function fetchArtistPieces({ artistId, artistName }) {
  const pieces = []
  for (let page = 0; page < SEARCH_MAX_PAGES; page++) {
    const { results, totalResults } = await searchMusic(artistName, { limit: SEARCH_PAGE, offset: page * SEARCH_PAGE })
    pieces.push(...results.filter((piece) => piece.artistSummary?.artistId === artistId))
    if ((page + 1) * SEARCH_PAGE >= totalResults || !results.length) break
  }
  return pieces
}
