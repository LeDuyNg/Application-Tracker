import { useStats } from '../api/hooks/stats';
import { formatPercent } from '../lib/format';
import { label, STAGE_TYPES } from '../lib/enums';
import { ErrorNote, Loading } from './feedback';
import { StatCard } from './StatCard';

/**
 * The row of headline numbers + the funnel, for the dashboard. `days` is optional — omit
 * for all-time. The backend echoes back which window it used in `window.description`.
 */
export function StatsBar({ days }: { days?: number }) {
  const { data, isLoading, error } = useStats(days ? { days } : {});

  if (isLoading) return <Loading label="Loading stats…" />;
  if (error) return <ErrorNote error={error} prefix="Stats" />;
  if (!data) return null;

  return (
    <div className="stack">
      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))' }}>
        <StatCard label="Total applications" value={data.totalApplications} hint={data.window.description} />
        <StatCard label="Active pipeline" value={data.activePipeline} />
        <StatCard label="Response rate" value={formatPercent(data.responseRatePct)} hint="got past submission" />
        <StatCard label="Offer rate" value={formatPercent(data.offerRatePct)} />
        <StatCard label="Ghost rate" value={formatPercent(data.ghostRatePct)} hint="21+ days, no reply" />
        <StatCard
          label="Avg days to first reply"
          value={data.avgDaysToFirstResponse == null ? '—' : data.avgDaysToFirstResponse.toFixed(1)}
        />
      </div>

      <FunnelBars funnel={data.funnel} />
    </div>
  );
}

/**
 * How far applications got. Each bar is the count of applications whose furthest PASSED
 * round was this stage type. Rendered in `StageType` declaration order (the pipeline's
 * progression), so the shape reads left-to-right as a funnel.
 */
function FunnelBars({ funnel }: { funnel: { stageType: string; count: number }[] }) {
  const byType = new Map(funnel.map((f) => [f.stageType, f.count]));
  const rows = STAGE_TYPES.map((t) => ({ type: t, count: byType.get(t) ?? 0 })).filter((r) => r.count > 0);
  const max = Math.max(1, ...rows.map((r) => r.count));

  if (rows.length === 0) return null;

  return (
    <div className="card">
      <h3 className="eyebrow" style={{ marginBottom: 'var(--space-3)' }}>Funnel — furthest stage reached</h3>
      <div className="stack" style={{ gap: 'var(--space-2)' }}>
        {rows.map((r) => (
          <div key={r.type} className="row funnel-row" style={{ gap: 'var(--space-3)' }}>
            <div className="small" style={{ width: 160, textAlign: 'right' }}>{label(r.type)}</div>
            <div className="funnel-track">
              <div className="funnel-fill" style={{ width: `${(r.count / max) * 100}%`, minWidth: 2 }} />
            </div>
            <div className="small mono" style={{ width: 28 }}>{r.count}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
