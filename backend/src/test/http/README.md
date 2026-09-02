# HTTP Client collection

IntelliJ's built-in HTTP Client — run requests from the gutter arrows, no Postman needed
(`CLAUDE.md §9`). Pick the environment (`local` / `prod`) from the dropdown at the top right.

- `jobtracker.http` — the full flow, the security boundary, and deliberate error cases.
- `http-client.env.json` — base URLs only. **Committed.**
- `http-client.private.env.json` — the credentials. **Never committed**; `.gitignore`
  excludes it by name, because the `*.env` rules do not match a `.json` filename.

## Credentials (from Phase 2 onward)

Create `http-client.private.env.json` next to this file:

```json
{
  "local": {
    "token": "<app.mcp-token from backend/config/application-local.yml>",
    "session": "<JSESSIONID cookie>",
    "xsrf": "<XSRF-TOKEN cookie>"
  }
}
```

**`token`** is the MCP bearer token. It works on `GET` and nothing else — that is the
read-only guarantee, not a limitation of this file. Every `GET` in the collection uses it, so
the entire read half runs without a browser.

**`session` and `xsrf`** are needed for writes, because writes require a human session. Sign
in once at `http://localhost:8080/oauth2/authorization/google` with the allowlisted account,
then copy both cookies from devtools → Application → Cookies → localhost. `XSRF-TOKEN` is
echoed back as the `X-XSRF-TOKEN` header; a 403 on a write is almost always that header
missing or stale, not an authorization problem.

The session expires. When writes start returning 401, sign in again and re-copy.

## Why this file earns its keep

1. ~~**Phase 4 backfill.**~~ Superseded — the job search already in progress was entered
   through the live UI instead, which doubled as the dogfooding pass. No `backfill.http` was
   written.
2. ~~**Phase 5 APM traffic.**~~ Superseded — **tracing was dropped from the project**
   (`CLAUDE.md §6`, 2026-09-02, and §14): APM is a separately-billed SKU with no trial
   offerable on the student Pro plan. The error cases at the bottom still earn their keep,
   though, for a different reason than originally written: they are what makes the Phase 5
   **error-rate monitor** test-fireable.
3. **It is executable documentation of the security model.** The boundary section asserts
   that an unauthenticated call is 401 JSON rather than a redirect, and that a valid MCP
   token attempting a write is 403.

Requests that create something store the new id in a global variable, so the file runs top to
bottom in one pass.
