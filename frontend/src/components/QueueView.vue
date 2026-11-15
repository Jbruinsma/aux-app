<template>
  <section
    ref="rootRef"
    class="queue-view"
    :class="{ open, glow: Boolean(glowColor), browsing: view !== current, paused: !isPlaying }"
    :style="{ '--glow': glowColor || 'transparent', '--view': view }"
    role="dialog"
    aria-modal="true"
    aria-label="Queue"
    :aria-hidden="open ? 'false' : 'true'"
    :inert="!open"
    @keydown="onKeydown"
  >
    <div class="top">
      <div class="top-left">
        <button ref="closeRef" type="button" class="icon-btn" aria-label="Close queue" @click="emit('close')">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
        </button>
        <button type="button" class="back" :tabindex="view !== current ? 0 : -1" @click="view = current">Back to now playing</button>
      </div>
      <p class="label">
        <template v-if="playlistLink">Playing from <router-link :to="playlistLink.to">{{ playlistLink.name || 'playlist' }}</router-link></template>
        <template v-else>Now playing</template>
      </p>
      <div class="modes">
        <button type="button" class="icon-btn toggle" :class="{ active: shuffleOn }" :disabled="!canShuffle" aria-label="Shuffle" :aria-pressed="shuffleOn ? 'true' : 'false'" @click="emit('shuffle')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m18 14 4 4-4 4" /><path d="m18 2 4 4-4 4" /><path d="M2 18h1.97a4 4 0 0 0 3.3-1.7l5.46-8.6a4 4 0 0 1 3.3-1.7H22" /><path d="M2 6h1.97a4 4 0 0 1 3.3 1.7l.47.73M22 18h-5.97a4 4 0 0 1-3.3-1.7l-.47-.73" /></svg>
        </button>
        <button type="button" class="icon-btn toggle" :class="{ active: repeatMode !== 'OFF' }" :disabled="!canPlay" :aria-label="repeatLabel" :title="repeatLabel" @click="emit('repeat')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m17 2 4 4-4 4" /><path d="M3 11v-1a4 4 0 0 1 4-4h14" /><path d="m7 22-4-4 4-4" /><path d="M21 13v1a4 4 0 0 1-4 4H3" />
            <path v-if="repeatMode === 'PIECE'" d="M11 10h1v4" />
            <path v-else-if="repeatMode === 'OFF'" d="m3 3 18 18" />
          </svg>
        </button>
      </div>
    </div>

    <div class="stage" @wheel="onWheel">
      <button type="button" class="nudge prev" :disabled="view <= 0" aria-label="Show earlier songs" @click="browse(-1)">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>
      </button>
      <ol class="track">
        <li v-for="slot in visibleSlots" :key="slot.key" class="slot-item" :style="{ '--i': slot.index }">
          <button
            type="button"
            class="slot"
            :class="{ on: slot.index === current, past: slot.index < current }"
            :aria-current="slot.index === current ? 'true' : null"
            :aria-label="slot.index === current ? `Now playing: ${slot.label}` : `Play ${slot.label}`"
            @click="pick(slot.index)"
          >
            <img v-if="slot.cover" class="cover" :src="slot.cover" alt="" decoding="async" />
            <span v-else class="cover cover-empty" aria-hidden="true">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
            </span>
            <span class="cap">
              <span class="cap-title"><span v-if="slot.index === current" class="eq" aria-hidden="true"><i></i><i></i><i></i></span>{{ slot.title }}</span>
              <span v-if="slot.subtitle" class="cap-artist">{{ slot.subtitle }}</span>
            </span>
          </button>
        </li>
      </ol>
      <button type="button" class="nudge next" :disabled="view >= slots.length - 1" aria-label="Show later songs" @click="browse(1)">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 18 6-6-6-6" /></svg>
      </button>
    </div>

    <div class="bottom">
      <div class="scrub">
        <span class="time">{{ currentTimeLabel }}</span>
        <input
          class="slider"
          type="range"
          min="0"
          :max="Math.floor(duration)"
          step="any"
          :value="progress"
          :disabled="!canPlay"
          aria-label="Seek"
          :aria-valuetext="`${currentTimeLabel} of ${durationLabel}`"
          :style="{ '--fill': progressFill }"
          @input="e => emit('seek', parseFloat(e.target.value))"
        />
        <span class="time">{{ durationLabel }}</span>
      </div>
      <div class="controls">
        <span></span>
        <div class="transport">
          <button type="button" class="icon-btn big" :disabled="!canSkip" aria-label="Previous" @click="emit('prev')">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M6 5h2v14H6zM19 5.5v13a.5.5 0 0 1-.77.42L9 12.42a.5.5 0 0 1 0-.84l9.23-6.5a.5.5 0 0 1 .77.42z" /></svg>
          </button>
          <button type="button" class="play" :disabled="!canPlay" :aria-label="isPlaying ? 'Pause' : 'Play'" @click="emit('toggle-play')">
            <svg v-if="!isPlaying" width="22" height="22" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5v11l9-5.5z" fill="currentColor" /></svg>
            <svg v-else width="22" height="22" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5h3v11H3zM8 1.5h3v11H8z" fill="currentColor" /></svg>
          </button>
          <button type="button" class="icon-btn big" :disabled="!canSkip" aria-label="Next" @click="emit('next')">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M16 5h2v14h-2zM5 5.5v13a.5.5 0 0 0 .77.42L15 12.42a.5.5 0 0 0 0-.84L5.77 5.08A.5.5 0 0 0 5 5.5z" /></svg>
          </button>
        </div>
        <div class="volume">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M11 4.7a.7.7 0 0 0-1.2-.5L6.4 7.6A1.4 1.4 0 0 1 5.4 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.4a1.4 1.4 0 0 1 1 .4l3.4 3.4a.7.7 0 0 0 1.2-.5z" /><path d="M16 9a5 5 0 0 1 0 6" /><path d="M19.4 18.4a9 9 0 0 0 0-12.8" /></svg>
          <input class="slider" type="range" min="0" max="1" step="0.01" :value="volume" aria-label="Volume" :style="{ '--fill': volumeFill }" @input="e => emit('volume', parseFloat(e.target.value))" />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
