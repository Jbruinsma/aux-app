<template>
  <div>
    <button
      v-if="showArrow"
      type="button"
      class="collapse-toggle"
      :class="{ collapsed }"
      :aria-label="collapsed ? 'Show player' : 'Hide player'"
      :aria-expanded="collapsed ? 'false' : 'true'"
      @click="collapsed = !collapsed"
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path v-if="collapsed" d="m18 15-6-6-6 6" />
        <path v-else d="m6 9 6 6 6-6" />
      </svg>
    </button>

    <div class="bottom-player" :class="{ collapsed }" v-if="shouldShowPlayer">
      <div class="now-playing-info">
        <img v-if="currentMusicPiece?.cover" class="song-cover" :src="currentMusicPiece.cover" alt="" />
        <span v-else class="song-cover cover-empty" aria-hidden="true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" />
          </svg>
        </span>
        <div class="song-details">
          <p class="song-title">{{ currentMusicPiece?.title || 'No song playing' }}</p>
          <p v-if="currentMusicPiece?.artist || uploader" class="song-artist">
            <router-link
              v-if="currentMusicPiece.artist && currentMusicPiece.artistId"
              class="artist-name artist-link"
              :to="{ name: 'Artist', params: { artistId: currentMusicPiece.artistId } }"
            >{{ currentMusicPiece.artist }}</router-link>
            <span v-else-if="currentMusicPiece.artist" class="artist-name">{{ currentMusicPiece.artist }}</span>
            <template v-if="uploader">
              <span v-if="currentMusicPiece.artist" aria-hidden="true">·</span>
              <router-link
                class="uploader"
                :to="{ name: 'Profile', params: { username: uploader.username } }"
                :aria-label="`Uploaded by ${uploader.username}`"
                :title="`Uploaded by ${uploader.username}`"
              >
                <img v-if="uploader.pfpUrl" class="uploader-pfp" :src="resolveCoverURL(uploader.pfpUrl)" alt="" />
                <span v-else class="uploader-pfp uploader-initial" aria-hidden="true">{{ uploader.username.charAt(0).toUpperCase() }}</span>
                <span class="uploader-name">{{ uploader.username }}</span>
              </router-link>
            </template>
          </p>
        </div>
      </div>

      <div class="controls">
        <button type="button" class="icon-btn" :disabled="!canPlay || !musicStore.playlist.length" aria-label="Previous" @click="prevTrack">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M6 5h2v14H6zM19 5.5v13a.5.5 0 0 1-.77.42L9 12.42a.5.5 0 0 1 0-.84l9.23-6.5a.5.5 0 0 1 .77.42z" />
          </svg>
        </button>
        <button type="button" class="play" :disabled="!canPlay" :aria-label="isPlaying ? 'Pause' : 'Play'" @click="togglePlay">
          <svg v-if="!isPlaying" width="16" height="16" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5v11l9-5.5z" fill="currentColor" /></svg>
          <svg v-else width="16" height="16" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5h3v11H3zM8 1.5h3v11H8z" fill="currentColor" /></svg>
        </button>
        <button type="button" class="icon-btn" :disabled="!canPlay || !musicStore.playlist.length" aria-label="Next" @click="nextTrack">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M16 5h2v14h-2zM5 5.5v13a.5.5 0 0 0 .77.42L15 12.42a.5.5 0 0 0 0-.84L5.77 5.08A.5.5 0 0 0 5 5.5z" />
          </svg>
        </button>
      </div>

      <div class="right-side">
        <button type="button" class="icon-btn toggle" :class="{ active: shuffleOn }" :disabled="!canPlay || !musicStore.currentPlaylistUUID || !musicStore.playlist.length" aria-label="Shuffle" :aria-pressed="shuffleOn ? 'true' : 'false'" @click="toggleShuffle">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m18 14 4 4-4 4" /><path d="m18 2 4 4-4 4" />
            <path d="M2 18h1.97a4 4 0 0 0 3.3-1.7l5.46-8.6a4 4 0 0 1 3.3-1.7H22" />
            <path d="M2 6h1.97a4 4 0 0 1 3.3 1.7l.47.73M22 18h-5.97a4 4 0 0 1-3.3-1.7l-.47-.73" />
          </svg>
        </button>
        <button
          type="button"
          class="icon-btn toggle"
          :class="{ active: repeatMode !== 'OFF' }"
          :disabled="!canPlay"
          :aria-label="REPEAT_LABELS[repeatMode]"
          :title="REPEAT_LABELS[repeatMode]"
          @click="musicStore.cycleRepeat()"
        >
          <svg v-if="repeatMode === 'PIECE'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m17 2 4 4-4 4" /><path d="M3 11v-1a4 4 0 0 1 4-4h14" />
            <path d="m7 22-4-4 4-4" /><path d="M21 13v1a4 4 0 0 1-4 4H3" /><path d="M11 10h1v4" />
          </svg>
          <svg v-else-if="repeatMode === 'PLAYLIST'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m17 2 4 4-4 4" /><path d="M3 11v-1a4 4 0 0 1 4-4h14" />
            <path d="m7 22-4-4 4-4" /><path d="M21 13v1a4 4 0 0 1-4 4H3" />
          </svg>
          <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m17 2 4 4-4 4" /><path d="M3 11v-1a4 4 0 0 1 4-4h14" />
            <path d="m7 22-4-4 4-4" /><path d="M21 13v1a4 4 0 0 1-4 4H3" /><path d="m3 3 18 18" />
          </svg>
        </button>
        <div class="volume">
          <svg class="volume-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M11 4.7a.7.7 0 0 0-1.2-.5L6.4 7.6A1.4 1.4 0 0 1 5.4 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.4a1.4 1.4 0 0 1 1 .4l3.4 3.4a.7.7 0 0 0 1.2-.5z" />
            <path d="M16 9a5 5 0 0 1 0 6" /><path d="M19.4 18.4a9 9 0 0 0 0-12.8" />
          </svg>
          <input class="slider" type="range" min="0" max="1" step="0.01" v-model="volume" aria-label="Volume" :style="{ '--fill': volumeFill }" />
        </div>
      </div>
    </div>

    <audio v-if="currentMusicPiece?.mp3File" ref="audioRef" :key="`${currentMusicPiece.uuid}-${forceShowPlayer}`" :src="currentMusicPiece.mp3File" preload="metadata" controls @loadedmetadata="onLoadedMetadata" @timeupdate="onTimeUpdate" @ended="onTrackEnd" @error="onAudioError" @seeking="onSeeking" style="width:0; height:0; opacity:0;"></audio>

    <div class="progress-bar-container" :class="{ collapsed }" v-if="shouldShowPlayer">
      <span class="time">{{ formattedCurrentTime }}</span>
      <input
        class="slider"
        type="range"
        min="0"
        :max="Math.floor(duration)"
        step="any"
        :value="progress"
        :disabled="!canPlay" aria-label="Seek"
        :aria-valuetext="`${formattedCurrentTime} of ${formattedDuration}`"
        :style="{ '--fill': progressFill }"
        @input="e => progress = parseFloat(e.target.value)"
      />
      <span class="time">{{ formattedDuration }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, watch, watchEffect, nextTick, onBeforeUnmount, ref } from 'vue'
