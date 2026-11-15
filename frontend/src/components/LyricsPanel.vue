<template>
  <Transition name="lyrics-page" :css="variant === 'page'">
    <section
      v-if="open"
      class="lyrics aux-page"
      :class="[variant, { glow: variant === 'page' && Boolean(glowColor) }]"
      :style="variant === 'page' ? { '--glow': glowColor || 'transparent' } : null"
      aria-label="Lyrics"
      @keydown.esc="variant === 'page' && emit('close')"
    >
      <header v-if="variant === 'page'" class="head">
        <span class="grabber" aria-hidden="true"></span>
        <div class="head-row">
          <button type="button" class="art-btn" aria-label="Show the album cover" @click="emit('close')">
            <img v-if="piece?.cover" class="art" :src="piece.cover" alt="" />
            <span v-else class="art art-empty" aria-hidden="true"></span>
          </button>
          <div class="head-text">
            <h2 class="title"><MarqueeText :text="piece?.title || ''" :play="isPlaying" /></h2>
            <p v-if="piece?.artist" class="artist"><MarqueeText :text="piece.artist" :play="isPlaying" /></p>
          </div>
          <button ref="closeBtn" type="button" class="icon-btn" aria-label="Close lyrics" @click="emit('close')">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
          </button>
        </div>
      </header>

      <div
        ref="scroller"
        class="body"
        :class="{ centered: !lines && !lyrics?.plain }"
        tabindex="-1"
        @wheel.passive="stopFollowing"
        @touchmove.passive="stopFollowing"
        @keydown="onKeys"
      >
        <div v-if="state === 'waiting' || state === 'loading'" class="finding" role="status">
          <span class="ring" aria-hidden="true">
            <svg width="64" height="64" viewBox="0 0 52 52"><circle class="ring-track" cx="26" cy="26" r="20" /></svg>
            <svg class="ring-arrow" width="64" height="64" viewBox="0 0 52 52"><path d="M26 6a20 20 0 1 1-20 20" /><path d="M1 30l5-5 5 5" /></svg>
          </span>
          <p class="finding-title">Finding Lyrics</p>
        </div>
        <div v-else-if="state === 'error'" class="note">
          <p>We couldn't reach the lyrics service.</p>
          <button type="button" class="btn secondary small" @click="load">Try again</button>
        </div>
        <div v-else-if="state === 'missing'" class="note">
          <p class="note-title">No lyrics found for this song</p>
          <p>Lyrics are matched by title, artist and length, so a different edit or a misspelled title won't find them.</p>
        </div>
        <p v-else-if="lyrics?.instrumental && !lines && !lyrics.plain" class="note note-title">This song is instrumental.</p>
        <ol v-else-if="lines" class="lines">
          <li v-for="(line, index) in lines" :key="`${index}-${line.time}`">
            <button
              type="button"
              class="line"
              :class="{ now: index === active, past: index < active, gap: !line.text }"
              :aria-current="index === active ? 'true' : null"
              :aria-label="line.text ? `${line.text}, at ${clock(line.time)}` : `Instrumental break at ${clock(line.time)}`"
              :data-index="index"
              @click="jump(line)"
            >{{ line.text || '♪' }}</button>
          </li>
        </ol>
        <template v-else-if="lyrics?.plain">
          <p class="note">These lyrics aren't timed, so they don't follow the song.</p>
          <p class="plain">{{ lyrics.plain }}</p>
        </template>
      </div>

      <button v-if="lines && !following && variant !== 'page'" type="button" class="btn primary resync" @click="resync">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12a9 9 0 1 1-3-6.7L21 8" /><path d="M21 3v5h-5" /></svg>
        Sync
      </button>

      <!-- Phone: the player's controls under the lyrics, folded away with the chevron to give the lyrics more room -->
      <div v-if="variant === 'page'" class="controls" :class="{ folded }">
        <button v-if="lines && !following" type="button" class="btn primary resync" @click="resync">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12a9 9 0 1 1-3-6.7L21 8" /><path d="M21 3v5h-5" /></svg>
          Sync
        </button>
        <button type="button" class="fold" :aria-expanded="folded ? 'false' : 'true'" :aria-label="folded ? 'Show controls' : 'Hide controls'" @click="folded = !folded">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
        </button>
        <div class="controls-clip">
          <div class="controls-body" :inert="folded">
            <input
              class="slider"
              type="range"
              min="0"
              :max="Math.floor(duration)"
              step="any"
              :value="position"
              :disabled="!canPlay"
              aria-label="Seek"
              :aria-valuetext="`${clock(position)} of ${clock(duration)}`"
              :style="{ '--fill': `${duration ? Math.min(100, (position / duration) * 100) : 0}%` }"
              @input="(e) => emit('scrub', parseFloat(e.target.value))"
            />
            <div class="times"><span>{{ clock(position) }}</span><span>{{ clock(duration) }}</span></div>
            <div class="transport">
              <button type="button" class="ctrl" :disabled="!canSkip" aria-label="Previous" @click="emit('prev')">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M6 5h2v14H6zM19 5.5v13a.5.5 0 0 1-.77.42L9 12.42a.5.5 0 0 1 0-.84l9.23-6.5a.5.5 0 0 1 .77.42z" /></svg>
              </button>
              <button type="button" class="play" :disabled="!canPlay" :aria-label="isPlaying ? 'Pause' : 'Play'" @click="emit('toggle-play')">
                <svg v-if="!isPlaying" width="24" height="24" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5v11l9-5.5z" fill="currentColor" /></svg>
                <svg v-else width="24" height="24" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5h3v11H3zM8 1.5h3v11H8z" fill="currentColor" /></svg>
              </button>
              <button type="button" class="ctrl" :disabled="!canSkip" aria-label="Next" @click="emit('next')">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M16 5h2v14h-2zM5 5.5v13a.5.5 0 0 0 .77.42L15 12.42a.5.5 0 0 0 0-.84L5.77 5.08A.5.5 0 0 0 5 5.5z" /></svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  </Transition>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { fetchLyrics, lineAt } from '@/utils/lyrics.js'
