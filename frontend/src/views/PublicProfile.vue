<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="wrap">
      <p v-if="status === 'loading'" class="meta page-state">Loading @{{ username }}…</p>

      <section v-else-if="status === 'missing'" class="page-state">
        <h1>No one here goes by @{{ username }}</h1>
        <p class="meta">Check the spelling, or find people from your home screen.</p>
        <router-link to="/dashboard" class="btn secondary">Go home</router-link>
      </section>

      <section v-else-if="status === 'error'" class="page-state">
        <h1>We couldn't load this profile</h1>
        <p class="meta">Check that the server is running, then try again.</p>
        <button type="button" class="btn secondary" @click="loadProfile">Try again</button>
      </section>

      <div v-else class="layout">
        <aside class="side">
          <ProfileCard
            flat
            :username="profile.username"
            :display-name="profile.profileDetails?.displayName ?? ''"
            :picture-url="pictureUrl"
            :banner-url="bannerUrl"
            :country="countryName"
            :about="profile.profileDetails?.about ?? ''"
            :website="profile.profileDetails?.website ?? ''"
          >
            <p v-if="profile.followingMe && !profile.isMe" class="badge">Follows you</p>
            <dl class="stats">
              <div v-for="stat in stats" :key="stat.label">
                <dt>{{ stat.label }}</dt>
                <dd>{{ stat.value.toLocaleString() }}</dd>
              </div>
            </dl>
            <router-link
              v-if="profile.isMe"
              :to="{ name: 'Settings', params: { username: profile.username } }"
              class="btn secondary action"
            >Edit profile</router-link>
            <router-link v-else-if="!loggedIn" to="/login" class="btn secondary action">Log in to follow</router-link>
            <template v-else>
              <button type="button" class="btn secondary action" disabled aria-describedby="follow-soon">
                {{ profile.isFollowing ? 'Following' : 'Follow' }}
              </button>
              <p id="follow-soon" class="hint">Following is coming soon.</p>
            </template>
          </ProfileCard>
        </aside>

        <div class="main-col">
          <section aria-labelledby="recent-heading">
            <h2 id="recent-heading">Recent tracks</h2>
            <EmptyState
              title="No plays yet"
              :text="profile.isMe ? 'Play a song and it shows up here.' : `Songs @${profile.username} plays show up here.`"
            />
          </section>

          <section aria-labelledby="artists-heading">
            <h2 id="artists-heading">Top artists</h2>
            <EmptyState
              icon="mic"
              title="No top artists yet"
              :text="profile.isMe
                ? 'Your most-played artists appear here once you’ve listened to a few songs.'
                : `@${profile.username}’s most-played artists appear here once they’ve listened to a few songs.`"
            />
          </section>

          <section aria-labelledby="favorites-heading">
            <h2 id="favorites-heading">Favorite artists</h2>
            <EmptyState v-if="favorites.error" tone="error" icon="mic" :title="favorites.error" text="Check that the server is running, then refresh the page." />
            <EmptyState v-else-if="favorites.loading" tone="loading" icon="mic" title="Loading favorite artists…" />
            <EmptyState
              v-else-if="!favorites.items.length"
              icon="mic"
              title="No favorite artists yet"
              :text="profile.isMe
                ? 'Favorite an artist from their page and they show up here.'
                : `Artists @${profile.username} favorites show up here.`"
            />
            <ul v-else class="artists">
              <li v-for="artist in favorites.items" :key="artist.artistId">
                <router-link class="artist" :to="{ name: 'Artist', params: { artistId: artist.artistId } }">
                  <img v-if="artist.artistPfpUrl" class="artist-av" :src="resolveCoverURL(artist.artistPfpUrl)" alt="" loading="lazy" />
                  <span v-else class="artist-av artist-av-empty" aria-hidden="true">{{ artist.artistName.charAt(0).toUpperCase() }}</span>
                  <span class="artist-name">{{ artist.artistName }}</span>
                </router-link>
              </li>
            </ul>
          </section>

          <section aria-labelledby="albums-heading">
            <h2 id="albums-heading">Top albums</h2>
            <EmptyState
              icon="disc"
              title="No top albums yet"
              :text="profile.isMe
                ? 'Your most-played albums appear here once you’ve listened to a few songs.'
                : `@${profile.username}’s most-played albums appear here once they’ve listened to a few songs.`"
            />
          </section>

          <section aria-labelledby="playlists-heading">
            <h2 id="playlists-heading">Playlists</h2>
            <PlaylistGrid v-if="profile.playlists.length" :playlists="profile.playlists" :owner="profile.username" />
            <EmptyState
              v-else
              title="No playlists yet"
              :text="profile.isMe ? 'Create a playlist and it shows up here.' : `@${profile.username} hasn’t shared any playlists yet.`"
            />
          </section>

          <section aria-labelledby="uploads-heading">
            <h2 id="uploads-heading">Uploads</h2>
            <EmptyState
              title="No uploads yet"
              :text="profile.isMe
                ? 'Songs you upload show up here, where others can play and download them.'
                : `Songs @${profile.username} uploads show up here, ready to play and download.`"
            />
          </section>
        </div>
      </div>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import EmptyState from '@/components/EmptyState.vue'
import PlaylistGrid from '@/components/PlaylistGrid.vue'
import ProfileCard from '@/components/ProfileCard.vue'
import { fetchAPI } from '@/utils/api.js'
import { resolveCoverURL } from '@/utils/display.js'
import { useUserStore } from '@/stores/user.js'
import { API_BASE_URL } from '@/utils/variables.js'
import { COUNTRIES } from '@/utils/countries.js'
import { fetchFavoriteArtists } from '@/utils/artist.js'

