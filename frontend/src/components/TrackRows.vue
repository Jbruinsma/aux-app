<template>
  <ol class="rows" :class="{ ranked, counted: hasCounts }" :aria-label="label">
    <li v-for="(piece, index) in pieces" :key="piece.musicPieceId ?? `${index}-${piece.name}`" class="tr" :class="{ on: isCurrent(piece) }">
      <span v-if="ranked" class="rk">{{ start + index }}</span>
      <button
        v-if="canPlay(piece)"
        type="button"
        class="play"
        :aria-label="`Play ${piece.name}${piece.artistSummary?.artistName ? ` by ${piece.artistSummary.artistName}` : ''}`"
        @click="emit('play', index)"
      >
        <svg width="12" height="12" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5v11l9-5.5z" fill="currentColor" /></svg>
      </button>
      <span v-else class="play-spacer" aria-hidden="true"></span>
      <img v-if="piece.coverUrl" class="art" :src="resolveCoverURL(piece.coverUrl)" alt="" loading="lazy" />
      <span v-else class="art art-empty" aria-hidden="true">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
      </span>
      <span class="text">
        <span class="ti">{{ piece.name }}</span>
        <router-link
          v-if="linkArtists && piece.artistSummary?.artistName && piece.artistSummary.artistId"
          class="ar artist-link"
          :to="{ name: 'Artist', params: { artistId: piece.artistSummary.artistId } }"
        >{{ piece.artistSummary.artistName }}</router-link>
        <span v-else-if="piece.artistSummary?.artistName" class="ar">{{ piece.artistSummary.artistName }}</span>
      </span>
      <span v-if="hasCounts" class="cnt">
        <span class="ctrk"><span class="fill" :style="{ width: `${countShare(piece)}%` }"></span></span>
        <span class="ct">{{ piece.playCount === 1 ? '1 play' : `${piece.playCount} plays` }}</span>
      </span>
    </li>
  </ol>
</template>

<script setup>
import { computed } from 'vue'
import { useMusicStore } from '@/stores/music.js'
import { resolveCoverURL } from '@/utils/display.js'

// The style guide's track row: optional rank, play button, 40px art, title and artist, and a play count bar when the
// pieces carry `playCount`. A piece without a `musicPieceId` (or with `playable` off) gets no play button
const props = defineProps({
  pieces: { type: Array, required: true },
  label: { type: String, required: true },
  ranked: Boolean,
  // Rank of the first row, for a list split across columns
  start: { type: Number, default: 1 },
  playable: { type: Boolean, default: true },
  linkArtists: { type: Boolean, default: true },
})
const emit = defineEmits(['play'])

const musicStore = useMusicStore()

const hasCounts = computed(() => props.pieces.some((piece) => typeof piece.playCount === 'number'))
const maxCount = computed(() => Math.max(1, ...props.pieces.map((piece) => piece.playCount ?? 0)))

function canPlay(piece) {
  return props.playable && Boolean(piece.musicPieceId)
}

function isCurrent(piece) {
  return Boolean(piece.musicPieceId) && musicStore.currentMusicPiece?.uuid === piece.musicPieceId
}

function countShare(piece) {
  return Math.round(((piece.playCount ?? 0) / maxCount.value) * 100)
}
</script>

<style scoped>
.rows { list-style: none; margin: 0; padding: 0; }
.tr { align-items: center; border-bottom: 1px solid var(--line); display: grid; gap: var(--space-3); grid-template-columns: 32px 40px minmax(0, 1fr); padding: var(--space-2) var(--space-3); }
.ranked .tr { grid-template-columns: 20px 32px 40px minmax(0, 1fr); }
.counted .tr { grid-template-columns: 32px 40px minmax(0, 1fr) minmax(90px, 200px); }
.ranked.counted .tr { grid-template-columns: 20px 32px 40px minmax(0, 1fr) minmax(90px, 200px); }
.tr:first-child { border-top: 1px solid var(--line); }
.tr:hover { background: var(--surface-alt); }
.tr.on { background: var(--purple-soft); }
.rk, .ar, .ct { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.rk { text-align: right; }
.play { align-items: center; background: none; border: 1px solid var(--line-strong); border-radius: var(--radius-pill); color: var(--ink); cursor: pointer; display: flex; height: 32px; justify-content: center; padding: 0; width: 32px; }
.play:hover { border-color: var(--primary); color: var(--link); }
.on .play { background: var(--primary); border-color: var(--primary); color: var(--on-primary); }
.play:focus-visible, .artist-link:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.play-spacer { height: 32px; width: 32px; }
.art { border-radius: var(--radius-sm); display: block; height: 40px; object-fit: cover; width: 40px; }
.art-empty { align-items: center; background: var(--surface-alt); color: var(--ink-muted); display: flex; justify-content: center; }
.text { display: flex; flex-direction: column; min-width: 0; }
.ti { font: 600 15px/22px var(--font-sans); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.on .ti { color: var(--link); }
.ar { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.artist-link { text-decoration: none; }
.artist-link:hover { color: var(--link); text-decoration: underline; }
.cnt { display: flex; flex-direction: column; gap: 2px; }
.ctrk { background: var(--surface-alt); border-radius: var(--radius-pill); height: 6px; overflow: hidden; }
.on .ctrk { background: var(--surface); }
.fill { background: var(--purple); border-radius: var(--radius-pill); display: block; height: 100%; }

@media (max-width: 720px) {
  .counted .tr { grid-template-columns: 32px 40px minmax(0, 1fr); }
  .ranked.counted .tr { grid-template-columns: 20px 32px 40px minmax(0, 1fr); }
  .cnt { display: none; }
}
</style>
