/**
 * The app mark: a funnel — three tapering bars — nodding to the application funnel that is
 * the dashboard's signature stat. `currentColor`, so it takes the colour of whatever wraps
 * it (indigo in the header, white on the gradient tile).
 */
export function Logo({ size = 24 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      className="logo"
    >
      <rect x="2" y="4" width="20" height="4" rx="2" fill="currentColor" />
      <rect x="6" y="11" width="12" height="4" rx="2" fill="currentColor" />
      <rect x="9" y="18" width="6" height="4" rx="2" fill="currentColor" />
    </svg>
  );
}
