import type { ApplicationFilters } from '../api/hooks/applications';
import { useCompanies } from '../api/hooks/companies';
import { APPLICATION_STATUSES, label } from '../lib/enums';

/**
 * Controlled filter inputs for the applications list. "Controlled" = each input's value
 * comes from `value` (parent state) and every keystroke calls `onChange` with the next
 * filter object; the parent owns the state and re-runs the query. This component holds none.
 */
export function FiltersBar({
  value,
  onChange,
}: {
  value: ApplicationFilters;
  onChange: (next: ApplicationFilters) => void;
}) {
  const companies = useCompanies();

  // Any filter change resets to page 0 — page 3 of the old result set rarely makes sense
  // for the new one.
  function set(patch: Partial<ApplicationFilters>) {
    onChange({ ...value, ...patch, page: 0 });
  }

  return (
    <div className="row-wrap card" style={{ padding: 'var(--space-3)' }}>
      <input
        placeholder="Search company, role, notes…"
        value={value.q ?? ''}
        onChange={(e) => set({ q: e.target.value })}
        style={{ flex: '2 1 220px' }}
      />

      <select
        value={value.status ?? ''}
        onChange={(e) => set({ status: e.target.value || undefined })}
        style={{ flex: '1 1 140px' }}
      >
        <option value="">Any status</option>
        {APPLICATION_STATUSES.map((s) => (
          <option key={s} value={s}>{label(s)}</option>
        ))}
      </select>

      <select
        value={value.companyId ?? ''}
        onChange={(e) => set({ companyId: e.target.value || undefined })}
        style={{ flex: '1 1 160px' }}
      >
        <option value="">Any company</option>
        {(companies.data ?? []).map((c) => (
          <option key={c.id} value={c.id}>{c.name}</option>
        ))}
      </select>

      <label className="row small" style={{ gap: 'var(--space-1)' }}>
        From
        <input
          type="date"
          value={value.from ?? ''}
          onChange={(e) => set({ from: e.target.value || undefined })}
        />
      </label>
      <label className="row small" style={{ gap: 'var(--space-1)' }}>
        To
        <input
          type="date"
          value={value.to ?? ''}
          onChange={(e) => set({ to: e.target.value || undefined })}
        />
      </label>

      <button
        type="button"
        onClick={() => onChange({ page: 0, size: value.size })}
        disabled={!value.q && !value.status && !value.companyId && !value.from && !value.to}
      >
        Clear
      </button>
    </div>
  );
}
