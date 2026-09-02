import { Logo } from '../components/Logo';

/**
 * What a signed-out visitor sees. <App> renders this instead of the router when
 * `useMe()` reports no person. The "Continue with Google" button is a real <a> (a full
 * navigation, so the browser follows Spring's redirect to Google) — not a router link.
 */
export function Landing() {
  return (
    <div className="landing">
      <div className="landing-card">
        <div className="landing-mark">
          <Logo size={26} />
        </div>

        <h1>JobTracker</h1>
        <p className="lead">
          Every application and interview round of your job search, in one place.
        </p>

        <ul className="landing-features">
          <li>
            <FunnelIcon />
            <span>Track companies, applications and each interview stage as it happens.</span>
          </li>
          <li>
            <BellIcon />
            <span>See what needs chasing and what has gone quiet.</span>
          </li>
          <li>
            <CalendarIcon />
            <span>Know which interviews are coming up this week.</span>
          </li>
        </ul>

        <a href="/oauth2/authorization/google" className="btn primary btn-google">
          <GoogleIcon />
          Continue with Google
        </a>

        <p className="tiny faint" style={{ marginTop: 'var(--space-4)', marginBottom: 0 }}>
          Private — single user, invite-only.
        </p>
      </div>
    </div>
  );
}

/* --- small inline icons (16px, currentColor) --- */

function FunnelIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path d="M2 3h12l-4.5 5.5V13L6.5 11V8.5L2 3Z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
    </svg>
  );
}
function BellIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path d="M4 7a4 4 0 1 1 8 0c0 3 1 4 1 4H3s1-1 1-4Z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
      <path d="M6.5 13a1.5 1.5 0 0 0 3 0" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}
function CalendarIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <rect x="2.5" y="3.5" width="11" height="10" rx="1.5" stroke="currentColor" strokeWidth="1.4" />
      <path d="M2.5 6.5h11M5.5 2v3M10.5 2v3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}
function GoogleIcon() {
  // A plain white "G" — the button ground is indigo, so the full-colour Google mark
  // would clash. Monochrome is the conventional treatment on a coloured button.
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M21.5 12.2c0-.7-.06-1.37-.18-2.02H12v3.83h5.34a4.57 4.57 0 0 1-1.98 3v2.5h3.2c1.87-1.73 2.94-4.28 2.94-7.31Z"
        fill="#fff"
      />
      <path
        d="M12 22c2.67 0 4.9-.88 6.54-2.4l-3.2-2.48c-.89.6-2.03.95-3.34.95-2.57 0-4.75-1.73-5.53-4.06H3.16v2.56A9.99 9.99 0 0 0 12 22Z"
        fill="#fff"
        opacity="0.75"
      />
      <path
        d="M6.47 12.01c-.2-.6-.31-1.24-.31-1.9 0-.66.11-1.3.31-1.9V5.65H3.16A9.99 9.99 0 0 0 2 10.1c0 1.6.38 3.12 1.16 4.46l3.31-2.55Z"
        fill="#fff"
        opacity="0.55"
      />
      <path
        d="M12 4.15c1.45 0 2.75.5 3.78 1.48l2.83-2.83C16.9 1.2 14.67.3 12 .3A9.99 9.99 0 0 0 3.16 5.65l3.31 2.56C7.25 5.88 9.43 4.15 12 4.15Z"
        fill="#fff"
        opacity="0.9"
      />
    </svg>
  );
}
