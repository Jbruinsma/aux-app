import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// AUX_BACKEND=local in .env.local (gitignored) proxies to your local backend; anything else uses the shared server
const backends = { local: 'http://localhost:5000', server: 'http://150.136.105.240:5000' }

export default defineConfig(({ mode }) => {
  const target = backends[loadEnv(mode, fileURLToPath(new URL('.', import.meta.url)), '').AUX_BACKEND] ?? backends.server
  return {
    plugins: [
      vue(),
      vueDevTools(),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      },
    },
    server: {
      host: true,
      port: 5173,
      proxy: {
        '/uploads': {
          target,
          changeOrigin: true,
        },
        '/api': {
          target,
          changeOrigin: true,
        },
      },
      hmr: {
        overlay: false
      }
    }
  }
})
