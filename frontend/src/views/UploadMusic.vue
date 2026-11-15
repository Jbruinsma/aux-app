<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="stage">
      <form class="upload-card" @submit.prevent="upload">
        <div class="cover">
          <label
            for="cover-input"
            class="drop"
            :class="{ over: dragOver, filled: coverShown }"
            @dragover.prevent="dragOver = true"
            @dragleave="dragOver = false"
            @drop.prevent="onDropCover"
          >
            <img v-if="coverShown" :src="coverShown" alt="Cover preview" />
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
          <p v-if="usingAlbumCover" id="cover-hint" class="hint">Using the cover of {{ album.albumName }}. Choose your own to use that instead.</p>
          <p v-else id="cover-hint" class="hint">A JPEG or PNG, at least 256 × 256 pixels and up to 5MB. It's cropped to a square.</p>
        </div>

        <div class="fields">
          <h1>Upload a song</h1>
          <p v-if="targetPlaylist" class="lead">It's added to your playlist once it's uploaded.</p>

          <div class="field">
            <div v-if="youtubeAvailable" class="tabs" role="tablist" aria-label="Where the song comes from">
              <button
                v-for="item in SOURCES"
                :key="item.id"
                type="button"
                role="tab"
                class="tab"
                :class="{ active: source === item.id }"
                :aria-selected="source === item.id ? 'true' : 'false'"
                :disabled="uploading"
                @click="source = item.id"
              >{{ item.label }}</button>
            </div>
            <span v-else class="label">MP3 file</span>
            <template v-if="source === 'youtube'">
              <div v-if="youtubeImport" class="import">
                <img class="import-thumb" :src="youtubeImport.thumbnailUrl" alt="" />
                <span class="import-text" aria-live="polite">
                  <span class="import-title">{{ youtubeImport.title ?? 'YouTube video' }}</span>
                  <span class="hint">{{ importReady ? `${formatLength(youtubeImport.durationSeconds)} · Ready to upload` : YOUTUBE_STEPS[youtubeImport.status] }}</span>
                </span>
                <button type="button" class="btn secondary small" :disabled="uploading" @click="clearImport">{{ importReady ? 'Use a different link' : 'Cancel' }}</button>
              </div>
              <div v-else class="file-row">
                <label class="visually-hidden" for="youtube-url">YouTube link</label>
                <input
                  id="youtube-url"
                  v-model="youtubeUrl"
                  class="input url"
                  type="url"
                  inputmode="url"
                  :maxlength="YOUTUBE_URL_MAX"
                  autocomplete="off"
                  aria-describedby="youtube-hint"
                  @keydown.enter.prevent="getAudio"
                />
                <button type="button" class="btn secondary" :disabled="!youtubeUrl.trim() || startingImport" @click="getAudio">{{ startingImport ? 'Starting…' : 'Get audio' }}</button>
              </div>
              <p id="youtube-hint" class="hint">A link to one YouTube video, 30 seconds to 10 minutes long. Getting the audio usually takes under a minute. It counts toward your 2GB of storage once uploaded.</p>
            </template>
            <template v-else>
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
              <p id="mp3-hint" class="hint">An MP3 up to 25MB, between 30 seconds and 10 minutes long. Uploads count toward your 2GB of storage.</p>
            </template>
          </div>

          <div class="field">
            <label class="label" for="song-title">Title</label>
            <input id="song-title" v-model="title" class="input" type="text" :maxlength="TITLE_MAX" required autocomplete="off" />
          </div>

          <ArtistPicker ref="artistPicker" v-model="artist" />

          <div v-if="artist?.artistId" class="field">
            <label class="label" for="song-album">Album</label>
            <span class="select-wrap">
              <select id="song-album" v-model="albumId" class="input select" aria-describedby="album-hint">
                <option value="">None, it's a single</option>
                <option v-for="item in albums" :key="item.albumId" :value="item.albumId">{{ item.albumName }}</option>
                <option :value="NEW_ALBUM">{{ draftAlbum ? `New album: ${draftAlbum.albumName}` : 'Create a new album…' }}</option>
              </select>
            </span>
            <div v-if="draftAlbum && albumId === NEW_ALBUM" class="draft">
              <img class="draft-cover" :src="draftAlbum.coverPreview" alt="" />
              <span class="draft-text">
                <span class="draft-name">{{ draftAlbum.albumName }}</span>
                <span class="hint">{{ draftAlbum.tracks.length ? `This song, then ${draftAlbum.tracks.length === 1 ? '1 other' : `${draftAlbum.tracks.length} others`}` : 'Just this song for now' }}</span>
              </span>
              <button type="button" class="btn secondary small" @click="openDraft">Edit album</button>
            </div>
            <label v-if="album" class="check">
              <input v-model="useAlbumCover" type="checkbox" />
              Use the album cover
            </label>
            <p id="album-hint" class="hint">{{ albumHint }}</p>
          </div>
          <TagPicker v-model="tags" :error="tagsError" :disabled="uploading" @update:model-value="tagsError = ''" />

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

    <AlbumDialog
      ref="albumDialog"
      :artist="artist?.artistId ? artist : null"
      :albums="albums"
      @draft="onDraft"
      @cancel="onDraftCancel"
    />
    <AppFooter />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import AlbumDialog from '@/components/AlbumDialog.vue'
