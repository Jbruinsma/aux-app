<template>
  <div class="add-music-page">
    <nav class="navbar">
      <div class="nav-content">
        <router-link to="/" class="site-name">Unchained</router-link>
        <div class="nav-links">
          <router-link to="/dashboard" class="nav-link">Playlists</router-link>
          <router-link to="/settings" class="nav-link">Settings</router-link>
        </div>
      </div>
    </nav>

    <div class="container">
      <div class="upload-section">
        <div class="title-row">
          <div class="back-arrow" @click="attemptBack">
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
                d="M15.75 9V5.25A2.25 2.25 0 0 0 13.5 3h-6a2.25 2.25 0 0 0-2.25 2.25v13.5A2.25 2.25 0 0 0 7.5 21h6a2.25 2.25 0 0 0 2.25-2.25V15M12 9l-3 3m0 0 3 3m-3-3h12.75"
              />
            </svg>
          </div>
          <h1>Add Music to "{{ playlistName }}"</h1>
        </div>

        <div
          class="music-upload"
          @dragover.prevent
          @drop.prevent="handleDrop"
          @click="triggerFileInput"
        >
          <p>Drag & drop your MP3 files here, or click to browse</p>
          <input
            ref="fileInput"
            type="file"
            accept=".mp3"
            multiple
            @change="handleFileChange"
            style="display: none"
          />
        </div>

        <transition-group v-if="musicFiles.length" name="fade" tag="div" class="music-stack">
          <div v-for="(file, index) in musicFiles" :key="file.name + index" class="music-item">
            <div class="music-info">
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
                  d="m9 9 10.5-3m0 6.553v3.75a2.25 2.25 0 0 1-1.632 2.163l-1.32.377a1.803 1.803 0 1 1-.99-3.467l2.31-.66a2.25 2.25 0 0 0 1.632-2.163Zm0 0V2.25L9 5.25v10.303m0 0v3.75a2.25 2.25 0 0 1-1.632 2.163l-1.32.377a1.803 1.803 0 0 1-.99-3.467l2.31-.66A2.25 2.25 0 0 0 9 15.553Z"
                />
              </svg>
              {{ file.name }}
            </div>
            <div class="music-delete" @click="removeFile(index)">
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
                  d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0"
                />
              </svg>
            </div>
          </div>
        </transition-group>

        <button class="upload-btn" @click="submitFiles" :disabled="!musicFiles.length">
          Upload Music
        </button>
      </div>
    </div>

    <div v-if="showExitModal" class="modal-overlay" @click.self="showExitModal = false">
      <div class="modal-content">
        <h2>Are you sure?</h2>
        <p>If you exit now, your uploaded music files will not be saved.</p>
        <div class="modal-actions">
          <button class="modal-btn cancel" @click="showExitModal = false">Cancel</button>
          <button class="modal-btn exit" @click="confirmExit">Exit Anyway</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI, postToAPI } from '@/utils/api.js'
import { v4 as uuidv4 } from 'uuid'
import { API_BASE_URL } from '@/utils/variables.js'

const { username, id } = useRoute().params

const isOwner = ref(false)
const fileInput = ref(null)
const musicFiles = ref([])
const showExitModal = ref(false)
const router = useRouter()

const playlistName = ref('')

const userStore = useUserStore()
const currentUser = userStore.userData?.username

onMounted(async () => {
  const url = `${API_BASE_URL}/api/playlists/${username}/${id}`
  const playlistInfo = await fetchAPI(url)
  isOwner.value = playlistInfo.owner === currentUser

  playlistName.value = playlistInfo.name

  let isSharedWith = false
  let canEdit = false

  for (const userSharedWith of playlistInfo.sharedWith) {
    if (userSharedWith.username === currentUser) {
      isSharedWith = true
      canEdit = userSharedWith.canEdit
      break
    }
  }

  const canAccess = isOwner.value || (isSharedWith && canEdit)

  if (!canAccess) {
    await router.push('/dashboard')
  }
})

function triggerFileInput() {
  fileInput.value.click()
}

function handleFileChange(event) {
  const files = Array.from(event.target.files)
  musicFiles.value.push(...files)
}

function handleDrop(event) {
  const files = Array.from(event.dataTransfer.files).filter((f) => f.type === 'audio/mpeg')
  musicFiles.value.push(...files)
}

