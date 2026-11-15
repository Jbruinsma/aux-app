<template>
  <section v-if="showSection" class="recs" aria-labelledby="recs-heading" :aria-busy="status === 'loading' ? 'true' : 'false'">
    <div class="recs-head">
      <div>
        <h2 id="recs-heading">Recommended</h2>
        <p class="recs-sub">Based on the songs in this playlist</p>
      </div>
      <button type="button" class="refresh" :class="{ spinning }" :disabled="status === 'loading'" @click="refresh">
        <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12a9 9 0 0 1 15-6.7L21 8" /><path d="M21 3v5h-5" /><path d="M21 12a9 9 0 0 1-15 6.7L3 16" /><path d="M8 16H3v5" /></svg>
        Refresh
      </button>
    </div>

    <ol v-if="status === 'loading'" class="tracks" aria-hidden="true">
      <li v-for="n in SHOWN" :key="n" class="track skeleton">
        <span class="track-main">
          <span class="rank"></span>
          <span class="art"></span>
          <span class="track-text"><span class="bar" :style="{ width: `${30 + ((n * 17) % 30)}%` }"></span><span class="bar short"></span></span>
        </span>
      </li>
    </ol>

    <EmptyState v-else-if="status === 'error'" tone="error" title="Recommendations couldn't load." text="Check that the server is running, then try Refresh." />
    <EmptyState v-else-if="!visible.length" title="No recommendations yet" :text="EMPTY_TEXT" />

    <TransitionGroup v-else tag="ol" name="rec" class="tracks">
      <li v-for="piece in visible" :key="piece.musicPieceId" class="track">
        <button type="button" class="track-main" :aria-label="`Play ${label(piece)}`" @click="play(piece)">
          <span class="rank" aria-hidden="true">
            <svg class="rank-play" viewBox="0 0 24 24"><path d="M7 4.5v15l12.5-7.5z" /></svg>
          </span>
          <img v-if="piece.coverUrl" class="art" :src="resolveCoverURL(piece.coverUrl)" alt="" loading="lazy" />
          <span v-else class="art" aria-hidden="true">
            <svg class="icon" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
          </span>
          <span class="track-text">
            <span class="track-name">{{ piece.name }}</span>
            <span v-if="artistName(piece)" class="track-artist"><span class="artist-name artist-inline">{{ artistName(piece) }}</span></span>
          </span>
        </button>

        <router-link
          v-if="piece.artistSummary?.artistId"
          class="artist-col"
          :to="{ name: 'Artist', params: { artistId: piece.artistSummary.artistId } }"
        >{{ artistName(piece) }}</router-link>
        <span v-else-if="artistName(piece)" class="artist-col">{{ artistName(piece) }}</span>

        <router-link
          v-if="piece.uploader"
          class="uploader"
          :to="{ name: 'Profile', params: { username: piece.uploader.username } }"
          :aria-label="`Uploaded by ${piece.uploader.username}`"
          :title="`Uploaded by ${piece.uploader.username}`"
        >
          <img v-if="piece.uploader.pfpUrl" class="uploader-pfp" :src="resolveCoverURL(piece.uploader.pfpUrl)" alt="" loading="lazy" />
          <span v-else class="uploader-pfp uploader-initial" aria-hidden="true">{{ piece.uploader.username.charAt(0).toUpperCase() }}</span>
          <span class="uploader-name">{{ piece.uploader.username }}</span>
        </router-link>

        <div v-if="canAdd" class="add-wrap">
          <button
            :ref="(el) => setAddRef(piece.musicPieceId, el)"
            type="button"
            class="add-btn"
            :class="{ added: flashing.has(piece.musicPieceId) }"
            :aria-label="addLabel(piece)"
            :aria-haspopup="canEdit ? null : 'menu'"
            :aria-expanded="canEdit ? null : (pickerFor === piece.musicPieceId ? 'true' : 'false')"
            :aria-controls="canEdit ? null : `rec-picker-${piece.musicPieceId}`"
            :disabled="flashing.has(piece.musicPieceId) && canEdit"
            @click="onAdd(piece)"
          >
            <svg v-if="flashing.has(piece.musicPieceId)" class="icon small" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5" /></svg>
            <svg v-else class="icon small" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14" /><path d="M5 12h14" /></svg>
            {{ flashing.has(piece.musicPieceId) ? 'Added' : 'Add' }}
            <svg v-if="!canEdit && !flashing.has(piece.musicPieceId)" class="icon chev" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
          </button>

          <div
            v-if="pickerFor === piece.musicPieceId"
            :id="`rec-picker-${piece.musicPieceId}`"
            ref="pickerRef"
            class="picker"
            :class="{ up: pickerUp }"
            role="menu"
            aria-label="Add to playlist"
            @keydown="onPickerKeydown"
          >
            <p class="picker-title" aria-hidden="true">Add to playlist</p>
            <p v-if="library === null" class="picker-empty">Loading your playlists…</p>
            <p v-else-if="!myPlaylists.length" class="picker-empty">You don't have any playlists yet.</p>
            <div v-else class="picker-list">
              <button
                v-for="target in myPlaylists"
                :key="target.playlistId"
                type="button"
                class="picker-item"
                role="menuitem"
                tabindex="-1"
                :disabled="addingTo === target.playlistId"
                @click="addTo(target, piece)"
              >
                <img v-if="target.playlistCoverUrl" class="picker-cover" :src="resolveCoverURL(target.playlistCoverUrl)" alt="" />
                <span v-else class="picker-cover" aria-hidden="true"></span>
                <span class="picker-text">
                  <span class="picker-name">{{ target.playlistName }}</span>
                  <span class="picker-count">{{ target.totalPieces === 1 ? '1 song' : `${target.totalPieces} songs` }}</span>
                </span>
                <svg v-if="addedTo(piece).has(target.playlistId)" class="icon tick" viewBox="0 0 24 24" aria-label="Added"><path d="M20 6 9 17l-5-5" /></svg>
              </button>
            </div>
          </div>
        </div>
      </li>
    </TransitionGroup>
  </section>
