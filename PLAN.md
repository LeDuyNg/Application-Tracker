# PLAN.md — Build plan

Phased plan for the Job Application Tracker. Read `STATE.md` for where the work currently
stands; `CLAUDE.md` for stack, architecture, and the decision log; `SCHEMA.md` for the data
model.

**How to use this file:** work top to bottom. Each phase has an objective, prerequisites, a
task checklist, a "done when" bar, and gotchas. Check boxes off as you go. Phases 3
(frontend) and 6 (MCP) include extra explanation because they're new ground for the owner.

**Progress marker:** at the end of each session, tick the boxes here and update
**`STATE.md`** (and `CLAUDE.md §2` if the phase changed). `STATE.md` is what a fresh
session reads to pick up the thread.

Legend: `[ ]` todo · `[~]` in progress · `[x]` done

---

## Phase 0 — Prerequisites & accounts

**Objective:** every external account, credential, and the empty repo ready, so no build
phase is blocked waiting on signup or DNS propagation.

**Prerequisites:** none.

### Repo & project root

The project does not exist yet. `CLAUDE.md`, `PLAN.md` and `SCHEMA.md` currently sit on
their own; the first task is to create the project root and move them into it, so every
path in `CLAUDE.md §5` becomes real.

- [x] Choose the project root directory (`Application-Tracker/`). The backend, frontend and
      mcp-server directories become siblings *inside* it — the root is **not** the Spring
      Boot project itself.
- [x] Move `CLAUDE.md`, `PLAN.md`, `SCHEMA.md` to that root. They stay at the top level for
      the life of the project; `README.md` joins them in Phase 7.
- [x] `git init` in the project root; first commit with the three planning docs.
- [~] Create a GitHub repo (public — enables free CI and is part of the portfolio). Push.
- [x] Add `.gitignore` (Java/Maven, Node, `*.env`, `application-local.yml` if it holds
      secrets, `/target`, `/dist`, `/node_modules`, `.idea/` except `.idea/runConfigurations/`).
- [x] Add an MIT (or preferred) `LICENSE`.

### IntelliJ

- [x] Open the **project root** as the IntelliJ project (not `backend/`). Phase 1 adds
      `backend/pom.xml`, which IntelliJ then offers to import as a Maven module — accept.
      This keeps the docs, backend, frontend and mcp-server in one window.
- [x] Confirm **JDK 25** is installed and registered (*File → Project Structure → SDKs*;
      IntelliJ can download Temurin 25 for you). Set the project SDK and language level
      to 25.
- [x] Confirm a Docker daemon is running and visible in the **Services** tool window —
      Testcontainers in Phase 1 depends on it.
- [x] Commit `.idea/runConfigurations/` only; gitignore the rest of `.idea/`.

### Domain
- [x] Register a domain (Cloudflare/Namecheap, ~$10/yr). Record it in `CLAUDE.md §2`.
- [x] Nothing to point at yet — DNS comes in Phase 4.

### Oracle Cloud
- [x] Create an "Always Free" account (needs a card for identity; not charged for
      Always-Free resources).
- [x] Create a VM: **`VM.Standard.E2.1.Micro`** (AMD x86), **1/8 OCPU burstable to 1,
      1 GB RAM**, **Ubuntu 24.04 (x86_64)**, ~50 GB boot volume.
      *A1 was the original choice and is unobtainable — see `CLAUDE.md §6`. Always Free
      resources exist only in your home region, so a different region is not a workaround.*
- [x] **One instance only.** The Always Free description implies two E2 micros, but this
      tenancy has one usable. Phase 5 is planned around that: no Datadog Agent in
      production. (Tracing was later dropped altogether — `CLAUDE.md §6`, 2026-09-02.)
- [x] Expect a slow first boot and slow `apt` operations — 1/8 OCPU baseline. It bursts to a
      full OCPU, which covers JVM startup and request handling; sustained CPU work is what
      it cannot do. Single-user traffic is well within it.
- [x] Assign a **reserved public IP** (not ephemeral) to the instance.
- [x] Add your SSH public key during creation; confirm `ssh ubuntu@<ip>` works.
- [x] **Open ports 80 and 443** — this is two steps:
  - [x] VCN → Security List → add ingress rules: `0.0.0.0/0` TCP 80 and 443.
  - [x] On the instance: the Ubuntu image ships iptables rules that also block them —
        add `iptables` ACCEPT rules for 80/443 and persist with `netfilter-persistent
        save` (exact commands go in `deploy/RUNBOOK.md` in Phase 4).
- [x] Add a **4 GB swap file** (`fallocate` / `mkswap` / `swapon` + `/etc/fstab`), and set
      `vm.swappiness=10` in `/etc/sysctl.d/`. On 1 GB this is load-bearing, not a safety
      net — but the heap is sized so the app does not *live* in swap: the boot volume is
      network-attached and steady-state swapping would be painfully slow.

### MongoDB Atlas
- [x] Create a free account and an **M0** cluster (pick the region closest to the Oracle
      region).
- [x] Database user for the app (strong generated password). Save credentials in your
      password manager.
- [x] Network Access → allowlist: add the Oracle instance's **reserved public IP** (`/32`).
      Also add your current IP for local admin.
- [x] Copy the **SRV connection string**; note the DB name will be `jobtracker`.
      **The string Atlas hands you contains no database name** — it ends `/?retryWrites=...`
      with nothing between the slash and the question mark. Put `jobtracker` in that gap, or
      the app dies at startup with `Database name must not be empty` (`CLAUDE.md §6`).
- [x] For inspecting data during dev, use IntelliJ's **Database** tool window (`+` →
      MongoDB → paste the SRV string). MongoDB Compass is an alternative if you'd rather
      have a separate window — you don't need both.

