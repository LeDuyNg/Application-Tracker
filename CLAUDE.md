# CLAUDE.md — Job Application Tracker

> **Read `STATE.md` first, then this file.** `STATE.md` says where the work actually
> stands, what is running on the machine, and what to do next; it changes every session.
> This file is the source of truth for the project: what it is, the stack, the
> architecture, every decision made and why, the conventions to follow, and where the
> detail lives (`STATE.md`, `PLAN.md`, `SCHEMA.md`).
>
> Keep this file current. When a decision changes, update **§6 Decision log** with a new
> dated entry — do not silently rewrite history.

---

## 1. What this project is

A **deployable web app to track a real job search** — companies, roles, applications, and
the interview stages each application goes through — plus observability and an AI query
layer.

Four deliverables, all built around one data store:

1. **CRUD app** — Spring Boot REST API + React dashboard over MongoDB.
2. **Datadog integration** — custom metrics + a dashboard + one alert, against the
   *deployed* instance. **No APM, anywhere**: it is a separately-billed SKU with no trial
   offerable on the student Pro plan, and the 1 GB host could not carry the Agent beside
   the JVM in any case (§6, 2026-09-02). Metrics reach Datadog over HTTPS via Micrometer
   and never needed an Agent, so this deliverable is unaffected. A smaller true claim
   beats a vague larger one.
