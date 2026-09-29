<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main>
      <div class="page-head">
        <div class="wrap">
          <h1>Settings</h1>
          <nav class="tabs" aria-label="Settings sections">
            <router-link
              v-for="item in TABS"
              :key="item.id"
              class="tab"
              :class="{ active: tab === item.id }"
              :aria-current="tab === item.id ? 'page' : undefined"
              :to="{ name: 'Settings', params: { username, tab: item.id === 'profile' ? undefined : item.id } }"
            >{{ item.label }}</router-link>
          </nav>
        </div>
      </div>

      <div v-if="tab === 'profile'" class="wrap layout">
        <div class="main-col">
          <section aria-labelledby="picture-heading">
            <h2 id="picture-heading">Your picture</h2>
            <!-- Same 3:1 box as the banner preview, so the controls below line up with the banner's -->
            <div class="avatar-area">
              <img v-if="shownPicture" class="avatar" :src="shownPicture" alt="" />
              <div v-else class="avatar avatar-empty" aria-hidden="true">{{ initial }}</div>
            </div>

            <div class="picture-controls">
                <div class="file-row">
                  <input
                    id="photo-input"
                    ref="fileInput"
                    class="visually-hidden"
                    type="file"
                    accept="image/jpeg,image/png"
                    aria-describedby="photo-hint"
                    @change="onPick"
                  />
                  <label for="photo-input" class="btn secondary">Choose file</label>
                  <span class="file-name">{{ newPhoto ? newPhoto.name : 'No file chosen' }}</span>
                  <button v-if="newPhoto" type="button" class="btn text revert" @click="revertPhoto">Revert</button>
                </div>
                <p id="photo-hint" class="hint">
                  A JPEG or PNG, at least 256 × 256 pixels and under 5MB. It's cropped to a square and shown in a circle,
                  like the example.
                </p>
                <p v-if="photoError" class="error" role="alert">{{ photoError }}</p>
                <p v-if="photoSaved" class="success" role="status">Your new picture is saved.</p>
                <button type="button" class="btn primary" :disabled="!newPhoto || uploading" @click="uploadPhoto">
                  {{ uploading ? 'Uploading…' : 'Upload picture' }}
                </button>
            </div>
          </section>

          <section aria-labelledby="banner-heading">
            <h2 id="banner-heading">Your banner</h2>
            <div
              v-if="newBanner"
              ref="bannerFrame"
              class="banner-frame editing"
              :class="{ dragging: bannerDrag }"
              tabindex="0"
              role="group"
              aria-label="Banner position"
              aria-describedby="banner-adjust-hint"
              @pointerdown="startBannerDrag"
              @pointermove="moveBannerDrag"
              @pointerup="endBannerDrag"
              @pointercancel="endBannerDrag"
              @keydown="nudgeBanner"
            >
              <img :src="bannerPreviewUrl" :style="bannerStyle" alt="" draggable="false" />
            </div>
            <div v-else class="banner-frame">
              <img v-if="savedBanner" class="banner-saved" :src="savedBanner" alt="Your current banner" />
              <p v-else class="banner-empty">No banner yet</p>
            </div>

            <div v-if="newBanner" class="adjust">
              <p id="banner-adjust-hint" class="hint">Drag to reposition, or use the arrow keys. Zoom with the slider or + and −.</p>
              <div class="zoom-row">
                <label for="banner-zoom" class="zoom-label">Zoom</label>
                <input
                  id="banner-zoom"
                  class="zoom"
                  type="range"
                  :min="MIN_ZOOM"
                  :max="MAX_ZOOM"
                  step="0.01"
                  :value="bannerCrop.zoom"
                  @input="setBannerZoom(Number($event.target.value))"
                />
                <button type="button" class="btn text" @click="resetBannerCrop">Reset</button>
              </div>
            </div>
            <div class="picture-controls banner-controls">
              <div class="file-row">
                <input
                  id="banner-input"
                  ref="bannerInput"
                  class="visually-hidden"
                  type="file"
                  accept="image/jpeg,image/png"
                  aria-describedby="banner-hint"
                  @change="onPickBanner"
                />
                <label for="banner-input" class="btn secondary">Choose file</label>
                <span class="file-name">{{ newBanner ? newBanner.name : 'No file chosen' }}</span>
                <button v-if="newBanner" type="button" class="btn text revert" @click="revertBanner">Revert</button>
              </div>
              <p id="banner-hint" class="hint">
                A wide JPEG or PNG, at least 600 × 200 pixels and under 5MB. It's cropped to 3:1, so 1500 × 500 looks
                sharpest. See how it looks on your profile card.
              </p>
              <p v-if="bannerError" class="error" role="alert">{{ bannerError }}</p>
              <p v-if="bannerSaved" class="success" role="status">Your new banner is saved.</p>
              <button type="button" class="btn primary" :disabled="!newBanner || bannerUploading" @click="uploadBanner">
                {{ bannerUploading ? 'Uploading…' : 'Upload banner' }}
              </button>
            </div>
          </section>

          <section class="wide" aria-labelledby="creative-heading">
            <h2 id="creative-heading">Get creative!</h2>
            <form class="details" @submit.prevent="saveDetails">
              <div class="field">
                <label class="row-label" for="display-name">Display name</label>
                <input
                  id="display-name"
                  v-model="details.displayName"
                  class="input"
                  type="text"
                  :maxlength="NAME_MAX"
                  :placeholder="username"
                  :aria-invalid="nameInvalid ? 'true' : 'false'"
                  aria-describedby="name-error"
                />
                <p v-if="nameInvalid" id="name-error" class="error">
                  Use {{ NAME_MIN }} to {{ NAME_MAX }} characters, or leave it empty.
                </p>
              </div>

              <div class="field">
                <label class="row-label" for="country">Country</label>
                <div class="select-wrap">
                  <select id="country" v-model="details.country" class="input select">
                    <option value="">None</option>
                    <option v-for="country in COUNTRIES" :key="country.code" :value="country.code">{{ country.name }}</option>
                  </select>
                </div>
              </div>

              <div class="field">
                <label class="row-label" for="website">Website</label>
                <input
                  id="website"
                  v-model="details.website"
                  class="input"
                  type="url"
                  placeholder="https://"
                  :aria-invalid="websiteInvalid ? 'true' : 'false'"
                  aria-describedby="website-error"
                />
                <p v-if="websiteInvalid" id="website-error" class="error">
                  Enter a full link starting with http:// or https://
                </p>
              </div>

              <div class="field about-field">
                <label class="row-label" for="about">About you</label>
                <textarea
                  id="about"
                  v-model="details.about"
                  class="input textarea"
                  rows="4"
                  :maxlength="ABOUT_LIMIT"
                  aria-describedby="about-hint"
                ></textarea>
                <p id="about-hint" class="hint">
                  {{ details.about.length }} / {{ ABOUT_LIMIT }} characters, plain text only.
                </p>
              </div>

              <div class="wide">
                <button type="submit" class="btn primary" :disabled="detailsSaving || nameInvalid || websiteInvalid">
                  {{ detailsSaving ? 'Saving…' : 'Save changes' }}
                </button>
                <p v-if="detailsError" class="error status-line" role="alert">{{ detailsError }}</p>
                <p v-if="detailsSaved" class="success status-line" role="status">Your details are saved.</p>
              </div>
            </form>
          </section>

          <section aria-labelledby="links-heading">
            <h2 id="links-heading">Links to other sites</h2>
            <label class="check">
              <input v-model="warnBeforeLeaving" type="checkbox" aria-describedby="links-hint" />
              Warn me before opening a link to another site
            </label>
            <p id="links-hint" class="hint">
              Turn this back on if you chose "Save my option" by mistake. This setting is saved on this browser.
            </p>
          </section>
        </div>

        <aside class="side" aria-labelledby="preview-heading">
          <h2 id="preview-heading" class="side-title">How others see you</h2>
          <ProfileCard
            :username="username"
            :display-name="details.displayName"
            :picture-url="shownPicture"
            :banner-url="bannerPreviewUrl || savedBanner"
            :banner-style="bannerPreviewUrl ? bannerStyle : null"
            :country="countryName"
            :about="details.about"
            :website="websiteUrl"
          />
        </aside>
      </div>

      <div v-else class="wrap layout">
        <section class="main-col" aria-labelledby="apps-heading">
          <div>
            <h2 id="apps-heading">Connect your music apps</h2>
            <p class="intro">Connect Spotify so Aux can learn your taste from what you actually play.</p>
          </div>
          <ul class="apps">
            <li v-for="app in APPS" :key="app.id" class="app">
              <svg class="app-icon" viewBox="0 0 40 40" aria-hidden="true">
                <circle class="spotify" cx="20" cy="20" r="20" />
                <path d="M10 15.5c6.5-2 14-1.4 20 1.8M11 21c5.4-1.6 11.4-1 16.2 1.6M12 26.2c4.2-1.1 8.8-.7 12.5 1.2"
                      fill="none" stroke="#fff" stroke-width="2.6" stroke-linecap="round" />
              </svg>
              <div class="app-text">
                <p class="app-name">{{ app.name }}</p>
                <p class="app-desc">{{ app.description }}</p>
                <p class="app-status">Not connected</p>
              </div>
              <button type="button" class="btn secondary" disabled :aria-describedby="`${app.id}-soon`">Connect</button>
              <p :id="`${app.id}-soon`" class="visually-hidden">Connecting {{ app.name }} is coming soon.</p>
            </li>
          </ul>
          <p class="hint">Spotify connectivity coming soon.</p>
        </section>
      </div>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import ProfileCard from '@/components/ProfileCard.vue'
