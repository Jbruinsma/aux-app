<template>
  <div id="app">
    <GlobalNotifications />

    <router-view></router-view>
    <BottomPlayer v-if="!hideBottomPlayerOnThisRoute" />
    <ExternalLinkDialog />
  </div>
</template>

<script setup>
import { computed, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'

import BottomPlayer from '@/components/BottomPlayer.vue'
import ExternalLinkDialog from '@/components/ExternalLinkDialog.vue'
import GlobalNotifications from '@/components/GlobalNotifications.vue'

import { useUserStore } from '@/stores/user.js'
import { useMusicStore } from '@/stores/music.js'
import { useEventsStore } from '@/stores/events.js'

import { fetchAPI } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'
import { keepSessionAlive } from '@/utils/session.js'

const bannedRoutes = ['/', '/login', '/register', '/onboarding']

const route = useRoute()
const userStore = useUserStore()
const musicStore = useMusicStore()
const eventsStore = useEventsStore()

// Refreshes the token before it expires, so leaving music playing never logs anyone out
onUnmounted(keepSessionAlive())

// One live stream per session: opens after login (once onboarding is done), restarts for a different account, closes
// on logout. A refreshed token doesn't restart it: the server ends the stream when its token expires and it reconnects
// with the current one
watch(
  () => [userStore.loggedIn && userStore.onboarded, userStore.userData?.userId],
  ([active, userId], [wasActive, previousUserId] = []) => {
    if (!active) eventsStore.disconnect()
    else if (wasActive && previousUserId && userId !== previousUserId) eventsStore.restart()
    else eventsStore.connect()
  },
  { immediate: true },
)

const hideBottomPlayerOnThisRoute = computed(() => {
  if (bannedRoutes.includes(route.path)) {
    return true
  }

  const currentUser = userStore.userData?.username

  return !(currentUser || musicStore.forceShowPlayerActive || musicStore.currentSong)
})

// Restore once per login, not on page changes or token refreshes. Ignore stale replies.
watch(
  () => [userStore.loggedIn, userStore.userData?.userId, userStore.onboarded],
  async ([loggedIn, , onboarded], previous, onCleanup) => {
    if (!loggedIn || !onboarded) return
    let cancelled = false
    onCleanup(() => { cancelled = true })
    try {
      const data = await fetchAPI(`${API_BASE_URL}/api/users/me/last-playback`, { notifyOnError: false })
      if (cancelled || !data) return
      // Something is already playing: keep it, but still take this account's shuffle and repeat
      if (musicStore.isPlaying) musicStore.applyPlayPreferences(data.playPreferences)
      else musicStore.updateBottomPlayerAfterLogin(data)
    } catch (err) {
      console.log('Error fetching last playback:', err)
    }
  },
  { immediate: true },
)

</script>

<style>
html,
body,
#app {
  margin: 0;
  padding: 0;
  width: 100%;
  max-width: none;
  font-family: var(--font-sans);
  background: var(--surface);
  color: var(--ink);
  line-height: 1.6;
  margin-bottom: 70px;
}
</style>