3. **MCP server** — a thin, read-only layer letting Claude Desktop answer natural-language
   questions about the job search ("how many applications this month?", "what interviews
   do I have this week?").
4. **Deployment** — live on an Oracle Cloud VPS, used daily for the owner's real job search
   (dogfooding is the point).

### Why it matters (portfolio / JD context)

- A **distinct domain** from the owner's other projects (not another monitoring tool).
- Demonstrates **full-stack ownership of a real deployed service**, not a local dev toy.
- Ties together **data modeling** (MongoDB), **observability** (Datadog), and **AI-tool
  integration** (MCP) around one practical product — a coherent story, not three bolted-on
  features.

### Resume bullet (draft — keep in sync with reality)

> Built and deployed a full-stack job application tracker (Java 25, Spring Boot 4, React,
> MongoDB Atlas) with Datadog metrics, dashboards and alerting, and an MCP server enabling
> natural-language queries over application status and interview stages via Claude Desktop.

*(**Every word of this is now true rather than planned**, as of 2026-09-02: the metrics,
the dashboard and the error-rate monitor all exist against the deployed instance. It says
"metrics, dashboards and alerting", not "APM", and that is the whole claim — there
is no tracing in this project, locally or in production (§6, 2026-09-02). The bullet was
written this way before that was forced, which is why it needed no edit when it was.
"Why no APM?" has a real answer worth giving in an interview: a 1 GB host, an Agent needing
~0.5 GB beside a JVM already at ~500 MB, one instance in the tenancy, and a separately-billed
tracing SKU.)*

---

## 2. Current status

| | |
|---|---|
| **Current phase** | **Phase 6 — MCP server**, on branch `phase-6-mcp`. The server is built, tested against the live API and wired into `claude_desktop_config.json`; what remains is restarting Claude Desktop and running the four example queries. Phases 1–5 done and merged. Live at `https://app4jobtrack.me`, auto-deploying from `main`, with custom metrics, a dashboard and an error-rate monitor in Datadog (**us5**) — **no APM, no Agent**, by decision (§6, §14). Backups remain deferred. See `STATE.md`. |
| **Phase 0 status** | **Complete.** Domain, Oracle VM, Atlas M0, Google OAuth client all done. Datadog redeemed, on **Pro**, API key generated. The APM-trial-availability check came back **no**, and the response was to **drop tracing from the project entirely** — see §6 (2026-09-02) and §14. |
| **Session handoff** | See **`STATE.md`** — current branch, what is built, what is next, machine setup, and the Boot 4 traps already found |
| **Plan** | See `PLAN.md` for the full phased checklist |
| **Schema** | See `SCHEMA.md` for the full data model |
| **Live URL** | **`https://app4jobtrack.me`** — live since 2026-09-02. Deploy record: `deploy/RUNBOOK.md`. |
| **Repo** | `https://github.com/LeDuyNg/Application-Tracker` — `main` carries Phases 1–5, each merged `--no-ff` so every phase boundary is a commit. Current branch: `phase-6-mcp`. |
| **Local dev** | `docker start jt-mongo` (MongoDB 8.3.8 on `:27017`), then the **JobTracker (local)** run config, then `cd frontend && npm run dev` (`:5173`). |
| **IDE** | **IntelliJ IDEA** — Spring Initializr, HTTP Client, Docker, and Database tool windows are all used; see §9. |
| **Datadog plan** | **Pro via the GitHub Student Developer Pack** (10 hosts, ~13-month retention, free for 2 years). APM is *not* included and no trial is offerable on top of it; tracing is out of scope (§6, §14). |

Update this table at the end of every working session.

---

## 3. Tech stack (authoritative — do not substitute without a decision-log entry)

### Backend
| Thing | Choice | Notes |
|---|---|---|
| Language | **Java 25 (LTS)** | GA Sep 2025. Chosen as the current LTS; the original tie-breaker was `dd-trace-java` support, which no longer applies now that tracing is out of scope (§14) — the LTS argument stands on its own. |
| Framework | **Spring Boot 4.1.x** (on `4.1.1`) | Spring Framework 7. Baseline Java 17+. **Jackson 3** is the default JSON mapper — package is `tools.jackson.*`, not `com.fasterxml.jackson.*`. |
| Build tool | **Maven** | Single module in `backend/`. Use the `mvnw` wrapper (IntelliJ picks it up automatically). |
| Persistence | **Spring Data MongoDB** | Repositories + `MongoTemplate` for aggregations. |
| Security | **Spring Security** + `spring-boot-starter-oauth2-client` | Google OAuth2 login for the SPA; a static bearer token for the MCP server. |
| API docs | **springdoc-openapi 3.x** | Swagger UI at `/swagger-ui.html` — useful for manual testing while learning. **Pin the 3.x line**: springdoc 2.x targets Boot 3 / Framework 6 and will not work on Boot 4. **Disabled in prod** (`springdoc.api-docs.enabled=false`) — see §6. |
| Metrics | **Micrometer** + `micrometer-registry-datadog` | Pushes custom metrics straight to the Datadog API over HTTPS — independent of whether the Agent is installed. Budget: ~100 custom timeseries (see §6). |
| Tests | JUnit 5, **Testcontainers** (real MongoDB), Spring Boot Test | `*Test` = unit (Surefire), `*IT` = integration (Failsafe). Use the `mongo:8` image to match the Atlas major version. |

### Frontend
| Thing | Choice | Notes |
|---|---|---|
| Tooling | **Vite** + **React** + **TypeScript** (`react-ts` template) | Dev server on `:5173`, proxies `/api` etc. to `:8080`. |
| Server state | **TanStack Query** (`@tanstack/react-query`) | All API calls go through query/mutation hooks. |
| Routing | **react-router-dom** | |
| Forms | **react-hook-form** + **zod** + `@hookform/resolvers` | Zod schemas mirror the backend DTOs. |
| HTTP | **`fetch`** wrapped in a small `apiClient` | `credentials: "include"` so the session cookie is sent. |
| Dates | **date-fns** | Formatting and relative-date display only. The single date dependency; no moment/dayjs/luxon. |
| Styling | **Plain CSS / CSS Modules** | Deliberately minimal — the owner is new to React; no Tailwind/CSS-in-JS concepts to learn on top of React itself. |

### MCP server
| Thing | Choice | Notes |
|---|---|---|
| Language | **TypeScript** (Node) | Official `@modelcontextprotocol/sdk`. |
| Transport | **stdio** | Launched as a subprocess by Claude Desktop. Runs **locally**, not on the VPS. |
| Runtime | `tsx` for dev, compiled `dist/` for the configured entry | |
| Testing | `@modelcontextprotocol/inspector` first, then Claude Desktop | |

### Database
| Thing | Choice | Notes |
|---|---|---|
| Store | **MongoDB Atlas M0** (free tier) | 512 MB, shared, 3-node replica set under the hood. **No automated backups on M0** → we run our own `mongodump` cron. TLS enforced. IP allowlist required. |
| Local (tests/dev) | MongoDB via **Testcontainers** / a throwaway `docker run` | Prod is Atlas; local is only for running tests. |

### Deployment / infra
| Thing | Choice | Notes |
|---|---|---|
| Host | **Oracle Cloud "Always Free" — `VM.Standard.E2.1.Micro` (AMD x86)** | **1/8 OCPU baseline (bursts to 1 full OCPU), 1 GB RAM.** One instance. Chosen because A1 ARM capacity is unobtainable — see §6. 1 GB demands a tuned JVM and swap; it also rules out running the Datadog Agent alongside the app. |
| OS | **Ubuntu 24.04 (x86_64)** | Default login user `ubuntu`. x86 rather than arm64 since the shape changed — one less architecture caveat for any native dependency. |
| Runtime deploy | **Fat JAR + `systemd`** — **no Docker on the VPS** | Mongo is managed (Atlas), so the box only runs the API + Nginx; Docker's parity/autodiscovery wins don't apply and it costs memory. |
| Reverse proxy / TLS | **Nginx + certbot (Let's Encrypt)** | Same pattern the owner used on a prior project. Serves the SPA static build and proxies `/api`, `/oauth2`, `/login` to `127.0.0.1:8080`. |
| CI/CD | **GitHub Actions** | `mvn verify` → `mvn package` → `scp` JAR to the VPS → `ssh systemctl restart`. Frontend: `npm run build` → `rsync dist/` → `/var/www/jobtracker`. |
| DNS | A record → the instance's **reserved** public IP | Must be *reserved*, not ephemeral, or a stop/start breaks the Atlas allowlist and TLS. |

---

## 4. Architecture

```
                         ┌──────────────────────────────────────────┐
                         │   Oracle E2.1.Micro VPS (Ubuntu, 1 GB)    │
                         │                                          │
  Browser ───HTTPS──────▶│  Nginx  ──/──────▶  React static build   │
   (you)                 │   :443   ──/api────▶ ┐                   │
                         │          ──/oauth2─▶ │  Spring Boot API   │
                         │          ──/login──▶ │  (systemd, :8080)  │
                         │                      └────────┬──────────┘
                         └───────────────────────────────┼───────────┘
                                                         │ mongodb+srv (TLS)
                                                         ▼
                                            ┌───────────────────────┐
                                            │  MongoDB Atlas M0     │
                                            │  (companies,          │
                                            │   applications)       │
                                            └───────────▲───────────┘
                                                        │ Bearer token, HTTPS
  Claude Desktop ──stdio──▶  MCP server (local, TS)  ───┘  (calls the deployed /api)

  Spring Boot API ──HTTPS──▶ Datadog API   (custom metrics via Micrometer, always)

  There is no Datadog Agent anywhere in this diagram, and that is deliberate: Micrometer
  pushes over the API, and tracing is out of scope entirely (§6, §14).
```

### Request flows

- **Human using the app:** Browser → Nginx → SPA loads → SPA calls `/api/...` with the
  session cookie → Spring Security checks the session → controller → service → MongoDB
  Atlas. Unauthenticated `/api` calls return **401** (not a redirect); the SPA then sends
  the user to `/oauth2/authorization/google`.
- **Claude Desktop query:** user asks a question → Claude picks an MCP tool → MCP server
  calls the deployed `/api/...` endpoint with `Authorization: Bearer <MCP_TOKEN>` → a
  token filter authorizes it (read-only) → same controller/service path → JSON back to
  Claude.
- **Metrics:** the deployed API pushes custom metrics to Datadog continuously via the
  Micrometer Datadog registry — over HTTPS, no agent involved. **There is no Datadog Agent
  anywhere**, in production or locally, and therefore no traces at all. Tracing is a
  declared non-goal (§14); the reasoning is in §6 (2026-09-02).

---

## 5. Repository layout

```
Application-Tracker/
├── STATE.md                   ← session handoff: where the work stands, read this first
├── CLAUDE.md                  ← this file (source of truth)
├── PLAN.md                    ← phased build plan + checklists
├── SCHEMA.md                  ← full data model
├── README.md                  ← written in Phase 7 (public-facing)
├── .gitignore
│
├── backend/                   ← Spring Boot + Maven, single module
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd / .mvn/
│   └── src/
│       ├── main/java/dev/duynguyen/jobtracker/
│       │   ├── JobTrackerApplication.java
│       │   ├── config/        ← SecurityConfig, MongoConfig, OpenApiConfig, IndexInitializer, WebConfig
│       │   ├── company/       ← Company, CompanyRepository, CompanyService, CompanyController, dto/
│       │   ├── application/   ← Application, Stage, *Repository, *Service, *Controller, dto/
│       │   ├── stats/         ← StatsService (MongoTemplate aggregations), StatsController
│       │   ├── auth/          ← BearerTokenFilter, AllowlistOidcUserService, ProblemDetailAuthHandler, MeController
│       │   └── common/        ← GlobalExceptionHandler, enums/, error DTO, time utils, Validation
│       ├── main/resources/
│       │   ├── application.yml            ← shared defaults
│       │   ├── application-local.yml      ← local dev (localhost Mongo, Swagger on)
│       │   └── application-prod.yml       ← prod (env-var driven, no secrets in file)
│       └── test/java/...                  ← *Test (unit), *IT (Testcontainers)
│
├── frontend/                  ← Vite + React + TS
│   ├── package.json / vite.config.ts / tsconfig.json / index.html
│   └── src/
│       ├── main.tsx / App.tsx
│       ├── api/               ← apiClient.ts, types.ts (mirror backend DTOs), hooks/
│       ├── components/        ← StatCard, StatusBadge, StageTimeline, FiltersBar, SafeLink, ...
│       ├── pages/             ← Dashboard, ApplicationsList, ApplicationDetail, ApplicationForm
│       └── lib/               ← formatting, date helpers, url.ts (href scheme allowlist)
│
├── mcp-server/                ← TypeScript MCP server (runs locally, calls the deployed API)
│   ├── package.json / tsconfig.json / README.md / .env.example
│   └── src/
│       ├── index.ts           ← server + stdio transport + tool registration
│       ├── config.ts          ← reads/validates API_BASE_URL + API_TOKEN; fails closed
│       ├── apiClient.ts       ← fetch wrapper, injects Bearer token. `get` only, no writes
│       ├── types.ts           ← mirrors of the backend response DTOs
│       ├── format.ts          ← API JSON → the text Claude reads (dates, zones, enums)
│       └── tools/             ← one file per tool, plus result.ts (text/error shapes)
│
├── deploy/
│   ├── jobtracker.service     ← systemd unit for the API
│   ├── nginx-jobtracker.conf  ← Nginx vhost (rate limiting; written in Phase 3)
│   ├── jobtracker-proxy.conf  ← proxy_set_header snippet, incl. X-Forwarded-Proto
│   ├── backup-mongo.sh        ← mongodump + push to Oracle Object Storage (rclone)
│   ├── backup-mongo.service   ← + backup-mongo.timer (systemd timer)
│   └── RUNBOOK.md             ← step-by-step server setup + deploy + restore
│
└── .github/workflows/
    ├── backend.yml            ← build, test, deploy the API
    └── frontend.yml           ← build, deploy the SPA
```

Java base package: **`dev.duynguyen.jobtracker`**. Package **by feature**, not by layer.

---

## 6. Decision log

Each entry: what was decided, why, what was rejected. **Append, don't rewrite.**

### 2026-09-01 — Initial architecture

- **Java 25 LTS (not 26).** Non-LTS 26 was considered; 25 removes the risk of the
  `dd-trace-java` javaagent lagging a brand-new JDK, and "current LTS" is the safer resume
  claim. App code is JDK-version-agnostic anyway.
- **Spring Boot 4 + Maven (not Gradle).** Owner's preference is Maven. Single-module
  project; the frontend and MCP server are separate toolchains in sibling directories.
- **MongoDB Atlas M0 (not self-hosted on the VPS).** Rejected self-hosting because: M0 has
  no automated backup either, so the `mongodump` cron is needed *both* ways — self-hosting
  only adds version upgrades, hardening, and disk management on top. M0 also gives a
  replicated 3-node cluster for free and **decouples the data from a reclaimable free-tier
  VM**. Cost: an Atlas dependency (mild tension with the "no third-party dependency"
  ideal) and a DB-admin talking point that a prior project already covers.
- **Bare JAR + systemd on the VPS (not Docker).** With Mongo managed, the box only runs
  the API + Nginx. Docker's main wins (local/prod DB parity, agent autodiscovery)
  evaporate, and it costs memory on a 2-OCPU box. Trade-off accepted: no image-tag
  rollback (mitigate by keeping the last 3 JARs), and no "containerized" resume line
  (systemd + Nginx + TLS + CI/CD + managed DB still tell a deployment story).
- **Nginx + certbot (not Caddy).** Matches the pattern the owner already knows from a
  prior project; no new tool to learn mid-project.
- **Auth: Google OAuth2 for humans + a static bearer token for the MCP server.** Google
  login is an interactive browser redirect and can't be done headless, so the MCP server
  needs a separate non-interactive credential. Both live in one Spring Security filter
  chain; the bearer path is **read-only**. Single permitted user, enforced by an
  email allowlist env var.
  *(The "one filter chain" half is **superseded** by the 2026-09-01 review entry below —
  two chains. The rest stands.)*
- **Datadog: Micrometer registry for custom metrics permanently; the Agent + `dd-trace`
  javaagent only during the 14-day trial** to capture APM screenshots for the README,
  then removed. APM traces are not on the Datadog free tier; infra/custom metrics,
  dashboards, and monitors are.
  *(**Superseded** by the 2026-09-01 review entry below: the GitHub Student Developer Pack
  provides Pro, so the Agent stays permanently. The APM half still stands — APM is a
  separate paid SKU on every plan.)* *(**Superseded** by the 2026-09-02 entry "Tracing dropped" — there is no APM in this project at all.)*
- **MCP server in TypeScript, stdio transport.** Most-trodden path for Claude Desktop;
  official SDK; runs locally and calls the *deployed* API so validation stays in one
  place.

### 2026-09-01 — Data model

- **Two collections: `companies` and `applications`; `stages[]` embedded in
  `applications`.** "Apply to multiple roles at one company" makes the company a real
  grouping entity (reference it — independent identity, shared across many applications).
  A "process" in the owner's words = an interview **stage/round** (OA, live screen,
  technical, superday...), which is owned by the application, bounded, and always read
  with it → embed. This deliberately uses reference *and* embed, which is the modeling
  story for interviews. See `SCHEMA.md`.
- `stages[]` **replaces** the earlier separate `statusHistory[]` and `interviews[]`
  arrays — they were redundant (a phone screen was both a "status" and an "interview").
- `companyName` is **denormalized** onto each application (list views + text search
  without a lookup); kept in sync on company rename.
- Application-level `status` and `currentStageType` are **denormalized** for fast
  filtering/display; `stages[]` is the source of truth for the timeline.
- **Superday granularity:** default to one stage per superday; split into multiple stages
  ad hoc when per-interview detail is wanted. No schema change either way.

### 2026-09-01 — Corrections from the pre-build doc review

A review of these three documents before any code was written surfaced the following.
Each is a change to what was previously written here or in `SCHEMA.md` / `PLAN.md`.

- **Datadog: Pro via the GitHub Student Developer Pack; the Agent stays permanently.**
  Supersedes the "Agent only during the trial" half of the earlier Datadog decision. The
  pack gives Pro for 10 hosts free for 2 years, with ~13-month metric retention instead of
  the free tier's 1 day — which is what makes an "applications created over time" widget
  worth screenshotting at all. With host slots free, the Agent runs permanently and we get
  host infra metrics (CPU/memory/disk) alongside the app's custom metrics for nothing.
  *(**Superseded** by the host-change entry below — the Agent does not fit on a 1 GB box.
  The APM-is-a-separate-SKU half still stands.)*
  **APM is unchanged and still trial-only**: it is a separate paid SKU (~$31/host/mo) and
  is *not* part of Pro, student pack or otherwise. Micrometer's registry pushes over the
  API and does not depend on the Agent, so it stays as-is either way. *(**Superseded** by the 2026-09-02 entry "Tracing dropped" — there is no APM in this project at all.)*
- **Custom-metric budget: ~100 timeseries.** Pro allots 100 custom metrics *per host* and
  we have one host. Datadog counts unique metric-name + tag-value combinations, and
  Micrometer expands one timer into several metrics (count/sum/avg/max), so exporting
  Actuator's `http.server.requests` unfiltered (`uri × method × status × outcome ×
  exception`) can consume most of the budget by itself. Filter it with a `MeterFilter`
  or use explicit `@Timed` on the few endpoints that matter. Never tag by application id.
- **Two Spring Security filter chains, not one.** Supersedes the "both live in one filter
  chain" decision. A `securityMatcher`-scoped **stateless bearer chain** (CSRF off,
  `SessionCreationPolicy.STATELESS`, only `GET` permitted) plus a **session/OAuth2 chain**.
  This makes the MCP token's read-only rule a single request-matcher line instead of
  `@PreAuthorize` sprinkled over GET-backed service methods, and removes the filter
  ordering hazard entirely. Compare the token with `MessageDigest.isEqual` (constant time).
- **Swagger UI is disabled in prod** (`springdoc.api-docs.enabled=false`). It was
  previously `permitAll` in the Phase 2 security config, which would publish the whole API
  surface on a public domain. Local/dev keeps it.
- **Google's `email_verified` claim is checked** alongside the `APP_ALLOWED_EMAILS`
  allowlist. An unverified email claim is not an identity.
- **`lastContactAt` added to `applications`.** `updatedAt` (`@LastModifiedDate`) bumps on
  any write, so fixing a typo in `notes` would reset it — which breaks "which companies
  haven't I heard back from in 2+ weeks?", one of the four headline MCP queries. The
  service sets `lastContactAt` only when a stage is added or a stage's status/dates change.
- **Terminal application statuses are sticky.** Status is derived from `stages[]` *and*
  settable explicitly; without this rule, marking something `WITHDRAWN` and then editing an
  old stage's note would recompute it back to `ACTIVE`. Recompute only when the current
  status is `ACTIVE` or `OFFER`. `GHOSTED` is **manual-only** — no rule derives it.
- **`currentStageType` uses the *lowest* pending sequence, not the latest.** The earlier
  rule ("latest stage that is `SCHEDULED` or `EXPECTED`") returns a round you haven't
  reached yet when a later stage is already pencilled in as `EXPECTED`.
- **Search uses a case-insensitive regex, not a `$text` index.** `$text` matches whole
  stemmed tokens, so typing `strip` in the filter bar would not find Stripe and `eng`
  would not find Engineer — unusable for the SPA's free-text filter. At hundreds of
  documents a regex over `companyName` / `role` / `notes` is fast enough, and it frees the
  one-text-index-per-collection slot. Atlas Search (available on M0, 3-index limit) is the
  upgrade path if this ever stops scaling.
- **`@Future` dropped from `scheduledAt`.** Phase 4 ends with backfilling the job search
  already in progress — applications already sent, interviews already held. `@Future` would
  reject exactly that data. The only rule kept is `scheduledAt` non-null when
  `status = SCHEDULED`.
- **`CompanyService.delete` blocks with 409** when applications reference the company
  (previously left open in `PLAN.md`). Cascading would silently destroy application
  history; the error message names the referencing applications.
- **G1, not ZGC, on the VPS.** The unit previously specified `-XX:+UseZGC` with `-Xmx2g`.
  ZGC buys low pause times on large heaps at a footprint and CPU cost that a 2-OCPU box
  with a 2 GB heap cannot justify; G1 is the default and the right one here. Use
  `-XX:MaxRAMPercentage=50` rather than a hardcoded `-Xmx` so the unit survives a resize.
- **`CorsConfig` deleted from the planned layout.** Prod is same-origin behind Nginx and
  dev goes through the Vite proxy, so CORS is never exercised. It was dead code.

### 2026-09-01 — Host changed to the AMD micro shape

- **`VM.Standard.E2.1.Micro` (1/8 OCPU burstable to 1, 1 GB RAM, x86) instead of Ampere A1
  (2 OCPU / 12 GB).** Not a preference — A1 capacity was unobtainable in the home region,
  and Always Free resources exist only in the home region, so waiting or switching region
  were the alternatives. Two E2 micros are always free and are reliably available.
  Accepted trade-offs: a tuned JVM, mandatory swap, and no room for the Datadog Agent on
  the app host. Rejected: paying ~€4/mo for a Hetzner box (keeps the project genuinely
  free), and retrying A1 indefinitely (the job search has its own clock).
- **JVM sizing changes with it.** `-XX:MaxRAMPercentage=50` would hand a 1 GB box a 512 MB
  heap, which leaves nothing for metaspace, thread stacks, code cache, Nginx and the OS.
  Explicit `-Xmx256m` plus a capped metaspace instead. The collector is **SerialGC**, which
  the JVM already selects on a 1-core sub-2 GB machine — G1 is the wrong choice at this
  size, so the earlier "G1, not ZGC" entry now reads "SerialGC, and let ergonomics pick it".
- **4 GB swap, with `vm.swappiness=10`.** Cheap on a 50 GB volume and it comfortably
  covers `apt` operations and spikes. But be clear about what it buys: swap is a **safety
  margin, not capacity**. It does not make a 1 GB box behave like a 5 GB one; it makes it
  survive a spike slowly instead of dying. The boot volume is network-attached, so paged-out
  memory costs milliseconds per access, and a GC that has to page a swapped heap back in can
  freeze the app for seconds. Size `-Xmx` so the steady state is entirely RAM-resident and
  swap is only ever touched under a spike.
- **Cap the service with systemd rather than relying on swap absorbing a runaway.** Set
  `MemoryHigh` / `MemoryMax` on `jobtracker.service`. With 4 GB of swap and no cap, a leak
  thrashes for a long time and takes the whole box — including SSH — down with it. With a
  cap, the JVM alone is killed and `Restart=on-failure` brings it back. A fast restart is a
  better failure mode than an unreachable host.
- **The Datadog Agent no longer runs on the app host.** This reverses the "Agent stays
  permanently" part of the student-pack entry above: ~0.5 GB RSS does not fit beside a JVM
  in 1 GB. Custom metrics are unaffected — Micrometer pushes to the Datadog API over HTTPS
  and never needed an agent. What is lost is host infra metrics for the app box.
- **APM, if it happens, uses the second free micro.** *(**Superseded same day** — only one
  instance is actually available to this tenancy. See the entry below.)*

### 2026-09-01 — Only one instance; APM moves off the deployed host

Written after discovering the tenancy has one usable instance, not the two the Always Free
description implies. This supersedes the "APM uses the second free micro" bullet above.

- **No Datadog Agent anywhere in production, and therefore no production APM traces.**
  `dd-trace-java` can only send traces to an Agent, the Agent needs ~0.5 GB, and the single
  1 GB host is already carrying a JVM at ~500 MB plus Nginx and the OS. There is nowhere
  left to put it. Running both on the app host would push it into steady-state swapping on
  a network-attached boot volume — the app would be visibly broken to make a screenshot.
- **APM is exercised locally instead.** During the 14-day trial: run the app plus a Datadog
  Agent on the laptop, drive it with the `.http` collection, capture the flame graphs and
  the service map. This is a real, working `dd-trace-java` setup and gives genuine interview
  material about instrumentation and trace sampling — it is simply not the deployed
  instance, and the README says so. *(**Superseded** by the 2026-09-02 entry "Tracing dropped" — there is no APM in this project at all.)*
  The premise here — that a 14-day APM trial exists to be spent — turned out to be false;
  see 2026-09-02.
- **The claims were narrowed to match.** §1's deliverable and the resume bullet now say
  "metrics, dashboards and alerting" for production and name APM separately as local. This
  is the honest version: a reviewer who asks "so what does your APM show in prod?" gets a
  straight answer instead of a walk-back.
- **What production actually keeps:** custom metrics via Micrometer straight to the Datadog
  API (never needed an agent), the dashboard, and the error-rate monitor. Those run
  continuously and are what the dashboard screenshot shows. Host infra metrics for the box
  are the casualty of having no Agent.
- **Rejected:** paying for a bigger shape for two weeks (defeats the free-tier point for a
  screenshot); running the Agent on the app host anyway (breaks the running app);
  dropping Datadog entirely (metrics, dashboard and monitor all still work and are the
  larger part of the observability story).

### 2026-09-01 — Backend scaffolded

- **Spring Boot 4.1.1, not 4.0.x.** Initializr's current line. §3 updated. No downside
  found; springdoc 3.1.0 and Testcontainers 2.0.5 both resolve against it.
- **Coordinates:** group `dev.duynguyen`, artifact `jobtracker`, package
  `dev.duynguyen.jobtracker`, main class `JobTrackerApplication`. Initializr defaulted to
  `com.leduynguyen` / `backend`; renamed to match §5 before any real code existed.
- **The Datadog registry must be `enabled: false` by default.** It auto-configures on
  classpath presence alone and fails context creation with `apiKey was 'null' but it is
  required`. Since the dependency is added in Phase 1 but not configured until Phase 5,
  without this default *every* `@SpringBootTest` fails for four phases. `application.yml`
  sets `false`; `application-prod.yml` sets `true`.
- **Jackson 3 moved `WRITE_DATES_AS_TIMESTAMPS`** from `SerializationFeature` to
  `DateTimeFeature`, so the Boot 3 property `spring.jackson.serialization.write-dates-as-
  timestamps` fails the context load. The Boot 4 key is
  `spring.jackson.datatype.datetime.write-dates-as-timestamps`. (Jackson 3 already
  defaults to ISO-8601; we set it explicitly anyway.)
- **Testcontainers 2.x renamed its modules** — `junit-jupiter` → `testcontainers-junit-
  jupiter`, `mongodb` → `testcontainers-mongodb` — and the Boot parent does not manage
  their versions, so `testcontainers-bom` is imported explicitly.
- **`LocalDate` storage must be pinned to UTC with explicit converters.** Spring Data
  converts `LocalDate` to a BSON date using the **JVM's default timezone**, so the same
  `appliedDate` is stored as a different instant depending on where the process runs — and
  every "days to first response" or date-range calculation then differs between a dev
  laptop and the UTC VPS, from identical data. `config/MongoConfig` registers
  reading/writing converters fixed to UTC midnight. Found by `StatsServiceIT`, which
  produced 5.1 average days locally against a hand-derived 5.4.
- **MongoDB connection properties moved to `spring.mongodb.*` in Boot 4.** The Boot 3
  prefix `spring.data.mongodb.*` is deprecated at level **`error`** since 4.0.0, which
  means it is *not bound at all* — it does not warn, it silently falls back to the default
  `mongodb://localhost/test`. Found because `IndexInitializer` reported creating all nine
  indexes while `jobtracker.companies` did not exist: they had gone into a `test` database.
  In prod this would have pointed the app at a `test` database on Atlas. The whole family
  moved (`uri`, `database`, `host`, `port`, `username`, `password`, `ssl.*`,
  `replica-set-name`, …). **Exception:** `spring.data.mongodb.auto-index-creation` stays
  where it is — it is a Spring Data concern, not a connection one.
- **JDK 25 is installed (Homebrew `openjdk@25`, 25.0.4.1) and IntelliJ already uses it**
  (SDK `homebrew-25`, language level 25). It is *not* symlinked into
  `/Library/Java/JavaVirtualMachines`, so `/usr/libexec/java_home -V` does not list it —
  which is misleading. The **shell** still defaults to JDK 26, so `./mvnw` from a terminal
  cross-compiles with `--release 25` while IntelliJ compiles on 25. Set
  `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home` in
  `~/.zshrc` so both agree.

### 2026-09-01 — The three read queries

Written while building follow-ups, upcoming interviews and free-text search — the last of
the Phase 1 service layer.

- **The read queries live in `application/ApplicationQueryService`, not `ApplicationService`.**
  `ApplicationService` owns the write path and the four derived fields — the invariants that
  are easy to break and where every line is load-bearing. These three are pure reads against
  `MongoTemplate` that mutate nothing. Splitting them keeps the class holding the tricky
  rules from acquiring a second, unrelated job, and mirrors `StatsService`, which was already
  separate for the same reason. Rejected: putting them on the repository (they need
  `MongoTemplate`, escaping and boundary math, none of which belongs in a derived query).
- **`lastContactAt` is now seeded on *every* create, not only when the service generates the
  submission stage.** A real bug, found by writing the gone-quiet query rather than by a
  test. The query matches `lastContactAt: { $lte: now − 14 days }` and `null` is not `$lte`
  anything — so an application created with its `stages[]` supplied, which skips
  `seedSubmissionStage`, would have been permanently invisible to it. That is exactly the
  shape of the Phase 4 backfill, so the whole historical job search would have been missing
  from the one query it matters most for, silently, in the data hardest to eyeball.
  Seeded from the furthest-forward stage date, falling back to `appliedDate` at local
  midnight. `SCHEMA.md §1` records the rule.
- **Upcoming interviews excludes terminal applications.** `SCHEMA.md §10.4` did not say so;
  it does now. A round left sitting at `SCHEDULED` on an application you withdrew from is
  stale data, not an appointment, and a calendar view that shows it is worse than one that
  does not.
- **`days` on upcoming interviews is capped at 365.** The endpoint answers "what is coming
  up"; an uncapped window invites scanning the entire future for nothing.
- **Follow-ups returns two named lists rather than one tagged list, and does not deduplicate
  them.** "You said to chase this today" and "this has gone silent for a fortnight" prompt
  different actions and the MCP tool renders them under different headings. An application in
  both is the most urgent row in the response, not a duplicate to be collapsed. The response
  also echoes the thresholds (7 and 14 days) so callers quote them instead of hardcoding
  them.
- **The gone-quiet half matches `status = ACTIVE` exactly, not merely non-terminal.** An
  `OFFER` you have not answered is silence of your own making.
- **Search input goes through `Pattern.quote`, and there is a test proving `.*` matches
  nothing.** The escaping was already decided; what was not obvious is that the interesting
  failure is not the crash on `(`. It is that an unescaped `.*` quietly returns the entire
  collection — a wrong answer with no error attached. Both are pinned in
  `ApplicationSearchIT`.

### 2026-09-01 — Testcontainers: one container per JVM, not per test class

Found the moment a second `*IT` class was added, which is the earliest it could possibly have
been found — and it presented as `Connection refused` in *every* IT except the first,
including `StatsServiceIT`, which had been green for days.

- **`AbstractMongoIT` starts its container in a static initializer, with neither
  `@Testcontainers` nor `@Container`.** That annotation pair binds the container's lifecycle
  to a *test class*: it stops the container when the class finishes. With one IT class that
  is invisible. With four, the first class's teardown stops the container while Spring's
  cached application context still holds the old port, and every later class dies against a
  port nothing is listening on. A static initializer gives one container for the whole JVM
  run — matching the lifetime of the context Spring is already caching — and Ryuk removes it
  when the JVM exits. This is Testcontainers' documented singleton-container pattern.
- **The lesson generalises past this bug:** a green suite with a single integration-test class
  says nothing about container lifecycle. The failure mode is not a wrong assertion, it is
  infrastructure disappearing between classes, and it appears all at once.
- **Import `org.testcontainers.mongodb.MongoDBContainer`.** Testcontainers 2.x ships *both*
  that and `org.testcontainers.containers.MongoDBContainer`, the 1.x-compatible shim, and
  both compile. Every snippet online uses the old one.

### 2026-09-01 — Controllers, error handling, and the end of Phase 1's code

- **`GlobalExceptionHandler` is a plain `@RestControllerAdvice`, not a subclass of
  `ResponseEntityExceptionHandler`.** The base class already handles several of these
  exceptions and quietly wins over any method that does not match the exact signature it
  expects to override — a subtle way to have a handler that never runs. A plain advice makes
  the mapping explicit: the list of `@ExceptionHandler` annotations *is* the API's error
  contract. Every response is RFC 7807 `application/problem+json`; validation failures attach
  an `errors` array naming every bad field, not just the first.
- **Jackson's message is passed through on a bad enum value.** For `"source":
  "CARRIER_PIGEON"` it reads "not one of the values accepted for Enum class: [REFERRAL,
  COLD_APPLY, ...]" — it names every legal value, which is exactly what a caller needs and
  what the MCP server will surface. The cost is exposing DTO type names; for a single-user
  API whose entire schema is published in `SCHEMA.md`, that is not a secret. The "at
  [Source: ...]" tail is trimmed off, since it helps nobody.
- **Search returns `PagedModel`, not `Page`.** Serializing a `PageImpl` straight out emits
  Spring Data's internal structure, which it explicitly warns is not a stable contract and
  logs a warning about. `PagedModel` is the supported wrapper and gives
  `{content, page:{size, number, totalElements, totalPages}}` — a shape the SPA's
  `types.ts` and the MCP client can mirror without being broken by a Spring Data upgrade.
- **All three stage operations return the whole updated application, not the stage.** A stage
  mutation also moves `currentStageType`, sometimes `status`, and usually `lastContactAt`.
  Returning just the stage would leave every caller holding a stale parent and needing a
  second GET to discover what else changed.
- **Route ordering is not load-bearing, contrary to appearances.** `/api/applications/
  followups` and `/interviews` sit where `/{id}` could also match. Spring's
  `PathPatternParser` scores a literal segment above a variable one regardless of declaration
  order, so they resolve correctly wherever they appear in the file. Worth knowing because the
  older `AntPathMatcher` did not, and every "declare the specific route first" answer online
  is about that. `ApplicationControllerIT` pins it either way.
- **`days` and `from`/`to` on `/api/stats` stay mutually exclusive at the controller
  boundary** — the service raises the 400. Verified end to end rather than assumed.

### 2026-09-01 — MockMvc needs its own starter on Boot 4

- **`spring-boot-starter-webmvc-test` must be added explicitly**, and
  `@AutoConfigureMockMvc` now lives in `org.springframework.boot.webmvc.test.autoconfigure`,
  not `org.springframework.boot.test.autoconfigure.web.servlet`. Boot 4 split the test slices
  into per-technology modules, and `spring-boot-starter-test` no longer carries the web one.
  The failure is a plain "package does not exist", and every Boot 3 answer says to use the
  starter you already have — so the natural next move is to doubt the import rather than the
  dependency list. Same family of trap as the Testcontainers 2.x module rename.

### 2026-09-01 — Atlas verified end to end, and two traps on the way there

Phase 1's last open item, done: the app was pointed at the real M0 cluster, exercised, and
pointed back at local Mongo. Confirmed against Atlas: connection to the 3-node replica set
(1 primary, 2 secondaries), all 9 indexes created, a create/read round trip, the `$facet`
stats aggregation, and regex search. `appliedDate` written as `2026-08-15` came back as
`2026-08-15`, stored at `04:00Z` — midnight Eastern — which is the `MongoConfig` UTC
converter doing its job against a real cluster rather than a container. Test data removed;
the cluster is empty.

- **Atlas's connection string has no database name in it.** The "Connect" dialog gives you
  `mongodb+srv://user:pass@cluster.xxxxx.mongodb.net/?retryWrites=true&w=majority` — note
  `/?`, with nothing between the slash and the question mark. Pasted as-is the app dies at
  startup with `IllegalArgumentException: Database name must not be empty`. `jobtracker`
  goes in that gap. Worth contrasting with the `spring.data.mongodb.*` trap already in this
  log: that one failed *silently* into a database called `test`, this one fails loudly. The
  loud failure is the better bug, and it only exists because the prefix is right.
- **A commented-out YAML block must be commented with `#` and no space** when the reader is
  expected to uncomment it. `# mongodb:` uncommented by deleting only the `#` leaves a
  leading space, which shifts the whole document to indent 1; the next key at column 0 then
  reads as a second document and SnakeYAML reports `expected '<document start>', but found
  '<block mapping start>'` **against that later line**, which looks like a duplicate-key
  error somewhere else entirely. The template now uses `#mongodb:` at the correct
  indentation, so "delete the `#`" is unambiguous and correct, and the `.example` is checked
  by actually uncommenting it and parsing the result.

### 2026-09-01 — Phase 2: authentication

Two chains, as decided in the pre-build review. Verified against the running app with the
real token, not only in tests: unauthenticated `/api` is 401 `problem+json` with **no**
`Location` header, the `XSRF-TOKEN` cookie is present on that same rejected response, the
bearer token reads and cannot write (403 on POST and DELETE), a wrong or empty token is 401,
and `/oauth2/authorization/google` redirects to Google with
`redirect_uri=http://localhost:5173/login/oauth2/code/google` and scope `openid profile email`.

- **`AllowlistOidcUserService`, not `AllowlistOAuth2UserService`.** §5's layout planned the
  latter before any code existed. Google's `openid` scope makes this an OIDC login, so the
  extension point is `OidcUserService` and the class is named for what it actually is. §5
  corrected rather than the code bent to match a guess made earlier.
- **The allowlist check is split out of `loadUser` into `verify(OidcUser)`.**
  `super.loadUser` calls Google's userinfo endpoint, so the method containing the rule was
  untestable without a network. Splitting it is what turns "an unverified email is rejected"
  from a comment into an assertion.
- **An empty allowlist admits nobody, and an empty `app.mcp-token` authenticates nobody.**
  Both fail closed, and both have a test. The failure mode being guarded is a deploy that
  forgets the environment variable: locking the owner out is recoverable, admitting the
  internet is not.
- **The bearer comparison hashes both sides before comparing.** `MessageDigest.isEqual` does
  not short-circuit on content, but it does compare lengths first, which leaks the token's
  length. Hashing to a fixed 32 bytes removes that, and costs nothing at one request at a
  time.
- **Swagger is permitted only when the `local` profile is active**, in addition to being
  disabled outright in prod by `springdoc.api-docs.enabled=false`. Two independent reasons,
  deliberately: the property is one line from being flipped back by someone debugging
  production, and publishing the whole API surface is a large consequence for a small
  mistake.
- **`server.servlet.session.cookie.secure` is set in `application-prod.yml`, not
  `application.yml`.** A secure cookie is never sent back over plain http, so setting it
  globally would break local login while looking like a session bug.
- **Every Phase 1 IT now runs `@WithMockUser` with `.with(csrf())` on writes.** The
  alternative — permitting the test paths in the security config — would mean the tests no
  longer exercise the configuration that ships.

### 2026-09-01 — A test that only failed after 9pm

`ApplicationControllerIT.followups` asserted `daysOverdue == 0` for a follow-up dated
`LocalDate.now()`. It passed all day and failed at 21:00 Pacific, because `app.timezone` is
`America/New_York`: at 21:00 PDT it is already tomorrow in New York, the service computed
`today()` a day ahead of the test's `LocalDate.now()`, and the assertion was off by exactly
one.

- **A test asserting on a relative-date boundary must use the same clock the service uses.**
  The controller ITs now inject `TimeService` and call `today()`. This is the hazard
  `SCHEMA.md §7` describes, arriving from the direction nobody watches — not in the
  production code, which was right, but in the test written to check it.
- Worth keeping in mind for CI: a GitHub runner is UTC, which is a *third* zone. The fix
  makes the tests zone-independent rather than merely correct in one place.

### 2026-09-01 — Phase 3: the React SPA, scaffolded in one pass

The whole frontend was written in a single session against the running local API. It builds
(`tsc -b && vite build`) and lints (`oxlint`) clean, and every read endpoint was curled
through the Vite proxy to confirm `src/api/types.ts` matches the wire shapes. It has **not**
been driven in a browser yet — that, and the Google login round trip, are the open Phase 3
items (`STATE.md`).

- **Toolchain landed newer than §3 anticipated:** Vite **8**, React **19**, TypeScript
  **6**, from `npm create vite@latest -- --template react-ts`. §3 was written expecting
  roughly Vite 5 / React 18. No code impact — the patterns (hooks, JSX, the proxy) are
  unchanged — so §3's table is left as-is rather than chased version by version. `zod` is
  **v4**, `react-router-dom` **v7**, `@tanstack/react-query` **v5**.
- **One `fetch` wrapper, `src/api/client.ts`, and nothing else calls the network** (§11).
  It attaches `credentials: "include"`, sets `X-XSRF-TOKEN` from the `XSRF-TOKEN` cookie on
  mutating verbs only, parses RFC 7807 bodies into a typed `ApiError` (carrying `detail` and
  the `errors[]` field list), and on a 401 sends the browser to
  `/oauth2/authorization/google`.
- **`useMe()` opts out of the 401 redirect**, every other call keeps it. A signed-out
  visitor must land on a page with a "Sign in" link, not be bounced to Google before the
  shell renders; a session that lapses *mid-use* should bounce. One boolean option on the
  client (`redirectOnUnauthorized`, default true) expresses both.
- **Mutations invalidate across query families, not just their own.** A stage edit can move
  `status` / `currentStageType` / `lastContactAt`, which feed stats, follow-ups and
  upcoming-interviews. Rather than reason about which moved, the applications hooks
  invalidate all four families (`applications` list, `stats`, `followups`, `interviews`) on
  every write. They refetch lazily, only if mounted.
- **Enum arrays are re-declared in `src/lib/enums.ts` in SCHEMA.md §5 order** — `StageType`
  especially, since the funnel renders in that order. This is a third copy of the list
  (Java enum, `SCHEMA.md §5`, now TS); the same "keep the three in sync" note applies, now
  four.
- **zod schemas are written over the *form's* shape** (every text field a string, `""` =
  not filled), with an explicit `toRequest()` mapper turning blanks into `null`. Trying to
  make the schema mirror the DTO directly fights react-hook-form's "" defaults and the
  optional-vs-null distinction. The mapper is where "" → `null` and comma-strings → arrays
  happen, in one greppable place per form.
