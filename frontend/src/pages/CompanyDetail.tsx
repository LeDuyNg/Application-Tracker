import { Link, useNavigate, useParams } from 'react-router-dom';

import { useApplications } from '../api/hooks/applications';
import { useCompany, useDeleteCompany } from '../api/hooks/companies';
import { ApplicationTable } from '../components/ApplicationTable';
import { ErrorNote, Empty, Loading } from '../components/feedback';

/** One company: its fields, its contacts, and every application that references it. */
export function CompanyDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: company, isLoading, error } = useCompany(id);
  const apps = useApplications({ companyId: id, size: 100 });
  const del = useDeleteCompany();

  if (isLoading) return <Loading />;
  if (error) return <ErrorNote error={error} />;
  if (!company) return null;

  return (
    <div className="stack" style={{ gap: 'var(--space-5)' }}>
      <div className="spread">
        <h1 style={{ margin: 0 }}>{company.name}</h1>
        <div className="row">
          <Link to={`/companies/${company.id}/edit`}><button>Edit</button></Link>
          <button
            className="danger"
            disabled={del.isPending}
            onClick={() => {
              if (confirm(`Delete ${company.name}?`)) {
                del.mutate(company.id, { onSuccess: () => navigate('/companies') });
              }
            }}
          >
            Delete
          </button>
        </div>
      </div>

      {/* The backend returns 409 with a message naming the referencing applications. */}
      {del.error ? <ErrorNote error={del.error} /> : null}

      <div className="card grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
        <div><div className="muted small">Industry</div>{company.industry ?? '—'}</div>
        <div><div className="muted small">Location</div>{company.location ?? '—'}</div>
        <div>
          <div className="muted small">Website</div>
          {company.website ? <a href={company.website} target="_blank" rel="noreferrer">open ↗</a> : '—'}
        </div>
      </div>

      {company.tags.length > 0 ? (
        <div className="row-wrap">
          {company.tags.map((t) => <span key={t} className="badge gray">{t}</span>)}
        </div>
      ) : null}

      {company.notes ? (
        <div className="card">
          <h3>Notes</h3>
          <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{company.notes}</p>
        </div>
      ) : null}

      <section className="stack">
        <h2>Contacts</h2>
        {company.contacts.length === 0 ? (
          <Empty>No contacts.</Empty>
        ) : (
          <div className="card" style={{ padding: 0 }}>
            <table>
              <thead>
                <tr><th>Name</th><th>Title</th><th>Email</th><th>Phone</th><th>Notes</th></tr>
              </thead>
              <tbody>
                {company.contacts.map((ct, i) => (
                  <tr key={i}>
                    <td>{ct.name}</td>
                    <td>{ct.title ?? '—'}</td>
                    <td>{ct.email ?? '—'}</td>
                    <td>{ct.phone ?? '—'}</td>
                    <td>{ct.notes ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="stack">
        <h2>Applications</h2>
        {apps.isLoading ? (
          <Loading />
        ) : (
          <div className="card" style={{ padding: 0 }}>
            <ApplicationTable rows={apps.data?.content ?? []} />
          </div>
        )}
      </section>
    </div>
  );
}
