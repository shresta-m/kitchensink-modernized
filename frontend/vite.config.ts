import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// In dev, /api is proxied to the Spring Boot backend, so the browser sees one origin (no CORS setup needed).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': { target: process.env.BACKEND_URL ?? 'http://localhost:8080', changeOrigin: true } },
  },
  test: { environment: 'node' },
})