import { useCoverColor } from '@/utils/coverColor.js'
import MarqueeText from '@/components/MarqueeText.vue'

// The playing song's lyrics, from LRCLIB (src/utils/lyrics.js), matched by length. Timed lyrics follow the song with
// the line being sung centered; clicking a line emits `seek` with its time. Scrolling by hand stops the following so
// later lines can be read, and "Sync" brings it back.
// `stage` fills the lyrics side of the desktop full-screen view (QueueView), which has its own controls and tinted
// background. `page` is the phone's full-screen lyrics, Apple Music style: a small cover, title and artist at the top
// (a long title slides back and forth while playing) and the cover's color behind it
const props = defineProps({
  open: Boolean,
  variant: { type: String, default: 'page' },
  // The store's currentMusicPiece: { uuid, title, artist, cover }
  piece: { type: Object, default: null },
  position: { type: Number, default: 0 },
  duration: { type: Number, default: 0 },
  isPlaying: Boolean,
  // Phone controls
  canPlay: Boolean,
  canSkip: Boolean,
})
// `seek` is a clicked line (it also starts playback); `scrub` is the slider
const emit = defineEmits(['close', 'seek', 'scrub', 'toggle-play', 'prev', 'next'])

const scroller = ref(null)
const closeBtn = ref(null)
const lyrics = ref(null)
const state = ref('waiting')
const following = ref(true)
const folded = ref(false)
let request = null

const glowColor = useCoverColor(() => (props.variant === 'page' ? props.piece?.cover || '' : ''))

const lines = computed(() => lyrics.value?.lines ?? null)
const active = computed(() => (lines.value ? lineAt(lines.value, props.position) : -1))

async function load() {
  request?.abort()
  const piece = props.piece
  lyrics.value = null
  if (!piece?.uuid || !(props.duration > 0)) return (state.value = 'waiting')
  following.value = true
  state.value = 'loading'
  const controller = new AbortController()
  request = controller
  try {
    const found = await fetchLyrics({ musicPieceId: piece.uuid, title: piece.title, artist: piece.artist, duration: props.duration }, { signal: controller.signal })
    if (request !== controller) return
    lyrics.value = found
    state.value = found ? 'ready' : 'missing'
    await nextTick()
    scrollToActive(false)
  } catch (err) {
    if (err?.name === 'AbortError' || request !== controller) return
    state.value = 'error'
  }
}

// A new song, or its length arriving once the audio loads. Nothing is fetched while the panel is closed
watch(() => [props.open, props.piece?.uuid, Math.round(props.duration)], ([open], previous) => {
  if (!open) return
  const [wasOpen, oldId, oldLength] = previous ?? []
  if (wasOpen && oldId === props.piece?.uuid && oldLength === Math.round(props.duration) && state.value !== 'waiting') return
  load()
  if (!wasOpen && props.variant === 'page') nextTick(() => closeBtn.value?.focus())
}, { immediate: true })

watch(active, () => {
  if (following.value) scrollToActive(true)
})

function scrollToActive(smooth) {
  const list = scroller.value
  if (!list || active.value < 0) return list?.scrollTo({ top: 0 })
  const line = list.querySelector(`[data-index="${active.value}"]`)
  if (!line) return
  const top = line.offsetTop - list.clientHeight / 2 + line.offsetHeight / 2
  const calm = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches
  list.scrollTo({ behavior: smooth && !calm ? 'smooth' : 'auto', top: Math.max(0, top) })
}

