# STATE.md — session handoff

> **Read this first, then `CLAUDE.md`.** This file is the *volatile* half of the project's
> memory: where the work actually stands right now, what is running on this machine, and
> what to pick up next. The other three docs are the *stable* half and do not change often.
>
> **Keep this file current.** Update it at the end of every working session — it is worth
> more than a tidy commit history when a fresh session starts cold.

---

## 1. Which doc to read for what

| Question | File |
|---|---|
| What is this project, what stack, **why** each decision | `CLAUDE.md` — §6 decision log is the important part |
| What is the data model, what does each query do | `SCHEMA.md` |
| What is the full phased build plan, what's ticked | `PLAN.md` |
| Where am I *right now*, what do I do next | **this file** |

`CLAUDE.md` is the source of truth for the stack and conventions. If this file and
`CLAUDE.md` disagree about a *decision*, `CLAUDE.md` wins and this file is stale. If they
disagree about *what is built*, check the code.

---

## 2. Where the work stands

**Current phase:** **Phase 4 — Deploy. Done. The app is live at
`https://app4jobtrack.me`.** Oracle E2.1.Micro, Nginx + certbot TLS, systemd, Atlas M0,
and both GitHub Actions workflows deploying from `main`. Health `UP`, Datadog metrics
flowing to **US5**. Two things deliberately not done: **backups** (deferred, `RUNBOOK.md`
Step 12) and the **backfill**, so the live app is empty. Next is Phase 5 — Datadog
dashboard + alert; branch `phase-5-datadog`.

*Previously:* Phase 3 — React SPA. **Functional and merged to `main`. The UI is a
deliberate first pass — the owner will iterate on it later.** The whole SPA is built and
running: all seven pages, both forms, the stage timeline, a collapsible sidebar shell, a
signed-out landing page, and Google login **verified end to end in a browser** (the owner
logged in and back out). `./mvnw verify` and `npm run build` / `npm run lint` are green.
**Branch:** `phase-3-frontend`, merged `--no-ff` into `main`.
**`main`** now contains Phases 1–3, each phase boundary a merge commit. `./mvnw verify` was
run on the Phase 3 tree before the merge — 102 tests green — so "main is deployable" stays
checked rather than assumed.

The `phase-1-backend-crud` and `phase-2-auth` branches still exist locally and on the remote,
pointing into merged history. Harmless; delete them whenever.

### Done
- **Phase 1 — backend CRUD.** Domain, repositories, DTOs, mappers, `CompanyService`,
  `ApplicationService` (three denormalization rules), `StatsService`,
  `ApplicationQueryService` (follow-ups / upcoming interviews / escaped-regex search),
  16 endpoints, `GlobalExceptionHandler` (RFC 7807), and the `.http` collection. Verified
  against the real Atlas M0, not only Testcontainers.
- **Phase 2 — authentication.** Two `SecurityFilterChain` beans:
  - *bearer chain* (`@Order(1)`, matches an `Authorization: Bearer` header) — stateless,
    CSRF off, `GET /api/**` requires `ROLE_MCP`, everything else `denyAll()`. Token compared
    as SHA-256 digests.
  - *browser chain* (`@Order(2)`) — Google OIDC, `AllowlistOidcUserService` checking the
    allowlist **and** `email_verified`, CSRF cookie with the deferred-token opt-out,
    401/403 as `problem+json` instead of redirects, `GET /api/me`.
- **103 tests green** (`./mvnw verify`): 25 unit, 78 integration. Verified with the local
  `jt-mongo` container **stopped** — see the ambient-Mongo trap in §4; a green run with it
  running proves less than it appears to.
- **Phase 3 — React SPA.** `frontend/` on Vite 8 / React 19 / TS 6. `vite.config.ts`
  proxies `/api`, `/oauth2`, `/login` → `:8080`. `src/api/` = `types.ts` (DTO mirrors),
  `client.ts` (the one `fetch` wrapper: session cookie, `X-XSRF-TOKEN` on mutations,
  `ApiError`, 401→login), one TanStack Query hook per operation with cross-family
  invalidation after writes. `<App>` is an auth gate: splash → `Landing` (signed out) →
  the routed app. `AppShell` is a **collapsible left sidebar** (localStorage-remembered,
  icon rail + hover tooltips when collapsed) with a profile block (avatar / name / email /
  **Sign out**). Seven pages, both entity forms on react-hook-form + zod, an inline stage
  timeline. Theme: warm-editorial, Fraunces serif headings, one pine-green accent —
  `styles/theme.css`, ~450 lines, no framework. **The visual design is a first pass the
  owner has explicitly deferred refining.**
