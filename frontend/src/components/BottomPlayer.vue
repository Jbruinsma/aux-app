<template>
  <div>
    <button v-if="showArrow" @click="collapsed = !collapsed" class="collapse-toggle">
      <svg v-if="collapsed" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-6">
        <path stroke-linecap="round" stroke-linejoin="round" d="M4.5 10.5 12 3m0 0 7.5 7.5M12 3v18" />
      </svg>
      <svg v-else xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-6">
        <path stroke-linecap="round" stroke-linejoin="round" d="M19.5 13.5 12 21m0 0-7.5-7.5M12 21V3" />
      </svg>
    </button>

    <div class="bottom-player" :class="{ collapsed }" v-if="shouldShowPlayer">
      <div class="now-playing-info">
        <div class="song-cover" :style="{ backgroundImage: `url(${currentMusicPiece?.cover || '/default_cover.png'})` }"></div>
        <div class="song-details">
          <div class="song-title">{{ currentMusicPiece?.title || 'No song playing' }}</div>
          <div class="song-artist">{{ currentMusicPiece?.artist || '' }}</div>
        </div>
      </div>

      <div class="controls">
        <button @click="prevTrack">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-6">
            <path stroke-linecap="round" stroke-linejoin="round" d="M21 16.811c0 .864-.933 1.406-1.683.977l-7.108-4.061a1.125 1.125 0 0 1 0-1.954l7.108-4.061A1.125 1.125 0 0 1 21 8.689v8.122ZM11.25 16.811c0 .864-.933 1.406-1.683.977l-7.108-4.061a1.125 1.125 0 0 1 0-1.954l7.108-4.061a1.125 1.125 0 0 1 1.683.977v8.122Z" />
          </svg>
        </button>
        <button @click="togglePlay">
          <svg v-if="!isPlaying" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-6">
            <path stroke-linecap="round" stroke-linejoin="round" d="M5.25 5.653c0-.856.917-1.398 1.667-.986l11.54 6.347a1.125 1.125 0 0 1 0 1.972l-11.54 6.347a1.125 1.125 0 0 1-1.667-.986V5.653Z" />
          </svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-6">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 5.25v13.5m-7.5-13.5v13.5" />
          </svg>
        </button>
        <button @click="nextTrack">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-6">
            <path stroke-linecap="round" stroke-linejoin="round" d="M3 8.689c0-.864.933-1.406 1.683-.977l7.108 4.061a1.125 1.125 0 0 1 0 1.954l-7.108 4.061A1.125 1.125 0 0 1 3 16.811V8.69ZM12.75 8.689c0-.864.933-1.406 1.683-.977l7.108 4.061a1.125 1.125 0 0 1 0 1.954l-7.108 4.061a1.125 1.125 0 0 1-1.683-.977V8.69Z" />
          </svg>
        </button>
      </div>

      <div class="right-side">
        <button @click="toggleShuffle" :class="{ active: shuffleOn }">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-6">
            <path stroke-linecap="round" stroke-linejoin="round" d="M7.5 21 3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5" />
          </svg>
        </button>
        <button @click="toggleRepeat" :class="{ active: repeatOn }">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-6">
            <path stroke-linecap="round" stroke-linejoin="round" d="M19.5 12c0-1.232-.046-2.453-.138-3.662a4.006 4.006 0 0 0-3.7-3.7 48.678 48.678 0 0 0-7.324 0 4.006 4.006 0 0 0-3.7 3.7c-.017.22-.032.441-.046.662M19.5 12l3-3m-3 3-3-3m-12 3c0 1.232.046 2.453.138 3.662a4.006 4.006 0 0 0 3.7 3.7 48.656 48.656 0 0 0 7.324 0 4.006 4.006 0 0 0 3.7-3.7c.017-.22.032-.441.046-.662M4.5 12l3 3m-3-3-3 3" />
          </svg>
        </button>
        <div class="volume">
          <input type="range" min="0" max="1" step="0.01" v-model="volume" />
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-6 volume-icon">
            <path stroke-linecap="round" stroke-linejoin="round" d="M19.114 5.636a9 9 0 0 1 0 12.728M16.463 8.288a5.25 5.25 0 0 1 0 7.424M6.75 8.25l4.72-4.72a.75.75 0 0 1 1.28.53v15.88a.75.75 0 0 1-1.28.53l-4.72-4.72H4.51c-.88 0-1.704-.507-1.938-1.354A9.009 9.009 0 0 1 2.25 12c0-.83.112-1.633.322-2.396C2.806 8.756 3.63 8.25 4.51 8.25H6.75Z" />
          </svg>
        </div>
      </div>
    </div>

    <audio v-if="currentMusicPiece?.mp3File" ref="audioRef" :key="`${currentMusicPiece.uuid}-${forceShowPlayer}`" :src="currentMusicPiece.mp3File" preload="metadata" controls @loadedmetadata="onLoadedMetadata" @timeupdate="onTimeUpdate" @ended="onTrackEnd" style="width:0; height:0; opacity:0;"></audio>

    <div class="progress-bar-container" :class="{ collapsed }" v-if="shouldShowPlayer">
      <span class="time">{{ formattedCurrentTime }}</span>
      <input type="range" min="0" :max="Math.floor(duration)" step="any" :value="progress" @input="e => progress = parseFloat(e.target.value)" />
      <span class="time">{{ formattedDuration }}</span>
    </div>
  </div>
