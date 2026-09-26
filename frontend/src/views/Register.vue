<template>
  <HeroLayout :show-join="false">
    <form class="auth-card" @submit.prevent="register">
      <h1>Create your account</h1>

      <label class="label" for="register-username">Username</label>
      <input
        id="register-username"
        v-model="username"
        class="input"
        type="text"
        autocomplete="username"
        minlength="3"
        maxlength="16"
        required
        autofocus
        aria-describedby="register-username-hint"
      />
      <p id="register-username-hint" class="hint">3 to 16 characters.</p>

      <label class="label" for="register-email">Email</label>
      <input id="register-email" v-model="email" class="input" type="email" autocomplete="email" required />

      <label class="label" for="register-password">Password</label>
      <input
        id="register-password"
        v-model="password"
        class="input"
        type="password"
        autocomplete="new-password"
        minlength="8"
        maxlength="32"
        required
        aria-describedby="register-password-hint"
      />
      <p id="register-password-hint" class="hint">8 to 32 characters.</p>

      <label class="label" for="register-confirm">Confirm password</label>
      <input id="register-confirm" v-model="confirmPassword" class="input" type="password" autocomplete="new-password" required />

      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>

      <button type="submit" class="btn primary submit" :disabled="submitting">Create account</button>

      <p class="switch">Have an account? <router-link to="/login">Log in</router-link></p>
    </form>
  </HeroLayout>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import HeroLayout from '@/components/HeroLayout.vue'
import { postToAPI } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'
import { API_BASE_URL } from '@/utils/variables.js'

const router = useRouter()
const userStore = useUserStore()

const username = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const errorMessage = ref('')
const submitting = ref(false)

async function register() {
  errorMessage.value = ''

  if (password.value !== confirmPassword.value) {
    errorMessage.value = "Those passwords don't match. Type the same password in both fields."
    return
  }

  submitting.value = true
  try {
    const response = await postToAPI(`${API_BASE_URL}/api/auth/register`, {
      username: username.value,
      email: email.value,
      password: password.value,
    })
    userStore.login(response.user.username, response.token)
    await router.push('/dashboard')
  } catch (err) {
    if (err.message === 'Username already exists') {
      errorMessage.value = 'That username is taken. Try another one.'
    } else if (err.message === 'password is too long') {
      errorMessage.value = 'That password is too long. Use fewer or simpler characters.'
    } else {
      errorMessage.value = "We couldn't create your account. Check that the server is running, then try again."
    }
  } finally {
    submitting.value = false
  }
}
</script>