</template>

<script setup>
// "Recommended" under a playlist's songs: 5 songs like the ones in it, from discover. Owners and editors add a song
// to this playlist in one click; anyone else adds it to one of their own playlists. Errors and empty results hide the
// section, since it's an extra
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import EmptyState from '@/components/EmptyState.vue'
import { useEventsStore } from '@/stores/events.js'
import { useMusicStore } from '@/stores/music.js'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'
import { fetchDiscover } from '@/utils/discover.js'
import { resolveCoverURL } from '@/utils/display.js'
import { addPiecesToPlaylist } from '@/utils/music.js'
import { addablePlaylists, mergeRecommendations, pickSeeds } from '@/utils/recommendations.js'

const props = defineProps({
  playlistId: { type: String, required: true },
  // Every song in the playlist (the page only shows this once all pages have loaded)
  tracks: { type: Array, default: () => [] },
  canEdit: Boolean,
})
// `added`: put the piece at the end of the list now; `add-failed`: the request failed, take it out again
const emit = defineEmits(['added', 'add-failed'])

const SHOWN = 5
// How long the "Added" check shows before the row leaves (owners) or the button resets (everyone else)
const FLASH_MS = 1500
const LEAVE_AFTER_MS = 700

const eventsStore = useEventsStore()
const musicStore = useMusicStore()
const notification = useNotificationStore()
const userStore = useUserStore()

const status = ref('loading')
const pool = ref([])
// Songs added here that are leaving the list (owners and editors)
const gone = reactive(new Set())
// Rows showing the "Added" check
const flashing = reactive(new Set())
const spinning = ref(false)
let request = 0

const inPlaylist = computed(() => new Set(props.tracks.map((track) => track.musicPieceId)))
// A song just added here stays (showing "Added") until it leaves, even though it's already in the playlist
const visible = computed(() => pool.value
  .filter((piece) => !gone.has(piece.musicPieceId) && (!inPlaylist.value.has(piece.musicPieceId) || flashing.has(piece.musicPieceId)))
  .slice(0, SHOWN))

// Logged-out visitors can still play the songs, but have nowhere to add them
const canAdd = computed(() => props.canEdit || userStore.loggedIn)
const library = computed(() => eventsStore.library)
const myPlaylists = computed(() => addablePlaylists(library.value, userStore.userData?.username, props.playlistId))

function artistName(piece) {
  return piece.artistSummary?.artistName ?? ''
}

