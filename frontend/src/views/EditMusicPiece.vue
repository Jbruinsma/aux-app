<template>
  <div class="edit-song-page">
    <nav class="navbar">
      <div class="container nav-content">
        <router-link to="/" class="site-name">Unchained</router-link>
        <div class="nav-links">
          <router-link to="/dashboard" class="nav-link">Playlists</router-link>
          <router-link to="/settings" class="nav-link">Settings</router-link>
        </div>
      </div>
    </nav>

    <div class="container content">

      <div class="save-row">
        <div class="back-arrow" @click="attemptCancel">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-6">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0 0 13.5 3h-6a2.25 2.25 0 0 0-2.25 2.25v13.5A2.25 2.25 0 0 0 7.5 21h6a2.25 2.25 0 0 0 2.25-2.25V15M12 9l-3 3m0 0 3 3m-3-3h12.75" />
          </svg>
        </div>
        <button class="save-btn" @click="saveChanges">Save</button>
      </div>

      <div class="cover-section">
        <div class="edit-cover" @click="triggerCoverUpload">
          <div class="song-cover"
               :style="{ backgroundImage: 'url(' + resolveCoverURL(musicPiece.cover) + ')' }"
          >
            <div class="cover-overlay">⋯</div>
          </div>
          <input
            type="file"
            ref="coverInput"
            accept="image/*"
            style="display: none"
            @change="handleCoverChange"
          />
        </div>
      </div>

      <div class="fields">
        <div class="field">
          <label>Name</label>
          <input
            type="text"
            v-model="songName"
            placeholder="File name (e.g. cool_song.mp3)"
          />
        </div>

        <div class="field">
          <label>Artist</label>
          <input
            type="text"
            v-model="artistName"
            placeholder="Artist name"
          />
        </div>

        <div class="field">
          <label>Download</label>
        <div class="song-download-box" @click="downloadMP3">
          <div class="song-thumbnail" :style="{ backgroundImage: 'url(' + resolveCoverURL(musicPiece.cover) + ')' }"></div>
          <div class="song-info">
            <div class="song-title">{{ musicPiece.title }}</div>
          </div>
          <div class="download-icon">
            <a :href="musicPiece.audio" download @click.stop>
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3" />
              </svg>
            </a>
          </div>
        </div>
        </div>

      </div>
    </div>

    <div v-if="showCancelModal" class="modal-overlay" @click.self="showCancelModal = false">
      <div class="modal-content exit-modal">
        <h2>Exit Editing?</h2>
        <p>If you've made edits to this file, they will not be saved.</p>
        <div class="modal-actions">
          <button class="modal-btn cancel" @click="showCancelModal = false">Cancel</button>
          <button class="modal-btn exit-editing" @click="confirmCancel">Exit</button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchAPI, postToAPI } from '@/utils/api.js'
import {displayArtist, resolveCoverURL} from '@/utils/display.js'
import { useMusicStore } from '@/stores/music.js'
import { useUserStore } from '@/stores/user.js'
import { API_BASE_URL } from '@/utils/variables.js'

const route = useRoute()
const router = useRouter()
const { username, playlist_id, index, uuid } = route.params
const showCancelModal = ref(false)

const userStore = useUserStore()
const musicStore = useMusicStore()

const musicPiece = ref({
  cover: '',
  audio: '',
  title: '',
  artist: ''
})

const songName = ref('')
const artistName = ref('')
const coverInput = ref(null)
let oldPreview = null

onMounted(async () => {
  const url = `${API_BASE_URL}/api/playlists/summary/${username}/${playlist_id}/music_piece/${index}/${uuid}`
  const pieceInfo = await fetchAPI(url)
  musicPiece.value.cover = pieceInfo.cover
  musicPiece.value.audio = pieceInfo.audio
  musicPiece.value.title = pieceInfo.title
  musicPiece.value.artist = pieceInfo.artist
  songName.value = pieceInfo.title
  artistName.value = displayArtist(pieceInfo.artist)
})

function triggerCoverUpload() {
  coverInput.value.click()
}

function handleCoverChange(event) {
  const file = event.target.files[0]
  if (file) {
    if (oldPreview) URL.revokeObjectURL(oldPreview)
    oldPreview = URL.createObjectURL(file)
    musicPiece.value.cover = oldPreview
    musicPiece.value.newCoverFile = file
  }
}

async function saveChanges() {
  const formData = new FormData()
  formData.append('name', songName.value)
  formData.append('artist', artistName.value || '')
  if (musicPiece.value.newCoverFile) {
    formData.append('cover', musicPiece.value.newCoverFile)
  }

  const url = `${API_BASE_URL}/api/playlists/update/${username}/${playlist_id}/music_piece/${index}/${uuid}`
  const response = await postToAPI(url, formData, false)
  const updatedPiece = response.musicPiece

  if (musicStore.getCurrentPlaylistUUID() === playlist_id) {

    if (musicStore.getCurrentMusicPieceUUID() === updatedPiece.uuid) {
      musicStore.currentMusicPiece.cover  = updatedPiece.cover
      musicStore.currentMusicPiece.title  = updatedPiece.title
      musicStore.currentMusicPiece.artist = updatedPiece.artist
    }

    for (const musicPiece of musicStore.playlist) {
      if (musicPiece.uuid === updatedPiece.uuid) {
        musicPiece.cover  = updatedPiece.cover
        musicPiece.title  = updatedPiece.title
        musicPiece.artist = updatedPiece.artist
        break
      }
    }

    for (const orderedMusicPiece of musicStore.orderedPlaylist.orderedPlaylist) {
      if (orderedMusicPiece.uuid === updatedPiece.uuid) {
        orderedMusicPiece.cover  = updatedPiece.cover
        orderedMusicPiece.title  = updatedPiece.title
        orderedMusicPiece.artist = updatedPiece.artist
        break
      }
    }

    await musicStore.saveLastPlayback(userStore.userData?.username)

  }

  await router.push(`/playlist/${username}/${playlist_id}`)
}