import ArtistPicker from '@/components/ArtistPicker.vue'
import TagPicker from '@/components/TagPicker.vue'
import { isTagsError, tagsErrorMessage } from '@/utils/tags.js'
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
import { fetchArtist } from '@/utils/artist.js'
import { fetchArtistAlbums, findOrCreateAlbum, setPieceAlbum } from '@/utils/albums.js'
import { resolveCoverURL } from '@/utils/display.js'
import {
  YOUTUBE_ERRORS,
  YOUTUBE_PENDING,
  YOUTUBE_POLL_MS,
  YOUTUBE_STEPS,
  discardYoutubeImport,
  fetchYoutubeImport,
  fetchYoutubeImportAvailability,
  startYoutubeImport,
  youtubeErrorMessage,
} from '@/utils/youtube.js'

const route = useRoute()
const router = useRouter()
const notification = useNotificationStore()

// From a playlist's Add music page: /upload?playlist=<id>&owner=<username> adds the song to it afterwards
const targetPlaylist = computed(() => (route.query.playlist && route.query.owner
  ? { id: String(route.query.playlist), owner: String(route.query.owner) }
  : null))
const backRoute = computed(() => {
  if (targetPlaylist.value) return { name: 'Add', params: { username: targetPlaylist.value.owner, id: targetPlaylist.value.id } }
  if (route.query.artist) return { name: 'Artist', params: { artistId: String(route.query.artist) } }
  return { name: 'Dashboard' }
})

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
const tags = ref([])
const tagsError = ref('')

// The title the page filled in itself (from a file name or a video title). A new file or video replaces it, but never
// a title the user typed
let autoTitle = ''
function suggestTitle(suggestion) {
  const current = title.value.trim()
  if (current && current !== autoTitle) return
  autoTitle = suggestion.slice(0, TITLE_MAX).trim()
  title.value = autoTitle
}

// Testers can take the audio from a YouTube video instead of an MP3: "Get audio" starts an import on the server, which
// is polled until it's READY, and the upload then sends its importId instead of a file. The tabs only show for
// testers (GET /api/imports/youtube says so). Each tab keeps its own choice; the upload uses the open one
const SOURCES = [{ id: 'mp3', label: 'MP3 file' }, { id: 'youtube', label: 'YouTube link' }]
const YOUTUBE_URL_MAX = 200
const source = ref('mp3')
const youtubeAvailable = ref(false)
const youtubeUrl = ref('')
const youtubeImport = ref(null)
const startingImport = ref(false)
const importReady = computed(() => youtubeImport.value?.status === 'READY')
let pollTimer = null

