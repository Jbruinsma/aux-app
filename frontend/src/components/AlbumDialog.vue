<template>
  <dialog
    ref="dialogEl"
    class="album-dialog"
    aria-labelledby="album-dialog-title"
    @cancel="saving && $event.preventDefault()"
    @close="onClose"
  >
    <form v-if="artist" class="card" @submit.prevent="save">
      <h2 id="album-dialog-title">{{ editing ? `Edit ${editing.albumName}` : `New album for ${artist.artistName}` }}</h2>
      <p class="lead">
        {{ draft
          ? `“${draft.songName}” goes on it. Add any of your other songs by ${artist.artistName} too; each shows the album cover unless you keep its own.`
          : `Pick which of your songs by ${artist.artistName} are on it. Each shows the album cover unless you keep its own.` }}
      </p>

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
          <p id="album-cover-hint" class="hint">
            Cover: a JPEG or PNG, at least 256 × 256 pixels and up to 5MB. It's cropped to a square.
            <template v-if="editing"> A new cover changes it for every song using it, including other people's.</template>
          </p>
        </div>
      </div>

      <fieldset class="songs">
        <legend class="label">Your songs <span class="count">{{ pickedCount + (draft ? 1 : 0) }} picked</span></legend>
        <p class="hint">Only the person who uploaded a song can put it on an album. Anyone can add theirs from Upload.</p>
        <p v-if="mineStatus === 'loading'" class="hint">Loading your songs…</p>
        <p v-else-if="mineStatus === 'error'" class="hint">We couldn't load your songs, so none can be picked right now.</p>
        <p v-else-if="!mine.length && !draft" class="hint">You haven't uploaded any songs by {{ artist.artistName }}.</p>
        <ul v-if="mine.length || draft" class="song-list">
          <li v-if="draft" class="song picked upload-track">
            <span class="song-main">
              <input type="checkbox" checked disabled :aria-label="`${draft.songName}, the song you're uploading`" />
              <img v-if="coverShown" class="art" :src="coverShown" alt="" />
              <span v-else class="art art-empty" aria-hidden="true"></span>
              <span class="song-text">
                <span class="song-name">{{ draft.songName }}</span>
                <span class="song-note">The song you're uploading</span>
              </span>
            </span>
          </li>
          <li v-for="piece in mine" :key="piece.musicPieceId" class="song" :class="{ picked: picks[piece.musicPieceId] }">
            <label class="song-main">
              <input type="checkbox" :checked="Boolean(picks[piece.musicPieceId])" @change="toggle(piece)" />
              <img v-if="piece.coverUrl" class="art" :src="resolveCoverURL(piece.coverUrl)" alt="" loading="lazy" />
              <span v-else class="art art-empty" aria-hidden="true"></span>
              <span class="song-text">
                <span class="song-name">{{ piece.name }}</span>
                <span v-if="otherAlbum(piece)" class="song-note">On {{ otherAlbum(piece) }}. Picking it moves it here.</span>
              </span>
            </label>
            <label v-if="picks[piece.musicPieceId]" class="own-cover">
              <input v-model="picks[piece.musicPieceId].useAlbumCover" type="checkbox" />
              Use album cover
            </label>
          </li>
        </ul>
      </fieldset>

      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <div v-if="confirmingDelete" class="confirm" role="alertdialog" aria-labelledby="album-delete-q">
        <p id="album-delete-q">Delete {{ editing.albumName }}? Its songs become singles and keep the cover they show.</p>
        <div class="confirm-actions">
          <button type="button" class="btn secondary" :disabled="saving" @click="confirmingDelete = false">Keep it</button>
          <button type="button" class="btn primary" :disabled="saving" @click="remove">{{ saving ? 'Deleting…' : 'Delete album' }}</button>
        </div>
      </div>

      <div v-else class="actions">
        <button v-if="editing" type="button" class="btn text delete" :disabled="saving" @click="confirmingDelete = true">Delete album</button>
        <span class="spacer"></span>
        <button type="button" class="btn secondary" :disabled="saving" @click="close">Cancel</button>
        <button type="submit" class="btn primary" :disabled="saving">{{ submitLabel }}</button>
      </div>
    </form>
  </dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { resolveCoverURL } from '@/utils/display.js'
