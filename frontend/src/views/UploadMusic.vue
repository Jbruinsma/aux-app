<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="stage">
      <form class="upload-card" @submit.prevent="upload">
        <div class="cover">
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
          <input
            id="cover-input"
            ref="coverInput"
            class="visually-hidden"
            type="file"
            accept="image/jpeg,image/png"
            aria-describedby="cover-hint"
            @change="onPickCover($event.target.files[0])"
          />
          <p id="cover-hint" class="hint">A JPEG or PNG, at least 256 × 256 pixels and up to 5MB. It's cropped to a square.</p>
        </div>

        <div class="fields">
          <h1>Upload a song</h1>
          <p v-if="targetPlaylist" class="lead">It's added to your playlist once it's uploaded.</p>

          <div class="field">
            <span class="label">MP3 file</span>
            <div class="file-row">
              <input
                id="mp3-input"
                ref="mp3Input"
                class="visually-hidden"
                type="file"
                accept="audio/mpeg,.mp3"
                aria-describedby="mp3-hint"
                @change="onPickMp3($event.target.files[0])"
              />
              <label for="mp3-input" class="btn secondary">{{ mp3File ? 'Choose a different MP3' : 'Choose MP3' }}</label>
              <span v-if="checkingMp3" class="file-meta">Checking…</span>
              <span v-else-if="mp3File" class="file-meta">{{ mp3File.name }} · {{ formatLength(mp3Seconds) }} · {{ formatSize(mp3File.size) }}</span>
            </div>
            <p id="mp3-hint" class="hint">An MP3 up to 25MB, between 5 seconds and 10 minutes long. Uploads count toward your 2GB of storage.</p>
          </div>

          <div class="field">
            <label class="label" for="song-title">Title</label>
            <input id="song-title" v-model="title" class="input" type="text" :maxlength="TITLE_MAX" required autocomplete="off" />
          </div>

          <ArtistPicker ref="artistPicker" v-model="artist" />

          <div class="field">
            <label class="check">
              <input v-model="isPublic" type="checkbox" aria-describedby="public-hint" />
              Make this song public
            </label>
            <p id="public-hint" class="hint">
              {{ isPublic
                ? 'Anyone can play it, and it can go in anyone\'s playlists.'
                : 'Only you can play it. It can only go in your own playlists, or in private ones you edit.' }}
            </p>
          </div>

          <p v-if="error" class="error" role="alert">{{ error }}</p>

          <div class="actions">
            <button type="submit" class="btn primary" :disabled="!canUpload">{{ uploading ? 'Uploading…' : 'Upload song' }}</button>
            <router-link :to="backRoute" class="btn text">Cancel</router-link>
          </div>
        </div>
      </form>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import ArtistPicker from '@/components/ArtistPicker.vue'
import { PHOTO_ERRORS, photoProblem } from '@/utils/photo.js'
import {
  TITLE_MAX,
  UPLOAD_ERRORS,
  addPiecesToPlaylist,
  additionMessage,
  mp3Problem,
  uploadMusicPiece,
} from '@/utils/music.js'
import { useNotificationStore } from '@/stores/notification.js'

const route = useRoute()
const router = useRouter()
const notification = useNotificationStore()

// From a playlist's Add music page: /upload?playlist=<id>&owner=<username> adds the song to it afterwards
const targetPlaylist = computed(() => (route.query.playlist && route.query.owner
  ? { id: String(route.query.playlist), owner: String(route.query.owner) }
  : null))
const backRoute = computed(() => (targetPlaylist.value
  ? { name: 'Add', params: { username: targetPlaylist.value.owner, id: targetPlaylist.value.id } }
  : { name: 'Dashboard' }))

const mp3Input = ref(null)
const mp3File = ref(null)
const mp3Seconds = ref(null)
const checkingMp3 = ref(false)
const coverInput = ref(null)
const coverFile = ref(null)
const coverPreview = ref('')
const dragOver = ref(false)
const title = ref('')
const artist = ref(null)
const artistPicker = ref(null)
const isPublic = ref(false)
const uploading = ref(false)
const error = ref('')

const hasArtist = computed(() => Boolean(artist.value || artistPicker.value?.pendingName))
const canUpload = computed(() => !uploading.value && !checkingMp3.value && Boolean(mp3File.value && coverFile.value && title.value.trim() && hasArtist.value))

onBeforeUnmount(() => URL.revokeObjectURL(coverPreview.value))

