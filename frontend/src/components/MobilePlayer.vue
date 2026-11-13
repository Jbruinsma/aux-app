<template>
  <div class="mobile-player">
    <div v-show="!open" class="mini">
      <button ref="miniOpenRef" type="button" class="mini-open" :aria-label="openLabel" @click="setOpen(true)">
        <img v-if="piece?.cover" class="mini-cover" :src="piece.cover" alt="" />
        <span v-else class="mini-cover cover-empty" aria-hidden="true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
        </span>
        <span class="mini-text">
          <span class="mini-title">{{ title }}</span>
          <span v-if="subtitle" class="mini-artist">{{ subtitle }}</span>
        </span>
      </button>
      <button type="button" class="play mini-play" :disabled="!canPlay" :aria-label="isPlaying ? 'Pause' : 'Play'" @click="emit('toggle-play')">
        <svg v-if="!isPlaying" width="16" height="16" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5v11l9-5.5z" fill="currentColor" /></svg>
        <svg v-else width="16" height="16" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5h3v11H3zM8 1.5h3v11H8z" fill="currentColor" /></svg>
      </button>
      <button type="button" class="icon-btn" :disabled="!canSkip" aria-label="Next" @click="emit('next')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M16 5h2v14h-2zM5 5.5v13a.5.5 0 0 0 .77.42L15 12.42a.5.5 0 0 0 0-.84L5.77 5.08A.5.5 0 0 0 5 5.5z" /></svg>
      </button>
      <span class="mini-progress" aria-hidden="true"><span :style="{ width: progressFill }"></span></span>
    </div>

    <div
      ref="sheetRef"
      class="sheet"
      :class="{ open, dragging: dragStart !== null }"
      :style="dragOffset ? { transform: `translateY(${dragOffset}px)` } : null"
      role="dialog"
      aria-modal="true"
      aria-label="Now playing"
      @keydown="onSheetKeydown"
      @click="onSheetClick"
    >
      <div class="sheet-top" @pointerdown="onDragStart" @pointermove="onDragMove" @pointerup="onDragEnd" @pointercancel="onDragEnd">
        <span class="grabber" aria-hidden="true"></span>
        <button ref="closeRef" type="button" class="icon-btn" aria-label="Close player" @click="setOpen(false)">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
        </button>
        <p class="sheet-label">Now playing</p>
        <span></span>
      </div>

      <img v-if="piece?.cover" class="sheet-cover" :src="piece.cover" alt="" />
      <span v-else class="sheet-cover cover-empty" aria-hidden="true">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
      </span>

      <div class="meta">
        <button
          v-if="menuItems.length"
          ref="menuBtnRef"
          type="button"
          class="meta-text meta-btn"
          aria-haspopup="menu"
          :aria-expanded="menuOpen ? 'true' : 'false'"
          @click="setMenu(!menuOpen)"
        >
          <span class="sheet-title">{{ title }}</span>
          <span v-if="piece?.artist" class="sheet-artist">{{ piece.artist }}</span>
        </button>
        <div v-else class="meta-text">
          <span class="sheet-title">{{ title }}</span>
          <span v-if="piece?.artist" class="sheet-artist">{{ piece.artist }}</span>
        </div>
        <router-link
          v-if="uploader"
          class="uploader"
          :to="{ name: 'Profile', params: { username: uploader.username } }"
          :aria-label="`Uploaded by ${uploader.username}`"
        >
          <img v-if="uploader.pfpUrl" class="uploader-pfp" :src="resolveCoverURL(uploader.pfpUrl)" alt="" />
          <span v-else class="uploader-pfp uploader-initial" aria-hidden="true">{{ uploader.username.charAt(0).toUpperCase() }}</span>
          <span class="uploader-name">{{ uploader.username }}</span>
        </router-link>
        <div ref="menuRef" class="menu" :class="{ open: menuOpen }" role="menu" aria-label="Song options" @keydown="onMenuKeydown">
          <button v-for="item in menuItems" :key="item.key" type="button" class="menu-item" role="menuitem" tabindex="-1" @click="go(item.to)">
            <svg v-if="item.key === 'artist'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m11 7.601-5.994 8.19a1 1 0 0 0 .1 1.298l.817.818a1 1 0 0 0 1.314.087L15.09 12" /><path d="M16.5 21.174C15.5 20.5 14.372 20 13 20c-2.058 0-3.928 2.356-6 2-2.072-.356-2.775-3.369-1.5-4.5" /><circle cx="16" cy="7" r="5" /></svg>
            <svg v-else-if="item.key === 'playlist'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 15V6" /><path d="M18.5 18a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z" /><path d="M12 12H3" /><path d="M16 6H3" /><path d="M12 18H3" /></svg>
            <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" /></svg>
            <span class="menu-text">{{ item.label }}<span v-if="item.sub" class="menu-sub">{{ item.sub }}</span></span>
          </button>
        </div>
      </div>

      <div class="scrub">
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
        <div class="times"><span>{{ currentTimeLabel }}</span><span>{{ durationLabel }}</span></div>
      </div>

      <div class="transport">
        <button type="button" class="icon-btn big" :disabled="!canSkip" aria-label="Previous" @click="emit('prev')">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M6 5h2v14H6zM19 5.5v13a.5.5 0 0 1-.77.42L9 12.42a.5.5 0 0 1 0-.84l9.23-6.5a.5.5 0 0 1 .77.42z" /></svg>
        </button>
        <button type="button" class="play sheet-play" :disabled="!canPlay" :aria-label="isPlaying ? 'Pause' : 'Play'" @click="emit('toggle-play')">
          <svg v-if="!isPlaying" width="28" height="28" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5v11l9-5.5z" fill="currentColor" /></svg>
          <svg v-else width="28" height="28" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5h3v11H3zM8 1.5h3v11H8z" fill="currentColor" /></svg>
        </button>
        <button type="button" class="icon-btn big" :disabled="!canSkip" aria-label="Next" @click="emit('next')">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M16 5h2v14h-2zM5 5.5v13a.5.5 0 0 0 .77.42L15 12.42a.5.5 0 0 0 0-.84L5.77 5.08A.5.5 0 0 0 5 5.5z" /></svg>
        </button>
      </div>

      <div class="modes">
        <button type="button" class="icon-btn mode" :class="{ active: shuffleOn }" :disabled="!canShuffle" aria-label="Shuffle" :aria-pressed="shuffleOn ? 'true' : 'false'" @click="emit('shuffle')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m18 14 4 4-4 4" /><path d="m18 2 4 4-4 4" /><path d="M2 18h1.97a4 4 0 0 0 3.3-1.7l5.46-8.6a4 4 0 0 1 3.3-1.7H22" /><path d="M2 6h1.97a4 4 0 0 1 3.3 1.7l.47.73M22 18h-5.97a4 4 0 0 1-3.3-1.7l-.47-.73" /></svg>
        </button>
        <button type="button" class="icon-btn mode" :class="{ active: repeatMode !== 'OFF' }" :disabled="!canPlay" :aria-label="repeatLabel" @click="emit('repeat')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="m17 2 4 4-4 4" /><path d="M3 11v-1a4 4 0 0 1 4-4h14" /><path d="m7 22-4-4 4-4" /><path d="M21 13v1a4 4 0 0 1-4 4H3" />
            <path v-if="repeatMode === 'PIECE'" d="M11 10h1v4" />
            <path v-else-if="repeatMode === 'OFF'" d="m3 3 18 18" />
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resolveCoverURL } from '@/utils/display.js'

