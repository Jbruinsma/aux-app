<template>
  <ol class="rows" :class="{ ranked, counted: hasCounts, 'with-action': Boolean($slots.action) }" :aria-label="label">
    <li v-for="(piece, index) in pieces" :key="piece.musicPieceId ?? `${index}-${piece.name}`" class="tr" :class="{ on: isCurrent(piece) }">
      <span v-if="ranked" class="rk">{{ numbers ? (numbers[index] ?? '') : start + index }}</span>
      <button
        v-if="canPlay(piece)"
        type="button"
        class="play"
        :aria-label="`${isPlaying(piece) ? 'Pause' : 'Play'} ${piece.name}${piece.artistSummary?.artistName ? ` by ${piece.artistSummary.artistName}` : ''}`"
        @click="onPlayClick(piece, index)"
      >
        <svg v-if="isPlaying(piece)" width="12" height="12" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5h3v11H3zM8 1.5h3v11H8z" fill="currentColor" /></svg>
        <svg v-else width="12" height="12" viewBox="0 0 14 14" aria-hidden="true"><path d="M3 1.5v11l9-5.5z" fill="currentColor" /></svg>
      </button>
      <span v-else class="play-spacer" aria-hidden="true"></span>
      <img v-if="piece.coverUrl" class="art" :src="resolveCoverURL(piece.coverUrl)" alt="" loading="lazy" />
      <span v-else class="art art-empty" aria-hidden="true">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>
      </span>
      <span class="text">
        <router-link
          v-if="linked && piece.musicPieceId"
          class="ti ti-link"
          :to="{ name: 'Piece', params: { musicPieceId: piece.musicPieceId } }"
        >{{ piece.name }}</router-link>
        <span v-else class="ti">{{ piece.name }}</span>
        <span class="sub">
          <router-link
            v-if="piece.artistSummary?.artistName && piece.artistSummary.artistId"
            class="ar artist-link"
            :to="{ name: 'Artist', params: { artistId: piece.artistSummary.artistId } }"
          >{{ piece.artistSummary.artistName }}</router-link>
          <span v-else-if="piece.artistSummary?.artistName" class="ar">{{ piece.artistSummary.artistName }}</span>
          <router-link
            v-if="showUploader && piece.uploader?.username"
            class="up"
            :to="{ name: 'Profile', params: { username: piece.uploader.username } }"
            :title="`Uploaded by ${piece.uploader.username}`"
          >
            <img v-if="piece.uploader.pfpUrl" class="up-pfp" :src="resolveCoverURL(piece.uploader.pfpUrl)" alt="" loading="lazy" />
            <span v-else class="up-pfp up-initial" aria-hidden="true">{{ piece.uploader.username.charAt(0).toUpperCase() }}</span>
            <span class="up-name"><span class="sr-only">Uploaded by </span>{{ piece.uploader.username }}</span>
          </router-link>
        </span>
      </span>
      <span v-if="hasCounts" class="cnt">
        <span class="ctrk"><span class="fill" :style="{ width: `${countShare(piece)}%` }"></span></span>
        <span class="ct">{{ piece.playCount === 1 ? '1 play' : `${piece.playCount} plays` }}</span>
      </span>
      <span v-if="$slots.action" class="act"><slot name="action" :piece="piece" :index="index" /></span>
    </li>
  </ol>
</template>

<script setup>
import { computed } from 'vue'
import { useMusicStore } from '@/stores/music.js'
import { resolveCoverURL } from '@/utils/display.js'

// The style guide's track row: optional rank, play button, 40px art, title and artist, and a play count bar when the
// pieces carry `playCount`. A piece without a `musicPieceId` gets no play button. The playing piece's button shows
// pause and pauses it; pressed again it resumes where it was instead of starting over, so `play` is only emitted for
// the other pieces. With `linked`, the title links to the piece's page and the whole row opens it. The `action` slot ({ piece, index }) adds a small button at the end of each row
const props = defineProps({
  pieces: { type: Array, required: true },
  label: { type: String, required: true },
  ranked: Boolean,
  // Rank of the first row, for a list split across columns
  start: { type: Number, default: 1 },
  // Shown in the rank column instead of 1, 2, 3 (an album's track numbers); a null leaves that row's blank
  numbers: { type: Array, default: null },
  // Adds who uploaded each piece after the artist
  showUploader: Boolean,
  // Clicking a row opens the piece's page
  linked: Boolean,
})
const emit = defineEmits(['play'])

