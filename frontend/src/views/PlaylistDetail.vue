<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="wrap">
      <p v-if="status === 'loading'" class="meta page-state">Loading playlist…</p>

      <section v-else-if="status === 'missing'" class="page-state">
        <h1>This playlist isn't available</h1>
        <p class="meta">It may have been deleted, or it's private.</p>
        <router-link to="/dashboard" class="btn secondary">Go home</router-link>
      </section>

      <section v-else-if="status === 'error'" class="page-state">
        <h1>We couldn't load this playlist</h1>
        <p class="meta">Check that the server is running, then try again.</p>
        <button type="button" class="btn secondary" @click="loadPlaylist">Try again</button>
      </section>

      <template v-else>
        <header class="head">
          <div class="cover-wrap">
            <img v-if="playlist.playlistCoverUrl" class="cover" :src="resolveCoverURL(playlist.playlistCoverUrl)" alt="" />
            <div v-else class="cover cover-empty" aria-hidden="true">
              <svg class="note" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
            </div>
          </div>

          <div class="head-text">
            <p class="eyebrow">Playlist</p>
            <h1>{{ playlist.playlistName }}</h1>
            <p class="meta">
              <router-link :to="{ name: 'Profile', params: { username: owner.username } }" class="owner">
                <img v-if="owner.pfpUrl" class="owner-pfp" :src="resolveCoverURL(owner.pfpUrl)" alt="" />
                <span v-else class="owner-pfp owner-initial" aria-hidden="true">{{ owner.username.charAt(0).toUpperCase() }}</span>
                {{ owner.username }}
              </router-link>
              <span aria-hidden="true">·</span>
              <span>{{ songCount }}</span>
            </p>

            <div class="actions">
              <button type="button" class="btn primary" :disabled="!tracks.length" @click="playFrom(0)">
                <svg class="icon filled" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4.5v15l12.5-7.5z" /></svg>
                Play
              </button>
              <button type="button" class="btn secondary" :disabled="!tracks.length" @click="shuffle">
                <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="m18 14 4 4-4 4" /><path d="m18 2 4 4-4 4" /><path d="M2 18h1.97a4 4 0 0 0 3.3-1.7l5.45-8.6a4 4 0 0 1 3.3-1.7H22" />
                  <path d="M2 6h1.97a4 4 0 0 1 3.3 1.7l.75 1.2" /><path d="M22 18h-6.04a4 4 0 0 1-3.3-1.8l-.75-1.2" />
                </svg>
                Shuffle
              </button>

              <button
                v-if="!isOwner"
                type="button"
                class="btn secondary"
                :aria-pressed="playlist.isSaved ? 'true' : 'false'"
                :disabled="saving"
                @click="toggleSaved"
              >
                <svg v-if="playlist.isSaved" class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5" /></svg>
                <svg v-else class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14" /><path d="M5 12h14" /></svg>
                {{ playlist.isSaved ? 'Saved' : 'Save' }}
              </button>

              <div v-else ref="menuWrap" class="menu-wrap">
                <button
                  type="button"
                  class="btn secondary icon-btn"
                  aria-label="More options"
                  aria-haspopup="true"
                  :aria-expanded="menuOpen ? 'true' : 'false'"
                  aria-controls="playlist-menu"
                  @click="menuOpen = !menuOpen"
                >
                  <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                    <circle cx="5" cy="12" r="1" /><circle cx="12" cy="12" r="1" /><circle cx="19" cy="12" r="1" />
                  </svg>
                </button>
                <ul v-if="menuOpen" id="playlist-menu" class="menu">
                  <li>
                    <router-link class="menu-item" :to="{ name: 'Add', params: { username: owner.username, id } }">
                      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14" /><path d="M5 12h14" /></svg>
                      Add music
                    </router-link>
                  </li>
                  <li>
                    <router-link class="menu-item" :to="{ name: 'Edit', params: { username: owner.username, id } }">
                      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M21.17 6.81a1 1 0 0 0-3.99-3.99L3.84 16.17a2 2 0 0 0-.5.83l-1.32 4.35a.5.5 0 0 0 .62.62l4.35-1.32a2 2 0 0 0 .83-.5z" />
                      </svg>
                      Edit playlist
                    </router-link>
                  </li>
                  <li>
                    <router-link class="menu-item" :to="{ name: 'AddFriends', params: { username: owner.username, id } }">
                      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M19 8v6" /><path d="M22 11h-6" />
                      </svg>
                      Manage access
                    </router-link>
                  </li>
                  <li class="menu-sep">
                    <button type="button" class="menu-item" @click="openDeleteDialog">
                      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M3 6h18" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" /><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                      </svg>
                      Delete playlist
                    </button>
                  </li>
                </ul>
              </div>
            </div>

            <p v-if="actionError" class="error action-error" role="alert">{{ actionError }}</p>
          </div>
        </header>

        <section class="songs" aria-labelledby="songs-heading">
          <h2 id="songs-heading">Songs</h2>

          <template v-if="!tracks.length">
            <EmptyState
              title="No songs yet"
              :text="isOwner ? 'Upload an MP3 to start this playlist.' : `@${owner.username} hasn't added any songs yet.`"
            />
            <router-link v-if="isOwner" class="btn primary empty-action" :to="{ name: 'Add', params: { username: owner.username, id } }">
              Add music
            </router-link>
          </template>

          <ol v-else class="tracks">
            <li v-for="(track, index) in tracks" :key="track.musicPieceId" class="track" :class="{ playing: isPlayingTrack(track) }">
              <button
                type="button"
                class="track-main"
                :aria-label="`Play ${track.name}${artistName(track) ? ` by ${artistName(track)}` : ''}`"
                @click="playFrom(index)"
              >
                <span class="rank" aria-hidden="true">
                  <span class="rank-number">{{ index + 1 }}</span>
                  <svg class="rank-play" viewBox="0 0 24 24"><path d="M7 4.5v15l12.5-7.5z" /></svg>
                </span>
                <img v-if="track.coverUrl" class="art" :src="resolveCoverURL(track.coverUrl)" alt="" loading="lazy" />
                <span v-else class="art art-empty" aria-hidden="true">
                  <svg class="icon" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
                </span>
                <span class="track-text">
                  <span class="track-name">{{ track.name }}</span>
                  <span v-if="artistName(track)" class="track-artist">{{ artistName(track) }}</span>
                </span>
              </button>
              <router-link
                v-if="isOwner"
                class="edit"
                :aria-label="`Edit ${track.name}`"
                :to="{ name: 'EditMP3', params: { username: owner.username, playlist_id: id, index, uuid: track.musicPieceId } }"
              >
                <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M21.17 6.81a1 1 0 0 0-3.99-3.99L3.84 16.17a2 2 0 0 0-.5.83l-1.32 4.35a.5.5 0 0 0 .62.62l4.35-1.32a2 2 0 0 0 .83-.5z" />
                </svg>
              </router-link>
            </li>
          </ol>
        </section>
      </template>
    </main>

    <dialog
      ref="deleteDialog"
      class="confirm"
      aria-labelledby="delete-title"
      aria-describedby="delete-text"
      @cancel="deleting && $event.preventDefault()"
      @close="onDeleteDialogClose"
    >
      <div class="confirm-body">
        <h2 id="delete-title">Delete this playlist?</h2>
        <p id="delete-text" class="confirm-text">
          "{{ playlist?.playlistName }}" will be deleted for everyone. The songs stay in your uploads. You can't undo this.
        </p>
        <p v-if="deleteError" class="error" role="alert">{{ deleteError }}</p>
        <div class="confirm-actions">
          <button type="button" class="btn secondary" autofocus :disabled="deleting" @click="deleteDialog.close()">Keep playlist</button>
          <button type="button" class="btn primary" :disabled="deleting" @click="confirmDelete">
            {{ deleting ? 'Deleting…' : 'Delete playlist' }}
          </button>
        </div>
      </div>
    </dialog>

    <AppFooter />
  </div>