function attemptCancel() {
  showCancelModal.value = true
}

function confirmCancel(){
  router.push({ name: 'Playlist', params: { username: username, id: playlist_id } })
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&family=Libertinus+Math&display=swap');
.back-arrow{align-items:center;color:#fff;cursor:pointer;display:flex;flex-shrink:0;padding:0.5rem;transition:transform 0.2s;}
.back-arrow:hover{color:#f44336;}
.back-arrow svg{height:24px;width:24px;}
.save-row{align-items:center;display:flex;gap:1rem;justify-content:space-between;margin-bottom:1rem;}
.save-btn{align-items:center;background:#333;border:none;border-radius:6px;color:#fff;cursor:pointer;display:flex;font-size:1rem;justify-content:center;padding:0.75rem 2rem;transition:background 0.3s;}
.save-btn:hover{background:#444;}
.container.content{margin:2rem auto;max-width:600px;padding:0 1rem;text-align:center;}
.cover-overlay{align-items:center;background:rgba(0,0,0,0.4);border-radius:12px;bottom:0;color:#fff;display:flex;font-size:2rem;justify-content:center;left:0;position:absolute;right:0;top:0;transition:background 0.3s;}
.cover-overlay:hover{background:rgba(0,0,0,0.6);}
.cover-section{margin-bottom:2rem;}
.download-btn{background:#444;border-radius:8px;color:#f0f0f0;display:inline-block;padding:0.75rem 1.5rem;text-decoration:none;transition:background 0.3s;}
.download-btn:hover{background:#555;}
.download-icon svg{height:22px;stroke:#fff;width:22px;}
.download-icon svg:hover{stroke:#4caf50;}
.edit-song-page{background:#1e1e1e;color:#f0f0f0;min-height:100vh;}
.field{align-items:center;display:flex;flex-direction:column;margin-bottom:1.5rem;}
.field label{color:#ccc;font-size:0.9rem;margin-bottom:0.3rem;text-align:left;width:60%;}
.field input{background:#2a2a2a;border:none;border-radius:8px;color:#f0f0f0;padding:0.75rem;width:60%;}
.fields{margin-bottom:2rem;}
.nav-content{align-items:center;display:flex;justify-content:space-between;margin:0 auto;max-width:1200px;padding:0 1rem;}
.nav-link{color:#fff;font-weight:500;text-decoration:none;transition:color 0.3s;}
.nav-link:hover{color:#ccc;}
.nav-links{display:flex;gap:2rem;}
.navbar{background:#000;padding:1rem 0;}
.site-name{color:#fff;font-family:'Libertinus Math',serif;font-size:1.8rem;text-decoration:none;}
.song-cover{background:#444 center;background-size:cover;border-radius:12px;height:300px;margin:0 auto;position:relative;width:300px;}
.song-download-box{align-items:center;background:#2a2a2a;border-radius:8px;cursor:pointer;display:flex;gap:1rem;justify-content:space-between;margin:1rem auto;padding:0.5rem 1rem;width:50%;}
.song-thumbnail{background:#555 center;background-size:cover;border-radius:6px;height:35px;width:35px;}
.song-info{flex:1;text-align:left;}
.song-title{font-size:1rem;}
.download-icon svg{height:24px;stroke:#fff;width:24px;}
.download-icon svg:hover{stroke:#4caf50;}
.exit-modal{border:2px solid #f44336;}
.modal-actions{display:flex;gap:1rem;justify-content:center;}
.modal-btn{background:#555;border:none;border-radius:6px;color:#fff;cursor:pointer;font-size:1rem;padding:0.75rem 1.5rem;transition:background 0.3s;}
.modal-btn.cancel:hover{background:#666;}
.modal-btn.exit-editing{background:#d9534f;}
.modal-btn.exit-editing:hover{background:#c9302c;}
.modal-content{background:#2a2a2a;border-radius:8px;max-width:400px;padding:2rem;text-align:center;width:90%;}
.modal-content h2{font-size:1.5rem;margin-bottom:1rem;}
.modal-content p{color:#ccc;margin-bottom:2rem;}
.modal-option svg{display:block;flex:0 0 auto;height:20px;width:20px;}
.modal-overlay{align-items:center;background:rgba(0,0,0,0.7);display:flex;height:100vh;justify-content:center;left:0;position:fixed;top:0;width:100vw;z-index:1000;}
</style>