- **Stages are absent from `ApplicationForm`.** They are managed only through the inline
  timeline on the detail page (`POST`/`PATCH`/`DELETE .../stages`), matching the backend's
  "exactly one code path maintains the derived fields" rule. The create form relies on the
  service seeding `APPLICATION_SUBMITTED`.
- **Plain CSS, one `theme.css` of custom properties.** No CSS framework, no CSS-in-JS —
  component styling is a mix of utility classes (`.card`, `.stack`, `.row`, `.badge`) and
  inline `style={{…}}` for one-offs. Deliberately unsophisticated (§3, §12).
- **Not built, not in the Phase 3 checklist:** logout (no backend endpoint yet either —
  `STATE.md`), a toast system (errors render inline), a real mobile layout.

### 2026-09-01 — Phase 3 continued: login round trip, logout, and the shell

Verified Google login end to end in a browser (the Phase 2 acceptance criterion that was
still owed), fixed what that surfaced, and reshaped the frontend around the owner's
feedback. Merged to `main` with the UI explicitly a first pass.

- **OAuth success redirect must be absolute, built from `app.base-url`.** `oauth2Login()`
  had `.defaultSuccessUrl("/", true)`. Root-relative `/`, behind the Vite dev proxy,
  resolves against the backend (`:8080`) — the browser landed on the bare API, hit
  `anyRequest().denyAll()`, and got a naked 403 that read as a login failure when login had
  in fact succeeded. Replaced with a `SimpleUrlAuthenticationSuccessHandler` pointed at
  `appProperties.getBaseUrl() + "/"` (`http://localhost:5173/` local, the real origin in
  prod — correct in both), `alwaysUseDefaultTargetUrl(true)` since an SPA has no meaningful
  "page you were on". Added to `STATE.md`'s trap table.