const props = defineProps({
  piece: { type: Object, default: null },
  uploader: { type: Object, default: null },
  // { to, name } for "Go to playlist", or null when there's no playlist to link to
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
})
const emit = defineEmits(['toggle-play', 'prev', 'next', 'shuffle', 'repeat', 'seek'])

const router = useRouter()
const route = useRoute()

const open = ref(false)
const menuOpen = ref(false)
const miniOpenRef = ref(null)
const closeRef = ref(null)
const sheetRef = ref(null)
const menuRef = ref(null)
const menuBtnRef = ref(null)

const title = computed(() => props.piece?.title || 'No song playing')
const subtitle = computed(() => props.piece?.artist || props.uploader?.username || '')
const openLabel = computed(() => `Open player: ${title.value}${props.piece?.artist ? ` by ${props.piece.artist}` : ''}`)

const menuItems = computed(() => {
  const items = []
  if (props.piece?.artist && props.piece?.artistId) {
    items.push({ key: 'artist', label: 'Go to artist', sub: props.piece.artist, to: { name: 'Artist', params: { artistId: props.piece.artistId } } })
  }
  if (props.playlistLink) items.push({ key: 'playlist', label: 'Go to playlist', sub: props.playlistLink.name, to: props.playlistLink.to })
  if (props.uploader) {
    items.push({ key: 'uploader', label: 'Go to uploader', sub: props.uploader.username, to: { name: 'Profile', params: { username: props.uploader.username } } })
  }
  return items
})

