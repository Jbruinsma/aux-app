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

      <slot name="below" />
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import BrandBlocks from '@/components/BrandBlocks.vue'

</script>

<style scoped>
.wrap { padding: 0 var(--page-gutter); }
.hero { border-bottom: 1px solid var(--line); }
.hero-in { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: var(--space-8); align-items: start; padding-top: 56px; padding-bottom: var(--space-8); }
@media (max-width: 720px) {
  .hero-in { grid-template-columns: 1fr; padding-top: var(--space-8); }
}
</style>
