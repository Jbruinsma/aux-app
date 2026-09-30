<template>
  <HeroLayout>
    <form class="auth-card" @submit.prevent="register">
      <h1>Create your account</h1>

      <label class="label" for="register-email">Email</label>
      <input id="register-email" v-model="email" class="input" type="email" autocomplete="email" required autofocus />

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
      email: email.value,
      password: password.value,
    })
    userStore.login(response.user, response.token)
    await router.push({ name: 'Onboarding' })
  } catch (err) {
    if (err.code === 'EMAIL_TAKEN') {
      errorMessage.value = 'That email already has an account. Log in instead.'
    } else if (err.code === 'INVALID_FIELD' && err.parameter === 'password') {
      errorMessage.value = "That password isn't allowed. Use 8 to 32 characters."
    } else {
      errorMessage.value = "We couldn't create your account. Check that the server is running, then try again."
    }
  } finally {
    submitting.value = false
  }
}
</script>
