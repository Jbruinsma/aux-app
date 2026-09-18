<template>
  <div class="settings-page">
    <!-- Nav bar -->
    <nav class="navbar">
      <div class="nav-content">
        <router-link to="/" class="site-name">Unchained</router-link>
        <div class="nav-links">
          <router-link to="/dashboard" class="nav-link">Playlists</router-link>
          <router-link :to="{ name: 'Profile', params: { username: username } }" class="nav-link">Profile</router-link>
          <router-link :to="{ name: 'Settings', params: { username: username } }" class="nav-link">Settings</router-link>
        </div>
      </div>
    </nav>

    <div class="container settings-layout">
      <div v-if="user" class="profile-info">
        <label
          class="profile-pic-upload"
          :class="{ 'drag-active': dragActive }"
          :style="{ backgroundImage: `url(${resolveCoverURL(user.profilePicture)})` }"
          @dragover.prevent="onDragOver"
          @dragleave.prevent="onDragLeave"
          @drop.prevent="onDrop"
        >
          <div class="overlay">
            <span class="overlay-text">...</span>
          </div>
          <input
            type="file"
            @change="onFileSelected"
            accept="image/*"
          />
        </label>
        <div class="username">@{{ user.username }}</div>
      </div>

      <div class="settings-content">
        <h2>Account Settings</h2>

        <div class="settings-option">
          <div class="option-header" @click="toggleUsername">Change Username</div>
          <div v-if="showUsername" class="option-content" @click.stop>
            <div class="at-input">
              <span>@</span>
              <input
                type="text"
                placeholder="New username"
                v-model="newUsername"
                maxlength="20"
              />
            </div>
            <small class="char-count">{{ newUsername.length }} / 20</small>
            <p v-if="showUsernameErrorMessage" class="username-error">{{ usernameErrorMessage }}</p>
            <p v-if="showUsernameSuccessMessage" class="username-success">{{ usernameSuccessMessage }}</p>
            <button @click="confirmAction('username')">Save</button>
          </div>
        </div>

        <div class="settings-option">
          <div class="option-header" @click="togglePassword">Change Password</div>
          <div v-if="showPassword" class="option-content" @click.stop>
            <input type="password" minlength="8" maxlength="64" placeholder="Old password" v-model="oldPassword" />
            <input type="password" minlength="8" maxlength="64" placeholder="New password" v-model="newPassword"/>
            <input type="password" minlength="8" maxlength="64" placeholder="Confirm new password" v-model="confirmNewPassword" />
            <p v-if="showPasswordErrorMessage" class="username-error">{{ passwordErrorMessage }}</p>
            <p v-if="showPasswordSuccessMessage" class="username-success">{{ passwordSuccessMessage }}</p>
            <button @click="confirmAction('password')">Update Password</button>
          </div>
        </div>

        <button class="logout-button" @click="confirmLogout = true" >Log Out</button>
        <button class="delete-button" @click="confirmDelete = true">Delete Account</button>
      </div>
    </div>

    <div v-if="showModal" class="modal-overlay">
      <div class="modal-content">
        <p>Are you sure you want to update your {{ actionType }}?</p>
        <div class="modal-buttons">
          <button @click="performUpdate">Confirm</button>
          <button @click="showModal = false">Cancel</button>
        </div>
      </div>
    </div>

    <div v-if="confirmDelete" class="modal-overlay">
      <div class="modal-content">
        <p>Are you sure you want to delete your account? This action cannot be undone.</p>
        <div class="modal-buttons">
          <button @click="deleteAccount">Delete</button>
          <button @click="confirmDelete = false">Cancel</button>
        </div>
      </div>
    </div>

    <div v-if="confirmLogout" class="modal-overlay">
      <div class="modal-content">
        <p>Are you sure you want to logout?</p>
        <div class="modal-buttons">
          <button @click="logoutOfAccount">Logout</button>
          <button @click="confirmLogout = false">Cancel</button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { fetchAPI, postToAPI } from '@/utils/api.js'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user.js'
import { resolveCoverURL } from '@/utils/display.js'
import router from '@/router/index.js'
import { API_BASE_URL } from '@/utils/variables.js'


const user = ref(null)
const showUsername = ref(false)
const showPassword = ref(false)
const showModal = ref(false)
const confirmDelete = ref(false)
const confirmLogout = ref(false)
const actionType = ref('')
const dragActive = ref(false)


const showUsernameErrorMessage = ref(false)
const usernameErrorMessage = ref('')
const showUsernameSuccessMessage = ref(false)
const usernameSuccessMessage = ref('')
const showPasswordErrorMessage = ref(false)
const passwordErrorMessage = ref('')
const showPasswordSuccessMessage = ref(false)
const passwordSuccessMessage = ref('')


const newUsername = ref('')
const oldPassword = ref('')
const newPassword = ref('')
const confirmNewPassword = ref('')


const userStore = useUserStore()
let currentUser = userStore.userData?.username
let username = useRoute().params.username

