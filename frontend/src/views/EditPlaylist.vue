<template>
  <div class="playlist-detail-page">
    <nav class="navbar">
      <div class="container nav-content">
        <router-link to="/" class="site-name">Unchained</router-link>
        <div class="nav-links">
          <router-link to="/dashboard" class="nav-link">Playlists</router-link>
          <router-link to="/settings" class="nav-link">Settings</router-link>
        </div>
      </div>
    </nav>
    <div class="container playlist-content">

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
          <div
            class="playlist-cover"
            :style="{ backgroundImage: `url(${resolveCoverURL(playlist.cover)})` }"
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
        <div class="title-row">
          <div class="title-input">
            <input
              type="text"
              v-model="playlistNameInput"
              placeholder="Playlist name"
              maxlength="50"
            />
          </div>
        </div>
      </div>

      <div v-if="isOwner" class="public-toggle">
        <label class="toggle-label">
          <input
            type="checkbox"
            v-model="playlist.isPublic"
            @change="togglePublicStatus(playlist.isPublic)"
          />
          <span class="slider"></span>
          <span class="toggle-text">{{ playlist.isPublic ? 'Public' : 'Private' }}</span>
        </label>
      </div>

      <div v-if="playlist.musicPieces.length === 0" class="empty-playlist-message">
          <p>This playlist is empty.</p>
      </div>
      <div class="song-list">
        <div
          v-for="(musicPiece, index) in playlist.musicPieces"
          :key="index"
          class="song-item"
        >
          <div
            class="song-thumbnail"
            :style="{ backgroundImage: `url(${resolveCoverURL(musicPiece.cover)})` }"
          ></div>
          <div class="song-info">
            <div class="song-title">{{ musicPiece.title }}</div>
            <div class="song-artist" v-if="displayArtist(musicPiece.artist)">
              {{ musicPiece.artist }}
            </div>
          </div>
          <div class="song-edit" @click="deleteMusicPiece(index)">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
            </svg>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showCancelModal" class="modal-overlay" @click.self="showCancelModal = false">
      <div class="modal-content exit-modal">
        <h2>Exit Editing?</h2>
        <p>If you've made edits, they will not be saved.</p>
        <div class="modal-actions">
          <button class="modal-btn cancel" @click="showCancelModal = false">Cancel</button>
          <button class="modal-btn exit-editing" @click="confirmCancel">Exit</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { fetchAPI, postToAPI } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'
import router from '@/router/index.js'
import { useRoute } from 'vue-router'
import { displayArtist, resolveCoverURL } from '@/utils/display.js'
import { useMusicStore } from '@/stores/music.js'
import { API_BASE_URL } from '@/utils/variables.js'

const route = useRoute()
const { username, id } = route.params

const playlistNameInput = ref('')
const coverInput = ref(null)
const showCancelModal = ref(false)

const isOwner = ref(false)

let oldPreview = ref(null )

const userStore = useUserStore()
const musicStore = useMusicStore()
const currentUser = userStore.userData?.username

const playlistChanges = ref({
  cover: null,
  name: null,
  isPublic: null,
  musicDeleted: []
})

const playlist = ref({
  cover: '',
  name: '',
  owner: '',
  isPublic: false,
  musicPieces: []
})

onMounted(async () => {
  try {
    if (currentUser === null || currentUser === undefined) {
      await router.push({ name: 'Login', query: { redirect: route.fullPath } })
      return
    }

    const url = `${API_BASE_URL}/api/playlists/${username}/${id}`
    const playlistInfo = await fetchAPI(url)

    console.log("Playlist Info:", playlistInfo)

    let canAccess = false

    for (const addedUser of playlistInfo.sharedWith){
      console.log("Added User:", addedUser)
      if (addedUser.username === currentUser) {
        if (addedUser.canEdit === true) {
          canAccess = true
        }
        break
      }
    }

    if (currentUser === playlistInfo.owner) {
      canAccess = true
      isOwner.value = true
    }

    if (!canAccess) {
      await router.push('/dashboard')
      return
    }

    playlist.value.cover = playlistInfo.cover
    playlist.value.name = playlistInfo.name
    playlistNameInput.value = playlistInfo.name
    playlist.value.owner = playlistInfo.username
    playlist.value.musicPieces = playlistInfo.musicPieces
    playlist.value.isPublic = playlistInfo.isPublic
  } catch (err) { console.error('Error fetching playlist:', err) }
})

