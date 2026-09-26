<template>
  <div class="empty">
    <svg class="icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
         stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <template v-if="icon === 'users'">
        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
        <path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
      </template>
      <template v-else-if="icon === 'mic'">
        <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z" />
        <path d="M19 10v2a7 7 0 0 1-14 0v-2" /><line x1="12" x2="12" y1="19" y2="22" />
      </template>
      <template v-else>
        <path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" />
      </template>
    </svg>
    <p class="title">{{ title }}</p>
    <p class="text">{{ text }}</p>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  // Lucide outline icon name: 'music' (default), 'users' or 'mic'
  icon: { type: String, default: 'music' },
  title: { type: String, required: true },
  text: { type: String, required: true },
})
</script>

<style scoped>
.empty { display: flex; flex-direction: column; align-items: flex-start; gap: var(--space-1); padding: var(--space-4); border: 1px solid var(--line); border-radius: var(--radius-md); }
.icon { color: var(--ink-muted); margin-bottom: var(--space-1); }
.title { margin: 0; font: 600 16px/24px var(--font-sans); color: var(--ink); }
.text { margin: 0; font: 500 14px/20px var(--font-sans); color: var(--ink-muted); max-width: 60ch; }
</style>
