import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useNotificationStore } from '@/stores/notification.js'
import { displayArtist, resolveCoverURL } from '@/utils/display.js'
import { shufflePlaylist } from '@/utils/playlist.js'

export const useMusicStore = defineStore('music', () => {
  const DEFAULT_UUID = ''
  const DEFAULT_COVER = ''
  const DEFAULT_TITLE = 'No song playing'
  const DEFAULT_ARTIST = displayArtist('')
  const DEFAULT_PLAYLIST = []
  const DEFAULT_ORDERED_PLAYLIST = {orderedPlaylist: [], orderedPlaylistCurrentIndex: 0}
  const DEFAULT_VOLUME = 0
  const DEFAULT_POSITION = 0

  const showArrow = ref(true)
  const collapsed = ref(true)
  const currentMusicPiece = ref({
    uuid: DEFAULT_UUID,
    cover: DEFAULT_COVER,
    title: DEFAULT_TITLE,
    artist: DEFAULT_ARTIST,
    mp3File: null,
  })
  const currentPlaylistUUID = ref(DEFAULT_UUID)
  const playlist = ref(DEFAULT_PLAYLIST)
  const orderedPlaylist = ref(DEFAULT_ORDERED_PLAYLIST)
  const isPlaying = ref(false)
  const shuffleOn = ref(false)
  const repeatOn = ref(false)
  const volume = ref(DEFAULT_VOLUME)
  const position = ref(DEFAULT_POSITION)
  const forceShowPlayerActive = ref(false)

  let currentPlaylistIndex = null

  // The old playlist/play and playback-save endpoints have been retired.
  // Phase 2 will build queues and request audio from the music-piece playback API.
  async function loadPlaylist() {
    useNotificationStore().neutral('Playback is temporarily unavailable.')
    return false
  }

  async function saveLastPlayback() {
    // Kept for existing callers until Phase 2 records listening events instead.
    return false
  }

  function moveForward() {
    if (!playlist.value.length) return
    currentPlaylistIndex = currentPlaylistIndex === playlist.value.length - 1 ? 0 : currentPlaylistIndex + 1;

    if (!shuffleOn.value) { orderedPlaylist.value.orderedPlaylistCurrentIndex = currentPlaylistIndex; }

    updateCurrentMusicPiece(playlist.value[currentPlaylistIndex]);
    position.value = 0;
    isPlaying.value = true;
  }

  function moveBackward() {
    if (!playlist.value.length) return
    if (currentPlaylistIndex > 0) {
      currentPlaylistIndex--;
      if (!shuffleOn.value) { orderedPlaylist.value.orderedPlaylistCurrentIndex = currentPlaylistIndex; }
      updateCurrentMusicPiece(playlist.value[currentPlaylistIndex]);
      position.value = 0;
      isPlaying.value = true;
    }
  }

  function playMusicPiece(musicPiece) {
    currentMusicPiece.value = musicPiece
    position.value = 0
    isPlaying.value = true
  }

  function toggleShuffle() {
    if (!playlist.value.length) return
    shuffleOn.value = !shuffleOn.value;

    if (shuffleOn.value) {
      orderedPlaylist.value.orderedPlaylist = [...playlist.value];
      orderedPlaylist.value.orderedPlaylistCurrentIndex = currentPlaylistIndex;
      playlist.value = shufflePlaylist( orderedPlaylist.value.orderedPlaylist, currentPlaylistIndex );
      currentPlaylistIndex = 0;
    } else {
      const currentTrack = playlist.value[currentPlaylistIndex];
      const original = orderedPlaylist.value.orderedPlaylist;
      playlist.value = original;
      const originalIdx = original.findIndex( (item) => item.uuid === currentTrack.uuid );
      currentPlaylistIndex = originalIdx >= 0 ? originalIdx : 0;
    }
    if (!shuffleOn.value) { orderedPlaylist.value.orderedPlaylistCurrentIndex = currentPlaylistIndex; }
  }

  function toggleRepeat() {
    repeatOn.value = !repeatOn.value
  }

  function forceShowPlayer() {
    forceShowPlayerActive.value = true
  }

  function updateBottomPlayerAfterLogin(playbackData) {
    const piece = playbackData?.musicPiece
    if (!piece?.musicPieceId) return
    reset()
    currentPlaylistUUID.value = playbackData.playlistId ?? ''
    currentMusicPiece.value = {
      uuid: piece.musicPieceId,
      title: piece.name,
      cover: piece.coverUrl ? resolveCoverURL(piece.coverUrl) : '',
      artist: piece.artistSummary?.artistName ?? '',
      // LastPlayback contains metadata only, not an audio URL or a queue.
      mp3File: null,
    }
    collapsed.value = false
    forceShowPlayerActive.value = true
  }

  function updateCurrentMusicPiece(musicPiece) {
    currentMusicPiece.value.uuid = musicPiece.uuid
    currentMusicPiece.value.cover = musicPiece.cover && resolveCoverURL(musicPiece.cover)
    currentMusicPiece.value.title = musicPiece.title
    currentMusicPiece.value.artist = displayArtist(musicPiece.artist)
    currentMusicPiece.value.mp3File = musicPiece.audio && resolveCoverURL(musicPiece.audio)
  }

  function getCurrentPlaylistIndex() { return currentPlaylistIndex }

  function getCurrentPlaylistUUID(){ return currentPlaylistUUID.value }

  function getCurrentMusicPieceUUID() { return currentMusicPiece.value.uuid }

  function setCurrentPlaylistIndex(index) { currentPlaylistIndex = index }

  function isEmpty(){
    return ( currentMusicPiece.value.uuid === DEFAULT_UUID && currentPlaylistUUID.value === DEFAULT_UUID && playlist.value.length === DEFAULT_PLAYLIST.length && isPlaying.value === false )
  }

  function reset() {
    showArrow.value = true
    collapsed.value = true
    currentMusicPiece.value = {
      uuid: DEFAULT_UUID,
      cover: DEFAULT_COVER,
      title: DEFAULT_TITLE,
      artist: DEFAULT_ARTIST,
      mp3File: null,
    }
    currentPlaylistUUID.value = DEFAULT_UUID
    playlist.value = [...DEFAULT_PLAYLIST]
    orderedPlaylist.value = { orderedPlaylist: [], orderedPlaylistCurrentIndex: 0 }
    isPlaying.value = false
    shuffleOn.value = false
    repeatOn.value = false
    position.value = DEFAULT_POSITION
    forceShowPlayerActive.value = false
    currentPlaylistIndex = null
  }

  return {
    showArrow,
    collapsed,
    currentMusicPiece,
    currentPlaylistUUID,
    playlist,
    orderedPlaylist,
    isPlaying,
    shuffleOn,
    repeatOn,
    volume,
    position,
    forceShowPlayerActive,
    loadPlaylist,
    saveLastPlayback,
    moveForward,
    moveBackward,
    playMusicPiece,
    toggleShuffle,
    toggleRepeat,
    forceShowPlayer,
    updateBottomPlayerAfterLogin,
    updateCurrentMusicPiece,
    getCurrentPlaylistIndex,
    getCurrentPlaylistUUID,
    getCurrentMusicPieceUUID,
    setCurrentPlaylistIndex,
    isEmpty,
    reset,
  }
})
