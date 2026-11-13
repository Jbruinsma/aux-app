<template>
  <ul ref="scroller" class="cards" :class="{ dragging }" tabindex="0" :aria-label="label">
    <li v-for="(piece, index) in pieces" :key="piece.musicPieceId" class="card" :class="{ playing: isCurrent(piece) }">
      <button type="button" class="card-main" :aria-label="playLabel(piece)" @click="emit('play', index)">
        <span class="card-art-wrap">
          <img v-if="piece.coverUrl" class="card-art" :src="resolveCoverURL(piece.coverUrl)" alt="" loading="lazy" />
          <span v-else class="card-art card-art-empty" aria-hidden="true">
            <svg class="card-icon" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
          </span>
          <span class="card-play" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M7 4.5v15l12.5-7.5z" /></svg></span>
        </span>
        <span class="card-body">
          <span class="card-title">{{ piece.name }}</span>
          <span v-if="piece.artistSummary?.artistName" class="card-meta">{{ piece.artistSummary.artistName }}</span>
        </span>
      </button>
    </li>
  </ul>
</template>

<script setup>
import { ref } from 'vue'
import { useMusicStore } from '@/stores/music.js'
import { resolveCoverURL } from '@/utils/display.js'
import { useDragScroll } from '@/utils/dragScroll.js'

// A sideways row of song cards (cover, title, artist) that play on click. Scrolls by mouse drag as well
defineProps({
  pieces: { type: Array, required: true },
  label: { type: String, required: true },
})
const emit = defineEmits(['play'])

const musicStore = useMusicStore()
const scroller = ref(null)
const { dragging } = useDragScroll(scroller)

function isCurrent(piece) {
  return musicStore.currentMusicPiece?.uuid === piece.musicPieceId
}

function playLabel(piece) {
  const artist = piece.artistSummary?.artistName
  return `Play ${piece.name}${artist ? ` by ${artist}` : ''}`
}
</script>

<style scoped>
.cards { cursor: grab; display: grid; gap: var(--space-4); grid-auto-columns: 160px; grid-auto-flow: column; list-style: none; margin: 0; overflow-x: auto; overscroll-behavior-x: contain; padding: 0 0 var(--space-3); scrollbar-color: var(--line-strong) transparent; scrollbar-width: thin; }
.cards:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.cards.dragging { cursor: grabbing; user-select: none; }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); display: flex; flex-direction: column; min-width: 0; overflow: hidden; }
.card-main { background: none; border: 0; color: inherit; cursor: pointer; display: flex; flex: 1; flex-direction: column; font: inherit; min-width: 0; padding: 0; text-align: left; }
.card-main:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: -2px; }
.dragging .card-main { cursor: grabbing; }
.card-art-wrap { display: block; position: relative; }
.card-art { aspect-ratio: 1; background: var(--surface-alt); display: block; object-fit: cover; width: 100%; }
.card-art-empty { color: var(--ink-muted); display: grid; place-items: center; }
.card-icon { fill: none; height: 48px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.5; width: 48px; }
.card-play { align-items: center; background: var(--primary); border-radius: var(--radius-pill); bottom: var(--space-3); display: flex; height: 40px; justify-content: center; opacity: 0; position: absolute; right: var(--space-3); transition: opacity var(--dur-fast) var(--ease); width: 40px; }
.card-play svg { fill: var(--on-primary); height: 18px; width: 18px; }
.card-main:hover .card-play, .card-main:focus-visible .card-play, .playing .card-play { opacity: 1; }
.card-main:hover .card-play { background: var(--purple-hover); }
.card-body { background: var(--surface-alt); border-top: 1px solid var(--line); display: flex; flex: 1; flex-direction: column; gap: 2px; padding: var(--space-3) var(--space-4); }
.card-title { font: 600 16px/24px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-main:hover .card-title, .playing .card-title { color: var(--link); }
.playing .card-body { background: var(--purple-soft); }
.card-meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

@media (hover: none) {
  .card-play { opacity: 1; }
}
@media (max-width: 720px) {
  .cards { grid-auto-columns: 136px; }
}
</style>
