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
   *deployed* instance, plus APM traces captured from a **local** run during the 14-day
   trial. The 1 GB host cannot carry the Datadog Agent alongside the JVM, and only one
   instance is available — see §6. Say which is which; a smaller true claim beats a vague
   larger one.
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

*(Says "metrics, dashboards and alerting", not "APM": those are what run continuously
against the deployed instance. APM tracing is exercised locally during the trial and is
worth discussing in an interview — but claiming it as a production capability would not
survive a follow-up question.)*

---

## 2. Current status

| | |
|---|---|
| **Current phase** | **Phase 1 — backend CRUD.** Service layer complete (domain, repositories, DTOs, mappers, all four read features; 62 tests green). Controllers + `GlobalExceptionHandler` are next. |
| **Phase 0 status** | Repo, IntelliJ and local MongoDB done. **Outstanding:** domain, Oracle VM, Atlas M0, Google OAuth client, Datadog student-pack redemption. None block Phase 1. |
| **Session handoff** | See **`STATE.md`** — current branch, what is built, what is next, machine setup, and the Boot 4 traps already found |
| **Plan** | See `PLAN.md` for the full phased checklist |
| **Schema** | See `SCHEMA.md` for the full data model |
| **Live URL** | _not deployed yet_ (`https://app4jobtrack.me` once Phase 4 is done) |
| **Repo** | `https://github.com/LeDuyNg/Application-Tracker` — `phase-1-backend-crud` is pushed; `main` is still docs-only. |
| **Local dev** | `docker start jt-mongo` (MongoDB 8.3.8 on `:27017`), then the **JobTracker (local)** run config. |
| **IDE** | **IntelliJ IDEA** — Spring Initializr, HTTP Client, Docker, and Database tool windows are all used; see §9. |
| **Datadog plan** | **Pro via the GitHub Student Developer Pack** (10 hosts, ~13-month retention, free for 2 years). APM is *not* included — see §6. |

Update this table at the end of every working session.

---

## 3. Tech stack (authoritative — do not substitute without a decision-log entry)

### Backend
| Thing | Choice | Notes |
|---|---|---|
| Language | **Java 25 (LTS)** | GA Sep 2025. `dd-trace-java` fully supports it. |
| Framework | **Spring Boot 4.1.x** (on `4.1.1`) | Spring Framework 7. Baseline Java 17+. **Jackson 3** is the default JSON mapper — package is `tools.jackson.*`, not `com.fasterxml.jackson.*`. |
| Build tool | **Maven** | Single module in `backend/`. Use the `mvnw` wrapper (IntelliJ picks it up automatically). |
| Persistence | **Spring Data MongoDB** | Repositories + `MongoTemplate` for aggregations. |
| Security | **Spring Security** + `spring-boot-starter-oauth2-client` | Google OAuth2 login for the SPA; a static bearer token for the MCP server. |
| API docs | **springdoc-openapi 3.x** | Swagger UI at `/swagger-ui.html` — useful for manual testing while learning. **Pin the 3.x line**: springdoc 2.x targets Boot 3 / Framework 6 and will not work on Boot 4. **Disabled in prod** (`springdoc.api-docs.enabled=false`) — see §6. |
| Metrics | **Micrometer** + `micrometer-registry-datadog` | Pushes custom metrics straight to the Datadog API over HTTPS — independent of whether the Agent is installed. Budget: ~100 custom timeseries (see §6). |
| APM | **`dd-trace-java`** javaagent, **local only** | Never runs on the VPS: tracing needs a Datadog Agent to send to, and the Agent plus a JVM does not fit in 1 GB. Exercised on the laptop during the 14-day trial for screenshots and interview material. APM is also a separate paid SKU, not part of student-pack Pro (see §6). |
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
| OS | **Ubuntu 24.04 (x86_64)** | Default login user `ubuntu`. x86 rather than arm64 since the shape changed — one less architecture caveat for `dd-trace-java` and any native dependency. |
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
  Local dev run + Datadog Agent (laptop) ──▶ Datadog   (APM traces, trial window only —
                                                        never on the 1 GB VPS)
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
  in production**, so there are no production traces; APM is exercised on a local run during
  the trial only (§6).

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
│       │   ├── config/        ← SecurityConfig, MongoConfig, OpenApiConfig, IndexInitializer
│       │   ├── company/       ← Company, CompanyRepository, CompanyService, CompanyController, dto/
│       │   ├── application/   ← Application, Stage, *Repository, *Service, *Controller, dto/
│       │   ├── stats/         ← StatsService (MongoTemplate aggregations), StatsController
│       │   ├── auth/          ← BearerTokenFilter, AllowlistOAuth2UserService, MeController
│       │   └── common/        ← GlobalExceptionHandler, enums/, error DTO, time utils
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
│       ├── components/        ← StatCard, StatusBadge, StageTimeline, FiltersBar, ...
│       ├── pages/             ← Dashboard, ApplicationsList, ApplicationDetail, ApplicationForm
│       └── lib/               ← formatting, date helpers
│
├── mcp-server/                ← TypeScript MCP server (runs locally)
│   ├── package.json / tsconfig.json
│   └── src/
│       ├── index.ts           ← server + stdio transport + tool registration
│       ├── apiClient.ts       ← fetch wrapper, injects Bearer token
│       └── tools/             ← one file per tool
│
├── deploy/
│   ├── jobtracker.service     ← systemd unit for the API
│   ├── nginx-jobtracker.conf  ← Nginx vhost
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
  separate paid SKU on every plan.)*
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
  API and does not depend on the Agent, so it stays as-is either way.
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
  instance, and the README says so.
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

**Never commit secrets.** Local dev uses `backend/src/main/resources/application-local.yml`
(gitignored if it holds anything sensitive) or environment variables. Prod reads
everything from the systemd `EnvironmentFile` at `/etc/jobtracker/jobtracker.env` (mode
`600`, owned by the service user).

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
| `DD_SITE` | API (metrics) | e.g. `datadoghq.com` |
| `API_BASE_URL` | MCP server | `https://<your-domain>` |

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
- **GraalVM native image.** Kills the `dd-trace-java` javaagent path and the build is
  painful on 2 ARM cores.
- **Kubernetes / containers on the VPS.** See §6.
- **Frontend and end-to-end tests.** Deliberate: the backend carries the test story
  (unit + Testcontainers ITs on every endpoint), and the SPA is a single-user dashboard
  verified by using it daily. Named here so it reads as a decision rather than an
  oversight.
