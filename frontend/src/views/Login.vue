<template>
  <HeroLayout>
    <form class="auth-card" @submit.prevent="handleLogin">
      <h1>Log in to Aux</h1>

      <label class="label" for="login-email">Email</label>
      <input id="login-email" v-model="email" class="input" type="email" autocomplete="email" required autofocus />

      <label class="label" for="login-password">Password</label>
      <input id="login-password" v-model="password" class="input" type="password" autocomplete="current-password" required />

      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>

      <button type="submit" class="btn primary submit" :disabled="submitting">Log in</button>

      <button type="button" class="btn text forgot" :aria-expanded="showForgot ? 'true' : 'false'" @click="showForgot = !showForgot">
        Forgot your password?
      </button>
      <p v-if="showForgot" class="help">
        Resetting your password isn't available yet.
        <a href="https://github.com/Jbruinsma/aux-app/issues/new" target="_blank" rel="noopener">Report a problem</a>
        and the Aux team will help you get back into your account.
      </p>

      <p class="switch">New to Aux? <router-link to="/register">Sign up</router-link></p>
    </form>
  </HeroLayout>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import HeroLayout from '@/components/HeroLayout.vue'
import { postToAPI } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'
import { API_BASE_URL } from '@/utils/variables.js'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const email = ref('')
const password = ref('')
const errorMessage = ref('')
const showForgot = ref(false)
const submitting = ref(false)

async function handleLogin() {
  errorMessage.value = ''
  submitting.value = true
  try {
    const response = await postToAPI(`${API_BASE_URL}/api/auth/login`, {
      email: email.value,
      password: password.value,
    })
    userStore.login(response.user, response.token)
    await router.push(route.query.redirect || '/dashboard')
  } catch (err) {
    errorMessage.value = err.code === 'INVALID_CREDENTIALS'
      ? "That email and password don't match. Try again."
      : "We couldn't log you in. Check that the server is running, then try again."
  } finally {
    submitting.value = false
  }
}
</script>
