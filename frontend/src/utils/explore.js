import { fetchAPI } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

// [TopMusicPiece]: this week's 10 most played public pieces, most played first, each with its `playCount`
export function fetchTopPieces() {
  return fetchAPI(`${API_BASE_URL}/api/core/top`, { notifyOnError: false })
}

// [string]: the top tags, curated genres and moods first
export function fetchTopTags() {
  return fetchAPI(`${API_BASE_URL}/api/tags`, { notifyOnError: false })
}

// { musicPieces: [MusicPieceOverview], nextCursor }: public pieces with the tag, most played in the last 30 days
// first, pages of 50
export function fetchTagPieces(tag, cursor = null) {
  const query = cursor ? `?${new URLSearchParams({ cursor })}` : ''
  return fetchAPI(`${API_BASE_URL}/api/tags/${encodeURIComponent(tag)}/music-pieces${query}`, { notifyOnError: false })
}

// "lo-fi" → "Lo-fi"; tags are stored lowercase
export function tagLabel(tag) {
  return tag ? tag.charAt(0).toUpperCase() + tag.slice(1) : ''
}
