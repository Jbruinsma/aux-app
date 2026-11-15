<template>
  <section
    v-if="open"
    class="lyrics aux-page"
    :class="[variant, { glow: variant === 'page' && Boolean(glowColor) }]"
    :style="variant === 'page' ? { '--glow': glowColor || 'transparent' } : null"
    aria-label="Lyrics"
    @keydown.esc="variant === 'page' && emit('close')"
  >
    <header v-if="variant === 'page'" class="head">
      <div class="head-text">
        <h2>Lyrics</h2>
        <p class="song">{{ piece?.title }}<template v-if="piece?.artist"> · {{ piece.artist }}</template></p>
      </div>
      <button ref="closeBtn" type="button" class="icon-btn" aria-label="Close lyrics" @click="emit('close')">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18" /><path d="m6 6 12 12" /></svg>
      </button>
    </header>

    <div v-if="lines" class="timing" role="group" aria-label="Lyrics timing">
      <span class="timing-label">{{ offsetLabel }}</span>
      <button type="button" class="btn secondary small" :aria-label="'Show lyrics half a second earlier'" @click="nudge(-0.5)">Earlier</button>
      <button type="button" class="btn secondary small" :aria-label="'Show lyrics half a second later'" @click="nudge(0.5)">Later</button>
      <button v-if="offset" type="button" class="btn text small" @click="nudge(-offset)">Reset</button>
    </div>

    <div
      ref="scroller"
      class="body"
      tabindex="-1"
      @wheel.passive="stopFollowing"
      @touchmove.passive="stopFollowing"
      @keydown="onKeys"
    >
      <p v-if="state === 'waiting'" class="note" role="status">Lyrics load once the song does.</p>
      <p v-else-if="state === 'loading'" class="note" role="status">Finding lyrics…</p>
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

    <button v-if="lines && !following" type="button" class="btn primary resync" @click="resync">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12a9 9 0 1 1-3-6.7L21 8" /><path d="M21 3v5h-5" /></svg>
      Sync to song
    </button>

    <p class="credit">Lyrics from LRCLIB</p>
  </section>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { fetchLyrics, lineAt, readOffset, saveOffset } from '@/utils/lyrics.js'
import { useCoverColor } from '@/utils/coverColor.js'

// The playing song's lyrics, from LRCLIB (src/utils/lyrics.js), matched by length. Timed lyrics follow the song with
// the line being sung centered; clicking a line emits `seek` with its time. Scrolling by hand stops the following so
// later lines can be read, and "Sync to song" brings it back. Earlier and Later nudge the timing for this song when
// LRCLIB's copy runs ahead or behind.
// `stage` fills the lyrics side of the desktop full-screen view (QueueView), which has its own controls and tinted
// background. `page` is the phone's full-screen lyrics, with its own header and the cover's color behind it
const props = defineProps({
  open: Boolean,
  variant: { type: String, default: 'page' },
  // The store's currentMusicPiece: { uuid, title, artist, cover }
  piece: { type: Object, default: null },
  position: { type: Number, default: 0 },
  duration: { type: Number, default: 0 },
})
const emit = defineEmits(['close', 'seek'])

const scroller = ref(null)
const closeBtn = ref(null)
const lyrics = ref(null)
const state = ref('waiting')
const following = ref(true)
const offset = ref(0)
let request = null

const glowColor = useCoverColor(() => (props.variant === 'page' ? props.piece?.cover || '' : ''))

const lines = computed(() => lyrics.value?.lines ?? null)
const active = computed(() => (lines.value ? lineAt(lines.value, props.position - offset.value) : -1))
const offsetLabel = computed(() => {
  if (!offset.value) return 'Out of time?'
  const amount = `${Math.abs(offset.value).toFixed(1)}s`
  return offset.value > 0 ? `Lyrics ${amount} later` : `Lyrics ${amount} earlier`
})

