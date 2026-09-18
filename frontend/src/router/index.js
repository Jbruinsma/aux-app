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

const routes = [
  { path: '/', name: 'Home', component: LandingPage },
  { path: '/login', name: 'Login', component: Login},
  { path: '/register', name: 'Register', component: Register},
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
  routes
})

export default router
