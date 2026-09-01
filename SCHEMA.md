# SCHEMA.md — Data model

MongoDB database: **`jobtracker`** on Atlas M0.
Two collections: **`companies`** and **`applications`**.
`stages[]` is embedded inside each application document.

This file is the authoritative data model. If code and this file disagree, one of them is a
bug — fix the mismatch, don't let it stand.

---

## 1. Modeling philosophy

Two techniques, each used on purpose:

| Relationship | Technique | Why |
|---|---|---|
| `application` → `company` | **Reference** (`companyId` + denormalized `companyName`) | A company has its own identity, is shared across many applications, and its data (contacts, notes) must not duplicate or drift. It also has an independent lifecycle. |
| `application` → its `stages` | **Embed** (`stages[]` array) | A stage is part of the application's identity, the list is small and bounded (3–8 rounds), and you never read a stage without its application. No independent lifecycle. |

This is the MongoDB talking point for interviews: *"I referenced the company because one
company has many applications and shared data shouldn't drift; I embedded the stages
because they're owned by the application, bounded, and always read together with it —
one round trip renders the whole pipeline."*

### Denormalization (and the rule that keeps it honest)

| Denormalized field | Lives on | Source of truth | Sync rule |
|---|---|---|---|
| `companyName` | `applications` | `companies.name` | Updated for all of a company's applications whenever the company is renamed (service-layer logic). |
| `applications.status` | `applications` | the history in `stages[]` + explicit user action | Set by the service when a terminal stage is added (a `FAILED` stage → `REJECTED`, an accepted `OFFER` → `ACCEPTED`) or by an explicit status change. **Terminal statuses are sticky** — see below. |
| `applications.currentStageType` | `applications` | `stages[]` | Recomputed on every stage add/update: the `type` of the stage with the **lowest `sequence`** whose status is `SCHEDULED` or `EXPECTED`; if none, the `type` of the stage with the **highest `sequence`** that is `PASSED`. |
| `applications.lastContactAt` | `applications` | `stages[]` | Set to "now" by the service **only** when a stage is added, or an existing stage's `status`, `scheduledAt` or `completedAt` changes. Never touched by edits to notes, tags, comp, or any other field. |

**Sticky terminal statuses.** `status` is both derived *and* directly settable, which means
a naive "recompute on every stage mutation" would undo explicit user choices — mark an
application `WITHDRAWN`, then fix a typo in an old stage's notes, and it would silently
flip back to `ACTIVE`. Rule: **recompute only when the current status is `ACTIVE` or
`OFFER`.** `ACCEPTED`, `REJECTED`, `WITHDRAWN` and `GHOSTED` are only ever changed by an
explicit status update. `GHOSTED` in particular is **manual-only** — no rule derives it.

**Why `lastContactAt` exists and `updatedAt` is not enough.** `updatedAt`
(`@LastModifiedDate`) bumps on *any* write, so correcting a typo resets it. "Which
companies haven't I heard back from in 2+ weeks?" is one of the four headline queries this
whole project exists to answer, so it needs a field that tracks *contact*, not *editing*.

All denormalization sync happens in the **service layer**, never in controllers, never in
the MCP server (which is read-only anyway).

---

## 2. Collection: `companies`

One document per employer.

| Field | BSON type | Java type | Required | Constraints / notes |
|---|---|---|---|---|
| `_id` | ObjectId | `String` (`@Id`) | auto | |
| `name` | string | `String` | ✓ | **Unique** (see indexes). Canonical name — pick one ("Meta", not "Facebook"). Trimmed. 1–120 chars. |
| `website` | string | `String` | – | URL. Not validated strictly. |
| `industry` | string | `String` | – | Free text ("Fintech", "Big Tech", "Healthcare"). |
| `location` | string | `String` | – | HQ or the office relevant to you. |
| `contacts` | array\<object\> | `List<Contact>` | – | Recruiters / referrers that span roles. See §4.1. |
| `notes` | string | `String` | – | Company-level notes ("strong WLB reputation", "sponsors H1B"). |
| `tags` | array\<string\> | `List<String>` | – | Free tags. Lowercased on save. |
| `createdAt` | date | `Instant` | auto | `@CreatedDate` |
| `updatedAt` | date | `Instant` | auto | `@LastModifiedDate` |

### Example

