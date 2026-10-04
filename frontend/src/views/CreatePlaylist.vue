<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="stage">
      <div class="create-card" :class="{ split: step === 'COVER' }">
        <StepBanner ref="banner" class="banner" :alt="step === 'COVER'" :finishing="finishing" :replay-key="step" />

        <Transition name="cover-in">
          <div v-if="step === 'COVER'" class="cover">
            <span class="frame">
              <label
                for="cover-input"
                class="drop"
                :class="{ over: dragOver, filled: coverPreview }"
                @dragover.prevent="dragOver = true"
                @dragleave="dragOver = false"
                @drop.prevent="onDropCover"
              >
                <img v-if="coverPreview" :src="coverPreview" alt="Cover preview" />
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
            <button v-if="coverFile" type="button" class="btn text" @click="coverInput.click()">Choose a different cover</button>
          </div>
        </Transition>

        <div class="side">
          <form v-if="step === 'NAME'" class="step" @submit.prevent="toCoverStep">
            <p class="eyebrow">Step 1 of 2</p>
            <h1 ref="heading" tabindex="-1">Name your playlist</h1>
            <p class="lead">You can change the name and who can see it later.</p>

            <label class="label" for="playlist-name">Name</label>
            <input
              id="playlist-name"
              ref="nameEl"
              v-model="name"
              class="input"
              type="text"
              :maxlength="NAME_MAX"
              required
              autofocus
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
              <button type="submit" class="btn primary" :disabled="!name.trim()">Next</button>
              <router-link to="/dashboard" class="btn text">Cancel</router-link>
            </div>
          </form>

          <form v-else class="step" @submit.prevent="createPlaylist">
            <p ref="nameEl" class="title">
              <span class="title-text">{{ name.trim() }}</span>
              <svg class="icon" viewBox="0 0 24 24" aria-label="Name set"><path d="M20 6 9 17l-5-5" /></svg>
            </p>
            <p class="eyebrow">Step 2 of 2</p>
            <h1 ref="heading" tabindex="-1">Add a cover</h1>
            <p id="cover-hint" class="lead">A JPEG or PNG, at least 256 × 256 pixels and up to 5MB. We'll crop it to a square.</p>

            <p v-if="error" class="error" role="alert">{{ error }}</p>

            <div class="actions">
              <button type="submit" class="btn primary" :disabled="!coverFile || creating">
                {{ creating ? 'Creating…' : 'Create playlist' }}
              </button>
              <button type="button" class="btn text" :disabled="creating" @click="toNameStep">Back</button>
            </div>
          </form>
        </div>
      </div>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import StepBanner from '@/components/StepBanner.vue'
import { postToAPI } from '@/utils/api.js'
import { API_BASE_URL } from '@/utils/variables.js'
import { PHOTO_ERRORS, photoProblem } from '@/utils/photo.js'
import { animationsDone, slideFrom } from '@/utils/motion.js'
import { useUserStore } from '@/stores/user.js'

// Same limit as the backend's PlaylistCreationDetails
const NAME_MAX = 36

const router = useRouter()
const userStore = useUserStore()

// The steps only live on this page; nothing reaches the server until "Create playlist" sends everything in one request
const step = ref('NAME')
const finishing = ref(false)
const banner = ref(null)
const heading = ref(null)
const nameEl = ref(null)

const name = ref('')
const isPublic = ref(false)
const coverInput = ref(null)
const coverFile = ref(null)
const coverPreview = ref('')
const dragOver = ref(false)
const creating = ref(false)
const error = ref('')

onMounted(() => {
  if (!userStore.loggedIn) router.replace({ name: 'Login' })
})

onBeforeUnmount(() => URL.revokeObjectURL(coverPreview.value))

async function toCoverStep() {
  if (!name.value.trim()) return
  error.value = ''
  const from = nameEl.value.getBoundingClientRect()
  step.value = 'COVER'
  await nextTick()
  heading.value?.focus()
  slideFrom(nameEl.value, from, 16)
}

