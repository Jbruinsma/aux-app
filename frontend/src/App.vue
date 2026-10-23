<template>
  <div id="app">
    <GlobalNotifications />

    <router-view></router-view>
    <BottomPlayer v-if="!hideBottomPlayerOnThisRoute" />
    <ExternalLinkDialog />
  </div>
</template>

<script setup>
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'

import BottomPlayer from '@/components/BottomPlayer.vue'
import ExternalLinkDialog from '@/components/ExternalLinkDialog.vue'
import GlobalNotifications from '@/components/GlobalNotifications.vue'

import { useUserStore } from '@/stores/user.js'
import { useMusicStore } from '@/stores/music.js'
import { useEventsStore } from '@/stores/events.js'

import { fetchAPI } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

const bannedRoutes = ['/', '/login', '/register', '/onboarding']

const route = useRoute()
const userStore = useUserStore()
const musicStore = useMusicStore()
const eventsStore = useEventsStore()

// One live stream per session: opens after login (once onboarding is done), restarts with a new token, closes on logout
watch(
  () => [userStore.loggedIn && userStore.onboarded, userStore.token],
  ([active, token], [wasActive, previousToken] = []) => {
    if (!active || !token) eventsStore.disconnect()
    else if (wasActive && previousToken && token !== previousToken) eventsStore.restart()
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

// Restore once per authenticated session, not on page changes. Ignore stale replies.
watch(
  () => [userStore.loggedIn, userStore.token, userStore.onboarded],
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
