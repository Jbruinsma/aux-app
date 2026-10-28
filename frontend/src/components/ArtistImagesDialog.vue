<template>
  <dialog
    ref="dialogEl"
    class="images"
    aria-labelledby="artist-images-title"
    @cancel="busy && $event.preventDefault()"
    @close="onClose"
  >
    <div v-if="artist" class="card">
      <h2 id="artist-images-title">Edit {{ artist.artistName }}</h2>
      <p class="lead">You created this artist, so you can set its picture and banner. Everyone sees them.</p>

      <section class="part" aria-labelledby="artist-picture-heading">
        <h3 id="artist-picture-heading">Picture</h3>
        <div class="picture-row">
          <img v-if="pictureShown" class="avatar" :src="pictureShown" alt="" />
          <span v-else class="avatar avatar-empty" aria-hidden="true">{{ artist.artistName.charAt(0).toUpperCase() }}</span>
          <div class="controls">
            <div class="file-row">
              <input
                id="artist-picture-input"
                ref="pictureInput"
                class="visually-hidden"
                type="file"
                accept="image/jpeg,image/png"
                aria-describedby="artist-picture-hint"
                @change="onPickPicture($event.target.files[0])"
              />
              <label for="artist-picture-input" class="btn secondary">Choose file</label>
              <span class="file-name">{{ newPicture ? newPicture.name : 'No file chosen' }}</span>
            </div>
            <p id="artist-picture-hint" class="hint">A JPEG or PNG, at least 256 × 256 pixels and under 5MB. It's cropped to a square.</p>
            <p v-if="pictureError" class="error" role="alert">{{ pictureError }}</p>
            <button type="button" class="btn primary" :disabled="!newPicture || busy" @click="savePicture">
              {{ savingPicture ? 'Uploading…' : 'Upload picture' }}
            </button>
          </div>
        </div>
      </section>

      <section class="part" aria-labelledby="artist-banner-heading">
        <h3 id="artist-banner-heading">Banner</h3>
        <BannerCropper
          v-if="newBanner"
          v-model="bannerCrop"
          :src="bannerPreview"
          :width="bannerSize.width"
          :height="bannerSize.height"
        />
        <div v-else class="banner-frame">
          <img v-if="artist.artistBannerUrl" class="banner-saved" :src="resolveCoverURL(artist.artistBannerUrl)" alt="" />
          <p v-else class="banner-empty">No banner yet</p>
        </div>
        <div class="controls">
          <div class="file-row">
            <input
              id="artist-banner-input"
              ref="bannerInput"
              class="visually-hidden"
              type="file"
              accept="image/jpeg,image/png"
              aria-describedby="artist-banner-hint"
              @change="onPickBanner($event.target.files[0])"
            />
            <label for="artist-banner-input" class="btn secondary">Choose file</label>
            <span class="file-name">{{ newBanner ? newBanner.name : 'No file chosen' }}</span>
          </div>
          <p id="artist-banner-hint" class="hint">A wide JPEG or PNG, at least 600 × 200 pixels and under 5MB. It's cropped to 3:1.</p>
          <p v-if="bannerError" class="error" role="alert">{{ bannerError }}</p>
          <button type="button" class="btn primary" :disabled="!newBanner || busy" @click="saveBanner">
            {{ savingBanner ? 'Uploading…' : 'Upload banner' }}
          </button>
        </div>
      </section>

      <div class="actions">
        <button type="button" class="btn secondary" :disabled="busy" @click="close">Done</button>
      </div>
    </div>
  </dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import BannerCropper from '@/components/BannerCropper.vue'
import { resolveCoverURL } from '@/utils/display.js'
import { BANNER_ERRORS, PHOTO_ERRORS, bannerProblem, photoProblem } from '@/utils/photo.js'
import { DEFAULT_CROP, bannerCropRect } from '@/utils/banner.js'
import { uploadArtistBanner, uploadArtistPicture } from '@/utils/artist.js'
import { useNotificationStore } from '@/stores/notification.js'

// The artist page opens this with `open(artist)` (ArtistDetails) when canEdit is true. Each image saves on its own and
// emits `updated` with the new { artistPfpUrl } or { artistBannerUrl }
const emit = defineEmits(['updated'])
const notification = useNotificationStore()

const dialogEl = ref(null)
const pictureInput = ref(null)
const bannerInput = ref(null)
const artist = ref(null)
const newPicture = ref(null)
const picturePreview = ref('')
const pictureError = ref('')
const savingPicture = ref(false)
const newBanner = ref(null)
const bannerPreview = ref('')
const bannerSize = ref(null)
const bannerCrop = ref({ ...DEFAULT_CROP })
const bannerError = ref('')
const savingBanner = ref(false)

const busy = computed(() => savingPicture.value || savingBanner.value)
const pictureShown = computed(() => picturePreview.value
  || (artist.value?.artistPfpUrl ? resolveCoverURL(artist.value.artistPfpUrl) : ''))

