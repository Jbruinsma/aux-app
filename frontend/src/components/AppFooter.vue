<template>
  <footer class="foot">
    <div class="footin">
      <div class="cols">
        <div class="col">
          <h2>Aux</h2>
          <ul>
            <li><router-link to="/music">Music</router-link></li>
          </ul>
        </div>
        <div class="col">
          <h2>Help</h2>
          <ul>
            <li><a :href="`${REPO_URL}/issues/new`" target="_blank" rel="noopener">Report A Problem</a></li>
            <li><a :href="`${REPO_URL}#readme`" target="_blank" rel="noopener">Getting Started</a></li>
          </ul>
        </div>
        <div class="col">
          <h2>Account</h2>
          <ul v-if="loggedIn">
            <li><router-link to="/music">Music</router-link></li>
            <li><router-link :to="{ name: 'Profile', params: { username } }">Your Profile</router-link></li>
            <li><router-link :to="{ name: 'Settings', params: { username } }">Settings</router-link></li>
          </ul>
          <ul v-else>
            <li><router-link to="/login">Log In</router-link></li>
            <li><router-link to="/register">Sign Up</router-link></li>
          </ul>
        </div>
        <div class="col">
          <h2>Feedback</h2>
          <ul>
            <li><a :href="`${REPO_URL}/issues/new`" target="_blank" rel="noopener">Send Feedback</a></li>
            <li><a :href="`${REPO_URL}/issues`" target="_blank" rel="noopener">Known Issues</a></li>
          </ul>
        </div>
        <div class="col">
          <h2>Follow us</h2>
          <ul>
            <li><a :href="REPO_URL" target="_blank" rel="noopener">GitHub</a></li>
          </ul>
        </div>
        <div class="col">
          <h2>Team</h2>
          <ul class="team">
            <li v-for="member in TEAM" :key="member.name">
              <a v-if="member.github" :href="member.github" target="_blank" rel="noopener noreferrer">{{ member.name }}</a>
              <template v-else>{{ member.name }}</template>
            </li>
          </ul>
        </div>
      </div>
      <p class="legal">© {{ year }} Aux · Built for CSCI 318</p>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/stores/user.js'

const REPO_URL = 'https://github.com/Jbruinsma/aux-app'
const TEAM = [
  { name: 'Justin', github: 'https://github.com/Jbruinsma' },
  { name: 'Mo', github: 'https://github.com/fwmo' },
  { name: 'RJ', github: 'https://github.com/Arjay05Rai' },
  { name: 'Muaz' },
  { name: 'Milind' },
]

const userStore = useUserStore()
const username = computed(() => userStore.userData?.username)
const loggedIn = computed(() => userStore.loggedIn && !!username.value)
const year = new Date().getFullYear()
</script>

<style scoped>
.foot { background: var(--surface-alt); border-top: 1px solid var(--line); }
.footin { padding: var(--space-8) var(--page-gutter) var(--space-6); }
.cols { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: var(--space-6); }
h2 { font: 700 14px/20px var(--font-sans); color: var(--ink); margin: 0 0 var(--space-3); }
ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--space-2); }
li { font: 500 14px/20px var(--font-sans); color: var(--ink-muted); }
a { color: var(--ink-muted); text-decoration: none; }
a:hover { color: var(--link); text-decoration: underline; }
.legal { margin: var(--space-8) 0 0; padding-top: var(--space-4); border-top: 1px solid var(--line); font: 500 12px/16px var(--font-sans); color: var(--ink-muted); }
@media (max-width: 860px) {
  .cols { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 520px) {
  .cols { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
