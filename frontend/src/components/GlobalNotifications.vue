<template>
  <Transition name="notification">
    <div v-if="notification.visible" class="global-notification" :class="notification.type">
      <span class="notification-icon">
        {{ icon }}
      </span>

      <span class="notification-message">
        {{ notification.message }}
      </span>

      <button class="notification-close" @click="notification.hide">×</button>
    </div>
  </Transition>
</template>

<script setup>
import { computed } from 'vue'
import { useNotificationStore } from '@/stores/notification'

const notification = useNotificationStore()

const icon = computed(() => {
  if (notification.type === 'success') {
    return '✓'
  }

  if (notification.type === 'error') {
    return '!'
  }

  return '●'
})
</script>

//Styling:

<style scoped>
.global-notification {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;

  display: flex;
  align-items: center;
  gap: 12px;

  width: calc(100% - 40px);
  max-width: 450px;
  padding: 14px 18px;

  background: #242424;
  color: #f0f0f0;

  border-radius: 10px;
  border: 1px solid #3a3a3a;

  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
}

/* SUCCESS */
.global-notification.success {
  border-left: 5px solid #22c55e;
}

.global-notification.success .notification-icon {
  color: #22c55e;
}

/* ERROR */
.global-notification.error {
  border-left: 5px solid #ef4444;
}

.global-notification.error .notification-icon {
  color: #ef4444;
}

/* NEUTRAL */
.global-notification.neutral {
  border-left: 5px solid #9ca3af;
}

.global-notification.neutral .notification-icon {
  color: #9ca3af;
}

.notification-icon {
  font-size: 18px;
  font-weight: bold;
}

.notification-message {
  flex: 1;
  font-size: 15px;
}

.notification-close {
  border: none;
  background: transparent;
  color: #aaa;
  font-size: 22px;
  cursor: pointer;
}

.notification-close:hover {
  color: white;
}

/* Animation */

.notification-enter-active,
.notification-leave-active {
  transition: all 0.25s ease;
}

.notification-enter-from,
.notification-leave-to {
  opacity: 0;
  transform: translate(-50%, -15px);
}
</style>
