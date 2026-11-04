/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    host: true,
    // Dev calls go to /api and are proxied, so the browser never deals with CORS or absolute URLs.
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
        // Browsers attach an Origin header to every non-GET request, even same-origin, and
        // the proxy forwarded it — so the API saw a cross-origin PATCH from whatever port
        // Vite happened to be on and rejected it with "Invalid CORS request". GETs passed
        // (no Origin), mutations did not, which looks exactly like "the buttons don't work".
        // Stripping it here makes the request genuinely same-origin, so the dev port no
        // longer has to be in the CORS allow-list at all.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
        },
      },
    },
    watch: { usePolling: true }, // required for file watching through a Docker bind mount
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
});