import { fetchAPI, request } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'
import { API_BASE_URL } from '@/utils/variables.js'
import { resolveCoverURL } from '@/utils/display.js'
import { BANNER_ERRORS, PHOTO_ERRORS, bannerProblem, photoProblem } from '@/utils/photo.js'
import { DEFAULT_CROP, MAX_ZOOM, MIN_ZOOM, bannerCropRect, bannerImageStyle, clampCrop, panCrop } from '@/utils/banner.js'
import { COUNTRIES } from '@/utils/countries.js'
import { setSkipExternalWarning, skipExternalWarning } from '@/utils/externalLinks.js'

const ABOUT_LIMIT = 200
// Same limits as the backend's ProfileDetailsUpdate
const NAME_MIN = 3
const NAME_MAX = 15

const TABS = [
  { id: 'profile', label: 'Profile' },
  { id: 'applications', label: 'Applications' },
]

// Spotify is the one listening service Aux supports; connecting needs a backend sign-in flow that doesn't exist yet
const APPS = [
  {
    id: 'spotify',
    name: 'Spotify',
    description: 'Bring in what you play on Spotify, from desktop, mobile or any other device.',
  },
]

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const username = computed(() => userStore.userData?.username ?? '')
// Anything other than a known tab shows Profile
const tab = computed(() => (TABS.some((t) => t.id === route.params.tab) ? route.params.tab : 'profile'))
const initial = computed(() => username.value.charAt(0).toUpperCase())