const musicStore = useMusicStore()

const hasCounts = computed(() => props.pieces.some((piece) => typeof piece.playCount === 'number'))
const maxCount = computed(() => Math.max(1, ...props.pieces.map((piece) => piece.playCount ?? 0)))

function canPlay(piece) {
  return Boolean(piece.musicPieceId)
}

function isCurrent(piece) {
  return Boolean(piece.musicPieceId) && musicStore.currentMusicPiece?.uuid === piece.musicPieceId
}

function isPlaying(piece) {
  return isCurrent(piece) && musicStore.isPlaying
}

function onPlayClick(piece, index) {
  if (!isCurrent(piece)) return emit('play', index)
  if (musicStore.isPlaying) musicStore.isPlaying = false
  else if (musicStore.currentMusicPiece?.mp3File) musicStore.isPlaying = true
  // After a reload the song has no audio yet
  else musicStore.resume()
}

function countShare(piece) {
  return Math.round(((piece.playCount ?? 0) / maxCount.value) * 100)
}
</script>

<style scoped>
.rows { --cols: 32px 40px minmax(0, 1fr); list-style: none; margin: 0; padding: 0; }
.rows.ranked { --cols: 20px 32px 40px minmax(0, 1fr); }
.rows.counted { --cols: 32px 40px minmax(0, 1fr) minmax(90px, 200px); }
.rows.ranked.counted { --cols: 20px 32px 40px minmax(0, 1fr) minmax(90px, 200px); }
.tr { align-items: center; border-bottom: 1px solid var(--line); display: grid; gap: var(--space-3); grid-template-columns: var(--cols); padding: var(--space-2) var(--space-3); position: relative; }
.with-action .tr { grid-template-columns: var(--cols) 36px; }
.act { display: flex; justify-content: flex-end; }
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
.sub { align-items: center; display: flex; gap: var(--space-2); min-width: 0; }
.ar { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.up { align-items: center; color: var(--ink-muted); display: inline-flex; flex: none; font: 500 14px/20px var(--font-sans); gap: var(--space-1); max-width: 50%; text-decoration: none; }
.ar + .up::before { color: var(--ink-muted); content: '·'; margin-right: var(--space-1); }
.up:hover .up-name { color: var(--link); text-decoration: underline; }
.up:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.up-pfp { border-radius: var(--radius-pill); flex: none; height: 16px; object-fit: cover; width: 16px; }
.up-initial { background: var(--primary); color: var(--on-primary); display: grid; font: 700 10px/1 var(--font-sans); place-items: center; }
.up-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sr-only { clip-path: inset(50%); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }
.artist-link { text-decoration: none; }
.ti-link { color: inherit; text-decoration: none; }
.ti-link::after { content: ''; inset: 0; position: absolute; }
.ti-link:hover { color: var(--link); text-decoration: underline; }
.ti-link:focus-visible { outline: none; }
.ti-link:focus-visible::after { border-radius: var(--radius-sm); outline: 2px solid var(--focus-ring); outline-offset: -2px; }
.play, .artist-link, .up, .act { position: relative; z-index: 1; }
.artist-link:hover { color: var(--link); text-decoration: underline; }
.cnt { display: flex; flex-direction: column; gap: 2px; }
.ctrk { background: var(--surface-alt); border-radius: var(--radius-pill); height: 6px; overflow: hidden; }
.on .ctrk { background: var(--surface); }
.fill { background: var(--purple); border-radius: var(--radius-pill); display: block; height: 100%; }

@media (max-width: 720px) {
  .rows.counted { --cols: 32px 40px minmax(0, 1fr); }
  .rows.ranked.counted { --cols: 20px 32px 40px minmax(0, 1fr); }
  .cnt { display: none; }
}
</style>
