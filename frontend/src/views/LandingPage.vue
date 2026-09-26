<template>
  <HeroLayout>
    <div>
      <h1 class="wordmark">Aux</h1>
      <p class="tagline">Find people who hear it too.</p>
      <p class="lead">
        Upload the music you own, play it in your browser, and get matched with listeners who share your
        favorite artists and genres. No gatekeeping, no algorithm deciding for you.
      </p>
      <div class="cta">
        <router-link to="/register" class="btn primary">Create your account</router-link>
        <router-link to="/login" class="btn secondary">Log in</router-link>
      </div>
    </div>
  </HeroLayout>
</template>

<script setup>
import HeroLayout from '@/components/HeroLayout.vue'
</script>

<style scoped>
.wordmark { font: 800 clamp(64px, 11vw, 120px)/0.9 var(--font-sans); letter-spacing: -0.03em; color: var(--primary); margin: 0; }
[data-theme='dark'] .wordmark { color: var(--link); }
.tagline { font: 700 20px/28px var(--font-sans); margin: var(--space-4) 0 var(--space-2); }
.lead { color: var(--ink-muted); max-width: 52ch; margin: 0; }
.cta { display: flex; flex-wrap: wrap; gap: var(--space-3); margin-top: var(--space-6); }
</style>
