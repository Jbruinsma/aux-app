<template>
  <div class="aux-page with-player">
    <AppHeader />

    <main class="wrap">
      <p v-if="status === 'loading'" class="meta page-state">Loading @{{ username }}…</p>

      <section v-else-if="status === 'missing'" class="page-state">
        <h1>No one here goes by @{{ username }}</h1>
        <p class="meta">Check the spelling, or find people from your home screen.</p>
        <router-link :to="{ name: 'Dashboard' }" class="btn secondary">Go home</router-link>
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
            <p v-if="!profile.isMe && isFriend(followState)" class="badge">Friends</p>
            <p v-else-if="profile.followingMe && !profile.isMe" class="badge">Follows you</p>
            <dl class="stats">
              <div v-for="stat in stats" :key="stat.id">
                <dt>{{ stat.label }}</dt>
                <dd>
                  <a v-if="stat.id === 'uploads'" class="stat-link" href="#uploads-heading" :aria-label="`${stat.value} uploads, go to uploads`" @click.prevent="goToUploads">{{ stat.value }}</a>
                  <button
                    v-else-if="canSeeFollows"
                    type="button"
                    class="stat-link"
                    :aria-label="`${stat.value} ${stat.label.toLowerCase()}, show the list`"
                    @click="followDialog.open(stat.id)"
                  >{{ stat.value }}</button>
                  <template v-else>{{ stat.value }}</template>
                </dd>
              </div>
            </dl>
            <router-link
              v-if="profile.isMe"
              :to="{ name: 'Settings', params: { username: profile.username } }"
              class="btn secondary action"
            >Edit profile</router-link>
            <router-link v-else-if="!loggedIn" to="/login" class="btn secondary action">Log in to follow</router-link>
            <template v-else>
              <button
                type="button"
                class="btn action"
                :class="profile.isFollowing ? 'secondary' : 'primary'"
                :aria-pressed="profile.isFollowing ? 'true' : 'false'"
                :disabled="following"
                @click="toggleFollow"
              >{{ profile.isFollowing ? 'Following' : profile.followingMe ? 'Follow back' : 'Follow' }}</button>
              <p v-if="followError" class="error" role="alert">{{ followError }}</p>
            </template>
          </ProfileCard>
        </aside>

        <div class="main-col">
          <section v-if="compatibility" class="compatibility" aria-label="Musical compatibility">
            <div class="compatibility-top">
              <img v-if="pictureUrl" class="compatibility-avatar" :src="pictureUrl" alt="" />
              <span v-else class="compatibility-avatar compatibility-initial" aria-hidden="true">{{ profile.username.charAt(0).toUpperCase() }}</span>
              <p class="compatibility-line">Your musical compatibility with <b>{{ profile.username }}</b> is <span class="compatibility-level">{{ compatibility.label }}</span></p>
            </div>
            <div class="compatibility-meter" role="img" :aria-label="`${profile.compatibility} percent compatible`">
              <span v-for="n in 5" :key="n" class="compatibility-segment" :class="{ filled: n <= compatibility.filled }"></span>
              <span class="compatibility-percent">{{ profile.compatibility }}%</span>
            </div>
            <p v-if="sharedNames.length" class="compatibility-shared">
              You both listen to
              <template v-for="(name, index) in sharedNames" :key="name"><b>{{ name }}</b>{{ nameSeparator(index, sharedNames.length) }}</template>.
            </p>
          </section>

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
            <ArtistGrid v-else :artists="favorites.items" :add-music="false" />
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
            <h2 id="uploads-heading" tabindex="-1">Uploads</h2>
            <EmptyState
              v-if="uploads.unavailable"
              title="Uploads are coming soon"
              :text="`Songs @${profile.username} uploads will show up here, grouped by artist, once Aux can list them.`"
            />
            <EmptyState v-else-if="uploads.error" tone="error" :title="uploads.error" text="Check that the server is running, then try again.">
              <button type="button" class="btn secondary" @click="loadUploads()">Try again</button>
            </EmptyState>
            <EmptyState v-else-if="uploads.loading && !uploads.items.length" tone="loading" title="Loading uploads…" />
            <EmptyState
              v-else-if="!uploads.items.length"
              title="No uploads yet"
              :text="profile.isMe
                ? 'Songs you upload show up here, where others can play and download them.'
                : `Songs @${profile.username} uploads show up here, ready to play and download.`"
            >
              <router-link v-if="profile.isMe" :to="{ name: 'Upload' }" class="btn secondary">Upload music</router-link>
            </EmptyState>
            <template v-else>
              <div v-for="group in uploadGroups" :key="group.artist.artistId || group.artist.artistName" class="artist-group">
                <component
                  :is="group.artist.artistId ? 'router-link' : 'div'"
                  class="group-head"
                  :to="group.artist.artistId ? { name: 'Artist', params: { artistId: group.artist.artistId } } : null"
                >
                  <img v-if="group.artist.artistPfpUrl" class="group-av" :src="resolveCoverURL(group.artist.artistPfpUrl)" alt="" loading="lazy" />
                  <span v-else class="group-av group-av-empty" aria-hidden="true">{{ group.artist.artistName.charAt(0).toUpperCase() }}</span>
                  <span class="group-name">{{ group.artist.artistName }}</span>
                </component>
                <TrackRows
                  :pieces="group.pieces"
                  :label="`Uploads by ${group.artist.artistName}`"
                  :link-artists="false"
                  ranked
                  @play="(index) => playUpload(group.pieces[index])"
                />
              </div>
              <button v-if="uploads.nextCursor" type="button" class="btn secondary more" :disabled="uploads.loading" @click="loadUploads(uploads.nextCursor)">
                {{ uploads.loading ? 'Loading…' : 'Show more' }}
              </button>
            </template>
          </section>
        </div>
      </div>
    </main>

    <FollowListDialog
      v-if="profile"
      ref="followDialog"
      :username="profile.username"
      :counts="{ followers: profile.followersCount ?? '0', following: profile.followingCount ?? '0' }"
      @changed="refreshProfile"
    />
    <AppFooter />
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import ArtistGrid from '@/components/ArtistGrid.vue'
import EmptyState from '@/components/EmptyState.vue'
import FollowListDialog from '@/components/FollowListDialog.vue'
import TrackRows from '@/components/TrackRows.vue'
import PlaylistGrid from '@/components/PlaylistGrid.vue'
import ProfileCard from '@/components/ProfileCard.vue'
import { fetchAPI } from '@/utils/api.js'
import { resolveCoverURL } from '@/utils/display.js'
import { useUserStore } from '@/stores/user.js'
import { API_BASE_URL } from '@/utils/variables.js'
import { COUNTRIES } from '@/utils/countries.js'
import { compatibilityLevel, nameSeparator } from '@/utils/matches.js'
import { fetchFavoriteArtists } from '@/utils/artist.js'
import { isFriend, setFollowing } from '@/utils/follows.js'
import { USER_UPLOADS_MOCKED, fetchUploads, fetchUserUploads, groupByArtist } from '@/utils/music.js'
import { useMusicStore } from '@/stores/music.js'

