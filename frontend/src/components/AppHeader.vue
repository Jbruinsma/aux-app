<template>
  <header class="top">
    <div class="topin">
      <router-link class="logo" :to="loggedIn ? '/dashboard' : '/'">Aux</router-link>

      <nav v-if="loggedIn && userStore.onboarded" aria-label="Main">
        <router-link
          v-for="item in MEMBER_NAV"
          :key="item"
          :to="`/${item.toLowerCase()}`"
          :class="{ active: route.name === item }"
        >{{ item }}</router-link>
      </nav>
      <nav v-else-if="!loggedIn" aria-label="Main">
        <router-link v-for="item in GUEST_NAV" :key="item" to="/login">{{ item }}</router-link>
      </nav>

      <div class="actions">
        <template v-if="loggedIn && userStore.onboarded">
          <a
            :href="`/settings/${username}`"
            class="icon-link"
            :class="{ active: route.name === 'Settings' }"
            title="Settings"
            aria-label="Settings"
            @click.prevent="rerouteToSettings()"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          </a>
          <a
            :href="`/${username}`"
            class="avatar-link"
            :class="{ active: onOwnProfile }"
            title="Your profile"
            aria-label="Your profile"
            @click.prevent="rerouteToPublicProfile()"
          >
            <img v-if="avatarUrl" :src="avatarUrl" alt="" />
            <span v-else aria-hidden="true">{{ username.charAt(0).toUpperCase() }}</span>
          </a>
        </template>
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
import { useNotificationStore } from '@/stores/notification.js'

import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user.js'
import { useMusicStore } from '@/stores/music.js'
import { resolveCoverURL } from '@/utils/display.js'
import { rerouteToPublicProfile, rerouteToSettings } from '@/utils/reroutes.js'

const GUEST_NAV = ['Music', 'Charts', 'Friends', 'Explore']
const MEMBER_NAV = ['Music', 'Charts', 'Friends', 'Explore']

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notification = useNotificationStore()
const musicStore = useMusicStore()

const loggedIn = computed(() => userStore.loggedIn && !!userStore.userData?.username)
const username = computed(() => userStore.userData?.username ?? '')
const avatarUrl = computed(() => {
  const url = userStore.userData?.profilePictureUrl
  return url ? resolveCoverURL(url) : ''
})
const onOwnProfile = computed(() => route.name === 'Profile' && route.params.username === username.value)

async function logOut() {
  musicStore.saveLastPlayback(userStore.userData?.username)
  userStore.logout()
  notification.success('You are logged out.')
  await router.push('/')
}
</script>

<style scoped>
.top { position: sticky; top: env(safe-area-inset-top, 0px); z-index: 5; background: var(--surface); border-bottom: 1px solid var(--line); }
.topin { min-height: calc(var(--header-height) - 1px); display: flex; align-items: center; gap: var(--space-6); padding: var(--space-3) var(--page-gutter); }
.logo { margin-right: auto; font: 800 24px/1 var(--font-sans); color: var(--primary); letter-spacing: -0.02em; text-decoration: none; }
[data-theme='dark'] .logo { color: var(--link); }
nav { display: flex; gap: var(--space-4); overflow-x: auto; }
nav a { color: var(--ink-muted); text-decoration: none; font: 600 14px/20px var(--font-sans); white-space: nowrap; padding: var(--space-2) 0; border-bottom: 3px solid transparent; }
nav a:hover { color: var(--ink); border-bottom-color: var(--primary); }
nav a.active { color: var(--link); border-bottom-color: var(--primary); }
.actions { display: flex; align-items: center; gap: var(--space-2); padding-left: var(--space-4); border-left: 1px solid var(--line); }
.icon-link { display: grid; place-items: center; width: 36px; height: 36px; border-radius: var(--radius-pill); color: var(--ink-muted); }
.icon-link:hover { color: var(--ink); background: var(--surface-alt); }
.icon-link.active { color: var(--link); }
.avatar-link { display: grid; place-items: center; width: 32px; height: 32px; border-radius: var(--radius-pill); overflow: hidden; background: var(--primary); color: var(--on-primary); font: 700 14px/1 var(--font-sans); text-decoration: none; box-shadow: 0 0 0 2px var(--surface), 0 0 0 3px transparent; }
.avatar-link img { width: 100%; height: 100%; object-fit: cover; }
.avatar-link:hover { box-shadow: 0 0 0 2px var(--surface), 0 0 0 3px var(--line-strong); }
.avatar-link.active { box-shadow: 0 0 0 2px var(--surface), 0 0 0 4px var(--primary); }
@media (max-width: 720px) {
  .topin { min-height: 0; gap: var(--space-3); flex-wrap: wrap; }
  nav { order: 3; flex-basis: 100%; gap: var(--space-4); }
  .actions { padding-left: 0; border-left: 0; }
}
</style>
