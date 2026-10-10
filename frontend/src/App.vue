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

import { fetchAPI } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

const bannedRoutes = ['/', '/login', '/register', '/onboarding']

const route = useRoute()
const userStore = useUserStore()
const musicStore = useMusicStore()

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
      if (cancelled || !data || musicStore.isPlaying) return
      musicStore.updateBottomPlayerAfterLogin(data)
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
