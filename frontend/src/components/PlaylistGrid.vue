<template>
  <ul class="grid">
    <li v-for="playlist in playlists" :key="playlist.playlistId">
      <router-link
        class="tile"
        :to="{ name: 'Playlist', params: { username: playlist.ownerUsername ?? owner, id: playlist.playlistId } }"
      >
        <span class="cover-wrap">
          <img v-if="playlist.playlistCoverUrl" class="cover" :src="resolveCoverURL(playlist.playlistCoverUrl)" alt="" />
          <span v-else class="cover cover-empty" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" />
            </svg>
          </span>
          <span v-if="playlist.isSaved" class="saved-mark" role="img" aria-label="Saved" title="Saved">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z" />
            </svg>
          </span>
        </span>
        <span class="tile-name">{{ playlist.playlistName }}</span>
        <span class="tile-meta">{{ meta(playlist) }}</span>
      </router-link>
    </li>
  </ul>
</template>

<script setup>
import { resolveCoverURL } from '@/utils/display.js'

// Cover grid for playlist items (profile, library or search results). `owner` is the username to link with when an
// item has no `ownerUsername`; items owned by someone else get a "by <owner>" line, and `isSaved` items
// get a bookmark on the cover
const props = defineProps({
  playlists: { type: Array, required: true },
  owner: { type: String, required: true },
})

function meta(playlist) {
  const total = playlist.totalPieces ?? playlist.pieceCount ?? 0
  const songs = total === 1 ? '1 song' : `${total} songs`
  const by = playlist.ownerUsername
  return by && by.toLowerCase() !== props.owner.toLowerCase() ? `by ${by} · ${songs}` : songs
}
</script>

<style scoped>
.grid { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: var(--space-4); }
.tile { display: flex; flex-direction: column; gap: 2px; text-decoration: none; color: var(--ink); border-radius: var(--radius-sm); }
.tile:hover .tile-name { color: var(--link); text-decoration: underline; }
.cover-wrap { display: block; margin-bottom: var(--space-2); position: relative; }
.cover { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: var(--radius-sm); background: var(--surface-alt); display: block; }
.cover-empty { display: grid; place-items: center; color: var(--ink-muted); }
.saved-mark { background: var(--primary); border-radius: var(--radius-sm); color: var(--on-primary); display: grid; height: 24px; place-items: center; position: absolute; right: var(--space-2); top: var(--space-2); width: 24px; }
.tile-name { font: 600 15px/22px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tile-meta { font: 500 14px/20px var(--font-sans); color: var(--ink-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
