<template>
  <HeroLayout>
    <div>
      <h1 class="wordmark">Aux</h1>
      <p class="tagline">Find people who hear it too.</p>
      <p class="lead">
        Upload the music you own, play it in your browser, and get matched with listeners who share
        your favorite artists and genres. No gatekeeping, no algorithm deciding for you.
      </p>
      <div class="cta">
        <router-link to="/register" class="btn primary">Create your account</router-link>
        <router-link to="/login" class="btn secondary">Log in</router-link>
      </div>
      <div class="faces">
        <img v-for="src in PFPS" :key="src" :src="src" alt="" />
        <span class="faces-note">Join listeners already on Aux.</span>
      </div>
    </div>
    <template #below>
      <section class="top">
        <div class="wrap top-in">
          <div>
            <h2>Top</h2>
            <p class="section-lead">The albums getting the most plays on Aux right now.</p>
          </div>
          <ul class="grid">
            <li v-for="album in topAlbums" :key="album.album" class="tile">
              <img class="cover" :src="album.cover" :alt="`${album.album} cover`" loading="lazy" />
              <span class="tile-name">{{ album.album }}</span>
              <span class="tile-meta">{{ album.artist }}</span>
            </li>
          </ul>
        </div>
      </section>
    </template>
  </HeroLayout>
</template>

<script setup>
import HeroLayout from '@/components/HeroLayout.vue'

// Hardcoded until GET /api/core/top is implemented (analytics)
const COVER_BASE = 'https://aux.justinabruinsma.com/music-cover'
const topAlbums = [
  { album: 'DAMN.', artist: 'Kendrick Lamar', cover: `${COVER_BASE}/top-damn.webp` },
  {
    album: 'My Beautiful Dark Twisted Fantasy',
    artist: 'Kanye West',
    cover: `${COVER_BASE}/top-mbdtf.webp`,
  },
  { album: 'UP 2 ME', artist: 'Yeat', cover: `${COVER_BASE}/top-up2me.jpg` },
]

// Hardcoded to save an API call; picked from real uploads in the R2 bucket
const PFPS = [
  '09f810b7-ed4a-4b44-b322-860b80909537',
  '12bbd883-fc2d-4abe-8f03-4292d60f8234',
  '1a6af45f-4aad-424b-82fc-9edde6460a0d',
  '39880875-01ba-43b8-aacd-69f86991bf62',
  '5b828901-e1a7-40e4-b7b7-530ae14b828a',
  '66acb350-287e-4bb3-8f68-7fd03ce636f2',
].map((id) => `https://aux.justinabruinsma.com/pfp/${id}.webp`)
</script>

<style scoped>
.wordmark {
  font: 800 clamp(64px, 11vw, 120px) / 0.9 var(--font-sans);
  letter-spacing: -0.03em;
  color: var(--primary);
  margin: 0;
}
[data-theme='dark'] .wordmark {
  color: var(--link);
}
.tagline {
  font: 700 20px/28px var(--font-sans);
  margin: var(--space-4) 0 var(--space-2);
}
.lead {
  color: var(--ink-muted);
  max-width: 52ch;
  margin: 0;
}
.cta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-6);
}
.faces {
  align-items: center;
  display: flex;
  margin-top: var(--space-8);
}
.faces img {
  border: 2px solid var(--surface);
  border-radius: var(--radius-pill);
  height: 56px;
  margin-left: -12px;
  object-fit: cover;
  width: 56px;
}
.faces img:first-child {
  margin-left: 0;
}
.faces-note {
  color: var(--ink-muted);
  font: 500 14px/20px var(--font-sans);
  margin-left: var(--space-3);
}
.top {
  border-bottom: 1px solid var(--line);
  padding: var(--space-8) 0;
}
.wrap {
  padding: 0 var(--page-gutter);
}
.top-in {
  align-items: start;
  display: grid;
  gap: var(--space-8);
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}
h2 {
  font: 700 28px/34px var(--font-sans);
  margin: 0 0 var(--space-2);
}
.section-lead {
  color: var(--ink-muted);
  margin: 0;
  max-width: 52ch;
}
.grid {
  display: grid;
  gap: var(--space-6);
  grid-template-columns: repeat(3, minmax(0, 1fr));
  list-style: none;
  margin: 0;
  padding: 0;
}
.tile {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.cover {
  aspect-ratio: 1;
  background: var(--surface-alt);
  border-radius: var(--radius-sm);
  margin-bottom: var(--space-2);
  object-fit: cover;
  width: 100%;
}
.tile-name {
  font: 600 16px/24px var(--font-sans);
}
.tile-meta {
  color: var(--ink-muted);
  font: 500 14px/20px var(--font-sans);
}
@media (max-width: 720px) {
  .top-in {
    gap: var(--space-6);
    grid-template-columns: 1fr;
  }
  .grid {
    gap: var(--space-4);
  }
}
</style>
