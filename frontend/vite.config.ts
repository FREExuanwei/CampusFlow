import { defineConfig } from 'vite'

const proxy = { '/api/v1': { target: 'http://127.0.0.1:18087', changeOrigin: false } }
const headers = {
  'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' ws://127.0.0.1:5173; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'",
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
}

export default defineConfig({
  server: { host: '127.0.0.1', port: 5173, strictPort: true, cors: false, proxy, headers },
  preview: { host: '127.0.0.1', port: 5173, strictPort: true, cors: false, proxy, headers },
})
