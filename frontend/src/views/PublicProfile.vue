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
          <ProfileCard flat :username="profile.username" :picture-url="pictureUrl">
            <p v-if="profile.followingMe && !profile.isMe" class="badge">Follows you</p>
            <dl class="stats">
              <div>
                <dt>Playlists</dt>
                <dd>{{ profile.playlists.length }}</dd>
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
        </div>
      </div>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
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

const route = useRoute()
const userStore = useUserStore()

const username = computed(() => route.params.username)
const loggedIn = computed(() => userStore.loggedIn)

const profile = ref(null)
const status = ref('loading') // loading | ready | missing | error

const pictureUrl = computed(() => (profile.value?.pfpUrl ? resolveCoverURL(profile.value.pfpUrl) : ''))

// Playlists here are already filtered by the backend: other people only see public ones
async function loadProfile() {
  const requested = username.value
  status.value = 'loading'
  try {
    const data = await fetchAPI(`${API_BASE_URL}/api/users/profile/${encodeURIComponent(requested)}`)
    // Ignore a slow answer for a profile the user has already navigated away from
    if (requested !== username.value) return
    profile.value = data
    status.value = 'ready'
    glideToTopAfterRefresh()
  } catch (err) {
    if (requested !== username.value) return
    status.value = err.code === 'USER_NOT_FOUND' || err.status === 404 ? 'missing' : 'error'
  }
}

// After a refresh, start where the reader was (Vue Router keeps it in history.state) and glide back to the top.
// Only once per page load, and never when the reader prefers reduced motion.
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

// Also reloads when moving from one profile straight to another, where the component is reused
watch(username, loadProfile, { immediate: true })
</script>

<style scoped>
.wrap { width: 100%; padding: 0 var(--page-gutter); display: flex; flex-direction: column; }
/* The profile panel runs to the window's left edge, so the page drops its left gutter once it's showing */
.wrap:has(.layout) { padding-left: 0; }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-2); }
h2 { font: 700 20px/28px var(--font-sans); margin: 0 0 var(--space-3); }
.meta { font: 500 14px/20px var(--font-sans); color: var(--ink-muted); margin: 0; }
.hint { margin: var(--space-1) 0 0; font: 500 12px/16px var(--font-sans); color: var(--ink-muted); }

.page-state { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-3); padding: var(--space-8) 0; }
p.page-state { display: block; }

/* The profile fills the left side; a full-height divider separates it from the listening sections */
.layout { flex: 1; display: grid; grid-template-columns: calc(400px + var(--page-gutter)) minmax(0, 1fr); }
/* The banner fills the panel edge to edge; the text inside lines up with the Aux logo */
.side :deep(.body) { padding-left: var(--page-gutter); }
/* The profile fills the left side from under the top bar to the bottom of the window and stays put while
   the right side scrolls; the banner sits flush against the top bar and the divider */
.side { position: sticky; top: calc(var(--header-height) + env(safe-area-inset-top, 0px)); align-self: start; height: calc(100dvh - var(--header-height) - env(safe-area-inset-top, 0px)); overflow-y: auto; }
.main-col { display: flex; flex-direction: column; gap: var(--space-8); padding: var(--space-8) 0 var(--space-8) var(--space-8); border-left: 1px solid var(--line); }

.badge { align-self: flex-start; margin: var(--space-2) 0 0; padding: var(--space-1) var(--space-3); border-radius: var(--radius-pill); background: var(--purple-soft); font: 600 12px/16px var(--font-sans); }
.stats { display: flex; gap: var(--space-6); margin: var(--space-4) 0 0; padding-top: var(--space-4); border-top: 1px solid var(--line); }
.stats div { display: flex; flex-direction: column-reverse; }
.stats dt { font: 600 12px/16px var(--font-sans); color: var(--ink-muted); }
.stats dd { margin: 0; font: 700 20px/28px var(--font-sans); }
.action { margin-top: var(--space-4); text-align: center; }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  /* Stacked: the panel spans the full window width, and the sections below keep both gutters */
  .side { position: static; height: auto; overflow: visible; margin-right: calc(-1 * var(--page-gutter)); border-bottom: 1px solid var(--line); }
  .side :deep(.body) { padding-right: var(--page-gutter); }
  .main-col { padding-left: var(--page-gutter); border-left: 0; }
}
</style>