if (username !== currentUser) { useRouter().back() }

onMounted(async () => {
  const url = `${API_BASE_URL}/api/users/profile/${username}`
  user.value = await fetchAPI(url)
})

function toggleUsername() {
  showUsername.value = !showUsername.value
  if (showUsername.value) showPassword.value = false
}
function togglePassword() {
  showPassword.value = !showPassword.value
  if (showPassword.value) showUsername.value = false
}

function confirmAction(type) {
  console.log(`Confirming ${type}...`)
  actionType.value = type
  showModal.value = true
}

async function performUpdate() {
  if (actionType.value === 'username') {
    if (newUsername.value === currentUser) { toggleUsernameErrorMessage("You cannot change your username to your current username.") }
    else if (newUsername.value.length < 3 || newUsername.value.length > 20 || newUsername.value.includes(' ')) { toggleUsernameErrorMessage("Username must be between 3 and 20 characters, and cannot contain spaces.") }
    else if (await fetchAPI(`${API_BASE_URL}/api/users/check-username/${newUsername.value}`) === true){
      toggleUsernameErrorMessage("Username is already taken.")
    } else {
      const data = await postToAPI(`${API_BASE_URL}/api/users/${currentUser}/update-username/${newUsername.value}`, null, true)
      if ('error' in data){ toggleUsernameErrorMessage(data.error) }
      else { toggleUsernameSuccessMessage( `Your username has been successfully updated to @${newUsername.value}.`) }
      user.value.username = newUsername.value
      currentUser = newUsername.value
      userStore.updateUsername(newUsername.value)
      username = newUsername.value
      await router.replace({ name: 'Settings', params: { username: newUsername.value } })
      newUsername.value = ''
    }
  } else if (actionType.value === 'password') {
    if (newPassword.value !== confirmNewPassword.value) { togglePasswordErrorMessage("Passwords do not match.") }
    else if (newPassword.value.length < 8 || newPassword.value.length > 64) { togglePasswordErrorMessage("Password must be between 8 and 64 characters.") }
    else if (/^\s+$/.test(newPassword.value)) { togglePasswordErrorMessage("Password cannot be only spaces.") }
    else {
      const data = await postToAPI(`${API_BASE_URL}/api/users/${currentUser}/update-password`, {'old_password': oldPassword.value, 'new_password': newPassword.value}, true)
      if ('error' in data){ togglePasswordErrorMessage(data.error) }
      else { togglePasswordSuccessMessage("Your password has been successfully updated.") }
      oldPassword.value = ''
      newPassword.value = ''
      confirmNewPassword.value = ''
      togglePassword()
    }

  }

  showModal.value = false
}

async function deleteAccount() {
  const currentUser = userStore.userData?.username
  await fetchAPI(`${API_BASE_URL}/api/users/${currentUser}/delete`)
  userStore.logout()
  await router.push({ name: 'Home' })
}

async function logoutOfAccount() {
  userStore.logout()
  await router.push({ name: 'Home' })
}

