import { fetchAPI } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

// [TopMusicPiece]: this week's 10 most played public pieces, most played first. Today each has only name, coverUrl and
// artistSummary; `musicPieceId`, `uploader` and `playCount` are requested (backend/BACKEND_REQUESTS.md). Rows play and
// show the count bar once those arrive
export function fetchTopPieces() {
  return fetchAPI(`${API_BASE_URL}/api/core/top`, { notifyOnError: false })
}

// [string]: the top tags, curated genres and moods first
export function fetchTopTags() {
  return fetchAPI(`${API_BASE_URL}/api/tags`, { notifyOnError: false })
}

// GET /api/tags/{tag}/music-pieces isn't on the backend yet (backend/BACKEND_REQUESTS.md). Until it ships the tag page
// shows sample songs labeled as a preview; flip this off once it does, since the real call already uses the requested shape
export const TAG_PIECES_MOCKED = true

const sample = (n, name, artistName) => ({
  musicPieceId: `preview-${n}`,
  name,
  coverUrl: null,
  artistSummary: { artistId: `preview-artist-${n}`, artistName, artistPfpUrl: null },
  uploader: null,
  isAvailable: true,
})
const PREVIEW_TAG_PIECES = [
  sample(1, 'Pink + White', 'Frank Ocean'),
  sample(2, 'Good Days', 'SZA'),
  sample(3, 'Let It Happen', 'Tame Impala'),
  sample(4, 'Motion Sickness', 'Phoebe Bridgers'),
  sample(5, 'Money Trees', 'Kendrick Lamar'),
]

// { musicPieces: [MusicPieceOverview], nextCursor }: public pieces with the tag, most played first, pages of 50
export async function fetchTagPieces(tag, cursor = null) {
  if (TAG_PIECES_MOCKED) return { musicPieces: PREVIEW_TAG_PIECES, nextCursor: null }
  const query = cursor ? `?${new URLSearchParams({ cursor })}` : ''
  return fetchAPI(`${API_BASE_URL}/api/tags/${encodeURIComponent(tag)}/music-pieces${query}`, { notifyOnError: false })
}

// "lo-fi" → "Lo-fi"; tags are stored lowercase
export function tagLabel(tag) {
  return tag ? tag.charAt(0).toUpperCase() + tag.slice(1) : ''
}