async function setOpen(value) {
  open.value = value
  if (!value) menuOpen.value = false
  document.body.classList.toggle('player-sheet-open', value)
  await nextTick()
  if (value) closeRef.value?.focus({ preventScroll: true })
  else miniOpenRef.value?.focus({ preventScroll: true })
}

// Any navigation, from the menu or the uploader link, leaves the full player
watch(() => route?.fullPath, () => { if (open.value) setOpen(false) })
// A narrow window that gets wider unmounts this; don't leave the page locked
onBeforeUnmount(() => document.body.classList.remove('player-sheet-open'))

async function setMenu(value, refocus = true) {
  menuOpen.value = value
  await nextTick()
  if (value) menuRef.value?.querySelector('[role="menuitem"]')?.focus()
  else if (refocus) menuBtnRef.value?.focus()
}

function go(to) {
  menuOpen.value = false
  router?.push(to)
}

function onMenuKeydown(e) {
  if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return
  e.preventDefault()
  const items = [...menuRef.value.querySelectorAll('[role="menuitem"]')]
  const i = items.indexOf(document.activeElement)
  const next = e.key === 'ArrowDown' ? (i + 1) % items.length : (i - 1 + items.length) % items.length
  items[next].focus()
}

// Taps outside the menu close it
function onSheetClick(e) {
  if (menuOpen.value && !menuRef.value?.contains(e.target) && !menuBtnRef.value?.contains(e.target)) setMenu(false, false)
}

// Escape closes the menu first, then the player. Tab stays inside the player while it's open
function onSheetKeydown(e) {
  if (e.key === 'Escape') {
    e.preventDefault()
    if (menuOpen.value) setMenu(false)
    else setOpen(false)
    return
  }
  if (e.key !== 'Tab') return
  const focusable = [...sheetRef.value.querySelectorAll('button:not([disabled]):not([tabindex="-1"]), a[href], input:not([disabled])')]
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus() }
  else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus() }
}

// Drag the top of the player down to close it
const CLOSE_DRAG = 120
const dragStart = ref(null)
const dragOffset = ref(0)

function onDragStart(e) {
  if (e.target.closest('button')) return
  dragStart.value = e.clientY
  e.currentTarget.setPointerCapture?.(e.pointerId)
}

function onDragMove(e) {
  if (dragStart.value === null) return
  dragOffset.value = Math.max(0, e.clientY - dragStart.value)
}

function onDragEnd() {
  if (dragStart.value === null) return
  const close = dragOffset.value >= CLOSE_DRAG
  dragStart.value = null
  dragOffset.value = 0
  if (close) setOpen(false)
}
</script>