function handleProfilePicChange(event) {
  const file = event.target.files[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (e) => {
    user.value.profilePicture = e.target.result
  }
  reader.readAsDataURL(file)
}

function onDragOver() {
  dragActive.value = true
}
function onDragLeave() {
  dragActive.value = false
}
function onDrop(event) {
  dragActive.value = false
  const file = event.dataTransfer.files[0]
  if (file) uploadProfilePic(file)
}

function onFileSelected(event) {
  const file = event.target.files[0]
  if (file) uploadProfilePic(file)
}

async function uploadProfilePic(file) {
  const formData = new FormData();
  formData.append("profile_picture", file);

  try {
    const response = await fetch(`${API_BASE_URL}/api/users/${username}/update/profile-picture`, {
      method: 'POST',
      body: formData
    });

    const result = await response.json();

    if (result.profile_picture_url) {
      user.value.profilePicture = `http://127.0.0.1:5000${result.profile_picture_url}`;
    }
  } catch (err) {
    console.error("Upload failed:", err);
  }
}

function toggleUsernameErrorMessage(message){
  showUsernameErrorMessage.value = true
  usernameErrorMessage.value = message
  if (showUsernameSuccessMessage.value === true) {
    showUsernameSuccessMessage.value = false
    usernameSuccessMessage.value = ''
  }

}

function toggleUsernameSuccessMessage(message){
  showUsernameSuccessMessage.value = true
  usernameSuccessMessage.value = message
  if (showUsernameErrorMessage.value === true) {
    showUsernameErrorMessage.value = false
    usernameErrorMessage.value = ''
  }
}

function togglePasswordErrorMessage(message){
  showPasswordErrorMessage.value = true
  passwordErrorMessage.value = message
  if (showPasswordSuccessMessage.value === true) {
    showPasswordSuccessMessage.value = false
    passwordSuccessMessage.value = ''
  }
}

function togglePasswordSuccessMessage(message){
  showPasswordSuccessMessage.value = true
  passwordSuccessMessage.value = message
  if (showPasswordErrorMessage.value === true) {
    showPasswordErrorMessage.value = false
    passwordErrorMessage.value = ''
  }
}

</script>
<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&family=Libertinus+Math&display=swap');

.at-input { align-items:center; background:#2a2a2a; border-radius:8px; display:flex; overflow:hidden; width:100%; }
.at-input input { background:transparent; border:none; color:#f0f0f0; flex:1; padding:0.75rem; }
.at-input input:focus { outline:2px solid #666; }
.at-input span { color:#aaa; font-size:1.2rem; padding:0 0.75rem; }
.char-count { color:#aaa; font-size:0.9rem; text-align:right; }
.container.settings-layout { align-items:flex-start; display:flex; gap:3rem; margin:2rem auto; max-width:1200px; padding:0 1rem; }
.delete-button { background:#ff1a1a; border:none; border-radius:5px; color:#fff; cursor:pointer; display:block; margin:1rem auto 0 auto; max-width:500px; padding:0.75rem; width:100%; }
.delete-button:hover { background:#cc0000; }
.logout-button { background:#ff4d4d; border:none; border-radius:5px; color:#fff; cursor:pointer; display:block; margin:1.5rem auto 0 auto; max-width:500px; padding:0.75rem; width:100%; }
.logout-button:hover { background:#e60000; }
.modal-buttons button { border:none; border-radius:5px; cursor:pointer; margin:0 0.5rem; padding:0.6rem 1.2rem; }
.modal-buttons button:first-child { background:#4caf50; color:#fff; }
.modal-buttons button:hover:first-child { background:#45a049; }
.modal-buttons button:last-child { background:#f44336; color:#fff; }
.modal-buttons button:hover:last-child { background:#e53935; }
.modal-content { background:#2b2b2b; border:2px solid #f44336; border-radius:8px; padding:2rem; text-align:center; }
.modal-content p { font-size:1.2rem; margin-bottom:1.5rem; }
.modal-overlay { align-items:center; background:rgba(0,0,0,0.7); bottom:0; display:flex; justify-content:center; left:0; position:fixed; right:0; top:0; z-index:1000; }
.nav-content { align-items:center; display:flex; justify-content:space-between; margin:0 auto; max-width:1200px; padding:0 1rem; }
.nav-link { color:#fff; font-weight:500; text-decoration:none; transition:color 0.3s; }
.nav-link:hover { color:#ccc; }
.nav-links { display:flex; gap:2rem; }
.navbar { background:#000; padding:1rem 0; }
.option-content { display:flex; flex-direction:column; gap:1rem; margin-top:1rem; }
.option-content button, .option-content input { border-radius:5px; box-sizing:border-box; font-size:1rem; padding:0.7rem; width:100%; }
.option-content button { background:#fff; border:none; color:#000; cursor:pointer; }
.option-content button:hover { background:#ddd; }
.option-content input { background:#333; border:none; color:#fff; }
.option-content input:focus { outline:2px solid #666; }
.option-header { font-size:1.2rem; font-weight:500; }
.overlay-text { color:#fff; font-size:2rem; }
.profile-info { align-items:center; display:flex; flex-direction:column; flex:0 0 220px; }
.profile-pic-upload { background:#444; background-position:center; background-size:cover; border-radius:50%; cursor:pointer; height:150px; margin-bottom:1rem; overflow:hidden; position:relative; width:150px; }
.profile-pic-upload input[type='file'] { display:none; }
.profile-pic-upload .overlay { align-items:center; background:rgba(0,0,0,0.5); display:flex; height:100%; justify-content:center; left:0; opacity:1; position:absolute; top:0; transition:opacity 0.3s; width:100%; }
.profile-pic-upload:hover .overlay, .profile-pic-upload.drag-active .overlay { opacity:1; }
.settings-content { flex:1; }
.settings-content h2 { border-bottom:2px solid rgba(255,255,255,0.2); font-size:2rem; margin-bottom:1.5rem; padding-bottom:0.5rem; }
.settings-option { background:rgba(255,255,255,0.05); border-radius:8px; cursor:pointer; margin:0 auto 1rem auto; max-width:500px; padding:1rem; transition:background 0.3s; }
.settings-option:hover { background:rgba(255,255,255,0.1); }
.settings-page { background:#1e1e1e; color:#f0f0f0; min-height:100vh; }
.site-name { color:#fff; font-family:'Libertinus Math', serif; font-size:1.8rem; text-decoration:none; }
.username { color:#ddd; font-family:'Libertinus Math', serif; font-size:1.8rem; }
.username-error {color: #ff4d4d; font-size: 0.9rem; margin-top: -0.5rem; text-align: center; width: 100%; }
.username-success {color: #34b233; font-size: 0.9rem; margin-top: -0.5rem; text-align: center; width: 100%; }
</style>
