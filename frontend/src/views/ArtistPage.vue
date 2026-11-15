<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="wrap">
      <p v-if="status === 'loading'" class="meta page-state" role="status">Loading artist…</p>

      <section v-else-if="status === 'missing'" class="page-state">
        <h1>This artist isn't on Aux</h1>
        <p class="meta">The link may be wrong. Find artists from your home screen.</p>
        <router-link :to="{ name: 'Dashboard' }" class="btn secondary">Go home</router-link>
      </section>

      <section v-else-if="status === 'error'" class="page-state">
        <h1>We couldn't load this artist</h1>
        <p class="meta">Check that the server is running, then try again.</p>
        <button type="button" class="btn secondary" @click="loadArtist">Try again</button>
      </section>

      <div v-else class="layout">
        <aside class="side">
          <ProfileCard
            flat
            :username="artist.artistName"
            :display-name="artist.artistName"
            subtitle="Artist"
            :picture-url="artist.artistPfpUrl ? resolveCoverURL(artist.artistPfpUrl) : ''"
            :banner-url="artist.artistBannerUrl ? resolveCoverURL(artist.artistBannerUrl) : ''"
          >
            <router-link v-if="!loggedIn" to="/login" class="btn secondary action">Log in to favorite</router-link>
            <button
              v-else
              type="button"
              class="btn action"
              :class="artist.isFavorite ? 'secondary' : 'primary'"
              :aria-pressed="artist.isFavorite ? 'true' : 'false'"
              :disabled="favoriting"
              @click="toggleFavorite"
            >
              <svg class="icon" :class="{ filled: artist.isFavorite }" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
              </svg>
              {{ artist.isFavorite ? 'Favorited' : 'Favorite' }}
            </button>
            <p v-if="favoriteError" class="error" role="alert">{{ favoriteError }}</p>
            <router-link v-if="loggedIn" :to="addMusicRoute" class="btn secondary action">Add music</router-link>
            <button v-if="artist.canEdit" type="button" class="btn secondary action" @click="imagesDialog.open(artist)">
              Edit picture and banner
            </button>
          </ProfileCard>
        </aside>

        <div class="main-col">
          <section aria-labelledby="songs-heading">
            <h2 id="songs-heading">Songs by {{ artist.artistName }}</h2>
            <EmptyState v-if="songs.error" tone="error" :title="songs.error" text="Check that the server is running, then try again.">
              <button type="button" class="btn secondary" @click="loadSongs()">Try again</button>
            </EmptyState>
            <EmptyState v-else-if="songs.loading && !songs.items.length" tone="loading" :title="`Loading songs by ${artist.artistName}…`" />
            <EmptyState
              v-else-if="!songs.items.length"
              title="No songs yet"
              :text="`Songs credited to ${artist.artistName} show up here as soon as someone uploads one.`"
            >
              <router-link v-if="loggedIn" :to="addMusicRoute" class="btn secondary">Add music</router-link>
            </EmptyState>
            <template v-else>
              <div v-if="loggedIn" class="songs-tools">
                <p class="meta">{{ albums.items.length ? 'Songs are grouped into albums, then singles.' : 'Group your songs into albums so they\'re easier to find.' }}</p>
                <button type="button" class="btn secondary small" @click="albumDialog.open()">New album</button>
              </div>

              <section
                v-for="section in arranged.albums"
                :key="section.album.albumId"
                class="album"
                :aria-labelledby="`album-${section.album.albumId}`"
              >
                <header class="album-head">
                  <img class="album-cover" :src="resolveCoverURL(section.album.coverUrl)" alt="" />
                  <div class="album-text">
                    <p class="eyebrow">Album</p>
                    <h3 :id="`album-${section.album.albumId}`">{{ section.album.albumName }}</h3>
                    <p class="meta">{{ section.album.pieceCount === 1 ? '1 song' : `${section.album.pieceCount} songs` }}</p>
                  </div>
                  <div class="album-actions">
                    <button type="button" class="btn primary small" :disabled="!section.pieces.length" @click="musicStore.playPieces(section.pieces, 0)">
                      Play<span class="sr-only"> {{ section.album.albumName }}</span>
                    </button>
                    <router-link v-if="loggedIn" :to="{ name: 'Upload', query: { artist: artist.artistId, album: section.album.albumId } }" class="btn secondary small">
                      Add a song<span class="sr-only"> to {{ section.album.albumName }}</span>
                    </router-link>
                    <button v-if="section.album.canEdit" type="button" class="btn secondary small" @click="albumDialog.open(section.album)">
                      Edit<span class="sr-only"> {{ section.album.albumName }}</span>
                    </button>
                  </div>
                </header>
                <TrackRows
                  v-if="section.pieces.length"
                  :pieces="section.pieces"
                  :label="`${section.album.albumName} by ${artist.artistName}, most played first`"
                  ranked
                  show-uploader
                  @play="(index) => musicStore.playPieces(section.pieces, index)"
                />
                <p v-else-if="albumPieces[section.album.albumId]?.error" class="meta">We couldn't load this album's songs.</p>
                <p v-else-if="!albumPieces[section.album.albumId]?.loading" class="meta">No songs on it yet.</p>
                <button
                  v-if="albumPieces[section.album.albumId]?.nextCursor"
                  type="button"
                  class="btn secondary more"
                  :disabled="albumPieces[section.album.albumId].loading"
                  @click="loadAlbumPieces(section.album, albumPieces[section.album.albumId].nextCursor)"
                >
                  {{ albumPieces[section.album.albumId].loading ? 'Loading…' : 'Show more' }}
                </button>
              </section>

              <section v-if="arranged.singles.length" class="album singles" aria-labelledby="singles-heading">
                <h3 v-if="arranged.albums.length" id="singles-heading" class="singles-title">Singles</h3>
                <h3 v-else id="singles-heading" class="sr-only">All songs</h3>
                <TrackRows
                  :pieces="arranged.singles"
                  :label="`${arranged.albums.length ? 'Singles' : 'Songs'} by ${artist.artistName}, most played first`"
                  ranked
                  show-uploader
                  @play="(index) => musicStore.playPieces(arranged.singles, index)"
                >
                  <template #action="{ piece }">
                    <button
                      v-if="isMine(piece)"
                      type="button"
                      class="row-edit"
                      :aria-label="`Change the cover of ${piece.name}`"
                      title="Change cover"
                      @click="editDialog.open(piece)"
                    >
                      <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="9" cy="9" r="2" /><path d="m21 15-3.09-3.09a2 2 0 0 0-2.82 0L6 21" /></svg>
                    </button>
                  </template>
                </TrackRows>
              </section>
              <button v-if="songs.nextCursor" type="button" class="btn secondary more" :disabled="songs.loading" @click="loadSongs(songs.nextCursor)">
                {{ songs.loading ? 'Loading…' : 'Show more' }}
              </button>
            </template>
          </section>
        </div>
      </div>
    </main>

    <ArtistImagesDialog ref="imagesDialog" @updated="Object.assign(artist, $event)" />
    <AlbumDialog ref="albumDialog" :artist="artist" :albums="albums.items" @saved="onAlbumSaved" @deleted="onAlbumDeleted" />
    <EditPieceDialog ref="editDialog" @saved="onPieceSaved" />
    <AppFooter />
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import AlbumDialog from '@/components/AlbumDialog.vue'
import ArtistImagesDialog from '@/components/ArtistImagesDialog.vue'
import EditPieceDialog from '@/components/EditPieceDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import ProfileCard from '@/components/ProfileCard.vue'
import TrackRows from '@/components/TrackRows.vue'
import { resolveCoverURL } from '@/utils/display.js'
import { fetchArtist, fetchArtistPieces, setArtistFavorite } from '@/utils/artist.js'
import { arrangeByAlbum, fetchAlbumPieces, fetchArtistAlbums } from '@/utils/albums.js'
import { useMusicStore } from '@/stores/music.js'
import { useUserStore } from '@/stores/user.js'
import { useNotificationStore } from '@/stores/notification.js'

