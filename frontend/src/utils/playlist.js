import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

// Delete isn't on the backend yet. It's requested in BACKEND_REQUESTS.md and answers from mock data until then; flip
// this off once it ships, since the real call below already uses the requested shape
export const PLAYLIST_DELETE_MOCKED = true

const wait = () => new Promise((resolve) => setTimeout(resolve, 400))

// PlaylistOverview: { playlistId, playlistName, playlistCoverUrl, totalPieces, isPublic, playlistOwner, isSaved,
// musicPieces (first 50), nextCursor, editors, access: OWNER | EDITOR | LISTENER | null }.
// 403 PLAYLIST_INVITE_PENDING: the caller was invited to this private playlist and hasn't accepted yet
export function fetchPlaylist(username, playlistId, options) {
  return fetchAPI(`${API_BASE_URL}/api/playlists/${encodeURIComponent(username)}/${encodeURIComponent(playlistId)}`, options)
}

// { musicPieces, nextCursor }. The cursor is opaque: send back exactly what the last page gave
export function fetchPlaylistPieces(playlistId, cursor) {
  const query = new URLSearchParams({ cursor })
  return fetchAPI(`${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/pieces?${query}`)
}

// { invites: [{ playlistId, playlistName, playlistCoverUrl, permission: LISTENER | EDITOR, invitedBy }] }, newest first
export function fetchPendingInvites(options) {
  return fetchAPI(`${API_BASE_URL}/api/playlists/invites`, options)
}

export function acceptInvite(playlistId) {
  return request('POST', `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/invites/accept`)
}

export function declineInvite(playlistId) {
  return request('DELETE', `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/invites`)
}

// Returns the new isSaved. Unsaving a playlist that isn't saved is a 404 SAVED_PLAYLIST_NOT_FOUND on the backend; that's
// already the state the caller wants (e.g. unsaved in another tab), so treat it as success
export async function setPlaylistSaved(playlistId, saved) {
  const url = `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/save`
  try {
    const response = await request(saved ? 'PUT' : 'DELETE', url, undefined, {
      ignoredErrorCodes: saved ? [] : ['SAVED_PLAYLIST_NOT_FOUND'],
    })
    return response.isSaved
  } catch (err) {
    if (!saved && err.code === 'SAVED_PLAYLIST_NOT_FOUND') return false
    throw err
  }
}

// Returns nothing (204)
export async function deletePlaylist(playlistId) {
  if (!PLAYLIST_DELETE_MOCKED) return request('DELETE', `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}`)
  await wait()
  return null
}

// Members are owner-only: anyone else gets 404 PLAYLIST_NOT_FOUND.
// [{ user: { userId, username, pfpUrl }, permission: LISTENER | EDITOR, status: PENDING | ACCEPTED, invitedAt, respondedAt }]
export function fetchMembers(playlistId, options) {
  return fetchAPI(`${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/members`, options)
}

// 403 USER_ALREADY_INVITED, 400 INVALID_FIELD (inviting yourself), 404 USER_NOT_FOUND
export function inviteMember(playlistId, userId, permission, options) {
  return request('POST', `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/members`, { userId, permission }, options)
}

export function updateMember(playlistId, userId, permission) {
  return request('PUT', `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/members`, { userId, permission })
}

// Removes an accepted member, or cancels a pending invite
export function removeMember(playlistId, userId) {
  return request('DELETE', `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/members`, { userId })
}
