<template>
  <div>
    <section class="hero">
      <div class="container">
        <h1 class="logo">Unchained</h1>
        <p class="tagline">Your Music. Unchained.</p>
        <div class="buttons">
          <router-link to="/register" class="btn primary">Get Started</router-link>
          <router-link to="/login" class="btn outline">Login</router-link>
        </div>
      </div>
    </section>

    <section id="features" class="section dark">
      <div class="container features">
        <div class="feature">
          <div class="icon">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"
                 stroke-width="1.5" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round"
                    d="m9 9 10.5-3m0 6.553v3.75a2.25 2.25 0 0 1-1.632 2.163l-1.32.377a1.803 1.803 0 1 1-.99-3.467l2.31-.66a2.25 2.25 0 0 0 1.632-2.163Zm0 0V2.25L9 5.25v10.303m0 0v3.75a2.25 2.25 0 0 1-1.632 2.163l-1.32.377a1.803 1.803 0 0 1-.99-3.467l2.31-.66A2.25 2.25 0 0 0 9 15.553Z"/>
            </svg>
          </div>
          <h3>Upload MP3s</h3>
          <p>Store your personal music collection, stream it from anywhere, totally yours.</p>
        </div>
        <div class="feature">
          <div class="icon">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"
                 stroke-width="1.5" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round"
                    d="M2.25 12.75V12A2.25 2.25 0 0 1 4.5 9.75h15A2.25 2.25 0 0 1 21.75 12v.75m-8.69-6.44-2.12-2.12a1.5 1.5 0 0 0-1.061-.44H4.5A2.25 2.25 0 0 0 2.25 6v12a2.25 2.25 0 0 0 2.25 2.25h15A2.25 2.25 0 0 0 21.75 18V9a2.25 2.25 0 0 0-2.25-2.25h-5.379a1.5 1.5 0 0 1-1.06-.44Z"/>
            </svg>
          </div>
          <h3>Build Playlists</h3>
          <p>Create custom playlists, organize your songs how you want, no restrictions.</p>
        </div>
        <div class="feature">
          <div class="icon">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"
                 stroke-width="1.5" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round"
                    d="M9 17.25v1.007a3 3 0 0 1-.879 2.122L7.5 21h9l-.621-.621A3 3 0 0 1 15 18.257V17.25m6-12V15a2.25 2.25 0 0 1-2.25 2.25H5.25A2.25 2.25 0 0 1 3 15V5.25m18 0A2.25 2.25 0 0 0 18.75 3H5.25A2.25 2.25 0 0 0 3 5.25m18 0V12a2.25 2.25 0 0 1-2.25 2.25H5.25A2.25 2.25 0 0 1 3 12V5.25"/>
            </svg>
          </div>
          <h3>Local Playback</h3>
          <p>Play, pause, shuffle your music in-browser — fast and private, without algorithms.</p>
        </div>
      </div>
    </section>

    <section class="section learn-more">
      <div class="container">
        <h2>Learn More.</h2>
        <router-link to="https://github.com/Jbruinsma/Unchained" class="btn primary">Learn More</router-link>
      </div>
    </section>

    <footer>
      <div class="container">
        <p>Created by Justin Bruinsma — Crafted with purpose, structure, and a love for music.</p>
      </div>
    </footer>
  </div>
</template>

<script setup>

import { useUserStore } from '@/stores/user.js'
import { useMusicStore } from '@/stores/music.js'

const userStore = useUserStore()
const musicStore = useMusicStore()

const currentUser = userStore.userData.username
musicStore.saveLastPlayback(currentUser)
userStore.logout()

</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&family=Libertinus+Math&display=swap');
* { box-sizing: border-box; }
body, html { font-family: 'Roboto', sans-serif; }
.btn { border-radius: 5px; font-weight: 500; padding: 0.8rem 1.8rem; text-decoration: none; transition: all 0.3s; }
.btn.outline { background: transparent; border: 2px solid #fff; color: #fff; }
.btn.outline:hover { background: #fff; color: #000; }
.btn.primary { background: #fff; color: #000; }
.btn.primary:hover { background: #ccc; }
.buttons { display: flex; flex-wrap: wrap; gap: 1rem; justify-content: center; }
.container { margin: auto; max-width: 1200px; padding: 3rem 1rem; width: 90%; }
.feature { background: rgba(255,255,255,0.05); border-radius: 8px; flex: 1 1 250px; padding: 2rem; }
.feature h3 { font-size: 1.5rem; margin-bottom: 1rem; margin-top: 1rem; }
.features { display: flex; flex-wrap: wrap; gap: 2rem; justify-content: center; }
footer { background: #000; color: #888; padding: 2rem 1rem; text-align: center; }
.hero { align-items: center; background: #000; display: flex; justify-content: center; min-height: 100vh; text-align: center; }
.icon svg { height: 50px; stroke: #fff; width: 50px; }
.logo { font-family: 'Libertinus Math', serif; font-size: 4rem; }
.section { background: #1e1e1e; text-align: center; }
.section.dark { background: #2c2c2c; }
.section.learn-more { background: #1e1e1e; }
.section h2 { font-size: 2.5rem; margin-bottom: 1rem; }
.tagline { color: #ccc; font-size: 1.8rem; margin-bottom: 2rem; }
</style>
