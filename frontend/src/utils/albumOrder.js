// An album's songs in their tracklist order, with a group per disc. The backend has no track numbers yet ("Album track
// order" in ../backend/BACKEND_REQUESTS.md), so for now each album is looked up in Apple's public catalog (the iTunes
// Search API: no key, open to browsers) and our songs are matched to its tracklist by title. Once pieces carry
// `trackNumber` those win and nothing is looked up; delete the lookup then, or switch it off here
export const ALBUM_ORDER_FROM_CATALOG = true

const SEARCH_URL = 'https://itunes.apple.com/search'
const LOOKUP_URL = 'https://itunes.apple.com/lookup'
const CACHE_PREFIX = 'aux-album-order:'
const DAY = 24 * 60 * 60 * 1000
// A tracklist doesn't change; an album Apple doesn't have might be added later
const FOUND_TTL = 30 * DAY
const MISSING_TTL = DAY
const EDITION = /\b(deluxe|expanded|extended|special|anniversary|remaster(ed)?|bonus|complete|collector'?s|platinum|super)\b/

// Lowercase, no accents, straight apostrophes, "&" as "and"
function plain(text) {
  return String(text ?? '')
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[’‘`´]/g, "'")
    .replace(/&/g, ' and ')
}

// "(feat. Drake)", "[ft. X]" and a trailing "feat. X" are credits, not part of the title
function withoutFeatures(text) {
  return text
    .replace(/[([]\s*(feat|ft|featuring|with)\.?\s[^)\]]*[)\]]/g, ' ')
    .replace(/\s[-–]?\s*(feat|ft|featuring)\.?\s.*$/, ' ')
}

// Notes on which release a track is from, not which song it is: "[Bonus Track]", "(Extended Version)", "(2011
// Remaster)". "Remix", "Live", "Radio Edit" and "Acoustic" stay, since those are different recordings
const RELEASE_NOTE = /[([]\s*(bonus( track)?|(extended|album|explicit|clean|original|lp|deluxe|single)( version| edition)?|(\d{4} )?remaster(ed)?( \d{4})?( version)?)\s*[)\]]/g

function withoutReleaseNotes(text) {
  return text.replace(RELEASE_NOTE, ' ')
}

// Everything in brackets, and a " - Remastered 2011" style suffix
function withoutExtras(text) {
  return text.replace(/[([][^)\]]*[)\]]/g, ' ').replace(/\s[-–]\s.*$/, ' ')
}

function squash(text) {
  return text.replace(/[^\p{L}\p{N}]/gu, '')
}

// Exact title, credits and release notes left out: "Money Trees (feat. Jay Rock)" is "moneytrees", "Swimming Pools
// (Drank) [Extended Version]" is "swimmingpoolsdrank", "Bitch, Don't Kill My Vibe (feat. JAY-Z) [Remix]" is
// "bitchdontkillmyviberemix"
export function titleKey(title) {
  return squash(withoutReleaseNotes(withoutFeatures(plain(title))))
}

// Bare title: "Swimming Pools (Drank) [Extended Version]" is "swimmingpools"
export function baseTitleKey(title) {
  return squash(withoutExtras(withoutFeatures(plain(title)))) || titleKey(title)
}

// Pairs pieces with tracks: exact titles first, so "Bitch, Don't Kill My Vibe" takes the original and not the remix,
// then bare titles. The earliest free track wins, so a deluxe edition's bonus copy of a song comes last. Each track is used once. Returns Map(piece index → track)
export function matchTracks(pieces, tracks) {
  const matches = new Map()
  const free = [...tracks]
  for (const key of [titleKey, baseTitleKey]) {
    pieces.forEach((piece, index) => {
      if (matches.has(index)) return
      const wanted = key(piece.name)
      if (!wanted) return
      const at = free.findIndex((track) => key(track.title) === wanted)
      if (at >= 0) matches.set(index, free.splice(at, 1)[0])
    })
  }
  return matches
}

function backendTrack(piece) {
  return typeof piece.trackNumber === 'number' ? { disc: piece.discNumber ?? 1, track: piece.trackNumber } : null
}

// { ordered, groups, inOrder }. `ordered` is every piece in play order; `groups` splits it for display, each
// { key, disc, label, pieces, numbers, start }: one per disc, always labeled ("Disc 1", even when it's the only one),
// then pieces not on the tracklist as the next disc, numbered 1, 2, 3 in the order they came. `numbers` are track
// numbers for the rank column and `start` is the group's first index in `ordered`. With no tracklist and no `trackNumber`, it's the pieces as given
export function arrangeAlbum(pieces, tracklist) {
  const fromBackend = pieces.some((piece) => backendTrack(piece))
  let tracks
  if (fromBackend) tracks = new Map(pieces.map((piece, index) => [index, backendTrack(piece)]).filter(([, track]) => track))
  else tracks = tracklist?.length ? matchTracks(pieces, tracklist) : new Map()
  if (!tracks.size) return { ordered: pieces, groups: [{ key: 'all', label: null, pieces, numbers: null, start: 0 }], inOrder: false }

  const matched = [...tracks].sort(([, a], [, b]) => a.disc - b.disc || a.track - b.track)
  const others = pieces.filter((_, index) => !tracks.has(index))
  const groups = []
  for (const [index, track] of matched) {
    let group = groups.at(-1)
    if (group?.disc !== track.disc) {
      group = { key: `disc-${track.disc}`, disc: track.disc, label: `Disc ${track.disc}`, pieces: [], numbers: [] }
      groups.push(group)
    }
    group.pieces.push(pieces[index])
    group.numbers.push(track.track)
  }
  if (others.length) {
    const disc = groups.at(-1).disc + 1
    groups.push({ key: 'other', disc, label: `Disc ${disc}`, pieces: others, numbers: others.map((_, index) => index + 1) })
  }
  let start = 0
  for (const group of groups) {
    group.start = start
    start += group.pieces.length
  }
  return { ordered: groups.flatMap((group) => group.pieces), groups, inOrder: true }
}

function albumName(name) {
  return squash(withoutExtras(plain(name).replace(EDITION, ' ')))
}

// The catalog album that is this one: same artist, then the same name exactly, or the same name with the same kind of
// edition (asking for "Deluxe" picks Apple's deluxe; asking for neither picks the standard one). Clean versions lose
// to the originals, then more tracks wins. Null when none fits
export function pickAlbum(results, artist, album) {
  const artistKey = squash(plain(artist))
  const exact = squash(plain(album))
  const base = albumName(album)
  const edition = EDITION.test(plain(album))
  let best = null
  for (const candidate of results) {
    const candidateArtist = squash(plain(candidate.artistName))
    if (!artistKey || !candidateArtist || !(candidateArtist.includes(artistKey) || artistKey.includes(candidateArtist))) continue
    let score = 0
    if (squash(plain(candidate.collectionName)) === exact) score = 3
    else if (albumName(candidate.collectionName) === base) score = EDITION.test(plain(candidate.collectionName)) === edition ? 2 : 1
    if (!score) continue
    const rank = [score, candidate.collectionExplicitness === 'cleaned' ? 0 : 1, candidate.trackCount ?? 0]
    if (!best || outranks(rank, best.rank)) best = { candidate, rank }
  }
  return best?.candidate ?? null
}

// Compares [score, original, trackCount] left to right
function outranks(rank, other) {
  const at = rank.findIndex((value, i) => value !== other[i])
  return at >= 0 && rank[at] > other[at]
}

async function getJSON(url) {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`Catalog lookup failed: ${response.status}`)
  return response.json()
}

// Apple's id for the artist, or null. Kept for the page's life, since an artist page looks up several albums
const artistIds = new Map()
async function findArtistId(artist) {
  const key = squash(plain(artist))
  if (!artistIds.has(key)) {
    const search = new URLSearchParams({ term: artist, entity: 'musicArtist', media: 'music', limit: '5' })
    const results = (await getJSON(`${SEARCH_URL}?${search}`)).results ?? []
    artistIds.set(key, results.find((result) => squash(plain(result.artistName)) === key)?.artistId ?? null)
  }
  return artistIds.get(key)
}

// The artist's own album list finds albums that a plain search ranks below singles and remixes ("Blonde", "Currents");
// the plain search is the fallback for an artist Apple names differently
async function findAlbum(artist, album) {
  const artistId = await findArtistId(artist)
  if (artistId) {
    const lookup = new URLSearchParams({ id: String(artistId), entity: 'album', limit: '200' })
    const found = pickAlbum(((await getJSON(`${LOOKUP_URL}?${lookup}`)).results ?? []).filter((item) => item.wrapperType === 'collection'), artist, album)
    if (found) return found
  }
  const search = new URLSearchParams({ term: `${artist} ${album}`, entity: 'album', media: 'music', limit: '25' })
  return pickAlbum((await getJSON(`${SEARCH_URL}?${search}`)).results ?? [], artist, album)
}

// [{ disc, track, title }] in tracklist order, or null when Apple doesn't have the album. Throws when Apple can't be
// reached or turns us away
async function lookUp(artist, album) {
  const found = await findAlbum(artist, album)
  if (!found) return null
  const lookup = new URLSearchParams({ id: String(found.collectionId), entity: 'song' })
  const songs = ((await getJSON(`${LOOKUP_URL}?${lookup}`)).results ?? []).filter((item) => item.wrapperType === 'track' && item.kind === 'song')
  if (!songs.length) return null
  return songs
    .map((song) => ({ disc: song.discNumber ?? 1, track: song.trackNumber, title: song.trackName }))
    .filter((track) => typeof track.track === 'number')
    .sort((a, b) => a.disc - b.disc || a.track - b.track)
}

function readCache(key) {
  try {
    const saved = JSON.parse(localStorage.getItem(key) ?? 'null')
    if (!saved) return undefined
    if (Date.now() - saved.at > (saved.tracks ? FOUND_TTL : MISSING_TTL)) return undefined
    return saved.tracks
  } catch {
    return undefined
  }
}

function writeCache(key, tracks) {
  try {
    localStorage.setItem(key, JSON.stringify({ at: Date.now(), tracks }))
  } catch {
    // Storage can be full or blocked; it's looked up again next visit
  }
}

const pending = new Map()
// One lookup at a time: Apple allows about 20 calls a minute, and an artist page can have many albums
let queue = Promise.resolve()

// The album's tracklist from Apple's catalog, or null. Remembered in this browser, so each album is looked up once a
// month at most. Never throws: a failed lookup leaves the album in the order the backend sent
export function fetchAlbumTracklist(artist, album) {
  if (!ALBUM_ORDER_FROM_CATALOG || !artist || !album) return Promise.resolve(null)
  const key = `${CACHE_PREFIX}${squash(plain(artist))}|${plain(album).trim()}`
  const saved = readCache(key)
  if (saved !== undefined) return Promise.resolve(saved)
  if (pending.has(key)) return pending.get(key)
  const result = queue.then(() => lookUp(artist, album)).then(
    (tracks) => {
      writeCache(key, tracks)
      return tracks
    },
    () => {
      // Tried again on the next page load, not on every render
      return null
    },
  )
  queue = result
  pending.set(key, result)
  return result
}
