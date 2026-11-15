<template>
  <dialog
    ref="dialogEl"
    class="album-dialog"
    aria-labelledby="album-dialog-title"
    @cancel="saving && $event.preventDefault()"
    @close="onClose"
  >
    <form v-if="artist" class="card" @submit.prevent="save">
      <h2 id="album-dialog-title">{{ editing ? `Edit ${editing.name}` : `New album for ${artist.artistName}` }}</h2>
      <p class="lead">Pick its songs. Each one shows the album cover unless you keep its own.</p>

      <div class="top">
        <div class="cover-col">
          <label for="album-cover-input" class="cover-pick">
            <img v-if="coverShown" :src="coverShown" alt="Album cover preview" />
            <span v-else class="cover-empty">Choose a cover</span>
          </label>
          <input
            id="album-cover-input"
            ref="coverInput"
            class="visually-hidden"
            type="file"
            accept="image/jpeg,image/png"
            aria-describedby="album-cover-hint"
            @change="onPickCover($event.target.files[0])"
          />
        </div>
        <div class="fields">
          <div class="field">
            <label class="label" for="album-name">Album name</label>
            <input id="album-name" v-model="name" class="input" type="text" :maxlength="ALBUM_NAME_MAX" required autocomplete="off" />
          </div>
          <p id="album-cover-hint" class="hint">Cover: a JPEG or PNG, at least 256 × 256 pixels and up to 5MB. It's cropped to a square.</p>
        </div>
      </div>

      <fieldset class="songs">
        <legend class="label">Songs <span class="count">{{ tracks.length }} picked</span></legend>
        <p v-if="!songs.length" class="hint">{{ artist.artistName }} has no songs yet.</p>
        <ul v-else class="song-list">
          <li v-for="piece in songs" :key="piece.musicPieceId" class="song" :class="{ picked: trackFor(piece) }">
            <label class="song-main">
              <input type="checkbox" :checked="Boolean(trackFor(piece))" @change="toggle(piece)" />
              <span class="num" aria-hidden="true">{{ trackFor(piece) ? tracks.indexOf(trackFor(piece)) + 1 : '' }}</span>
              <img v-if="piece.coverUrl" class="art" :src="resolveCoverURL(piece.coverUrl)" alt="" loading="lazy" />
              <span v-else class="art art-empty" aria-hidden="true"></span>
              <span class="song-text">
                <span class="song-name">{{ piece.name }}</span>
                <span v-if="otherAlbum(piece)" class="song-note">On {{ otherAlbum(piece) }}; picking it moves it here</span>
              </span>
            </label>
            <label v-if="trackFor(piece)" class="own-cover">
              <input v-model="trackFor(piece).useAlbumCover" type="checkbox" />
              Use album cover
            </label>
          </li>
        </ul>
      </fieldset>

      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <div v-if="confirmingDelete" class="confirm" role="alertdialog" aria-labelledby="album-delete-q">
        <p id="album-delete-q">Delete {{ editing.name }}? Its songs go back to being singles.</p>
        <div class="confirm-actions">
          <button type="button" class="btn secondary" :disabled="saving" @click="confirmingDelete = false">Keep it</button>
          <button type="button" class="btn primary" :disabled="saving" @click="remove">{{ saving ? 'Deleting…' : 'Delete album' }}</button>
        </div>
      </div>

      <div v-else class="actions">
        <button v-if="editing" type="button" class="btn text delete" :disabled="saving" @click="confirmingDelete = true">Delete album</button>
        <span class="spacer"></span>
        <button type="button" class="btn secondary" :disabled="saving" @click="close">Cancel</button>
        <button type="submit" class="btn primary" :disabled="saving">{{ saving ? 'Saving…' : editing ? 'Save album' : 'Create album' }}</button>
      </div>
    </form>
  </dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { resolveCoverURL } from '@/utils/display.js'
import { PHOTO_ERRORS, photoProblem } from '@/utils/photo.js'
import { ALBUM_NAME_MAX, createAlbum, deleteAlbum, updateAlbum } from '@/utils/albums.js'
import { useUserStore } from '@/stores/user.js'
import { useNotificationStore } from '@/stores/notification.js'

// Makes or edits one of an artist's albums. The artist page calls `open(album | null)`, passing the artist, every
// loaded song by them and their albums; it emits `saved` with the Album and `deleted` with its albumId
const props = defineProps({
  artist: { type: Object, default: null },
  songs: { type: Array, required: true },
  albums: { type: Array, required: true },
})
const emit = defineEmits(['saved', 'deleted'])

const userStore = useUserStore()
const notification = useNotificationStore()
const dialogEl = ref(null)
const coverInput = ref(null)
const editing = ref(null)
const name = ref('')
const coverFile = ref(null)
const coverPreview = ref('')
// [{ musicPiece, useAlbumCover }] in track order: the order they were picked
const tracks = ref([])
const error = ref('')
const saving = ref(false)
const confirmingDelete = ref(false)

const coverShown = computed(() => coverPreview.value || editing.value?.coverUrl || '')

function open(album = null) {
  editing.value = album
  name.value = album?.name ?? ''
  clearCover()
  tracks.value = (album?.tracks ?? []).map((track) => ({ ...track }))
  error.value = ''
  confirmingDelete.value = false
  dialogEl.value.showModal()
  document.documentElement.classList.add('scroll-locked')
}

function close() {
  dialogEl.value?.close()
}

function onClose() {
  document.documentElement.classList.remove('scroll-locked')
  clearCover()
}

onBeforeUnmount(onClose)