- **`accessDeniedHandler` added to the browser chain.** It had only `authenticationEntryPoint`.
  An authenticated-but-forbidden request therefore fell through to Tomcat's blank 403 page
  instead of `problem+json`. The bearer chain always wired both; this was an oversight.
- **Logout: `POST /api/logout` → 204.** Stock Spring Security `logout()` with a
  `HttpStatusReturningLogoutSuccessHandler(NO_CONTENT)` and `deleteCookies("JSESSIONID")`.
  Under `/api/` so it rides the existing dev proxy and the SPA's api client (`base = /api`)
  with no new wiring; the `LogoutFilter` runs ahead of authorization so it needs no
  `permitAll`. CSRF-protected like any write — the api client already sends `X-XSRF-TOKEN`
  on mutating verbs. `useLogout()` clears the React Query cache and hard-navigates to `/`.
- **Logout tests live in their own `LogoutIT`, not in `SecurityIT`.** Adding two
  `.with(csrf())` methods to `SecurityIT` perturbed the implicit ordering that
  `csrfCookieIsNotDeferred` depends on ("the first request in the run emits a fresh
  XSRF-TOKEN cookie") and it began failing with "No cookie". Both are stable apart. Noted
  in `STATE.md`.
- **`<App>` is now an auth gate**, not just a router: `useMe()` runs once; while it is in
  flight a splash shows; if there is no signed-in person a dedicated `Landing` page renders
  and **the router never mounts** — so a signed-out visitor is not bounced to Google before
  seeing anything, which is what happened when every dashboard query fired a 401. `useMe()`
  keeps `redirectOnUnauthorized: false`; every other call still redirects on a mid-session 401.
- **The shell is a collapsible left sidebar**, replacing the top nav. Brand, icon nav, and
  a designed profile block (avatar / name / email / Sign out) pinned to the foot. Collapse
  state is remembered in `localStorage` (`jt:sidebar-collapsed`, try/catch-guarded);
  collapsed it is a 68px icon rail with hover-label tooltips. Below 860px it is a top strip
  and the collapse toggle is hidden.
- **Design direction: "warm editorial".** After the owner rejected both the initial light
  indigo ("too bright") and a dark-glass pass ("too dark, generic"), the theme settled on a
  paper-tone ground, **Fraunces** (serif) for headings and the wordmark, Inter for UI,
  JetBrains Mono for numbers / badges / eyebrow labels, and a single deep pine-green accent
  with a mint gradient. Hover motion throughout (row tint + inset rule, card lift, nav
  slide, link underline wipe). The owner has **explicitly deferred further UI work** — this
  is a functional first pass, not the finished look. `theme.css` is the single source; no
  framework, ~450 lines.
- **`index.html`** loads Fraunces + Inter + JetBrains Mono from Google Fonts; new favicon
  (pine funnel mark). `color-scheme: light`.

### 2026-09-02 — Pre-deploy security pass

A full read of the backend and frontend before Phase 4, looking for security flaws rather
than bugs. The two-chain auth model, the constant-time token comparison, the allowlist,
CSRF wiring, regex escaping and the absence of any XSS sink all held up unchanged; git
history was scanned across every ref and carries only placeholders. Six changes came out
of it. Recorded here because each is a rule, not a fix.

- **URL fields are scheme-restricted, at both ends.** `jobPostingUrl` and `company.website`
  were `@Size`-only and were rendered straight into `<a href>`. React escapes text content
  but **not** href attributes, so a stored `javascript:alert(1)` was a link that ran script
  in the app's own origin — stored XSS whose only mitigation was that the app has one
  writer. Backend: `common/Validation.HTTP_URL` on all four DTO fields. Frontend:
  `lib/url.ts` parses with `new URL()` (not a regex — it normalises `JaVaScRiPt:`, leading
  control characters and whitespace before the protocol is read) and `components/SafeLink`
  is now the only place a stored URL becomes a link. Two independent checks deliberately:
  the constraint cannot retroactively clean rows written before it existed.
- **`app.mcp-token` no longer has a working default on the `local` profile.** It was
  `${APP_MCP_TOKEN:local-dev-token-change-me}` — a read token for the entire API, published
  in a public repo, live for anyone who ever boots prod with the wrong profile active. Now
  `${APP_MCP_TOKEN:}`, which `BearerTokenFilter`'s `isBlank()` guard turns into "nobody
  authenticates". Fails closed, matching what `AppProperties` already argued for the
  allowlist. Prod was already correct (no default at all).
- **`@Valid` moved onto the type argument** — `List<@Valid ContactRequest>`, not
  `@Valid List<ContactRequest>`. Both cascade today, but Hibernate Validator deprecated the
  container form (HV000271, warned on every startup). If support is dropped, nested contact
  and stage validation stops running **silently** — no error, just unvalidated input. The
  warnings are gone from the build, which is how the fix was confirmed.
- **Collections are bounded and so is page size.** `stages` ≤ 50, `contacts` /
  `interviewers` / `tags` ≤ 20, and `config/WebConfig` caps the pageable resolver at 100
  (Spring Data's default max is **2000** — `@PageableDefault(size = 20)` sets the default,
  it does not cap what a caller may ask for). Self-inflicted only, since there is one
  writer, but `?size=2000` against `-Xmx256m` on a 1 GB box is a one-line OOM.
- **`deploy/nginx-jobtracker.conf` + `deploy/jobtracker-proxy.conf` written early.** Two
  things could not wait for Phase 4. Rate limiting (`limit_req_zone` for `/api` at 10r/s,
  `/oauth2` and `/login` at 1r/s, plus `limit_conn` and a 256k body cap): a flood does not
  need to authenticate to take down a 1/8-OCPU box. And `X-Forwarded-Proto`, without which
  `request.isSecure()` is false behind Nginx and Spring builds the OAuth `redirect_uri` as
  `http://` — Google then rejects it and **login fails outright**, not degrades. The proxy
  headers live in one snippet because three near-identical `location` blocks is how they
  drift.
- **`server.forward-headers-strategy: framework` was already set in `application-prod.yml`.**
  Flagged as a suspected gap and found to be a false alarm. Noted so it is not "fixed" again.
- **`/actuator/health` is permitted on loopback only.** It has to stay unauthenticated —
  the deploy smoke check curls it with no credential — but it does not have to be reachable
  from the internet, and the difference matters because the response is only harmless while
  two properties stay right. `application.yml` already sets
  `management.endpoints.web.exposure.include: health` and
  `management.endpoint.health.show-details: never` (both verified), which is why this was
  filed as info rather than a hole. But `show-details: always` is one word and a plausible
  thing to type while debugging a failing probe, and it turns the same public URL into the
  Mongo driver's wire version, the JAR's path on disk, and the volume's free space.
  `SecurityConfig.LOOPBACK_HEALTH` scopes the `permitAll` so that mistake stops being a
  disclosure — the same two-independent-reasons shape already used for Swagger. Defence in
  depth on top of "8080 is not publicly reachable", **not** a replacement for it: anyone
  reaching 8080 directly could spoof `X-Forwarded-For`. `SecurityIT.publicEndpoints` asserts
  both directions, and was confirmed to fail (`expected:<401> but was:<200>`) with the rule
  reverted.

**Still open, deliberately not done in this pass:** *(**Resolved** — see the entry below.)*
the SPA's static assets get no response
security headers. Spring Security sets `nosniff` / `X-Frame-Options: DENY` / HSTS on
`/api`, `/oauth2` and `/login`, but `index.html` and the JS bundle are served by Nginx and
get none, so the app is framable. A CSP must allow `fonts.googleapis.com` and
`fonts.gstatic.com` (`index.html` loads Google Fonts). The vhost says so in a comment.

### 2026-09-02 — Security headers on the static assets, and the nginx config actually run

Closes the one item the security pass above left open, and stops `deploy/` being a set of
files nobody had ever executed.

- **Response security headers now cover the static half of the site**, in
  `deploy/jobtracker-security-headers.conf`: HSTS, `nosniff`, `Referrer-Policy`,
  `Permissions-Policy`, `X-Frame-Options: DENY` and a CSP. Spring Security already sets its
  own on `/api`, `/oauth2` and `/login`, so the snippet is included **only** in the static
  locations — putting it at server level would double every header on the API responses, and
  two `Content-Security-Policy` headers are enforced as their *intersection*, which is a
  miserable thing to debug later.
- **`add_header` does not merge, and that shaped the file.** A location block containing any
  `add_header` of its own silently discards every `add_header` inherited from its parent —
  no warning, no error. `/assets/` and `= /index.html` both set `Cache-Control`, so they
  would have lost the entire security set had it been declared once at server level. The
  include is therefore repeated in all three static locations. **Do not "tidy" it by
  hoisting it.**
- **The CSP's shape is dictated by what the app actually does.** `script-src 'self'` with no
  `'unsafe-inline'` is achievable because the Vite build emits an external module bundle and
  zero inline `<script>` blocks (checked against `dist/index.html`). `style-src` **does**
  need `'unsafe-inline'`, and that is an accepted gap rather than an unnoticed one: the
  components carry 74 inline `style={{…}}` attributes and `style-src` governs style
  attributes as well as `<style>` blocks, so removing it means moving all 74 into
  `theme.css` first. Inline style injection is a far weaker vector than script.
  `fonts.googleapis.com` / `fonts.gstatic.com` are there for the webfonts `index.html`
  links, and `*.googleusercontent.com` for the Google profile picture in the sidebar avatar.
- **`expires 1y` and `add_header Cache-Control` together emit the header twice.** `expires`
  generates its own `Cache-Control` and `add_header` appends rather than replaces, so
  `/assets/` was returning both `max-age=31536000` and `public, immutable` as separate
  headers. Found by reading real responses, not by reading the file. Now one `add_header`
  and no `expires`.
- **The nginx config was run, not just written.** `nginx:alpine` in Docker with a throwaway
  self-signed cert: `nginx -t` passes, all six headers are present on `/`, `/index.html`
  **and** `/assets/app.js` (which is the proof the inheritance handling works), and 30 rapid
  requests to `/api` produce 21 pass-throughs followed by 429s — `burst=20` behaving exactly
  as configured. Worth repeating on the VPS after certbot rewrites the TLS block.

### 2026-09-02 — APM is not offerable on the student Pro plan

The Phase 0 item flagged as "the one with no recovery path if discovered late" was checked,
and the answer is no. Recorded here because `PLAN.md` Phase 0 requires it, and because the
reasoning matters more than the fact.

- **What was checked:** account is redeemed and shows **Pro** via the GitHub Student
  Developer Pack, API key generated, DD site noted. Under *Plan & Usage*, **APM offers no
  trial affordance.**
- **Why, and why it was predictable.** APM is a separate paid SKU (~$31/host/mo) and is not
  bundled into Pro on any plan — already recorded on 2026-09-01. The 14-day trial the
  earlier entries assumed is the *new-account evaluation* trial; redeeming the student Pro
  offer takes the account onto Pro and that evaluation window is no longer on offer. So the
  two decisions were individually right and their combination was never checked, which is
  exactly the thing this Phase 0 item existed to catch.
- **Blast radius: nil for anything already claimed.** §1's deliverable 2 and the resume
  bullet were narrowed on 2026-09-01 to say "metrics, dashboards and alerting" for
  production and to name APM separately as a local exercise. Nothing anywhere promises
  production APM. What is lost is only the *local* flame-graph screenshots and the
  `dd-trace-java` instrumentation story.
- **What production keeps, unchanged:** custom metrics pushed by Micrometer straight to the
  Datadog API over HTTPS (never needed an Agent), the dashboard, and the error-rate monitor.
  ~13-month retention on Pro, which is what made the "applications over time" widget worth
  building. Phase 5 is otherwise unaffected.
- **Open:** whether to drop tracing from the project entirely or exercise it locally through
  OpenTelemetry instead. Not decided here — see the next entry when it is.

**Interview note, worth keeping.** "Why is there no APM in production?" now has a better
answer than a screenshot would have been: a 1 GB host, an Agent that needs ~0.5 GB beside a
JVM already at ~500 MB, one instance in the tenancy, and a tracing SKU that is separately
billed. That is a capacity-and-cost tradeoff explained from first principles, which is
stronger material than a flame graph captured on a laptop.

### 2026-09-02 — Tracing dropped from the project (Option A)

Follows directly from the entry above. With no APM trial offerable, the choice was: drop
tracing, stand up a second Datadog org on its own evaluation trial, or exercise tracing
locally through OpenTelemetry to a Jaeger/Tempo container. **Dropped.**

- **Nothing that was claimed had to change**, which is what made this the cheap option.
  §1's deliverable and the resume bullet were narrowed on 2026-09-01 to say "metrics,
  dashboards and alerting" for production, with APM named separately as local-only. Removing
  the local half left every outward claim exactly as it was.
- **Rejected: a second Datadog org on a fresh evaluation trial.** It would have worked, and
  it means two orgs and two API keys, one of which is production's. The realistic failure is
  pointing the local Agent at the prod key, or the reverse — a live credential mishandled for
  the sake of screenshots. Not a good trade.
- **Rejected: OpenTelemetry + a local collector.** Genuinely appealing: no trial clock, real
  flame graphs, and arguably the more transferable skill. Declined because it adds a tool and
  a container to a project whose §12 premise is that every dependency is something the owner
  must learn to debug, and whose §14 has already turned down larger things for the same
  reason. **This is the one to revisit** if distributed tracing is ever wanted on the CV —
  it is the right approach, it just is not this project's job.
- **What the observability deliverable is now, in full:** custom metrics pushed by Micrometer
  straight to the Datadog API, a dashboard, and one error-rate monitor — against the deployed
  instance, at ~13-month retention on student Pro. No Agent anywhere, in production or
  locally. Phase 5 loses its APM subsection and its wind-down step and is otherwise unchanged.
- **The custom-metric budget still binds.** ~100 timeseries per host, and Micrometer expands
  one timer into several metrics — that constraint came from Pro, not from APM, and dropping
  tracing does not relax it. The `MeterFilter` on `http.server.requests` is still required.

**Recorded as a non-goal, not an omission.** §14 now names APM explicitly, with the reasoning,
so it reads as a decision to anyone reviewing the repo — which is the same convention already
applied to frontend tests and Kubernetes.

### 2026-09-02 — Multi-user reconsidered, and deliberately declined

Raised during Phase 4 prep and settled the same day. **No change: single user, enforced by
the email allowlist.** §14's non-goal stands as written.

Recorded because of *when* it was asked. The database is still empty — the Phase 4 backfill
has not run — so this was the one moment where adding `ownerId` to `companies` and
`applications` was a schema edit rather than a data migration. That window closes as soon as
real applications are entered. Passing it up is a choice, not an oversight, and a later
session should not have to guess whether it was ever considered.

- **What multi-tenancy would have cost here**, since the audit was done anyway: `ownerId` on
  both collections keyed on the OIDC `sub` claim (not email — emails change); owner scoping
  on both repositories, `CompanyService`, `ApplicationService`, `ApplicationQueryService`'s
  three queries and `StatsService`'s `$facet`; `name_unique` becoming a compound
  `{ownerId, name}` index, because a globally unique company name means the second person to
  apply to Stripe gets a 409; an ownership check inside `ApplicationService.requireCompany`,
  or user A can attach an application to user B's company; per-user MCP tokens, since the
  current static one is permitted on `GET /api/**` and would read everyone's data; and
  cross-tenant tests across all 16 endpoints. The frontend would barely change.
- **Why not, beyond effort.** A missed owner filter fails *silently* — it leaks data rather
  than throwing. A half-finished multi-tenant app is worse for a portfolio than a clean
  single-user one that documents the constraint, which §14 already does.
- **Open public signup was rejected more firmly.** It adds abuse handling, storage growth on
  a 512 MB shared M0, account deletion and export, and the obligation of holding *strangers'*
  recruiter names, emails and phone numbers in `contacts[]` — third-party personal data, with
  backups currently deferred. Mostly obligation, very little engineering signal.
- **If this is ever revisited**, the allowlisted-but-private variant is the one to build:
  real multi-tenancy with no abuse surface. Open signup is not.

### 2026-09-02 — Phase 4: deployed

Live at `https://app4jobtrack.me`. The runbook (`deploy/RUNBOOK.md`) was written before the
deploy and corrected *during* it, which is why it now contains several things that were not
obvious in advance. Recorded here are the decisions; the traps are in `STATE.md §4`.

- **Three identities on the box, and sudo scoped to one command.** `ubuntu` is a person,
  `deploy` is CI, `jobtracker` runs the JVM and owns the secrets with no login shell. CI's
  sudoers entry permits exactly `systemctl restart jobtracker`. The cost of this shows up
  immediately — the `deploy` account needs its SSH key installed *through* `ubuntu`, since
  `ssh-copy-id` cannot authenticate as an account that has no key yet — and it is still the
  right shape: a leaked CI key restarts a service, it does not own the box.
- **Ordering in the runbook was wrong twice, in the same way.** Atlas access was Step 9 but
  the app cannot boot without it (Step 7), and the deploy key was Step 11 but Step 7 uses it.
  Both were found by hitting them. The general lesson: a runbook's numbering encodes a
  dependency graph, and writing it linearly hides the edges.
- **`/actuator/health` being loopback-only shaped the CI gate.** The deploy workflow polls
  health *over ssh on the box* rather than from the runner, because the endpoint is not
  publicly reachable by design (2026-09-02 security pass) and Nginx does not proxy
  `/actuator`. A runner curling the public URL would 401 and fail every deploy. Worth noting
  as a case where a security decision correctly constrained a later design rather than being
  worked around.
- **The deploy gate is health, not systemd.** `systemctl restart` returning says the process
  started, not that the app works — on this box those are ~40 seconds apart, and every
  failure so far (Mongo auth, Datadog key) happened *after* Tomcat was already listening.
  The workflow polls for up to 180s and fails the deploy if health never reports `UP`.
- **Rollback is the JAR symlink.** Each deploy uploads `app-<sha>.jar` and repoints
  `app.jar`; the three newest are kept. With no Docker there is no image tag, so this is the
  rollback story in full — a decision from 2026-09-01 that only becomes concrete here.
- **Backups deferred, deliberately and with the cost written down.** M0 has no automated
  backup, no undelete and no point-in-time restore. `CLAUDE.md §3` names the `mongodump`
  cron as the reason M0 was acceptable over self-hosting, so that argument is currently
  unbacked. `PLAN.md` Phase 4's backup items are marked `[~]` rather than left unticked so
  the phase does not read as merely unfinished.
- **The backfill was skipped**, so the live app is empty. `PLAN.md` puts it first in Phase 4
  precisely because it is the cheapest place to find schema and validation problems, and
  deploying first inverted that. It is now the main thing standing between a working
  deployment and actually using it.

**Datadog is on site US5, not US1.** A key is valid only on its own site and a mismatch is
rejected with no error the app surfaces — a healthy service and an empty dashboard, with
nothing linking the two. §8 and the runbook now carry the detection loop.

### 2026-09-02 — The first CI run found a test that was never really passing

`JobTrackerApplicationTests` failed on the first GitHub Actions run, having been green
locally since Phase 1.

- **What it was.** A `@SpringBootTest` with no Testcontainers, relying on Spring Data's
  default `mongodb://localhost:27017`. `IndexInitializer` is an `ApplicationRunner` and
  `@SpringBootTest` executes those, so loading the context opens a Mongo connection and
  creates nine indexes. On a laptop with `jt-mongo` running that succeeds — **against the
  dev database**. On a runner with no ambient Mongo the context cannot start at all.
- **Why it survived three phases.** `./mvnw verify` is documented as the thing that
  reproduces CI, and it does — except for state the developer's machine happens to provide.
  A listening port is exactly that kind of state: invisible, ambient, and not expressed
  anywhere in the build. The test was reading a real database nobody intended it to touch.
- **Fixed as `JobTrackerApplicationIT extends AbstractMongoIT`.** A full-context test that
  talks to Mongo is an integration test by §11's own definition; the original `*Tests` name
  asserted "fast, no Docker", which was never true of it. Surefire 26 → 25, Failsafe 77 → 78,
  total unchanged at 103.
- **The general rule, now in `STATE.md §4`:** a green `./mvnw verify` only means something if
  nothing is listening on 27017. `docker stop jt-mongo` before trusting it.

**Worth noting what did the finding.** Nothing in the test suite could have caught this —
the suite was the thing that was wrong. It took an environment that genuinely lacked the
ambient dependency, which is the first concrete argument this project has produced for CI
existing at all beyond convenience.

### 2026-09-02 — A test that passed locally and failed in CI, with identical code

The second thing the first CI runs found, and a better bug than the first.
`SecurityIT.csrfCookieIsNotDeferred` failed on the runner with `No cookie with name
'XSRF-TOKEN'`, having been green on the laptop through three phases — including under
`./mvnw verify`, run immediately beforehand.

- **Root cause: `.with(csrf())` mutates shared state.**
  `SecurityMockMvcRequestPostProcessors.csrf()` does not merely decorate a request. It
  replaces the `CsrfTokenRepository` on the shared `CsrfFilter` bean with a test double that
  keeps the token in a request attribute instead of a cookie — and that swap persists for the
  remainder of the JVM run. Four IT classes carry `@AutoConfigureMockMvc` over
  `AbstractMongoIT`, so Spring caches and reuses **one** context across all of them. The
  cookie assertion therefore only held while it ran before *every* `.with(csrf())` test in any
  of those classes.
- **Why the machines disagreed: Maven's `runOrder` defaults to `filesystem`.** A filesystem
  enumerates in its own order, and macOS and a Linux runner do not agree. So class order — and
  with it the outcome — differed between the two with no code difference at all. Now pinned to
  `alphabetical` for both Surefire and Failsafe.
- **The fix is isolation, not ordering.** `CsrfCookieIT` holds that single assertion with
  `@DirtiesContext(BEFORE_CLASS)`, so it runs against a context whose `CsrfFilter` still has
  the real `CookieCsrfTokenRepository`. Pinning `runOrder` alone would have *hidden* this
  rather than fixed it — the test would still have been one alphabetically-earlier class away
  from breaking again.
- **Verified across all four orderings** (`alphabetical`, `reversealphabetical`, `hourly`,
  `filesystem`) and with the dev Mongo container stopped. `-Dfailsafe.runOrder=reversealphabetical`
  reproduces the CI ordering locally and is the way to check this class of problem in future.

**`STATE.md §4` previously described this as "order-fragile" and worked around it** by putting
the logout tests in their own class. That was the right instinct applied to the wrong cause:
the trigger was identified, the mechanism was not, and the workaround left the test one
alphabetical accident from failing. Recorded because "we noticed it was flaky and moved things
around" is how a real bug survives.

### 2026-09-02 — Phase 5: metrics, a dashboard, an alert

Closed the same day it opened, because dropping APM had already removed most of what made
this phase big. What remains is the part that was always the interesting half.

- **The `MeterFilter` is an allowlist, not the blocklist `PLAN.md` specified.** That plan
  said to filter `http.server.requests` down and stay under ~100 custom timeseries. Filtering
  that one metric is not enough: Boot's default binders — `jvm.gc.*`, `jvm.threads.states`,
  `tomcat.*`, `logback.events`, the Mongo driver's pool listeners — exceed the budget on a
  completely idle application, without anyone choosing to spend it. And nothing warns you;
  Datadog either bills the overage or caps and metrics quietly stop arriving. A blocklist also
  means the next dependency that ships a Micrometer binder enlarges the bill silently, whereas
  an allowlist makes a new metric arrive only when someone types its name into
  `MetricsConfig`. That is the decision point worth having.
- **The dropped metric was already predicted to be undroppable.** `PLAN.md` listed
  `jobtracker.api.request.duration` while noting it "will not arrive under that name".
  Correct — Actuator's timer is `http.server.requests`, and renaming it buys nothing.
  Keeping it with its templated `uri` tag answers "which endpoint is slow", which is the one
  question APM would have answered and a large part of why §14's non-goal costs less than it
  sounds.
- **`outcome` and `exception` are stripped.** `outcome` is derivable from `status`;
  `exception` is a class name, so its value set grows with the codebase. A tag bounded only
  by "how many exception types exist" is the exact shape that consumes a fixed budget without
  anyone noticing. Error counts survive as `jobtracker.api.errors`, tagged by status, from
  `GlobalExceptionHandler.problem()` — the single place every error response is built.
- **No percentiles, and that is a budget decision rather than an oversight.** Micrometer
  publishes none unless `management.metrics.distribution.percentiles` is set, and enabling
  them across `uri × method × status` costs roughly 90 series against an allowance of 100.
  The registry's `max` per `uri` isolates `/api/stats` perfectly well, and unlike an average
  a single slow aggregation is not washed out by a hundred fast reads. Percentiles scoped to
  that one endpoint stay available if the need ever appears.
- **A stage counter that counted almost nothing.** `jobtracker.stages.added` was wired into
  `addStage()` only — but `create()` makes stages by both of its branches, the seeded
  `APPLICATION_SUBMITTED` and a caller-supplied list. Every application undercounted by one,
  and a backfilled application arriving with four stages counted zero. **Found by the
  dashboard widget reading "no data"**, not by a test. This is the third time this exact
  shape has bitten: a rule attached to one branch of `create()` while the other does the same
  work silently — see the `lastContactAt` entry from 2026-09-01. The fix is the same
  shape too: one `countStages()` helper, called from both, with a javadoc naming its callers.
- **`MetricsIT` asserts deltas, never absolute counts.** Meters are monotonic and the IT
  context is shared, so `isEqualTo(1)` would pass or fail on Maven's `runOrder` — the failure
  that had already cost a CI-only red build hours earlier. Reading before and after is immune
  to it, and costs nothing.

**§1's resume bullet is now true rather than aspirational.** "Datadog metrics, dashboards and
alerting" describes things that exist against the deployed instance. It needed no edit when
APM was dropped, because it never claimed APM — which is what the 2026-09-01 narrowing was
for.

### 2026-09-02 — Phase 6: the MCP server

Built, exercised against the live API, and wired into Claude Desktop's config. What is not
done is the part only a human can do: restarting Desktop and running the four example
queries.

- **`ApiClient` exposes `get` and nothing else.** The read-only rule is already enforced
  where it counts — the bearer filter chain permits `GET /api/**` and denies the rest
  (2026-09-01) — and that was verified again here against the deployed instance rather than
  assumed: `POST`, `PUT`, `PATCH` and `DELETE` with the real MCP token all return 403, and
  the probe created nothing. Giving the client no way to *spell* a write is not a second
  guarantee, it is a way of making the first one unbreakable by accident from this side.
- **Four narrow tools, not one query tool.** `PLAN.md` argued this as a prompt-engineering
  point — Claude picks better from specific descriptions. The stronger reason is that it
  makes the set of data that can leave the API a property of this repo rather than of what
  somebody asks for.
- **The tool *descriptions* are load-bearing code, and two of them encode a trap.**
  `get_application_stats` has to tell Claude that "this month" is a calendar range for
  `from`/`to` while `days` is rolling, or it answers a calendar question with a rolling
  window and states it as fact (`SCHEMA.md §10.1`) — the output therefore prints the window
  the API says it measured, above the numbers. `search_applications` has to say that partial
  words match, or Claude "corrects" the user's term first; the backend uses an escaped regex
  rather than `$text` for exactly that reason (`SCHEMA.md §6`).
- **Times are rendered with their zone abbreviation attached.** `scheduledAt` is a UTC
  instant, and "14:00" with no zone is an invitation for Claude to repeat it as a local
  time. `en-US` is the formatting locale for one narrow reason: it renders
  `timeZoneName: 'short'` as `PDT`, where `en-GB` renders the same field as `GMT-7`. Display
  defaults to the machine's zone (this runs on the laptop, so that is the owner's) with
  `APP_TIMEZONE` as an override. Same family as the `LocalDate` trap in `STATE.md §4`,
  arriving in the presentation layer this time.
