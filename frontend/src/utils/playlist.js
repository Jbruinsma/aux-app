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

// Only GET exists on the backend. Save, unsave and delete are requested in BACKEND_REQUESTS.md and answer from mock
// data until then; flip this off once they ship, since the real calls below already use the requested shapes
export const PLAYLIST_ACTIONS_MOCKED = true

const wait = () => new Promise((resolve) => setTimeout(resolve, 400))

// PlaylistOverview: { playlistId, playlistName, playlistCoverUrl, totalPieces, playlistOwner, isSaved, musicPieces }
export function fetchPlaylist(username, playlistId) {
  return fetchAPI(`${API_BASE_URL}/api/playlists/${encodeURIComponent(username)}/${encodeURIComponent(playlistId)}`)
}

// Returns nothing (204)
export async function setPlaylistSaved(playlistId, saved) {
  if (!PLAYLIST_ACTIONS_MOCKED) {
    return request(saved ? 'PUT' : 'DELETE', `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}/save`)
  }
  await wait()
  return null
}

// Returns nothing (204)
export async function deletePlaylist(playlistId) {
  if (!PLAYLIST_ACTIONS_MOCKED) return request('DELETE', `${API_BASE_URL}/api/playlists/${encodeURIComponent(playlistId)}`)
  await wait()
  return null
}
