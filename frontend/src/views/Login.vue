<template>
  <div class="auth-page">
    <div class="container">
      <h1 class="logo">Login</h1>
      <form class="auth-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label for="username">Username</label>
          <input v-model="username" id="username" type="text" placeholder="Enter username" />
        </div>
        <div class="form-group">
          <label for="password">Password</label>
          <input v-model="password" id="password" type="password" placeholder="Enter password" />
        </div>

        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
        <button type="submit" class="btn primary">Login</button>
      </form>

      <p class="switch-auth">
        Don’t have an account?
        <router-link to="/register">Register here</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>

import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { postToAPI } from '@/utils/api.js'
import { useUserStore } from '@/stores/user.js'
import { API_BASE_URL } from '@/utils/variables.js'

const username = ref('')
const password = ref('')
const errorMessage = ref('')

const userStore = useUserStore()

const router = useRouter()
const route = useRoute()

const redirectTo = route.query.redirect || '/dashboard'
async function handleLogin(){
  try {
    const url = `${API_BASE_URL}/api/auth/login`
    const response = await postToAPI(url,{
      username: username.value,
      password: password.value
    }, true)

    if ('error' in response) {
      errorMessage.value = response.error
      return
    }

    userStore.login(username.value)
    username.value = ''
    password.value = ''
    await router.push(redirectTo)

  } catch (error) {
    errorMessage.value = error.message || 'Login failed.'
  }
}

</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&family=Libertinus+Math&display=swap');
.auth-page{align-items:center;background:#1e1e1e;display:flex;justify-content:center;min-height:100vh;}
.container{background:rgba(0,0,0,0.6);border-radius:8px;padding:2.5rem;width:400px;}
.logo{color:#f0f0f0;font-family:'Libertinus Math',serif;font-size:2.5rem;margin-bottom:2rem;text-align:center;}
.auth-form{display:flex;flex-direction:column;}
.form-group{margin-bottom:1.5rem;}
.form-group:last-child{margin-bottom:0;}
.form-group label{color:#ccc;display:block;margin-bottom:0.5rem;}
.form-group input{box-sizing:border-box;border:none;border-radius:5px;outline:none;padding:0.75rem;width:100%;}
.btn{background:#fff;border:none;border-radius:5px;color:#000;cursor:pointer;font-weight:500;padding:0.8rem 1.8rem;text-decoration:none;transition:all 0.3s;}
.btn:hover{background:#ccc;}
.switch-auth{color:#ccc;font-size:0.95rem;margin-top:1.5rem;text-align:center;}
.switch-auth a{color:#fff;text-decoration:underline;transition:color 0.3s;}
.switch-auth a:hover{color:#ccc;}
.error{color:#e74c3c;font-size:0.9rem;margin:0.5rem 0;text-align:center;}
</style>
