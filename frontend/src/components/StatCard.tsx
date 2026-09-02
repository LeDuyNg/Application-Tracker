import type { ReactNode } from 'react';

/** A labelled number with a gradient edge. A row of these forms the <StatsBar>. */
export function StatCard({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="stat">
      <div className="eyebrow">{label}</div>
      <div className="num">{value}</div>
      {hint ? <div className="tiny faint" style={{ marginTop: 2 }}>{hint}</div> : null}
    </div>
  );
}
