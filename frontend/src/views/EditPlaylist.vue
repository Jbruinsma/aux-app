<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="stage">
      <p v-if="loadError" class="error" role="alert">{{ loadError }}</p>
      <p v-else-if="!original" class="meta">Loading playlist…</p>

      <div v-else class="create-card split">
        <StepBanner ref="banner" class="banner" alt :finishing="finishing" />

        <div class="cover">
          <span class="frame">
            <label
              for="cover-input"
              class="drop filled"
              :class="{ over: dragOver }"
              @dragover.prevent="dragOver = true"
              @dragleave="dragOver = false"
              @drop.prevent="onDropCover"
            >
              <img v-if="coverSrc" :key="coverSrc" :src="coverSrc" alt="Playlist cover" />
              <span v-else class="drop-hint">
                <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
                  <rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="9" cy="9" r="2" /><path d="m21 15-3.09-3.09a2 2 0 0 0-2.82 0L6 21" />
                </svg>
                Drop a cover or click to choose
              </span>
            </label>
          </span>
          <input
            id="cover-input"
            ref="coverInput"
            class="visually-hidden"
            type="file"
            accept="image/jpeg,image/png"
            aria-describedby="cover-hint"
            @change="onPickCover($event.target.files[0])"
          />
          <button type="button" class="btn text" @click="coverInput.click()">Replace cover</button>
        </div>

        <form class="step side" @submit.prevent="save">
          <h1>Edit playlist</h1>
          <p id="cover-hint" class="lead">
            A new cover must be a JPEG or PNG, at least 256 × 256 pixels and up to 5MB.
          </p>

          <label class="label" for="playlist-name">Name</label>
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

          <label class="check">
            <input v-model="isPublic" type="checkbox" aria-describedby="public-hint" />
            Make this playlist public
          </label>
          <p id="public-hint" class="hint">
            {{ isPublic ? 'Anyone on Aux can find and play it.' : 'Only you can see it.' }}
          </p>

          <p v-if="error" class="error" role="alert">{{ error }}</p>

          <div class="actions">
            <button type="submit" class="btn primary" :disabled="!dirty || !name.trim() || saving">
              {{ saving ? 'Saving…' : 'Save changes' }}
            </button>
            <router-link :to="playlistRoute" class="btn text">Cancel</router-link>
          </div>
        </form>
      </div>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import StepBanner from '@/components/StepBanner.vue'
import { request } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'
import { resolveCoverURL } from '@/utils/display.js'
import { fetchPlaylist } from '@/utils/playlist.js'
import { PHOTO_ERRORS, photoProblem } from '@/utils/photo.js'
import { animationsDone } from '@/utils/motion.js'
import { useUserStore } from '@/stores/user.js'

// Same limit as the backend's PlaylistDetailsUpdate
const NAME_MAX = 36

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { username, id } = route.params
const playlistRoute = { name: 'Playlist', params: { username, id } }

const original = ref(null)
const loadError = ref('')
const banner = ref(null)
const finishing = ref(false)

const name = ref('')
const isPublic = ref(false)
const coverInput = ref(null)
const coverFile = ref(null)
const coverPreview = ref('')
const dragOver = ref(false)
const saving = ref(false)
const error = ref('')

const coverSrc = computed(() => coverPreview.value
  || (original.value?.playlistCoverUrl ? resolveCoverURL(original.value.playlistCoverUrl) : ''))
const nameChanged = computed(() => name.value.trim() !== original.value?.playlistName)
const publicChanged = computed(() => isPublic.value !== original.value?.isPublic)
const dirty = computed(() => Boolean(original.value) && (nameChanged.value || publicChanged.value || Boolean(coverFile.value)))

