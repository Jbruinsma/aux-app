import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchAPI, postToAPI } from '@/utils/api.js'
import { displayArtist } from '@/utils/display.js'
import { shufflePlaylist } from '@/utils/playlist.js'
import { API_BASE_URL } from '@/utils/variables.js'

export const useMusicStore = defineStore('music', () => {
  // Default values
  const DEFAULT_UUID = ''
  const DEFAULT_COVER = '/default_cover.png'
  const DEFAULT_TITLE = 'No song playing'
  const DEFAULT_ARTIST = displayArtist('')
  const DEFAULT_PLAYLIST = []
  const DEFAULT_ORDERED_PLAYLIST = {orderedPlaylist: [], orderedPlaylistCurrentIndex: 0}
  const DEFAULT_VOLUME = 0
  const DEFAULT_POSITION = 0

  const firstLoad = ref(true)

  // Reactive state
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

  // Actions
  async function loadPlaylist(playlistUUID, playlistOwner, startingMusicPieceIndex) {

    if ( currentPlaylistUUID.value === playlistUUID && currentPlaylistIndex === startingMusicPieceIndex ) {
      isPlaying.value = true
      return
    }

    try {
      const shuffle = shuffleOn.value

      let url
      if (startingMusicPieceIndex === null) { url = `${API_BASE_URL}/api/playlists/${playlistOwner}/${playlistUUID}/play-shuffled/` }
      else { url = `${API_BASE_URL}/api/playlists/${playlistOwner}/${playlistUUID}/play/${startingMusicPieceIndex}?shuffle=${shuffle}`}

      const data = await fetchAPI(url)

      if ('error' in data) { return }

      currentPlaylistIndex = data.startIndex

      orderedPlaylist.value.orderedPlaylist = data.orderedPlaylist
      orderedPlaylist.value.orderedPlaylistCurrentIndex = startingMusicPieceIndex === null ? data.orderedStartingIndex : data.startIndex
      playlist.value = data.playlist
      currentPlaylistUUID.value = playlistUUID

      const track = playlist.value[currentPlaylistIndex]
      updateCurrentMusicPiece(track)

      position.value = 0
      isPlaying.value = true
      firstLoad.value = false
    } catch (err) {
      console.error('Failed to load playlist:', err)
    }
  }

  async function saveLastPlayback(currentUser) {
    if (!currentUser) return

    const payload = {
      position: position.value,
      current_playlist_index: currentPlaylistIndex,
      current_music_piece_uuid: currentMusicPiece.value.uuid,
      playlist_uuid: currentPlaylistUUID.value,
      repeatOn: repeatOn.value,
      shuffleOn: shuffleOn.value,
    }

    try {
      const url = `${API_BASE_URL}/api/users/${currentUser}/update-last-playback`
      const data = await postToAPI(url, payload)
      console.log('Saved last playback:', data)
    } catch (err) {
      console.error('Failed to save last playback:', err)
    }
  }

  function moveForward() {
    currentPlaylistIndex = currentPlaylistIndex === playlist.value.length - 1 ? 0 : currentPlaylistIndex + 1;

    if (!shuffleOn.value) { orderedPlaylist.value.orderedPlaylistCurrentIndex = currentPlaylistIndex; }

    updateCurrentMusicPiece(playlist.value[currentPlaylistIndex]);
    position.value = 0;
    isPlaying.value = true;
  }

  function moveBackward() {
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
    const wasShuffled = playbackData.shuffleOn
    const wasRepeating = playbackData.repeatOn
    const savedIndex = playbackData.currentPlaylistIndex
    const backendQueue = playbackData.queue
    const originalOrder = playbackData.orderedPlaylist

    shuffleOn.value = wasShuffled
    repeatOn.value = wasRepeating
    currentPlaylistUUID.value = playbackData.currentPlaylistUUID


    orderedPlaylist.value.orderedPlaylist = [...originalOrder]
    orderedPlaylist.value.orderedPlaylistCurrentIndex = savedIndex

    if (wasShuffled) {
      playlist.value = backendQueue
      currentPlaylistIndex = 0
    } else {
      playlist.value = [...originalOrder]
      currentPlaylistIndex = savedIndex
    }

    const track = playlist.value[currentPlaylistIndex]
    updateCurrentMusicPiece(track)
    position.value = playbackData.position
    collapsed.value = false
    forceShowPlayerActive.value = true
  }

  function updateCurrentMusicPiece(musicPiece) {
    currentMusicPiece.value.uuid = musicPiece.uuid
    currentMusicPiece.value.cover = musicPiece.cover
    currentMusicPiece.value.title = musicPiece.title
    currentMusicPiece.value.artist = displayArtist(musicPiece.artist)
    currentMusicPiece.value.mp3File = musicPiece.audio
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
    orderedPlaylist.value = DEFAULT_ORDERED_PLAYLIST
    isPlaying.value = false
    shuffleOn.value = false
    repeatOn.value = false
    position.value = DEFAULT_POSITION
    forceShowPlayerActive.value = false
    currentPlaylistIndex = null
  }

  return {
    // state
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
    // actions
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
    // reset
    reset,
  }
})
