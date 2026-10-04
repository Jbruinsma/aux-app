<template>
  <div class="aux-page with-player">
    <AppHeader />
    <main>
      <section class="hero">
        <div class="wrap hero-in">
          <div class="panel hero-text">
            <h1 class="greeting">Welcome back, {{ username }}</h1>
            <p class="lead">{{ librarySummary }}</p>

            <form class="search" role="search" @submit.prevent>
              <label class="sr-only" for="dash-search">Search Aux</label>
              <svg class="search-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
              </svg>
              <input
                id="dash-search"
                v-model="searchText"
                class="search-input"
                type="search"
                autocomplete="off"
                maxlength="200"
                placeholder="Search people, music and playlists"
              />
            </form>
          </div>

          <!-- No listening data yet: ghost shapes show what each section will hold -->
          <div class="soon">
            <section class="panel" aria-labelledby="recent-heading">
              <h2 id="recent-heading">Recent listening</h2>
              <ul class="ghost-rows" aria-hidden="true">
                <li v-for="n in 4" :key="n" class="ghost-row">
                  <span class="ghost-art"></span>
                  <span class="ghost-lines"><span class="ghost-line"></span><span class="ghost-line short"></span></span>
                </li>
              </ul>
              <p class="meta">Play a song from one of your playlists and it shows up here.</p>
            </section>

            <section class="panel" aria-labelledby="artists-heading">
              <h2 id="artists-heading">Top artists</h2>
              <ul class="ghost-artists" aria-hidden="true">
                <li v-for="n in 4" :key="n" class="ghost-artist">
                  <span class="ghost-circle"></span><span class="ghost-line short"></span>
                </li>
              </ul>
              <p class="meta">Your most-played artists appear here once you've listened to a few songs.</p>
            </section>

            <section class="panel" aria-labelledby="match-heading">
              <h2 id="match-heading">People who hear it too</h2>
              <ul class="ghost-rows" aria-hidden="true">
                <li v-for="n in 3" :key="n" class="ghost-row">
                  <span class="ghost-circle small"></span>
                  <span class="ghost-lines"><span class="ghost-line"></span></span>
                  <span class="ghost-badge"></span>
                </li>
              </ul>
              <p class="meta">Aux matches you with listeners who share your favorite artists and shows your compatibility score.</p>
            </section>
          </div>
        </div>
      </section>

      <div class="wrap below">
        <section v-if="searching" class="block" aria-labelledby="results-heading">
          <div class="section-head">
            <h2 id="results-heading">Results for “{{ trimmedQuery }}”</h2>
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

            <ul v-if="category === 'USERS'" class="people">
              <li v-for="user in search.results" :key="user.userId">
                <router-link class="person" :to="{ name: 'Profile', params: { username: user.username } }">
                  <img v-if="user.profilePictureUrl" class="person-av" :src="resolveCoverURL(user.profilePictureUrl)" alt="" />
                  <span v-else class="person-av person-av-empty" aria-hidden="true">{{ user.username.charAt(0).toUpperCase() }}</span>
                  <span class="person-name">{{ user.username }}</span>
                </router-link>
              </li>
            </ul>

            <!-- Songs have no page or playback yet, so rows aren't clickable -->
            <ul v-else-if="category === 'MUSIC'" class="songs">
              <li v-for="piece in search.results" :key="piece.musicPieceId" class="song">
                <img v-if="piece.coverUrl" class="song-art" :src="resolveCoverURL(piece.coverUrl)" alt="" />
                <span v-else class="song-art" aria-hidden="true"></span>
                <span class="song-text">
                  <span class="song-title">{{ piece.name }}</span>
                  <span v-if="piece.artistSummary?.artistName" class="song-artist">{{ piece.artistSummary.artistName }}</span>
                </span>
              </li>
            </ul>

            <PlaylistGrid v-else class="big-grid" :playlists="search.results" :owner="username" />

            <button
              v-if="search.results.length < search.total"
              type="button"
              class="btn secondary more"
              :disabled="search.loading"
              @click="loadMore"
            >{{ search.loading ? 'Loading…' : 'Show more' }}</button>
          </template>
        </section>

        <section v-else class="block" aria-labelledby="library-heading">
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
            v-else-if="libraryFilter === 'saved' && filteredLibrary.length === 0"
            title="Nothing saved yet"
            text="Save a playlist from someone else and it shows up here."
          />
          <PlaylistGrid v-else class="big-grid" :playlists="filteredLibrary" :owner="username">
            <li v-if="libraryFilter !== 'saved'">
              <button type="button" class="new-tile" @click="createDialog.open()">
                <span class="new-cover">
                  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M12 5v14" /><path d="M5 12h14" />
                  </svg>
                </span>
                <span class="new-name">Create</span>
              </button>
            </li>
          </PlaylistGrid>
        </section>

      </div>
    </main>
    <AppFooter />
    <CreateDialog ref="createDialog" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import CreateDialog from '@/components/CreateDialog.vue'
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
const createDialog = ref(null)
const userStore = useUserStore()

const username = computed(() => userStore.userData?.username ?? '')

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
h2 { font: 700 20px/28px var(--font-sans); margin: 0; }
.meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: 0; }
.sr-only { clip: rect(0 0 0 0); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }

