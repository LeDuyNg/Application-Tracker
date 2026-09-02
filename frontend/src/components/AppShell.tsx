import { NavLink, Outlet } from 'react-router-dom';

import { useMe } from '../api/hooks/useMe';

/**
 * The persistent frame around every page: a top nav and the signed-in identity. The routed
 * page renders where <Outlet /> is.
 *
 * Auth display logic: `useMe()` asks the backend who we are without auto-redirecting on a
 * 401 (see the hook). So here we have three states — loading, signed in (`data.person`),
 * and signed out — and only the signed-out one shows a "Sign in" link. That link is a plain
 * <a> to `/oauth2/authorization/google`, NOT a router link: it must be a real navigation so
 * the browser follows Spring's redirect to Google.
 */
export function AppShell() {
  const me = useMe();

  return (
    <div style={{ minHeight: '100%' }}>
      <header
        style={{
          background: 'var(--surface)',
          borderBottom: '1px solid var(--border)',
          padding: '0 var(--space-5)',
        }}
      >
        <div
          className="spread"
          style={{ maxWidth: 1100, margin: '0 auto', height: 56 }}
        >
          <nav className="row" style={{ gap: 'var(--space-4)' }}>
            <strong style={{ marginRight: 'var(--space-2)' }}>JobTracker</strong>
            <NavLink to="/" end style={navStyle}>Dashboard</NavLink>
            <NavLink to="/applications" style={navStyle}>Applications</NavLink>
            <NavLink to="/companies" style={navStyle}>Companies</NavLink>
          </nav>

          <div className="small">
            {me.isLoading ? (
              <span className="muted">…</span>
            ) : me.data?.person ? (
              <span className="muted">{me.data.email}</span>
            ) : (
              <a href="/oauth2/authorization/google">Sign in</a>
            )}
          </div>
        </div>
      </header>

      <main style={{ maxWidth: 1100, margin: '0 auto', padding: 'var(--space-6) var(--space-5)' }}>
        <Outlet />
      </main>
    </div>
  );
}

/** react-router hands NavLink a render function an `isActive` flag; bold the current tab. */
function navStyle({ isActive }: { isActive: boolean }): React.CSSProperties {
  return {
    color: isActive ? 'var(--text)' : 'var(--text-muted)',
    fontWeight: isActive ? 700 : 500,
    textDecoration: 'none',
  };
}