async function onPickMp3(file) {
  if (!file) return
  error.value = ''
  checkingMp3.value = true
  const { problem, seconds } = await mp3Problem(file)
  checkingMp3.value = false
  if (mp3Input.value) mp3Input.value.value = ''
  if (problem) {
    error.value = problem
    return
  }
  mp3File.value = file
  mp3Seconds.value = seconds
  if (!title.value.trim()) title.value = file.name.replace(/\.mp3$/i, '').slice(0, TITLE_MAX)
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

function formatLength(seconds) {
  if (seconds === null) return ''
  const whole = Math.round(seconds)
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`
}

function formatSize(bytes) {
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`
}

async function upload() {
  if (!canUpload.value) return
  error.value = ''
  uploading.value = true
  try {
    // A typed name that wasn't picked from the list becomes (or finds) that artist
    if (!artist.value) await artistPicker.value.create()
    if (!artist.value) return
    const piece = await uploadMusicPiece({
      name: title.value.trim(),
      artistId: artist.value.artistId,
      isPublic: isPublic.value,
      mp3File: mp3File.value,
      coverImage: coverFile.value,
    })
    if (targetPlaylist.value) {
      const result = await addPiecesToPlaylist(targetPlaylist.value.id, [piece.musicPieceId]).catch(() => null)
      if (result) notification[result.failedCount ? 'neutral' : 'success'](`Uploaded “${piece.name}”. ${additionMessage(result)}`)
      else notification.error(`Uploaded “${piece.name}”, but it couldn't be added to the playlist. Add it from your uploads.`)
      await router.push({ name: 'Playlist', params: { username: targetPlaylist.value.owner, id: targetPlaylist.value.id } })
    } else {
      notification.success(`Uploaded “${piece.name}”. It's in My Uploads on your dashboard.`)
      await router.push({ name: 'Dashboard' })
    }
  } catch (err) {
    if (err.sessionEnded) return
    if (UPLOAD_ERRORS[err.code]) error.value = UPLOAD_ERRORS[err.code]
    else if (PHOTO_ERRORS[err.code]) error.value = PHOTO_ERRORS[err.code]
    else if (err.code === 'INVALID_FIELD') error.value = `Give the song a title of 1 to ${TITLE_MAX} characters, and choose an artist.`
    else if (err.code === 'RATE_LIMITED') error.value = "You're uploading too quickly. Wait a minute, then try again."
    else error.value = "We couldn't upload this song. Check that the server is running, then try again."
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.stage { align-items: center; display: flex; flex-direction: column; padding: 56px var(--space-6) var(--space-8); }
.upload-card { align-items: start; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); display: grid; gap: var(--space-8); grid-template-columns: 240px minmax(0, 1fr); max-width: 800px; padding: var(--space-8) var(--space-6); width: 100%; }
h1 { font: 700 28px/34px var(--font-sans); margin: 0; }
.lead { color: var(--ink-muted); margin: 0; }
.fields { display: flex; flex-direction: column; gap: var(--space-4); min-width: 0; }
.field { display: flex; flex-direction: column; gap: var(--space-2); }
.label { font: 600 14px/20px var(--font-sans); }
.input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px 12px; width: 100%; }
.hint { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.check { align-items: center; cursor: pointer; display: flex; font: 600 14px/20px var(--font-sans); gap: var(--space-2); }
.check input { accent-color: var(--primary); height: 16px; margin: 0; width: 16px; }
.actions { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-3); margin-top: var(--space-2); }
.file-row { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-3); }
.file-meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); overflow-wrap: anywhere; }
.file-row:has(input:focus-visible) .btn { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.icon { fill: none; height: 32px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; width: 32px; }

.cover { display: flex; flex-direction: column; gap: var(--space-2); }
.drop { aspect-ratio: 1; background: var(--surface-alt); border: 2px dashed var(--line-strong); border-radius: var(--radius-sm); cursor: pointer; display: grid; overflow: hidden; place-items: center; transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease); width: 100%; }
.drop:hover, .drop.over { background: var(--purple-soft); border-color: var(--primary); }
.drop.filled { border-color: var(--line); border-style: solid; }
.cover:has(input:focus-visible) .drop { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.drop img { height: 100%; object-fit: cover; width: 100%; }
.drop-hint { align-items: center; color: var(--ink-muted); display: flex; flex-direction: column; font: 500 14px/20px var(--font-sans); gap: var(--space-2); padding: var(--space-6); text-align: center; }
.visually-hidden { clip-path: inset(50%); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

@media (max-width: 720px) {
  .stage { padding: var(--space-6) var(--space-4); }
  .upload-card { gap: var(--space-6); grid-template-columns: minmax(0, 1fr); padding: var(--space-6) var(--space-4); }
  .cover { max-width: 240px; }
}
</style>
