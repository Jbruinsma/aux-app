<template>
  <div class="aux-page">
    <AppHeader />

    <main>
      <section class="hero">
        <div class="wrap hero-in">
          <slot />
          <BrandBlocks />
        </div>
      </section>

      <section v-if="showJoin" class="band">
        <div class="wrap join">
          <div>
            <h2>Ready to find your people?</h2>
            <p class="section-lead">Sign up, upload a few songs, and start listening.</p>
          </div>
          <router-link to="/register" class="btn secondary">Sign up</router-link>
        </div>
      </section>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import BrandBlocks from '@/components/BrandBlocks.vue'

// Page frame shared by the homepage, Login and Register: the slot fills the left side, next to the brand blocks
defineProps({
  showJoin: { type: Boolean, default: true },
})
</script>

<style scoped>
.wrap { max-width: 1120px; margin: 0 auto; padding: 0 var(--space-6); }
h2 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-2); }
.section-lead { color: var(--ink-muted); max-width: 70ch; margin: 0; }
.hero { border-bottom: 1px solid var(--line); }
.hero-in { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: var(--space-8); align-items: start; padding-top: 56px; padding-bottom: var(--space-8); }
.band { padding: var(--space-8) 0; }
.join { display: flex; align-items: center; justify-content: space-between; gap: var(--space-6); flex-wrap: wrap; background: var(--purple-soft); border-radius: var(--radius-md); padding: var(--space-6); }
@media (max-width: 720px) {
  .wrap { padding: 0 var(--space-4); }
  .hero-in { grid-template-columns: 1fr; padding-top: var(--space-8); }
  .join { margin: 0 var(--space-4); }
}
</style>
