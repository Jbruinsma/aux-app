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
      <div v-if="pfps.length" class="faces">
        <img v-for="src in pfps" :key="src" :src="src" alt="" />
        <span class="faces-note">Join listeners already on Aux.</span>
      </div>
    </div>
    <template #below>
      <section v-if="topSongs.length" class="top">
        <div class="wrap top-in">
          <div>
            <h2>Top</h2>
            <p class="section-lead">The songs getting the most plays on Aux this week.</p>
          </div>
          <ul class="grid">
            <li v-for="song in topSongs" :key="song.musicPieceId" class="tile">
              <img v-if="song.coverUrl" class="cover" :src="resolveCoverURL(song.coverUrl)" :alt="`${song.name} cover`" loading="lazy" />
              <span v-else class="cover" aria-hidden="true"></span>
              <span class="tile-name">{{ song.name }}</span>
              <span v-if="song.artistSummary?.artistName" class="tile-meta">{{ song.artistSummary.artistName }}</span>
            </li>
          </ul>
        </div>
      </section>
    </template>
  </HeroLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import HeroLayout from '@/components/HeroLayout.vue'
import { fetchAPI } from '@/utils/api.js'
import { resolveCoverURL } from '@/utils/display.js'
import { API_BASE_URL } from '@/utils/variables.js'

// Random users with a custom pfp; the row stays hidden if the call fails
const pfps = ref([])
onMounted(async () => {
  try {
    const urls = await fetchAPI(`${API_BASE_URL}/api/core/users`)
    pfps.value = urls.map(resolveCoverURL)
  } catch {
    // fetchAPI already logs the error
  }
})

// GET /api/core/top: this week's most played songs. The section hides when nothing was played or the call fails
const topSongs = ref([])
onMounted(async () => {
  try {
    const songs = await fetchAPI(`${API_BASE_URL}/api/core/top`, { notifyOnError: false })
    topSongs.value = (songs ?? []).slice(0, 3)
  } catch {
    // No Top section
  }
})
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tile-meta {
  color: var(--ink-muted);
  font: 500 14px/20px var(--font-sans);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
