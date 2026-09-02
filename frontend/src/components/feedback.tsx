import type { ReactNode } from 'react';

import { ApiError } from '../api/client';
import { Logo } from './Logo';

/** A plain loading line. */
export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <p className="muted row" style={{ gap: 'var(--space-2)' }}>
      <span className="spinner-dot" /> {label}
    </p>
  );
}

/** Full-screen loader shown by <App> while `useMe()` is in flight. */
export function Splash() {
  return (
    <div className="center-screen">
      <div className="stack" style={{ alignItems: 'center', gap: 'var(--space-3)', color: 'var(--accent)' }}>
        <Logo size={32} />
        <span className="small muted">Loading…</span>
      </div>
    </div>
  );
}

/** Red box for a failed query or mutation. Pulls the backend's ProblemDetail message. */
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

/** Muted text for "nothing here yet" states. */
export function Empty({ children }: { children: ReactNode }) {
  return <p className="muted" style={{ padding: 'var(--space-4) 0', margin: 0 }}>{children}</p>;
}
