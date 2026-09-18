<template>
  <div class="playlist-detail-page">
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
    <div class="container playlist-content">
      <div class="cover-section">
        <div
          class="playlist-cover"
          :style="{ backgroundImage: `url(${resolveCoverURL(playlist.cover)})` }"
        ></div>
        <div class="title-row">
          <h1>{{ playlist.name }}</h1>
          <div class="menu-dots" @click="toggleMenu">⋯</div>
        </div>
        <router-link :to="{ name: 'Profile', params: { username: username } }" class="owner-name">
          @{{ playlist.owner }}
        </router-link>
      </div>
      <div class="controls">
        <button @click="handlePlayButtonClick" class="play-btn control-btn">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="24"
            height="24"
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="1.5"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M5.25 5.653c0-.856.917-1.398 1.667-.986l11.54 6.347a1.125 1.125 0 0 1 0 1.972l-11.54 6.347a1.125 1.125 0 0 1-1.667-.986V5.653Z"
            />
          </svg>
          Play
        </button>
        <button @click="handleShuffleButtonClick" class="shuffle-btn control-btn">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="24"
            height="24"
            fill="none"
            viewBox="0 0 24 24"
            stroke-width="1.5"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M7.5 21 3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5"
            />
          </svg>
          Shuffle
        </button>
      </div>
      <div v-if="playlist.musicPieces.length === 0" class="empty-playlist-message">
        <div v-if="isOwner || canEdit">
          <p>Add an mp3 file to start listening</p>
          <button class="btn primary" @click="addMusic">Add Music</button>
        </div>
        <div v-else>
          <p>This playlist is empty.</p>
        </div>
      </div>
      <div class="music-piece-list">
        <div
          v-for="(musicPiece, index) in playlist.musicPieces"
          :key="index"
          class="music-piece-item"
          @click="handleSpecificMusicPieceClick(index)"
        >
          <div
            class="music-piece-thumbnail"
            :style="{ backgroundImage: `url(${resolveCoverURL(musicPiece.cover)})` }"
          ></div>
          <div class="music-piece-info">
            <div class="music-piece-title">{{ musicPiece.title }}</div>
            <div class="music-piece-artist" v-if="displayArtist(musicPiece.artist)">
              {{ musicPiece.artist }}
            </div>
          </div>
          <router-link
            v-if="isOwner || canEdit"
            class="music-piece-edit"
            :to="{
              name: 'EditMP3',
              params: {
                username: playlist.owner,
                playlist_id: id,
                index: index,
                uuid: musicPiece.uuid,
              },
            }"
            @click.stop
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="m16.862 4.487 1.687-1.688a1.875 1.875
                  0 1 1 2.652 2.652L6.832 19.82a4.5 4.5
                  0 0 1-1.897 1.13l-2.685.8.8-2.685a4.5
                  4.5 0 0 1 1.13-1.897L16.863 4.487Zm0
                  0L19.5 7.125"
              />
            </svg>
          </router-link>
        </div>
      </div>
      <div class="stats">
        <p v-if="totalSaved === 1">
          This playlist is saved by 1 person
        </p>
        <p v-else>
          This playlist is saved by {{ totalSaved }} people
        </p>
      </div>
    </div>
    <div v-if="showMenu" class="modal-overlay" @click.self="showMenu = false">
      <div class="modal-content options-modal">
        <h1>Options</h1>
        <template v-if="isOwner">
          <div class="modal-option" @click="addMusic">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="size-6"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
            </svg>
            Add Music
          </div>
          <div class="modal-option" @click="editPlaylist">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="size-6"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L6.832 19.82a4.5 4.5 0 0 1-1.897 1.13l-2.685.8.8-2.685a4.5 4.5 0 0 1 1.13-1.897L16.863 4.487Zm0 0L19.5 7.125"
              />
            </svg>
            Edit Playlist
          </div>
          <div class="modal-option" @click="addFriend(username, id)">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="size-6"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M18 7.5v3m0 0v3m0-3h3m-3 0h-3m-2.25-4.125a3.375 3.375 0 1 1-6.75 0 3.375 3.375 0 0 1 6.75 0ZM3 19.235v-.11a6.375 6.375 0 0 1 12.75 0v.109A12.318 12.318 0 0 1 9.374 21c-2.331 0-4.512-.645-6.374-1.766Z"
              />
            </svg>
            Manage Access
          </div>
          <div class="modal-option delete-option" @click="attemptDeletePlaylist">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="size-6"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0"
              />
            </svg>
            Delete Playlist
          </div>
        </template>
        <template v-else-if="isSharedWith && canEdit">
          <div class="modal-option" @click="addMusic">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="size-6"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
            </svg>
            Add Music
          </div>
          <div class="modal-option" @click="editPlaylist">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="size-6"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L6.832 19.82a4.5 4.5 0 0 1-1.897 1.13l-2.685.8.8-2.685a4.5 4.5 0 0 1 1.13-1.897L16.863 4.487Zm0 0L19.5 7.125"
              />
            </svg>
            Edit Playlist
          </div>
          <div class="modal-option" @click="removePlaylistFromLibrary">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="size-6"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
            </svg>
            Remove from Library
          </div>
        </template>
        <template v-else-if="isSharedWith && !canEdit">
          <div class="modal-option" @click="removePlaylistFromLibrary">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="size-6"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
            </svg>
            Remove from Library
          </div>
        </template>
        <template v-else>
          <div v-if="isAddedToLibrary" class="modal-option" @click="removePlaylistFromLibrary">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="size-6"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
            </svg>
            Remove from Library
          </div>
          <div v-else class="modal-option" @click="addPublicPlaylistToLibrary">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="size-6"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
            </svg>
            Add to Library
          </div>
        </template>
        <button class="modal-close" @click="showMenu = false">Close</button>
      </div>
    </div>
    <div v-if="showDeleteModal" class="modal-overlay" @click.self="showDeleteModal = false">
      <div class="modal-content exit-modal">
        <h2>Delete Playlist?</h2>
        <p>This action cannot be undone. Are you sure you want to delete "{{ playlist.name }}"?</p>
        <div class="modal-actions">
          <button class="modal-btn cancel" @click="showDeleteModal = false">Cancel</button>
          <button class="modal-btn exit-editing" @click="confirmDelete">Delete</button>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { displayArtist, resolveCoverURL } from '@/utils/display.js'
