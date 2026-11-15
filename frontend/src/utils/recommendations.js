// Recommended songs for a playlist, built from GET /api/music-pieces/{id}/discover (fetchDiscover in discover.js). The
// backend has no playlist-level recommendations, so a few of the playlist's songs are used as seeds and their results
// merged here

// How many of the playlist's songs to ask about
export const MAX_SEEDS = 3

/**
 * The songs to ask discover about: the newest playable songs in the playlist, newest first. Discover only takes a seed
 * the caller could play on its own: a public song, or a private one they uploaded (`username` is the caller's).
 */
export function pickSeeds(tracks, username = null, max = MAX_SEEDS) {
  const me = username?.toLowerCase() ?? null
  const seeds = []
  for (let i = tracks.length - 1; i >= 0 && seeds.length < max; i--) {
    const track = tracks[i]
    if (track.isAvailable === false) continue
    const mine = me !== null && track.uploader?.username?.toLowerCase() === me
    if (track.isPublic !== false || mine) seeds.push(track)
  }
  return seeds
}

/**
 * Merges discover result lists into one ranking. A song scores more the higher it sits in a list, and adds up across
 * lists, so a song that comes up for several seeds ranks first. Ties keep the order songs were first seen in. Songs in
 * `excludeIds` (already in the playlist) are dropped.
 */
export function mergeRecommendations(lists, excludeIds = new Set()) {
  const merged = new Map()
  let seen = 0
  for (const list of lists) {
    list.forEach((piece, rank) => {
      if (excludeIds.has(piece.musicPieceId)) return
      const entry = merged.get(piece.musicPieceId) ?? { piece, score: 0, order: seen++ }
      entry.score += list.length - rank
      merged.set(piece.musicPieceId, entry)
    })
  }
  return [...merged.values()]
    .sort((a, b) => b.score - a.score || a.order - b.order)
    .map((entry) => entry.piece)
}

/**
 * The playlists someone can add a song to from another person's playlist: ones they own or edit, from `access` on
 * each `LibraryPlaylist` (OWNER, EDITOR, LISTENER, or null for one only saved). An entry without the field (a stream
 * from before it existed) counts as theirs only when they own it.
 */
export function addablePlaylists(library, username, excludeId = null) {
  if (!Array.isArray(library) || !username) return []
  const me = username.toLowerCase()
  return library.filter((playlist) => {
    if (playlist.playlistId === excludeId) return false
    // A playlist saved and also edited still counts; one only saved has access null
    if ('access' in playlist) return playlist.access === 'OWNER' || playlist.access === 'EDITOR'
    return !playlist.isSaved && playlist.ownerUsername?.toLowerCase() === me
  })
}
