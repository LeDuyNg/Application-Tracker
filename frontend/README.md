# frontend — Job Application Tracker SPA

Vite + React + TypeScript. Talks to the Spring Boot API in `../backend`.

## Run

```bash
npm install
npm run dev        # http://localhost:5173  (proxies /api, /oauth2, /login → :8080)
npm run build      # type-check + production build into dist/
npm run preview    # serve the built dist/ locally
npm run lint       # oxlint
```

The backend must be running on `:8080` (`../backend`, `local` profile) and a local MongoDB
must be up (`docker start jt-mongo`). Sign in with Google at the top-right of the app — the
Vite proxy is what makes the OAuth redirect land back on `:5173`.

## Layout

| Path | What |
|---|---|
| `src/api/types.ts` | Hand-kept TS mirrors of the backend DTOs (`SCHEMA.md §5` for enums) |
| `src/api/client.ts` | The only `fetch` wrapper — session cookie, CSRF header, `ApiError`, 401→login |
| `src/api/hooks/` | One TanStack Query hook per operation; mutations invalidate the affected keys |
| `src/lib/` | Date/number formatting (`date-fns`) and enum label maps |
| `src/components/` | Presentational pieces + the dashboard widgets |
| `src/forms/` | `react-hook-form` + `zod` schemas and the two entity forms |
| `src/pages/` | One component per route; owns the mutations, passes state down |
| `src/styles/theme.css` | The handful of CSS custom properties; no component library |

Conventions (CLAUDE.md §11): components never call `fetch` directly — always a hook.
`strict: true`, no `any`.
