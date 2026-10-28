<template>
  <div class="empty" :class="tone" :role="tone === 'error' ? 'alert' : tone === 'loading' ? 'status' : null">
    <svg class="icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
         stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <template v-if="tone === 'error'">
        <circle cx="12" cy="12" r="10" /><line x1="12" x2="12" y1="8" y2="12" /><line x1="12" x2="12.01" y1="16" y2="16" />
      </template>
      <template v-else-if="icon === 'users'">
        <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
        <path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
      </template>
      <template v-else-if="icon === 'disc'">
        <circle cx="12" cy="12" r="10" /><circle cx="12" cy="12" r="2" />
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
    <p v-if="text" class="text">{{ text }}</p>
    <div v-if="$slots.default" class="actions"><slot /></div>
  </div>
</template>

<script setup>
// `tone`: 'empty' (nothing to show yet), 'loading', or 'error' (announced, with a warning icon in place of `icon`).
// The default slot holds the next step, such as a button
defineProps({
  icon: { type: String, default: 'music' },
  title: { type: String, required: true },
  text: { type: String, default: '' },
  tone: { type: String, default: 'empty' },
})
</script>

<style scoped>
.empty { align-items: flex-start; border: 1px solid var(--line); border-radius: var(--radius-md); display: flex; flex-direction: column; gap: var(--space-1); padding: var(--space-4); }
.icon { color: var(--ink-muted); margin-bottom: var(--space-1); }
.title { color: var(--ink); font: 600 16px/24px var(--font-sans); margin: 0; }
.text { color: var(--ink-muted); font: 500 14px/20px var(--font-sans); margin: 0; max-width: 60ch; }
.actions { display: flex; flex-wrap: wrap; gap: var(--space-3); margin-top: var(--space-3); }
.loading .title { color: var(--ink-muted); }
.error .icon, .error .title { color: var(--danger); }
</style>
