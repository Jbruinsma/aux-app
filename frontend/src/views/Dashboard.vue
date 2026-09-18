<template>
  <div class="dashboard">
    <nav class="navbar">
      <div class="container nav-content">
        <router-link to="/" class="site-name">Unchained</router-link>
        <div class="nav-links">
          <span class="nav-link" @click="rerouteToDashboard()">Playlists</span>
          <span class="nav-link" @click="rerouteToPublicProfile()">Profile</span>
          <span class="nav-link" @click="rerouteToSettings()">Settings</span>
        </div>
      </div>
    </nav>
    <div class="container content">
      <div class="playlist-grid">
        <router-link
          v-for="(playlist, index) in playlists"
          :key="index"
          class="playlist-box"
          :to="{ name: 'Playlist', params: { username: playlist.owner, id: playlist.uuid } }"
        >
          <div class="playlist-image" :style="{ backgroundImage: `url(${resolveCoverURL(playlist.cover)})` }"></div>
          <div class="playlist-name">{{ playlist.name }}</div>
        </router-link>
        <router-link to="/create" class="playlist-box add-new">
          <svg xmlns="http://www.w3.org/2000/svg"
               viewBox="0 0 24 24" fill="none"
               stroke-width="1.5" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round"
                  d="M12 4.5v15m7.5-7.5h-15" />
          </svg>
        </router-link>
      </div>
    </div>
  </div>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { fetchAPI } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'
import { resolveCoverURL } from '@/utils/display.js'
import router from '@/router/index.js'
import { rerouteToDashboard, rerouteToPublicProfile, rerouteToSettings } from '@/utils/reroutes.js'
import { API_BASE_URL } from '@/utils/variables.js'

const userStore = useUserStore()

const currentUser = ref (null)
const user = ref(null)
const playlists = ref([])

onMounted(async () => {

  currentUser.value = userStore.userData?.username

  if (currentUser.value === null) {
    await router.push('/login')
    return
  }

  const url = `${API_BASE_URL}/api/users/profile/${currentUser.value}`
  const data = await fetchAPI(url)
  user.value = data

  if ("error" in data) {
    await router.push('/')
    return
  }

  playlists.value = Object.values(user.value.playlists)
})

</script>
<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&family=Libertinus+Math&display=swap');
.container.content { max-width: 1200px; margin: 2rem auto; padding: 0 1rem; }
.dashboard { background: #1e1e1e; min-height: 100vh; color: #f0f0f0; }
.nav-content { align-items: center; display: flex; justify-content: space-between; margin: 0 auto; max-width: 1200px; padding: 0 1rem; }
.nav-link { color: #fff; font-weight: 500; text-decoration: none; transition: color 0.3s; }
.nav-link:hover { color: #ccc; cursor: pointer; }
.nav-links { display: flex; gap: 2rem; }
.navbar { background: #000; padding: 1rem 0; }
.playlist-box { background: rgba(255,255,255,0.05); border-radius: 8px; cursor: pointer; overflow: hidden; text-align: center; transition: transform 0.3s; text-decoration: none; }
.playlist-box:hover { transform: scale(1.05); }
.playlist-box.add-new { align-items: center; display: flex; height: 240px; justify-content: center; }
.playlist-box.add-new svg { height: 50px; stroke: #fff; width: 50px; }
.playlist-grid { display: grid; gap: 2rem; grid-template-columns: repeat(auto-fit, 180px); justify-content: center; }
.playlist-image { background: #444; background-size: contain; background-position: center; background-repeat: no-repeat; height: 180px; width: 100%; }
.playlist-name { color: #ddd; font-size: 1.1rem; padding: 1rem; }
.site-name { color: #fff; font-family: 'Libertinus Math', serif; font-size: 1.8rem; text-decoration: none; }
</style>
