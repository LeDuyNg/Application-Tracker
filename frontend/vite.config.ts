import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The dev server runs on :5173. The backend API runs on :8080. A browser treats those two
// ports as *different origins*, which would mean CORS pre-flights and — worse — the session
// cookie set by :8080 would not be considered "same-site" for requests the SPA makes.
//
// The proxy fixes both: the browser only ever talks to :5173, and Vite forwards anything
// matching these path prefixes to :8080 server-to-server. As far as the browser is
// concerned everything is one origin, so the cookie just works.
//
//   /api    — the REST API
//   /oauth2 — where the SPA sends the user to start Google login
//             (/oauth2/authorization/google)
//   /login  — where Google redirects back to (/login/oauth2/code/google); Spring Security
//             handles that callback, sets the session cookie, and 302s to "/"
//
// See PLAN.md Phase 3 for why proxying /login specifically matters.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/oauth2': 'http://localhost:8080',
      '/login': 'http://localhost:8080',
    },
  },
})
