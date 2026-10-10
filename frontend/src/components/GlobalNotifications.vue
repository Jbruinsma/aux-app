<template>
  <div class="notification-region" :role="notification.type === 'error' ? 'alert' : 'status'" aria-atomic="true">
    <Transition name="notification">
      <div v-if="notification.visible" class="global-notification" :class="notification.type">
        <svg class="notification-icon" viewBox="0 0 24 24" aria-hidden="true">
          <path v-if="notification.type === 'success'" d="M20 6 9 17l-5-5" />
          <template v-else>
            <circle cx="12" cy="12" r="9" />
            <path v-if="notification.type === 'error'" d="M12 8v5m0 3h.01" />
            <path v-else d="M12 11v5m0-8h.01" />
          </template>
        </svg>
        <span class="notification-message">{{ notification.message }}</span>
        <button type="button" class="notification-close" aria-label="Dismiss notification" @click="notification.hide">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m18 6-12 12M6 6l12 12" /></svg>
        </button>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { useNotificationStore } from '@/stores/notification.js'

const notification = useNotificationStore()
</script>

<style scoped>
.notification-region { left: max(var(--space-4), env(safe-area-inset-left, 0px)); pointer-events: none; position: fixed; right: max(var(--space-4), env(safe-area-inset-right, 0px)); top: calc(env(safe-area-inset-top, 0px) + var(--space-6)); z-index: 9999; }
.global-notification { --notification-accent: var(--ink-muted); align-items: center; background: var(--surface); border: 1px solid var(--line-strong); border-inline-start: var(--space-1) solid var(--notification-accent); border-radius: var(--radius-md); box-sizing: border-box; color: var(--ink); display: flex; font: 500 14px/20px var(--font-sans); gap: var(--space-3); margin: 0 auto; max-width: 450px; padding: var(--space-2) var(--space-3); pointer-events: auto; }
.global-notification.success { --notification-accent: var(--success); }
.global-notification.error { --notification-accent: var(--danger); }
.notification-icon { color: var(--notification-accent); fill: none; flex: none; height: 20px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; width: 20px; }
.notification-message { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.notification-close { align-items: center; background: transparent; border: 0; border-radius: var(--radius-sm); color: var(--ink-muted); cursor: pointer; display: flex; flex: none; height: 44px; justify-content: center; padding: var(--space-3); width: 44px; }
.notification-close:hover { background: var(--surface-alt); color: var(--ink); }
.notification-close:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.notification-close svg { fill: none; height: 20px; stroke: currentColor; stroke-linecap: round; stroke-width: 2; width: 20px; }
.notification-enter-active, .notification-leave-active { transition: opacity var(--dur-fast) var(--ease), transform var(--dur-med) var(--ease); }
.notification-enter-from, .notification-leave-to { opacity: 0; transform: translateY(calc(-1 * var(--space-3))); }
@media (prefers-reduced-motion: reduce) {
  .notification-enter-active, .notification-leave-active { transition: none; }
}
</style>