- **`claude_desktop_config.json` names an absolute path to `node`, and the reason is
  narrower than it first looked.** The original claim written here — that Desktop launches
  with a minimal environment so a bare `"node"` would not resolve — is **wrong for this
  Desktop version**, and its own log says so: it assembles a PATH explicitly and that PATH
  includes `~/.nvm/versions/node/v25.2.1/bin`. A bare `"node"` would very likely have
  worked. What was actually verified is the weaker statement that the absolute path works
  even under `PATH=/usr/bin:/bin`, launched from `/`.

  The real trade-off, with Node installed via nvm: an **absolute path** is independent of
  however Desktop resolves PATH — undocumented behaviour that has varied across versions —
  but carries a version number and breaks on the next `nvm install`. A bare **`"node"`**
  depends on that PATH harvesting but then follows nvm's current default automatically, so
  it survives Node upgrades. Neither is strictly better; the absolute path is in place
  because it demonstrably works, and the failure mode is recorded in `STATE.md §4` with the
  one-liner to repoint it, since the symptom (Desktop reports the server failing to start)
  points nowhere near the cause.

  **Recorded this way deliberately.** The mistake was asserting a mechanism from a plausible
  general belief about MCP clients rather than from this client's behaviour, having only
  tested that the chosen option worked — testing the alternative would have shown it too.
