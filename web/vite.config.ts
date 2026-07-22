import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// The dev server runs on 5173 — the origin the backend's CORS policy permits
// (API_SPECIFICATION §5b). Changing it here means changing it there too.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // Fail rather than fall back to 5174. The backend's CORS policy allows the
    // 5173 origin specifically (API_SPECIFICATION §5b), so a silent port bump
    // would produce a dev server whose every API call is rejected by preflight —
    // a confusing failure that looks like a backend bug. Better to stop and say
    // the port is taken.
    strictPort: true,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    environmentOptions: {
      // jsdom enforces CORS on XHR using the document origin, which defaults to
      // localhost:3000. Setting it to the real dev origin means the live-backend
      // test actually exercises the backend's CORS policy (which allows :5173)
      // instead of being blocked by an origin the app never runs on.
      jsdom: { url: 'http://localhost:5173/' },
    },
    // The API client validates this at import time and throws without it.
    // Tests exercise the client's wiring, never a real backend.
    env: {
      VITE_API_BASE_URL: 'http://test.local/api/v1',
    },
  },
});