async function saveChanges() {
  const formData = new FormData()

  formData.append('name', playlistChanges.value.name ?? '')

  if (playlistChanges.value.isPublic === null) { formData.append('isPublic', playlist.value.isPublic) }
  else { formData.append('isPublic', playlistChanges.value.isPublic) }

  formData.append('isPublic', playlistChanges.value.isPublic)
  formData.append('musicDeleted', JSON.stringify(playlistChanges.value.musicDeleted))
  if (playlistChanges.value.cover) {
    formData.append('cover', playlistChanges.value.cover)
  }
  try {
    const postURL = `${API_BASE_URL}/api/playlists/${username}/${id}/edit`
    await postToAPI(postURL, formData, false)
    if (musicStore.getCurrentPlaylistUUID() === id) {
      const deletedIds = playlistChanges.value.musicDeleted.map(d => d.uuid)

      if (deletedIds.includes(musicStore.getCurrentMusicPieceUUID())) { musicStore.reset() }

      const fetchURL = `${API_BASE_URL}/api/playlists/${username}/${id}/play?shuffle=false`
      const updated = await fetchAPI(fetchURL)
      musicStore.playlist = updated.playlist
      musicStore.orderedPlaylist.orderedPlaylist = updated.orderedPlaylist
      musicStore.setCurrentPlaylistIndex(updated.startIndex)
      musicStore.orderedPlaylist.orderedPlaylistCurrentIndex = updated.startIndex

      if (musicStore.getCurrentMusicPieceUUID()) {
        const track = updated.playlist[updated.startIndex]
        musicStore.updateCurrentMusicPiece(track)
      }

      playlistChanges.value.musicDeleted = []
      await musicStore.saveLastPlayback(userStore.userData?.username)
    }

    await router.push({ name: 'Playlist', params: { username, id } })
  } catch (err) {
    console.error('Save error:', err)
  }
}

function togglePublicStatus(newStatus) {
  console.log("Playlist is now public?", newStatus)
  addUpdate('isPublic', newStatus)
}

function triggerCoverUpload() {
  coverInput.value.click()
}

function handleCoverChange(event) {
  const file = event.target.files[0]
  if (file) {
    if (oldPreview.value) {
      URL.revokeObjectURL(oldPreview)
    }
    oldPreview.value = URL.createObjectURL(file)
    playlist.value.cover = oldPreview
    playlistChanges.value.cover = file
    addUpdate('cover', file)
  }
}

function confirmCancel(){
  router.push({ name: 'Playlist', params: { username: username, id: id } })
}

function attemptCancel() {
  showCancelModal.value = true
}

function deleteMusicPiece(index) {
  const removedMusicPiece = playlist.value.musicPieces.splice(index, 1)[0]

  playlistChanges.value.musicDeleted.push({
    index,
    uuid: removedMusicPiece.uuid,
    title: removedMusicPiece.title,
    artist: removedMusicPiece.artist
  })
}

function addUpdate(updateKey, update){
  playlistChanges.value[updateKey] = update
}

watch(playlistNameInput, (newVal) => {
  addUpdate('name', newVal)
})
</script>

