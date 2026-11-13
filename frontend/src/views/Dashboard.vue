<template>
  <div class="aux-page with-player">
    <AppHeader />
    <main>
      <section class="welcome">
        <div class="wrap">
          <h1 class="greeting">Welcome back, {{ username }}</h1>
        </div>
      </section>

      <div class="wrap below">
        <div class="rows">
          <section class="block" aria-labelledby="recent-heading">
            <div class="section-head">
              <div>
                <h2 id="recent-heading">Recent Listening</h2>
                <p v-if="!recent.preview" class="meta">The last songs you played, newest first.</p>
              </div>
            </div>

            <EmptyState v-if="recent.error" tone="error" :title="recent.error" :text="STREAM_RETRY" />
            <EmptyState v-else-if="recent.loading" tone="loading" title="Loading your recent listening…" />
            <EmptyState
              v-else-if="!recent.items.length"
              title="No plays yet"
              text="Play a song from one of your playlists or uploads and it shows up here."
            />
            <div v-else class="scroll-area">
              <ul
                ref="recentScroller"
                class="cards scroller"
                :class="{ dragging: recentDragging, hinting }"
                tabindex="0"
                aria-label="Recent Listening songs"
              >
                <li
                  v-for="item in recent.items"
                  :key="item.musicPiece.musicPieceId"
                  class="card"
                  :class="{ playing: !recent.preview && isCurrent(item.musicPiece) }"
                >
                  <component
                    :is="recent.preview ? 'span' : 'button'"
                    :type="recent.preview ? null : 'button'"
                    class="card-main"
                    :aria-label="recent.preview ? null : playLabel(item.musicPiece)"
                    @click="playRecent(item)"
                  >
                    <span class="card-art-wrap">
                      <img v-if="item.musicPiece.coverUrl" class="card-art" :src="resolveCoverURL(item.musicPiece.coverUrl)" alt="" loading="lazy" />
                      <span v-else class="card-art card-art-empty" aria-hidden="true">
                        <svg class="card-icon" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
                      </span>
                      <span v-if="!recent.preview" class="card-play" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M7 4.5v15l12.5-7.5z" /></svg></span>
                    </span>
                    <span class="card-body">
                      <span class="card-title">{{ item.musicPiece.name }}</span>
                      <span v-if="item.musicPiece.artistSummary?.artistName" class="card-meta">{{ item.musicPiece.artistSummary.artistName }}</span>
                      <span v-if="item.musicPiece.uploader" class="card-uploader">
                        <img v-if="item.musicPiece.uploader.pfpUrl" class="uploader-pfp" :src="resolveCoverURL(item.musicPiece.uploader.pfpUrl)" alt="" loading="lazy" />
                        <span v-else class="uploader-pfp uploader-initial" aria-hidden="true">{{ item.musicPiece.uploader.username.charAt(0).toUpperCase() }}</span>
                        <span class="uploader-name"><span class="sr-only">Uploaded by </span>{{ item.musicPiece.uploader.username }}</span>
                      </span>
                    </span>
                  </component>
                </li>
              </ul>
              <span v-if="hinting" :key="hintRun" class="drag-hint" aria-hidden="true" @animationend="hinting = false">
                <svg class="hint-cursor" viewBox="0 0 24 24">
                  <path d="M18 11.5V9a2 2 0 0 0-2-2a2 2 0 0 0-2 2v1.4" />
                  <path d="M14 10V8a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2" />
                  <path d="M10 9.9V9a2 2 0 0 0-2-2a2 2 0 0 0-2 2v5" />
                  <path d="M6 14a2 2 0 0 0-2-2a2 2 0 0 0-2 2" />
                  <path d="M18 11a2 2 0 1 1 4 0v3a8 8 0 0 1-8 8h-4a8 8 0 0 1-8-8 2 2 0 1 1 4 0" />
                </svg>
                <span class="hint-label">Drag To Scroll</span>
              </span>
            </div>
          </section>

          <section class="block" aria-labelledby="library-heading">
            <div class="section-head">
              <h2 id="library-heading">Your Library</h2>
              <div class="tabs" role="tablist" aria-label="Library filter">
                <button
                  v-for="item in LIBRARY_TABS"
                  :key="item.id"
                  type="button"
                  role="tab"
                  class="tab"
                  :class="{ active: libraryFilter === item.id }"
                  :aria-selected="libraryFilter === item.id ? 'true' : 'false'"
                  @click="libraryFilter = item.id"
                >{{ item.label }}</button>
              </div>
            </div>

            <EmptyState v-if="libraryError" tone="error" :title="libraryError" :text="STREAM_RETRY" />
            <EmptyState v-else-if="libraryLoading" tone="loading" title="Loading your library…" />
            <EmptyState
              v-else-if="libraryFilter === 'saved' && filteredLibrary.length === 0"
              title="Nothing saved yet"
              text="Save a playlist from someone else and it shows up here."
            >
              <button type="button" class="btn secondary" @click="findPlaylists">Find playlists</button>
            </EmptyState>
            <PlaylistGrid v-else ref="libraryGrid" class="big-grid" :class="{ dragging: libraryDragging }" :playlists="shownLibrary" :owner="username">
              <li v-if="libraryFilter !== 'saved'">
                <button type="button" class="new-tile" @click="createDialog.open()">
                  <span class="new-cover">
                    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                         stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                      <path d="M12 5v14" /><path d="M5 12h14" />
                    </svg>
                  </span>
                  <span class="new-name">Create</span>
                </button>
              </li>
            </PlaylistGrid>
            <button
              v-if="!isMobile && !libraryLoading && !libraryError && filteredLibrary.length > LIBRARY_PREVIEW_COUNT"
              type="button"
              class="btn secondary more"
              :aria-expanded="libraryExpanded ? 'true' : 'false'"
              @click="libraryExpanded = !libraryExpanded"
            >{{ libraryExpanded ? 'Show less' : `More (${filteredLibrary.length - LIBRARY_PREVIEW_COUNT})` }}</button>
          </section>

          <div class="split">
            <section class="block" aria-labelledby="uploads-heading">
              <div class="section-head">
                <div>
                  <h2 id="uploads-heading">My Uploads</h2>
                  <p class="meta">{{ uploadsSummary }}</p>
                </div>
                <router-link v-if="uploads.items.length" :to="{ name: 'Upload' }" class="btn secondary">Upload music</router-link>
              </div>

              <EmptyState v-if="uploads.error" tone="error" :title="uploads.error" :text="STREAM_RETRY" />
              <EmptyState v-else-if="uploads.loading" tone="loading" title="Loading your uploads…" />
              <EmptyState
                v-else-if="!uploads.items.length"
                title="No uploads yet"
                text="Upload an MP3 to start your library."
              >
                <router-link :to="{ name: 'Upload' }" class="btn primary">Upload music</router-link>
              </EmptyState>
              <ul
                v-else
                ref="uploadsScroller"
                class="cards scroller"
                :class="{ dragging: uploadsDragging }"
                tabindex="0"
                aria-label="My Uploads songs"
              >
                <li v-for="(piece, index) in uploads.items" :key="piece.musicPieceId" class="card" :class="{ playing: isPlayingUpload(piece) }">
                  <button type="button" class="card-main" :aria-label="playLabel(piece)" @click="musicStore.playPieces(uploads.items, index)">
                    <span class="card-art-wrap">
                      <img v-if="piece.coverUrl" class="card-art" :src="resolveCoverURL(piece.coverUrl)" alt="" loading="lazy" />
                      <span v-else class="card-art card-art-empty" aria-hidden="true">
                        <svg class="card-icon" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
                      </span>
                      <span class="card-play" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M7 4.5v15l12.5-7.5z" /></svg></span>
                    </span>
                    <span class="card-body">
                      <span class="card-title">{{ piece.name }}</span>
                      <span v-if="piece.artistSummary?.artistName" class="card-meta">{{ piece.artistSummary.artistName }}</span>
                    </span>
                  </button>
                  <button type="button" class="card-edit" :aria-label="`Edit ${piece.name}`" title="Edit song" @click="editDialog.open(piece)">
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M21.17 6.81a1 1 0 0 0-3.99-3.99L3.84 16.17a2 2 0 0 0-.5.83l-1.32 4.35a.5.5 0 0 0 .62.62l4.35-1.32a2 2 0 0 0 .83-.5z" />
                    </svg>
                  </button>
                </li>
              </ul>
            </section>

            <section class="block" aria-labelledby="artists-heading">
              <div class="section-head">
                <div>
                  <h2 id="artists-heading">Your Top Artists</h2>
                  <p v-if="!topArtists.preview" class="meta">The artists you've played most, all time.</p>
                </div>
              </div>

              <EmptyState v-if="topArtists.error" tone="error" :title="topArtists.error" :text="STREAM_RETRY" />
              <EmptyState v-else-if="topArtists.loading" tone="loading" icon="mic" title="Loading your top artists…" />
              <EmptyState
                v-else-if="!topArtists.items.length"
                icon="mic"
                title="No top artists yet"
                text="Your most-played artists appear here once you've listened to a few songs."
              />
              <template v-else>
                <ol class="artists" aria-label="Your Top Artists">
                  <li v-for="(artist, index) in shownArtists" :key="artist.artistId">
                    <component
                      :is="topArtists.preview ? 'span' : 'router-link'"
                      class="artist-row"
                      :to="topArtists.preview ? null : { name: 'Artist', params: { artistId: artist.artistId } }"
                    >
                      <span class="artist-rank">{{ index + 1 }}</span>
                      <img v-if="artist.artistPfpUrl" class="artist-av" :src="resolveCoverURL(artist.artistPfpUrl)" alt="" loading="lazy" />
                      <span v-else class="artist-av artist-av-empty" aria-hidden="true">{{ artist.artistName.charAt(0).toUpperCase() }}</span>
                      <span class="artist-name">{{ artist.artistName }}</span>
                    </component>
                  </li>
                </ol>
                <button
                  v-if="topArtists.items.length > ARTISTS_PREVIEW_COUNT"
                  type="button"
                  class="btn secondary more"
                  :aria-expanded="artistsExpanded ? 'true' : 'false'"
                  @click="artistsExpanded = !artistsExpanded"
                >{{ artistsExpanded ? 'Show less' : `Show all (${topArtists.items.length})` }}</button>
              </template>
            </section>
          </div>
        </div>

      </div>
    </main>
    <AppFooter />
    <CreateDialog ref="createDialog" />
    <EditPieceDialog ref="editDialog" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import CreateDialog from '@/components/CreateDialog.vue'