function label(piece) {
  return artistName(piece) ? `${piece.name} by ${artistName(piece)}` : piece.name
}

function addLabel(piece) {
  if (flashing.has(piece.musicPieceId)) return `${piece.name} added`
  return props.canEdit ? `Add ${piece.name} to this playlist` : `Add ${piece.name} to one of your playlists`
}

// Owners and editors always see the section, with an empty or error state when there's nothing to show, so they know
// it's there. Listeners only see it when it has songs
const EMPTY_TEXT = "They come from other people's public songs, so check back as more get uploaded."
const showSection = computed(() => props.canEdit || status.value === 'loading' || (status.value === 'ready' && visible.value.length > 0))

// status: loading, ready (pool may be empty), or error (every discover request failed)
async function load() {
  const mine = ++request
  status.value = 'loading'
  const seeds = pickSeeds(props.tracks, userStore.userData?.username)
  if (!seeds.length) {
    pool.value = []
    status.value = 'ready'
    return
  }
  const results = await Promise.allSettled(seeds.map((seed) => fetchDiscover(seed.musicPieceId)))
  if (mine !== request) return
  const fulfilled = results.filter((r) => r.status === 'fulfilled')
  pool.value = mergeRecommendations(fulfilled.map((r) => r.value?.musicPieces ?? []), inPlaylist.value)
  gone.clear()
  status.value = fulfilled.length ? 'ready' : 'error'
}

function refresh() {
  spinning.value = false
  requestAnimationFrame(() => { spinning.value = true })
  closePicker()
  load()
}

// Plays the song on its own, outside the playlist
function play(piece) {
  musicStore.playPieces([piece])
}

function flash(id) {
  flashing.add(id)
  setTimeout(() => flashing.delete(id), FLASH_MS)
}

function onAdd(piece) {
  if (props.canEdit) addHere(piece)
  else togglePicker(piece)
}

// Owners and editors: straight into this playlist. The song joins the list at once and comes back out if the request fails
async function addHere(piece) {
  const id = piece.musicPieceId
  flash(id)
  emit('added', piece)
  setTimeout(() => gone.add(id), LEAVE_AFTER_MS)
  try {
    await addPiecesToPlaylist(props.playlistId, [id])
    notification.success(`Added "${piece.name}" to this playlist.`)
  } catch (err) {
    if (err.sessionEnded) return
    emit('add-failed', piece)
    gone.delete(id)
    flashing.delete(id)
    notification.error(`We couldn't add "${piece.name}". Check that the server is running, then try again.`)
  }
}

/* Everyone else: a menu of their own playlists */

const pickerFor = ref(null)
const pickerUp = ref(false)
const pickerRef = ref(null)
const addingTo = ref(null)
const addRefs = new Map()
// musicPieceId -> playlist ids it was added to on this visit, for the menu's check marks
const added = reactive(new Map())

function setAddRef(id, el) {
  if (el) addRefs.set(id, el)
  else addRefs.delete(id)
}

function addedTo(piece) {
  return added.get(piece.musicPieceId) ?? new Set()
}

// The menu is about 320px tall; near the bottom of the window (above the player bar) it opens upward
const PICKER_ROOM = 320
const PLAYER_BAR = 110

async function togglePicker(piece) {
  if (pickerFor.value === piece.musicPieceId) return closePicker(true)
  const button = addRefs.get(piece.musicPieceId)
  pickerUp.value = Boolean(button) && button.getBoundingClientRect().bottom + PICKER_ROOM > window.innerHeight - PLAYER_BAR
  pickerFor.value = piece.musicPieceId
  await nextTick()
  pickerItems()[0]?.focus()
}

function closePicker(refocus = false) {
  const id = pickerFor.value
  if (!id) return
  pickerFor.value = null
  if (refocus) addRefs.get(id)?.focus()
}

function pickerItems() {
  const menu = Array.isArray(pickerRef.value) ? pickerRef.value[0] : pickerRef.value
  return menu ? [...menu.querySelectorAll('[role="menuitem"]:not([disabled])')] : []
}

function onPickerKeydown(e) {
  const items = pickerItems()
  const i = items.indexOf(document.activeElement)
  if (e.key === 'Escape') {
    e.preventDefault()
    e.stopPropagation()
    closePicker(true)
  } else if (e.key === 'ArrowDown' && items.length) {
    e.preventDefault()
    items[(i + 1) % items.length].focus()
  } else if (e.key === 'ArrowUp' && items.length) {
    e.preventDefault()
    items[(i - 1 + items.length) % items.length].focus()
  } else if (e.key === 'Tab') {
    closePicker()
  }
}

