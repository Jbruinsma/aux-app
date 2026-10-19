<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="wrap">
      <router-link :to="playlistRoute" class="back">
        <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>
        {{ playlist ? playlist.playlistName : 'Back to playlist' }}
      </router-link>
      <h1>Add music</h1>

      <p v-if="status === 'loading'" class="meta">Loading…</p>

      <section v-else-if="status === 'forbidden'" class="page-state">
        <p class="meta">Only the playlist's owner and editors can add music.</p>
        <router-link :to="playlistRoute" class="btn secondary">Back to playlist</router-link>
      </section>

      <section v-else-if="status === 'error'" class="page-state">
        <p class="meta">We couldn't load this playlist. Check that the server is running, then try again.</p>
        <button type="button" class="btn secondary" @click="load">Try again</button>
      </section>

      <template v-else>
        <div class="toolbar">
          <div class="tabs" role="tablist" aria-label="Where to find songs">
            <button
              v-for="item in SOURCES"
              :key="item.id"
              type="button"
              role="tab"
              class="tab"
              :class="{ active: source === item.id }"
              :aria-selected="source === item.id ? 'true' : 'false'"
              @click="source = item.id"
            >{{ item.label }}</button>
          </div>
          <router-link :to="uploadRoute" class="btn text">Upload a new song</router-link>
        </div>

        <div v-if="source === 'search'" class="search">
          <label class="visually-hidden" for="music-search">Search songs and artists</label>
          <input
            id="music-search"
            v-model="searchText"
            class="input"
            type="search"
            autocomplete="off"
            maxlength="200"
            placeholder="Search songs and artists"
          />
        </div>

        <div class="action-bar">
          <p class="meta" aria-live="polite">{{ selectedText }}</p>
          <button type="button" class="btn primary" :disabled="!selected.size || adding" @click="add">
            {{ adding ? 'Adding…' : selected.size > 1 ? `Add ${selected.size} songs` : 'Add to playlist' }}
          </button>
        </div>

        <p v-if="list.error" class="error" role="alert">{{ list.error }}</p>
        <p v-else-if="list.loading" class="meta">{{ source === 'uploads' ? 'Loading your uploads…' : 'Searching…' }}</p>
        <EmptyState
          v-else-if="source === 'uploads' && !list.items.length"
          title="No uploads yet"
          text="Upload an MP3 and it shows up here, ready to add."
        />
        <p v-else-if="source === 'search' && !searchText.trim()" class="meta">Type a song or artist to find music on Aux.</p>
        <EmptyState
          v-else-if="!list.items.length"
          icon="disc"
          :title="`No songs match “${searchText.trim()}”`"
          text="Check the spelling or try a shorter search."
        />
        <ul v-else class="songs">
          <li v-for="piece in list.items" :key="piece.musicPieceId" class="song" :class="{ chosen: selected.has(piece.musicPieceId) }">
            <label class="song-main" :class="{ disabled: inPlaylist.has(piece.musicPieceId) }">
              <input
                type="checkbox"
                :checked="selected.has(piece.musicPieceId)"
                :disabled="inPlaylist.has(piece.musicPieceId)"
                @change="toggle(piece)"
              />
              <img v-if="piece.coverUrl" class="art" :src="resolveCoverURL(piece.coverUrl)" alt="" loading="lazy" />
              <span v-else class="art" aria-hidden="true"></span>
              <span class="song-text">
                <span class="song-title">{{ piece.name }}</span>
                <span v-if="piece.artistSummary?.artistName" class="song-artist">{{ piece.artistSummary.artistName }}</span>
              </span>
              <span v-if="inPlaylist.has(piece.musicPieceId)" class="tag">In playlist</span>
            </label>
          </li>
        </ul>

      </template>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import EmptyState from '@/components/EmptyState.vue'
import { resolveCoverURL } from '@/utils/display.js'
import { fetchPlaylist } from '@/utils/playlist.js'
import { addPiecesToPlaylist, additionMessage, fetchUploads, searchMusic } from '@/utils/music.js'
import { useNotificationStore } from '@/stores/notification.js'

const SOURCES = [
  { id: 'uploads', label: 'Your uploads' },
  { id: 'search', label: 'Search music' },
]

const route = useRoute()
const router = useRouter()
const notification = useNotificationStore()
const { username, id } = route.params
const playlistRoute = { name: 'Playlist', params: { username, id } }
const uploadRoute = { name: 'Upload', query: { playlist: id, owner: username } }

const status = ref('loading')
const playlist = ref(null)
const source = ref('uploads')
const searchText = ref('')
const list = reactive({ items: [], loading: false, error: '' })
// Songs already in the playlist, from its first page of tracks. Others already in it come back as skipped
const inPlaylist = ref(new Set())
const selected = ref(new Map())
const adding = ref(false)
let searchTimer = null
let requestId = 0

const selectedText = computed(() => {
  if (!selected.value.size) return 'Choose songs to add.'
  return selected.value.size === 1 ? '1 song chosen.' : `${selected.value.size} songs chosen.`
})