import EditPieceDialog from '@/components/EditPieceDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import PlaylistGrid from '@/components/PlaylistGrid.vue'
import { useUserStore } from '@/stores/user.js'
import { resolveCoverURL } from '@/utils/display.js'
import { PREVIEW_RECENTS, PREVIEW_TOP_ARTISTS } from '@/utils/dashboardPreview.js'
import { useDragScroll } from '@/utils/dragScroll.js'
import { STREAM_RETRY, useStreamRow } from '@/utils/streamRow.js'
import { useMusicStore } from '@/stores/music.js'

const LIBRARY_TABS = [
  { id: 'all', label: 'All' },
  { id: 'yours', label: 'Yours' },
  { id: 'saved', label: 'Saved' },
]
// Top artists shown before "Show all"
const ARTISTS_PREVIEW_COUNT = 5

const router = useRouter()
const createDialog = ref(null)
const editDialog = ref(null)
const userStore = useUserStore()

const username = computed(() => userStore.userData?.username ?? '')

// The rows come from the live event stream (stores/events.js); see utils/streamRow.js
const streamRow = useStreamRow

// Phones show every section as a sideways row, so the library lists all its playlists there instead of a preview
const mobileQuery = window.matchMedia?.('(max-width: 720px)') ?? null
const isMobile = ref(mobileQuery?.matches ?? false)
const onMobileChange = (e) => { isMobile.value = e.matches }
mobileQuery?.addEventListener?.('change', onMobileChange)
onBeforeUnmount(() => mobileQuery?.removeEventListener?.('change', onMobileChange))