- **Tested with a scripted stdio client rather than the MCP Inspector.** The Inspector is an
  interactive browser UI and cannot assert anything. A script does `initialize` →
  `tools/list` → `tools/call` across all four tools and four failure scenarios, and — the
  part that actually matters — it parses every stdout line as JSON, which is what proves no
  diagnostic ever leaked into the protocol stream. That is the failure this phase is most
  likely to have, and the Inspector would not have caught it.

**Found while testing, not an MCP bug: `avgDaysToFirstResponse` can be negative.** The live
data has an application recorded as applied 2026-09-02 whose online assessment completed
2026-08-21 — eleven days *before* it. `SCHEMA.md §9` defines the metric as
`firstResponseAt − appliedDate` with no floor and no rule that a stage must postdate the
application, so the mean is dragged negative and the MCP tool now reports "Average time to
first response: -11.1 days", which is a nonsense sentence. Arithmetic confirmed against all
three qualifying applications (−11.3, +4.7, +233.7 → 75.9 all-time, matching the API).
Deliberately **not** fixed here: it is a data-modelling question — is a stage before
`appliedDate` legitimate (a recruiter reached out first) or a data-entry slip? — and papering
over it in the presentation layer would be the same "confidently wrong" failure this project
keeps designing against, only hidden better. Recorded as an open item in `STATE.md`.

