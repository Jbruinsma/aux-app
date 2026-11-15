<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="wrap">
      <div v-if="status === 'loading'" class="head skeleton" aria-busy="true">
        <div class="cover"></div>
        <div>
          <span class="bar" style="--w: 60px"></span>
          <span class="bar title" style="--w: 55%"></span>
          <span class="bar" style="--w: 30%"></span>
          <span class="bar" style="--w: 40%"></span>
        </div>
        <p class="sr-only" role="status">Loading song…</p>
      </div>

      <section v-else-if="status === 'missing'" class="page-state">
        <h1>This song isn't on Aux</h1>
        <p class="meta">It may have been deleted, or it's private.</p>
        <router-link :to="{ name: 'Dashboard' }" class="btn secondary">Go home</router-link>
      </section>

      <section v-else-if="status === 'error'" class="page-state">
        <h1>We couldn't load this song</h1>
        <p class="meta">Check that the server is running, then try again.</p>
        <button type="button" class="btn secondary" @click="loadPiece">Try again</button>
      </section>

      <template v-else>
        <header class="head">
          <img v-if="piece.coverUrl" class="cover" :src="resolveCoverURL(piece.coverUrl)" alt="" />
          <div v-else class="cover cover-empty" aria-hidden="true">
            <svg class="note" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
          </div>

          <div class="head-text">
            <p class="eyebrow">Song<span v-if="isMine && !piece.isPublic" class="badge">Private</span></p>
            <h1>{{ piece.name }}</h1>
            <p v-if="piece.artistSummary?.artistName || piece.album?.albumName" class="meta">
              <router-link
                v-if="piece.artistSummary?.artistId"
                class="artist-link"
                :to="{ name: 'Artist', params: { artistId: piece.artistSummary.artistId } }"
              >{{ piece.artistSummary.artistName }}</router-link>
              <template v-if="piece.album?.albumName">
                <span v-if="piece.artistSummary?.artistId" aria-hidden="true">·</span>
                <span><span class="sr-only">From the album </span>{{ piece.album.albumName }}</span>
              </template>
            </p>
            <p v-if="piece.uploader" class="meta uploader-line">
              <router-link class="uploader" :to="{ name: 'Profile', params: { username: piece.uploader.username } }">
                <img v-if="piece.uploader.pfpUrl" class="uploader-pfp" :src="resolveCoverURL(piece.uploader.pfpUrl)" alt="" />
                <span v-else class="uploader-pfp uploader-initial" aria-hidden="true">{{ piece.uploader.username.charAt(0).toUpperCase() }}</span>
                <span><span class="sr-only">Uploaded by </span>{{ piece.uploader.username }}</span>
              </router-link>
            </p>
            <TagList v-if="piece.tags?.length" class="piece-tags" :tags="piece.tags" label="Song tags" />

            <div class="actions">
              <button type="button" class="btn primary" @click="togglePlay">
                <svg v-if="playingThis" class="icon filled" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 4h4v16H6zM14 4h4v16h-4z" /></svg>
                <svg v-else class="icon filled" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4.5v15l12.5-7.5z" /></svg>
                {{ playingThis ? 'Pause' : 'Play' }}
              </button>
              <router-link v-if="!loggedIn" to="/login" class="btn secondary">Log in to favorite</router-link>
              <button
                v-else
                type="button"
                class="btn secondary"
                :aria-pressed="piece.isFavorite ? 'true' : 'false'"
                :disabled="favoriting"
                @click="toggleFavorite"
              >
                <svg class="icon" :class="{ hearted: piece.isFavorite }" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
                </svg>
                {{ piece.isFavorite ? 'Favorited' : 'Favorite' }}
              </button>
              <button v-if="isMine" type="button" class="btn secondary" @click="editDialog.open(piece)">
                <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M21.17 6.81a1 1 0 0 0-3.99-3.99L3.84 16.17a2 2 0 0 0-.5.83l-1.32 4.35a.5.5 0 0 0 .62.62l4.35-1.32a2 2 0 0 0 .83-.5z" /></svg>
                Edit
              </button>
            </div>
            <p v-if="favoriteError" class="error" role="alert">{{ favoriteError }}</p>
          </div>
        </header>

        <section class="details" aria-labelledby="details-heading">
          <h2 id="details-heading">Details</h2>
          <dl class="facts">
            <div v-if="piece.durationSeconds != null" class="fact">
              <dt>Length</dt>
              <dd>{{ formatLength(piece.durationSeconds) }}</dd>
            </div>
            <div class="fact">
              <dt>Plays</dt>
              <dd>{{ piece.playCount.toLocaleString('en-US') }}</dd>
            </div>
            <div class="fact">
              <dt>Uploaded</dt>
              <dd><time :datetime="piece.createdAt">{{ formatDate(piece.createdAt) }}</time></dd>
            </div>
          </dl>
        </section>
      </template>
    </main>

    <EditPieceDialog ref="editDialog" @saved="Object.assign(piece, $event)" />
    <AppFooter />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import EditPieceDialog from '@/components/EditPieceDialog.vue'
