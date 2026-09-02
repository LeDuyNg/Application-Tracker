# HTTP Client collection

IntelliJ's built-in HTTP Client — run requests from the gutter arrows, no Postman needed
(`CLAUDE.md §9`). Pick the environment (`local` / `prod`) from the dropdown at the top right
of the editor.

- `jobtracker.http` — the full flow, plus deliberate error cases.
- `http-client.env.json` — just the base URLs. **Committed.**
- `http-client.private.env.json` — auth tokens, once Phase 2 adds them. **Never committed**;
  IntelliJ creates it next to this file and `.gitignore` already excludes `*.private.env.json`.

Two things this collection is for beyond manual testing:

1. **Phase 4 backfill.** `backfill.http` will follow the same shape to enter the job search
   already in progress.
2. **Phase 5 APM traffic.** Re-running this against a local instrumented run is what
   generates the traces to screenshot — which is why the error cases at the bottom matter:
   without them the traces have no error spans in them.

Requests that create something store the new id in a global variable, so the file runs top
to bottom in one pass. Run it that way the first time; individual requests work afterwards
as long as the variables are still set.