const route = useRoute()
const userStore = useUserStore()

const username = computed(() => route.params.username)
const loggedIn = computed(() => userStore.loggedIn)

const profile = ref(null)
const status = ref('loading')

// These counts aren't in the profile response yet; they show 0 until the backend sends
// uploadsCount, followersCount and followingCount
const stats = computed(() => [
  { label: 'Uploads', value: profile.value.uploadsCount ?? 0 },
  { label: 'Followers', value: profile.value.followersCount ?? 0 },
  { label: 'Following', value: profile.value.followingCount ?? 0 },
])

const pictureUrl = computed(() => (profile.value?.pfpUrl ? resolveCoverURL(profile.value.pfpUrl) : ''))
const bannerUrl = computed(() => (profile.value?.bannerUrl ? resolveCoverURL(profile.value.bannerUrl) : ''))
const countryName = computed(() => COUNTRIES.find((c) => c.code === profile.value?.profileDetails?.country)?.name ?? '')

// Playlists here are already filtered by the backend: other people only see public ones
async function loadProfile() {
  const requested = username.value
  status.value = 'loading'
  try {
    const data = await fetchAPI(`${API_BASE_URL}/api/users/profile/${encodeURIComponent(requested)}`)
    if (requested !== username.value) return
    profile.value = data
    status.value = 'ready'
    glideToTopAfterRefresh()
  } catch (err) {
    if (requested !== username.value) return
    status.value = err.code === 'USER_NOT_FOUND' || err.status === 404 ? 'missing' : 'error'
  }
}

let glidePending = performance.getEntriesByType('navigation')[0]?.type === 'reload'

async function glideToTopAfterRefresh() {
  if (!glidePending) return
  glidePending = false
  const saved = history.state?.scroll?.top ?? 0
  if (saved <= 0 || matchMedia('(prefers-reduced-motion: reduce)').matches) return
  await nextTick()
  window.scrollTo({ top: saved, behavior: 'instant' })
  requestAnimationFrame(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
}

// GET /api/artists/favorites/{username}, newest first
const favorites = reactive({ items: [], loading: true, error: '' })

async function loadFavorites() {
  const requested = username.value
  Object.assign(favorites, { items: [], loading: true, error: '' })
  try {
    const items = await fetchFavoriteArtists(requested)
    if (requested === username.value) favorites.items = items ?? []
  } catch (err) {
    if (err.sessionEnded || requested !== username.value) return
    favorites.error = "We couldn't load favorite artists."
  } finally {
    if (requested === username.value) favorites.loading = false
  }
}

watch(username, () => {
  loadProfile()
  loadFavorites()
}, { immediate: true })
</script>

<style scoped>
.wrap { width: 100%; padding: 0 var(--page-gutter); display: flex; flex-direction: column; }
.wrap:has(.layout) { padding-left: 0; }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-2); }
h2 { font: 700 20px/28px var(--font-sans); margin: 0 0 var(--space-3); }
.meta { font: 500 14px/20px var(--font-sans); color: var(--ink-muted); margin: 0; }
.hint { margin: var(--space-1) 0 0; font: 500 12px/16px var(--font-sans); color: var(--ink-muted); }

.page-state { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-3); padding: var(--space-8) 0; }
p.page-state { display: block; }

.layout { flex: 1; display: grid; grid-template-columns: calc(400px + var(--page-gutter)) minmax(0, 1fr); }
.side :deep(.body) { padding-left: var(--page-gutter); }
.side { position: sticky; top: calc(var(--header-height) + env(safe-area-inset-top, 0px)); align-self: start; height: calc(100dvh - var(--header-height) - env(safe-area-inset-top, 0px)); overflow-y: auto; }
.main-col { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-8) 0 var(--space-8) var(--space-8); border-left: 1px solid var(--line); }

.badge { align-self: flex-start; margin: var(--space-2) 0 0; padding: var(--space-1) var(--space-3); border-radius: var(--radius-pill); background: var(--purple-soft); font: 600 12px/16px var(--font-sans); }
.stats { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-4); margin: var(--space-4) 0 0; padding-top: var(--space-4); border-top: 1px solid var(--line); }
.stats div { display: flex; flex-direction: column-reverse; }
.stats dt { font: 600 12px/16px var(--font-sans); color: var(--ink-muted); }
.stats dd { margin: 0; font: 700 20px/28px var(--font-sans); font-variant-numeric: tabular-nums; }
.action { margin-top: var(--space-4); text-align: center; }

.artists { display: grid; gap: var(--space-6); grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); list-style: none; margin: 0; padding: 0; }
.artist { align-items: center; color: var(--ink); display: flex; flex-direction: column; gap: var(--space-2); text-decoration: none; }
.artist:hover .artist-name { color: var(--link); text-decoration: underline; }
.artist:focus-visible { border-radius: var(--radius-sm); outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.artist-av { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-pill); object-fit: cover; width: 100%; }
.artist-av-empty { background: var(--purple-soft); color: var(--link); display: grid; font: 800 40px/1 var(--font-sans); place-items: center; }
.artist-name { font: 600 16px/24px var(--font-sans); max-width: 100%; overflow: hidden; text-align: center; text-overflow: ellipsis; white-space: nowrap; }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .side { position: static; height: auto; overflow: visible; margin-right: calc(-1 * var(--page-gutter)); border-bottom: 1px solid var(--line); }
  .side :deep(.body) { padding-right: var(--page-gutter); }
  .main-col { padding-left: var(--page-gutter); border-left: 0; }
}
</style>