// Desktop queue (above 720px): the playing song's cover large in the middle, songs already played to the left and
// what's up next to the right, over a background tinted with the cover's color. It replaces the bottom bar while open,
// so it carries all of the bar's controls. Like MobilePlayer, it only shows state and sends the controls up to
// BottomPlayer
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { resolveCoverURL } from '@/utils/display.js'
import { useCoverColor } from '@/utils/coverColor.js'

const props = defineProps({
  open: Boolean,
  // The fetched queue in play order (MusicPieceOverview items) and where the current song is in it
  queue: { type: Array, default: () => [] },
  index: { type: Number, default: null },
  // The current song as BottomPlayer has it, used when there's no queue to show
  piece: { type: Object, default: null },
  // { to, name } for "Playing from", or null when the music isn't from a playlist
  playlistLink: { type: Object, default: null },
  isPlaying: Boolean,
  canPlay: Boolean,
  canSkip: Boolean,
  canShuffle: Boolean,
  shuffleOn: Boolean,
  repeatMode: { type: String, default: 'OFF' },
  repeatLabel: { type: String, default: '' },
  progress: { type: Number, default: 0 },
  duration: { type: Number, default: 0 },
  progressFill: { type: String, default: '0%' },
  currentTimeLabel: { type: String, default: '0:00' },
  durationLabel: { type: String, default: '0:00' },
  volume: { type: Number, default: 1 },
  volumeFill: { type: String, default: '100%' },
})
const emit = defineEmits(['close', 'play-at', 'toggle-play', 'prev', 'next', 'shuffle', 'repeat', 'seek', 'volume'])

const route = useRoute()
const rootRef = ref(null)
const closeRef = ref(null)

// Covers are only rendered this many places either side of the one in view
const WINDOW = 7

const slots = computed(() => {
  if (props.queue.length && props.index !== null) {
    return props.queue.map((p, i) => {
      const artist = p.artistSummary?.artistName ?? ''
      const subtitle = artist || p.uploader?.username || ''
      return {
        key: `${i}-${p.musicPieceId}`,
        index: i,
        title: p.name,
        subtitle,
        label: subtitle ? `${p.name} by ${subtitle}` : p.name,
        cover: p.coverUrl ? resolveCoverURL(p.coverUrl) : '',
      }
    })
  }
  if (!props.piece?.uuid) return []
  const subtitle = props.piece.artist || props.piece.uploader?.username || ''
  return [{ key: props.piece.uuid, index: 0, title: props.piece.title, subtitle, label: subtitle ? `${props.piece.title} by ${subtitle}` : props.piece.title, cover: props.piece.cover }]
})

const current = computed(() => (props.queue.length && props.index !== null ? props.index : 0))
// The cover in the middle. Browsing moves it without changing the song; a new song brings it back
const view = ref(current.value)
watch(current, (i) => { view.value = i })

const visibleSlots = computed(() => slots.value.slice(Math.max(0, view.value - WINDOW), view.value + WINDOW + 1))

const glowColor = useCoverColor(() => props.piece?.cover || '')

function browse(step) {
  view.value = Math.min(Math.max(0, view.value + step), Math.max(0, slots.value.length - 1))
}

