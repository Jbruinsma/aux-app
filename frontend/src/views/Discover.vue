<template>
  <div class="aux-page discover-page">
    <AppHeader />

    <main class="main">
      <div class="bar">
        <button type="button" class="btn text back" @click="goBack">
          <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m12 19-7-7 7-7" /><path d="M19 12H5" /></svg>
          Back
        </button>
        <div class="bar-text">
          <h1>Discover</h1>
          <p v-if="seed" class="meta">Songs like {{ seed.name }}{{ artistName(seed) ? ` · ${artistName(seed)}` : '' }}</p>
        </div>
        <p v-if="status === 'ready'" class="hint" aria-hidden="true">Arrow keys, drag or scroll to move · Enter to play</p>
      </div>

      <p v-if="status === 'loading'" class="meta page-state">Finding songs like this one…</p>

      <section v-else-if="status === 'empty'" class="page-state">
        <EmptyState
          title="Not enough songs to recommend yet"
          text="Discover needs more songs to compare with this one. Upload a song to give it more to work with."
        />
        <div class="state-actions">
          <router-link class="btn primary" :to="{ name: 'Upload' }">Upload song</router-link>
          <button type="button" class="btn secondary" @click="goBack">Go back</button>
        </div>
      </section>

      <section v-else-if="status === 'missing'" class="page-state">
        <h2>This song can't be used for Discover</h2>
        <p class="meta">It may be private or deleted.</p>
        <button type="button" class="btn secondary" @click="goBack">Go back</button>
      </section>

      <section v-else-if="status === 'error'" class="page-state">
        <h2>We couldn't load Discover</h2>
        <p class="meta">Check that the server is running, then try again.</p>
        <button type="button" class="btn secondary" @click="load">Try again</button>
      </section>

      <div v-else class="stage">
        <div
          ref="viewport"
          class="viewport"
          :class="{ dragging }"
          :style="{ '--dwell': `${DWELL_MS}ms` }"
          tabindex="0"
          :aria-label="`Songs like ${seed.name}. Use the arrow keys to move and Enter to play.`"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerCancel"
          @wheel.prevent="onWheel"
        >
          <div ref="world" class="world" :class="{ instant: dragging || instant, hidden: introducing }" :style="worldStyle" aria-hidden="true">
            <div
              v-for="cell in cells"
              :key="cell.key"
              class="tile"
              :class="{ focus: cell.ring === 0 }"
              :style="{ left: `${cell.x * size.pitch}px`, opacity: DIM[Math.min(cell.ring, DIM.length - 1)], top: `${cell.y * size.pitch}px` }"
              :data-x="cell.x"
              :data-y="cell.y"
            >
              <img v-if="cell.piece.coverUrl" :src="resolveCoverURL(cell.piece.coverUrl)" alt="" draggable="false" />
              <svg v-else class="note" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
              <span v-if="cell.ring === 0 && dwelling" :key="dwellToken" class="dwell-bar" />
            </div>
          </div>
        </div>

        <p class="sr-only" aria-live="polite">{{ announcement }}</p>

        <aside class="info" :class="{ open: infoOpen }" :inert="infoOpen ? null : ''" aria-label="Song details">
          <p v-if="focused.rank" class="eyebrow">Match #{{ focused.rank }}</p>
          <h2 class="info-name">{{ focused.piece.name }}</h2>
          <p v-if="artistName(focused.piece)" class="info-artist">{{ artistName(focused.piece) }}</p>
          <router-link
            v-if="focused.piece.uploader"
            class="uploader"
            :to="{ name: 'Profile', params: { username: focused.piece.uploader.username } }"
          >
            <img v-if="focused.piece.uploader.pfpUrl" class="uploader-pfp" :src="resolveCoverURL(focused.piece.uploader.pfpUrl)" alt="" />
            <span v-else class="uploader-pfp uploader-initial" aria-hidden="true">{{ focused.piece.uploader.username.charAt(0).toUpperCase() }}</span>
            @{{ focused.piece.uploader.username }}
          </router-link>
          <ul v-if="focused.piece.tags?.length" class="tags" aria-label="Tags">
            <li v-for="tag in focused.piece.tags" :key="tag">{{ tag }}</li>
          </ul>
          <div class="info-actions">
            <button type="button" class="btn primary" @click="play">
              <svg class="icon filled" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4.5v15l12.5-7.5z" /></svg>
              Play
            </button>
            <button v-if="focused.rank" type="button" class="btn secondary" @click="discoverFromHere">Discover from here</button>
          </div>
        </aside>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useMusicStore } from '@/stores/music.js'
