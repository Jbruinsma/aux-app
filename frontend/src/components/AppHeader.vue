<template>
  <header class="top">
    <div class="topin">
      <router-link class="logo" :to="loggedIn ? '/dashboard' : '/'">Aux</router-link>

      <nav v-if="loggedIn" aria-label="Main">
        <a href="/dashboard" :class="{ active: route.name === 'Dashboard' }" @click.prevent="rerouteToDashboard()">Home</a>
        <a href="#" :class="{ active: route.name === 'Profile' }" @click.prevent="rerouteToPublicProfile()">Profile</a>
        <a href="#" :class="{ active: route.name === 'Settings' }" @click.prevent="rerouteToSettings()">Settings</a>
      </nav>
      <!-- These sections need an account, so logged-out visitors are sent to log in -->
      <nav v-else aria-label="Main">
        <router-link v-for="item in GUEST_NAV" :key="item" to="/login">{{ item }}</router-link>
      </nav>

      <div class="actions">
        <button v-if="loggedIn" class="btn text" @click="logOut">Log out</button>
        <template v-else>
          <router-link to="/login" class="btn text">Log in</router-link>
          <router-link to="/register" class="btn secondary">Sign up</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user.js'
import { useMusicStore } from '@/stores/music.js'
import { rerouteToDashboard, rerouteToPublicProfile, rerouteToSettings } from '@/utils/reroutes.js'

const GUEST_NAV = ['Music', 'Charts', 'Friends', 'Explore']

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const musicStore = useMusicStore()

const loggedIn = computed(() => userStore.loggedIn && !!userStore.userData?.username)

async function logOut() {
  musicStore.saveLastPlayback(userStore.userData?.username)
  userStore.logout()
  await router.push('/')
}
</script>

<style scoped>
.top { position: sticky; top: env(safe-area-inset-top, 0px); z-index: 5; background: var(--surface); border-bottom: 1px solid var(--line); }
.topin { max-width: 1120px; margin: 0 auto; display: flex; align-items: center; gap: var(--space-6); padding: var(--space-3) var(--space-6); }
.logo { margin-right: auto; font: 800 24px/1 var(--font-sans); color: var(--primary); letter-spacing: -0.02em; text-decoration: none; }
[data-theme='dark'] .logo { color: var(--link); }
nav { display: flex; gap: var(--space-4); overflow-x: auto; }
nav a { color: var(--ink-muted); text-decoration: none; font: 600 14px/20px var(--font-sans); white-space: nowrap; padding: var(--space-2) 0; border-bottom: 3px solid transparent; }
nav a:hover { color: var(--ink); border-bottom-color: var(--primary); }
nav a.active { color: var(--link); border-bottom-color: var(--primary); }
/* The divider separates the section links from the account buttons */
.actions { display: flex; align-items: center; gap: var(--space-2); padding-left: var(--space-4); border-left: 1px solid var(--line); }
@media (max-width: 720px) {
  .topin { gap: var(--space-3); padding: var(--space-3) var(--space-4); flex-wrap: wrap; }
  nav { order: 3; flex-basis: 100%; gap: var(--space-4); }
  .actions { padding-left: 0; border-left: 0; }
}
</style>
