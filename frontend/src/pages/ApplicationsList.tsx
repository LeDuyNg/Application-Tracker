import { useState } from 'react';
import { Link } from 'react-router-dom';

import { useApplications, type ApplicationFilters } from '../api/hooks/applications';
import { ApplicationTable } from '../components/ApplicationTable';
import { FiltersBar } from '../components/FiltersBar';
import { Pagination } from '../components/Pagination';
import { ErrorNote, Loading } from '../components/feedback';

/**
 * The full, filterable, paged list. All filter + page state lives here in one object and is
 * handed to `useApplications`; TanStack Query keys the cache by that object, so going back
 * to a filter combination you've already seen is instant.
 */
export function ApplicationsList() {
  const [filters, setFilters] = useState<ApplicationFilters>({ page: 0, size: 20 });
  const { data, isLoading, isFetching, error } = useApplications(filters);

  return (
    <div className="stack" style={{ gap: 'var(--space-5)' }}>
      <div className="spread">
        <h1>Applications</h1>
        <Link to="/applications/new"><button className="primary">+ New application</button></Link>
      </div>

      <FiltersBar value={filters} onChange={setFilters} />

      {error ? <ErrorNote error={error} /> : null}

      {isLoading ? (
        <Loading />
      ) : (
        <>
          <div className="spread small muted">
            <span>{data?.page.totalElements ?? 0} result{(data?.page.totalElements ?? 0) === 1 ? '' : 's'}</span>
            {isFetching ? <span>updating…</span> : null}
          </div>
          <div className="card" style={{ padding: 0 }}>
            <ApplicationTable rows={data?.content ?? []} />
          </div>
          <Pagination
            page={data?.page.number ?? 0}
            totalPages={data?.page.totalPages ?? 1}
            onPage={(page) => setFilters((f) => ({ ...f, page }))}
          />
        </>
      )}
    </div>
  );
}
