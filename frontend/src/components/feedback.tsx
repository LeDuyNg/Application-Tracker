import type { ReactNode } from 'react';

import { ApiError } from '../api/client';

/** A plain loading line. Deliberately not a spinner animation — less to maintain. */
export function Loading({ label = 'Loading…' }: { label?: string }) {
  return <p className="muted">{label}</p>;
}

/** Red box for a failed query or mutation. Pulls the backend's ProblemDetail message when present. */
export function ErrorNote({ error, prefix }: { error: unknown; prefix?: string }) {
  let message = 'Something went wrong.';
  if (error instanceof ApiError) message = error.detail ?? error.message;
  else if (error instanceof Error) message = error.message;
  return (
    <div className="error-note" role="alert">
      {prefix ? `${prefix}: ` : ''}
      {message}
    </div>
  );
}

/** Centered muted text for "nothing here yet" states. */
export function Empty({ children }: { children: ReactNode }) {
  return <p className="muted" style={{ padding: 'var(--space-4) 0' }}>{children}</p>;
}
