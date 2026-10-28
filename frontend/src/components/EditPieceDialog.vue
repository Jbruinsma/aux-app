<template>
  <dialog
    ref="dialogEl"
    class="edit"
    aria-labelledby="edit-piece-title"
    @cancel="saving && $event.preventDefault()"
    @close="onClose"
  >
    <form v-if="piece" class="card" @submit.prevent="save">
      <h2 id="edit-piece-title">Edit song</h2>

      <div class="grid">
        <div class="cover">
          <label
            for="edit-cover-input"
            class="drop"
            :class="{ over: dragOver }"
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
            <span class="drop-change">Change cover</span>
          </label>
          <input
            id="edit-cover-input"
            ref="coverInput"
            class="visually-hidden"
            type="file"
            accept="image/jpeg,image/png"
            aria-describedby="edit-cover-hint"
            @change="onPickCover($event.target.files[0])"
          />
          <p id="edit-cover-hint" class="hint">A JPEG or PNG, at least 256 × 256 pixels and up to 5MB. It's cropped to a square.</p>
        </div>

        <div class="fields">
          <div class="field">
            <label class="label" for="edit-title">Title</label>
            <input id="edit-title" v-model="title" class="input" type="text" :maxlength="TITLE_MAX" required autocomplete="off" />
          </div>

          <ArtistPicker ref="artistPicker" v-model="artist" />

          <fieldset class="field visibility">
            <legend class="label">Who can play it</legend>
            <label v-if="piece.isPublic === undefined" class="check">
              <input v-model="visibility" type="radio" name="edit-visibility" :value="null" />
              Keep as it is
            </label>
            <label class="check">
              <input v-model="visibility" type="radio" name="edit-visibility" :value="true" />
              Public: anyone can play it
            </label>
            <label class="check">
              <input v-model="visibility" type="radio" name="edit-visibility" :value="false" />
              Private: only you, and private playlists you collaborate on
            </label>
            <p v-if="goingPrivate" class="warning" role="note">
              It becomes unavailable on public playlists and on playlists you no longer collaborate on. It stays listed
              there, grayed out, and nobody else can play it.
            </p>
          </fieldset>
        </div>
      </div>

      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <div class="actions">
        <button type="button" class="btn secondary" :disabled="saving" @click="close">Cancel</button>
        <button type="submit" class="btn primary" :disabled="!canSave">{{ saving ? 'Saving…' : 'Save changes' }}</button>
      </div>
    </form>
  </dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import ArtistPicker from '@/components/ArtistPicker.vue'
import { resolveCoverURL } from '@/utils/display.js'
import { PHOTO_ERRORS, photoProblem } from '@/utils/photo.js'
import { TITLE_MAX, UPLOAD_ERRORS, updateMusicPiece } from '@/utils/music.js'
import { useNotificationStore } from '@/stores/notification.js'

// Edits one of the caller's uploads (PUT /api/music-pieces/{id}), sending only what changed. The parent calls
// `open(piece)` with a MusicPieceOverview; the `uploads` event refreshes the row afterwards. Pieces don't say whether
// they're public yet (BACKEND_REQUESTS.md), so until they do the visibility starts on "Keep as it is"
const emit = defineEmits(['saved'])
const notification = useNotificationStore()

const dialogEl = ref(null)
const coverInput = ref(null)
const artistPicker = ref(null)
const piece = ref(null)
const title = ref('')
const artist = ref(null)
const visibility = ref(null)
const coverFile = ref(null)
const coverPreview = ref('')
const dragOver = ref(false)
const saving = ref(false)
const error = ref('')

const changes = computed(() => {
  if (!piece.value) return {}
  const result = {}
  const name = title.value.trim()
  if (name && name !== piece.value.name) result.name = name
  if (artist.value && artist.value.artistId !== piece.value.artistSummary?.artistId) result.artistId = artist.value.artistId
  if (visibility.value !== null && visibility.value !== piece.value.isPublic) result.isPublic = visibility.value
  if (coverFile.value) result.coverImage = coverFile.value
  return result
})
const pendingArtist = computed(() => !artist.value && Boolean(artistPicker.value?.pendingName))
const canSave = computed(() => !saving.value && Boolean(title.value.trim())
  && (Object.keys(changes.value).length > 0 || pendingArtist.value))
const goingPrivate = computed(() => visibility.value === false && piece.value?.isPublic !== false)

function open(target) {
  piece.value = target
  title.value = target.name
  artist.value = target.artistSummary?.artistId ? target.artistSummary : null
  visibility.value = target.isPublic ?? null
  clearCover()
  coverPreview.value = target.coverUrl ? resolveCoverURL(target.coverUrl) : ''
  error.value = ''
  dialogEl.value.showModal()
  document.documentElement.classList.add('scroll-locked')
}

function close() {
  dialogEl.value.close()
}

