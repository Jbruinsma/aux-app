<template>
  <div class="aux-page with-player">
    <AppHeader />
    <main class="home">
      <section class="welcome">
        <img v-if="avatarUrl" class="av av-img" :src="avatarUrl" alt="" />
        <div v-else class="av" aria-hidden="true">{{ initial }}</div>
        <div class="welcome-text">
          <h1>Welcome back, {{ username }}</h1>
          <p class="meta">{{ librarySummary }}</p>
        </div>
        <router-link to="/create" class="btn primary">Create playlist</router-link>
      </section>

      <form class="search" role="search" @submit.prevent>
        <label class="sr-only" for="dash-search">Search Aux</label>
        <input
          id="dash-search"
          v-model="searchText"
          class="input search-input"
          type="search"
          autocomplete="off"
          maxlength="200"
          placeholder="Search people, music and playlists"
        />
      </form>

      <div class="layout">
        <div class="main-col">
          <section v-if="searching" aria-labelledby="results-heading">
            <div class="section-head">
              <h2 id="results-heading">Results</h2>
              <div class="tabs" role="tablist" aria-label="Search categories">
                <button
                  v-for="item in SEARCH_TABS"
                  :key="item.id"
                  type="button"
                  role="tab"
                  class="tab"
                  :class="{ active: category === item.id }"
                  :aria-selected="category === item.id ? 'true' : 'false'"
                  @click="category = item.id"
                >{{ item.label }}</button>
              </div>
            </div>

            <p v-if="search.error" class="error" role="alert">{{ search.error }}</p>
            <p v-else-if="search.loading && !search.results.length" class="meta">Searching…</p>
            <EmptyState
              v-else-if="!search.results.length"
              :icon="activeTab.icon"
              :title="`No ${activeTab.label.toLowerCase()} match “${trimmedQuery}”`"
              text="Check the spelling or try a shorter search."
            />
            <template v-else>
              <p class="meta count" aria-live="polite">{{ resultCount }}</p>

              <ul v-if="category === 'USERS'" class="rows">
                <li v-for="user in search.results" :key="user.userId">
                  <router-link class="row row-link" :to="{ name: 'Profile', params: { username: user.username } }">
                    <img v-if="user.profilePictureUrl" class="row-av" :src="resolveCoverURL(user.profilePictureUrl)" alt="" />
                    <span v-else class="row-av row-av-empty" aria-hidden="true">{{ user.username.charAt(0).toUpperCase() }}</span>
                    <span class="row-title">{{ user.username }}</span>
                  </router-link>
                </li>
              </ul>

              <!-- Songs have no page or playback yet, so rows aren't clickable -->
              <ul v-else-if="category === 'MUSIC'" class="rows">
                <li v-for="piece in search.results" :key="piece.musicPieceId" class="row">
                  <img v-if="piece.coverUrl" class="row-art" :src="resolveCoverURL(piece.coverUrl)" alt="" />
                  <span v-else class="row-art" aria-hidden="true"></span>
                  <span class="row-text">
                    <span class="row-title">{{ piece.name }}</span>
                    <span v-if="piece.artistSummary?.artistName" class="row-sub">{{ piece.artistSummary.artistName }}</span>
                  </span>
                </li>
              </ul>

              <PlaylistGrid v-else :playlists="search.results" :owner="username" />

              <button
                v-if="search.results.length < search.total"
                type="button"
                class="btn secondary more"
                :disabled="search.loading"
                @click="loadMore"
              >{{ search.loading ? 'Loading…' : 'Show more' }}</button>
            </template>
          </section>

          <section v-else aria-labelledby="library-heading">
            <div class="section-head">
              <h2 id="library-heading">Your library</h2>
              <div class="tabs" role="tablist" aria-label="Library filter">
                <button
                  v-for="item in LIBRARY_TABS"
                  :key="item.id"
                  type="button"
                  role="tab"
                  class="tab"
                  :class="{ active: libraryFilter === item.id }"
                  :aria-selected="libraryFilter === item.id ? 'true' : 'false'"
                  @click="libraryFilter = item.id"
                >{{ item.label }}</button>
              </div>
            </div>

            <p v-if="libraryError" class="error" role="alert">{{ libraryError }}</p>
            <p v-else-if="libraryLoading" class="meta">Loading your library…</p>
            <EmptyState
              v-else-if="filteredLibrary.length === 0"
              :title="emptyLibrary.title"
              :text="emptyLibrary.text"
            />
            <PlaylistGrid v-else :playlists="filteredLibrary" :owner="username" />
          </section>

          <section aria-labelledby="recent-heading">
            <h2 id="recent-heading">Recent listening</h2>
            <EmptyState
              title="No plays yet"
              text="Play a song from one of your playlists and it shows up here."
            />
          </section>

          <section aria-labelledby="artists-heading">
            <h2 id="artists-heading">Top artists</h2>
            <EmptyState
              icon="mic"
              title="No top artists yet"
              text="Your most-played artists appear here once you've listened to a few songs."
            />
          </section>
        </div>

        <aside class="side">
          <section class="panel" aria-labelledby="match-heading">
            <h2 id="match-heading">People who hear it too</h2>
            <p class="meta">
              Aux matches you with listeners who share your favorite artists and genres, and shows your
              compatibility score with each one.
            </p>
            <div class="meter" aria-hidden="true">
              <span class="seg"></span><span class="seg"></span><span class="seg"></span>
              <span class="seg"></span><span class="seg"></span>
            </div>
            <p class="meta">Matches show up here once you've listened to a few songs.</p>
          </section>
        </aside>
      </div>
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import EmptyState from '@/components/EmptyState.vue'
import PlaylistGrid from '@/components/PlaylistGrid.vue'
import { fetchAPI } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'
import { resolveCoverURL } from '@/utils/display.js'
import { API_BASE_URL } from '@/utils/variables.js'

