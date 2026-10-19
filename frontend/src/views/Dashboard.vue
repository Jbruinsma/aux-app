<template>
  <div class="aux-page with-player">
    <AppHeader />
    <main>
      <section class="welcome">
        <div class="wrap welcome-in">
          <div class="welcome-text">
            <h1 class="greeting">Welcome back, {{ username }}</h1>
            <p class="meta">{{ librarySummary }}</p>
          </div>

          <form class="search" role="search" @submit.prevent>
            <label class="sr-only" for="dash-search">Search Aux</label>
            <svg class="search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
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

        <div v-else class="rows">
          <section class="block" aria-labelledby="recent-heading">
            <div class="section-head">
              <div>
                <h2 id="recent-heading">Recent Listening</h2>
                <p v-if="!recent.preview" class="meta">The last songs you played, newest first.</p>
              </div>
            </div>

            <p v-if="recent.error" class="error" role="alert">{{ recent.error }}</p>
            <p v-else-if="recent.loading" class="meta">Loading your recent listening…</p>
            <EmptyState
              v-else-if="!recent.items.length"
              title="No plays yet"
              text="Play a song from one of your playlists and it shows up here."
            />
            <div v-else class="scroll-area">
              <ul
                ref="recentScroller"
                class="cards scroller"
                :class="{ dragging: recentDragging, hinting }"
                tabindex="0"
                aria-label="Recent Listening songs"
              >
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
              <span v-if="hinting" :key="hintRun" class="drag-hint" aria-hidden="true" @animationend="hinting = false">
                <svg class="hint-cursor" viewBox="0 0 24 24">
                  <path d="M18 11.5V9a2 2 0 0 0-2-2a2 2 0 0 0-2 2v1.4" />
                  <path d="M14 10V8a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2" />
                  <path d="M10 9.9V9a2 2 0 0 0-2-2a2 2 0 0 0-2 2v5" />
                  <path d="M6 14a2 2 0 0 0-2-2a2 2 0 0 0-2 2" />
                  <path d="M18 11a2 2 0 1 1 4 0v3a8 8 0 0 1-8 8h-4a8 8 0 0 1-8-8 2 2 0 1 1 4 0" />
                </svg>
                <span class="hint-label">Drag To Scroll</span>
              </span>
            </div>
          </section>

          <section class="block" aria-labelledby="artists-heading">
            <div class="section-head">
              <div>
                <h2 id="artists-heading">Your Top Artists</h2>
                <p v-if="!topArtists.preview" class="meta">The artists you've played most, all time.</p>
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
            <ol
              v-else
              ref="topScroller"
              class="cards scroller"
              :class="{ dragging: topDragging }"
              tabindex="0"
              aria-label="Your Top Artists"
            >
              <li v-for="(artist, index) in topArtists.items" :key="artist.artistId" class="card">
                <span class="card-art-wrap">
                  <img v-if="artist.artistPfpUrl" class="card-art" :src="resolveCoverURL(artist.artistPfpUrl)" alt="" loading="lazy" />
                  <span v-else class="card-art card-art-empty card-initial" aria-hidden="true">{{ artist.artistName.charAt(0).toUpperCase() }}</span>
                  <span class="rank">#{{ index + 1 }}</span>
                </span>
                <span class="card-body">
                  <span class="card-title">{{ artist.artistName }}</span>
                </span>
              </li>
            </ol>
          </section>

          <section class="block" aria-labelledby="match-heading">
            <div class="section-head">
              <div>
                <h2 id="match-heading">People Who Hear It Too</h2>
                <p class="meta">
                  {{ MATCHES_MOCKED
                    ? "Preview: matching isn't live yet, so these are sample listeners."
                    : 'Listeners who share your favorite artists, with your compatibility score.' }}
                </p>
              </div>
            </div>

            <p v-if="matches.error" class="error" role="alert">{{ matches.error }}</p>
            <p v-else-if="matches.loading" class="meta">Finding people who hear it too…</p>
            <EmptyState
              v-else-if="!matches.items.length"
              icon="users"
              title="No matches yet"
              text="Keep listening. Aux matches you with people once it knows your favorite artists."
            />
            <ul v-else class="matches">
              <li v-for="match in matches.items" :key="match.user.userId" class="match">
                <div class="match-top">
                  <img v-if="match.user.pfpUrl" class="match-av" :src="resolveCoverURL(match.user.pfpUrl)" alt="" />
                  <span v-else class="match-av match-av-empty" aria-hidden="true">{{ match.user.username.charAt(0).toUpperCase() }}</span>
                  <p class="match-line">
                    Your musical compatibility with
                    <span v-if="MATCHES_MOCKED" class="match-name">{{ match.user.username }}</span>
                    <router-link v-else class="match-name" :to="{ name: 'Profile', params: { username: match.user.username } }">{{ match.user.username }}</router-link>
                    is <span class="level">{{ compatibilityLevel(match.compatibility).label }}</span>
                  </p>
                </div>
                <div class="meter" role="img" :aria-label="`${match.compatibility} percent compatible`">
                  <span v-for="n in 5" :key="n" class="seg" :class="{ filled: n <= compatibilityLevel(match.compatibility).filled }"></span>
                  <span class="pct">{{ match.compatibility }}%</span>
                </div>
                <p v-if="match.sharedArtists.length" class="shared">
                  You both listen to
                  <template v-for="(artist, index) in match.sharedArtists" :key="artist.artistId"><b>{{ artist.artistName }}</b>{{ nameSeparator(index, match.sharedArtists.length) }}</template>.
                </p>
              </li>
            </ul>
          </section>

          <section class="block" aria-labelledby="library-heading">
            <div class="section-head">
              <h2 id="library-heading">Your Library</h2>
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
            <PlaylistGrid v-else class="big-grid" :playlists="shownLibrary" :owner="username">
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
            <button
              v-if="!libraryLoading && !libraryError && filteredLibrary.length > LIBRARY_PREVIEW_COUNT"
              type="button"
              class="btn secondary more"
              :aria-expanded="libraryExpanded ? 'true' : 'false'"
              @click="libraryExpanded = !libraryExpanded"
            >{{ libraryExpanded ? 'Show less' : `More (${filteredLibrary.length - LIBRARY_PREVIEW_COUNT})` }}</button>
          </section>
        </div>

      </div>
    </main>
    <AppFooter />
    <CreateDialog ref="createDialog" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
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
import { MATCHES_MOCKED, compatibilityLevel, fetchMatches, nameSeparator } from '@/utils/matches.js'
import { DASHBOARD_PREVIEW, PREVIEW_RECENTS, PREVIEW_TOP_ARTISTS } from '@/utils/dashboardPreview.js'
import { useDragScroll } from '@/utils/dragScroll.js'

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
const LIBRARY_PREVIEW_COUNT = 7
const libraryExpanded = ref(false)