function onClose() {
  document.documentElement.classList.remove('scroll-locked')
  clearCover()
}

function clearCover() {
  if (coverFile.value) URL.revokeObjectURL(coverPreview.value)
  coverFile.value = null
}

onBeforeUnmount(onClose)

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
  clearCover()
  coverFile.value = file
  coverPreview.value = URL.createObjectURL(file)
}

async function save() {
  if (!canSave.value) return
  error.value = ''
  saving.value = true
  try {
    // A typed name that wasn't picked from the list becomes (or finds) that artist
    if (pendingArtist.value) await artistPicker.value.create()
    if (!artist.value) return
    const fields = changes.value
    if (!Object.keys(fields).length) return close()
    const updated = await updateMusicPiece(piece.value.musicPieceId, fields)
    notification.success(`Saved changes to “${updated?.name ?? title.value.trim()}”.`)
    emit('saved', updated)
    close()
  } catch (err) {
    if (err.sessionEnded) return
    if (err.code === 'MUSIC_PIECE_NOT_FOUND') error.value = "This song isn't in your uploads anymore."
    else if (err.code === 'ARTIST_NOT_FOUND') error.value = UPLOAD_ERRORS.ARTIST_NOT_FOUND
    else if (PHOTO_ERRORS[err.code]) error.value = PHOTO_ERRORS[err.code]
    // Only a cover is uploaded here, so a too-big request means the cover
    else if (err.code === 'REQUEST_FAILED') error.value = PHOTO_ERRORS.IMAGE_TOO_LARGE
    else if (err.code === 'INVALID_FIELD') error.value = `Give the song a title of 1 to ${TITLE_MAX} characters.`
    else if (err.code === 'RATE_LIMITED') error.value = "You're saving too quickly. Wait a minute, then try again."
    else error.value = "We couldn't save your changes. Check that the server is running, then try again."
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.edit { background: none; border: 0; color: var(--ink); font: 400 16px/24px var(--font-sans); max-width: none; overscroll-behavior: contain; padding: 0; width: min(720px, calc(100% - 32px)); }
.edit::backdrop { background: rgb(0 0 0 / 0.45); }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); display: flex; flex-direction: column; gap: var(--space-4); padding: var(--space-6); }
h2 { font: 700 20px/28px var(--font-sans); margin: 0; }
.grid { align-items: start; display: grid; gap: var(--space-6); grid-template-columns: 200px minmax(0, 1fr); }
.fields { display: flex; flex-direction: column; gap: var(--space-4); min-width: 0; }
.field { display: flex; flex-direction: column; gap: var(--space-2); }
.visibility { border: 0; margin: 0; min-width: 0; padding: 0; }
.label { font: 600 14px/20px var(--font-sans); padding: 0; }
.input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px 12px; width: 100%; }
.hint { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.check { align-items: center; cursor: pointer; display: flex; font: 500 14px/20px var(--font-sans); gap: var(--space-2); }
.check input { accent-color: var(--primary); flex: none; height: 16px; margin: 0; width: 16px; }
.warning { background: var(--purple-soft); border-radius: var(--radius-sm); color: var(--ink); font: 500 14px/20px var(--font-sans); margin: 0; padding: var(--space-3); }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.actions { display: flex; flex-wrap: wrap; gap: var(--space-3); justify-content: flex-end; }
.icon { fill: none; height: 32px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; width: 32px; }

.cover { display: flex; flex-direction: column; gap: var(--space-2); }
.drop { aspect-ratio: 1; background: var(--surface-alt); border: 1px solid var(--line); border-radius: var(--radius-sm); cursor: pointer; display: grid; overflow: hidden; place-items: center; position: relative; transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease); width: 100%; }
.drop:hover, .drop.over { background: var(--purple-soft); border-color: var(--primary); }
.cover:has(input:focus-visible) .drop { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.drop img { height: 100%; object-fit: cover; width: 100%; }
.drop-hint { align-items: center; color: var(--ink-muted); display: flex; flex-direction: column; font: 500 14px/20px var(--font-sans); gap: var(--space-2); padding: var(--space-6); text-align: center; }
.drop-change { background: var(--ink); border-radius: var(--radius-pill); bottom: var(--space-3); color: var(--surface); font: 600 12px/16px var(--font-sans); left: 50%; opacity: 0; padding: var(--space-1) var(--space-3); position: absolute; transform: translateX(-50%); transition: opacity var(--dur-fast) var(--ease); white-space: nowrap; }
.drop:hover .drop-change, .cover:has(input:focus-visible) .drop-change { opacity: 1; }
.visually-hidden { clip-path: inset(50%); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

@media (max-width: 720px) {
  .grid { gap: var(--space-4); grid-template-columns: minmax(0, 1fr); }
  .cover { max-width: 200px; }
}
</style>