- **Google login + logout verified in a browser.** The owner signed in with the allowlisted
  account and signed back out. `POST /api/logout` → 204 (`LogoutIT` covers it).
- **Phase 4 — deploy.** Live at `https://app4jobtrack.me`. Oracle `VM.Standard.E2.1.Micro`
  (Ubuntu 24.04, 4 GB swap), Temurin 25 JRE, systemd `jobtracker.service` with `-Xmx256m`
  and `MemoryHigh/Max` caps, Nginx + certbot with the security-header snippet and rate
  limiting, Atlas M0 over `authSource=admin`, three identities (`ubuntu` / `deploy` /
  `jobtracker`) with sudo scoped to one command. `deploy/RUNBOOK.md` is the full record,
  written and corrected as the deploy happened rather than after.
- **CI/CD.** `.github/workflows/backend.yml` and `frontend.yml`, path-filtered, deploying
  on push to `main`. The backend workflow SHA-names each JAR, repoints the `app.jar`
  symlink, keeps the newest three as the rollback, and gates on `/actuator/health` polled
  **over ssh on the box** — the endpoint is loopback-only, so a runner curling the public
  URL would 401 and fail every deploy.
- **Datadog metrics reaching the platform**, agentless via Micrometer, on site **US5**.
- **Pre-deploy security pass (2026-09-02).** Full read of backend + frontend for security
  flaws. Core auth model held up; six changes applied — URL scheme allowlist on
  `jobPostingUrl` / `website` at both ends (stored-XSS sink), `app.mcp-token` local default
  removed, `@Valid` moved onto type arguments (HV000271), collection + page-size caps,
  `deploy/nginx-*.conf` written early for rate limiting and `X-Forwarded-Proto`, and
  `/actuator/health` narrowed to loopback callers. **103 tests green.** Full reasoning in `CLAUDE.md §6`, entry "Pre-deploy security pass".

### Open, after Phase 4
1. **The backfill, and a full dogfooding pass.** Still the biggest gap, and now the *only*
   thing between here and using the app for real. `PLAN.md` Phase 4 puts the backfill first
   for a reason — it is the cheapest place to find schema and validation problems — and it
   was skipped in the rush to deploy. **The live database is empty.** Do it against the
   deployed app now rather than locally; that also serves as the dogfooding pass.
2. **Backups are deferred**, not done. Atlas M0 has no automated backup, no undelete and no
   point-in-time restore, so the moment real data goes in, it exists in exactly one place.
   Worth reconsidering *before* the backfill rather than after. `RUNBOOK.md` Step 12.
2. **The UI itself.** Functional and coherent but deliberately unpolished; the owner will
   iterate. No toast system (mutation errors render inline via `ErrorNote`); mobile is
   flex-wrap + a sidebar-to-top-strip breakpoint, not a real responsive pass.
3. **`npm run preview`** on the built `dist/` — never run (the `build` step itself is green).
4. ~~**Static assets have no response security headers.**~~ **Done** —
   `deploy/jobtracker-security-headers.conf` (HSTS, nosniff, Referrer-Policy,
   Permissions-Policy, X-Frame-Options, CSP), included in each static location because
   `add_header` does not inherit into a block that sets one of its own. The whole vhost was
   run under `nginx:alpine` in Docker: `nginx -t` passes, the headers appear on `/`,
   `/index.html` and `/assets/*`, and the rate limiter returns 429 after its burst.
   **Re-run `nginx -t` on the VPS** once certbot has rewritten the TLS block.
4. **Google Cloud Console** must have `http://localhost:5173/login/oauth2/code/google` as an
   authorized redirect URI — it does (login worked), noted here so it is not forgotten for prod.

### Phase 0 — complete
Datadog is redeemed, shows **Pro**, and an API key is generated. The
**APM-trial-availability check is done and the answer is no** (2026-09-02) — APM is a
separate paid SKU and no trial is offerable on top of student Pro. Found at the right time,
which is the whole reason that item existed.

