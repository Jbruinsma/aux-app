<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="wrap">
      <router-link :to="playlistRoute" class="back">
        <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>
        {{ playlist ? playlist.playlistName : 'Back to playlist' }}
      </router-link>
      <h1>Manage access</h1>

      <p v-if="status === 'loading'" class="meta">Loading…</p>

      <section v-else-if="status === 'forbidden'" class="page-state">
        <p class="meta">Only the playlist's owner can manage who has access.</p>
        <router-link :to="playlistRoute" class="btn secondary">Back to playlist</router-link>
      </section>

      <section v-else-if="status === 'error'" class="page-state">
        <p class="meta">We couldn't load who has access. Check that the server is running, then try again.</p>
        <button type="button" class="btn secondary" @click="load">Try again</button>
      </section>

      <div v-else class="layout">
        <section class="panel" aria-labelledby="invite-heading">
          <h2 id="invite-heading">Invite people</h2>
          <p class="meta">
            {{ playlist.isPublic
              ? 'Anyone can already listen to this public playlist. Invite people you want to help edit it.'
              : 'This playlist is private, so only you and the people you invite can open it.' }}
          </p>

          <div class="field">
            <label class="label" for="invite-permission">Access for new invites</label>
            <span class="select-wrap field-select">
              <select id="invite-permission" v-model="invitePermission" class="input select" aria-describedby="permission-hint">
                <option value="LISTENER">Listener</option>
                <option value="EDITOR">Editor</option>
              </select>
            </span>
            <p id="permission-hint" class="hint">Listeners can play the playlist. Editors can also add songs and change its name and cover.</p>
          </div>

          <PeoplePicker :exclude="excluded" @pick="invite" />
          <p v-if="inviting" class="meta" aria-live="polite">Sending the invite…</p>
          <p v-if="inviteError" class="error" role="alert">{{ inviteError }}</p>
        </section>

        <section aria-labelledby="people-heading">
          <h2 id="people-heading">People with access</h2>
          <ul class="people">
            <li class="person">
              <Avatar :user="playlist.playlistOwner" />
              <span class="person-text">
                <span class="person-name">{{ playlist.playlistOwner.username }}</span>
                <span class="person-meta">You</span>
              </span>
              <span class="role">Owner</span>
            </li>
            <li v-for="member in members" :key="member.user.userId" class="person">
              <Avatar :user="member.user" />
              <span class="person-text">
                <span class="person-name">{{ member.user.username }}</span>
                <span class="person-meta">{{ member.status === 'PENDING' ? 'Invite pending' : 'Accepted' }}</span>
              </span>
              <label class="visually-hidden" :for="`permission-${member.user.userId}`">Access for {{ member.user.username }}</label>
              <span class="select-wrap permission-wrap">
                <select
                  :id="`permission-${member.user.userId}`"
                  class="input select permission"
                  :value="member.permission"
                  :disabled="busy[member.user.userId]"
                  @change="changePermission(member, $event.target)"
                >
                  <option value="LISTENER">Listener</option>
                  <option value="EDITOR">Editor</option>
                </select>
              </span>
              <button
                type="button"
                class="btn text remove"
                :disabled="busy[member.user.userId]"
                @click="remove(member)"
              >{{ member.status === 'PENDING' ? 'Cancel invite' : 'Remove' }}</button>
            </li>
          </ul>
          <p v-if="!members.length" class="meta">No one else has access yet.</p>
          <p v-if="memberError" class="error" role="alert">{{ memberError }}</p>
        </section>
      </div>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, defineComponent, h, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import PeoplePicker from '@/components/PeoplePicker.vue'
import { resolveCoverURL } from '@/utils/display.js'
import { fetchMembers, fetchPlaylist, inviteMember, removeMember, updateMember } from '@/utils/playlist.js'
import { useNotificationStore } from '@/stores/notification.js'

const Avatar = defineComponent({
  props: { user: { type: Object, required: true } },
  setup: (props) => () => props.user.pfpUrl
    ? h('img', { class: 'avatar', src: resolveCoverURL(props.user.pfpUrl), alt: '' })
    : h('span', { class: 'avatar avatar-initial', 'aria-hidden': 'true' }, props.user.username.charAt(0).toUpperCase()),
})

const route = useRoute()
const notification = useNotificationStore()
const { username, id } = route.params
const playlistRoute = { name: 'Playlist', params: { username, id } }

const status = ref('loading')
const playlist = ref(null)
const members = ref([])
const invitePermission = ref('LISTENER')
const inviting = ref(false)
const inviteError = ref('')
const memberError = ref('')
const busy = reactive({})

const excluded = computed(() => [playlist.value?.playlistOwner.userId, ...members.value.map((m) => m.user.userId)])

