<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="wrap">
      <p v-if="status === 'loading'" class="meta page-state" role="status">Loading artist…</p>

      <section v-else-if="status === 'missing'" class="page-state">
        <h1>This artist isn't on Aux</h1>
        <p class="meta">The link may be wrong. Find artists from your home screen.</p>
        <router-link to="/dashboard" class="btn secondary">Go home</router-link>
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
            <button v-if="artist.canEdit" type="button" class="btn secondary action" @click="imagesDialog.open(artist)">
              Edit picture and banner
            </button>
          </ProfileCard>
        </aside>

        <div class="main-col">
          <section aria-labelledby="songs-heading">
            <h2 id="songs-heading">Songs by {{ artist.artistName }}</h2>
            <EmptyState v-if="songs.error" tone="error" :title="songs.error" text="Check that the server is running, then try again.">
              <button type="button" class="btn secondary" @click="loadSongs">Try again</button>
            </EmptyState>
            <EmptyState v-else-if="songs.loading" tone="loading" :title="`Loading songs by ${artist.artistName}…`" />
            <EmptyState
              v-else-if="!songs.items.length"
              title="No songs yet"
              :text="`Songs credited to ${artist.artistName} show up here as soon as someone uploads one.`"
            >
              <router-link v-if="loggedIn" :to="{ name: 'Upload' }" class="btn secondary">Upload music</router-link>
            </EmptyState>
            <ol v-else class="tracks">
              <li v-for="(piece, index) in songs.items" :key="piece.musicPieceId" class="track" :class="{ playing: isPlaying(piece) }">
                <button
                  type="button"
                  class="track-main"
                  :aria-label="`Play ${piece.name}`"
                  @click="musicStore.playPieces(songs.items, index)"
                >
                  <span class="rank" aria-hidden="true">
                    <span class="rank-number">{{ index + 1 }}</span>
                    <svg class="rank-play" viewBox="0 0 24 24"><path d="M7 4.5v15l12.5-7.5z" /></svg>
                  </span>
                  <img v-if="piece.coverUrl" class="art" :src="resolveCoverURL(piece.coverUrl)" alt="" loading="lazy" />
                  <span v-else class="art art-empty" aria-hidden="true">
                    <svg class="icon" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
                  </span>
                  <span class="track-name">{{ piece.name }}</span>
                </button>
              </li>
            </ol>
          </section>
        </div>
      </div>
    </main>

    <ArtistImagesDialog ref="imagesDialog" @updated="Object.assign(artist, $event)" />
    <AppFooter />
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import ArtistImagesDialog from '@/components/ArtistImagesDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import ProfileCard from '@/components/ProfileCard.vue'
import { resolveCoverURL } from '@/utils/display.js'
import { fetchArtist, fetchArtistPieces, setArtistFavorite } from '@/utils/artist.js'
import { useMusicStore } from '@/stores/music.js'
import { useUserStore } from '@/stores/user.js'
import { useNotificationStore } from '@/stores/notification.js'

// GET /api/artists/{artistId}: laid out like a user's profile. Anyone can view it; favoriting needs an account, and
// the artist's creator (canEdit) can change its picture and banner. Songs come from music search until there's an
// artist endpoint (fetchArtistPieces); they play on their own, like My uploads
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

async function loadArtist() {
  const requested = artistId.value
  status.value = 'loading'
  favoriteError.value = ''
  try {
    const data = await fetchArtist(requested)
    if (requested !== artistId.value) return
    artist.value = data
    status.value = 'ready'
    loadSongs()
  } catch (err) {
    if (err.sessionEnded || requested !== artistId.value) return
    status.value = err.status === 404 ? 'missing' : 'error'
  }
}

const songs = reactive({ items: [], loading: true, error: '' })

async function loadSongs() {
  const current = artist.value
  Object.assign(songs, { items: [], loading: true, error: '' })
  try {
    const items = await fetchArtistPieces(current)
    if (artist.value === current) songs.items = items
  } catch (err) {
    if (err.sessionEnded || artist.value !== current) return
    songs.error = "We couldn't load this artist's songs."
  } finally {
    if (artist.value === current) songs.loading = false
  }
}

function isPlaying(piece) {
  return !musicStore.currentPlaylistUUID && musicStore.currentMusicPiece.uuid === piece.musicPieceId
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

.tracks { border-top: 1px solid var(--line); list-style: none; margin: 0; padding: 0; }
.track { border-bottom: 1px solid var(--line); transition: background var(--dur-fast) var(--ease); }
.track:hover { background: var(--surface-alt); }
.track.playing { background: var(--purple-soft); }
.track-main { align-items: center; background: none; border: 0; color: inherit; cursor: pointer; display: grid; font: inherit; gap: var(--space-3); grid-template-columns: 32px 40px minmax(0, 1fr); padding: var(--space-3) var(--space-2); text-align: left; width: 100%; }
.rank { color: var(--ink-muted); display: grid; font: 500 14px/20px var(--font-sans); place-items: center; }
.rank > * { grid-area: 1 / 1; }
.rank-play { fill: var(--link); height: 20px; opacity: 0; width: 20px; }
.track:hover .rank-number, .track-main:focus-visible .rank-number, .playing .rank-number { opacity: 0; }
.track:hover .rank-play, .track-main:focus-visible .rank-play, .playing .rank-play { opacity: 1; }
.art { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-sm); color: var(--ink-muted); display: grid; height: 40px; object-fit: cover; place-items: center; width: 40px; }
.art .icon { height: 20px; width: 20px; }
.track-name { font: 600 16px/24px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.playing .track-name { color: var(--link); }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .side { border-bottom: 1px solid var(--line); height: auto; margin-right: calc(-1 * var(--page-gutter)); overflow: visible; position: static; }
  .side :deep(.body) { padding-right: var(--page-gutter); }
  .main-col { border-left: 0; padding-left: var(--page-gutter); }
}
</style>