**Decided: tracing is dropped from the project entirely** (Option A). A second Datadog org on
a fresh evaluation trial and OpenTelemetry-to-a-local-collector were both considered and
declined — see `CLAUDE.md §6`, "Tracing dropped", for why. Nothing outwardly claimed changed:
§1 and the resume bullet were already narrowed on 2026-09-01 and never promised production
APM. `CLAUDE.md §1/§2/§3/§4/§14` and `PLAN.md` Phase 5 are all updated; APM is now a named
non-goal in §14 rather than an omission.

Phase 5 is therefore metrics + dashboard + one alert, and nothing on a clock.

### Gaps noticed, deliberately not built
- ~~No logout endpoint / button.~~ **Done** — `POST /api/logout` → 204 on the browser
  chain, "Sign out" in the sidebar profile block, `useLogout()` clears the query cache and
  hard-navigates to `/`.
- **No refresh-token / silent re-auth.** A lapsed session drops the user to the landing
  page on the next call (the api client redirects to Google on a 401). Fine for one user;
  worth a note if it ever annoys.

---

## 3. This machine

| Thing | State |
|---|---|
| JDK | **25.0.4.1** via Homebrew `openjdk@25`. **Not** symlinked, so `/usr/libexec/java_home -V` does not list it — that is expected, not a problem. IntelliJ uses it. |
| Shell JDK | Defaults to **26**. `./mvnw` from a terminal cross-compiles with `--release 25`; IntelliJ compiles on 25. Set `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home` in `~/.zshrc` to make them agree. Open item. |
| MongoDB | Docker container `jt-mongo`, **mongo:8.3.8**, on `localhost:27017`. `docker start jt-mongo` after a reboot — `docker run` only ever needs to happen once. |
| IntelliJ | Project opened at the **repo root**, `backend/` imported as a Maven module. Run config **JobTracker (local)** is committed at `.idea/runConfigurations/`. |
| Docker | Required for Testcontainers (`./mvnw verify`). |
| Node | **v25.2.1 / npm 11.13.0.** `frontend/` scaffolded with Vite 8, React 19, TypeScript 6 (newer than the `react-ts` template CLAUDE.md §3 anticipated — no code impact). `node_modules/` is gitignored; run `npm install` in `frontend/` on a fresh clone. |

### Commands

```bash
cd backend
./mvnw test      # unit only (Surefire)  — fast, no Docker
./mvnw verify    # + *IT (Failsafe)      — needs Docker
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# app: http://localhost:8080 · swagger: /swagger-ui.html · health: /actuator/health

cd frontend
npm install
npm run dev      # http://localhost:5173 — proxies /api, /oauth2, /login → :8080
npm run build    # tsc -b && vite build  (this is the type-check too)
npm run lint     # oxlint
npm run preview  # serve the built dist/
```

Full local stack: `docker start jt-mongo`, then the backend on `local`, then `npm run dev`.

IntelliJ's green arrow runs `*Test` **and** `*IT` alike, ignoring the Surefire/Failsafe
split. Only `./mvnw verify` reproduces CI.

---

## 4. Traps already found — do not rediscover these

All are recorded with full reasoning in `CLAUDE.md §6`. Listed here because each cost real
time and each would silently reappear if someone copied a Spring Boot 3 snippet.

