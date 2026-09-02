/** Prev / next for the paged applications list. `page` is 0-based, matching Spring. */
export function Pagination({
  page,
  totalPages,
  onPage,
}: {
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="row" style={{ justifyContent: 'center', gap: 'var(--space-3)' }}>
      <button type="button" onClick={() => onPage(page - 1)} disabled={page <= 0}>
        ← Prev
      </button>
      <span className="small muted">Page {page + 1} of {totalPages}</span>
      <button type="button" onClick={() => onPage(page + 1)} disabled={page >= totalPages - 1}>
        Next →
      </button>
    </div>
  );
}
