import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// host + allowedHosts: true lets the preview be opened through any hostname (Kubernetes ingress, localhost...)
export default defineConfig({
  plugins: [react()],
  server: { host: '0.0.0.0', allowedHosts: true },
})
