import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    loggedIn: false, userData: {}, token: null
  }),
  actions: {
    // `token` is the session token from /api/auth/login or /register, sent as `Authorization: Bearer <token>`
    login(username, token = null) {
      this.loggedIn = true
      this.userData = {'username': username}
      this.token = token
    },
    logout() {
      this.loggedIn = false
      this.userData = {}
      this.token = null
      useMusicStore().reset()
    },
    updateUsername(new_username){
      this.userData.username = new_username
    }
  },
  persist: {
    storage: localStorage,
  }
})


import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useMusicStore } from '@/stores/music.js'

export function authenticateLogin() {
  const router = useRouter()
  const userStore = useUserStore()

  onMounted(() => {
    if (!userStore.loggedIn) {
      router.push('/')
    }
  })
}
