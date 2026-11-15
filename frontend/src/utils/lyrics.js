// Lyrics come straight from LRCLIB (https://lrclib.net), a free, open lyrics database that allows browser requests.
// Songs are matched by title, artist and length: LRCLIB's /api/get only answers when the length is within about 2
// seconds of its copy, so a different edit or remix of a song doesn't come back with the wrong timing. When that finds
// nothing, /api/search by title and artist is tried, keeping only a result within MAX_LENGTH_GAP seconds
const LRCLIB = 'https://lrclib.net/api'
const MAX_LENGTH_GAP = 3

// { lines: [{ time, text }] | null (timed, in order), plain: string | null, instrumental } or null when LRCLIB has
// nothing for the song. Cached per piece for the session
const cache = new Map()

export async function fetchLyrics({ musicPieceId, title, artist, duration }, { signal } = {}) {
  const key = `${musicPieceId}:${Math.round(duration)}`
  if (cache.has(key)) return cache.get(key)
  const track = cleanTitle(title)
  const query = { track_name: track, artist_name: artist ?? '' }
  let record = await lrclib('get', { ...query, duration: Math.round(duration) }, signal)
  if (!record) {
    const results = (await lrclib('search', query, signal)) ?? []
    record = closest(results, duration)
  }
  const lyrics = record ? toLyrics(record) : null
  cache.set(key, lyrics)
  return lyrics
}

async function lrclib(path, params, signal) {
  const response = await fetch(`${LRCLIB}/${path}?${new URLSearchParams(params)}`, { signal })
  if (response.status === 404) return null
  if (!response.ok) throw Object.assign(new Error('Lyrics request failed'), { status: response.status })
  return response.json()
}

// The result nearest in length, timed lyrics first, or null when none is close enough to trust
function closest(results, duration) {
  const near = results.filter((item) => Math.abs((item.duration ?? 0) - duration) <= MAX_LENGTH_GAP)
  near.sort((a, b) => Number(Boolean(b.syncedLyrics)) - Number(Boolean(a.syncedLyrics))
    || Math.abs(a.duration - duration) - Math.abs(b.duration - duration))
  return near[0] ?? null
}

function toLyrics({ syncedLyrics, plainLyrics, instrumental }) {
  const lines = syncedLyrics ? parseLrc(syncedLyrics) : []
  if (!lines.length && !plainLyrics && !instrumental) return null
  return { lines: lines.length ? lines : null, plain: plainLyrics || null, instrumental: Boolean(instrumental) }
}

// Uploads are often named after their file: drop the extension and tags like "(Official Audio)" so the title matches
export function cleanTitle(title = '') {
  return title
    .replace(/\.mp3$/i, '')
    .replace(/\s*[([][^)\]]*\b(official|audio|video|lyrics?|visualizer|hq|hd)\b[^)\]]*[)\]]/gi, '')
    .trim()
}

const STAMP = /\[(\d+):(\d{1,2}(?:\.\d+)?)\]/g

// LRC: "[01:02.50] text". A line can carry several stamps (a repeated chorus); tags like [ar:...] are skipped. Empty
// lines are kept, as instrumental breaks
export function parseLrc(text) {
  const lines = []
  for (const raw of text.split(/\r?\n/)) {
    const stamps = [...raw.matchAll(STAMP)]
    if (!stamps.length) continue
    const words = raw.replace(STAMP, '').trim()
    for (const [, minutes, seconds] of stamps) lines.push({ time: Number(minutes) * 60 + Number(seconds), text: words })
  }
  return lines.sort((a, b) => a.time - b.time)
}

// Index of the line being sung at `seconds`, or -1 before the first
export function lineAt(lines, seconds) {
  let low = 0
  let high = lines.length - 1
  let found = -1
  while (low <= high) {
    const middle = (low + high) >> 1
    if (lines[middle].time <= seconds) {
      found = middle
      low = middle + 1
    } else {
      high = middle - 1
    }
  }
  return found
}

// Per-song timing nudge in seconds (positive: lyrics come later), kept in this browser
const offsetKey = (musicPieceId) => `aux-lyrics-offset-${musicPieceId}`

export function readOffset(musicPieceId) {
  try {
    return Number(localStorage.getItem(offsetKey(musicPieceId))) || 0
  } catch {
    return 0
  }
}

export function saveOffset(musicPieceId, offset) {
  try {
    if (offset) localStorage.setItem(offsetKey(musicPieceId), String(offset))
    else localStorage.removeItem(offsetKey(musicPieceId))
  } catch {
    // Blocked storage: the nudge lasts until the page reloads
  }
}
