<template>
  <div class="aux-page with-player">
    <AppHeader />
    <main class="home">
      <section class="welcome">
        <img v-if="avatarUrl" class="av av-img" :src="avatarUrl" alt="" />
        <div v-else class="av" aria-hidden="true">{{ initial }}</div>
        <div>
          <h1>Welcome back, {{ username }}</h1>
          <p class="meta">{{ playlistSummary }}</p>
        </div>
      </section>

      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>

      <div class="layout">
        <div class="main-col">
          <section aria-labelledby="playlists-heading">
            <div class="section-head">
              <h2 id="playlists-heading">Your playlists</h2>
              <router-link to="/create" class="btn primary">Create playlist</router-link>
            </div>

            <p v-if="loading" class="meta">Loading your playlists…</p>
            <EmptyState
              v-else-if="playlists.length === 0 && !errorMessage"
              title="No playlists yet"
              text="Create a playlist and upload an MP3 to start your library."
            />
            <PlaylistGrid v-else :playlists="playlists" :owner="username" />
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

          <section class="panel" aria-labelledby="find-heading">
            <h2 id="find-heading">Find people</h2>
            <form class="find" @submit.prevent="openProfile">
              <label class="label" for="find-username">Username</label>
              <div class="find-row">
                <input
                  id="find-username"
                  v-model="searchQuery"
                  class="input"
                  type="text"
                  autocomplete="off"
                  placeholder="jordan.k"
                />
                <button class="btn secondary" type="submit" :disabled="!searchQuery.trim()">View profile</button>
              </div>
            </form>
          </section>
        </aside>
      </div>
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import EmptyState from '@/components/EmptyState.vue'
import PlaylistGrid from '@/components/PlaylistGrid.vue'
import { fetchAPI } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'
import { resolveCoverURL } from '@/utils/display.js'
import { API_BASE_URL } from '@/utils/variables.js'

const router = useRouter()
const userStore = useUserStore()

const profile = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const searchQuery = ref('')

const username = computed(() => profile.value?.username ?? userStore.userData?.username ?? '')
const initial = computed(() => username.value.charAt(0).toUpperCase())
const avatarUrl = computed(() => (profile.value?.pfpUrl ? resolveCoverURL(profile.value.pfpUrl) : null))
const playlists = computed(() => profile.value?.playlists ?? [])

const playlistSummary = computed(() => {
  if (loading.value) return 'Your music, your people.'
  const count = playlists.value.length
  return count === 1 ? '1 playlist' : `${count} playlists`
})

function openProfile() {
  const target = searchQuery.value.trim()
  if (!target) return
  router.push({ name: 'Profile', params: { username: target } })
}

onMounted(async () => {
  const currentUser = userStore.userData?.username
  if (!currentUser) {
    await router.push({ name: 'Login', query: { redirect: '/dashboard' } })
    return
  }

  try {
    profile.value = await fetchAPI(`${API_BASE_URL}/api/users/profile/${encodeURIComponent(currentUser)}`)
  } catch {
    errorMessage.value = "We couldn't load your profile. Check that the server is running, then refresh the page."
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.home { width: 100%; padding: 0 var(--page-gutter) var(--space-8); }
h1 { font: 700 28px/34px var(--font-sans); margin: 0; }
h2 { font: 700 20px/28px var(--font-sans); margin: 0 0 var(--space-3); }
.meta { font: 500 14px/20px var(--font-sans); color: var(--ink-muted); margin: 0; }
.error { color: var(--danger); font: 500 14px/20px var(--font-sans); margin: var(--space-4) 0 0; }

.welcome { display: flex; align-items: center; gap: var(--space-4); padding: var(--space-8) 0 var(--space-6); border-bottom: 1px solid var(--line); }
.av { width: 64px; height: 64px; border-radius: var(--radius-pill); background: var(--primary); color: var(--on-primary); display: grid; place-items: center; font: 700 24px/1 var(--font-sans); flex: none; }
.av-img { object-fit: cover; background: var(--surface-alt); }

.layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: var(--space-8); padding-top: var(--space-6); }
.main-col { display: flex; flex-direction: column; gap: var(--space-8); }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); margin-bottom: var(--space-3); flex-wrap: wrap; }
.section-head h2 { margin: 0; }


.side { display: flex; flex-direction: column; gap: var(--space-4); }
.panel { background: var(--surface-alt); border-radius: var(--radius-md); padding: var(--space-4); display: flex; flex-direction: column; gap: var(--space-3); }
.panel h2 { margin: 0; }
.meter { display: flex; gap: var(--space-1); }
.seg { flex: 1; height: 8px; border-radius: 2px; background: var(--surface); }

.find { display: flex; flex-direction: column; gap: var(--space-2); }
.label { font: 600 14px/20px var(--font-sans); }
.find-row { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.input { flex: 1; min-width: 0; font: 400 16px/24px var(--font-sans); padding: 8px 12px; color: var(--ink); background: var(--surface); border: 1px solid var(--line-strong); border-radius: var(--radius-sm); }

@media (max-width: 860px) {
  .layout { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .welcome { padding-top: var(--space-6); }
  .av { width: 48px; height: 48px; font-size: 19px; }
}
</style>
