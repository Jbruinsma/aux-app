<template>
  <div class="aux-page with-player">
    <AppHeader />
    <main class="artists-page">
      <section class="hero">
        <div class="wrap hero-in">
          <div>
            <h1>Artists</h1>
            <p class="meta">Find an artist, play their most popular songs, or add music to them.</p>
          </div>
          <router-link :to="{ name: 'Upload' }" class="btn secondary">Upload music</router-link>
        </div>
      </section>

      <div class="search-bar">
        <div class="wrap">
          <form class="search" role="search" @submit.prevent>
            <label class="sr-only" for="artists-search">Search artists</label>
            <svg class="search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
            </svg>
            <input
              id="artists-search"
              v-model="searchText"
              class="search-input"
              type="search"
              autocomplete="off"
              :maxlength="ARTIST_NAME_MAX"
              placeholder="Search artists"
            />
          </form>
        </div>
      </div>

      <div class="wrap body">
        <section v-if="query" aria-labelledby="results-heading">
          <h2 id="results-heading">Artists matching “{{ query }}”</h2>
          <EmptyState v-if="results.error" tone="error" icon="mic" :title="results.error" text="Check that the server is running, then try again." />
          <EmptyState v-else-if="results.loading && !results.items.length" tone="loading" icon="mic" title="Searching…" />
          <EmptyState
            v-else-if="!results.items.length"
            icon="mic"
            :title="`No artists match “${query}”`"
            text="Check the spelling, or upload a song and create the artist."
          >
            <router-link :to="{ name: 'Upload' }" class="btn secondary">Upload music</router-link>
          </EmptyState>
          <template v-else>
            <ArtistGrid :artists="results.items" />
            <p v-if="results.items.length >= SEARCH_LIMIT" class="meta note">Showing the first {{ SEARCH_LIMIT }}. Type more of the name to narrow it down.</p>
          </template>
        </section>

        <template v-else>
          <section v-if="!ARTIST_DIRECTORY_MOCKED" aria-labelledby="all-heading">
            <h2 id="all-heading">All artists</h2>
            <EmptyState v-if="directory.error" tone="error" icon="mic" :title="directory.error" text="Check that the server is running, then try again." />
            <EmptyState v-else-if="directory.loading && !directory.items.length" tone="loading" icon="mic" title="Loading artists…" />
            <EmptyState v-else-if="!directory.items.length" icon="mic" title="No artists yet" text="Upload a song and create its artist." />
            <template v-else>
              <ArtistGrid :artists="directory.items" />
              <button v-if="directory.nextCursor" type="button" class="btn secondary more" :disabled="directory.loading" @click="loadDirectory(directory.nextCursor)">
                {{ directory.loading ? 'Loading…' : 'Show more' }}
              </button>
            </template>
          </section>

          <section aria-labelledby="favorites-heading">
            <h2 id="favorites-heading">Your favorite artists</h2>
            <p v-if="ARTIST_DIRECTORY_MOCKED" class="meta lead">Browsing every artist on Aux is coming soon. Until then, search for one by name.</p>
            <EmptyState v-if="favorites.error" tone="error" icon="mic" :title="favorites.error" text="Check that the server is running, then refresh the page." />
            <EmptyState v-else-if="favorites.loading" tone="loading" icon="mic" title="Loading your favorite artists…" />
            <EmptyState
              v-else-if="!favorites.items.length"
              icon="mic"
              title="No favorite artists yet"
              text="Search for an artist and favorite them from their page. They show up here."
            />
            <ArtistGrid v-else :artists="favorites.items" />
          </section>
        </template>
      </div>
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import ArtistGrid from '@/components/ArtistGrid.vue'
import EmptyState from '@/components/EmptyState.vue'
import { ARTIST_DIRECTORY_MOCKED, fetchAllArtists, fetchFavoriteArtists } from '@/utils/artist.js'
import { ARTIST_NAME_MAX, searchArtists } from '@/utils/music.js'
import { useUserStore } from '@/stores/user.js'