// Library: the `library` event, an array of LibraryPlaylist. `isSaved` means saved from someone else
const libraryRow = streamRow('library', null, 'your library')
const library = computed(() => libraryRow.value.items)
const libraryLoading = computed(() => libraryRow.value.loading)
const libraryError = computed(() => libraryRow.value.error)
const libraryFilter = ref('all')
const LIBRARY_PREVIEW_COUNT = 7
const musicStore = useMusicStore()

// My Uploads: the `uploads` event. Clicking a song plays it with the rest of the uploads queued after it
const uploads = streamRow('uploads', 'uploadedMusicPieces', 'your uploads')
const uploadsSummary = computed(() => {
  const { items, loading, error } = uploads.value
  if (loading || error) return 'Songs you uploaded, newest first.'
  return items.length === 1 ? '1 song, newest first.' : `${items.length} songs, newest first.`
})

function isPlayingUpload(piece) {
  return !musicStore.currentPlaylistUUID && musicStore.currentMusicPiece.uuid === piece.musicPieceId
}

function isCurrent(piece) {
  return musicStore.currentMusicPiece.uuid === piece.musicPieceId
}

function playLabel(piece) {
  const artist = piece.artistSummary?.artistName
  return `Play ${piece.name}${artist ? ` by ${artist}` : ''}`
}