import { useMusicStore } from '@/stores/music.js'
import { useUserStore } from '@/stores/user.js'
import { resolveCoverURL } from '@/utils/display.js'

const userStore = useUserStore()
const musicStore = useMusicStore()

const collapsed = ref(false)

// Tells the page how much room to leave under its footer (aux_styles.css)
watchEffect(() => {
  const root = document.documentElement.classList
  root.toggle('player-open', !collapsed.value)
  root.toggle('player-collapsed', collapsed.value)
})
onBeforeUnmount(() => document.documentElement.classList.remove('player-open', 'player-collapsed'))
const duration = ref(0)
const audioRef = ref(null)
const progress  = computed({
    get: () => musicStore.position,
    set: v  => { musicStore.position = v }
})

const showArrow = computed(() => musicStore.forceShowPlayerActive || musicStore.currentMusicPiece !== null)
const shouldShowPlayer = showArrow

const currentMusicPiece = computed(() => musicStore.currentMusicPiece)
const uploader = computed(() => currentMusicPiece.value?.uploader ?? null)
// A restored song has no audio yet, but Play can fetch it
const canPlay = computed(() => Boolean(currentMusicPiece.value?.mp3File || (currentMusicPiece.value?.uuid && musicStore.playlist.length)))
const isPlaying = computed(() => musicStore.isPlaying)
const shuffleOn = computed(() => musicStore.shuffleOn)
const repeatMode = computed(() => musicStore.repeatMode)
const REPEAT_LABELS = { OFF: 'Repeat: off', PLAYLIST: 'Repeat: playlist', PIECE: 'Repeat: this song' }
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
    audio.play().catch(onPlayBlocked)
  } else {
    audio.pause()
  }
}, { immediate: true })