// The picked artist's albums. A song put on one uses its cover unless "Use the album cover" is unticked, and choosing
// a cover of your own unticks it. /upload?artist=<id>&album=<id> (an album's Add a song) starts with that album picked.
// "Create a new album…" opens the album dialog around this song: its cover starts as the song's, and the caller's other
// songs by the artist can join it. The album is made (or found, by name) just before the upload, which sends its
// albumId; with the album cover the song's own cover is left out
const NEW_ALBUM = 'new'
const albumDialog = ref(null)
const albums = ref([])
const albumId = ref('')
const draftAlbum = ref(null)
const useAlbumCover = ref(true)
const album = computed(() => {
  if (albumId.value === NEW_ALBUM) return draftAlbum.value ? { albumName: draftAlbum.value.albumName, coverUrl: draftAlbum.value.coverPreview } : null
  return albums.value.find((item) => item.albumId === albumId.value) ?? null
})
const usingAlbumCover = computed(() => Boolean(album.value && useAlbumCover.value))
const coverShown = computed(() => (usingAlbumCover.value ? resolveCoverURL(album.value.coverUrl) : coverPreview.value))
const albumHint = computed(() => {
  if (albumId.value === NEW_ALBUM && album.value) return `${album.value.albumName} is made with this song on it.`
  if (album.value) return `It's added to ${album.value.albumName}.`
  return albums.value.length ? `Or add it to one of ${artist.value.artistName}'s albums, or start a new one.` : 'Or start a new album with it.'
})

let albumsRequest = 0
watch(() => artist.value?.artistId, async (artistId) => {
  const id = ++albumsRequest
  albums.value = []
  dropDraft()
  if (!artistId) return (albumId.value = '')
  if (albumId.value === NEW_ALBUM) albumId.value = ''
  try {
    const items = await fetchArtistAlbums(artistId)
    if (id !== albumsRequest) return
    albums.value = items
    const wanted = albumId.value || String(route.query.album ?? '')
    albumId.value = items.some((item) => item.albumId === wanted) ? wanted : ''
  } catch {
    // Only None and a new album are offered
  }
})

watch(albumId, (value) => {
  if (value !== NEW_ALBUM) dropDraft()
  else if (!draftAlbum.value) openDraft()
})

function openDraft() {
  albumDialog.value.open(null, {
    draft: {
      songName: title.value.trim() || 'Your new song',
      coverFile: draftAlbum.value?.coverImage ?? coverFile.value,
      albumName: draftAlbum.value?.albumName ?? '',
      tracks: draftAlbum.value?.tracks ?? [],
    },
  })
}

function onDraft({ albumName, coverImage, tracks }) {
  dropDraft()
  draftAlbum.value = { albumName, coverImage, tracks, coverPreview: URL.createObjectURL(coverImage) }
  useAlbumCover.value = true
}

// Closing the dialog without an album goes back to None
function onDraftCancel() {
  if (!draftAlbum.value) albumId.value = ''
}

function dropDraft() {
  if (draftAlbum.value) URL.revokeObjectURL(draftAlbum.value.coverPreview)
  draftAlbum.value = null
}

const hasArtist = computed(() => Boolean(artist.value || artistPicker.value?.pendingName))
const hasCover = computed(() => usingAlbumCover.value || Boolean(coverFile.value))
const hasAudio = computed(() => (source.value === 'youtube' ? importReady.value : Boolean(mp3File.value)))
const canUpload = computed(() => !uploading.value && !checkingMp3.value && Boolean(hasAudio.value && hasCover.value && title.value.trim() && hasArtist.value))

onBeforeUnmount(() => {
  URL.revokeObjectURL(coverPreview.value)
  dropDraft()
  clearImport()
})

