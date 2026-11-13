<template>
  <div class="aux-page with-player">
    <AppHeader />
    <main class="explore" :style="{ '--sticky-top': stickyTop }">
      <section class="hero">
        <div class="wrap">
          <h1>Explore</h1>
          <p class="meta">Search Aux, or find something new.</p>
        </div>
      </section>

      <div class="search-bar">
        <div class="wrap search-row">
          <form class="search" role="search" @submit.prevent>
            <label class="sr-only" for="explore-search">Search Aux</label>
            <svg class="search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
            </svg>
            <input
              id="explore-search"
              ref="searchInput"
              v-model="searchText"
              class="search-input"
              type="search"
              autocomplete="off"
              maxlength="200"
              placeholder="Search people, music and playlists"
            />
          </form>
          <button v-if="searching" type="button" class="cancel" @click="cancelSearch">Cancel</button>
        </div>
      </div>

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

          <EmptyState v-if="search.error" tone="error" :title="search.error" />
          <EmptyState v-else-if="search.loading && !search.results.length" tone="loading" :icon="activeTab.icon" title="Searching…" />
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
                  <router-link
                    v-if="piece.artistSummary?.artistName && piece.artistSummary.artistId"
                    class="song-artist artist-link"
                    :to="{ name: 'Artist', params: { artistId: piece.artistSummary.artistId } }"
                  >{{ piece.artistSummary.artistName }}</router-link>
                  <span v-else-if="piece.artistSummary?.artistName" class="song-artist">{{ piece.artistSummary.artistName }}</span>
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

        <template v-else>
          <section v-if="tags.loading || tags.error || tags.items.length" class="block" aria-labelledby="tags-heading">
            <div class="section-head">
              <h2 id="tags-heading">Genres and moods</h2>
              <button
                v-if="tags.items.length > TAGS_COLLAPSED"
                type="button"
                class="see-all mobile-only"
                :aria-expanded="tagsExpanded ? 'true' : 'false'"
                @click="tagsExpanded = !tagsExpanded"
              >{{ tagsExpanded ? 'Show less' : 'See all' }}</button>
            </div>
            <EmptyState v-if="tags.error" tone="error" :title="tags.error" />
            <EmptyState v-else-if="tags.loading" tone="loading" title="Loading genres and moods…" />
            <ul v-else class="tags" :class="{ collapsed: !tagsExpanded }">
              <li v-for="tag in tags.items" :key="tag">
                <router-link class="tag" :to="{ name: 'ExploreTag', params: { tag } }">
                  <span>{{ tagLabel(tag) }}</span>
                  <svg class="tag-icon" width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" />
                  </svg>
                </router-link>
              </li>
            </ul>
          </section>

          <section class="block" aria-labelledby="top-heading">
            <div class="section-head">
              <div>
                <h2 id="top-heading">Top this week</h2>
                <p class="meta">The most played songs on Aux in the last 7 days.</p>
              </div>
              <button
                v-if="top.items.length > TOP_COLLAPSED"
                type="button"
                class="see-all mobile-only"
                :aria-expanded="topExpanded ? 'true' : 'false'"
                @click="topExpanded = !topExpanded"
              >{{ topExpanded ? 'Show less' : 'See all' }}</button>
            </div>
            <EmptyState v-if="top.error" tone="error" :title="top.error" />
            <EmptyState v-else-if="top.loading" tone="loading" title="Loading this week's top songs…" />
            <EmptyState
              v-else-if="!top.items.length"
              title="Nothing played this week yet"
              text="Songs show up here once people start playing them."
            />
            <div v-else class="top-columns" :class="{ collapsed: !topExpanded }">
              <TrackRows :pieces="top.items.slice(0, TOP_COLLAPSED)" label="Top this week, 1 to 5" ranked @play="playTop" />
              <TrackRows
                v-if="top.items.length > TOP_COLLAPSED"
                class="top-rest"
                :pieces="top.items.slice(TOP_COLLAPSED)"
                :start="TOP_COLLAPSED + 1"
                :label="`Top this week, ${TOP_COLLAPSED + 1} to ${top.items.length}`"
                ranked
                @play="(index) => playTop(TOP_COLLAPSED + index)"
              />
            </div>
          </section>

          <section v-if="seed && (similar.loading || similar.error || similar.items.length)" class="block" aria-labelledby="similar-heading">
            <div class="section-head">
              <div>
                <h2 id="similar-heading">More like {{ seed.name }}</h2>
                <p class="meta">Picked from the last song you played.</p>
              </div>
              <router-link v-if="similar.items.length" class="see-all" :to="{ name: 'Discover', params: { id: seed.musicPieceId } }">See all</router-link>
            </div>
            <EmptyState v-if="similar.error" tone="error" :title="similar.error" />
            <EmptyState v-else-if="similar.loading" tone="loading" title="Finding songs like it…" />
            <SongCards v-else :pieces="similar.items" :label="`Songs like ${seed.name}`" @play="(index) => musicStore.playPieces(similar.items, index)" />
          </section>

          <section class="block" aria-labelledby="match-heading">
            <div class="section-head">
              <div>
                <h2 id="match-heading">People who hear it too</h2>
                <p class="meta">Listeners who share your favorite artists, with your compatibility score.</p>
              </div>
            </div>
            <EmptyState v-if="matches.error" tone="error" :title="matches.error" :text="STREAM_RETRY" />
            <EmptyState v-else-if="matches.loading" tone="loading" icon="users" title="Finding people who hear it too…" />
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
                    <router-link class="match-name" :to="{ name: 'Profile', params: { username: match.user.username } }">{{ match.user.username }}</router-link>
                    is <span class="level">{{ compatibilityLevel(match.compatibility).label }}</span>
                  </p>
                </div>
                <div class="meter" role="img" :aria-label="`${match.compatibility} percent compatible`">
                  <span v-for="n in 5" :key="n" class="seg" :class="{ filled: n <= compatibilityLevel(match.compatibility).filled }"></span>
                  <span class="pct">{{ match.compatibility }}%</span>
                </div>
                <p v-if="match.sharedArtists?.length" class="shared">
                  You both listen to
                  <template v-for="(artist, index) in match.sharedArtists" :key="artist.artistId"><b>{{ artist.artistName }}</b>{{ nameSeparator(index, match.sharedArtists.length) }}</template>.
                </p>
                <p v-else-if="match.sharedTags?.length" class="shared">
                  You both listen to
                  <template v-for="(tag, index) in match.sharedTags" :key="tag"><b>{{ tag }}</b>{{ nameSeparator(index, match.sharedTags.length) }}</template>.
                </p>
              </li>
            </ul>
          </section>
        </template>
      </div>
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import EmptyState from '@/components/EmptyState.vue'
import PlaylistGrid from '@/components/PlaylistGrid.vue'
import SongCards from '@/components/SongCards.vue'
import TrackRows from '@/components/TrackRows.vue'
import { useMusicStore } from '@/stores/music.js'
import { useUserStore } from '@/stores/user.js'
import { fetchAPI } from '@/utils/api.js'
import { fetchDiscover } from '@/utils/discover.js'
import { resolveCoverURL } from '@/utils/display.js'
import { fetchTopPieces, fetchTopTags, tagLabel } from '@/utils/explore.js'
import { compatibilityLevel, nameSeparator } from '@/utils/matches.js'
import { STREAM_RETRY, useStreamRow } from '@/utils/streamRow.js'
import { API_BASE_URL } from '@/utils/variables.js'