</template>



<script setup>
import { computed, watch, nextTick, ref } from 'vue'
import { useMusicStore } from '@/stores/music.js'
import { useUserStore } from '@/stores/user.js'

const userStore = useUserStore()
const musicStore = useMusicStore()

const collapsed = ref(false)
const duration = ref(0)
const audioRef = ref(null)
const progress  = computed({
    get: () => musicStore.position,
    set: v  => { musicStore.position = v }
})

const showArrow = computed(() => musicStore.forceShowPlayerActive || musicStore.currentMusicPiece !== null)
const shouldShowPlayer = showArrow

const currentMusicPiece = computed(() => musicStore.currentMusicPiece)
const isPlaying = computed(() => musicStore.isPlaying)
const shuffleOn = computed(() => musicStore.shuffleOn)
const repeatOn = computed(() => musicStore.repeatOn)
const volume = computed({
  get: () => musicStore.volume,
  set: val => (musicStore.volume = val)
})

const saveLastPlayback = musicStore.saveLastPlayback

const forceShowPlayer = computed(() => musicStore.forceShowPlayerActive)

watch(isPlaying, playing => {
  const audio = audioRef.value
  if (!audio) return
  if (playing) {
    audio.play().catch(() => {
    })
  } else {
    audio.pause()
  }
}, { immediate: true })

watch(volume, v => {
  const audio = audioRef.value
  if (audio) audio.volume = v
}, { immediate: true })

const formattedDuration = computed(() => {
  const m = Math.floor(duration.value / 60)
  const s = String(Math.floor(duration.value % 60)).padStart(2, '0')
  return `${m}:${s}`
})
const formattedCurrentTime = computed(() => {
  const m = Math.floor(progress.value / 60)
  const s = String(Math.floor(progress.value % 60)).padStart(2, '0')
  return `${m}:${s}`
})

watch(progress, val => {
  const audio = audioRef.value
  if (audio && Math.abs(audio.currentTime - val) > 0.5) {
    audio.currentTime = val
  }
})

watch(
  () => currentMusicPiece.value?.mp3File,
  async newUrl => {
    if (!newUrl || !audioRef.value) return
    await nextTick()
    audioRef.value.load()
    audioRef.value.volume = volume.value
    if (isPlaying.value) {
      audioRef.value.play().catch(() => {})
    }
  },
  { immediate: false }
)

watch(
  () => musicStore.forceShowPlayerActive,
  async (nowVisible) => {
    if (nowVisible && audioRef.value) {
      await nextTick()
      audioRef.value.load()
      audioRef.value.currentTime = musicStore.position
    }
  }
)

watch(() => musicStore.position,
  async (newPos) => {
  const audio = audioRef.value
    if (!audio || isPlaying.value) return
    await nextTick()
    audio.currentTime = newPos
})

async function onLoadedMetadata() {
  const audio = audioRef.value
  duration.value = audio.duration

  if (musicStore.position > 0 && musicStore.position < duration.value) {
    audio.currentTime = musicStore.position
  } else {
    musicStore.position = 0
    audio.currentTime = 0
  }

  audio.volume = volume.value
  if (isPlaying.value) {
    audio.play().catch(() => {})
  }
}

function onTrackEnd(){
  if (!audioRef.value) return
  if (musicStore.repeatOn) {
    progress.value = 0
    audioRef.value.play().catch(() => {})
  } else { nextTrack() }
}

function onTimeUpdate() {
  if (!audioRef.value) return
  progress.value = audioRef.value.currentTime
}

function togglePlay() {
  if (!musicStore.isEmpty()) {
    musicStore.isPlaying = !musicStore.isPlaying
    const currentUser = userStore.userData?.username
    if (currentUser === null || currentUser === undefined) {
      return
    }
    if (!musicStore.isPlaying) {
      saveLastPlayback(currentUser)
    }
  }
}

