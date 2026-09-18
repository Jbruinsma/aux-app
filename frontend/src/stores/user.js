import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    loggedIn: false, userData: {}
  }),
  actions: {
    login(username) {
      this.loggedIn = true
      this.userData = {'username': username}
    },
    logout() {
      this.loggedIn = false
      this.userData = {}
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