onMounted(() => {
  if (!userStore.loggedIn) {
    router.replace({ name: 'Login' })
    return
  }
  loadSavedProfile()
})

// A 401 means the session ended: log out and go to the login page
async function handleUnauthorized(err) {
  if (err.status !== 401) return false
  userStore.logout()
  await router.replace({ name: 'Login' })
  return true
}

/* Picture: choosing a file only previews it; nothing is sent until "Upload picture" */

const fileInput = ref(null)
const newPhoto = ref(null)
const previewUrl = ref('')
const photoError = ref('')
const photoSaved = ref(false)
const uploading = ref(false)

const savedPicture = computed(() => {
  const url = userStore.userData?.profilePictureUrl
  return url ? resolveCoverURL(url) : ''
})
const shownPicture = computed(() => previewUrl.value || savedPicture.value)

onBeforeUnmount(() => {
  URL.revokeObjectURL(previewUrl.value)
  URL.revokeObjectURL(bannerPreviewUrl.value)
})

async function onPick(event) {
  const [file] = event.target.files
  if (!file) return
  photoError.value = ''
  photoSaved.value = false
  const problem = await photoProblem(file)
  if (problem) {
    photoError.value = problem
    clearChoice()
    return
  }
  URL.revokeObjectURL(previewUrl.value)
  newPhoto.value = file
  previewUrl.value = URL.createObjectURL(file)
}

