/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    // 개발 중에는 /api 요청을 로컬 백엔드로 넘긴다. 같은 출처가 되므로 백엔드에 CORS를 열 필요가 없다.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
  },
})
