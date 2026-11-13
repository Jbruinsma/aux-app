<template>
  <ul class="artists">
    <li v-for="artist in artists" :key="artist.artistId" class="artist">
      <router-link class="who" :to="{ name: 'Artist', params: { artistId: artist.artistId } }">
        <img v-if="artist.artistPfpUrl" class="av" :src="resolveCoverURL(artist.artistPfpUrl)" alt="" loading="lazy" />
        <span v-else class="av av-empty" aria-hidden="true">{{ artist.artistName.charAt(0).toUpperCase() }}</span>
        <span class="name">{{ artist.artistName }}</span>
      </router-link>
      <router-link
        v-if="addMusic"
        class="add"
        :to="{ name: 'Upload', query: { artist: artist.artistId } }"
        :aria-label="`Add music to ${artist.artistName}`"
      >Add music</router-link>
    </li>
  </ul>
</template>

<script setup>
import { resolveCoverURL } from '@/utils/display.js'

// Round artist pictures with names, each linking to the artist page. `addMusic` adds a link under each that opens Upload
// with the artist already picked
defineProps({
  // [ArtistSummary]
  artists: { type: Array, required: true },
  addMusic: { type: Boolean, default: true },
})
</script>

<style scoped>
.artists { display: grid; gap: var(--space-6); grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); list-style: none; margin: 0; padding: 0; }
.artist { align-items: center; display: flex; flex-direction: column; gap: var(--space-1); min-width: 0; }
.who { align-items: center; color: var(--ink); display: flex; flex-direction: column; gap: var(--space-2); max-width: 100%; text-decoration: none; width: 100%; }
.who:hover .name { color: var(--link); text-decoration: underline; }
.who:focus-visible, .add:focus-visible { border-radius: var(--radius-sm); outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.av { aspect-ratio: 1; background: var(--surface-alt); border-radius: var(--radius-pill); object-fit: cover; width: 100%; }
.av-empty { background: var(--purple-soft); color: var(--link); display: grid; font: 800 40px/1 var(--font-sans); place-items: center; }
.name { font: 600 16px/24px var(--font-sans); max-width: 100%; overflow: hidden; text-align: center; text-overflow: ellipsis; white-space: nowrap; }
.add { color: var(--link); font: 600 14px/20px var(--font-sans); text-decoration: none; }
.add:hover { text-decoration: underline; }
@media (max-width: 720px) {
  .artists { gap: var(--space-4); grid-template-columns: repeat(auto-fill, minmax(112px, 1fr)); }
}
</style>
