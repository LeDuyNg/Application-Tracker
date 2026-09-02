import { Link, useNavigate, useParams } from 'react-router-dom';

import { useApplication, useDeleteApplication } from '../api/hooks/applications';
import { StageTimeline } from '../components/StageTimeline';
import { StatusBadge } from '../components/StatusBadge';
import { ErrorNote, Loading } from '../components/feedback';
import { label } from '../lib/enums';
import { formatCompensation, formatDate, formatRelative } from '../lib/format';

/** One application: all its fields, its stage timeline, and edit / delete. */
export function ApplicationDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: app, isLoading, error } = useApplication(id);
  const del = useDeleteApplication();

  if (isLoading) return <Loading />;
  if (error) return <ErrorNote error={error} />;
  if (!app) return null;

  return (
    <div className="stack" style={{ gap: 'var(--space-5)' }}>
      <div className="spread">
        <div>
          <h1 style={{ marginBottom: 4 }}>{app.role}</h1>
          <div className="row" style={{ gap: 'var(--space-2)' }}>
            <Link to={`/companies/${app.companyId}`}>{app.companyName}</Link>
            <StatusBadge status={app.status} />
          </div>
        </div>
        <div className="row">
          <Link to={`/applications/${app.id}/edit`}><button>Edit</button></Link>
          <button
            className="danger"
            disabled={del.isPending}
            onClick={() => {
              if (confirm(`Delete the ${app.role} application at ${app.companyName}?`)) {
                del.mutate(app.id, { onSuccess: () => navigate('/applications') });
              }
            }}
          >
            Delete
          </button>
        </div>
      </div>

      {del.error ? <ErrorNote error={del.error} /> : null}

      <div className="card grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
        <Field label="Applied">{formatDate(app.appliedDate)}</Field>
        <Field label="Source">{label(app.source)}</Field>
        <Field label="Current stage">{label(app.currentStageType)}</Field>
        <Field label="Follow-up">{formatDate(app.followUpDate)}</Field>
        <Field label="Work mode">{label(app.workMode)}</Field>
        <Field label="Location">{app.location ?? '—'}</Field>
        <Field label="Compensation">{formatCompensation(app.compensation)}</Field>
        <Field label="Last contact">
          {app.lastContactAt ? formatRelative(app.lastContactAt) : '—'}
        </Field>
        <Field label="Job posting">
          {app.jobPostingUrl ? (
            <a href={app.jobPostingUrl} target="_blank" rel="noreferrer">open ↗</a>
          ) : (
            '—'
          )}
        </Field>
      </div>

      {app.tags.length > 0 ? (
        <div className="row-wrap">
          {app.tags.map((t) => <span key={t} className="badge gray">{t}</span>)}
        </div>
      ) : null}

      {app.notes ? (
        <div className="card">
          <h3>Notes</h3>
          <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{app.notes}</p>
        </div>
      ) : null}

      <StageTimeline applicationId={app.id} stages={app.stages} />
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="muted small">{label}</div>
      <div>{children}</div>
    </div>
  );
}