// GET /api/artists/{artistId}: laid out like a user's profile. Anyone can view it; favoriting needs an account, and
// the artist's creator (canEdit) can change its picture and banner. Songs come from GET /api/artists/{id}/music-pieces,
// most played first, and play on their own, like My uploads. Add music opens Upload with this artist already picked.
// Albums (GET /api/artists/{id}/albums) each get their own section with their songs from GET /api/albums/{id}/music-pieces,
// most played first; songs with no `album` are singles, whose uploader can change their cover here. Signed-in users
// can make albums and put their own songs on them
const route = useRoute()
const userStore = useUserStore()
const notification = useNotificationStore()
const musicStore = useMusicStore()

const artistId = computed(() => route.params.artistId)
const loggedIn = computed(() => userStore.loggedIn)
const artist = ref(null)
const status = ref('loading')
const favoriting = ref(false)
const favoriteError = ref('')
const imagesDialog = ref(null)
const addMusicRoute = computed(() => ({ name: 'Upload', query: { artist: artistId.value } }))

async function loadArtist() {
  const requested = artistId.value
  status.value = 'loading'
  favoriteError.value = ''
  try {
    const data = await fetchArtist(requested)
    if (requested !== artistId.value) return
    artist.value = data
    status.value = 'ready'
    albums.items = []
    loadSongs()
    loadAlbums()
  } catch (err) {
    if (err.sessionEnded || requested !== artistId.value) return
    status.value = err.status === 404 ? 'missing' : 'error'
  }
}