---

## 7. Data model (summary — full detail in `SCHEMA.md`)

- **`companies`** — one per company. Name (unique), website, industry, `contacts[]`,
  notes, tags.
- **`applications`** — one per role applied to. Links to a company via `companyId` +
  denormalized `companyName`. Holds `role`, `status` (`ACTIVE | OFFER | ACCEPTED |
  REJECTED | WITHDRAWN | GHOSTED`), `appliedDate`, `source`, `followUpDate`,
  `jobPostingUrl`, `location`, `workMode`, `compensation`, `notes`, `tags`,
  `lastContactAt`, and `stages[]`. **`SCHEMA.md §3` is the full field list** — this is a
  summary and will lag it.
- **`stages[]`** (embedded) — the ordered rounds. Each: `stageId`, `sequence`, `type`
  (`APPLICATION_SUBMITTED | RECRUITER_SCREEN | ONLINE_ASSESSMENT | TAKE_HOME |
  PHONE_SCREEN | TECHNICAL_INTERVIEW | SYSTEM_DESIGN | BEHAVIORAL | HIRING_MANAGER |
  SUPERDAY | TEAM_MATCH | REFERENCE_CHECK | OFFER | OTHER`), `status` (`EXPECTED |
  SCHEDULED | PASSED | FAILED | CANCELLED | RESCHEDULED | NO_RESPONSE`), `scheduledAt`,
  `completedAt`, `format`, `interviewers[]`, `notes`.

  **`StageType`'s declaration order above is canonical** — it is the funnel's progression
  order, and the stats funnel renders in it. Keep this list, `SCHEMA.md §5`, and the Java
  enum in the same order.

### The four read features (API endpoints == MCP tools)

| Feature | Endpoint | MCP tool |
|---|---|---|
| Stats / funnel | `GET /api/stats?days=` | `get_application_stats` |
| Due follow-ups | `GET /api/applications/followups` | `list_pending_followups` |
| Text search | `GET /api/applications?q=` | `search_applications` |
| Upcoming interviews | `GET /api/applications/interviews?days=` | `get_upcoming_interviews` |

---

## 8. Environment & configuration

**Never commit secrets.** There is no single file — where a value lives depends on where the
code runs:

| Where it runs | Secrets live in | Committed? |
|---|---|---|
| **Your laptop** (backend, Phases 1–3) | `backend/config/application-local.yml` | No — gitignored. Copy `application-local.yml.example` beside it to start. |
| **The VPS** (Phase 4+) | `/etc/jobtracker/jobtracker.env`, mode `600`, owned by the service user, loaded by systemd `EnvironmentFile=` | No — never in the repo at all |
| **GitHub Actions** (Phase 4) | Repository secrets (`SSH_PRIVATE_KEY`, `SSH_HOST`, `SSH_USER`) | No — not a file |
| **MCP server** (Phase 6) | `mcp-server/.env` for dev; the `env` block of `claude_desktop_config.json` for real use | No — `.env` is gitignored, and the Claude Desktop config lives outside the repo |

`backend/src/main/resources/application-local.yml` **is** committed and must stay free of
secrets — it reads `${GOOGLE_CLIENT_ID:}` and friends, and serves as documentation of what
the local profile expects. `backend/config/` is a **default Spring Boot config location**
resolved against the working directory (`backend/`, for both the IntelliJ run configuration
and `./mvnw spring-boot:run`), and it takes **higher precedence than the classpath** — so the
file there overrides the committed one property by property without editing it. Verified by
setting `server.port` in it and watching the app bind that port instead.

**Do not use the IntelliJ run configuration's "Environment variables" field.**
`.idea/runConfigurations/` is committed deliberately (PLAN.md Phase 0), so anything typed
there is tracked by git. This is the one place the "just use env vars" habit backfires on
this project.

