<template>
  <div class="profile-page">
    <nav class="navbar">
      <div class="container nav-content">
        <router-link to="/" class="site-name">Unchained</router-link>
        <div class="nav-links">
          <span class="nav-link" @click="rerouteToDashboard()">Playlists</span>
          <span class="nav-link" @click="rerouteToPublicProfile()">Profile</span>
          <span class="nav-link" @click="rerouteToSettings()">Settings</span>
        </div>
        <div class="nav-search">
          <input type="text" v-model="searchQuery" @keyup.enter="performSearch" placeholder="Search user..." />
        </div>
      </div>
    </nav>

    <div v-if="user" class="container profile-layout">
      <div v-if="'error' in user" class="not-found">
        <h1>User does not exist</h1>
        <p>Sorry, we couldn’t find @{{ username }}.</p>
      </div>

      <div v-else class="profile-container">
        <div class="profile-info">
          <div class="profile-pic" :style="{ backgroundImage: `url(${resolveCoverURL(user.profilePicture)})` }"></div>
          <div class="username">@{{ user.username }}</div>
        </div>

        <div class="profile-content">
          <div v-if="publicPlaylists.length !== 0">
          <h2>Public Playlists</h2>
          <div class="playlist-grid">
            <router-link
              v-for="(playlist, index) in publicPlaylists"
              :key="index"
              class="playlist-box"
              :to="{ name: 'Playlist', params: { username: playlist.owner, id: playlist.uuid } }"
            >
              <div class="playlist-image" :style="{ backgroundImage: `url(${resolveCoverURL(playlist.cover)})` }"></div>
              <div class="playlist-name">{{ playlist.name }}</div>
            </router-link>
          </div>
        </div>
          <div v-else class="empty-state">
            <p>This user hasn’t made any public playlists yet. Check back later!</p>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="loading-container">
      <p>Loading profile...</p>
    </div>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { computed, onMounted, ref, watch } from 'vue'
import {fetchAPI} from "@/utils/api.js";
import {resolveCoverURL} from "@/utils/display.js";
import { rerouteToDashboard, rerouteToPublicProfile, rerouteToSettings } from '@/utils/reroutes.js'
import { API_BASE_URL } from '@/utils/variables.js'
import router from '@/router/index.js'

const route = useRoute()
const username = computed(() => route.params.username)
const user = ref(null)
const publicPlaylists = ref([])

const searchQuery = ref('')

async function loadProfile() {
  const url = `${API_BASE_URL}/api/users/profile/${username.value}`
  const data = await fetchAPI(url)
  user.value = data

  if ("error" in data) {
    console.log("Error fetching user:", data.error)
    return
  }

  const userPlaylists = user.value.playlists
  for (const playlistID in userPlaylists){
    const currPlaylist = userPlaylists[playlistID]
    if (currPlaylist.isPublic === true && currPlaylist.owner === username.value) {
      publicPlaylists.value.push(currPlaylist)
    }
  }
}

onMounted(loadProfile)

watch(() => route.params.username,() => {
  publicPlaylists.value = []
  loadProfile()
})


function performSearch() {
  if (searchQuery.value.trim() !== '') {
    router.push({ name: 'Profile', params: { username: searchQuery.value.trim() } })
    searchQuery.value = ''
  }
}

</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&family=Libertinus+Math&display=swap');
.container.profile-layout{margin:2rem auto;max-width:1200px;padding:0 1rem;}
.empty-state{color:#ccc;font-size:1.1rem;margin-top:4rem;text-align:center;}
.loading-container{margin-top:4rem;text-align:center;}
.nav-content{align-items:center;display:flex;justify-content:space-between;margin:0 auto;max-width:1200px;padding:0 1rem;}
.nav-link{color:#fff;font-weight:500;text-decoration:none;transition:color 0.3s;}
.nav-link:hover{color:#ccc;cursor:pointer;}
.nav-links{display:flex;gap:2rem;}
.nav-search{display:flex;justify-content:flex-end;margin-left:2rem;}
.nav-search input{background:#121212;border:1px solid rgba(255,255,255,0.2);border-radius:4px;color:#f0f0f0;font-size:1rem;padding:0.4rem 0.8rem;transition:border 0.3s;width:180px;}
.nav-search input:focus{border:1px solid #f0f0f0;outline:none;}
.navbar{background:#000;padding:1rem 0;}
.not-found{margin:4rem auto;max-width:600px;text-align:center;}
.not-found h1{font-size:2rem;margin-bottom:1rem;}
.not-found p{color:#ccc;font-size:1.1rem;}
.playlist-box{background:rgba(255,255,255,0.05);border-radius:8px;color:inherit;cursor:pointer;overflow:hidden;text-align:center;text-decoration:none;transition:transform 0.3s;}
.playlist-box:hover{transform:scale(1.05);}
.playlist-grid{display:grid;gap:2rem;grid-template-columns:repeat(auto-fit,180px);}
.playlist-image{background:#444;background-position:center;background-repeat:no-repeat;background-size:100% 100%;height:180px;width:100%;}
.playlist-name{color:#ddd;font-size:1.1rem;padding:1rem;}
.profile-container{align-items:flex-start;display:flex;gap:3rem;}
.profile-content{flex:1;}
.profile-content h2{border-bottom:2px solid rgba(255,255,255,0.2);font-size:2rem;margin-bottom:1.5rem;padding-bottom:0.5rem;}
.profile-info{align-items:center;display:flex;flex:0 0 220px;flex-direction:column;}
.profile-page{background:#1e1e1e;color:#f0f0f0;min-height:100vh;}
.profile-pic{background:#444;background-position:center;background-repeat:no-repeat;background-size:cover;border-radius:50%;height:150px;margin-bottom:1rem;width:150px;}
.site-name{color:#fff;font-family:'Libertinus Math',serif;font-size:1.8rem;text-decoration:none;}
.username{color:#ddd;font-family:'Libertinus Math',serif;font-size:1.8rem;}
@media (max-width: 768px){
  .nav-content{align-items:flex-start;flex-direction:column;gap:1rem;}
  .nav-links{flex-wrap:wrap;gap:1rem;justify-content:center;width:100%;}
  .nav-search{justify-content:center;margin-left:0;width:100%;}
  .nav-search input{width:80%;}
  .profile-container{align-items:center;flex-direction:column;gap:2rem;}
  .playlist-grid{grid-template-columns:1fr;justify-items:center;width:100%;}
}

</style>