// The Artists tab. Typing searches artists by name (GET /api/artists?q=, at most 20). With nothing typed it lists every
// artist once the backend can (ARTIST_DIRECTORY_MOCKED), and the caller's favorite artists
const SEARCH_LIMIT = 20
const DEBOUNCE_MS = 300

const userStore = useUserStore()
const searchText = ref('')
const query = computed(() => searchText.value.trim())

const results = reactive({ items: [], loading: false, error: '' })
let searchId = 0
let debounce = null

async function runSearch(text) {
  const id = ++searchId
  results.loading = true
  results.error = ''
  try {
    const items = await searchArtists(text)
    if (id === searchId) results.items = items ?? []
  } catch (err) {
    if (err.sessionEnded || id !== searchId) return
    results.error = err.code === 'RATE_LIMITED'
      ? 'Too many searches at once. Wait a few seconds, then try again.'
      : "Artist search isn't working right now."
  } finally {
    if (id === searchId) results.loading = false
  }
}

watch(query, (text) => {
  clearTimeout(debounce)
  searchId++
  Object.assign(results, { items: [], loading: Boolean(text), error: '' })
  if (text) debounce = setTimeout(() => runSearch(text), DEBOUNCE_MS)
})

onBeforeUnmount(() => clearTimeout(debounce))

const favorites = reactive({ items: [], loading: true, error: '' })

async function loadFavorites() {
  try {
    favorites.items = (await fetchFavoriteArtists(userStore.userData.username)) ?? []
  } catch (err) {
    if (!err.sessionEnded) favorites.error = "We couldn't load your favorite artists."
  } finally {
    favorites.loading = false
  }
}

const directory = reactive({ items: [], nextCursor: null, loading: false, error: '' })

async function loadDirectory(cursor = null) {
  directory.loading = true
  directory.error = ''
  try {
    const page = await fetchAllArtists(cursor)
    directory.items = cursor ? [...directory.items, ...page.artists] : page.artists
    directory.nextCursor = page.nextCursor ?? null
  } catch (err) {
    if (!err.sessionEnded) directory.error = "We couldn't load the artists."
  } finally {
    directory.loading = false
  }
}

onMounted(() => {
  if (userStore.userData?.username) loadFavorites()
  else favorites.loading = false
  if (!ARTIST_DIRECTORY_MOCKED) loadDirectory()
})
</script>

<style scoped>
h1 { font: 700 28px/34px var(--font-sans); margin: 0; }
h2 { font: 700 20px/28px var(--font-sans); margin: 0 0 var(--space-4); overflow-wrap: anywhere; }
.meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.sr-only { clip: rect(0 0 0 0); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }
.wrap { padding-left: var(--page-gutter); padding-right: var(--page-gutter); }
.hero { padding-top: var(--space-8); }
.hero-in { align-items: flex-end; display: flex; flex-wrap: wrap; gap: var(--space-4); justify-content: space-between; }
.hero .meta { margin-top: var(--space-1); }
.search-bar { background: var(--surface); border-bottom: 1px solid var(--line); padding: var(--space-4) 0; position: sticky; top: calc(var(--header-height) + env(safe-area-inset-top, 0px)); z-index: 4; }
.search { max-width: 640px; position: relative; }
.search-icon { color: var(--ink-muted); left: var(--space-3); pointer-events: none; position: absolute; top: 50%; transform: translateY(-50%); }
.search-input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px var(--space-3) 8px 40px; width: 100%; }
.search-input:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.body { display: flex; flex-direction: column; gap: var(--space-8); padding-bottom: var(--space-8); padding-top: var(--space-8); }
.lead { margin: calc(var(--space-2) * -1) 0 var(--space-4); }
.note { margin-top: var(--space-4); }
.more { margin-top: var(--space-6); }
</style>
