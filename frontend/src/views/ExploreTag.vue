<template>
  <div class="aux-page with-player">
    <AppHeader />
    <main class="wrap">
      <router-link class="back" :to="{ name: 'Explore' }">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>
        Explore
      </router-link>
      <h1>{{ tagLabel(tag) }}</h1>
      <p class="meta">Public songs tagged {{ tag }}, most played this month first.</p>

      <section class="block" aria-label="Songs">
        <EmptyState v-if="state.error" tone="error" :title="state.error" />
        <EmptyState v-else-if="state.loading" tone="loading" :title="`Loading ${tag} songs…`" />
        <EmptyState
          v-else-if="!state.items.length"
          :title="`No songs tagged ${tag} yet`"
          text="Tag your uploads so people can find them here."
        />
        <template v-else>
          <TrackRows
            :pieces="state.items"
            :label="`Songs tagged ${tag}`"
            ranked
            @play="(index) => musicStore.playPieces(state.items, index)"
          />
          <button v-if="state.cursor" type="button" class="btn secondary more" :disabled="state.loadingMore" @click="loadMore">
            {{ state.loadingMore ? 'Loading…' : 'Show more' }}
          </button>
        </template>
      </section>
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import EmptyState from '@/components/EmptyState.vue'
import TrackRows from '@/components/TrackRows.vue'
import { useMusicStore } from '@/stores/music.js'
import { fetchTagPieces, tagLabel } from '@/utils/explore.js'

const route = useRoute()
const musicStore = useMusicStore()
const tag = computed(() => String(route.params.tag ?? '').toLowerCase())
const state = reactive({ items: [], cursor: null, loading: true, loadingMore: false, error: '' })
let request = 0

watch(tag, async (value) => {
  const id = ++request
  Object.assign(state, { items: [], cursor: null, loading: true, loadingMore: false, error: '' })
  try {
    const page = await fetchTagPieces(value)
    if (id !== request) return
    state.items = page.musicPieces ?? []
    state.cursor = page.nextCursor ?? null
  } catch (err) {
    if (id !== request || err.sessionEnded) return
    state.error = `We couldn't load ${value} songs.`
  } finally {
    if (id === request) state.loading = false
  }
}, { immediate: true })

async function loadMore() {
  const id = request
  state.loadingMore = true
  try {
    const page = await fetchTagPieces(tag.value, state.cursor)
    if (id !== request) return
    state.items = [...state.items, ...(page.musicPieces ?? [])]
    state.cursor = page.nextCursor ?? null
  } catch (err) {
    if (id !== request || err.sessionEnded) return
    state.error = `We couldn't load more ${tag.value} songs.`
  } finally {
    if (id === request) state.loadingMore = false
  }
}
</script>

<style scoped>
.wrap { padding: var(--space-6) var(--page-gutter) var(--space-8); }
.back { align-items: center; color: var(--link); display: inline-flex; font: 600 14px/20px var(--font-sans); gap: var(--space-1); text-decoration: none; }
.back:hover { text-decoration: underline; }
.back:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
h1 { font: 700 28px/34px var(--font-sans); margin: var(--space-4) 0 var(--space-1); }
.meta { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; }
.block { padding-top: var(--space-6); }
.more { margin-top: var(--space-6); }
</style>
