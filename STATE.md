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

**Current phase:** Phase 1 (backend CRUD), service layer complete; controllers remain.
**Branch:** `phase-1-backend-crud`, **pushed** and tracking `origin/phase-1-backend-crud`.
`main` on the remote is still the docs-only commit — the branch has not been merged.

### Done and tested
- Domain layer: 6 enums, `Company`/`Contact`, `Application`/`Stage`/`Compensation`.
- `IndexInitializer` — all 9 indexes from `SCHEMA.md §6`, verified present in a real Mongo.
- Repositories, request/response DTOs, hand-written mappers.
- `CompanyService` (rename cascade, 409-on-delete) and `ApplicationService` (CRUD, stage
  add/update/delete, and the three denormalization rules).
- `StatsService` — the `$facet` aggregation plus a second one for average days to first
  response.
- **`ApplicationQueryService`** — the other three read features: follow-ups (both halves),
  upcoming interviews, escaped-regex search. Separate from `ApplicationService` on purpose
  (`CLAUDE.md §6`).
- **62 tests green** (`./mvnw verify`): 21 unit, 41 integration across four `*IT` classes.

### Not started (the rest of Phase 1)
1. **Controllers** — `CompanyController`, `ApplicationController`, `StatsController`, plus
   `GlobalExceptionHandler` (RFC 7807 `ProblemDetail`; the three exception types in
   `common/` already exist and just need mapping). The service layer is finished, so these
   are now genuinely mechanical: each endpoint is a signature over a method that exists.
   Note `StatsController` must reject `days` together with `from`/`to` with a 400, and
   `ApplicationController` resolves a `Pageable` — the service supplies `appliedDate desc`
   when the caller sends no sort.
2. **`ApplicationControllerIT`** — happy path per endpoint, plus 404 and 400.
3. **`backend/src/test/http/jobtracker.http`** — IntelliJ HTTP Client collection.
4. One item is **blocked**: pointing `application-local.yml` at a real Atlas URI, which
   needs the Atlas cluster from Phase 0.

**Suggested next step:** the controllers and `GlobalExceptionHandler` together — the handler
is what turns the exceptions the services already throw into the status codes the ITs assert,
so writing controllers without it means writing the ITs twice.

### Two things found while building the read queries
Both are in `CLAUDE.md §6` with full reasoning; repeated here because each was silent.

- **`lastContactAt` was never set when an application was created with its `stages[]`
  supplied.** `null` is not `$lte` any date, so those applications could never appear in the
  gone-quiet query — and that is exactly the shape of the Phase 4 backfill. The entire
  historical job search would have been invisible to the query it matters most for.
- **`@Testcontainers` + `@Container` stops the container when a test *class* finishes.**
  Latent while only `StatsServiceIT` existed; adding a second `*IT` broke every class after
  the first, `StatsServiceIT` included, with `Connection refused`. `AbstractMongoIT` now
  starts the container in a static initializer instead.

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