</template>

<script setup>
import { useNotificationStore } from '@/stores/notification.js'

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import EmptyState from '@/components/EmptyState.vue'
import { resolveCoverURL } from '@/utils/display.js'
import { PLAYLIST_DELETE_MOCKED, deletePlaylist, fetchPlaylist, setPlaylistSaved } from '@/utils/playlist.js'
import { useUserStore } from '@/stores/user.js'
import { useMusicStore } from '@/stores/music.js'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notification = useNotificationStore()
const musicStore = useMusicStore()

const status = ref('loading')
const playlist = ref(null)
const saving = ref(false)
const actionError = ref('')

const id = computed(() => route.params.id)
const owner = computed(() => playlist.value?.playlistOwner ?? { username: route.params.username })
const tracks = computed(() => playlist.value?.musicPieces ?? [])
const isOwner = computed(
  () => owner.value.username?.toLowerCase() === userStore.userData?.username?.toLowerCase(),
)
const songCount = computed(() => {
  const total = playlist.value?.totalPieces ?? tracks.value.length
  return total === 1 ? '1 song' : `${total} songs`
})

// A 401 means the session ended: log out and go to the login page
async function handleUnauthorized(err) {
  if (err.status !== 401) return false
  userStore.logout()
  await router.replace({ name: 'Login', query: { redirect: route.fullPath } })
  return true
}

