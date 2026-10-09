import { defineStore } from 'pinia'

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    message: '',
    type: 'neutral',
    visible: false,
    timeout: null,
  }),

  actions: {
    show(message, type = 'neutral', duration = 3000) {
      if (this.timeout) {
        clearTimeout(this.timeout)
      }

      this.message = message
      this.type = type
      this.visible = true

      this.timeout = setTimeout(() => {
        this.hide()
      }, duration)
    },

    hide() {
      this.visible = false
      this.timeout = null
    },

    success(message) {
      this.show(message, 'success')
    },

    error(message) {
      this.show(message, 'error')
    },

    neutral(message) {
      this.show(message, 'neutral')
    },
  },
})