watch(volume, v => {
  const audio = audioRef.value
  if (audio) audio.volume = v
}, { immediate: true })

const progressFill = computed(() => (duration.value ? `${Math.min(100, (progress.value / duration.value) * 100)}%` : '0%'))
const volumeFill = computed(() => `${volume.value * 100}%`)

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
      audioRef.value.play().catch(onPlayBlocked)
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
  musicStore.duration = audio.duration
  lastTime = null
  const target = musicStore.seekOnLoad ?? musicStore.position
  musicStore.seekOnLoad = null

  if (target > 0 && target < duration.value) {
    musicStore.position = target
    audio.currentTime = target
  } else {
    musicStore.position = 0
    audio.currentTime = 0
  }

  audio.volume = volume.value
  if (isPlaying.value) {
    audio.play().catch(onPlayBlocked)
  }
}

function onTrackEnd() {
  if (!audioRef.value) return
  lastTime = null
  if (musicStore.repeatMode === 'PIECE') {
    musicStore.startRepeat()
    progress.value = 0
    audioRef.value.play().catch(onPlayBlocked)
  } else {
    musicStore.finishListening()
    nextTrack()
  }
}

const PREFETCH_BEFORE_END = 20
// Steps between timeupdate events are about a quarter second; anything bigger is a seek, not listening
const MAX_LISTEN_STEP = 1.5
let lastTime = null

function onTimeUpdate() {
  const audio = audioRef.value
  if (!audio || musicStore.seekOnLoad !== null) return
  if (lastTime !== null) {
    const step = audio.currentTime - lastTime
    if (step > 0 && step < MAX_LISTEN_STEP) musicStore.recordListening(step)
  }
  lastTime = audio.currentTime
  progress.value = audio.currentTime
  if (audio.duration - audio.currentTime < PREFETCH_BEFORE_END) musicStore.prefetchNext()
}

// The browser refused to start audio (no click yet, for example after a reload): show Play instead of a stuck Pause
function onPlayBlocked(err) {
  if (err?.name === 'NotAllowedError') musicStore.isPlaying = false
}

function onSeeking() {
  lastTime = null
}

function onAudioError() {
  musicStore.recoverStream()
}