.hero-text { justify-content: center; min-width: 0; }
.greeting { font: 800 40px/44px var(--font-sans); margin: 0; overflow-wrap: anywhere; }
.lead { color: var(--ink-muted); margin: calc(var(--space-3) * -1) 0 0; }

.search { position: relative; }
.search-icon { color: var(--ink-muted); left: var(--space-4); pointer-events: none; position: absolute; top: 50%; transform: translateY(-50%); }
.search-input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 15px var(--space-4) 15px 48px; width: 100%; }

.wrap { padding: 0 var(--page-gutter); }
.hero { border-bottom: 1px solid var(--line); }
.hero-in { align-items: stretch; display: grid; gap: var(--space-4); grid-template-columns: minmax(0, 2fr) minmax(0, 3fr); padding-bottom: var(--space-8); padding-top: 56px; }
.below { padding-bottom: var(--space-8); }
.block { border-bottom: 1px solid var(--line); padding: var(--space-8) 0; }
.section-head { align-items: flex-end; display: flex; flex-wrap: wrap; gap: var(--space-3) var(--space-6); justify-content: space-between; margin-bottom: var(--space-6); }

.tabs { display: flex; gap: var(--space-6); }
.tab { background: none; border: 0; border-bottom: 3px solid transparent; color: var(--ink-muted); cursor: pointer; font: 600 14px/20px var(--font-sans); padding: 0 0 var(--space-2); }
.tab:hover { border-bottom-color: var(--primary); color: var(--ink); }
.tab.active { border-bottom-color: var(--primary); color: var(--link); }

.count { margin: calc(var(--space-3) * -1) 0 var(--space-4); }
.below .big-grid { gap: var(--space-6); grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); }
.new-tile { background: none; border: 0; color: var(--ink); cursor: pointer; display: flex; flex-direction: column; gap: 2px; padding: 0; text-align: left; width: 100%; }
.new-cover { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-sm); color: var(--ink-muted); display: grid; margin-bottom: var(--space-2); place-items: center; transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease); }
.new-tile:hover .new-cover { background: var(--purple-soft); color: var(--link); }
.new-tile:hover .new-name { color: var(--link); text-decoration: underline; }
.new-name { font: 600 15px/22px var(--font-sans); }

.people { display: grid; gap: var(--space-6); grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); list-style: none; margin: 0; padding: 0; }
.person { align-items: center; color: var(--ink); display: flex; flex-direction: column; gap: var(--space-3); text-decoration: none; }
.person:hover .person-name { color: var(--link); text-decoration: underline; }
.person-av { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-pill); object-fit: cover; width: 100%; }
.person-av-empty { background: var(--primary); color: var(--on-primary); display: grid; font: 800 48px/1 var(--font-sans); place-items: center; }
.person-name { font: 600 16px/24px var(--font-sans); max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.songs { column-gap: var(--space-8); display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); list-style: none; margin: 0; padding: 0; }
.song { align-items: center; border-bottom: 1px solid var(--line); display: flex; gap: var(--space-3); padding: var(--space-3) 0; }
.song-art { background: var(--surface-alt); border-radius: var(--radius-sm); flex: none; height: 56px; object-fit: cover; width: 56px; }
.song-text { display: flex; flex-direction: column; min-width: 0; }
.song-title { font: 600 16px/24px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.song-artist { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.more { margin-top: var(--space-6); }

.soon { display: grid; gap: var(--space-4); grid-template-columns: repeat(3, minmax(0, 1fr)); }
.panel { background: var(--surface-alt); border-radius: var(--radius-md); display: flex; flex-direction: column; gap: var(--space-4); padding: var(--space-4); }
.ghost-rows, .ghost-artists { list-style: none; margin: 0; padding: 0; }
.ghost-rows { display: flex; flex-direction: column; gap: var(--space-3); }
.ghost-row { align-items: center; display: flex; gap: var(--space-3); }
.ghost-art { background: var(--surface); border-radius: var(--radius-sm); flex: none; height: 40px; width: 40px; }
.ghost-lines { display: flex; flex: 1; flex-direction: column; gap: var(--space-2); }
.ghost-line { background: var(--surface); border-radius: var(--radius-pill); display: block; height: 10px; width: 70%; }
.ghost-line.short { width: 40%; }
.ghost-artists { display: grid; gap: var(--space-3); grid-template-columns: repeat(4, minmax(0, 1fr)); }
.ghost-artist { align-items: center; display: flex; flex-direction: column; gap: var(--space-2); }
.ghost-artist .ghost-line { width: 70%; }
.ghost-circle { aspect-ratio: 1; background: var(--surface); border-radius: var(--radius-pill); display: block; width: 100%; }
.ghost-circle.small { flex: none; width: 40px; }
.ghost-badge { background: var(--purple-soft); border-radius: var(--radius-pill); flex: none; height: 20px; width: 56px; }

@media (max-width: 1280px) {
  .hero-in { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .hero-in { padding-top: var(--space-8); }
  .soon { grid-template-columns: 1fr; }
  .songs { grid-template-columns: 1fr; }
  .below .big-grid { grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }
  .people { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); }
}
</style>
