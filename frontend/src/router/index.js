import { nextTick } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import LandingPage from '../views/LandingPage.vue'
import Login from '@/views/Login.vue'
import Register from '@/views/Register.vue'
import Dashboard from '@/views/Dashboard.vue'
import PublicProfile from '@/views/PublicProfile.vue'
import Settings from '@/views/Settings.vue'
import CreatePlaylist from '@/views/CreatePlaylist.vue'
import PlaylistDetail from '@/views/PlaylistDetail.vue'
import AddMusic from '@/views/AddMusic.vue'
import AddFriends from '@/views/AddFriends.vue'
import EditPlaylist from '@/views/EditPlaylist.vue'
import EditMusicPiece from '@/views/EditMusicPiece.vue'
import Onboarding from '@/views/Onboarding.vue'
import { useUserStore } from '@/stores/user.js'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: LandingPage,
    // Logged-in users get their own home instead of the public landing page
    beforeEnter: () => (useUserStore().loggedIn ? { name: 'Dashboard' } : true),
  },
  {
    path: '/login',
    name: 'Login',
    component: Login,
    beforeEnter: () => (useUserStore().loggedIn ? { name: 'Dashboard' } : true),
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
    beforeEnter: () => (useUserStore().loggedIn ? { name: 'Dashboard' } : true),
  },
  {
    path: '/onboarding',
    name: 'Onboarding',
    component: Onboarding,
    beforeEnter: () => {
      const userStore = useUserStore()
      if (!userStore.loggedIn) return { name: 'Login' }
      return userStore.onboarded ? { name: 'Dashboard' } : true
    },
  },
  { path: '/dashboard', name: 'Dashboard', component: Dashboard},
  { path: '/:username', name: 'Profile', component: PublicProfile },
  { path: '/settings/:username', name: 'Settings', component: Settings},
  { path: '/create', name: 'Create', component: CreatePlaylist},
  { path: '/playlist/:username/:id', name: 'Playlist', component: PlaylistDetail },
  { path: '/add/:username/:id', name: 'Add', component: AddMusic },
  { path: '/add-friends/:username/:id', name: 'AddFriends', component: AddFriends },
  { path: '/edit-playlist/:username/:id', name: 'Edit', component: EditPlaylist },
  { path: '/:username/:playlist_id/edit-mp3/:index/:uuid', name: 'EditMP3', component: EditMusicPiece }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    // Leave room for the sticky header when jumping to a section by its #id
    if (to.hash) return { el: to.hash, top: 72, behavior: 'smooth' }
    return { top: 0 }
  },
})

// Accounts that haven't picked a username and photo yet can't use the rest of the app
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (userStore.loggedIn && !userStore.onboarded && to.name !== 'Onboarding') return { name: 'Onboarding' }
})

// Moving between these pages morphs the shared pieces (auth-card, brand-blocks) instead of swapping the page
const MORPH_ROUTES = new Set(['Home', 'Login', 'Register', 'Onboarding'])
let finishNavigation = null
router.afterEach(() => finishNavigation?.())

router.beforeResolve((to, from) => {
  const morph = document.startViewTransition && MORPH_ROUTES.has(to.name) && MORPH_ROUTES.has(from.name) && to.name !== from.name
  if (!morph) return
  // Hold the navigation until the browser has captured the old page, then let the transition wait for the new one
  return new Promise((resolve) => {
    document.startViewTransition(async () => {
      const navigated = new Promise((done) => (finishNavigation = done))
      resolve()
      await navigated
      finishNavigation = null
      await nextTick()
    })
  })
})

export default router