### Google Cloud (OAuth)
- [x] Create a project ("job-tracker").
- [x] OAuth consent screen: External, in "Testing" mode, add your Google account as a
      test user (that's enough for single-user; no verification needed).
- [x] Create an **OAuth 2.0 Client ID** (type: Web application):
  - Authorized redirect URIs:
    `http://localhost:8080/login/oauth2/code/google`
    `https://<your-domain>/login/oauth2/code/google`
- [x] Save the client ID and secret.

### Datadog

You have **Datadog Pro free for 2 years via the GitHub Student Developer Pack** (10 hosts,
~13-month metric retention). This materially changes Phase 5 — see `CLAUDE.md §6`.

- [x] Redeem the Datadog offer in the GitHub Student Developer Pack and join the Datadog
      Student Developer program. The 2-year clock starts at redemption; irrelevant at this
      project's timeline, and you want the account live well before Phase 5.
- [x] Confirm the account shows **Pro** (not Free) under *Plan & Usage*. Free would mean
      1-day metric retention, which would make the Phase 5 dashboard worthless.
- [x] Generate an **API key** (used by the Micrometer registry). Note your **DD site**
      (e.g. `datadoghq.com`).
- [x] **Check now whether an APM trial can be started on top of the student Pro plan**
      (*Plan & Usage* → APM, look for a "start trial" affordance). APM is a separate paid
      SKU and is **not** included in Pro. If a trial is *not* offerable on this account,
      the APM screenshots in Phase 5 need rethinking — and that is much cheaper to
      discover now than in Phase 5. Record the answer in `CLAUDE.md §6`.
      **Answer (2026-09-02): no. APM is not offerable on the student Pro plan.** Recorded
      in `CLAUDE.md §6`.
- [x] ~~Do **not** start the APM trial yet~~ — moot: there is no trial to start. Tracing
      was dropped from the project in response (`CLAUDE.md §6`, "Tracing dropped"), and
      Phase 5's APM subsection is now a pointer to that entry.

**Done when:** you can `ssh` to the VPS; the M0 cluster shows "Active"; you have the Google
client ID/secret, the Atlas URI, and the Datadog API key saved; Datadog shows the **Pro**
plan; the GitHub repo exists at the project root with the three planning docs pushed and
open in IntelliJ.

---

## Phase 1 — Backend CRUD API (local)

**Objective:** a fully working REST API against Atlas (or local Mongo), covering companies,
applications, stages, and the four read features — verified by tests and Swagger UI. **No
auth yet** (added in Phase 2).

**Prerequisites:** Phase 0 (Atlas URI). Docker installed locally (for Testcontainers).

### Project setup
- [x] Scaffold with **IntelliJ's Spring Initializr**: *File → New → Module → Spring Boot*,
      with the project root already open. Location `backend/`, Maven, **Java 25**, Spring
      Boot **4.0.x**, group `dev.duynguyen`, artifact `jobtracker`, package
      `dev.duynguyen.jobtracker`. Dependencies: Spring Web, Spring Data MongoDB,
      Validation, Spring Boot Actuator, Testcontainers. (Equivalent to
      [start.spring.io](https://start.spring.io) if you'd rather generate and unzip.)
- [x] **Skip Lombok.** `CLAUDE.md §11` already mandates constructor injection with `final`
      fields and records for DTOs, and `SCHEMA.md §11` explains why `@Document` classes are
      mutable — Lombok would save a handful of getters at the cost of an annotation
      processor to configure, another JDK-25 compatibility surface, and a tool the owner
      would have to debug. IntelliJ generates getters/setters on demand anyway.
- [x] Add `springdoc-openapi-starter-webmvc-ui` — **pin the 3.x line**. springdoc 2.x
      targets Boot 3 / Spring Framework 6 and will fail on Boot 4. Verify the app starts
      and `/swagger-ui.html` renders *before* writing controllers; a version mismatch here
      is much easier to diagnose on an empty app.
- [x] Add `micrometer-registry-datadog`, **and immediately set
      `management.datadog.metrics.export.enabled: false` in `application.yml`**. The
      dependency is not inert: the registry auto-configures on classpath presence and fails
      context creation with `apiKey was 'null' but it is required`, breaking every
      `@SpringBootTest` from here until Phase 5 configures it. `application-prod.yml` flips
      it back to `true`.
- [x] Import `testcontainers-bom` in `<dependencyManagement>` — the Boot parent does not
      manage Testcontainers module versions. Note the 2.x artifact names:
      `testcontainers-junit-jupiter` and `testcontainers-mongodb` (the 1.x names
      `junit-jupiter` / `mongodb`, which most snippets still use, will not resolve).
- [x] `pom.xml`: set `<java.version>25</java.version>`; confirm `spring-boot-maven-plugin`
      present; add `maven-failsafe-plugin` for `*IT` tests.
- [x] `application.yml` + `application-local.yml` + `application-prod.yml` per
      `CLAUDE.md §8`. Local points at `mongodb://localhost:27017/jobtracker`.
- [x] `application.yml`: `management.endpoints.web.exposure.include=health` (day one, not a
      Phase 7 polish step). `application-prod.yml`: `springdoc.api-docs.enabled=false` and
      `springdoc.swagger-ui.enabled=false` — Swagger must not be reachable on the public
      domain (`CLAUDE.md §6`).
- [x] IntelliJ **run configuration**: Spring Boot → `JobTrackerApplication`, *Active
      profiles* `local`. Commit it under `.idea/runConfigurations/`.
- [~] Commit the generated skeleton on branch `phase-1-backend-crud`.

### Domain layer (`company/`, `application/`, `common/enums/`)
- [x] `Company` `@Document("companies")` class + `Contact` POJO (see `SCHEMA.md §2, §4.1`).
- [x] `Application` `@Document("applications")` class + `Stage`, `Compensation` POJOs
      (`SCHEMA.md §3, §4.2, §4.3`).
- [x] Enums in `common/enums/`: `ApplicationStatus`, `ApplicationSource`, `WorkMode`,
      `StageType`, `StageStatus`, `StageFormat` (`SCHEMA.md §5`). **Declare `StageType`'s
      constants in `SCHEMA.md §5`'s exact order** — that order is the funnel's progression
      order and the stats output depends on it.
- [x] `@EnableMongoAuditing` config; `@CreatedDate` / `@LastModifiedDate` on the `Instant`
      fields.
- [x] Index creation: an `IndexInitializer` `ApplicationRunner` in `config/` that ensures
      every index in `SCHEMA.md §6` via `IndexOperations` (preferred over annotation
      auto-creation for explicitness). Log what it creates. **No text index** — free-text
      search uses a regex (`SCHEMA.md §6`, §10.3).

### Repositories
- [x] `CompanyRepository extends MongoRepository<Company, String>` — plus
      `Optional<Company> findByNameIgnoreCase(String name)`.
- [x] `ApplicationRepository extends MongoRepository<Application, String>` — derived
      queries where they're simple (`findByCompanyId`, `findByStatus`).
- [x] Anything involving `stages[]` traversal, text search, or grouping goes through
      `MongoTemplate` in the service, not derived queries.

### DTOs + mappers (`*/dto/`)
- [x] Request records: `CreateCompanyRequest`, `UpdateCompanyRequest`, `ContactRequest`,
      `CreateApplicationRequest`, `UpdateApplicationRequest`, `CompensationRequest`, and a
      single **`StageRequest`** serving both add and update. Bean Validation annotations per
      `SCHEMA.md §8.1`.
      *(This list originally named separate `AddStageRequest` and `UpdateStageRequest`. They
      would have been field-for-field identical: `PATCH` on a stage is a full replacement of
      its fields, not a partial merge, because a record cannot distinguish "absent" from
      "explicitly null" without wrapper types. One record, documented as such.)*
- [x] Response records: `CompanyResponse`, `ApplicationResponse` (includes `stages`),
      `ApplicationSummaryResponse` (compact, for lists/search), `StatsResponse`,
      `UpcomingInterviewResponse`, `FollowupResponse`. Plus `ApplicationSearchRequest`, the
      filter bundle for the search endpoint's query parameters.
- [x] Hand-written `CompanyMapper`, `ApplicationMapper` (no MapStruct).

### Services
- [x] `CompanyService`: CRUD. `delete` **blocks with 409** when applications reference the
      company (decided — `CLAUDE.md §6`); the error message names the referencing
      applications so the 409 is actionable. Rename updates `companyName` on every one of
      that company's applications.
- [x] `ApplicationService`: CRUD; on create, seed `stages[0]` as `APPLICATION_SUBMITTED`
      / `PASSED` with `completedAt = appliedDate` at midnight **in `app.timezone`**, not
      UTC, unless the caller supplied stages; validate `companyId` exists; set
      `companyName` from the company; keep `sequence` contiguous.
- [x] Denormalization sync per `SCHEMA.md §1`, on every stage mutation — three rules that
      are each easy to get subtly wrong, so give each one a unit test:
  - `currentStageType` = the **lowest**-`sequence` stage that is `SCHEDULED`/`EXPECTED`,
    else the **highest**-`sequence` `PASSED` stage. (Not "the latest pending stage" — that
    returns a round you haven't reached when a later one is already pencilled in.)
  - `status` recomputes **only when the current status is `ACTIVE` or `OFFER`**. Terminal
    statuses are sticky, or marking something `WITHDRAWN` and later fixing a stage note
    silently flips it back to `ACTIVE`. `GHOSTED` is manual-only.
  - `lastContactAt` = now, but **only** when a stage is added or an existing stage's
    `status`/`scheduledAt`/`completedAt` changes — never on note/tag/comp edits. This is
    the field the "gone quiet" query depends on; `updatedAt` cannot serve.
- [x] Stage operations: `addStage`, `updateStage(stageId, ...)`, `deleteStage(stageId)`.
- [x] `StatsService` (in `stats/`): the `$facet` aggregation from `SCHEMA.md §10.1`;
      compute rates in Java per `SCHEMA.md §9`.
- [x] Follow-ups query (`SCHEMA.md §10.2`) — both halves: `followUpDate` due, **and** the
      gone-quiet union on `lastContactAt`, tagged in the response so the two groups are
      distinguishable.
- [x] Upcoming-interviews aggregation (`SCHEMA.md §10.4`).
- [x] Free-text search as an **escaped** case-insensitive regex over `companyName` / `role`
      / `notes` (`SCHEMA.md §10.3`). `Pattern.quote` the user's input — an unescaped `(` is
      a 500 and a pathological pattern is a cheap DoS.
      *(All three live in `ApplicationQueryService`, not `ApplicationService` — see
      `CLAUDE.md §6`. Building these turned up a real bug: `lastContactAt` was never set on
      an application created with its stages supplied, which would have hidden the entire
      Phase 4 backfill from the gone-quiet query. Fixed and recorded in `SCHEMA.md §1`.)*
- [x] `TimeService` in `common/` wrapping a `Clock` + `app.timezone` for all relative-date
      math (`SCHEMA.md §7`).

### Controllers (REST)
- [x] `CompanyController`: `POST/GET/GET{id}/PUT{id}/DELETE{id} /api/companies`.
- [x] `ApplicationController`:
  - `POST /api/applications`, `GET /api/applications/{id}`, `PUT /api/applications/{id}`,
    `DELETE /api/applications/{id}`
  - `GET /api/applications` with `q, status, companyId, from, to, page, size, sort`
    (`Pageable`), default sort `appliedDate,desc` (`SCHEMA.md §10.3`)
  - `POST /api/applications/{id}/stages`, `PATCH /api/applications/{id}/stages/{stageId}`,
    `DELETE /api/applications/{id}/stages/{stageId}`
  - `GET /api/applications/followups`
  - `GET /api/applications/interviews?days=`
- [x] `StatsController`: `GET /api/stats?days=` **and** `?from=&to=`, mutually exclusive
      (400 if both). "How many applications this month?" is a *calendar* month, which
      `days=30` cannot express — see `SCHEMA.md §10.1`.
- [x] `GlobalExceptionHandler` (`common/`): `@RestControllerAdvice` → RFC 7807
      `ProblemDetail`; map `NoSuchElementException`/custom `NotFoundException` → 404,
      `MethodArgumentNotValidException` → 400 with field errors, custom
      `ValidationException` → 400, `IllegalStateException` for the delete-conflict → 409.
      *(Also covers binding failures — a bad enum in the body, an unconvertible query param —
      and a logged catch-all so a 500 has the same shape as everything else. Deliberately a
      plain advice rather than a `ResponseEntityExceptionHandler` subclass; see
      `CLAUDE.md §6`.)*
- [x] Date serialization: `spring.jackson.datatype.datetime.write-dates-as-timestamps:
      false` — **not** the Boot 3 key `spring.jackson.serialization.*`, which fails the
      context load on Jackson 3 (`SCHEMA.md §11`).

### Tests
- [x] `AbstractMongoIT` base class: `@SpringBootTest` + `@Testcontainers` with
      `MongoDBContainer("mongo:8")` wired via `@ServiceConnection` — match the Atlas major
      version so you don't discover a behaviour difference in prod.
- [x] `ApplicationServiceTest` (unit, mocks): stage sequencing, `companyId` validation, and
      one test per denormalization rule — `currentStageType` picks the lowest pending
      sequence (not the latest), a `WITHDRAWN` application stays `WITHDRAWN` after an
      unrelated stage edit, and `lastContactAt` moves on a stage status change but **not**
      on a notes edit.
- [x] `ApplicationControllerIT`: happy path for every endpoint + 404 + 400 cases.
      Plus `CompanyControllerIT`, which covers the two rules that corrupt data silently when
      broken: the rename cascade and the 409-on-delete.
- [x] `StatsServiceIT`: seed ~10 applications across statuses/stages, assert the funnel and
      each rate from `SCHEMA.md §9`.
- [x] `UpcomingInterviewsIT`, `FollowupsIT`: boundary cases (exactly `days` away, terminal
      statuses excluded). Plus `ApplicationSearchIT` — substring and case-insensitive
      matching (what `$text` could not do), and the escaping cases: `(` must not 500 and
      `.*` must match nothing rather than everything.
      *(Adding a second `*IT` class exposed a container-lifecycle bug in `AbstractMongoIT`
      that had been latent while only one existed — `CLAUDE.md §6`.)*
- [x] `mvn verify` green — **84 tests** (20 unit, 64 integration across six `*IT` classes).

### Manual verification
- [x] `backend/src/test/http/jobtracker.http` — IntelliJ's **HTTP Client**, run from the
      gutter arrows. Exercises the full flow: create company → create application → add
      stages → stats/followups/interviews/search. Committed to git; this is the collection
      re-run against prod in Phase 5 to generate metric traffic. No Postman needed.
      *(Every request carries a `client.test(...)` assertion, and the whole file was driven
      against a running instance rather than written and assumed. The error cases at the end
      are deliberate — they are what makes the Phase 5 error-rate monitor test-fireable. See
      `src/test/http/README.md`.)*
- [x] Point `application-local.yml` at the **Atlas** URI once and confirm it works against
      the real cluster; then switch back to local Mongo for fast iteration.
      *(Done via `backend/config/application-local.yml`, the gitignored override, so the
      committed file stays secret-free. Verified against the real M0: replica-set connection,
      9 indexes created, a create/read round trip, the `$facet` stats aggregation, regex
      search, and `LocalDate` surviving the trip intact. Test data removed afterwards.)*

**Done when:** `mvn verify` is green; Swagger UI at `/swagger-ui.html` lists every
endpoint; you can drive the whole company→application→stages→reads flow manually; the four
read features return correct data against seeded records.

**Gotchas:**
- Spring Boot 4 uses **Jackson 3** (`tools.jackson.*`). If you add custom serializers, use
  the new package. Most code won't touch this.
- Testcontainers needs Docker running locally. `mvn test` (Surefire) runs only unit tests;
  `mvn verify` runs `*IT` too.
- `LocalDate` fields serialize as `"2026-08-02"` — make sure the DTO uses `LocalDate`, not
  `Instant`, for `appliedDate`/`followUpDate`.
- There is **no text index** (`SCHEMA.md §6`). If you reach for `$text` out of habit,
  re-read why: it matches whole stemmed tokens, so `strip` would not find Stripe.
- IntelliJ's green-arrow test runner executes `*Test` **and** `*IT` classes alike, ignoring
  the Surefire/Failsafe split. Only `./mvnw verify` reproduces what CI runs.

---

## Phase 2 — Authentication

**Objective:** Google OAuth2 login gates the SPA; a static bearer token gates the MCP
server; only one email may log in. One Spring Security filter chain.

**Prerequisites:** Phase 1; Google client ID/secret (Phase 0).

### Config
- [x] Add `spring-boot-starter-oauth2-client` (and `spring-boot-starter-security` if not
      transitively present).
- [x] `application-*.yml`: `spring.security.oauth2.client.registration.google` with
      `client-id` / `client-secret` from env (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`);
      `spring.security.oauth2.client.provider.google` defaults are built in.
- [x] Env: `APP_ALLOWED_EMAILS` (comma-separated, one entry), `APP_MCP_TOKEN` (long random).
- [x] Set `registration.google.redirect-uri` explicitly per profile:
      `http://localhost:5173/login/oauth2/code/google` on `local` (**note the port — 5173,
      the Vite dev server, not 8080**; see Phase 3) and
      `${APP_BASE_URL}/login/oauth2/code/google` on `prod`. Register both in the Google
      client alongside the `localhost:8080` entry you may still want for Swagger-only runs.

### Security

**Two `SecurityFilterChain` beans, not one** (decided — `CLAUDE.md §6`). Splitting them by
`securityMatcher` makes the MCP token's read-only rule a single line and removes the filter
ordering hazard entirely.

- [x] **Chain 1 — bearer / MCP**, `@Order(1)`, `securityMatcher` = requests carrying an
      `Authorization: Bearer` header:
  - `sessionManagement(STATELESS)`, `csrf.disable()` (no cookies, no CSRF surface).
  - `BearerTokenFilter` (a `OncePerRequestFilter`): if the token matches `APP_MCP_TOKEN`,
    set an `Authentication` with authority `ROLE_MCP`; otherwise leave it unauthenticated
    (→ 401). Compare with **`MessageDigest.isEqual`**, not `String.equals` — constant time.
  - `authorizeHttpRequests`: `GET /api/**` requires `ROLE_MCP`; **everything else is
    denied**. That one line is the entire read-only guarantee — no `@PreAuthorize` needed.
- [x] **Chain 2 — browser / OAuth2**, `@Order(2)`, everything else:
  - `authorizeHttpRequests`: permit `/login/**`, `/oauth2/**`, `/actuator/health`;
    everything under `/api/**` requires auth; deny the rest. **Swagger is *not* permitted
    here** — it is disabled outright in prod via `springdoc.api-docs.enabled=false`
    (Phase 1), and only reachable on the `local` profile.
  - `oauth2Login()` with a custom `OidcUserService` that **rejects any email not in
    `APP_ALLOWED_EMAILS`, and also rejects an unverified email** (check the
    `email_verified` claim — an unverified claim is not an identity).
  - A **`failureHandler`** that returns 403 JSON. Note the default behaviour is a redirect
    to `/login?error`, *not* a 403 — throwing `OAuth2AuthenticationException` alone does
    not give you the status code you want.
  - `exceptionHandling().authenticationEntryPoint(...)` → return **401** for unauthenticated
    `/api/**` calls (JSON `ProblemDetail`), **not** a redirect to Google. The SPA decides
    when to redirect.
  - CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()` + a
    `CsrfTokenRequestAttributeHandler` **with `setCsrfRequestAttributeName(null)`**. That
    last argument is not optional decoration: since Spring Security 6 the CSRF token is
    generated *lazily*, so without it the `XSRF-TOKEN` cookie is never set until something
    reads the token — and an SPA that loads and immediately POSTs gets a 403 that looks
    like a bug in your code. Setting it to `null` opts out of the deferred loading.
    The SPA echoes the `XSRF-TOKEN` cookie as the `X-XSRF-TOKEN` header. Session cookie
    `SameSite=Lax`, `Secure` in prod.
- [x] `MeController`: `GET /api/me` → `{ email, name, picture }` from the OAuth principal,
      so the SPA can show who's logged in and detect session validity.

### Tests
- [x] `SecurityIT` (Testcontainers + `MockMvc` / `WebTestClient`):
  - unauthenticated `GET /api/applications` → 401 (JSON, not redirect)
  - bearer token valid → `GET` 200; bearer token on `POST` → 403; also on `PUT`, `PATCH`
    and `DELETE`, including the stage sub-resources
  - wrong bearer token → 401
  - a response to an unauthenticated request sets the `XSRF-TOKEN` cookie (guards the
    deferred-token regression above)
  - (OAuth login flow itself is hard to fully integration-test; at minimum unit-test the
    email-allowlist `OidcUserService` — allowed email passes, other email throws.)
- [x] Existing Phase 1 ITs updated to authenticate (helper that injects a mock OAuth2
      user, or uses the bearer token for GETs).

**Done when:** hitting `/api/**` without auth returns 401 JSON; logging in via Google with
the allowlisted account works and `GET /api/me` returns your profile; a non-allowlisted
Google account is rejected; the MCP bearer token can read but not write; `mvn verify`
green.

*Status: everything here is done and verified against the running app **except the live
Google round trip**, which cannot be completed yet — the `local` redirect URI points at
`http://localhost:5173`, the Vite dev server, which does not exist until Phase 3. The
allowlist logic itself is unit-tested (allowed / other / unverified / missing claim / empty
list). To try a real login before Phase 3, register
`http://localhost:8080/login/oauth2/code/google` in the Google client and override
`spring.security.oauth2.client.registration.google.redirect-uri` to match in
`backend/config/application-local.yml`.*

**Gotchas:**
- The OAuth redirect URI registered in Google must **exactly** match
  `{APP_BASE_URL}/login/oauth2/code/google` (scheme, host, path). Localhost and prod are
  separate entries.
- With `SameSite=Lax`, cross-site POSTs are blocked by the browser anyway, but keep CSRF
  on — it's the correct answer and cheap.
- With two chains, `@Order` and the `securityMatcher` predicates do the work that filter
  ordering did in a single-chain design. Get the matcher wrong and browser requests fall
  into the bearer chain (or vice versa) — assert both paths in `SecurityIT`.
- A 403 on a POST from the SPA is almost always CSRF, not authorization. Check that the
  `XSRF-TOKEN` cookie exists before suspecting anything else.

---

## Phase 3 — React SPA (local)

**Objective:** a working dashboard against the local API — list/filter/search applications,
see a company, add/edit an application and its stages, view stats, follow-ups, and upcoming
interviews. Runs on `localhost:5173`, proxying to `localhost:8080`.

**Prerequisites:** Phases 1–2 running locally.

> **New-ground notes (owner is new to React/TS).** Concepts introduced here, in order:
> npm & `package.json`; the Vite dev server; JSX (HTML-in-JS); components & props;
> `useState` / `useEffect`; **TanStack Query** (the thing that fetches, caches, and
> re-fetches server data so you don't hand-roll loading/error state); client-side routing;
> **react-hook-form + zod** (typed forms with validation). Each is explained the first
> time it's used in the code. Keep components small; prefer many simple files over few
> clever ones.

### Setup
- [x] `npm create vite@latest frontend -- --template react-ts`; `cd frontend; npm install`.
- [x] Install: `@tanstack/react-query`, `react-router-dom`, `react-hook-form`, `zod`,
      `@hookform/resolvers`, `date-fns` (date formatting).
- [x] `vite.config.ts`: dev `server.proxy` forwarding `/api`, `/oauth2`, `/login` to
      `http://localhost:8080` (so the browser treats everything as same-origin and the
      session cookie works).
- [x] `tsconfig.json`: `"strict": true`.
- [x] Set up `QueryClientProvider` and `BrowserRouter` in `main.tsx`.
- [x] Branch `phase-3-frontend`. Cut from `main` at `75473df`, which carries Phases 1 and 2.

### API layer (`src/api/`)
- [x] `types.ts` — TypeScript interfaces mirroring the backend response DTOs
      (`ApplicationResponse`, `CompanyResponse`, `Stage`, `StatsResponse`, etc.), plus the
      enum string-literal unions matching `SCHEMA.md §5`.
- [x] `apiClient.ts` — a `fetch` wrapper: base `/api`, `credentials: "include"`, sets
      `X-XSRF-TOKEN` from the `XSRF-TOKEN` cookie on mutations, throws a typed `ApiError`
      on non-2xx, and **on 401 triggers a redirect** to `/oauth2/authorization/google`.
- [x] `hooks/` — one hook per operation using `useQuery` / `useMutation`:
      `useApplications(filters)`, `useApplication(id)`, `useCreateApplication()`,
      `useUpdateApplication()`, `useDeleteApplication()`, `useAddStage()`,
      `useUpdateStage()`, `useCompanies()`, `useCreateCompany()`, `useStats(days)`,
      `useFollowups()`, `useUpcomingInterviews(days)`, `useMe()`. Mutations invalidate the
      relevant queries on success.

### Routing & shell
- [x] Routes: `/` (Dashboard), `/applications` (list), `/applications/new` (form),
      `/applications/:id` (detail), `/applications/:id/edit` (form), `/companies` (list),
      `/companies/:id` (detail).
- [x] `AppShell` — top nav + `useMe()` showing the logged-in email; a "Sign in" state if
      `useMe()` 401s.

### Components (`src/components/`)
- [x] `StatusBadge` (application status → colored pill), `StageStatusBadge`.
- [x] `StatCard` (label + number), used in a `StatsBar`.
- [x] `StageTimeline` — vertical list of an application's stages with type, date, status,
      notes; "add stage" and inline edit.
- [x] `FiltersBar` — status select, company select, free-text `q`, date range; drives
      `useApplications`.
- [x] `ApplicationTable` — rows: company, role, status, current stage, applied date,
      next interview; links to detail.
- [x] `FollowupsWidget`, `UpcomingInterviewsWidget` — for the dashboard.
- [x] `ApplicationForm` — react-hook-form + zod resolver; zod schema mirrors
      `CreateApplicationRequest`; company is a select populated by `useCompanies()` with an
      "add new company" affordance.
- [x] `CompanyForm` — name, website, industry, contacts (field array), notes, tags.

### Pages
- [x] `Dashboard` — `StatsBar` + `FollowupsWidget` + `UpcomingInterviewsWidget` + a
      "recent activity" slice of `useApplications`.
- [x] `ApplicationsList` — `FiltersBar` + `ApplicationTable` + pagination.
- [x] `ApplicationDetail` — all fields + `StageTimeline` + edit/delete.
- [x] `ApplicationForm` page (new/edit).
- [x] `CompaniesList` / `CompanyDetail` (detail shows the company's applications).

### Styling
- [x] Plain CSS or CSS Modules. One `theme.css` with a few CSS variables (colors,
      spacing). No component library. Responsive enough to use on a laptop; mobile is a
      stretch goal.

### Verification
- [x] **Google login + logout verified in a browser** at `localhost:5173` (the Phase 2
      criterion that was still owed). Fixed on the way: the OAuth success redirect had to
      become an absolute URL from `app.base-url` — a root-relative `/` resolves to the bare
      API behind the dev proxy and 403s. `POST /api/logout` → 204, "Sign out" in the shell.
- [ ] **Full workflow not yet run** — create a company → application → stages and confirm
      the dashboard widgets / filters / funnel reflect it. The DB is empty. This is the
      real "Done when" bar and is still open (`STATE.md`).
- [ ] `npm run preview` on the built `dist/` — not run. `npm run build` and `npm run lint`
      are green.
- [x] Read plumbing checked by curl through the proxy: `/api/me` → 401 `problem+json` +
      `XSRF-TOKEN` cookie; stats / applications / followups / interviews / companies return
      the shapes `src/api/types.ts` expects; `/oauth2/authorization/google` → 302 to Google
      with `redirect_uri=…:5173/login/oauth2/code/google`.
- [x] Logout, auth gate + landing page, collapsible sidebar with profile block. UI is a
      **deliberate first pass** — the owner has deferred refining it.

**Done when:** you can run your real job-search workflow end-to-end in the browser locally
— add companies and applications, log every interview stage, and the stats / follow-ups /
upcoming-interviews views are correct.

**Gotchas:**
- The session cookie only rides along if requests are **same-origin** — that's what the
  Vite proxy is for in dev. Don't call `http://localhost:8080` directly from the SPA.
- **"The proxy makes OAuth work" is only two-thirds true, and the missing third will cost
  you an afternoon.** Cookies ignore ports, so a session cookie set by `localhost:8080` *is*
  sent from `localhost:5173` — that part is fine. But if the registered redirect URI points
  at `:8080`, Google sends the *browser* there after login and you end up staring at a bare
  API on port 8080 instead of your SPA. The fix, all three parts: (1) register
  `http://localhost:5173/login/oauth2/code/google` in the Google client, (2) set
  `spring.security.oauth2.client.registration.google.redirect-uri` to match on the `local`
  profile (Phase 2), (3) proxy `/login` in `vite.config.ts` so the callback reaches Spring,
  and set the OAuth `successHandler` to an **absolute** URL from `app.base-url` so you land
  back on the SPA. **Not** a root-relative `/` — behind the proxy that resolves against
  `:8080`, the user hits `denyAll()`, and Spring returns a bare 403 that looks exactly like
  a failed login even though login succeeded. (Found the hard way — CLAUDE.md §6.)
- TanStack Query caches aggressively; after a mutation you must `invalidateQueries` for the
  affected keys or the UI shows stale data.
- Google OAuth "Testing" mode consent screen is fine for one user; you don't need to
  publish/verify the app.

---

## Phase 4 — Deploy

**Objective:** the API and SPA live at `https://<your-domain>`, deployed automatically from
`main` by GitHub Actions, backed by Atlas, with a nightly off-box backup. Start using it
for real.

**Prerequisites:** Phases 1–3; Phase 0 infra (VPS, domain, Atlas allowlist, Google prod
redirect URI).

### Backfill your existing search

Do this *first*, against local Mongo, before the deploy — it is the fastest way to find
schema and validation problems, and the app is useless for dogfooding while it's empty.

> **Done 2026-09-02, but out of order and against the deployed app**, not local Mongo. The
> deploy went first and this followed, which is the inversion this section exists to prevent.
> It cost nothing this time — three applications entered through the live UI, no schema or
> validation problems surfaced — but the reason for the original ordering stands: a schema
> problem found here is cheap, and the same problem found after data is in Atlas is not.

- [x] ~~Write `backend/src/test/http/backfill.http`~~ — entered through the live UI instead,
      which also served as the Phase 3 dogfooding pass. Three applications, deliberately
      differing in status so the funnel, follow-ups and filters had something to show.
- [x] Expect to be entering **historical** interviews — stages that already happened. This
      is why `scheduledAt` is not `@Future` (`SCHEMA.md §8.1`); if a past-dated stage is
      rejected, that constraint crept back in. *(It was not.)*
- [x] Sanity-check the result against `GET /api/stats` — if the funnel or response rate
      looks wrong, it's cheaper to fix the aggregation now than after the data is in Atlas.

### Server baseline (`deploy/RUNBOOK.md` — write it as you go)
- [x] DNS: A record `@` (and `www`) → the reserved public IP.
- [x] `apt` install: `openjdk`? no — install **Temurin 25** from the Adoptium apt repo (or
      SDKMAN). `nginx`, `certbot`, `python3-certbot-nginx`, `rclone`,
      `mongodb-database-tools` (for `mongodump`).
- [x] Confirm the 4 GB swap file from Phase 0 is active (`free -m`, `swapon --show`) and
      that `vm.swappiness=10` survived a reboot.
- [x] Create service user `jobtracker` (no login shell); dirs `/opt/jobtracker`,
      `/etc/jobtracker`, `/var/www/jobtracker`, `/var/backups/jobtracker`.
- [x] Create a **deploy user** for CI with its own SSH key, and give it write access to
      `/opt/jobtracker` and `/var/www/jobtracker` (group ownership is enough). CI logs in
      as this user, not as `ubuntu` and not as `jobtracker`.
- [x] `/etc/sudoers.d/jobtracker-deploy`: a **`NOPASSWD` entry scoped to exactly**
      `/usr/bin/systemctl restart jobtracker` for the deploy user. Not blanket sudo. Without
      this the CI restart step fails on a password prompt — a very common first-deploy stall.
- [x] Basic hardening: `PasswordAuthentication no` in `sshd_config`, `unattended-upgrades`
      enabled, `fail2ban` installed. The box is on the public internet with a single
      purpose; this is 10 minutes.
- [x] `/etc/jobtracker/jobtracker.env` (mode 600, owner `jobtracker`): all prod env vars
      from `CLAUDE.md §8` (`SPRING_PROFILES_ACTIVE=prod`, `MONGODB_URI`,
      `GOOGLE_CLIENT_ID/SECRET`, `APP_ALLOWED_EMAILS`, `APP_MCP_TOKEN`, `APP_BASE_URL`,
      `DD_API_KEY`, `DD_SITE`).

### systemd
- [x] `deploy/jobtracker.service`: `ExecStart=/usr/bin/java -Xmx256m -XX:MaxMetaspaceSize=128m
      -Xss512k -jar /opt/jobtracker/app.jar`, `EnvironmentFile=/etc/jobtracker/jobtracker.env`,
      `User=jobtracker`, `Restart=on-failure`, `SuccessExitStatus=143`. Install to
      `/etc/systemd/system/`, `daemon-reload`, `enable --now`.
      **Explicit `-Xmx`, not `MaxRAMPercentage`** on a 1 GB box: 50% would be a 512 MB heap
      with nothing left for metaspace, thread stacks, code cache, Nginx and the OS.
      **SerialGC**, which the JVM already picks on a 1-core sub-2 GB machine — don't
      override it (`CLAUDE.md §6`).
- [x] Add `MemoryHigh=700M` and `MemoryMax=850M` to `jobtracker.service`. With 4 GB of swap
      and no cap, a runaway thrashes for a long time and takes SSH down with it; with a cap,
      the JVM alone dies and `Restart=on-failure` brings it back in seconds. Tune the
      numbers once you have seen real RSS.
- [x] After first deploy, check real usage: `systemctl status jobtracker` for RSS, `free -m`
      for swap, and `vmstat 5` — the **`si`/`so` columns are the ones that matter**. Steady
      non-zero swap-in/out means the app is living in swap: drop `-Xmx` rather than adding
      more swap. A large `free -m` swap-used number with `si`/`so` at zero is harmless —
      that is just cold pages parked, which is exactly what swap is for.
- [x] Consider `-XX:TieredStopAtLevel=1` if startup is painfully slow — it trades
      steady-state throughput for faster warmup, a good deal for one user.

### Nginx + TLS
- [x] `deploy/nginx-jobtracker.conf`: server for `<your-domain>`; `root
      /var/www/jobtracker`; `location /` → `try_files $uri /index.html` (SPA routing);
      `location ~ ^/(api|oauth2|login)/` → `proxy_pass http://127.0.0.1:8080` with
      `proxy_set_header` for Host, X-Forwarded-For/Proto.
- [x] Security headers in the vhost: `Strict-Transport-Security` (after TLS is confirmed
      working), `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-
      cross-origin`, `X-Frame-Options: DENY`. Plus a `limit_req` zone on `/api` — a few
      lines, and the box is publicly reachable.
- [x] `certbot --nginx -d <your-domain> -d www.<your-domain>`; confirm auto-renew timer.
- [x] Spring: set `server.forward-headers-strategy=framework` so the OAuth redirect builds
      `https://` URLs behind Nginx.

### Atlas / Google
- [x] Confirm the VPS public IP is in the Atlas allowlist.
- [x] Confirm `https://<your-domain>/login/oauth2/code/google` is an authorized redirect
      URI in the Google client.

### CI/CD (`.github/workflows/`)
- [x] `backend.yml` on push to `main` touching `backend/**`:
      `mvn -B verify` (Testcontainers works on GitHub runners; this already produces the
      jar — don't follow it with a second `package -DskipTests` build) → `scp`
      `target/*.jar` to `/opt/jobtracker/app-<sha>.jar`, symlink `app.jar` → new file, keep
      the last 3 → `ssh sudo systemctl restart jobtracker` → poll `/actuator/health` until
      `UP` (with a timeout) to verify.
- [x] `frontend.yml` on push to `main` touching `frontend/**`: `npm ci` → `npm run build`
      → `rsync --delete dist/` to `/var/www/jobtracker`.
- [x] GitHub secrets: `SSH_PRIVATE_KEY`, `SSH_HOST`, `SSH_USER`. Use a deploy-only SSH key.
- [x] `VITE_*` build-time config if any (base URL is same-origin `/api`, so likely none).

### Backups (`deploy/backup-mongo.*`) — **DEFERRED, not done**

> Deferred by decision on 2026-09-02. Atlas M0 has no automated backups, no undelete and
> no point-in-time restore, so until this is picked up the job search lives in exactly one
> place — and `CLAUDE.md §3` names this cron as the reason M0 was acceptable over
> self-hosting at all, which leaves that argument currently unbacked. Items below are
> marked `[~]` rather than `[ ]` so the phase does not read as merely unfinished.
> `deploy/RUNBOOK.md` Step 12 carries a one-line manual `mongodump` as an interim.
- [~] `backup-mongo.sh`: `mongodump --uri "$MONGODB_URI" --archive --gzip` →
      `/var/backups/jobtracker/jobtracker-$(date +%F).archive.gz`; `rclone copy` to Oracle
      Object Storage (free 20 GB); delete local archives older than 14 days.
- [~] `backup-mongo.service` + `backup-mongo.timer` (daily). `systemctl enable --now`.
      The service runs `User=jobtracker` with
      `EnvironmentFile=/etc/jobtracker/jobtracker.env` — that's where `MONGODB_URI` comes
      from; the script must not carry its own copy of the credentials.
- [~] Configure `rclone` for Oracle Object Storage (S3-compatible). **Make the bucket
      private** and confirm it — the dumps contain recruiter names, emails and phone
      numbers, plus your own comp expectations.
- [~] **Test a restore** into a scratch database and diff a few docs. Record the restore
      steps in `RUNBOOK.md`.

**Done when:** `https://<your-domain>` loads the SPA; Google login works; you can do full
CRUD against the live app; pushing to `main` auto-deploys within a few minutes;
`/actuator/health` is `UP`. ~~a backup archive has landed in Object Storage and a test
restore succeeded~~ — **deferred, see above**. **You start entering real applications.**

**Status (2026-09-02): met, except backups and the backfill.** Live at
`https://app4jobtrack.me`, TLS via certbot, both GitHub Actions workflows deploying from
`main`, health `UP`, Datadog metrics flowing. Still open: the backfill at the top of this
phase was skipped, so the app is deployed but empty — that and the Phase 3 dogfooding pass
are the remaining work before this phase is genuinely finished.

**Gotchas:**
- Oracle's double firewall (Security List **and** instance iptables) is the #1 "site
  unreachable" cause. Verify both.
- If the reserved IP ever changes (don't let it), the Atlas allowlist and TLS both break.
- The shape is x86_64 now, so architecture caveats mostly disappear — install the x64
  Temurin build.
- **Memory is the binding constraint on this box, not CPU.** Rough budget for 1 GB: OS
  ~150–250 MB, Nginx ~15 MB, JVM RSS ~450–550 MB with `-Xmx256m` (heap plus metaspace,
  code cache and thread stacks). That leaves very little slack, which is what the swap file
  is for. If you see the OOM killer in `dmesg`, the JVM is the thing it will kill.

---

## Phase 5 — Datadog

**Objective:** custom metrics flowing continuously from the deployed instance, a dashboard
with 3+ widgets, and one alert. Screenshots captured for the README, labelled with where
they came from.

> **No APM, and no Agent anywhere.** Tracing was dropped from the project on 2026-09-02
> after Phase 0 established that no APM trial is offerable on the student Pro plan. See
> `CLAUDE.md §6` ("Tracing dropped") and `§14`. This phase is metrics, a dashboard and an
> alert — nothing else.

**Prerequisites:** Phase 4 live and receiving some real traffic; the Datadog **Pro** plan
confirmed in Phase 0.

> **What the student pack changes** (`CLAUDE.md §6`). Pro gives ~13-month metric retention
> instead of the free tier's 1 day — the difference between an "applications created over
> time" widget that tells a story and one that shows yesterday. That retention is the whole
> reason this phase is worth doing.
>
> Host slots being free once suggested installing the Agent permanently; the change to a
> single 1 GB micro reversed that, and dropping tracing settled it. **Host infra metrics
> are the casualty** — CPU/memory/disk for the box are not in Datadog. The app's own custom
> metrics are unaffected, because Micrometer pushes them straight to the API over HTTPS and
> never needed an Agent.

### Custom metrics (permanent)
- [x] Configure the Micrometer Datadog registry in `application-prod.yml`:
      `management.datadog.metrics.export.api-key=${DD_API_KEY}`,
      `...uri=https://api.${DD_SITE}`, a sensible `step` (e.g. 30s).
- [x] Custom metrics:
  - `jobtracker.applications.created` — counter, in `ApplicationService.create`, incremented
    **after** the save so rejected attempts do not count.
  - `jobtracker.stages.added` — counter, tagged by `stage_type` (closed enum, so cardinality
    cannot drift).
  - ~~`jobtracker.api.request.duration`~~ — **aspiration dropped, as this bullet anticipated.**
    `http.server.requests` is kept instead, with its templated `uri` tag, which answers
    "which endpoint is slow" without inventing a second timer. No `@Timed` needed.
  - `jobtracker.api.errors` — counter, in `GlobalExceptionHandler.problem()`, tagged by
    `status` only.
- [x] **Stay inside the ~100-timeseries budget.** Pro allots 100 custom metrics per host and
    you have one host; Datadog counts unique metric-name + tag-value combinations, and
    Micrometer expands a single timer into several metrics (count/sum/avg/max). Exporting
    `http.server.requests` unfiltered (`uri × method × status × outcome × exception`) can
    eat most of the budget alone. Add a `MeterFilter` that drops `outcome` and `exception`
    and keeps the templated `uri`, or don't export it at all. Never tag by application id.
    **Done in `config/MetricsConfig`, and it went further than this bullet:** the filter is
    an **allowlist**, not a blocklist. Boot's default binders (`jvm.gc.*`,
    `jvm.threads.states`, `tomcat.*`, `logback.events`, the Mongo driver's pool listeners)
    exceed 100 series on an idle app without anyone choosing to spend it, and a blocklist
    means the next dependency that ships a binder quietly enlarges the bill. `MetricsIT`
    probes the filter directly rather than asserting on whichever binders happen to be
    present.
- [x] Redeploy; confirm metrics appear in Datadog Metrics Explorer. **Site is `us5`, not
      `datadoghq.com`** — a key is valid only on its own site and a mismatch is rejected with
      no error the app surfaces and no data in the UI (`CLAUDE.md §6`, `STATE.md §4`).
- [ ] **Still open, and time-gated:** check *Plan &amp; Usage → Custom Metrics* for the real
      series count once traffic has run for **a day**. The budget table in `MetricsConfig` is
      arithmetic; that page is truth. A series only exists once its tag combination has
      occurred, so checking early under-reports. If it is near 100, `ALLOWED_THIRD_PARTY` is
      the lever and `jvm.memory.used` (~16 series) is the first thing to drop.

### Datadog Agent — nowhere in production

Two constraints combine: the Agent needs ~0.5 GB, and there is exactly **one** 1 GB host,
already carrying a JVM at ~500 MB plus Nginx and the OS. There is nowhere to put it
(`CLAUDE.md §6`). Custom metrics are unaffected — Micrometer pushes straight to the Datadog
API over HTTPS and never needed an agent.

- [x] Do **not** `apt install datadog-agent` on the VPS. If you try it anyway, the box will
      swap continuously on network-attached storage and the app will be visibly slow — you
      would be breaking the running app to produce a screenshot.
- [x] Accept the one loss: **no host infra metrics** (CPU/memory/disk) for the app box in
      Datadog. The dashboard is built from application metrics instead, which is the more
      interesting half anyway.

### APM — dropped

Phase 0 established (2026-09-02) that no APM trial is offerable on the student Pro plan, and
the response was to drop tracing from the project rather than stand up a second Datadog org
or add OpenTelemetry. Nothing to do here. Reasoning and the rejected alternatives are in
`CLAUDE.md §6` ("Tracing dropped"); it is a named non-goal in `§14`.

If distributed tracing is ever wanted, OpenTelemetry to a local Jaeger/Tempo container is
the route — not this.

### Dashboard + alert
- [x] Dashboard "Job Tracker — API". **Not p50/p95/p99** — Micrometer publishes no
      percentiles unless `management.metrics.distribution.percentiles` is set, and enabling
      them across `uri × method × status` would cost ~90 series against a 100 budget. The
      registry gives `count`, `avg`, `max` per timer, and **`max` by `uri`** is the widget
      that matters: it isolates `/api/stats` without one slow aggregation being averaged
      away. Percentiles scoped to that single endpoint remain available if ever wanted.
- [x] Monitor: **API error rate > 5% over 5 minutes** → notify (email). Note in the
      dashboard description what the threshold is based on and that it needs validation
      against real traffic (interview talking point).
- [ ] Screenshot the dashboard for the README — Phase 7 work, and worth leaving until
      there is more than a few days of data behind it. Label it as the deployed instance and
      say what is absent and why (no APM, no host infra metrics).

**Done when:** custom metrics from the deployed instance are visible in Datadog; the
dashboard is built from those metrics; the error-rate monitor exists and has been
test-fired (temporarily lower the threshold or generate errors to see it alert); the
custom-metric series count is comfortably under 100. No wind-down step: nothing here is on
a clock, because nothing here is a trial.

**Status (2026-09-02): complete.** Three domain counters and an allowlist `MeterFilter`
shipped and deployed; metrics arriving on **us5**; dashboard and error-rate monitor built in
the Datadog console. The series-count check above is the one item left, and it is left
deliberately — it needs a day of traffic to mean anything.

**Gotchas:**
- **Do not be tempted to put the Agent on the VPS "just for an hour"** to get host infra
  metrics. One 1 GB host already running a JVM has no room; it will swap on network-attached
  storage and the app will be visibly degraded. Breaking the running app to produce a
  screenshot is a bad trade, and the screenshot would show a pathologically slow service
  anyway.
- The Micrometer registry counts as "custom metrics" for billing, and the budget is per
  *host* — one host, ~100 series. A handful of deliberate low-cardinality metrics fits
  easily; one unfiltered Actuator timer does not.
- Metric retention is ~13 months on Pro, but only from when the metric first arrives. A
  widget you want in the README needs data behind it — wire the metrics up early in the
  phase and build the dashboard at the end of it.

---

## Phase 6 — MCP server

**Objective:** a local TypeScript MCP server exposing 4 read-only tools that Claude Desktop
uses to answer natural-language questions about the job search, calling the deployed API
with the bearer token.

**Prerequisites:** Phase 4 (deployed API); `APP_MCP_TOKEN` value; Claude Desktop installed.

> **New-ground notes (owner is new to MCP).** What MCP is: an open protocol that lets an
> LLM client (Claude Desktop) discover and call *tools* you define. You write a small
> server; Claude Desktop launches it as a subprocess and talks to it over stdin/stdout
> ("stdio transport"). Each *tool* is: a name, a description Claude reads to decide when to
> use it, an input schema (a zod object), and a handler function. Your handler just calls
> your REST API and returns the JSON as text. Claude does all the language understanding —
> you only provide clear tool names, descriptions, and schemas. **Design rule:** four
> narrow tools beat one "run any query" tool — Claude picks the right one and you control
> exactly what data leaves the API.

### Setup
- [x] `mcp-server/`: `npm init`, install `@modelcontextprotocol/sdk`, `zod`; dev deps
      `typescript`, `tsx`, `@types/node`. SDK **1.30.0**, zod **4.5.4**, TypeScript
      **~6.0.2** — matching `frontend/`'s versions rather than taking TS 7, so the repo has
      one TypeScript line to reason about, not two.
- [x] `tsconfig.json` (`strict`, `module: nodenext`, `target: es2022`).
      **`"types": ["node"]` must be set explicitly.** Without it `process`, `fetch`,
      `Response`, `URL` and `AbortSignal` are all "Cannot find name", because `lib` here is
      `es2023` with no DOM and @types/node is not picked up implicitly.
- [x] `.env` (gitignored): `API_BASE_URL=https://<your-domain>`, `API_TOKEN=<APP_MCP_TOKEN>`.
      Read straight off the VPS into the file without passing through a terminal buffer.
      `npm run dev` passes `node --env-file=.env`; no `dotenv` dependency needed. Claude
      Desktop does not read it — it passes its own `env` block.
- [x] Branch `phase-6-mcp`.

### Implementation (`src/`)
- [x] `apiClient.ts` — `fetch` wrapper: prepends `API_BASE_URL`, sets
      `Authorization: Bearer ${API_TOKEN}`, throws on non-2xx with the response body,
      returns parsed JSON. Typed against the same DTO shapes as the backend.
- [x] `index.ts` — create an **`McpServer`** (the SDK's high-level API: `registerTool`
      handles the `list_tools` / `call_tool` plumbing and the zod-to-JSON-Schema conversion
      for you) and connect a `StdioServerTransport`. The low-level `Server` class with
      hand-written request handlers also works, but it's more code for no benefit here.
- [x] `tools/getApplicationStats.ts` — input `{ days?: number, from?: string, to?: string }`
      → `GET /api/stats` → a compact text summary (counts by status, funnel,
      response/ghost/offer rates). **The tool description must tell Claude that "this
      month" means a calendar month and should be sent as `from`/`to`, while `days` is a
      rolling window** — and the output should state which window it used, so a rolling
      30-day count is never reported as "this month".
- [x] `tools/listPendingFollowups.ts` — no input → `GET /api/applications/followups` →
      list company, role, `followUpDate`, days overdue. The response has two groups —
      follow-ups *due* and pipelines *gone quiet* (`lastContactAt` older than 14 days) —
      label them separately in the text; the second is what answers "which companies
      haven't I heard back from in 2+ weeks?".
- [x] `tools/searchApplications.ts` — input `{ query: string }` →
      `GET /api/applications?q=` → top ~15 as company / role / status / current stage /
      applied date. Partial words work (the backend uses a regex, not `$text`), so
      "everything related to Stripe" and "strip" both hit.
- [x] `tools/getUpcomingInterviews.ts` — input `{ days: number }` (default 7) →
      `GET /api/applications/interviews?days=` → list datetime, company, role, stage type,
      format, interviewers.
- [x] Each tool: zod schema, a clear description written for Claude ("Use this when the
      user asks about ... "), graceful error text if the API call fails.

### Testing & wiring
- [x] ~~`npx @modelcontextprotocol/inspector`~~ — exercised with a **scripted stdio client**
      instead (`initialize` → `tools/list` → `tools/call` for all four tools against the
      live API, plus four failure scenarios). The Inspector is an interactive browser UI and
      cannot assert; a script can, and it also proved stdout stays pure protocol, which is
      the failure mode that matters here. `npm run inspect` is still wired up for poking by
      hand.
- [x] Build: `tsc` → `dist/index.js`. Claude Desktop is pointed at the built file rather
      than `tsx`, so the tool path has no dev dependency in it.
- [x] Add to `claude_desktop_config.json`
      (macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`):
      ```json
      { "mcpServers": {
          "job-tracker": {
            "command": "node",
            "args": ["/absolute/path/to/mcp-server/dist/index.js"],
            "env": { "API_BASE_URL": "https://<your-domain>", "API_TOKEN": "<APP_MCP_TOKEN>" }
      } } }
      ```
- [x] Verified the configured command works the way Claude Desktop will run it: the exact
      `command`/`args`/`env` from the config, launched with `PATH=/usr/bin:/bin` from `/`,
      completes a handshake and returns live data. That shows an **absolute path to `node`**
      is robust; it does *not* show a bare `"node"` would fail, and Desktop's own log shows
      it building a PATH that would find one. See `CLAUDE.md §6` for the trade-off under
      nvm.
- [ ] **Restart Claude Desktop; confirm the tools appear.** Needs a human — the config is
      written and validated, but Desktop only reads it at startup.
- [ ] Run the example queries and save transcripts:
  - "How many applications have I sent this month?"
  - "Which companies haven't I heard back from in 2+ weeks?"
  - "What interviews do I have this week?"
  - "Show me everything related to Stripe."

**Done when:** all four tools work from Claude Desktop against the live API; the example
queries return correct answers; transcripts are saved for the README; the bearer token is
confirmed read-only (a tool cannot mutate data even if asked).

**Status (2026-09-02): built, tested against the live API, and wired in — not yet driven
from Claude Desktop itself.** All four tools return correct data over a real stdio
handshake, and the read-only guarantee is confirmed against the deployed instance:
`POST` / `PUT` / `PATCH` / `DELETE` with the MCP token all return **403** and nothing was
created. What is left needs a human: restart Claude Desktop, confirm the tools appear, run
the four example queries, save the transcripts.

**Gotchas:**
- MCP stdio servers must not write anything but protocol messages to **stdout** — send all
  logging to **stderr**.
- The server runs on your laptop and talks to the *deployed* API over HTTPS; it does not
  need to run on the VPS.
- If a tool schema or description is vague, Claude will call the wrong tool or pass bad
  args — iterate on the wording.
- Keep the token out of git; it lives only in `.env` and the Claude Desktop config.

---

## Phase 7 — README & polish

**Objective:** a public-facing `README.md` that tells the whole story, plus interview prep
notes.

**Prerequisites:** Phases 1–6 done and deployed.

- [~] `README.md` — **written 2026-09-02**, complete except the captured assets. Five slots
      are marked with HTML comments and `docs/CAPTURE.md` says exactly what to capture and
      where to save it; `grep -n "SCREENSHOT\|TRANSCRIPT" README.md` lists what is left.
      The two image tags are commented out rather than left dangling, so nothing renders as a
      broken image before the files exist.
  - One-paragraph what/why; live URL.
  - Architecture diagram (reuse `CLAUDE.md §4`, cleaned up — or a real diagram).
  - Stack table.
  - Data model summary (link `SCHEMA.md`).
  - Local setup (backend, frontend, mcp) — the commands from `CLAUDE.md §9`.
  - Deployment overview (link `deploy/RUNBOOK.md`).
  - Datadog dashboard screenshot, from the deployed instance. **Say what is not there and
    why**: no APM, because it is a separately-billed SKU with no trial offerable on the
    student Pro plan, and no host infra metrics, because the free 1 GB host cannot run the
    Agent alongside the JVM. Being explicit about a constraint you reasoned through reads better
    than an unqualified claim that invites an awkward question).
  - MCP: the config snippet + 2–3 example query transcripts.
  - Screenshots of the dashboard UI.
  - *Added, not in the original list:* a note that the live link is **single-user behind an
    email allowlist**, so a visitor lands on the landing page and cannot get in. Without it
    the deployment reads as broken to exactly the audience the link exists for.
- [ ] `deploy/RUNBOOK.md` finalized (server setup, deploy, rollback, restore-from-backup).
- [ ] Fill in `CLAUDE.md §9` with the now-real commands; set `CLAUDE.md §2` to "shipped".
- [ ] Interview prep notes (`docs/interview-notes.md` or a private doc):
  - Why MongoDB fit this model (status-dependent stage fields, evolving pipeline shape)
    vs. a relational `applications` table — and why the company/application split uses
    reference while stages use embed.
  - What the Datadog alert threshold was based on and how you'd validate it with real
    traffic.
  - How the MCP tool boundaries were chosen (4 narrow tools vs. one query tool).
  - Concrete dogfooding examples — real insights the app/MCP surfaced about your search.
- [ ] Final polish pass: consistent error handling, empty states in the UI, a favicon,
      dependency versions bumped. (Actuator exposure and Swagger-off-in-prod were set in
      Phase 1 — verify them here rather than doing them here.)
- [ ] Re-read `SCHEMA.md` against the shipped code one final time and reconcile any drift;
      it is the document a reader will trust most.

**Done when:** a stranger can read the README and understand what was built, see it
running, and see the Datadog + MCP evidence; you can speak to every interview talking
point with specifics from your own usage.

---

## Cross-cutting checklist (keep true throughout)

- [ ] No secrets in git. `application-local.yml` (if sensitive) and all `.env` files are
      gitignored.
- [ ] Every new decision or reversal gets a dated entry in `CLAUDE.md §6`.
- [ ] `mvn verify` stays green on `main`.
- [ ] Every endpoint has at least one integration test (happy path + primary failure).
- [ ] `SCHEMA.md` and the code agree — reconcile immediately on any drift.
- [ ] `STATE.md` is updated at the end of each session — branch, what is built, what is
      next, and any new trap worth not rediscovering.
- [ ] `CLAUDE.md §2` reflects the current phase at the end of each session.
- [ ] Commit messages end with the `Claude-Session:` trailer carrying **the current
      session's URL** — a new one each session, not one URL copied forever
      (`CLAUDE.md §11`).
- [ ] Datadog custom-metric series stay under ~100 (`CLAUDE.md §6`).