// A recent play starts its playlist from that song, with the current shuffle. Without a playlist, or when the playlist
// can't be played anymore, the song plays on its own
function playRecent({ musicPiece, playlistId, playlistOwnerUsername, playlistName }) {
  if (recent.value.preview) return
  if (!playlistId) return musicStore.playPieces([musicPiece])
  return musicStore.playPlaylist(playlistId, { start: musicPiece.musicPieceId, shuffle: musicStore.shuffleOn, fallback: musicPiece, name: playlistName ?? null, ownerUsername: playlistOwnerUsername ?? null })
}

// Next step for an empty Saved tab: search playlists on Explore
function findPlaylists() {
  router.push({ name: 'Explore', query: { category: 'PLAYLISTS' } })
}
const libraryExpanded = ref(false)

const filteredLibrary = computed(() => {
  if (libraryFilter.value === 'yours') return library.value.filter((p) => !p.isSaved)
  if (libraryFilter.value === 'saved') return library.value.filter((p) => p.isSaved)
  return library.value
})

const shownLibrary = computed(() =>
  libraryExpanded.value || isMobile.value ? filteredLibrary.value : filteredLibrary.value.slice(0, LIBRARY_PREVIEW_COUNT),
)

// Recent listening and top artists: the `recents` and `top-artists` events, up to 20 each, all in the scrolling rows
const recent = streamRow('recents', 'recents', 'your recent listening', PREVIEW_RECENTS)
const topArtists = streamRow('topArtists', 'artists', 'your top artists', PREVIEW_TOP_ARTISTS)
const artistsExpanded = ref(false)
const shownArtists = computed(() =>
  artistsExpanded.value ? topArtists.value.items : topArtists.value.items.slice(0, ARTISTS_PREVIEW_COUNT),
)

/* The card rows scroll sideways by mouse drag too. Recent Listening shows a hint (a pointer dragging the row) every
   HINT_EVERY_MS until the user drags it themselves; it never shows when they prefer reduced motion */

const HINT_EVERY_MS = 10_000
const recentScroller = ref(null)
const uploadsScroller = ref(null)
const libraryGrid = ref(null)
const libraryScroller = computed(() => libraryGrid.value?.$el ?? null)
const hinting = ref(false)
const hintRun = ref(0)
const hintDismissed = ref(false)
let hintTimer = null
let firstHint = null

const { dragging: recentDragging } = useDragScroll(recentScroller, {
  onDrag: () => {
    hintDismissed.value = true
    hinting.value = false
    clearInterval(hintTimer)
  },
})
const { dragging: libraryDragging } = useDragScroll(libraryScroller)
const { dragging: uploadsDragging } = useDragScroll(uploadsScroller)

function playHint() {
  const list = recentScroller.value
  if (hintDismissed.value || hinting.value || !list || list.scrollWidth <= list.clientWidth) return
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
  hintRun.value++
  hinting.value = true
}

watch(() => recent.value.items.length, async (count) => {
  if (!count) return
  await nextTick()
  clearInterval(hintTimer)
  clearTimeout(firstHint)
  firstHint = setTimeout(playHint, 1000)
  hintTimer = setInterval(playHint, HINT_EVERY_MS)
})

onBeforeUnmount(() => {
  clearInterval(hintTimer)
  clearTimeout(firstHint)
})

onMounted(async () => {
  if (!username.value) {
    await router.push({ name: 'Login', query: { redirect: '/music' } })
  }
})
</script>