<style scoped>
button:disabled, input:disabled { cursor: not-allowed; opacity: 0.4; }
.mobile-player { color: var(--ink); font-family: var(--font-sans); }
.mobile-player :focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }

.icon-btn { align-items: center; background: none; border: 0; border-radius: var(--radius-pill); color: var(--ink); cursor: pointer; display: inline-flex; flex: none; height: 44px; justify-content: center; padding: 0; width: 44px; }
.play { align-items: center; background: var(--primary); border: 0; border-radius: var(--radius-pill); color: var(--on-primary); cursor: pointer; display: inline-flex; flex: none; justify-content: center; padding: 0; transition: background var(--dur-fast) var(--ease); }
.play:active { background: var(--purple-hover); }
.cover-empty { align-items: center; background: var(--surface-alt); border: 1px solid var(--line); color: var(--ink-muted); display: flex; justify-content: center; }

.mini { align-items: center; background: var(--surface-alt); border: 1px solid var(--line); border-radius: var(--radius-md); bottom: max(var(--space-2), calc(env(safe-area-inset-bottom, 0px) - var(--space-3))); display: flex; gap: var(--space-1); height: 64px; left: var(--space-2); overflow: hidden; padding: 0 var(--space-1) 0 var(--space-2); position: fixed; right: var(--space-2); z-index: 100; }
.mini-open { align-items: center; background: none; border: 0; border-radius: var(--radius-sm); color: inherit; cursor: pointer; display: flex; flex: 1; gap: var(--space-3); min-width: 0; padding: 0; text-align: left; }
.mini-cover { border-radius: var(--radius-sm); flex: none; height: 44px; object-fit: cover; width: 44px; }
.mini-text { display: flex; flex-direction: column; min-width: 0; }
.mini-title { font: 600 16px/24px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mini-artist { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mini-play { height: 40px; margin: 0 2px; width: 40px; }
.mini-progress { background: var(--line); bottom: 0; height: 2px; left: 0; position: absolute; right: 0; }
.mini-progress span { background: var(--purple); display: block; height: 100%; }

.sheet { background: var(--surface); display: flex; flex-direction: column; inset: 0; overflow-y: auto; overscroll-behavior: contain; padding: calc(env(safe-area-inset-top, 0px) + var(--space-2)) var(--space-6) calc(env(safe-area-inset-bottom, 0px) + var(--space-4)); position: fixed; transform: translateY(100%); transition: transform var(--dur-med) var(--ease), visibility 0s linear var(--dur-med); visibility: hidden; z-index: 200; }
.sheet.open { transform: translateY(0); transition: transform var(--dur-med) var(--ease), visibility 0s; visibility: visible; }
.sheet.dragging { transition: none; }
.sheet-top { align-items: center; cursor: grab; display: grid; flex: none; grid-template-columns: 44px 1fr 44px; padding-top: var(--space-2); position: relative; touch-action: none; }
.grabber { background: var(--line-strong); border-radius: var(--radius-pill); height: 5px; left: 50%; position: absolute; top: 0; transform: translateX(-50%); width: 36px; }
.sheet-label { color: var(--ink-muted); font: 600 12px/16px var(--font-sans); margin: 0; text-align: center; }
.sheet-cover { align-self: center; aspect-ratio: 1; border-radius: var(--radius-md); flex: none; margin: var(--space-6) 0; object-fit: cover; width: min(100%, calc(100dvh - 420px)); }

.meta { align-items: flex-end; display: flex; flex: none; gap: var(--space-3); justify-content: space-between; position: relative; }
.meta-text { display: block; flex: 1; min-width: 0; }
.meta-btn { background: none; border: 0; border-radius: var(--radius-sm); color: inherit; cursor: pointer; margin: calc(var(--space-1) * -1); padding: var(--space-1); text-align: left; transition: background var(--dur-fast) var(--ease); }
.meta-btn:active, .meta-btn[aria-expanded="true"] { background: var(--surface-alt); }
.sheet-title { display: block; font: 700 20px/28px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sheet-artist { color: var(--ink-muted); display: block; font: 500 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.uploader { align-items: center; color: var(--ink-muted); display: inline-flex; flex: none; font: 600 12px/16px var(--font-sans); gap: var(--space-1); max-width: 40%; min-height: 44px; text-decoration: none; }
.uploader:hover { color: var(--ink); }
.uploader-pfp { border-radius: var(--radius-pill); flex: none; height: 20px; object-fit: cover; width: 20px; }
.uploader-initial { align-items: center; background: var(--primary); color: var(--on-primary); display: flex; font: 700 11px/1 var(--font-sans); justify-content: center; }
.uploader-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.menu { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.16); left: 0; min-width: 240px; opacity: 0; padding: var(--space-1) 0; pointer-events: none; position: absolute; top: calc(100% + var(--space-2)); transform: translateY(-4px) scale(0.98); transform-origin: top left; transition: opacity var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease), visibility 0s linear var(--dur-fast); visibility: hidden; z-index: 5; }
.menu.open { opacity: 1; pointer-events: auto; transform: none; transition: opacity var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease), visibility 0s; visibility: visible; }
.menu-item { align-items: center; background: none; border: 0; color: var(--ink); cursor: pointer; display: flex; font: 600 16px/24px var(--font-sans); gap: var(--space-3); min-height: 44px; padding: var(--space-2) var(--space-4); text-align: left; width: 100%; }
.menu-item:hover, .menu-item:focus-visible { background: var(--surface-alt); }
.menu-item + .menu-item { border-top: 1px solid var(--line); }
.menu-item svg { color: var(--ink-muted); flex: none; }
.menu-text { min-width: 0; }
.menu-sub { color: var(--ink-muted); display: block; font: 500 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.scrub { flex: none; margin-top: var(--space-4); }
.times { color: var(--ink-muted); display: flex; font: 600 12px/16px var(--font-sans); font-variant-numeric: tabular-nums; justify-content: space-between; }
.slider { -webkit-appearance: none; appearance: none; background: transparent; cursor: pointer; height: 24px; margin: 0; width: 100%; }
.slider::-webkit-slider-runnable-track { background: linear-gradient(to right, var(--purple) var(--fill, 0%), var(--line) var(--fill, 0%)); border-radius: var(--radius-pill); height: 4px; }
.slider::-moz-range-track { background: var(--line); border-radius: var(--radius-pill); height: 4px; }
.slider::-moz-range-progress { background: var(--purple); border-radius: var(--radius-pill); height: 4px; }
.slider::-webkit-slider-thumb { -webkit-appearance: none; background: var(--purple); border: 0; border-radius: var(--radius-pill); height: 16px; margin-top: -6px; width: 16px; }
.slider::-moz-range-thumb { background: var(--purple); border: 0; border-radius: var(--radius-pill); height: 16px; width: 16px; }

.transport { align-items: center; display: flex; flex: none; gap: var(--space-8); justify-content: center; margin-top: var(--space-4); }
.icon-btn.big { height: 56px; width: 56px; }
.sheet-play { height: 72px; width: 72px; }
.modes { display: flex; flex: none; justify-content: space-between; margin-top: auto; padding: var(--space-4) var(--space-2) 0; }
.mode { color: var(--ink-muted); flex-direction: column; gap: 2px; height: 48px; }
.mode.active { color: var(--link); }
.mode::after { background: currentColor; border-radius: var(--radius-pill); content: ""; height: 4px; opacity: 0; width: 4px; }
.mode.active::after { opacity: 1; }

@media (prefers-reduced-motion: reduce) {
  .sheet, .sheet.open, .menu, .menu.open, .play, .meta-btn { transition: none; }
}
</style>