const route = useRoute()
const userStore = useUserStore()
const musicStore = useMusicStore()

const username = computed(() => route.params.username)
const loggedIn = computed(() => userStore.loggedIn)

const profile = ref(null)
const status = ref('loading')

// A snapshot from the profile response, not the live matches event. Zero is a real score.
const compatibility = computed(() => profile.value?.compatibility == null ? null : compatibilityLevel(profile.value.compatibility))
const sharedNames = computed(() => {
  if (!profile.value?.compatibility) return []
  const artists = profile.value.sharedArtists ?? []
  return (artists.length ? artists.map((artist) => artist.artistName) : profile.value.sharedTags ?? []).slice(0, 3)
})

// Counts come formatted by the server ("1.2K"). Followers and Following open their lists, unless the user's
// followsVisibility leaves the caller out (`canViewFollows`, not sent yet, so treated as true until it is)
const stats = computed(() => [
  { id: 'uploads', label: 'Uploads', value: profile.value.uploadsCount ?? '0' },
  { id: 'followers', label: 'Followers', value: profile.value.followersCount ?? '0' },
  { id: 'following', label: 'Following', value: profile.value.followingCount ?? '0' },
])
const canSeeFollows = computed(() => profile.value.isMe || profile.value.canViewFollows !== false)
const followState = computed(() => ({ isFollowing: profile.value?.isFollowing, followsMe: profile.value?.followingMe }))
const followDialog = ref(null)

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

// Picks up new counts after a follow, without showing the loading state
async function refreshProfile() {
  const requested = username.value
  try {
    const data = await fetchAPI(`${API_BASE_URL}/api/users/profile/${encodeURIComponent(requested)}`, { notifyOnError: false })
    if (requested === username.value) profile.value = data
  } catch {
    // The page keeps what it has
  }
}

const following = ref(false)
const followError = ref('')

