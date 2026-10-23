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

      <section v-else-if="status === 'invite'" class="head invite" aria-labelledby="invite-title">
        <div class="cover-wrap">
          <img v-if="invite?.playlistCoverUrl" class="cover" :src="resolveCoverURL(invite.playlistCoverUrl)" alt="" />
          <div v-else class="cover cover-empty" aria-hidden="true">
            <svg class="note" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
          </div>
        </div>

        <div class="head-text">
          <p class="eyebrow">Playlist invitation</p>
          <h1 id="invite-title">{{ invite ? `You're invited to ${invite.playlistName}` : "You're invited to this playlist" }}</h1>
          <p class="meta">{{ inviteText }}</p>
          <div class="actions">
            <button type="button" class="btn primary" :disabled="!!responding" @click="respondToInvite(true)">
              {{ responding === 'accept' ? 'Accepting…' : 'Accept invitation' }}
            </button>
            <button type="button" class="btn secondary" :disabled="!!responding" @click="respondToInvite(false)">
              {{ responding === 'decline' ? 'Declining…' : 'Decline' }}
            </button>
          </div>
          <p v-if="inviteError" class="error action-error" role="alert">{{ inviteError }}</p>
        </div>
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
            <p v-if="editors.length" class="meta editors">
              <span>Editors</span>
              <router-link
                v-for="editor in editors"
                :key="editor.userId"
                :to="{ name: 'Profile', params: { username: editor.username } }"
                class="owner"
              >
                <img v-if="editor.pfpUrl" class="owner-pfp" :src="resolveCoverURL(editor.pfpUrl)" alt="" />
                <span v-else class="owner-pfp owner-initial" aria-hidden="true">{{ editor.username.charAt(0).toUpperCase() }}</span>
                {{ editor.username }}
              </router-link>
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
                v-if="access === null"
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

              <div v-else-if="canEdit" ref="menuWrap" class="menu-wrap">
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
                  <li v-if="isOwner">
                    <router-link class="menu-item" :to="{ name: 'AddFriends', params: { username: owner.username, id } }">
                      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M19 8v6" /><path d="M22 11h-6" />
                      </svg>
                      Manage access
                    </router-link>
                  </li>
                  <li v-if="isOwner" class="menu-sep">
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
              :text="canEdit ? 'Upload an MP3 to start this playlist.' : `@${owner.username} hasn't added any songs yet.`"
            />
            <router-link v-if="canEdit" class="btn primary empty-action" :to="{ name: 'Add', params: { username: owner.username, id } }">
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

              <div v-if="canEdit || track.isAvailable !== false" class="menu-wrap track-menu-wrap">
                <button
                  type="button"
                  class="track-more"
                  :aria-label="`More options for ${track.name}`"
                  aria-haspopup="true"
                  :aria-expanded="trackMenuFor === track.musicPieceId ? 'true' : 'false'"
                  :aria-controls="`track-menu-${track.musicPieceId}`"
                  @click="toggleTrackMenu(track)"
                >
                  <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                    <circle cx="5" cy="12" r="1" /><circle cx="12" cy="12" r="1" /><circle cx="19" cy="12" r="1" />
                  </svg>
                </button>
                <ul v-if="trackMenuFor === track.musicPieceId" :id="`track-menu-${track.musicPieceId}`" class="menu track-menu">
                  <li v-if="track.isAvailable !== false">
                    <router-link class="menu-item" :to="{ name: 'Discover', params: { id: track.musicPieceId } }">
                      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                        <circle cx="12" cy="12" r="10" /><path d="m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z" />
                      </svg>
                      Discover similar songs
                    </router-link>
                  </li>
                  <li v-if="canEdit">
                    <button type="button" class="menu-item" :disabled="!!removingId" @click="removeTrack(track)">
                      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10" /><path d="M8 12h8" /></svg>
                      {{ removingId === track.musicPieceId ? 'Removing…' : 'Remove from playlist' }}
                    </button>
                  </li>
                </ul>
              </div>
            </li>
          </ol>

          <div v-if="playlist.nextCursor" ref="moreSentinel" class="more">
            <template v-if="moreError">
              <p class="error" role="alert">{{ moreError }}</p>
              <button type="button" class="btn secondary" @click="loadMore">Try again</button>
            </template>
            <p v-else class="meta">Loading more songs…</p>
          </div>
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
import {
  PLAYLIST_DELETE_MOCKED,
  acceptInvite,
  declineInvite,
  deletePlaylist,
  fetchPlaylist,
  fetchPlaylistPieces,
  removePieceFromPlaylist,
  setPlaylistSaved,
} from '@/utils/playlist.js'
import { useMusicStore } from '@/stores/music.js'
import { useEventsStore } from '@/stores/events.js'