const SEARCH_TABS = [
  { id: 'USERS', label: 'People', icon: 'users' },
  { id: 'MUSIC', label: 'Music', icon: 'disc' },
  { id: 'PLAYLISTS', label: 'Playlists', icon: 'music' },
]
const LIBRARY_TABS = [
  { id: 'all', label: 'All' },
  { id: 'yours', label: 'Yours' },
  { id: 'saved', label: 'Saved' },
]
const PAGE_SIZE = 20
const DEBOUNCE_MS = 300

const router = useRouter()
const userStore = useUserStore()

const username = computed(() => userStore.userData?.username ?? '')
const initial = computed(() => username.value.charAt(0).toUpperCase())
const avatarUrl = computed(() => {
  const url = userStore.userData?.profilePictureUrl
  return url ? resolveCoverURL(url) : null
})

// Library: GET /api/users/library. `isSaved` means saved from someone else
const library = ref([])
const libraryLoading = ref(true)
const libraryError = ref('')
const libraryFilter = ref('all')

const filteredLibrary = computed(() => {
  if (libraryFilter.value === 'yours') return library.value.filter((p) => !p.isSaved)
  if (libraryFilter.value === 'saved') return library.value.filter((p) => p.isSaved)
  return library.value
})

const librarySummary = computed(() => {
  if (libraryLoading.value || libraryError.value) return 'Your music, your people.'
  const saved = library.value.filter((p) => p.isSaved).length
  const own = library.value.length - saved
  const ownText = own === 1 ? '1 playlist' : `${own} playlists`
  return saved ? `${ownText} · ${saved} saved` : ownText
})

const emptyLibrary = computed(() => {
  if (libraryFilter.value === 'saved') {
    return { title: 'Nothing saved yet', text: 'Save a playlist from someone else and it shows up here.' }
  }
  return { title: 'No playlists yet', text: 'Create a playlist and upload an MP3 to start your library.' }
})

// Search: GET /api/core/search, one category at a time
const searchText = ref('')
const category = ref('USERS')
const search = reactive({ results: [], total: 0, loading: false, error: '' })
const trimmedQuery = computed(() => searchText.value.trim())
const searching = computed(() => trimmedQuery.value.length > 0)
const activeTab = computed(() => SEARCH_TABS.find((t) => t.id === category.value))
const resultCount = computed(() => (search.total === 1 ? '1 result' : `${search.total} results`))

let requestId = 0
let debounceTimer = null

async function runSearch(offset) {
  const id = ++requestId
  search.loading = true
  search.error = ''
  const params = new URLSearchParams({
    category: category.value,
    query: trimmedQuery.value,
    limit: PAGE_SIZE,
    offset,
  })
  try {
    const page = await fetchAPI(`${API_BASE_URL}/api/core/search?${params}`)
    if (id !== requestId) return
    search.results = offset === 0 ? page.results : [...search.results, ...page.results]
    search.total = page.totalResults
  } catch (err) {
    if (id !== requestId) return
    search.error = err.code === 'RATE_LIMITED'
      ? 'Too many searches at once. Wait a few seconds, then try again.'
      : "Search isn't working right now. Check that the server is running, then try again."
  } finally {
    if (id === requestId) search.loading = false
  }
}