async function addTo(target, piece) {
  if (addingTo.value) return
  addingTo.value = target.playlistId
  try {
    const result = await addPiecesToPlaylist(target.playlistId, [piece.musicPieceId])
    closePicker(true)
    if (result.failedCount) {
      notification.neutral(`"${piece.name}" is already in ${target.playlistName}, or can't be added to it.`)
      return
    }
    if (!added.has(piece.musicPieceId)) added.set(piece.musicPieceId, new Set())
    added.get(piece.musicPieceId).add(target.playlistId)
    flash(piece.musicPieceId)
    notification.success(`Added "${piece.name}" to ${target.playlistName}.`)
  } catch (err) {
    if (err.sessionEnded) return
    closePicker(true)
    notification.error(`We couldn't add "${piece.name}" to ${target.playlistName}. Check that the server is running, then try again.`)
  } finally {
    addingTo.value = null
  }
}

function closeOnOutside(e) {
  if (pickerFor.value && !e.target.closest?.('.add-wrap')) closePicker()
}

onMounted(() => {
  document.addEventListener('pointerdown', closeOnOutside)
  load()
})
onBeforeUnmount(() => {
  request++
  document.removeEventListener('pointerdown', closeOnOutside)
})
</script>

<style scoped>
.recs { padding-top: var(--space-8); }
.recs-head { align-items: flex-end; display: flex; gap: var(--space-4); justify-content: space-between; margin-bottom: var(--space-3); }
h2 { font: 700 20px/28px var(--font-sans); margin: 0; }
.recs-sub { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 2px 0 0; }
.refresh { align-items: center; background: none; border: 0; border-radius: var(--radius-sm); color: var(--link); cursor: pointer; display: inline-flex; flex: none; font: 600 14px/20px var(--font-sans); gap: var(--space-1); padding: var(--space-2); }
.refresh:hover:not(:disabled) { text-decoration: underline; }
.refresh:disabled { cursor: default; opacity: 0.4; }
.refresh .icon { height: 16px; width: 16px; }
.refresh.spinning .icon { animation: spin 600ms var(--ease); }
@keyframes spin { to { transform: rotate(360deg); } }
.icon { fill: none; flex: none; height: 20px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 20px; }
.icon.small { height: 16px; width: 16px; }

/* Rows match the playlist's own (PlaylistDetail.vue) */
.tracks { border-top: 1px solid var(--line); list-style: none; margin: 0; padding: 0; position: relative; }
.track { align-items: center; background: var(--surface); border-bottom: 1px solid var(--line); display: flex; transition: background var(--dur-fast) var(--ease); }
.track:hover { background: var(--surface-alt); }
.track-main { align-items: center; background: none; border: 0; color: inherit; cursor: pointer; display: grid; flex: 1; font: inherit; gap: var(--space-3); grid-template-columns: 32px 40px minmax(0, 1fr); min-width: 0; padding: var(--space-3) var(--space-2); text-align: left; }
.track-main:focus-visible { border-radius: var(--radius-sm); outline: 2px solid var(--focus-ring); outline-offset: -2px; }
.rank { display: grid; place-items: center; }
.rank-play { fill: var(--link); height: 20px; opacity: 0; width: 20px; }
.art { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-sm); color: var(--ink-muted); display: grid; height: 40px; object-fit: cover; place-items: center; width: 40px; }
.track-text { display: flex; flex-direction: column; min-width: 0; }
.track-name { font: 600 16px/24px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.track-artist { align-items: center; color: var(--ink-muted); display: flex; font: 500 14px/20px var(--font-sans); min-width: 0; white-space: nowrap; }
.artist-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
.artist-inline { display: none; }
.artist-col { color: var(--ink-muted); flex: none; font: 500 14px/20px var(--font-sans); max-width: 25%; overflow: hidden; padding: 0 var(--space-2); text-decoration: none; text-overflow: ellipsis; white-space: nowrap; }
a.artist-col:hover { color: var(--link); text-decoration: underline; }
.artist-col:focus-visible, .uploader:focus-visible { border-radius: var(--radius-sm); outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.uploader { align-items: center; color: var(--ink-muted); display: flex; flex: none; font: 500 14px/20px var(--font-sans); gap: var(--space-2); max-width: 30%; padding: 0 var(--space-2); text-decoration: none; }
.uploader:hover .uploader-name { color: var(--link); text-decoration: underline; }
.uploader-pfp { border-radius: var(--radius-pill); flex: none; height: 24px; object-fit: cover; width: 24px; }
.uploader-initial { background: var(--primary); color: var(--on-primary); display: grid; font: 700 11px/1 var(--font-sans); place-items: center; }
.uploader-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.add-wrap { flex: none; margin-right: var(--space-2); position: relative; }
.add-btn { align-items: center; background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); cursor: pointer; display: inline-flex; font: 600 14px/20px var(--font-sans); gap: var(--space-1); justify-content: center; min-width: 84px; padding: 7px 12px; transition: background var(--dur-fast) var(--ease), border-color var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease); }
.add-btn:hover:not(:disabled) { background: var(--surface-alt); }
.add-btn:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.add-btn.added { border-color: var(--success); color: var(--success); cursor: default; }
.add-btn .chev { height: 14px; margin-left: -2px; width: 14px; }

