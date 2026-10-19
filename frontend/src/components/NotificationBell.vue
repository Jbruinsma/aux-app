<template>
  <div ref="root" class="bell-wrap">
    <button
      type="button"
      class="bell"
      :class="{ active: open }"
      :aria-label="unseenCount ? `Notifications, ${unseenCount} new` : 'Notifications'"
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
      <span v-if="unseenCount" class="badge" aria-hidden="true">{{ unseenCount > 9 ? '9+' : unseenCount }}</span>
    </button>

    <div v-if="open" id="notification-panel" class="panel" role="region" aria-label="Notifications">
      <p class="panel-title">Notifications</p>
      <p v-if="loadError" class="panel-state error">{{ loadError }}</p>
      <p v-else-if="!loaded" class="panel-state">Loading…</p>
      <p v-else-if="!invites.length" class="panel-state">You have no new notifications.</p>
      <ul v-else class="list">
        <li v-for="invite in invites" :key="invite.playlistId" class="item">
          <img v-if="invite.playlistCoverUrl" class="cover" :src="resolveCoverURL(invite.playlistCoverUrl)" alt="" />
          <span v-else class="cover cover-empty" aria-hidden="true"></span>
          <div class="item-body">
            <p class="item-text">
              <strong>{{ invite.invitedBy.username }}</strong> invited you to {{ invite.permission === 'EDITOR' ? 'edit' : 'listen to' }}
              <router-link :to="playlistRoute(invite)" class="playlist-link" @click="open = false">{{ invite.playlistName }}</router-link>
            </p>
            <div class="item-actions">
              <button type="button" class="btn primary small" :disabled="!!busy[invite.playlistId]" @click="respond(invite, true)">
                {{ busy[invite.playlistId] === 'accept' ? 'Accepting…' : 'Accept' }}
              </button>
              <button type="button" class="btn text small" :disabled="!!busy[invite.playlistId]" @click="respond(invite, false)">
                {{ busy[invite.playlistId] === 'decline' ? 'Declining…' : 'Decline' }}
              </button>
            </div>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { resolveCoverURL } from '@/utils/display.js'
import { acceptInvite, declineInvite, fetchPendingInvites } from '@/utils/playlist.js'
import { useNotificationStore } from '@/stores/notification.js'
import { useUserStore } from '@/stores/user.js'

const REFRESH_MS = 60_000

const router = useRouter()
const userStore = useUserStore()
const notification = useNotificationStore()

const root = ref(null)
const open = ref(false)
const invites = ref([])
const loaded = ref(false)
const loadError = ref('')
const busy = reactive({})
const seen = ref(readSeen())
let timer = null

// Which invites this account has already seen lives in this browser only
function seenKey() {
  return `aux-seen-invites-${userStore.userData?.userId ?? userStore.userData?.username}`
}

function readSeen() {
  try {
    return new Set(JSON.parse(localStorage.getItem(seenKey()) ?? '[]'))
  } catch {
    return new Set()
  }
}

function markAllSeen() {
  seen.value = new Set(invites.value.map((invite) => invite.playlistId))
  try {
    localStorage.setItem(seenKey(), JSON.stringify([...seen.value]))
  } catch {
    // Storage can be blocked; the badge still clears until the page reloads
  }
}

const unseenCount = computed(() => invites.value.filter((invite) => !seen.value.has(invite.playlistId)).length)

async function load() {
  try {
    const { invites: pending } = await fetchPendingInvites({ notifyOnError: false })
    invites.value = pending
    loadError.value = ''
    if (open.value) markAllSeen()
  } catch (err) {
    if (err.sessionEnded) return
    loadError.value = "We couldn't load your notifications."
  } finally {
    loaded.value = true
  }
}

function toggle() {
  open.value = !open.value
  if (open.value) {
    markAllSeen()
    load()
  }
}

function playlistRoute(invite) {
  return { name: 'Playlist', params: { username: invite.invitedBy.username, id: invite.playlistId } }
}

async function respond(invite, accept) {
  busy[invite.playlistId] = accept ? 'accept' : 'decline'
  try {
    if (accept) await acceptInvite(invite.playlistId)
    else await declineInvite(invite.playlistId)
    invites.value = invites.value.filter((item) => item.playlistId !== invite.playlistId)
    if (accept) {
      notification.success(`You joined ${invite.playlistName}.`)
      open.value = false
      await router.push(playlistRoute(invite))
    } else {
      notification.neutral(`Invitation to ${invite.playlistName} declined.`)
    }
  } catch (err) {
    if (err.sessionEnded) return
    if (err.code === 'INVITE_NOT_FOUND') await load()
  } finally {
    delete busy[invite.playlistId]
  }
}

function onPointerDown(event) {
  if (open.value && !root.value?.contains(event.target)) open.value = false
}

function onKeyDown(event) {
  if (event.key === 'Escape') open.value = false
}

function onFocus() {
  load()
}

onMounted(() => {
  load()
  timer = setInterval(load, REFRESH_MS)
  window.addEventListener('focus', onFocus)
  document.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  clearInterval(timer)
  window.removeEventListener('focus', onFocus)
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
.cover { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-sm); flex: none; object-fit: cover; width: 40px; }
.item-body { display: flex; flex: 1; flex-direction: column; gap: var(--space-2); min-width: 0; }
.item-text { color: var(--ink); font: 400 14px/20px var(--font-sans); margin: 0; overflow-wrap: anywhere; }
.item-text strong { font-weight: 600; }
.playlist-link { color: var(--link); font-weight: 600; text-decoration: none; }
.playlist-link:hover { text-decoration: underline; }
.item-actions { align-items: center; display: flex; gap: var(--space-2); }
.btn.small { font-size: 14px; padding: 6px 12px; }
</style>