function loadMore() {
  runSearch(search.results.length)
}

watch(trimmedQuery, (query) => {
  clearTimeout(debounceTimer)
  if (!query) {
    requestId++
    Object.assign(search, { results: [], total: 0, loading: false, error: '' })
    return
  }
  search.loading = true
  debounceTimer = setTimeout(() => runSearch(0), DEBOUNCE_MS)
})

watch(category, () => {
  clearTimeout(debounceTimer)
  search.results = []
  if (searching.value) runSearch(0)
})

onMounted(async () => {
  if (!username.value) {
    await router.push({ name: 'Login', query: { redirect: '/dashboard' } })
    return
  }

  try {
    library.value = await fetchAPI(`${API_BASE_URL}/api/users/library`)
  } catch {
    libraryError.value = "We couldn't load your library. Check that the server is running, then refresh the page."
  } finally {
    libraryLoading.value = false
  }
})
</script>

<style scoped>
.home { width: 100%; padding: 0 var(--page-gutter) var(--space-8); }
h1 { font: 700 28px/34px var(--font-sans); margin: 0; }
h2 { font: 700 20px/28px var(--font-sans); margin: 0 0 var(--space-3); }
.meta { font: 500 14px/20px var(--font-sans); color: var(--ink-muted); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.sr-only { clip: rect(0 0 0 0); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

.welcome { display: flex; align-items: center; gap: var(--space-4); padding: var(--space-8) 0 var(--space-6); border-bottom: 1px solid var(--line); }
.welcome-text { flex: 1; min-width: 0; }
.av { width: 64px; height: 64px; border-radius: var(--radius-pill); background: var(--primary); color: var(--on-primary); display: grid; place-items: center; font: 700 24px/1 var(--font-sans); flex: none; }
.av-img { object-fit: cover; background: var(--surface-alt); }

.search { padding-top: var(--space-6); }
.input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px 12px; }
.search-input { padding: 10px 16px; width: 100%; }

.layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: var(--space-8); padding-top: var(--space-6); }
.main-col { display: flex; flex-direction: column; gap: var(--space-8); }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); margin-bottom: var(--space-4); flex-wrap: wrap; }
.section-head h2 { margin: 0; }

.tabs { display: flex; gap: var(--space-6); }
.tab { background: none; border: 0; border-bottom: 3px solid transparent; color: var(--ink-muted); cursor: pointer; font: 600 14px/20px var(--font-sans); padding: 0 0 var(--space-2); }
.tab:hover { border-bottom-color: var(--primary); color: var(--ink); }
.tab.active { border-bottom-color: var(--primary); color: var(--link); }

.count { margin-bottom: var(--space-3); }
.rows { list-style: none; margin: 0; padding: 0; }
.row { align-items: center; border-bottom: 1px solid var(--line); display: flex; gap: var(--space-3); padding: var(--space-2) var(--space-3); }
.rows li:first-child .row, .rows li.row:first-child { border-top: 1px solid var(--line); }
.row-link { color: var(--ink); text-decoration: none; }
.row-link:hover { background: var(--surface-alt); }
.row-link:hover .row-title { color: var(--link); text-decoration: underline; }
.row-av { background: var(--surface-alt); border-radius: var(--radius-pill); flex: none; height: 40px; object-fit: cover; width: 40px; }
.row-av-empty { background: var(--primary); color: var(--on-primary); display: grid; font: 700 16px/1 var(--font-sans); place-items: center; }
.row-art { background: var(--surface-alt); border-radius: var(--radius-sm); flex: none; height: 40px; object-fit: cover; width: 40px; }
.row-text { display: flex; flex-direction: column; min-width: 0; }
.row-title { font: 600 15px/22px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row-sub { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.more { margin-top: var(--space-4); }

.side { display: flex; flex-direction: column; gap: var(--space-4); }
.panel { background: var(--surface-alt); border-radius: var(--radius-md); padding: var(--space-4); display: flex; flex-direction: column; gap: var(--space-3); }
.panel h2 { margin: 0; }
.meter { display: flex; gap: var(--space-1); }
.seg { flex: 1; height: 8px; border-radius: 2px; background: var(--surface); }

@media (max-width: 860px) {
  .layout { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .welcome { flex-wrap: wrap; padding-top: var(--space-6); }
  .av { width: 48px; height: 48px; font-size: 19px; }
}
</style>