// The button flips straight away and goes back if the request fails. The counts are strings ("1.2K"), so the new ones
// come from loading the profile again rather than adding 1
async function toggleFollow() {
  const current = profile.value
  const follow = !current.isFollowing
  followError.value = ''
  following.value = true
  current.isFollowing = follow
  try {
    await setFollowing(current.username, follow)
    refreshProfile()
  } catch (err) {
    current.isFollowing = !follow
    if (err.sessionEnded) return
    followError.value = err.code === 'USER_NOT_FOUND'
      ? `@${current.username} isn't on Aux anymore. They may have changed their username.`
      : `We couldn't ${follow ? 'follow' : 'unfollow'} @${current.username}. Check that the server is running, then try again.`
  } finally {
    following.value = false
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

// Uploads: your own from GET /api/music-pieces; anyone else's need GET /api/users/{username}/music-pieces, which isn't in
// the API yet (USER_UPLOADS_MOCKED). Shown grouped by artist, most played first
const uploads = reactive({ items: [], nextCursor: null, loading: true, error: '', unavailable: false })
const uploadGroups = computed(() => groupByArtist(uploads.items))
const uploadQueue = computed(() => uploadGroups.value.flatMap((group) => group.pieces))

function isOwnProfile(requested) {
  return requested === userStore.userData?.username
}

async function loadUploads(cursor = null) {
  const requested = username.value
  const own = isOwnProfile(requested)
  if (!cursor) Object.assign(uploads, { items: [], nextCursor: null, error: '', unavailable: !own && USER_UPLOADS_MOCKED })
  if (uploads.unavailable) {
    uploads.loading = false
    return
  }
  uploads.loading = true
  try {
    const page = own
      ? { musicPieces: (await fetchUploads()).uploadedMusicPieces ?? [], nextCursor: null }
      : await fetchUserUploads(requested, cursor)
    if (requested !== username.value) return
    uploads.items = cursor ? [...uploads.items, ...page.musicPieces] : page.musicPieces
    uploads.nextCursor = page.nextCursor ?? null
  } catch (err) {
    if (err.sessionEnded || requested !== username.value) return
    uploads.error = "We couldn't load uploads."
  } finally {
    if (requested === username.value) uploads.loading = false
  }
}

// Plays the upload with the rest queued after it, in the order shown
function playUpload(piece) {
  musicStore.playPieces(uploadQueue.value, uploadQueue.value.indexOf(piece))
}

async function goToUploads() {
  const heading = document.getElementById('uploads-heading')
  heading?.scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' })
  heading?.focus({ preventScroll: true })
}

watch(username, () => {
  loadProfile()
  loadFavorites()
  loadUploads()
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

.compatibility { background: var(--surface-alt); border-radius: var(--radius-md); display: flex; flex-direction: column; gap: var(--space-3); max-width: 420px; padding: var(--space-4); }
.compatibility-top { align-items: center; display: flex; gap: var(--space-3); }
.compatibility-avatar { border-radius: var(--radius-pill); flex: none; height: 44px; object-fit: cover; width: 44px; }
.compatibility-initial { background: var(--primary); color: var(--on-primary); display: grid; font: 700 17px/1 var(--font-sans); place-items: center; }
.compatibility-line { font: 400 15px/22px var(--font-sans); margin: 0; overflow-wrap: anywhere; }
.compatibility-line b { font-weight: 600; }
.compatibility-level { color: var(--link); font-weight: 700; }
.compatibility-meter { align-items: center; display: flex; gap: var(--space-1); }
.compatibility-segment { background: var(--surface); border-radius: 2px; flex: 1; height: 8px; }
.compatibility-segment.filled { background: var(--purple); }
.compatibility-percent { color: var(--ink-muted); font: 600 12px/16px var(--font-sans); margin-left: var(--space-2); }
.compatibility-shared { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; overflow-wrap: anywhere; }
.compatibility-shared b { color: var(--ink); font-weight: 600; }

.badge { align-self: flex-start; margin: var(--space-2) 0 0; padding: var(--space-1) var(--space-3); border-radius: var(--radius-pill); background: var(--purple-soft); font: 600 12px/16px var(--font-sans); }
.stats { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-4); margin: var(--space-4) 0 0; padding-top: var(--space-4); border-top: 1px solid var(--line); }
.stats div { display: flex; flex-direction: column-reverse; }
.stats dt { font: 600 12px/16px var(--font-sans); color: var(--ink-muted); }
.stats dd { margin: 0; font: 700 20px/28px var(--font-sans); font-variant-numeric: tabular-nums; }
.action { margin-top: var(--space-4); text-align: center; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: var(--space-2) 0 0; }
.stat-link { background: none; border: 0; border-radius: var(--radius-sm); color: inherit; cursor: pointer; font: inherit; padding: 0; text-decoration: none; }
.stat-link:hover { color: var(--link); text-decoration: underline; }
#uploads-heading { scroll-margin-top: calc(var(--header-height) + var(--space-4)); }
#uploads-heading:focus { outline: none; }

.artist-group + .artist-group { margin-top: var(--space-6); }
.group-head { align-items: center; color: var(--ink); display: flex; gap: var(--space-3); margin-bottom: var(--space-3); text-decoration: none; width: fit-content; }
a.group-head:hover .group-name { color: var(--link); text-decoration: underline; }
.group-av { border-radius: var(--radius-pill); flex: none; height: 32px; object-fit: cover; width: 32px; }
.group-av-empty { background: var(--purple-soft); color: var(--link); display: grid; font: 700 14px/1 var(--font-sans); place-items: center; }
.group-name { font: 600 16px/24px var(--font-sans); }
.more { margin-top: var(--space-6); }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .side { position: static; height: auto; overflow: visible; margin-right: calc(-1 * var(--page-gutter)); border-bottom: 1px solid var(--line); }
  .side :deep(.body) { padding-right: var(--page-gutter); }
  .main-col { padding-left: var(--page-gutter); border-left: 0; }
}
</style>