function clearChoice() {
  URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  newPhoto.value = null
  // Reset the input so picking the same file again still fires `change`
  if (fileInput.value) fileInput.value.value = ''
}

// Brings the current picture back without touching the server
function revertPhoto() {
  photoError.value = ''
  clearChoice()
}

async function uploadPhoto() {
  if (!newPhoto.value || uploading.value) return
  photoError.value = ''
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', newPhoto.value)
    const { profilePictureUrl } = await request('PUT', `${API_BASE_URL}/api/users/me/profile-picture`, form)
    userStore.updateUser({ profilePictureUrl })
    clearChoice()
    photoSaved.value = true
  } catch (err) {
    if (await handleUnauthorized(err)) return
    photoError.value = PHOTO_ERRORS[err.code] ?? "We couldn't upload your picture. Check that the server is running, then try again."
  } finally {
    uploading.value = false
  }
}

/* Banner: choosing a file only previews it; nothing is sent until "Upload banner". The crop lives here and
   drives both the editor frame and the profile card, so they always show the same framing */

const bannerInput = ref(null)
const bannerFrame = ref(null)
const newBanner = ref(null)
const bannerPreviewUrl = ref('')
const bannerError = ref('')
const bannerSaved = ref(false)
const bannerUploading = ref(false)
const bannerSize = ref(null) // { width, height } of the original image
const bannerCrop = ref({ ...DEFAULT_CROP })
const bannerDrag = ref(null) // { pointerId, x, y, crop } while dragging

const bannerStyle = computed(() =>
  bannerSize.value ? bannerImageStyle(bannerCrop.value, bannerSize.value.width, bannerSize.value.height) : null,
)

function setBannerCrop(crop) {
  bannerCrop.value = clampCrop(crop, bannerSize.value.width, bannerSize.value.height)
}

function setBannerZoom(zoom) {
  setBannerCrop({ ...bannerCrop.value, zoom })
}

function resetBannerCrop() {
  bannerCrop.value = { ...DEFAULT_CROP }
}

function startBannerDrag(event) {
  if (event.button !== 0) return
  bannerFrame.value.setPointerCapture(event.pointerId)
  bannerDrag.value = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, crop: bannerCrop.value }
}

function moveBannerDrag(event) {
  const drag = bannerDrag.value
  if (!drag || drag.pointerId !== event.pointerId) return
  // The crop math works in frame widths, so convert the pixel distance using the frame's width on screen
  const frameWidth = bannerFrame.value.clientWidth
  const { width, height } = bannerSize.value
  bannerCrop.value = panCrop(drag.crop, (event.clientX - drag.x) / frameWidth, (event.clientY - drag.y) / frameWidth, width, height)
}

function endBannerDrag() {
  bannerDrag.value = null
}

const NUDGE = 0.02 // a frame width per 50 key presses
const KEY_MOVES = { ArrowLeft: [NUDGE, 0], ArrowRight: [-NUDGE, 0], ArrowUp: [0, NUDGE], ArrowDown: [0, -NUDGE] }