onMounted(async () => {
  youtubeAvailable.value = await fetchYoutubeImportAvailability()
})

async function getAudio() {
  const url = youtubeUrl.value.trim()
  if (!url || startingImport.value) return
  error.value = ''
  startingImport.value = true
  try {
    showImport(await startYoutubeImport(url))
  } catch (err) {
    if (err.sessionEnded) return
    error.value = err.code === 'RATE_LIMITED' ? "You're going too quickly. Wait a minute, then try again." : youtubeErrorMessage(err.code)
  } finally {
    startingImport.value = false
  }
}

function showImport(item) {
  clearTimeout(pollTimer)
  if (item.status === 'FAILED') {
    // The link stays in the box, so it can be fixed or tried again
    youtubeImport.value = null
    error.value = youtubeErrorMessage(item.error?.code)
    return
  }
  youtubeImport.value = item
  if (YOUTUBE_PENDING.includes(item.status)) pollTimer = setTimeout(poll, YOUTUBE_POLL_MS)
  else if (item.status === 'READY' && item.title) suggestTitle(item.title)
}

async function poll() {
  const current = youtubeImport.value
  if (!current) return
  try {
    const item = await fetchYoutubeImport(current.importId)
    // Cancelled, or replaced by another link, while this was in flight
    if (youtubeImport.value?.importId !== current.importId) return
    showImport(item)
  } catch (err) {
    if (youtubeImport.value?.importId !== current.importId || err.sessionEnded) return
    if (err.code === 'IMPORT_NOT_FOUND') {
      youtubeImport.value = null
      error.value = YOUTUBE_ERRORS.IMPORT_NOT_FOUND
      return
    }
    // A dropped request; the import is still running on the server
    pollTimer = setTimeout(poll, YOUTUBE_POLL_MS)
  }
}

// Cancels a running import or throws away an unused one, so it doesn't hold space on the server
function clearImport() {
  clearTimeout(pollTimer)
  if (youtubeImport.value) discardYoutubeImport(youtubeImport.value.importId)
  youtubeImport.value = null
}