async function loadPlaylist() {
  const { username, id: playlistId } = route.params
  status.value = 'loading'
  actionError.value = ''
  try {
    const result = await fetchPlaylist(username, playlistId)
    if (route.params.id !== playlistId) return
    playlist.value = result
    status.value = 'ready'
  } catch (err) {
    if (await handleUnauthorized(err)) return
    status.value = err.status === 404 ? 'missing' : 'error'
  }
}

watch(() => [route.params.username, route.params.id], loadPlaylist, { immediate: true })

function artistName(track) {
  return track.artistSummary?.artistName ?? ''
}

function isPlayingTrack(track) {
  return musicStore.getCurrentPlaylistUUID() === id.value && musicStore.getCurrentMusicPieceUUID() === track.musicPieceId
}

async function playFrom(index) {
  musicStore.reset()
  musicStore.forceShowPlayer()
  await musicStore.loadPlaylist(id.value, owner.value.username, index)
}

async function shuffle() {
  musicStore.reset()
  musicStore.forceShowPlayer()
  musicStore.toggleShuffle()
  await musicStore.loadPlaylist(id.value, owner.value.username, null)
}

async function toggleSaved() {
  const saved = !playlist.value.isSaved
  actionError.value = ''
  saving.value = true
  try {
    playlist.value.isSaved = await setPlaylistSaved(id.value, saved)
    notification.success(playlist.value.isSaved ? 'Playlist saved to your library.' : 'Playlist removed from your library.')
  } catch (err) {
    if (await handleUnauthorized(err)) return
    actionError.value = saved
      ? "We couldn't save this playlist. Check that the server is running, then try again."
      : "We couldn't remove this playlist from your library. Check that the server is running, then try again."
  } finally {
    saving.value = false
  }
}

const menuOpen = ref(false)
const menuWrap = ref(null)

function closeMenuOnOutside(event) {
  if (menuOpen.value && !menuWrap.value?.contains(event.target)) menuOpen.value = false
}

function closeMenuOnEscape(event) {
  if (event.key === 'Escape') menuOpen.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', closeMenuOnOutside)
  document.addEventListener('keydown', closeMenuOnEscape)
})

const deleteDialog = ref(null)
const deleting = ref(false)
const deleteError = ref('')

function openDeleteDialog() {
  menuOpen.value = false
  deleteError.value = ''
  deleteDialog.value.showModal()
  document.documentElement.classList.add('scroll-locked')
}

function onDeleteDialogClose() {
  document.documentElement.classList.remove('scroll-locked')
}

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', closeMenuOnOutside)
  document.removeEventListener('keydown', closeMenuOnEscape)
  onDeleteDialogClose()
})

async function confirmDelete() {
  if (deleting.value) return
  deleteError.value = ''
  deleting.value = true
  try {
    await deletePlaylist(id.value)
    deleteDialog.value.close()
    if (PLAYLIST_DELETE_MOCKED) {
      notification.neutral("Preview only: the playlist wasn't deleted.")
      return
    }
    if (musicStore.getCurrentPlaylistUUID() === id.value) musicStore.reset()
    notification.success('Playlist deleted.')
    await router.push({ name: 'Dashboard' })
  } catch (err) {
    if (await handleUnauthorized(err)) return
    deleteError.value = "We couldn't delete this playlist. Check that the server is running, then try again."
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.wrap { display: flex; flex-direction: column; padding: 0 var(--page-gutter) var(--space-8); width: 100%; }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-2); overflow-wrap: anywhere; }
h2 { font: 700 20px/28px var(--font-sans); margin: 0 0 var(--space-3); }
.meta { align-items: center; color: var(--ink-muted); display: flex; flex-wrap: wrap; font: 500 14px/20px var(--font-sans); gap: var(--space-2); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.action-error { margin-top: var(--space-3); }
.icon { fill: none; flex: none; height: 20px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 20px; }
.icon.filled { fill: currentColor; stroke: none; }

.page-state { align-items: flex-start; display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-8) 0; }
p.page-state { display: block; }