```json
{
  "_id": { "$oid": "66d5aaaa0000000000000001" },
  "name": "Stripe",
  "website": "https://stripe.com/jobs",
  "industry": "Fintech",
  "location": "Remote / South San Francisco, CA",
  "contacts": [
    {
      "name": "Alex Kim",
      "title": "Technical Recruiter",
      "email": "akim@example.com",
      "phone": null,
      "notes": "Covers all backend reqs; responds within a day"
    }
  ],
  "notes": "Two roles open that fit. High interview bar, strong comp.",
  "tags": ["fintech", "high-bar"],
  "createdAt": { "$date": "2026-08-01T00:00:00Z" },
  "updatedAt": { "$date": "2026-08-20T00:00:00Z" }
}
```

---

## 3. Collection: `applications`

One document per role applied to. This is the collection the dashboard and all four MCP
tools operate on.

| Field | BSON type | Java type | Required | Constraints / notes |
|---|---|---|---|---|
| `_id` | ObjectId | `String` (`@Id`) | auto | |
| `companyId` | ObjectId | `String` | ✓ | Must reference an existing `companies._id`. Validated in the service. |
| `companyName` | string | `String` | ✓ | Denormalized copy of `companies.name`. Kept in sync. |
| `role` | string | `String` | ✓ | Job title as posted. 1–160 chars. |
| `status` | string (enum) | `ApplicationStatus` | ✓ | `ACTIVE` \| `OFFER` \| `ACCEPTED` \| `REJECTED` \| `WITHDRAWN` \| `GHOSTED`. Default `ACTIVE`. Denormalized (see §1). |
| `appliedDate` | date | `LocalDate` | ✓ | The day you applied. Not in the future. |
| `source` | string (enum) | `ApplicationSource` | ✓ | `REFERRAL` \| `COLD_APPLY` \| `RECRUITER_OUTREACH` \| `CAREER_FAIR` \| `NETWORKING_EVENT` \| `JOB_BOARD` \| `COMPANY_WEBSITE` \| `OTHER`. |
| `currentStageType` | string (enum) | `StageType` | – | Denormalized: the round you're in / waiting on. Recomputed on every stage change. |
| `followUpDate` | date | `LocalDate` | – | When to chase. Powers `list_pending_followups`. |
| `jobPostingUrl` | string | `String` | – | |
| `location` | string | `String` | – | Role location / "Remote". |
| `workMode` | string (enum) | `WorkMode` | – | `REMOTE` \| `HYBRID` \| `ONSITE`. |
| `compensation` | object | `Compensation` | – | `{ min, max, currency }`. See §4.2. |
| `notes` | string | `String` | – | Freeform. Searched by the free-text filter (§10.3). |
| `tags` | array\<string\> | `List<String>` | – | Lowercased on save. |
| `stages` | array\<object\> | `List<Stage>` | ✓ (≥ 1) | Ordered rounds. See §4.3. First entry is normally `APPLICATION_SUBMITTED`. |
| `lastContactAt` | date | `Instant` | – | Last time the *process* moved (stage added, or a stage's status/dates changed). **Not** `updatedAt`. Powers the "gone quiet" query — see §1 and §10.2. |
| `createdAt` | date | `Instant` | auto | `@CreatedDate` |
| `updatedAt` | date | `Instant` | auto | `@LastModifiedDate` |

### Example — one company, two applications

```json
// applications — role #1 (in progress)
{
  "_id": { "$oid": "66d5bbbb0000000000000101" },
  "companyId": { "$oid": "66d5aaaa0000000000000001" },
  "companyName": "Stripe",
  "role": "Backend Engineer, Payments",
  "status": "ACTIVE",
  "appliedDate": "2026-08-02",
  "source": "REFERRAL",
  "currentStageType": "TECHNICAL_INTERVIEW",
  "followUpDate": "2026-09-06",
  "jobPostingUrl": "https://stripe.com/jobs/12345",
  "location": "Remote (US)",
  "workMode": "REMOTE",
  "compensation": { "min": 150000, "max": 185000, "currency": "USD" },
  "notes": "Referred by a former colleague. Team owns the ledger service. JD stresses Java + Kafka.",
  "tags": ["referral", "payments"],
  "lastContactAt": { "$date": "2026-08-25T09:00:00Z" },
  "stages": [
    {
      "stageId": "d1f0c6e2-0001", "sequence": 1, "type": "APPLICATION_SUBMITTED",
      "status": "PASSED", "scheduledAt": null,
      "completedAt": { "$date": "2026-08-02T00:00:00Z" },
      "format": null, "interviewers": [], "notes": null
    },
    {
      "stageId": "d1f0c6e2-0002", "sequence": 2, "type": "ONLINE_ASSESSMENT",
      "status": "PASSED", "scheduledAt": { "$date": "2026-08-06T00:00:00Z" },
      "completedAt": { "$date": "2026-08-07T18:00:00Z" },
      "format": "ASYNC", "interviewers": [], "notes": "HackerRank, 2 medium problems, all cases passed"
    },
    {
      "stageId": "d1f0c6e2-0003", "sequence": 3, "type": "RECRUITER_SCREEN",
      "status": "PASSED", "scheduledAt": { "$date": "2026-08-12T15:00:00Z" },
      "completedAt": { "$date": "2026-08-12T15:30:00Z" },
      "format": "PHONE", "interviewers": ["Alex Kim"], "notes": "Comp expectations aligned"
    },
    {
      "stageId": "d1f0c6e2-0004", "sequence": 4, "type": "TECHNICAL_INTERVIEW",
      "status": "SCHEDULED", "scheduledAt": { "$date": "2026-09-04T16:00:00Z" },
      "completedAt": null,
      "format": "VIDEO", "interviewers": ["Priya N. (Staff Eng)"], "notes": "45 min coding + 15 min API design"
    }
  ],
  "createdAt": { "$date": "2026-08-02T00:00:00Z" },
  "updatedAt": { "$date": "2026-08-25T09:00:00Z" }
}
```

```json
// applications — role #2, same company (rejected early)
{
  "_id": { "$oid": "66d5bbbb0000000000000102" },
  "companyId": { "$oid": "66d5aaaa0000000000000001" },
  "companyName": "Stripe",
  "role": "Infrastructure Engineer",
  "status": "REJECTED",
  "appliedDate": "2026-08-02",
  "source": "COMPANY_WEBSITE",
  "currentStageType": "ONLINE_ASSESSMENT",
  "followUpDate": null,
  "workMode": "HYBRID",
  "compensation": null,
  "notes": "Applied same day as the Payments role.",
  "tags": [],
  "lastContactAt": { "$date": "2026-08-09T11:00:00Z" },
  "stages": [
    {
      "stageId": "a7b3ee10-0001", "sequence": 1, "type": "APPLICATION_SUBMITTED",
      "status": "PASSED", "scheduledAt": null,
      "completedAt": { "$date": "2026-08-02T00:00:00Z" },
      "format": null, "interviewers": [], "notes": null
    },
    {
      "stageId": "a7b3ee10-0002", "sequence": 2, "type": "ONLINE_ASSESSMENT",
      "status": "FAILED", "scheduledAt": { "$date": "2026-08-06T00:00:00Z" },
      "completedAt": { "$date": "2026-08-06T20:00:00Z" },
      "format": "ASYNC", "interviewers": [], "notes": "Ran out of time on Q2"
    }
  ],
  "createdAt": { "$date": "2026-08-02T00:00:00Z" },
  "updatedAt": { "$date": "2026-08-09T11:00:00Z" }
}
```

---

## 4. Embedded sub-documents

### 4.1 `Contact` (in `companies.contacts[]`)

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | ✓ | |
| `title` | string | – | "Technical Recruiter", "Hiring Manager" |
| `email` | string | – | Bean Validation `@Email` when present |
| `phone` | string | – | |
| `notes` | string | – | |

### 4.2 `Compensation` (in `applications.compensation`)

| Field | Type | Required | Notes |
|---|---|---|---|
| `min` | int (`Integer`) | – | Annual base, whole currency units |
| `max` | int (`Integer`) | – | `max >= min` when both present (service-validated) |
| `currency` | string | – | ISO 4217, e.g. `USD`. Default `USD` if `min`/`max` set and currency omitted. |

The whole object is optional. A string-only note in `applications.notes` is an acceptable
substitute early on.

### 4.3 `Stage` (in `applications.stages[]`) — the "process" / round

| Field | BSON type | Java type | Required | Constraints / notes |
|---|---|---|---|---|
| `stageId` | string | `String` | ✓ | UUID generated on creation. Stable handle for `PATCH .../stages/{stageId}`. |
| `sequence` | int | `int` | ✓ | 1-based order. Explicit (a stage can be `EXPECTED` before it has a date). Unique within the array; service keeps them contiguous. |
| `type` | string (enum) | `StageType` | ✓ | See §5. |
| `status` | string (enum) | `StageStatus` | ✓ | See §5. Default `EXPECTED` for a stage you know is coming, `SCHEDULED` if you set a `scheduledAt`. |
| `scheduledAt` | date | `Instant` | – | Date+time, UTC. `null` = known round, not yet scheduled. Required to be non-null when `status = SCHEDULED`. |
| `completedAt` | date | `Instant` | – | Set when `status` becomes `PASSED` / `FAILED`. |
| `format` | string (enum) | `StageFormat` | – | `PHONE` \| `VIDEO` \| `ONSITE` \| `ASYNC`. |
| `interviewers` | array\<string\> | `List<String>` | – | "Name (role)" strings. Free text. |
| `notes` | string | `String` | – | Prep notes / feedback / debrief. |

**Superday:** by default one stage with `type = SUPERDAY`, several `interviewers`, one
`status`. To track each interview of the superday separately, add multiple stages
(`sequence` 5, 6, 7…) with specific types (`TECHNICAL_INTERVIEW`, `SYSTEM_DESIGN`,
`BEHAVIORAL`, `HIRING_MANAGER`). No schema change needed.

---

## 5. Enums

Stored in BSON as the **enum constant name** (string). Spring Data does this by default;
it's readable in the IntelliJ Database tool window and stable across code refactors.

**`StageType`'s order below is canonical.** It is the pipeline's progression order, the
stats funnel renders in it, and the Java enum must declare its constants in exactly this
order. `CLAUDE.md §7` repeats the list — keep the three in sync.

| Enum | Java type | Values | Meaning |
|---|---|---|---|
| Application status | `ApplicationStatus` | `ACTIVE` | In progress. |
| | | `OFFER` | Offer extended, not yet decided. |
| | | `ACCEPTED` | You accepted an offer. Terminal. |
| | | `REJECTED` | Company rejected you. Terminal. |
| | | `WITHDRAWN` | You pulled out. Terminal. |
| | | `GHOSTED` | No response for a long time; you've given up. Terminal-ish. |
| Application source | `ApplicationSource` | `REFERRAL`, `COLD_APPLY`, `RECRUITER_OUTREACH`, `CAREER_FAIR`, `NETWORKING_EVENT`, `JOB_BOARD`, `COMPANY_WEBSITE`, `OTHER` | How the application originated. |
| Work mode | `WorkMode` | `REMOTE`, `HYBRID`, `ONSITE` | |
| Stage type | `StageType` | `APPLICATION_SUBMITTED` | The initial submission. Normally `stages[0]`. |
| | | `RECRUITER_SCREEN` | Non-technical intro call. |
| | | `ONLINE_ASSESSMENT` | Automated coding/aptitude test. |
| | | `TAKE_HOME` | Take-home assignment. |
| | | `PHONE_SCREEN` | Technical phone screen. |
| | | `TECHNICAL_INTERVIEW` | Live coding / technical round. |
| | | `SYSTEM_DESIGN` | Design round. |
| | | `BEHAVIORAL` | Behavioral / values round. |
| | | `HIRING_MANAGER` | HM conversation. |
| | | `SUPERDAY` | Onsite loop / final panel (one or many). |
| | | `TEAM_MATCH` | Post-loop team matching. |
| | | `REFERENCE_CHECK` | |
| | | `OFFER` | Offer stage (paperwork, negotiation). |
| | | `OTHER` | Anything else. |
| Stage status | `StageStatus` | `EXPECTED` | Known to be coming, no date yet. |
| | | `SCHEDULED` | Has a `scheduledAt` in the future. |
| | | `PASSED` | Completed, advanced. |
| | | `FAILED` | Completed, did not advance. |
| | | `CANCELLED` | Called off, won't happen. |
| | | `RESCHEDULED` | Moved; a newer stage entry supersedes this one. |
| | | `NO_RESPONSE` | Ghosted on this specific round. |
| Stage format | `StageFormat` | `PHONE`, `VIDEO`, `ONSITE`, `ASYNC` | |

**Terminal application statuses:** `ACCEPTED`, `REJECTED`, `WITHDRAWN`, `GHOSTED`.
Used by `list_pending_followups` (exclude these) and stats.

---

## 6. Indexes

Created via Spring Data annotations (`@Indexed`, `@CompoundIndex`, `@TextIndexed`) with
`spring.data.mongodb.auto-index-creation=true`, **or** an `ApplicationRunner` that calls
`IndexOperations` explicitly (preferred for prod visibility — decide in Phase 1).

### `companies`

| Index | Type | Purpose |
|---|---|---|
| `{ name: 1 }` | unique | Prevent duplicate companies; look up by name. |

### `applications`

| Index | Type | Purpose |
|---|---|---|
| `{ companyId: 1 }` | single | "All my applications at company X". |
| `{ status: 1 }` | single | Filter by status; stats grouping. |
| `{ appliedDate: -1 }` | single | Default list sort; date-range filters; "this month". |
| `{ followUpDate: 1 }` | single, **sparse** | `list_pending_followups` (field is optional). |
| `{ "stages.scheduledAt": 1 }` | multikey | `get_upcoming_interviews`. |
| `{ "stages.status": 1 }` | multikey | "Applications with a round `EXPECTED` / `SCHEDULED`". |
| `{ lastContactAt: 1 }` | single, **sparse** | "Gone quiet" query (§10.2). |
| `{ companyName: 1 }` | single | Grouping by company; also lets an anchored `^prefix` regex use the index. |
| `{ status: 1, appliedDate: -1 }` | compound | "Active applications, newest first" (the dashboard default). Add only if the single-field indexes are measured to be insufficient. |

**No text index — deliberate.** `$text` matches whole *stemmed tokens*, so typing `strip`
in the filter bar would not find Stripe and `eng` would not find Engineer. That is exactly
the interaction the SPA's free-text filter and the `search_applications` MCP tool need, so
free-text search uses a **case-insensitive regex** instead (§10.3). At hundreds of
documents a collection scan on a regex is sub-millisecond, and skipping the text index
leaves the one-per-collection slot free. If this ever stops scaling, **Atlas Search** is
the upgrade path — it is available on M0 (3-index limit) and does real substring and fuzzy
matching.

At personal scale (hundreds–low thousands of docs) index choice barely affects latency;
they're defined for correctness of practice and as an interview talking point.

---

## 7. Dates & timezones

| Field | Type | Rationale |
|---|---|---|
| `appliedDate`, `followUpDate` | `LocalDate` (BSON date at UTC midnight) | Date-only semantics; avoids off-by-one bugs in "this month" math. |
| `stages[].scheduledAt`, `stages[].completedAt` | `Instant` (UTC) | True points in time. |
| `createdAt`, `updatedAt` | `Instant` (UTC) | Auditing. |

**`@PastOrPresent` caveat:** Bean Validation compares against the JVM's *default* zone,
not `app.timezone`. Set the VPS clock to UTC and pass `app.timezone` explicitly everywhere
the two could differ; if `appliedDate` validation ever rejects a same-day entry, this is
why. Validate `appliedDate` in the service against `TimeService.today()` rather than
relying on the annotation alone.

**Rule:** the API and MCP server compute all relative windows ("due soon", "this week",
"this month", "in the next N days") in the **owner's configured timezone**
(`app.timezone`, default `America/New_York` — set to yours), then convert the boundaries to
UTC `Instant` / `LocalDate` for the Mongo query. Never compare "now" in UTC against a
local expectation. This is centralized in one `TimeService` / `Clock`-backed helper in
`common/`.

---

## 8. Validation

Two layers.

### 8.1 API layer (primary)

Jakarta Bean Validation on request DTOs:

- `company`: `name` `@NotBlank @Size(max=120)`; `email` on contacts `@Email`.
- `application`: `companyId` `@NotBlank`; `role` `@NotBlank @Size(max=160)`;
  `appliedDate` `@NotNull @PastOrPresent`; `source` `@NotNull`; `status` `@NotNull`;
  `stages` `@NotEmpty @Valid`.
- `stage`: `type` `@NotNull`; `status` `@NotNull`; `sequence` `@Positive`.
  **`scheduledAt` is deliberately *not* `@Future`.** Backfilling the job search already in
  progress (Phase 4) means entering applications already sent and interviews already held;
  a future-only constraint would reject exactly that data. The only rule is `scheduledAt`
  non-null when `status = SCHEDULED`, checked in the service.
- Unknown enum values → 400 automatically (Jackson/Spring binding).

Cross-field rules enforced in the service (return 400 `ProblemDetail`):
`compensation.max >= compensation.min`; `companyId` exists; `sequence` values stay
contiguous and unique; `scheduledAt` non-null when `status = SCHEDULED`; `completedAt`
non-null when `status ∈ {PASSED, FAILED}`.

### 8.2 Collection layer (optional, defense-in-depth)

A `$jsonSchema` validator on each collection requiring the mandatory fields and
constraining enum fields to their allowed sets. Catches writes that bypass the API — a
stray edit from IntelliJ's Database tool window, or a `mongorestore` of an old dump. Add in
Phase 1 if cheap; skip if it slows iteration.

---

## 9. Derived metrics (definitions)

These are computed, never stored. Used by `GET /api/stats` and `get_application_stats`.

| Metric | Definition |
|---|---|
| **Total applications** | Count of `applications` (optionally filtered to `appliedDate >= today − days`). |
| **By status** | Group by `status`, count. |
| **Funnel / by furthest stage** | For each application, the max `sequence` stage with `status = PASSED`; group by that stage's `type`, count. Shows how many reached OA, screen, technical, superday, offer. |
| **Response rate** | Applications with `size(stages) >= 2` (got any activity past the initial submission) ÷ total. Expressed as a percentage. **Recording convention this depends on:** a plain rejection with no interaction is recorded as a *status change to `REJECTED`*, never as an extra stage. Log a stage only when the company actually engaged (screen, OA, interview). Break that habit and this metric drifts without any code changing. |
| **Ghost rate** | Applications where `status ∈ {ACTIVE, GHOSTED}` **and** `size(stages) == 1` **and** `appliedDate <= today − 21 days`, ÷ **applications with `appliedDate <= today − 21 days`**. Dividing by the unfiltered total would understate the rate, since applications sent last week cannot yet be ghosted. |
| **Offer rate** | Applications with `status ∈ {OFFER, ACCEPTED}` ÷ total. |
| **Avg days to first response** | Mean, over applications with ≥ 2 stages, of `firstResponseAt − appliedDate` in days — where `firstResponseAt` is the earliest non-null of `scheduledAt` / `completedAt` on the stage with the **lowest `sequence` greater than 1**. (`sequence` is 1-based; don't read this as a 0-indexed array offset.) |
| **Active pipeline** | Count of `status = ACTIVE`. |

---

## 10. Query patterns — the four read features

Each is exposed once as a REST endpoint and consumed by the matching MCP tool.

### 10.1 `GET /api/stats?days=<int?>&from=<date?>&to=<date?>` → `get_application_stats`

**Why `from`/`to` and not just `days`:** "how many applications have I sent **this
month**?" is one of the headline MCP queries, and it means a *calendar* month, not a
rolling 30 days. `days` stays as sugar for "the last N days"; `from`/`to` answer the
calendar question. The MCP tool resolves "this month" to `from`/`to` boundaries in
`app.timezone` and says which window it used in its answer, so Claude never reports a
rolling window as if it were a calendar month.

`MongoTemplate` aggregation:
1. `$match` on `appliedDate` — `>= today−days` when `days` is given, or the
   `from`/`to` range when those are. `days` and `from`/`to` are mutually exclusive (400).
2. `$facet` with parallel pipelines: one `$group` by `status`; one `$unwind stages` +
   `$match stages.status = "PASSED"` + `$group` by `stages.type` (furthest-stage funnel);
   one for the counts feeding response/ghost/offer rates.
3. Assemble into a `StatsResponse` DTO in the service; compute the rate percentages in
   Java.

### 10.2 `GET /api/applications/followups` → `list_pending_followups`

```
{ followUpDate: { $lte: <today + 7 days, LocalDate> },
  status: { $nin: ["ACCEPTED","REJECTED","WITHDRAWN","GHOSTED"] } }
sort: { followUpDate: 1 }
```
**Gone-quiet pipelines** are a second query, unioned in the service and tagged in the
response so the UI/MCP can label the two groups apart:
```
{ status: "ACTIVE",
  lastContactAt: { $lte: <now − 14 days, Instant> } }
sort: { lastContactAt: 1 }
```
This uses **`lastContactAt`, never `updatedAt`** — see §1. `updatedAt` bumps when you fix a
typo, which would silently hide an application that has genuinely gone quiet. This query is
what answers "which companies haven't I heard back from in 2+ weeks?".

### 10.3 `GET /api/applications?q=<text>&status=&companyId=&from=&to=&page=&size=&sort=` → `search_applications`

- `q` → a **case-insensitive regex** across `companyName`, `role` and `notes`:
  `new Criteria().orOperator(Criteria.where("companyName").regex(quoted, "i"), ...)`.
  **Escape the user's input** with `Pattern.quote` / `Regex.escape` before building the
  criteria — an unescaped `q` of `(` or `*` is a 500, and a pathological pattern is a
  cheap DoS. See §6 for why this is a regex and not `$text`.
- Other params → additional `$match` criteria via `Criteria`.
- Always paginated (`Pageable`), default `sort = appliedDate desc`, default `size = 20`.
- `search_applications` (MCP) uses only `q` and returns the top N with a compact shape
  (company, role, status, currentStageType, appliedDate).

### 10.4 `GET /api/applications/interviews?days=<int>` → `get_upcoming_interviews`

`MongoTemplate` aggregation:
```
$match  { "stages.scheduledAt": { $gte: now, $lte: now + days } }
$unwind "$stages"
$match  { "stages.scheduledAt": { $gte: now, $lte: now + days },
          "stages.status": "SCHEDULED" }
$sort   { "stages.scheduledAt": 1 }
$project { companyName: 1, role: 1,
           stageType: "$stages.type", when: "$stages.scheduledAt",
           format: "$stages.format", interviewers: "$stages.interviewers" }
```

---

## 11. Spring Data mapping notes

- Documents are plain classes; DTOs are records. Spring Data *does* support records via
  persistence constructors — the reason for classes here is narrower: `@Id` population and
  `@CreatedDate` / `@LastModifiedDate` auditing both write fields **after** construction,
  which fights an immutable record and forces `with`-style copying on every save. Mutable
  classes for `@Document` types, records for DTOs. (Don't repeat the folk claim that
  "Spring Data needs a no-arg constructor" — it doesn't, and an interviewer may know that.)
- `@Document("companies")` / `@Document("applications")`. `@Id private String id;` (Mongo
  `ObjectId` maps to `String` fine).
- Enable auditing: `@EnableMongoAuditing` on a config class; `@CreatedDate` /
  `@LastModifiedDate` on `Instant` fields.
- `Stage`, `Contact`, `Compensation` are POJOs with no `@Document` — embedded automatically.
- Field names map 1:1 (camelCase Java → camelCase BSON). No `@Field` annotations needed.
- Jackson 3 (Spring Boot 4 default): `java.time` handled by the JSR-310 module which is
  auto-registered. `Instant` serializes as an ISO-8601 string in API responses — Jackson 3
  defaults to this, and `application.yml` sets it explicitly under
  **`spring.jackson.datatype.datetime.write-dates-as-timestamps`**. Note the key: Jackson 3
  moved `WRITE_DATES_AS_TIMESTAMPS` from `SerializationFeature` to `DateTimeFeature`, so
  the Boot 3 key `spring.jackson.serialization.write-dates-as-timestamps` **fails the
  context load** on Boot 4 with `No enum constant
  tools.jackson.databind.SerializationFeature.write-dates-as-timestamps`. Most Jackson
  advice online is still Jackson 2.
- Aggregations: `MongoTemplate.aggregate(Aggregation.newAggregation(...), "applications",
  ResultType.class)`.

---

## 12. Evolution notes

- **Adding a stage type / status:** add the enum constant; no migration needed (old docs
  just don't have it). Update this file's §5.
- **Making `companies` optional later:** if the two-collection split ever feels heavy,
  a `company` string already exists as `companyName` on every application — you could stop
  writing `companyId` and treat `companyName` as the grouping key. Not planned.
- **`statusHistory` if analytics need per-transition timing:** the `stages[]` array
  already carries `scheduledAt`/`completedAt` per round, which covers the planned metrics.
  If application-level `ACTIVE → REJECTED` transition timestamps are needed, add a small
  `statusHistory: [{ status, at }]` array — additive, no migration.
- **Contacts on applications (not just companies):** if a specific interviewer/recruiter
  is tied to one application rather than the company, add `applications.contacts[]` using
  the same `Contact` shape. Additive.
- **If regex search stops scaling:** move to **Atlas Search** (available on M0, 3-index
  limit) for real substring, fuzzy and relevance-ranked matching. This changes only
  `ApplicationService`'s search method and the index definition — the endpoint contract in
  §10.3 and the MCP tool stay identical.