| Trap | What happens |
|---|---|
| `spring.data.mongodb.uri` | Deprecated at level **error** in Boot 4 — not bound at all, silently falls back to database `test`. Use **`spring.mongodb.*`**. |
| `LocalDate` storage | Spring Data converts it using the **JVM default timezone**, so the same date is a different instant per machine. `config/MongoConfig` pins it to UTC midnight. Do not remove it. |
| Jackson date property | Jackson 3 moved `WRITE_DATES_AS_TIMESTAMPS` to `DateTimeFeature`. The Boot 3 key `spring.jackson.serialization.*` **fails the context load**. |
| Datadog registry | Auto-configures on classpath presence and demands an API key. Must stay `enabled: false` by default or every `@SpringBootTest` fails. |
| Testcontainers 2.x | Modules renamed: `testcontainers-junit-jupiter`, `testcontainers-mongodb`. The Boot parent does not manage their versions — the BOM is imported explicitly. |
| springdoc | Needs the **3.x** line for Boot 4. 2.x targets Boot 3 and will not work. |
| Atlas SRV string | Has **no database name** — ends `/?retryWrites=...` or `/?appName=...`. Pasted as-is: `IllegalArgumentException: Database name must not be empty` at startup. Put `jobtracker` between the `/` and the `?`. |
| Atlas password with punctuation | The SRV string is a **URI**, so a password containing `@ : / ? # [ ] %` must be percent-encoded or the parse breaks — `@` is the common one and splits the userinfo in the wrong place, giving a host-lookup or auth error that points nowhere near the password. Encode it, or choose an alphanumeric password. Not yet hit; listed because it is the same family as the row above. |
| Commented-out YAML | Uncommenting `# key:` by deleting only the `#` leaves a leading space, shifting the document to indent 1. SnakeYAML then blames a *later* line with "expected '<document start>'". Comment as `#key:` at the right indent so deleting `#` alone is correct. |
| `oauth2Login()` with no client | Boot only creates a `ClientRegistrationRepository` when a registration is configured, so from Phase 2 **every** `@SpringBootTest` fails at context creation without dummy `client-id`/`client-secret`. Set on `AbstractMongoIT` and `JobTrackerApplicationTests`. |
| `LocalDate.now()` in a test | The JVM's default zone, not `app.timezone`. A `daysOverdue` assertion passed all day and failed at 21:00 Pacific, when it became tomorrow in New York. Tests asserting on relative-date boundaries must use `TimeService.today()`. |
| MockMvc on Boot 4 | Needs **`spring-boot-starter-webmvc-test`** — `spring-boot-starter-test` no longer carries the web slice — and `@AutoConfigureMockMvc` moved to `org.springframework.boot.webmvc.test.autoconfigure`. Fails as "package does not exist". |
| `@Testcontainers` + `@Container` | Ties the container to a **test class** — it is stopped when that class ends, so every later `*IT` gets `Connection refused` against a cached port. Start it in a static initializer. |
| `MongoDBContainer` import | Testcontainers 2.x ships **both** `org.testcontainers.mongodb.MongoDBContainer` and the 1.x shim `org.testcontainers.containers.MongoDBContainer`. Both compile. Use the former. |
| `null` in a `$lte` query | `null` is not `$lte` anything, so a document with a null field silently drops out of a range query. Load-bearing for `followUpDate` (wanted) and `lastContactAt` (a bug until it was seeded on create). |
| OAuth success `redirect-uri` vs proxy | `oauth2Login().defaultSuccessUrl("/")` sends a root-relative `/` that, behind the Vite dev proxy, resolves against the **backend** (`:8080`) — the user lands on the bare API, hits `denyAll()`, sees a naked 403. Fix: redirect to an **absolute** URL built from `app.base-url` (`:5173` local, real origin prod). See CLAUDE.md §6. |
| A local Mongo makes `./mvnw verify` lie | `JobTrackerApplicationTests` was a `@SpringBootTest` with no Testcontainers, so it fell back to Spring Data's default `mongodb://localhost:27017` — and passed for three phases because the dev `jt-mongo` container was listening there. `IndexInitializer` is an `ApplicationRunner`, which `@SpringBootTest` runs, so any full-context test opens a Mongo connection. The first CI run failed instantly. **`./mvnw verify` only reproduces CI if nothing is listening on 27017**: `docker stop jt-mongo` before trusting a green run. Fixed by making it `JobTrackerApplicationIT extends AbstractMongoIT`. |
| `http2 on;` on Ubuntu 24.04 | Standalone directive from **nginx 1.25.1**; Ubuntu 24.04 ships 1.24.0, where it is `unknown directive "http2"` and nginx will not load — which also blocks `certbot --nginx`, since certbot runs `nginx -t` first. Use `listen 443 ssl http2;`. Verify configs against **`ubuntu:24.04`'s** nginx, not `nginx:alpine`, which is far newer and passes. |
| certbot's chicken-and-egg | A vhost whose `:443` blocks have no `ssl_certificate` cannot load, and `certbot --nginx` refuses to run when `nginx -t` fails — so the certificate can never be obtained. Ship the blocks pointing at Ubuntu's `ssl-cert-snakeoil` placeholder; certbot rewrites both lines on issue. |
| Atlas `authSource` | Adding the database name (`/jobtracker`) to satisfy Spring silently changes the **auth** database: the spec defaults `authSource` to the database in the URI, falling back to `admin` only when there is none. Atlas creates every user in `admin`. Result: `bad auth : authentication failed`, which reads as a wrong password. Append `&authSource=admin`. Tell: `SCRAM-SHA-1` in the error — Atlas negotiates SHA-256 for users it recognises, so a SHA-1 fallback means the user was not found. |
| `DD_SITE` mismatch | Datadog runs several sites (`datadoghq.com` = US1, `us3`, `us5`, `datadoghq.eu`, `ap1`) and a key is valid only on its own. A mismatch is rejected with no error the app surfaces and no data in the UI — a healthy service and an empty dashboard, with nothing connecting the two. This org is **US5**. Diagnose with `curl https://api.<site>/api/v1/validate -H "DD-API-KEY: …"`. |
| Sourcing an `EnvironmentFile` in bash | `set -a; . jobtracker.env` looks like a fair way to test config and lies: it is not a shell script, and `MONGODB_URI` contains `&`, which bash reads as "run in background". The assignment truncates and vanishes, `$MONGODB_URI` is empty, and the probe fails against `localhost` — a completely different fault from the one being chased. systemd's parser is unaffected, so this only ever misleads diagnosis. |
| `mongodump` prints the URI on failure | Password included, straight into scrollback. Pipe probe output through `sed -E 's\|(//)[^:]*:[^@]*(@)\|\1USER:PASS\2\|'`. |
| `ssh user@ip` vs a `Host` alias | A `~/.ssh/config` `Host myalias` block matches the **alias**, not the address inside it. `ssh ubuntu@<ip>` matches nothing, falls back to default identities, and fails `Permission denied (publickey)` on a box reachable a second earlier by alias. |
| `SecurityIT.csrfCookieIsNotDeferred` is order-fragile | It asserts "the first request in the run emits a fresh `XSRF-TOKEN` cookie". Adding more `.with(csrf())` tests to the same class perturbs the ordering it depends on and it fails with "No cookie". Logout tests live in their own `LogoutIT` for this reason. |

