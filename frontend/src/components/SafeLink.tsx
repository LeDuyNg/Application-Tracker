import { safeUrl } from '../lib/url';

/**
 * Renders a user-supplied URL as a link, or an em dash when there isn't a safe one.
 *
 * Every stored URL in the app goes through here rather than into a bare `<a href={...}>`
 * — see `lib/url.ts` for why that matters. A value that is missing *or* refused renders
 * identically, because from the reader's point of view "no link" and "not a link we will
 * open" are the same thing.
 *
 * `rel="noreferrer"` implies `noopener`, so the opened tab gets no handle on `window.opener`.
 */
export function SafeLink({ url }: { url: string | null | undefined }) {
  const href = safeUrl(url);
  if (!href) return <>—</>;
  return (
    <a href={href} target="_blank" rel="noreferrer">
      open ↗
    </a>
  );
}