Tests need none of this: `./mvnw verify` uses Testcontainers and never reads the local
config. The Datadog key is prod-only — the Micrometer registry is `enabled: false` outside
the `prod` profile, so a key on the laptop would do nothing (in Phase 5 the local *Agent*
takes `DD_API_KEY` through its own config, not the app's).

| Variable | Used by | Example / notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | API | `local` or `prod` |
| `MONGODB_URI` | API | `mongodb+srv://user:pass@cluster.xxxx.mongodb.net/jobtracker?retryWrites=true&w=majority` |
| `GOOGLE_CLIENT_ID` | API | from Google Cloud Console |
| `GOOGLE_CLIENT_SECRET` | API | " |
| `APP_ALLOWED_EMAILS` | API | comma-separated; the one Google account allowed to log in |
| `APP_MCP_TOKEN` | API + MCP server | long random string; MCP sends it as `Authorization: Bearer` |
| `APP_BASE_URL` | API | `https://<your-domain>` — used for the OAuth redirect |
| `DD_API_KEY` | API (metrics) | Datadog API key for the Micrometer registry |
| `DD_SITE` | API (metrics) | **`us5.datadoghq.com`** for this org. Datadog runs several sites (`datadoghq.com` = US1, `us3`, `us5`, `datadoghq.eu`, `ap1`) and a key is valid only on its own; a mismatch is rejected silently — no error worth noticing, no data in the UI. `application-prod.yml` builds the Micrometer `uri` from this. |
| `API_BASE_URL` | MCP server | `https://<your-domain>`. https is required (localhost excepted) — the bearer token rides every request. |
| `API_TOKEN` | MCP server | the same value as `APP_MCP_TOKEN`. Missing or blank stops the server at startup rather than producing one whose every call 401s. |
| `APP_TIMEZONE` | MCP server | **Optional.** IANA zone for displaying interview times. Defaults to the machine's own, which is normally right since the server runs on the laptop. |

Spring profiles:
- **`local`** — points at a localhost MongoDB, permissive CORS for `http://localhost:5173`,
  OAuth redirect `http://localhost:8080/login/oauth2/code/google`, Datadog registry
  disabled.
- **`prod`** — everything from env vars, CORS effectively unused (same origin via Nginx),
  OAuth redirect `https://<your-domain>/login/oauth2/code/google`.

---

## 9. Local development

> Commands assume you're in the repo root. Fill in once the projects are scaffolded
> (Phases 1/3/6).

```bash
# Backend (needs a local MongoDB running, or rely on tests only)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local     # http://localhost:8080
./mvnw test                                                  # unit tests (Surefire)
./mvnw verify                                                # + integration tests (Testcontainers; needs Docker)
# Swagger UI: http://localhost:8080/swagger-ui.html

# Frontend
cd frontend
npm install
npm run dev                                                  # http://localhost:5173 (proxies /api → :8080)
npm run build                                                # outputs dist/

# MCP server
cd mcp-server
npm install
npm run dev                                                  # runs with tsx over stdio
npx @modelcontextprotocol/inspector npm run dev              # interactive tool testing
```

A local MongoDB for manual backend runs:
```bash
docker run -d --name jt-mongo -p 27017:27017 mongo:8
```

### IntelliJ IDEA setup

The IDE is IntelliJ; these are the parts of it this project actually leans on.

| Need | Where |
|---|---|
| Scaffold the backend | **File → New → Project → Spring Boot** (the built-in Spring Initializr client). Same options as `start.spring.io`; see `PLAN.md` Phase 1. |
| Run the API | A **Spring Boot** run configuration on `JobTrackerApplication`, with *Active profiles* = `local`. Avoids retyping `-Dspring-boot.run.profiles`. |
| Run tests | Right-click `src/test/java` → Run. Note the split: the green arrow runs `*Test` **and** `*IT` classes directly, ignoring the Surefire/Failsafe separation — only `./mvnw verify` reproduces CI exactly. |
| Testcontainers | Needs a running Docker daemon (Docker Desktop or Colima). Check the **Services** tool window to see containers start. |
| Manual API calls | **HTTP Client** — `.http` files live in `backend/src/test/http/`, run with the gutter arrows. This replaces Postman; keep the files in git. |
| Inspect Mongo | **Database** tool window → `+` → MongoDB, paste the Atlas SRV string or `mongodb://localhost:27017`. Replaces Compass if you prefer one window. |
| Frontend | Open `frontend/` — IntelliJ Ultimate handles Vite/React/TS natively; `npm` scripts run from the **npm** tool window. On Community Edition, use VS Code or the terminal for the frontend instead. |

Two IntelliJ-specific gotchas for this stack:

- **Java 25 language level.** After scaffolding, check *Project Structure → Project* is
  set to SDK 25 / language level 25, and *Settings → Build → Compiler → Java Compiler*
  has no stale `--release` override. A mismatch shows up as phantom red code that still
  compiles fine under Maven.
- **Enable annotation processing** (*Settings → Build → Compiler → Annotation
  Processors*) if you keep Lombok. If you drop Lombok — recommended, see `PLAN.md`
  Phase 1 — this is one less thing to get wrong.

---

## 10. Deployment

Full steps in `deploy/RUNBOOK.md` (written in Phase 4). Summary:

1. Oracle `VM.Standard.E2.1.Micro` instance, Ubuntu 24.04 x86_64, **reserved** public IP.
   Open 80/443 in the VCN
   Security List **and** in the instance's iptables (Ubuntu Oracle images block them by
   default).
2. Install Temurin 25, Nginx, certbot. Add a **4 GB swap file** (`vm.swappiness=10`).
3. `systemd` unit `jobtracker.service` runs
   `java -Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k -jar /opt/jobtracker/app.jar` with
   `EnvironmentFile=/etc/jobtracker/jobtracker.env`. **SerialGC** (the JVM's own choice on a
   1-core sub-2 GB machine — don't override it), and an explicit `-Xmx` rather than
   `MaxRAMPercentage`, which on 1 GB would leave nothing for metaspace, stacks and Nginx.
   See §6.
4. Nginx vhost: certbot TLS, serve `/var/www/jobtracker`, proxy `/api` `/oauth2` `/login`
   → `127.0.0.1:8080`.
5. Atlas: add the VPS's public IP to the Network Access allowlist.
6. Google Cloud: add `https://<your-domain>/login/oauth2/code/google` as an authorized
   redirect URI.
7. GitHub Actions deploys on push to `main`: build + test → `scp` JAR (keep last 3) →
   `ssh sudo systemctl restart jobtracker`; frontend build → `rsync` to
   `/var/www/jobtracker`. This needs two things that are easy to forget: a **deploy user
   with write access** to `/opt/jobtracker` and `/var/www/jobtracker`, and a **sudoers
   `NOPASSWD` entry scoped to exactly `systemctl restart jobtracker`** — not blanket sudo.
8. `backup-mongo.timer` runs `mongodump` nightly → Oracle Object Storage via rclone.
   **Test a restore before relying on it.**

---

## 11. Conventions

### Java / Spring
- **Package by feature** (`company/`, `application/`, `stats/`), not by layer.
- **Constructor injection only** (no `@Autowired` fields). Prefer `final` fields.
- **DTOs are Java `record`s.** Never expose `@Document` entities directly from
  controllers — always map to a response DTO. Keep mapping in a small hand-written mapper
  class per feature (no MapStruct — one less thing to learn/debug).
- **Validation** with Jakarta Bean Validation on request DTOs (`@NotBlank`, `@NotNull`,
  `@PastOrPresent`, `@Valid`). Reject unknown enum values at binding. **No `@Future`
  anywhere** — historical backfill is a first-class use case (§6). Cross-field and
  conditional rules live in the service, not in annotations.
- **Errors:** a single `@RestControllerAdvice` returning RFC 7807 `ProblemDetail`.
  404 for missing resources, 400 for validation, 401 unauthenticated, 403 wrong user,
  409 for conflicts.
- **REST:** plural nouns (`/api/companies`, `/api/applications`). Sub-resources for
  stages (`POST /api/applications/{id}/stages`, `PATCH .../stages/{stageId}`).
- **Actuator:** `management.endpoints.web.exposure.include=health` from day one, not as a
  final-polish step. Swagger/OpenAPI is off in prod (§6).
- **Aggregations** (stats) use `MongoTemplate` / `Aggregation`, not derived queries.
- **Time:** store `Instant` in UTC for timestamps; `LocalDate` for date-only fields
  (`appliedDate`, `followUpDate`). Do all "this week / this month / due soon" boundary
  math in the owner's timezone, then convert to UTC for the query. See `SCHEMA.md §
  Dates`.
- **Tests:** `FooServiceTest` = fast unit tests with mocks (Surefire). `FooControllerIT` /
  `FooRepositoryIT` = full-context integration tests with a Testcontainers MongoDB
  (Failsafe, `*IT` suffix). Every endpoint gets at least one IT covering the happy path
  and the main failure (401 / 404 / 400).

### TypeScript (frontend & MCP)
- `strict: true` in `tsconfig`. No `any` — use `unknown` + narrowing.
- Frontend: **all** server interaction goes through a TanStack Query hook in
  `src/api/hooks/`. Components never call `fetch` directly.
- Frontend types in `src/api/types.ts` mirror the backend response DTOs field-for-field.
- MCP: every tool input is a **zod schema**; the handler returns
  `{ content: [{ type: "text", text: ... }] }`. Keep tools thin — one API call, minimal
  shaping.
- Comment the non-obvious parts generously — the owner is new to TS.

### Git
- `main` is deployable. Branch per phase/feature: `phase-1-backend-crud`,
  `phase-3-frontend`, etc.
- Conventional-ish commits: `feat(api): add stage sub-resource endpoints`,
  `fix(frontend): send credentials on api calls`, `docs: update decision log`.
- Every commit message ends with a `Claude-Session:` trailer carrying **the URL of the
  session that produced the commit** — a different URL each session, not the one below
  copied forever. PR descriptions end with the same URL, without the `Claude-Session:`
  prefix. Format:
  `Claude-Session: https://claude.ai/code/session_<id>`

---

## 12. Developer context (the owner)

- **Comfortable:** Java, Spring Boot (basic — CRUD, REST controllers, dependency
  injection; less so with Spring Security internals, aggregation pipelines, Testcontainers).
- **New to:** React, TypeScript, the whole frontend toolchain (Vite, npm, JSX, hooks,
  TanStack Query). Also new to **MCP** and writing MCP servers.
- **Implications for how Claude should work on this project:**
  - In the **frontend (Phase 3)** and **MCP (Phase 6)** phases: explain *why*, not just
    *what*. Introduce each new concept (a hook, a query, JSX, a zod schema) the first time
    it appears. Prefer boring, well-documented patterns over clever ones. Add code
    comments.
  - In the **backend** phases: normal pace is fine, but don't assume deep knowledge of
    Spring Security filter chains, `MongoTemplate` aggregation, or Testcontainers wiring —
    spell those out.
  - Keep dependencies minimal. Every added library is something the owner has to learn to
    debug.
  - When a step touches infra (Oracle, Atlas, Google Cloud, Datadog, DNS), give the exact
    clicks/commands — these are one-time setup tasks that are easy to get subtly wrong.
  - **IDE is IntelliJ IDEA.** Prefer IntelliJ-native instructions (run configurations,
    HTTP Client `.http` files, the Database and Services tool windows) over "install
    Postman / Compass / another GUI". Fewer tools to learn. See §9.

---

## 13. Glossary

| Term | Meaning in this project |
|---|---|
| **Company** | An employer. One document in `companies`. Can have many applications. |
| **Application** | One role you applied to at one company. One document in `applications`. The unit most of the app operates on. |
| **Stage** (a.k.a. "process") | One round in an application's pipeline — OA, recruiter screen, technical interview, superday, offer, etc. Embedded in the application as an entry in `stages[]`. Has its own `type`, `status`, dates, notes. |
| **Application `status`** | The overall state of an application: `ACTIVE`, `OFFER`, `ACCEPTED`, `REJECTED`, `WITHDRAWN`, `GHOSTED`. Denormalized; derived from what's happened in `stages[]`. |
| **Stage `status`** | The state of one round: `EXPECTED`, `SCHEDULED`, `PASSED`, `FAILED`, `CANCELLED`, `RESCHEDULED`, `NO_RESPONSE`. |
| **Response rate** | Derived metric: applications with ≥ 2 stages ÷ total applications (i.e. got past the initial submission). Defined precisely in `SCHEMA.md`. |
| **MCP** | Model Context Protocol. Our MCP *server* exposes 4 read-only tools that Claude Desktop calls to answer questions about the job search. It calls the deployed REST API. |
| **Dogfooding** | Using the deployed app for the owner's actual job search. A project goal, not a nice-to-have. |

---

## 14. Out of scope / non-goals

- **Multi-user / teams.** Single user, enforced by email allowlist. No roles, no sharing.
- **Writes from the MCP server / Claude.** The MCP layer is strictly read-only.
- **Job-board scraping / external ATS integration.** All data is entered manually by
  design (the project deliberately avoids depending on a third-party API for its core
  data).
- **Email/calendar notifications.** `followUpDate` and `get_upcoming_interviews` surface
  what's due; no push notifications.
- **Native mobile app.** Responsive web only, and even that is a stretch goal.
- **GraalVM native image.** The original reason was that it kills the `dd-trace-java`
  javaagent path; with tracing dropped that no longer applies, but the build is still
  painful and buys nothing here — a single-user API's startup time is not a problem worth
  a native-image toolchain.
- **APM / distributed tracing, in any form.** Not an oversight and not a casualty of the
  1 GB box alone: Datadog APM is a separately-billed SKU with no trial offerable on top of
  the student Pro plan (§6, 2026-09-02), and the alternatives — a second Datadog org on its
  own evaluation trial, or OpenTelemetry to a local Jaeger/Tempo — were both considered and
  declined. The first risks cross-wiring a prod API key for the sake of screenshots; the
  second adds a tool to a project that deliberately keeps its dependency count low (§12).
  The observability story is custom metrics, a dashboard and an alert, on a genuinely
  deployed service — and "why is there no APM?" is answerable from first principles, which
  is better material than a flame graph captured on a laptop.
- **Kubernetes / containers on the VPS.** See §6.
- **Frontend and end-to-end tests.** Deliberate: the backend carries the test story
  (unit + Testcontainers ITs on every endpoint), and the SPA is a single-user dashboard
  verified by using it daily. Named here so it reads as a decision rather than an
  oversight.