function pick(i) {
  if (i === current.value) return
  emit('play-at', i)
}

// Trackpads scroll sideways in steps; one step per 80px
let wheelSum = 0
function onWheel(e) {
  if (Math.abs(e.deltaX) <= Math.abs(e.deltaY)) return
  e.preventDefault()
  wheelSum += e.deltaX
  if (Math.abs(wheelSum) < 80) return
  browse(wheelSum > 0 ? 1 : -1)
  wheelSum = 0
}

watch(() => props.open, async (isOpen) => {
  if (!isOpen) return
  view.value = current.value
  await nextTick()
  closeRef.value?.focus({ preventScroll: true })
})

// Following a link (the playlist name) leaves the queue
watch(() => route?.fullPath, () => { if (props.open) emit('close') })

// Escape closes; ←/→ browse (except on the sliders, which use them); Tab stays inside while it's open
function onKeydown(e) {
  if (e.key === 'Escape') {
    e.preventDefault()
    emit('close')
    return
  }
  const onSlider = e.target?.type === 'range'
  if ((e.key === 'ArrowLeft' || e.key === 'ArrowRight') && !onSlider) {
    e.preventDefault()
    browse(e.key === 'ArrowLeft' ? -1 : 1)
    return
  }
  if (e.key !== 'Tab') return
  const focusable = [...rootRef.value.querySelectorAll('button:not([disabled]):not([tabindex="-1"]), a[href], input:not([disabled])')]
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus() }
  else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus() }
}
</script>

<style scoped>
/* The cover's color radiates from behind the middle cover and fades into surface. The style guide's one gradient */
.queue-view { --cover: clamp(180px, min(28vw, calc(100dvh - 400px)), 340px); --gap: var(--space-8); background: var(--surface); color: var(--ink); display: flex; flex-direction: column; font-family: var(--font-sans); inset: 0; position: fixed; transform: translateY(100%); transition: --glow 700ms var(--ease), transform var(--dur-med) var(--ease), visibility 0s linear var(--dur-med); visibility: hidden; z-index: 150; }
.queue-view.open { transform: none; transition: --glow 700ms var(--ease), transform var(--dur-med) var(--ease), visibility 0s; visibility: visible; }
.queue-view.glow { background: radial-gradient(ellipse 55% 70% at 50% 42%, color-mix(in srgb, var(--glow) var(--glow-in), var(--surface)) 0%, color-mix(in srgb, var(--glow) var(--glow-mid), var(--surface)) 45%, var(--surface) 100%); }
.queue-view :focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
button:disabled, input:disabled { cursor: not-allowed; opacity: 0.4; }

.icon-btn { align-items: center; background: none; border: 0; border-radius: var(--radius-pill); color: var(--ink-muted); cursor: pointer; display: inline-flex; flex: none; height: 36px; justify-content: center; padding: 0; position: relative; width: 36px; }
.icon-btn:hover:not(:disabled) { background: var(--surface-alt); color: var(--ink); }
.icon-btn.big { color: var(--ink); height: 44px; width: 44px; }
.toggle.active { color: var(--link); }
.toggle.active::after { background: currentColor; border-radius: var(--radius-pill); bottom: 2px; content: ''; height: 4px; position: absolute; width: 4px; }
.play { align-items: center; background: var(--primary); border: 0; border-radius: var(--radius-pill); color: var(--on-primary); cursor: pointer; display: inline-flex; height: 56px; justify-content: center; padding: 0; transition: background var(--dur-fast) var(--ease); width: 56px; }
.play:hover:not(:disabled) { background: var(--purple-hover); }

.top { align-items: center; display: grid; flex: none; grid-template-columns: 1fr auto 1fr; height: 64px; padding: 0 var(--space-6); }
.top-left { align-items: center; display: flex; gap: var(--space-2); }
.back { background: none; border: 0; border-radius: var(--radius-sm); color: var(--link); cursor: pointer; font: 600 14px/20px var(--font-sans); opacity: 0; padding: var(--space-2); pointer-events: none; transition: opacity var(--dur-fast) var(--ease); }
.back:hover { text-decoration: underline; }
.browsing .back { opacity: 1; pointer-events: auto; }
.label { color: var(--ink-muted); font: 600 14px/20px var(--font-sans); margin: 0; text-align: center; }
.label a { color: var(--link); text-decoration: none; }
.label a:hover { text-decoration: underline; }
.modes { align-items: center; display: flex; gap: var(--space-2); justify-self: end; }