// Arrow keys move the image the way dragging would; + and − zoom
function nudgeBanner(event) {
  const move = KEY_MOVES[event.key]
  const { width, height } = bannerSize.value
  if (move) {
    bannerCrop.value = panCrop(bannerCrop.value, move[0], move[1], width, height)
  } else if (event.key === '+' || event.key === '=') {
    setBannerZoom(bannerCrop.value.zoom + 0.1)
  } else if (event.key === '-' || event.key === '_') {
    setBannerZoom(bannerCrop.value.zoom - 0.1)
  } else {
    return
  }
  event.preventDefault()
}

async function onPickBanner(event) {
  const [file] = event.target.files
  if (!file) return
  const problem = await bannerProblem(file)
  revertBanner()
  bannerSaved.value = false
  if (problem) {
    bannerError.value = problem
    return
  }
  const url = URL.createObjectURL(file)
  const image = new Image()
  image.src = url
  await image.decode()
  bannerSize.value = { width: image.naturalWidth, height: image.naturalHeight }
  bannerCrop.value = { ...DEFAULT_CROP }
  newBanner.value = file
  bannerPreviewUrl.value = url
}

function revertBanner() {
  URL.revokeObjectURL(bannerPreviewUrl.value)
  bannerPreviewUrl.value = ''
  newBanner.value = null
  bannerSize.value = null
  bannerDrag.value = null
  bannerError.value = ''
  // Reset the input so picking the same file again still fires `change`
  if (bannerInput.value) bannerInput.value.value = ''
}

const savedBanner = computed(() => {
  const url = userStore.userData?.bannerUrl
  return url ? resolveCoverURL(url) : ''
})

// The server crops, so we send the original file plus the visible area in pixels of the original image
async function uploadBanner() {
  if (!newBanner.value || bannerUploading.value) return
  bannerError.value = ''
  bannerUploading.value = true
  try {
    const { width, height } = bannerSize.value
    const rect = bannerCropRect(bannerCrop.value, width, height)
    // Rounding can push the area one pixel past the image edge, which the server rejects
    rect.x = Math.min(rect.x, width - rect.width)
    rect.y = Math.min(rect.y, height - rect.height)

    const query = new URLSearchParams({
      cropX: rect.x,
      cropY: rect.y,
      cropWidth: rect.width,
      cropHeight: rect.height,
    })
    const form = new FormData()
    form.append('file', newBanner.value)
    const { bannerUrl } = await request('PUT', `${API_BASE_URL}/api/users/me/banner?${query}`, form)
    userStore.updateUser({ bannerUrl })
    revertBanner()
    bannerSaved.value = true
  } catch (err) {
    if (await handleUnauthorized(err)) return
    bannerError.value = BANNER_ERRORS[err.code] ?? "We couldn't upload your banner. Check that the server is running, then try again."
  } finally {
    bannerUploading.value = false
  }
}

/* Get creative: the saved details load when the page opens, and "Save changes" replaces them on the server */

const details = reactive({ displayName: '', country: '', website: '', about: '' })
const detailsSaving = ref(false)
const detailsSaved = ref(false)
const detailsError = ref('')

// The server sends null for anything the user never filled in; the form wants empty strings
function showDetails(saved) {
  details.displayName = saved?.displayName ?? ''
  details.country = saved?.country ?? ''
  details.website = saved?.website ?? ''
  details.about = saved?.about ?? ''
}

// The profile endpoint has both the saved details and the saved banner
async function loadSavedProfile() {
  try {
    const profile = await fetchAPI(`${API_BASE_URL}/api/users/profile/${encodeURIComponent(username.value)}`)
    showDetails(profile.profileDetails)
    userStore.updateUser({ bannerUrl: profile.bannerUrl })
  } catch (err) {
    if (await handleUnauthorized(err)) return
    detailsError.value = "We couldn't load your saved details. Check that the server is running, then reload."
  }
}

const nameInvalid = computed(() => {
  const length = details.displayName.trim().length
  return length > 0 && (length < NAME_MIN || length > NAME_MAX)
})