import { PHOTO_ERRORS, photoProblem } from '@/utils/photo.js'
import { fetchUploads, updateMusicPiece } from '@/utils/music.js'
import { ALBUM_NAME_MAX, deleteAlbum, findOrCreateAlbum, setPieceAlbum, updateAlbum } from '@/utils/albums.js'
import { useNotificationStore } from '@/stores/notification.js'

// Makes or edits one of an artist's albums. The artist page calls `open(album | null)` with an AlbumOverview, passing
// the artist and their albums; it emits `saved` with the album and `deleted` with its albumId. Only a song's uploader
// can put it on an album, so the songs offered are the caller's own uploads by this artist (GET /api/music-pieces).
// A new album with the name of one the artist already has is that album: the server returns it and ignores the cover.
// Upload opens it with `open(null, { draft: { songName, coverFile, albumName, tracks } })` for a new album around the
// song being uploaded: the cover starts as the song's, and saving emits `draft` ({ albumName, coverImage, tracks })
// instead of making the album, since the song doesn't exist yet. Closing without saving emits `cancel`
const props = defineProps({
  artist: { type: Object, default: null },
  albums: { type: Array, required: true },
})
const emit = defineEmits(['saved', 'deleted', 'draft', 'cancel'])

const notification = useNotificationStore()
const dialogEl = ref(null)
const coverInput = ref(null)
const editing = ref(null)
const name = ref('')
const coverFile = ref(null)
const coverPreview = ref('')
// The caller's uploads by this artist, and which are picked: { [musicPieceId]: { useAlbumCover } }
const mine = ref([])
const mineStatus = ref('ready')
const picks = ref({})
const error = ref('')
const saving = ref(false)
const confirmingDelete = ref(false)
const draft = ref(null)
let finished = false
let openRun = 0

const coverShown = computed(() => coverPreview.value || (editing.value?.coverUrl ? resolveCoverURL(editing.value.coverUrl) : ''))
const pickedCount = computed(() => Object.keys(picks.value).length)
const submitLabel = computed(() => {
  if (saving.value) return 'Saving…'
  if (draft.value) return 'Use this album'
  return editing.value ? 'Save album' : 'Create album'
})

function open(album = null, options = {}) {
  editing.value = album
  draft.value = options.draft ?? null
  name.value = album?.albumName ?? draft.value?.albumName ?? ''
  clearCover()
  if (draft.value?.coverFile) {
    coverFile.value = draft.value.coverFile
    coverPreview.value = URL.createObjectURL(draft.value.coverFile)
  }
  picks.value = Object.fromEntries((draft.value?.tracks ?? []).map((track) => [track.musicPiece.musicPieceId, { useAlbumCover: track.useAlbumCover }]))
  mine.value = []
  finished = false
  error.value = ''
  confirmingDelete.value = false
  dialogEl.value.showModal()
  document.documentElement.classList.add('scroll-locked')
  loadMine()
}

async function loadMine() {
  const run = ++openRun
  const artistId = props.artist?.artistId
  mineStatus.value = 'loading'
  try {
    const pieces = (await fetchUploads())?.uploadedMusicPieces ?? []
    if (run !== openRun) return
    mine.value = pieces.filter((piece) => piece.artistSummary?.artistId === artistId)
    // Editing: the songs already on the album start picked, with the cover choice they have
    if (editing.value) {
      picks.value = Object.fromEntries(mine.value
        .filter((piece) => piece.album?.albumId === editing.value.albumId)
        .map((piece) => [piece.musicPieceId, { useAlbumCover: piece.usesAlbumCover }]))
    }
    mineStatus.value = 'ready'
  } catch (err) {
    if (run === openRun && !err.sessionEnded) mineStatus.value = 'error'
  }
}

function close() {
  dialogEl.value?.close()
}