<style scoped>
.back-arrow{align-items:center;color:#fff;cursor:pointer;display:flex;flex-shrink:0;padding:0.5rem;transition:transform 0.2s;}
.back-arrow:hover{color:#f44336;}
.back-arrow svg{height:24px;width:24px;}
.cover-overlay{align-items:center;background:rgba(0,0,0,0.4);border-radius:12px;bottom:0;color:#fff;display:flex;font-size:2rem;justify-content:center;left:0;position:absolute;right:0;top:0;}
.cover-overlay:hover{background:rgba(0,0,0,0.6);}
.edit-cover{cursor:pointer;position:relative;}
.empty-playlist-message{margin-top:2rem;text-align:center;}
.empty-playlist-message p{font-size:1.2rem;margin-bottom:0.5rem;}
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
.nav-content{align-items:center;display:flex;justify-content:space-between;margin:0 auto;max-width:1200px;padding:0 1rem;}
.nav-link{color:#fff;font-weight:500;text-decoration:none;transition:color 0.3s;}
.nav-link:hover{color:#ccc;}
.nav-links{display:flex;gap:2rem;}
.navbar{background:#000;padding:1rem 0;}
.playlist-content{margin:2rem auto;max-width:700px;padding:0 1rem;text-align:center;}
.playlist-cover{background-color:#777;background-position:center;background-size:cover;border-radius:12px;height:300px;margin:0 auto;position:relative;width:300px;}
.playlist-detail-page{background:#1e1e1e;color:#f0f0f0;min-height:100vh;}
.save-row{align-items:center;display:flex;gap:1rem;justify-content:space-between;margin-bottom:1rem;}
.save-btn{align-items:center;background:#333;border:none;border-radius:6px;color:#fff;cursor:pointer;display:flex;font-size:1rem;justify-content:center;padding:0.75rem 2rem;transition:background 0.3s;}
.save-btn:hover{background:#444;}
.site-name{color:#fff;font-family:'Libertinus Math',serif;font-size:1.8rem;text-decoration:none;}
.song-artist{color:#ccc;font-size:0.9rem;}
.song-edit{cursor:pointer;}
.song-edit:hover svg{stroke:#f44336;}
.song-edit svg{height:24px;stroke:#fff;width:24px;}
.song-info{flex:1;text-align:left;}
.song-item{align-items:center;background:#2a2a2a;border-radius:8px;display:flex;gap:1rem;justify-content:space-between;max-width:500px;padding:0.5rem 1rem;position:relative;width:100%;}
.song-list{align-items:center;display:flex;flex-direction:column;gap:1rem;}
.song-thumbnail{background:#555 center;background-size:cover;border-radius:6px;height:60px;width:60px;}
.song-title{font-size:1.1rem;}
.title-input{margin-top:1.2rem;margin-bottom:1.5rem;text-align:center;}
.title-input input{background:#2a2a2a;border:1px solid #444;border-radius:8px;box-shadow:inset 0 0 4px rgba(0,0,0,0.6);color:#f0f0f0;font-size:1.4rem;max-width:300px;padding:0.6rem 1rem;transition:border 0.3s,background 0.3s;width:90%;}
.title-input input:focus{background:#333;border-color:#666;outline:none;}
.title-row{align-items:center;display:flex;gap:1rem;justify-content:center;margin-top:1rem;}
.title-row h1{font-family:'Libertinus Math',serif;font-size:2.2rem;margin:0;}

.public-toggle{align-items:center;display:flex;justify-content:center;margin:2rem 0;}
.toggle-label{align-items:center;cursor:pointer;display:flex;gap:1rem;}
.toggle-label input{display:none;}
.slider{background:#555;border-radius:24px;height:24px;position:relative;transition:background 0.3s;width:50px;}
.slider::before{background:#f0f0f0;border-radius:50%;bottom:3px;content:"";height:18px;left:3px;position:absolute;transition:transform 0.3s;width:18px;}
input:checked + .slider{background:#4caf50;}
input:checked + .slider::before{transform:translateX(26px);}
.toggle-text{color:#f0f0f0;font-size:1rem;}
</style>