import { onMounted, ref } from 'vue'
import { fetchAPI, postToAPI } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'
import router from '@/router/index.js'
import { useRoute } from 'vue-router'
import { isLoggedIn } from '@/utils/user.js'
import { useMusicStore } from '@/stores/music.js'
import { rerouteToDashboard, rerouteToPublicProfile, rerouteToSettings } from '@/utils/reroutes.js'
import { API_BASE_URL } from '@/utils/variables.js'

const userStore = useUserStore()
const currentUser = userStore.userData?.username
const musicStore = useMusicStore()

const showDeleteModal = ref(false)
const showMenu = ref(false)
const isOwner = ref(false)
const isAddedToLibrary = ref(false)
const isPublic = ref(false)
const isSharedWith = ref(false)
const canEdit = ref(false)

const playlist = ref({
  cover: '',
  name: '',
  owner: '',
  musicPieces: [],
  savedBy: [],
  added: []
})

const totalSaved = ref(0)

const route = useRoute()
const { username, id } = route.params

onMounted(async () => {
  try {
    const url = `${API_BASE_URL}/api/playlists/${username}/${id}`
    const playlistInfo = await fetchAPI(url)

    if ('error' in playlistInfo) {
      await router.push('/dashboard')
      return
    }

    isOwner.value = playlistInfo.username === currentUser
    if (isOwner.value) canEdit.value = true

    if (playlistInfo.isPublic === false) {
      isPublic.value = false
    } else if (playlistInfo.isPublic === true) {
      isPublic.value = true
    } else {
      console.log('Error: isPublic is not a boolean value.')
      await router.push('/dashboard')
      return
    }

    for (const userSharedWith of playlistInfo.sharedWith) {
      if (userSharedWith.username === currentUser) {
        isSharedWith.value = true
        canEdit.value = userSharedWith.canEdit
        isAddedToLibrary.value = true
        break
      }
    }

    for (const userWhoSaved of playlistInfo.savedBy) {
      console.log(userWhoSaved)
      if (currentUser === userWhoSaved) {
        isAddedToLibrary.value = true
        break
      }
    }

    const canAccess = isOwner.value || isPublic.value || isSharedWith.value

    if (!canAccess) {
      await router.push('/dashboard')
      return
    }

    playlist.value.cover = playlistInfo.cover
    playlist.value.name = playlistInfo.name
    playlist.value.owner = playlistInfo.username
    playlist.value.musicPieces = playlistInfo.musicPieces
    playlist.value.savedBy = playlistInfo.savedBy
    playlist.value.added = playlistInfo.sharedWith
    totalSaved.value = playlistInfo.savedBy.length + playlistInfo.sharedWith.length

  } catch (err) {
    console.error('Error fetching playlist:', err)
  }
})

