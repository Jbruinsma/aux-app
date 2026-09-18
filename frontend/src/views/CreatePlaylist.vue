<template>
  <div class="create-playlist-page">
    <nav class="navbar">
      <div class="container nav-content">
        <router-link to="/" class="site-name">Unchained</router-link>
        <div class="nav-links">
          <router-link to="/dashboard" class="nav-link">Playlists</router-link>
          <router-link to="/testUser" class="nav-link">Profile</router-link>
          <router-link to="/settings" class="nav-link">Settings</router-link>
        </div>
      </div>
    </nav>

    <div class="container create-form">
      <h1>New Playlist</h1>
      <!-- Cover upload -->
      <div class="form-group">
<!--        <label>Playlist Cover</label>-->
        <div class="cover-upload" @click="triggerFileInput">
          <input type="file" accept="image/*" ref="fileInput" @change="handleFileChange" hidden />
          <div v-if="coverPreview" class="cover-preview">
            <img :src="coverPreview" alt="Cover preview" />
          </div>
          <div v-else class="cover-placeholder">
            <p>Click to upload cover image</p>
          </div>
        </div>
      </div>

      <!-- Playlist name -->
      <div class="form-group narrow-center">
        <label>Playlist Name <span class="char-count">({{ playlistName.length }}/50)</span></label>
        <input
          v-model="playlistName"
          type="text"
          maxlength="50"
          placeholder="Enter playlist name"
        />
      </div>

      <!-- Public toggle -->
      <div class="form-group narrow-center toggle-group">
        <label>Make Public</label>
        <label class="switch">
          <input type="checkbox" v-model="isPublic">
          <span class="slider round"></span>
        </label>
      </div>

      <div class="form-group narrow-center">
        <button @click="createPlaylist" class="create-btn">Create Playlist</button>
      </div>
    </div>
  </div>
</template>

<script setup>

import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { v4 as uuidv4 } from 'uuid'
import { useUserStore } from '@/stores/user.js'
import { postToAPI } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'

const userStore = useUserStore()
const currentUser = userStore.userData?.username

const playlistName = ref('')
const isPublic = ref(false)
const coverFile = ref(null)
const coverPreview = ref(null)
const fileInput = ref(null)
const router = useRouter()

function triggerFileInput() {
  fileInput.value.click()
}

function handleFileChange(event) {
  const file = event.target.files[0]
  if (file) {
    coverFile.value = file
    coverPreview.value = URL.createObjectURL(file)
  }
}

async function createPlaylist() {

  try {

    if (!playlistName.value.trim()) {
      alert("Playlist needs a name.")
      return
    }

    const playlistUUID = uuidv4()
    const formData = new FormData()
    formData.append('owner', currentUser)
    formData.append('uuid', playlistUUID)
    formData.append('name', playlistName.value)
    formData.append('isPublic', isPublic.value)
    formData.append('cover', coverFile.value)

    const url = `${API_BASE_URL}/api/playlists/${currentUser}/create`
    const postRequest = await postToAPI(url, formData, false)

    if ('error' in postRequest) {
      await router.push('/dashboard')
    } else {
      await router.push(`/playlist/${currentUser}/${playlistUUID}`)
    }
  } catch (error) {
    console.log(error)
    alert("Something went wrong. Please try again.")
  }
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&family=Libertinus+Math&display=swap');

.char-count { color: #aaa; font-size: 0.9rem; }
.cover-placeholder p { color: #aaa; }
.cover-preview img { border-radius: 8px; max-width: 100%; }
.cover-upload { background: none; border: 2px dashed #666; border-radius: 8px; cursor: pointer; padding: 2rem; text-align: center; transition: background 0.3s; }
.cover-upload:hover { background: rgba(255,255,255,0.1); }
.create-btn { background: #333; border: none; border-radius: 6px; color: #fff; cursor: pointer; display: block; font-size: 1rem; margin: 0 auto; padding: 0.75rem 2rem; transition: background 0.3s; }
.create-btn:hover { background: #444; }
.create-form { background: rgba(255,255,255,0.05); border-radius: 10px; margin: 2rem auto; max-width: 600px; padding: 2rem; }
.create-form h1 { font-family: 'Libertinus Math', serif; font-size: 2rem; margin-bottom: 1.5rem; text-align: center; }
.create-playlist-page { background: #1e1e1e; color: #f0f0f0; min-height: 100vh; }
.form-group { margin-bottom: 1.5rem; }
.form-group label { display: block; font-size: 1.1rem; margin-bottom: 0.5rem; }
input[type="text"] { background: #333; border: none; border-radius: 6px; color: #f0f0f0; padding: 0.75rem; width: 100%; }
.nav-content { align-items: center; display: flex; justify-content: space-between; margin: 0 auto; max-width: 1200px; padding: 0 1rem; }
.nav-link { color: #fff; font-weight: 500; text-decoration: none; transition: color 0.3s; }
.nav-link:hover { color: #ccc; }
.nav-links { display: flex; gap: 2rem; }
.navbar { background: #000; padding: 1rem 0; }
.narrow-center { margin: 0 auto; max-width: 400px; text-align: left; }
.site-name { color: #fff; font-family: 'Libertinus Math', serif; font-size: 1.8rem; text-decoration: none; }
.slider { background-color: #444; border-radius: 34px; bottom: 0; cursor: pointer; left: 0; position: absolute; right: 0; top: 0; transition: .4s; }
.slider:before { background-color: white; border-radius: 50%; bottom: 3px; content: ""; height: 18px; left: 3px; position: absolute; transition: .4s; width: 18px; }
.switch { display: inline-block; height: 24px; position: relative; width: 50px; }
.switch input { height: 0; opacity: 0; width: 0; }
input:checked + .slider { background-color: #34b233; }
input:checked + .slider:before { transform: translateX(26px); }
</style>
