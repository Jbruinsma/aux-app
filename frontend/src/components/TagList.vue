<template>
  <ul v-if="tags.length" class="tag-list" :aria-label="label">
    <li v-for="tag in tags" :key="tag">
      <router-link class="tag" :to="{ name: 'ExploreTag', params: { tag } }">{{ tag }}</router-link>
    </li>
  </ul>
</template>

<script setup>
// A piece's or playlist's tags, shown as gray chips that open the tag's Explore page. Gray, not purple-soft: the style
// guide keeps purple-soft for selected chips (as in TagPicker)
defineProps({
  tags: { type: Array, default: () => [] },
  // What the tags belong to, for screen readers ("Playlist tags", "Song tags")
  label: { type: String, default: 'Tags' },
})
</script>

<style scoped>
.tag-list { display: flex; flex-wrap: wrap; gap: var(--space-2); list-style: none; margin: 0; padding: 0; }
.tag { background: var(--surface-alt); border-radius: var(--radius-pill); color: var(--ink-muted); display: inline-block; font: 600 12px/16px var(--font-sans); padding: var(--space-1) var(--space-3); text-decoration: none; transition: color var(--dur-fast) var(--ease); }
.tag:hover { color: var(--ink); text-decoration: underline; }
.tag:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }

@media (prefers-reduced-motion: reduce) {
  .tag { transition: none; }
}
</style>
