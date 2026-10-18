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
        </div>
      </section>

      <nav class="section-nav" aria-label="Dashboard sections">
        <div class="wrap tabs">
          <router-link
            v-for="item in SECTIONS"
            :key="item.id"
            class="tab"
            :class="{ active: section === item.id }"
            :aria-current="section === item.id ? 'page' : undefined"
            :to="{ name: 'Dashboard', params: { section: item.id === 'library' ? undefined : item.id } }"
          >{{ item.label }}</router-link>
        </div>
      </nav>

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

        <section v-else-if="section === 'library'" class="block" aria-labelledby="library-heading">
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

        <section v-else-if="section === 'recent-listening'" class="block" aria-labelledby="recent-heading">
          <div class="section-head">
            <div>
              <h2 id="recent-heading">Recent listening</h2>
              <p class="meta">The last songs you played, newest first.</p>
            </div>
          </div>

          <p v-if="recent.error" class="error" role="alert">{{ recent.error }}</p>
          <p v-else-if="recent.loading" class="meta">Loading your recent listening…</p>
          <EmptyState
            v-else-if="!recent.items.length"
            title="No plays yet"
            text="Play a song from one of your playlists and it shows up here."
          />
          <ul v-else class="cards">
            <li v-for="{ musicPiece } in recent.items" :key="musicPiece.musicPieceId" class="card">
              <img v-if="musicPiece.coverUrl" class="card-art" :src="resolveCoverURL(musicPiece.coverUrl)" alt="" loading="lazy" />
              <span v-else class="card-art card-art-empty" aria-hidden="true">
                <svg class="card-icon" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
              </span>
              <span class="card-body">
                <span class="card-title">{{ musicPiece.name }}</span>
                <span v-if="musicPiece.artistSummary?.artistName" class="card-meta">{{ musicPiece.artistSummary.artistName }}</span>
              </span>
            </li>
          </ul>
        </section>

        <section v-else-if="section === 'top-artists'" class="block" aria-labelledby="artists-heading">
          <div class="section-head">
            <div>
              <h2 id="artists-heading">Top artists</h2>
              <p class="meta">The artists you've played most, all time.</p>
            </div>
          </div>

          <p v-if="topArtists.error" class="error" role="alert">{{ topArtists.error }}</p>
          <p v-else-if="topArtists.loading" class="meta">Loading your top artists…</p>
          <EmptyState
            v-else-if="!topArtists.items.length"
            icon="mic"
            title="No top artists yet"
            text="Your most-played artists appear here once you've listened to a few songs."
          />
          <ol v-else class="cards">
            <li v-for="(artist, index) in topArtists.items" :key="artist.artistId" class="card">
              <span class="card-art-wrap">
                <img v-if="artist.artistPfpUrl" class="card-art" :src="resolveCoverURL(artist.artistPfpUrl)" alt="" loading="lazy" />
                <span v-else class="card-art card-art-empty card-initial" aria-hidden="true">{{ artist.artistName.charAt(0).toUpperCase() }}</span>
                <span class="rank">#{{ index + 1 }}</span>
              </span>
              <span class="card-body">
                <span class="card-title">{{ artist.artistName }}</span>
                <span class="card-meta">{{ index === 0 ? 'Your most played artist' : `Number ${index + 1} for you` }}</span>
              </span>
            </li>
          </ol>
        </section>

        <section v-else class="block" aria-labelledby="match-heading">
          <div class="section-head">
            <div>
              <h2 id="match-heading">People who hear it too</h2>
              <p class="meta">Listeners who share your favorite artists, with your compatibility score.</p>
            </div>
          </div>

          <EmptyState
            icon="users"
            title="Matching is on its way"
            text="Aux will match you with listeners who share your taste once matching is built. Keep listening in the meantime."
          />
        </section>

      </div>
    </main>
    <AppFooter />
    <CreateDialog ref="createDialog" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
const SECTIONS = [
  { id: 'library', label: 'Your library' },
  { id: 'recent-listening', label: 'Recent listening' },
  { id: 'top-artists', label: 'Top artists' },
  { id: 'people-who-hear-it-too', label: 'People who hear it too' },
]
const PAGE_SIZE = 20
const DEBOUNCE_MS = 300

const route = useRoute()
const router = useRouter()