const filteredLibrary = computed(() => {
  if (libraryFilter.value === 'yours') return library.value.filter((p) => !p.isSaved)
  if (libraryFilter.value === 'saved') return library.value.filter((p) => p.isSaved)
  return library.value
})

const shownLibrary = computed(() =>
  libraryExpanded.value ? filteredLibrary.value : filteredLibrary.value.slice(0, LIBRARY_PREVIEW_COUNT),
)

const librarySummary = computed(() => {
  if (libraryLoading.value || libraryError.value) return 'Your music, your people.'
  const saved = library.value.filter((p) => p.isSaved).length
  const own = library.value.length - saved
  const ownText = own === 1 ? '1 playlist' : `${own} playlists`
  return saved ? `${ownText} · ${saved} saved` : ownText
})


// Recent listening: GET /api/users/recent-listening. Top artists: GET /api/users/top-artists. Both return up to 4
const recent = reactive({ items: [], loading: true, error: '', preview: false })
const topArtists = reactive({ items: [], loading: true, error: '', preview: false })
const matches = reactive({ items: [], loading: true, error: '', preview: false })

// With previews on, a row that has placeholder data shows it instead of the account's own
async function loadRow(state, load, key, what, placeholder = []) {
  if (DASHBOARD_PREVIEW && placeholder.length) {
    state.items = placeholder
    state.preview = true
    state.loading = false
    return
  }
  try {
    state.items = (await load())[key] ?? []
  } catch (err) {
    if (err.sessionEnded) return
    state.error = `We couldn't load ${what}. Check that the server is running, then refresh the page.`
  } finally {
    state.loading = false
  }
}

/* Both card rows scroll sideways by mouse drag too. Recent Listening shows a hint (a pointer dragging the row) every
   HINT_EVERY_MS until the user drags it themselves; it never shows when they prefer reduced motion */

const HINT_EVERY_MS = 10_000
const recentScroller = ref(null)
const topScroller = ref(null)
const hinting = ref(false)
const hintRun = ref(0)
const hintDismissed = ref(false)
let hintTimer = null
let firstHint = null

const { dragging: recentDragging } = useDragScroll(recentScroller, {
  onDrag: () => {
    hintDismissed.value = true
    hinting.value = false
    clearInterval(hintTimer)
  },
})
const { dragging: topDragging } = useDragScroll(topScroller)

function playHint() {
  const list = recentScroller.value
  if (hintDismissed.value || hinting.value || !list || list.scrollWidth <= list.clientWidth) return
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
  hintRun.value++
  hinting.value = true
}

watch(() => recent.items.length, async (count) => {
  if (!count) return
  await nextTick()
  clearInterval(hintTimer)
  clearTimeout(firstHint)
  firstHint = setTimeout(playHint, 1000)
  hintTimer = setInterval(playHint, HINT_EVERY_MS)
})

