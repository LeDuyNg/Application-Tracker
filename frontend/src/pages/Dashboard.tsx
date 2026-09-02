import { Link } from 'react-router-dom';

import { useApplications } from '../api/hooks/applications';
import { useMe } from '../api/hooks/useMe';
import { FollowupsWidget } from '../components/FollowupsWidget';
import { StatsBar } from '../components/StatsBar';
import { UpcomingInterviewsWidget } from '../components/UpcomingInterviewsWidget';
import { ApplicationTable } from '../components/ApplicationTable';
import { Loading } from '../components/feedback';

/**
 * The landing page: headline stats + funnel, what needs chasing, upcoming interviews, and
 * the most recent applications. Each block is its own component fetching its own data, so
 * one slow query doesn't block the rest.
 */
export function Dashboard() {
  // "Recent activity" = the newest 5 applications (the API already sorts by appliedDate desc).
  const recent = useApplications({ page: 0, size: 5 });

  const me = useMe();
  const firstName = (me.data?.name ?? '').split(' ')[0];

  return (
    <div className="stack" style={{ gap: 'var(--space-6)' }}>
      <div className="spread page-head">
        <div>
          <div className="eyebrow">Overview</div>
          <h1 style={{ margin: 0 }}>{firstName ? `Welcome back, ${firstName}` : 'Dashboard'}</h1>
        </div>
        <Link to="/applications/new"><button className="primary">+ New application</button></Link>
      </div>

      <StatsBar />

      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', alignItems: 'start' }}>
        <FollowupsWidget />
        <UpcomingInterviewsWidget days={14} />
      </div>

      <section className="stack">
        <div className="spread">
          <h2>Recent applications</h2>
          <Link to="/applications" className="small">View all →</Link>
        </div>
        {recent.isLoading ? (
          <Loading />
        ) : (
          <div className="card" style={{ padding: 0 }}>
            <ApplicationTable rows={recent.data?.content ?? []} />
          </div>
        )}
      </section>
    </div>
  );
}