const section = computed(() =>
  SECTIONS.some((item) => item.id === route.params.section) ? route.params.section : 'library',
)
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


// Recent listening: GET /api/users/recent-listening. Top artists: GET /api/users/top-artists. Both return up to 4
const recent = reactive({ items: [], loading: false, loaded: false, error: '' })
const topArtists = reactive({ items: [], loading: false, loaded: false, error: '' })

async function loadSection(state, url, key, what) {
  if (state.loaded || state.loading) return
  state.loading = true
  state.error = ''
  try {
    state.items = (await fetchAPI(`${API_BASE_URL}${url}`))[key] ?? []
    state.loaded = true
  } catch {
    state.error = `We couldn't load your ${what}. Check that the server is running, then refresh the page.`
  } finally {
    state.loading = false
  }
}

watch(section, (id) => {
  if (!username.value) return
  if (id === 'recent-listening') loadSection(recent, '/api/users/recent-listening', 'recents', 'recent listening')
  else if (id === 'top-artists') loadSection(topArtists, '/api/users/top-artists', 'artists', 'top artists')
}, { immediate: true })

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

.panel { background: var(--surface-alt); border-radius: var(--radius-md); display: flex; flex-direction: column; gap: var(--space-4); padding: var(--space-4); }
.hero-text { justify-content: center; max-width: 720px; min-width: 0; }
.greeting { font: 800 40px/44px var(--font-sans); margin: 0; overflow-wrap: anywhere; }
.lead { color: var(--ink-muted); margin: calc(var(--space-3) * -1) 0 0; }

.search { position: relative; }
.search-icon { color: var(--ink-muted); left: var(--space-4); pointer-events: none; position: absolute; top: 50%; transform: translateY(-50%); }
.search-input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 15px var(--space-4) 15px 48px; width: 100%; }

.wrap { padding: 0 var(--page-gutter); }
.hero-in { padding-bottom: var(--space-6); padding-top: 56px; }
.below { padding-bottom: var(--space-8); }
.block { border-bottom: 1px solid var(--line); padding: var(--space-8) 0; }
.section-head { align-items: flex-end; display: flex; flex-wrap: wrap; gap: var(--space-3) var(--space-6); justify-content: space-between; margin-bottom: var(--space-6); }

.tabs { display: flex; gap: var(--space-6); }
.tab { background: none; border: 0; border-bottom: 3px solid transparent; color: var(--ink-muted); cursor: pointer; font: 600 14px/20px var(--font-sans); padding: 0 0 var(--space-2); }
.tab:hover { border-bottom-color: var(--primary); color: var(--ink); }
.tab.active { border-bottom-color: var(--primary); color: var(--link); }
.section-nav { border-bottom: 1px solid var(--line); }
.section-nav .tabs { overflow-x: auto; scrollbar-width: none; }
.section-nav .tab { flex: none; font-size: 15px; text-decoration: none; white-space: nowrap; }

.cards { display: grid; gap: var(--space-6); grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); list-style: none; margin: 0; padding: 0; }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); display: flex; flex-direction: column; min-width: 0; overflow: hidden; }
.card-art-wrap { display: block; position: relative; }
.card-art { aspect-ratio: 1; background: var(--surface-alt); display: block; object-fit: cover; width: 100%; }
.card-art-empty { color: var(--ink-muted); display: grid; place-items: center; }
.card-icon { fill: none; height: 48px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; width: 48px; }
.card-initial { background: var(--purple-soft); color: var(--link); font: 800 56px/1 var(--font-sans); }
.rank { background: var(--primary); border-radius: var(--radius-pill); color: var(--on-primary); font: 600 12px/16px var(--font-sans); left: var(--space-3); padding: 2px var(--space-2); position: absolute; top: var(--space-3); }
.card-body { background: var(--surface-alt); border-top: 1px solid var(--line); display: flex; flex: 1; flex-direction: column; gap: 2px; padding: var(--space-3) var(--space-4); }
.card-title { font: 600 16px/24px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

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

@media (max-width: 720px) {
  .hero-in { padding-top: var(--space-8); }
  .cards { gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }
  .songs { grid-template-columns: 1fr; }
  .below .big-grid { grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }
  .people { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); }
}
</style>
