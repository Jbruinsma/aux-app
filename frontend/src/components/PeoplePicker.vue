<template>
  <div class="picker">
    <label class="label" :for="inputId">Find people</label>
    <input
      :id="inputId"
      v-model="query"
      class="input"
      type="search"
      autocomplete="off"
      maxlength="200"
      placeholder="Search by username"
      :aria-describedby="`${inputId}-status`"
      :aria-controls="`${inputId}-results`"
    />
    <p :id="`${inputId}-status`" class="hint" aria-live="polite">{{ statusText }}</p>
    <ul v-if="shown.length" :id="`${inputId}-results`" class="results">
      <li v-for="user in shown" :key="user.userId">
        <button type="button" class="result" @click="pick(user)">
          <img v-if="user.profilePictureUrl" class="avatar" :src="resolveCoverURL(user.profilePictureUrl)" alt="" />
          <span v-else class="avatar avatar-initial" aria-hidden="true">{{ user.username.charAt(0).toUpperCase() }}</span>
          <span class="name">{{ user.username }}</span>
          <span class="add">Add</span>
        </button>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, useId, watch } from 'vue'
import { fetchAPI } from '@/utils/api.js'
import { resolveCoverURL } from '@/utils/display.js'
import { API_BASE_URL } from '@/utils/variables.js'

const props = defineProps({
  exclude: { type: Array, default: () => [] },
})
const emit = defineEmits(['pick'])

const inputId = `people-${useId()}`
const query = ref('')
const results = ref([])
const loading = ref(false)
const error = ref('')
let timer = null
let requestId = 0

const trimmed = computed(() => query.value.trim())
const shown = computed(() => results.value.filter((user) => !props.exclude.includes(user.userId)))
const statusText = computed(() => {
  if (!trimmed.value) return 'Type a username to find someone.'
  if (error.value) return error.value
  if (loading.value) return 'Searching…'
  if (!shown.value.length) return `No one else matches “${trimmed.value}”.`
  return shown.value.length === 1 ? '1 person found.' : `${shown.value.length} people found.`
})

// GET /api/core/search, category USERS: { results: [{ userId, username, profilePictureUrl }], totalResults }
async function search(text) {
  const id = ++requestId
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ category: 'USERS', query: text, limit: 8, offset: 0 })
    const page = await fetchAPI(`${API_BASE_URL}/api/core/search?${params}`, { notifyOnError: false })
    if (id === requestId) results.value = page.results
  } catch (err) {
    if (id !== requestId || err.sessionEnded) return
    results.value = []
    error.value = err.code === 'RATE_LIMITED'
      ? 'Too many searches at once. Wait a few seconds, then try again.'
      : "Search isn't working right now. Check that the server is running, then try again."
  } finally {
    if (id === requestId) loading.value = false
  }
}

watch(trimmed, (text) => {
  clearTimeout(timer)
  if (!text) {
    requestId++
    results.value = []
    loading.value = false
    error.value = ''
    return
  }
  loading.value = true
  timer = setTimeout(() => search(text), 300)
})

onBeforeUnmount(() => clearTimeout(timer))

function pick(user) {
  emit('pick', user)
  query.value = ''
}
</script>

<style scoped>
.picker { display: flex; flex-direction: column; gap: var(--space-2); }
.label { font: 600 14px/20px var(--font-sans); }
.input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px 12px; width: 100%; }
.hint { color: var(--ink-muted); font: 500 12px/16px var(--font-sans); margin: 0; }
.results { border: 1px solid var(--line); border-radius: var(--radius-sm); list-style: none; margin: 0; max-height: 280px; overflow-y: auto; padding: 0; }
.results li + li { border-top: 1px solid var(--line); }
.result { align-items: center; background: none; border: 0; color: var(--ink); cursor: pointer; display: flex; font: inherit; gap: var(--space-3); padding: var(--space-2) var(--space-3); text-align: left; width: 100%; }
.result:hover { background: var(--surface-alt); }
.avatar { border-radius: var(--radius-pill); flex: none; height: 32px; object-fit: cover; width: 32px; }
.avatar-initial { background: var(--primary); color: var(--on-primary); display: grid; font: 700 14px/1 var(--font-sans); place-items: center; }
.name { flex: 1; font: 600 16px/24px var(--font-sans); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.add { color: var(--link); font: 600 14px/20px var(--font-sans); }
.result:hover .add { text-decoration: underline; }
</style>
