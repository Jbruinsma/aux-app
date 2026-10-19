<template>
  <div class="artist-picker">
    <label class="label" :for="inputId">Artist</label>

    <div v-if="modelValue" class="chosen">
      <img v-if="modelValue.artistPfpUrl" class="avatar" :src="resolveCoverURL(modelValue.artistPfpUrl)" alt="" />
      <span v-else class="avatar avatar-initial" aria-hidden="true">{{ modelValue.artistName.charAt(0).toUpperCase() }}</span>
      <span class="name">{{ modelValue.artistName }}</span>
      <button type="button" class="btn text change" @click="clear">Change</button>
    </div>

    <template v-else>
      <input
        :id="inputId"
        v-model="query"
        class="input"
        type="search"
        autocomplete="off"
        :maxlength="ARTIST_NAME_MAX"
        placeholder="Search artists"
        :aria-describedby="`${inputId}-status`"
      />
      <p :id="`${inputId}-status`" class="hint" aria-live="polite">{{ statusText }}</p>
      <ul v-if="trimmed && !loading && (results.length || canCreate)" class="results">
        <li v-for="artist in results" :key="artist.artistId">
          <button type="button" class="result" @click="choose(artist)">
            <img v-if="artist.artistPfpUrl" class="avatar" :src="resolveCoverURL(artist.artistPfpUrl)" alt="" />
            <span v-else class="avatar avatar-initial" aria-hidden="true">{{ artist.artistName.charAt(0).toUpperCase() }}</span>
            <span class="name">{{ artist.artistName }}</span>
          </button>
        </li>
        <li v-if="canCreate">
          <button type="button" class="result create" :disabled="creating" @click="create">
            <span class="avatar avatar-add" aria-hidden="true">+</span>
            <span class="name">{{ creating ? 'Creating…' : `Create artist “${trimmed}”` }}</span>
          </button>
        </li>
      </ul>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, useId, watch } from 'vue'
import { resolveCoverURL } from '@/utils/display.js'
import { ARTIST_NAME_MAX, findOrCreateArtist, searchArtists } from '@/utils/music.js'

defineProps({
  // The chosen ArtistSummary, or null
  modelValue: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue'])

const inputId = `artist-${useId()}`
const query = ref('')
const results = ref([])
const loading = ref(false)
const creating = ref(false)
const error = ref('')
let timer = null
let requestId = 0

const trimmed = computed(() => query.value.trim())
const exactMatch = computed(() => results.value.some((a) => a.artistName.toLowerCase() === trimmed.value.toLowerCase()))
const canCreate = computed(() => Boolean(trimmed.value) && !exactMatch.value)
const statusText = computed(() => {
  if (!trimmed.value) return "Find the artist, or add them if they're not on Aux yet."
  if (loading.value) return 'Searching…'
  if (!results.value.length) return `No artist called “${trimmed.value}” yet.`
  return results.value.length === 1 ? '1 artist found.' : `${results.value.length} artists found.`
})

watch(trimmed, (text) => {
  clearTimeout(timer)
  error.value = ''
  const id = ++requestId
  if (!text) {
    results.value = []
    loading.value = false
    return
  }
  loading.value = true
  timer = setTimeout(async () => {
    try {
      const artists = await searchArtists(text)
      if (id === requestId) results.value = artists
    } catch (err) {
      if (id === requestId && !err.sessionEnded) {
        results.value = []
        error.value = "Artist search isn't working right now. Check that the server is running, then try again."
      }
    } finally {
      if (id === requestId) loading.value = false
    }
  }, 300)
})

onBeforeUnmount(() => clearTimeout(timer))

function choose(artist) {
  emit('update:modelValue', artist)
  query.value = ''
}

function clear() {
  emit('update:modelValue', null)
}

// Returns the existing artist if someone already made one with this name
async function create() {
  if (!canCreate.value || creating.value) return
  creating.value = true
  error.value = ''
  try {
    choose(await findOrCreateArtist(trimmed.value))
  } catch (err) {
    if (err.sessionEnded) return
    error.value = err.code === 'CONFLICT'
      ? 'Someone added that artist at the same moment. Try again.'
      : err.code === 'INVALID_FIELD'
        ? `Use 1 to ${ARTIST_NAME_MAX} characters for the artist's name.`
        : "We couldn't add that artist. Check that the server is running, then try again."
  } finally {
    creating.value = false
  }
}

defineExpose({ pendingName: trimmed, create })
</script>

<style scoped>
.artist-picker { display: flex; flex-direction: column; gap: var(--space-2); }
.label { font: 600 14px/20px var(--font-sans); }
.input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px 12px; width: 100%; }
.hint { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.chosen { align-items: center; background: var(--surface-alt); border-radius: var(--radius-sm); display: flex; gap: var(--space-3); padding: var(--space-2) var(--space-3); }
.change { font-size: 14px; margin-left: auto; }
.results { border: 1px solid var(--line); border-radius: var(--radius-sm); list-style: none; margin: 0; max-height: 260px; overflow-y: auto; padding: 0; }
.results li + li { border-top: 1px solid var(--line); }
.result { align-items: center; background: none; border: 0; color: var(--ink); cursor: pointer; display: flex; font: inherit; gap: var(--space-3); padding: var(--space-2) var(--space-3); text-align: left; width: 100%; }
.result:hover { background: var(--surface-alt); }
.create .name { color: var(--link); }
.avatar { border-radius: var(--radius-pill); flex: none; height: 32px; object-fit: cover; width: 32px; }
.avatar-initial { background: var(--primary); color: var(--on-primary); display: grid; font: 700 14px/1 var(--font-sans); place-items: center; }
.avatar-add { background: var(--purple-soft); color: var(--link); display: grid; font: 700 18px/1 var(--font-sans); place-items: center; }
.name { flex: 1; font: 600 16px/24px var(--font-sans); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
