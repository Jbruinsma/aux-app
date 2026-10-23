<template>
  <div ref="root" class="bell-wrap">
    <button
      type="button"
      class="bell"
      :class="{ active: open }"
      :aria-label="unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications'"
      title="Notifications"
      aria-haspopup="true"
      :aria-expanded="open ? 'true' : 'false'"
      aria-controls="notification-panel"
      @click="toggle"
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
           stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M10.27 21a2 2 0 0 0 3.46 0" />
        <path d="M3.26 15.33A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.67C19.41 13.96 18 12.5 18 8A6 6 0 0 0 6 8c0 4.5-1.41 5.96-2.74 7.33" />
      </svg>
      <span v-if="unreadCount" class="badge" aria-hidden="true">{{ unreadCount > 9 ? '9+' : unreadCount }}</span>
    </button>

    <div v-if="open" id="notification-panel" class="panel" role="region" aria-label="Notifications">
      <p class="panel-title">Notifications</p>
      <p v-if="!page && eventsStore.status === 'error'" class="panel-state error">We couldn't load your notifications. We'll keep trying.</p>
      <p v-else-if="!page" class="panel-state">Loading…</p>
      <p v-else-if="!items.length" class="panel-state">No notifications yet. Invitations and playlist activity show up here.</p>
      <template v-else>
        <ul class="list">
          <li v-for="item in items" :key="item.notificationId" class="item" :class="{ unread: !item.isRead }">
            <img v-if="item.playlist.playlistCoverUrl" class="cover" :src="resolveCoverURL(item.playlist.playlistCoverUrl)" alt="" />
            <span v-else class="cover" aria-hidden="true"></span>
            <div class="item-body">
              <p class="item-text">
                <strong>{{ item.actor.username }}</strong> {{ describe(item) }}
                <router-link :to="notificationPlaylistRoute(item)" class="playlist-link" @click="open = false">{{ item.playlist.playlistName }}</router-link>
              </p>
              <p class="item-time">{{ timeAgo(item.createdAt) }}</p>
              <div v-if="item.type === 'PLAYLIST_INVITE'" class="item-actions">
                <button type="button" class="btn primary small" :disabled="!!busy[item.notificationId]" @click="respond(item, true)">
                  {{ busy[item.notificationId] === 'accept' ? 'Accepting…' : 'Accept' }}
                </button>
                <button type="button" class="btn text small" :disabled="!!busy[item.notificationId]" @click="respond(item, false)">
                  {{ busy[item.notificationId] === 'decline' ? 'Declining…' : 'Decline' }}
                </button>
              </div>
            </div>
          </li>
        </ul>
        <p v-if="olderError" class="panel-state error">{{ olderError }}</p>
        <button v-if="nextCursor" type="button" class="btn text older" :disabled="loadingOlder" @click="loadOlder">
          {{ loadingOlder ? 'Loading…' : 'Show older' }}
        </button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { resolveCoverURL } from '@/utils/display.js'
import { acceptInvite, declineInvite } from '@/utils/playlist.js'
import { fetchNotifications, markAllNotificationsRead, notificationPlaylistRoute, timeAgo } from '@/utils/notifications.js'
import { useEventsStore } from '@/stores/events.js'
import { useNotificationStore } from '@/stores/notification.js'

const router = useRouter()
const eventsStore = useEventsStore()
const notification = useNotificationStore()

const root = ref(null)
const open = ref(false)
const busy = reactive({})
// Invites answered here, hidden until the server's next `notifications` event drops them
const answered = ref(new Set())
// Pages after the first, loaded with "Show older"
const older = ref([])
const olderCursor = ref(undefined)
const loadingOlder = ref(false)
const olderError = ref('')
// Opening the bell marks everything read; the badge clears straight away and stays clear until new data arrives
const readLocally = ref(false)

// The `notifications` event: the first page, replaced whole whenever it changes
const page = computed(() => eventsStore.notifications)

watch(page, () => {
  readLocally.value = false
  answered.value = new Set()
  // A changed snapshot can remove deleted playlists from any page, including cached older invites.
  older.value = []
  olderCursor.value = undefined
})

const items = computed(() => {
  const first = page.value?.notifications ?? []
  const seen = new Set(first.map((item) => item.notificationId))
  return [...first, ...older.value.filter((item) => !seen.has(item.notificationId))]
    .filter((item) => !answered.value.has(item.notificationId))
})
const nextCursor = computed(() => (olderCursor.value === undefined ? page.value?.nextCursor : olderCursor.value))
const unreadCount = computed(() => (readLocally.value ? 0 : page.value?.unreadCount ?? 0))