function toggleMenu() {
  showMenu.value = !showMenu.value
}

function addMusic() {
  showMenu.value = false
  router.push({ name: 'Add', params: { username, id } })
}

function editPlaylist() {
  showMenu.value = false
  router.push({ name: 'Edit', params: { username, id } })
}

function addFriend() {
  showMenu.value = false
  router.push({ name: 'AddFriends', params: { username, id } })
}

async function addPublicPlaylistToLibrary() {
  if (!isLoggedIn(currentUser)) {
    await router.push({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }
  const url = `${API_BASE_URL}/api/users/${currentUser}/add-public-playlist`
  const response = await postToAPI(url, {
    playlist_owner: username,
      playlist_uuid: id,
    },
    true,
  )

  if ('error' in response) {
    console.log('ERROR IN ADDING PUBLIC PLAYLIST TO LIBRARY: ', response)
  } else {
    playlist.value.savedBy = response.savedBy
    isAddedToLibrary.value = true
  }
  showMenu.value = false
}

async function removePlaylistFromLibrary() {
  if (!isLoggedIn(currentUser)) {
    await router.push({
      name: 'Login',
      query: { redirect: route.fullPath },
    })
    return
  }
  isAddedToLibrary.value = false
  canEdit.value = false
  showMenu.value = false

  let url = `${API_BASE_URL}/api/users/${currentUser}/remove-public-playlist`

  for (const userSharedWith of playlist.value.added){
    if (userSharedWith.username === currentUser) {
      url = `${API_BASE_URL}/api/users/${currentUser}/remove-added-to-playlist`
    }
  }

   const response = await postToAPI(
    url,
    {
      playlist_owner: username,
      playlist_uuid: id,
    },
    true,
  )

  if ('error' in response) {
    console.log('ERROR REMOVING PLAYLIST FROM LIBRARY: ', response)
  } else {
    playlist.value.savedBy = response.savedBy
    playlist.value.added = response.added
    totalSaved.value = playlist.value.savedBy.length + playlist.value.added.length
    isAddedToLibrary.value = false

    if (isPublic.value === false){
      if (musicStore.getCurrentPlaylistUUID() === id){ musicStore.reset() }
      await router.push('/dashboard')
      return
    }

  }
  showMenu.value = false
}

function attemptDeletePlaylist() {
  showDeleteModal.value = true
  showMenu.value = false
}

async function confirmDelete() {
  try {
    const url = `${API_BASE_URL}/api/playlists/${username}/${id}/delete`
    await postToAPI(url)
    if (musicStore.getCurrentPlaylistUUID() === id){ musicStore.reset() }
    await router.push('/dashboard')
  } catch (err) {
    console.error('Error deleting playlist:', err)
  }
}

async function handleSpecificMusicPieceClick(index) {
  musicStore.forceShowPlayer()
  await musicStore.loadPlaylist(id, playlist.value.owner, index)
}

async function handlePlayButtonClick() {
  musicStore.reset()
  musicStore.forceShowPlayer()
  await musicStore.loadPlaylist(id, playlist.value.owner, 0)
}

async function handleShuffleButtonClick() {
  musicStore.reset()
  musicStore.forceShowPlayer()
  musicStore.toggleShuffle()
  await musicStore.loadPlaylist(id, playlist.value.owner, null)
}
</script>
<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&family=Libertinus+Math&display=swap');
.cover-section{margin-bottom:1rem;position:relative;}
.controls{display:flex;gap:2rem;justify-content:center;margin-bottom:2rem;}
.control-btn:hover{cursor:pointer;}
.exit-modal{border:2px solid #f44336;}
.empty-playlist-message{margin-top:2rem;text-align:center;}
.empty-playlist-message p{font-size:1.2rem;margin-bottom:0.5rem;}
.menu-dots{cursor:pointer;font-size:1.5rem;user-select:none;}
.modal-actions{display:flex;gap:1rem;justify-content:center;}
.modal-btn{background:#555;border:none;border-radius:6px;color:#fff;cursor:pointer;font-size:1rem;padding:0.75rem 1.5rem;transition:background 0.3s;}
.modal-btn.cancel:hover{background:#666;}
.modal-btn.exit-editing{background:#d9534f;}
.modal-btn.exit-editing:hover{background:#c9302c;}
.modal-close{background:#333;border:none;border-radius:6px;color:#fff;cursor:pointer;margin-top:1rem;padding:0.75rem 1.5rem;}
.modal-close:hover{background:#444;}
.modal-content{background:#2a2a2a;border-radius:8px;max-width:400px;padding:2rem;text-align:center;width:90%;}
.modal-content h2{font-size:1.5rem;margin-bottom:1rem;}
.modal-content p{color:#ccc;margin-bottom:2rem;}
.modal-option{align-items:center;background:#333;border-radius:6px;color:#fff;cursor:pointer;display:flex;gap:0.75rem;margin:0.5rem 0;padding:0.75rem 1rem;transition:background 0.3s;}
.modal-option:hover{background:#444;}
.modal-option svg{display:block;flex:0 0 auto;height:20px;width:20px;}
.modal-overlay{align-items:center;background:rgba(0,0,0,0.7);display:flex;height:100vh;justify-content:center;left:0;position:fixed;top:0;width:100vw;z-index:1000;}
.nav-content{align-items:center;display:flex;justify-content:space-between;margin:0 auto;max-width:1200px;padding:0 1rem;}
.nav-link{color:#fff;font-weight:500;text-decoration:none;transition:color 0.3s;}
.nav-link:hover{color:#ccc;cursor:pointer;}
.nav-links{display:flex;gap:2rem;}
.navbar{background:#000;padding:1rem 0;}
.owner-name{color:#fff;font-size:1.2rem;margin-bottom:1rem;text-decoration:none;}
.owner-name:hover{font-style:italic;}
.options-modal{border:2px solid #fff;}
.play-btn,.shuffle-btn{display:flex;}
.play-btn,.shuffle-btn,.empty-playlist-message .btn{align-items:center;background:#333;border:none;border-radius:8px;color:#fff;font-size:1rem;gap:0.5rem;padding:0.75rem 1.5rem;transition:background 0.3s;}
.play-btn:hover,.shuffle-btn:hover,.empty-playlist-message .btn:hover{background:#444;}
.playlist-content{margin:2rem auto;max-width:700px;padding:0 1rem;text-align:center;}
.playlist-cover{background-color:#777;background-position:center;background-size:cover;border-radius:12px;height:300px;margin:0 auto;width:300px;}
.playlist-detail-page{background:#1e1e1e;color:#f0f0f0;min-height:100vh;}
.site-name{color:#fff;font-family:'Libertinus Math',serif;font-size:1.8rem;text-decoration:none;}
.music-piece-artist{color:#ccc;font-size:0.9rem;}
.music-piece-edit{cursor:pointer;opacity:0;transition:opacity 0.3s;}
.music-piece-edit:hover svg{stroke:#f06543;}
.music-piece-edit svg{height:24px;stroke:#f0f0f0;width:24px;}
.music-piece-info{flex:1;text-align:left;}
.music-piece-item{align-items:center;background:#2a2a2a;border-radius:8px;display:flex;gap:1rem;justify-content:space-between;max-width:500px;padding:0.5rem 1rem;position:relative;width:100%;}
.music-piece-item:hover{cursor:pointer;}
.music-piece-item:hover .music-piece-edit{cursor:pointer;opacity:1;}
.music-piece-list{align-items:center;display:flex;flex-direction:column;gap:1rem;}
.music-piece-thumbnail{background:#555 center;background-size:cover;border-radius:6px;height:60px;width:60px;}
.music-piece-title{font-size:1.1rem;}
.title-row{align-items:center;display:flex;gap:1rem;justify-content:center;margin-top:1rem;}
.title-row h1{font-family:'Libertinus Math',serif;font-size:2.2rem;margin:0;}
.delete-option{border:2px solid #f44336;margin-top:2rem;}
</style>