const songs = reactive({ items: [], nextCursor: null, loading: true, error: '' })

async function loadSongs(cursor = null) {
  const current = artist.value
  if (!cursor) Object.assign(songs, { items: [], nextCursor: null })
  songs.loading = true
  songs.error = ''
  try {
    const page = await fetchArtistPieces(current.artistId, cursor)
    if (artist.value !== current) return
    songs.items = cursor ? [...songs.items, ...page.musicPieces] : page.musicPieces
    songs.nextCursor = page.nextCursor ?? null
  } catch (err) {
    if (err.sessionEnded || artist.value !== current) return
    songs.error = "We couldn't load this artist's songs."
  } finally {
    if (artist.value === current) songs.loading = false
  }
}

const albums = reactive({ items: [] })
// albumId → { items, nextCursor, loading, error }
const albumPieces = reactive({})
const albumDialog = ref(null)
const editDialog = ref(null)
const arranged = computed(() => arrangeByAlbum(albums.items, albumPieces, songs.items))

async function loadAlbums() {
  const current = artist.value
  try {
    const items = await fetchArtistAlbums(current.artistId)
    if (artist.value !== current) return
    for (const key of Object.keys(albumPieces)) delete albumPieces[key]
    albums.items = items
    items.forEach((album) => loadAlbumPieces(album))
  } catch {
    // Songs on albums don't show; the singles still do
    if (artist.value === current) albums.items = []
  }
}

async function loadAlbumPieces(album, cursor = null) {
  const current = artist.value
  if (!albumPieces[album.albumId]) albumPieces[album.albumId] = { items: [], nextCursor: null, loading: false, error: false }
  const entry = albumPieces[album.albumId]
  entry.loading = true
  entry.error = false
  try {
    const page = await fetchAlbumPieces(album.albumId, cursor)
    if (artist.value !== current || albumPieces[album.albumId] !== entry) return
    entry.items = cursor ? [...entry.items, ...page.musicPieces] : page.musicPieces
    entry.nextCursor = page.nextCursor ?? null
  } catch (err) {
    if (!err.sessionEnded) entry.error = true
  } finally {
    entry.loading = false
  }
}

// Saving moves songs between albums and singles, and can change their covers, so both are read again
function onAlbumSaved() {
  loadAlbums()
  loadSongs()
}

// Its songs are singles now
function onAlbumDeleted(albumId) {
  albums.items = albums.items.filter((album) => album.albumId !== albumId)
  delete albumPieces[albumId]
  loadSongs()
}

function isMine(piece) {
  return loggedIn.value && piece.uploader?.userId === userStore.userData?.userId
}

function onPieceSaved(updated) {
  if (!updated?.musicPieceId) return
  // Credited to another artist now: it leaves this page
  if (updated.artistSummary?.artistId && updated.artistSummary.artistId !== artist.value.artistId) {
    songs.items = songs.items.filter((piece) => piece.musicPieceId !== updated.musicPieceId)
    return
  }
  const merge = (piece) => (piece.musicPieceId === updated.musicPieceId ? { ...piece, ...updated, playCount: piece.playCount } : piece)
  songs.items = songs.items.map(merge)
  for (const entry of Object.values(albumPieces)) entry.items = entry.items.map(merge)
}

