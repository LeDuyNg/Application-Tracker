import type { ApplicationStatus, StageStatus } from '../api/types';
import { label } from '../lib/enums';

/**
 * Application status → a coloured pill. The tone mapping encodes how the status *feels*:
 * green = good, red = over, amber = needs attention, blue = in progress, gray = neutral.
 */
const APP_TONE: Record<ApplicationStatus, string> = {
  ACTIVE: 'blue',
  OFFER: 'green',
  ACCEPTED: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'gray',
  GHOSTED: 'gray',
};

export function StatusBadge({ status }: { status: ApplicationStatus }) {
  return <span className={`badge ${APP_TONE[status]}`}>{label(status)}</span>;
}

const STAGE_TONE: Record<StageStatus, string> = {
  EXPECTED: 'gray',
  SCHEDULED: 'blue',
  PASSED: 'green',
  FAILED: 'red',
  CANCELLED: 'gray',
  RESCHEDULED: 'amber',
  NO_RESPONSE: 'amber',
};

export function StageStatusBadge({ status }: { status: StageStatus }) {
  return <span className={`badge ${STAGE_TONE[status]}`}>{label(status)}</span>;
}
