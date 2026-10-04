<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="wrap">
      <h1>Create playlist</h1>

      <form class="create" @submit.prevent="createPlaylist">
        <div class="cover-field">
          <input
            id="cover-input"
            ref="coverInput"
            class="visually-hidden"
            type="file"
            accept="image/jpeg,image/png"
            aria-describedby="cover-hint"
            @change="onPickCover($event.target.files[0])"
          />
          <label
            for="cover-input"
            class="drop"
            :class="{ over: dragOver, filled: coverPreview }"
            @dragover.prevent="dragOver = true"
            @dragleave="dragOver = false"
            @drop.prevent="onDropCover"
          >
            <img v-if="coverPreview" :src="coverPreview" alt="Cover preview" />
            <span v-else class="drop-text">
              <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="9" cy="9" r="2" /><path d="m21 15-3.09-3.09a2 2 0 0 0-2.82 0L6 21" />
              </svg>
              Choose a cover
            </span>
          </label>
          <p id="cover-hint" class="hint">A JPEG or PNG, at least 256 × 256 pixels and under 5MB. It's cropped to a square.</p>
          <button v-if="coverFile" type="button" class="btn text change" @click="coverInput.click()">Change cover</button>
        </div>

        <div class="fields">
          <div class="field">
            <label class="row-label" for="playlist-name">Name</label>
            <input
              id="playlist-name"
              v-model="name"
              class="input"
              type="text"
              :maxlength="NAME_MAX"
              required
              autocomplete="off"
              aria-describedby="name-hint"
            />
            <p id="name-hint" class="hint">{{ name.length }} / {{ NAME_MAX }} characters</p>
          </div>

          <div class="field">
            <label class="check">
              <input v-model="isPublic" type="checkbox" aria-describedby="public-hint" />
              Make this playlist public
            </label>
            <p id="public-hint" class="hint">
              {{ isPublic ? 'Anyone on Aux can find and play it.' : 'Only you can see it.' }}
            </p>
          </div>

          <p v-if="error" class="error" role="alert">{{ error }}</p>

          <div class="form-actions">
            <button type="submit" class="btn primary" :disabled="!canCreate">
              {{ creating ? 'Creating…' : 'Create playlist' }}
            </button>
            <router-link to="/dashboard" class="btn text">Cancel</router-link>
          </div>
        </div>
      </form>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import { postToAPI } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'
import { PHOTO_ERRORS, photoProblem } from '@/utils/photo.js'
import { useUserStore } from '@/stores/user.js'

// Same limit as the backend's PlaylistCreationDetails
const NAME_MAX = 36

const router = useRouter()
const userStore = useUserStore()

const name = ref('')
const isPublic = ref(false)
const coverInput = ref(null)
const coverFile = ref(null)
const coverPreview = ref('')
const dragOver = ref(false)
const creating = ref(false)
const error = ref('')

const canCreate = computed(() => !creating.value && !!name.value.trim() && !!coverFile.value)

onMounted(() => {
  if (!userStore.loggedIn) router.replace({ name: 'Login' })
})

onBeforeUnmount(() => URL.revokeObjectURL(coverPreview.value))

function onDropCover(event) {
  dragOver.value = false
  onPickCover(event.dataTransfer.files[0])
}

async function onPickCover(file) {
  if (!file) return
  error.value = ''
  const problem = await photoProblem(file)
  if (coverInput.value) coverInput.value.value = ''
  if (problem) {
    error.value = problem
    return
  }
  URL.revokeObjectURL(coverPreview.value)
  coverFile.value = file
  coverPreview.value = URL.createObjectURL(file)
}

// The server crops and resizes the cover, so the original file is sent
async function createPlaylist() {
  if (!canCreate.value) return
  error.value = ''
  creating.value = true
  try {
    const form = new FormData()
    form.append('playlistName', name.value.trim())
    form.append('isPublic', isPublic.value)
    form.append('playlistCover', coverFile.value)
    const playlist = await postToAPI(`${API_BASE_URL}/api/playlists`, form)
    await router.push({ name: 'Playlist', params: { username: playlist.playlistOwner.username, id: playlist.playlistId } })
  } catch (err) {
    // A 401 means the session ended
    if (err.status === 401) {
      userStore.logout()
      await router.replace({ name: 'Login' })
      return
    }
    if (PHOTO_ERRORS[err.code]) error.value = PHOTO_ERRORS[err.code]
    else if (err.code === 'INVALID_FIELD') error.value = `Give your playlist a name of 1 to ${NAME_MAX} characters.`
    else if (err.code === 'RATE_LIMITED') error.value = "You're creating playlists too quickly. Wait a minute, then try again."
    else error.value = "We couldn't create your playlist. Check that the server is running, then try again."
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.wrap { padding: var(--space-8) var(--page-gutter); width: 100%; }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-6); }
.create { align-items: start; display: grid; gap: var(--space-8); grid-template-columns: 240px minmax(0, 480px); }
.cover-field { display: flex; flex-direction: column; gap: var(--space-2); }
.drop { align-items: center; aspect-ratio: 1; background: var(--surface-alt); border: 2px dashed var(--line-strong); border-radius: var(--radius-sm); cursor: pointer; display: grid; overflow: hidden; place-items: center; transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease); width: 100%; }
.drop:hover, .drop.over { background: var(--purple-soft); border-color: var(--primary); }
.drop.filled { border-color: var(--line); border-style: solid; }
.drop img { height: 100%; object-fit: cover; width: 100%; }
.drop-text { align-items: center; color: var(--ink-muted); display: flex; flex-direction: column; font: 600 14px/20px var(--font-sans); gap: var(--space-2); }
.visually-hidden { clip: rect(0 0 0 0); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }
.visually-hidden:focus-visible + .drop { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.change { align-self: flex-start; font-size: 14px; padding-left: 0; }
.fields { display: flex; flex-direction: column; gap: var(--space-4); }
.field { display: flex; flex-direction: column; gap: var(--space-2); }
.row-label { font: 600 14px/20px var(--font-sans); }
.input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px 12px; width: 100%; }
.hint { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.check { align-items: center; cursor: pointer; display: flex; font: 600 14px/20px var(--font-sans); gap: var(--space-2); }
.check input { accent-color: var(--primary); height: 16px; width: 16px; }
.form-actions { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-3); margin-top: var(--space-2); }
.icon { fill: none; height: 32px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; width: 32px; }

@media (max-width: 720px) {
  .wrap { padding-top: var(--space-6); }
  .create { gap: var(--space-6); grid-template-columns: minmax(0, 1fr); }
  .cover-field { max-width: 240px; }
}
</style>
