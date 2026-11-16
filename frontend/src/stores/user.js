import { defineStore } from 'pinia'
import { API_BASE_URL } from '@/utils/variables.js'

export const useUserStore = defineStore('user', {
  state: () => ({
    loggedIn: false, userData: {}, token: null,
    // From the last AuthResponse: the single-use token for POST /api/auth/refresh, and when `token` expires (epoch ms)
    refreshToken: null, expiresAt: 0,
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
    // Register, login or refresh: the whole AuthResponse { token, expiresAt, refreshToken, refreshExpiresAt, user }
    startSession(response) {
      this.login(response.user, response.token)
      this.setTokens(response)
    },
    setTokens({ token, expiresAt, refreshToken }) {
      this.token = token
      this.expiresAt = Date.parse(expiresAt) || 0
      this.refreshToken = refreshToken ?? null
    },
    updateUser(user) {
      Object.assign(this.userData, user)
    },
    logout() {
      // Ends the session on the server too, so the refresh token can't be used again. Best effort: if it fails the
      // token just expires on its own
      if (this.refreshToken) {
        fetch(`${API_BASE_URL}/api/auth/logout`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: this.refreshToken }),
          keepalive: true,
        }).catch(() => {})
      }
      this.loggedIn = false
      this.userData = {}
      this.token = null
      this.refreshToken = null
      this.expiresAt = 0
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
