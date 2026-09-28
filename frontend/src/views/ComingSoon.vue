<template>
  <div class="aux-page with-player">
    <AppHeader />
    <main class="wrap">
      <h1>{{ title }}</h1>
      <EmptyState :title="`The ${title.toLowerCase()} page is coming soon`" text="Check back soon." />
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import EmptyState from '@/components/EmptyState.vue'

// Stand-in for top bar sections that don't have their own page yet (Music, Friends, Explore)
defineProps({
  title: { type: String, required: true },
})
</script>

<style scoped>
.wrap { width: 100%; padding: var(--space-8) var(--page-gutter); }
h1 { font: 700 28px/34px var(--font-sans); margin: 0 0 var(--space-4); }
</style>
