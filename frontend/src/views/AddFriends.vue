<template>
  <div class="add-friends-page">
    <nav class="navbar">
      <div class="nav-content">
        <router-link to="/" class="site-name">Unchained</router-link>
        <div class="nav-links">
          <router-link to="/dashboard" class="nav-link">Playlists</router-link>
          <router-link :to="{ name: 'Settings', params: { username: currentUser } }" class="nav-link">Settings</router-link>
        </div>
      </div>
    </nav>

    <div class="container">
      <div class="upload-section">
        <div class="title-row">
          <div class="back-arrow" @click="attemptBack">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none"
                 viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round"
                    d="M15.75 9V5.25A2.25 2.25 0 0 0 13.5 3h-6a2.25 2.25 0 0 0-2.25 2.25v13.5A2.25 2.25 0 0 0 7.5 21h6a2.25 2.25 0 0 0 2.25-2.25V15M12 9l-3 3m0 0 3 3m-3-3h12.75" />
            </svg>
          </div>
          <div class="title-row">
          <h1>Add Friends to "{{playlistName}}"</h1>
          <p>Even if "{{playlistName}}" is not public, these friends will still have access.</p>
            </div>
        </div>

        <div class="friend-input">
          <span class="at-symbol">@</span>
          <input
            type="text"
            v-model="newFriend"
            placeholder="username"
            @keyup.enter="addFriend"
          />
          <button class="add-btn" @click="addFriend">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none"
                 viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round"
                    d="m12.75 15 3-3m0 0-3-3m3 3h-7.5M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
            </svg>
          </button>
        </div>

        <div v-if="usernameError" class="username-error">{{ usernameError }}</div>


        <div v-if="friendList.length" class="friend-stack">
          <div
            v-for="(friend, index) in friendList"
            :key="friend.username + index"
            class="friend-item"
          >
            <div class="friend-info">
              <svg v-if="friend.isNew" xmlns="http://www.w3.org/2000/svg" fill="none"
                   viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round"
                      d="M18 7.5v3m0 0v3m0-3h3m-3 0h-3m-2.25-4.125a3.375 3.375 0 1 1-6.75 0 3.375 3.375 0 0 1 6.75 0ZM3 19.235v-.11a6.375 6.375 0 0 1 12.75 0v.109A12.318 12.318 0 0 1 9.374 21c-2.331 0-4.512-.645-6.374-1.766Z" />
              </svg>

              <svg v-else xmlns="http://www.w3.org/2000/svg" fill="none"
                   viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round"
                      d="M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.584-7.499-1.632Z" />
              </svg>

              {{ friend.username }}
            </div>
            <div class="toggle-permission">
              <label class="toggle-label">
                <input type="checkbox" v-model="friend.canEdit"
                       @change="togglePermission( friend.username, friend.canEdit)"
                />
                <span class="slider"></span>
                <span class="toggle-text">{{ friend.canEdit ? 'Edit' : 'Read-Only' }}</span>
              </label>
            </div>
            <div class="friend-delete" @click="removeFriend(index)">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none"
                   viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round"
                      d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
              </svg>
            </div>
          </div>
        </div>

        <button
          class="upload-btn"
          @click="submitFriends"
        >
          Save
        </button>
      </div>
    </div>

    <div v-if="showExitModal" class="modal-overlay" @click.self="showExitModal = false">
      <div class="modal-content">
        <h2>Are you sure?</h2>
        <p>If you exit now, your added friends will not be saved.</p>
        <div class="modal-actions">
          <button class="modal-btn cancel" @click="showExitModal = false">Cancel</button>
          <button class="modal-btn exit" @click="confirmExit">Exit Anyway</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>

import { API_BASE_URL } from '@/utils/variables.js'

const testUsername = 'test_user'
const testPlaylistUUID = 'test_playlist_1'

import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchAPI, postToAPI } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'

const userStore = useUserStore()
const currentUser = ref(userStore.userData?.username)

const router = useRouter()
const newFriend = ref('')
const friendList = ref([])
const showExitModal = ref(false)
const usernameError = ref('')

const playlistName = ref(null)
const owner = ref('')
const { username, id } = useRoute().params



onMounted(async () => {
  const url = `${API_BASE_URL}/api/playlists/${username}/${id}`
  const playlistInfo = await fetchAPI(url)
  owner.value = playlistInfo.owner

  if (currentUser.value !== owner.value) {
    await router.push('/dashboard')
    return
  }

  playlistName.value = playlistInfo.name
  friendList.value = playlistInfo.sharedWith
})

async function addFriend() {
  const trimmed = newFriend.value.trim()
  if (!trimmed) return
  if (owner.value === trimmed) {
    usernameError.value = `You are the owner of this playlist.`
    return
  }

  try {
    const url = `${API_BASE_URL}/api/users/check-username/${trimmed}`
    const data = await fetchAPI(url)

    if (data.exists) {
      friendList.value.push({ username: trimmed, canEdit: false, isNew: true })
      newFriend.value = ''
      usernameError.value = ''
    } else { usernameError.value = `User @${trimmed} does not exist.` }
  } catch (err) { console.error("Error checking username:", err) }
}

