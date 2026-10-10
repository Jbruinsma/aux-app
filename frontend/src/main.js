import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import './assets/aux_styles.css'

import { initializeTheme } from '@/utils/theme.js'

const stopTheme = initializeTheme()
if (import.meta.hot) import.meta.hot.dispose(stopTheme)

const app = createApp(App)

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

app.use(pinia)
app.use(router)

app.mount('#app')