function clearCover() {
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
  coverFile.value = null
  coverPreview.value = ''
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

function trackFor(piece) {
  return tracks.value.find((track) => track.musicPiece.musicPieceId === piece.musicPieceId)
}

function toggle(piece) {
  const track = trackFor(piece)
  tracks.value = track ? tracks.value.filter((item) => item !== track) : [...tracks.value, { musicPiece: piece, useAlbumCover: true }]
}

function otherAlbum(piece) {
  if (trackFor(piece)) return ''
  const album = props.albums.find((item) => item.albumId !== editing.value?.albumId
    && item.tracks.some((track) => track.musicPiece.musicPieceId === piece.musicPieceId))
  return album?.name ?? ''
}

function saveError(err) {
  if (err.code === 'STORAGE_FULL') return 'This browser is out of room for album covers. Try a smaller image.'
  if (PHOTO_ERRORS[err.code]) return PHOTO_ERRORS[err.code]
  if (err.code === 'ALBUM_NOT_FOUND') return 'This album was deleted. Close this and make it again.'
  return "We couldn't save the album. Check that the server is running, then try again."
}

async function save() {
  const trimmed = name.value.trim()
  error.value = ''
  if (!trimmed) return (error.value = `Give the album a name of 1 to ${ALBUM_NAME_MAX} characters.`)
  if (!editing.value && !coverFile.value) return (error.value = 'Choose a cover for the album.')
  if (!tracks.value.length) return (error.value = 'Pick at least one song.')
  saving.value = true
  try {
    const fields = { name: trimmed, coverImage: coverFile.value, tracks: tracks.value }
    const album = editing.value
      ? await updateAlbum(props.artist.artistId, editing.value.albumId, fields)
      : await createAlbum(props.artist.artistId, fields, userStore.userData?.userId)
    notification.success(editing.value ? `Saved ${album.name}.` : `Made the album ${album.name}.`)
    emit('saved', album)
    close()
  } catch (err) {
    if (err.sessionEnded) return
    error.value = saveError(err)
  } finally {
    saving.value = false
  }
}

async function remove() {
  saving.value = true
  try {
    await deleteAlbum(props.artist.artistId, editing.value.albumId)
    notification.success(`Deleted ${editing.value.name}.`)
    emit('deleted', editing.value.albumId)
    close()
  } catch (err) {
    if (err.sessionEnded) return
    confirmingDelete.value = false
    error.value = "We couldn't delete the album. Check that the server is running, then try again."
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.album-dialog { background: none; border: 0; color: var(--ink); font: 400 16px/24px var(--font-sans); max-width: none; overscroll-behavior: contain; padding: 0; width: min(640px, calc(100% - 32px)); }
.album-dialog::backdrop { background: rgb(0 0 0 / 0.45); }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); display: flex; flex-direction: column; gap: var(--space-4); max-height: 85vh; overflow-y: auto; padding: var(--space-6); }
h2 { font: 700 20px/28px var(--font-sans); margin: 0; overflow-wrap: anywhere; }
.lead, .hint { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.hint { font-size: 12px; line-height: 16px; }
.top { align-items: flex-start; display: flex; gap: var(--space-4); }
.cover-pick { aspect-ratio: 1; background: var(--surface-alt); border: 1px dashed var(--line-strong); border-radius: var(--radius-sm); cursor: pointer; display: grid; overflow: hidden; place-items: center; width: 128px; }
.cover-col:has(input:focus-visible) .cover-pick { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.cover-pick img { height: 100%; object-fit: cover; width: 100%; }
.cover-empty { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); padding: var(--space-2); text-align: center; }
.fields { display: flex; flex: 1; flex-direction: column; gap: var(--space-2); min-width: 0; }
.field { display: flex; flex-direction: column; gap: var(--space-1); }
.label { font: 600 14px/20px var(--font-sans); }
.input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: var(--space-2) var(--space-3); }
.input:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.songs { border: 0; display: flex; flex-direction: column; gap: var(--space-2); margin: 0; min-width: 0; padding: 0; }
.count { color: var(--ink-muted); font-weight: 500; margin-left: var(--space-2); }
.song-list { border-top: 1px solid var(--line); list-style: none; margin: 0; padding: 0; }
.song { align-items: center; border-bottom: 1px solid var(--line); display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-3); padding: var(--space-2) var(--space-1); }
.song.picked { background: var(--purple-soft); }
.song-main { align-items: center; cursor: pointer; display: flex; flex: 1; gap: var(--space-3); min-width: 0; }
.num { color: var(--ink-muted); flex: none; font: 600 12px/16px var(--font-sans); text-align: right; width: 16px; }
.art { border-radius: var(--radius-sm); flex: none; height: 40px; object-fit: cover; width: 40px; }
.art-empty { background: var(--surface-alt); }
.song-text { display: flex; flex-direction: column; min-width: 0; }
.song-name { font: 600 15px/22px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.song-note { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); }
.own-cover { align-items: center; color: var(--ink); cursor: pointer; display: flex; flex: none; font: 500 14px/20px var(--font-sans); gap: var(--space-2); }
input[type='checkbox'] { accent-color: var(--primary); flex: none; height: 18px; margin: 0; width: 18px; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.actions { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-3); }
.spacer { flex: 1; }
.btn.text.delete { color: var(--danger); }
.confirm { background: var(--surface-alt); border-radius: var(--radius-md); display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-4); }
.confirm p { margin: 0; }
.confirm-actions { display: flex; gap: var(--space-3); justify-content: flex-end; }
.visually-hidden { clip-path: inset(50%); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

@media (max-width: 520px) {
  .top { flex-direction: column; }
}
</style>
