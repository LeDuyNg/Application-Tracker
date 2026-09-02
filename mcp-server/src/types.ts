/**
 * TypeScript mirrors of the backend response DTOs this server reads.
 *
 * Hand-kept in sync with `backend/.../application/dto/` and `.../stats/dto/`, and a subset
 * of `frontend/src/api/types.ts` — only the shapes the four read tools actually touch. If a
 * field is added on the backend, add it in both places.
 *
 * `SCHEMA.md §5` is the source of truth for the enum values. The string-literal unions
 * below must match it exactly, because the backend (de)serialises enums by their Java
 * constant name.
 *
 * Convention: a field the backend may send as JSON `null` is typed `T | null`. Jackson
 * emits `null` for an unset reference field rather than omitting the key, so `null` — not
 * `undefined` — is what actually arrives on the wire.
 */

// ---------------------------------------------------------------- enums (SCHEMA.md §5)

export type ApplicationStatus =
  | 'ACTIVE'
  | 'OFFER'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'GHOSTED';

/** Declaration order is the funnel's progression order — keep it aligned with SCHEMA.md §5. */
export type StageType =
  | 'APPLICATION_SUBMITTED'
  | 'RECRUITER_SCREEN'
  | 'ONLINE_ASSESSMENT'
  | 'TAKE_HOME'
  | 'PHONE_SCREEN'
  | 'TECHNICAL_INTERVIEW'
  | 'SYSTEM_DESIGN'
  | 'BEHAVIORAL'
  | 'HIRING_MANAGER'
  | 'SUPERDAY'
  | 'TEAM_MATCH'
  | 'REFERENCE_CHECK'
  | 'OFFER'
  | 'OTHER';

export type StageFormat = 'PHONE' | 'VIDEO' | 'ONSITE' | 'ASYNC';

// ---------------------------------------------------------------- responses

/** Compact row for list/search results — no stages, notes or compensation (SCHEMA.md §10.3). */
export interface ApplicationSummaryResponse {
  id: string;
  companyId: string;
  companyName: string;
  role: string;
  status: ApplicationStatus;
  currentStageType: StageType | null;
  appliedDate: string; // ISO date, "2026-08-15"
  followUpDate: string | null;
  lastContactAt: string | null; // ISO-8601 instant
}

export interface FollowupDue {
  application: ApplicationSummaryResponse;
  /** Days past followUpDate. Negative means "not due yet" — the list looks a week ahead. */
  daysOverdue: number;
}

export interface FollowupGoneQuiet {
  application: ApplicationSummaryResponse;
  daysSinceContact: number;
}

export interface FollowupResponse {
  due: FollowupDue[];
  goneQuiet: FollowupGoneQuiet[];
  /** The look-ahead the backend used for `due`, echoed so we quote it instead of guessing. */
  dueWithinDays: number;
  /** The silence threshold used for `goneQuiet`, likewise. */
  quietAfterDays: number;
}

export interface UpcomingInterviewResponse {
  applicationId: string;
  stageId: string;
  companyId: string;
  companyName: string;
  role: string;
  stageType: StageType;
  scheduledAt: string; // ISO-8601 instant
  format: StageFormat | null;
  interviewers: string[];
}

/**
 * The window the stats actually describe, echoed back by the backend on purpose.
 *
 * `description` is human-readable text written by the API. Quoting it is what stops a
 * rolling 30-day count ever being reported as "this month" (SCHEMA.md §10.1).
 */
export interface StatsWindow {
  from: string | null;
  to: string | null;
  description: string;
}

export interface FunnelEntry {
  stageType: StageType;
  count: number;
}

export interface StatsResponse {
  window: StatsWindow;
  totalApplications: number;
  /** Only statuses with at least one application appear as keys. */
  byStatus: Partial<Record<ApplicationStatus, number>>;
  funnel: FunnelEntry[];
  activePipeline: number;
  responseRatePct: number;
  ghostRatePct: number;
  offerRatePct: number;
  avgDaysToFirstResponse: number | null;
}

/**
 * Spring's `PagedModel` wire shape — what `GET /api/applications` returns.
 *
 * The controller returns this rather than a raw `Page` because Spring Data's internal
 * `PageImpl` structure is explicitly not a stable contract. `{content, page:{...}}` is.
 */
export interface PagedModel<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

/**
 * RFC 7807 problem body — what every backend error returns, as
 * `Content-Type: application/problem+json` (CLAUDE.md §11).
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Attached by the backend on validation failures. */
  errors?: Array<{ field: string; message: string }>;
}