const SEARCH_TABS = [
  { id: 'USERS', label: 'People', icon: 'users' },
  { id: 'MUSIC', label: 'Music', icon: 'disc' },
  { id: 'PLAYLISTS', label: 'Playlists', icon: 'music' },
]
const PAGE_SIZE = 20
const DEBOUNCE_MS = 300
// Desktop shows every tag tile and all ten top songs; phones start with these and a "See all"
const TAG_COUNT = 12
const TAGS_COLLAPSED = 6
const TOP_COLLAPSED = 5
const SIMILAR_COUNT = 12

const route = useRoute()
const userStore = useUserStore()
const musicStore = useMusicStore()
const username = computed(() => userStore.userData?.username ?? '')

// The search field sticks just under the header, whose height changes when it wraps on phones
const stickyTop = ref('var(--header-height)')
let headerObserver = null
onMounted(() => {
  const header = document.querySelector('header.top')
  if (!header || typeof ResizeObserver === 'undefined') return
  headerObserver = new ResizeObserver(() => { stickyTop.value = `${header.offsetHeight}px` })
  headerObserver.observe(header)
})
onBeforeUnmount(() => headerObserver?.disconnect())

// Genres and moods: the top tags, curated ones first
const tags = reactive({ items: [], loading: true, error: '' })
const tagsExpanded = ref(false)

