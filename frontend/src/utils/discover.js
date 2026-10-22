import { fetchAPI } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

// Fewer results than this fill the first ring with repeats, so Discover shows the "upload more" state instead
export const MIN_RESULTS = 8

// { musicPieces: [MusicPieceOverview] }: up to 100 public pieces like the seed, best match first. 404
// MUSIC_PIECE_NOT_FOUND: the seed is gone, or private and not the caller's. Doesn't notify; the page explains errors
export function fetchDiscover(musicPieceId) {
  return fetchAPI(`${API_BASE_URL}/api/music-pieces/${encodeURIComponent(musicPieceId)}/discover`, { notifyOnError: false })
}

// The grid is one square block that repeats forever. The seed sits in the middle of the block and the results spiral
// out from it, best first, so the closer a piece is to the seed the better it matches. Spare cells repeat results
export function buildLayout(resultCount) {
  let side = Math.ceil(Math.sqrt(resultCount + 1))
  if (side % 2 === 0) side++ // odd, so the seed is dead centre
  const half = (side - 1) / 2
  const spiral = new Map([['0,0', 0]])
  const directions = [[1, 0], [0, 1], [-1, 0], [0, -1]]
  let x = 0
  let y = 0
  let step = 1
  for (let turn = 0; spiral.size < side * side; turn++) {
    const [dx, dy] = directions[turn % 4]
    for (let i = 0; i < step; i++) {
      x += dx
      y += dy
      if (Math.abs(x) <= half && Math.abs(y) <= half) spiral.set(`${x},${y}`, spiral.size)
    }
    if (turn % 2 === 1) step++
  }
  return { side, half, spiral }
}

// Which piece a grid cell shows: rank 0 is the seed, rank n is the nth result
export function slotAt(layout, resultCount, x, y) {
  const wrap = (n) => ((((n + layout.half) % layout.side) + layout.side) % layout.side) - layout.half
  const index = layout.spiral.get(`${wrap(x)},${wrap(y)}`)
  if (index === 0 || resultCount === 0) return 0
  return ((index - 1) % resultCount) + 1
}
