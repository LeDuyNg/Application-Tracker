import { Link } from 'react-router-dom';

import { useCompanies } from '../api/hooks/companies';
import { ErrorNote, Empty, Loading } from '../components/feedback';

export function CompaniesList() {
  const { data, isLoading, error } = useCompanies();

  return (
    <div className="stack" style={{ gap: 'var(--space-5)' }}>
      <div className="spread">
        <h1>Companies</h1>
        <Link to="/companies/new"><button className="primary">+ New company</button></Link>
      </div>

      {error ? <ErrorNote error={error} /> : null}
      {isLoading ? (
        <Loading />
      ) : !data || data.length === 0 ? (
        <Empty>No companies yet.</Empty>
      ) : (
        <div className="card" style={{ padding: 0 }}>
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Industry</th>
                <th>Location</th>
                <th>Contacts</th>
              </tr>
            </thead>
            <tbody>
              {data.map((c) => (
                <tr key={c.id}>
                  <td><Link to={`/companies/${c.id}`}>{c.name}</Link></td>
                  <td>{c.industry ?? '—'}</td>
                  <td>{c.location ?? '—'}</td>
                  <td>{c.contacts.length}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