function describe(item) {
  if (item.type === 'PLAYLIST_INVITE') return `invited you to ${item.permission === 'EDITOR' ? 'edit' : 'listen to'}`
  if (item.type === 'INVITE_ACCEPTED') return 'accepted your invite to'
  const count = item.trackCount ?? 0
  return `added ${count === 1 ? '1 song' : `${count} songs`} to`
}

function toggle() {
  open.value = !open.value
  if (open.value && unreadCount.value) {
    readLocally.value = true
    markAllNotificationsRead().catch((err) => {
      if (!err.sessionEnded) readLocally.value = false
    })
  }
}

async function loadOlder() {
  if (!nextCursor.value || loadingOlder.value) return
  const snapshot = page.value
  loadingOlder.value = true
  olderError.value = ''
  try {
    const next = await fetchNotifications(nextCursor.value)
    if (page.value !== snapshot) return
    older.value = [...older.value, ...next.notifications]
    olderCursor.value = next.nextCursor
  } catch (err) {
    if (!err.sessionEnded) olderError.value = "We couldn't load older notifications. Try again."
  } finally {
    loadingOlder.value = false
  }
}

// The invite disappears from the server's list once answered; the next event confirms it
async function respond(item, accept) {
  const id = item.notificationId
  busy[id] = accept ? 'accept' : 'decline'
  try {
    if (accept) await acceptInvite(item.playlist.playlistId)
    else await declineInvite(item.playlist.playlistId)
    answered.value = new Set([...answered.value, id])
    if (accept) {
      notification.success(`You joined ${item.playlist.playlistName}.`)
      open.value = false
      await router.push(notificationPlaylistRoute(item))
    } else {
      notification.neutral(`Invitation to ${item.playlist.playlistName} declined.`)
    }
  } catch (err) {
    if (err.sessionEnded) return
    if (err.code === 'INVITE_NOT_FOUND') answered.value = new Set([...answered.value, id])
  } finally {
    delete busy[id]
  }
}

function onPointerDown(event) {
  if (open.value && !root.value?.contains(event.target)) open.value = false
}

function onKeyDown(event) {
  if (event.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onPointerDown)
  document.removeEventListener('keydown', onKeyDown)
})
</script>

<style scoped>
.bell-wrap { position: relative; }
.bell { background: none; border: 0; border-radius: var(--radius-pill); color: var(--ink-muted); cursor: pointer; display: grid; height: 36px; place-items: center; position: relative; width: 36px; }
.bell:hover { background: var(--surface-alt); color: var(--ink); }
.bell.active { color: var(--link); }
.bell:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.badge { background: var(--danger); border: 2px solid var(--surface); border-radius: var(--radius-pill); color: var(--white); font: 700 11px/14px var(--font-sans); min-width: 18px; padding: 0 4px; position: absolute; right: -2px; text-align: center; top: -2px; }

.panel { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); max-height: min(480px, 70vh); overflow-y: auto; position: absolute; right: 0; top: calc(100% + var(--space-2)); width: min(360px, calc(100vw - 32px)); z-index: 20; }
.panel-title { border-bottom: 1px solid var(--line); font: 700 16px/24px var(--font-sans); margin: 0; padding: var(--space-3) var(--space-4); }
.panel-state { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; padding: var(--space-4); }
.panel-state.error { color: var(--danger); }
.list { list-style: none; margin: 0; padding: 0; }
.item { align-items: flex-start; display: flex; gap: var(--space-3); padding: var(--space-3) var(--space-4); }
.item + .item { border-top: 1px solid var(--line); }
.item.unread { background: var(--purple-soft); }
.cover { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-sm); flex: none; object-fit: cover; width: 40px; }
.item-body { display: flex; flex: 1; flex-direction: column; gap: var(--space-1); min-width: 0; }
.item-text { color: var(--ink); font: 400 14px/20px var(--font-sans); margin: 0; overflow-wrap: anywhere; }
.item-text strong { font-weight: 600; }
.item-time { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.playlist-link { color: var(--link); font-weight: 600; text-decoration: none; }
.playlist-link:hover { text-decoration: underline; }
.item-actions { align-items: center; display: flex; gap: var(--space-2); margin-top: var(--space-1); }
.btn.small { font-size: 14px; padding: 6px 12px; }
.older { display: block; font-size: 14px; margin: 0 auto var(--space-3); }
</style>
