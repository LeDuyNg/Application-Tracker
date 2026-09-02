import { useEffect, useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';

import { useLogout, useMe } from '../api/hooks/useMe';
import { Logo } from './Logo';

const COLLAPSE_KEY = 'jt:sidebar-collapsed';

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === '1';
  } catch {
    return false;
  }
}

/**
 * The persistent frame: a fixed left sidebar (brand, nav, a designed profile block with the
 * sign-out button) and a scrolling content column.
 *
 * The sidebar collapses to a 68px icon rail — toggled by the button at its foot, remembered
 * in localStorage. Collapsed, each nav item shows its label as a hover tooltip. On narrow
 * screens (<=860px, see theme.css) the sidebar becomes a top strip and the collapse toggle
 * is hidden.
 *
 * <App> has already confirmed a signed-in person by the time this renders, so
 * `useMe().data` is populated.
 */
export function AppShell() {
  const me = useMe();
  const logout = useLogout();

  const [collapsed, setCollapsed] = useState<boolean>(readCollapsed);

  useEffect(() => {
    try {
      localStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0');
    } catch {
      /* private mode / storage disabled — the toggle still works for this session */
    }
  }, [collapsed]);

  const name = me.data?.name ?? '';
  const email = me.data?.email ?? '';
  const initials =
    (name || email)
      .split(/[\s@.]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]?.toUpperCase() ?? '')
      .join('') || '?';

  return (
    <div className="app-frame">
      <aside className={collapsed ? 'sidebar collapsed' : 'sidebar'}>
        <NavLink to="/" className="brand" title="JobTracker">
          <span className="logo-tile"><Logo size={17} /></span>
          <span className="side-label">JobTracker</span>
        </NavLink>

        <nav className="side-nav">
          <NavLink to="/" end>
            <DashIcon />
            <span className="side-label">Dashboard</span>
            <span className="side-tip">Dashboard</span>
          </NavLink>
          <NavLink to="/applications">
            <StackIcon />
            <span className="side-label">Applications</span>
            <span className="side-tip">Applications</span>
          </NavLink>
          <NavLink to="/companies">
            <BuildingIcon />
            <span className="side-label">Companies</span>
            <span className="side-tip">Companies</span>
          </NavLink>
        </nav>

        <div className="side-spacer" />

        <div className="profile">
          <div className="profile-id">
            {me.data?.picture ? (
              <img className="avatar" src={me.data.picture} alt="" referrerPolicy="no-referrer" />
            ) : (
              <span className="avatar">{initials}</span>
            )}
            <div className="profile-text">
              <div className="profile-name">{name || 'Signed in'}</div>
              <div className="profile-email" title={email}>{email}</div>
            </div>
          </div>
          <button
            type="button"
            className="signout"
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
            title="Sign out"
          >
            <SignOutIcon />
            <span className="side-label">{logout.isPending ? 'Signing out…' : 'Sign out'}</span>
          </button>
        </div>

        <button
          type="button"
          className="collapse-btn"
          onClick={() => setCollapsed((c) => !c)}
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          <ChevronIcon flipped={collapsed} />
          <span className="side-label">Collapse</span>
        </button>
      </aside>

      <div className="content">
        <div className="content-inner">
          <Outlet />
        </div>
      </div>
    </div>
  );
}

/* --- 16px nav icons, currentColor --- */
function DashIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <rect x="2" y="2" width="5.5" height="5.5" rx="1.4" stroke="currentColor" strokeWidth="1.4" />
      <rect x="2" y="10" width="5.5" height="4" rx="1.4" stroke="currentColor" strokeWidth="1.4" />
      <rect x="9.5" y="2" width="4.5" height="4" rx="1.4" stroke="currentColor" strokeWidth="1.4" />
      <rect x="8.5" y="8.5" width="5.5" height="5.5" rx="1.4" stroke="currentColor" strokeWidth="1.4" />
    </svg>
  );
}
function StackIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path d="M8 2 14 5 8 8 2 5 8 2Z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
      <path d="M2 8.5 8 11.5 14 8.5" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
      <path d="M2 11.8 8 14.8 14 11.8" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
    </svg>
  );
}
function BuildingIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <rect x="3" y="2" width="10" height="12" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
      <path d="M6 5h1.5M6 8h1.5M6 11h1.5M9 5h1.5M9 8h1.5M9 11h1.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}
function SignOutIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path d="M6 2H3.5A1.5 1.5 0 0 0 2 3.5v9A1.5 1.5 0 0 0 3.5 14H6" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      <path d="M10 5l3 3-3 3M13 8H6" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
function ChevronIcon({ flipped }: { flipped: boolean }) {
  return (
    <svg
      width="15"
      height="15"
      viewBox="0 0 16 16"
      fill="none"
      aria-hidden="true"
      style={{ transform: flipped ? 'rotate(180deg)' : undefined, transition: 'transform .2s ease' }}
    >
      <path d="M10 3 5 8l5 5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
