/**
 * TypeScript mirrors of the backend's response and request DTOs.
 *
 * These are hand-kept in sync with `backend/.../application/dto/`, `.../company/dto/` and
 * `.../stats/dto/`. If a field is added there, add it here. `SCHEMA.md §5` is the source of
 * truth for the enum values; the string-literal unions below must match it exactly, because
 * the backend (de)serialises enums by their constant name.
 *
 * Convention: a field the backend may send as JSON `null` is typed `T | null`. Java's
 * Jackson emits `null` for an unset reference field rather than omitting the key, so `null`
 * — not `undefined` — is what actually arrives.
 */

// ---------------------------------------------------------------- enums (SCHEMA.md §5)

export type ApplicationStatus =
  | 'ACTIVE'
  | 'OFFER'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'GHOSTED';

export type ApplicationSource =
  | 'REFERRAL'
  | 'COLD_APPLY'
  | 'RECRUITER_OUTREACH'
  | 'CAREER_FAIR'
  | 'NETWORKING_EVENT'
  | 'JOB_BOARD'
  | 'COMPANY_WEBSITE'
  | 'OTHER';

export type WorkMode = 'REMOTE' | 'HYBRID' | 'ONSITE';

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

export type StageStatus =
  | 'EXPECTED'
  | 'SCHEDULED'
  | 'PASSED'
  | 'FAILED'
  | 'CANCELLED'
  | 'RESCHEDULED'
  | 'NO_RESPONSE';

export type StageFormat = 'PHONE' | 'VIDEO' | 'ONSITE' | 'ASYNC';

// ---------------------------------------------------------------- responses

export interface ContactResponse {
  name: string;
  title: string | null;
  email: string | null;
  phone: string | null;
  notes: string | null;
}

export interface CompanyResponse {
  id: string;
  name: string;
  website: string | null;
  industry: string | null;
  location: string | null;
  contacts: ContactResponse[];
  notes: string | null;
  tags: string[];
  createdAt: string; // ISO-8601 instant
  updatedAt: string;
}

export interface CompensationResponse {
  min: number | null;
  max: number | null;
  currency: string | null;
}

export interface StageResponse {
  stageId: string;
  sequence: number;
  type: StageType;
  status: StageStatus;
  scheduledAt: string | null; // ISO-8601 instant
  completedAt: string | null;
  format: StageFormat | null;
  interviewers: string[];
  notes: string | null;
}

export interface ApplicationResponse {
  id: string;
  companyId: string;
  companyName: string;
  role: string;
  status: ApplicationStatus;
  appliedDate: string; // ISO date, "2026-08-15"
  source: ApplicationSource;
  currentStageType: StageType | null;
  followUpDate: string | null; // ISO date
  jobPostingUrl: string | null;
  location: string | null;
  workMode: WorkMode | null;
  compensation: CompensationResponse | null;
  notes: string | null;
  tags: string[];
  stages: StageResponse[];
  lastContactAt: string | null; // ISO-8601 instant
  createdAt: string;
  updatedAt: string;
}

/** Compact row for list/search results — no stages, notes or compensation (SCHEMA.md §10.3). */
export interface ApplicationSummaryResponse {
  id: string;
  companyId: string;
  companyName: string;
  role: string;
  status: ApplicationStatus;
  currentStageType: StageType | null;
  appliedDate: string;
  followUpDate: string | null;
  lastContactAt: string | null;
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
  dueWithinDays: number;
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

export interface MeResponse {
  email: string | null;
  name: string | null;
  picture: string | null;
  /** true for a human session, false for the MCP token. */
  person: boolean;
}

/**
 * Spring's `PagedModel` wire shape — what `GET /api/applications` returns. Mirrors the
 * `{content, page:{...}}` structure the controller deliberately switched to over raw `Page`.
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

// ---------------------------------------------------------------- requests

export interface ContactRequest {
  name: string;
  title?: string | null;
  email?: string | null;
  phone?: string | null;
  notes?: string | null;
}

export interface CompanyRequest {
  name: string;
  website?: string | null;
  industry?: string | null;
  location?: string | null;
  contacts?: ContactRequest[];
  notes?: string | null;
  tags?: string[];
}

export interface CompensationRequest {
  min?: number | null;
  max?: number | null;
  currency?: string | null;
}

export interface StageRequest {
  type: StageType;
  status: StageStatus;
  sequence?: number | null;
  scheduledAt?: string | null; // ISO-8601 instant
  completedAt?: string | null;
  format?: StageFormat | null;
  interviewers?: string[];
  notes?: string | null;
}

/** Body of POST /api/applications. `status` and `stages` are optional here (SCHEMA.md §8.1). */
export interface CreateApplicationRequest {
  companyId: string;
  role: string;
  status?: ApplicationStatus | null;
  appliedDate: string; // ISO date
  source: ApplicationSource;
  followUpDate?: string | null;
  jobPostingUrl?: string | null;
  location?: string | null;
  workMode?: WorkMode | null;
  compensation?: CompensationRequest | null;
  notes?: string | null;
  tags?: string[];
  stages?: StageRequest[];
}

/** Body of PUT /api/applications/{id}. Full replacement; `status` is required, `stages` absent. */
export interface UpdateApplicationRequest {
  companyId: string;
  role: string;
  status: ApplicationStatus;
  appliedDate: string;
  source: ApplicationSource;
  followUpDate?: string | null;
  jobPostingUrl?: string | null;
  location?: string | null;
  workMode?: WorkMode | null;
  compensation?: CompensationRequest | null;
  notes?: string | null;
  tags?: string[];
}
