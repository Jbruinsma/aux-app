import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    loggedIn: false, userData: {}, token: null
  }),
  getters: {
    onboarded: (state) => state.userData.onboardingStep === 'DONE',
  },
  actions: {
    // `token` is the session token from /api/auth/login or /register, sent as `Authorization: Bearer <token>`
    // `user` is the UserSummary from the backend: {userId, username, profilePictureUrl, onboardingStep}
    login(user, token = null) {
      this.loggedIn = true
      this.userData = { ...user }
      this.token = token
    },
    updateUser(user) {
      Object.assign(this.userData, user)
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