onMounted(async () => {
  if (!userStore.loggedIn) {
    await router.replace({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const playlist = await fetchPlaylist(username, id)
    // Only the owner can edit (the endpoint 404s for anyone else), so send everyone else to the playlist itself
    if (playlist.playlistOwner.userId !== userStore.userData.userId) {
      await router.replace(playlistRoute)
      return
    }
    original.value = playlist
    name.value = playlist.playlistName
    isPublic.value = playlist.isPublic
  } catch (err) {
    loadError.value = err.code === 'PLAYLIST_NOT_FOUND'
      ? "This playlist doesn't exist or isn't yours."
      : "We couldn't load this playlist. Check that the server is running, then refresh the page."
  }
})

onBeforeUnmount(() => URL.revokeObjectURL(coverPreview.value))

// ponytail: native confirm; swap for a styled dialog if the browser prompt looks out of place
onBeforeRouteLeave(() => {
  if (!dirty.value || finishing.value) return true
  return window.confirm('Leave without saving? Your changes to this playlist will be lost.')
})

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

// Only changed fields are sent; the backend leaves missing ones as they are
async function save() {
  if (!dirty.value || saving.value) return
  error.value = ''
  saving.value = true
  try {
    const form = new FormData()
    if (nameChanged.value) form.append('playlistName', name.value.trim())
    if (publicChanged.value) form.append('isPublic', isPublic.value)
    if (coverFile.value) form.append('playlistCover', coverFile.value)
    await request('PUT', `${API_BASE_URL}/api/playlists/${encodeURIComponent(id)}`, form)
    finishing.value = true
    await nextTick()
    await animationsDone(banner.value.$el)
    await router.push(playlistRoute)
  } catch (err) {
    // A 401 means the session ended
    if (err.status === 401) {
      userStore.logout()
      await router.replace({ name: 'Login' })
      return
    }
    if (PHOTO_ERRORS[err.code]) error.value = PHOTO_ERRORS[err.code]
    else if (err.code === 'INVALID_FIELD') error.value = `Give your playlist a name of 1 to ${NAME_MAX} characters.`
    else if (err.code === 'PLAYLIST_NOT_FOUND') error.value = "This playlist no longer exists or isn't yours."
    else if (err.code === 'RATE_LIMITED') error.value = "You're saving too quickly. Wait a minute, then try again."
    else error.value = "We couldn't save your changes. Check that the server is running, then try again."
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.stage { align-items: center; display: flex; flex-direction: column; padding: 56px var(--space-6) var(--space-8); }
.meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }

.create-card { align-items: center; animation: card-in var(--dur-med) var(--ease-pop) both; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); display: grid; gap: var(--space-8); grid-template-columns: 252px minmax(0, 1fr); max-width: 720px; padding: var(--space-8) var(--space-6); width: 100%; }
.banner { grid-column: 1 / -1; margin: calc(-1 * var(--space-8)) calc(-1 * var(--space-6)) 0; }

.step { display: flex; flex-direction: column; gap: var(--space-2); }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-2); }
.lead { color: var(--ink-muted); margin: 0 0 var(--space-4); }
.label { font: 600 14px/20px var(--font-sans); }
.input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px 12px; }
.hint { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.hint + .check { margin-top: var(--space-3); }
.check { align-items: center; cursor: pointer; display: flex; font: 600 14px/20px var(--font-sans); gap: var(--space-2); }
.check input { accent-color: var(--primary); height: 16px; margin: 0; width: 16px; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.actions { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-3); margin-top: var(--space-4); }
.icon { fill: none; flex: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; }

.cover { align-items: center; display: flex; flex-direction: column; gap: var(--space-2); margin-top: -88px; position: relative; z-index: 1; }
.frame { background: var(--surface); border-radius: var(--radius-md); display: block; padding: 6px; }
.drop { background: var(--surface-alt); border: 2px dashed var(--line-strong); border-radius: var(--radius-sm); cursor: pointer; display: grid; height: 240px; overflow: hidden; place-items: center; transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease); width: 240px; }
.drop:hover, .drop.over { background: var(--purple-soft); border-color: var(--primary); }
.drop.filled { border-color: var(--line); border-style: solid; }
.drop.filled.over { border-color: var(--primary); border-style: dashed; }
.cover:has(input:focus-visible) .drop { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.drop img { animation: cover-pop var(--dur-med) var(--ease-pop) both; height: 100%; object-fit: cover; width: 100%; }
.drop-hint { align-items: center; color: var(--ink-muted); display: flex; flex-direction: column; font: 500 14px/20px var(--font-sans); gap: var(--space-2); padding: var(--space-6); text-align: center; }
.drop-hint .icon { height: 32px; stroke-width: 1.5; width: 32px; }
.visually-hidden { clip-path: inset(50%); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

@keyframes cover-pop {
  from { opacity: 0; transform: scale(1.04); }
}

@media (max-width: 720px) {
  .stage { padding: var(--space-6) var(--space-4); }
  .create-card { grid-template-columns: minmax(0, 1fr); padding: var(--space-6) var(--space-4); }
  .banner { margin: calc(-1 * var(--space-6)) calc(-1 * var(--space-4)) 0; }
  .drop { height: 200px; width: 200px; }
}
</style>