import { MIN_RESULTS, buildLayout, fetchDiscover, slotAt } from '@/utils/discover.js'
import { resolveCoverURL } from '@/utils/display.js'

const DWELL_MS = 2000 // how long the focus stays on a piece before its details show
const WHEEL_STEP = 40 // px of scrolling per cell
const WHEEL_LOCK_MS = 70 // shortest time between scroll steps
const FLING_MS = 120 // how far a drag's speed carries before it snaps
const INTRO_RING_MS = 70 // gap between rings in the intro
const INTRO_FLIGHT_MS = 650 // flight time for the first ring; each ring out adds INTRO_RING_MS
const DIM = [1, 0.7, 0.45, 0.3] // opacity by distance from the focused piece

const route = useRoute()
const router = useRouter()
const musicStore = useMusicStore()

const status = ref('loading')
const seed = ref(null)
const results = ref([])

const layout = computed(() => buildLayout(results.value.length))

function pieceAt(x, y) {
  const rank = slotAt(layout.value, results.value.length, x, y)
  return { piece: rank === 0 ? seed.value : results.value[rank - 1], rank }
}

function artistName(piece) {
  return piece?.artistSummary?.artistName ?? ''
}

async function load() {
  const id = route.params.id
  status.value = 'loading'
  stopDwell()
  try {
    const data = await fetchDiscover(id)
    if (route.params.id !== id) return
    seed.value = data.seed
    results.value = data.musicPieces
    if (data.musicPieces.length < MIN_RESULTS) {
      status.value = 'empty'
      return
    }
    Object.assign(cam, { x: 0, y: 0 })
    Object.assign(offset, { x: 0, y: 0 })
    status.value = 'ready'
    await nextTick()
    measure()
    intro()
  } catch (err) {
    if (err.sessionEnded || route.params.id !== id) return
    status.value = err.status === 404 ? 'missing' : 'error'
  }
}


function goBack() {
  if (window.history.state?.back) router.back()
  else router.push({ name: 'Dashboard' })
}

/* Camera: `cam` is the settled cell in the middle of the screen, `offset` the drag distance in px */

const viewport = ref(null)
const world = ref(null)
const size = reactive({ cell: 0, height: 0, pitch: 0, width: 0 })
const cam = reactive({ x: 0, y: 0 })
const offset = reactive({ x: 0, y: 0 })
const dragging = ref(false)
const instant = ref(true) // skip the snap transition, e.g. when the grid first appears

function measure() {
  if (!viewport.value) return
  const styles = getComputedStyle(viewport.value)
  size.cell = parseFloat(styles.getPropertyValue('--discover-cell')) || 0
  size.pitch = size.cell + (parseFloat(styles.getPropertyValue('--discover-gap')) || 0)
  size.width = viewport.value.clientWidth
  size.height = viewport.value.clientHeight
}

let resizeObserver = null
watch(viewport, (element, previous) => {
  if (previous) resizeObserver?.unobserve(previous)
  if (element) resizeObserver?.observe(element)
})

// The cell under the middle of the screen, also mid-drag
const centre = computed(() => {
  if (!size.pitch) return { x: cam.x, y: cam.y }
  return { x: Math.round(cam.x - offset.x / size.pitch), y: Math.round(cam.y - offset.y / size.pitch) }
})