// Top this week: GET /api/core/top
const top = reactive({ items: [], loading: true, error: '' })
const topExpanded = ref(false)

function playTop(index) {
  musicStore.playPieces(top.items, index)
}

async function load(state, fetcher, what, shape = (items) => items) {
  try {
    state.items = shape((await fetcher()) ?? [])
  } catch (err) {
    if (err.sessionEnded) return
    state.error = `We couldn't load ${what}.`
  } finally {
    state.loading = false
  }
}

onMounted(() => {
  load(tags, fetchTopTags, 'genres and moods', (items) => items.slice(0, TAG_COUNT))
  load(top, fetchTopPieces, "this week's top songs")
})

// More like: Discover, seeded with the latest song from the `recents` event
const recent = useStreamRow('recents', 'recents', 'your recent listening')
const seed = computed(() => recent.value.items[0]?.musicPiece ?? null)
const similar = reactive({ items: [], loading: false, error: '' })
let similarRequest = 0

watch(() => seed.value?.musicPieceId, async (id) => {
  const request = ++similarRequest
  Object.assign(similar, { items: [], loading: Boolean(id), error: '' })
  if (!id) return
  try {
    const result = await fetchDiscover(id)
    if (request !== similarRequest) return
    similar.items = (result.musicPieces ?? []).slice(0, SIMILAR_COUNT)
  } catch (err) {
    if (request !== similarRequest || err.sessionEnded) return
    // The seed went private or was deleted: leave the section out rather than show an error about it
    if (err.code !== 'MUSIC_PIECE_NOT_FOUND') similar.error = "We couldn't load songs like it."
  } finally {
    if (request === similarRequest) similar.loading = false
  }
}, { immediate: true })

// People who hear it too: the `matches` event
const matches = useStreamRow('matches', 'matches', 'people who hear it too')

// Search: GET /api/core/search, one category at a time. ?category= picks the tab, e.g. from the Dashboard's empty Saved tab
const searchText = ref('')
const searchInput = ref(null)
const category = ref(SEARCH_TABS.some((tab) => tab.id === route?.query?.category) ? route.query.category : 'USERS')
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
  const params = new URLSearchParams({ category: category.value, query: trimmedQuery.value, limit: PAGE_SIZE, offset })
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

function cancelSearch() {
  searchText.value = ''
  searchInput.value?.blur()
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
  if (!route?.query?.category) return
  await nextTick()
  searchInput.value?.focus()
})
onBeforeUnmount(() => clearTimeout(debounceTimer))
</script>

<style scoped>
h1 { font: 700 28px/34px var(--font-sans); margin: 0; }
h2 { font: 700 20px/28px var(--font-sans); margin: 0; }
.meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.sr-only { clip: rect(0 0 0 0); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }
.wrap { padding-left: var(--page-gutter); padding-right: var(--page-gutter); }
.below { padding-bottom: var(--space-8); }
.block { border-bottom: 1px solid var(--line); min-width: 0; padding: var(--space-8) 0; }
.block:last-child { border-bottom: 0; }
.section-head { align-items: flex-end; display: flex; flex-wrap: wrap; gap: var(--space-3) var(--space-6); justify-content: space-between; margin-bottom: var(--space-6); }
.see-all { background: none; border: 0; color: var(--link); cursor: pointer; font: 600 14px/20px var(--font-sans); padding: 0; text-decoration: none; }
.see-all:hover { text-decoration: underline; }
.see-all:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.mobile-only { display: none; }

