import { fetchAPI } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

// GET /api/users/matches isn't on the backend yet (backend/BACKEND_REQUESTS.md, Dashboard). Until it ships the dashboard shows
// sample listeners labeled as a preview; flip this off once it does, since the real call already uses the requested shape
export const MATCHES_MOCKED = true

const PREVIEW_MATCHES = [
  {
    user: { userId: 'preview-1', username: 'jordan_k', pfpUrl: null },
    compatibility: 87,
    sharedArtists: [{ artistId: 'a1', artistName: 'Frank Ocean' }, { artistId: 'a2', artistName: 'SZA' }, { artistId: 'a3', artistName: 'Mitski' }],
  },
  {
    user: { userId: 'preview-2', username: 'lena_park', pfpUrl: null },
    compatibility: 64,
    sharedArtists: [{ artistId: 'a4', artistName: 'Kendrick Lamar' }, { artistId: 'a5', artistName: 'Tame Impala' }],
  },
  {
    user: { userId: 'preview-3', username: 'sam_rivers', pfpUrl: null },
    compatibility: 31,
    sharedArtists: [{ artistId: 'a6', artistName: 'Yeat' }],
  },
  {
    user: { userId: 'preview-4', username: 'maya_lin', pfpUrl: null },
    compatibility: 12,
    sharedArtists: [{ artistId: 'a7', artistName: 'Daniel Caesar' }],
  },
]

// { matches: [{ user: { userId, username, pfpUrl }, compatibility: 0-100, sharedArtists: [ArtistSummary] }] }
export async function fetchMatches() {
  if (!MATCHES_MOCKED) return fetchAPI(`${API_BASE_URL}/api/users/matches`, { notifyOnError: false })
  return { matches: PREVIEW_MATCHES }
}

// The style guide's levels; each one fills one more of the meter's five segments
const LEVELS = [
  { min: 80, label: 'Super' },
  { min: 60, label: 'Very high' },
  { min: 40, label: 'High' },
  { min: 20, label: 'Medium' },
  { min: 0, label: 'Low' },
]

export function compatibilityLevel(score) {
  const index = LEVELS.findIndex((level) => score >= level.min)
  return { label: LEVELS[index].label, filled: LEVELS.length - index }
}

// What follows name `index` of `count` in "A, B and C"
export function nameSeparator(index, count) {
  if (index === count - 1) return ''
  return index === count - 2 ? ' and ' : ', '
}
