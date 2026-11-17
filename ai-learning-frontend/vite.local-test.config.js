import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { host: '127.0.0.1', port: 24679, strictPort: true, proxy: {
    '/backend/': { target: 'http://127.0.0.1:8082', changeOrigin: true, rewrite: path => path.replace(/^\/backend/, '') }
  } }
})