const route = useRoute()
const router = useRouter()
const notification = useNotificationStore()
const musicStore = useMusicStore()
const eventsStore = useEventsStore()

const status = ref('loading')
const playlist = ref(null)
const saving = ref(false)
const actionError = ref('')

const id = computed(() => route.params.id)
const owner = computed(() => playlist.value?.playlistOwner ?? { username: route.params.username })
const tracks = computed(() => playlist.value?.musicPieces ?? [])
const editors = computed(() => playlist.value?.editors ?? [])
const access = computed(() => playlist.value?.access ?? null)
const isOwner = computed(() => access.value === 'OWNER')
const canEdit = computed(() => access.value === 'OWNER' || access.value === 'EDITOR')
const songCount = computed(() => {
  const total = playlist.value?.totalPieces ?? tracks.value.length
  return total === 1 ? '1 song' : `${total} songs`
})

async function loadPlaylist() {
  const { username, id: playlistId } = route.params
  status.value = 'loading'
  actionError.value = ''
  moreError.value = ''
  try {
    const result = await fetchPlaylist(username, playlistId, {
      ignoredErrorCodes: ['PLAYLIST_NOT_FOUND', 'PLAYLIST_INVITE_PENDING'],
    })
    if (route.params.id !== playlistId) return
    playlist.value = result
    status.value = 'ready'
  } catch (err) {
    if (err.sessionEnded || route.params.id !== playlistId) return
    if (err.code === 'PLAYLIST_INVITE_PENDING') showInvite()
    else status.value = err.status === 404 ? 'missing' : 'error'
  }
}

/* Invitation: a private playlist the caller was invited to but hasn't accepted. The invite list has its name, cover
   and who sent it; without it the page still offers Accept and Decline */

// The invite's name, cover, permission and who sent it come from the live `invites` event; until it arrives (or if it
// doesn't list this playlist) the page still offers Accept and Decline
const invite = computed(() => (status.value === 'invite'
  ? eventsStore.invites?.invites?.find((item) => item.playlistId === route.params.id) ?? null
  : null))
const inviteError = ref('')
const responding = ref('')

const inviteText = computed(() => {
  if (!invite.value) return 'Accept to open this private playlist.'
  const what = invite.value.permission === 'EDITOR' ? 'listen to and edit' : 'listen to'
  return `@${invite.value.invitedBy.username} invited you to ${what} this private playlist.`
})

function showInvite() {
  inviteError.value = ''
  status.value = 'invite'
}

async function respondToInvite(accept) {
  if (responding.value) return
  inviteError.value = ''
  responding.value = accept ? 'accept' : 'decline'
  try {
    if (accept) {
      await acceptInvite(id.value)
      notification.success('Invitation accepted.')
      await loadPlaylist()
    } else {
      await declineInvite(id.value)
      notification.neutral('Invitation declined.')
      await router.push({ name: 'Dashboard' })
    }
  } catch (err) {
    if (err.sessionEnded) return
    if (err.code === 'INVITE_NOT_FOUND') await loadPlaylist()
    else inviteError.value = `We couldn't ${accept ? 'accept' : 'decline'} the invitation. Check that the server is running, then try again.`
  } finally {
    responding.value = ''
  }
}

/* Songs past the first 50 load in pages of 50 as the end of the list scrolls into view */

const moreSentinel = ref(null)
const loadingMore = ref(false)
const moreError = ref('')
let moreObserver = null

async function loadMore() {
  const current = playlist.value
  if (!current?.nextCursor || loadingMore.value) return
  loadingMore.value = true
  moreError.value = ''
  try {
    const page = await fetchPlaylistPieces(current.playlistId, current.nextCursor)
    if (playlist.value !== current) return
    current.musicPieces.push(...page.musicPieces)
    current.nextCursor = page.nextCursor
  } catch (err) {
    if (err.sessionEnded || playlist.value !== current) return
    if (err.code === 'PLAYLIST_NOT_FOUND' || err.code === 'PLAYLIST_INVITE_PENDING') await loadPlaylist()
    else moreError.value = "We couldn't load more songs."
  } finally {
    loadingMore.value = false
  }
  // Observing again re-checks the sentinel, so a short page that leaves it on screen loads the next one too
  if (moreSentinel.value && !moreError.value) {
    moreObserver?.unobserve(moreSentinel.value)
    moreObserver?.observe(moreSentinel.value)
  }
}