// From an artist's Add music: /upload?artist=<artistId> starts with that artist picked. If it can't be loaded the
// picker just starts empty
onMounted(async () => {
  const artistId = route.query.artist
  if (!artistId) return
  try {
    const { artistId: id, artistName, artistPfpUrl } = await fetchArtist(String(artistId))
    if (!artist.value) artist.value = { artistId: id, artistName, artistPfpUrl }
  } catch {
    // Keep the empty picker
  }
})

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
  suggestTitle(file.name.replace(/\.mp3$/i, ''))
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
  useAlbumCover.value = false
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
    const newAlbum = albumId.value === NEW_ALBUM ? draftAlbum.value : null
    // A new album is made first so the song goes up with its albumId. An album with that name already is the one used
    let onAlbum = album.value
    if (newAlbum) {
      try {
        onAlbum = await findOrCreateAlbum(artist.value.artistId, { albumName: newAlbum.albumName, coverImage: newAlbum.coverImage })
      } catch (err) {
        if (err.sessionEnded) return
        error.value = PHOTO_ERRORS[err.code] ?? (err.code === 'RATE_LIMITED'
          ? "You're uploading too quickly. Wait a minute, then try again."
          : `We couldn't make ${newAlbum.albumName}, so the song wasn't uploaded. Try again.`)
        return
      }
    }
    const fromYoutube = source.value === 'youtube'
    const piece = await uploadMusicPiece({
      name: title.value.trim(),
      artistId: artist.value.artistId,
      isPublic: isPublic.value,
      mp3File: fromYoutube ? null : mp3File.value,
      importId: fromYoutube ? youtubeImport.value.importId : null,
      coverImage: usingAlbumCover.value ? null : coverFile.value,
      tags: [...tags.value],
      albumId: onAlbum?.albumId ?? null,
    })
    // The server used it up; nothing to discard on the way out
    if (fromYoutube) youtubeImport.value = null
    // The caller's other songs picked in the album dialog join it too, one at a time (they share the upload rate limit)
    let movesFailed = 0
    for (const track of newAlbum?.tracks ?? []) {
      await setPieceAlbum(track.musicPiece.musicPieceId, onAlbum.albumId, track.useAlbumCover).catch(() => { movesFailed += 1 })
    }
    if (targetPlaylist.value) {
      const result = await addPiecesToPlaylist(targetPlaylist.value.id, [piece.musicPieceId]).catch(() => null)
      if (result) notification[result.failedCount ? 'neutral' : 'success'](`Uploaded “${piece.name}”. ${additionMessage(result)}`)
      else notification.error(`Uploaded “${piece.name}”, but it couldn't be added to the playlist. Add it from your uploads.`)
      await router.push({ name: 'Playlist', params: { username: targetPlaylist.value.owner, id: targetPlaylist.value.id } })
    } else if (onAlbum) {
      if (movesFailed) notification.error(`Uploaded “${piece.name}” to ${onAlbum.albumName}, but ${movesFailed === 1 ? '1 other song' : `${movesFailed} other songs`} couldn't join it. Add them from the artist page.`)
      else notification.success(newAlbum ? `Uploaded “${piece.name}” and made the album ${onAlbum.albumName}.` : `Uploaded “${piece.name}” to ${onAlbum.albumName}.`)
      await router.push({ name: 'Artist', params: { artistId: artist.value.artistId } })
    } else {
      notification.success(`Uploaded “${piece.name}”. It's in My Uploads on your dashboard.`)
      await router.push({ name: 'Dashboard' })
    }
  } catch (err) {
    if (err.sessionEnded) return
    if (isTagsError(err)) tagsError.value = tagsErrorMessage(err)
    else if (source.value === 'youtube' && YOUTUBE_ERRORS[err.code] && !UPLOAD_ERRORS[err.code]) {
      error.value = YOUTUBE_ERRORS[err.code]
      if (err.code === 'IMPORT_NOT_FOUND') youtubeImport.value = null
    } else if (UPLOAD_ERRORS[err.code]) error.value = UPLOAD_ERRORS[err.code]
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
.select-wrap { display: block; }
.draft { align-items: center; background: var(--surface-alt); border-radius: var(--radius-sm); display: flex; gap: var(--space-3); padding: var(--space-2) var(--space-3); }
.draft-cover { border-radius: var(--radius-sm); flex: none; height: 40px; object-fit: cover; width: 40px; }
.draft-text { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.draft-name { font: 600 15px/22px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.btn.small { flex: none; font-size: 14px; padding: 6px 12px; }
.check input { accent-color: var(--primary); height: 16px; margin: 0; width: 16px; }
.actions { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-3); margin-top: var(--space-2); }
.file-row { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-3); }
.file-meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); overflow-wrap: anywhere; }
.file-row:has(input[type="file"]:focus-visible) .btn { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.url { flex: 1; min-width: 0; }
.tabs { display: flex; gap: var(--space-6); }
.tab { background: none; border: 0; border-bottom: 3px solid transparent; color: var(--ink-muted); cursor: pointer; font: 600 14px/20px var(--font-sans); padding: 0 0 var(--space-2); }
.tab:hover { border-bottom-color: var(--primary); color: var(--ink); }
.tab.active { border-bottom-color: var(--primary); color: var(--link); }
.tab[disabled] { cursor: not-allowed; opacity: 0.4; }
.import { align-items: center; background: var(--surface-alt); border-radius: var(--radius-sm); display: flex; gap: var(--space-3); padding: var(--space-2) var(--space-3); }
.import-thumb { aspect-ratio: 16 / 9; border-radius: var(--radius-sm); flex: none; object-fit: cover; width: 64px; }
.import-text { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.import-title { font: 600 15px/22px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
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