// Only the cells on screen, plus two rings of margin, are rendered
const cells = computed(() => {
  if (status.value !== 'ready' || !size.pitch) return []
  const { x: cx, y: cy } = centre.value
  const rx = Math.ceil(size.width / size.pitch / 2) + 2
  const ry = Math.ceil(size.height / size.pitch / 2) + 2
  const list = []
  for (let y = cy - ry; y <= cy + ry; y++) {
    for (let x = cx - rx; x <= cx + rx; x++) {
      list.push({ key: `${x},${y}`, piece: pieceAt(x, y).piece, ring: Math.max(Math.abs(x - cx), Math.abs(y - cy)), x, y })
    }
  }
  return list
})

const worldStyle = computed(() => {
  const x = size.width / 2 - size.cell / 2 - cam.x * size.pitch + offset.x
  const y = size.height / 2 - size.cell / 2 - cam.y * size.pitch + offset.y
  return { transform: `translate3d(${x}px, ${y}px, 0)` }
})

const focused = computed(() => pieceAt(cam.x, cam.y))

const announcement = computed(() => {
  const { piece } = focused.value
  return piece ? `${piece.name}${artistName(piece) ? ` by ${artistName(piece)}` : ''}` : ''
})

function moveTo(x, y) {
  Object.assign(cam, { x, y })
  Object.assign(offset, { x: 0, y: 0 })
  instant.value = false
  startDwell()
}

/* Dwell: the focused piece's details show once the focus has stayed put for DWELL_MS */

const dwelling = ref(false)
const dwellToken = ref(0) // a new key restarts the progress bar
const infoOpen = ref(false)
let dwellTimer = null

function stopDwell() {
  clearTimeout(dwellTimer)
  dwelling.value = false
  infoOpen.value = false
}

function startDwell() {
  stopDwell()
  dwellToken.value++
  dwelling.value = true
  dwellTimer = setTimeout(showInfo, DWELL_MS)
}

function showInfo() {
  clearTimeout(dwellTimer)
  infoOpen.value = true
}

// Plays the focused piece, then the rest of the grid's songs in order
function play() {
  musicStore.playPieces([seed.value, ...results.value], focused.value.rank)
}

function discoverFromHere() {
  const { piece, rank } = focused.value
  if (rank) router.push({ name: 'Discover', params: { id: piece.musicPieceId } })
}

/* Intro: the seed appears, then every piece glides out from under it to its spot, nearest ring first */

const introducing = ref(false)
let introRun = 0

async function intro() {
  const run = ++introRun
  instant.value = true
  introducing.value = true
  await preloadCovers(cells.value.map((cell) => cell.piece.coverUrl).filter(Boolean).map(resolveCoverURL), 1500)
  if (run !== introRun) return
  introducing.value = false
  await nextTick()
  let landed = 0
  if (!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    for (const el of world.value?.querySelectorAll('.tile') ?? []) {
      const x = el.dataset.x - cam.x
      const y = el.dataset.y - cam.y
      const ring = Math.max(Math.abs(x), Math.abs(y))
      const opacity = el.style.opacity
      const keyframes = ring === 0
        ? [{ opacity: 0, scale: 0.9 }, { opacity, scale: 1 }]
        : [
            { opacity: 0, scale: 0.5, translate: `${-x * size.pitch}px ${-y * size.pitch}px` },
            { offset: 0.3, opacity },
            { opacity, scale: 1, translate: '0px 0px' },
          ]
      const duration = ring === 0 ? 360 : INTRO_FLIGHT_MS + ring * INTRO_RING_MS
      const delay = ring === 0 ? 0 : 200 + ring * INTRO_RING_MS
      // Waits under the seed, hidden, until its turn; long soft deceleration into place
      el.animate?.(keyframes, { delay, duration, easing: 'cubic-bezier(0.16, 1, 0.3, 1)', fill: 'backwards' })
      landed = Math.max(landed, delay + duration)
    }
  }
  await new Promise((done) => setTimeout(done, landed))
  if (run === introRun) startDwell()
}

// Waits for the covers on screen so pieces don't fly in blank. Gives up after `timeout` ms
function preloadCovers(urls, timeout) {
  const loads = [...new Set(urls)].map((src) => new Promise((done) => {
    const img = new Image()
    img.onload = img.onerror = done
    img.src = src
  }))
  return Promise.race([Promise.all(loads), new Promise((done) => setTimeout(done, timeout))])
}