function stopFollowing() {
  if (lines.value) following.value = false
}

function onKeys(event) {
  if (['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End'].includes(event.key)) stopFollowing()
}

function resync() {
  following.value = true
  scrollToActive(true)
}

function jump(line) {
  following.value = true
  emit('seek', line.time)
}

function clock(seconds) {
  const whole = Math.floor(seconds)
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`
}
</script>

<style scoped>
/* Text sits on the cover's tint, where muted gray and purple can't be kept readable, so everything uses ink and
   steps back with opacity instead */
.lyrics { background: none; color: var(--ink); display: flex; flex-direction: column; font-family: var(--font-sans); min-height: 0; position: relative; }
.lyrics.stage { height: 100%; }
.lyrics.page { background: var(--surface); inset: 0; padding-bottom: env(safe-area-inset-bottom, 0px); position: fixed; transition: --glow 700ms var(--ease); z-index: 210; }
.lyrics.page.glow { background: radial-gradient(ellipse 95% 48% at 50% 32%, color-mix(in srgb, var(--glow) var(--glow-in), var(--surface)) 0%, color-mix(in srgb, var(--glow) var(--glow-mid), var(--surface)) 50%, var(--surface) 100%); }
.head { display: flex; flex: none; flex-direction: column; gap: var(--space-3); padding: calc(env(safe-area-inset-top, 0px) + var(--space-2)) var(--space-4) var(--space-2) var(--space-6); }
.grabber { align-self: center; background: color-mix(in srgb, var(--ink) 25%, transparent); border-radius: var(--radius-pill); height: 5px; width: 36px; }
.head-row { align-items: center; display: flex; gap: var(--space-3); }
.art-btn { background: none; border: 0; border-radius: var(--radius-sm); cursor: pointer; flex: none; padding: 0; }
.art { border-radius: var(--radius-sm); box-shadow: 0 4px 12px rgb(0 0 0 / 0.2); flex: none; height: 56px; object-fit: cover; width: 56px; }
.art-empty { background: var(--surface-alt); }
.head-text { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.title { font: 700 17px/24px var(--font-sans); margin: 0; }
.artist { font: 500 17px/24px var(--font-sans); margin: 0; opacity: 0.75; }
.icon-btn { align-items: center; background: none; border: 0; border-radius: var(--radius-pill); color: var(--ink); cursor: pointer; display: flex; flex: none; height: 36px; justify-content: center; padding: 0; width: 36px; }
.icon-btn:hover { background: color-mix(in srgb, var(--ink) 8%, transparent); }
.icon-btn svg { fill: none; height: 20px; stroke: currentColor; stroke-linecap: round; stroke-width: 2; width: 20px; }
.btn.small { font-size: 14px; padding: 4px 10px; }
.body { flex: 1; min-height: 0; mask-image: linear-gradient(to bottom, transparent 0, #000 var(--space-8), #000 calc(100% - var(--space-8)), transparent 100%); overflow-x: hidden; overflow-y: auto; overscroll-behavior: contain; padding: var(--space-8) var(--space-6); scrollbar-width: none; }
.stage .body { padding: var(--space-8) var(--space-4); }
/* Nothing to follow (loading, none found, instrumental): the message sits in the middle */
.body.centered { align-items: center; display: flex; flex-direction: column; justify-content: center; text-align: center; }
.centered .note { align-items: center; margin: 0; max-width: 32ch; }
.body::-webkit-scrollbar { display: none; }
.body:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: -2px; }
.note { display: flex; flex-direction: column; font: 500 16px/24px var(--font-sans); gap: var(--space-3); margin: 0 0 var(--space-4); opacity: 0.8; }
.note p { margin: 0; }
.note-title { font: 700 22px/30px var(--font-sans); opacity: 1; }
/* Loading: a purple arrow eases round a faint ring (mockups/lyrics-loading.html, option B) */
.finding { align-items: center; display: flex; flex-direction: column; }
.ring { height: 64px; margin-bottom: var(--space-6); position: relative; width: 64px; }
.ring svg { inset: 0; position: absolute; }
.ring-track { fill: none; stroke: color-mix(in srgb, var(--ink) 14%, transparent); stroke-width: 4; }
.ring-arrow { animation: ring-spin 1.2s cubic-bezier(0.45, 0.05, 0.55, 0.95) infinite; fill: none; stroke: var(--link); stroke-linecap: round; stroke-linejoin: round; stroke-width: 4; }
.finding-title { font: 700 18px/26px var(--font-sans); margin: 0; }
@keyframes ring-spin { to { transform: rotate(360deg); } }
.lines { list-style: none; margin: 0; padding: 0 0 40vh; }
.line { background: none; border: 0; border-radius: var(--radius-sm); color: var(--ink); cursor: pointer; display: block; font: 700 26px/34px var(--font-sans); opacity: 0.45; overflow-wrap: anywhere; padding: var(--space-2) var(--space-6) var(--space-2) var(--space-2); text-align: left; transition: opacity var(--dur-fast) var(--ease), transform var(--dur-med) var(--ease); transform-origin: left center; width: 100%; }
.stage .line { font-size: 32px; line-height: 42px; }
.line:hover { background: color-mix(in srgb, var(--ink) 8%, transparent); opacity: 0.8; }
.line:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 0; }
.line.past { opacity: 0.3; }
.line.now { opacity: 1; transform: scale(1.03); }
.line.gap { font-size: 20px; }
.plain { font: 700 22px/32px var(--font-sans); margin: 0; white-space: pre-line; }
/* .btn.resync: the global .btn is inline-block with large padding, which left the icon off the text's center */
.btn.resync { align-items: center; align-self: center; bottom: calc(var(--space-8) * 1.5); display: inline-flex; font: 600 13px/16px var(--font-sans); gap: 6px; padding: 6px 12px; position: absolute; }
.resync svg { display: block; fill: none; flex: none; height: 14px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2.25; width: 14px; }

/* Phone controls, laid out like the player's. Folding slides them down out of the way, leaving the chevron */
.controls { flex: none; padding: 0 var(--space-6) var(--space-4); position: relative; }
.controls .btn.resync { bottom: calc(100% + var(--space-3)); left: 50%; transform: translateX(-50%); }
.fold { align-items: center; background: none; border: 0; border-radius: var(--radius-pill); color: var(--ink); cursor: pointer; display: flex; height: 28px; justify-content: center; margin: 0 auto; opacity: 0.7; padding: 0; width: 44px; }
.fold svg { fill: none; height: 22px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; transition: transform var(--dur-med) var(--ease); width: 22px; }
.folded .fold svg { transform: rotate(180deg); }
.controls-clip { display: grid; grid-template-rows: 1fr; transition: grid-template-rows var(--dur-med) var(--ease), opacity var(--dur-med) var(--ease); }
.folded .controls-clip { grid-template-rows: 0fr; opacity: 0; }
.controls-body { min-height: 0; overflow: hidden; }
.slider { -webkit-appearance: none; appearance: none; background: transparent; cursor: pointer; display: block; height: 24px; margin: 0; width: 100%; }
.slider::-webkit-slider-runnable-track { background: linear-gradient(to right, var(--purple) var(--fill, 0%), var(--line) var(--fill, 0%)); border-radius: var(--radius-pill); height: 4px; }
.slider::-moz-range-track { background: var(--line); border-radius: var(--radius-pill); height: 4px; }
.slider::-moz-range-progress { background: var(--purple); border-radius: var(--radius-pill); height: 4px; }
.slider::-webkit-slider-thumb { -webkit-appearance: none; background: var(--purple); border: 0; border-radius: var(--radius-pill); height: 16px; margin-top: -6px; width: 16px; }
.slider::-moz-range-thumb { background: var(--purple); border: 0; border-radius: var(--radius-pill); height: 16px; width: 16px; }
.times { display: flex; font: 600 12px/16px var(--font-sans); font-variant-numeric: tabular-nums; justify-content: space-between; opacity: 0.75; }
.transport { align-items: center; display: flex; gap: var(--space-8); justify-content: center; padding: var(--space-3) 0 var(--space-1); }
.ctrl { align-items: center; background: none; border: 0; border-radius: var(--radius-pill); color: var(--ink); cursor: pointer; display: inline-flex; height: 48px; justify-content: center; padding: 0; width: 48px; }
.play { align-items: center; background: var(--primary); border: 0; border-radius: var(--radius-pill); color: var(--on-primary); cursor: pointer; display: inline-flex; height: 64px; justify-content: center; padding: 0; transition: background var(--dur-fast) var(--ease); width: 64px; }
.play:active { background: var(--purple-hover); }
.controls button:disabled, .controls input:disabled { cursor: not-allowed; opacity: 0.4; }

.lyrics-page-enter-active, .lyrics-page-leave-active { transition: opacity var(--dur-med) var(--ease); }
.lyrics-page-enter-from, .lyrics-page-leave-to { opacity: 0; }

@media (prefers-reduced-motion: reduce) {
  .line, .lyrics.page, .lyrics-page-enter-active, .lyrics-page-leave-active, .controls-clip, .fold svg { transition: none; }
  .line.now { transform: none; }
  .ring-arrow { animation-duration: 3s; }
}
</style>