async function toNameStep() {
  error.value = ''
  step.value = 'NAME'
  await nextTick()
  nameEl.value?.focus()
}

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
  if (!coverFile.value || creating.value) return
  error.value = ''
  creating.value = true
  try {
    const form = new FormData()
    form.append('playlistName', name.value.trim())
    form.append('isPublic', isPublic.value)
    form.append('playlistCover', coverFile.value)
    const playlist = await postToAPI(`${API_BASE_URL}/api/playlists`, form)
    finishing.value = true
    await nextTick()
    await animationsDone(banner.value.$el)
    await router.push({ name: 'Playlist', params: { username: playlist.playlistOwner.username, id: playlist.playlistId } })
  } catch (err) {
    // A 401 means the session ended
    if (err.status === 401) {
      userStore.logout()
      await router.replace({ name: 'Login' })
      return
    }
    if (PHOTO_ERRORS[err.code]) error.value = PHOTO_ERRORS[err.code]
    else if (err.code === 'INVALID_FIELD') {
      await toNameStep()
      error.value = `Give your playlist a name of 1 to ${NAME_MAX} characters.`
    }
    else if (err.code === 'RATE_LIMITED') error.value = "You're creating playlists too quickly. Wait a minute, then try again."
    else error.value = "We couldn't create your playlist. Check that the server is running, then try again."
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.stage { align-items: center; display: flex; flex-direction: column; padding: 56px var(--space-6) var(--space-8); }

.create-card { animation: card-in var(--dur-med) var(--ease-pop) both; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); display: grid; gap: var(--space-8); grid-template-columns: minmax(0, 1fr); max-width: 720px; padding: var(--space-8) var(--space-6); width: 100%; }
.create-card.split { align-items: center; grid-template-columns: 252px minmax(0, 1fr); }
.banner { grid-column: 1 / -1; margin: calc(-1 * var(--space-8)) calc(-1 * var(--space-6)) 0; }
.side { justify-self: center; max-width: 360px; width: 100%; }
.split .side { max-width: none; }

.step { display: flex; flex-direction: column; gap: var(--space-2); }
.eyebrow { color: var(--ink-muted); font: 600 12px/16px var(--font-sans); margin: 0; }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-2); }
h1:focus { outline: none; }
.lead { color: var(--ink-muted); margin: 0 0 var(--space-4); }
.label { font: 600 14px/20px var(--font-sans); }
.input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px 12px; }
.hint { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.hint + .check { margin-top: var(--space-3); }
.check { align-items: center; cursor: pointer; display: flex; font: 600 14px/20px var(--font-sans); gap: var(--space-2); }
.check input { accent-color: var(--primary); height: 16px; margin: 0; width: 16px; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.actions { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-3); margin-top: var(--space-4); }

.title { align-items: center; color: var(--link); display: flex; font: 700 20px/28px var(--font-sans); gap: var(--space-2); margin: 0 0 var(--space-2); min-width: 0; transform-origin: left center; }
.title-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.title .icon { color: var(--success); height: 20px; width: 20px; }
.icon { fill: none; flex: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; }

.cover { align-items: center; display: flex; flex-direction: column; gap: var(--space-2); margin-top: -88px; position: relative; z-index: 1; }
.frame { background: var(--surface); border-radius: var(--radius-md); display: block; padding: 6px; }
.drop { background: var(--surface-alt); border: 2px dashed var(--line-strong); border-radius: var(--radius-sm); cursor: pointer; display: grid; height: 240px; overflow: hidden; place-items: center; transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease); width: 240px; }
.drop:hover, .drop.over { background: var(--purple-soft); border-color: var(--primary); }
.drop.filled { border-color: var(--line); border-style: solid; }
.cover:has(input:focus-visible) .drop { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.drop img { animation: cover-pop var(--dur-med) var(--ease-pop) both; height: 100%; object-fit: cover; width: 100%; }
.drop-hint { align-items: center; color: var(--ink-muted); display: flex; flex-direction: column; font: 500 14px/20px var(--font-sans); gap: var(--space-2); padding: var(--space-6); text-align: center; }
.drop-hint .icon { height: 32px; stroke-width: 1.5; width: 32px; }
.visually-hidden { clip-path: inset(50%); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

.cover-in-enter-active { transition: opacity var(--dur-med) var(--ease), transform var(--dur-med) var(--ease); }
.cover-in-enter-from { opacity: 0; transform: scale(0.96); }
@keyframes cover-pop {
  from { opacity: 0; transform: scale(1.04); }
}

@media (max-width: 720px) {
  .stage { padding: var(--space-6) var(--space-4); }
  .create-card { padding: var(--space-6) var(--space-4); }
  .banner { margin: calc(-1 * var(--space-6)) calc(-1 * var(--space-4)) 0; }
  .create-card.split { grid-template-columns: minmax(0, 1fr); }
  .drop { height: 200px; width: 200px; }
}
</style>