function togglePlay() {
  if (!canPlay.value) return
  if (!currentMusicPiece.value?.mp3File) {
    musicStore.resume()
    return
  }
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
button:disabled, input:disabled { cursor: not-allowed; opacity: 0.4; }
.bottom-player,
.progress-bar-container { position: fixed; left: 0; right: 0; background: var(--surface-alt); color: var(--ink); font-family: var(--font-sans); transition: transform var(--dur-med) var(--ease); }
.bottom-player { bottom: 32px; z-index: 100; display: flex; align-items: center; justify-content: space-between; gap: var(--space-4); padding: var(--space-3) var(--page-gutter) var(--space-1); border-top: 1px solid var(--line); }
.bottom-player.collapsed { transform: translateY(calc(100% + 32px)); }
.progress-bar-container { bottom: 0; z-index: 99; height: 32px; display: flex; align-items: center; gap: var(--space-3); padding: 0 var(--page-gutter); }
.progress-bar-container.collapsed { transform: translateY(100%); }
.time { font: 500 12px/16px var(--font-sans); color: var(--ink-muted); font-variant-numeric: tabular-nums; min-width: 32px; }
.time:last-child { text-align: right; }

.collapse-toggle { position: fixed; right: var(--page-gutter); bottom: 108px; z-index: 101; display: grid; place-items: center; width: 36px; height: 36px; padding: 0; border-radius: var(--radius-pill); border: 1px solid var(--line-strong); background: var(--surface); color: var(--ink-muted); cursor: pointer; }
.collapse-toggle:hover { color: var(--ink); background: var(--surface-alt); }
.collapse-toggle.collapsed { bottom: var(--space-4); }

.now-playing-info { display: flex; align-items: center; gap: var(--space-3); min-width: 0; flex: 1; }
.song-cover { width: 48px; height: 48px; flex: none; border-radius: var(--radius-sm); object-fit: cover; background: var(--surface); }
.cover-empty { display: grid; place-items: center; color: var(--ink-muted); border: 1px solid var(--line); }
.song-details { min-width: 0; }
.song-title { margin: 0; font: 600 15px/22px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.song-artist { align-items: center; color: var(--ink-muted); display: flex; font: 500 14px/20px var(--font-sans); gap: var(--space-2); margin: 0; min-width: 0; white-space: nowrap; }
.artist-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
.artist-link { color: inherit; text-decoration: none; }
.artist-link:hover { color: var(--link); text-decoration: underline; }
.uploader { align-items: center; color: inherit; display: inline-flex; flex: none; gap: var(--space-1); max-width: 50%; text-decoration: none; }
.uploader:hover { color: var(--link); text-decoration: underline; }
.uploader-pfp { border-radius: var(--radius-pill); flex: none; height: 16px; object-fit: cover; width: 16px; }
.uploader-initial { background: var(--primary); color: var(--on-primary); display: grid; font: 700 10px/1 var(--font-sans); place-items: center; }
.uploader-name { overflow: hidden; text-overflow: ellipsis; }

.controls { position: absolute; left: 50%; transform: translateX(-50%); display: flex; align-items: center; gap: var(--space-4); }
.icon-btn { position: relative; display: grid; place-items: center; width: 36px; height: 36px; padding: 0; border: 0; border-radius: var(--radius-pill); background: none; color: var(--ink-muted); cursor: pointer; }
.icon-btn:hover { color: var(--ink); background: var(--surface); }
.play { display: grid; place-items: center; width: 44px; height: 44px; padding: 0; border: 0; border-radius: var(--radius-pill); background: var(--primary); color: var(--on-primary); cursor: pointer; }
.play:hover { background: var(--purple-hover); }

.right-side { display: flex; align-items: center; justify-content: flex-end; gap: var(--space-2); flex: 1; }
.toggle.active { color: var(--link); }
.toggle.active::after { content: ''; position: absolute; bottom: 2px; width: 4px; height: 4px; border-radius: var(--radius-pill); background: currentColor; }
.volume { display: flex; align-items: center; gap: var(--space-2); margin-left: var(--space-2); color: var(--ink-muted); }
.volume .slider { width: 100px; }

.slider { -webkit-appearance: none; appearance: none; flex: 1; height: 16px; margin: 0; background: transparent; cursor: pointer; }
.slider::-webkit-slider-runnable-track { height: 4px; border-radius: var(--radius-pill); background: linear-gradient(to right, var(--purple) var(--fill, 0%), var(--line) var(--fill, 0%)); }
.slider::-moz-range-track { height: 4px; border-radius: var(--radius-pill); background: var(--line); }
.slider::-moz-range-progress { height: 4px; border-radius: var(--radius-pill); background: var(--purple); }
.slider::-webkit-slider-thumb { -webkit-appearance: none; width: 12px; height: 12px; margin-top: -4px; border-radius: var(--radius-pill); background: var(--purple); border: 0; }
.slider::-moz-range-thumb { width: 12px; height: 12px; border-radius: var(--radius-pill); background: var(--purple); border: 0; }

.bottom-player :focus-visible,
.progress-bar-container :focus-visible,
.collapse-toggle:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }

@media (max-width: 600px) {
  .bottom-player { flex-direction: column; gap: var(--space-2); padding-top: var(--space-3); }
  .controls { position: static; transform: none; }
  .right-side { justify-content: center; }
  .collapse-toggle { bottom: 196px; }
}
</style>
