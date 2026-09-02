import { Link } from 'react-router-dom';

import { useUpcomingInterviews } from '../api/hooks/interviews';
import { label } from '../lib/enums';
import { formatDateTime } from '../lib/format';
import { Empty, ErrorNote, Loading } from './feedback';

/**
 * "What's on my calendar" — one row per scheduled round, soonest first, across all
 * applications (SCHEMA.md §10.4). Terminal applications are already excluded by the backend.
 */
export function UpcomingInterviewsWidget({ days = 14 }: { days?: number }) {
  const { data, isLoading, error } = useUpcomingInterviews(days);

  if (isLoading) return <Loading label="Loading interviews…" />;
  if (error) return <ErrorNote error={error} prefix="Interviews" />;

  return (
    <div className="card stack">
      <h2>Upcoming interviews (next {days} days)</h2>
      {!data || data.length === 0 ? (
        <Empty>Nothing scheduled.</Empty>
      ) : (
        <ul className="stack" style={{ listStyle: 'none', padding: 0, gap: 'var(--space-3)' }}>
          {data.map((iv) => (
            <li key={iv.stageId} className="stack" style={{ gap: 2 }}>
              <div className="spread">
                <strong>{label(iv.stageType)}</strong>
                <span className="small nowrap">{formatDateTime(iv.scheduledAt)}</span>
              </div>
              <div className="small muted">
                <Link to={`/applications/${iv.applicationId}`}>{iv.companyName}</Link> · {iv.role}
                {iv.format ? ` · ${label(iv.format)}` : ''}
                {iv.interviewers.length > 0 ? ` · ${iv.interviewers.join(', ')}` : ''}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