.stage { flex: 1; min-height: 0; overflow: hidden; position: relative; }
.track { left: 50%; list-style: none; margin: 0; padding: 0; position: absolute; top: 50%; transform: translateX(calc(var(--view) * -1 * (var(--cover) + var(--gap)) - var(--cover) / 2)); transition: transform var(--dur-med) var(--ease); }
.slot-item { left: calc(var(--i) * (var(--cover) + var(--gap))); position: absolute; top: calc(var(--cover) / -2 - 28px); width: var(--cover); }
.slot { background: none; border: 0; color: inherit; cursor: pointer; display: block; padding: 0; text-align: center; width: 100%; }
.slot.on { cursor: default; }
.cover { aspect-ratio: 1; border-radius: var(--radius-md); display: block; object-fit: cover; transform: scale(0.8); transition: opacity var(--dur-fast) var(--ease), transform var(--dur-med) var(--ease); width: 100%; }
.slot:hover .cover { transform: scale(0.83); }
.slot.on .cover, .slot.on:hover .cover { transform: none; }
.slot.past .cover { opacity: 0.55; }
.slot.past:hover .cover { opacity: 1; }
.cover-empty { align-items: center; background: var(--surface-alt); border: 1px solid var(--line); color: var(--ink-muted); display: flex; justify-content: center; }
.cap { display: block; margin-top: calc(var(--cover) * -0.1 + var(--space-3)); padding: 0 var(--space-6); transition: margin var(--dur-med) var(--ease); }
.slot.on .cap { margin-top: var(--space-4); padding: 0; }
.slot.past .cap { opacity: 0.7; }
.cap-title { display: block; font: 600 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cap-artist { color: var(--ink-muted); display: block; font: 500 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.slot.on .cap-title { color: var(--link); font: 700 20px/28px var(--font-sans); }
.slot.on .cap-artist { font: 500 16px/24px var(--font-sans); }
/* Muted gray and purple text can't be kept readable on an unknown tint, so text over the glow uses ink. The
   equalizer bars still mark the playing song */
.glow .cap-artist, .glow .label, .glow .slot.on .cap-title { color: var(--ink); }
.eq { align-items: flex-end; display: inline-flex; gap: 2px; height: 14px; margin-right: var(--space-2); }
.eq i { animation: eq 900ms var(--ease) infinite alternate; background: currentColor; border-radius: 1px; display: block; height: 100%; transform-origin: bottom; width: 3px; }
.eq i:nth-child(2) { animation-delay: -300ms; }
.eq i:nth-child(3) { animation-delay: -600ms; }
.paused .eq i { animation-play-state: paused; }
@keyframes eq { from { transform: scaleY(0.3); } to { transform: scaleY(1); } }
.nudge { align-items: center; background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-pill); color: var(--ink); cursor: pointer; display: flex; height: 44px; justify-content: center; padding: 0; position: absolute; top: calc(50% - 22px - 28px); width: 44px; z-index: 1; }
.nudge:hover:not(:disabled) { background: var(--surface-alt); }
.nudge.prev { left: var(--space-6); }
.nudge.next { right: var(--space-6); }

.bottom { flex: none; margin: 0 auto; max-width: 1120px; padding: 0 var(--space-8) var(--space-6); width: 100%; }
.scrub { align-items: center; display: flex; gap: var(--space-3); }
.time { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); font-variant-numeric: tabular-nums; min-width: 32px; }
.time:last-child { text-align: right; }
.controls { align-items: center; display: grid; grid-template-columns: 1fr auto 1fr; margin-top: var(--space-2); }
.transport { align-items: center; display: flex; gap: var(--space-6); }
.volume { align-items: center; color: var(--ink-muted); display: flex; gap: var(--space-2); justify-self: end; }
.volume .slider { flex: none; width: 100px; }
.slider { -webkit-appearance: none; appearance: none; background: transparent; cursor: pointer; flex: 1; height: 16px; margin: 0; }
.slider::-webkit-slider-runnable-track { background: linear-gradient(to right, var(--purple) var(--fill, 0%), var(--line) var(--fill, 0%)); border-radius: var(--radius-pill); height: 4px; }
.slider::-moz-range-track { background: var(--line); border-radius: var(--radius-pill); height: 4px; }
.slider::-moz-range-progress { background: var(--purple); border-radius: var(--radius-pill); height: 4px; }
.slider::-webkit-slider-thumb { -webkit-appearance: none; background: var(--purple); border: 0; border-radius: var(--radius-pill); height: 12px; margin-top: -4px; width: 12px; }
.slider::-moz-range-thumb { background: var(--purple); border: 0; border-radius: var(--radius-pill); height: 12px; width: 12px; }

@media (prefers-reduced-motion: reduce) {
  .queue-view, .queue-view.open, .track, .cover, .cap, .back, .play { transition: none; }
  .eq i { animation: none; }
}
</style>