/* Same menu look as the playlist page's menus */
.picker { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.16); min-width: 280px; padding: var(--space-1) 0; position: absolute; right: 0; top: calc(100% + var(--space-1)); z-index: 20; }
.picker.up { bottom: calc(100% + var(--space-1)); top: auto; }
.picker-title { color: var(--ink-muted); font: 600 12px/16px var(--font-sans); margin: 0; padding: var(--space-2) var(--space-4) var(--space-1); }
.picker-empty { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; padding: var(--space-2) var(--space-4) var(--space-3); }
.picker-list { max-height: 248px; overflow-y: auto; }
.picker-item { align-items: center; background: none; border: 0; color: var(--ink); cursor: pointer; display: grid; gap: var(--space-3); grid-template-columns: 32px minmax(0, 1fr) 20px; min-height: 44px; padding: var(--space-1) var(--space-4); text-align: left; width: 100%; }
.picker-item:hover, .picker-item:focus-visible { background: var(--surface-alt); outline: none; }
.picker-item:disabled { cursor: wait; opacity: 0.6; }
.picker-cover { background: var(--surface-alt); border-radius: var(--radius-sm); display: block; height: 32px; object-fit: cover; width: 32px; }
.picker-text { min-width: 0; }
.picker-name { display: block; font: 600 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.picker-count { color: var(--ink-muted); display: block; font: 500 12px/16px var(--font-sans); }
.tick { color: var(--success); }

.skeleton:hover { background: var(--surface); }
.skeleton .track-main { cursor: default; }
.skeleton .art, .bar { animation: pulse 1.2s var(--ease) infinite alternate; background: var(--surface-alt); }
.bar { border-radius: var(--radius-sm); display: block; height: 14px; }
.bar.short { height: 12px; margin-top: 6px; width: 18%; }
@keyframes pulse { from { opacity: 0.5; } to { opacity: 1; } }

/* An added song fades out; the next one fades in at the bottom and the rest slide up */
.rec-enter-active, .rec-leave-active { transition: opacity var(--dur-med) var(--ease), transform var(--dur-med) var(--ease); }
.rec-enter-from { opacity: 0; transform: translateY(4px); }
.rec-leave-to { opacity: 0; transform: translateY(-4px); }
.rec-leave-active { left: 0; position: absolute; right: 0; }
.rec-move { transition: transform var(--dur-med) var(--ease); }

@media (hover: hover) {
  .track:not(.skeleton):hover .rank-play, .track-main:focus-visible .rank-play { opacity: 1; }
}

@media (max-width: 720px) {
  .track-main { gap: var(--space-2); grid-template-columns: 24px 40px minmax(0, 1fr); padding-left: 0; }
  .artist-col, .uploader-name { display: none; }
  .artist-inline { display: inline; }
  .add-btn { min-width: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .rec-enter-active, .rec-leave-active, .rec-move, .add-btn, .track { transition: none; }
  .refresh.spinning .icon, .skeleton .art, .bar { animation: none; }
}
</style>