function togglePermission(username, canEdit) {
  console.log(canEdit)
  console.log(`User ${username} now has permission:`, canEdit ? "Edit" : "Read-Only")
}

function removeFriend(index) {
  friendList.value.splice(index, 1)
}

async function submitFriends() {
  console.log("Adding friends:", friendList.value)

  for (const friend of friendList.value) { delete friend.isNew }

  const url = `${API_BASE_URL}/api/playlists/${username}/${id}/add/friends`
  await postToAPI(url, {
    friends: friendList.value
  })
  await router.push(`/playlist/${username}/${id}`)
}

function attemptBack() {
  if (friendList.value.length) {
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

.add-btn{background:#333;border:none;border-radius:6px;color:#fff;cursor:pointer;padding:0.5rem;transition:background 0.3s;}
.add-btn:hover svg {color: #4caf50;}
.add-btn svg{height:24px;width:24px;}
.add-friends-page{background:#1e1e1e;color:#f0f0f0;min-height:100vh;}
.back-arrow{align-items:center;color:#fff;cursor:pointer;display:flex;}
.back-arrow:hover{color:#f44336;}
.back-arrow svg{height:24px;width:24px;}
.container{background:rgba(255,255,255,0.05);border-radius:10px;margin:2rem auto;max-width:600px;padding:2rem;}
.friend-delete{cursor:pointer;transition:transform 0.2s;}
.friend-delete:hover{transform:scale(1.2);}
.friend-info{align-items:center;display:flex;gap:0.5rem;}
.friend-input{display:flex;gap:0.5rem;margin:0 auto 1rem auto;max-width:400px;}
.friend-input { background:#333; }
.friend-input .at-symbol{align-items:center;border-radius:6px;color:#aaa;display:flex;font-size:1.1rem;justify-content:center;padding:0 0.75rem;}
.friend-input input{background:#333;border:none;border-radius:6px;color:#f0f0f0;flex:1;font-size:1rem;padding:0.75rem 1rem;transition:background 0.3s;}
.friend-input input:focus{background:#444;outline:none;}
.friend-item{align-items:center;background:#333;border-radius:6px;display:flex;gap:1rem;justify-content:space-between;margin-bottom:0.5rem;padding:0.75rem 1rem;}
.friend-item svg{flex-shrink:0;height:20px;width:20px;}
.modal-actions{display:flex;gap:1rem;justify-content:center;}
.modal-btn{background:#555;border:none;border-radius:6px;color:#fff;cursor:pointer;font-size:1rem;padding:0.75rem 1.5rem;transition:background 0.3s;}
.modal-btn.cancel:hover{background:#666;}
.modal-btn.exit{background:#d9534f;}
.modal-btn.exit:hover{background:#c9302c;}
.modal-content{background:#2a2a2a;border:2px solid #f44336;border-radius:8px;max-width:400px;padding:2rem;text-align:center;width:90%;}
.modal-content h2{font-size:1.5rem;margin-bottom:1rem;}
.modal-content p{color:#ccc;margin-bottom:2rem;}
.modal-overlay{align-items:center;background:rgba(0,0,0,0.7);display:flex;height:100vh;justify-content:center;left:0;position:fixed;top:0;width:100vw;z-index:1000;}
.nav-content{align-items:center;display:flex;justify-content:space-between;margin:0 auto;max-width:1200px;padding:0 1rem;}
.nav-link{color:#fff;font-weight:500;text-decoration:none;transition:color 0.3s;}
.nav-link:hover{color:#ccc;}
.nav-links{display:flex;gap:2rem;}
.navbar{background:#000;padding:1rem 0;}
.site-name{color:#fff;font-family:'Libertinus Math',serif;font-size:1.8rem;text-decoration:none;}
.title-row{display:flex;flex-direction:column;gap:0.5rem;justify-content:center;margin-bottom:1.5rem;text-align:center;}
.title-row h1{font-family:'Libertinus Math',serif;font-size:2rem;}
.title-row p{color:#aaa;font-size:0.95rem;font-weight:400;line-height:1.4;}
.toggle-permission .toggle-label{align-items:center;cursor:pointer;display:flex;gap:0.5rem;}
.toggle-permission .toggle-label input{display:none;}
.toggle-permission .slider{background:#555;border-radius:20px;height:20px;position:relative;transition:background 0.3s;width:40px;}
.toggle-permission .slider::before{background:#f0f0f0;border-radius:50%;bottom:3px;content:"";height:14px;left:3px;position:absolute;transition:transform 0.3s;width:14px;}
.toggle-permission input:checked + .slider{background:#4caf50;}
.toggle-permission input:checked + .slider::before{transform:translateX(20px);}
.toggle-permission .toggle-text{color:#f0f0f0;font-size:0.9rem;}
.upload-btn{background:#333;border:none;border-radius:6px;color:#fff;cursor:pointer;display:block;font-size:1rem;margin:2rem auto 0 auto;padding:0.75rem 2rem;transition:background 0.3s;}
.upload-btn:disabled{background:#555;cursor:not-allowed;}
.upload-btn:hover:not(:disabled){background:#444;}
.username-error { color: #f44336; font-size: 0.9rem; margin-top: 0.5rem; margin-bottom: 0.5rem; text-align: center; }
</style>