/* Input: arrow keys, scrolling and dragging all move one cell at a time and snap */

const KEYS = { ArrowDown: [0, 1], ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1] }

function onKeydown(event) {
  if (status.value !== 'ready' || event.altKey || event.ctrlKey || event.metaKey) return
  // Leave typing alone, and let Enter and Escape work on focused buttons and links
  if (event.target.closest?.('input, textarea, select, [contenteditable]')) return
  if (KEYS[event.key]) {
    event.preventDefault()
    moveTo(cam.x + KEYS[event.key][0], cam.y + KEYS[event.key][1])
  } else if (event.target.closest?.('button, a')) {
    return
  } else if (event.key === 'Enter') {
    play()
  } else if (event.key === 'Escape') {
    stopDwell()
  }
}

let wheel = { x: 0, y: 0 }
let wheelLockedUntil = 0

function onWheel(event) {
  wheel.x += event.deltaX
  wheel.y += event.deltaY
  const now = performance.now()
  if (now < wheelLockedUntil) return
  const dx = Math.sign(Math.trunc(wheel.x / WHEEL_STEP))
  const dy = Math.sign(Math.trunc(wheel.y / WHEEL_STEP))
  if (!dx && !dy) return
  moveTo(cam.x + dx, cam.y + dy)
  wheel = { x: 0, y: 0 }
  wheelLockedUntil = now + WHEEL_LOCK_MS
}

let drag = null

function onPointerDown(event) {
  if (event.button !== 0) return
  viewport.value.setPointerCapture?.(event.pointerId)
  drag = { lastT: event.timeStamp, lastX: event.clientX, lastY: event.clientY, moved: false, startX: event.clientX, startY: event.clientY, vx: 0, vy: 0 }
}

function onPointerMove(event) {
  if (!drag) return
  const dx = event.clientX - drag.startX
  const dy = event.clientY - drag.startY
  if (!drag.moved && Math.hypot(dx, dy) < 6) return
  if (!drag.moved) {
    drag.moved = true
    dragging.value = true
    stopDwell()
  }
  const dt = Math.max(1, event.timeStamp - drag.lastT)
  drag.vx = (event.clientX - drag.lastX) / dt
  drag.vy = (event.clientY - drag.lastY) / dt
  Object.assign(drag, { lastT: event.timeStamp, lastX: event.clientX, lastY: event.clientY })
  Object.assign(offset, { x: dx, y: dy })
}

// A drag snaps to the nearest cell, carried a little by its speed. A tap brings that tile to the middle,
// or opens the details at once if it's already there
function onPointerUp(event) {
  if (!drag) return
  const { moved, vx, vy } = drag
  drag = null
  dragging.value = false
  if (moved) {
    moveTo(
      cam.x - Math.round((offset.x + vx * FLING_MS) / size.pitch),
      cam.y - Math.round((offset.y + vy * FLING_MS) / size.pitch),
    )
    return
  }
  const tile = document.elementFromPoint?.(event.clientX, event.clientY)?.closest('.tile')
  if (!tile) return
  const x = Number(tile.dataset.x)
  const y = Number(tile.dataset.y)
  if (x === cam.x && y === cam.y) showInfo()
  else moveTo(x, y)
}

function onPointerCancel() {
  if (!drag) return
  drag = null
  dragging.value = false
  moveTo(cam.x, cam.y)
}

onMounted(() => {
  resizeObserver = new ResizeObserver(measure)
  if (viewport.value) resizeObserver.observe(viewport.value)
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  introRun++
  clearTimeout(dwellTimer)
  resizeObserver?.disconnect()
  window.removeEventListener('keydown', onKeydown)
})

watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped>
.discover-page { height: 100dvh; min-height: 0; overflow: hidden; }
/* BottomPlayer: a 72px bar over a 32px progress strip */
.main { display: flex; flex: 1 1 0; flex-direction: column; min-height: 0; padding-bottom: 104px; }
h1 { font: 700 20px/28px var(--font-sans); margin: 0; }
h2 { font: 700 20px/28px var(--font-sans); margin: 0; }
.meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.icon { fill: none; flex: none; height: 20px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 20px; }
.icon.filled { fill: currentColor; stroke: none; }
.sr-only { clip: rect(0 0 0 0); height: 1px; margin: -1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

.bar { align-items: center; border-bottom: 1px solid var(--line); display: flex; gap: var(--space-4); padding: var(--space-3) var(--page-gutter); }
.back { align-items: center; display: inline-flex; gap: var(--space-1); }
.bar-text { flex: 1; min-width: 0; }
.bar-text .meta { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hint { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }

.page-state { align-items: flex-start; display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-8) var(--page-gutter); }
p.page-state { display: block; }
.state-actions { display: flex; flex-wrap: wrap; gap: var(--space-3); }

.stage { --discover-cell: 220px; --discover-gap: 14px; flex: 1; min-height: 0; position: relative; }
.viewport { cursor: grab; inset: 0; outline-offset: -2px; overflow: hidden; position: absolute; touch-action: none; user-select: none; }
.viewport.dragging { cursor: grabbing; }
.world { left: 0; position: absolute; top: 0; transition: transform var(--dur-fast) var(--ease); will-change: transform; }
.world.instant { transition: none; }
.world.hidden { visibility: hidden; }
.tile { background: var(--surface-alt); border-radius: var(--radius-sm); height: var(--discover-cell); position: absolute; transition: opacity var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease); width: var(--discover-cell); }
.tile img { border-radius: var(--radius-sm); display: block; height: 100%; object-fit: cover; pointer-events: none; width: 100%; }
.note { color: var(--ink-muted); fill: none; height: 48px; left: 50%; position: absolute; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; top: 50%; translate: -50% -50%; width: 48px; }
.tile.focus { outline: 2px solid var(--focus-ring); outline-offset: 2px; transform: scale(1.06); z-index: 1; }
.dwell-bar { animation: dwell var(--dwell) linear forwards; background: var(--primary); bottom: 0; height: 3px; left: 0; position: absolute; transform-origin: left; width: 100%; }
@keyframes dwell { from { transform: scaleX(0); } to { transform: scaleX(1); } }

.info { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); display: flex; flex-direction: column; gap: var(--space-2); left: calc(50% + var(--discover-cell) / 2 + var(--space-8)); opacity: 0; padding: var(--space-4); pointer-events: none; position: absolute; top: 50%; transform: translate(calc(-1 * var(--space-2)), -50%); transition: opacity var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease); width: 300px; z-index: 2; }
.info.open { opacity: 1; pointer-events: auto; transform: translate(0, -50%); }
.eyebrow { color: var(--ink-muted); font: 600 12px/16px var(--font-sans); margin: 0; }
.info-name { overflow-wrap: anywhere; }
.info-artist { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.uploader { align-items: center; align-self: flex-start; color: var(--ink); display: inline-flex; font: 600 14px/20px var(--font-sans); gap: var(--space-2); text-decoration: none; }
.uploader:hover { text-decoration: underline; }
.uploader-pfp { border-radius: var(--radius-pill); height: 24px; object-fit: cover; width: 24px; }
.uploader-initial { background: var(--primary); color: var(--on-primary); display: grid; font: 700 12px/1 var(--font-sans); place-items: center; }
.tags { display: flex; flex-wrap: wrap; gap: var(--space-2); list-style: none; margin: 0; padding: 0; }
.tags li { background: var(--surface-alt); border-radius: var(--radius-pill); color: var(--ink); font: 600 12px/16px var(--font-sans); padding: var(--space-1) var(--space-3); }
.info-actions { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-top: var(--space-2); }
.info-actions .btn { align-items: center; display: inline-flex; gap: var(--space-2); }

@media (max-width: 720px) {
  .hint { display: none; }
  .stage { --discover-cell: 150px; --discover-gap: 10px; }
  .info { bottom: var(--space-3); left: var(--space-3); right: var(--space-3); top: auto; transform: translateY(var(--space-2)); width: auto; }
  .info.open { transform: none; }
}

@media (prefers-reduced-motion: reduce) {
  .world, .tile, .info { transition: none; }
  .dwell-bar { display: none; }
}
</style>