<style scoped>
h2 { font: 700 20px/28px var(--font-sans); margin: 0; }
.meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.sr-only { clip: rect(0 0 0 0); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

.welcome { padding-top: var(--space-8); }
.greeting { font: 700 28px/34px var(--font-sans); margin: 0; overflow-wrap: anywhere; }

.wrap { padding-left: var(--page-gutter); padding-right: var(--page-gutter); }
.below { padding-bottom: var(--space-8); }
.block { border-bottom: 1px solid var(--line); min-width: 0; padding: var(--space-8) 0; }
.split { column-gap: var(--space-8); display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); }
.split .block { border-bottom: 0; }
.section-head { align-items: flex-end; display: flex; flex-wrap: wrap; gap: var(--space-3) var(--space-6); justify-content: space-between; margin-bottom: var(--space-6); }

.tabs { display: flex; gap: var(--space-6); }
.tab { background: none; border: 0; border-bottom: 3px solid transparent; color: var(--ink-muted); cursor: pointer; font: 600 14px/20px var(--font-sans); padding: 0 0 var(--space-2); }
.tab:hover { border-bottom-color: var(--primary); color: var(--ink); }
.tab.active { border-bottom-color: var(--primary); color: var(--link); }

.cards { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); list-style: none; margin: 0; padding: 0; }
.scroller { cursor: grab; grid-auto-columns: 160px; grid-auto-flow: column; grid-template-columns: none; overflow-x: auto; overscroll-behavior-x: contain; padding-bottom: var(--space-3); scrollbar-color: var(--line-strong) transparent; scrollbar-width: thin; }
.scroller:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.scroller.dragging { cursor: grabbing; user-select: none; }
.scroll-area { position: relative; }
.hinting > li { animation: hint-nudge 2.8s var(--ease) both; }
.drag-hint { align-items: center; animation: hint-move 2.8s var(--ease) both; display: flex; flex-direction: column; gap: var(--space-2); left: 50%; pointer-events: none; position: absolute; top: 30%; z-index: 2; }
.hint-cursor { fill: var(--surface); height: 44px; stroke: var(--ink); stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 44px; }
.hint-label { background: var(--ink); border-radius: var(--radius-pill); color: var(--surface); font: 600 14px/20px var(--font-sans); padding: var(--space-1) var(--space-3); white-space: nowrap; }
@keyframes hint-move {
  0% { opacity: 0; transform: translateX(40px); }
  12% { opacity: 1; transform: translateX(40px); }
  20% { transform: translateX(40px) scale(0.9); }
  48% { transform: translateX(-80px) scale(0.9); }
  74% { transform: translateX(40px) scale(0.9); }
  82% { opacity: 1; transform: translateX(40px); }
  100% { opacity: 0; transform: translateX(40px); }
}
@keyframes hint-nudge {
  0%, 20% { transform: translateX(0); }
  48% { transform: translateX(-120px); }
  74%, 100% { transform: translateX(0); }
}
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); display: flex; flex-direction: column; min-width: 0; overflow: hidden; position: relative; }
.card-edit { align-items: center; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-pill); color: var(--ink); cursor: pointer; display: flex; height: 36px; justify-content: center; opacity: 0; padding: 0; position: absolute; right: var(--space-2); top: var(--space-2); transition: opacity var(--dur-fast) var(--ease); width: 36px; }
.card-edit svg { fill: none; height: 18px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 18px; }
.card-edit:hover { background: var(--surface-alt); color: var(--link); }
.card-edit:focus-visible { opacity: 1; outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.card:hover .card-edit { opacity: 1; }
@media (hover: none) {
  .card-edit { opacity: 1; }
}
.card-main { background: none; border: 0; color: inherit; display: flex; flex: 1; flex-direction: column; font: inherit; min-width: 0; padding: 0; text-align: left; }
button.card-main, a.card-main { cursor: pointer; text-decoration: none; }
button.card-main:focus-visible, a.card-main:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: -2px; }
.scroller.dragging .card-main { cursor: grabbing; }
.card-art-wrap { display: block; position: relative; }
.card-play { align-items: center; background: var(--primary); border-radius: var(--radius-pill); bottom: var(--space-3); display: flex; height: 40px; justify-content: center; opacity: 0; position: absolute; right: var(--space-3); transition: opacity var(--dur-fast) var(--ease); width: 40px; }
.card-play svg { fill: var(--on-primary); height: 18px; width: 18px; }
.card-main:hover .card-play, .card-main:focus-visible .card-play, .playing .card-play { opacity: 1; }
.card-main:hover .card-play { background: var(--purple-hover); }
.card-art { aspect-ratio: 1; background: var(--surface-alt); display: block; object-fit: cover; width: 100%; }
.card-art-empty { color: var(--ink-muted); display: grid; place-items: center; }
.card-icon { fill: none; height: 48px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; width: 48px; }
.card-initial { background: var(--purple-soft); color: var(--link); font: 800 56px/1 var(--font-sans); }
.rank { background: var(--primary); border-radius: var(--radius-pill); color: var(--on-primary); font: 600 12px/16px var(--font-sans); left: var(--space-3); padding: 2px var(--space-2); position: absolute; top: var(--space-3); }
.card-body { background: var(--surface-alt); border-top: 1px solid var(--line); display: flex; flex: 1; flex-direction: column; gap: 2px; padding: var(--space-3) var(--space-4); }
.card-title { font: 600 16px/24px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
button.card-main:hover .card-title, a.card-main:hover .card-title, .playing .card-title { color: var(--link); }
.playing .card-body { background: var(--purple-soft); }
.card-uploader { align-items: center; color: var(--ink-muted); display: flex; font: 500 12px/16px var(--font-sans); gap: var(--space-1); margin-top: var(--space-1); min-width: 0; }
.uploader-pfp { border-radius: var(--radius-pill); flex: none; height: 16px; object-fit: cover; width: 16px; }
.uploader-initial { background: var(--primary); color: var(--on-primary); display: grid; font: 700 10px/1 var(--font-sans); place-items: center; }
.uploader-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }


.below .big-grid { gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }
.new-tile { background: none; border: 0; color: var(--ink); cursor: pointer; display: flex; flex-direction: column; gap: 2px; padding: 0; text-align: left; width: 100%; }
.new-cover { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-sm); color: var(--ink-muted); display: grid; margin-bottom: var(--space-2); place-items: center; transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease); }
.new-tile:hover .new-cover { background: var(--purple-soft); color: var(--link); }
.new-tile:hover .new-name { color: var(--link); text-decoration: underline; }
.new-name { font: 600 15px/22px var(--font-sans); }


.more { margin-top: var(--space-6); }

.artists { list-style: none; margin: 0; padding: 0; }
.artist-row { align-items: center; border-bottom: 1px solid var(--line); color: var(--ink); display: grid; gap: var(--space-3); grid-template-columns: 20px 44px minmax(0, 1fr); padding: var(--space-2) var(--space-3); text-decoration: none; }
.artists li:first-child .artist-row { border-top: 1px solid var(--line); }
a.artist-row:hover { background: var(--surface-alt); }
a.artist-row:hover .artist-name { color: var(--link); }
a.artist-row:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: -2px; }
.artist-rank { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); text-align: right; }
.artist-av { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-pill); display: block; height: 44px; object-fit: cover; width: 44px; }
.artist-av-empty { background: var(--purple-soft); color: var(--link); display: grid; font: 700 17px/1 var(--font-sans); place-items: center; }
.artist-name { font: 600 16px/24px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

@media (max-width: 1000px) {
  .split { grid-template-columns: minmax(0, 1fr); }
  .split .block { border-bottom: 1px solid var(--line); }
  .split .block:last-child { border-bottom: 0; }
}
@media (max-width: 720px) {
  .welcome { padding-top: var(--space-6); }
  .greeting { font-size: 24px; line-height: 30px; }
  .block { padding: var(--space-6) 0; }
  .section-head { margin-bottom: var(--space-4); }
  .scroller { grid-auto-columns: 136px; }
  .below .big-grid { cursor: grab; grid-auto-columns: 136px; grid-auto-flow: column; grid-template-columns: none; overflow-x: auto; overscroll-behavior-x: contain; padding-bottom: var(--space-3); scrollbar-color: var(--line-strong) transparent; scrollbar-width: thin; }
  .below .big-grid.dragging { cursor: grabbing; user-select: none; }
}
</style>
