<template>
  <div id="app">
    <router-view></router-view>
    <BottomPlayer v-if="!hideBottomPlayerOnThisRoute" />
  </div>
</template>

<script setup>
import { computed, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import BottomPlayer from '@/components/BottomPlayer.vue'
import { useUserStore } from '@/stores/user.js'
import { useMusicStore } from '@/stores/music.js'
import { fetchAPI } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

const bannedRoutes = ['/', '/login', '/register']

const route = useRoute()
const userStore = useUserStore()
const musicStore = useMusicStore()

const hideBottomPlayerOnThisRoute = computed(() => {
  if (bannedRoutes.includes(route.path)) { return true }
  const currentUser = userStore.userData?.username
  return !(currentUser || musicStore.forceShowPlayerActive || musicStore.currentSong)
})

watchEffect(async () => {
  if (userStore.loggedIn) {
    const url = `${API_BASE_URL}/api/users/${userStore.userData.username}/get-last-playback`
    try {
      const data = await fetchAPI(url)

      if (data === null || data === undefined) { return }

      musicStore.updateBottomPlayerAfterLogin(data)
    } catch (err) {
      console.log("Error fetching last playback:", err)
    }
  }
})

</script>

<style>
html, body, #app { margin: 0; padding: 0; width: 100%; max-width: none; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #121212; color: #f0f0f0; line-height: 1.6; margin-bottom: 70px; }
</style>