.hero { padding-top: var(--space-8); }
.hero .meta { margin-top: var(--space-1); }
.search-bar { background: var(--surface); border-bottom: 1px solid var(--line); padding: var(--space-4) 0; position: sticky; top: calc(var(--sticky-top) + env(safe-area-inset-top, 0px)); z-index: 4; }
.search-row { align-items: center; display: flex; gap: var(--space-3); }
.search { flex: 1; max-width: 640px; position: relative; }
.search-icon { color: var(--ink-muted); left: var(--space-3); pointer-events: none; position: absolute; top: 50%; transform: translateY(-50%); }
.search-input { background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); color: var(--ink); font: 400 16px/24px var(--font-sans); padding: 8px var(--space-3) 8px 40px; width: 100%; }
.search-input:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.cancel { background: none; border: 0; color: var(--link); cursor: pointer; display: none; font: 600 16px/24px var(--font-sans); padding: var(--space-2) 0; }
.cancel:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }

.tabs { display: flex; gap: var(--space-6); }
.tab { background: none; border: 0; border-bottom: 3px solid transparent; color: var(--ink-muted); cursor: pointer; font: 600 14px/20px var(--font-sans); padding: 0 0 var(--space-2); }
.tab:hover { border-bottom-color: var(--primary); color: var(--ink); }
.tab.active { border-bottom-color: var(--primary); color: var(--link); }
.tab:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.count { margin: calc(var(--space-3) * -1) 0 var(--space-4); }

.tags { display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); list-style: none; margin: 0; padding: 0; }
.tag { align-items: flex-end; background: var(--purple-soft); border-radius: var(--radius-md); color: var(--link); display: flex; font: 700 16px/24px var(--font-sans); height: 72px; overflow: hidden; padding: var(--space-3); position: relative; text-decoration: none; transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease); }
.tag:hover { background: var(--primary); color: var(--on-primary); }
.tag:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.tag span { position: relative; }
.tag-icon { opacity: 0.35; position: absolute; right: -6px; top: -6px; }

.top-columns { column-gap: var(--space-8); display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }

.matches { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); list-style: none; margin: 0; padding: 0; }
.match { background: var(--surface-alt); border-radius: var(--radius-md); display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-4); }
.match-top { align-items: center; display: flex; gap: var(--space-3); }
.match-av { border-radius: var(--radius-pill); flex: none; height: 44px; object-fit: cover; width: 44px; }
.match-av-empty { background: var(--primary); color: var(--on-primary); display: grid; font: 700 17px/1 var(--font-sans); place-items: center; }
.match-line { font: 400 15px/22px var(--font-sans); margin: 0; min-width: 0; overflow-wrap: anywhere; }
.match-name { color: var(--ink); font-weight: 700; text-decoration: none; }
.match-name:hover { color: var(--link); text-decoration: underline; }
.level { color: var(--link); font-weight: 700; }
.meter { align-items: center; display: flex; gap: var(--space-1); }
.seg { background: var(--surface); border-radius: 2px; flex: 1; height: 8px; }
.seg.filled { background: var(--primary); }
.pct { color: var(--ink-muted); font: 600 12px/16px var(--font-sans); margin-left: var(--space-2); }
.shared { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.shared b { color: var(--ink); font-weight: 600; }

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
.artist-link { text-decoration: none; }
.artist-link:hover { color: var(--link); text-decoration: underline; }
.below .big-grid { gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }
.more { margin-top: var(--space-6); }

@media (max-width: 1000px) {
  .top-columns { grid-template-columns: minmax(0, 1fr); }
  .top-rest :deep(.tr:first-child) { border-top: 0; }
}
@media (max-width: 720px) {
  .hero { padding-top: var(--space-6); }
  .search-bar { padding: var(--space-2) 0 var(--space-3); }
  .cancel, .mobile-only { display: block; }
  .block { padding: var(--space-6) 0; }
  .section-head { margin-bottom: var(--space-4); }
  .tags { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .tags.collapsed li:nth-child(n + 7) { display: none; }
  .top-columns.collapsed .top-rest { display: none; }
  .matches { grid-template-columns: minmax(0, 1fr); }
  .songs { grid-template-columns: 1fr; }
  .people { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); }
}
</style>