import TagList from '@/components/TagList.vue'
import { resolveCoverURL } from '@/utils/display.js'
import { fetchMusicPiece, setPieceFavorite } from '@/utils/music.js'
import { useMusicStore } from '@/stores/music.js'
import { useUserStore } from '@/stores/user.js'
import { useNotificationStore } from '@/stores/notification.js'

// GET /api/music-pieces/{musicPieceId}: one song's page, laid out like the playlist page's head (mockups/piece-page.html).
// Anyone can view a public song; your own private ones show to you with a Private badge. Play plays it on its own.
// Favoriting needs an account, and the uploader can edit it here as on My uploads
const route = useRoute()
const userStore = useUserStore()
const musicStore = useMusicStore()
const notification = useNotificationStore()

const musicPieceId = computed(() => route.params.musicPieceId)
const loggedIn = computed(() => userStore.loggedIn)
const piece = ref(null)
const status = ref('loading')
const favoriting = ref(false)
const favoriteError = ref('')
const editDialog = ref(null)

const isMine = computed(() => loggedIn.value && !!piece.value?.uploader && piece.value.uploader.userId === userStore.userData?.userId)
const isCurrent = computed(() => !!piece.value && musicStore.currentMusicPiece?.uuid === piece.value.musicPieceId)
const playingThis = computed(() => isCurrent.value && musicStore.isPlaying)

async function loadPiece() {
  const requested = musicPieceId.value
  status.value = 'loading'
  favoriteError.value = ''
  try {
    const data = await fetchMusicPiece(requested)
    if (requested !== musicPieceId.value) return
    piece.value = data
    status.value = 'ready'
  } catch (err) {
    if (err.sessionEnded || requested !== musicPieceId.value) return
    status.value = err.status === 404 ? 'missing' : 'error'
  }
}

// Pause and resume when it's already the song in the player; otherwise start it on its own
function togglePlay() {
  if (isCurrent.value) musicStore.isPlaying = !musicStore.isPlaying
  else musicStore.playPieces([piece.value])
}

async function toggleFavorite() {
  const favorite = !piece.value.isFavorite
  favoriteError.value = ''
  favoriting.value = true
  try {
    await setPieceFavorite(piece.value.musicPieceId, favorite)
    piece.value.isFavorite = favorite
    notification.success(favorite ? `${piece.value.name} is in your favorites.` : `Removed ${piece.value.name} from your favorites.`)
  } catch (err) {
    if (err.sessionEnded) return
    if (err.status === 404) return loadPiece()
    favoriteError.value = favorite
      ? "We couldn't favorite this song. Check that the server is running, then try again."
      : "We couldn't remove this song from your favorites. Check that the server is running, then try again."
  } finally {
    favoriting.value = false
  }
}