function open(target) {
  artist.value = { ...target }
  clearPicture()
  clearBanner()
  pictureError.value = ''
  bannerError.value = ''
  dialogEl.value.showModal()
  document.documentElement.classList.add('scroll-locked')
}

function close() {
  dialogEl.value.close()
}

function onClose() {
  document.documentElement.classList.remove('scroll-locked')
  clearPicture()
  clearBanner()
}

onBeforeUnmount(onClose)

function clearPicture() {
  URL.revokeObjectURL(picturePreview.value)
  picturePreview.value = ''
  newPicture.value = null
  if (pictureInput.value) pictureInput.value.value = ''
}

function clearBanner() {
  URL.revokeObjectURL(bannerPreview.value)
  bannerPreview.value = ''
  newBanner.value = null
  bannerSize.value = null
  if (bannerInput.value) bannerInput.value.value = ''
}

async function onPickPicture(file) {
  if (!file) return
  pictureError.value = ''
  const problem = await photoProblem(file)
  clearPicture()
  if (problem) {
    pictureError.value = problem
    return
  }
  newPicture.value = file
  picturePreview.value = URL.createObjectURL(file)
}

async function onPickBanner(file) {
  if (!file) return
  bannerError.value = ''
  const problem = await bannerProblem(file)
  clearBanner()
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
  bannerPreview.value = url
}

async function savePicture() {
  if (!newPicture.value || busy.value) return
  pictureError.value = ''
  savingPicture.value = true
  try {
    const { profilePictureUrl } = await uploadArtistPicture(artist.value.artistId, newPicture.value)
    artist.value.artistPfpUrl = profilePictureUrl
    emit('updated', { artistPfpUrl: profilePictureUrl })
    clearPicture()
    notification.success(`${artist.value.artistName}'s new picture is saved.`)
  } catch (err) {
    if (err.sessionEnded) return
    pictureError.value = imageError(err, PHOTO_ERRORS, 'picture')
  } finally {
    savingPicture.value = false
  }
}

async function saveBanner() {
  if (!newBanner.value || busy.value) return
  bannerError.value = ''
  savingBanner.value = true
  try {
    const { width, height } = bannerSize.value
    const rect = bannerCropRect(bannerCrop.value, width, height)
    // Rounding can push the area one pixel past the image edge, which the server rejects
    rect.x = Math.min(rect.x, width - rect.width)
    rect.y = Math.min(rect.y, height - rect.height)
    const { bannerUrl } = await uploadArtistBanner(artist.value.artistId, newBanner.value, rect)
    artist.value.artistBannerUrl = bannerUrl
    emit('updated', { artistBannerUrl: bannerUrl })
    clearBanner()
    notification.success(`${artist.value.artistName}'s new banner is saved.`)
  } catch (err) {
    if (err.sessionEnded) return
    bannerError.value = imageError(err, BANNER_ERRORS, 'banner')
  } finally {
    savingBanner.value = false
  }
}

function imageError(err, messages, what) {
  if (err.code === 'NOT_ARTIST_CREATOR') return "Only the person who created this artist can change its images."
  if (err.code === 'RATE_LIMITED') return "You're uploading too quickly. Wait a minute, then try again."
  return messages[err.code] ?? `We couldn't upload the ${what}. Check that the server is running, then try again.`
}

defineExpose({ open })
</script>

<style scoped>
.images { background: none; border: 0; color: var(--ink); font: 400 16px/24px var(--font-sans); max-width: none; overscroll-behavior: contain; padding: 0; width: min(640px, calc(100% - 32px)); }
.images::backdrop { background: rgb(0 0 0 / 0.45); }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); display: flex; flex-direction: column; gap: var(--space-4); padding: var(--space-6); }
h2 { font: 700 20px/28px var(--font-sans); margin: 0; overflow-wrap: anywhere; }
h3 { font: 600 16px/24px var(--font-sans); margin: 0; }
.lead { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.part { border-top: 1px solid var(--line); display: flex; flex-direction: column; gap: var(--space-3); padding-top: var(--space-4); }
.picture-row { align-items: flex-start; display: flex; gap: var(--space-4); }
.avatar { background: var(--surface-alt); border-radius: var(--radius-pill); flex: none; height: 96px; object-fit: cover; width: 96px; }
.avatar-empty { background: var(--primary); color: var(--on-primary); display: grid; font: 700 36px/1 var(--font-sans); place-items: center; }
.controls { align-items: flex-start; display: flex; flex-direction: column; gap: var(--space-2); min-width: 0; }
.file-row { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-3); }
.file-row:has(input:focus-visible) .btn { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.file-name { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); overflow-wrap: anywhere; }
.hint { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.banner-frame { aspect-ratio: 3 / 1; background: var(--purple-soft); border-radius: var(--radius-md); display: grid; overflow: hidden; place-items: center; }
.banner-saved { height: 100%; object-fit: cover; width: 100%; }
.banner-empty { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.actions { display: flex; justify-content: flex-end; }
.visually-hidden { clip-path: inset(50%); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

@media (max-width: 520px) {
  .picture-row { flex-direction: column; }
}
</style>