async function load() {
  status.value = 'loading'
  try {
    const result = await fetchPlaylist(username, id, { ignoredErrorCodes: ['PLAYLIST_NOT_FOUND', 'PLAYLIST_INVITE_PENDING'] })
    if (result.access !== 'OWNER') {
      status.value = 'forbidden'
      return
    }
    playlist.value = result
    members.value = await fetchMembers(result.playlistId)
    status.value = 'ready'
  } catch (err) {
    if (err.sessionEnded) return
    status.value = err.code === 'PLAYLIST_NOT_FOUND' || err.code === 'PLAYLIST_INVITE_PENDING' ? 'forbidden' : 'error'
  }
}

load()

async function invite(user) {
  if (inviting.value) return
  inviteError.value = ''
  inviting.value = true
  try {
    const member = await inviteMember(playlist.value.playlistId, user.userId, invitePermission.value, {
      ignoredErrorCodes: ['USER_ALREADY_INVITED', 'USER_NOT_FOUND', 'INVALID_FIELD'],
    })
    members.value.push(member)
    notification.success(`Invite sent to ${user.username}.`)
  } catch (err) {
    if (err.sessionEnded) return
    if (err.code === 'USER_ALREADY_INVITED') {
      inviteError.value = `${user.username} already has access or an invite.`
      members.value = await fetchMembers(playlist.value.playlistId).catch(() => members.value)
    } else if (err.code === 'USER_NOT_FOUND') inviteError.value = `We couldn't find ${user.username}. They may have changed their username.`
    else if (err.code === 'INVALID_FIELD') inviteError.value = "You can't invite yourself."
    else inviteError.value = "We couldn't send the invite. Check that the server is running, then try again."
  } finally {
    inviting.value = false
  }
}

async function changePermission(member, select) {
  const permission = select.value
  if (permission === member.permission) return
  const userId = member.user.userId
  memberError.value = ''
  busy[userId] = true
  try {
    const updated = await updateMember(playlist.value.playlistId, userId, permission)
    Object.assign(member, updated)
    notification.success(`${member.user.username} is now ${permission === 'EDITOR' ? 'an editor' : 'a listener'}.`)
  } catch (err) {
    if (err.sessionEnded) return
    memberError.value = `We couldn't change ${member.user.username}'s access. Check that the server is running, then try again.`
    select.value = member.permission
  } finally {
    busy[userId] = false
  }
}

async function remove(member) {
  const userId = member.user.userId
  memberError.value = ''
  busy[userId] = true
  try {
    await removeMember(playlist.value.playlistId, userId)
    members.value = members.value.filter((m) => m.user.userId !== userId)
    notification.success(member.status === 'PENDING'
      ? `Invite to ${member.user.username} canceled.`
      : `${member.user.username} no longer has access.`)
  } catch (err) {
    if (err.sessionEnded) return
    memberError.value = `We couldn't remove ${member.user.username}. Check that the server is running, then try again.`
  } finally {
    busy[userId] = false
  }
}
</script>

<style scoped>
.wrap { padding: var(--space-8) var(--page-gutter); width: 100%; }
.back { align-items: center; color: var(--link); display: inline-flex; font: 600 14px/20px var(--font-sans); gap: var(--space-1); margin-bottom: var(--space-2); text-decoration: none; }
.back:hover { text-decoration: underline; }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-6); }
h2 { font: 700 20px/28px var(--font-sans); margin: 0 0 var(--space-3); }
.meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: var(--space-3) 0 0; }
.hint { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.label { font: 600 14px/20px var(--font-sans); }
.icon { fill: none; height: 16px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 16px; }
.page-state { align-items: flex-start; display: flex; flex-direction: column; gap: var(--space-3); }

.layout { align-items: start; display: grid; gap: var(--space-8); grid-template-columns: minmax(0, 400px) minmax(0, 1fr); }
.panel { background: var(--surface-alt); border-radius: var(--radius-md); display: flex; flex-direction: column; gap: var(--space-4); padding: var(--space-4); }
.panel h2 { margin: 0; }
.field { display: flex; flex-direction: column; gap: var(--space-2); }
.input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px 12px; }

.people { border-top: 1px solid var(--line); list-style: none; margin: 0; padding: 0; }
.person { align-items: center; border-bottom: 1px solid var(--line); display: flex; gap: var(--space-3); padding: var(--space-3) 0; }
.person :deep(.avatar) { border-radius: var(--radius-pill); flex: none; height: 40px; object-fit: cover; width: 40px; }
.person :deep(.avatar-initial) { background: var(--primary); color: var(--on-primary); display: grid; font: 700 16px/1 var(--font-sans); place-items: center; }
.person-text { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.person-name { font: 600 16px/24px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.person-meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); }
.role { color: var(--ink-muted); font: 600 14px/20px var(--font-sans); padding: 0 var(--space-3); }
.field-select { display: block; }
.permission-wrap { flex: none; }
.permission { font-size: 14px; padding-bottom: 6px; padding-left: 10px; padding-top: 6px; }
.remove { flex: none; font-size: 14px; }
.visually-hidden { clip-path: inset(50%); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

@media (max-width: 900px) {
  .layout { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 520px) {
  .person { flex-wrap: wrap; }
  .person-text { flex-basis: calc(100% - 52px); }
  .permission-wrap { margin-left: 52px; }
}
</style>
