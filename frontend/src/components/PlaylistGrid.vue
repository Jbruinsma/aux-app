<template>
  <ul class="grid">
    <li v-for="playlist in playlists" :key="playlist.playlistId">
      <router-link class="tile" :to="{ name: 'Playlist', params: { username: owner, id: playlist.playlistId } }">
        <img v-if="playlist.playlistCoverUrl" class="cover" :src="resolveCoverURL(playlist.playlistCoverUrl)" alt="" />
        <span v-else class="cover cover-empty" aria-hidden="true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" />
          </svg>
        </span>
        <span class="tile-name">{{ playlist.playlistName }}</span>
        <span class="tile-meta">{{ songCount(playlist.totalPieces) }}</span>
      </router-link>
    </li>
  </ul>
</template>

<script setup>
import { resolveCoverURL } from '@/utils/display.js'

// Cover grid for ProfilePlaylist items from GET /api/users/profile/{username}; `owner` is that profile's username
defineProps({
  playlists: { type: Array, required: true },
  owner: { type: String, required: true },
})

function songCount(total) {
  return total === 1 ? '1 song' : `${total} songs`
}
</script>

<style scoped>
.grid { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: var(--space-4); }
.tile { display: flex; flex-direction: column; gap: 2px; text-decoration: none; color: var(--ink); border-radius: var(--radius-sm); }
.tile:hover .tile-name { color: var(--link); text-decoration: underline; }
.cover { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: var(--radius-sm); background: var(--surface-alt); margin-bottom: var(--space-2); }
.cover-empty { display: grid; place-items: center; color: var(--ink-muted); }
.tile-name { font: 600 15px/22px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tile-meta { font: 500 14px/20px var(--font-sans); color: var(--ink-muted); }
</style>