**General lesson:** this project runs Spring Boot 4 / Jackson 3 / Spring Data 5, and most
material online is Boot 3. Verify property names against the config metadata in the jars
rather than trusting a snippet.

---

## 5. Phase 0 — outstanding, and one thing that changed

Phase 0 is **paused, not finished**. None of it blocks Phase 1 except the Atlas item.

- [x] Domain registration
- [x] Oracle instance — **shape changed**: `VM.Standard.E2.1.Micro` (1/8 OCPU burstable,
      1 GB RAM, x86), **one instance only**. A1 ARM was unobtainable. See `CLAUDE.md §6`.
- [x] MongoDB Atlas M0 cluster
- [x] Google Cloud OAuth client
- [x] Datadog student-pack redemption, and the **APM-trial-availability check** — the one
      Phase 0 item with no recovery path if discovered late. Done 2026-09-02: Pro confirmed,
      API key generated, no APM trial offerable → tracing dropped (`CLAUDE.md §6`).

**Consequence of the 1 GB single host, already propagated through the docs:** no Datadog
Agent anywhere, therefore no host infra metrics for the box. Combined with the 2026-09-02
finding that no APM trial is offerable, **there is no tracing in this project at all** — a
named non-goal in `CLAUDE.md §14`, not an omission. The resume bullet and §1 deliverables
were narrowed on 2026-09-01 and needed no further edit. **Do not quietly widen those claims
back**: production is custom metrics, a dashboard and one alert.

---

## 6. Working agreements that have held up

- Docs and code must agree. Several `SCHEMA.md` statements were wrong and were corrected
  *as code was written against them*, not left to drift. Keep doing that, and record the
  reasoning rather than silently editing.
- The decision log is **append-only**. Superseded entries get an inline
  *(**Superseded** — see …)* marker; they are not rewritten.
- Every commit message ends with a `Claude-Session:` trailer carrying **that session's**
  URL — a new one each session, not the one from a previous session copied forward.
- Verify, don't assume. The index initializer *logged* nine successes while writing to the
  wrong database; the stats IT caught a timezone bug because expected values were derived
  by hand rather than recorded from output. Both would have reached production.