watch(moreSentinel, (element, previous) => {
  if (previous) moreObserver?.unobserve(previous)
  if (element) moreObserver?.observe(element)
})

function artistName(track) {
  return track.artistSummary?.artistName ?? ''
}

function isPlayingTrack(track) {
  return musicStore.getCurrentPlaylistUUID() === id.value && musicStore.getCurrentMusicPieceUUID() === track.musicPieceId
}

function playFrom(index) {
  return musicStore.playPlaylist(id.value, { start: tracks.value[index]?.musicPieceId ?? null })
}

function shuffle() {
  return musicStore.playPlaylist(id.value, { shuffle: true })
}

async function toggleSaved() {
  const saved = !playlist.value.isSaved
  actionError.value = ''
  saving.value = true
  try {
    playlist.value.isSaved = await setPlaylistSaved(id.value, saved)
    notification.success(playlist.value.isSaved ? 'Playlist saved to your library.' : 'Playlist removed from your library.')
  } catch (err) {
    if (err.sessionEnded) return
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
  if (trackMenuFor.value && !event.target.closest?.('.track-menu-wrap')) trackMenuFor.value = null
}

function closeMenuOnEscape(event) {
  if (event.key !== 'Escape') return
  menuOpen.value = false
  trackMenuFor.value = null
}

/* Each song's ⋯ menu: Discover for anyone who can play the song, Remove for owners and editors. One open at a time,
   keyed by musicPieceId */

const trackMenuFor = ref(null)
const removingId = ref(null)

function toggleTrackMenu(track) {
  trackMenuFor.value = trackMenuFor.value === track.musicPieceId ? null : track.musicPieceId
}

// Later songs keep their positions on the backend, so the next-page cursor stays valid after a removal
async function removeTrack(track) {
  if (removingId.value) return
  const current = playlist.value
  removingId.value = track.musicPieceId
  try {
    await removePieceFromPlaylist(current.playlistId, track.musicPieceId)
    trackMenuFor.value = null
    if (playlist.value !== current) return
    const index = current.musicPieces.indexOf(track)
    if (index !== -1) current.musicPieces.splice(index, 1)
    current.totalPieces = Math.max(0, current.totalPieces - 1)
    notification.success(`Removed "${track.name}" from this playlist.`)
  } catch (err) {
    if (err.sessionEnded || playlist.value !== current) return
    trackMenuFor.value = null
    // The caller lost edit access (or the playlist is gone) since the page loaded
    if (err.code === 'PLAYLIST_NOT_FOUND') await loadPlaylist()
    else notification.error(`We couldn't remove "${track.name}". Check that the server is running, then try again.`)
  } finally {
    removingId.value = null
  }
}

onMounted(() => {
  moreObserver = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) loadMore()
  }, { rootMargin: '400px 0px' })
  if (moreSentinel.value) moreObserver.observe(moreSentinel.value)
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
  moreObserver?.disconnect()
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
    if (err.sessionEnded) return
    deleteError.value = "We couldn't delete this playlist. Check that the server is running, then try again."
  } finally {
    deleting.value = false
  }
}

watch(() => [route.params.username, route.params.id], loadPlaylist, { immediate: true })
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
.editors { margin-top: var(--space-2); }
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
.more { align-items: flex-start; display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-4) 0; }
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
.track-menu-wrap { flex: none; margin-right: var(--space-2); }
.track-more { align-items: center; background: none; border: 0; border-radius: var(--radius-sm); color: var(--ink-muted); cursor: pointer; display: grid; height: 36px; padding: 0; place-items: center; transition: color var(--dur-fast) var(--ease); width: 36px; }
.track-more:hover, .track-more[aria-expanded="true"] { color: var(--ink); }
.track-menu { left: auto; right: 0; }
.menu-item[disabled] { cursor: not-allowed; opacity: .4; }

@media (hover: hover) {
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
}
</style>