.head { align-items: end; border-bottom: 1px solid var(--line); display: grid; gap: var(--space-6); grid-template-columns: 232px minmax(0, 1fr); padding: var(--space-8) 0 var(--space-6); }
.cover { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-sm); display: block; object-fit: cover; width: 100%; }
.cover-empty { color: var(--ink-muted); display: grid; place-items: center; }
.note { fill: none; height: 64px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; width: 64px; }
.head-text { min-width: 0; }
.eyebrow { color: var(--ink-muted); font: 600 12px/16px var(--font-sans); margin: 0 0 var(--space-1); }
.owner { align-items: center; color: var(--ink); display: inline-flex; font-weight: 600; gap: var(--space-2); text-decoration: none; }
.owner:hover { text-decoration: underline; }
.owner-pfp { border-radius: var(--radius-pill); height: 24px; object-fit: cover; width: 24px; }
.owner-initial { background: var(--primary); color: var(--on-primary); display: grid; font: 700 12px/1 var(--font-sans); place-items: center; }

.actions { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-3); margin-top: var(--space-4); }
.actions .btn { align-items: center; display: inline-flex; gap: var(--space-2); }
.actions .icon-btn { padding-left: 12px; padding-right: 12px; }

.menu-wrap { position: relative; }
.menu { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); left: 0; list-style: none; margin: 0; min-width: 220px; padding: var(--space-1) 0; position: absolute; top: calc(100% + var(--space-1)); z-index: 20; }
.menu-item { align-items: center; background: none; border: 0; color: var(--ink); cursor: pointer; display: flex; font: 500 14px/20px var(--font-sans); gap: var(--space-3); padding: var(--space-2) var(--space-4); text-align: left; text-decoration: none; width: 100%; }
.menu-item:hover { background: var(--surface-alt); }
.menu-item .icon { color: var(--ink-muted); }
.menu-sep { border-top: 1px solid var(--line); margin-top: var(--space-1); padding-top: var(--space-1); }

.songs { padding-top: var(--space-6); }
.empty-action { align-self: flex-start; margin-top: var(--space-4); }
.tracks { border-top: 1px solid var(--line); list-style: none; margin: 0; padding: 0; }
.track { align-items: center; border-bottom: 1px solid var(--line); display: flex; transition: background var(--dur-fast) var(--ease); }
.track:hover { background: var(--surface-alt); }
.track.playing { background: var(--purple-soft); }
.track-main { align-items: center; background: none; border: 0; color: inherit; cursor: pointer; display: grid; flex: 1; font: inherit; gap: var(--space-3); grid-template-columns: 32px 40px minmax(0, 1fr); min-width: 0; padding: var(--space-3) var(--space-2); text-align: left; }
.rank { color: var(--ink-muted); display: grid; font: 500 14px/20px var(--font-sans); place-items: center; }
.rank > * { grid-area: 1 / 1; }
.rank-play { fill: var(--link); height: 20px; opacity: 0; width: 20px; }
.playing .rank-number { opacity: 0; }
.playing .rank-play { opacity: 1; }
.art { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-sm); color: var(--ink-muted); display: grid; height: 40px; object-fit: cover; place-items: center; width: 40px; }
.track-text { display: flex; flex-direction: column; min-width: 0; }
.track-name { font: 600 16px/24px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.playing .track-name { color: var(--link); }
.track-artist { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.edit { border-radius: var(--radius-sm); color: var(--ink-muted); display: grid; flex: none; height: 40px; margin-right: var(--space-2); place-items: center; width: 40px; }
.edit:hover { color: var(--ink); }

@media (hover: hover) {
  .edit { opacity: 0; }
  .track:hover .edit, .edit:focus-visible { opacity: 1; }
  .track:hover .rank-number, .track-main:focus-visible .rank-number { opacity: 0; }
  .track:hover .rank-play, .track-main:focus-visible .rank-play { opacity: 1; }
}

.confirm { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); color: var(--ink); font: 400 16px/24px var(--font-sans); overscroll-behavior: contain; padding: 0; width: min(440px, calc(100% - 32px)); }
.confirm::backdrop { background: rgb(0 0 0 / 0.45); }
.confirm-body { display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-6); }
.confirm h2 { margin: 0; }
.confirm-text { margin: 0; overflow-wrap: anywhere; }
.confirm-actions { display: flex; flex-wrap: wrap; gap: var(--space-3); margin-top: var(--space-2); }

@media (max-width: 720px) {
  .head { align-items: start; gap: var(--space-4); grid-template-columns: minmax(0, 1fr); padding-top: var(--space-6); }
  .cover-wrap { max-width: 200px; }
  .track-main { gap: var(--space-2); grid-template-columns: 24px 40px minmax(0, 1fr); padding-left: 0; }
  .edit { margin-right: 0; }
}
</style>
