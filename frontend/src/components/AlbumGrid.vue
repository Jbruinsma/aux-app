<template>
  <ul class="grid">
    <li v-for="album in albums" :key="album.albumId">
      <router-link
        class="tile"
        :to="{ name: 'Artist', params: { artistId: album.artistSummary.artistId }, hash: `#album-${album.albumId}` }"
      >
        <img v-if="album.coverUrl" class="cover" :src="resolveCoverURL(album.coverUrl)" alt="" loading="lazy" />
        <span v-else class="cover cover-empty" aria-hidden="true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10" /><circle cx="12" cy="12" r="3" />
          </svg>
        </span>
        <span class="tile-name">{{ album.albumName }}</span>
        <span class="tile-meta">{{ album.artistSummary.artistName }}</span>
      </router-link>
    </li>
  </ul>
</template>

<script setup>
import { resolveCoverURL } from '@/utils/display.js'

// Cover grid for AlbumOverview items, like PlaylistGrid. Albums have no page of their own, so each opens its section
// on the artist's page
defineProps({
  albums: { type: Array, required: true },
})
</script>

<style scoped>
.grid { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: var(--space-4); }
.tile { display: flex; flex-direction: column; gap: 2px; text-decoration: none; color: var(--ink); border-radius: var(--radius-sm); }
.tile:hover .tile-name { color: var(--link); text-decoration: underline; }
.tile:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.cover { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: var(--radius-sm); background: var(--surface-alt); display: block; margin-bottom: var(--space-2); }
.cover-empty { display: grid; place-items: center; color: var(--ink-muted); }
.tile-name { font: 600 15px/22px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tile-meta { font: 500 14px/20px var(--font-sans); color: var(--ink-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