async function load() {
  request?.abort()
  const piece = props.piece
  lyrics.value = null
  if (!piece?.uuid || !(props.duration > 0)) return (state.value = 'waiting')
  offset.value = readOffset(piece.uuid)
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
  emit('seek', Math.max(0, line.time + offset.value))
}

function nudge(amount) {
  offset.value = Math.round((offset.value + amount) * 10) / 10
  saveOffset(props.piece.uuid, offset.value)
  if (following.value) nextTick(() => scrollToActive(true))
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
.head { align-items: flex-start; display: flex; gap: var(--space-3); justify-content: space-between; padding: calc(env(safe-area-inset-top, 0px) + var(--space-4)) var(--space-4) var(--space-2) var(--space-6); }
.head-text { min-width: 0; }
h2 { font: 700 20px/28px var(--font-sans); margin: 0; }
.song { font: 500 14px/20px var(--font-sans); margin: 0; opacity: 0.75; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.icon-btn { align-items: center; background: none; border: 0; border-radius: var(--radius-pill); color: var(--ink); cursor: pointer; display: flex; flex: none; height: 36px; justify-content: center; padding: 0; width: 36px; }
.icon-btn:hover { background: color-mix(in srgb, var(--ink) 8%, transparent); }
.icon-btn svg { fill: none; height: 20px; stroke: currentColor; stroke-linecap: round; stroke-width: 2; width: 20px; }
.timing { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-2); padding: 0 var(--space-6) var(--space-3); }
.stage .timing { padding: 0 0 var(--space-3); }
.timing-label { flex: 1; font: 500 12px/16px var(--font-sans); min-width: 0; opacity: 0.75; }
.btn.small { font-size: 14px; padding: 4px 10px; }
.btn.text { color: var(--ink); }
.body { flex: 1; mask-image: linear-gradient(to bottom, transparent 0, #000 var(--space-8), #000 calc(100% - var(--space-8)), transparent 100%); overflow-y: auto; overscroll-behavior: contain; padding: var(--space-8) var(--space-6); scrollbar-width: none; }
.stage .body { padding: var(--space-8) 0; }
.body::-webkit-scrollbar { display: none; }
.body:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: -2px; }
.note { display: flex; flex-direction: column; font: 500 16px/24px var(--font-sans); gap: var(--space-3); margin: 0 0 var(--space-4); opacity: 0.8; }
.note p { margin: 0; }
.note-title { font: 700 22px/30px var(--font-sans); opacity: 1; }
.lines { list-style: none; margin: 0; padding: 0 0 40vh; }
.line { background: none; border: 0; border-radius: var(--radius-sm); color: var(--ink); cursor: pointer; display: block; font: 700 26px/34px var(--font-sans); margin: 0 calc(-1 * var(--space-2)); opacity: 0.45; padding: var(--space-2); text-align: left; transition: opacity var(--dur-fast) var(--ease), transform var(--dur-med) var(--ease); transform-origin: left center; width: calc(100% + var(--space-4)); }
.stage .line { font-size: 32px; line-height: 42px; }
.line:hover { background: color-mix(in srgb, var(--ink) 8%, transparent); opacity: 0.8; }
.line:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 0; }
.line.past { opacity: 0.3; }
.line.now { opacity: 1; transform: scale(1.03); }
.line.gap { font-size: 20px; }
.plain { font: 700 22px/32px var(--font-sans); margin: 0; white-space: pre-line; }
.resync { align-items: center; align-self: center; bottom: calc(var(--space-8) * 1.5); display: flex; gap: var(--space-2); position: absolute; }
.resync svg { fill: none; height: 16px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 16px; }
.credit { font: 500 12px/16px var(--font-sans); margin: 0; opacity: 0.6; padding: var(--space-2) var(--space-6); }
.stage .credit { padding: var(--space-2) 0; }

@media (prefers-reduced-motion: reduce) {
  .line, .lyrics.page { transition: none; }
  .line.now { transform: none; }
}
</style>
