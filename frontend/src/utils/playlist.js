import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

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

// Owner or editor only; anyone else gets 404 PLAYLIST_NOT_FOUND. Returns nothing (204). A 404 MUSIC_PIECE_NOT_FOUND
// means the song is already gone (e.g. removed in another tab), which is the state the caller wants, so treat it as
// success. Doesn't notify on errors: the caller says which song failed
export async function removePieceFromPlaylist(playlistId, musicPieceId) {
  const url = `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/pieces/${encodeURIComponent(musicPieceId)}`
  try {
    await request('DELETE', url, undefined, { notifyOnError: false })
  } catch (err) {
    if (err.code !== 'MUSIC_PIECE_NOT_FOUND') throw err
  }
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
  try {
    await request('DELETE', `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}`, undefined, { notifyOnError: false })
  } catch (err) {
    // Already deleted (including deletion in another tab).
    if (err.status !== 404) throw err
  }
}

// Takes the caller off a playlist they accepted an invite to (not the owner). Already gone, 404 MEMBERSHIP_NOT_FOUND (left in
// another tab, or removed by the owner), counts as done
export async function leavePlaylist(playlistId) {
  try {
    await request('DELETE', `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/membership`, undefined, { notifyOnError: false })
  } catch (err) {
    if (err.status !== 404) throw err
  }
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
