<template>
  <article class="profile-card" :class="{ flat }">
    <div class="banner">
      <img v-if="bannerUrl" :src="bannerUrl" :class="{ cover: !bannerStyle }" :style="bannerStyle" alt="" />
    </div>
    <div class="body">
      <img v-if="pictureUrl" class="av" :src="pictureUrl" alt="" />
      <div v-else class="av av-empty" aria-hidden="true">{{ initial }}</div>
      <p class="name">{{ displayName || username }}</p>
      <p class="meta">@{{ username }}<template v-if="country"> · {{ country }}</template></p>
      <p v-if="about" class="about">{{ about }}</p>
      <p v-if="website" class="site"><ExternalLink :href="website">{{ websiteLabel }}</ExternalLink></p>
      <slot />
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import ExternalLink from '@/components/ExternalLink.vue'

const props = defineProps({
  username: { type: String, required: true },
  displayName: { type: String, default: '' },
  pictureUrl: { type: String, default: '' },
  bannerUrl: { type: String, default: '' },
  bannerStyle: { type: Object, default: null },
  country: { type: String, default: '' },
  about: { type: String, default: '' },
  website: { type: String, default: '' },
  flat: { type: Boolean, default: false },
})

const initial = computed(() => props.username.charAt(0).toUpperCase())
const websiteLabel = computed(() => props.website.replace(/^https?:\/\//, '').replace(/\/$/, ''))
</script>

<style scoped>
.profile-card { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); overflow: hidden; }
.profile-card.flat { background: none; border: 0; border-radius: 0; overflow: visible; }
.flat .banner { border-radius: 0; }
.flat .body { padding: 0 var(--space-8) var(--space-6) 0; }
.flat .av { margin-left: var(--space-4); }
.banner { position: relative; overflow: hidden; aspect-ratio: 3 / 1; background: var(--purple-soft); }
.banner img { display: block; }
.banner img.cover { width: 100%; height: 100%; object-fit: cover; }
.body { display: flex; flex-direction: column; gap: 2px; padding: 0 var(--space-6) var(--space-6); }
.av { position: relative; width: 112px; height: 112px; margin-top: -56px; margin-bottom: var(--space-2); border-radius: var(--radius-pill); border: 4px solid var(--surface); object-fit: cover; background: var(--surface-alt); }
.av-empty { display: grid; place-items: center; background: var(--primary); color: var(--on-primary); font: 700 40px/1 var(--font-sans); }
.name { margin: 0; font: 700 24px/32px var(--font-sans); overflow-wrap: anywhere; }
.meta { margin: 0; font: 500 14px/20px var(--font-sans); color: var(--ink-muted); }
.about { margin: var(--space-3) 0 0; font: 400 15px/22px var(--font-sans); white-space: pre-line; overflow-wrap: anywhere; }
.site { margin: var(--space-1) 0 0; font: 500 14px/20px var(--font-sans); color: var(--link); overflow-wrap: anywhere; }
.site a { color: inherit; text-decoration: none; }
.site a:hover { text-decoration: underline; }
</style>