async function saveDetails() {
  if (detailsSaving.value || nameInvalid.value || websiteInvalid.value) return
  detailsError.value = ''
  detailsSaved.value = false
  detailsSaving.value = true
  try {
    // Empty values are sent as null, which clears them on the server
    const body = {
      displayName: details.displayName.trim() || null,
      country: details.country || null,
      website: details.website.trim() || null,
      about: details.about.trim() || null,
    }
    const saved = await request('PUT', `${API_BASE_URL}/api/users/me/profile-details`, body)
    showDetails(saved)
    detailsSaved.value = true
  } catch (err) {
    if (await handleUnauthorized(err)) return
    detailsError.value = err.code === 'INVALID_FIELD' ? err.message : "We couldn't save your details. Check that the server is running, then try again."
  } finally {
    detailsSaving.value = false
  }
}

const websiteInvalid = computed(() => {
  const value = details.website.trim()
  if (!value) return false
  try {
    const url = new URL(value)
    return url.protocol !== 'http:' && url.protocol !== 'https:'
  } catch {
    return true
  }
})

const countryName = computed(() => COUNTRIES.find((c) => c.code === details.country)?.name ?? '')
const websiteUrl = computed(() => (websiteInvalid.value ? '' : details.website.trim()))

/* Links to other sites: undo "Save my option" from the leaving-Aux dialog */
const warnBeforeLeaving = computed({
  get: () => !skipExternalWarning.value,
  set: (warn) => setSkipExternalWarning(!warn),
})
</script>

<style scoped>
.wrap { padding: 0 var(--page-gutter); }
.page-head { border-bottom: 1px solid var(--line); padding-top: var(--space-8); }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-4); }
.tabs { display: flex; gap: var(--space-6); }
.tab { font: 600 14px/20px var(--font-sans); color: var(--ink-muted); text-decoration: none; padding-bottom: var(--space-2); border-bottom: 3px solid transparent; }
.tab:hover { color: var(--ink); border-bottom-color: var(--primary); }
.tab.active { color: var(--link); border-bottom-color: var(--primary); }

.layout { display: grid; grid-template-columns: minmax(0, 1fr) 380px; gap: var(--space-8); padding-top: var(--space-6); padding-bottom: var(--space-8); }
/* Wide screens: picture beside banner, then "Get creative" across both, so the fields fill the width up to the preview.
   Fixed tracks, not auto-fit: a full-row item keeps auto-fit's empty tracks open and leaves a gap on the right */
.main-col { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: var(--space-8); align-content: start; }
.wide { grid-column: 1 / -1; }
h2 { font: 700 20px/28px var(--font-sans); margin: 0 0 var(--space-4); }
.hint { margin: 0; font: 500 12px/16px var(--font-sans); color: var(--ink-muted); }
.error { margin: 0; font: 500 14px/20px var(--font-sans); color: var(--danger); }
.success { margin: 0; font: 500 14px/20px var(--font-sans); color: var(--success); }