function toggleShuffle() {
  musicStore.toggleShuffle()
}

function toggleRepeat() {
  musicStore.toggleRepeat()
}

function prevTrack() {
  const currentUser = userStore.userData?.username
  musicStore.moveBackward()
  musicStore.saveLastPlayback(currentUser)
}

function nextTrack() {
  const currentUser = userStore.userData?.username
  musicStore.moveForward()
  musicStore.saveLastPlayback(currentUser)
}
</script>

<style scoped>
.bottom-player { align-items: center; background: #1e1e1e; border-top: 2px solid rgba(255,255,255,0.1); bottom: 30px; color: #f0f0f0; display: flex; justify-content: space-between; left: 0; padding: 0.5rem 1.5rem; position: fixed; right: 0; transition: transform 0.3s; z-index: 100; }
.bottom-player.collapsed { transform: translateY(calc(100% + 30px)); }
.collapse-toggle { align-items: center; background: #1e1e1e; border: 2px solid rgba(255,255,255,0.1); border-radius: 50%; bottom: 100px; color: #f0f0f0; cursor: pointer; display: flex; padding: 0.4rem; position: fixed; right: 1.5rem; transition: background 0.2s, color 0.2s; z-index: 101; }
.controls { align-items: center; display: flex; gap: 1.5rem; left: 50%; position: absolute; transform: translateX(-50%); }
.controls button { align-items: center; background: none; border: none; color: #f0f0f0; cursor: pointer; display: flex; font-size: 1.5rem; }
.controls button:hover { color: #4caf50; }
.now-playing-info { align-items: center; color: #f0f0f0; display: flex; gap: 1rem; }
.progress-bar-container { align-items: center; background: #1e1e1e; bottom: 0; color: #aaa; display: flex; gap: 0.8rem; left: 0; padding: 0.3rem 1.5rem; position: fixed; right: 0; transition: transform 0.3s; z-index: 99; }
.progress-bar-container.collapsed { transform: translateY(100%); }
.progress-bar-container input[type="range"] { -webkit-appearance: none; background: transparent; cursor: pointer; flex: 1; height: 6px; }
.progress-bar-container input[type="range"]::-moz-range-thumb { background: #fff; border-radius: 50%; height: 14px; width: 14px; }
.progress-bar-container input[type="range"]::-moz-range-track { background: #666; border-radius: 3px; height: 6px; }
.progress-bar-container input[type="range"]::-webkit-slider-runnable-track { background: #666; border-radius: 3px; height: 6px; }
.progress-bar-container input[type="range"]::-webkit-slider-thumb { -webkit-appearance: none; background: #fff; border-radius: 50%; height: 14px; margin-top: -4px; width: 14px; }
.right-side { align-items: center; display: flex; gap: 1rem; }
.right-side button { align-items: center; background: none; border: none; color: #f0f0f0; cursor: pointer; display: flex; transition: color 0.2s, transform 0.2s; }
.right-side button.active { color: #4caf50; transform: scale(1.2); }
.right-side button.active:hover { color: #fff; }
.size-6 { height: 24px; width: 24px; }
.song-artist { color: #aaa; font-size: 0.85rem; }
.song-cover { background: #444 center; background-size: cover; border-radius: 8px; height: 50px; width: 50px; }
.song-details { display: flex; flex-direction: column; }
.song-title { font-size: 1rem; font-weight: 500; }
.time { font-size: 0.85rem; }
.volume input[type="range"] { -webkit-appearance: none; background: transparent; cursor: pointer; height: 6px; width: 100px; }
.volume input[type="range"]::-moz-range-thumb { background: #fff; border-radius: 50%; height: 14px; width: 14px; }
.volume input[type="range"]::-moz-range-track { background: #666; border-radius: 3px; height: 6px; }
.volume input[type="range"]::-webkit-slider-runnable-track { background: #666; border-radius: 3px; height: 6px; }
.volume input[type="range"]::-webkit-slider-thumb { -webkit-appearance: none; background: #fff; border-radius: 50%; height: 14px; margin-top: -4px; width: 14px; }
.volume-icon { width: 15px; height: 15px; margin-left: 0.5rem; }

@media (max-width:600px){
  .bottom-player{flex-direction:column;align-items:center;gap:1rem;padding:1rem;}
  .now-playing-info{flex-direction:column;align-items:center;text-align:center;}
  .controls{justify-content:center;width:100%;}
  .right-side{justify-content:center;width:100%;flex-wrap:wrap;}
}


</style>