function onClose() {
  document.documentElement.classList.remove('scroll-locked')
  clearCover()
  if (draft.value && !finished) emit('cancel')
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

function toggle(piece) {
  const next = { ...picks.value }
  if (next[piece.musicPieceId]) delete next[piece.musicPieceId]
  else next[piece.musicPieceId] = { useAlbumCover: true }
  picks.value = next
}

// The name of the album the song is on now, when that's not this one
function otherAlbum(piece) {
  return piece.album && piece.album.albumId !== editing.value?.albumId ? piece.album.albumName : ''
}

// The songs whose album or cover choice has to change for them to match the picks on `albumId`
function changesFor(albumId) {
  const changes = []
  for (const piece of mine.value) {
    const pick = picks.value[piece.musicPieceId]
    const onIt = piece.album?.albumId === albumId
    if (pick && !onIt) changes.push(() => setPieceAlbum(piece.musicPieceId, albumId, pick.useAlbumCover))
    else if (pick && pick.useAlbumCover !== piece.usesAlbumCover) changes.push(() => updateMusicPiece(piece.musicPieceId, { useAlbumCover: pick.useAlbumCover }))
    else if (!pick && onIt) changes.push(() => setPieceAlbum(piece.musicPieceId, null))
  }
  return changes
}

function saveError(err) {
  if (PHOTO_ERRORS[err.code]) return PHOTO_ERRORS[err.code]
  if (err.code === 'ALBUM_NAME_TAKEN') return `${props.artist.artistName} already has an album with that name.`
  if (err.code === 'NOT_ALBUM_EDITOR') return "Only the album's creator or the artist's creator can change it."
  if (err.code === 'ALBUM_NOT_FOUND') return 'This album was deleted. Close this and make it again.'
  if (err.code === 'CONFLICT') return 'Someone made this album at the same moment. Try again.'
  if (err.code === 'RATE_LIMITED') return "You're making changes too quickly. Wait a minute, then try again."
  return "We couldn't save the album. Check that the server is running, then try again."
}

async function save() {
  const trimmed = name.value.trim()
  error.value = ''
  if (!trimmed) return (error.value = `Give the album a name of 1 to ${ALBUM_NAME_MAX} characters.`)
  if (!editing.value && !coverFile.value) return (error.value = 'Choose a cover for the album.')
  if (draft.value) {
    finished = true
    const tracks = mine.value
      .filter((piece) => picks.value[piece.musicPieceId])
      .map((piece) => ({ musicPiece: piece, useAlbumCover: picks.value[piece.musicPieceId].useAlbumCover }))
    emit('draft', { albumName: trimmed, coverImage: coverFile.value, tracks })
    return close()
  }
  saving.value = true
  try {
    const fields = { albumName: trimmed, coverImage: coverFile.value }
    const album = editing.value
      ? await updateAlbum(editing.value.albumId, fields)
      : await findOrCreateAlbum(props.artist.artistId, fields)
    const existed = !editing.value && props.albums.some((item) => item.albumId === album.albumId)
    // One at a time: song edits share the upload rate limit
    let failed = 0
    for (const change of changesFor(album.albumId)) {
      await change().catch((err) => {
        if (err.sessionEnded) throw err
        failed += 1
      })
    }
    if (failed) notification.error(`Saved ${album.albumName}, but ${failed === 1 ? '1 song' : `${failed} songs`} couldn't be moved. Try again.`)
    else if (existed) notification.success(`${props.artist.artistName} already had ${album.albumName}, so your songs went on it.`)
    else notification.success(editing.value ? `Saved ${album.albumName}.` : `Made the album ${album.albumName}.`)
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
    await deleteAlbum(editing.value.albumId)
    notification.success(`Deleted ${editing.value.albumName}.`)
    emit('deleted', editing.value.albumId)
    close()
  } catch (err) {
    if (err.sessionEnded) return
    confirmingDelete.value = false
    error.value = err.code === 'NOT_ALBUM_EDITOR'
      ? "Only the album's creator or the artist's creator can delete it."
      : "We couldn't delete the album. Check that the server is running, then try again."
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
.upload-track .song-main { cursor: default; }
.song-main { align-items: center; cursor: pointer; display: flex; flex: 1; gap: var(--space-3); min-width: 0; }
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
