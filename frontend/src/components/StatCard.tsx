import type { ReactNode } from 'react';

/** A labelled number. Several sit in a row on the dashboard as the <StatsBar>. */
export function StatCard({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="card" style={{ padding: 'var(--space-4)' }}>
      <div className="muted small">{label}</div>
      <div style={{ fontSize: '1.6rem', fontWeight: 700, marginTop: 2 }}>{value}</div>
      {hint ? <div className="muted small" style={{ marginTop: 2 }}>{hint}</div> : null}
    </div>
  );
}
