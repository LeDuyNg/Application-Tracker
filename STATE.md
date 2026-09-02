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

**Current phase:** Phase 2 (authentication) — **code complete**. One acceptance criterion
cannot be checked until Phase 3 exists; see below.
**Branch:** `phase-2-auth`, cut from `phase-1-backend-crud`. Neither is merged to `main`,
which is still docs-only.

### Done and tested
- **Phase 1, complete** — domain, repositories, DTOs, mappers, `CompanyService`,
  `ApplicationService` (three denormalization rules), `StatsService`,
  `ApplicationQueryService` (follow-ups / interviews / search), 16 endpoints,
  `GlobalExceptionHandler`, and the `.http` collection. Verified against the real Atlas M0.
- **Phase 2** — two `SecurityFilterChain` beans:
  - *bearer chain* (`@Order(1)`, matches an `Authorization: Bearer` header): stateless, CSRF
    off, `GET /api/**` requires `ROLE_MCP`, everything else `denyAll()`. Token compared as
    SHA-256 digests in constant time.
  - *browser chain* (`@Order(2)`): Google OIDC login, `AllowlistOidcUserService` checking the
    allowlist **and** `email_verified`, CSRF cookie with the deferred-token opt-out, RFC 7807
    401/403 instead of redirects, `GET /api/me`.
- **100 tests green** (`./mvnw verify`): 26 unit, 74 integration across seven `*IT` classes.

### Verified against the running app, not just in tests
Unauthenticated `/api` → 401 `problem+json` with **no** `Location` header; the `XSRF-TOKEN`
cookie present on that same rejected response; bearer token reads (200) and cannot write
(403 on POST and DELETE); wrong and empty tokens → 401; `/actuator/health` open; Swagger
reachable on `local`; `/oauth2/authorization/google` → Google with
`redirect_uri=http://localhost:5173/login/oauth2/code/google`, scope `openid profile email`.

### The one thing not verified
**A real Google login round trip.** The `local` redirect URI points at `http://localhost:5173`
— the Vite dev server, which does not exist until Phase 3 — so signing in cannot currently
complete. The allowlist logic is unit-tested in isolation (allowed / other address /
unverified / missing claim / empty list). To try it before Phase 3, register
`http://localhost:8080/login/oauth2/code/google` in the Google client and override
`redirect-uri` to match in `backend/config/application-local.yml`.

This also means the `.http` collection's **write** requests cannot run yet: they need a real
`JSESSIONID`. Every `GET` works now, using the MCP bearer token.

**Suggested next step: Phase 3 — the React SPA.** It unblocks the login round trip as a side
effect. Phase 0's remaining item is the **Datadog** student-pack redemption, and the
APM-trial-availability check inside it is the one task with no recovery path if discovered
late.

### Gaps noticed, deliberately not built
- **No logout endpoint.** `PLAN.md` Phase 2 does not list one and Phase 3 does not either,
  but a login system without one is odd — worth adding when the SPA needs it.

---

## 3. This machine

| Thing | State |
|---|---|
| JDK | **25.0.4.1** via Homebrew `openjdk@25`. **Not** symlinked, so `/usr/libexec/java_home -V` does not list it — that is expected, not a problem. IntelliJ uses it. |
| Shell JDK | Defaults to **26**. `./mvnw` from a terminal cross-compiles with `--release 25`; IntelliJ compiles on 25. Set `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home` in `~/.zshrc` to make them agree. Open item. |
| MongoDB | Docker container `jt-mongo`, **mongo:8.3.8**, on `localhost:27017`. `docker start jt-mongo` after a reboot — `docker run` only ever needs to happen once. |
| IntelliJ | Project opened at the **repo root**, `backend/` imported as a Maven module. Run config **JobTracker (local)** is committed at `.idea/runConfigurations/`. |
| Docker | Required for Testcontainers (`./mvnw verify`). |

### Commands

```bash
cd backend
./mvnw test      # unit only (Surefire)  — fast, no Docker
./mvnw verify    # + *IT (Failsafe)      — needs Docker
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# app: http://localhost:8080 · swagger: /swagger-ui.html · health: /actuator/health
```

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
| Atlas SRV string | Has **no database name** — ends `/?retryWrites=...`. Pasted as-is: `IllegalArgumentException: Database name must not be empty` at startup. Put `jobtracker` between the `/` and the `?`. |
| Commented-out YAML | Uncommenting `# key:` by deleting only the `#` leaves a leading space, shifting the document to indent 1. SnakeYAML then blames a *later* line with "expected '<document start>'". Comment as `#key:` at the right indent so deleting `#` alone is correct. |
| `oauth2Login()` with no client | Boot only creates a `ClientRegistrationRepository` when a registration is configured, so from Phase 2 **every** `@SpringBootTest` fails at context creation without dummy `client-id`/`client-secret`. Set on `AbstractMongoIT` and `JobTrackerApplicationTests`. |
| `LocalDate.now()` in a test | The JVM's default zone, not `app.timezone`. A `daysOverdue` assertion passed all day and failed at 21:00 Pacific, when it became tomorrow in New York. Tests asserting on relative-date boundaries must use `TimeService.today()`. |
| MockMvc on Boot 4 | Needs **`spring-boot-starter-webmvc-test`** — `spring-boot-starter-test` no longer carries the web slice — and `@AutoConfigureMockMvc` moved to `org.springframework.boot.webmvc.test.autoconfigure`. Fails as "package does not exist". |
| `@Testcontainers` + `@Container` | Ties the container to a **test class** — it is stopped when that class ends, so every later `*IT` gets `Connection refused` against a cached port. Start it in a static initializer. |
| `MongoDBContainer` import | Testcontainers 2.x ships **both** `org.testcontainers.mongodb.MongoDBContainer` and the 1.x shim `org.testcontainers.containers.MongoDBContainer`. Both compile. Use the former. |
| `null` in a `$lte` query | `null` is not `$lte` anything, so a document with a null field silently drops out of a range query. Load-bearing for `followUpDate` (wanted) and `lastContactAt` (a bug until it was seeded on create). |

**General lesson:** this project runs Spring Boot 4 / Jackson 3 / Spring Data 5, and most
material online is Boot 3. Verify property names against the config metadata in the jars
rather than trusting a snippet.

---

## 5. Phase 0 — outstanding, and one thing that changed

Phase 0 is **paused, not finished**. None of it blocks Phase 1 except the Atlas item.

- [ ] Domain registration
- [ ] Oracle instance — **shape changed**: `VM.Standard.E2.1.Micro` (1/8 OCPU burstable,
      1 GB RAM, x86), **one instance only**. A1 ARM was unobtainable. See `CLAUDE.md §6`.
- [ ] MongoDB Atlas M0 cluster
- [ ] Google Cloud OAuth client
- [ ] Datadog student-pack redemption, and the **APM-trial-availability check** — the one
      Phase 0 item with no recovery path if discovered late

**Consequence of the 1 GB single host, already propagated through the docs:** no Datadog
Agent in production, therefore no production APM traces. APM is exercised locally during the
trial. The resume bullet and §1 deliverables were narrowed to match — see `CLAUDE.md §6`,
entry "Only one instance". Do not quietly widen those claims back.

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