async function toggleFavorite() {
  const favorite = !artist.value.isFavorite
  favoriteError.value = ''
  favoriting.value = true
  try {
    await setArtistFavorite(artist.value.artistId, favorite)
    artist.value.isFavorite = favorite
    notification.success(favorite ? `${artist.value.artistName} is in your favorite artists.` : `Removed ${artist.value.artistName} from your favorite artists.`)
  } catch (err) {
    if (err.sessionEnded) return
    if (err.status === 404) return loadArtist()
    favoriteError.value = favorite
      ? "We couldn't favorite this artist. Check that the server is running, then try again."
      : "We couldn't remove this artist from your favorites. Check that the server is running, then try again."
  } finally {
    favoriting.value = false
  }
}

watch(artistId, loadArtist, { immediate: true })
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; padding: 0 var(--page-gutter); width: 100%; }
.wrap:has(.layout) { padding-left: 0; }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-2); }
h2 { font: 700 20px/28px var(--font-sans); margin: 0 0 var(--space-3); overflow-wrap: anywhere; }
.meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: var(--space-2) 0 0; }

.page-state { align-items: flex-start; display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-8) 0; }
p.page-state { display: block; }

.layout { display: grid; flex: 1; grid-template-columns: calc(400px + var(--page-gutter)) minmax(0, 1fr); }
.side :deep(.body) { padding-left: var(--page-gutter); }
.side { align-self: start; height: calc(100dvh - var(--header-height) - env(safe-area-inset-top, 0px)); overflow-y: auto; position: sticky; top: calc(var(--header-height) + env(safe-area-inset-top, 0px)); }
.main-col { border-left: 1px solid var(--line); display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-8) 0 var(--space-8) var(--space-8); }

/* .btn.action: the global .btn is inline-block, which would leave the heart sitting on the text's baseline */
.btn.action { align-items: center; display: flex; gap: var(--space-2); justify-content: center; margin-top: var(--space-4); }
.btn.action ~ .btn.action { margin-top: var(--space-3); }
.icon { display: block; fill: none; flex: none; height: 18px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 18px; }
.icon.filled { fill: var(--link); stroke: var(--link); }

.more { margin-top: var(--space-6); }
.sr-only { clip-path: inset(50%); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }
.btn.small { font-size: 14px; padding: 6px 12px; }

.songs-tools { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-3); justify-content: space-between; margin-bottom: var(--space-4); }

/* Albums: each gets its own section, split from the next by a rule */
.album { border-top: 1px solid var(--line); padding-top: var(--space-6); }
.album + .album { margin-top: var(--space-8); }
.album:first-of-type { border-top: 0; padding-top: 0; }
.album-head { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-4); margin-bottom: var(--space-4); }
.album-cover { border-radius: var(--radius-sm); flex: none; height: 96px; object-fit: cover; width: 96px; }
.album-text { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.eyebrow { color: var(--ink-muted); font: 600 12px/16px var(--font-sans); letter-spacing: 0.06em; margin: 0; text-transform: uppercase; }
h3 { font: 700 20px/28px var(--font-sans); margin: 0; overflow-wrap: anywhere; }
.album-actions { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.singles-title { margin-bottom: var(--space-3); }
.row-edit { align-items: center; background: none; border: 0; border-radius: var(--radius-pill); color: var(--ink-muted); cursor: pointer; display: flex; height: 32px; justify-content: center; padding: 0; width: 32px; }
.row-edit:hover { background: var(--surface); color: var(--link); }
.row-edit:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.row-edit svg { fill: none; height: 18px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 18px; }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .side { border-bottom: 1px solid var(--line); height: auto; margin-right: calc(-1 * var(--page-gutter)); overflow: visible; position: static; }
  .side :deep(.body) { padding-right: var(--page-gutter); }
  .main-col { border-left: 0; padding-left: var(--page-gutter); }
}
</style>
