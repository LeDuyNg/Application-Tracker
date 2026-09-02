import { Link } from 'react-router-dom';

import { useFollowups } from '../api/hooks/followups';
import { formatDate } from '../lib/format';
import { StatusBadge } from './StatusBadge';
import { Empty, ErrorNote, Loading } from './feedback';

/**
 * The dashboard's "what needs chasing" panel. Two lists, deliberately kept apart
 * (SCHEMA.md §10.2): `due` = a reminder you set that has arrived; `goneQuiet` = an active
 * pipeline nobody has moved in weeks. An application can be in both — that's the most
 * urgent case, not a bug.
 */
export function FollowupsWidget() {
  const { data, isLoading, error } = useFollowups();

  if (isLoading) return <Loading label="Loading follow-ups…" />;
  if (error) return <ErrorNote error={error} prefix="Follow-ups" />;
  if (!data) return null;

  return (
    <div className="card stack hover">
      <h2>Needs chasing</h2>

      <section>
        <h3 className="muted small">Due (within {data.dueWithinDays} days)</h3>
        {data.due.length === 0 ? (
          <Empty>Nothing due.</Empty>
        ) : (
          <ul className="stack" style={{ listStyle: 'none', padding: 0, gap: 'var(--space-2)' }}>
            {data.due.map(({ application: a, daysOverdue }) => (
              <li key={a.id} className="spread">
                <span>
                  <Link className="link" to={`/applications/${a.id}`}>{a.companyName}</Link>{' '}
                  <span className="muted small">· {a.role}</span>
                </span>
                <span className="small nowrap">
                  <StatusBadge status={a.status} />{' '}
                  {daysOverdue > 0
                    ? `${daysOverdue}d overdue`
                    : daysOverdue === 0
                      ? 'today'
                      : `in ${-daysOverdue}d`}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h3 className="muted small">Gone quiet ({data.quietAfterDays}+ days, no contact)</h3>
        {data.goneQuiet.length === 0 ? (
          <Empty>Nothing has gone quiet.</Empty>
        ) : (
          <ul className="stack" style={{ listStyle: 'none', padding: 0, gap: 'var(--space-2)' }}>
            {data.goneQuiet.map(({ application: a, daysSinceContact }) => (
              <li key={a.id} className="spread">
                <span>
                  <Link className="link" to={`/applications/${a.id}`}>{a.companyName}</Link>{' '}
                  <span className="muted small">· {a.role}</span>
                </span>
                <span className="small nowrap muted">
                  {daysSinceContact}d since contact
                  {a.lastContactAt ? ` (${formatDate(a.lastContactAt)})` : ''}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
