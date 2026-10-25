import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'
import { resolveCoverURL } from '@/utils/display.js'
import {
  IGNORED_PLAY_ERRORS,
  REPEAT_MODES,
  fetchQueue,
  fetchQueuePage,
  fetchStream,
  reportPlay,
  savePlayPreferences,
} from '@/utils/playback.js'

// The server builds the play queue in pages and signs the audio URLs; this store keeps the queue it has fetched,
// where in it we are, shuffle, and the current song's stream
const REFILL_WHEN_LEFT = 3
// A prefetched URL is only used if it still has this long to live
const STREAM_MARGIN_MS = 60_000
// A listen counts after this many seconds actually played, or the whole piece if it's shorter
const LISTEN_TO_COUNT = 30
// The server keeps shuffle and repeat (play preferences) but not position or queue order, so those are saved here, per
// account
const SAVE_EVERY_SECONDS = 5
const DEFAULT_VOLUME = 0.8

const QUEUE_ERRORS = {
  PLAYLIST_INVITE_PENDING: 'Accept the invitation to play this playlist.',
  PLAYLIST_NOT_FOUND: "This playlist isn't available anymore.",
  MUSIC_PIECE_NOT_FOUND: "That song isn't in this playlist anymore.",
}

export const useMusicStore = defineStore('music', () => {
  const DEFAULT_TITLE = 'No song playing'
  const emptyPiece = () => ({ uuid: '', cover: '', title: DEFAULT_TITLE, artist: '', mp3File: null, playToken: null, expiresAt: 0 })

  const showArrow = ref(true)
  const collapsed = ref(true)
  // { uuid, cover, title, artist, mp3File (signed URL), playToken, expiresAt }
  const currentMusicPiece = ref(emptyPiece())
  const currentPlaylistUUID = ref('')
  // The fetched queue, in play order: MusicPieceOverview items
  const playlist = ref([])
  const queueCursor = ref(null)
  const isPlaying = ref(false)
  const shuffleOn = ref(false)
  // OFF stops at the end of the queue, PLAYLIST starts it again, PIECE repeats the current song
  const repeatMode = ref('OFF')
  const volume = ref(DEFAULT_VOLUME)
  const position = ref(0)
  // Where to seek once the next audio source has loaded (after refreshing an expired URL)
  const seekOnLoad = ref(null)
  const forceShowPlayerActive = ref(false)
  // Length of the current piece in seconds, once its audio has loaded
  const duration = ref(0)

  let currentPlaylistIndex = null
  let playRequest = 0
  let loadingMore = null
  let prefetching = null
  let recoveredUrl = null
  // musicPieceId -> { url, playToken, expiresAt } fetched ahead of time so the next song starts without a gap
  const prefetched = new Map()
  // Seconds of the current playToken's listen actually played (seeking doesn't count)
  let listened = 0
  let reportedToken = null
  let unsavedSeconds = 0
  const warmAudio = typeof Audio === 'undefined' ? null : new Audio()

  const notify = () => useNotificationStore()

  function stopMissingPlaylist(err) {
    if (err.code !== 'PLAYLIST_NOT_FOUND' || !currentPlaylistUUID.value) return false
    reset()
    notify().neutral(QUEUE_ERRORS.PLAYLIST_NOT_FOUND)
    return true
  }

  function toCurrent(piece, stream) {
    return {
      uuid: piece.musicPieceId,
      title: piece.name,
      cover: piece.coverUrl ? resolveCoverURL(piece.coverUrl) : '',
      artist: piece.artistSummary?.artistName ?? '',
      mp3File: stream?.url ?? null,
      playToken: stream?.playToken ?? null,
      expiresAt: stream?.expiresAt ?? 0,
    }
  }

  async function requestStream(piece) {
    const { url, expiresInSeconds, playToken } = await fetchStream(piece.musicPieceId, currentPlaylistUUID.value || null)
    return { url, playToken, expiresAt: Date.now() + expiresInSeconds * 1000 }
  }

  async function streamFor(piece) {
    const saved = prefetched.get(piece.musicPieceId)
    prefetched.delete(piece.musicPieceId)
    if (saved && saved.expiresAt - Date.now() > STREAM_MARGIN_MS) return saved
    return requestStream(piece)
  }

  // Starts queue item `index`: its details show right away, the audio once its URL arrives
  async function playAt(index) {
    const piece = playlist.value[index]
    if (!piece) return false
    const request = ++playRequest
    finishListening()
    listened = 0
    duration.value = 0
    currentPlaylistIndex = index
    currentMusicPiece.value = toCurrent(piece, null)
    position.value = 0
    seekOnLoad.value = null
    try {
      const stream = await streamFor(piece)
      if (request !== playRequest) return false
      currentMusicPiece.value = toCurrent(piece, stream)
      isPlaying.value = true
      collapsed.value = false
      forceShowPlayerActive.value = true
      refill()
      return true
    } catch (err) {
      if (request !== playRequest || err.sessionEnded) return false
      if (stopMissingPlaylist(err)) return false
      isPlaying.value = false
      notify().error(err.code === 'MUSIC_PIECE_NOT_FOUND'
        ? `"${piece.name}" can't be played.`
        : "We couldn't start this song. Check that the server is running, then try again.")
      return false
    }
  }

  // Plays songs that aren't from a playlist (your uploads, for example), starting at `index`. Their streams are requested
  // without a playlistId. With no playlist there's no server shuffle, but the shuffle preference is left as it is
  function playPieces(pieces, index = 0) {
    if (!pieces?.[index]) return false
    playRequest++
    prefetched.clear()
    currentPlaylistUUID.value = ''
    playlist.value = [...pieces]
    queueCursor.value = null
    forceShowPlayerActive.value = true
    return playAt(index)
  }

  // Replaces the queue with a playlist's, starting at `start` (a musicPieceId) or the top
  async function playPlaylist(playlistId, { start = null, shuffle = false } = {}) {
    const request = ++playRequest
    forceShowPlayerActive.value = true
    try {
      const page = await fetchQueue(playlistId, { start, shuffle })
      if (request !== playRequest) return false
      prefetched.clear()
      currentPlaylistUUID.value = playlistId
      playlist.value = page.musicPieces
      queueCursor.value = page.nextCursor
      setPreferences({ shuffle })
      if (!page.musicPieces.length) {
        notify().neutral('This playlist has no songs to play yet.')
        return false
      }
      return playAt(0)
    } catch (err) {
      if (request !== playRequest || err.sessionEnded) return false
      if (currentPlaylistUUID.value === playlistId && stopMissingPlaylist(err)) return false
      notify().error(QUEUE_ERRORS[err.code] ?? "We couldn't start this playlist. Check that the server is running, then try again.")
      return false
    }
  }

  // Fetches the next queue page once only a few songs are left. Returns the pending load, if any
  function refill() {
    const left = playlist.value.length - (currentPlaylistIndex ?? 0) - 1
    if (!queueCursor.value || loadingMore || left > REFILL_WHEN_LEFT) return loadingMore
    const playlistId = currentPlaylistUUID.value
    const cursor = queueCursor.value
    loadingMore = fetchQueuePage(playlistId, cursor)
      .then((page) => {
        if (currentPlaylistUUID.value !== playlistId || queueCursor.value !== cursor) return
        playlist.value.push(...page.musicPieces)
        queueCursor.value = page.nextCursor
      })
      .catch((err) => {
        if (currentPlaylistUUID.value === playlistId && queueCursor.value === cursor && stopMissingPlaylist(err)) return
        // Tried again on the next song
      })
      .finally(() => {
        loadingMore = null
      })
    return loadingMore
  }

  async function moveForward() {
    if (!playlist.value.length) return
    const next = (currentPlaylistIndex ?? -1) + 1
    if (next >= playlist.value.length && queueCursor.value) await refill()
    if (!playlist.value.length) return
    if (next < playlist.value.length) return playAt(next)
    // End of the queue
    finishListening()
    if (repeatMode.value === 'PLAYLIST') return repeatQueue()
    isPlaying.value = false
    position.value = 0
  }

  // Repeat PLAYLIST: a playlist's queue comes again from /play (with the current shuffle, so a shuffled one gets a new
  // order); songs played on their own start over
  function repeatQueue() {
    if (currentPlaylistUUID.value) return playPlaylist(currentPlaylistUUID.value, { shuffle: shuffleOn.value })
    return playAt(0)
  }

  // Back to the start of the song, or to the previous one if it only just started
  function moveBackward() {
    if (!playlist.value.length) return
    if (position.value > 3 || !currentPlaylistIndex) {
      position.value = 0
      return
    }
    return playAt(currentPlaylistIndex - 1)
  }

  // The server shuffles: ask for a new queue that starts with the current song, which keeps playing
  async function toggleShuffle() {
    const playlistId = currentPlaylistUUID.value
    const current = playlist.value[currentPlaylistIndex]
    if (!playlistId || !current) return
    const shuffle = !shuffleOn.value
    try {
      const page = await fetchQueue(playlistId, { start: current.musicPieceId, shuffle })
      if (currentPlaylistUUID.value !== playlistId || playlist.value[currentPlaylistIndex] !== current) return
      playlist.value = page.musicPieces
      queueCursor.value = page.nextCursor
      currentPlaylistIndex = 0
      setPreferences({ shuffle })
      prefetched.clear()
    } catch (err) {
      if (currentPlaylistUUID.value === playlistId && stopMissingPlaylist(err)) return
      if (!err.sessionEnded) notify().error("We couldn't change shuffle. Check that the server is running, then try again.")
    }
  }

  // Near the end of a song: get the next one's URL and start loading its audio
  async function prefetchNext() {
    const next = playlist.value[(currentPlaylistIndex ?? -1) + 1]
    if (!next || prefetched.has(next.musicPieceId) || prefetching) return
    const request = playRequest
    prefetching = requestStream(next)
    try {
      const stream = await prefetching
      if (request !== playRequest) return
      prefetched.set(next.musicPieceId, stream)
      if (warmAudio) {
        warmAudio.preload = 'auto'
        warmAudio.src = stream.url
      }
    } catch (err) {
      if (request === playRequest && stopMissingPlaylist(err)) return
      // The song asks again when it starts
    } finally {
      prefetching = null
    }
  }

  // The audio failed, most likely because its signed URL expired during a long pause: get a new one and pick up
  // where it stopped. A URL that fails straight after being refreshed isn't retried again
  async function recoverStream() {
    const current = currentMusicPiece.value
    if (!current.uuid || !current.mp3File) return
    if (current.mp3File === recoveredUrl) {
      isPlaying.value = false
      notify().error(`"${current.title}" stopped playing. Try it again in a moment.`)
      return
    }
    const resumeAt = position.value
    const request = playRequest
    try {
      const stream = await requestStream({ musicPieceId: current.uuid })
      if (request !== playRequest) return
      recoveredUrl = stream.url
      seekOnLoad.value = resumeAt
      // The listen goes on under the new token; if the old one was already reported, so is this one
      if (reportedToken === current.playToken) reportedToken = stream.playToken
      currentMusicPiece.value = { ...current, mp3File: stream.url, playToken: stream.playToken, expiresAt: stream.expiresAt }
    } catch (err) {
      if (request !== playRequest || err.sessionEnded) return
      if (stopMissingPlaylist(err)) return
      isPlaying.value = false
      notify().error(`"${current.title}" stopped playing. Check that the server is running, then try again.`)
    }
  }

  /* Listens: the player adds the seconds it actually played; a listen is reported once per playToken */

  function recordListening(seconds) {
    if (!isPlaying.value || !(seconds > 0)) return
    listened += seconds
    if (listened >= LISTEN_TO_COUNT) sendPlay()
    unsavedSeconds += seconds
    if (unsavedSeconds >= SAVE_EVERY_SECONDS) saveLastPlayback()
  }

  // On track end or skip: reports the listen if it reached 30 seconds, or (nearly) the whole of a shorter piece
  function finishListening() {
    const length = duration.value
    if (listened >= LISTEN_TO_COUNT || (length > 0 && length < LISTEN_TO_COUNT && listened >= length - 1)) sendPlay()
  }

  function sendPlay() {
    const { uuid, playToken } = currentMusicPiece.value
    if (!uuid || !playToken || playToken === reportedToken) return
    reportedToken = playToken
    reportPlay(uuid, playToken, Math.round(listened)).catch((err) => {
      if (!err.sessionEnded && !IGNORED_PLAY_ERRORS.includes(err.code)) console.warn('Play not recorded:', err.code)
    })
  }

  // Repeat one: a token counts once, so each new listen of the same piece gets a new one
  async function startRepeat() {
    finishListening()
    listened = 0
    const current = currentMusicPiece.value
    if (!current.uuid) return
    const request = playRequest
    try {
      const stream = await requestStream({ musicPieceId: current.uuid })
      if (currentMusicPiece.value.uuid === current.uuid) currentMusicPiece.value.playToken = stream.playToken
    } catch (err) {
      if (request === playRequest && stopMissingPlaylist(err)) return
      // The repeat still plays; this listen just isn't counted
    }
  }

  /* Resume: the song, position, shuffle and the rest of the queue are saved in localStorage per account */

  function accountId() {
    const user = useUserStore().userData
    return user?.userId ?? user?.username ?? null
  }

  function storageKey() {
    const id = accountId()
    return id ? `aux-playback-${id}` : null
  }

  // Volume stays on this device; shuffle and repeat are play preferences kept on the server
  function settingsKey() {
    const id = accountId()
    return id ? `aux-player-settings-${id}` : null
  }

  function applySettings() {
    try {
      const key = settingsKey()
      const settings = key ? JSON.parse(localStorage.getItem(key) ?? 'null') : null
      if (typeof settings?.volume === 'number') volume.value = Math.min(1, Math.max(0, settings.volume))
    } catch {
      // Bad or blocked storage: keep the default
    }
  }

  function saveSettings() {
    const key = settingsKey()
    if (!key) return
    try {
      localStorage.setItem(key, JSON.stringify({ volume: Number(volume.value) }))
    } catch {
      // Storage can be blocked; the volume still holds until the page reloads
    }
  }

  // `playPreferences` from GET /api/users/me/last-playback: { shuffleEnabled, repeatPreference }
  function applyPlayPreferences(preferences) {
    if (!preferences) return
    shuffleOn.value = Boolean(preferences.shuffleEnabled)
    repeatMode.value = REPEAT_MODES.includes(preferences.repeatPreference) ? preferences.repeatPreference : 'OFF'
  }

  // Every shuffle or repeat change: the player changes straight away and the server is told in the background
  function setPreferences({ shuffle = shuffleOn.value, repeat = repeatMode.value }) {
    if (shuffle === shuffleOn.value && repeat === repeatMode.value) return
    shuffleOn.value = shuffle
    repeatMode.value = repeat
    savePlayPreferences({ shuffleEnabled: shuffle, repeatPreference: repeat }).catch((err) => {
      if (!err.sessionEnded) console.warn('Play preferences not saved:', err.code)
    })
  }

  function readSaved() {
    try {
      const key = storageKey()
      return key ? JSON.parse(localStorage.getItem(key) ?? 'null') : null
    } catch {
      return null
    }
  }

  function saveLastPlayback() {
    unsavedSeconds = 0
    const key = storageKey()
    const { uuid } = currentMusicPiece.value
    if (!key || !uuid) return false
    const index = currentPlaylistIndex ?? 0
    try {
      localStorage.setItem(key, JSON.stringify({
        musicPieceId: uuid,
        playlistId: currentPlaylistUUID.value || null,
        position: Math.floor(position.value),
        playing: isPlaying.value,
        queue: playlist.value.slice(index),
        cursor: queueCursor.value,
      }))
      return true
    } catch {
      return false
    }
  }

  watch([() => currentMusicPiece.value.uuid, isPlaying, shuffleOn], () => saveLastPlayback())
  watch(volume, saveSettings)
  watch(accountId, applySettings, { immediate: true })
  if (typeof window !== 'undefined') window.addEventListener('pagehide', () => saveLastPlayback())

  // GET /api/users/me/last-playback ({ musicPiece, playlistId, playPreferences }): shows that song, paused, with its
  // queue rebuilt so Play picks up where the user left off. Saved position and queue are only used for the same song.
  // musicPiece is null for someone who has never played anything, but their preferences still apply
  async function restoreLastPlayback(playbackData) {
    const piece = playbackData?.musicPiece
    if (!piece?.musicPieceId) {
      applyPlayPreferences(playbackData?.playPreferences)
      return
    }
    const saved = readSaved()
    reset()
    // After reset(), which turns shuffle and repeat off
    applyPlayPreferences(playbackData.playPreferences)
    const request = playRequest
    const playlistId = playbackData.playlistId ?? null
    const same = saved?.musicPieceId === piece.musicPieceId && (saved.playlistId ?? null) === playlistId
    currentMusicPiece.value = toCurrent(piece, null)
    currentPlaylistUUID.value = playlistId ?? ''
    playlist.value = [piece]
    currentPlaylistIndex = 0
    collapsed.value = false
    forceShowPlayerActive.value = true
    if (same) position.value = Math.max(0, saved.position ?? 0)
    // Pick the music back up if it was playing at the refresh. Browsers often block audio that starts without a click,
    // in which case the player just shows Play
    const resumePlaying = () => {
      if (same && saved.playing && request === playRequest) resume()
    }
    if (!playlistId) return resumePlaying()

    if (same && saved.queue?.[0]?.musicPieceId === piece.musicPieceId) {
      playlist.value = saved.queue
      queueCursor.value = saved.cursor ?? null
      return resumePlaying()
    }
    try {
      const page = await fetchQueue(playlistId, { start: piece.musicPieceId, shuffle: shuffleOn.value })
      if (request !== playRequest) return
      if (page.musicPieces.length) {
        playlist.value = page.musicPieces
        queueCursor.value = page.nextCursor
      }
      resumePlaying()
    } catch (err) {
      if (request === playRequest && stopMissingPlaylist(err)) return
      // The playlist can't be played anymore: keep just this song, played on its own
      if (request === playRequest) currentPlaylistUUID.value = ''
    }
  }

  // Play after a restore: the song has no audio yet, so get it and continue from the saved position
  async function resume() {
    const at = position.value
    const started = await playAt(currentPlaylistIndex ?? 0)
    if (started && at > 0) {
      position.value = at
      seekOnLoad.value = at
    }
    return started
  }

  function playMusicPiece(musicPiece) {
    currentMusicPiece.value = musicPiece
    position.value = 0
    isPlaying.value = true
  }

  // OFF → PLAYLIST → PIECE → OFF
  function cycleRepeat() {
    const next = REPEAT_MODES[(REPEAT_MODES.indexOf(repeatMode.value) + 1) % REPEAT_MODES.length]
    setPreferences({ repeat: next })
  }

  function forceShowPlayer() {
    forceShowPlayerActive.value = true
  }

  function updateBottomPlayerAfterLogin(playbackData) {
    return restoreLastPlayback(playbackData)
  }

  function getCurrentPlaylistIndex() { return currentPlaylistIndex }

  function getCurrentPlaylistUUID() { return currentPlaylistUUID.value }

  function getCurrentMusicPieceUUID() { return currentMusicPiece.value.uuid }

  function setCurrentPlaylistIndex(index) { currentPlaylistIndex = index }

  function isEmpty() {
    return !currentMusicPiece.value.uuid && !currentPlaylistUUID.value && !playlist.value.length && !isPlaying.value
  }

  function reset() {
    playRequest++
    showArrow.value = true
    collapsed.value = true
    currentMusicPiece.value = emptyPiece()
    currentPlaylistUUID.value = ''
    playlist.value = []
    queueCursor.value = null
    isPlaying.value = false
    shuffleOn.value = false
    repeatMode.value = 'OFF'
    position.value = 0
    seekOnLoad.value = null
    forceShowPlayerActive.value = false
    duration.value = 0
    currentPlaylistIndex = null
    recoveredUrl = null
    listened = 0
    reportedToken = null
    prefetched.clear()
  }

  return {
    showArrow,
    collapsed,
    currentMusicPiece,
    currentPlaylistUUID,
    playlist,
    queueCursor,
    isPlaying,
    shuffleOn,
    repeatMode,
    volume,
    position,
    seekOnLoad,
    duration,
    forceShowPlayerActive,
    playPlaylist,
    playPieces,
    playAt,
    saveLastPlayback,
    restoreLastPlayback,
    resume,
    recordListening,
    finishListening,
    startRepeat,
    moveForward,
    moveBackward,
    playMusicPiece,
    toggleShuffle,
    cycleRepeat,
    applyPlayPreferences,
    prefetchNext,
    recoverStream,
    forceShowPlayer,
    updateBottomPlayerAfterLogin,
    getCurrentPlaylistIndex,
    getCurrentPlaylistUUID,
    getCurrentMusicPieceUUID,
    setCurrentPlaylistIndex,
    isEmpty,
    reset,
  }
})