function removeFile(index) {
  musicFiles.value.splice(index, 1)
}

function submitFiles() {
  const formData = new FormData();

  formData.append('playlist_uuid', id);
  formData.append('playlist_name', playlistName.value);
  const uuids = [];

  for (const file of musicFiles.value) {
    const uuid = uuidv4();
    uuids.push(uuid);
    formData.append('mp3s', file);
  }

  formData.append('uuids', JSON.stringify(uuids));

  const url = `${API_BASE_URL}/api/playlists/${username}/${id}/add`;
  postToAPI(url, formData, false)
    .then(response => {
      console.log('Files uploaded:', response);
      router.push(`/playlist/${username}/${id}`);
    })
    .catch(error => {
      console.error('Upload error:', error);
    });
}

function attemptBack() {
  if (musicFiles.value.length) {
    showExitModal.value = true
  } else {
    router.back()
  }
}

function confirmExit() {
  showExitModal.value = false
  router.back()
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&family=Libertinus+Math&display=swap');
.add-music-page{background:#1e1e1e;color:#f0f0f0;min-height:100vh;}
.back-arrow{align-items:center;color:#fff;cursor:pointer;display:flex;margin-bottom:1rem;}
.back-arrow:hover{color:#f44336;}
.back-arrow svg{height:24px;width:24px;}
.container{background:rgba(255,255,255,0.05);border-radius:10px;margin:2rem auto;max-width:600px;padding:2rem;}
.title-row{margin-bottom:1.5rem;text-align:center;}
.title-row h1{font-family:'Libertinus Math',serif;font-size:2rem;}
.music-upload{border:2px dashed #666;border-radius:8px;cursor:pointer;margin-bottom:1rem;padding:2rem;text-align:center;transition:background 0.3s;}
.music-upload:hover{background:rgba(255,255,255,0.1);}
.music-upload p{color:#aaa;}
.music-item{align-items:center;background:#333;border-radius:6px;display:flex;gap:1rem;justify-content:space-between;margin-bottom:0.5rem;padding:0.75rem 1rem;}
.music-info{align-items:center;display:flex;gap:0.5rem;}
.music-item svg{flex-shrink:0;height:20px;width:20px;}
.music-delete{cursor:pointer;transition:transform 0.2s;}
.music-delete:hover{transform:scale(1.2);}
.upload-btn{background:#333;border:none;border-radius:6px;color:#fff;cursor:pointer;display:block;font-size:1rem;margin:2rem auto 0 auto;padding:0.75rem 2rem;transition:background 0.3s;}
.upload-btn:disabled{background:#555;cursor:not-allowed;}
.upload-btn:hover:not(:disabled){background:#444;}
.modal-overlay{align-items:center;background:rgba(0,0,0,0.7);display:flex;height:100vh;justify-content:center;left:0;position:fixed;top:0;width:100vw;z-index:1000;}
.modal-content{background:#2a2a2a;border:2px solid #f44336;border-radius:8px;max-width:400px;padding:2rem;text-align:center;width:90%;}
.modal-content h2{font-size:1.5rem;margin-bottom:1rem;}
.modal-content p{color:#ccc;margin-bottom:2rem;}
.modal-actions{display:flex;gap:1rem;justify-content:center;}
.modal-btn{background:#555;border:none;border-radius:6px;color:#fff;cursor:pointer;font-size:1rem;padding:0.75rem 1.5rem;transition:background 0.3s;}
.modal-btn.cancel:hover{background:#666;}
.modal-btn.exit{background:#d9534f;}
.modal-btn.exit:hover{background:#c9302c;}
.navbar{background:#000;padding:1rem 0;}
.nav-content{align-items:center;display:flex;justify-content:space-between;margin:0 auto;max-width:1200px;padding:0 1rem;}
.nav-links{display:flex;gap:2rem;}
.nav-link{color:#fff;font-weight:500;text-decoration:none;transition:color 0.3s;}
.nav-link:hover{color:#ccc;}
.site-name{color:#fff;font-family:'Libertinus Math',serif;font-size:1.8rem;text-decoration:none;}
.fade-enter-active,.fade-leave-active{transition:opacity 0.5s;}
.fade-enter-from,.fade-leave-to{opacity:0;}
.fade-enter-to,.fade-leave-from{opacity:1;}
</style>