// 7:47, or 1:02:05 past an hour
function formatLength(seconds) {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = String(seconds % 60).padStart(2, '0')
  return h ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`
}

// Oct 3, 2026
function formatDate(iso) {
  return new Date(iso).toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' })
}

watch(musicPieceId, loadPiece, { immediate: true })
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; padding: 0 var(--page-gutter) var(--space-8); width: 100%; }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-2); overflow-wrap: anywhere; }
h2 { font: 700 20px/28px var(--font-sans); margin: 0 0 var(--space-3); }
.meta { align-items: center; color: var(--ink-muted); display: flex; flex-wrap: wrap; font: 500 14px/20px var(--font-sans); gap: var(--space-2); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: var(--space-3) 0 0; }
.icon { fill: none; flex: none; height: 18px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 18px; }
.icon.filled { fill: currentColor; stroke: none; }
.icon.hearted { fill: var(--link); stroke: var(--link); }
.sr-only { clip: rect(0 0 0 0); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

.page-state { align-items: flex-start; display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-8) 0; }

.head { align-items: end; border-bottom: 1px solid var(--line); display: grid; gap: var(--space-6); grid-template-columns: 232px minmax(0, 1fr); padding: var(--space-8) 0 var(--space-6); }
.cover { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-sm); display: block; object-fit: cover; width: 100%; }
.cover-empty { color: var(--ink-muted); display: grid; place-items: center; }
.note { fill: none; height: 64px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; width: 64px; }
.head-text { min-width: 0; }
.eyebrow { align-items: center; color: var(--ink-muted); display: flex; font: 600 12px/16px var(--font-sans); gap: var(--space-2); margin: 0 0 var(--space-1); }
.badge { border: 1px solid var(--line-strong); border-radius: var(--radius-pill); font: 600 11px/14px var(--font-sans); padding: 1px var(--space-2); }
.artist-link { color: var(--ink); font-weight: 600; text-decoration: none; }
.artist-link:hover { color: var(--link); text-decoration: underline; }
.uploader-line { margin-top: var(--space-2); }
.uploader { align-items: center; color: inherit; display: inline-flex; gap: var(--space-2); text-decoration: none; }
.uploader:hover { color: var(--link); text-decoration: underline; }
.uploader-pfp { border-radius: var(--radius-pill); flex: none; height: 24px; object-fit: cover; width: 24px; }
.uploader-initial { background: var(--primary); color: var(--on-primary); display: grid; font: 700 12px/1 var(--font-sans); place-items: center; }
.piece-tags { margin-top: var(--space-3); }

.actions { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-3); margin-top: var(--space-4); }
.actions .btn { align-items: center; display: inline-flex; gap: var(--space-2); }

.details { padding-top: var(--space-6); }
.facts { border-top: 1px solid var(--line); margin: 0; max-width: 560px; }
.fact { border-bottom: 1px solid var(--line); display: grid; gap: var(--space-4); grid-template-columns: 140px minmax(0, 1fr); padding: var(--space-3); }
.fact dt { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); }
.fact dd { font: 600 14px/20px var(--font-sans); margin: 0; }

.skeleton .cover, .skeleton .bar { animation: pulse 1.2s var(--ease) infinite alternate; background: var(--surface-alt); }
.bar { border-radius: var(--radius-sm); display: block; height: 14px; width: var(--w, 40%); }
.bar + .bar { margin-top: var(--space-3); }
.bar.title { height: 28px; }
@keyframes pulse { from { opacity: 0.5; } to { opacity: 1; } }
@media (prefers-reduced-motion: reduce) { .skeleton .cover, .skeleton .bar { animation: none; } }

@media (max-width: 720px) {
  .head { align-items: start; grid-template-columns: 1fr; }
  .head .cover { max-width: 232px; }
  .fact { grid-template-columns: 110px minmax(0, 1fr); }
}
</style>