/* Picture */
.avatar-area { aspect-ratio: 3 / 1; max-height: 200px; display: flex; align-items: center; margin-bottom: var(--space-4); }
.avatar { height: 100%; aspect-ratio: 1; border-radius: var(--radius-pill); object-fit: cover; background: var(--surface-alt); }
.avatar-empty { display: grid; place-items: center; background: var(--primary); color: var(--on-primary); font: 700 56px/1 var(--font-sans); }
.revert { font-size: 14px; }
.picture-controls { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-3); max-width: 480px; }
.file-row { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
.file-row .btn { font-size: 14px; }
.file-name { font: 500 14px/20px var(--font-sans); color: var(--ink-muted); overflow-wrap: anywhere; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
/* The label stands in for the hidden file input, so it takes the input's keyboard focus ring */
.visually-hidden:focus-visible + label { outline: 2px solid var(--focus-ring); outline-offset: 2px; }

/* Get creative */
/* Name, country and website share a row; "About you" lines up under the first two, the save button gets its own row */
/* One field per row: display name, then country, then website, then about */
.details { display: grid; grid-template-columns: minmax(0, 560px); gap: var(--space-4); align-items: start; }
.field { display: flex; flex-direction: column; gap: var(--space-2); }
.row-label { font: 600 14px/20px var(--font-sans); }
.input { width: 100%; font: 400 16px/24px var(--font-sans); padding: 8px 12px; color: var(--ink); background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); }
.input[aria-invalid='true'] { border-color: var(--danger); }
.textarea { resize: vertical; }
.details .hint, .details .error { margin-top: var(--space-1); }
.status-line { margin-top: var(--space-2) !important; }

/* Applications: flat rows split by hairlines, like the track list */
.intro { margin: 0; color: var(--ink-muted); }
.apps { list-style: none; margin: 0; padding: 0; border-top: 1px solid var(--line); }
.app { display: grid; grid-template-columns: 40px minmax(0, 1fr) auto; gap: var(--space-4); align-items: center; padding: var(--space-4) 0; border-bottom: 1px solid var(--line); }
.app-icon { width: 40px; height: 40px; }
/* Spotify's brand color belongs to Spotify, so it's the one place raw hex is allowed */
.spotify { fill: #1db954; }
.app-text { min-width: 0; }
.app-name { margin: 0; font: 600 16px/24px var(--font-sans); }
.app-desc { margin: 0; font: 400 14px/20px var(--font-sans); }
.app-status { margin: var(--space-1) 0 0; font: 500 12px/16px var(--font-sans); color: var(--ink-muted); }

.banner-frame { aspect-ratio: 3 / 1; border-radius: var(--radius-md); overflow: hidden; background: var(--purple-soft); display: grid; place-items: center; margin-bottom: var(--space-4); }
.banner-frame.editing { position: relative; display: block; cursor: grab; touch-action: none; user-select: none; }
.banner-frame.editing.dragging { cursor: grabbing; }
.banner-frame img { display: block; pointer-events: none; }
.adjust { display: flex; flex-direction: column; gap: var(--space-2); margin-bottom: var(--space-4); }
.zoom-row { display: flex; align-items: center; gap: var(--space-3); }
.zoom-label { font: 600 14px/20px var(--font-sans); }
.zoom { flex: 1; accent-color: var(--primary); }
.banner-saved { width: 100%; height: 100%; object-fit: cover; }
.banner-empty { margin: 0; font: 500 14px/20px var(--font-sans); color: var(--ink-muted); }

.check { display: flex; align-items: center; gap: var(--space-2); font: 600 14px/20px var(--font-sans); cursor: pointer; margin-bottom: var(--space-1); }
.check input { width: 16px; height: 16px; accent-color: var(--primary); }

/* The browser's own arrow sits tight to the edge, so draw one 1rem further in */
.select-wrap { position: relative; }
.select { appearance: none; -webkit-appearance: none; padding-right: calc(var(--space-4) + 28px); cursor: pointer; }
.select-wrap::after { content: ''; position: absolute; top: 50%; right: calc(var(--space-4) + 8px); width: 12px; height: 12px; transform: translateY(-50%); pointer-events: none; background: var(--ink-muted); -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='3' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E") center / contain no-repeat; mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='3' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E") center / contain no-repeat; }

/* Preview */
/* Stays in view below the sticky header while you scroll through the fields it previews */
.side { position: sticky; top: calc(88px + env(safe-area-inset-top, 0px)); align-self: start; display: flex; flex-direction: column; gap: var(--space-3); }
.side-title { font: 700 16px/24px var(--font-sans); margin: 0; }

/* Too narrow for two sections or three fields side by side */
@media (max-width: 1400px) {
  .main-col { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .side { position: static; }
}
@media (max-width: 600px) {
  .app { grid-template-columns: 40px minmax(0, 1fr); }
  .app .btn { grid-column: 2; justify-self: start; }
}
</style>
