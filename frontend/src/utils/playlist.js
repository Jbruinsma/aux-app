import { fetchAPI, request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

function shuffleArray(array) {
  let currentIndex = array.length;
  while (currentIndex > 1) {
    const randomIndex = Math.floor(Math.random() * currentIndex);
    currentIndex--;
    const temp = array[currentIndex];
    array[currentIndex] = array[randomIndex];
    array[randomIndex] = temp;
  }
}

export function shufflePlaylist(playlist, startIndex = 0){
  const selectedPiece = playlist[startIndex];
  const remainingPieces = playlist.slice(0, startIndex).concat(playlist.slice(startIndex + 1));
  shuffleArray(remainingPieces);
  return [selectedPiece, ...remainingPieces]
}

// Delete isn't on the backend yet. It's requested in BACKEND_REQUESTS.md and answers from mock data until then; flip
// this off once it ships, since the real call below already uses the requested shape
export const PLAYLIST_DELETE_MOCKED = true

const wait = () => new Promise((resolve) => setTimeout(resolve, 400))

// PlaylistOverview: { playlistId, playlistName, playlistCoverUrl, totalPieces, playlistOwner, isSaved, musicPieces }
export function fetchPlaylist(username, playlistId) {
  return fetchAPI(`${API_BASE_URL}/api/playlists/${encodeURIComponent(username)}/${encodeURIComponent(playlistId)}`)
}

// Returns the new isSaved. Unsaving a playlist that isn't saved is a 404 SAVED_PLAYLIST_NOT_FOUND on the backend; that's
// already the state the caller wants (e.g. unsaved in another tab), so treat it as success
export async function setPlaylistSaved(playlistId, saved) {
  const url = `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/save`
  try {
    const response = await request(saved ? 'PUT' : 'DELETE', url)
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