onBeforeUnmount(() => {
  clearInterval(hintTimer)
  clearTimeout(firstHint)
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

  loadRow(recent, () => fetchAPI(`${API_BASE_URL}/api/users/recent-listening`), 'recents', 'your recent listening', PREVIEW_RECENTS)
  loadRow(topArtists, () => fetchAPI(`${API_BASE_URL}/api/users/top-artists`), 'artists', 'your top artists', PREVIEW_TOP_ARTISTS)
  loadRow(matches, fetchMatches, 'matches', 'people who hear it too')

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

.welcome { border-bottom: 1px solid var(--line); }
.welcome-in { display: flex; flex-direction: column; gap: var(--space-4); padding-bottom: var(--space-8); padding-top: var(--space-8); }
.welcome-text { display: flex; flex-direction: column; gap: var(--space-1); min-width: 0; }
.greeting { font: 700 28px/34px var(--font-sans); margin: 0; overflow-wrap: anywhere; }

.search { max-width: 560px; position: relative; }
.search-icon { color: var(--ink-muted); left: var(--space-3); pointer-events: none; position: absolute; top: 50%; transform: translateY(-50%); }
.search-input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px var(--space-3) 8px 40px; width: 100%; }

.wrap { padding-left: var(--page-gutter); padding-right: var(--page-gutter); }
.below { padding-bottom: var(--space-8); }
.block { border-bottom: 1px solid var(--line); min-width: 0; padding: var(--space-8) 0; }
.rows { column-gap: var(--space-8); display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.section-head { align-items: flex-end; display: flex; flex-wrap: wrap; gap: var(--space-3) var(--space-6); justify-content: space-between; margin-bottom: var(--space-6); }

.tabs { display: flex; gap: var(--space-6); }
.tab { background: none; border: 0; border-bottom: 3px solid transparent; color: var(--ink-muted); cursor: pointer; font: 600 14px/20px var(--font-sans); padding: 0 0 var(--space-2); }
.tab:hover { border-bottom-color: var(--primary); color: var(--ink); }
.tab.active { border-bottom-color: var(--primary); color: var(--link); }

.cards { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); list-style: none; margin: 0; padding: 0; }
.scroller { cursor: grab; grid-auto-columns: 160px; grid-auto-flow: column; grid-template-columns: none; overflow-x: auto; overscroll-behavior-x: contain; padding-bottom: var(--space-3); scrollbar-color: var(--line-strong) transparent; scrollbar-width: thin; }
.scroller:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.scroller.dragging { cursor: grabbing; user-select: none; }
.scroll-area { position: relative; }
.hinting > li { animation: hint-nudge 2.8s var(--ease) both; }
.drag-hint { align-items: center; animation: hint-move 2.8s var(--ease) both; display: flex; flex-direction: column; gap: var(--space-2); left: 50%; pointer-events: none; position: absolute; top: 30%; z-index: 2; }
.hint-cursor { fill: var(--surface); height: 44px; stroke: var(--ink); stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 44px; }
.hint-label { background: var(--ink); border-radius: var(--radius-pill); color: var(--surface); font: 600 14px/20px var(--font-sans); padding: var(--space-1) var(--space-3); white-space: nowrap; }
@keyframes hint-move {
  0% { opacity: 0; transform: translateX(40px); }
  12% { opacity: 1; transform: translateX(40px); }
  20% { transform: translateX(40px) scale(0.9); }
  48% { transform: translateX(-80px) scale(0.9); }
  74% { transform: translateX(40px) scale(0.9); }
  82% { opacity: 1; transform: translateX(40px); }
  100% { opacity: 0; transform: translateX(40px); }
}
@keyframes hint-nudge {
  0%, 20% { transform: translateX(0); }
  48% { transform: translateX(-120px); }
  74%, 100% { transform: translateX(0); }
}
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

.matches { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); list-style: none; margin: 0; padding: 0; }
.match { background: var(--surface-alt); border-radius: var(--radius-md); display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-4); }
.match-top { align-items: center; display: flex; gap: var(--space-3); }
.match-av { border-radius: var(--radius-pill); flex: none; height: 44px; object-fit: cover; width: 44px; }
.match-av-empty { background: var(--primary); color: var(--on-primary); display: grid; font: 700 17px/1 var(--font-sans); place-items: center; }
.match-line { font: 400 15px/22px var(--font-sans); margin: 0; min-width: 0; overflow-wrap: anywhere; }
.match-name { color: var(--ink); font-weight: 700; text-decoration: none; }
a.match-name:hover { color: var(--link); text-decoration: underline; }
.level { color: var(--link); font-weight: 700; }
.meter { align-items: center; display: flex; gap: var(--space-1); }
.seg { background: var(--surface); border-radius: 2px; flex: 1; height: 8px; }
.seg.filled { background: var(--primary); }
.pct { color: var(--ink-muted); font: 600 12px/16px var(--font-sans); margin-left: var(--space-2); }
.shared { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.shared b { color: var(--ink); font-weight: 600; }

.count { margin: calc(var(--space-3) * -1) 0 var(--space-4); }
.below .big-grid { gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }
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

@media (max-width: 1100px) {
  .rows { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 720px) {
  .welcome-in { padding-bottom: var(--space-6); padding-top: var(--space-6); }
  .scroller { grid-auto-columns: 136px; }
  .songs { grid-template-columns: 1fr; }
  .below .big-grid { grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }
  .people { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); }
}
</style>