async function load() {
  status.value = 'loading'
  try {
    const result = await fetchPlaylist(username, id, { ignoredErrorCodes: ['PLAYLIST_NOT_FOUND', 'PLAYLIST_INVITE_PENDING'] })
    if (result.access !== 'OWNER' && result.access !== 'EDITOR') {
      status.value = 'forbidden'
      return
    }
    playlist.value = result
    inPlaylist.value = new Set(result.musicPieces.map((piece) => piece.musicPieceId))
    status.value = 'ready'
    loadSource()
  } catch (err) {
    if (err.sessionEnded) return
    status.value = err.code === 'PLAYLIST_NOT_FOUND' || err.code === 'PLAYLIST_INVITE_PENDING' ? 'forbidden' : 'error'
  }
}

async function loadSource() {
  clearTimeout(searchTimer)
  const request = ++requestId
  list.error = ''
  if (source.value === 'search' && !searchText.value.trim()) {
    list.items = []
    list.loading = false
    return
  }
  list.loading = true
  try {
    const items = source.value === 'uploads'
      ? (await fetchUploads()).uploadedMusicPieces
      : (await searchMusic(searchText.value.trim())).results
    if (request === requestId) list.items = items
  } catch (err) {
    if (request !== requestId || err.sessionEnded) return
    list.items = []
    list.error = err.code === 'RATE_LIMITED'
      ? 'Too many searches at once. Wait a few seconds, then try again.'
      : "We couldn't load songs. Check that the server is running, then try again."
  } finally {
    if (request === requestId) list.loading = false
  }
}

watch(source, loadSource)
watch(searchText, () => {
  clearTimeout(searchTimer)
  list.loading = Boolean(searchText.value.trim())
  searchTimer = setTimeout(loadSource, 300)
})
onBeforeUnmount(() => clearTimeout(searchTimer))

function toggle(piece) {
  const next = new Map(selected.value)
  if (next.has(piece.musicPieceId)) next.delete(piece.musicPieceId)
  else next.set(piece.musicPieceId, piece)
  selected.value = next
}

async function add() {
  if (!selected.value.size || adding.value) return
  adding.value = true
  try {
    const result = await addPiecesToPlaylist(id, [...selected.value.keys()])
    notification[result.failedCount ? 'neutral' : 'success'](additionMessage(result))
    await router.push(playlistRoute)
  } catch (err) {
    if (err.sessionEnded) return
    notification.error(err.code === 'PLAYLIST_NOT_FOUND'
      ? "This playlist doesn't exist anymore, or you can no longer edit it."
      : "We couldn't add those songs. Check that the server is running, then try again.")
  } finally {
    adding.value = false
  }
}

load()
</script>

<style scoped>
.wrap { padding: var(--space-8) var(--page-gutter); width: 100%; }
.back { align-items: center; color: var(--link); display: inline-flex; font: 600 14px/20px var(--font-sans); gap: var(--space-1); margin-bottom: var(--space-2); text-decoration: none; }
.back:hover { text-decoration: underline; }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-6); }
.meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.icon { fill: none; height: 16px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 16px; }
.page-state { align-items: flex-start; display: flex; flex-direction: column; gap: var(--space-3); }
.visually-hidden { clip-path: inset(50%); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

.toolbar { align-items: flex-end; border-bottom: 1px solid var(--line); display: flex; flex-wrap: wrap; gap: var(--space-3); justify-content: space-between; margin-bottom: var(--space-4); }
.tabs { display: flex; gap: var(--space-6); }
.tab { background: none; border: 0; border-bottom: 3px solid transparent; color: var(--ink-muted); cursor: pointer; font: 600 14px/20px var(--font-sans); padding: 0 0 var(--space-2); }
.tab:hover { border-bottom-color: var(--primary); color: var(--ink); }
.tab.active { border-bottom-color: var(--primary); color: var(--link); }
.search { margin-bottom: var(--space-4); max-width: 560px; }
.input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px 12px; width: 100%; }

.songs { border-top: 1px solid var(--line); list-style: none; margin: 0; max-width: 800px; padding: 0; }
.song { border-bottom: 1px solid var(--line); }
.song:hover { background: var(--surface-alt); }
.song.chosen { background: var(--purple-soft); }
.song-main { align-items: center; cursor: pointer; display: flex; gap: var(--space-3); padding: var(--space-3) var(--space-2); }
.song-main.disabled { cursor: default; opacity: 0.6; }
.song-main input { accent-color: var(--primary); flex: none; height: 18px; margin: 0; width: 18px; }
.art { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-sm); flex: none; object-fit: cover; width: 40px; }
.song-text { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.song-title { font: 600 16px/24px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.song-artist { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tag { color: var(--ink-muted); flex: none; font: 600 12px/16px var(--font-sans); }

.action-bar { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-4); justify-content: space-between; margin-bottom: var(--space-4); max-width: 800px; }
</style>
