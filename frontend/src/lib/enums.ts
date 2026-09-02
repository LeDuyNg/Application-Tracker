/**
 * Enum values as arrays (for <select> options) plus human-readable labels.
 *
 * The arrays are declared in the same order as `SCHEMA.md §5` / the Java enums. `StageType`
 * order in particular is the funnel's progression order — don't re-sort it alphabetically.
 */
import type {
  ApplicationSource,
  ApplicationStatus,
  StageFormat,
  StageStatus,
  StageType,
  WorkMode,
} from '../api/types';

/** Turn SCREAMING_SNAKE_CASE into "Title Case" as a fallback when no explicit label is set. */
function titleCase(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

export const APPLICATION_STATUSES: ApplicationStatus[] = [
  'ACTIVE',
  'OFFER',
  'ACCEPTED',
  'REJECTED',
  'WITHDRAWN',
  'GHOSTED',
];

export const APPLICATION_SOURCES: ApplicationSource[] = [
  'REFERRAL',
  'COLD_APPLY',
  'RECRUITER_OUTREACH',
  'CAREER_FAIR',
  'NETWORKING_EVENT',
  'JOB_BOARD',
  'COMPANY_WEBSITE',
  'OTHER',
];

export const WORK_MODES: WorkMode[] = ['REMOTE', 'HYBRID', 'ONSITE'];

export const STAGE_TYPES: StageType[] = [
  'APPLICATION_SUBMITTED',
  'RECRUITER_SCREEN',
  'ONLINE_ASSESSMENT',
  'TAKE_HOME',
  'PHONE_SCREEN',
  'TECHNICAL_INTERVIEW',
  'SYSTEM_DESIGN',
  'BEHAVIORAL',
  'HIRING_MANAGER',
  'SUPERDAY',
  'TEAM_MATCH',
  'REFERENCE_CHECK',
  'OFFER',
  'OTHER',
];

export const STAGE_STATUSES: StageStatus[] = [
  'EXPECTED',
  'SCHEDULED',
  'PASSED',
  'FAILED',
  'CANCELLED',
  'RESCHEDULED',
  'NO_RESPONSE',
];

export const STAGE_FORMATS: StageFormat[] = ['PHONE', 'VIDEO', 'ONSITE', 'ASYNC'];

/** A few labels read better than plain title-case; everything else falls through. */
const LABELS: Record<string, string> = {
  COLD_APPLY: 'Cold apply',
  RECRUITER_OUTREACH: 'Recruiter outreach',
  JOB_BOARD: 'Job board',
  COMPANY_WEBSITE: 'Company website',
  APPLICATION_SUBMITTED: 'Applied',
  RECRUITER_SCREEN: 'Recruiter screen',
  ONLINE_ASSESSMENT: 'Online assessment',
  TAKE_HOME: 'Take-home',
  PHONE_SCREEN: 'Phone screen',
  TECHNICAL_INTERVIEW: 'Technical interview',
  SYSTEM_DESIGN: 'System design',
  HIRING_MANAGER: 'Hiring manager',
  TEAM_MATCH: 'Team match',
  REFERENCE_CHECK: 'Reference check',
  NO_RESPONSE: 'No response',
};

export function label(value: string | null | undefined): string {
  if (!value) return '—';
  return LABELS[value] ?? titleCase(value);
}
